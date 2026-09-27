package cn.ottohub.oh2013.util;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

/**
 * 关于页加载：老 WebView（如 Android 4.1）常无法完成现代 HTTPS 握手。
 * 用 NetWorkUtil（TLS 兼容栈）拉 HTML，再 loadDataWithBaseURL，绕开 WebView SSL。
 * 图片同样内嵌为 data URI，避免页面能开但图标全挂。
 */
public final class LegacyAboutPageLoader {

    private LegacyAboutPageLoader() {
    }

    public static void load(final WebView webView, final String aboutUrl) {
        if (webView == null || aboutUrl == null || aboutUrl.length() == 0) {
            return;
        }
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                String html = null;
                try {
                    html = NetWorkUtil.get(aboutUrl);
                } catch (Throwable t) {
                    try {
                        String http = ImageUrlUtil.toHttpFallback(aboutUrl);
                        if (http != null && !http.equals(aboutUrl)) {
                            html = NetWorkUtil.get(http);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (html != null && html.length() > 0) {
                    html = inlineHttpsImages(html);
                }
                final String finalHtml = html;
                main.post(new Runnable() {
                    public void run() {
                        try {
                            if (finalHtml != null && finalHtml.length() > 0) {
                                webView.loadDataWithBaseURL(aboutUrl, finalHtml,
                                        "text/html", "UTF-8", null);
                            } else {
                                // 兜底：新机 WebView 仍可直连
                                webView.loadUrl(aboutUrl);
                            }
                        } catch (Throwable t) {
                            try {
                                webView.loadUrl(aboutUrl);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
            }
        }, "AboutPageLoader").start();
    }

    /** 把 https 图片拉下来改成 data URI，避免老 WebView 二次 HTTPS 失败。 */
    private static String inlineHttpsImages(String html) {
        if (html == null) return null;
        StringBuilder out = new StringBuilder(html.length() + 256);
        int i = 0;
        while (i < html.length()) {
            int src = indexOfIgnoreCase(html, "src=\"https://", i);
            if (src < 0) {
                out.append(html.substring(i));
                break;
            }
            out.append(html.substring(i, src));
            int urlStart = src + 5; // after src="
            int urlEnd = html.indexOf('"', urlStart);
            if (urlEnd < 0) {
                out.append(html.substring(src));
                break;
            }
            String imgUrl = html.substring(urlStart, urlEnd);
            String dataUri = fetchAsDataUri(imgUrl);
            if (dataUri != null) {
                out.append("src=\"").append(dataUri).append('"');
            } else {
                out.append(html.substring(src, urlEnd + 1));
            }
            i = urlEnd + 1;
        }
        return out.toString();
    }

    private static int indexOfIgnoreCase(String hay, String needle, int from) {
        int nlen = needle.length();
        for (int p = from; p + nlen <= hay.length(); p++) {
            if (hay.regionMatches(true, p, needle, 0, nlen)) {
                return p;
            }
        }
        return -1;
    }

    private static String fetchAsDataUri(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = NetWorkUtil.openCompat(url);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            String mime = conn.getContentType();
            if (mime == null || mime.length() == 0) {
                mime = "image/png";
            } else {
                int semi = mime.indexOf(';');
                if (semi > 0) mime = mime.substring(0, semi).trim();
            }
            byte[] raw = NetWorkUtil.readStream(conn.getInputStream());
            if (raw == null || raw.length == 0 || raw.length > 512 * 1024) {
                return null;
            }
            return "data:" + mime + ";base64," + base64Encode(raw);
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    /** minSdk 3：不依赖 android.util.Base64（API 8+） */
    private static String base64Encode(byte[] data) {
        final char[] table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? (data[i++] & 0xFF) : -1;
            int b2 = i < data.length ? (data[i++] & 0xFF) : -1;
            sb.append(table[b0 >> 2]);
            sb.append(table[((b0 & 0x3) << 4) | (b1 >= 0 ? (b1 >> 4) : 0)]);
            sb.append(b1 >= 0 ? table[((b1 & 0xF) << 2) | (b2 >= 0 ? (b2 >> 6) : 0)] : '=');
            sb.append(b2 >= 0 ? table[b2 & 0x3F] : '=');
        }
        return sb.toString();
    }
}
