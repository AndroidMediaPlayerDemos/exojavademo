package com.example.exo_java_demo;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

/**
 * android.media.MediaPlayer + SurfaceView 播放演示，
 * 集成自 MediaPlayerWithSurfaceView 工程，与主界面的 ExoPlayer 使用同一视频源
 * （{@link SourceRepository} 默认预置源），便于直观对比两种播放内核的起播速度与画面表现。
 */
public class MediaPlayerActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener {

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediaplayer);

        SurfaceView surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        releaseMediaPlayer();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setDisplay(holder);
        try {
            mediaPlayer.setDataSource(SourceRepository.PRESETS.get(0).url);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 网络流必须先注册监听再异步准备，避免 prepare() 在主线程阻塞引发 ANR
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                // 源不可达（如 HTTP 4xx/5xx）在异步准备阶段以错误回调到达
                Toast.makeText(MediaPlayerActivity.this,
                        getString(R.string.mediaplayer_error, what, extra),
                        Toast.LENGTH_LONG).show();
                releaseMediaPlayer();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(this, R.string.mediaplayer_invalid, Toast.LENGTH_LONG).show();
            releaseMediaPlayer();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        releaseMediaPlayer();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
