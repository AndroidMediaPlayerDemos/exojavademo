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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.service.IMyAidlInterface;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private Dialog speedDialog;
    private final float[] speeds = new float[]{0.5f, 1f, 1.5f, 2f};
    private float currentSpeed = 1.0f; // 默认播放速度为1.0x
    private SourceRepository sourceRepository;

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
        findViewById(R.id.btn_source).setOnClickListener(v -> showSourceDialog());
        findViewById(R.id.btn_mediaplayer).setOnClickListener(v ->
                startActivity(new Intent(this, MediaPlayerActivity.class)));
        findViewById(R.id.btn_sharepoint).setOnClickListener(v ->
                startActivity(new Intent(this, WebViewActivity.class)));
        sourceRepository = new SourceRepository(this);
        initializePlayer();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // FR-5：源非法或不可达时给出用户可见反馈（默认重试耗尽后触发）
                Toast.makeText(MainActivity.this,
                        getString(R.string.source_error, error.getErrorCodeName()),
                        Toast.LENGTH_LONG).show();
            }
        });
        PlayerView playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        // 默认源：预置列表第一条（原 TearsOfSteel 源 2026-11 起整桶 403，已更换）
        player.addMediaItem(MediaItem.fromUri(Uri.parse(sourceRepository.defaultUrl())));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void showSourceDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_source);
        EditText input = dialog.findViewById(R.id.source_input);
        SourceAdapter adapter = new SourceAdapter(buildSourceRows(), this::loadSource);
        RecyclerView list = dialog.findViewById(R.id.source_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        dialog.findViewById(R.id.btn_load_source).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                loadSource(url);
                dialog.dismiss();
            }
        });
        dialog.findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            sourceRepository.clearHistory();
            adapter.updateRows(buildSourceRows());
        });
        dialog.show();
    }

    private List<SourceAdapter.Row> buildSourceRows() {
        List<SourceAdapter.Row> rows = new ArrayList<>();
        rows.add(SourceAdapter.Row.header(getString(R.string.source_section_presets)));
        for (SourceRepository.Preset preset : SourceRepository.PRESETS) {
            rows.add(SourceAdapter.Row.item(getString(preset.labelRes), preset.url));
        }
        List<String> history = sourceRepository.getHistory();
        if (!history.isEmpty()) {
            rows.add(SourceAdapter.Row.header(getString(R.string.source_section_history)));
            for (String url : history) {
                rows.add(SourceAdapter.Row.item(url, url));
            }
        }
        return rows;
    }

    private void loadSource(String url) {
        if (player == null) {
            return;
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.setPlayWhenReady(true);
        sourceRepository.addToHistory(url);
        Toast.makeText(this, getString(R.string.source_loading, url), Toast.LENGTH_SHORT).show();
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
