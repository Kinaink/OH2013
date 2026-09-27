package cn.ottohub.oh2013.api;

import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class SearchApi {

    public static String seid = "";
    public static String searchKeyword = "";

    /**
     * 搜索视频（OTTOhub）
     */
    public static JSONArray search(String keyword, int page) throws IOException, JSONException {
        if (!searchKeyword.equals(keyword)) {
            searchKeyword = keyword;
            seid = "";
        }
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        String path = "/api/video/search?search_term="
                + URLEncoder.encode(searchKeyword, "UTF-8")
                + "&offset=" + offset
                + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJson(path);
        if (!OttoApiUtil.isSuccess(result)) return null;
        return OttoApiUtil.getVideoListArray(result);
    }

    /**
     * 搜索动态（OTTOhub blog）
     */
    public static JSONArray searchBlogs(String keyword, int page) throws IOException, JSONException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        String path = "/api/blog/search?search_term="
                + URLEncoder.encode(keyword != null ? keyword : "", "UTF-8")
                + "&offset=" + offset
                + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJson(path);
        if (!OttoApiUtil.isSuccess(result)) return null;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return null;
        JSONArray list = data.optJSONArray("blog_list");
        if (list == null) list = data.optJSONArray("blogs");
        return list;
    }

    /**
     * 解析搜索结果为 VideoCard 列表
     */
    public static void getVideosFromSearchResult(JSONArray input, ArrayList<VideoCard> videoCardList) throws JSONException {
        if (input == null) return;
        for (int i = 0; i < input.length(); i++) {
            videoCardList.add(OttoApiUtil.parseVideoCard(input.getJSONObject(i)));
        }
    }
}
