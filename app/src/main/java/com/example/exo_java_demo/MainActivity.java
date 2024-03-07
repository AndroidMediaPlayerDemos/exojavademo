package com.example.exo_java_demo;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    public String[] VIDEO_URIS = {"https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.jpg", "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"};

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

        PlayerView playerView = findViewById(R.id.playerView);
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        for (String uri : VIDEO_URIS) {
            MediaItem mediaItem = MediaItem.fromUri(uri);
            ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(defaultSourceFactory)
                    .createMediaSource(mediaItem);
            player.setMediaSource(mediaSource);
        }
        // Prepare the player.
        player.prepare();
        // Start the playback.
        player.play();
    }
}