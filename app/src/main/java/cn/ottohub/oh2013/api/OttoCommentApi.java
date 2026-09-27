package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * OTTOhub 视频/动态评论 API，并将响应转换为 B 站兼容 JSON 供现有 UI 解析。
 */
public final class OttoCommentApi {

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = -1;

    public static class FetchResult {
        public int code;
        public JSONArray replies;
        public String nextOffset;
        public boolean isEnd;
        public String message;
    }

    private OttoCommentApi() {
    }

    public static FetchResult fetchVideoComments(long vid, int offset, long parentVcid) throws IOException, JSONException {
        FetchResult result = new FetchResult();
        result.replies = new JSONArray();
        result.nextOffset = "";
        result.isEnd = true;
        result.code = CODE_FAIL;

        String path = "/api/comment/videos/" + vid
                + "?parent_vcid=" + parentVcid
                + "&offset=" + offset
                + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject resp = OttoApiUtil.getJsonWithToken(path);
        if (!OttoApiUtil.isSuccess(resp)) {
            result.message = resp.optString("message", "加载失败");
            return result;
        }

        JSONObject data = resp.optJSONObject("data");
        JSONArray list = data != null ? data.optJSONArray("comment_list") : null;
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                result.replies.put(convertToBiliFormat(list.getJSONObject(i), vid, parentVcid));
            }
        }

        int count = list != null ? list.length() : 0;
        if (count < ApiConfig.PAGE_SIZE) {
            result.isEnd = true;
            result.nextOffset = "";
        } else {
            result.isEnd = false;
            result.nextOffset = String.valueOf(offset + ApiConfig.PAGE_SIZE);
        }
        result.code = CODE_OK;
        return result;
    }

    public static FetchResult fetchBlogComments(long bid, int offset, long parentBcid) throws IOException, JSONException {
        FetchResult result = new FetchResult();
        result.replies = new JSONArray();
        result.nextOffset = "";
        result.isEnd = true;
        result.code = CODE_FAIL;

        String path = "/api/comment/blogs/" + bid
                + "?parent_bcid=" + parentBcid
                + "&offset=" + offset
                + "&num=" + ApiConfig.PAGE_SIZE;
        JSONObject resp = OttoApiUtil.getJsonWithToken(path);
        if (!OttoApiUtil.isSuccess(resp)) {
            result.message = resp.optString("message", "加载失败");
            return result;
        }

        JSONObject data = resp.optJSONObject("data");
        JSONArray list = data != null ? data.optJSONArray("comment_list") : null;
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                result.replies.put(convertToBiliFormat(list.getJSONObject(i), bid, parentBcid));
            }
        }

        int count = list != null ? list.length() : 0;
        if (count < ApiConfig.PAGE_SIZE) {
            result.isEnd = true;
            result.nextOffset = "";
        } else {
            result.isEnd = false;
            result.nextOffset = String.valueOf(offset + ApiConfig.PAGE_SIZE);
        }
        result.code = CODE_OK;
        return result;
    }

    /**
     * 将 OttoHub 评论转为 B 站兼容结构。rpid 取自 vcid 或 bcid；parent 取自 parent_vcid 或 parent_bcid。
     * @param fetchParentId 本次列表请求的 parent_*（拉取子评时 >0）；API 缺字段时用其补齐 root/parent
     */
    public static JSONObject convertToBiliFormat(JSONObject otto, long oid, long fetchParentId) throws JSONException {
        long rpid = OttoApiUtil.parseLong(otto, "vcid");
        if (rpid == 0) {
            rpid = OttoApiUtil.parseLong(otto, "bcid");
        }
        long parentId = OttoApiUtil.parseLong(otto, "parent_vcid");
        if (parentId == 0) {
            parentId = OttoApiUtil.parseLong(otto, "parent_bcid");
        }
        // 子评列表：若响应未带 parent_*，用请求时的父评论 id，避免 UI 把子评当一级去回复 / 误发顶级
        if (parentId == 0 && fetchParentId > 0) {
            parentId = fetchParentId;
        }
        long uid = OttoApiUtil.parseLong(otto, "uid");
        boolean child = fetchParentId > 0 || parentId > 0;

        JSONObject bili = new JSONObject();
        bili.put("rpid", rpid);
        bili.put("oid", oid);
        bili.put("root", child ? parentId : 0);
        bili.put("parent", child ? parentId : 0);
        bili.put("rcount", OttoApiUtil.parseInt(otto, "child_comment_num"));
        bili.put("like", 0);
        bili.put("action", 0);
        bili.put("ctime", parseTimeToEpoch(otto.optString("time", "")));

        // Reply/UserInfo 仍按 B 站结构解析，必须补齐 level_info / vip / is_senior_member
        JSONObject member = new JSONObject();
        member.put("mid", uid);
        member.put("uname", otto.optString("username", "用户" + uid));
        member.put("avatar", otto.optString("avatar_url", ""));
        member.put("is_senior_member", 0);
        JSONObject levelInfo = new JSONObject();
        levelInfo.put("current_level", 0);
        member.put("level_info", levelInfo);
        JSONObject vip = new JSONObject();
        vip.put("vipStatus", 0);
        vip.put("nickname_color", "");
        member.put("vip", vip);
        int ifMyComment = 0;
        Object ifMyObj = otto.opt("if_my_comment");
        if (ifMyObj instanceof Boolean) {
            ifMyComment = ((Boolean) ifMyObj).booleanValue() ? 1 : 0;
        } else if (ifMyObj instanceof Number) {
            ifMyComment = ((Number) ifMyObj).intValue() != 0 ? 1 : 0;
        } else if (ifMyObj != null && ifMyObj != JSONObject.NULL) {
            String s = String.valueOf(ifMyObj).trim();
            if ("1".equals(s) || "true".equalsIgnoreCase(s)) ifMyComment = 1;
        }
        member.put("if_my_comment", ifMyComment);
        bili.put("member", member);
        bili.put("if_my_comment", ifMyComment);

        JSONObject content = new JSONObject();
        // 保留原始 content，便于 [@name](url) / HTML 提及渲染
        content.put("message", otto.optString("content", ""));
        bili.put("content", content);
        return bili;
    }

    public static JSONObject buildSendSuccessResponse(long oid, JSONObject ottoComment) throws JSONException {
        JSONObject wrapper = new JSONObject();
        wrapper.put("code", 0);
        JSONObject data = new JSONObject();
        long parentId = OttoApiUtil.parseLong(ottoComment, "parent_vcid");
        if (parentId == 0) {
            parentId = OttoApiUtil.parseLong(ottoComment, "parent_bcid");
        }
        JSONObject reply = convertToBiliFormat(ottoComment, oid, parentId);
        data.put("reply", reply);
        wrapper.put("data", data);
        return wrapper;
    }

    public static JSONObject sendVideoComment(long vid, long parentVcid, String content) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("parent_vcid", parentVcid);
        body.put("content", content);
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/comment/videos/" + vid, body);
        if (!OttoApiUtil.isSuccess(resp)) {
            JSONObject err = new JSONObject();
            err.put("code", CODE_FAIL);
            err.put("message", resp.optString("message", "发送失败"));
            return err;
        }
        return buildLocalSendOk(vid, parentVcid, content);
    }

    public static JSONObject sendBlogComment(long bid, long parentBcid, String content) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("parent_bcid", parentBcid);
        body.put("content", content);
        JSONObject resp = OttoApiUtil.postJsonWithToken("/api/comment/blogs/" + bid, body);
        if (!OttoApiUtil.isSuccess(resp)) {
            JSONObject err = new JSONObject();
            err.put("code", CODE_FAIL);
            err.put("message", resp.optString("message", "发送失败"));
            return err;
        }
        return buildLocalSendOk(bid, parentBcid, content);
    }

    private static JSONObject buildLocalSendOk(long oid, long parentId, String content) throws JSONException {
        JSONObject ok = new JSONObject();
        ok.put("code", 0);
        ok.put("message", "");
        JSONObject data = new JSONObject();
        JSONObject reply = new JSONObject();
        // 本地占位 rpid；UI 应以 refresh 为准，勿据此二次插入
        reply.put("rpid", System.currentTimeMillis());
        reply.put("oid", oid);
        reply.put("root", parentId);
        reply.put("parent", parentId);
        reply.put("rcount", 0);
        reply.put("like", 0);
        reply.put("action", 0);
        reply.put("ctime", System.currentTimeMillis() / 1000);
        JSONObject member = new JSONObject();
        member.put("mid", SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
        member.put("uname", SharedPreferencesUtil.getString("uname", "我"));
        member.put("avatar", SharedPreferencesUtil.getString("avatar", ""));
        reply.put("member", member);
        JSONObject msg = new JSONObject();
        msg.put("message", content);
        reply.put("content", msg);
        data.put("reply", reply);
        ok.put("data", data);
        return ok;
    }

    public static int deleteVideoComment(long vcid) throws IOException, JSONException {
        JSONObject resp = OttoApiUtil.deleteJsonWithToken("/api/comment/video-comments/" + vcid);
        return OttoApiUtil.isSuccess(resp) ? CODE_OK : CODE_FAIL;
    }

    public static int deleteBlogComment(long bcid) throws IOException, JSONException {
        JSONObject resp = OttoApiUtil.deleteJsonWithToken("/api/comment/blog-comments/" + bcid);
        return OttoApiUtil.isSuccess(resp) ? CODE_OK : CODE_FAIL;
    }

    private static long parseTimeToEpoch(String time) {
        if (time == null || time.length() == 0) return System.currentTimeMillis() / 1000;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
            return sdf.parse(time).getTime() / 1000;
        } catch (Exception e) {
            return System.currentTimeMillis() / 1000;
        }
    }
}
