/*
 * OH2013 — OTTOhub 播放历史
 */
package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.ApiResult;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
import cn.ottohub.oh2013.util.StringUtil;

public class HistoryApi {

    public static class HistoryResult {
        public ApiResult apiResult;
        public List<VideoCard> newItems;

        public HistoryResult(ApiResult apiResult, List<VideoCard> newItems) {
            this.apiResult = apiResult;
            this.newItems = newItems;
        }
    }

    public static HistoryResult getHistory(ApiResult lastResult) throws IOException, JSONException {
        ApiResult apiResult = new ApiResult();
        List<VideoCard> newItems = new ArrayList<VideoCard>();

        if (!OttoAuthApi.isLoggedIn()) {
            apiResult.code = -101;
            apiResult.message = "未登录";
            return new HistoryResult(apiResult, newItems);
        }

        int offset = (int) lastResult.offset;
        String path = "/api/video/history-list?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJsonWithToken(path);
        if (!OttoApiUtil.isSuccess(result)) {
            apiResult.code = -1;
            apiResult.message = result.optString("message", "请求失败");
            return new HistoryResult(apiResult, newItems);
        }

        JSONObject data = result.optJSONObject("data");
        JSONArray list = data != null ? data.optJSONArray("video_list") : null;
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                long vid = OttoApiUtil.parseLong(item, "vid");
                String title = item.optString("title", "未知标题");
                String cover = item.optString("cover_url", "");
                String upName = item.optString("username", "未知用户");
                int lastWatch = OttoApiUtil.parseInt(item, "last_watch_second");
                String viewStr;
                if (lastWatch == -1) {
                    viewStr = "已看完";
                } else if (lastWatch == 0) {
                    viewStr = "还没看过";
                } else {
                    viewStr = "看到" + StringUtil.toTime(lastWatch);
                }
                newItems.add(new VideoCard(title, upName, viewStr, cover, vid, String.valueOf(vid)));
            }
        }

        int count = list != null ? list.length() : 0;
        apiResult.offset = offset + count;
        apiResult.isBottom = count < ApiConfig.PAGE_SIZE;
        apiResult.code = 0;
        return new HistoryResult(apiResult, newItems);
    }

    public static void report(final long aid, final long cid, final int progressMs) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false)) return;
        if (aid == 0) return;
        if (!OttoAuthApi.isLoggedIn()) return;

        final long vid = aid > 0 ? aid : cid;
        final int progressSec = progressMs / 1000;

        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("vid", vid);
                    body.put("last_watch_second", progressSec);
                    OttoApiUtil.postJsonWithToken("/api/video/watch-history", body);
                } catch (Exception e) {
                }
            }
        }).start();
    }
}
