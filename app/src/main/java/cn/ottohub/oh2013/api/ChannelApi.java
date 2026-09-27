package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.StringUtil;

/**
 * OTTOhub 频道 API。
 */
public final class ChannelApi {

    public static class ChannelItem {
        public long channelId;
        public String channelName;
        public String channelTitle;
        public String description;
        public String coverUrl;
        public int memberCount;
        public int followerCount;
        public boolean isMember;
        public boolean isFollowing;
        public int joinPermission;
        public int userRole;
    }

    public static class NoticeItem {
        public long noticeId;
        public String title;
        public String content;
        public int sortOrder;
    }

    private ChannelApi() {
    }

    private static boolean parseBool(JSONObject c, String key) {
        if (c == null || !c.has(key) || c.isNull(key)) return false;
        Object v = c.opt(key);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) {
            int n = ((Number) v).intValue();
            return n == 1; // 仅 1 视为真，避免 -1/2 等哨兵被当成已关注
        }
        String s = String.valueOf(v);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static ChannelItem parseChannelItem(JSONObject c) {
        ChannelItem item = new ChannelItem();
        item.channelId = OttoApiUtil.parseLong(c, "channel_id");
        item.channelName = c.optString("channel_name", "");
        item.channelTitle = c.optString("channel_title", "");
        if (item.channelTitle.length() == 0) item.channelTitle = item.channelName;
        item.description = c.optString("description", "");
        item.coverUrl = c.optString("cover_url", "");
        item.memberCount = OttoApiUtil.parseInt(c, "member_count");
        item.followerCount = OttoApiUtil.parseInt(c, "follower_count");
        item.isMember = parseBool(c, "is_member");
        item.isFollowing = parseBool(c, "is_following");
        item.joinPermission = OttoApiUtil.parseInt(c, "join_permission");
        item.userRole = OttoApiUtil.parseInt(c, "user_role");
        return item;
    }

    public static List<ChannelItem> fetchChannelList(int page) throws IOException, JSONException {
        int limit = ApiConfig.PAGE_SIZE;
        String path = "/api/channel?page=" + page + "&limit=" + limit + "&sort=follower_count&order=desc";
        JSONObject result = OttoApiUtil.getJsonWithToken(path);
        ArrayList<ChannelItem> list = new ArrayList<ChannelItem>();
        if (!OttoApiUtil.isSuccess(result)) return list;

        JSONObject data = result.optJSONObject("data");
        JSONArray channels = data != null ? data.optJSONArray("channels") : null;
        if (channels == null) return list;

        for (int i = 0; i < channels.length(); i++) {
            list.add(parseChannelItem(channels.getJSONObject(i)));
        }
        return list;
    }

    public static ChannelItem fetchChannelDetail(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/channel/" + channelId);
        if (!OttoApiUtil.isSuccess(result)) return null;
        JSONObject c = result.optJSONObject("data");
        if (c == null) return null;
        return parseChannelItem(c);
    }

    /** @return true on success */
    public static boolean joinChannel(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.postJsonWithToken(
                "/api/channel/" + channelId + "/members", new JSONObject());
        return OttoApiUtil.isSuccess(result);
    }

    /** @return true on success */
    public static boolean leaveChannel(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.deleteJsonWithToken(
                "/api/channel/" + channelId + "/members/me");
        return OttoApiUtil.isSuccess(result);
    }

    /** @return true on success */
    public static boolean followChannel(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.postJsonWithToken(
                "/api/channel/" + channelId + "/follow", new JSONObject());
        return OttoApiUtil.isSuccess(result);
    }

    /** @return true on success */
    public static boolean unfollowChannel(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.deleteJsonWithToken(
                "/api/channel/" + channelId + "/follow");
        return OttoApiUtil.isSuccess(result);
    }

    /**
     * 获取频道公告列表（按 sort_order 升序）。
     */
    public static List<NoticeItem> fetchNotices(long channelId) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/channel/" + channelId + "/notices");
        ArrayList<NoticeItem> list = new ArrayList<NoticeItem>();
        if (!OttoApiUtil.isSuccess(result)) return list;
        JSONObject data = result.optJSONObject("data");
        JSONArray notices = data != null ? data.optJSONArray("notices") : null;
        if (notices == null) return list;
        for (int i = 0; i < notices.length(); i++) {
            JSONObject n = notices.getJSONObject(i);
            NoticeItem item = new NoticeItem();
            item.noticeId = OttoApiUtil.parseLong(n, "notice_id");
            item.title = n.optString("title", "");
            item.content = n.optString("content", "");
            item.sortOrder = OttoApiUtil.parseInt(n, "sort_order");
            list.add(item);
        }
        return list;
    }

    /**
     * 获取频道内容。type = video | blog。
     * video 写入 videoOut，blog 写入 blogOut（另一侧可为 null）。
     * @return 1 if end, 0 if more, -1 on error
     */
    public static int fetchChannelContent(long channelId, String type, int page,
            List<VideoCard> videoOut, List<BlogItem> blogOut)
            throws IOException, JSONException {
        int limit = ApiConfig.PAGE_SIZE;
        if (type == null || type.length() == 0) type = "video";
        String path = "/api/channel/" + channelId + "/content?type=" + type
                + "&page=" + page + "&limit=" + limit;
        JSONObject result = OttoApiUtil.getJsonWithToken(path);
        if (!OttoApiUtil.isSuccess(result)) return -1;

        JSONObject data = result.optJSONObject("data");
        JSONArray content = data != null ? data.optJSONArray("content") : null;
        if (content == null || content.length() == 0) return 1;

        for (int i = 0; i < content.length(); i++) {
            JSONObject row = content.getJSONObject(i);
            String rowType = row.optString("type", type);
            if ("video".equals(rowType)) {
                if (videoOut == null) continue;
                long vid = OttoApiUtil.parseLong(row, "vid");
                String title = row.optString("title", "");
                String cover = row.optString("cover_url", "");
                String upName = row.optString("username", "");
                long views = OttoApiUtil.parseLong(row, "view_count");
                videoOut.add(new VideoCard(title, upName, StringUtil.toWan(views), cover, vid, String.valueOf(vid)));
            } else if ("blog".equals(rowType)) {
                if (blogOut == null) continue;
                BlogItem blog = BlogApi.parseBlog(row);
                if (blog.time == null || blog.time.length() == 0) {
                    blog.time = row.optString("created_at", "");
                }
                blogOut.add(blog);
            }
        }
        return content.length() < limit ? 1 : 0;
    }

    /** @deprecated prefer {@link #fetchChannelContent} */
    public static int fetchChannelVideos(long channelId, int page, List<VideoCard> out)
            throws IOException, JSONException {
        return fetchChannelContent(channelId, "video", page, out, null);
    }
}
