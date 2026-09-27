package cn.ottohub.oh2013;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

/**
 * 关于我们：展示 https://ulic.cc/oh2013/about.html
 */
public class AboutFragment extends Fragment {

    private static final String ABOUT_URL = "https://ulic.cc/oh2013/about.html";
    private WebView webView;

    // ===== 遥控器：确认键刷新，方向键滚动 =====
    private boolean mKeyNavActive = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFFFFFFF);

        webView = new WebView(getActivity());
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
        return root;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        super.onDestroyView();
    }

    /** 供 MainActivity.dispatchKeyEvent：方向键滚动页面，确认键刷新。 */
    public boolean handleRemoteKey(android.view.KeyEvent event) {
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = cn.ottohub.oh2013.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_UP
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_DOWN
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_CONFIRM) {
            return false;
        }
        if (!mKeyNavActive) {
            mKeyNavActive = true;
        }
        if (event.getRepeatCount() != 0) {
            return true;
        }
        if (webView == null) return true;
        if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_UP) {
            webView.scrollBy(0, -120);
            return true;
        } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_DOWN) {
            webView.scrollBy(0, 120);
            return true;
        } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_CONFIRM) {
            cn.ottohub.oh2013.util.LegacyAboutPageLoader.load(webView, ABOUT_URL);
            return true;
        }
        return true;
    }
}
