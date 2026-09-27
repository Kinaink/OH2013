package cn.ottohub.oh2013.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 封面/头像异步加载。支持 GIF（Movie 逐帧）与圆形裁剪。
 */
public final class CoverImageLoader {

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static ExecutorService EXECUTOR;

  private CoverImageLoader() {}

  private static synchronized ExecutorService executor() {
    if (EXECUTOR == null) {
      int n = SdkHelper.getImageLoadThreads();
      if (n < 1) n = 1;
      if (n > 2 && SdkHelper.isAncientDevice()) n = 1;
      EXECUTOR = Executors.newFixedThreadPool(n);
    }
    return EXECUTOR;
  }

  public static void loadInto(
      final Context context,
      final ImageView target,
      String url,
      final int maxWidth,
      final int maxHeight) {
    loadInto(context, target, url, maxWidth, maxHeight, false);
  }

  public static void loadIntoCircle(
      final Context context,
      final ImageView target,
      String url,
      final int size) {
    loadInto(context, target, url, size, size, true);
  }

  public static void loadInto(
      final Context context,
      final ImageView target,
      String url,
      final int maxWidth,
      final int maxHeight,
      final boolean circle) {
    if (context == null || target == null) return;
    if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;

    final String resolved = ImageUrlUtil.resolve(url);
    if (resolved == null || resolved.length() == 0) return;

    final String tag = resolved + (circle ? "#c" : "");
    target.setTag(tag);
    // GIF/动图：URL 带 .gif 直接走 Movie；否则下载后按魔数判断
    final boolean urlLooksGif = isGifUrl(resolved);

    if (!urlLooksGif) {
      Bitmap cached = GlobalImageCache.getInstance().get(tag);
      if (cached != null && !cached.isRecycled()) {
        target.setImageBitmap(cached);
        return;
      }
    }

    executor().execute(
        new Runnable() {
          @Override
          public void run() {
            final byte[] data = downloadBytes(context, resolved);
            if (data != null && data.length > 0 && (urlLooksGif || isGifBytes(data))) {
              MAIN.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Object t = target.getTag();
                      if (t == null || !tag.equals(t.toString())) return;
                      applyGif(target, data, maxWidth, maxHeight, circle);
                    }
                  });
              return;
            }

            // APNG：Android Movie 不支持，退回静态首帧（仍显示图）
            Bitmap bitmap = null;
            if (data != null && data.length > 0) {
              try {
                android.graphics.BitmapFactory.Options opts =
                    new android.graphics.BitmapFactory.Options();
                opts.inPreferredConfig = SdkHelper.preferredBitmapConfig();
                // 旧机允许系统回收 bitmap 像素，降低 OOM
                try {
                    java.lang.reflect.Field f = opts.getClass().getField("inPurgeable");
                    f.setBoolean(opts, true);
                    java.lang.reflect.Field f2 = opts.getClass().getField("inInputShareable");
                    f2.setBoolean(opts, true);
                } catch (Throwable t) {
                }
                bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                if (bitmap != null && (maxWidth > 0 || maxHeight > 0)) {
                  int bw = bitmap.getWidth();
                  int bh = bitmap.getHeight();
                  float sx = maxWidth > 0 ? (float) maxWidth / bw : 1f;
                  float sy = maxHeight > 0 ? (float) maxHeight / bh : 1f;
                  float scale = Math.min(sx, sy);
                  if (scale > 0 && scale < 1f) {
                    int nw = Math.max(1, (int) (bw * scale));
                    int nh = Math.max(1, (int) (bh * scale));
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                    if (scaled != bitmap) {
                      try { bitmap.recycle(); } catch (Throwable ignored) {}
                      bitmap = scaled;
                    }
                  }
                }
              } catch (Throwable ignored) {
              }
            }
            if (bitmap == null || bitmap.isRecycled()) {
              bitmap = download(context, resolved, maxWidth, maxHeight);
            }
            if (bitmap == null || bitmap.isRecycled()) return;
            if (circle) {
              Bitmap rounded = toCircle(bitmap);
              if (rounded != null) {
                if (rounded != bitmap) {
                  try {
                    bitmap.recycle();
                  } catch (Throwable ignored) {
                  }
                }
                bitmap = rounded;
              }
            }
            final Bitmap resultBitmap = bitmap;
            if (!isApngBytes(data)) {
              GlobalImageCache.getInstance().put(tag, resultBitmap);
            }
            MAIN.post(
                new Runnable() {
                  @Override
                  public void run() {
                    Object t = target.getTag();
                    if (t == null || !tag.equals(t.toString())) return;
                    target.setImageBitmap(resultBitmap);
                  }
                });
          }
        });
  }

  public static Bitmap download(Context context, String url, int maxWidth, int maxHeight) {
    Bitmap bitmap = downloadOnce(context, url, maxWidth, maxHeight);
    if (bitmap != null) return bitmap;
    String httpUrl = ImageUrlUtil.toHttpFallback(url);
    if (httpUrl != null && !httpUrl.equals(url)) {
      return downloadOnce(context, httpUrl, maxWidth, maxHeight);
    }
    return null;
  }

  /** 供灯箱等判断 URL 是否像 GIF（含 query 前的路径） */
  public static boolean isLikelyGifUrl(String url) {
    return isGifUrl(ImageUrlUtil.resolve(url));
  }

  private static boolean isGifUrl(String url) {
    if (url == null) return false;
    String lower = url.toLowerCase();
    int q = lower.indexOf('?');
    if (q > 0) lower = lower.substring(0, q);
    // 常见 CDN：xxx.gif / xxx.gif.webp / format=gif
    return lower.endsWith(".gif")
        || lower.indexOf(".gif.") >= 0
        || lower.indexOf("format=gif") >= 0
        || lower.indexOf("image/gif") >= 0;
  }

  /** 根据文件头判断 GIF（GIF87a / GIF89a） */
  private static boolean isGifBytes(byte[] data) {
    return data != null
        && data.length >= 6
        && data[0] == 'G'
        && data[1] == 'I'
        && data[2] == 'F'
        && data[3] == '8'
        && (data[4] == '7' || data[4] == '9')
        && data[5] == 'a';
  }

  /** APNG：PNG 签名后含 acTL chunk（动画 PNG） */
  private static boolean isApngBytes(byte[] data) {
    if (data == null || data.length < 41) return false;
    // PNG signature
    if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') {
      return false;
    }
    // 扫描前若干字节找 acTL
    int limit = Math.min(data.length - 4, 512);
    for (int i = 8; i < limit; i++) {
      if (data[i] == 'a' && data[i + 1] == 'c' && data[i + 2] == 'T' && data[i + 3] == 'L') {
        return true;
      }
    }
    return false;
  }

  private static void applyGif(
      ImageView target, byte[] data, int maxWidth, int maxHeight, boolean circle) {
    try {
      Movie movie = Movie.decodeByteArray(data, 0, data.length);
      if (movie == null) {
        Bitmap bmp =
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bmp != null) {
          if (circle) bmp = toCircle(bmp);
          if (bmp != null) {
            target.setBackgroundColor(0x00000000);
            target.setImageBitmap(bmp);
            lockImageViewToContentHeight(target, bmp.getHeight(), maxHeight, circle);
          }
        }
        return;
      }
      // 彻底去掉灰底占位
      target.setBackgroundColor(0x00000000);
      try {
        target.setBackgroundDrawable(null);
      } catch (Throwable ignored) {
      }
      target.setMinimumHeight(0);
      target.setMinimumWidth(0);
      target.setPadding(0, 0, 0, 0);
      boolean isPhotoView = false;
      try {
        isPhotoView = target.getClass().getName().indexOf("PhotoView") >= 0;
      } catch (Throwable ignored) {
      }
      if (!isPhotoView) {
        target.setAdjustViewBounds(true);
        target.setScaleType(ImageView.ScaleType.FIT_CENTER);
      }
      // 软件层：Movie 在硬件加速下常画出不透明灰底（API 11+，反射防 VerifyError）
      try {
        cn.ottohub.oh2013.util.SdkHelper.setSoftwareLayer(target);
      } catch (Throwable ignored) {
      }
      GifMovieDrawable drawable = new GifMovieDrawable(movie, maxWidth, maxHeight, circle);
      target.setImageDrawable(drawable);
      if (!circle && !isPhotoView && !(maxWidth > 0 && maxHeight > 0 && maxWidth == maxHeight)) {
        // 固定方图（列表封面 72dp 等）勿改 LayoutParams，否则回收后会 MATCH_PARENT 错乱
        lockImageViewToContentHeight(target, drawable.getIntrinsicHeight(), maxHeight, false);
      }
      target.invalidate();
    } catch (Throwable t) {
      // ignore
    }
  }

  /** 把 ImageView 高度锁到内容高度，避免 MATCH_PARENT / 测量错误留下整屏灰条 */
  private static void lockImageViewToContentHeight(
      ImageView target, int contentH, int maxHeight, boolean circle) {
    if (target == null || circle) return;
    android.view.ViewGroup.LayoutParams lp = target.getLayoutParams();
    android.view.ViewParent parent = target.getParent();
    int height = contentH;
    if (height <= 0) {
      height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
    } else if (maxHeight > 0 && height > maxHeight) {
      height = maxHeight;
    }
    if (lp == null) {
      if (parent instanceof android.widget.LinearLayout) {
        lp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, height);
      } else if (parent instanceof android.widget.FrameLayout) {
        lp = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, height);
      } else {
        lp = new android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, height);
      }
    } else {
      lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
      lp.height = height;
    }
    try {
      target.setMaxHeight(height > 0 ? height : Integer.MAX_VALUE);
    } catch (Throwable ignored) {
    }
    target.setLayoutParams(lp);
    target.requestLayout();
    // 父级若是仅包一层的 FrameLayout，也锁高度
    if (parent instanceof android.widget.FrameLayout) {
      android.view.ViewGroup pg = (android.view.ViewGroup) parent;
      if (pg.getChildCount() == 1) {
        android.view.ViewGroup.LayoutParams plp = pg.getLayoutParams();
        if (plp != null) {
          plp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
          pg.setLayoutParams(plp);
        }
      }
    }
  }

  private static byte[] downloadBytes(Context context, String urlStr) {
    HttpURLConnection conn = null;
    try {
      conn = NetWorkUtil.openCompat(urlStr);
      NetWorkUtil.applySSLCompat(conn, urlStr);
      conn.setConnectTimeout(12000);
      conn.setReadTimeout(12000);
      conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
      conn.setRequestProperty("Accept-Encoding", "identity");
      conn.connect();
      if (conn.getResponseCode() >= 400) return null;
      InputStream is = conn.getInputStream();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = is.read(buf)) != -1) {
        bos.write(buf, 0, n);
      }
      is.close();
      return bos.toByteArray();
    } catch (Exception e) {
      return null;
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static Bitmap downloadOnce(Context context, String urlStr, int maxWidth, int maxHeight) {
    if (urlStr == null || urlStr.length() == 0) return null;
    HttpURLConnection conn = null;
    File tempFile = null;
    try {
      conn = NetWorkUtil.openCompat(urlStr);
      NetWorkUtil.applySSLCompat(conn, urlStr);
      conn.setConnectTimeout(12000);
      conn.setReadTimeout(12000);
      conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
      conn.setRequestProperty("Accept-Encoding", "identity");
      // 不设 Referer：OSS/CDN 签名图常因 Referer 校验失败导致封面空白
      conn.connect();

      int code = conn.getResponseCode();
      if (code >= 400) return null;

      if (context == null) return null;
      tempFile = new File(context.getCacheDir(), "cover_" + Math.abs(urlStr.hashCode()) + ".tmp");
      InputStream is = conn.getInputStream();
      FileOutputStream fos = new FileOutputStream(tempFile);
      byte[] buf = new byte[8192];
      int len;
      while ((len = is.read(buf)) != -1) {
        fos.write(buf, 0, len);
      }
      is.close();
      fos.close();

      if (!tempFile.exists() || tempFile.length() == 0) return null;
      int minScale = SdkHelper.getSdkInt() >= 9 ? 2 : 4;
      return GlobalImageCache.decodeFileSafely(tempFile, maxWidth, maxHeight, minScale);
    } catch (Exception e) {
      return null;
    } finally {
      if (conn != null) conn.disconnect();
      if (tempFile != null && tempFile.exists()) {
        tempFile.delete();
      }
    }
  }

  public static Bitmap toCircle(Bitmap src) {
    if (src == null || src.isRecycled()) return null;
    int size = Math.min(src.getWidth(), src.getHeight());
    if (size <= 0) return src;
    Bitmap output;
    try {
      output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    } catch (OutOfMemoryError e) {
      return src;
    }
    Canvas canvas = new Canvas(output);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    paint.setShader(shader);
    float left = (src.getWidth() - size) / 2f;
    float top = (src.getHeight() - size) / 2f;
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setTranslate(-left, -top);
    shader.setLocalMatrix(matrix);
    canvas.drawOval(new RectF(0, 0, size, size), paint);
    return output;
  }

  /** 简易 GIF Drawable（Movie）：先画到透明 Bitmap 再贴出，去掉 Movie 自带灰底 */
  private static final class GifMovieDrawable extends Drawable implements Runnable {
    private final Movie movie;
    private final int drawW;
    private final int drawH;
    private final boolean circle;
    private long start;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap frameBitmap;
    private Canvas frameCanvas;

    GifMovieDrawable(Movie movie, int maxW, int maxH, boolean circle) {
      this.movie = movie;
      this.circle = circle;
      this.start = SystemClock.uptimeMillis();
      int mw = movie.width();
      int mh = movie.height();
      if (mw <= 0) mw = 1;
      if (mh <= 0) mh = 1;
      float sx = maxW > 0 ? (float) maxW / mw : 1f;
      float sy = maxH > 0 ? (float) maxH / mh : 1f;
      float scale = Math.min(sx, sy);
      if (scale <= 0f) scale = 1f;
      if (!circle && scale > 1f) scale = 1f;
      this.drawW = Math.max(1, Math.round(mw * scale));
      this.drawH = Math.max(1, Math.round(mh * scale));
      try {
        frameBitmap = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
        frameCanvas = new Canvas(frameBitmap);
      } catch (Throwable t) {
        frameBitmap = null;
        frameCanvas = null;
      }
    }

    @Override
    public void draw(Canvas canvas) {
      if (movie == null) return;
      long now = SystemClock.uptimeMillis();
      int dur = movie.duration();
      if (dur <= 0) dur = 1000;
      int rel = (int) ((now - start) % dur);
      movie.setTime(rel);

      int mw = movie.width();
      int mh = movie.height();
      if (mw <= 0 || mh <= 0) return;

      android.graphics.Rect bounds = getBounds();
      int bw = drawW;
      int bh = drawH;
      // 高度绝不超过固有高度，防止 ImageView 过高时露出灰条
      if (bounds.width() > 0 && bounds.width() < bw) bw = bounds.width();
      if (bounds.height() > 0 && bounds.height() < bh) bh = bounds.height();
      if (bw <= 0) bw = drawW;
      if (bh <= 0) bh = drawH;

      float left = bounds.left + Math.max(0, (bounds.width() - bw) / 2f);
      float top = bounds.top;

      // 先画到透明帧，避免 Movie.draw 直接往 View 上铺不透明灰底
      if (frameBitmap != null && frameCanvas != null
          && frameBitmap.getWidth() == mw && frameBitmap.getHeight() == mh) {
        frameBitmap.eraseColor(0x00000000);
        movie.draw(frameCanvas, 0, 0);
      }

      canvas.save();
      canvas.clipRect(left, top, left + bw, top + bh);
      if (circle) {
        float d = Math.min(bw, bh);
        canvas.translate(left + (bw - d) / 2f, top + (bh - d) / 2f);
        canvas.clipPath(circlePath(d));
        float cscale = d / Math.max(mw, mh);
        canvas.scale(cscale, cscale);
        if (frameBitmap != null) {
          canvas.drawBitmap(frameBitmap, 0, 0, paint);
        } else {
          movie.draw(canvas, 0, 0);
        }
      } else {
        float sx = (float) bw / mw;
        float sy = (float) bh / mh;
        float scale = Math.min(sx, sy);
        if (scale <= 0) scale = 1f;
        float dw = mw * scale;
        float dh = mh * scale;
        canvas.translate(left + (bw - dw) / 2f, top + (bh - dh) / 2f);
        canvas.scale(scale, scale);
        if (frameBitmap != null) {
          canvas.drawBitmap(frameBitmap, 0, 0, paint);
        } else {
          movie.draw(canvas, 0, 0);
        }
      }
      canvas.restore();

      MAIN.postDelayed(this, 16);
      invalidateSelf();
    }

    private static android.graphics.Path circlePath(float d) {
      android.graphics.Path p = new android.graphics.Path();
      p.addOval(new RectF(0, 0, d, d), android.graphics.Path.Direction.CW);
      return p;
    }

    @Override
    public void run() {
      invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter cf) {
      paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
      return drawW;
    }

    @Override
    public int getIntrinsicHeight() {
      return drawH;
    }
  }
}
