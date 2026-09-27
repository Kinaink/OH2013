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

/**
 * 弹幕API — OTTOhub /api/danmaku
 * 发送、点赞、撤回弹幕
 */
public class DanmakuApi {

    // ========== 弹幕颜色常量 ==========
    public static final int COLOR_WHITE = 0xFFFFFF;   // 白色
    public static final int COLOR_RED = 0xFF0000;     // 红色
    public static final int COLOR_BLUE = 0x0000FF;    // 蓝色
    public static final int COLOR_GREEN = 0x00FF00;   // 绿色
    public static final int COLOR_YELLOW = 0xFFFF00;  // 黄色
    public static final int COLOR_ORANGE = 0xFFA500;  // 橙色
    public static final int COLOR_PINK = 0xFFC0CB;    // 粉色
    public static final int COLOR_PURPLE = 0x800080;  // 紫色

    // ========== 弹幕模式常量 ==========
    public static final int MODE_SCROLL = 1;      // 滚动弹幕
    public static final int MODE_TOP = 5;         // 顶部弹幕
    public static final int MODE_BOTTOM = 4;      // 底部弹幕

    /**
     * 通过BVID发送视频弹幕（OH2013：bvid 即 vid）
     * @param progress 播放进度（毫秒，与 MediaPlayer 一致）
     */
    public static int sendVideoDanmakuByBvid(long cid, String msg, String bvid, long progress, int color, int mode) {
        long vid = 0;
        if (bvid != null && bvid.length() > 0) {
            try {
                vid = Long.parseLong(bvid);
            } catch (NumberFormatException e) {
                vid = cid;
            }
        }
        if (vid <= 0) vid = cid;
        return sendVideoDanmakuByAid(cid, msg, vid, progress, color, mode);
    }

    /**
     * 通过AID/vid发送视频弹幕
     * @param progress 播放进度（毫秒）；OTTOhub 需要秒
     */
    public static int sendVideoDanmakuByAid(long cid, String msg, long aid, long progress, int color, int mode) {
        long vid = aid > 0 ? aid : cid;
        double timeSec = progress / 1000.0;
        return OttoDanmakuUtil.sendDanmaku(vid, msg, timeSec, mode, color, "25px");
    }

    /**
     * 点赞弹幕（OTTOhub 暂不支持）
     */
    public static int likeDanmaku(long dmid, long cid, int op) {
        return -1;
    }

    /**
     * 撤回弹幕（OTTOhub 暂不支持）
     */
    public static int recallDanmaku(long dmid, long cid) {
        return -1;
    }
}
