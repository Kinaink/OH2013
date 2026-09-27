package cn.ottohub.oh2013.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class Reply implements Serializable {

    public long rpid;
    public long oid;
    public long root;
    public long parent;
    public boolean forceDelete;
    public String ofBvid = "";
    public String pubTime;
    public long ctime;
    public UserInfo sender;
    public String message;
    public ArrayList<String> pictureList = new ArrayList<String>();
    public int likeCount;
    public boolean upLiked;
    public boolean upReplied;
    public boolean liked;
    public int childCount;
    public boolean isDynamic;
    public ArrayList<Reply> childMsgList = new ArrayList<Reply>();
    public boolean isTop;
    public int replyCount;
    /** 是否本人评论（if_my_comment） */
    public boolean isMine;

    public Reply() {
    }

    public Reply(boolean isRoot, JSONObject replyJson) throws JSONException {
        this.rpid = replyJson.getLong("rpid");
        this.oid = replyJson.getLong("oid");
        this.root = replyJson.getLong("root");
        this.parent = replyJson.getLong("parent");

        if (replyJson.has("member") && !replyJson.isNull("member")) {
            this.sender = new UserInfo(replyJson.getJSONObject("member"));
        }

        JSONObject content = replyJson.getJSONObject("content");
        JSONObject replyCtrl = replyJson.optJSONObject("reply_control");
        long rawCtime = replyJson.getLong("ctime");
        this.ctime = rawCtime;
        long ctimeMs = rawCtime * 1000;

        String time;
        if (replyCtrl != null && System.currentTimeMillis() - ctimeMs < 3 * 24 * 60 * 60 * 1000 && replyCtrl.has("time_desc")) {
            time = replyCtrl.getString("time_desc");
        } else {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(ctimeMs);
        }

        if (replyCtrl != null && replyCtrl.has("location")) {
            String location = replyCtrl.getString("location");
            if (location != null && location.length() > 5) {
                time = time + " | IP:" + location.substring(5);
            }
        }
        this.pubTime = time;

        if (replyCtrl != null && replyCtrl.has("is_up_top") && replyCtrl.getBoolean("is_up_top")) {
            this.isTop = true;
        }

        String rawMessage = content.getString("message");
        this.message = stripHtml(rawMessage);
        if (this.isTop) {
            this.message = "[置顶]" + this.message;
        }

        this.likeCount = replyJson.getInt("like");
        this.liked = replyJson.getInt("action") == 1;
        int ifMy = 0;
        Object ifMyObj = replyJson.opt("if_my_comment");
        if (ifMyObj instanceof Boolean) {
            ifMy = ((Boolean) ifMyObj).booleanValue() ? 1 : 0;
        } else if (ifMyObj instanceof Number) {
            ifMy = ((Number) ifMyObj).intValue() != 0 ? 1 : 0;
        } else if (ifMyObj != null && ifMyObj != JSONObject.NULL) {
            String s = String.valueOf(ifMyObj).trim();
            if ("1".equals(s) || "true".equalsIgnoreCase(s)) ifMy = 1;
        }
        if (ifMy == 0 && replyJson.has("member") && !replyJson.isNull("member")) {
            Object mIf = replyJson.optJSONObject("member").opt("if_my_comment");
            if (mIf instanceof Boolean) {
                ifMy = ((Boolean) mIf).booleanValue() ? 1 : 0;
            } else if (mIf instanceof Number) {
                ifMy = ((Number) mIf).intValue() != 0 ? 1 : 0;
            } else if (mIf != null && mIf != JSONObject.NULL) {
                String s = String.valueOf(mIf).trim();
                if ("1".equals(s) || "true".equalsIgnoreCase(s)) ifMy = 1;
            }
        }
        this.isMine = ifMy == 1;
        if (!this.isMine && this.sender != null && this.sender.mid != 0) {
            long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            if (selfMid != 0 && this.sender.mid == selfMid) {
                this.isMine = true;
            }
        }

        if (replyJson.has("up_action") && !replyJson.isNull("up_action")) {
            JSONObject upAction = replyJson.getJSONObject("up_action");
            this.upLiked = upAction.optBoolean("like", false);
            this.upReplied = upAction.optBoolean("reply", false);
        }

        if (isRoot) {
            if (content.has("pictures") && !content.isNull("pictures")) {
                JSONArray pictures = content.getJSONArray("pictures");
                for (int j = 0; j < pictures.length(); j++) {
                    JSONObject picture = pictures.getJSONObject(j);
                    String imgSrc = picture.optString("img_src", "");
                    if (imgSrc != null && imgSrc.length() > 0) {
                        this.pictureList.add(imgSrc);
                    }
                }
            }

            this.childCount = replyJson.getInt("rcount");

            if (replyJson.has("replies") && !replyJson.isNull("replies")) {
                JSONArray childReplies = replyJson.getJSONArray("replies");
                for (int j = 0; j < childReplies.length(); j++) {
                    Reply childReply = new Reply(false, childReplies.getJSONObject(j));
                    this.childMsgList.add(childReply);
                }
            }
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }
        // 先把 @提及链接转成 markdown，避免 <a> 被剥掉后丢失可点击信息
        String result = html.replaceAll(
                "(?i)<a\\s+[^>]*href\\s*=\\s*[\"'](https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?)[\"'][^>]*>\\s*@?([^<]+?)\\s*</a>",
                "[@$3](https://www.ottohub.cn/u/$2)");
        result = result.replaceAll("<br\\s*/?>", "\n");
        result = result.replaceAll("<[^>]+>", "");
        return result;
    }

    public String getFormattedTime() {
        return pubTime != null ? pubTime : "";
    }

    public boolean isRoot() {
        return root == 0;
    }

    public boolean isTop() {
        return isTop;
    }

    public String getContent() {
        return message != null ? message : "";
    }
}