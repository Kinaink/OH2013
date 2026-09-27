package cn.ottohub.oh2013.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cn.ottohub.oh2013.R;

public class DeviceUtil {

    private static final String TAG = "DeviceUtil";

    private static final String KEY_FORCE_TV_MODE = "force_tv_mode";

    public static boolean isForceTvModeEnabled() {
        return false;
    }

    /**
     * 已废弃独立 TV UI：电视与手机共用同一套界面，仅靠按键绑定做遥控器适配。
     * 始终返回 false，避免跳转 TvMainActivity / 强制横屏全屏。
     */
    public static boolean isTv(Context context) {
        return false;
    }

    // 获取 SDK_INT，兼容 Android 2.2
    public static int getSdkInt() {
        try {
            Field field = Build.VERSION.class.getField("SDK_INT");
            return field.getInt(null);
        } catch (Exception e) {
            // Android 2.2 及以下没有 SDK_INT，使用 VERSION.SDK
            try {
                Field field = Build.VERSION.class.getField("SDK");
                return Integer.parseInt(field.get(null).toString());
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    // 检测系统特征（兼容 Android 2.2）。featureName 传真实 feature 字符串
    //（如 "android.hardware.touchscreen"），不要传常量名——FEATURE_KEYBOARD 等
    // 在 PackageManager 里没有同名常量字段，反射常量名会误报"字段不存在"。
    private static boolean hasSystemFeature(PackageManager pm, String feature) {
        try {
            Method method = PackageManager.class.getMethod("hasSystemFeature", String.class);
            Boolean result = (Boolean) method.invoke(pm, feature);
            return result != null && result.booleanValue();
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "hasSystemFeature: hasSystemFeature 方法不存在 (Android 2.2 及以下)");
            return false;
        } catch (Exception e) {
            Log.d(TAG, "hasSystemFeature: " + feature + " 检测异常 - " + e.getMessage());
            return false;
        }
    }

    // 显示 Toast
    private static void showToast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "showToast 失败: " + e.getMessage());
        }
    }

    public static boolean isLeanbackSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasSystemFeature(pm, "android.software.leanback");
    }

    public static boolean isTouchScreen(Context context) {
        PackageManager pm = context.getPackageManager();
        return hasSystemFeature(pm, "android.hardware.touchscreen");
    }

    /**
     * 设备是否有物理按键（硬件键盘 / 方向键）。用于判断是否需要引导按键绑定：
     * 纯触屏机（无键盘、无方向键）直接跳过绑定流程。
     * Configuration.keyboard / navigation 均为 API 1，Android 1.5 可用。
     */
    public static boolean hasHardwareKeys(android.content.Context context) {
        try {
            android.content.res.Configuration cfg = context.getResources().getConfiguration();
            if (cfg.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS) {
                return true;
            }
            if (cfg.navigation == android.content.res.Configuration.NAVIGATION_DPAD
                    || cfg.navigation == android.content.res.Configuration.NAVIGATION_WHEEL) {
                return true;
            }
        } catch (Throwable t) {
        }
        return false;
    }

    /**
     * 是否为圆形屏幕（手表等），仅真圆屏返回 true，方形手表保持 false。
     * Wear 标准信号是 WindowInsets.isRound()（API 23+），需在 insets 传递完成后调用
     * （即 View 已 attach/布局后）；Configuration.isScreenRound() 作为辅助信号。
     * 均反射调用避免 minSdk 3 平台 VerifyError。
     *
     * @param view 已 attach 的任意视图（getRootWindowInsets 要求已附加）
     */
    public static boolean isRoundScreen(android.view.View view) {
        // 1. WindowInsets.isRound()（Wear 标准信号）
        try {
            java.lang.reflect.Method gm = android.view.View.class.getMethod("getRootWindowInsets");
            Object insets = gm.invoke(view);
            if (insets != null) {
                java.lang.reflect.Method im = insets.getClass().getMethod("isRound");
                Object r = im.invoke(insets);
                Log.d(TAG, "isRound: WindowInsets.isRound()=" + r);
                if (Boolean.TRUE.equals(r)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "isRound: WindowInsets error=" + t);
        }
        // 2. Configuration.isScreenRound() 辅助信号
        try {
            java.lang.reflect.Method m =
                    android.content.res.Configuration.class.getMethod("isScreenRound");
            Object cfg = view.getResources().getConfiguration();
            Object r = m.invoke(cfg);
            Log.d(TAG, "isRound: Configuration.isScreenRound()=" + r);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            Log.d(TAG, "isRound: Configuration error=" + t);
        }
        return false;
    }

    /**
     * 圆形屏幕适配：自动定位标题栏（含 btn_back 的容器）、返回按钮、标题文字，
     * 再把返回按钮+标题整组居中。标题 = 容器内第一个非返回按钮的 TextView。
     *
     * @param activity 页面 Activity
     */
    public static void centerTitleBarForRoundScreen(android.app.Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            android.view.View back = activity.findViewById(R.id.btn_back);
            if (back == null) {
                return;
            }
            android.view.ViewGroup titleBar = (android.view.ViewGroup) back.getParent();
            if (titleBar == null) {
                return;
            }
            android.view.View title = null;
            int n = titleBar.getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View v = titleBar.getChildAt(i);
                if (v == back) {
                    continue;
                }
                if (v instanceof android.widget.TextView) {
                    title = v;
                    break;
                }
            }
            if (title == null) {
                return;
            }
            centerTitleBarForRoundScreen(titleBar, back, title);
        } catch (Throwable t) {
        }
    }

    /**
     * 圆形屏幕（手表）适配：把"返回按钮 + 标题"作为一组整体居中。
     * 原布局返回按钮靠左、标题居中；圆屏时改为返回按钮紧贴标题左侧，整组水平居中。
     * 用不可见锚点 view 占屏幕中心，返回按钮在其左侧、标题在其右侧。
     *
     * @param titleBar 标题栏 RelativeLayout
     * @param backBtn  返回按钮（ImageButton/ImageView）
     * @param title    标题 TextView
     */
    public static void centerTitleBarForRoundScreen(android.view.ViewGroup titleBar,
                                                    android.view.View backBtn,
                                                    android.view.View title) {
        if (titleBar == null || backBtn == null || title == null) {
            return;
        }
        try {
            // 锚点（不可见，占屏幕中心）
            android.view.View anchor = titleBar.findViewById(R.id.round_bar_anchor);
            if (anchor == null) {
                anchor = new android.view.View(titleBar.getContext());
                anchor.setId(R.id.round_bar_anchor);
                anchor.setVisibility(android.view.View.INVISIBLE);
                android.widget.RelativeLayout.LayoutParams ap =
                        new android.widget.RelativeLayout.LayoutParams(1, 1);
                ap.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                ap.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                anchor.setLayoutParams(ap);
                titleBar.addView(anchor);
            }

            int gap = (int) (titleBar.getContext().getResources()
                    .getDisplayMetrics().density * 6 + 0.5f);

            android.widget.RelativeLayout.LayoutParams blp =
                    (android.widget.RelativeLayout.LayoutParams) backBtn.getLayoutParams();
            blp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT, 0);
            blp.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL, 0);
            blp.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT, 0);
            blp.addRule(android.widget.RelativeLayout.LEFT_OF, R.id.round_bar_anchor);
            blp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
            blp.rightMargin = gap;
            backBtn.setLayoutParams(blp);

            android.widget.RelativeLayout.LayoutParams tlp =
                    (android.widget.RelativeLayout.LayoutParams) title.getLayoutParams();
            tlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT, 0);
            tlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT, 0);
            tlp.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT, 0);
            tlp.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL, 0);
            tlp.addRule(android.widget.RelativeLayout.RIGHT_OF, R.id.round_bar_anchor);
            tlp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
            tlp.leftMargin = gap;
            title.setLayoutParams(tlp);
        } catch (Throwable t) {
        }
    }
}
