package cn.ottohub.oh2013.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * OTTOhub 登录 — 严格对齐 auth_api.md
 *
 * POST /api/auth/login
 * Body: uid_email + pw （JSON 或 form 均可）
 * 成功: { "status":"success", "data":{ "uid", "token", "avatar_url", ... } }
 *
 * 此前「登录响应缺少 data」的真正原因：
 * 1) 走通用 NetWorkUtil.post，302 时 Content-Type 被改成 form，服务端收到空/错 body，
 *    偶发返回 status=success 但没有 data；
 * 2) 只认 data 为 JSONObject，忽略 JSON null / 字符串 / 根级平铺；
 * 3) 附带 B 站 Cookie / 错误 Origin 干扰。
 *
 * 本类独立建连，不经过 NetWorkUtil.post。
 */
public class OttoAuthApi {

    private static final String TAG = "OttoAuthApi";
    private static final String LOGIN_PATH = "/api/auth/login";

    public static final int LOGIN_OK = 0;
    public static final int LOGIN_FAIL = -1;
    public static final int LOGIN_NETWORK = -2;

    private static String sLastErrorMessage = "";

    public static String getLastErrorMessage() {
        return sLastErrorMessage != null ? sLastErrorMessage : "";
    }

    public static int login(String uidEmail, String password) {
        sLastErrorMessage = "";
        if (uidEmail == null) uidEmail = "";
        uidEmail = uidEmail.trim();
        if (password == null) password = "";
        if (uidEmail.length() == 0 || password.length() == 0) {
            sLastErrorMessage = "请输入账号和密码";
            return LOGIN_FAIL;
        }

        try {
            // 1) JSON（文档首选）
            JSONObject result = tryLoginJson(uidEmail, password);
            // 2) form-urlencoded 兜底
            if (!isOk(result)) {
                JSONObject formResult = tryLoginForm(uidEmail, password);
                if (isOk(formResult) || result == null) {
                    result = formResult;
                }
            }

            if (result == null) {
                sLastErrorMessage = "服务器无响应";
                return LOGIN_FAIL;
            }

            String status = result.optString("status", "");
            if (!"success".equalsIgnoreCase(status)) {
                sLastErrorMessage = mapError(result);
                return LOGIN_FAIL;
            }

            JSONObject data = extractData(result);
            if (data == null) {
                Log.e(TAG, "success but no data, keys=" + result.toString());
                sLastErrorMessage = "登录响应缺少 data";
                return LOGIN_FAIL;
            }

            String token = firstString(data, new String[]{"token", "access_token", "auth_token"});
            if (token.length() == 0) {
                sLastErrorMessage = "登录成功但未返回 token";
                return LOGIN_FAIL;
            }

            long uid = OttoApiUtil.parseLong(data, "uid");
            if (uid == 0) uid = OttoApiUtil.parseLong(data, "id");
            String username = firstString(data, new String[]{"username", "name", "email", "uname"});
            if (username.length() == 0) username = "用户" + uid;

            SharedPreferencesUtil.putString(SharedPreferencesUtil.OTTO_TOKEN, token);
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, uid);
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.OTTO_UID, uid);
            SharedPreferencesUtil.putString("uname", username);
            SharedPreferencesUtil.putString("avatar", firstString(data, new String[]{"avatar_url", "avatar"}));
            SharedPreferencesUtil.putString("cover", firstString(data, new String[]{"cover_url", "cover"}));
            try {
                android.content.Context ctx = cn.ottohub.oh2013.BaseActivity.getAppContext();
                if (ctx != null) {
                    cn.ottohub.oh2013.util.ImNotifyService.sync(ctx);
                }
            } catch (Exception ignored) {
            }
            return LOGIN_OK;
        } catch (IOException e) {
            Log.e(TAG, "login network", e);
            sLastErrorMessage = "网络错误，请重试";
            return LOGIN_NETWORK;
        } catch (Exception e) {
            Log.e(TAG, "login fail", e);
            sLastErrorMessage = e.getMessage() != null ? e.getMessage() : "登录失败";
            return LOGIN_FAIL;
        }
    }

    private static boolean isOk(JSONObject r) {
        return r != null && "success".equalsIgnoreCase(r.optString("status", ""));
    }

    private static JSONObject tryLoginJson(String uidEmail, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("uid_email", uidEmail);
            body.put("pw", password);
        } catch (JSONException e) {
            return null;
        }
        String raw = postRaw(ApiConfig.BASE_URL + LOGIN_PATH, body.toString(), "application/json");
        return parseQuiet(raw);
    }

    private static JSONObject tryLoginForm(String uidEmail, String password) throws IOException {
        String form;
        try {
            form = "uid_email=" + URLEncoder.encode(uidEmail, "UTF-8")
                    + "&pw=" + URLEncoder.encode(password, "UTF-8");
        } catch (Exception e) {
            form = "uid_email=" + uidEmail + "&pw=" + password;
        }
        String raw = postRaw(ApiConfig.BASE_URL + LOGIN_PATH, form, "application/x-www-form-urlencoded");
        return parseQuiet(raw);
    }

    private static JSONObject parseQuiet(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.length() == 0) return null;
        if (t.charAt(0) == '\uFEFF') t = t.substring(1).trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            Log.e(TAG, "non-json: " + (t.length() > 100 ? t.substring(0, 100) : t));
            return null;
        }
        try {
            Object o = new JSONTokener(t).nextValue();
            if (o instanceof JSONObject) return (JSONObject) o;
        } catch (JSONException e) {
            Log.e(TAG, "parse: " + e.getMessage());
        }
        return null;
    }

    /**
     * 取出 data：
     * - 正常 JSONObject
     * - JSON 字符串
     * - 根上直接带 token（兼容）
     */
    private static JSONObject extractData(JSONObject result) {
        if (result == null) return null;
        if (!result.isNull("data")) {
            Object raw = result.opt("data");
            if (raw instanceof JSONObject) {
                return (JSONObject) raw;
            }
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                if (s.startsWith("{")) {
                    try {
                        return new JSONObject(s);
                    } catch (JSONException ignored) {
                    }
                }
            }
        }
        if (result.has("token") || result.has("uid")) {
            return result;
        }
        // 有的网关包一层 result
        if (!result.isNull("result")) {
            Object r = result.opt("result");
            if (r instanceof JSONObject) {
                JSONObject inner = (JSONObject) r;
                if (inner.has("token") || inner.has("data")) {
                    if (!inner.isNull("data") && inner.opt("data") instanceof JSONObject) {
                        return (JSONObject) inner.opt("data");
                    }
                    return inner;
                }
            }
        }
        return null;
    }

    private static String firstString(JSONObject o, String[] keys) {
        for (int i = 0; i < keys.length; i++) {
            String v = o.optString(keys[i], "");
            if (v != null && v.length() > 0 && !"null".equals(v)) return v;
        }
        return "";
    }

    private static String postRaw(String urlStr, String body, String contentType) throws IOException {
        String current = urlStr;
        byte[] payload = body.getBytes("UTF-8");
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection conn = null;
            try {
                conn = NetWorkUtil.openCompat(current);
                if (conn instanceof HttpsURLConnection) {
                    NetWorkUtil.applySSLCompat(conn, current);
                }
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(false);
                // 不加 charset，部分 PHP 对 Content-Type 解析挑剔
                conn.setRequestProperty("Content-Type", contentType);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
                // 不带 Cookie / Origin，避免干扰

                OutputStream os = conn.getOutputStream();
                os.write(payload);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.length() == 0) {
                        throw new IOException("重定向无 Location");
                    }
                    if (loc.startsWith("/")) {
                        URL base = new URL(current);
                        loc = base.getProtocol() + "://" + base.getHost()
                                + (base.getPort() > 0 ? (":" + base.getPort()) : "") + loc;
                    }
                    // 始终带着原 body 再 POST（等价 307）
                    current = loc;
                    continue;
                }

                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (is == null) return "";
                String enc = conn.getContentEncoding();
                if (enc != null && enc.toLowerCase().indexOf("gzip") >= 0) {
                    is = new GZIPInputStream(is);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                is.close();
                byte[] all = bos.toByteArray();
                if (all.length >= 2 && (all[0] & 0xFF) == 0x1F && (all[1] & 0xFF) == 0x8B) {
                    GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(all));
                    bos = new ByteArrayOutputStream();
                    while ((n = gis.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    gis.close();
                    all = bos.toByteArray();
                }
                String text = new String(all, "UTF-8");
                Log.d(TAG, "login HTTP " + code + " body=" + (text.length() > 200 ? text.substring(0, 200) : text));
                return text;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw new IOException("重定向过多");
    }

    private static String mapError(JSONObject result) {
        String message = result.optString("message", "");
        if ("error_password".equals(message)) return "账号或密码错误";
        if ("missing_argument".equals(message)) return "请输入账号和密码";
        if ("too_many_requests".equals(message)) return "请求过于频繁，请稍后再试";
        if (message.length() > 0) return message;
        return "登录失败，请检查账号或密码";
    }

    public static void logout() {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.OTTO_TOKEN, "");
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 0);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.OTTO_UID, 0);
        SharedPreferencesUtil.putString("uname", "");
        SharedPreferencesUtil.putString("avatar", "");
        SharedPreferencesUtil.putString("cover", "");
        ChatApi.clearChatToken();
        try {
            android.content.Context ctx = cn.ottohub.oh2013.BaseActivity.getAppContext();
            if (ctx != null) {
                cn.ottohub.oh2013.util.ImNotifyService.sync(ctx);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public static boolean isLoggedIn() {
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        return token != null && token.length() > 0;
    }

    /** 当前登录用户 Otto uid（优先 otto_uid，兼容旧版仅写 mid） */
    public static long getSelfUid() {
        long uid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.OTTO_UID, 0);
        if (uid > 0) return uid;
        return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }
}
