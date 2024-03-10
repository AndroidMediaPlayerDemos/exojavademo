package com.example.exo_java_demo;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    public String[] VIDEO_URIS = {"https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.jpg", "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"};
    private PlayerView playerView;
    private ExoPlayer player;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playerView = findViewById(R.id.player_view);

        ApplicationInfo applicationInfo;
        String applicationName;
        try {
            applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), 0);
            applicationName = (String) getPackageManager().getApplicationLabel(applicationInfo);

        } catch (PackageManager.NameNotFoundException e) {
            applicationName = "com.example.exo_java_demo";
        }

        DataSource.Factory dataSourceFactory = () -> {
            HttpDataSource dataSource = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).createDataSource();
//            dataSource.setRequestProperty("Authorization", "");
            return dataSource;
        };

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(this).build();
        // 创建一个默认的 LoadControl
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        60000, // 增加最大缓冲时间到60秒
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        // trackSelector.setParameters(
        //        trackSelector.buildUponParameters().setAllowVideoMixedMimeTypeAdaptiveness(true));

        // 配置期望的视频分辨率为720p
        DefaultTrackSelector.Parameters parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720) // 设置最大分辨率为1280x720
                .setForceHighestSupportedBitrate(true) // 可选，如果你想要最高的比特率轨道
                .build();
        // 应用这些参数到 trackSelector
        trackSelector.setParameters(parameters);

        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).setMediaSourceFactory(mediaSourceFactory).setBandwidthMeter(bandwidthMeter).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);

        for (String uri : VIDEO_URIS) {
            player.addMediaItem(MediaItem.fromUri(uri));
        }
        // Prepare the player.
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    // Active playback.
                } else {
                    // Not playing because playback is paused, ended, suppressed, or the player
                    // is buffering, stopped or failed. Check player.getPlayWhenReady,
                    // player.getPlaybackState, player.getPlaybackSuppressionReason and
                    // player.getPlaybackError for details.
                }
            }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
                // 获取视频的宽度和高度
                // 根据视频的宽度和高度设置 PlayerView 的尺寸
                ViewGroup.LayoutParams params = playerView.getLayoutParams();
                params.width = videoSize.width; //这可能需要根据你的需求进行调整
                params.height = videoSize.height; //这可能需要根据你的需求进行调整
                playerView.setLayoutParams(params);
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);

        ViewGroup.LayoutParams params = playerView.getLayoutParams();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        playerView.setLayoutParams(params);
    }

    @Override
    protected void onDestroy() {
        player.release();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.stop();
        player.release();
    }
}