package cn.ottohub.oh2013.api;

/**
 * OH2013 — OTTOhub 客户端 API 配置
 */
public final class ApiConfig {

    public static final String BASE_URL = "https://api.ottohub.cn";
    public static final String CHAT_BASE_URL = "https://api-chat.ottohub.cn";
    public static final String SITE_URL = "https://www.ottohub.cn/";
    public static final int PAGE_SIZE = 12;

    /**
     * 虚拟主机公告 JSON 地址。
     * 请改成你自己的域名，格式见项目根目录 HOST_ANNOUNCEMENT.md
     */
    public static final String ANNOUNCEMENT_URL = "https://ulic.cc/oh2013/announcement.json";

    /**
     * 版本更新 JSON（可与公告同机）。格式见 HOST_ANNOUNCEMENT.md
     */
    public static final String UPDATE_URL = "https://ulic.cc/oh2013/version.json";

    private ApiConfig() {
    }
}
