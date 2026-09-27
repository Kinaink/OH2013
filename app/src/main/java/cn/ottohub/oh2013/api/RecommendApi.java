/*
 * OH2013 — 基于 BiliClassic 魔改，对接 OTTOhub API
 */
package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.StringUtil;

public class RecommendApi {

    public static final String FEED_POPULAR = "popular";
    public static final String FEED_NEW = "new";
    public static final String FEED_RANDOM = "random";

    /**
     * 获取视频列表（热门，兼容原 RecommendFragment 分页参数）
     *
     * @param freshIdx 页码（从 1 开始）
     * @param fetchRow 已加载条数，用作 offset
     */
    public static void getRecommend(List<VideoCard> videoCardList, int freshIdx, int fetchRow)
            throws IOException, JSONException {
        fetchVideoList(FEED_POPULAR, fetchRow, videoCardList);
    }

    /**
     * 按 feed 类型拉取列表
     */
    public static void fetchVideoList(String feed, int offset, List<VideoCard> videoCardList)
            throws IOException, JSONException {
        String path;
        if (FEED_NEW.equals(feed)) {
            path = "/api/video/new?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        } else if (FEED_RANDOM.equals(feed)) {
            path = "/api/video/random?num=" + ApiConfig.PAGE_SIZE;
        } else {
            path = "/api/video/popular?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE + "&time_limit=7";
        }
        JSONObject result = OttoApiUtil.getJson(path);
        JSONArray list = OttoApiUtil.getVideoListArray(result);
        if (list != null) {
            OttoApiUtil.parseVideoList(list, videoCardList);
        }
    }

    /**
     * 获取相关视频
     */
    public static ArrayList<VideoCard> getRelated(long vid) throws JSONException, IOException {
        String path = "/api/video/related/" + vid + "?num=" + ApiConfig.PAGE_SIZE + "&offset=0";
        JSONObject result = OttoApiUtil.getJson(path);
        ArrayList<VideoCard> videoList = new ArrayList<VideoCard>();
        JSONArray list = OttoApiUtil.getVideoListArray(result);
        if (list != null) {
            OttoApiUtil.parseVideoList(list, videoList);
        }
        return videoList;
    }

    /**
     * 获取热门视频（兼容旧调用）
     */
    public static void getPopular(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        fetchVideoList(FEED_POPULAR, offset, videoCardList);
    }

    /**
     * 随机推荐（替代入站必刷）
     */
    public static void getPrecious(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        fetchVideoList(FEED_RANDOM, 0, videoCardList);
    }
}
