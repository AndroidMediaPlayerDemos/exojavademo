#!/usr/bin/env python3
"""T3.1 ABR 弱网实测限速代理：上游全速预取，下游按令牌桶匀速放行。

背景（本地模拟器实测）：`adb emu network speed` 在 google_atd x86_64 镜像上
配置值与实际交付严重不符（edge/umts/数值 800k 档实测均只给 ~15-30kbps 涓流），
guest 内核又仅带 sch_htb（无 ingress/ifb/police），因此限速放到宿主用户态做：

- GET /__ctrl?bps=N   设置限速（0=不限速），编排脚本在用例打点间切换
- GET /akamai/<path>  → https://dash.akamaized.net/<path>（DASH 实测源固定上游）

模拟器内经 10.0.2.2（宿主回环别名）访问。python3 stdlib 零依赖，
macOS 本地与 Linux CI 同一份。上游由独立线程全速预取进有界缓冲（8MB），
与下游放行速率解耦：限速期间管道保持满载，解除瞬间即可倾泻；
令牌桶容量 1 秒额度、涓流粒度 64KB，无突发长静默——下游读超时只取决于
真实速率，不取决于整形器行为。
"""
import http.server
import threading
import time
import urllib.request

UPSTREAM = "https://dash.akamaized.net/"
CHUNK = 65536
MAX_BUFFER = 8 * 1024 * 1024

_lock = threading.Lock()
_rate_bps = 0        # 0 = 不限速
_tokens = 0.0
_last = time.monotonic()


def _acquire(nbytes):
    """按当前 bps 领取 nbytes 字节额度，不足则分段小睡等待。"""
    global _tokens, _last
    while True:
        with _lock:
            if _rate_bps <= 0:
                return
            now = time.monotonic()
            _tokens = min(_rate_bps / 8.0,
                          _tokens + (now - _last) * _rate_bps / 8.0)
            _last = now
            if _tokens >= nbytes:
                _tokens -= nbytes
                return
            need = (nbytes - _tokens) * 8.0 / _rate_bps
        time.sleep(min(need, 0.25))


class Prefetcher(threading.Thread):
    """上游读线程：全速读进有界块列表，写端按需取——读速率与客户端放行解耦。"""

    def __init__(self, upstream):
        super().__init__(daemon=True)
        self.upstream = upstream
        self._cv = threading.Condition()
        self._chunks = []
        self._eof = False

    def run(self):
        while True:
            chunk = self.upstream.read(CHUNK)
            while True:
                with self._cv:
                    if not chunk or sum(map(len, self._chunks)) < MAX_BUFFER:
                        if chunk:
                            self._chunks.append(chunk)
                        else:
                            self._eof = True
                        self._cv.notify_all()
                        break
                time.sleep(0.05)
            if not chunk:
                return

    def next_chunk(self):
        with self._cv:
            while not self._chunks and not self._eof:
                self._cv.wait(0.5)
            return self._chunks.pop(0) if self._chunks else None


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path.startswith("/__ctrl"):
            bps = 0
            if "bps=" in self.path:
                try:
                    bps = int(self.path.split("bps=")[1].split("&")[0])
                except ValueError:
                    pass
            with _lock:
                global _rate_bps, _tokens
                _rate_bps = bps
                _tokens = 0.0
            body = "rate={}".format(bps).encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        req = urllib.request.Request(
            UPSTREAM + self.path.lstrip("/"),
            headers={k: v for k, v in self.headers.items()
                     if k.lower() in ("range", "user-agent")})
        try:
            upstream = urllib.request.urlopen(req, timeout=30)
        except Exception:
            self.send_error(502, "upstream fetch failed")
            return
        self.send_response(upstream.status)
        length_known = False
        for h in ("Content-Length", "Content-Type", "Accept-Ranges",
                  "Content-Range"):
            if h in upstream.headers:
                self.send_header(h, upstream.headers[h])
                length_known = length_known or h == "Content-Length"
        if not length_known:
            self.close_connection = True   # 无长度则用连接关闭定界
        self.end_headers()
        fetcher = Prefetcher(upstream)
        fetcher.start()
        while True:
            chunk = fetcher.next_chunk()
            if chunk is None:
                break
            _acquire(len(chunk))
            self.wfile.write(chunk)

    def log_message(self, *_args):
        pass  # 静默访问日志；错误仍走 send_error


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("127.0.0.1", 8801), Handler).serve_forever()
