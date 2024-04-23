package com.example.exo_java_demo;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private SimpleExoPlayer player;
    private Dialog speedDialog;
    private final float[] speeds = new float[]{0.5f, 1f, 1.5f, 2f};
    private float currentSpeed = 1.0f; // 默认播放速度为1.0x

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speedDialog = new Dialog(this);
        speedDialog.setContentView(R.layout.dialog_speed);
        RecyclerView recyclerView = speedDialog.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SpeedAdapter(speeds, speed -> {
            if (player != null) {
                player.setPlaybackParameters(new PlaybackParameters(speed));
                currentSpeed = speed;
                recyclerView.getAdapter().notifyDataSetChanged();
            }
            speedDialog.dismiss();
        }, currentSpeed));

        findViewById(R.id.button).setOnClickListener(v -> speedDialog.show());
        initializePlayer();
    }

    private void initializePlayer() {
        player = new SimpleExoPlayer.Builder(this).build();
        PlayerView playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        Uri videoUri = (Uri.parse("https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"));
        player.addMediaItem(MediaItem.fromUri(videoUri));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
