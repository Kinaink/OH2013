/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */
package cn.ottohub.oh2013.api;

import android.text.SpannableStringBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.ottohub.oh2013.model.At;
import cn.ottohub.oh2013.model.Collection;
import cn.ottohub.oh2013.model.Stats;
import cn.ottohub.oh2013.model.UserInfo;
import cn.ottohub.oh2013.model.VideoInfo;
import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.StringUtil;

public class VideoInfoApi {

    // 画质常量
    private static final int QUALITY_360P = 16;
    private static final int QUALITY_480P = 32;
    private static final int QUALITY_720P = 64;
    private static final int QUALITY_1080P = 80;

    // 视频信息短缓存：VideoDetailActivity.checkAndSetup 与 VideoDetailFragment.loadVideoData
    // 会在同一页面几乎同时各取一次视频信息，用 5 秒 TTL 去重，避免两条完全相同的请求。
    private static VideoInfo sCachedInfo;
    private static String sCachedKey;
    private static long sCachedTime;

    private static String cacheKey(long aid) {
        return "aid:" + aid;
    }

    private static String cacheKey(String bvid) {
        return "bvid:" + bvid;
    }

    private static synchronized VideoInfo fromCache(String key) {
        if (sCachedInfo != null && key.equals(sCachedKey)
                && System.currentTimeMillis() - sCachedTime < 5000) {
            return sCachedInfo;
        }
        return null;
    }

    private static synchronized void toCache(String key, VideoInfo info) {
        sCachedKey = key;
        sCachedInfo = info;
        sCachedTime = System.currentTimeMillis();
    }

    // toWan
    private static String toWanLocal(long num) {
        if (num >= 100000000) {
            float value = (float) num / 100000000;
            String result = formatFloatLocal(value);
            return result + "亿";
        } else if (num >= 10000) {
            float value = (float) num / 10000;
            String result = formatFloatLocal(value);
            return result + "万";
        } else {
            return String.valueOf(num);
        }
    }

    private static String formatFloatLocal(float value) {
        int intPart = (int) value;
        int fracPart = (int) ((value - intPart) * 10);
        String result = String.valueOf(intPart);
        if (fracPart > 0) {
            result = result + "." + String.valueOf(fracPart);
        }
        return result;
    }

    /**
     * 通过 bvid（OTTOhub 中为 vid 字符串）获取视频信息
     */
    public static VideoInfo getVideoInfo(String bvid) throws IOException, JSONException {
        long vid = 0;
        try {
            vid = Long.parseLong(bvid);
        } catch (NumberFormatException e) {
            return null;
        }
        return getVideoInfo(vid);
    }

    /**
     * 通过 vid（映射到 aid 字段）获取视频信息
     */
    public static VideoInfo getVideoInfo(long aid) throws IOException, JSONException {
        String key = cacheKey(aid);
        VideoInfo cached = fromCache(key);
        if (cached != null) return cached;
        JSONObject result = OttoApiUtil.getJsonWithToken("/api/video/" + aid);
        if (!OttoApiUtil.isSuccess(result)) return null;
        VideoInfo videoInfo = getInfoByOttoJson(result.getJSONObject("data"));
        toCache(key, videoInfo);
        return videoInfo;
    }

    /**
     * 解析 OTTOhub 视频详情 JSON
     */
    public static VideoInfo getInfoByOttoJson(JSONObject data) throws JSONException {
        VideoInfo videoInfo = new VideoInfo();
        long vid = OttoApiUtil.parseLong(data, "vid");
        videoInfo.aid = vid;
        videoInfo.bvid = String.valueOf(vid);
        videoInfo.title = data.optString("title", "");
        videoInfo.cover = data.optString("cover_url", "");
        videoInfo.description = data.optString("intro", "");
        videoInfo.timeDesc = data.optString("time", "");
        videoInfo.pubdate = parseTimeToEpoch(data.optString("time", ""));
        int durationSec = OttoApiUtil.parseInt(data, "duration");
        videoInfo.duration = StringUtil.toTime(durationSec);

        Stats stats = new Stats();
        stats.view = OttoApiUtil.parseInt(data, "view_count");
        stats.like = OttoApiUtil.parseInt(data, "like_count");
        stats.favorite = OttoApiUtil.parseInt(data, "favorite_count");
        stats.reply = OttoApiUtil.parseInt(data, "comment_count");
        // 优先 danmaku_count；否则先用 0，详情页异步从 /api/danmaku/{vid} 拉取真实条数
        stats.danmaku = OttoApiUtil.parseInt(data, "danmaku_count");
        if (stats.danmaku <= 0 && data.has("danmaku_num")) {
            stats.danmaku = OttoApiUtil.parseInt(data, "danmaku_num");
        }
        stats.coin = 0;
        stats.coin_limit = 0;
        videoInfo.stats = stats;

        ArrayList<String> pagenames = new ArrayList<String>();
        ArrayList<Long> cids = new ArrayList<Long>();
        pagenames.add(videoInfo.title);
        cids.add(vid);
        videoInfo.pagenames = pagenames;
        videoInfo.cids = cids;

        videoInfo.tags = data.optString("tag", "");
        if (videoInfo.tags.length() == 0) {
            JSONArray tagsArr = data.optJSONArray("tags");
            if (tagsArr != null) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < tagsArr.length(); i++) {
                    if (i > 0) sb.append("/");
                    sb.append(tagsArr.optString(i, ""));
                }
                videoInfo.tags = sb.toString();
            }
        }

        ArrayList<UserInfo> staffList = new ArrayList<UserInfo>();
        UserInfo userInfo = new UserInfo();
        userInfo.name = data.optString("username", "");
        userInfo.avatar = data.optString("avatar_url", "");
        userInfo.mid = OttoApiUtil.parseLong(data, "uid");
        userInfo.sign = data.optString("userintro", "");
        staffList.add(userInfo);
        videoInfo.staff = staffList;

        videoInfo.qualities = new ArrayList<Integer>();
        videoInfo.qualities.add(QUALITY_480P);

        videoInfo.epid = -1;
        videoInfo.copyright = OttoApiUtil.parseInt(data, "type");
        return videoInfo;
    }

    private static long parseTimeToEpoch(String time) {
        if (time == null || time.length() < 10) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = sdf.parse(time);
            return d != null ? d.getTime() / 1000 : 0;
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date d2 = sdf2.parse(time.substring(0, 10));
                return d2 != null ? d2.getTime() / 1000 : 0;
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    /**
     * 通过 vid 获取标签
     */
    public static String getTags(String bvid) throws IOException, JSONException {
        VideoInfo info = getVideoInfo(bvid);
        return info != null ? info.tags : "";
    }

    /**
     * 通过 vid 获取标签
     */
    public static String getTags(long aid) throws IOException, JSONException {
        VideoInfo info = getVideoInfo(aid);
        return info != null ? info.tags : "";
    }

    /**
     * 解析标签 JSON
     */
    public static String analyzeTags(JSONArray tagJson) throws JSONException {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < tagJson.length(); i++) {
            if (i > 0) tags.append("/");
            tags.append(((JSONObject) tagJson.get(i)).getString("tag_name"));
        }
        return tags.toString();
    }

    /**
     * 解析合集信息
     */
    public static Collection analyzeUgcSeason(JSONObject json) throws JSONException {
        Collection collection = new Collection();
        collection.id = json.optInt("id", -1);
        collection.title = json.optString("title");
        collection.intro = json.optString("intro");
        collection.cover = json.optString("cover");
        collection.mid = json.optLong("mid");
        collection.view = toWanLocal(json.getJSONObject("stat").optLong("view"));

        JSONArray sections = json.optJSONArray("sections");
        if (sections != null) {
            List<Collection.Section> sectionList = new ArrayList<Collection.Section>();
            for (int i = 0; i < sections.length(); i++) {
                JSONObject sectionJson = sections.getJSONObject(i);
                Collection.Section section = new Collection.Section();
                section.season_id = sectionJson.optInt("season_id", -1);
                section.id = sectionJson.optInt("id", -1);
                section.title = sectionJson.optString("title");

                JSONArray episodes = sectionJson.optJSONArray("episodes");
                if (episodes != null) {
                    List<Collection.Episode> episodeList = new ArrayList<Collection.Episode>();
                    for (int j = 0; j < episodes.length(); j++) {
                        JSONObject episodeJson = episodes.getJSONObject(j);
                        Collection.Episode episode = new Collection.Episode();
                        episode.season_id = episodeJson.optInt("season_id", -1);
                        episode.section_id = episodeJson.optInt("section_id", -1);
                        episode.id = episodeJson.optInt("id", -1);
                        episode.aid = episodeJson.optLong("aid", -1);
                        episode.cid = episodeJson.optLong("cid", -1);
                        episode.title = episodeJson.optString("title");
                        JSONObject arc = episodeJson.optJSONObject("arc");
                        if (arc != null) {
                            episode.arc = getInfoByJson(arc);
                        }
                        episode.bvid = episodeJson.optString("bvid");
                        episodeList.add(episode);
                    }
                    section.episodes = episodeList;
                }
                sectionList.add(section);
            }
            collection.sections = sectionList;
        }
        return collection;
    }

    /**
     * 解析视频信息 JSON
     */
    public static VideoInfo getInfoByJson(JSONObject data) throws JSONException {
        VideoInfo videoInfo = new VideoInfo();

        videoInfo.title = data.getString("title");
        videoInfo.cover = data.getString("pic");

        // 解析描述
        if (data.has("desc_v2") && !data.isNull("desc_v2")) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            JSONArray descArray = data.getJSONArray("desc_v2");
            ArrayList<At> ats = new ArrayList<At>();
            for (int i = 0; i < descArray.length(); i++) {
                JSONObject curObj = descArray.getJSONObject(i);
                int type = curObj.getInt("type");
                if (type == 2) {
                    String rawText = curObj.getString("raw_text");
                    int start = sb.length();
                    sb.append("@").append(rawText);
                    int end = sb.length();
                    ats.add(new At(curObj.getLong("biz_id"), start, end));
                } else {
                    sb.append(curObj.getString("raw_text"));
                }
            }
            videoInfo.description = sb.toString();
            videoInfo.descAts = ats;
        } else {
            videoInfo.description = data.getString("desc");
        }

        videoInfo.bvid = data.optString("bvid");
        videoInfo.aid = data.getLong("aid");

        // 发布时间
        long pubTime = data.getLong("pubdate") * 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        videoInfo.timeDesc = sdf.format(new Date(pubTime));
        videoInfo.pubdate = data.getLong("pubdate");

        videoInfo.duration = StringUtil.toTime(data.getInt("duration"));

        if (data.has("copyright") && !data.isNull("copyright")) {
            videoInfo.copyright = data.getInt("copyright");
        }

        // 统计数据
        JSONObject stat = data.getJSONObject("stat");
        Stats stats = new Stats();
        stats.view = stat.getInt("view");
        stats.like = stat.getInt("like");
        stats.coin = stat.getInt("coin");
        stats.reply = stat.getInt("reply");
        stats.danmaku = stat.getInt("danmaku");
        stats.favorite = stat.optInt("favorite", -1);
        stats.coin_limit = (videoInfo.copyright == VideoInfo.COPYRIGHT_REPRINT) ? 1 : 2;
        videoInfo.stats = stats;

        // 分P信息
        JSONArray pages = data.optJSONArray("pages");
        if (pages != null) {
            ArrayList<String> pagenames = new ArrayList<String>();
            ArrayList<Long> cids = new ArrayList<Long>();
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.getJSONObject(i);
                String pagename = page.getString("part");
                pagenames.add(pagename);
                long cid = page.getLong("cid");
                cids.add(cid);
            }
            videoInfo.pagenames = pagenames;
            videoInfo.cids = cids;
        }

        videoInfo.upowerExclusive = data.optBoolean("is_upower_exclusive", true);

        // 权限信息
        JSONObject rights = data.optJSONObject("rights");
        if (rights != null) {
            videoInfo.isCooperation = (rights.optInt("is_cooperation", 0) == 1);
            videoInfo.isSteinGate = (rights.optInt("is_stein_gate", 0) == 1);
            videoInfo.is360 = (rights.optInt("is_360", 0) == 1);
        }

        // 解析可用画质
        videoInfo.qualities = new ArrayList<Integer>();

        // 方法1：从 accept_quality 获取
        JSONArray acceptQuality = data.optJSONArray("accept_quality");
        if (acceptQuality != null && acceptQuality.length() > 0) {
            for (int i = 0; i < acceptQuality.length(); i++) {
                int q = acceptQuality.getInt(i);
                if (!videoInfo.qualities.contains(q)) {
                    videoInfo.qualities.add(q);
                }
            }
        }

        // 方法2：从 dash 中获取
        JSONObject dash = data.optJSONObject("dash");
        if (dash != null) {
            JSONArray videoList = dash.optJSONArray("video");
            if (videoList != null && videoList.length() > 0) {
                for (int i = 0; i < videoList.length(); i++) {
                    JSONObject video = videoList.getJSONObject(i);
                    int id = video.optInt("id", 0);
                    if (id > 0 && !videoInfo.qualities.contains(id)) {
                        videoInfo.qualities.add(id);
                    }
                }
            }
        }

        // 方法3：从 accept_quality 的另一个字段获取
        if (videoInfo.qualities.size() == 0) {
            JSONArray qualityList = data.optJSONArray("accept_quality");
            if (qualityList != null && qualityList.length() > 0) {
                for (int i = 0; i < qualityList.length(); i++) {
                    int q = qualityList.getInt(i);
                    if (!videoInfo.qualities.contains(q)) {
                        videoInfo.qualities.add(q);
                    }
                }
            }
        }

        // 如果还是没有，使用默认值
        if (videoInfo.qualities.size() == 0) {
            videoInfo.qualities.add(QUALITY_360P);
            videoInfo.qualities.add(QUALITY_480P);
            videoInfo.qualities.add(QUALITY_720P);
        }

        // 排序（从低到高）
        java.util.Collections.sort(videoInfo.qualities);

        // UP主信息
        ArrayList<UserInfo> staffList = new ArrayList<UserInfo>();
        if (videoInfo.isCooperation) {
            JSONArray staff = data.getJSONArray("staff");
            for (int i = 0; i < staff.length(); i++) {
                JSONObject staffInfo = staff.getJSONObject(i);
                UserInfo staffMember = new UserInfo();
                staffMember.mid = staffInfo.getLong("mid");
                staffMember.sign = staffInfo.getString("title");
                staffMember.name = staffInfo.getString("name");
                staffMember.avatar = staffInfo.getString("face");
                staffMember.fans = staffInfo.getInt("follower");
                staffMember.level = 6;
                staffMember.followed = false;
                staffMember.notice = "";
                if (staffInfo.has("official") && !staffInfo.isNull("official")) {
                    JSONObject official = staffInfo.getJSONObject("official");
                    staffMember.official = official.optInt("role", 0);
                    staffMember.officialDesc = official.optString("title", "");
                }
                staffList.add(staffMember);
            }
        } else {
            if (data.optJSONObject("owner") != null) {
                JSONObject owner = data.getJSONObject("owner");
                UserInfo userInfo = new UserInfo();
                userInfo.name = owner.getString("name");
                userInfo.avatar = owner.getString("face");
                userInfo.mid = owner.getLong("mid");
                userInfo.sign = "Hub主";
                staffList.add(userInfo);
            }
        }
        videoInfo.staff = staffList;

        // 争议信息
        if (data.optJSONObject("argue_info") != null) {
            videoInfo.argueMsg = data.getJSONObject("argue_info").optString("argue_msg", "");
        }

        // 番剧EPID
        try {
            String redirectUrl = data.optString("redirect_url");
            if (redirectUrl != null && redirectUrl.length() > 0
                    && redirectUrl.contains("bangumi")) {
                String epidStr = redirectUrl.replace("https://www.bilibili.com/bangumi/play/ep", "");
                videoInfo.epid = Long.parseLong(epidStr);
            } else {
                videoInfo.epid = -1;
            }
        } catch (Exception e) {
            videoInfo.epid = -1;
        }

        // 合集信息
        JSONObject ugcSeason = data.optJSONObject("ugc_season");
        if (ugcSeason != null) {
            videoInfo.collection = analyzeUgcSeason(ugcSeason);
        }

        return videoInfo;
    }

    /**
     * 获取正在观看人数
     */
    public static String getWatching(long aid, long cid) throws IOException, JSONException {
        return "";
    }
}