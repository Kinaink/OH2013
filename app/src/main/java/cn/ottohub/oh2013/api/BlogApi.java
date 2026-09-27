package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.model.SlideshowItem;
import cn.ottohub.oh2013.model.UserInfo;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.StringUtil;

/**
 * OTTOhub 博客/动态 API
 */
public class BlogApi {

    public static final String FEED_LATEST = "latest";
    public static final String FEED_POPULAR = "popular";
    public static final String FEED_RANDOM = "random";

    public static void fetchList(String feed, int offset, List<BlogItem> out)
            throws IOException, JSONException {
        String path;
        if (FEED_RANDOM.equals(feed)) {
            path = "/api/blog/random?num=" + ApiConfig.PAGE_SIZE;
        } else if (FEED_POPULAR.equals(feed)) {
            path = "/api/blog/popular?time_limit=7&offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        } else {
            path = "/api/blog/latest?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        }
        JSONObject result = OttoApiUtil.getJson(path);
        JSONArray list = getBlogListArray(result);
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            out.add(parseBlog(list.getJSONObject(i)));
        }
    }

    public static JSONArray search(String keyword, int page) throws IOException, JSONException {
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        String path = "/api/blog/search?search_term="
                + URLEncoder.encode(keyword, "UTF-8")
                + "&offset=" + offset
                + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result = OttoApiUtil.getJson(path);
        return getBlogListArray(result);
    }

    public static JSONArray getBlogListArray(JSONObject result) throws JSONException {
        if (!OttoApiUtil.isSuccess(result)) return null;
        JSONObject data = result.optJSONObject("data");
        if (data != null && data.has("blog_list")) {
            return data.optJSONArray("blog_list");
        }
        return result.optJSONArray("blog_list");
    }

    public static BlogItem parseBlog(JSONObject item) throws JSONException {
        BlogItem blog = new BlogItem();
        blog.bid = OttoApiUtil.parseLong(item, "bid");
        blog.uid = OttoApiUtil.parseLong(item, "uid");
        blog.title = item.optString("title", "");
        blog.content = item.optString("content", "");
        blog.time = item.optString("time", "");
        blog.username = item.optString("username", "");
        blog.avatarUrl = item.optString("avatar_url", "");
        blog.viewCount = OttoApiUtil.parseInt(item, "view_count");
        blog.likeCount = OttoApiUtil.parseInt(item, "like_count");
        blog.favoriteCount = OttoApiUtil.parseInt(item, "favorite_count");
        blog.commentCount = OttoApiUtil.parseInt(item, "comment_count");
        blog.ifLike = OttoApiUtil.parseInt(item, "if_like") == 1;
        blog.ifFavorite = OttoApiUtil.parseInt(item, "if_favorite") == 1;
        blog.isGore = OttoApiUtil.parseInt(item, "is_gore");
        JSONArray thumbs = item.optJSONArray("thumbnails");
        if (thumbs != null && thumbs.length() > 0) {
            blog.thumbnail = thumbs.optString(0, "");
        }
        return blog;
    }

    public static BlogItem fetchDetail(long bid) throws IOException, JSONException {
        if (bid <= 0) return null;
        // blog_api.md：GET /api/blog/{bid}/detail — 字段多在根级；失败再试 GET /api/blog/{bid}（blog_list 单元素）
        JSONObject result = null;
        try {
            result = OttoApiUtil.getJsonWithToken("/api/blog/" + bid + "/detail");
        } catch (Exception e) {
            result = null;
        }
        BlogItem blog = parseBlogFromDetailResponse(result);
        if (blog != null && blog.bid > 0) {
            return blog;
        }
        try {
            result = OttoApiUtil.getJson("/api/blog/" + bid);
        } catch (Exception e) {
            result = null;
        }
        blog = parseBlogFromDetailResponse(result);
        if (blog != null && blog.bid > 0) {
            return blog;
        }
        // 最后兜底：带 token 的简略接口
        try {
            result = OttoApiUtil.getJsonWithToken("/api/blog/" + bid);
        } catch (Exception e) {
            return null;
        }
        return parseBlogFromDetailResponse(result);
    }

    /** 兼容根级字段 / data 对象 / blog_list[0] */
    private static BlogItem parseBlogFromDetailResponse(JSONObject result) throws JSONException {
        if (result == null || !OttoApiUtil.isSuccess(result)) return null;
        JSONObject src = result.optJSONObject("data");
        if (src == null) {
            src = result;
        }
        // GET /api/blog/{bid} → data.blog_list[0] 或根级 blog_list[0]
        JSONArray list = src.optJSONArray("blog_list");
        if (list == null) {
            list = result.optJSONArray("blog_list");
        }
        if (list != null && list.length() > 0) {
            JSONObject first = list.optJSONObject(0);
            if (first != null) {
                BlogItem b = parseBlog(first);
                if (b.bid > 0) return b;
            }
        }
        // detail：字段在根级（与 status 同级）
        if (src.has("bid") || src.has("title") || src.has("content")) {
            BlogItem b = parseBlog(src);
            if (b.bid > 0) return b;
        }
        if (result.has("bid") || result.has("title") || result.has("content")) {
            BlogItem b = parseBlog(result);
            if (b.bid > 0) return b;
        }
        return null;
    }

    /** POST /api/blog/like/{bid} */
    public static JSONObject toggleLike(long bid) throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return null;
        JSONObject body = new JSONObject();
        return OttoApiUtil.postJsonWithToken("/api/blog/like/" + bid, body);
    }

    /** 收藏/取消收藏动态。响应 data: if_favorite, favorite_count */
    public static JSONObject toggleFavorite(long bid) throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return null;
        JSONObject body = new JSONObject();
        return OttoApiUtil.postJsonWithToken("/api/blog/favorite/" + bid, body);
    }

    /**
     * 收藏的动态列表。offset=(page-1)*PAGE_SIZE, num=PAGE_SIZE。
     * @return 1 if end (empty or size &lt; PAGE_SIZE), 0 if more, -1 on error
     */
    public static int fetchFavoriteBlogs(int page, List<BlogItem> out)
            throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return -1;
        int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        return fetchFavoriteBlogsByOffset(offset, out);
    }

    /** 最近一次成功响应里的收藏动态总数；未知为 -1 */
    private static volatile int sFavoriteBlogTotal = -1;

    /** Load more by absolute offset (prefer out.size() when appending). */
    public static int fetchFavoriteBlogsByOffset(int offset, List<BlogItem> out)
            throws IOException, JSONException {
        if (!OttoAuthApi.isLoggedIn()) return -1;
        if (offset < 0) offset = 0;
        if (offset == 0) {
            sFavoriteBlogTotal = -1;
        }
        if (sFavoriteBlogTotal >= 0 && offset >= sFavoriteBlogTotal) {
            return 1;
        }
        String path = "/api/blog/favorite-list?offset=" + offset + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject result;
        try {
            result = OttoApiUtil.getJsonWithToken(path);
        } catch (Exception e) {
            // 越界 HTML 等：当作没有更多，勿抛给 UI
            return 1;
        }
        if (!OttoApiUtil.isSuccess(result)) {
            return 1;
        }

        try {
            JSONObject data = result.optJSONObject("data");
            if (data != null && data.has("favorite_blog_count")) {
                int total = data.optInt("favorite_blog_count", -1);
                if (total >= 0) {
                    sFavoriteBlogTotal = total;
                    if (offset >= total) return 1;
                }
            }
        } catch (Exception ignored) {
        }

        JSONArray list;
        try {
            list = getBlogListArray(result);
        } catch (Exception e) {
            return 1;
        }
        if (list == null || list.length() == 0) return 1;

        try {
            for (int i = 0; i < list.length(); i++) {
                out.add(parseBlog(list.getJSONObject(i)));
            }
        } catch (Exception e) {
            return 1;
        }
        if (sFavoriteBlogTotal >= 0 && out != null && out.size() >= sFavoriteBlogTotal) {
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

    /** POST /api/blog/submit — 发布动态 */
    public static JSONObject submitBlog(String title, String content, int isGore)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title != null ? title : "");
        body.put("content", content != null ? content : "");
        body.put("is_gore", isGore);
        body.put("blog_type", 0);
        return OttoApiUtil.postJsonWithToken("/api/blog/submit", body);
    }

    /** DELETE /api/blog/{bid} */
    public static JSONObject deleteBlog(long bid) throws IOException, JSONException {
        return OttoApiUtil.deleteJsonWithToken("/api/blog/" + bid);
    }
}
