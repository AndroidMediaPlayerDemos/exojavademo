package com.example.exo_java_demo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.example.service.IMyAidlInterface;

/**
 * AIDL 服务端，集成自 MediaPlayerWithSurfaceView 工程的 MyService。
 *
 * 原工程需与 exojavademo 分装两台应用才能验证跨进程通信；集成后本应用
 * 既是服务端也是客户端，可独立完成绑定验证。跨应用绑定仍可用：
 * manifest 已为本应用声明导出的服务 Action，外部应用按该 Action 绑定即可。
 */
public class MyService extends Service {

    private final IMyAidlInterface.Stub binder = new IMyAidlInterface.Stub() {
        @Override
        public int add(int anInt, long aLong) {
            return (int) (anInt + aLong);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
