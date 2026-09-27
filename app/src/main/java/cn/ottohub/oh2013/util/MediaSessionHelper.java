package cn.ottohub.oh2013.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/**
 * 媒体会话门面：本体不含 API 21+ 类型引用，避免在 Android 5.0 以下加载时 VerifyError。
 * 真实实现见 {@link MediaSessionHelperApi21}（仅 SDK≥21 时反射加载）。
 */
public class MediaSessionHelper {

    public interface PlayPauseListener {
        void onPlayPause();
    }

    private final MediaSessionHelper delegate;

    public MediaSessionHelper(Context context, Class<?> activityClass) {
        MediaSessionHelper impl = null;
        if (SdkHelper.getSdkInt() >= 21) {
            try {
                Class<?> cl = Class.forName("cn.ottohub.oh2013.util.MediaSessionHelperApi21");
                impl = (MediaSessionHelper) cl
                        .getConstructor(Context.class, Class.class, Boolean.TYPE)
                        .newInstance(context, activityClass, Boolean.TRUE);
            } catch (Throwable t) {
                impl = null;
            }
        }
        this.delegate = impl;
    }

    /** 供 Api21 子类调用的空构造路径 */
    protected MediaSessionHelper() {
        this.delegate = null;
    }

    public void setPlayPauseListener(PlayPauseListener listener) {
        if (delegate != null) delegate.setPlayPauseListener(listener);
    }

    public void setMetadata(String title, String artist) {
        if (delegate != null) delegate.setMetadata(title, artist);
    }

    public void setCoverBitmap(Bitmap bitmap) {
        if (delegate != null) delegate.setCoverBitmap(bitmap);
    }

    public void setPlaying(boolean playing) {
        if (delegate != null) delegate.setPlaying(playing);
    }

    public void updatePlaybackPosition(long position, long duration) {
        if (delegate != null) delegate.updatePlaybackPosition(position, duration);
    }

    public void hideNotification() {
        if (delegate != null) delegate.hideNotification();
    }

    public void release() {
        if (delegate != null) delegate.release();
    }
}
