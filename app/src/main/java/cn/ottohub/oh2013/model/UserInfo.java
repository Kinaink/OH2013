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
package cn.ottohub.oh2013.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class UserInfo implements Serializable {
    public long mid;
    public String name;
    public String avatar;
    public String sign;
    public int fans;
    public int level;
    public int following;
    public boolean followed;
    public String notice;
    public boolean isSeniorMember;  // 硬核会员

    public int official;
    public String officialDesc;
    public long mtime;

    public int vip_role = 0;
    public String vip_nickname_color = "";

    public long current_exp = 0;
    public long next_exp = 0;

    public String medal_name = "";
    public int medal_level = 0;

    public long experience = 0;
    public java.util.ArrayList<String> honours;

    /** 个人封面图 URL（/api/user 的 cover_url） */
    public String coverUrl = "";
    /** 性别（sex / gender） */
    public String sex = "";
    /** 注册时间（time / register_time / created_at） */
    public String registerTime = "";

    public String sys_notice = "";

    // 直播功能暂时没有喵，注释掉
    // public LiveRoom live_room = null;

    public int is_senior_member = 0;

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.is_senior_member = isSeniorMember;
        this.isSeniorMember = isSeniorMember == 1;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, String sysNotice, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.sys_notice = sysNotice;
        this.is_senior_member = isSeniorMember;
        this.isSeniorMember = isSeniorMember == 1;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long currentExp, long nextExp, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.current_exp = currentExp;
        this.next_exp = nextExp;
        this.is_senior_member = isSeniorMember;
        this.isSeniorMember = isSeniorMember == 1;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, int vipRole, String sysNotice, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.vip_role = vipRole;
        this.sys_notice = sysNotice;
        this.is_senior_member = isSeniorMember;
        this.isSeniorMember = isSeniorMember == 1;
    }

    public UserInfo(long mid, String name, String avatar, String sign, int fans, int following, int level, boolean followed, String notice, int official, String officialDesc, long mtime, int isSeniorMember) {
        this.mid = mid;
        this.name = name;
        this.avatar = avatar;
        this.sign = sign;
        this.fans = fans;
        this.level = level;
        this.following = following;
        this.followed = followed;
        this.notice = notice;
        this.official = official;
        this.officialDesc = officialDesc;
        this.mtime = mtime;
        this.is_senior_member = isSeniorMember;
        this.isSeniorMember = isSeniorMember == 1;
    }

    public UserInfo() {
    }

    public UserInfo(JSONObject userInfoJson) throws JSONException {
        JSONObject levelInfo = userInfoJson.optJSONObject("level_info");
        this.level = levelInfo != null ? levelInfo.optInt("current_level", 0) : 0;
        this.mid = 0;
        if (userInfoJson.has("mid")) {
            Object midObj = userInfoJson.opt("mid");
            if (midObj instanceof Number) {
                this.mid = ((Number) midObj).longValue();
            } else if (midObj != null) {
                try {
                    this.mid = Long.parseLong(String.valueOf(midObj).trim());
                } catch (NumberFormatException e) {
                    this.mid = 0;
                }
            }
        }
        this.name = userInfoJson.optString("uname", "用户");
        this.avatar = userInfoJson.optString("avatar", "");
        this.is_senior_member = userInfoJson.optInt("is_senior_member", 0);
        JSONObject vip = userInfoJson.optJSONObject("vip");
        if (vip != null) {
            this.vip_role = vip.optInt("vipStatus", 0);
            this.vip_nickname_color = vip.optString("nickname_color", "");
        }
        if ((!userInfoJson.isNull("fans_detail")) && (!SharedPreferencesUtil.getBoolean("no_medal", false))) {
            JSONObject fansDetail = userInfoJson.optJSONObject("fans_detail");
            if (fansDetail != null) {
                this.medal_name = fansDetail.optString("medal_name", "");
                this.medal_level = fansDetail.optInt("level", 0);
            }
        }
    }
}