package com.example.exo_java_demo;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * 带宽采样诊断探针（不做档位断言、必过；经 -Pclass 单独驱动，默认批次以
 * notClass 排除，仅诊断 CI est 异常时启用）：CI run 42488899281 中 est 冻结
 * 0kbps、档位冻结 360p，而缓冲数秒内打满 50s——带宽采样与真实传输矛盾。
 * 75s 观察窗内全量打点 onBandwidthEstimate 原始值与每次 onLoad 实测速率，
 * logcat 即结论（est 与 LOAD rate 的背离/一致性）。
 */
@OptIn(markerClass = UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class AbrMeterProbeTest {

    private static final String TAG = "ABR_PROBE";
    private static final String PROXY_DASH_URL =
            "http://10.0.2.2:8801/akamai/bbb_30fps/bbb_30fps.mpd";

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
    public void probeBandwidthSampling() throws InterruptedException {
        AtomicInteger lastEst = new AtomicInteger(-1);
        AtomicInteger estEvents = new AtomicInteger(0);
        AtomicLong loadedBytes = new AtomicLong(0);
        // 全量记录带宽估计回调原始值（bps）与事件计数
        scenario.onActivity(activity -> ((ExoPlayer) playerOf(activity)).addAnalyticsListener(
                new AnalyticsListener() {
                    @Override
                    public void onBandwidthEstimate(EventTime eventTime, int bitrateEstimate,
                            long elapsedRealtimeMs, long loadDurationMs) {
                        lastEst.set(bitrateEstimate);
                        int n = estEvents.incrementAndGet();
                        // 每 10 笔打一行，避免刷屏；首笔必打
                        if (n <= 3 || n % 10 == 0) {
                            Log.i(TAG, "EST#" + n + " bps=" + bitrateEstimate);
                        }
                    }

                    @Override
                    public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo,
                            MediaLoadData mediaLoadData) {
                        loadedBytes.addAndGet(loadEventInfo.bytesLoaded);
                        String uri = loadEventInfo.uri.toString();
                        String tail = uri.substring(uri.lastIndexOf('/') + 1);
                        long durMs = loadEventInfo.loadDurationMs;
                        long kbps = durMs > 0 ? loadEventInfo.bytesLoaded * 8000 / durMs : -1;
                        Log.i(TAG, "LOAD " + tail + " bytes=" + loadEventInfo.bytesLoaded
                                + " durMs=" + durMs + " rate=" + kbps + "kbps");
                    }

                    @Override
                    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
                        Log.i(TAG, "SIZE h=" + videoSize.height);
                    }
                }));
        scenario.onActivity(activity -> {
            Player player = playerOf(activity);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                    .buildUpon().setMaxVideoSize(1024, 576).build());
        });
        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog())
                .perform(replaceText(PROXY_DASH_URL), closeSoftKeyboard());
        onView(withId(R.id.btn_load_source)).inRoot(isDialog()).perform(click());
        Log.i(TAG, "PROBE start");

        long deadline = SystemClock.elapsedRealtime() + 75_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(5_000);
            scenario.onActivity(activity -> {
                Player player = playerOf(activity);
                Log.i(TAG, "VITAL h=" + player.getVideoSize().height
                        + " est=" + lastEst.get() + "bps"
                        + " estEvents=" + estEvents.get()
                        + " loadedMB=" + loadedBytes.get() / (1024 * 1024)
                        + " state=" + player.getPlaybackState()
                        + " pos=" + player.getCurrentPosition() / 1000 + "s"
                        + " bufMs=" + (player.getBufferedPosition() - player.getCurrentPosition()));
            });
        }
        Log.i(TAG, "PROBE end");
    }

    private static Player playerOf(MainActivity activity) {
        PlayerView playerView = activity.findViewById(R.id.player_view);
        Player player = playerView.getPlayer();
        org.junit.Assert.assertTrue("Player 未初始化", player != null);
        return player;
    }
}
