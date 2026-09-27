package cn.ottohub.oh2013.util;

public class SdkHelper {
    private SdkHelper() {}

    public static int getSdkInt() {
        try {
            return android.os.Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(android.os.Build.VERSION.SDK);
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    /** Java 堆上限（KB） */
    public static int getMaxMemoryKB() {
        return (int) (Runtime.getRuntime().maxMemory() / 1024);
    }

    /** 高内存设备：堆 >= 32MB（可启用绘制缓存等内存开销较大的优化） */
    public static boolean isHighMemoryDevice() {
        return getMaxMemoryKB() >= 32768;
    }

    /** 低内存设备：堆 < 24MB（强制单线程解码等保守策略） */
    public static boolean isLowMemoryDevice() {
        return getMaxMemoryKB() < 24576;
    }

    /**
     * 图片加载线程数：优先用户设置值（IMAGE_LOAD_THREADS，>0 时用设置）；
     * 未设置时按设备内存给默认值（低内存 1，否则 2）
     */
    public static int getImageLoadThreads() {
        int saved = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (saved > 0) {
            return saved;
        }
        return isLowMemoryDevice() ? 1 : 2;
    }

    /**
     * 关闭过度滚动（API 9+）。必须用反射：API<9 平台上没有 setOverScrollMode 方法，
     * 若在字节码里直接引用，verifier 会在类加载时因方法不存在而拒绝整个类（VerifyError）。
     * OVER_SCROLL_NEVER = 2。
     */
    public static void setOverScrollNever(android.view.View view) {
        try {
            java.lang.reflect.Method m = android.view.View.class.getMethod("setOverScrollMode", int.class);
            m.invoke(view, 2);
        } catch (Throwable t) {
        }
    }

    /**
     * 读取布尔资源。API 1 的 Resources 只有 getBoolean(int, boolean)（带默认值），
     * 单参 getBoolean(int) 是 API 4+ 才有的；若在字节码里直接引用单参版本，
     * verifier 会在 API<4 平台上因方法不存在而拒绝整个类（VerifyError）。
     * 这里用反射，先试单参，失败再退回带默认值的双参版本。
     */
    public static boolean getBooleanResource(android.content.res.Resources res, int resId) {
        if (res == null) {
            return false;
        }
        try {
            java.lang.reflect.Method m = android.content.res.Resources.class.getMethod("getBoolean", int.class);
            return ((Boolean) m.invoke(res, resId)).booleanValue();
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method m2 = android.content.res.Resources.class.getMethod("getBoolean", int.class, boolean.class);
                return ((Boolean) m2.invoke(res, resId, Boolean.FALSE)).booleanValue();
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    /**
     * 读取 DisplayMetrics.densityDpi。该字段是 API 4+ 才加入的，API 3 上不存在；
     * 若在字节码里直接引用该字段，verifier 会在类加载时因字段不存在而拒绝整个类（VerifyError）。
     * 这里用反射读取，失败时退回 160（mdpi 默认值）。
     */
    public static int getDensityDpi(android.util.DisplayMetrics dm) {
        if (dm == null) {
            return 160;
        }
        try {
            java.lang.reflect.Field f = android.util.DisplayMetrics.class.getField("densityDpi");
            return f.getInt(dm);
        } catch (Throwable t) {
            return 160;
        }
    }

    /**
     * 在 View 上注册 onAttachToWindow 回调（仅首次触发后自动移除）。
     * View.OnAttachStateChangeListener 是 API 12+ 的接口，若在字节码里直接 new 实现类，
     * verifier 会在 API<12 平台上因接口不存在而拒绝整个类（VerifyError）。
     * 这里用 Proxy + 反射动态创建监听器，API 3 也安全。
     */
    public static void onViewAttached(android.view.View view, final Runnable onAttached) {
        if (view == null || onAttached == null) return;
        try {
            final Class<?> listenerClass = Class.forName("android.view.View$OnAttachStateChangeListener");
            final Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            try {
                                String name = method.getName();
                                if ("onViewAttachedToWindow".equals(name)) {
                                    onAttached.run();
                                    // 已触发，移除监听（反射调用）
                                    if (args != null && args.length > 0) {
                                        try {
                                            java.lang.reflect.Method remove =
                                                    android.view.View.class.getMethod(
                                                            "removeOnAttachStateChangeListener", listenerClass);
                                            remove.invoke(args[0], proxy);
                                        } catch (Throwable t) {
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                            }
                            return null;
                        }
                    });
            java.lang.reflect.Method add =
                    android.view.View.class.getMethod("addOnAttachStateChangeListener", listenerClass);
            add.invoke(view, listener);
        } catch (Throwable t) {
        }
    }

    /**
     * PendingIntent flags：API 23+ 追加 FLAG_IMMUTABLE（0x04000000），
     * 禁止直接引用 PendingIntent.FLAG_IMMUTABLE 字段（旧机 VerifyError / NoSuchField）。
     */
    public static int pendingIntentFlags(int baseFlags) {
        if (getSdkInt() >= 23) {
            return baseFlags | 0x04000000;
        }
        return baseFlags;
    }

    /**
     * View.setLayerType(SOFTWARE) — API 11+；反射调用，避免旧机 VerifyError。
     */
    public static void setSoftwareLayer(android.view.View view) {
        if (view == null || getSdkInt() < 11) return;
        try {
            java.lang.reflect.Method m = android.view.View.class.getMethod(
                    "setLayerType", int.class, android.graphics.Paint.class);
            m.invoke(view, 1, null); // LAYER_TYPE_SOFTWARE = 1
        } catch (Throwable t) {
        }
    }

    /**
     * View.setAlpha(float) — API 11+；旧机忽略。
     */
    public static void setViewAlpha(android.view.View view, float alpha) {
        if (view == null) return;
        if (getSdkInt() < 11) return;
        try {
            java.lang.reflect.Method m = android.view.View.class.getMethod("setAlpha", float.class);
            m.invoke(view, Float.valueOf(alpha));
        } catch (Throwable t) {
        }
    }

    public static void setScaleX(android.view.View view, float v) {
        invokeFloatView(view, "setScaleX", v, 11);
    }

    public static void setScaleY(android.view.View view, float v) {
        invokeFloatView(view, "setScaleY", v, 11);
    }

    public static void setTranslationX(android.view.View view, float v) {
        invokeFloatView(view, "setTranslationX", v, 11);
    }

    public static void setTranslationY(android.view.View view, float v) {
        invokeFloatView(view, "setTranslationY", v, 11);
    }

    public static void setPivotX(android.view.View view, float v) {
        invokeFloatView(view, "setPivotX", v, 11);
    }

    public static void setPivotY(android.view.View view, float v) {
        invokeFloatView(view, "setPivotY", v, 11);
    }

    public static void setRotationY(android.view.View view, float v) {
        invokeFloatView(view, "setRotationY", v, 11);
    }

    private static void invokeFloatView(android.view.View view, String method, float value, int minSdk) {
        if (view == null || getSdkInt() < minSdk) return;
        try {
            java.lang.reflect.Method m = android.view.View.class.getMethod(method, float.class);
            m.invoke(view, Float.valueOf(value));
        } catch (Throwable t) {
        }
    }

    /**
     * 在后台线程准备 Looper 并返回 Handler（兼容 API&lt;5 无 HandlerThread）。
     */
    public static android.os.Handler createWorkerHandler(String threadName) {
        final Object lock = new Object();
        final android.os.Looper[] holder = new android.os.Looper[1];
        Thread t = new Thread(new Runnable() {
            public void run() {
                android.os.Looper.prepare();
                synchronized (lock) {
                    holder[0] = android.os.Looper.myLooper();
                    lock.notifyAll();
                }
                android.os.Looper.loop();
            }
        }, threadName != null ? threadName : "oh-worker");
        t.start();
        synchronized (lock) {
            while (holder[0] == null) {
                try {
                    lock.wait(5000);
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        if (holder[0] == null) {
            return new android.os.Handler();
        }
        return new android.os.Handler(holder[0]);
    }

    /** 远古机：API≤10（2.3.x 及更早）或极低内存 */
    public static boolean isAncientDevice() {
        return getSdkInt() <= 10 || isLowMemoryDevice();
    }

    /** API≤15（含 4.0.x）上不少系统 API 行为与现网差异大 */
    public static boolean isPreJellyBean() {
        return getSdkInt() < 16;
    }

    /** 图片解码优先配置：低内存/远古机用 RGB_565 省一半 */
    public static android.graphics.Bitmap.Config preferredBitmapConfig() {
        if (isLowMemoryDevice() || getSdkInt() < 14) {
            return android.graphics.Bitmap.Config.RGB_565;
        }
        return android.graphics.Bitmap.Config.ARGB_8888;
    }

    /** View 背景：API16+ 可用 setBackground，旧机必须 setBackgroundDrawable */
    public static void setBackgroundCompat(android.view.View view, android.graphics.drawable.Drawable d) {
        if (view == null) return;
        if (getSdkInt() >= 16) {
            try {
                java.lang.reflect.Method m = android.view.View.class.getMethod(
                        "setBackground", android.graphics.drawable.Drawable.class);
                m.invoke(view, d);
                return;
            } catch (Throwable t) {
            }
        }
        try {
            view.setBackgroundDrawable(d);
        } catch (Throwable t) {
        }
    }

    /**
     * 用 Drawable 透明度模拟 View alpha（0–255），兼容 API&lt;11（无 View.setAlpha）。
     * 若无背景则设置半透明黑色背景。
     */
    public static void setViewAlphaInt(android.view.View view, int alpha255) {
        if (view == null) return;
        if (alpha255 < 0) alpha255 = 0;
        if (alpha255 > 255) alpha255 = 255;
        if (getSdkInt() >= 11) {
            setViewAlpha(view, alpha255 / 255f);
            return;
        }
        try {
            android.graphics.drawable.Drawable bg = view.getBackground();
            if (bg != null) {
                bg = bg.mutate();
                bg.setAlpha(alpha255);
                setBackgroundCompat(view, bg);
            } else {
                view.setBackgroundColor((alpha255 << 24));
            }
        } catch (Throwable t) {
        }
    }

    /** 旧机 keep-alive 易半开连接卡死；API&lt;14 建议 Connection: close */
    public static boolean preferConnectionClose() {
        return getSdkInt() < 14 || isLowMemoryDevice();
    }

    /** 私信轮询间隔：远古机拉长，避免拖垮主线程/网络 */
    public static long imPollIntervalMs(boolean playbackActive) {
        if (playbackActive) return 45 * 1000L;
        if (isAncientDevice()) return 8 * 1000L;
        if (isPreJellyBean()) return 4 * 1000L;
        return 2 * 1000L;
    }
}
