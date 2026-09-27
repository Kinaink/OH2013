package cn.ottohub.oh2013.util;

/**
 * OTTOhub 图片 URL 规范化与下载地址处理。
 */
public final class ImageUrlUtil {

  public static final String FILE_BASE = "https://file.ottohub.org";

  private ImageUrlUtil() {}

  public static String resolve(String url) {
    if (url == null) return null;
    url = url.trim();
    if (url.length() == 0) return null;
    if (url.startsWith("//")) {
      return "https:" + url;
    }
    if (url.startsWith("/")) {
      return FILE_BASE + url;
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return FILE_BASE + "/" + url;
    }
    return url;
  }

  public static String toHttpFallback(String url) {
    if (url != null && url.startsWith("https://")) {
      return "http://" + url.substring(8);
    }
    return url;
  }
}
