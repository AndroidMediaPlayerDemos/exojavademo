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
 * 带宽采样/选集诊断探针（不做档位断言、必过；经 -Pclass 单独驱动，默认批次以
 * notClass 排除）。溯源 CI 恒 360p 时三轮迭代定位两层根因：est 显示恒 0 系
 * onBandwidthEstimate 槽位误读（槽 2 是窗口 ms、槽 4 才是估计值）；档位冻结系
 * media3 视口约束按物理屏截断自适应组（atd 小屏 320x640 → 仅 ≤640x360 档入选，
 * run 42536919418 全档 sup=true 而选集按像素数切分为证）。根因已收口
 * （run 42554954898 全绿），保留作 ABR 异常时的取证工具：75s 观察窗打点
 * 全档位 sel/sup/支持码、PARAMS（含 viewport）、est 原始值与每分片实测速率。
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
                    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs,
                            long totalBytesLoaded, long bitrateEstimate) {
                        lastEst.set((int) bitrateEstimate);
                        int n = estEvents.incrementAndGet();
                        // 每 10 笔打一行，避免刷屏；首笔必打
                        if (n <= 3 || n % 10 == 0) {
                            Log.i(TAG, "EST#" + n + " bps=" + bitrateEstimate
                                    + " winMs=" + totalLoadTimeMs
                                    + " bytes=" + totalBytesLoaded);
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
                    .buildUpon().setMaxVideoSize(1024, 576)
                    .clearViewportSizeConstraints().build());
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
                // 全档位清单：选中态/受支持态/支持码（3=HANDLED 2=EXCEEDS_CAPABILITIES 0/…=不支持）
                StringBuilder sel = new StringBuilder();
                for (androidx.media3.common.Tracks.Group g
                        : player.getCurrentTracks().getGroups()) {
                    for (int i = 0; i < g.length; i++) {
                        androidx.media3.common.Format f = g.getTrackFormat(i);
                        sel.append(f.width).append('x').append(f.height)
                                .append('@').append(f.bitrate / 1000).append('k')
                                .append(" sel=").append(g.isTrackSelected(i))
                                .append(" sup=").append(g.isTrackSupported(i))
                                .append('(').append(g.getTrackSupport(i)).append(") | ");
                    }
                }
                androidx.media3.common.TrackSelectionParameters p =
                        player.getTrackSelectionParameters();
                Log.i(TAG, "PARAMS maxVideo=" + p.maxVideoWidth + "x" + p.maxVideoHeight
                        + " maxVBr=" + p.maxVideoBitrate
                        + " viewport=" + p.viewportWidth + "x" + p.viewportHeight
                        + " physLimit=" + p.isViewportSizeLimitedByPhysicalDisplaySize);
                Log.i(TAG, "VITAL h=" + player.getVideoSize().height
                        + " est=" + lastEst.get() + "bps"
                        + " estEvents=" + estEvents.get()
                        + " loadedMB=" + loadedBytes.get() / (1024 * 1024)
                        + " tracks=[" + sel.toString().trim() + "]"
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
