package com.example.exo_java_demo;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.service.IMyAidlInterface;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private SimpleExoPlayer player;
    private Dialog speedDialog;
    private final float[] speeds = new float[]{0.5f, 1f, 1.5f, 2f};
    private float currentSpeed = 1.0f; // 默认播放速度为1.0x

    private IMyAidlInterface myService;
    private boolean serviceBound = false;
    private TextView aidlStatus;

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myService = IMyAidlInterface.Stub.asInterface(service);
            try {
                int result = myService.add(5, 6L);
                aidlStatus.setText(getString(R.string.aidl_result, result));
            } catch (RemoteException e) {
                aidlStatus.setText(getString(R.string.aidl_error, e.getMessage()));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            myService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aidlStatus = findViewById(R.id.aidl_status);
        // AIDL 客户端：默认绑定本应用内置的 MyService（自托管服务端）。
        // 如需恢复对 MediaPlayerWithSurfaceView 外部应用的跨应用绑定，改用：
        //   Intent intent = new Intent();
        //   intent.setPackage("com.example.mediaplayerwithsurfaceview");
        // manifest 的 <queries> 已为该场景声明包可见性。
        Intent intent = new Intent(this, MyService.class);
        serviceBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (!serviceBound) {
            aidlStatus.setText(R.string.aidl_bind_failed);
        }

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
        findViewById(R.id.btn_mediaplayer).setOnClickListener(v ->
                startActivity(new Intent(this, MediaPlayerActivity.class)));
        findViewById(R.id.btn_sharepoint).setOnClickListener(v ->
                startActivity(new Intent(this, WebViewActivity.class)));
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
        if (serviceBound) {
            unbindService(mConnection);
            serviceBound = false;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
