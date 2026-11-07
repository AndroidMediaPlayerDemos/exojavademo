package com.example.exo_java_demo;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
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
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_SEEK = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final int GESTURE_BRIGHTNESS = 3;

    private ExoPlayer player;
    private Dialog speedDialog;
    private final float[] speeds = new float[]{0.5f, 1f, 1.5f, 2f};
    private float currentSpeed = 1.0f; // 默认播放速度为1.0x
    private SourceRepository sourceRepository;

    private PlayerView playerView;
    private View errorPanel;
    private TextView errorText;
    private TextView gestureHint;
    private AudioManager audioManager;
    private GestureDetector gestureDetector;
    private int gestureMode = GESTURE_NONE;
    private long seekTargetMs = C.TIME_UNSET;
    private int volumeAtGestureStart;
    private float brightnessAtGestureStart;

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

        playerView = findViewById(R.id.player_view);
        errorPanel = findViewById(R.id.error_panel);
        errorText = findViewById(R.id.error_text);
        gestureHint = findViewById(R.id.gesture_hint);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        findViewById(R.id.btn_rewind).setOnClickListener(v -> {
            if (player != null) {
                player.seekBack();
            }
        });
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (player != null) {
                player.seekForward();
            }
        });
        findViewById(R.id.btn_retry).setOnClickListener(v -> retryPlayback());
        setupGestureControl();

        initializePlayer();
    }

    @OptIn(markerClass = UnstableApi.class) // seek 步长设置属 Media3 opt-in API
    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setSeekBackIncrementMs(10_000);
        player.setSeekForwardIncrementMs(10_000);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // FR-5/FR-8：源非法或不可达时展示错误面板（重试入口），不再只弹 Toast
                errorText.setText(getString(R.string.source_error, error.getErrorCodeName()));
                errorPanel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // 重新起播（重试或换源）即收起错误面板
                if (playbackState == Player.STATE_BUFFERING
                        || playbackState == Player.STATE_READY) {
                    errorPanel.setVisibility(View.GONE);
                }
            }
        });
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
        errorPanel.setVisibility(View.GONE);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.setPlayWhenReady(true);
        sourceRepository.addToHistory(url);
        Toast.makeText(this, getString(R.string.source_loading, url), Toast.LENGTH_SHORT).show();
    }

    /** FR-8：错误面板「重试」——对当前源重新 prepare 起播。 */
    private void retryPlayback() {
        if (player == null) {
            return;
        }
        errorPanel.setVisibility(View.GONE);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /**
     * FR-8：播放区手势 —— 单击切换控制条；横滑 seek（松手生效）；
     * 左半屏竖滑调亮度、右半屏竖滑调音量（滑动即生效）。
     * 屏幕左右边缘 24dp 内不接管，留给系统返回手势。
     */
    @OptIn(markerClass = UnstableApi.class) // PlayerView 控制条操作属 Media3 opt-in API
    private void setupGestureControl() {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float edgeSlop = 24 * getResources().getDisplayMetrics().density;
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 消费了 PlayerView 的触摸，控制条显隐在这里接管
                if (playerView.isControllerFullyVisible()) {
                    playerView.hideController();
                } else {
                    playerView.showController();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (gestureMode == GESTURE_NONE) {
                    if (Math.abs(dx) < touchSlop * 2 && Math.abs(dy) < touchSlop * 2) {
                        return true; // 未超过阈值，暂不判定方向
                    }
                    if (Math.abs(dx) > Math.abs(dy)) {
                        gestureMode = GESTURE_SEEK;
                    } else if (e1.getX() < playerView.getWidth() / 2f) {
                        gestureMode = GESTURE_BRIGHTNESS;
                        brightnessAtGestureStart = currentBrightness();
                    } else {
                        gestureMode = GESTURE_VOLUME;
                        volumeAtGestureStart =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    }
                }
                switch (gestureMode) {
                    case GESTURE_SEEK:
                        updateSeekByDrag(dx);
                        break;
                    case GESTURE_VOLUME:
                        updateVolumeByDrag(dy);
                        break;
                    case GESTURE_BRIGHTNESS:
                        updateBrightnessByDrag(dy);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        playerView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && (event.getRawX() < edgeSlop
                    || event.getRawX() > getResources().getDisplayMetrics().widthPixels - edgeSlop)) {
                return false; // 边缘区域不接管，避免与系统返回手势冲突
            }
            gestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                finishGesture();
            }
            return true;
        });
    }

    /** 松手收尾：seek 手势在此生效，其余只隐藏提示。 */
    private void finishGesture() {
        if (gestureMode == GESTURE_SEEK && player != null && seekTargetMs != C.TIME_UNSET) {
            player.seekTo(seekTargetMs);
        }
        gestureMode = GESTURE_NONE;
        seekTargetMs = C.TIME_UNSET;
        gestureHint.setVisibility(View.GONE);
    }

    /** 横滑 seek：1px 至少 1s；有时长时按「滑满一屏 ≈ 全片」等比放大。 */
    private void updateSeekByDrag(float dx) {
        if (player == null) {
            return;
        }
        long durationMs = player.getDuration();
        float msPerPx = durationMs > 0
                ? Math.max(1000f, durationMs / Math.max(1, playerView.getWidth()))
                : 1000f;
        long target = (long) (player.getCurrentPosition() + dx * msPerPx);
        if (durationMs > 0) {
            target = Math.min(target, durationMs);
        }
        seekTargetMs = Math.max(0, target);
        gestureHint.setText(getString(R.string.gesture_seek_target, formatPosition(seekTargetMs)));
        gestureHint.setVisibility(View.VISIBLE);
    }

    /** 右半屏竖滑调音量（上滑增大），调节即时生效。 */
    private void updateVolumeByDrag(float dy) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float delta = -dy / Math.max(1, playerView.getHeight()) * max;
        int target = Math.round(Math.min(max, Math.max(0, volumeAtGestureStart + delta)));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        gestureHint.setText(getString(R.string.gesture_volume, Math.round(target * 100f / max)));
        gestureHint.setVisibility(View.VISIBLE);
    }

    /** 左半屏竖滑调亮度（上滑增亮），仅影响本窗口。 */
    private void updateBrightnessByDrag(float dy) {
        float target = Math.min(1f,
                Math.max(0.01f, brightnessAtGestureStart - dy / Math.max(1, playerView.getHeight())));
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = target;
        getWindow().setAttributes(attributes);
        gestureHint.setText(getString(R.string.gesture_brightness, Math.round(target * 100)));
        gestureHint.setVisibility(View.VISIBLE);
    }

    private float currentBrightness() {
        float value = getWindow().getAttributes().screenBrightness;
        return value <= 0 ? 0.5f : value; // -1 = 跟随系统，按中等亮度起步
    }

    private String formatPosition(long positionMs) {
        long totalSeconds = positionMs / 1000;
        return String.format(Locale.getDefault(), "%d:%02d",
                totalSeconds / 60, totalSeconds % 60);
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
