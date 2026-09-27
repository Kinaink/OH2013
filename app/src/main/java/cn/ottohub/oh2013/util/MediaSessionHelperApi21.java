package cn.ottohub.oh2013.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

import cn.ottohub.oh2013.R;

/**
 * API 21+ 媒体会话实现。禁止在 SDK&lt;21 设备上直接引用此类（由 {@link MediaSessionHelper} 反射加载）。
 */
public class MediaSessionHelperApi21 extends MediaSessionHelper {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "media_playback";

    private final Context context;
    private final Class<?> activityClass;
    private MediaSession mediaSession;
    private NotificationManager notificationManager;
    private String title = "";
    private String artist = "";
    private Bitmap coverBitmap;
    private Bitmap defaultLargeIcon;
    private boolean isPlaying;
    private PlayPauseListener listener;
    private BroadcastReceiver playPauseReceiver;

    public MediaSessionHelperApi21(Context context, Class<?> activityClass, boolean ignored) {
        super();
        this.context = context;
        this.activityClass = activityClass;

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Class<?> channelClass = Class.forName("android.app.NotificationChannel");
                Object channel = channelClass
                        .getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(CHANNEL_ID, "媒体播放", 3);
                channelClass.getMethod("setShowBadge", boolean.class).invoke(channel, false);
                try {
                    channelClass.getMethod("setLockscreenVisibility", int.class)
                            .invoke(channel, Notification.VISIBILITY_PUBLIC);
                } catch (Throwable ignored2) {
                }
                notificationManager.getClass()
                        .getMethod("createNotificationChannel", channelClass)
                        .invoke(notificationManager, channel);
            } catch (Throwable t) {
            }
        }

        defaultLargeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);

        mediaSession = new MediaSession(context, "BiliClassicPlayer");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            || event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                        notifyListener();
                        return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

            @Override
            public void onPlay() {
                notifyListener();
            }

            @Override
            public void onPause() {
                notifyListener();
            }
        });
        mediaSession.setActive(true);

        playPauseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListener();
            }
        };
        context.registerReceiver(playPauseReceiver,
                new IntentFilter("cn.ottohub.oh2013.ACTION_MEDIA_PLAY_PAUSE"));
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onPlayPause();
        }
    }

    @Override
    public void setPlayPauseListener(PlayPauseListener listener) {
        this.listener = listener;
    }

    @Override
    public void setMetadata(String title, String artist) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        if (mediaSession != null) {
            android.media.MediaMetadata.Builder builder = new android.media.MediaMetadata.Builder();
            builder.putString(android.media.MediaMetadata.METADATA_KEY_TITLE, this.title);
            builder.putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, this.artist);
            builder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 0);
            if (coverBitmap != null && !coverBitmap.isRecycled()) {
                builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap);
            }
            mediaSession.setMetadata(builder.build());
        }
        showNotification();
    }

    @Override
    public void setCoverBitmap(Bitmap bitmap) {
        this.coverBitmap = bitmap;
    }

    @Override
    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (mediaSession != null) {
            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setState(state, 0, 1.0f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
        showNotification();
    }

    @Override
    public void updatePlaybackPosition(long position, long duration) {
        if (mediaSession == null) return;
        int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setState(state, position, 1.0f)
                .setActions(PlaybackState.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void showNotification() {
        if (notificationManager == null) return;

        Intent activityIntent = new Intent(context, activityClass);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, activityIntent,
                SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));

        Intent playPauseIntent = new Intent("cn.ottohub.oh2013.ACTION_MEDIA_PLAY_PAUSE");
        PendingIntent playPausePending = PendingIntent.getBroadcast(context, 1, playPauseIntent,
                SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));

        int playIcon = isPlaying ? R.drawable.bili_player_play_can_pause
                : R.drawable.bili_player_play_can_play;

        Notification.Builder builder;
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                builder = Notification.Builder.class
                        .getConstructor(Context.class, String.class)
                        .newInstance(context, CHANNEL_ID);
            } catch (Throwable t) {
                builder = new Notification.Builder(context);
            }
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setShowWhen(false);

        Bitmap largeIcon = (coverBitmap != null && !coverBitmap.isRecycled())
                ? coverBitmap : defaultLargeIcon;
        if (largeIcon != null && !largeIcon.isRecycled()) {
            builder.setLargeIcon(largeIcon);
        }

        try {
            builder.getClass().getMethod("setColor", int.class).invoke(builder, 0xff212121);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> styleClass = Class.forName("android.app.Notification$MediaStyle");
            Object mediaStyle = styleClass.newInstance();
            styleClass.getMethod("setMediaSession",
                    Class.forName("android.media.session.MediaSession$Token"))
                    .invoke(mediaStyle, mediaSession.getSessionToken());
            styleClass.getMethod("setShowActionsInCompactView", int[].class)
                    .invoke(mediaStyle, new int[]{0});
            builder.setStyle((Notification.Style) mediaStyle);
        } catch (Throwable t) {
            // 无 MediaStyle 时仍显示基础通知
        }
        builder.addAction(playIcon, isPlaying ? "暂停" : "播放", playPausePending);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void hideNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void release() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        hideNotification();
        if (playPauseReceiver != null) {
            try {
                context.unregisterReceiver(playPauseReceiver);
            } catch (Exception ignored) {
            }
            playPauseReceiver = null;
        }
        if (defaultLargeIcon != null && !defaultLargeIcon.isRecycled()) {
            defaultLargeIcon.recycle();
            defaultLargeIcon = null;
        }
    }
}
