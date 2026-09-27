package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * OTTOhub 全站聊天室 HTTP API（发言走 WebSocket）。
 */
public final class ChatApi {

    public static final String CHAT_TOKEN_KEY = "otto_chat_token";
    public static final String ROOM_MAIN = "main";

    public static class ChatMessage {
        public long id;
        public long uid;
        public String username;
        public String content;
        public String createdAt;
        public long replyToId;
        public String replyUsername;
        public String replyContent;
    }

    public static class RoomHistory {
        public String announcement;
        public List<ChatMessage> messages = new ArrayList<ChatMessage>();
        public boolean hasMore;
    }

    private ChatApi() {
    }

    public static String getCachedChatToken() {
        String t = SharedPreferencesUtil.getString(CHAT_TOKEN_KEY, "");
        return t != null ? t : "";
    }

    public static void clearChatToken() {
        SharedPreferencesUtil.removeValue(CHAT_TOKEN_KEY);
    }

    /** main_token → chat_token */
    public static String exchange() throws IOException, JSONException {
        String main = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        if (main == null || main.length() == 0) {
            throw new IOException("未登录");
        }
        JSONObject body = new JSONObject();
        body.put("main_token", main);
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("Content-Type");
        headers.add("application/json");
        headers.add("User-Agent");
        headers.add("OTTOhub");
        String resp = NetWorkUtil.postJson(
                ApiConfig.CHAT_BASE_URL + "/api/auth/exchange",
                body.toString(),
                headers);
        if (resp == null || resp.length() == 0) {
            throw new IOException("换票无响应");
        }
        JSONObject json = new JSONObject(resp);
        if (!"success".equals(json.optString("status"))) {
            throw new IOException(json.optString("message", "换票失败"));
        }
        JSONObject data = json.optJSONObject("data");
        if (data == null) {
            throw new IOException("换票无 data");
        }
        String chatToken = data.optString("chat_token", "");
        if (chatToken.length() == 0) {
            throw new IOException("未返回 chat_token");
        }
        SharedPreferencesUtil.putString(CHAT_TOKEN_KEY, chatToken);
        return chatToken;
    }

    public static String ensureChatToken() throws IOException, JSONException {
        String cached = getCachedChatToken();
        if (cached.length() > 0) {
            return cached;
        }
        return exchange();
    }

    public static RoomHistory fetchMessages(String room, int limit) throws IOException, JSONException {
        return fetchMessages(room, limit, 0);
    }

    /**
     * 拉取房间消息。beforeId&gt;0 时向前翻页（更早消息），见 chat_api.md。
     */
    public static RoomHistory fetchMessages(String room, int limit, long beforeId)
            throws IOException, JSONException {
        if (room == null || room.length() == 0) {
            room = ROOM_MAIN;
        }
        if (limit <= 0 || limit > 50) {
            limit = 50;
        }
        String url = ApiConfig.CHAT_BASE_URL + "/api/rooms/" + room + "/messages?limit=" + limit;
        if (beforeId > 0) {
            url = url + "&before_id=" + beforeId;
        }
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("User-Agent");
        headers.add("OTTOhub");
        String token = getCachedChatToken();
        if (token.length() > 0) {
            headers.add("Authorization");
            headers.add("Bearer " + token);
        }
        String resp = NetWorkUtil.get(url, headers);
        if (resp == null || resp.length() == 0) {
            throw new IOException("消息列表为空");
        }
        JSONObject json = new JSONObject(resp);
        if (!"success".equals(json.optString("status"))) {
            if ("error_token".equals(json.optString("message"))) {
                clearChatToken();
            }
            throw new IOException(json.optString("message", "拉取失败"));
        }
        JSONObject data = json.optJSONObject("data");
        RoomHistory history = new RoomHistory();
        if (data == null) {
            return history;
        }
        JSONObject pin = data.optJSONObject("pinned_announcement");
        if (pin != null) {
            history.announcement = pin.optString("content", "");
        }
        JSONArray list = data.optJSONArray("message_list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject m = list.optJSONObject(i);
                if (m == null) continue;
                history.messages.add(parseMessage(m));
            }
            history.hasMore = list.length() >= limit;
        }
        return history;
    }

    public static ChatMessage parseMessage(JSONObject m) {
        ChatMessage cm = new ChatMessage();
        if (m == null) return cm;
        cm.id = m.optLong("id", 0);
        cm.uid = m.optLong("uid", 0);
        cm.username = m.optString("username", "");
        cm.content = m.optString("content", "");
        cm.createdAt = m.optString("created_at", "");
        JSONObject reply = m.optJSONObject("reply");
        if (reply != null) {
            cm.replyToId = reply.optLong("id", 0);
            cm.replyUsername = reply.optString("username", "");
            cm.replyContent = reply.optString("content", "");
        }
        return cm;
    }

    /** DELETE /api/messages/{id} */
    public static void deleteMessage(long id) throws IOException, JSONException {
        if (id <= 0) throw new IOException("无效消息");
        String token = ensureChatToken();
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("Content-Type");
        headers.add("application/json");
        headers.add("Authorization");
        headers.add("Bearer " + token);
        headers.add("User-Agent");
        headers.add("OTTOhub");
        String url = ApiConfig.CHAT_BASE_URL + "/api/messages/" + id;
        String resp = NetWorkUtil.deleteJson(url, "{}", headers);
        if (resp == null || resp.length() == 0) {
            throw new IOException("删除无响应");
        }
        JSONObject json = new JSONObject(resp);
        if (!"success".equals(json.optString("status"))) {
            String msg = json.optString("message", "删除失败");
            if ("error_token".equals(msg)) {
                clearChatToken();
            }
            throw new IOException(msg);
        }
    }
}
