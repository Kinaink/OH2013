package cn.ottohub.oh2013.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import cn.ottohub.oh2013.api.OttoAuthApi;
import cn.ottohub.oh2013.api.OttoCommentApi;

public class ReplyHelper {

    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 防连发：同时只允许一条评论/回复在发送中 */
    private static volatile boolean sSending = false;

    public interface ReplyCallback {
        void onSuccess(String responseJson);
        void onFailed(String error);
    }

    public static boolean isSending() {
        return sSending;
    }

    public static void sendReply(final Context context, final long aid, final long root,
                                 final long parent, final String text, final ReplyCallback callback) {
        sendReply(context, aid, root, parent, text, false, callback);
    }

    public static void sendBlogReply(final Context context, final long bid, final long root,
                                     final long parent, final String text, final ReplyCallback callback) {
        sendReply(context, bid, root, parent, text, true, callback);
    }

    public static void sendReply(final Context context, final long oid, final long root,
                                 final long parent, final String text, final boolean isBlog,
                                 final ReplyCallback callback) {
        if (text == null || text.length() == 0) {
            showToast(context, "回复内容不能为空哦");
            if (callback != null) callback.onFailed("回复内容不能为空哦");
            return;
        }
        if (text.length() > 459) {
            showToast(context, "回复内容不能超过459字哦");
            if (callback != null) callback.onFailed("回复内容过长");
            return;
        }

        if (!OttoAuthApi.isLoggedIn()) {
            showToast(context, "请先登录后再回复的说~");
            if (callback != null) callback.onFailed("未登录");
            return;
        }

        if (sSending) {
            showToast(context, "正在发送，请稍候");
            if (callback != null) callback.onFailed("发送中");
            return;
        }
        sSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long parentId = 0;
                    // OTTOhub 仅两级：parent_* 必须是一级评论 id，或 0（发顶级）
                    // 永远不要把子回复 id 当作 parent，否则 error_parent
                    if (root > 0) {
                        parentId = root;
                    } else if (parent > 0) {
                        parentId = parent;
                    }
                    // root 与 parent 都 >0 且不同：parent 可能是子评，强制用 root
                    if (root > 0 && parent > 0 && root != parent) {
                        parentId = root;
                    }
                    // 调用方本意是回复（root/parent 曾传入）却解析成 0 → 拒绝静默发成顶级评论
                    if (parentId <= 0 && (root > 0 || parent > 0)) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                sSending = false;
                                showToast(context, "无法回复：父评论无效");
                                if (callback != null) callback.onFailed("父评论无效");
                            }
                        });
                        return;
                    }
                    final JSONObject result;
                    if (isBlog) {
                        result = OttoCommentApi.sendBlogComment(oid, parentId, text);
                    } else {
                        result = OttoCommentApi.sendVideoComment(oid, parentId, text);
                    }
                    final int code = result.optInt("code", -1);
                    String rawMsg = result.optString("message", "");
                    // 友好化 error_parent*
                    if ("error_parent".equals(rawMsg)
                            || "error_parent_bcid".equals(rawMsg)
                            || "error_parent_vcid".equals(rawMsg)) {
                        rawMsg = "只能回复一级评论（不支持楼中楼）";
                    }
                    final String message = rawMsg;
                    final String responseJson = result.toString();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sSending = false;
                            if (code == 0) {
                                showToast(context, "回复发送成功");
                                if (callback != null) callback.onSuccess(responseJson);
                            } else {
                                showToast(context, "发送失败: " + message);
                                if (callback != null) callback.onFailed(message);
                            }
                        }
                    });
                } catch (final Exception e) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sSending = false;
                            showToast(context, "发送失败: " + errorMsg);
                            if (callback != null) callback.onFailed(errorMsg);
                        }
                    });
                }
            }
        }).start();
    }

    public static void sendReplyWithPictures(final Context context, final long aid, final long root,
                                             final long parent, final String text,
                                             final String picturesJson, final ReplyCallback callback) {
        sendReply(context, aid, root, parent, text, false, callback);
    }

    private static void showToast(final Context context, final String msg) {
        if (context != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
