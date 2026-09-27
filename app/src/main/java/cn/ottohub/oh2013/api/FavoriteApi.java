/*
 * OH2013 — OTTOhub 收藏（无收藏夹，统一列表）
 */
package cn.ottohub.oh2013.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.ottohub.oh2013.model.FavoriteFolder;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.StringUtil;

public class FavoriteApi {

    private static final String TAG = "FavoriteApi";
    public static final long DEFAULT_FOLDER_ID = 0;

    public static long getAidByBvid(String bvid) {
        if (bvid == null || bvid.length() == 0) return 0L;
        try {
            return Long.parseLong(bvid);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static ArrayList getFavoriteFoldersFast(long mid) throws IOException, JSONException {
        ArrayList folders = new ArrayList();
        if (!OttoAuthApi.isLoggedIn()) return folders;
        FavoriteFolder folder = new FavoriteFolder();
        folder.fid = DEFAULT_FOLDER_ID;
        folder.name = "我的冷藏";
        folder.videoCount = 0;
        folders.add(folder);
        return folders;
    }

    public static HashMap getCoverMap(long mid) throws IOException, JSONException {
        return new HashMap();
    }

    public static void getFavoriteState(long aid, ArrayList folderNames, ArrayList fids, ArrayList states)
            throws IOException, JSONException {
        if (folderNames != null) folderNames.add("我的冷藏");
        if (fids != null) fids.add(DEFAULT_FOLDER_ID);
        boolean favorited = false;
        try {
            favorited = InteractionApi.isFavorited(aid);
        } catch (Exception e) {
            Log.w(TAG, "getFavoriteState: " + e.getMessage());
        }
        if (states != null) states.add(favorited);
    }

    public static int addFavorite(long aid, String bvid, long fid) throws IOException, JSONException {
        return InteractionApi.favorite(aid, fid);
    }

    public static int deleteFavorite(long aid, String bvid, long fid) throws IOException, JSONException {
        // 冷藏接口为 toggle：直接调用即可（UI 已据本地状态决定是否取消）
        return InteractionApi.favorite(aid, fid);
    }

    public static int getFolderVideos(long mid, long fid, int page, List<VideoCard> videoList)
            throws IOException, JSONException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        return getFolderVideosByOffset(offset, videoList);
    }

    /** 最近一次成功响应里的收藏总数；未知为 -1。用于翻页前拦截越界请求。 */
    private static volatile int sFavoriteVideoTotal = -1;

    /** Load more by absolute offset (prefer videoList.size() when appending). */
    public static int getFolderVideosByOffset(int offset, List<VideoCard> videoList)
            throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return -1;
        if (offset < 0) offset = 0;
        if (offset == 0) {
            sFavoriteVideoTotal = -1;
        }
        // 已知总数且已到头：勿再请求
        if (sFavoriteVideoTotal >= 0 && offset >= sFavoriteVideoTotal) {
            return 1;
        }
        // video_api.md: GET /api/video/favorite-list?offset=&num=&token=
        String path = "/api/video/favorite-list?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result;
        try {
            result = OttoApiUtil.getJsonWithToken(path);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // 仅明确非 JSON / HTML 越界视为结束；其它网络错误交给上层重试
            if (isNonJsonResponse(msg) && offset > 0) {
                Log.w(TAG, "favorite-list soft-end offset=" + offset + " err=" + msg);
                return 1;
            }
            Log.w(TAG, "favorite-list error offset=" + offset + " err=" + msg);
            throw new IOException(msg.length() > 0 ? msg : "favorite-list failed", e);
        }
        if (!OttoApiUtil.isSuccess(result)) {
            String msg = result != null ? result.optString("message", "") : "";
            Log.w(TAG, "favorite-list fail offset=" + offset + " msg=" + msg);
            if ("error_token".equals(msg) || "missing_argument".equals(msg)) {
                return -1;
            }
            // 空列表类业务：当作没有更多
            if (offset > 0 && (msg == null || msg.length() == 0
                    || "Not found".equalsIgnoreCase(msg))) {
                return 1;
            }
            return -1;
        }

        try {
            JSONObject data = result.optJSONObject("data");
            if (data != null && data.has("favorite_video_count")) {
                int total = data.optInt("favorite_video_count", -1);
                if (total >= 0) {
                    sFavoriteVideoTotal = total;
                    if (offset >= total) return 1;
                }
            }
        } catch (Exception ignored) {
        }

        JSONArray list = OttoApiUtil.getVideoListArray(result);
        if (list == null || list.length() == 0) return 1;

        int before = videoList != null ? videoList.size() : 0;
        OttoApiUtil.parseVideoList(list, videoList);
        int added = (videoList != null ? videoList.size() : 0) - before;
        if (added <= 0) return 1;

        if (sFavoriteVideoTotal >= 0 && videoList != null && videoList.size() >= sFavoriteVideoTotal) {
            return 1;
        }
        return list.length() < ApiConfig.PAGE_SIZE ? 1 : 0;
    }

    private static boolean isNonJsonResponse(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.indexOf("response_not_json") >= 0
                || m.indexOf("webpage") >= 0
                || m.indexOf("html") >= 0
                || m.indexOf("doctype") >= 0
                || msg.indexOf("<") >= 0
                || msg.indexOf("网页") >= 0
                || m.indexOf("not json") >= 0;
    }
}
