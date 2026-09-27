package cn.ottohub.oh2013;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

/**
 * 关于/帮助页统一展示 https://ulic.cc/oh2013/about.html
 */
public class AboutActivity extends BaseActivity {

    private static final String ABOUT_URL = "https://ulic.cc/oh2013/about.html";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFFFFFFF);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        try {
            settings.setJavaScriptEnabled(true);
        } catch (Throwable ignored) {
        }
        try {
            settings.setDomStorageEnabled(true);
        } catch (Throwable ignored) {
        }
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        // 老 WebView 直连 HTTPS 常因 TLS 失败白屏；走 NetWorkUtil 拉页再注入
        cn.ottohub.oh2013.util.LegacyAboutPageLoader.load(webView, ABOUT_URL);
        root.addView(webView);
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }
}

