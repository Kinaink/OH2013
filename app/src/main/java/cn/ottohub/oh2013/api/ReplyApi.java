package cn.ottohub.oh2013.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import cn.ottohub.oh2013.model.Reply;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * OH2013 — OTTOhub 评论 API（保留 B 站兼容返回码）。
 */
public class ReplyApi {

    public static final int REPLY_TYPE_VIDEO_CHILD = 0;
    public static final int REPLY_TYPE_VIDEO = 1;
    public static final int REPLY_TYPE_ARTICLE = 12;
    public static final int REPLY_TYPE_DYNAMIC_CHILD = 11;
    public static final int REPLY_TYPE_DYNAMIC = 17;
    public static final String TOP_TIP = "[置顶]";

    public static class ReplyResult {
        public int code;
        public Reply reply;
    }

    public static class ReplyListResult {
        public int code;
        public String nextPagination;

        public ReplyListResult(int code, String nextPagination) {
            this.code = code;
            this.nextPagination = nextPagination;
        }
    }

    public static ReplyListResult getRepliesLazy(long oid, long rpid, String pagination,
                                                 int type, int sort, List<Reply> replyList)
            throws JSONException, IOException {
        int offset = 0;
        if (pagination != null && pagination.length() > 0) {
            try {
                offset = Integer.parseInt(pagination);
            } catch (NumberFormatException e) {
                offset = 0;
            }
        }

        long parentId = rpid > 0 ? rpid : 0;
        OttoCommentApi.FetchResult fetch;
        if (type == REPLY_TYPE_DYNAMIC
                || type == REPLY_TYPE_DYNAMIC_CHILD
                || type == REPLY_TYPE_ARTICLE) {
            fetch = OttoCommentApi.fetchBlogComments(oid, offset, parentId);
        } else {
            fetch = OttoCommentApi.fetchVideoComments(oid, offset, parentId);
        }
        if (fetch.code != OttoCommentApi.CODE_OK) {
            return new ReplyListResult(-1, "");
        }

        JSONArray replies = fetch.replies;
        if (replies != null && replies.length() > 0) {
            analyzeReplyArray(rpid == 0, replies, replyList);
        }

        if (fetch.isEnd) {
            return new ReplyListResult(1, "");
        }
        return new ReplyListResult(0, fetch.nextOffset);
    }

    private static void analyzeReplyArray(boolean isRoot, JSONArray replies, List<Reply> replyList)
            throws JSONException {
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            Reply replyReturn = new Reply(isRoot, reply);
            replyList.add(replyReturn);
        }
    }

    public static ReplyResult sendReplyResult(long oid, long root, long parent, String text, int type)
            throws IOException, JSONException {
        long parentId = 0;
        // 仅两级：始终用一级评论 root，忽略子回复 parent，避免 error_parent
        if (root > 0) {
            parentId = root;
        } else if (parent > 0) {
            parentId = parent;
        }

        JSONObject result;
        if (type == REPLY_TYPE_DYNAMIC
                || type == REPLY_TYPE_DYNAMIC_CHILD
                || type == REPLY_TYPE_ARTICLE) {
            result = OttoCommentApi.sendBlogComment(oid, parentId, text);
        } else {
            result = OttoCommentApi.sendVideoComment(oid, parentId, text);
        }
        ReplyResult replyResult = new ReplyResult();
        replyResult.code = result.optInt("code", -1);
        replyResult.reply = null;

        if (replyResult.code == 0 && result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("reply") && !data.isNull("reply")) {
                replyResult.reply = new Reply(root != 0, data.getJSONObject("reply"));
            }
        }
        return replyResult;
    }

    public static String uploadReplyImage(long oid, byte[] imageData, String fileName) throws IOException, JSONException {
        Log.w("ReplyApi", "OTTOhub 暂不支持评论图片");
        return null;
    }

    public static int likeComment(long oid, long rpid, int type) throws IOException, JSONException {
        return -1;
    }

    public static int unlikeComment(long oid, long rpid, int type) throws IOException, JSONException {
        return -1;
    }

    public static int deleteComment(long oid, long rpid, int type) throws IOException, JSONException {
        if (type == REPLY_TYPE_DYNAMIC
                || type == REPLY_TYPE_DYNAMIC_CHILD
                || type == REPLY_TYPE_ARTICLE) {
            return OttoCommentApi.deleteBlogComment(rpid);
        }
        return OttoCommentApi.deleteVideoComment(rpid);
    }
}
