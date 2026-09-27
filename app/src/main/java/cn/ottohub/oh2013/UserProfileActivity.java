package cn.ottohub.oh2013;


import cn.ottohub.oh2013.util.NetWorkUtil;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.support.v4.util.LruCache;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cn.ottohub.oh2013.adapter.ViewPagerViewAdapter;
import cn.ottohub.oh2013.api.UserInfoApi;
import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.model.UserInfo;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.CoverImageLoader;
import cn.ottohub.oh2013.util.GlobalImageCache;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class UserProfileActivity extends BaseActivity {

    private static final int TAB_VIDEO = 0;
    private static final int TAB_BLOG = 1;

    private ImageView ivAvatar;
    private ImageView ivCover;
    private TextView tvUserNameTitle;
    private TextView tvUserSign;
    private TextView tvOid;
    private TextView tvFans;
    private TextView tvLevel;
    private TextView tvIntro;
    private android.widget.Button btnMedalWall;
    private android.widget.Button btnMoreInfo;
    private android.widget.Button btnFollow;
    private UserInfo currentUserInfo;
    private TextView tvFollowing;
    private TextView tabVideo;
    private TextView tabBlog;
    private View tabVideoUnderline;
    private View tabBlogUnderline;
    private View loadingLayout;
    private View contentLayout;
    private ViewPager viewPager;
    private ListView videoListView;
    private ListView blogListView;
    private ProgressBar videoProgressBar;
    private TextView videoEmptyView;
    private View videoFooterView;
    private ProgressBar videoFooterProgress;
    private TextView videoFooterText;
    private View blogFooterView;
    private ProgressBar blogFooterProgress;
    private TextView blogFooterText;

    private long mid;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean followBusy = false;
    private boolean mIsFollowing = false;
    private int currentTab = TAB_VIDEO;

    private ExecutorService executor;
    private LruCache<String, Bitmap> imageCache;
    private Map<Integer, Boolean> loadingMap = new java.util.HashMap<Integer, Boolean>();

    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private VideoListAdapter videoAdapter;
    private int currentPage = 1;
    private boolean isLoadingVideos = false;
    private boolean isVideoEnd = false;
    private boolean isLoadingMore = false;

    private List<BlogItem> blogList = new ArrayList<BlogItem>();
    private BlogListAdapter blogAdapter;
    private int blogPage = 1;
    private boolean isLoadingBlogs = false;
    private boolean isBlogEnd = false;
    private Set<Long> blogIdSet = new HashSet<Long>();

    private Set<Long> videoIdSet = new HashSet<Long>();

    private boolean isDestroyed = false;

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘（仅主线程访问）
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    private void setScrolling(boolean scrolling) {
        this.mScrolling = scrolling;
        if (!scrolling) {
            flushPendingBitmapSets();
        }
    }

    private void flushPendingBitmapSets() {
        if (pendingBitmapSets.isEmpty()) return;
        final java.util.ArrayList<Runnable> pending = new java.util.ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        // 分批应用（每帧最多 2 张），避免停下瞬间一次性 setImageBitmap 全部封面造成整帧卡顿
        final int[] idx = {0};
        final Runnable drain = new Runnable() {
            @Override
            public void run() {
                if (executor == null || executor.isShutdown()) {
                    return;
                }
                int applied = 0;
                while (idx[0] < pending.size() && applied < 2) {
                    try {
                        pending.get(idx[0]).run();
                    } catch (Throwable t) {
                    }
                    idx[0]++;
                    applied++;
                }
                if (idx[0] < pending.size()) {
                    mainHandler.postDelayed(this, 16);
                }
            }
        };
        drain.run();
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private void initCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        if (cacheSize < 1024) {
            cacheSize = 1024;
        }
        imageCache = new LruCache<String, Bitmap>(cacheSize);
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        // 统一?SdkHelper：优先用户?按?备内存给默?值，不写?
        int threadCount = cn.ottohub.oh2013.util.SdkHelper.getImageLoadThreads();
        executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        initRoundTitleBar();

        mid = getIntent().getLongExtra("mid", 0);

        if (mid == 0) {
            Toast.makeText(this, this.getString(R.string.userprofileactivity_toast_7528), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initCache();
        initExecutor();

        initViews();
        loadUserInfo();
        loadUserVideos();
    }

    private void initViews() {
        ivAvatar = (ImageView) findViewById(R.id.iv_avatar);
        ivCover = (ImageView) findViewById(R.id.iv_cover);
        tvUserNameTitle = (TextView) findViewById(R.id.tv_user_name_title);
        tvUserSign = (TextView) findViewById(R.id.tv_user_sign);
        tvOid = (TextView) findViewById(R.id.tv_oid);
        tvFans = (TextView) findViewById(R.id.tv_fans);
        tvLevel = (TextView) findViewById(R.id.tv_level);
        tvIntro = (TextView) findViewById(R.id.tv_intro);
        btnMedalWall = (android.widget.Button) findViewById(R.id.btn_medal_wall);
        if (btnMedalWall != null) {
            btnMedalWall.setVisibility(View.GONE);
        }
        btnMoreInfo = (android.widget.Button) findViewById(R.id.btn_more_info);
        if (btnMoreInfo != null) {
            btnMoreInfo.setVisibility(View.GONE);
        }
        btnFollow = (android.widget.Button) findViewById(R.id.btn_follow);
        if (btnFollow != null) {
            btnFollow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFollow();
                }
            });
        }
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openImageViewer(currentUserInfo != null ? currentUserInfo.avatar : null);
                }
            });
        }
        if (ivCover != null) {
            ivCover.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openImageViewer(currentUserInfo != null ? currentUserInfo.coverUrl : null);
                }
            });
        }
        tvFollowing = (TextView) findViewById(R.id.tv_following);
        tabVideo = (TextView) findViewById(R.id.tab_video);
        tabBlog = (TextView) findViewById(R.id.tab_blog);
        tabVideoUnderline = findViewById(R.id.tab_video_underline);
        tabBlogUnderline = findViewById(R.id.tab_blog_underline);
        if (tabVideo != null) {
            tabVideo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_VIDEO);
                }
            });
        }
        if (tabBlog != null) {
            tabBlog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_BLOG);
                }
            });
        }
        loadingLayout = findViewById(R.id.loading_layout);
        contentLayout = findViewById(R.id.content_layout);
        viewPager = (ViewPager) findViewById(R.id.view_pager);
        videoProgressBar = (ProgressBar) findViewById(R.id.video_progress);
        videoEmptyView = (TextView) findViewById(R.id.video_empty_view);

        videoListView = new ListView(this);
        videoListView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoListView.setDivider(null);
        videoListView.setDividerHeight(0);
        videoListView.setBackgroundColor(0xFFF5F5F5);
        videoListView.setSelector(android.R.color.transparent);
        videoListView.setCacheColorHint(0x00000000);

        blogListView = new ListView(this);
        blogListView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        blogListView.setDivider(null);
        blogListView.setDividerHeight(0);
        blogListView.setBackgroundColor(0xFFF5F5F5);
        blogListView.setSelector(android.R.color.transparent);
        blogListView.setCacheColorHint(0x00000000);

        videoFooterView = getLayoutInflater().inflate(R.layout.list_footer, null);
        videoFooterProgress = (ProgressBar) videoFooterView.findViewById(R.id.footer_progress);
        videoFooterText = (TextView) videoFooterView.findViewById(R.id.footer_text);
        if (videoFooterProgress != null) videoFooterProgress.setVisibility(View.GONE);
        if (videoFooterText != null) {
            videoFooterText.setText(getString(R.string.userprofileactivity_settext_563f));
        }
        videoFooterView.setVisibility(View.GONE);
        videoListView.addFooterView(videoFooterView);

        blogFooterView = getLayoutInflater().inflate(R.layout.list_footer, null);
        blogFooterProgress = (ProgressBar) blogFooterView.findViewById(R.id.footer_progress);
        blogFooterText = (TextView) blogFooterView.findViewById(R.id.footer_text);
        if (blogFooterProgress != null) blogFooterProgress.setVisibility(View.GONE);
        if (blogFooterText != null) {
            blogFooterText.setText(getString(R.string.userprofileactivity_settext_563f));
        }
        blogFooterView.setVisibility(View.GONE);
        blogListView.addFooterView(blogFooterView);

        videoAdapter = new VideoListAdapter();
        blogAdapter = new BlogListAdapter();
        videoListView.setAdapter(videoAdapter);
        blogListView.setAdapter(blogAdapter);

        List<View> pages = new ArrayList<View>();
        pages.add(videoListView);
        pages.add(blogListView);
        viewPager.setAdapter(new ViewPagerViewAdapter(pages));
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                switchTab(position, false);
            }
        });
        updateTabStyle();

        videoListView.setOnScrollListener(createScrollListener(TAB_VIDEO));
        blogListView.setOnScrollListener(createScrollListener(TAB_BLOG));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private AbsListView.OnScrollListener createScrollListener(final int tab) {
        return new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    setScrolling(false);
                    if (currentTab != tab) return;
                    if (!isLoadingMore && !isCurrentLoading() && !isCurrentEnd()) {
                        int lastVisible = view.getLastVisiblePosition();
                        int totalCount = getActiveCount();
                        if (lastVisible >= totalCount - 1 && totalCount > 0) {
                            isLoadingMore = true;
                            loadMoreCurrent();
                        }
                    }
                } else {
                    setScrolling(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (currentTab != tab) return;
                if (!isLoadingMore && !isCurrentLoading() && !isCurrentEnd() && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        isLoadingMore = true;
                        loadMoreCurrent();
                    }
                }
            }
        };
    }

    private View getActiveFooter() {
        return currentTab == TAB_BLOG ? blogFooterView : videoFooterView;
    }

    private ProgressBar getActiveFooterProgress() {
        return currentTab == TAB_BLOG ? blogFooterProgress : videoFooterProgress;
    }

    private TextView getActiveFooterText() {
        return currentTab == TAB_BLOG ? blogFooterText : videoFooterText;
    }

    private boolean isCurrentLoading() {
        return currentTab == TAB_BLOG ? isLoadingBlogs : isLoadingVideos;
    }

    private boolean isCurrentEnd() {
        return currentTab == TAB_BLOG ? isBlogEnd : isVideoEnd;
    }

    private int getActiveCount() {
        if (currentTab == TAB_BLOG) {
            return blogAdapter != null ? blogAdapter.getCount() : 0;
        }
        return videoAdapter != null ? videoAdapter.getCount() : 0;
    }

    private void loadMoreCurrent() {
        if (currentTab == TAB_BLOG) {
            loadMoreBlogs();
        } else {
            loadMoreVideos();
        }
    }

    private void switchTab(int tab) {
        switchTab(tab, true);
    }

    private void switchTab(int tab, boolean fromClick) {
        if (currentTab == tab && !fromClick) {
            updateTabStyle();
            updateEmptyForCurrentTab();
            return;
        }
        if (currentTab == tab && fromClick) {
            return;
        }
        currentTab = tab;
        isLoadingMore = false;
        if (fromClick && viewPager != null && viewPager.getCurrentItem() != tab) {
            viewPager.setCurrentItem(tab, true);
        }
        updateTabStyle();
        if (currentTab == TAB_VIDEO) {
            if (videoList.size() == 0 && !isLoadingVideos) {
                loadUserVideos();
            } else {
                if (videoAdapter != null) videoAdapter.notifyDataSetChanged();
                if (videoFooterView != null) {
                    videoFooterView.setVisibility(
                            isVideoEnd || videoList.size() == 0 ? View.GONE : View.VISIBLE);
                }
                updateEmptyForCurrentTab();
            }
        } else {
            if (blogList.size() == 0 && !isLoadingBlogs) {
                loadUserBlogs();
            } else {
                if (blogAdapter != null) blogAdapter.notifyDataSetChanged();
                if (blogFooterView != null) {
                    blogFooterView.setVisibility(
                            isBlogEnd || blogList.size() == 0 ? View.GONE : View.VISIBLE);
                }
                updateEmptyForCurrentTab();
            }
        }
    }

    private void updateTabStyle() {
        if (tabVideo != null) {
            if (currentTab == TAB_VIDEO) {
                tabVideo.setTextColor(0xFFFF8C00);
                tabVideo.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tabVideo.setTextColor(0xFF666666);
                tabVideo.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        if (tabBlog != null) {
            if (currentTab == TAB_BLOG) {
                tabBlog.setTextColor(0xFFFF8C00);
                tabBlog.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tabBlog.setTextColor(0xFF666666);
                tabBlog.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        if (tabVideoUnderline != null) {
            tabVideoUnderline.setBackgroundColor(currentTab == TAB_VIDEO ? 0xFFFF8C00 : 0x00000000);
        }
        if (tabBlogUnderline != null) {
            tabBlogUnderline.setBackgroundColor(currentTab == TAB_BLOG ? 0xFFFF8C00 : 0x00000000);
        }
    }

    private void updateEmptyForCurrentTab() {
        if (videoEmptyView == null) return;
        if (currentTab == TAB_VIDEO) {
            if (videoList.size() == 0 && !isLoadingVideos) {
                videoEmptyView.setText(getString(R.string.userprofileactivity_settext_8be5));
                videoEmptyView.setVisibility(View.VISIBLE);
            } else {
                videoEmptyView.setVisibility(View.GONE);
            }
        } else {
            if (blogList.size() == 0 && !isLoadingBlogs) {
                videoEmptyView.setText("暂无动态");
                videoEmptyView.setVisibility(View.VISIBLE);
            } else {
                videoEmptyView.setVisibility(View.GONE);
            }
        }
    }

    private void loadUserInfo() {
        if (isDestroyed) return;
        if (loadingLayout != null) loadingLayout.setVisibility(View.VISIBLE);
        if (contentLayout != null) contentLayout.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo userInfo = UserInfoApi.getUserInfo(mid);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
                            if (userInfo == null) {
                                Toast.makeText(UserProfileActivity.this,
                                        getString(R.string.userprofileactivity_toast_83b7),
                                        Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            displayUserInfo(userInfo);
                            if (contentLayout != null) contentLayout.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
                            Toast.makeText(UserProfileActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void displayUserInfo(UserInfo userInfo) {
        if (isDestroyed) return;
        currentUserInfo = userInfo;

        String displayName = userInfo.name != null && userInfo.name.length() > 0
                ? userInfo.name : ("用户 " + mid);

        if (tvUserNameTitle != null) {
            tvUserNameTitle.setText(displayName);
        }

        //
        if (tvUserSign != null) {
            tvUserSign.setText(displayName);
            tvUserSign.setVisibility(View.VISIBLE);
        }

        if (tvOid != null) {
            tvOid.setText("oid:" + mid);
            tvOid.setVisibility(View.VISIBLE);
        }

        //
        if (tvLevel != null) {
            String joinTime = (userInfo.registerTime != null && userInfo.registerTime.length() > 0)
                    ? userInfo.registerTime : "-";
            String levelName = UserInfoApi.levelNameFromExperience(userInfo.experience);
            tvLevel.setText("入站 " + joinTime + " · " + levelName);
            tvLevel.setVisibility(View.VISIBLE);
        }

        updateFansFollowingText(userInfo.fans, userInfo.following);

        if (tvIntro != null) {
            String intro = userInfo.sign != null ? userInfo.sign.trim() : "";
            if (intro.length() > 0) {
                tvIntro.setText(intro);
                tvIntro.setVisibility(View.VISIBLE);
            } else {
                tvIntro.setText("这个人很懒，?么都没写");
                tvIntro.setVisibility(View.VISIBLE);
            }
        }

        if (btnMedalWall != null) {
            btnMedalWall.setVisibility(View.GONE);
        }
        if (btnMoreInfo != null) {
            btnMoreInfo.setVisibility(View.GONE);
        }

        setupFollowButton(userInfo);

        if (userInfo.avatar != null && userInfo.avatar.length() > 0) {
            int avSize = (int) (getResources().getDisplayMetrics().density * 64);
            CoverImageLoader.loadIntoCircle(this, ivAvatar, userInfo.avatar, avSize);
        }

        if (ivCover != null) {
            if (userInfo.coverUrl != null && userInfo.coverUrl.length() > 0) {
                ivCover.setVisibility(View.VISIBLE);
                int coverW = getResources().getDisplayMetrics().widthPixels;
                int coverH = (int) (getResources().getDisplayMetrics().density * 120);
                CoverImageLoader.loadInto(this, ivCover, userInfo.coverUrl, coverW, coverH);
            } else {
                // 无封面时隐藏，title 下无橙线/阴影故无间隙
                ivCover.setVisibility(View.GONE);
            }
        }

        addAvatarBorder(ivAvatar);
    }

    private void updateFansFollowingText(int fans, int following) {
        if (tvFans != null) {
            tvFans.setText("粉丝 " + fans);
            tvFans.setVisibility(View.VISIBLE);
        }
        if (tvFollowing != null) {
            tvFollowing.setText("关注 " + following);
            tvFollowing.setVisibility(View.VISIBLE);
        }
    }

    private void setupFollowButton(UserInfo userInfo) {
        if (btnFollow == null) return;
        long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        View infoTexts = findViewById(R.id.user_info_texts);
        if (selfMid != 0 && selfMid == mid) {
            btnFollow.setVisibility(View.GONE);
            if (infoTexts != null) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) infoTexts.getLayoutParams();
                if (lp != null) {
                    lp.rightMargin = dpToPx(8);
                    infoTexts.setLayoutParams(lp);
                }
            }
            return;
        }
        btnFollow.setVisibility(View.VISIBLE);
        if (infoTexts != null) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) infoTexts.getLayoutParams();
            if (lp != null) {
                lp.rightMargin = dpToPx(84);
                infoTexts.setLayoutParams(lp);
            }
        }
        mIsFollowing = userInfo != null && userInfo.followed;
        updateFollowButtonText(mIsFollowing);
    }

    private void updateFollowButtonText(boolean followed) {
        mIsFollowing = followed;
        if (btnFollow == null || btnFollow.getVisibility() != View.VISIBLE) return;
        // follow_status: 1=已关注；暂无独立 mutual 字段
        btnFollow.setText(followed ? "已关注" : "关注");
        btnFollow.setTextColor(0xFFFFFFFF);
        btnFollow.setBackgroundColor(followed ? 0xFFBDBDBD : 0xFFFF8C00);
        btnFollow.invalidate();
    }

    private void toggleFollow() {
        if (followBusy || btnFollow == null) return;
        if (!cn.ottohub.oh2013.api.OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        followBusy = true;
        btnFollow.setEnabled(false);
        final boolean wasFollowed = mIsFollowing
                || (currentUserInfo != null && currentUserInfo.followed);
        // 乐观更新：立即翻转
        final boolean optimistic = !wasFollowed;
        updateFollowButtonText(optimistic);
        if (currentUserInfo != null) {
            currentUserInfo.followed = optimistic;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int status = UserInfoApi.toggleFollow(mid, wasFollowed, true);
                    final int newFans = UserInfoApi.lastNewFansCount;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            followBusy = false;
                            btnFollow.setEnabled(true);
                            if (status < 0) {
                                updateFollowButtonText(wasFollowed);
                                if (currentUserInfo != null) {
                                    currentUserInfo.followed = wasFollowed;
                                }
                                Toast.makeText(UserProfileActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            boolean followed = status == 1;
                            mIsFollowing = followed;
                            if (currentUserInfo != null) {
                                currentUserInfo.followed = followed;
                                if (newFans >= 0) {
                                    currentUserInfo.fans = newFans;
                                } else if (followed && !wasFollowed) {
                                    currentUserInfo.fans++;
                                } else if (!followed && wasFollowed && currentUserInfo.fans > 0) {
                                    currentUserInfo.fans--;
                                }
                                updateFansFollowingText(currentUserInfo.fans, currentUserInfo.following);
                            }
                            updateFollowButtonText(followed);
                            Toast.makeText(UserProfileActivity.this,
                                    followed ? "已关注" : "已取消关注", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            followBusy = false;
                            btnFollow.setEnabled(true);
                            updateFollowButtonText(wasFollowed);
                            if (currentUserInfo != null) {
                                currentUserInfo.followed = wasFollowed;
                            }
                            Toast.makeText(UserProfileActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void openImageViewer(String url) {
        if (url == null || url.length() == 0) return;
        ArrayList<String> list = new ArrayList<String>();
        list.add(url);
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putStringArrayListExtra("imageList", list);
        i.putExtra("index", 0);
        startActivity(i);
    }

    /**
     * 根据等级获取对应?drawable 资源
     */
    private int getLevelDrawable(int level, boolean isSeniorMember) {
        // ?会员优先
        if (isSeniorMember && level >= 6) {
            return R.drawable.level_h;
        }
        switch (level) {
            case 0: return R.drawable.level_0;
            case 1: return R.drawable.level_1;
            case 2: return R.drawable.level_2;
            case 3: return R.drawable.level_3;
            case 4: return R.drawable.level_4;
            case 5: return R.drawable.level_5;
            case 6: return R.drawable.level_6;
            default: return 0;
        }
    }

    private void addAvatarBorder(ImageView imageView) {
        // no-op: circular avatars via CoverImageLoader.loadIntoCircle
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void loadAvatar(String urlStr) {
        if (isDestroyed || executor == null || executor.isShutdown() || executor.isTerminated()) {
            return;
        }

        if (urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr.substring(8);
        }

        if (urlStr != null && urlStr.indexOf(".webp") > 0) {
            urlStr = urlStr.replace(".webp", ".jpg");
            Log.e("UserProfile", "webp ??jpg: " + urlStr);
        }

        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        final String finalUrl = urlStr;
        final ImageView avatarView = ivAvatar;
        avatarView.setTag(finalUrl);

        Bitmap cachedBitmap = GlobalImageCache.getInstance().get(finalUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            avatarView.setImageBitmap(cachedBitmap);
            addAvatarBorder(avatarView);
            return;
        }
        cachedBitmap = imageCache.get(finalUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            avatarView.setImageBitmap(cachedBitmap);
            addAvatarBorder(avatarView);
            return;
        }

        Log.e("UserProfile", "加载头像: " + finalUrl);

        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed || executor == null || executor.isShutdown()) {
                        return;
                    }
                    final Bitmap bitmap = downloadImage(finalUrl, true);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        // 写?全?缓存，后?面共?                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        imageCache.put(finalUrl, bitmap);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isDestroyed) return;
                                Object tag = avatarView.getTag();
                                if (tag != null && tag.equals(finalUrl)) {
                                    if (bitmap != null && !bitmap.isRecycled()) {
                                        avatarView.setImageBitmap(bitmap);
                                        addAvatarBorder(avatarView);
                                    } else {
                                        avatarView.setImageResource(R.drawable.bili_default_avatar);
                                    }
                                }
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            // 忽略
        }
    }

    private Bitmap downloadImage(String urlStr, boolean isAvatar) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", cn.ottohub.oh2013.api.ApiConfig.SITE_URL);
            conn.connect();

            is = conn.getInputStream();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            try { is.close(); } catch (Exception ignored) {}
            is = null;

            int outWidth = options.outWidth;
            int outHeight = options.outHeight;

            if (outWidth <= 0 || outHeight <= 0) {
                outWidth = 800;
                outHeight = 600;
            }

            // 按实际显示尺寸解码（封面 116x71dp / 头像 64dp），1:1 绘制无需?缩放滤镜
            float density = getResources().getDisplayMetrics().density;
            int targetWidth = isAvatar ? 64 : (int) (116 * density + 0.5f);
            int targetHeight = isAvatar ? 64 : (int) (71 * density + 0.5f);

            // 计算缩放比例，确保为 2 的幂（低版本 Android 要求?
            int scale = 1;
            while (outWidth / scale > targetWidth * 2 && outHeight / scale > targetHeight * 2 && scale < 16) {
                scale *= 2;
            }

            // 次?求，避免重?下载
            conn.disconnect();
            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", cn.ottohub.oh2013.api.ApiConfig.SITE_URL);
            conn.connect();
            is = conn.getInputStream();

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            // 如果预? bitmap ?，主动跳?
            int estWidth = outWidth / scale;
            int estHeight = outHeight / scale;
            int estBytes = estWidth * estHeight * 2;
            if (estBytes > 2 * 1024 * 1024) {
                Log.w("UserProfile", "图片过大，跳? " + estBytes + " bytes");
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);

            if (bitmap != null) {
                int bw = bitmap.getWidth();
                int bh = bitmap.getHeight();
                if (bw > targetWidth * 2 || bh > targetHeight * 2) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                    if (scaled != bitmap && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return scaled;
                }
            }

            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.e("UserProfile", "图片解码内存不足: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e("UserProfile", "下载图片失败: " + e.getMessage());
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void loadUserVideos() {
        videoList.clear();
        videoIdSet.clear();
        currentPage = 1;
        isVideoEnd = false;
        isLoadingMore = false;
        videoProgressBar.setVisibility(View.VISIBLE);
        getActiveFooter().setVisibility(View.GONE);

        Log.d("UserProfile", "?始加载?频列?mid=" + mid);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    Log.d("UserProfile", "调用 API: getUserVideos, page=1, mid=" + mid);
                    UserInfoApi.getUserVideos(mid, 1, "", items);
                    Log.d("UserProfile", "API 返回，?频数? " + (items == null ? "null" : items.size()));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (currentTab != TAB_VIDEO) {
                                if (!isLoadingBlogs && videoProgressBar != null) {
                                    videoProgressBar.setVisibility(View.GONE);
                                }
                                return;
                            }
                            videoProgressBar.setVisibility(View.GONE);
                            Log.d("UserProfile", "UI 线程更新，?频数?" + (items == null ? 0 : items.size()));

                            if (items == null || items.size() == 0) {
                                videoEmptyView.setText(getString(R.string.userprofileactivity_settext_8be5));
                                getActiveFooter().setVisibility(View.GONE);
                                Log.d("UserProfile", "视?列表为空，显示空视图");
                                return;
                            }

                            int added = 0;
                            for (VideoCard item : items) {
                                if (item.aid != 0 && !videoIdSet.contains(item.aid)) {
                                    videoIdSet.add(item.aid);
                                    videoList.add(item);
                                    added++;
                                    Log.d("UserProfile", "添加视?: aid=" + item.aid + ", title=" + item.title);
                                }
                            }
                            Log.d("UserProfile", "新?视??" + added + ", 当前总数=" + videoList.size());

                            if (added > 0) {
                                videoAdapter.notifyDataSetChanged();
                                currentPage = 2;
                                if (!isVideoEnd) {
                                    getActiveFooter().setVisibility(View.VISIBLE);
                                    if (getActiveFooterProgress() != null) {
                                        getActiveFooterProgress().setVisibility(View.GONE);
                                    }
                                    if (getActiveFooterText() != null) {
                                        getActiveFooterText().setText(getString(R.string.userprofileactivity_settext_563f));
                                        getActiveFooterText().setVisibility(View.VISIBLE);
                                    }
                                }
                                Log.d("UserProfile", "视?列表更新成功，当前页=" + currentPage);
                            } else {
                                videoEmptyView.setText(getString(R.string.userprofileactivity_settext_6682));
                                getActiveFooter().setVisibility(View.GONE);
                                Log.d("UserProfile", "\u65e0\u53ef\u6dfb\u52a0\u7684\u89c6\u9891");
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("UserProfile", "加载视频列表异常: " + e.getMessage(), e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            videoProgressBar.setVisibility(View.GONE);
                            videoEmptyView.setText("加载失败: " + e.getMessage());
                            getActiveFooter().setVisibility(View.GONE);
                            Log.e("UserProfile", "UI 显示错?: " + e.getMessage());
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showLoadEndTip() {
        if (getActiveFooter() == null) return;
        getActiveFooter().setVisibility(View.VISIBLE);
        if (getActiveFooterProgress() != null) {
            getActiveFooterProgress().setVisibility(View.GONE);
        }
        if (getActiveFooterText() != null) {
            getActiveFooterText().setText(getString(R.string.emoticon__no_more_data));
            getActiveFooterText().setVisibility(View.VISIBLE);
        }
    }

    private void loadMoreVideos() {
        if (isLoadingVideos || isVideoEnd || isDestroyed) {
            isLoadingMore = false;
            return;
        }
        isLoadingVideos = true;

        if (getActiveFooterProgress() != null) {
            getActiveFooterProgress().setVisibility(View.VISIBLE);
        }
        if (getActiveFooterText() != null) {
            getActiveFooterText().setText(getString(R.string.userprofileactivity_settext_563f));
            getActiveFooterText().setVisibility(View.VISIBLE);
        }
        getActiveFooter().setVisibility(View.VISIBLE);

        final int page = currentPage;
        Log.d("UserProfile", "loadMoreVideos page=" + page);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    final int result = UserInfoApi.getUserVideos(mid, page, "", items);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (currentTab != TAB_VIDEO) {
                                isLoadingVideos = false;
                                isLoadingMore = false;
                                return;
                            }
                            if (getActiveFooterProgress() != null) {
                                getActiveFooterProgress().setVisibility(View.GONE);
                            }
                            isLoadingVideos = false;
                            isLoadingMore = false;

                            if (items == null || items.size() == 0 || result == 1) {
                                isVideoEnd = true;
                                showLoadEndTip();
                                return;
                            }

                            int added = 0;
                            for (VideoCard item : items) {
                                if (item.aid != 0 && !videoIdSet.contains(item.aid)) {
                                    videoIdSet.add(item.aid);
                                    videoList.add(item);
                                    added++;
                                }
                            }

                            if (added > 0) {
                                videoAdapter.notifyDataSetChanged();
                                currentPage = page + 1;
                                getActiveFooter().setVisibility(View.VISIBLE);
                                if (getActiveFooterProgress() != null) {
                                    getActiveFooterProgress().setVisibility(View.GONE);
                                }
                                if (getActiveFooterText() != null) {
                                    getActiveFooterText().setVisibility(View.GONE);
                                }
                            } else {
                                if (items.size() > 0 && !isVideoEnd) {
                                    currentPage = page + 1;
                                    loadMoreVideos();
                                } else {
                                    isVideoEnd = true;
                                    showLoadEndTip();
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (getActiveFooterProgress() != null) {
                                getActiveFooterProgress().setVisibility(View.GONE);
                            }
                            isLoadingVideos = false;
                            isLoadingMore = false;
                            getActiveFooter().setVisibility(View.GONE);
                            Toast.makeText(UserProfileActivity.this,
                                    "加载更多失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadUserBlogs() {
        blogList.clear();
        blogIdSet.clear();
        blogPage = 1;
        isBlogEnd = false;
        isLoadingMore = false;
        isLoadingBlogs = true;
        if (videoProgressBar != null) {
            videoProgressBar.setVisibility(View.VISIBLE);
        }
        if (getActiveFooter() != null) {
            getActiveFooter().setVisibility(View.GONE);
        }
        if (videoEmptyView != null) {
            videoEmptyView.setText("暂无动态");
            videoEmptyView.setVisibility(View.GONE);
        }
        if (blogAdapter != null) {
            blogAdapter.notifyDataSetChanged();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<BlogItem> items = new ArrayList<BlogItem>();
                    final int result = UserInfoApi.getUserBlogs(mid, 1, items);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            isLoadingBlogs = false;
                            if (currentTab != TAB_BLOG) return;
                            if (videoProgressBar != null) {
                                videoProgressBar.setVisibility(View.GONE);
                            }

                            if (items == null || items.size() == 0 || result < 0) {
                                if (videoEmptyView != null) {
                                    videoEmptyView.setText(result < 0 ? "加载失败" : "暂无动态");
                                    videoEmptyView.setVisibility(View.VISIBLE);
                                }
                                if (getActiveFooter() != null) {
                                    getActiveFooter().setVisibility(View.GONE);
                                }
                                isBlogEnd = true;
                                return;
                            }

                            for (BlogItem item : items) {
                                if (item.bid != 0 && !blogIdSet.contains(item.bid)) {
                                    blogIdSet.add(item.bid);
                                    blogList.add(item);
                                }
                            }
                            if (blogAdapter != null) {
                                blogAdapter.notifyDataSetChanged();
                            }
                            if (videoEmptyView != null) {
                                videoEmptyView.setVisibility(View.GONE);
                            }
                            if (result == 1) {
                                isBlogEnd = true;
                                if (getActiveFooter() != null) {
                                    getActiveFooter().setVisibility(View.GONE);
                                }
                            } else {
                                blogPage = 2;
                                if (getActiveFooter() != null) {
                                    getActiveFooter().setVisibility(View.VISIBLE);
                                }
                                if (getActiveFooterProgress() != null) {
                                    getActiveFooterProgress().setVisibility(View.GONE);
                                }
                                if (getActiveFooterText() != null) {
                                    getActiveFooterText().setText(
                                            getString(R.string.userprofileactivity_settext_563f));
                                    getActiveFooterText().setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            isLoadingBlogs = false;
                            if (currentTab != TAB_BLOG) return;
                            if (videoProgressBar != null) {
                                videoProgressBar.setVisibility(View.GONE);
                            }
                            if (videoEmptyView != null) {
                                videoEmptyView.setText("加载失败: " + e.getMessage());
                                videoEmptyView.setVisibility(View.VISIBLE);
                            }
                            if (getActiveFooter() != null) {
                                getActiveFooter().setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreBlogs() {
        if (isLoadingBlogs || isBlogEnd || isDestroyed) {
            isLoadingMore = false;
            return;
        }
        isLoadingBlogs = true;

        if (getActiveFooterProgress() != null) {
            getActiveFooterProgress().setVisibility(View.VISIBLE);
        }
        if (getActiveFooterText() != null) {
            getActiveFooterText().setText(getString(R.string.userprofileactivity_settext_563f));
            getActiveFooterText().setVisibility(View.VISIBLE);
        }
        getActiveFooter().setVisibility(View.VISIBLE);

        final int page = blogPage;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<BlogItem> items = new ArrayList<BlogItem>();
                    final int result = UserInfoApi.getUserBlogs(mid, page, items);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (currentTab != TAB_BLOG) {
                                isLoadingBlogs = false;
                                isLoadingMore = false;
                                return;
                            }
                            if (getActiveFooterProgress() != null) {
                                getActiveFooterProgress().setVisibility(View.GONE);
                            }
                            isLoadingBlogs = false;
                            isLoadingMore = false;

                            if (items == null || items.size() == 0 || result == 1) {
                                int added = 0;
                                if (items != null) {
                                    for (BlogItem item : items) {
                                        if (item.bid != 0 && !blogIdSet.contains(item.bid)) {
                                            blogIdSet.add(item.bid);
                                            blogList.add(item);
                                            added++;
                                        }
                                    }
                                }
                                if (added > 0 && blogAdapter != null) {
                                    blogAdapter.notifyDataSetChanged();
                                }
                                isBlogEnd = true;
                                showLoadEndTip();
                                return;
                            }

                            int added = 0;
                            for (BlogItem item : items) {
                                if (item.bid != 0 && !blogIdSet.contains(item.bid)) {
                                    blogIdSet.add(item.bid);
                                    blogList.add(item);
                                    added++;
                                }
                            }
                            if (added > 0) {
                                if (blogAdapter != null) {
                                    blogAdapter.notifyDataSetChanged();
                                }
                                blogPage = page + 1;
                                getActiveFooter().setVisibility(View.VISIBLE);
                                if (getActiveFooterText() != null) {
                                    getActiveFooterText().setVisibility(View.GONE);
                                }
                            } else {
                                isBlogEnd = true;
                                showLoadEndTip();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (getActiveFooterProgress() != null) {
                                getActiveFooterProgress().setVisibility(View.GONE);
                            }
                            isLoadingBlogs = false;
                            isLoadingMore = false;
                            getActiveFooter().setVisibility(View.GONE);
                            Toast.makeText(UserProfileActivity.this,
                                    "加载更多失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
        if (imageCache != null) {
            imageCache.evictAll();
        }
        loadingMap.clear();
        videoIdSet.clear();
        blogIdSet.clear();
        if (videoList != null) {
            videoList.clear();
        }
        if (blogList != null) {
            blogList.clear();
        }
    }

    // VideoListAdapter

    class VideoListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return videoList.size();
        }

        @Override
        public Object getItem(int position) {
            return videoList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_user_video, parent, false);
                holder = new ViewHolder();
                holder.cover = (ImageView) convertView.findViewById(R.id.cover);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                holder.view = (TextView) convertView.findViewById(R.id.view);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            VideoCard item = videoList.get(position);
            holder.title.setText(item.title);
            holder.view.setText(item.view);

            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            int coverW = (int) (getResources().getDisplayMetrics().density * 120);
            int coverH = (int) (getResources().getDisplayMetrics().density * 75);
            cn.ottohub.oh2013.util.CoverImageLoader.loadInto(
                    UserProfileActivity.this, holder.cover, item.cover, coverW, coverH);

            final VideoCard clickItem = item;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickItem == null) return;
                    Intent intent = new Intent(UserProfileActivity.this, VideoDetailActivity.class);
                    if (clickItem.aid != 0) {
                        intent.putExtra("aid", clickItem.aid);
                    } else if (clickItem.bvid != null && clickItem.bvid.length() > 0) {
                        intent.putExtra("bvid", clickItem.bvid);
                    } else {
                        Toast.makeText(UserProfileActivity.this, UserProfileActivity.this.getString(R.string.userprofileactivity_toast_65e0), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView view;
    }

    class BlogListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return blogList.size();
        }

        @Override
        public Object getItem(int position) {
            return blogList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return blogList.get(position).bid;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(UserProfileActivity.this)
                        .inflate(R.layout.item_blog, parent, false);
            }
            final BlogItem item = blogList.get(position);
            TextView title = (TextView) row.findViewById(R.id.blog_title);
            TextView meta = (TextView) row.findViewById(R.id.blog_meta);
            ImageView avatar = (ImageView) row.findViewById(R.id.blog_avatar);

            title.setText(item.title != null && item.title.length() > 0 ? item.title : ("ob" + item.bid));
            meta.setText((item.username != null ? item.username : "")
                    + " · " + (item.time != null ? item.time : ""));

            // 用户空间动态列表不显示头像
            if (avatar != null) {
                avatar.setVisibility(View.GONE);
            }
            View metaParent = title.getParent() instanceof View ? (View) title.getParent() : null;
            if (metaParent != null && metaParent.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp =
                        (ViewGroup.MarginLayoutParams) metaParent.getLayoutParams();
                mlp.leftMargin = 0;
                metaParent.setLayoutParams(mlp);
            }

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item == null) return;
                    Intent intent = new Intent(UserProfileActivity.this, BlogDetailActivity.class);
                    intent.putExtra("bid", item.bid);
                    intent.putExtra("title", item.title);
                    intent.putExtra("content", item.content);
                    intent.putExtra("username", item.username);
                    intent.putExtra("avatar_url", item.avatarUrl);
                    intent.putExtra("uid", item.uid);
                    intent.putExtra("time", item.time);
                    intent.putExtra("is_gore", item.isGore);
                    startActivity(intent);
                }
            });
            return row;
        }
    }
}
