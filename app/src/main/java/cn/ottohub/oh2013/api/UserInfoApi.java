package cn.ottohub.oh2013.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.UserInfo;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
// OttoAuthApi 同包

// BlogApi 同包引用 getUserBlogs

public class UserInfoApi {

    private static final String TAG = "UserInfoApi";

    public static UserInfo getUserInfo(long mid) throws IOException, JSONException {
        UserInfo info = new UserInfo();
        info.mid = mid;
        info.name = "用户" + mid;
        info.avatar = "";
        info.sign = "";
        info.fans = 0;
        info.following = 0;
        info.followed = false;
        info.level = 0;
        info.honours = new ArrayList<String>();

        JSONObject userResp = OttoApiUtil.getJson("/api/user/" + mid);
        if (OttoApiUtil.isSuccess(userResp)) {
            JSONObject data = userResp.getJSONObject("data");
            info.name = data.optString("username", info.name);
            info.avatar = data.optString("avatar_url", "");
            info.sign = data.optString("intro", "");
            info.coverUrl = data.optString("cover_url", "");
            info.sex = data.optString("sex", "");
            if (info.sex.length() == 0) {
                info.sex = data.optString("gender", "");
            }
            info.registerTime = data.optString("time", "");
            if (info.registerTime.length() == 0) {
                info.registerTime = data.optString("register_time", "");
            }
            if (info.registerTime.length() == 0) {
                info.registerTime = data.optString("created_at", "");
            }
            info.fans = (int) OttoApiUtil.parseLong(data, "fans_count");
            info.following = (int) OttoApiUtil.parseLong(data, "followings_count");
            long exp = OttoApiUtil.parseLong(data, "experience");
            info.level = levelFromExperience(exp);
            info.experience = exp;
            String honour = data.optString("honour", "");
            if (honour.length() > 0) {
                String[] parts = honour.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String h = parts[i].trim();
                    if (h.length() > 0) info.honours.add(h);
                }
            }
            // 用户资料接口偶发带错关注字段；以专用 status 接口为准
            info.followed = false;
        }

        try {
            if (OttoAuthApi.isLoggedIn()) {
                JSONObject followResp = OttoApiUtil.getJsonWithToken("/api/following/status/" + mid);
                if (OttoApiUtil.isSuccess(followResp)) {
                    int status = parseFollowStatus(followResp);
                    if (status >= 0) {
                        info.followed = status == 1;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "follow status: " + e.getMessage());
        }
        return info;
    }

    public static int levelFromExperience(long exp) {
        if (exp >= 80000) return 8;
        if (exp >= 30000) return 7;
        if (exp >= 15000) return 6;
        if (exp >= 8000) return 5;
        if (exp >= 3000) return 4;
        if (exp >= 1000) return 3;
        if (exp >= 500) return 2;
        if (exp >= 50) return 1;
        return 0;
    }

    /** OttoHub 等级名：ZERO~OTTO */
    public static String levelNameFromExperience(long exp) {
        switch (levelFromExperience(exp)) {
            case 1: return "UNO";
            case 2: return "DUE";
            case 3: return "TRE";
            case 4: return "QUATTRO";
            case 5: return "CINQUE";
            case 6: return "SEI";
            case 7: return "SETTE";
            case 8: return "OTTO";
            default: return "ZERO";
        }
    }

    public static int getUserBlogs(long mid, int page, List<cn.ottohub.oh2013.model.BlogItem> blogList)
            throws IOException, JSONException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        String path = "/api/blog/users/" + mid + "/blogs?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJson(path);
        if (!OttoApiUtil.isSuccess(result)) return -1;
        org.json.JSONArray list = BlogApi.getBlogListArray(result);
        if (list == null || list.length() == 0) return 1;
        for (int i = 0; i < list.length(); i++) {
            blogList.add(BlogApi.parseBlog(list.getJSONObject(i)));
        }
        return list.length() < ApiConfig.PAGE_SIZE ? 1 : 0;
    }

    public static UserInfo getCurrentUserInfo() throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0 || !OttoAuthApi.isLoggedIn()) {
            return new UserInfo(0, "未登录", "", "", 0, 0, 0, false, "", 0, "", 0);
        }
        return getUserInfo(mid);
    }

    public static int getCurrentUserCoin() {
        return 0;
    }

    public static int getUserVideos(long mid, int page, String searchKeyword, List<VideoCard> videoList)
            throws IOException, JSONException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        String path = "/api/video/user/" + mid + "?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJson(path);
        JSONArray list = OttoApiUtil.getVideoListArray(result);
        if (list == null || list.length() == 0) return 1;
        OttoApiUtil.parseVideoList(list, videoList);
        return list.length() < ApiConfig.PAGE_SIZE ? 1 : 0;
    }

    public static int getFollowingList(long mid, int page, List<UserInfo> userList) throws IOException, JSONException {
        if (mid == 0) return -1;
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        // following_api.md：num 最大 18
        int num = ApiConfig.PAGE_SIZE;
        if (num > 18) num = 18;
        if (num < 1) num = 12;
        String path = "/api/following/list/" + mid + "?offset=" + offset + "&num=" + num;
        JSONObject result = OttoApiUtil.getJsonWithToken(path);
        if (!OttoApiUtil.isSuccess(result)) return -1;

        JSONObject data = result.optJSONObject("data");
        JSONArray users = null;
        if (data != null) {
            users = data.optJSONArray("user_list");
            if (users == null) users = data.optJSONArray("following_list");
            if (users == null) users = data.optJSONArray("list");
        }
        if (users == null) {
            users = result.optJSONArray("user_list");
        }
        if (users == null || users.length() == 0) return 1;

        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.getJSONObject(i);
            long uid = OttoApiUtil.parseLong(u, "uid");
            if (uid <= 0) uid = OttoApiUtil.parseLong(u, "id");
            if (uid <= 0) uid = OttoApiUtil.parseLong(u, "mid");
            String name = u.optString("username", "");
            if (name.length() == 0) name = u.optString("name", "用户" + uid);
            String avatar = u.optString("avatar_url", "");
            if (avatar.length() == 0) avatar = u.optString("avatar", "");
            // 关注列表里的人默认视为已关注；勿被错误 follow_status 弄乱
            boolean followed = true;
            if (u.has("follow_status")) {
                int fs = parseFollowStatusValue(u.opt("follow_status"));
                if (fs == 0) followed = false;
            }
            if (uid > 0) {
                userList.add(new UserInfo(uid, name, avatar, "", 0, 0, 0, followed, "", 0, "", 0));
            }
        }
        return users.length() < num ? 1 : 0;
    }

    public static int followUser(long mid, boolean isFollow) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/following/follow/" + mid, body);
        return OttoApiUtil.isSuccess(resp) ? 0 : -1;
    }

    /** 最近一次 toggleFollow 响应中的 new_fans_count；未返回时为 -1。 */
    public static int lastNewFansCount = -1;

    /**
     * 切换关注状态；成功返回最新 follow_status（1=已关注，0=未关注），失败返回 -1。
     * 若 POST 响应缺少 follow_status，则再 GET /api/following/status/{mid}；
     * 仍未知时按「切换前状态取反」推断。
     */
    public static int toggleFollow(long mid) throws IOException, JSONException {
        return toggleFollow(mid, false, false);
    }

    public static int toggleFollow(long mid, boolean currentlyFollowed, boolean useCurrentHint)
            throws IOException, JSONException {
        lastNewFansCount = -1;
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/following/follow/" + mid, new JSONObject());
        if (!OttoApiUtil.isSuccess(resp)) return -1;

        JSONObject data = resp.optJSONObject("data");
        if (resp.has("new_fans_count")) {
            lastNewFansCount = resp.optInt("new_fans_count", -1);
        } else if (data != null && data.has("new_fans_count")) {
            lastNewFansCount = data.optInt("new_fans_count", -1);
        }

        // 文档：follow_status 在根级；部分环境也会放 data 里
        int status = parseFollowStatus(resp);
        // POST 成功后状态必然翻转。若读到的仍是旧值/缺失，强制取反，避免误报「已取消关注」
        int expected = currentlyFollowed ? 0 : 1;
        if (useCurrentHint) {
            if (status < 0 || status == (currentlyFollowed ? 1 : 0)) {
                status = expected;
            }
        } else if (status < 0) {
            status = expected;
        }
        return status;
    }

    /**
     * Parse follow_status from root or data. Handles Number, String "0"/"1", Boolean.
     * @return 0 or 1, or -1 if absent
     */
    private static int parseFollowStatus(JSONObject obj) {
        if (obj == null) return -1;
        // 只认 follow_status，避免 is_following/followed 等杂字段误判为「已关注」
        if (obj.has("follow_status") && !obj.isNull("follow_status")) {
            int v = parseFollowStatusValue(obj.opt("follow_status"));
            if (v >= 0) return v;
        }
        JSONObject data = obj.optJSONObject("data");
        if (data != null && data.has("follow_status") && !data.isNull("follow_status")) {
            int v = parseFollowStatusValue(data.opt("follow_status"));
            if (v >= 0) return v;
        }
        return -1;
    }

    private static int parseFollowStatusValue(Object val) {
        if (val == null || val == JSONObject.NULL) return -1;
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue() ? 1 : 0;
        }
        if (val instanceof Number) {
            // 仅 1=已关注、0=未关注；-1 等哨兵值表示未知，勿当成「已关注」
            int n = ((Number) val).intValue();
            if (n == 1) return 1;
            if (n == 0) return 0;
            return -1;
        }
        String s = String.valueOf(val).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return 1;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return 0;
        try {
            int n = Integer.parseInt(s);
            if (n == 1) return 1;
            if (n == 0) return 0;
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void logout() {
        OttoAuthApi.logout();
    }
}
