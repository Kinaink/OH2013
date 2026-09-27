package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.adapter.ViewPagerViewAdapter;
import cn.ottohub.oh2013.api.BlogApi;
import cn.ottohub.oh2013.api.FavoriteApi;
import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.CoverImageLoader;
import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.DialogUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class FavoriteVideoListActivity extends BaseActivity {

    private static final int TAB_VIDEO = 0;
    private static final int TAB_BLOG = 1;

    private ViewPager viewPager;
    private ListView videoListView;
    private ListView blogListView;
    private TextView emptyView;
    private TextView titleText;
    private TextView tabVideo;
    private TextView tabBlog;
    private View videoFooterView;
    private View blogFooterView;
    private android.widget.ProgressBar videoFooterProgress;
    private android.widget.ProgressBar blogFooterProgress;

    private FavoriteVideoAdapter videoAdapter;
    private FavoriteBlogAdapter blogAdapter;
    private ArrayList<VideoCard> videoList = new ArrayList<VideoCard>();
    private ArrayList<BlogItem> blogList = new ArrayList<BlogItem>();

    private int currentTab = TAB_VIDEO;
    private long fid;
    private String folderName;
    private int videoPage = 1;
    private boolean videoEnd = false;
    private int blogPage = 1;
    private boolean blogEnd = false;
    private boolean videoLoading = false;
    private boolean blogLoading = false;

    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 用户是否已主动滚动过（首次加载后不自动触发加载更多，防止不足一屏时立即翻页触发风控�?
    private boolean mVideoUserScrolled = false;
    private boolean mBlogUserScrolled = false;

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_video_list);
        initRoundTitleBar();

        Intent intent = getIntent();
        fid = intent.getLongExtra("fid", 0L);
        folderName = intent.getStringExtra("name");

        titleText = (TextView) findViewById(R.id.title_text);
        if (folderName != null) {
            titleText.setText(folderName);
        }

        tabVideo = (TextView) findViewById(R.id.tab_video);
        tabBlog = (TextView) findViewById(R.id.tab_blog);
        if (tabVideo != null) {
            tabVideo.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    switchTab(TAB_VIDEO, true);
                }
            });
        }
        if (tabBlog != null) {
            tabBlog.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    switchTab(TAB_BLOG, true);
                }
            });
        }

        emptyView = (TextView) findViewById(R.id.empty_view);
        viewPager = (ViewPager) findViewById(R.id.view_pager);

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
        videoFooterProgress = (android.widget.ProgressBar) videoFooterView.findViewById(R.id.footer_progress);
        videoListView.addFooterView(videoFooterView);
        videoFooterView.setVisibility(View.GONE);

        blogFooterView = getLayoutInflater().inflate(R.layout.list_footer, null);
        blogFooterProgress = (android.widget.ProgressBar) blogFooterView.findViewById(R.id.footer_progress);
        blogListView.addFooterView(blogFooterView);
        blogFooterView.setVisibility(View.GONE);

        videoAdapter = new FavoriteVideoAdapter(this, videoList);
        // 不再提供长按删除/冷藏夹弹窗
        blogAdapter = new FavoriteBlogAdapter();
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

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        videoListView.setOnScrollListener(createScrollListener(TAB_VIDEO));
        blogListView.setOnScrollListener(createScrollListener(TAB_BLOG));

        updateTabStyle();
        loadVideos();
    }

    private AbsListView.OnScrollListener createScrollListener(final int tab) {
        return new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    if (tab == TAB_VIDEO && videoAdapter != null) {
                        videoAdapter.setScrolling(false);
                    }
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = tab == TAB_BLOG ? blogList.size() : videoList.size();
                    // 接近底部即加载更多（触屏滑动或惯性停住都触发）
                    if (lastVisible >= totalCount - 2
                            && !isTabLoading(tab) && !isTabEnd(tab) && totalCount > 0) {
                        if (tab == TAB_BLOG) {
                            mBlogUserScrolled = true;
                        } else {
                            mVideoUserScrolled = true;
                        }
                        loadMoreForTab(tab);
                    }
                } else {
                    if (tab == TAB_BLOG) {
                        mBlogUserScrolled = true;
                    } else {
                        mVideoUserScrolled = true;
                        if (videoAdapter != null) {
                            videoAdapter.setScrolling(true);
                            videoAdapter.setHideHighlight(true);
                        }
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isTabLoading(tab) && !isTabEnd(tab) && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        if (tab == TAB_BLOG) {
                            mBlogUserScrolled = true;
                        } else {
                            mVideoUserScrolled = true;
                        }
                        loadMoreForTab(tab);
                    }
                }
            }
        };
    }

    private boolean isTabLoading(int tab) {
        return tab == TAB_BLOG ? blogLoading : videoLoading;
    }

    private boolean isTabEnd(int tab) {
        return tab == TAB_BLOG ? blogEnd : videoEnd;
    }

    private void loadMoreForTab(int tab) {
        if (tab == TAB_BLOG) {
            loadMoreBlogs();
        } else {
            loadMoreVideos();
        }
    }

    private ListView getActiveListView() {
        return currentTab == TAB_BLOG ? blogListView : videoListView;
    }

    private int getActiveCount() {
        return currentTab == TAB_BLOG ? blogList.size() : videoList.size();
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
        selectedPosition = -1;
        if (fromClick && viewPager != null && viewPager.getCurrentItem() != tab) {
            viewPager.setCurrentItem(tab, true);
        }
        updateTabStyle();
        if (currentTab == TAB_VIDEO) {
            if (videoList.size() == 0 && !videoLoading) {
                loadVideos();
            } else {
                updateEmptyForCurrentTab();
            }
        } else {
            if (blogList.size() == 0 && !blogLoading) {
                loadBlogs();
            } else {
                updateEmptyForCurrentTab();
            }
        }
    }

    private void updateEmptyForCurrentTab() {
        if (viewPager != null) {
            viewPager.setVisibility(View.VISIBLE);
        }
        if (currentTab == TAB_VIDEO) {
            if (videoList.size() == 0) {
                if (!videoLoading) {
                    emptyView.setText(getString(R.string.favoritevideolistactivity_settext_6682));
                    emptyView.setVisibility(View.VISIBLE);
                    videoFooterView.setVisibility(View.GONE);
                }
            } else {
                emptyView.setVisibility(View.GONE);
                videoFooterView.setVisibility(videoEnd ? View.GONE : View.VISIBLE);
            }
        } else {
            if (blogList.size() == 0) {
                if (!blogLoading) {
                    emptyView.setText("暂无收藏动态");
                    emptyView.setVisibility(View.VISIBLE);
                    blogFooterView.setVisibility(View.GONE);
                }
            } else {
                emptyView.setVisibility(View.GONE);
                blogFooterView.setVisibility(blogEnd ? View.GONE : View.VISIBLE);
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
    }

    public void onVideoClick(VideoCard video, int position) {
        if (video == null) return;
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra("aid", video.aid);
        intent.putExtra("bvid", video.bvid);
        startActivity(intent);
    }

    private void onBlogClick(BlogItem item) {
        if (item == null) return;
        Intent intent = new Intent(this, BlogDetailActivity.class);
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

    // ===== 遥控器按键导航（模仿 RelatedVideosFragment�?=====
    private int selectedPosition = -1;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int count = getActiveCount();
        ListView listView = getActiveListView();
        if (count == 0 || listView == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = cn.ottohub.oh2013.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_UP
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_DOWN
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_CONFIRM
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_NUM_2
                && action != cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_NUM_8) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        if (currentTab == TAB_VIDEO && videoAdapter != null) {
            videoAdapter.setHideHighlight(false);
        }
        if (event.getRepeatCount() == 0) {
            if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
            } else if (action == cn.ottohub.oh2013.util.KeyBindingUtil.ACTION_CONFIRM) {
                if (currentTab == TAB_VIDEO) {
                    VideoCard item = videoList.get(selectedPosition);
                    if (item != null) {
                        onVideoClick(item, selectedPosition);
                    }
                } else {
                    BlogItem item = blogList.get(selectedPosition);
                    if (item != null) {
                        onBlogClick(item);
                    }
                }
                return true;
            }
            applySelection();
        }
        return true;
    }

    private int pageMove(int direction) {
        ListView listView = getActiveListView();
        if (listView == null) {
            return selectedPosition;
        }
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        int count = getActiveCount();
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    private void applySelection() {
        if (currentTab == TAB_VIDEO && videoAdapter != null) {
            videoAdapter.setSelectedPosition(selectedPosition);
        }
        ListView listView = getActiveListView();
        if (listView != null) {
            listView.setSelection(selectedPosition);
        }
    }

    private void showDeleteConfirm(final int position) {
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle(getString(R.string.favoritevideolistactivity_settitle_63d0))
                .setMessage(getString(R.string.favoritevideolistactivity_setmessage_786e))
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteVideo(position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoading() {
        View headerContainer = findViewById(R.id.header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
            headerContainer.requestLayout();
            headerContainer.invalidate();
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (currentTab == TAB_VIDEO && videoFooterView != null) {
            videoFooterView.setVisibility(View.GONE);
        }
        if (currentTab == TAB_BLOG && blogFooterView != null) {
            blogFooterView.setVisibility(View.GONE);
        }
        if (viewPager != null) {
            viewPager.setVisibility(View.VISIBLE);
        }
    }

    private void hideAllLoading() {
        View headerContainer = findViewById(R.id.header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private void showNoNetwork() {
        hideAllLoading();
        emptyView.setText(getString(R.string.emoticon__no_network));
        emptyView.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.VISIBLE);
    }

    private void showLoadError() {
        hideAllLoading();
        emptyView.setText(getString(R.string.emoticon__failed_need_retry));
        emptyView.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.VISIBLE);
    }

    private void deleteVideo(final int position) {
        if (fid == 0) {
            Toast.makeText(this, this.getString(R.string.favoritevideolistactivity_toast_6536), Toast.LENGTH_SHORT).show();
            return;
        }

        final VideoCard video = videoList.get(position);
        if (video == null) {
            Toast.makeText(this, this.getString(R.string.favoritevideolistactivity_toast_89c6), Toast.LENGTH_SHORT).show();
            return;
        }

        NetWorkUtil.refreshHeaders();

        String cookies = SharedPreferencesUtil.getString("cookies", "");
        String savedCsrf = SharedPreferencesUtil.getString("csrf", "");
        Log.e("FavoriteVideo", "===== 删除调试 =====");
        Log.e("FavoriteVideo", "Cookie: " + (cookies == null ? "null" : cookies));
        Log.e("FavoriteVideo", "保存�?csrf: " + savedCsrf);
        Log.e("FavoriteVideo", "aid: " + video.aid + ", fid: " + fid);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.deleteFavorite(video.aid, null, fid);
                    Log.e("FavoriteVideo", "删除结果: " + result);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (result == 0) {
                                Toast.makeText(FavoriteVideoListActivity.this, FavoriteVideoListActivity.this.getString(R.string.favoritevideolistactivity_toast_5220), Toast.LENGTH_SHORT).show();
                                videoList.remove(position);
                                videoAdapter.notifyDataSetChanged();

                                Intent broadcastIntent = new Intent();
                                broadcastIntent.setAction("cn.ottohub.oh2013.FAVORITE_CHANGED");
                                sendBroadcast(broadcastIntent);

                                if (videoList.size() == 0) {
                                    emptyView.setText(getString(R.string.favoritevideolistactivity_settext_6682));
                                    emptyView.setVisibility(View.VISIBLE);
                                    videoFooterView.setVisibility(View.GONE);
                                    if (currentTab == TAB_VIDEO) {
                                        viewPager.setVisibility(View.VISIBLE);
                                    }
                                    setResult(RESULT_OK);
                                } else {
                                    setResult(RESULT_OK);
                                }
                            } else if (result == -401) {
                                Toast.makeText(FavoriteVideoListActivity.this, FavoriteVideoListActivity.this.getString(R.string.favoritevideolistactivity_toast_767b), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(FavoriteVideoListActivity.this, "删除失败，错误码: " + result, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("FavoriteVideo", "删除异常: ", e);
                    mainHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(FavoriteVideoListActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadVideos() {
        retryCount = 0;
        doLoadVideos();
    }

    private void doLoadVideos() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            emptyView.setText(getString(R.string.favoritevideolistactivity_settext_8bf7));
            emptyView.setVisibility(View.VISIBLE);
            videoFooterView.setVisibility(View.GONE);
            if (currentTab == TAB_VIDEO) {
                viewPager.setVisibility(View.VISIBLE);
            }
            return;
        }

        NetWorkUtil.refreshHeaders();

        if (!isNetworkAvailable()) {
            if (currentTab == TAB_VIDEO) {
                showNoNetwork();
            }
            return;
        }

        videoLoading = true;
        if (currentTab == TAB_VIDEO) {
            showLoading();
            emptyView.setVisibility(View.GONE);
        }
        videoFooterView.setVisibility(View.GONE);
        if (videoFooterProgress != null) {
            videoFooterProgress.setVisibility(View.GONE);
        }
        videoPage = 1;
        videoEnd = false;
        videoList.clear();

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = FavoriteApi.getFolderVideos(mid, fid, videoPage, videoList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            videoLoading = false;
                            if (currentTab == TAB_VIDEO) {
                                hideAllLoading();
                            }
                            if (videoFooterProgress != null) {
                                videoFooterProgress.setVisibility(View.GONE);
                            }

                            if (result == -1) {
                                if (currentTab == TAB_VIDEO) {
                                    showLoadError();
                                }
                                return;
                            }

                            if (videoList.size() == 0) {
                                videoEnd = true;
                                videoFooterView.setVisibility(View.GONE);
                                if (currentTab == TAB_VIDEO) {
                                    emptyView.setText(getString(R.string.favoritevideolistactivity_settext_6682));
                                    emptyView.setVisibility(View.VISIBLE);
                                    viewPager.setVisibility(View.VISIBLE);
                                }
                            } else {
                                videoAdapter.notifyDataSetChanged();
                                retryCount = 0;
                                mVideoUserScrolled = false;
                                if (currentTab == TAB_VIDEO) {
                                    viewPager.setVisibility(View.VISIBLE);
                                    emptyView.setVisibility(View.GONE);
                                    videoListView.setSelection(0);
                                }

                                // 收藏视频列表不做下滑加载更多
                                videoEnd = true;
                                videoFooterView.setVisibility(View.GONE);
                                if (result != 1) {
                                    videoPage++;
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            videoLoading = false;
                            if (currentTab == TAB_VIDEO) {
                                hideAllLoading();
                            }
                            videoFooterView.setVisibility(View.GONE);
                            if (retryCount < MAX_RETRY && isNetworkAvailable()) {
                                retryCount++;
                                doLoadVideos();
                            } else if (currentTab == TAB_VIDEO) {
                                showLoadError();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadBlogs() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (mid == 0L) {
            emptyView.setText(getString(R.string.favoritevideolistactivity_settext_8bf7));
            emptyView.setVisibility(View.VISIBLE);
            blogFooterView.setVisibility(View.GONE);
            if (currentTab == TAB_BLOG) {
                viewPager.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (!isNetworkAvailable()) {
            if (currentTab == TAB_BLOG) {
                showNoNetwork();
            }
            return;
        }

        blogLoading = true;
        if (currentTab == TAB_BLOG) {
            showLoading();
            emptyView.setVisibility(View.GONE);
        }
        blogFooterView.setVisibility(View.GONE);
        if (blogFooterProgress != null) {
            blogFooterProgress.setVisibility(View.GONE);
        }
        blogPage = 1;
        blogEnd = false;
        blogList.clear();
        if (blogAdapter != null) {
            blogAdapter.notifyDataSetChanged();
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = BlogApi.fetchFavoriteBlogs(blogPage, blogList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            blogLoading = false;
                            if (currentTab == TAB_BLOG) {
                                hideAllLoading();
                            }
                            if (blogFooterProgress != null) {
                                blogFooterProgress.setVisibility(View.GONE);
                            }

                            if (result == -1) {
                                if (currentTab == TAB_BLOG) {
                                    showLoadError();
                                }
                                return;
                            }

                            if (blogList.size() == 0) {
                                blogEnd = true;
                                blogFooterView.setVisibility(View.GONE);
                                if (currentTab == TAB_BLOG) {
                                    emptyView.setText("暂无收藏动态");
                                    emptyView.setVisibility(View.VISIBLE);
                                    viewPager.setVisibility(View.VISIBLE);
                                }
                            } else {
                                blogAdapter.notifyDataSetChanged();
                                mBlogUserScrolled = false;
                                if (currentTab == TAB_BLOG) {
                                    viewPager.setVisibility(View.VISIBLE);
                                    emptyView.setVisibility(View.GONE);
                                    blogListView.setSelection(0);
                                }

                                if (result == 1) {
                                    blogEnd = true;
                                    blogFooterView.setVisibility(View.GONE);
                                } else {
                                    blogFooterView.setVisibility(View.VISIBLE);
                                    blogPage++;
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            blogLoading = false;
                            if (currentTab == TAB_BLOG) {
                                hideAllLoading();
                                showLoadError();
                            }
                            blogFooterView.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMoreVideos() {
        // 收藏视频列表已关闭下滑加载更多
        videoEnd = true;
        if (videoFooterView != null) {
            videoFooterView.setVisibility(View.GONE);
        }
        if (videoFooterProgress != null) {
            videoFooterProgress.setVisibility(View.GONE);
        }
    }

    private void loadMoreBlogs() {
        if (blogLoading || blogEnd) return;
        if (blogList.size() == 0) return;

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.emoticon__no_network), Toast.LENGTH_SHORT).show();
            return;
        }

        blogLoading = true;
        if (blogFooterProgress != null) {
            blogFooterProgress.setVisibility(View.VISIBLE);
        }
        blogFooterView.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final int result = BlogApi.fetchFavoriteBlogsByOffset(blogList.size(), blogList);

                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (blogFooterProgress != null) {
                                blogFooterProgress.setVisibility(View.GONE);
                            }
                            blogLoading = false;

                            if (result == 1) {
                                blogEnd = true;
                                blogFooterView.setVisibility(View.GONE);
                                if (blogList.size() > 0) {
                                    Toast.makeText(FavoriteVideoListActivity.this, getString(R.string.emoticon__no_more_data), Toast.LENGTH_SHORT).show();
                                }
                            } else if (result == 0) {
                                blogAdapter.notifyDataSetChanged();
                                blogPage++;
                                blogFooterView.setVisibility(View.VISIBLE);
                            } else {
                                // 业务失败也勿反复骚扰；当作暂无可加载
                                blogFooterView.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (blogFooterProgress != null) {
                                blogFooterProgress.setVisibility(View.GONE);
                            }
                            blogLoading = false;
                            blogFooterView.setVisibility(View.GONE);
                            blogEnd = true;
                        }
                    });
                }
            }
        }).start();
    }

    private class FavoriteBlogAdapter extends BaseAdapter {
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
                row = LayoutInflater.from(FavoriteVideoListActivity.this)
                        .inflate(R.layout.item_blog, parent, false);
            }
            final BlogItem item = blogList.get(position);
            TextView title = (TextView) row.findViewById(R.id.blog_title);
            TextView meta = (TextView) row.findViewById(R.id.blog_meta);
            ImageView avatar = (ImageView) row.findViewById(R.id.blog_avatar);

            title.setText(item.title != null && item.title.length() > 0 ? item.title : ("ob" + item.bid));
            meta.setText((item.username != null ? item.username : "") + " · " + (item.time != null ? item.time : ""));

            // 收藏动态列表不显示头像
            if (avatar != null) {
                avatar.setVisibility(View.GONE);
            }
            View metaParent = title.getParent() instanceof View ? (View) title.getParent() : null;
            if (metaParent != null && metaParent.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams mlp =
                        (android.view.ViewGroup.MarginLayoutParams) metaParent.getLayoutParams();
                mlp.leftMargin = 0;
                metaParent.setLayoutParams(mlp);
            }

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onBlogClick(item);
                }
            });
            return row;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        longPressHandler.removeCallbacks(longPressRunnable);
        if (videoAdapter != null) {
            videoAdapter.clearCache();
        }
    }
}
