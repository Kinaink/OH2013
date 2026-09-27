package cn.ottohub.oh2013.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.provider.Settings;
import android.view.WindowManager;

public class BrightnessHelper {
    public static float getScreenBrightness(Activity activity) {
        ContentResolver resolver = activity.getContentResolver();
        try {
            int nowBrightnessValue = Settings.System.getInt(resolver, "screen_brightness", -1);
            return nowBrightnessValue / 255.0f;
        } catch (Exception e) {
            return -1.0f;
        }
    }

    public static void setBrightness(Activity activity, float brightness) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = Math.max(brightness, 0.01f);
        activity.getWindow().setAttributes(lp);
    }
}
