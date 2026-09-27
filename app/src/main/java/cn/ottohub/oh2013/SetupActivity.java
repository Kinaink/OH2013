package cn.ottohub.oh2013;


import cn.ottohub.oh2013.util.NetWorkUtil;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ScrollView;
import android.widget.FrameLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import cn.ottohub.oh2013.util.KeyBindingUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class SetupActivity extends BaseActivity {

    private View mPageWelcome;
    private View mPageTiles;
    private View mPageBinding;
    private int mSelectedTab = -1;
    private FrameLayout mLastSelectedTile = null;
    private boolean mAnimating = false;
    private boolean mOnPage2 = false;
    private boolean mOnPage3 = false;
    private JSONArray mPendingChangelog = null;
    private boolean mPendingChangelogFailed = false;

    // ===== 磁贴按键导航 =====
    // 按键机（有物理按键设备）在磁贴页可用方向键移动光标、确认键选中；
    // 触屏机不使用按键导航，文字不高亮。
    private final java.util.List<FrameLayout> mTiles = new java.util.ArrayList<FrameLayout>();
    private int mTileFocusIndex = 0;
    private boolean mTileKeyNavActive = false; // 仅按键机按键后才置 true
    private int mTileCols = 2; // 磁贴列数（按屏幕宽度自适应，平板/TV 更多列）
    private TextView mBtnStart = null; // 磁贴页"开始使用/下一页"按钮（按键导航最后一站）

    // ===== 按键绑定（Setup 第三页，复用 KeyBindingSetupActivity 的录制逻辑） =====
    private static final int[] RECORD_ORDER = {
        KeyBindingUtil.ACTION_SOFT_LEFT,
        KeyBindingUtil.ACTION_SOFT_RIGHT,
        KeyBindingUtil.ACTION_UP,
        KeyBindingUtil.ACTION_DOWN,
        KeyBindingUtil.ACTION_LEFT,
        KeyBindingUtil.ACTION_RIGHT,
        KeyBindingUtil.ACTION_CONFIRM,
        KeyBindingUtil.ACTION_NUM_0,
        KeyBindingUtil.ACTION_NUM_1,
        KeyBindingUtil.ACTION_NUM_2,
        KeyBindingUtil.ACTION_NUM_3,
        KeyBindingUtil.ACTION_NUM_4,
        KeyBindingUtil.ACTION_NUM_5,
        KeyBindingUtil.ACTION_NUM_6,
        KeyBindingUtil.ACTION_NUM_7,
        KeyBindingUtil.ACTION_NUM_8,
        KeyBindingUtil.ACTION_NUM_9,
        KeyBindingUtil.ACTION_STAR,
        KeyBindingUtil.ACTION_POUND
    };
    private static final String[] RECORD_NAMES = {
        "左软键", "右软键", "上方向键", "下方向键",
        "左方向键", "右方向键", "确认键",
        "数字键 0", "数字键 1", "数字键 2", "数字键 3", "数字键 4",
        "数字键 5", "数字键 6", "数字键 7", "数字键 8", "数字键 9",
        "* 星号键", "# 井号键"
    };
    private boolean mRecording = false;
    private int mRecordIndex = 0;
    private long mLastRecordTime = 0L;

    // ===== 绑定页询问页按键导航 =====
    // 按键机在询问页可用方向键选择"是/否"，确认键触发；BACK 回磁贴页
    private int mAskChoiceIndex = 0; // 0 = 是，1 = 否
    private boolean mAskKeyNavActive = false;

    static final int TAB_PROFILE = 0;
    static final int TAB_HOME = 1;
    static final int TAB_NEW_ANIME = 2;
    static final int TAB_TIMELINE = 3;
    static final int TAB_RECOMMEND = 4;
    static final int TAB_ABOUT = 5;

    static final String[] TAB_NAMES = {"个人中心", "分区导航", "新番专题", "放送时间表", "推荐视频", "关于我们"};
    static final int[] TAB_VALUES = {TAB_PROFILE, TAB_HOME, TAB_NEW_ANIME, TAB_TIMELINE, TAB_RECOMMEND, TAB_ABOUT};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // 圆形屏幕（手表）适配：标题行居中显示，避免偏左在圆屏上难看。
        // 用户手动开启"圆形屏幕居中"开关立即生效；系统自动检测（WindowInsets.isRound /
        // Configuration.isScreenRound）需等首帧布局后 insets 传递完成再判断。
        final View rootLayoutForRound = findViewById(R.id.root_layout);
        if (rootLayoutForRound != null) {
            rootLayoutForRound.post(new Runnable() {
                @Override
                public void run() {
                    boolean manual = cn.ottohub.oh2013.util.SharedPreferencesUtil.getBoolean(
                            cn.ottohub.oh2013.util.SharedPreferencesUtil.ROUND_SCREEN_CENTER, false);
                    boolean roundCenter = manual || cn.ottohub.oh2013.util.DeviceUtil.isRoundScreen(rootLayoutForRound);
                    android.util.Log.d("SetupRound", "roundCenter=" + roundCenter + ", manual=" + manual);
                    if (roundCenter) {
                        centerTitlesForRoundScreen();
                    }
                }
            });
        }

        final View rootLayout = findViewById(R.id.root_layout);
        // 不在启动时做 fromAlpha=0 的动画：部分机型硬件加速下会整页一直透明=白屏
        if (rootLayout != null) {
            rootLayout.clearAnimation();
            rootLayout.setVisibility(View.VISIBLE);
        }

        String mode = getIntent().getStringExtra("mode");
        final boolean isUpgrade = "upgrade".equals(mode);
        TextView titleText = (TextView) findViewById(R.id.title_text);
        TextView btnText = (TextView) findViewById(R.id.btn_text);
        if (titleText != null) {
            if (isUpgrade) {
                titleText.setText(getString(R.string.setupactivity_settext_66f4));
            } else {
                titleText.setText(getString(R.string.setupactivity_settext_521d));
            }
        }
        if (btnText != null && isUpgrade) {
            btnText.setText(getString(R.string.setupactivity_settext_6b22));
        }

        mPageWelcome = findViewById(R.id.page_welcome);
        mPageTiles = findViewById(R.id.page_tiles);
        mPageBinding = findViewById(R.id.page_binding);

        initBindingPage();

        TextView btnNext = (TextView) findViewById(R.id.btn_next);
        if (btnNext != null) {
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    slideToTiles();
                }
            });
        }

        TextView page2Title = (TextView) findViewById(R.id.page2_title);
        if (page2Title != null) {
            if (isUpgrade) {
                page2Title.setText(getString(R.string.setupactivity_settext_66f4_1));
                generateChangelog();
            } else {
                // 首次使用：阅读用户条款（替代原「选择初始主页」）
                page2Title.setText(getString(R.string.activity_setup_6761));
                generateTerms();
            }
        }

        final TextView btnStart = (TextView) findViewById(R.id.btn_start);
        mBtnStart = btnStart;
        // 首次：同意；升级：触屏「开始使用」/ 按键机「下一步」
        boolean hasHardwareKeys = cn.ottohub.oh2013.util.DeviceUtil.hasHardwareKeys(SetupActivity.this);
        if (isUpgrade) {
            btnStart.setText(hasHardwareKeys
                    ? getString(R.string.activity_setup_4e0b)
                    : getString(R.string.activity_setup_5f00));
        } else {
            btnStart.setText(getString(R.string.activity_setup_540c));
        }
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAnimating) return;
                // 首次启动（非升级）、设备有物理按键且尚未绑定任何按键 → 滑入绑定页
                boolean isUpgrade = "upgrade".equals(getIntent().getStringExtra("mode"));
                boolean needBinding = !isUpgrade && !KeyBindingUtil.anyBound()
                        && cn.ottohub.oh2013.util.DeviceUtil.hasHardwareKeys(SetupActivity.this);
                if (needBinding) {
                    slideToBinding();
                } else {
                    mAnimating = true;
                    final int h = mPageTiles.getHeight();
                    if (h > 0) {
                        TranslateAnimation exit = new TranslateAnimation(0, 0, 0, h);
                        exit.setDuration(400);
                        exit.setInterpolator(new AccelerateInterpolator());
                        exit.setFillAfter(true);
                        exit.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                            }
                            @Override
                            public void onAnimationEnd(Animation animation) {
                                finishSetup(btnStart);
                            }
                            @Override
                            public void onAnimationRepeat(Animation animation) {
                            }
                        });
                        mPageTiles.startAnimation(exit);
                    } else {
                        finishSetup(btnStart);
                    }
                }
            }
        });
    }

    /**
     * 圆形屏幕（手表）适配：各页标题行与底部按钮行水平居中。
     */
    private void centerTitlesForRoundScreen() {
        int[] rowIds = {
                R.id.welcome_title_row,
                R.id.tiles_title_row,
                R.id.ask_title_row,
                R.id.record_title_row,
                R.id.welcome_bottom_row,
                R.id.tiles_bottom_row
        };
        for (int id : rowIds) {
            View row = findViewById(id);
            if (row instanceof LinearLayout) {
                ((LinearLayout) row).setGravity(Gravity.CENTER);
            }
        }
        // 录制页标题占满行宽（weight=1），文字本身也要居中
        TextView recordTitle = (TextView) findViewById(R.id.record_title);
        if (recordTitle != null) {
            recordTitle.setGravity(Gravity.CENTER);
        }
    }

    private void finishSetup(View btnStart) {
        if (btnStart != null) btnStart.setEnabled(false);
        if (mSelectedTab >= 0) {
            SharedPreferencesUtil.putInt("default_tab", TAB_VALUES[mSelectedTab]);
        }
        SharedPreferencesUtil.putBoolean("setup_shown", true);
        int versionCode = 0;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferencesUtil.putInt("last_version_code", versionCode);
        enterMain();
    }

    private void enterMain() {
        Intent intent = new Intent(SetupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * 初始化按键绑定页（第三页）的控件与点击事件。
     */
    private void initBindingPage() {
        if (mPageBinding == null) return;
        TextView btnYes = (TextView) findViewById(R.id.btn_yes);
        if (btnYes != null) {
            btnYes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    startRecording();
                }
            });
        }
        TextView btnNo = (TextView) findViewById(R.id.btn_no);
        if (btnNo != null) {
            btnNo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    finishBinding();
                }
            });
        }
        TextView btnExit = (TextView) findViewById(R.id.btn_exit);
        if (btnExit != null) {
            btnExit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    finishBinding();
                }
            });
        }
    }

    /**
     * 磁贴页 → 绑定页：整页横向滑入（与欢迎→磁贴转场一致）。
     */
    private void slideToBinding() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageTiles.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        findViewById(R.id.btn_start).setEnabled(false);
        // 绑定页滑入动画期间禁用其按钮，防止未完成时点 btn_no 直接跳过向导
        findViewById(R.id.btn_yes).setEnabled(false);
        findViewById(R.id.btn_no).setEnabled(false);
        findViewById(R.id.btn_exit).setEnabled(false);

        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final ViewGroup bindingGroup = (ViewGroup) mPageBinding;
        final ViewGroup askPage = (ViewGroup) findViewById(R.id.page_ask);
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);

        mPageBinding.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_next).setEnabled(false);

        // 先清残留动画（防止返回再进入时空白）
        bindingGroup.clearAnimation();
        tilesGroup.clearAnimation();
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                askPage.getChildAt(i).clearAnimation();
            }
        }

        // 磁贴行先向左滑出（逐行，与 slideToTiles 的滑入对称）
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            row.startAnimation(a);
        }
        int rowBase = tileContainer.getChildCount() * 60 + 80;

        // 磁贴页标题、按钮向左滑出（分割线、ScrollView 不动）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        int baseDelay = rowBase + tileIdx * 60 + 80;

        // 绑定页标题、消息、按钮从右滑入（分割线不动）
        if (askPage != null) {
            int bindIdx = 0;
            for (int i = 0; i < askPage.getChildCount(); i++) {
                if (i == 1) continue; // 分割线不动
                View child = askPage.getChildAt(i);
                TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
                a.setDuration(450);
                a.setStartOffset(baseDelay + bindIdx * 80);
                a.setInterpolator(new DecelerateInterpolator());
                a.setFillAfter(true);
                if (i == askPage.getChildCount() - 1) {
                    a.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            mAnimating = false;
                            mOnPage2 = false;
                            mOnPage3 = true;
                            mPageTiles.setVisibility(View.GONE);
                            tilesGroup.clearAnimation();
                            bindingGroup.clearAnimation();
                            for (int j = 0; j < askPage.getChildCount(); j++) {
                                askPage.getChildAt(j).clearAnimation();
                            }
                            for (int k = 0; k < tileContainer.getChildCount(); k++) {
                                tileContainer.getChildAt(k).clearAnimation();
                            }
                            // 动画完成：启用绑定页按钮（"是，开始录制" / "不需要"）
                            findViewById(R.id.btn_yes).setEnabled(true);
                            findViewById(R.id.btn_no).setEnabled(true);
                            findViewById(R.id.btn_exit).setEnabled(true);
                        }
                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                }
                child.startAnimation(a);
                bindIdx++;
            }
        }
        // 若 page_ask 为空则直接结束转场
        if (askPage == null || askPage.getChildCount() == 0) {
            mAnimating = false;
            mOnPage2 = false;
            mOnPage3 = true;
            mPageTiles.setVisibility(View.GONE);
            findViewById(R.id.btn_yes).setEnabled(true);
            findViewById(R.id.btn_no).setEnabled(true);
            findViewById(R.id.btn_exit).setEnabled(true);
        }
    }

    /**
     * 绑定页 → 磁贴页（返回）：绑定页元素逐行向右滑出，磁贴页元素/磁贴行逐行从左滑入
     * （磁贴页在绑定页左侧，返回时从左回来，与 slideToBinding 对称）。
     */
    private void slideBackToTiles() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageTiles.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        mPageTiles.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_start).setEnabled(true);

        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final ViewGroup bindingGroup = (ViewGroup) mPageBinding;
        final ViewGroup askPage = (ViewGroup) findViewById(R.id.page_ask);

        // 先清残留动画（防止再进入时空白）
        bindingGroup.clearAnimation();
        tilesGroup.clearAnimation();
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                askPage.getChildAt(i).clearAnimation();
            }
        }
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        for (int k = 0; k < tileContainer.getChildCount(); k++) {
            tileContainer.getChildAt(k).clearAnimation();
        }

        // 绑定页标题、消息、按钮向右滑出（分割线不动）
        int bindIdx = 0;
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                if (i == 1) continue; // 分割线不动
                View child = askPage.getChildAt(i);
                TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
                a.setDuration(300);
                a.setStartOffset(bindIdx * 60);
                a.setInterpolator(new AccelerateInterpolator());
                a.setFillAfter(true);
                child.startAnimation(a);
                bindIdx++;
            }
        }
        int rowBase = bindIdx * 60 + 80;

        // 磁贴页标题、按钮从左滑入（分割线不动，磁贴容器单独处理）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        // 磁贴行逐行从左滑入（最后一个磁贴行挂完成监听）
        int baseDelay = rowBase + tileIdx * 60 + 80;
        final LinearLayout finalTileContainer = tileContainer;
        int lastRow = tileContainer.getChildCount() - 1;
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(baseDelay + i * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == lastRow) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage3 = false;
                        mOnPage2 = true;
                        mPageBinding.setVisibility(View.GONE);
                        findViewById(R.id.btn_next).setEnabled(false);
                        tilesGroup.clearAnimation();
                        bindingGroup.clearAnimation();
                        for (int j = 0; j < askPage.getChildCount(); j++) {
                            askPage.getChildAt(j).clearAnimation();
                        }
                        for (int k2 = 0; k2 < finalTileContainer.getChildCount(); k2++) {
                            finalTileContainer.getChildAt(k2).clearAnimation();
                        }
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            row.startAnimation(a);
        }
    }

    private void startRecording() {
        mRecording = true;
        mRecordIndex = 0;
        mLastRecordTime = 0L;
        KeyBindingUtil.clearAll();
        findViewById(R.id.page_ask).setVisibility(View.GONE);
        findViewById(R.id.page_record).setVisibility(View.VISIBLE);
        updatePrompt();
    }

    private void updatePrompt() {
        TextView promptText = (TextView) findViewById(R.id.prompt_text);
        TextView progressText = (TextView) findViewById(R.id.progress_text);
        if (mRecordIndex >= RECORD_ORDER.length) {
            promptText.setText(getString(R.string.keybinding_done));
            progressText.setText("");
            finishBinding();
            return;
        }
        String keyName = RECORD_NAMES[mRecordIndex];
        promptText.setText(getString(R.string.keybinding_prompt_prefix) + " " + keyName);
        progressText.setText(getString(R.string.keybinding_progress,
                mRecordIndex + 1, RECORD_ORDER.length));
    }

    /**
     * 询问页按键导航高亮：当前选择项（是/否）背景变深粉，另一项恢复原样。
     * 用 state_selected selector + setSelected 驱动，避免 setBackgroundColor/
     * setBackgroundResource 混用导致老设备上高亮残留或尺寸跳动。
     */
    private void applyAskHighlight() {
        TextView btnYes = (TextView) findViewById(R.id.btn_yes);
        TextView btnNo = (TextView) findViewById(R.id.btn_no);
        if (btnYes != null) {
            btnYes.setSelected(mAskChoiceIndex == 0);
            btnYes.setTextColor(0xFFFFFFFF);
        }
        if (btnNo != null) {
            btnNo.setSelected(mAskChoiceIndex == 1);
            btnNo.setTextColor(mAskChoiceIndex == 1 ? 0xFFFFFFFF : 0xFFFF8C00);
        }
    }

    private void finishBinding() {
        SharedPreferencesUtil.putBoolean("setup_shown", true);
        try {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            SharedPreferencesUtil.putInt("last_version_code", versionCode);
        } catch (Exception e) {
        }
        if (mAnimating) {
            enterMain();
            return;
        }
        // 与磁贴页"开始使用"完成时一致：整页向下滑出后再进主界面
        mAnimating = true;
        final int h = mPageBinding != null ? mPageBinding.getHeight() : 0;
        if (h > 0) {
            TranslateAnimation exit = new TranslateAnimation(0, 0, 0, h);
            exit.setDuration(400);
            exit.setInterpolator(new AccelerateInterpolator());
            exit.setFillAfter(true);
            exit.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }
                @Override
                public void onAnimationEnd(Animation animation) {
                    enterMain();
                }
                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            mPageBinding.startAnimation(exit);
        } else {
            enterMain();
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // 录制页：任意按键被录入（BACK 除外——BACK 作为退出向导的逃生通道）
        if (mOnPage3 && mRecording && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                finishBinding();
                return true;
            }
            if (mRecordIndex < RECORD_ORDER.length) {
                long now = System.currentTimeMillis();
                if (now - mLastRecordTime < 1000L) {
                    return true;
                }
                mLastRecordTime = now;
                int action = RECORD_ORDER[mRecordIndex];
                KeyBindingUtil.saveKey(action, event.getKeyCode());
                mRecordIndex++;
                updatePrompt();
                return true;
            }
            return true;
        }
        // 绑定页询问页按键导航（按键机）：方向键切换"是/否"，确认键触发
        if (mOnPage3 && !mRecording
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_LEFT
                    || act == KeyBindingUtil.ACTION_RIGHT
                    || act == KeyBindingUtil.ACTION_UP
                    || act == KeyBindingUtil.ACTION_DOWN) {
                if (!mAskKeyNavActive) {
                    // 首次按键只激活高亮（停在默认项"是"），不切换选择
                    mAskKeyNavActive = true;
                    applyAskHighlight();
                    return true;
                }
                mAskChoiceIndex = 1 - mAskChoiceIndex;
                applyAskHighlight();
                return true;
            } else if (act == KeyBindingUtil.ACTION_CONFIRM) {
                if (!mAskKeyNavActive) {
                    mAskKeyNavActive = true;
                    applyAskHighlight();
                }
                if (mAskChoiceIndex == 0) {
                    startRecording();
                } else {
                    finishBinding();
                }
                return true;
            }
        }
        // 条款页（无磁贴）：方向键滚动正文，确认键触发「同意」
        if (mOnPage2 && mTiles.size() == 0
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_UP || act == KeyBindingUtil.ACTION_DOWN) {
                ScrollView tileScroll = (ScrollView) findViewById(R.id.tile_scroll);
                if (tileScroll != null) {
                    int delta = dpToPx(80);
                    tileScroll.smoothScrollBy(0, act == KeyBindingUtil.ACTION_DOWN ? delta : -delta);
                }
                return true;
            } else if (act == KeyBindingUtil.ACTION_CONFIRM) {
                if (mBtnStart != null) {
                    mBtnStart.performClick();
                }
                return true;
            }
        }
        // 磁贴页按键导航（仅按键机，触屏机不响应）：方向键移动光标，确认键选中
        if (mOnPage2 && mTiles.size() > 0
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_UP
                    || act == KeyBindingUtil.ACTION_DOWN
                    || act == KeyBindingUtil.ACTION_LEFT
                    || act == KeyBindingUtil.ACTION_RIGHT
                    || act == KeyBindingUtil.ACTION_CONFIRM) {
                // 首次按键即进入按键导航模式，文字高亮生效
                if (!mTileKeyNavActive) {
                    mTileKeyNavActive = true;
                    applyTileHighlight();
                }
                if (act == KeyBindingUtil.ACTION_UP) {
                    moveTileFocus(-1);
                } else if (act == KeyBindingUtil.ACTION_DOWN) {
                    moveTileFocus(1);
                } else if (act == KeyBindingUtil.ACTION_LEFT) {
                    moveTileFocus(-2);
                } else if (act == KeyBindingUtil.ACTION_RIGHT) {
                    moveTileFocus(2);
                } else if (act == KeyBindingUtil.ACTION_CONFIRM) {
                    confirmTile();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mAnimating) return;
        if (mOnPage3) {
            // 录制中按 BACK：退出向导（dispatchKeyEvent 已处理，但还是保留XD）
            if (mRecording) {
                finishBinding();
                return;
            }
            slideBackToTiles();
        } else if (mOnPage2) {
            slideToWelcome();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 按屏幕宽度自适应磁贴列数：
     * 超小屏（<360dp，手表等）2 列；手机（<600dp）2 列；平板（600-900dp）3 列；大屏 TV/横屏（≥900dp）4 列。
     */
    private int computeTileCols() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        if (widthDp >= 900) {
            return 4;
        } else if (widthDp >= 600) {
            return 3;
        }
        return 2;
    }

    private void generateTiles() {
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        tileContainer.removeAllViews();

        mTileCols = computeTileCols();

        for (int i = 0; i < TAB_NAMES.length; i += mTileCols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            // 铺不满的一行从左边开始排，避免整行居中
            row.setGravity(Gravity.LEFT);
            int rowPad = dpToPx(8);
            int rowPadV = dpToPx(4);
            row.setPadding(rowPad, rowPadV, rowPad, rowPadV);

            for (int c = 0; c < mTileCols; c++) {
                int idx = i + c;
                if (idx >= TAB_NAMES.length) {
                    break;
                }
                row.addView(createTile(idx));
            }

            tileContainer.addView(row);
        }
    }

    /** 首次使用：用户条款正文（替代磁贴选主页） */
    private void generateTerms() {
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        tileContainer.removeAllViews();
        mTiles.clear();
        mTileKeyNavActive = false;

        TextView tv = new TextView(this);
        tv.setText(USER_TERMS_TEXT);
        tv.setTextSize(13);
        tv.setTextColor(0xFF333333);
        tv.setLineSpacing(dpToPx(2), 1.15f);
        int pad = dpToPx(12);
        tv.setPadding(pad, pad, pad, pad);
        tileContainer.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static final String USER_TERMS_TEXT =
            "平台内容管理规范\n\n"
            + "总则\n\n"
            + "一、制定目的\n"
            + "为维护平台健康、正向、清朗的内容生态，保障全体用户舒适、安全的浏览与创作体验，"
            + "明确内容创作、发布、传播的行为边界，杜绝违法违规、违背道德、含沙射影、有悖大众认知、"
            + "令人引起不适、违反社会主义核心价值观等内容，特制定本规章。\n"
            + "本规章适用于平台所有注册用户、创作者、志愿者，站务员。"
            + "该条例覆盖视频与动态编辑等使用及观看此网站的用户以及发布、传播、评论、点赞、收藏等全部操作行为。\n\n"
            + "二、核心准则\n"
            + "平台内容创作与发布需坚守合法合规、公序良俗、文明友善、正向价值、大众接受五大原则，"
            + "严禁触碰法律底线、违背道德风尚、恶意影射、违规搬运、含沙射影及发布绝大多数用户严重反感的内容。\n\n"
            + "第一章 内容管理规范\n\n"
            + "一级内容管理规范\n"
            + "违反中华人民共和国宪法基本原则、危害国家主权、有损国家安全、泄露国家机密、颠覆国家政权、破坏国家统一等内容。\n"
            + "出现古今中外敏感政治人物相关的文字、图片、视频、影像等任何形式内容。\n"
            + "直接讨论、传播、解读、宣传任何带有意识形态色彩的话题、观点与论述、"
            + "禁止运用宏大叙事、团结群众、民族议题为理由输出任何政治观点。\n"
            + "严禁宣扬、展示、教唆偷窃、嫖娼、抢劫、赌博、吸毒等各类违反法律条文的犯罪行为。\n"
            + "发布、传播包含血腥暴力、药物滥用画面、音效、文字描述的内容。\n"
            + "发布、讨论、传播、娱乐化重大社会争议性事件、争议性人物相关内容。\n"
            + "针对特定人士的人身攻击，侮辱或者诽谤他人，侵害他人名誉、肖像、隐私和其他合法权益的内容。\n"
            + "可能对站点安全或用户账号安全及隐私产生危害的内容。\n"
            + "中华人民共和国有关部门禁止的内容\n\n"
            + "次级内容管理规范\n"
            + "发布、传播任何包含裸露性器官，内容主体为性的图片、视频、动态、文字等。\n"
            + "发布严重违背公序良俗的视频及动态内容，包括但不限于校园霸凌、打架斗殴、虐待动物等行为的画面展示、教唆、传播。\n"
            + "严禁针对奶龙、哪吒（含全品类IP形象）等IP、国民级动漫影视形象，进行恶意解构、低俗恶搞、丑化扭曲、恶意玩梗、恶意配文、恶意剪辑等创作与发布；"
            + "严禁发布经平台判定、用户集中反馈，会引发绝大多数受众生理不适、心理反感、情绪抵触的猎奇、惊悚、恶意引战、无底线蹭流内容。\n"
            + "间接借助历史事件、游戏剧情、小说内容、影像作品（如火影忍者、BanG Dream! It's MyGO!!!!!等）等载体影射现实、当下、特定历史时期或事件，"
            + "所发布、解读的剧情、内容须不脱离原著框架，仅允许基于原作内容进行解读，禁止过度掺杂个人主观判断或以解读为由输出强烈的个人情绪。\n\n"
            + "第二章 用户行为规范\n"
            + "搬运他人视频、动态内容的，必须清晰、准确注明原出处，包括作者、来源平台、原作品链接等信息。\n"
            + "搬运视频须在简介中添加不少于15字的原视频内容说明，完整客观介绍原视频核心信息，防止内容被断章取义、恶意剪辑。\n"
            + "严禁在平台内以任何形式刷屏、恶意刷屏、辱骂他人、人身攻击、引战对立、骚扰诽谤、发布低俗色情及违反公序良俗的言论与内容。\n"
            + "严禁无授权搬运、侵权搬运、篡改原内容搬运，未按要求注明出处及内容说明的搬运内容，平台将予以限流、下架处理。\n"
            + "严禁任何用户以平台官方、管理员、客服、工作人员等名义进行注册、发言、发布内容、引导用户及其他任何行为；"
            + "严禁使用与官方高度相似、足以引起普通用户混淆、误认的昵称、头像、简介、标题、标识等内容进行伪装。\n"
            + "发布虚假信息，或未经证实的内容，滥用弹劾、举报程序。\n\n"
            + "第三章 用户责任与平台处理措施\n\n"
            + "用户责任义务\n"
            + "用户发布内容前须自行完成全面自查，确保内容符合本规章所有要求；"
            + "发现违规内容可通过平台官方渠道举报，严禁恶意举报、诬告陷害；须配合平台对违规内容的整改、处理决定。\n"
            + "若出现一级内容管理规范所述之情形或违反用户行为规范第四条之规定，站务员可直接对相关稿件或内容予以删除，并对发布者提出警告，"
            + "若屡次违规或情节严重者，站务员可直接实施永久封禁措施。\n"
            + "若出现次级内容管理条例所述之情形或违反用户行为规范非第四条之规定，站务员可视情况对相关稿件或内容予以删除，"
            + "须积极联络版权相关方，并对发布者提出警告，若屡次违规或情节严重者，站务员有权对其实施封禁、禁言等措施。\n\n"
            + "违规处理方式\n"
            + "对违反本规章的内容，平台有权立即下架、屏蔽、删除、限流；"
            + "根据违规情节轻重，对账号采取警告、禁言、限制发布、短期封禁、永久封禁等处罚；"
            + "因违规内容造成平台或他人权益受损的，平台将依法追究法律责任。\n\n"
            + "第四章 全域封禁\n"
            + "鉴于OTTOhub与姊妹站OTTOWiki关系密切，且同属管辖。"
            + "若一方站点内出现重大违规行为的封禁用户，且该用户在两个站点亦存在确认身份的账号时，"
            + "则需至少一名OTTOhub的站务员与至少一名OTTOWiki的行政员参与全域封禁讨论。"
            + "若讨论结果同意，则对未封禁的目标账号实施全域封禁，否则任何站务员不得以全域封禁为由封禁用户。\n\n"
            + "第五章 条例的判定与更新\n\n"
            + "一、判定与更新规则\n"
            + "平台对违规内容、不适内容的判定，将依据用户反馈数据、社会公序良俗、内容导向、站务员决断等综合认定，结果具有最终效力；"
            + "平台可根据国家政策、法律法规、内容生态、用户反馈等变化为参考，经站务员讨论后更新本条例，更新公示后立即生效。\n\n"
            + "二、生效时间\n"
            + "本规章自发布之日起正式施行，全体用户务必严格遵守。";

    private FrameLayout createTile(final int index) {
        // 磁贴宽度 = (屏幕宽 - 容器padding - 单磁贴左右margin) / 列数
        int paddingPx = dpToPx(8) * 2;
        int marginPx = dpToPx(8) * 2;
        int tileSizePx = (getResources().getDisplayMetrics().widthPixels - paddingPx - marginPx) / mTileCols;
        int minTilePx = (int) getResources().getDimension(R.dimen.setup_tile_min_width);
        if (tileSizePx < minTilePx) {
            tileSizePx = minTilePx;
        }
        // 超小屏：磁贴总宽不能超过屏幕宽（否则换行错乱）
        int maxTilePx = (getResources().getDisplayMetrics().widthPixels - paddingPx - marginPx) / mTileCols;
        if (tileSizePx > maxTilePx) {
            tileSizePx = maxTilePx;
        }
        if (tileSizePx < dpToPx(40)) {
            tileSizePx = dpToPx(40);
        }

        FrameLayout tile = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSizePx, (int) (tileSizePx * 0.7f));
        int tileMargin = dpToPx(8);
        lp.setMargins(tileMargin, tileMargin, tileMargin, tileMargin);
        tile.setLayoutParams(lp);
        tile.setFocusable(true);
        tile.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setColor(0xFFFF8C00);

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setShape(GradientDrawable.RECTANGLE);
        pressedBg.setColor(0xFFE67300);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        sld.addState(new int[]{}, normalBg);

        tile.setBackgroundDrawable(sld);

        TextView label = new TextView(this);
        label.setText(TAB_NAMES[index]);
        label.setTextColor(Color.WHITE);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.setup_tile_text_size));
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        labelLp.gravity = Gravity.CENTER;
        label.setLayoutParams(labelLp);
        tile.addView(label);

        final View checkmarkOverlay = createCheckmarkOverlay();
        checkmarkOverlay.setVisibility(View.INVISIBLE);
        tile.addView(checkmarkOverlay);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastSelectedTile != null) {
                    View prevCheck = mLastSelectedTile.getChildAt(1);
                    if (prevCheck != null) {
                        prevCheck.setVisibility(View.INVISIBLE);
                    }
                }
                checkmarkOverlay.setVisibility(View.VISIBLE);
                mLastSelectedTile = (FrameLayout) v;
                mSelectedTab = index;
            }
        });

        mTiles.add(tile);
        return tile;
    }

    /**
     * 刷新磁贴按键导航高亮：仅按键机按键后生效（mTileKeyNavActive）。
     * 当前焦点磁贴背景变为深粉色，其余恢复原粉色；
     * 焦点在"下一步/开始使用"按钮时，按钮变为深粉底白字，否则恢复透明粉字。
     */
    private void applyTileHighlight() {
        if (!mTileKeyNavActive) {
            return;
        }
        for (int i = 0; i < mTiles.size(); i++) {
            FrameLayout tile = mTiles.get(i);
            if (tile == null) {
                continue;
            }
            tile.setBackgroundColor(i == mTileFocusIndex ? 0xFFE67300 : 0xFFFF8C00);
        }
        if (mBtnStart != null) {
            boolean focusStart = (mTileFocusIndex >= mTiles.size());
            if (focusStart) {
                mBtnStart.setBackgroundColor(0xFFE67300);
                mBtnStart.setTextColor(0xFFFFFFFF);
            } else {
                mBtnStart.setBackgroundDrawable(null);
                mBtnStart.setTextColor(0xFFFF8C00);
            }
        }
    }

    /**
     * 移动磁贴光标。direction：-1 上，+1 下，-2 左，+2 右。
     * 焦点索引范围 [0, mTiles.size()]，其中 mTiles.size() 表示"下一步"按钮。
     */
    private void moveTileFocus(int direction) {
        if (mTiles.size() == 0) {
            return;
        }
        int next = mTileFocusIndex;
        boolean onStartBtn = (mTileFocusIndex >= mTiles.size());
        if (onStartBtn) {
            // 焦点在"下一步"按钮：只允许向上回最后一排
            if (direction == -1) {
                int lastRowFirst = ((mTiles.size() - 1) / mTileCols) * mTileCols;
                next = Math.min(lastRowFirst + (mTileFocusIndex - mTiles.size()), mTiles.size() - 1);
            } else {
                return;
            }
        } else if (direction == 1 || direction == -1) {
            int nextRow = (mTileFocusIndex / mTileCols) + direction;
            if (nextRow < 0) {
                return; // 已是最上一行
            }
            int firstInRow = nextRow * mTileCols;
            if (firstInRow >= mTiles.size()) {
                // 下一行超出：若向下则移到"下一步"按钮
                if (direction == 1) {
                    next = mTiles.size();
                } else {
                    return;
                }
            } else {
                next = Math.min(firstInRow + (mTileFocusIndex % mTileCols), mTiles.size() - 1);
            }
        } else if (direction == 2) {
            if ((mTileFocusIndex % mTileCols) == mTileCols - 1
                    || mTileFocusIndex == mTiles.size() - 1) {
                return;
            }
            next = mTileFocusIndex + 1;
        } else if (direction == -2) {
            if (mTileFocusIndex % mTileCols == 0) {
                return;
            }
            next = mTileFocusIndex - 1;
        }
        if (next != mTileFocusIndex) {
            mTileFocusIndex = next;
            applyTileHighlight();
        }
    }

    /**
     * 确认选中当前聚焦磁贴（按键机）；焦点在"下一步"按钮时触发该按钮。
     */
    private void confirmTile() {
        if (mTileFocusIndex < 0) {
            return;
        }
        if (mTileFocusIndex >= mTiles.size()) {
            // 焦点在"下一步/开始使用"按钮
            if (mBtnStart != null) {
                mBtnStart.performClick();
            }
            return;
        }
        FrameLayout tile = mTiles.get(mTileFocusIndex);
        if (tile != null) {
            tile.performClick();
        }
    }

    private View createCheckmarkOverlay() {
        ImageView checkIcon = new ImageView(this);
        checkIcon.setImageResource(R.drawable.abs__ic_cab_done_holo_dark);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpToPx(28), dpToPx(28)
        );
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setMargins(0, 0, dpToPx(8), dpToPx(8));
        checkIcon.setLayoutParams(lp);
        return checkIcon;
    }

    private void slideToTiles() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageWelcome.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        final ScrollView tileScroll = (ScrollView) findViewById(R.id.tile_scroll);
        if (tileScroll != null) tileScroll.scrollTo(0, 0);

        mPageTiles.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_next).setEnabled(false);
        findViewById(R.id.btn_start).setEnabled(false);

        final ViewGroup welcomeGroup = (ViewGroup) mPageWelcome;
        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;

        // 第1页各元素向左滑出，逐行延迟（分割线不动）
        int[] outDurs = {500, 0, 380, 320};
        for (int i = 0; i < welcomeGroup.getChildCount() && i < outDurs.length; i++) {
            if (outDurs[i] == 0) continue;
            View child = welcomeGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(outDurs[i]);
            a.setStartOffset(i * 80);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
        }

        int baseDelay = welcomeGroup.getChildCount() * 80 + 120;

        // 第2页标题、按钮滑入（分割线不动，磁贴容器单独处理）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(450);
            a.setStartOffset(baseDelay + tileIdx * 80);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        // 磁贴行逐行滑入（速度与 slideToBinding 的磁贴行滑出一致：duration 300、行间隔 60），
        // 完成监听器挂到最后一个磁贴行，保证所有行都滑入后才结束转场。
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        int rowBase = baseDelay + tileIdx * 80 + 60;
        int lastRow = tileContainer.getChildCount() - 1;
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(rowBase + i * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == lastRow) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage2 = true;
                        mPageWelcome.setVisibility(View.GONE);
                        findViewById(R.id.btn_start).setEnabled(true);
                        clearChildAnimations(welcomeGroup);
                        clearChildAnimations(tilesGroup);
                        for (int j = 0; j < tileContainer.getChildCount(); j++) {
                            tileContainer.getChildAt(j).clearAnimation();
                        }
                        applyPendingChangelog();
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            row.startAnimation(a);
        }
    }

    private void generateChangelog() {
        final LinearLayout container = (LinearLayout) findViewById(R.id.tile_container);
        container.removeAllViews();

        final TextView loadingText = new TextView(this);
        loadingText.setText(getString(R.string.setupactivity_settext_6b63));
        loadingText.setTextColor(0xFF999999);
        loadingText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.setup_changelog_text_size));
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, (int) getResources().getDimension(R.dimen.setup_changelog_top_padding), 0, 0);
        loadingText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(loadingText);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final JSONArray changelog = fetchChangelog();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mPendingChangelog = changelog;
                        renderChangelogIfReady();
                    }
                });
            }
        }).start();
    }

    private void applyPendingChangelog() {
        if (mPendingChangelog != null || mPendingChangelogFailed) {
            renderChangelogIfReady(true);
        }
    }

    private void renderChangelogIfReady() {
        renderChangelogIfReady(false);
    }

    private void renderChangelogIfReady(boolean animateRows) {
        // 转场中不填充，避免 removeAllViews 打断滑入动画
        if (mAnimating || isFinishing()) return;
        final LinearLayout container = (LinearLayout) findViewById(R.id.tile_container);
        container.removeAllViews();
        final JSONArray changelog = mPendingChangelog;
        mPendingChangelog = null;
        mPendingChangelogFailed = false;
        if (changelog == null) {
            TextView errorText = new TextView(SetupActivity.this);
            errorText.setText("\u83B7\u53D6\u66F4\u65B0\u65E5\u5FD7\u5931\u8D25");
            errorText.setTextColor(0xFF999999);
            errorText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.setup_changelog_text_size));
            errorText.setGravity(Gravity.CENTER);
            errorText.setPadding(0, (int) getResources().getDimension(R.dimen.setup_changelog_top_padding), 0, 0);
            errorText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            container.addView(errorText);
            return;
        }
        int textColor = 0xFF333333;
        int pinkColor = 0xFFFF8C00;
        for (int i = 0; i < changelog.length(); i++) {
            String line = changelog.optString(i, "");
            if (line.length() == 0) {
                View spacer = new View(SetupActivity.this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12)));
                container.addView(spacer);
            } else if (line.startsWith("-")) {
                TextView tv = new TextView(SetupActivity.this);
                tv.setText(line);
                tv.setTextColor(textColor);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.setup_changelog_text_size));
                tv.setPadding(dpToPx(24), dpToPx(4), dpToPx(16), dpToPx(4));
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                container.addView(tv);
            } else {
                TextView tv = new TextView(SetupActivity.this);
                tv.setText(line);
                tv.setTextColor(pinkColor);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.setup_changelog_title_size));
                tv.setTypeface(null, Typeface.BOLD);
                tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4));
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                container.addView(tv);
            }
        }

        // 若日志在转场结束后才填充，则逐行补一次滑入动画
        if (animateRows) {
            animateRowsIn(container);
        }
    }

    private void animateRowsIn(final LinearLayout container) {
        final int width = mPageTiles != null ? mPageTiles.getWidth() : 0;
        if (width <= 0) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 40);
            a.setInterpolator(new DecelerateInterpolator());
            row.startAnimation(a);
        }
    }

    private JSONArray fetchChangelog() {
        String[] urls = {
                cn.ottohub.oh2013.api.ApiConfig.UPDATE_URL
        };
        for (String urlStr : urls) {
            HttpURLConnection conn = null;
            try {
                conn = NetWorkUtil.openCompat(urlStr);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "OTTOhub");
                if (conn.getResponseCode() != 200) continue;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                JSONObject versions = json.optJSONObject("versions");
                if (versions == null) continue;
                JSONObject branch = versions.optJSONObject("0.4");
                if (branch == null) continue;
                JSONArray changelog = branch.optJSONArray("changelog");
                if (changelog != null && changelog.length() > 0) {
                    return changelog;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private static boolean contains(int[] arr, int val) {
        for (int v : arr) if (v == val) return true;
        return false;
    }

    private void clearChildAnimations(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).clearAnimation();
        }
    }

    private void slideToWelcome() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageWelcome.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        mPageWelcome.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_start).setEnabled(false);

        final ViewGroup welcomeGroup = (ViewGroup) mPageWelcome;
        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);

        // 磁贴行先向右滑出
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            row.startAnimation(a);
        }

        int rowBase = tileContainer.getChildCount() * 60 + 80;

        // 第2页标题、按钮滑出（分割线不动）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        int baseDelay = rowBase + tileIdx * 60 + 80;

        // 第1页各元素从左滑入（分割线不动）
        int[] inDurs = {450, 0, 350, 300};
        for (int i = 0; i < welcomeGroup.getChildCount() && i < inDurs.length; i++) {
            if (inDurs[i] == 0) continue;
            View child = welcomeGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(inDurs[i]);
            a.setStartOffset(baseDelay + i * 80);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == inDurs.length - 1 || (i == welcomeGroup.getChildCount() - 1)) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage2 = false;
                        mPageTiles.setVisibility(View.GONE);
                        findViewById(R.id.btn_next).setEnabled(true);
                        clearChildAnimations(welcomeGroup);
                        clearChildAnimations(tilesGroup);
                        for (int j = 0; j < tileContainer.getChildCount(); j++) {
                            tileContainer.getChildAt(j).clearAnimation();
                        }
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            child.startAnimation(a);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
