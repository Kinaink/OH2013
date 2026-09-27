package cn.ottohub.oh2013.util;

/**
 * OH2013 品牌称谓格式化。
 */
public final class Terminology {

    private Terminology() {}

    public static String videoId(long vid) {
        return vid > 0 ? ("ov" + vid) : "";
    }

    public static String userId(long oid) {
        return oid > 0 ? ("oid" + oid) : "";
    }

    public static String blogId(long bid) {
        return bid > 0 ? ("ob" + bid) : "";
    }
}
