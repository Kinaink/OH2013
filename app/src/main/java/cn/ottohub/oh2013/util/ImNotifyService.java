package cn.ottohub.oh2013.util;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import cn.ottohub.oh2013.BaseActivity;
import cn.ottohub.oh2013.ImChatActivity;
import cn.ottohub.oh2013.MainActivity;
import cn.ottohub.oh2013.R;
import cn.ottohub.oh2013.api.OttoApiUtil;
import cn.ottohub.oh2013.api.OttoAuthApi;

/**
 * 私信未读轮询：后台保活轮询 unread-count / unread-list，
 * 弹出系统通知 + 前台对话框（含用户名、内容、头像）。无厂商离线推送。
 * 适配 Android 1.5+。
 */
public class ImNotifyService extends Service {

    public static final String KEY_IM_PUSH = "im_push_enabled";
    private static final String CHANNEL_ID = "im_notify";
    private static final int NOTIFY_ID = 3011;
    private static final int NOTIFY_FG = 3010;
    private static final long POLL_INTERVAL_MS = 2 * 1000L;
    /** 播放视频时降频，避免 5.x 设备卡顿 */
    private static final long POLL_INTERVAL_PLAYING_MS = 30 * 1000L;
    /** 进程被杀后用 Alarm 拉活的间隔（秒级轮询走 Handler） */
    private static final long KEEPALIVE_ALARM_MS = 60 * 1000L;
    private static final String PREF_LAST_MSG = "im_push_last_msg_id";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean checking;
    private volatile boolean running;
    /** 播放器前台时为 true：拉长轮询间隔 */
    private static volatile boolean sPlaybackActive;

    private final Runnable pollLoop = new Runnable() {
        public void run() {
            if (!running) return;
            pollOnce();
            if (running) {
                long delay = SdkHelper.imPollIntervalMs(sPlaybackActive);
                mainHandler.postDelayed(this, delay);
            }
        }
    };

    /** 播放页 onResume/onPause 调用，减轻 5.1 等机型卡顿 */
    public static void setPlaybackActive(boolean active) {
        sPlaybackActive = active;
    }

    public static boolean isEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_IM_PUSH, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferencesUtil.putBoolean(KEY_IM_PUSH, enabled);
        sync(context);
    }

    /** 按开关与登录态启动/停止轮询 */
    public static void sync(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        try {
            if (isEnabled() && OttoAuthApi.isLoggedIn()) {
                Intent i = new Intent(app, ImNotifyService.class);
                app.startService(i);
            } else {
                cancelAlarm(app);
                Intent i = new Intent(app, ImNotifyService.class);
                app.stopService(i);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled() || !OttoAuthApi.isLoggedIn()) {
            running = false;
            mainHandler.removeCallbacks(pollLoop);
            cancelAlarm(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Notification n = buildFgNotification();
                if (n != null) {
                    Service.class.getMethod("startForeground", int.class, Notification.class)
                            .invoke(this, NOTIFY_FG, n);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        running = true;
        mainHandler.removeCallbacks(pollLoop);
        mainHandler.post(pollLoop);
        scheduleKeepAlive(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        mainHandler.removeCallbacks(pollLoop);
        if (SdkHelper.getSdkInt() >= 26) {
            try {
                Service.class.getMethod("stopForeground", boolean.class).invoke(this, true);
            } catch (Exception e) {
                // ignore
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void pollOnce() {
        if (checking) return;
        checking = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    doPoll();
                } catch (Exception e) {
                    // ignore
                } finally {
                    checking = false;
                }
            }
        }).start();
    }

    private void doPoll() throws Exception {
        JSONObject countResp = OttoApiUtil.getJsonWithToken("/api/im/unread-count");
        if (countResp == null || !OttoApiUtil.isSuccess(countResp)) return;
        JSONObject cdata = countResp.optJSONObject("data");
        if (cdata == null) cdata = countResp;
        int num = OttoApiUtil.parseInt(cdata, "new_message_num");
        if (num <= 0) return;

        JSONObject listResp = OttoApiUtil.getJsonWithToken(
                "/api/im/unread-list?offset=0&num=1");
        if (listResp == null || !OttoApiUtil.isSuccess(listResp)) return;
        JSONObject data = listResp.optJSONObject("data");
        if (data == null) data = listResp;
        JSONArray arr = data.optJSONArray("message_list");
        if (arr == null || arr.length() == 0) return;

        JSONObject msg = arr.optJSONObject(0);
        if (msg == null) return;
        long msgId = OttoApiUtil.parseLong(msg, "msg_id");
        long last = SharedPreferencesUtil.getLong(PREF_LAST_MSG, 0);
        if (msgId > 0 && msgId <= last) return;
        if (msgId > 0) {
            SharedPreferencesUtil.putLong(PREF_LAST_MSG, msgId);
        }

        final long sender = OttoApiUtil.parseLong(msg, "sender");
        final String name = msg.optString("sender_name", "用户");
        final String content = msg.optString("content", "");
        final String avatar = msg.optString("sender_avatar_url", "");

        Bitmap largeIcon = null;
        if (avatar != null && avatar.length() > 0 && SdkHelper.getSdkInt() >= 11) {
            try {
                largeIcon = CoverImageLoader.download(ImNotifyService.this, avatar, 96, 96);
            } catch (Throwable t) {
                largeIcon = null;
            }
        }
        final Bitmap finalLarge = largeIcon;

        mainHandler.post(new Runnable() {
            public void run() {
                showNotification(sender, name, content, finalLarge);
                showPopupIfForeground(sender, name, content, avatar);
            }
        });
    }

    private void showNotification(long senderUid, String name, String content, Bitmap large) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent open = new Intent(this, ImChatActivity.class);
            open.putExtra("friend_uid", senderUid);
            open.putExtra("friend_name", name != null ? name : "");
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi;
            pi = PendingIntent.getActivity(this, (int) (senderUid & 0x7fffffff), open, SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));

            String title = name != null && name.length() > 0 ? name : "私信";
            String body = content != null ? content : "";
            String ticker = title + ": " + body;

            Notification notif = null;

            if (SdkHelper.getSdkInt() >= 26) {
                Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                Object builder = builderClass.getConstructor(Context.class, String.class)
                        .newInstance(this, CHANNEL_ID);
                builderClass.getMethod("setContentTitle", CharSequence.class).invoke(builder, title);
                builderClass.getMethod("setContentText", CharSequence.class).invoke(builder, body);
                builderClass.getMethod("setSmallIcon", int.class)
                        .invoke(builder, R.drawable.ic_launcher);
                builderClass.getMethod("setAutoCancel", boolean.class).invoke(builder, true);
                builderClass.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pi);
                builderClass.getMethod("setPriority", int.class).invoke(builder, 1);
                if (large != null) {
                    try {
                        builderClass.getMethod("setLargeIcon", Bitmap.class).invoke(builder, large);
                    } catch (Exception ignored) {
                    }
                }
                notif = (Notification) builderClass.getMethod("build").invoke(builder);
            } else {
                try {
                    Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                    Object builder = builderClass.getConstructor(Context.class).newInstance(this);
                    builderClass.getMethod("setContentTitle", CharSequence.class).invoke(builder, title);
                    builderClass.getMethod("setContentText", CharSequence.class).invoke(builder, body);
                    builderClass.getMethod("setSmallIcon", int.class)
                            .invoke(builder, R.drawable.ic_launcher);
                    builderClass.getMethod("setAutoCancel", boolean.class).invoke(builder, true);
                    builderClass.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pi);
                    if (large != null) {
                        try {
                            builderClass.getMethod("setLargeIcon", Bitmap.class).invoke(builder, large);
                        } catch (Exception ignored) {
                        }
                    }
                    notif = (Notification) builderClass.getMethod("getNotification").invoke(builder);
                } catch (Exception e) {
                    notif = new Notification(R.drawable.ic_launcher, ticker, System.currentTimeMillis());
                    try {
                        notif.getClass()
                                .getMethod("setLatestEventInfo", Context.class, CharSequence.class,
                                        CharSequence.class, PendingIntent.class)
                                .invoke(notif, this, title, body, pi);
                    } catch (Exception e2) {
                        // API 极老：仅 ticker
                    }
                }
            }
            if (notif != null) {
                nm.notify(NOTIFY_ID, notif);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void showPopupIfForeground(final long senderUid, final String name,
                                       final String content, final String avatarUrl) {
        Activity act = BaseActivity.getResumedActivity();
        if (act == null || act.isFinishing()) return;
        try {
            View view = LayoutInflater.from(act).inflate(R.layout.dialog_im_notify, null);
            ImageView iv = (ImageView) view.findViewById(R.id.im_notify_avatar);
            TextView tvName = (TextView) view.findViewById(R.id.im_notify_name);
            TextView tvContent = (TextView) view.findViewById(R.id.im_notify_content);
            tvName.setText(name != null ? name : "用户");
            tvContent.setText(content != null ? content : "");
            if (iv != null && avatarUrl != null && avatarUrl.length() > 0) {
                int size = (int) (act.getResources().getDisplayMetrics().density * 48);
                CoverImageLoader.loadIntoCircle(act, iv, avatarUrl, size);
            }

            final Activity host = act;
            new AlertDialog.Builder(DialogUtil.wrap(host))
                    .setTitle("新私信")
                    .setView(view)
                    .setPositiveButton("查看", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = new Intent(host, ImChatActivity.class);
                            i.putExtra("friend_uid", senderUid);
                            i.putExtra("friend_name", name != null ? name : "");
                            host.startActivity(i);
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            // ignore
        }
    }

    private Notification buildFgNotification() {
        try {
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi;
            pi = PendingIntent.getActivity(this, 0, open, SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            Class<?> builderClass = Class.forName("android.app.Notification$Builder");
            Object builder = builderClass.getConstructor(Context.class, String.class)
                    .newInstance(this, CHANNEL_ID);
            builderClass.getMethod("setContentTitle", CharSequence.class)
                    .invoke(builder, "私信监听");
            builderClass.getMethod("setContentText", CharSequence.class)
                    .invoke(builder, "后台接收新消息");
            builderClass.getMethod("setSmallIcon", int.class)
                    .invoke(builder, R.drawable.ic_launcher);
            builderClass.getMethod("setOngoing", boolean.class).invoke(builder, true);
            builderClass.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pi);
            builderClass.getMethod("setPriority", int.class).invoke(builder, -2);
            return (Notification) builderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            return null;
        }
    }

    private void createNotificationChannel() {
        if (SdkHelper.getSdkInt() < 26) return;
        try {
            Object manager = getSystemService("notification");
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Object channel = channelClass.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "私信消息", 3);
            channelClass.getMethod("setShowBadge", boolean.class).invoke(channel, true);
            manager.getClass().getMethod("createNotificationChannel", channelClass)
                    .invoke(manager, channel);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void scheduleKeepAlive(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(context, ImNotifyService.class);
            PendingIntent pi;
            pi = PendingIntent.getService(context, 99, intent, SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            long at = System.currentTimeMillis() + KEEPALIVE_ALARM_MS;
            if (SdkHelper.getSdkInt() >= 19) {
                try {
                    am.getClass().getMethod("setExact", int.class, long.class, PendingIntent.class)
                            .invoke(am, AlarmManager.RTC_WAKEUP, at, pi);
                } catch (Exception e) {
                    am.set(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /** @deprecated 使用 scheduleKeepAlive；保留别名避免旧调用报错 */
    public static void scheduleNext(Context context) {
        scheduleKeepAlive(context);
    }

    private static void cancelAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(context, ImNotifyService.class);
            PendingIntent pi;
            pi = PendingIntent.getService(context, 99, intent, SdkHelper.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            am.cancel(pi);
        } catch (Exception e) {
            // ignore
        }
    }
}
