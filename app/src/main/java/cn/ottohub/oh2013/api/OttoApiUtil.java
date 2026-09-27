package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
import cn.ottohub.oh2013.util.StringUtil;

/**
 * OTTOhub API 通用工具：请求头、分页、视频卡片解析。
 */
public final class OttoApiUtil {

    private OttoApiUtil() {
    }

    public interface OttoCall<T> {
        T run() throws IOException, JSONException;
    }

    public static boolean isSuccess(JSONObject result) {
        return result != null && "success".equals(result.optString("status"));
    }

    public static long parseLong(JSONObject obj, String key) {
        if (obj == null || !obj.has(key)) return 0;
        Object val = obj.opt(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        String s = String.valueOf(val);
        if (s == null || s.length() == 0 || "null".equals(s)) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int parseInt(JSONObject obj, String key) {
        return (int) parseLong(obj, key);
    }

    public static ArrayList<String> buildHeaders() {
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        headers.add("Accept");
        headers.add("application/json, text/plain, */*");
        headers.add("Accept-Language");
        headers.add("zh-CN,zh;q=0.9,en;q=0.8");
        headers.add("Referer");
        headers.add(ApiConfig.SITE_URL);
        headers.add("Origin");
        headers.add(ApiConfig.SITE_URL.replaceAll("/$", ""));
        return headers;
    }

    public static String appendToken(String url) {
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        if (token == null || token.length() == 0) return url;
        try {
            token = java.net.URLEncoder.encode(token, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 always available
        }
        if (url.indexOf('?') >= 0) {
            return url + "&token=" + token;
        }
        return url + "?token=" + token;
    }

    private static <T> T callOtto(OttoCall<T> call) throws IOException, JSONException {
        NetWorkUtil.setSkipAutoCookie(true);
        try {
            return call.run();
        } finally {
            NetWorkUtil.setSkipAutoCookie(false);
        }
    }

    public static JSONObject getJson(final String path) throws IOException, JSONException {
        return callOtto(new OttoCall<JSONObject>() {
            public JSONObject run() throws IOException, JSONException {
                return NetWorkUtil.getJson(ApiConfig.BASE_URL + path, buildHeaders());
            }
        });
    }

    public static JSONObject getJsonWithToken(final String path) throws IOException, JSONException {
        return callOtto(new OttoCall<JSONObject>() {
            public JSONObject run() throws IOException, JSONException {
                return NetWorkUtil.getJson(appendToken(ApiConfig.BASE_URL + path), buildHeaders());
            }
        });
    }

    public static JSONObject postJson(final String path, final JSONObject body) throws IOException, JSONException {
        return callOtto(new OttoCall<JSONObject>() {
            public JSONObject run() throws IOException, JSONException {
                final String url = ApiConfig.BASE_URL + path;
                String resp = NetWorkUtil.postJson(url, body.toString(), buildHeaders());
                if (resp == null || resp.length() == 0) return new JSONObject();
                return NetWorkUtil.parseJsonString(resp, url);
            }
        });
    }

    public static JSONObject postForm(final String path, final String formBody) throws IOException, JSONException {
        return callOtto(new OttoCall<JSONObject>() {
            public JSONObject run() throws IOException, JSONException {
                final String url = ApiConfig.BASE_URL + path;
                String resp = NetWorkUtil.post(url, formBody, buildHeaders(), "application/x-www-form-urlencoded");
                if (resp == null || resp.length() == 0) return new JSONObject();
                return NetWorkUtil.parseJsonString(resp, url);
            }
        });
    }

    public static JSONObject postJsonWithToken(String path, JSONObject body) throws IOException, JSONException {
        if (body == null) body = new JSONObject();
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        if (token != null && token.length() > 0) {
            body.put("token", token);
        }
        return postJson(path, body);
    }

    public static JSONObject deleteJsonWithToken(final String path) throws IOException, JSONException {
        return callOtto(new OttoCall<JSONObject>() {
            public JSONObject run() throws IOException, JSONException {
                JSONObject body = new JSONObject();
                String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
                if (token != null && token.length() > 0) {
                    body.put("token", token);
                }
                final String url = ApiConfig.BASE_URL + path;
                String resp = NetWorkUtil.deleteJson(url, body.toString(), buildHeaders());
                if (resp == null || resp.length() == 0) return new JSONObject();
                return NetWorkUtil.parseJsonString(resp, url);
            }
        });
    }

    public static VideoCard parseVideoCard(JSONObject item) throws JSONException {
        long vid = parseLong(item, "vid");
        String title = item.optString("title", "");
        String upName = item.optString("username", "");
        long views = parseLong(item, "view_count");
        String cover = item.optString("cover_url", "");
        int danmaku = parseInt(item, "danmaku_count");
        if (danmaku <= 0) danmaku = parseInt(item, "danmaku_num");
        if (danmaku <= 0) danmaku = parseInt(item, "comment_count");
        VideoCard card = new VideoCard(title, upName, StringUtil.toWan(views), cover, vid, String.valueOf(vid));
        card.cid = vid;
        card.danmaku = danmaku;
        return card;
    }

    public static void parseVideoList(JSONArray list, List<VideoCard> out) throws JSONException {
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            out.add(parseVideoCard(list.getJSONObject(i)));
        }
    }

    public static JSONArray getVideoListArray(JSONObject result) throws JSONException {
        if (!isSuccess(result)) return null;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return null;
        return data.optJSONArray("video_list");
    }
}
