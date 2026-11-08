package com.example.exo_java_demo;

import android.provider.Settings;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
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
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * T3.1 FR-6：HLS/DASH/ABR 弱网实测（仅在 CI 模拟器上由工作流脚本驱动，本机单跑会因
 * 无限速协调而超时失败——用 notClass 与常规用例隔离）。
 *
 * 单次会话内观察 ABR 下调与回升：切到 DASH 多码率源 → 满速等到高档（≥480p）→
 * logcat 打点 HOLD:high，主机脚本执行 `adb emu network speed edge` 并写
 * Settings.Global "abr_phase"="edge" → 等码率下调（≤240p）→ 打点 HOLD:low →
 * 主机恢复 network speed full 且 abr_phase=full → 等回升（≥480p）。
 * 高度断言基于 {@link Player.Listener#onVideoSizeChanged}，不轮询界面。
 */
@OptIn(markerClass = UnstableApi.class) // PlayerView 属 Media3 opt-in API
@RunWith(AndroidJUnit4.class)
public class AbrThrottleTest {

    private static final String TAG = "ABR_TEST";
    private static final String PHASE_KEY = "abr_phase";

    /** 满速下 ABR 应爬到的高度下限 / 限速后应降到的高度上限（bbb_30fps 档位 180~1080p）。 */
    private static final int HIGH_MIN_HEIGHT = 480;
    private static final int LOW_MAX_HEIGHT = 240;

    private ActivityScenario<MainActivity> scenario;

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
        // 切到 DASH 多码率预置源（Big Buck Bunny，180p~1080p）。
        // 定位方式与 MainPlaybackFlowTest.sourceSwitch 相同：按文字直点条目标题
        // （触摸穿透到条目根的 listener），先收软键盘避免遮挡可见性校验。
        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog()).perform(closeSoftKeyboard());
        onView(withText(R.string.preset_dash)).inRoot(isDialog()).perform(click());
        awaitMediaItem(".mpd", 60_000, "未切换到 DASH 预置源");

        // 满速：ABR 爬档到 ≥480p
        awaitVideoHeight(h -> h >= HIGH_MIN_HEIGHT, 180_000,
                "满速下未爬升到 " + HIGH_MIN_HEIGHT + "p");
        Log.i(TAG, "HOLD:high");

        // 主机脚本限速（adb emu network speed edge）后置 abr_phase=edge
        awaitPhase("edge", 240_000);
        awaitVideoHeight(h -> h <= LOW_MAX_HEIGHT, 300_000,
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

    /** 等视频高度满足条件（初始值即时检查，之后靠 onVideoSizeChanged）。 */
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
                    if (condition.test(videoSize.height)) {
                        matched.countDown();
                    }
                }
            });
        });
        assertTrue(message + "（超时）", matched.await(timeoutMs, TimeUnit.MILLISECONDS));
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
