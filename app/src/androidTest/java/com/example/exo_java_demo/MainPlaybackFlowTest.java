package com.example.exo_java_demo;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

/**
 * T3.3 主流程仪器测试（Espresso，CI 模拟器运行）：起播、倍速切换、源切换。
 * 播放器状态断言不轮询界面，而是经 {@link PlayerView#getPlayer()} 注册
 * {@link Player.Listener}，以 CountDownLatch 等待目标状态（网络源给足超时）。
 */
@OptIn(markerClass = UnstableApi.class) // PlayerView / MediaItem.localConfiguration 属 Media3 opt-in API
@RunWith(AndroidJUnit4.class)
public class MainPlaybackFlowTest {

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        // CI 模拟器上首次启动与网络起播都偏慢，放宽 Espresso 主策略超时
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(
                120, TimeUnit.SECONDS);
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    /** 起播：默认源（W3C Sintel MP4）进入 READY 且实际出画（isPlaying）。 */
    @Test
    public void initialPlayback_defaultSourceStartsPlaying() throws InterruptedException {
        CountDownLatch playing = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            if (player.isPlaying()) {
                playing.countDown();
                return;
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        playing.countDown();
                    }
                }
            });
        });
        assertTrue("默认源 60s 内未起播", playing.await(60, TimeUnit.SECONDS));
    }

    /** 倍速切换：对话框选 1.5x，PlaybackParameters 生效。 */
    @Test
    public void speedDialog_appliesSelectedSpeed() throws InterruptedException {
        onView(withId(R.id.button)).perform(click());
        onView(withText("1.5x")).inRoot(isDialog())
                .check(matches(isDisplayed()))
                .perform(click());

        CountDownLatch applied = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            if (player.getPlaybackParameters().speed == 1.5f) {
                applied.countDown();
                return;
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackParametersChanged(PlaybackParameters parameters) {
                    if (parameters.speed == 1.5f) {
                        applied.countDown();
                    }
                }
            });
        });
        assertTrue("选择 1.5x 后 10s 内未生效", applied.await(10, TimeUnit.SECONDS));
    }

    /** 源切换：源对话框点 HLS 预置源，媒体项切换且进入 READY。 */
    @Test
    public void sourceSwitch_playsHlsPreset() throws InterruptedException {
        onView(withId(R.id.btn_source)).perform(click());
        // 条目点击 listener 挂在条目根（LinearLayout），标题 TextView 非 clickable，
        // Espresso 的 click 走触摸分发会自然穿透到根；列表短、预置条目均在屏内，
        // 直接按文字定位即可（scrollTo/actionOnItem 要求条目 90% 可见，软键盘
        // 自动弹起时遮挡列表会误判，故先收键盘）。
        onView(withId(R.id.source_input)).inRoot(isDialog()).perform(closeSoftKeyboard());
        try {
            onView(withText(R.string.preset_hls)).inRoot(isDialog()).perform(click());
        } catch (RuntimeException e) {
            // AGP 控制台会截断 Cause 链（完整栈只在 runner 的 HTML 报告里），
            // 这里把根因折进断言消息，CI 日志一次可见。
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new AssertionError("点击预置条目失败，根因: " + root + " @ "
                    + root.getStackTrace()[0], e);
        }

        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> currentUri = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            MediaItem item = player.getCurrentMediaItem();
            if (item != null && item.localConfiguration != null) {
                currentUri.set(item.localConfiguration.uri.toString());
            }
            if (player.getPlaybackState() == Player.STATE_READY
                    && currentUri.get().contains(".m3u8")) {
                ready.countDown();
                return;
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    if (mediaItem != null && mediaItem.localConfiguration != null) {
                        currentUri.set(mediaItem.localConfiguration.uri.toString());
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY
                            && currentUri.get().contains(".m3u8")) {
                        ready.countDown();
                    }
                }
            });
        });
        assertTrue("HLS 源 90s 内未完成切换并进入 READY，当前：" + currentUri.get(),
                ready.await(90, TimeUnit.SECONDS));
    }

    private static Player playerOf(MainActivity activity) {
        PlayerView playerView = activity.findViewById(R.id.player_view);
        Player player = playerView.getPlayer();
        assertTrue("Player 未初始化", player != null);
        return player;
    }
}
