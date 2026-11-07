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

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.service.IMyAidlInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        findViewById(R.id.btn_tracks).setOnClickListener(v -> showTracksDialog());
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

    private void showTracksDialog() {
        List<TrackAdapter.Option> options = buildTrackOptions();
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.tracks_none, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_speed); // 复用单列表布局
        RecyclerView recyclerView = dialog.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new TrackAdapter(options, option -> {
            applyTrackSelection(option);
            dialog.dismiss();
        }));
        dialog.show();
    }

    /**
     * 当前源的可选档位：视频（画质）与音频各带一个「自动」档，
     * 仅列出设备支持的轨道，勾选当前覆盖中的轨道。
     */
    private List<TrackAdapter.Option> buildTrackOptions() {
        List<TrackAdapter.Option> options = new ArrayList<>();
        if (player == null) {
            return options;
        }
        List<TrackAdapter.Option> videoOptions = new ArrayList<>();
        List<TrackAdapter.Option> audioOptions = new ArrayList<>();
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO && group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                List<TrackAdapter.Option> target =
                        group.getType() == C.TRACK_TYPE_VIDEO ? videoOptions : audioOptions;
                target.add(new TrackAdapter.Option(group.getType(), group, i,
                        buildTrackLabel(group.getTrackFormat(i), group.getType()),
                        isOverridden(group, i)));
            }
        }
        if (!videoOptions.isEmpty()) {
            options.add(TrackAdapter.Option.auto(C.TRACK_TYPE_VIDEO,
                    getString(R.string.tracks_video_auto),
                    !hasOverrideOfType(C.TRACK_TYPE_VIDEO)));
            options.addAll(videoOptions);
        }
        if (!audioOptions.isEmpty()) {
            options.add(TrackAdapter.Option.auto(C.TRACK_TYPE_AUDIO,
                    getString(R.string.tracks_audio_auto),
                    !hasOverrideOfType(C.TRACK_TYPE_AUDIO)));
            options.addAll(audioOptions);
        }
        return options;
    }

    /** 轨道显示名：视频用 分辨率 + 码率，音频用 标签/语言 + 声道数。 */
    @OptIn(markerClass = UnstableApi.class) // Format 码率字段属 Media3 opt-in API，仅此展示用途
    private String buildTrackLabel(Format format, int trackType) {
        StringBuilder builder = new StringBuilder();
        if (trackType == C.TRACK_TYPE_VIDEO) {
            if (format.height > 0) {
                builder.append(format.height).append("p");
            }
            if (format.bitrate > Format.NO_VALUE) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(Math.round(format.bitrate / 1000f)).append(" kbps");
            }
        } else {
            if (format.label != null) {
                builder.append(format.label);
            } else if (format.language != null) {
                builder.append(new Locale(format.language).getDisplayLanguage());
            }
            if (format.channelCount > 0) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(format.channelCount).append(" 声道");
            }
        }
        if (builder.length() == 0) {
            builder.append(trackType == C.TRACK_TYPE_VIDEO ? "视频轨" : "音轨");
        }
        return builder.toString();
    }

    private boolean hasOverrideOfType(int trackType) {
        for (TrackSelectionOverride override
                : player.getTrackSelectionParameters().overrides.values()) {
            if (override.getType() == trackType) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverridden(Tracks.Group group, int trackIndex) {
        for (TrackSelectionOverride override
                : player.getTrackSelectionParameters().overrides.values()) {
            if (override.mediaTrackGroup.equals(group.getMediaTrackGroup())
                    && override.trackIndices.contains(trackIndex)) {
                return true;
            }
        }
        return false;
    }

    /** 应用选择：自动 = 清除该类型覆盖；具体轨 = 以 TrackSelectionOverride 固定该轨。 */
    private void applyTrackSelection(TrackAdapter.Option option) {
        TrackSelectionParameters.Builder builder =
                player.getTrackSelectionParameters().buildUpon();
        if (option.isAuto) {
            builder.clearOverridesOfType(option.trackType);
        } else {
            builder.clearOverridesOfType(option.trackType)
                    .setOverrideForType(new TrackSelectionOverride(
                            option.group.getMediaTrackGroup(),
                            Collections.singletonList(option.trackIndex)));
        }
        player.setTrackSelectionParameters(builder.build());
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
