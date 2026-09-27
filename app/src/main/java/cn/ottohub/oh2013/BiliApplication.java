package cn.ottohub.oh2013;

import android.app.Application;
import android.os.Handler;
import android.util.Log;

import java.io.File;

import cn.ottohub.oh2013.util.CrashHandler;
import cn.ottohub.oh2013.util.LocaleHelper;
import cn.ottohub.oh2013.util.SdkHelper;
import cn.ottohub.oh2013.util.QRCodeUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
import cn.ottohub.oh2013.util.UpdateCheckService;

public class BiliApplication extends Application {

    private cn.ottohub.oh2013.util.StorageFallbackContext mFallbackContext;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        // 注意：不能把这个 ContextWrapper 设为 base——framework（ActivityThread.handleReceiver）
        // 会强转 app.getBaseContext() 为 ContextImpl，包装过会让所有 manifest receiver 崩溃。
        // 这里保留原始 ContextImpl 给 framework，仅保留 wrapper 供 getCacheDir()/getFilesDir() 覆写用。
        super.attachBaseContext(base);
        mFallbackContext = new cn.ottohub.oh2013.util.StorageFallbackContext(base);
    }

    @Override
    public java.io.File getCacheDir() {
        return (mFallbackContext != null) ? mFallbackContext.getCacheDir() : super.getCacheDir();
    }

    @Override
    public java.io.File getFilesDir() {
        return (mFallbackContext != null) ? mFallbackContext.getFilesDir() : super.getFilesDir();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferencesUtil.init(this);
        LocaleHelper.init(this);
        if (SdkHelper.getSdkInt() < 17) {
            LocaleHelper.updateResourcesLocale(this);
        }
        CrashHandler.getInstance().init(this);
        QRCodeUtil.init(this);
        // 应用内日志（无 adb 用户排查用）：默认关闭，避免日志写入影响性能。
        // 开启后注入弹幕引擎诊断 sink，弹幕绘制耗时等写入日志文件。
        cn.ottohub.oh2013.util.LogFileUtil.init(this);
        master.flame.danmaku.util.DiagLogger.setSink(new master.flame.danmaku.util.DiagLogger.LogSink() {
            public void log(String tag, String msg) {
                cn.ottohub.oh2013.util.LogFileUtil.diag(tag, msg);
            }
        });

        // 延迟启动更新检查（5秒后，不影响启动速度）
        if (SharedPreferencesUtil.getBoolean("auto_check_update", true)) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    UpdateCheckService.schedule(BiliApplication.this);
                }
            }, 5000);
        }

        // 私信推送保活（登录且设置开启时）
        new Handler().postDelayed(new Runnable() {
            public void run() {
                cn.ottohub.oh2013.util.ImNotifyService.sync(BiliApplication.this);
            }
        }, 3000);
    }
}