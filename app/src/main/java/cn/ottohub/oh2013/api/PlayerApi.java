package cn.ottohub.oh2013.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.ottohub.oh2013.model.PlayerData;

/**
 * OH2013 播放地址 — OTTOhub 直链 MP4，无需 WBI/DASH。
 */
public class PlayerApi {

    private static final long URL_CACHE_TTL = 600000;
    private static final Map<String, CachedUrl> sUrlCache = new HashMap<String, CachedUrl>();

    private static class CachedUrl {
        final String videoUrl;
        final String audioUrl;
        final long timestamp;
        final String[] qnStrList;
        final int[] qnValueList;

        CachedUrl(String videoUrl, String audioUrl, long timestamp, String[] qnStrList, int[] qnValueList) {
            this.videoUrl = videoUrl;
            this.audioUrl = audioUrl;
            this.timestamp = timestamp;
            this.qnStrList = qnStrList;
            this.qnValueList = qnValueList;
        }
    }

    private static String buildCacheKey(PlayerData playerData) {
        return playerData.aid + "_" + playerData.cid;
    }

    public static void getVideo(PlayerData playerData, boolean download) throws JSONException, IOException {
        long vid = playerData.aid > 0 ? playerData.aid : playerData.cid;
        if (vid <= 0) {
            throw new JSONException("无效的视频 ID");
        }

        if (System.currentTimeMillis() - playerData.timeStamp < URL_CACHE_TTL
                && playerData.videoUrl != null && playerData.videoUrl.length() > 0) {
            return;
        }

        String cacheKey = buildCacheKey(playerData);
        if (!download) {
            CachedUrl cached;
            synchronized (sUrlCache) {
                cached = sUrlCache.get(cacheKey);
            }
            if (cached != null && cached.videoUrl != null && cached.videoUrl.length() > 0
                    && System.currentTimeMillis() - cached.timestamp < URL_CACHE_TTL) {
                playerData.videoUrl = cached.videoUrl;
                playerData.audioUrl = cached.audioUrl;
                playerData.qnStrList = cached.qnStrList;
                playerData.qnValueList = cached.qnValueList;
                playerData.timeStamp = System.currentTimeMillis();
                return;
            }
        }

        JSONObject result = OttoApiUtil.getJsonWithToken("/api/video/" + vid);
        if (!OttoApiUtil.isSuccess(result)) {
            throw new JSONException("无法获取视频详情");
        }
        JSONObject data = result.getJSONObject("data");
        String videoUrl = data.optString("video_url", "");
        String audioUrl = data.optString("audio_url", "");
        if (videoUrl.length() == 0) {
            videoUrl = data.optString("video_m3u8_url", "");
        }
        if (videoUrl.length() == 0) {
            throw new JSONException("无法获取视频地址");
        }
        // 相对路径补全为 file CDN 绝对地址，避免 MediaPlayer 当成本地文件
        videoUrl = cn.ottohub.oh2013.util.ImageUrlUtil.resolve(videoUrl);
        if (audioUrl != null && audioUrl.length() > 0) {
            audioUrl = cn.ottohub.oh2013.util.ImageUrlUtil.resolve(audioUrl);
        }
        if (videoUrl == null || videoUrl.length() == 0) {
            throw new JSONException("无法获取视频地址");
        }

        playerData.videoUrl = videoUrl;
        playerData.audioUrl = audioUrl != null ? audioUrl : "";
        playerData.danmakuUrl = "ottohub://danmaku/" + vid;
        int durationSec = OttoApiUtil.parseInt(data, "duration");
        playerData.durationMs = durationSec * 1000L;
        playerData.cid = vid;
        playerData.cidHistory = vid;
        playerData.progress = 0;
        playerData.qnStrList = new String[]{"默认"};
        playerData.qnValueList = new int[]{32};
        playerData.timeStamp = System.currentTimeMillis();

        if (!download) {
            synchronized (sUrlCache) {
                sUrlCache.put(cacheKey, new CachedUrl(videoUrl, playerData.audioUrl,
                        System.currentTimeMillis(), playerData.qnStrList, playerData.qnValueList));
            }
        }
    }

    public static void getBangumi(PlayerData playerData) throws JSONException, IOException {
        getVideo(playerData, false);
    }
}
