package com.example.exo_java_demo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    public String[] VIDEO_URIS = {
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.jpg",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PlayerView playerView = findViewById(R.id.playerView);
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        for (String uri : VIDEO_URIS) {
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.addMediaItem(mediaItem);
        }
        // Prepare the player.
        player.prepare();
        // Start the playback.
        player.play();
    }
}