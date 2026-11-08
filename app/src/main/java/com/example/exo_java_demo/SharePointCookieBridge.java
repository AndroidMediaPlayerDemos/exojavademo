package com.example.exo_java_demo;

import android.net.Uri;
import android.webkit.CookieManager;

import java.net.HttpCookie;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * FR-7 / T4.2（路线 A）：SharePoint 凭据搬运桥。
 * 把 WebView 登录得到的 rtFa / FedAuth 从 android.webkit.CookieManager 提取，
 * 写入 JVM CookieManager（经 CookieHandler.setDefault 全局生效）。Media3
 * DefaultHttpDataSource 底层的 HttpURLConnection 每次发起请求（清单、Range、
 * 302 跟随的每一跳）都会自动从默认 CookieHandler 取 Cookie，从而实现携带
 * 凭据直连播放 *.sharepoint.com 资源。
 *
 * 凭据只在两个 Cookie 存储之间搬运，不进界面、不进日志（PRD FR-7 红线）。
 */
public class SharePointCookieBridge {

    /** 只搬认证 Cookie，不整串复制 WebView Cookie。 */
    private static final List<String> AUTH_COOKIE_NAMES = Arrays.asList("rtFa", "FedAuth");

    private final java.net.CookieManager mediaCookieManager;

    public SharePointCookieBridge(java.net.CookieManager mediaCookieManager) {
        this.mediaCookieManager = mediaCookieManager;
    }

    /** 是否 FR-7 验证对象：*.sharepoint.com 直连 URL。 */
    public static boolean isSharePointUrl(String url) {
        String host = Uri.parse(url).getHost();
        return host != null && (host.equals("sharepoint.com") || host.endsWith(".sharepoint.com"));
    }

    /**
     * 把 WebView 中该 URL 主机下的 rtFa / FedAuth 写入 JVM CookieManager。
     * 同名同域同路径的新 Cookie 覆盖旧值，重新登录后无需清缓存、直接重试即可。
     *
     * @return 是否写入至少一个认证 Cookie（false = WebView 未登录过该主机）
     */
    public boolean syncCookies(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) {
            return false;
        }
        String cookieHeader = CookieManager.getInstance().getCookie("https://" + host + "/");
        if (cookieHeader == null) {
            return false;
        }
        URI uri = URI.create("https://" + host + "/");
        int injected = 0;
        for (String raw : cookieHeader.split(";")) {
            String pair = raw.trim();
            int eq = pair.indexOf('=');
            if (eq <= 0 || !AUTH_COOKIE_NAMES.contains(pair.substring(0, eq))) {
                continue;
            }
            HttpCookie cookie = new HttpCookie(pair.substring(0, eq), pair.substring(eq + 1));
            cookie.setDomain(host); // 不带前导点：仅精确匹配本主机，不泄漏到其他租户
            cookie.setPath("/");
            mediaCookieManager.getCookieStore().add(uri, cookie);
            injected++;
        }
        return injected > 0;
    }
}
