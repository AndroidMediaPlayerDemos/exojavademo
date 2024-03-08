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
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    public String[] VIDEO_URIS = {"https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.jpg", "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"};
    private PlayerView playerView;
    private ExoPlayer player;

    private DefaultTrackSelector trackSelector;


    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApplicationInfo applicationInfo;
        String applicationName;
        try {
            applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), 0);
            applicationName = (String) getPackageManager().getApplicationLabel(applicationInfo);

        } catch (PackageManager.NameNotFoundException e) {
            applicationName = "com.example.exo_java_demo";
        }

        setContentView(R.layout.activity_main);
        HttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().setUserAgent(Util.getUserAgent(this, applicationName)).setTransferListener(new DefaultBandwidthMeter.Builder(this).build());

        // 创建 DefaultDataSourceFactory
        DefaultDataSource.Factory defaultSourceFactory = new DefaultDataSource.Factory(this, httpDataSourceFactory);

        trackSelector = new DefaultTrackSelector(this);
        playerView = findViewById(R.id.player_view);
        player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);

        for (String uri : VIDEO_URIS) {
            MediaItem mediaItem = MediaItem.fromUri(uri);
            ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(defaultSourceFactory)
                    .createMediaSource(mediaItem);
            player.setMediaSource(mediaSource);
        }
        // Prepare the player.
        player.prepare();

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
        // Start the playback.
        player.play();
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
        super.onDestroy();
        super.onDestroy();
        player.release();
    }
}