package cn.ottohub.oh2013.util;

import java.util.Locale;
import java.util.Random;
import java.security.SecureRandom;

/**
 * OH2013：不再向 B 站拉取 buvid/ticket，仅生成本地占位 Cookie 字段。
 */
public class CookieGenerator {
    private static final String CHARSET = "0123456789ABCDEF";
    private static final int[] PCK = {8, 4, 4, 4, 12};
    private static final String[] MP = {"1","2","3","4","5","6","7","8","9","A","B","C","D","E","F","10"};

    private static volatile boolean isEnsuringCookies = false;

    public static void ensureCookies() {
        if (isEnsuringCookies) return;
        isEnsuringCookies = true;
        try {
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.UUID, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.UUID, genUuidInfoc());
            }
            if (SharedPreferencesUtil.getString(SharedPreferencesUtil.B_LSID, "").length() == 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.B_LSID, genBlsid());
            }
        } finally {
            isEnsuringCookies = false;
        }
    }

    public static String getCookieString(boolean forVideoQuality) {
        StringBuilder sb = new StringBuilder();

        boolean incognitoMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.INCOGNITO_MODE, false);
        if (!incognitoMode || forVideoQuality) {
            String loggedCookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            if (loggedCookie != null && loggedCookie.length() > 0) {
                sb.append(loggedCookie);
            }
        }

        // OTTOhub token 可附带在 cookie 字符串中供外置播放器参考（多数忽略）
        String otto = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        if (otto != null && otto.length() > 0) {
            appendCookie(sb, "otto_token", otto);
        }
        appendCookie(sb, "_uuid", SharedPreferencesUtil.getString(SharedPreferencesUtil.UUID, ""));
        appendCookie(sb, "b_lsid", SharedPreferencesUtil.getString(SharedPreferencesUtil.B_LSID, ""));

        return sb.toString();
    }

    private static void appendCookie(StringBuilder sb, String name, String value) {
        if (value == null || value.length() == 0) return;
        if (sb.length() > 0 && !sb.toString().endsWith("; ")) {
            sb.append("; ");
        }
        sb.append(name).append("=").append(value);
    }

    private static String genBlsid() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString() + "_" + Long.toHexString(System.currentTimeMillis()).toUpperCase(Locale.getDefault());
    }

    private static String genUuidInfoc() {
        long t = System.currentTimeMillis() % 100000;
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int len : PCK) {
            for (int i = 0; i < len; i++) {
                sb.append(MP[random.nextInt(16)]);
            }
            sb.append("-");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(String.format(Locale.getDefault(), "%05d", t)).append("infoc");
        return sb.toString();
    }
}
