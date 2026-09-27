package cn.ottohub.oh2013.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * OH2013 — OTTOhub 点赞/收藏交互（替代 B 站 InteractionApi）。
 */
public class InteractionApi {

    private static final int CODE_OK = 0;
    private static final int CODE_FAIL = -1;
    private static final int CODE_NOT_SUPPORTED = -999;

    public static int triple(long aid) throws IOException, JSONException {
        int likeCode = like(aid, 1);
        if (likeCode != CODE_OK) return likeCode;
        return favorite(aid, 0);
    }

    public static int like(long aid, int likeState) throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return CODE_FAIL;
        JSONObject body = new JSONObject();
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/video/like/" + aid, body);
        return OttoApiUtil.isSuccess(resp) ? CODE_OK : CODE_FAIL;
    }

    public static int coin(long aid, int multiply) throws IOException, JSONException {
        return CODE_NOT_SUPPORTED;
    }

    public static int favorite(long aid, long fid) throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return CODE_FAIL;
        JSONObject body = new JSONObject();
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/video/favorite/" + aid, body);
        return OttoApiUtil.isSuccess(resp) ? CODE_OK : CODE_FAIL;
    }

    public static int getVideoRelation(long aid) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/video/" + aid);
        if (!OttoApiUtil.isSuccess(result)) return CODE_FAIL;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return CODE_FAIL;
        return CODE_OK;
    }

    public static boolean isLiked(long aid) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/video/" + aid);
        if (!OttoApiUtil.isSuccess(result)) return false;
        JSONObject data = result.optJSONObject("data");
        return data != null && OttoApiUtil.parseInt(data, "if_like") == 1;
    }

    public static boolean isFavorited(long aid) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/video/" + aid);
        if (!OttoApiUtil.isSuccess(result)) return false;
        JSONObject data = result.optJSONObject("data");
        return data != null && OttoApiUtil.parseInt(data, "if_favorite") == 1;
    }
}
