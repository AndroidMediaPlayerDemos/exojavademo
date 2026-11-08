package com.example.exo_java_demo;

import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * T3.1 FR-6：HLS/DASH/ABR 弱网实测（仅在 CI 模拟器上由工作流脚本驱动，本机单跑会因
 * 无限速协调而超时失败——用 notClass 与常规用例隔离）。
 *
 * 单次会话内观察 ABR 下调与回升：经宿主限速代理加载 DASH 多码率源 → 满速等到
 * 高档（≥480p）→ logcat 打点 HOLD:high，编排脚本限速并写 Settings.Global
 * "abr_phase"="slow" → 等码率下调（≤240p）→ 打点 HOLD:low → 脚本恢复不限速且
 * abr_phase=full → 等回升（≥480p）。高度断言基于
 * {@link Player.Listener#onVideoSizeChanged}，不轮询界面。
 *
 * 限速通路为宿主 python 代理 scripts/abr_shaper_proxy.py（上游全速预取、下游
 * 令牌桶匀速涓流），模拟器内经 10.0.2.2:8801 访问；`adb emu network speed`
 * 在 google_atd 镜像上配置值与实际交付不符（各档实测 ~15-30kbps 涓流）、guest
 * 内核缺 ingress 整形模块（仅 sch_htb），均已弃用。限速 800kbps 的取值约束：
 * ①封顶 576p 后在飞分片 ~1.57MB≈16s，叠加并发音频分片仍 <App 30s 读超时；
 * ②压住 270p 档(760+67=827k) 逼 ABR 落到 180p。阶梯无 480p 档：≥480 实际
 * 命中 576p(3134k)。命名档教训：edge/umts 下大分片完不成，读超时耗尽重试后
 * 致命 Source error 进 IDLE（本地实测 37s/125s 死亡，h=0 曾伪装成降档）。
 */
@OptIn(markerClass = UnstableApi.class) // PlayerView 属 Media3 opt-in API
@RunWith(AndroidJUnit4.class)
public class AbrThrottleTest {

    private static final String TAG = "ABR_TEST";
    private static final String PHASE_KEY = "abr_phase";

    /** 满速下 ABR 应爬到的高度下限 / 限速后应降到的高度上限（bbb_30fps 档位 180~1080p）。 */
    private static final int HIGH_MIN_HEIGHT = 480;
    private static final int LOW_MAX_HEIGHT = 240;

    /** 宿主限速代理上的 DASH 源（代理把 /akamai/* 转发到 dash.akamaized.net）。 */
    private static final String PROXY_DASH_URL =
            "http://10.0.2.2:8801/akamai/bbb_30fps/bbb_30fps.mpd";

    /** h=0 是"无格式/渲染器重置"（致命错误后进 IDLE 也会触发），不算降档达标。 */

    private ActivityScenario<MainActivity> scenario;

    /** 最近一次 ABR 带宽估计（kbps，槽 4 才是 bitrateEstimate，-1=尚无样本）。 */
    private volatile int estimateKbps = -1;

    /** 最近一次心跳看到的播放状态（-1=尚未采样），用于 IDLE 快速失败。 */
    private volatile int lastPlaybackState = -1;

    @Before
    public void setUp() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(180, TimeUnit.SECONDS);
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    @Test
    public void dashAbr_downshiftUnderThrottleAndRecovery() throws InterruptedException {
        // 挂带宽估计监听（media3 1.11 无 ExoPlayer.getBandwidthMeter，走 AnalyticsListener。
        // 接口槽位：(EventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate)
        // —— 估计值在第 4 槽；曾误读第 2 槽（窗口毫秒数）致 est 显示恒 0）
        scenario.onActivity(activity -> ((ExoPlayer) playerOf(activity)).addAnalyticsListener(
                new AnalyticsListener() {
                    @Override
                    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs,
                            long totalBytesLoaded, long bitrateEstimate) {
                        estimateKbps = (int) (bitrateEstimate / 1000);
                    }
                }));
        // 封顶 576p：720p 分片 ~2.4MB 在 800kbps 下叠加并发音频即贴近 30s 读超时，
        // 576p 分片 ~1.57MB(~16s) 留足余量；≥480 断言仍由 576p 满足，180↔576
        // 间的升降档保持真实自适应。
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                    .buildUpon().setMaxVideoSize(1024, 576).build());
        });
        // 经宿主限速代理加载 DASH 多码率源（Big Buck Bunny，180p~576p）。
        // 输 URL 点加载（对话框随加载 dismiss），与手工流程 FR-1 一致。
        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog())
                .perform(replaceText(PROXY_DASH_URL), closeSoftKeyboard());
        onView(withId(R.id.btn_load_source)).inRoot(isDialog()).perform(click());
        awaitMediaItem("10.0.2.2:8801", 60_000, "未切换到限速代理 DASH 源");

        // 满速：ABR 爬档到 ≥480p
        awaitVideoHeight(h -> h >= HIGH_MIN_HEIGHT, 180_000,
                "满速下未爬升到 " + HIGH_MIN_HEIGHT + "p");
        Log.i(TAG, "HOLD:high");

        // 主机脚本限速（adb emu network speed 800，双向同值）后置 abr_phase=slow
        awaitPhase("slow", 240_000);
        awaitVideoHeight(h -> h > 0 && h <= LOW_MAX_HEIGHT, 300_000,
                "限速后未下调到 " + LOW_MAX_HEIGHT + "p 以内");
        Log.i(TAG, "HOLD:low");

        // 主机恢复限速后置 abr_phase=full：ABR 回升
        awaitPhase("full", 240_000);
        awaitVideoHeight(h -> h >= HIGH_MIN_HEIGHT, 300_000,
                "恢复限速后未回升到 " + HIGH_MIN_HEIGHT + "p");
        Log.i(TAG, "DONE");
    }

    /** 等当前媒体项变为包含指定片段的 URL（源切换确认）。 */
    private void awaitMediaItem(String urlPart, long timeoutMs, String message)
            throws InterruptedException {
        CountDownLatch switched = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            if (containsUrlPart(player, urlPart)) {
                switched.countDown();
                return;
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(
                        androidx.media3.common.MediaItem mediaItem, int reason) {
                    if (containsUrlPart(player, urlPart)) {
                        switched.countDown();
                    }
                }
            });
        });
        assertTrue(message + "（超时）", switched.await(timeoutMs, TimeUnit.MILLISECONDS));
    }

    private static boolean containsUrlPart(Player player, String urlPart) {
        androidx.media3.common.MediaItem item = player.getCurrentMediaItem();
        return item != null && item.localConfiguration != null
                && item.localConfiguration.uri.toString().contains(urlPart);
    }

    /** 等视频高度满足条件（初始值即时检查，之后靠 onVideoSizeChanged；全程打点供超时诊断）。 */
    private void awaitVideoHeight(IntPredicate condition, long timeoutMs, String message)
            throws InterruptedException {
        CountDownLatch matched = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            if (condition.test(player.getVideoSize().height)) {
                matched.countDown();
                return;
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    Log.i(TAG, "SIZE h=" + videoSize.height);
                    if (condition.test(videoSize.height)) {
                        matched.countDown();
                    }
                }
            });
        });
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (!matched.await(5, TimeUnit.SECONDS)) {
            scenario.onActivity(this::logPlaybackVitals);
            if (lastPlaybackState == Player.STATE_IDLE) {
                fail(message + "（播放器进入 IDLE：加载错误耗尽重试，详见 logcat Playback error）");
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                fail(message + "（超时）");
            }
        }
    }

    /** 心跳打点当前播放画像（高度/带宽估计/状态/进度/缓冲），超时时据此定位卡点。 */
    private void logPlaybackVitals(MainActivity activity) {
        Player player = playerOf(activity);
        lastPlaybackState = player.getPlaybackState();
        Log.i(TAG, "WAIT h=" + player.getVideoSize().height
                + " est=" + estimateKbps + "kbps"
                + " state=" + player.getPlaybackState()
                + (player.isPlaying() ? " playing" : " !playing")
                + " pos=" + player.getCurrentPosition() / 1000 + "s"
                + " bufMs=" + (player.getBufferedPosition() - player.getCurrentPosition()));
    }

    /** 等主机脚本写入限速阶段（adb shell settings put global abr_phase edge|full）。 */
    private void awaitPhase(String phase, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String value = Settings.Global.getString(
                    InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getContentResolver(), PHASE_KEY);
            if (phase.equals(value)) {
                return;
            }
            Thread.sleep(2_000);
        }
        fail("主机脚本未在 " + timeoutMs / 1000 + "s 内设置 " + PHASE_KEY + "=" + phase
                + "（限速协调未就位）");
    }

    private static Player playerOf(MainActivity activity) {
        PlayerView playerView = activity.findViewById(R.id.player_view);
        Player player = playerView.getPlayer();
        assertTrue("Player 未初始化", player != null);
        return player;
    }
}
