package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.ClipboardManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import cn.ottohub.oh2013.subsettings.DecoderSettingsActivity;
import cn.ottohub.oh2013.util.KeyBindingUtil;
import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.LocaleHelper;
import cn.ottohub.oh2013.util.PermissionUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
import cn.ottohub.oh2013.util.UpdateUtil;

import cn.ottohub.oh2013.util.SdkHelper;
import cn.ottohub.oh2013.util.DeviceInfoUtil;
import cn.ottohub.oh2013.util.DialogUtil;
public class SettingsActivity extends BaseActivity {

    // 播放器偏好
    private static final String KEY_PLAYER_PREFERENCE = "player_preference";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_DEFAULT_TAB = "default_tab";
    public static final String KEY_VIDEO_QUALITY = "video_quality";
    private static final String KEY_MODERN_MODE = "modern_mode";
    public static final String KEY_DECODER_TYPE = "decoder_type";
    private static final String KEY_BUILTIN_PLAYER = "use_builtin_player";
    private static final String KEY_ONLINE_PLAY = "online_play";
    private static final String KEY_CONVERT_PLAY = "convert_play";

    // 转码播放服务（SCF Web 函数，暂时直接使用默认地址，后续换自定义域名）
    // 注意：必须用 http（非 https）——HTC G1 的 Android 1.6 证书库太老，
    // 无法校验现代 HTTPS 证书链，https 会报 "Not trusted server certificate"
    private static final String CONVERT_API_BASE = "http://1303002254-dja6s2xtn7.ap-hongkong.tencentscf.com";
    private static final String CONVERT_BUCKET = "video-storage-1303002254";
    private static final String CONVERT_REGION = "ap-hongkong";

    // 视频画质（B站 API 标准值）
    private static final int QUALITY_360P = 16;
    private static final int QUALITY_480P = 32;
    private static final int QUALITY_720P = 64;
    private static final int QUALITY_1080P = 80;

    // 播放器偏好值
    private static final int PLAYER_AUTO = -1;
    private static final int PLAYER_MX_AD = 0;
    private static final int PLAYER_MX_PRO = 1;
    private static final int PLAYER_MOBO = 2;
    private static final int PLAYER_VLC = 3;
    private static final int PLAYER_VPLAYER = 4;
    private static final int PLAYER_ROCKPLAYER = 5;
    private static final int PLAYER_QQPLAYER = 6;
    private static final int PLAYER_SYSTEM = 7;
    private static final int PLAYER_BUILTIN = 8;
    private static final int PLAYER_LIANGWAN = 9;
    public static final int PLAYER_OSTWIND = 10;

    // 首页 Tab 索引
    private static final int TAB_PROFILE = 0;
    private static final int TAB_HOME = 1;
    private static final int TAB_CHANNEL = 2;
    private static final int TAB_NEW_VIDEOS = 3;
    private static final int TAB_RECOMMEND = 4;
    private static final int TAB_ABOUT = 5;

    // 解码方式
    private static final int DECODER_SYSTEM = 0;
    private static final int DECODER_IJK_HARD = 1;
    private static final int DECODER_IJK_SOFT = 2;

    // 内置播放器最低系统版本要求 (Android 2.3 / API 9)
    private static final int MIN_SDK_FOR_BUILTIN = 9;
    // IJK 硬解最低系统版本要求 (Android 4.1 / API 16)
    private static final int MIN_SDK_FOR_IJK_HARDWARE = 16;

    private TextView cacheSizeText;
    private LinearLayout clearCacheItem;
    private TextView playCacheSizeText;
    private LinearLayout clearPlayCacheItem;
    private LinearLayout playerChoiceItem;
    private TextView playerChoiceText;
    private LinearLayout decoderChoiceItem;
    private TextView decoderChoiceText;
    private LinearLayout playStreamFormatItem;
    private TextView playStreamFormatText;
    private LinearLayout defaultTabItem;
    private TextView defaultTabText;
    private LinearLayout videoQualityItem;
    private TextView videoQualityText;

    private TextView echoHoleText;
    private LinearLayout echoHoleItem;
    private int mLastEchoIndex = -1;

    private TextView crashLogSizeText;
    private LinearLayout clearCrashLogItem;

    private LinearLayout checkUpdateItem;
    private TextView checkUpdateText;

    private CheckBox checkboxAutoUpdate;
    private LinearLayout autoCheckUpdateItem;

    // 现代模式开关
    private CheckBox checkboxModernMode;
    private LinearLayout modernModeItem;

    // 在线播放开关
    private CheckBox checkboxOnlinePlay;
    private LinearLayout onlinePlayItem;
    private View onlinePlayWarning;

    // TV模式已移除
    private CheckBox checkboxRoundScreenCenter;

    private Handler mainHandler = new Handler();

    // 按键导航：设置页可交互条目（方向键上下移动光标，确认键触发点击）
    private java.util.List<View> mKeyNavItems = new java.util.ArrayList<View>();
    private int mKeyNavIndex = -1;

    // 是否已用遥控器按键导航过（触屏用户未按键时不高亮第一项）
    private boolean mKeyNavActive = false;

    private int currentVersionCode = -1;
    private String currentVersionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initRoundTitleBar();

        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // 按键绑定入口
        LinearLayout keyBindingItem = (LinearLayout) findViewById(R.id.key_binding_item);
        if (keyBindingItem != null) {
            keyBindingItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SettingsActivity.this, KeyBindingSetupActivity.class);
                    intent.putExtra("mode", "rebind");
                    startActivity(intent);
                }
            });
        }

        // 图片加载线程数设置
        LinearLayout threadItem = (LinearLayout) findViewById(R.id.image_thread_item);
        final TextView threadText = (TextView) findViewById(R.id.image_thread_text);
        updateImageThreadDisplay(threadText);

        if (threadItem != null) {
            threadItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showImageThreadDialog(threadText);
                }
            });
        }

        // 弹窗样式选择（2.3不支持，隐藏标题、选项、分隔线，避免隐藏不完全）
        LinearLayout dialogStyleItem = (LinearLayout) findViewById(R.id.dialog_style_item);
        TextView dialogStyleTitle = (TextView) findViewById(R.id.dialog_style_title);
        View dialogStyleDividerTop = findViewById(R.id.dialog_style_divider_top);
        View dialogStyleDividerBottom = findViewById(R.id.dialog_style_divider_bottom);
        if (cn.ottohub.oh2013.util.SdkHelper.getSdkInt() < 11) {
            if (dialogStyleItem != null) dialogStyleItem.setVisibility(View.GONE);
            if (dialogStyleTitle != null) dialogStyleTitle.setVisibility(View.GONE);
            if (dialogStyleDividerTop != null) dialogStyleDividerTop.setVisibility(View.GONE);
            if (dialogStyleDividerBottom != null) dialogStyleDividerBottom.setVisibility(View.GONE);
        } else {
            final TextView dialogStyleText = (TextView) findViewById(R.id.dialog_style_text);
            updateDialogStyleDisplay(dialogStyleText);
            if (dialogStyleItem != null) {
                dialogStyleItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDialogStyleDialog(dialogStyleText);
                    }
                });
            }
        }

        // 横屏适配开关
        final CheckBox landscapeCheckbox = (CheckBox) findViewById(R.id.checkbox_landscape);
        LinearLayout landscapeItem = (LinearLayout) findViewById(R.id.landscape_item);

        if (landscapeCheckbox != null) {
            boolean landscapeEnabled = SharedPreferencesUtil.getBoolean(KEY_LANDSCAPE_ENABLED, true);
            landscapeCheckbox.setChecked(landscapeEnabled);

            landscapeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(KEY_LANDSCAPE_ENABLED, isChecked);

                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启横屏模式，正在重启..." : "已关闭横屏模式，正在重启...",
                            Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            });

            if (landscapeItem != null) {
                landscapeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        landscapeCheckbox.toggle();
                    }
                });
            }
        }

        // 现代模式开关
        checkboxModernMode = (CheckBox) findViewById(R.id.checkbox_modern_mode);
        modernModeItem = (LinearLayout) findViewById(R.id.modern_mode_item);

        if (checkboxModernMode != null) {
            boolean modernModeEnabled = SharedPreferencesUtil.getBoolean(KEY_MODERN_MODE, false);
            checkboxModernMode.setChecked(modernModeEnabled);

            checkboxModernMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(KEY_MODERN_MODE, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启现代模式，重启后生效" : "已关闭现代模式，重启后生效",
                            Toast.LENGTH_SHORT).show();
                }
            });

            if (modernModeItem != null) {
                modernModeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxModernMode.toggle();
                    }
                });
            }
        }

        // 在线播放开关 - 低版本完全隐藏
        checkboxOnlinePlay = (CheckBox) findViewById(R.id.checkbox_online_play);
        onlinePlayItem = (LinearLayout) findViewById(R.id.online_play_item);
        onlinePlayWarning = findViewById(R.id.online_play_warning);

        if (onlinePlayItem != null) {
            // 在线播放开关始终可用（Ostwind 等播放器用 MediaPlayer+本地代理，
            // 即使系统不支持内置 IJK 也能在线播放），不再因内置播放器不可用而隐藏/强制关闭
            onlinePlayItem.setVisibility(View.VISIBLE);
            if (onlinePlayWarning != null) {
                onlinePlayWarning.setVisibility(View.VISIBLE);
            }

            boolean onlinePlayEnabled = SharedPreferencesUtil.getBoolean(KEY_ONLINE_PLAY, isBuiltinPlayerSupported());
            if (!SharedPreferencesUtil.contains(KEY_ONLINE_PLAY)) {
                onlinePlayEnabled = isBuiltinPlayerSupported();
                SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, onlinePlayEnabled);
            }
            checkboxOnlinePlay.setChecked(onlinePlayEnabled);

                checkboxOnlinePlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            // 2.3 以下（内置播放器不可用）：提示改用东风(Ostwind)播放器
                            if (!isBuiltinPlayerSupported()
                                    && getPlayerPreference() != PLAYER_OSTWIND) {
                                new AlertDialog.Builder(DialogUtil.wrap(SettingsActivity.this))
                                        .setTitle(getString(R.string.settingsactivity_settitle_63d0))
                                        .setMessage(getString(R.string.settingsactivity_setmessage_ostwind))
                                        .setPositiveButton("切换并开启", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, PLAYER_OSTWIND);
                                                updatePlayerChoiceDisplay();
                                                SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, true);
                                                checkboxOnlinePlay.setChecked(true);
                                                Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_5df2_1), Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                checkboxOnlinePlay.setChecked(false);
                                            }
                                        })
                                        .show();
                                return;
                            }
                            // 2.3+（或已是东风播放器）：内置/东风均可在线，直接开启
                            SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, true);
                            Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_5df2_4), Toast.LENGTH_SHORT).show();
                        } else {
                            SharedPreferencesUtil.putBoolean(KEY_ONLINE_PLAY, false);
                            Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_5df2), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                onlinePlayItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxOnlinePlay.toggle();
                    }
                });
        }

        // 转码播放开关 - 安卓5.0及以上、或有NEON的设备隐藏（仅老旧无NEON低配置设备可用）
        LinearLayout convertPlayItem = (LinearLayout) findViewById(R.id.convert_play_item);
        if (convertPlayItem != null) {
            if (SdkHelper.getSdkInt() >= 21 || DeviceInfoUtil.hasNeon()) {
                convertPlayItem.setVisibility(View.GONE);
                SharedPreferencesUtil.putBoolean(KEY_CONVERT_PLAY, false);
            } else {
                final CheckBox checkboxConvertPlay = (CheckBox) findViewById(R.id.checkbox_convert_play);
                if (checkboxConvertPlay != null) {
                    checkboxConvertPlay.setChecked(SharedPreferencesUtil.getBoolean(KEY_CONVERT_PLAY, false));
                    convertPlayItem.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            checkboxConvertPlay.toggle();
                        }
                    });
                    checkboxConvertPlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SharedPreferencesUtil.putBoolean(KEY_CONVERT_PLAY, isChecked);
                            Toast.makeText(SettingsActivity.this,
                                    isChecked ? getString(R.string.settingsactivity_toast_convert_play_on)
                                              : getString(R.string.settingsactivity_toast_convert_play_off),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }

        // TV模式已移除：电视与手机共用同一套 UI + 按键绑定遥控器适配
        View forceTvModeItem = findViewById(R.id.force_tv_mode_item);
        View forceTvModeWarning = findViewById(R.id.force_tv_mode_warning);
        if (forceTvModeItem != null) forceTvModeItem.setVisibility(View.GONE);
        if (forceTvModeWarning != null) forceTvModeWarning.setVisibility(View.GONE);
        try {
            SharedPreferencesUtil.putBoolean("force_tv_mode", false);
        } catch (Throwable ignored) {
        }

        // 圆形屏幕居中开关（手动覆盖：系统镜像检测不到圆屏时手动开启）
        checkboxRoundScreenCenter = (CheckBox) findViewById(R.id.checkbox_round_screen_center);
        final LinearLayout roundScreenCenterItem = (LinearLayout) findViewById(R.id.round_screen_center_item);
        if (roundScreenCenterItem != null && checkboxRoundScreenCenter != null) {
            boolean roundCenter = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.ROUND_SCREEN_CENTER, false);
            checkboxRoundScreenCenter.setChecked(roundCenter);
            checkboxRoundScreenCenter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.ROUND_SCREEN_CENTER, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启圆形屏幕居中" : "已关闭圆形屏幕居中",
                            Toast.LENGTH_SHORT).show();
                }
            });
            roundScreenCenterItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkboxRoundScreenCenter.toggle();
                }
            });
        }

        // 头像缓存管理
        cacheSizeText = (TextView) findViewById(R.id.cache_size);
        clearCacheItem = (LinearLayout) findViewById(R.id.clear_cache_item);

        updateCacheSize();

        clearCacheItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearCacheDialog();
            }
        });

        // 播放缓存管理
        playCacheSizeText = (TextView) findViewById(R.id.play_cache_size);
        clearPlayCacheItem = (LinearLayout) findViewById(R.id.clear_play_cache_item);

        updatePlayCacheSize();

        if (clearPlayCacheItem != null) {
            clearPlayCacheItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearPlayCacheDialog();
                }
            });
        }

        // Cookie 导入导出
        LinearLayout cookieItem = (LinearLayout) findViewById(R.id.cookie_manage_item);
        if (cookieItem != null) {
            cookieItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCookieDialog();
                }
            });
        }

        // 回声洞
        echoHoleText = (TextView) findViewById(R.id.echo_hole_text);
        echoHoleItem = (LinearLayout) findViewById(R.id.echo_hole_item);

        if (echoHoleItem != null) {
            echoHoleItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadEchoHole();
                }
            });
        }

        // 播放器选择
        playerChoiceItem = (LinearLayout) findViewById(R.id.player_choice_item);
        playerChoiceText = (TextView) findViewById(R.id.player_choice_text);

        if (playerChoiceItem != null) {
            updatePlayerChoiceDisplay();
            playerChoiceItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPlayerChoiceDialog();
                }
            });
        }

        // 解码方式选择
        decoderChoiceItem = (LinearLayout) findViewById(R.id.decoder_choice_item);
        decoderChoiceText = (TextView) findViewById(R.id.decoder_choice_text);

        if (decoderChoiceItem != null) {
            updateDecoderChoiceDisplay();
            decoderChoiceItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDecoderChoiceDialog();
                }
            });
        }

        // 播放流格式选择
        playStreamFormatItem = (LinearLayout) findViewById(R.id.play_stream_format_item);
        playStreamFormatText = (TextView) findViewById(R.id.play_stream_format_text);

        if (playStreamFormatItem != null) {
            updatePlayStreamFormatDisplay();
            playStreamFormatItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPlayStreamFormatDialog();
                }
            });
        }

        // 解码设置入口
        LinearLayout decoderSettingsItem = (LinearLayout) findViewById(R.id.decoder_settings_item);
        if (decoderSettingsItem != null) {
            decoderSettingsItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, DecoderSettingsActivity.class));
                }
            });
        }

        // 视频渲染方式选择 - 仅 API 14+ 可用
        LinearLayout rendererTypeItem = (LinearLayout) findViewById(R.id.renderer_type_item);
        final TextView rendererTypeText = (TextView) findViewById(R.id.renderer_type_text);
        if (rendererTypeItem != null && rendererTypeText != null) {
            if (SdkHelper.getSdkInt() < 14) {
                rendererTypeItem.setVisibility(View.GONE);
            } else {
                updateRendererTypeDisplay(rendererTypeText);
                rendererTypeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRendererTypeDialog(rendererTypeText);
                    }
                });
            }
        }

        // 弹幕引擎选择
        LinearLayout danmakuEngineItem = (LinearLayout) findViewById(R.id.danmaku_engine_item);
        final TextView danmakuEngineText = (TextView) findViewById(R.id.danmaku_engine_text);
        if (danmakuEngineItem != null && danmakuEngineText != null) {
            updateDanmakuEngineDisplay(danmakuEngineText);
            danmakuEngineItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDanmakuEngineDialog(danmakuEngineText);
                }
            });
        }

        // 默认首页选择
        defaultTabItem = (LinearLayout) findViewById(R.id.default_tab_item);
        defaultTabText = (TextView) findViewById(R.id.default_tab_text);

        if (defaultTabItem != null) {
            updateDefaultTabDisplay();
            defaultTabItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDefaultTabDialog();
                }
            });
        }

        // 语言选择
        LinearLayout localeItem = (LinearLayout) findViewById(R.id.locale_item);
        final TextView localeText = (TextView) findViewById(R.id.locale_text);

        if (localeItem != null && localeText != null) {
            updateLocaleDisplay(localeText);
            localeItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLocaleDialog(localeText);
                }
            });
        }

        // 视频画质选择
        videoQualityItem = (LinearLayout) findViewById(R.id.video_quality_item);
        videoQualityText = (TextView) findViewById(R.id.video_quality_text);

        if (videoQualityItem != null) {
            updateVideoQualityDisplay();
            videoQualityItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showVideoQualityDialog();
                }
            });
        }

        // 设备信息入口
        LinearLayout deviceInfoItem = (LinearLayout) findViewById(R.id.device_info_item);
        if (deviceInfoItem != null) {
            deviceInfoItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, DeviceInfoActivity.class));
                }
            });
        }

        // 崩溃日志管理
        crashLogSizeText = (TextView) findViewById(R.id.crash_log_size);
        clearCrashLogItem = (LinearLayout) findViewById(R.id.clear_crash_log_item);

        updateCrashLogSize();

        if (clearCrashLogItem != null) {
            clearCrashLogItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearCrashLogDialog();
                }
            });
        }

        // 运行日志：默认关闭（避免日志写入影响性能），开启后记录运行与弹幕绘制日志到文件
        final String KEY_RUN_LOG = "run_log_enabled";
        final CheckBox checkboxRunLog = (CheckBox) findViewById(R.id.checkbox_enable_run_log);
        LinearLayout enableRunLogItem = (LinearLayout) findViewById(R.id.enable_run_log_item);
        boolean runLogEnabled = SharedPreferencesUtil.getBoolean(KEY_RUN_LOG, false);
        cn.ottohub.oh2013.util.LogFileUtil.setEnabled(runLogEnabled);
        master.flame.danmaku.util.DiagLogger.setEnabled(runLogEnabled);
        if (checkboxRunLog != null) {
            checkboxRunLog.setChecked(runLogEnabled);
        }
        if (enableRunLogItem != null) {
            enableRunLogItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkboxRunLog.toggle();
                    boolean current = checkboxRunLog.isChecked();
                    SharedPreferencesUtil.putBoolean(KEY_RUN_LOG, current);
                    cn.ottohub.oh2013.util.LogFileUtil.setEnabled(current);
                    master.flame.danmaku.util.DiagLogger.setEnabled(current);
                    if (current) {
                        cn.ottohub.oh2013.util.LogFileUtil.clearDiag();
                        cn.ottohub.oh2013.util.LogFileUtil.log("App", "运行日志已开启 SDK="
                                + cn.ottohub.oh2013.util.SdkHelper.getSdkInt()
                                + " model=" + android.os.Build.MODEL);
                    }
                    Toast.makeText(SettingsActivity.this,
                            current ? getString(R.string.activity_settings_run_log_on)
                                    : getString(R.string.activity_settings_run_log_off),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 查看运行日志（应用内日志，无需 adb）
        LinearLayout viewLogItem = (LinearLayout) findViewById(R.id.view_log_item);
        if (viewLogItem != null) {
            viewLogItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cn.ottohub.oh2013.util.LogFileUtil.showLogsDialog(SettingsActivity.this);
                }
            });
        }

        // 检查更新
        checkUpdateItem = (LinearLayout) findViewById(R.id.check_update_item);
        checkUpdateText = (TextView) findViewById(R.id.check_update_text);

        if (checkUpdateItem != null) {
            checkUpdateItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkForUpdate();
                }
            });
        }

        // 自动检查更新开关
        checkboxAutoUpdate = (CheckBox) findViewById(R.id.checkbox_auto_update);
        autoCheckUpdateItem = (LinearLayout) findViewById(R.id.auto_check_update_item);

        boolean autoUpdateEnabled = SharedPreferencesUtil.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (checkboxAutoUpdate != null) {
            checkboxAutoUpdate.setChecked(autoUpdateEnabled);
        }

        if (autoCheckUpdateItem != null) {
            autoCheckUpdateItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkboxAutoUpdate.toggle();
                    boolean current = checkboxAutoUpdate.isChecked();
                    SharedPreferencesUtil.putBoolean(KEY_AUTO_CHECK_UPDATE, current);
                    Toast.makeText(SettingsActivity.this,
                            current ? "已开启自动检查更新" : "已关闭自动检查更新",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 私信消息推送开关
        final CheckBox checkboxImPush = (CheckBox) findViewById(R.id.checkbox_im_push);
        LinearLayout imPushItem = (LinearLayout) findViewById(R.id.im_push_item);
        if (checkboxImPush != null) {
            checkboxImPush.setChecked(cn.ottohub.oh2013.util.ImNotifyService.isEnabled());
        }
        if (imPushItem != null) {
            imPushItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (checkboxImPush == null) return;
                    checkboxImPush.toggle();
                    boolean on = checkboxImPush.isChecked();
                    cn.ottohub.oh2013.util.ImNotifyService.setEnabled(SettingsActivity.this, on);
                    Toast.makeText(SettingsActivity.this,
                            on ? "已开启私信推送" : "已关闭私信推送",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 无图模式开关
        final CheckBox checkboxNoImage = (CheckBox) findViewById(R.id.checkbox_no_image_mode);
        LinearLayout noImageItem = (LinearLayout) findViewById(R.id.no_image_mode_item);

        if (checkboxNoImage != null) {
            boolean noImageEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false);
            checkboxNoImage.setChecked(noImageEnabled);

            checkboxNoImage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启无图模式，将不加载任何图片" : "已关闭无图模式",
                            Toast.LENGTH_SHORT).show();
                }
            });

            if (noImageItem != null) {
                noImageItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxNoImage.toggle();
                    }
                });
            }
        }

        // 隐私模式开关（不记录播放历史）
        final CheckBox checkboxPrivacy = (CheckBox) findViewById(R.id.checkbox_privacy_mode);
        LinearLayout privacyItem = (LinearLayout) findViewById(R.id.privacy_mode_item);

        if (checkboxPrivacy != null) {
            boolean privacyEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false);
            checkboxPrivacy.setChecked(privacyEnabled);

            checkboxPrivacy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PRIVACY_MODE, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启隐私模式，不记录播放历史" : "已关闭隐私模式",
                            Toast.LENGTH_SHORT).show();
                }
            });

            if (privacyItem != null) {
                privacyItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxPrivacy.toggle();
                    }
                });
            }
        }

        // 无痕模式开关（不携带登录 Cookie）
        final CheckBox checkboxIncognito = (CheckBox) findViewById(R.id.checkbox_incognito_mode);
        LinearLayout incognitoItem = (LinearLayout) findViewById(R.id.incognito_mode_item);

        if (checkboxIncognito != null) {
            boolean incognitoEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.INCOGNITO_MODE, false);
            checkboxIncognito.setChecked(incognitoEnabled);

            checkboxIncognito.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.INCOGNITO_MODE, isChecked);
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "已开启无痕模式，不携带登录 Cookie" : "已关闭无痕模式",
                            Toast.LENGTH_SHORT).show();
                }
            });

            if (incognitoItem != null) {
                incognitoItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkboxIncognito.toggle();
                    }
                });
            }
        }

        initKeyNavigation();
    }

    /**
     * 收集设置页所有可交互条目（id 以 _item 结尾的 LinearLayout），
     * 供遥控器方向键/确认键导航。
     */
    private void initKeyNavigation() {
        hideOttoUnusedSettings();

        mKeyNavItems.clear();
        ScrollView scrollView = (ScrollView) findViewById(R.id.settings_scroll);
        if (scrollView == null || scrollView.getChildCount() == 0) {
            return;
        }
        ViewGroup container = (ViewGroup) scrollView.getChildAt(0);
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout
                    && child.getVisibility() == View.VISIBLE
                    && child.getId() != View.NO_ID) {
                String name = getResources().getResourceEntryName(child.getId());
                if (name != null && name.endsWith("_item")) {
                    mKeyNavItems.add(child);
                }
            }
        }
        if (mKeyNavItems.size() > 0) {
            mKeyNavIndex = 0;
            // 触屏用户未按键时不高亮第一项，等首次按键再显示
            applyKeyNavHighlight();
        }
    }

    /** OTTOhub 不需要的设置项：数据源/画质/语言/回声洞/隐私模式/无痕模式/Cookie */
    private void hideOttoUnusedSettings() {
        int[] ids = new int[]{
                R.id.modern_mode_item,
                R.id.video_quality_item,
                R.id.locale_item,
                R.id.echo_hole_item,
                R.id.privacy_mode_item,
                R.id.incognito_mode_item,
                R.id.cookie_manage_item
        };
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v != null) {
                hideSettingItemAndNeighbors(v);
            }
        }
        try {
            ScrollView scrollView = (ScrollView) findViewById(R.id.settings_scroll);
            if (scrollView != null && scrollView.getChildCount() > 0) {
                collapseEmptySettingSections((ViewGroup) scrollView.getChildAt(0));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 隐藏设置项，并顺带隐藏紧邻的区块标题、说明文字、细分割线，避免留下空白缝隙。
     */
    private void hideSettingItemAndNeighbors(View item) {
        item.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup) item.getParent();
        if (parent == null) return;
        int idx = parent.indexOfChild(item);

        // 前一个兄弟：区块标题 TextView（bold / 短文案）
        if (idx > 0) {
            View prev = parent.getChildAt(idx - 1);
            if (prev instanceof TextView && isLikelySectionTitle((TextView) prev)) {
                prev.setVisibility(View.GONE);
            }
        }

        // 后继兄弟：说明 TextView、再跟一条细分割线
        for (int i = idx + 1; i < parent.getChildCount(); i++) {
            View next = parent.getChildAt(i);
            if (next == null) break;
            if (next.getVisibility() == View.GONE) {
                continue;
            }
            if (isThinDivider(next)) {
                next.setVisibility(View.GONE);
                break;
            }
            if (next instanceof LinearLayout) {
                break;
            }
            if (next instanceof TextView) {
                TextView tv = (TextView) next;
                // 遇到下一个有文案的粗体区块标题则停止；空/说明文字一律隐藏
                CharSequence cs = tv.getText();
                String t = cs != null ? cs.toString().trim() : "";
                boolean boldTitle = false;
                try {
                    boldTitle = tv.getTypeface() != null && tv.getTypeface().isBold() && t.length() > 0;
                } catch (Throwable ignored) {
                }
                if (boldTitle) {
                    break;
                }
                next.setVisibility(View.GONE);
                continue;
            }
            break;
        }
    }

    private boolean isLikelySectionTitle(TextView tv) {
        if (tv == null) return false;
        CharSequence cs = tv.getText();
        String t = cs != null ? cs.toString().trim() : "";
        if (t.length() > 20) return false;
        try {
            if (tv.getTypeface() != null && tv.getTypeface().isBold()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // 空区块标题（如视频画质）
        return t.length() == 0;
    }

    private boolean isThinDivider(View v) {
        if (v == null) return false;
        if (v instanceof TextView || v instanceof ViewGroup || v instanceof ImageView
                || v instanceof CheckBox) {
            return false;
        }
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return false;
        // 1dp / 1px / 2px 分割线
        return lp.height >= 0 && lp.height <= 4;
    }

    /**
     * 扫描设置容器：隐藏「下面没有可见 _item」的区块标题，以及连续/悬空的细分割线。
     */
    private void collapseEmptySettingSections(ViewGroup container) {
        if (container == null) return;
        int n = container.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            if (child.getVisibility() != View.VISIBLE) continue;
            TextView tv = (TextView) child;
            if (!isLikelySectionTitle(tv)) continue;

            boolean hasVisibleItem = false;
            for (int j = i + 1; j < n; j++) {
                View next = container.getChildAt(j);
                if (next instanceof TextView && isLikelySectionTitle((TextView) next)
                        && next.getVisibility() == View.VISIBLE) {
                    break;
                }
                if (next instanceof LinearLayout
                        && next.getVisibility() == View.VISIBLE
                        && next.getId() != View.NO_ID) {
                    try {
                        String name = getResources().getResourceEntryName(next.getId());
                        if (name != null && name.endsWith("_item")) {
                            hasVisibleItem = true;
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (!hasVisibleItem) {
                tv.setVisibility(View.GONE);
            }
        }

        // 连续细分割线只留一条；前后都无可见内容时隐藏
        View lastVisible = null;
        for (int i = 0; i < n; i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (isThinDivider(child)) {
                if (lastVisible != null && isThinDivider(lastVisible)) {
                    child.setVisibility(View.GONE);
                    continue;
                }
                // 下一项若也不存在可见内容，则隐藏本分割线
                boolean hasAfter = false;
                for (int j = i + 1; j < n; j++) {
                    View after = container.getChildAt(j);
                    if (after.getVisibility() != View.VISIBLE) continue;
                    if (isThinDivider(after)) continue;
                    hasAfter = true;
                    break;
                }
                if (!hasAfter || lastVisible == null) {
                    child.setVisibility(View.GONE);
                    continue;
                }
            }
            if (child.getVisibility() == View.VISIBLE) {
                lastVisible = child;
            }
        }
    }

    private void hideTextViewsContaining(View root, String[] keywords) {
        if (root == null || keywords == null) return;
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if (cs != null) {
                String t = cs.toString();
                for (int i = 0; i < keywords.length; i++) {
                    if (t.equals(keywords[i]) || t.contains(keywords[i])) {
                        if (t.length() <= 12) {
                            root.setVisibility(View.GONE);
                        }
                        break;
                    }
                }
            }
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                hideTextViewsContaining(g.getChildAt(i), keywords);
            }
        }
    }

    /**
     * 刷新按键导航高亮：只有当前选中条目显示粉色边框背景，
     * 其余条目恢复白色背景。Android 2.x 无 getBackground 便捷处理，直接设色。
     * 触屏用户未按键时（mKeyNavActive=false）不做任何修改，避免误高亮第一项。
     */
    private void applyKeyNavHighlight() {
        if (!mKeyNavActive) {
            return;
        }
        for (int i = 0; i < mKeyNavItems.size(); i++) {
            View v = mKeyNavItems.get(i);
            v.setBackgroundColor(i == mKeyNavIndex ? 0x66FF8C00 : 0xFFFFFFFF);
        }
    }

    /**
     * 移动按键导航光标并滚动到可见。方向：-1 上，+1 下。
     */
    private void moveKeyNav(int direction) {
        if (mKeyNavItems.size() == 0) {
            return;
        }
        int next = mKeyNavIndex + direction;
        if (next < 0) {
            next = 0;
        } else if (next >= mKeyNavItems.size()) {
            next = mKeyNavItems.size() - 1;
        }
        if (next != mKeyNavIndex) {
            mKeyNavIndex = next;
            applyKeyNavHighlight();
            scrollKeyNavToVisible(mKeyNavItems.get(mKeyNavIndex));
        }
    }

    /** 滚动 ScrollView 让选中条目完整可见。 */
    private void scrollKeyNavToVisible(View item) {
        ScrollView scrollView = (ScrollView) findViewById(R.id.settings_scroll);
        if (scrollView == null || item == null) {
            return;
        }
        int top = item.getTop();
        int bottom = item.getBottom();
        int scrollY = scrollView.getScrollY();
        int height = scrollView.getHeight();
        if (top < scrollY) {
            scrollView.smoothScrollTo(0, Math.max(0, top));
        } else if (bottom > scrollY + height) {
            scrollView.smoothScrollTo(0, bottom - height);
        }
    }

    /**
     * 遥控器方向键：上下移动光标，确认键触发选中条目点击。
     * 仅在没有任何子 View 获得焦点（弹窗未打开）时生效。
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (mKeyNavItems.size() > 0
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int action = KeyBindingUtil.classify(event.getKeyCode());
            if (action == KeyBindingUtil.ACTION_UP) {
                mKeyNavActive = true;
                moveKeyNav(-1);
                return true;
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                mKeyNavActive = true;
                moveKeyNav(1);
                return true;
            } else if (action == KeyBindingUtil.ACTION_CONFIRM) {
                if (mKeyNavIndex >= 0 && mKeyNavIndex < mKeyNavItems.size()) {
                    mKeyNavActive = true;
                    applyKeyNavHighlight();
                    mKeyNavItems.get(mKeyNavIndex).performClick();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }


    // 判断是否支持 IJK 硬解 (Android 4.1+)
    private static boolean isIjkHardwareSupported() {
        return SdkHelper.getSdkInt() >= MIN_SDK_FOR_IJK_HARDWARE;
    }

    // 获取在线播放状态
    public static boolean isOnlinePlayEnabled() {
        // 默认：支持内置播放器（API 9+）时开启；老设备（API<9）默认关闭
        boolean defaultEnabled = isBuiltinPlayerSupported();
        boolean online = SharedPreferencesUtil.getBoolean(KEY_ONLINE_PLAY, defaultEnabled);
        return online;
    }

    // 获取转码播放状态（转码播放服务地址）
    public static boolean isConvertPlayEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_CONVERT_PLAY, false);
    }

    // 转码播放服务地址（SCF Web 函数 /transcode）
    public static String getConvertPlayApiBase() {
        return CONVERT_API_BASE;
    }

    public static String getConvertPlayApiUrl() {
        return CONVERT_API_BASE + "/transcode";
    }

    public static String getConvertPlayBucket() {
        return CONVERT_BUCKET;
    }

    public static String getConvertPlayRegion() {
        return CONVERT_REGION;
    }

    // 获取现代模式状态
    public static boolean isModernModeEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_MODERN_MODE, false);
    }

    // 获取放送时间表 API URL（OH2013 已无番剧时间表，保留空串避免打旧域名）
    public static String getTimelineApiUrl() {
        return "";
    }

    // 获取新番专题 API URL（同上）
    public static String getNewAnimeApiUrl() {
        return "";
    }

    // 判断设备是否支持内置播放器（IJK V3 需要 Android 2.3+）
    private static boolean isBuiltinPlayerSupported() {
        return SdkHelper.getSdkInt() >= MIN_SDK_FOR_BUILTIN;
    }

    // 获取默认播放器偏好（低版本强制非内置）
    public static int getDefaultPlayerPreference() {
        if (!isBuiltinPlayerSupported()) {
            return PLAYER_AUTO;
        }
        return PLAYER_BUILTIN;
    }

    // 获取当前播放器偏好（带版本适配）
    public static int getPlayerPreference() {
        int pref = SharedPreferencesUtil.getInt(KEY_PLAYER_PREFERENCE, getDefaultPlayerPreference());
        if (pref == PLAYER_BUILTIN && !isBuiltinPlayerSupported()) {
            SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, PLAYER_AUTO);
            return PLAYER_AUTO;
        }
        return pref;
    }

    // 获取播放器显示名称（带低版本提示）
    public static String getPlayerDisplayName() {
        int preference = getPlayerPreference();
        switch (preference) {
            case PLAYER_AUTO:
                return "自动检测";
            case PLAYER_MX_AD:
                return "MX Player (免费版)";
            case PLAYER_MX_PRO:
                return "MX Player (专业版)";
            case PLAYER_MOBO:
                return "MoboPlayer";
            case PLAYER_VLC:
                return "VLC";
            case PLAYER_VPLAYER:
                return "VPlayer";
            case PLAYER_ROCKPLAYER:
                return "RockPlaye Liter";
            case PLAYER_QQPLAYER:
                return "QQ影音";
            case PLAYER_BUILTIN:
                return isBuiltinPlayerSupported() ? "内置播放器" : "内置播放器 (不可用)";
            case PLAYER_LIANGWAN:
                return "凉腕播放器";
            case PLAYER_OSTWIND:
                return "Ostwind播放器";
            case PLAYER_SYSTEM:
            default:
                return "系统播放器";
        }
    }

    // 获取播放器包名
    public static String getPlayerPackageName() {
        int preference = getPlayerPreference();
        switch (preference) {
            case PLAYER_MX_AD:
                return "com.mxtech.videoplayer.ad";
            case PLAYER_MX_PRO:
                return "com.mxtech.videoplayer.pro";
            case PLAYER_MOBO:
                return "com.clov4r.android.nil";
            case PLAYER_VLC:
                return "org.videolan.vlc";
            case PLAYER_VPLAYER:
                return "me.abitno.vplayer.t";
            case PLAYER_ROCKPLAYER:
                return "com.redirectin.rockplayer.android.unified.lite";
            case PLAYER_QQPLAYER:
                return "com.tencent.research.drop";
            case PLAYER_LIANGWAN:
                return "com.aliangmaker.media";
            case PLAYER_OSTWIND:
                // Ostwind 是本 App 内置简易播放器（MediaPlayer + 自定义请求头），
                // 包名返回特殊标记，跳转处识别后直接启动本地 Activity
                return "cn.ottohub.oh2013.ostwind";
            case PLAYER_SYSTEM:
            case PLAYER_AUTO:
            default:
                return null;
        }
    }

    // 获取解码方式
    public static int getDecoderType() {
        // 默认值：4.1 以下默认软解；Lollipop(5.0-5.1) MediaCodec 不稳定也默认软解；其余默认硬解
        int sdk = SdkHelper.getSdkInt();
        int defaultDecoder;
        if (!isIjkHardwareSupported()) {
            defaultDecoder = DECODER_IJK_SOFT;
        } else if (sdk >= 21 && sdk <= 22) {
            defaultDecoder = DECODER_IJK_SOFT;
        } else {
            defaultDecoder = DECODER_IJK_HARD;
        }
        int saved = SharedPreferencesUtil.getInt(KEY_DECODER_TYPE, defaultDecoder);

        // 如果保存的值是硬解但设备不支持，自动修正为软解
        if (saved == DECODER_IJK_HARD && !isIjkHardwareSupported()) {
            saved = DECODER_IJK_SOFT;
            SharedPreferencesUtil.putInt(KEY_DECODER_TYPE, saved);
        }
        // Android 5.0–5.1：历史版本可能把硬解写进偏好，MediaCodec 易卡顿/加载失败
        if (sdk >= 21 && sdk <= 22 && saved == DECODER_IJK_HARD) {
            saved = DECODER_IJK_SOFT;
            SharedPreferencesUtil.putInt(KEY_DECODER_TYPE, saved);
        }

        return saved;
    }

    public static boolean useBuiltinPlayer() {
        return SharedPreferencesUtil.getBoolean(KEY_BUILTIN_PLAYER, SdkHelper.getSdkInt() >= 9);
    }

    // 视频画质选择
    private void showVideoQualityDialog() {
        final String[] qualities = {"360P 流畅", "480P 清晰", "720P 高清", "1080P 超清"};
        final int[] qualityValues = {QUALITY_360P, QUALITY_480P, QUALITY_720P, QUALITY_1080P};
        int currentQuality = getVideoQuality();

        int checkedIndex = 0;
        for (int i = 0; i < qualityValues.length; i++) {
            if (qualityValues[i] == currentQuality) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_1))
                .setSingleChoiceItems(qualities, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newQuality = qualityValues[which];
                        SharedPreferencesUtil.putInt(KEY_VIDEO_QUALITY, newQuality);
                        updateVideoQualityDisplay();
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + qualities[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateVideoQualityDisplay() {
        int quality = getVideoQuality();
        if (quality == QUALITY_1080P) {
            videoQualityText.setText(getString(R.string.settingsactivity_settext_8d85));
        } else if (quality == QUALITY_720P) {
            videoQualityText.setText(getString(R.string.settingsactivity_settext_9ad8));
        } else if (quality == QUALITY_480P) {
            videoQualityText.setText(getString(R.string.settingsactivity_settext_6e05));
        } else {
            videoQualityText.setText(getString(R.string.settingsactivity_settext_6d41));
        }
    }

    public static int getVideoQuality() {
        return SharedPreferencesUtil.getInt(KEY_VIDEO_QUALITY, QUALITY_360P);
    }

    // 获取渲染方式
    public static int getRendererType() {
        if (SdkHelper.getSdkInt() < 14) {
            return 0; // TextureView 需要 API 14+
        }
        // Lollipop 上 TextureView 合成更重，默认 SurfaceView 减轻卡顿
        int def = (SdkHelper.getSdkInt() >= 21 && SdkHelper.getSdkInt() <= 22) ? 0 : 1;
        return SharedPreferencesUtil.getInt(SharedPreferencesUtil.RENDERER_TYPE, def);
    }

    // 渲染方式选择
    private void showRendererTypeDialog(final TextView textView) {
        final String[] modes = {"SurfaceView", "TextureView"};
        final int[] values = {0, 1};
        int current = getRendererType();

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_89c6))
                .setSingleChoiceItems(modes, checkedIndex, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.RENDERER_TYPE, values[which]);
                        updateRendererTypeDisplay(textView);
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + modes[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateRendererTypeDisplay(TextView textView) {
        int type = getRendererType();
        textView.setText(type == 1 ? "TextureView" : "SurfaceView");
    }

    // 播放流格式选择 (1=MP4, 16=DASH)
    private void showPlayStreamFormatDialog() {
        final String[] modes = {"MP4 (fnval=1)", "DASH (fnval=16)"};
        final int[] values = {1, 16};
        int current = getPlayStreamFormat();

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_5))
                .setSingleChoiceItems(modes, checkedIndex, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.PLAY_STREAM_FORMAT, values[which]);
                        updatePlayStreamFormatDisplay();
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + modes[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updatePlayStreamFormatDisplay() {
        if (playStreamFormatText == null) return;
        int f = getPlayStreamFormat();
        playStreamFormatText.setText(f == 16 ? "DASH" : "MP4");
    }

    public static int getPlayStreamFormat() {
        return SharedPreferencesUtil.getInt(SharedPreferencesUtil.PLAY_STREAM_FORMAT, 1);
    }

    // 弹幕引擎选择
    private void showDanmakuEngineDialog(final TextView textView) {
        final String[] modes = {"完整版（DanmakuFlameMaster）", "简易版（BT-5弹幕引擎）"};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, 0);
        int checkedIndex = Math.min(current, 1);

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_5f39))
                .setSingleChoiceItems(modes, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, which);
                        updateDanmakuEngineDisplay(textView);
                        Toast.makeText(SettingsActivity.this,
                                "已切换为: " + modes[which],
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDanmakuEngineDisplay(TextView textView) {
        int mode = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, 0);
        textView.setText(mode == 1 ? "简易版（BT-5弹幕引擎）" : "完整版");
    }

    // 默认首页选择
    public static int getDefaultTab() {
        return SharedPreferencesUtil.getInt(KEY_DEFAULT_TAB, TAB_HOME);
    }

    private void updateDefaultTabDisplay() {
        String[] tabNames = {"个人中心", "分区导航", "关于我们"};
        int index = getDefaultTab();
        if (index >= 0 && index < tabNames.length) {
            defaultTabText.setText(tabNames[index]);
        } else {
            defaultTabText.setText("分区导航");
        }
    }

    private void showDefaultTabDialog() {
        final String[] tabNames = {"个人中心", "分区导航", "关于我们"};
        final int[] tabValues = {0, 1, 2};
        int currentIndex = getDefaultTab();

        int checkedIndex = 0;
        for (int i = 0; i < tabValues.length; i++) {
            if (tabValues[i] == currentIndex) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_4))
                .setSingleChoiceItems(tabNames, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newIndex = tabValues[which];
                        SharedPreferencesUtil.putInt(KEY_DEFAULT_TAB, newIndex);
                        updateDefaultTabDisplay();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 播放器选择对话框
    private void showPlayerChoiceDialog() {
        final String[] allPlayers = {"内置播放器", "自动检测", "MX Player (免费版)", "MX Player (专业版)", "MoboPlayer", "VLC", "VPlayer", "RockPlaye Liter", "QQ影音", "凉腕播放器", "Ostwind播放器", "系统播放器"};
        final int[] allValues = {PLAYER_BUILTIN, PLAYER_AUTO, PLAYER_MX_AD, PLAYER_MX_PRO, PLAYER_MOBO, PLAYER_VLC, PLAYER_VPLAYER, PLAYER_ROCKPLAYER, PLAYER_QQPLAYER, PLAYER_LIANGWAN, PLAYER_OSTWIND, PLAYER_SYSTEM};

        // 低版本过滤掉内置播放器
        ArrayList filteredPlayers = new ArrayList();
        ArrayList filteredValues = new ArrayList();

        for (int i = 0; i < allPlayers.length; i++) {
            if (allValues[i] == PLAYER_BUILTIN && !isBuiltinPlayerSupported()) {
                continue;
            }
            filteredPlayers.add(allPlayers[i]);
            filteredValues.add(Integer.valueOf(allValues[i]));
        }

        final String[] players = (String[]) filteredPlayers.toArray(new String[filteredPlayers.size()]);
        final int[] playerValues = new int[filteredValues.size()];
        for (int i = 0; i < filteredValues.size(); i++) {
            playerValues[i] = ((Integer) filteredValues.get(i)).intValue();
        }

        int currentPreference = getPlayerPreference();

        int checkedIndex = 0;
        for (int i = 0; i < playerValues.length; i++) {
            if (playerValues[i] == currentPreference) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_4))
                .setSingleChoiceItems(players, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newPreference = playerValues[which];
                        SharedPreferencesUtil.putInt(KEY_PLAYER_PREFERENCE, newPreference);
                        updatePlayerChoiceDisplay();

                        String tip = "已切换为 " + players[which];
                        if (newPreference == PLAYER_BUILTIN) {
                            tip += "，使用内置播放器";
                        } else if (newPreference == PLAYER_SYSTEM) {
                            tip += "，低版本系统可能无法播放";
                        } else if (newPreference == PLAYER_AUTO) {
                            tip += "，将自动选择可用播放器";
                        }
                        Toast.makeText(SettingsActivity.this, tip, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updatePlayerChoiceDisplay() {
        String displayName = getPlayerDisplayName();
        playerChoiceText.setText(displayName);
    }

    // 解码方式显示
    private void updateDecoderChoiceDisplay() {
        int decoder = getDecoderType();
        switch (decoder) {
            case DECODER_SYSTEM:
                decoderChoiceText.setText(getString(R.string.settingsactivity_settext_7cfb));
                break;
            case DECODER_IJK_HARD:
            default:
                decoderChoiceText.setText(getString(R.string.settingsactivity_settext_786c));
                break;
            case DECODER_IJK_SOFT:
                decoderChoiceText.setText(getString(R.string.settingsactivity_settext_8f6f));
                break;
        }
    }

    // 解码方式选择对话框
    private void showDecoderChoiceDialog() {
        final String[] decoders;
        final int[] decoderValues;

        if (isIjkHardwareSupported()) {
            // Android 4.1+ 显示三个选项
            decoders = new String[]{"系统解码器", "IJK 硬解", "软件解码器"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_HARD, DECODER_IJK_SOFT};
        } else {
            // Android 4.1 以下只显示两个选项
            decoders = new String[]{"系统解码器", "软件解码器"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_SOFT};
        }

        int currentDecoder = getDecoderType();

        int checkedIndex = 0;
        for (int i = 0; i < decoderValues.length; i++) {
            if (decoderValues[i] == currentDecoder) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_2))
                .setSingleChoiceItems(decoders, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newDecoder = decoderValues[which];
                        SharedPreferencesUtil.putInt(KEY_DECODER_TYPE, newDecoder);
                        updateDecoderChoiceDisplay();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 图片加载线程
    private void updateImageThreadDisplay(TextView textView) {
        if (textView == null) return;
        int threads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 1);
        textView.setText(threads + "线程");
    }

    private void showImageThreadDialog(final TextView textView) {
        final String[] items = {"单线程", "三线程", "自定线程"};
        final int[] values = {1, 3, -1};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 1);

        int checkedIndex = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { checkedIndex = i; break; }
        }
        if (checkedIndex == 1 && current != 3) checkedIndex = 2;

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_56fe))
                .setSingleChoiceItems(items, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 2) {
                            showCustomThreadDialog(textView);
                        } else {
                            SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, values[which]);
                            updateImageThreadDisplay(textView);
                            Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_91cd), Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomThreadDialog(final TextView textView) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 3);
        input.setText(String.valueOf(current == -1 ? 3 : current));

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_81ea))
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = input.getText().toString().trim();
                        if (s.length() == 0) return;
                        try {
                            final int val = Integer.parseInt(s);
                            if (val < 1 || val > 15) {
                                Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_8bf7_1), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (val > 5) {
                                new AlertDialog.Builder(DialogUtil.wrap(SettingsActivity.this))
                                        .setTitle(getString(R.string.settingsactivity_settitle_8b66))
                                        .setMessage("当前设置 " + val + " 个线程，超过安全建议值（5）。部分手机可能出现频繁卡顿甚至闪退的问题。若遇到此类问题，建议回到此处重新调低。\n\n确定继续吗？")
                                        .setPositiveButton("仍然设置", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                saveThreadValue(val, textView);
                                            }
                                        })
.setNegativeButton(getString(R.string.common_cancel), null)
                                        .show();
                            } else {
                                saveThreadValue(val, textView);
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_8bf7_2), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveThreadValue(int val, TextView textView) {
        SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, val);
        updateImageThreadDisplay(textView);
        Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_91cd), Toast.LENGTH_SHORT).show();
    }

    private void showDialogStyleDialog(final TextView textView) {
        int sdkInt = cn.ottohub.oh2013.util.SdkHelper.getSdkInt();
        if (sdkInt < 11) return; // 2.3 不支持样式选择
        final String[] items;
        final int[] values;
        if (sdkInt >= 21) {
            items = new String[]{"自动适配", "经典样式", "Holo", "Material"};
            values = new int[]{0, 1, 2, 3};
        } else {
            items = new String[]{"自动适配", "经典样式", "Holo"};
            values = new int[]{0, 1, 2};
        }
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DIALOG_STYLE, 0);
        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checkedIndex = i;
                break;
            }
        }
        new android.app.AlertDialog.Builder(cn.ottohub.oh2013.util.DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009))
                .setSingleChoiceItems(items, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int val = values[which];
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DIALOG_STYLE, val);
                        updateDialogStyleDisplay(textView);
                        Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_5df2_1), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDialogStyleDisplay(TextView textView) {
        int val = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DIALOG_STYLE, 0);
        if (val == 0) textView.setText(getString(R.string.settingsactivity_settext_81ea));
        else if (val == 1) textView.setText(getString(R.string.settingsactivity_settext_7ecf));
        else if (val == 2) textView.setText("Holo");
        else if (val == 3) textView.setText("Material");
    }

    // 语言选择
    private void showLocaleDialog(final TextView textView) {
        final String[] items = {"简体中文", "繁體中文"};
        final String[] values = {"zh_CN", "zh_TW"};
        String current = LocaleHelper.getCurrentLocale();
        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checkedIndex = i;
                break;
            }
        }
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_9009_3))
                .setSingleChoiceItems(items, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LocaleHelper.setCurrentLocale(values[which]);
                        dialog.dismiss();
                        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateLocaleDisplay(TextView textView) {
        String current = LocaleHelper.getCurrentLocale();
        if ("zh_TW".equals(current)) {
            textView.setText(getString(R.string.settingsactivity_settext_7e41));
        } else {
            textView.setText(getString(R.string.settingsactivity_settext_7b80));
        }
    }

    // 缓存管理
    private File getAvatarCacheFile() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                && PermissionUtil.hasWriteStorage(this)) {
            try {
                File externalCache = new File(Environment.getExternalStorageDirectory(), "BiliClassic/avatar_cache");
                if (!externalCache.exists()) {
                    externalCache.mkdirs();
                }
                return new File(externalCache, "avatar_cache.jpg");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new File(getCacheDir(), "avatar_cache.jpg");
    }

    private long getAvatarCacheSize() {
        File avatarFile = getAvatarCacheFile();
        if (avatarFile.exists()) {
            return avatarFile.length();
        }
        return 0;
    }

    private long getAnimeCacheSize() {
        try {
            File animeCacheDir = new File(getCacheDir(), "anime_cache");
            if (!animeCacheDir.exists()) {
                return 0;
            }
            return getFolderSize(animeCacheDir);
        } catch (Exception e) {
            return 0;
        }
    }

    private long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                size += getFolderSize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }

    private long getImageDiskCacheSize() {
        long size = 0;
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        File f = files[i];
                        if (f == null || f.isDirectory()) continue;
                        String n = f.getName();
                        if (n.startsWith("cover_") || n.startsWith("hom_")
                                || n.startsWith("srch_") || n.endsWith(".tmp")) {
                            size += f.length();
                        }
                    }
                }
            }
            File ohCache = new File(getCacheDir(), "oh2013_image_cache");
            size += getFolderSize(ohCache);
        } catch (Exception e) {
            // ignore
        }
        return size;
    }

    private long getTotalCacheSize() {
        return getAvatarCacheSize() + getAnimeCacheSize() + getImageDiskCacheSize();
    }

    private void updateCacheSize() {
        long totalSize = getTotalCacheSize();
        try {
            // 内存图缓存也计入提示（不精确到字节）
            cn.ottohub.oh2013.util.GlobalImageCache.getInstance();
        } catch (Throwable ignored) {
        }
        if (totalSize > 0) {
            cacheSizeText.setText(formatFileSize(totalSize));
        } else {
            cacheSizeText.setText(getString(R.string.settingsactivity_settext_65e0_2));
        }
    }

    private void showClearCacheDialog() {
        long totalSize = getTotalCacheSize();
        String sizeText = formatFileSize(totalSize);
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_6e05))
                .setMessage("将清除以下缓存：\n\n• 头像缓存\n• 封面/图片临时缓存\n• 内存图片缓存\n\n约 " + sizeText + "，清除后下次会自动重新下载。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearAllCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAllCache() {
        try {
            long freedSize = 0;
            int deletedCount = 0;

            File avatarFile = getAvatarCacheFile();
            if (avatarFile.exists()) {
                freedSize += avatarFile.length();
                if (avatarFile.delete()) {
                    deletedCount++;
                }
            }

            File animeCacheDir = new File(getCacheDir(), "anime_cache");
            if (animeCacheDir.exists()) {
                long size = getFolderSize(animeCacheDir);
                freedSize += size;
                deleteRecursive(animeCacheDir);
                deletedCount++;
            }

            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        File f = files[i];
                        if (f == null || f.isDirectory()) continue;
                        String n = f.getName();
                        if (n.startsWith("cover_") || n.startsWith("hom_")
                                || n.startsWith("srch_") || n.endsWith(".tmp")) {
                            freedSize += f.length();
                            if (f.delete()) deletedCount++;
                        }
                    }
                }
            }
            File ohCache = new File(getCacheDir(), "oh2013_image_cache");
            if (ohCache.exists()) {
                freedSize += getFolderSize(ohCache);
                deleteRecursive(ohCache);
                deletedCount++;
            }

            try {
                cn.ottohub.oh2013.util.GlobalImageCache.getInstance().releaseMemory();
            } catch (Throwable ignored) {
            }

            Toast.makeText(this, "已清除 " + deletedCount + " 项缓存，释放 " + formatFileSize(freedSize), Toast.LENGTH_SHORT).show();
            updateCacheSize();

        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private File getPlayCacheDir() {
        if (isSDCardAvailable() && PermissionUtil.hasWriteStorage(this)) {
            File sdCacheDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/cache");
            if (!sdCacheDir.exists()) {
                sdCacheDir.mkdirs();
            }
            return sdCacheDir;
        }
        return getCacheDir();
    }

    private boolean isSDCardAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private File[] getPlayCacheFiles() {
        File cacheDir = getPlayCacheDir();
        if (cacheDir == null || !cacheDir.exists()) {
            return new File[0];
        }

        File[] allFiles = cacheDir.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            return new File[0];
        }

        java.util.ArrayList<File> mp4Files = new java.util.ArrayList<File>();
        for (File file : allFiles) {
            if (file.isFile() && file.getName().endsWith(".mp4")) {
                mp4Files.add(file);
            }
        }

        return mp4Files.toArray(new File[mp4Files.size()]);
    }

    private long getPlayCacheTotalSize() {
        File[] cacheFiles = getPlayCacheFiles();
        long totalSize = 0;
        for (File file : cacheFiles) {
            totalSize += file.length();
        }
        return totalSize;
    }

    private int getPlayCacheFileCount() {
        return getPlayCacheFiles().length;
    }

    private void updatePlayCacheSize() {
        long totalSize = getPlayCacheTotalSize();
        int fileCount = getPlayCacheFileCount();

        if (totalSize > 0 && fileCount > 0) {
            String sizeText = formatFileSize(totalSize);
            playCacheSizeText.setText(sizeText + " (" + fileCount + "个视频)");
        } else {
            playCacheSizeText.setText(getString(R.string.settingsactivity_settext_65e0));
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return (size / 1024 / 1024) + " MB";
        } else {
            return (size / 1024 / 1024 / 1024) + " GB";
        }
    }

    private void showClearPlayCacheDialog() {
        int fileCount = getPlayCacheFileCount();
        if (fileCount == 0) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_6ca1_2), Toast.LENGTH_SHORT).show();
            return;
        }

        String totalSize = formatFileSize(getPlayCacheTotalSize());
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_6e05_1))
                .setMessage("确定要清除所有播放缓存吗？\n" +
                        "共 " + fileCount + " 个视频文件，总计 " + totalSize + "\n" +
                        "清除后可释放存储空间。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearPlayCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearPlayCache() {
        try {
            File[] cacheFiles = getPlayCacheFiles();
            int deletedCount = 0;
            long freedSpace = 0;

            for (File file : cacheFiles) {
                if (file.exists()) {
                    freedSpace += file.length();
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) {
                Toast.makeText(this, "已清除 " + deletedCount + " 个缓存文件，释放 " + formatFileSize(freedSpace), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, this.getString(R.string.settingsactivity_toast_6ca1_1), Toast.LENGTH_SHORT).show();
            }

            updatePlayCacheSize();

        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLoggedIn() {
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private void showCookieDialog() {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_7ba1))
                .setItems(new String[]{"保存到本地", "复制到剪切板", "从本地导入", "从剪切板导入"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                if (!checkLogin()) return;
                                exportCookieToFile();
                                break;
                            case 1:
                                if (!checkLogin()) return;
                                exportCookieToClipboard();
                                break;
                            case 2: importCookieFromFile(); break;
                            case 3: importCookieFromClipboard(); break;
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean checkLogin() {
        if (!isLoggedIn()) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_8bf7), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getCookieJson() {
        JSONObject json = new JSONObject();
        try {
            String cookies = SharedPreferencesUtil.getString("cookies", "");
            String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
            json.put("cookies", cookies);
            json.put("refresh_token", refreshToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    private File getCookieSaveFile() {
        File dir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                && PermissionUtil.hasWriteStorage(this)) {
            dir = new File(Environment.getExternalStorageDirectory(), "BiliClassic");
        } else {
            dir = getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "cookie_backup.json");
    }

    private void exportCookieToFile() {
        if (!PermissionUtil.hasWriteStorage(this)) {
            runWithStoragePermission(new Runnable() {
                @Override
                public void run() {
                    exportCookieToFile();
                }
            });
            return;
        }
        try {
            File file = getCookieSaveFile();
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(getCookieJson());
            fw.close();
            Toast.makeText(this, "已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCookieToClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(getCookieJson());
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_5df2_3), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_590d), Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromFile() {
        File file = getCookieSaveFile();
        if (!file.exists()) {
            Toast.makeText(this, "未找到备份文件: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            applyCookieJson(sb.toString());
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        final String clipText = cm.getText() != null ? cm.getText().toString() : "";
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(clipText);
        input.setMinLines(3);

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_7c98))
                .setView(input)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyCookieJson(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyCookieJson(String jsonStr) {
        if (jsonStr == null || jsonStr.length() == 0) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_5185), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            String cookies = json.optString("cookies", "");
            if (cookies == null || cookies.length() == 0) {
                Toast.makeText(this, this.getString(R.string.settingsactivity_toast_65e0), Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferencesUtil.putString("cookies", cookies);
            String refreshToken = json.optString("refresh_token", "");
            if (refreshToken != null && refreshToken.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
            }
            String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
            if (mid != null && mid.length() > 0) {
                try {
                    SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                } catch (NumberFormatException e) {
                }
            }
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
            if (csrf != null && csrf.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
            }
            NetWorkUtil.refreshHeaders();

            if (isLoggedIn()) {
                Toast.makeText(this, this.getString(R.string.settingsactivity_toast_5bfc_1), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, this.getString(R.string.settingsactivity_toast_5bfc), Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_683c), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private File getCrashLogDir() {
        return new File(getFilesDir().getParentFile(), "crashlog");
    }

    private File[] getCrashLogFiles() {
        File crashDir = getCrashLogDir();
        if (crashDir == null || !crashDir.exists()) {
            return new File[0];
        }
        File[] files = crashDir.listFiles();
        return files == null ? new File[0] : files;
    }

    private long getCrashLogTotalSize() {
        File[] files = getCrashLogFiles();
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        return total;
    }

    private int getCrashLogFileCount() {
        return getCrashLogFiles().length;
    }

    private void updateCrashLogSize() {
        int count = getCrashLogFileCount();
        long size = getCrashLogTotalSize();
        if (count == 0) {
            crashLogSizeText.setText(getString(R.string.settingsactivity_settext_65e0_1));
        } else {
            crashLogSizeText.setText(count + "个, " + formatFileSize(size));
        }
    }

    private void showClearCrashLogDialog() {
        int count = getCrashLogFileCount();
        if (count == 0) {
            Toast.makeText(this, this.getString(R.string.settingsactivity_toast_6ca1), Toast.LENGTH_SHORT).show();
            return;
        }

        String sizeText = formatFileSize(getCrashLogTotalSize());
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.settingsactivity_settitle_5220))
                .setMessage("确定要删除所有崩溃日志吗？\n共 " + count + " 个文件，总计 " + sizeText)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearCrashLog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearCrashLog() {
        try {
            File[] files = getCrashLogFiles();
            int deleted = 0;
            for (File f : files) {
                if (f.delete()) {
                    deleted++;
                }
            }
            Toast.makeText(this, "已删除 " + deleted + " 个崩溃日志", Toast.LENGTH_SHORT).show();
            updateCrashLogSize();

            if (getCrashLogFileCount() == 0) {
                getSharedPreferences("crash", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_crash", false)
                        .commit();
            }
        } catch (Exception e) {
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 回声洞
    private void loadEchoHole() {
        echoHoleText.setText(getString(R.string.settingsactivity_settext_563f));
        echoHoleItem.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection conn = NetWorkUtil.openCompat("https://api.ottohub.cn/");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    final String jsonStr = sb.toString();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            echoHoleText.setText(getString(R.string.settingsactivity_settext_968f));
                            echoHoleItem.setEnabled(true);
                            if (isFinishing()) return;
                            try {
                                JSONArray arr = new JSONArray(jsonStr);
                                if (arr.length() > 0) {
                                    int idx = (int) (Math.random() * arr.length());
                                    if (arr.length() > 1) {
                                        while (idx == mLastEchoIndex) {
                                            idx = (int) (Math.random() * arr.length());
                                        }
                                    }
                                    mLastEchoIndex = idx;
                                    JSONObject item = arr.getJSONObject(idx);
                                    String text = item.optString("text", "");
                                    String author = item.optString("author", "匿名");
                                    String device = item.optString("device", null);
                                    String time = item.optString("time", "未知");
                                    String msg = text + "\n\n" + author;
                                    if (device != null && device.length() > 0) {
                                        msg += "\n来自 " + device;
                                    }
                                    msg += "\n" + time;
                                    new AlertDialog.Builder(DialogUtil.wrap(SettingsActivity.this))
                                            .setTitle(getString(R.string.settingsactivity_settitle_56de))
                                            .setMessage(msg)
                                            .setPositiveButton("关闭", null)
                                            .show();
                                } else {
                                    Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_56de), Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.settingsactivity_toast_89e3), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (isFinishing()) return;
                            echoHoleText.setText(getString(R.string.settingsactivity_settext_968f));
                            echoHoleItem.setEnabled(true);
                            Toast.makeText(SettingsActivity.this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // 检查更新（使用 UpdateUtil）
    private void checkForUpdate() {
        checkUpdateText.setText(getString(R.string.settingsactivity_settext_6b63));
        checkUpdateItem.setEnabled(false);

        UpdateUtil.checkUpdate(this, currentVersionCode, currentVersionName,
                new UpdateUtil.UpdateCallback() {
                    @Override
                    public void onCheckStart() {
                        // UI 已经在调用前设置了
                    }

                    @Override
                    public void onCheckComplete(boolean hasUpdate, String message) {
                        checkUpdateText.setText(getString(R.string.settingsactivity_settext_68c0));
                        checkUpdateItem.setEnabled(true);
                        if (!hasUpdate) {
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCheckFailed(String error) {
                        checkUpdateText.setText(getString(R.string.settingsactivity_settext_68c0));
                        checkUpdateItem.setEnabled(true);
                        Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}