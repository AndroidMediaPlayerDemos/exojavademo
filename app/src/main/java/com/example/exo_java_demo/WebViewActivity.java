package com.example.exo_java_demo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * SharePoint 认证实验页，集成自 MediaPlayerWithSurfaceView 工程的 WebView 部分
 * （原工程中 rtFa / FedAuth Cookie 提取逻辑为注释掉的实验代码，此处将其转为可运行验证）。
 *
 * 在内嵌 WebView 中打开企业 SharePoint 站点并完成登录，页面加载后检查 Cookie 中
 * 是否出现 rtFa / FedAuth（SharePoint 认证 Cookie），为后续携带凭据播放企业视频
 * （PRD FR-7）验证前置条件。界面只报告 Cookie 是否存在，不显示 Cookie 内容。
 */
public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private TextView tvCookieStatus;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        webView = findViewById(R.id.webView);
        tvCookieStatus = findViewById(R.id.tvCookieStatus);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                checkAuthCookies(url);
            }
        });

        findViewById(R.id.btnLoad).setOnClickListener(v -> {
            String url = ((EditText) findViewById(R.id.etUrl)).getText().toString().trim();
            if (!url.isEmpty()) {
                tvCookieStatus.setText(R.string.cookie_checking);
                webView.loadUrl(url);
            }
        });
    }

    /** 只报告认证 Cookie 是否存在，凭据不进界面、不进日志。 */
    private void checkAuthCookies(String url) {
        String cookies = CookieManager.getInstance().getCookie(url);
        boolean hasRtFa = cookies != null && cookies.contains("rtFa=");
        boolean hasFedAuth = cookies != null && cookies.contains("FedAuth=");
        if (hasRtFa || hasFedAuth) {
            tvCookieStatus.setText(getString(R.string.cookie_found, hasRtFa, hasFedAuth));
        } else {
            tvCookieStatus.setText(R.string.cookie_missing);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }
}
