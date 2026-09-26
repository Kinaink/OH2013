-keepattributes *Annotation*

# Fragment：启动 ViewPager 依赖可实例化；keep 名称+无参构造，避免 minify 后白屏
-keep public class * extends android.support.v4.app.Fragment {
    public <init>();
}
-keepnames class * extends android.support.v4.app.Fragment
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service

# Player (IjkPlayer): JNI native methods
-keep class tv.danmaku.ijk.media.player.IjkMediaPlayer {
    native <methods>;
}-keepclassmembers class tv.danmaku.ijk.media.player.IjkMediaPlayer {
    @tv.danmaku.ijk.media.player.annotations.AccessedByNative <fields>;
    @tv.danmaku.ijk.media.player.annotations.CalledByNative <methods>;
}
-keep class tv.danmaku.ijk.media.player.ffmpeg.FFmpegApi {
    native <methods>;
}
-keep @interface tv.danmaku.ijk.media.player.annotations.*

# Player: public API
-keep class tv.danmaku.ijk.media.player.** { public protected *; }

# Danmaku: JNI utilities
-keep class tv.cjump.jni.** { *; }

# Danmaku: public API
-keep class master.flame.danmaku.** { public protected *; }

# MoboPlayer soft-decode JNI: native methods and native-called Java callbacks
-keep class com.clov4r.android.nil.** {
    <fields>;
    <methods>;
}

# GestureController 通过 Class.forName 反射加载进度提示 ViewHolder，
# ProGuard 混淆会重命名/删除仅被字符串引用的类，导致手势 seek 的进度提示静默消失，必须 keep
-keep class cn.ottohub.oh2013.util.PlayerToastMessageViewHolder {
    <fields>;
    <methods>;
}
