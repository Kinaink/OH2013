package cn.ottohub.oh2013.api;

/**
 * OH2013：原 B 站 WBI 签名工具已停用，保留空壳以免旧引用崩溃。
 */
public class ConfInfoApi {

    public static String signWBI(String url) {
        return url;
    }

    public static String getWBIRawKey() {
        return "";
    }

    public static String getWBIMixinKey(String raw_key) {
        return raw_key != null ? raw_key : "";
    }
}
