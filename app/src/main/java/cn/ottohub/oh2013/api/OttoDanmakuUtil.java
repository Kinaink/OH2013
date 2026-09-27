package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * 将 OTTOhub 滚幕 JSON 转为 B 站 XML 格式，供 DanmakuFlameMaster 解析。
 */
public final class OttoDanmakuUtil {

    private OttoDanmakuUtil() {
    }

    public static void fetchAndWriteXml(long vid, File outFile) throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJson("/api/danmaku/" + vid);
        if (!OttoApiUtil.isSuccess(result)) {
            writeEmptyXml(outFile);
            return;
        }
        JSONArray data = result.optJSONArray("data");
        writeJsonToXml(data, outFile);
    }

    /** 获取视频滚幕条数（详情页显示用） */
    public static int countDanmaku(long vid) {
        try {
            JSONObject result = OttoApiUtil.getJson("/api/danmaku/" + vid);
            if (!OttoApiUtil.isSuccess(result)) return 0;
            JSONArray data = result.optJSONArray("data");
            return data != null ? data.length() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 发送滚幕。成功返回 0，未登录 -101，无权限 -102，其它失败 -1。
     * @param timeSec 出现时间（秒）
     * @param mode B 站模式：1=scroll, 5=top, 4=bottom
     * @param color RGB（不含 alpha），如 0xFFFFFF
     * @param fontSize 如 "25px"
     */
    public static int sendDanmaku(long vid, String text, double timeSec, int mode, int color, String fontSize) {
        try {
            if (!OttoAuthApi.isLoggedIn()) {
                android.util.Log.e("OttoDanmakuUtil", "未登录，无法发送滚幕");
                return -101;
            }
            if (vid <= 0) return -1;
            if (text == null || text.length() == 0) return -1;

            String modeStr = modeToOtto(mode);
            String colorHex = colorToHex(color);
            String fs = (fontSize != null && fontSize.length() > 0) ? fontSize : "25px";

            JSONObject body = new JSONObject();
            body.put("vid", vid);
            body.put("text", text);
            body.put("time", timeSec);
            body.put("mode", modeStr);
            body.put("color", colorHex);
            body.put("font_size", fs);
            body.put("render", "");

            JSONObject result = OttoApiUtil.postJsonWithToken("/api/danmaku", body);
            if (OttoApiUtil.isSuccess(result)) {
                return 0;
            }
            String message = result != null ? result.optString("message", "") : "";
            android.util.Log.e("OttoDanmakuUtil", "发送滚幕失败: " + (result != null ? result.toString() : "null"));
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.indexOf("no_permission") >= 0 || lower.indexOf("no permission") >= 0
                        || message.indexOf("无权限") >= 0 || message.indexOf("权限") >= 0) {
                    return -102;
                }
            }
            return -1;
        } catch (Exception e) {
            android.util.Log.e("OttoDanmakuUtil", "发送滚幕异常: " + e.getMessage());
            return -1;
        }
    }

    private static String modeToOtto(int mode) {
        if (mode == DanmakuApi.MODE_TOP || mode == 5) return "top";
        if (mode == DanmakuApi.MODE_BOTTOM || mode == 4) return "bottom";
        return "scroll";
    }

    private static String colorToHex(int color) {
        int rgb = color & 0xFFFFFF;
        String hex = Integer.toHexString(rgb);
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return hex;
    }

    private static void writeEmptyXml(File outFile) throws IOException {
        writeBytes(outFile, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><i></i>".getBytes("UTF-8"));
    }

    public static void writeJsonToXml(JSONArray data, File outFile) throws IOException, JSONException {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>");
        if (data != null) {
            long now = System.currentTimeMillis() / 1000;
            for (int i = 0; i < data.length(); i++) {
                JSONObject d = data.getJSONObject(i);
                float time = (float) d.optDouble("time", 0);
                int mode = modeToBili(d.optString("mode", "scroll"));
                String fontSize = d.optString("font_size", "25px");
                int fontPx = 25;
                if (fontSize.endsWith("px")) {
                    try {
                        fontPx = Integer.parseInt(fontSize.substring(0, fontSize.length() - 2));
                    } catch (NumberFormatException ignored) {
                    }
                }
                int color = parseColor(d.optString("color", "ffffff"));
                String text = d.optString("text", "");
                text = escapeXml(text);
                sb.append("<d p=\"");
                sb.append(time).append(",");
                sb.append(mode).append(",");
                sb.append(fontPx).append(",");
                sb.append(color).append(",");
                sb.append(now).append(",0,0,0\">");
                sb.append(text);
                sb.append("</d>");
            }
        }
        sb.append("</i>");
        writeBytes(outFile, sb.toString().getBytes("UTF-8"));
    }

    private static int modeToBili(String mode) {
        if ("top".equals(mode)) return 5;
        if ("bottom".equals(mode)) return 4;
        return 1;
    }

    private static int parseColor(String color) {
        if (color == null) return 16777215;
        String c = color.startsWith("#") ? color.substring(1) : color;
        if (c.length() != 6) return 16777215;
        try {
            return Integer.parseInt(c, 16);
        } catch (NumberFormatException e) {
            return 16777215;
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
    }
}
