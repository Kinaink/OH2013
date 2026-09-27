package cn.ottohub.oh2013;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.adapter.ViewPagerViewAdapter;
import cn.ottohub.oh2013.api.ChannelApi;
import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.model.VideoCard;
import cn.ottohub.oh2013.util.CoverImageLoader;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * 频道详情：封面/简介/公告、加入/退出/关注，视频|动态双 Tab（ViewPager + 分页）。
 */
public class ChannelDetailActivity extends BaseActivity {

    private static final int TAB_VIDEO = 0;
    private static final int TAB_BLOG = 1;

    private long channelId;
    private ChannelApi.ChannelItem channel;

    private TextView titleText;
    private ImageView channelCover;
    private TextView channelNameTitle;
    private TextView channelDesc;
    private TextView channelMeta;
    private Button btnJoin;
    private Button btnLeave;
    private Button btnFollow;

    private TextView tabVideo;
    private TextView tabBlog;
    private View tabVideoUnderline;
    private View tabBlogUnderline;
    private ViewPager viewPager;

    private ListView videoListView;
    private ProgressBar videoProgress;
    private TextView videoEmpty;
    private RecommendGridAdapter videoAdapter;
    private final List<VideoCard> videoList = new ArrayList<VideoCard>();
    private int videoPage = 1;
    private boolean videoLoading;
    private boolean videoEnd;

    private ListView blogListView;
    private ProgressBar blogProgress;
    private TextView blogEmpty;
    private BlogSimpleAdapter blogAdapter;
    private final List<BlogItem> blogList = new ArrayList<BlogItem>();
    private int blogPage = 1;
    private boolean blogLoading;
    private boolean blogEnd;
    private boolean blogLoadedOnce;

    private boolean actionBusy;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_detail);
        initRoundTitleBar();

        channelId = getIntent().getLongExtra("channel_id", 0);
        String title = getIntent().getStringExtra("channel_title");
        if (title == null) title = "频道";

        titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null) titleText.setText(title);

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (channelId <= 0) {
            Toast.makeText(this, "频道无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initHeader();
        initTabsAndPager();
        loadChannelHeader();
        loadVideos(true);
    }

    private void initHeader() {
        channelCover = (ImageView) findViewById(R.id.channel_cover);
        channelNameTitle = (TextView) findViewById(R.id.channel_name_title);
        channelDesc = (TextView) findViewById(R.id.channel_desc);
        channelMeta = (TextView) findViewById(R.id.channel_meta);
        btnJoin = (Button) findViewById(R.id.btn_join);
        btnLeave = (Button) findViewById(R.id.btn_leave);
        btnFollow = (Button) findViewById(R.id.btn_follow);

        btnJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doJoin();
            }
        });
        btnLeave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLeave();
            }
        });
        btnFollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doToggleFollow();
            }
        });
    }

    private void initTabsAndPager() {
        tabVideo = (TextView) findViewById(R.id.tab_video);
        tabBlog = (TextView) findViewById(R.id.tab_blog);
        tabVideoUnderline = findViewById(R.id.tab_video_underline);
        tabBlogUnderline = findViewById(R.id.tab_blog_underline);
        viewPager = (ViewPager) findViewById(R.id.channel_pager);

        LayoutInflater inflater = LayoutInflater.from(this);
        View videoPage = inflater.inflate(R.layout.fragment_channel, null);
        videoListView = (ListView) videoPage.findViewById(R.id.channel_list);
        videoProgress = (ProgressBar) videoPage.findViewById(R.id.progress_bar);
        videoEmpty = (TextView) videoPage.findViewById(R.id.empty_view);
        videoEmpty.setText("该频道暂无视频");
        videoAdapter = new RecommendGridAdapter(this, videoList);
        videoListView.setAdapter(videoAdapter);
        videoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= videoList.size()) return;
                VideoCard card = videoList.get(position);
                Intent intent = new Intent(ChannelDetailActivity.this, VideoDetailActivity.class);
                intent.putExtra("aid", card.aid);
                intent.putExtra("bvid", card.bvid);
                startActivity(intent);
            }
        });
        videoListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!videoLoading && !videoEnd && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    loadVideos(false);
                }
            }
        });

        View blogPage = inflater.inflate(R.layout.fragment_channel, null);
        blogListView = (ListView) blogPage.findViewById(R.id.channel_list);
        blogProgress = (ProgressBar) blogPage.findViewById(R.id.progress_bar);
        blogEmpty = (TextView) blogPage.findViewById(R.id.empty_view);
        blogEmpty.setText("该频道暂无动态");
        blogAdapter = new BlogSimpleAdapter();
        blogListView.setAdapter(blogAdapter);
        blogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= blogList.size()) return;
                BlogItem item = blogList.get(position);
                Intent intent = new Intent(ChannelDetailActivity.this, BlogDetailActivity.class);
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
        blogListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!blogLoading && !blogEnd && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    loadBlogs(false);
                }
            }
        });

        List<View> pages = new ArrayList<View>();
        pages.add(videoPage);
        pages.add(blogPage);
        viewPager.setAdapter(new ViewPagerViewAdapter(pages));
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateTabStyle(position);
                if (position == TAB_BLOG && !blogLoadedOnce) {
                    loadBlogs(true);
                }
            }
        });

        tabVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(TAB_VIDEO, true);
            }
        });
        tabBlog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(TAB_BLOG, true);
            }
        });
    }

    private void updateTabStyle(int tab) {
        if (tabVideo != null) {
            if (tab == TAB_VIDEO) {
                tabVideo.setTextColor(0xFFFF8C00);
                tabVideo.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tabVideo.setTextColor(0xFF666666);
                tabVideo.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        if (tabBlog != null) {
            if (tab == TAB_BLOG) {
                tabBlog.setTextColor(0xFFFF8C00);
                tabBlog.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tabBlog.setTextColor(0xFF666666);
                tabBlog.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        if (tabVideoUnderline != null) {
            tabVideoUnderline.setBackgroundColor(tab == TAB_VIDEO ? 0xFFFF8C00 : 0x00000000);
        }
        if (tabBlogUnderline != null) {
            tabBlogUnderline.setBackgroundColor(tab == TAB_BLOG ? 0xFFFF8C00 : 0x00000000);
        }
    }

    private void loadChannelHeader() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ChannelApi.ChannelItem detail = ChannelApi.fetchChannelDetail(channelId);
                    String noticeContent = null;
                    try {
                        List<ChannelApi.NoticeItem> notices = ChannelApi.fetchNotices(channelId);
                        if (notices != null && notices.size() > 0) {
                            ChannelApi.NoticeItem first = notices.get(0);
                            if (first.content != null && first.content.length() > 0) {
                                noticeContent = first.content;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    final String announcement = noticeContent;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (detail == null) {
                                Toast.makeText(ChannelDetailActivity.this, "加载频道失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            channel = detail;
                            bindHeader(announcement);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ChannelDetailActivity.this, "加载频道失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void bindHeader(String announcement) {
        if (channel == null) return;
        if (titleText != null && channel.channelTitle != null) {
            titleText.setText(channel.channelTitle);
        }
        if (channelNameTitle != null) {
            channelNameTitle.setText(channel.channelTitle);
        }
        if (channelDesc != null) {
            if (announcement != null && announcement.length() > 0) {
                channelDesc.setText(announcement);
            } else if (channel.description != null && channel.description.length() > 0) {
                channelDesc.setText(channel.description);
            } else {
                channelDesc.setText("暂无简介");
            }
        }
        if (channelMeta != null) {
            channelMeta.setText(channel.memberCount + " 成员 · " + channel.followerCount + " 关注");
        }
        if (channelCover != null) {
            int h = (int) (getResources().getDisplayMetrics().density * 120);
            int w = getResources().getDisplayMetrics().widthPixels;
            CoverImageLoader.loadInto(this, channelCover, channel.coverUrl, w, h);
        }
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (channel == null) return;
        boolean member = channel.isMember;
        btnJoin.setEnabled(!member);
        btnJoin.setBackgroundColor(member ? 0xFFCCCCCC : 0xFFFF8C00);
        btnLeave.setEnabled(member);
        btnLeave.setBackgroundColor(member ? 0xFFFF8C00 : 0xFFCCCCCC);
        if (channel.isFollowing) {
            btnFollow.setText("取消关注");
            btnFollow.setBackgroundColor(0xFFCCCCCC);
        } else {
            btnFollow.setText("关注频道");
            btnFollow.setBackgroundColor(0xFFFF8C00);
        }
    }

    private boolean requireLogin() {
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.OTTO_TOKEN, "");
        if (token == null || token.length() == 0) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void doJoin() {
        if (actionBusy || !requireLogin()) return;
        actionBusy = true;
        btnJoin.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean ok = ChannelApi.joinChannel(channelId);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            if (ok) {
                                if (channel != null) {
                                    channel.isMember = true;
                                    channel.memberCount++;
                                }
                                Toast.makeText(ChannelDetailActivity.this, "已加入频道", Toast.LENGTH_SHORT).show();
                                updateActionButtons();
                                if (channelMeta != null && channel != null) {
                                    channelMeta.setText(channel.memberCount + " 成员 · " + channel.followerCount + " 关注");
                                }
                            } else {
                                btnJoin.setEnabled(true);
                                Toast.makeText(ChannelDetailActivity.this, "加入失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            updateActionButtons();
                            Toast.makeText(ChannelDetailActivity.this, "加入失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void doLeave() {
        if (actionBusy || !requireLogin()) return;
        actionBusy = true;
        btnLeave.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean ok = ChannelApi.leaveChannel(channelId);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            if (ok) {
                                if (channel != null) {
                                    channel.isMember = false;
                                    if (channel.memberCount > 0) channel.memberCount--;
                                }
                                Toast.makeText(ChannelDetailActivity.this, "已退出频道", Toast.LENGTH_SHORT).show();
                                updateActionButtons();
                                if (channelMeta != null && channel != null) {
                                    channelMeta.setText(channel.memberCount + " 成员 · " + channel.followerCount + " 关注");
                                }
                            } else {
                                updateActionButtons();
                                Toast.makeText(ChannelDetailActivity.this, "退出失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            updateActionButtons();
                            Toast.makeText(ChannelDetailActivity.this, "退出失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void doToggleFollow() {
        if (actionBusy || !requireLogin() || channel == null) return;
        actionBusy = true;
        btnFollow.setEnabled(false);
        final boolean currentlyFollowing = channel.isFollowing;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean ok = currentlyFollowing
                            ? ChannelApi.unfollowChannel(channelId)
                            : ChannelApi.followChannel(channelId);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            btnFollow.setEnabled(true);
                            if (ok) {
                                channel.isFollowing = !currentlyFollowing;
                                if (channel.isFollowing) {
                                    channel.followerCount++;
                                } else if (channel.followerCount > 0) {
                                    channel.followerCount--;
                                }
                                updateActionButtons();
                                if (channelMeta != null) {
                                    channelMeta.setText(channel.memberCount + " 成员 · " + channel.followerCount + " 关注");
                                }
                                Toast.makeText(ChannelDetailActivity.this,
                                        channel.isFollowing ? "已关注频道" : "已取消关注",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ChannelDetailActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionBusy = false;
                            btnFollow.setEnabled(true);
                            Toast.makeText(ChannelDetailActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadVideos(final boolean refresh) {
        if (videoLoading) return;
        if (refresh) {
            videoPage = 1;
            videoEnd = false;
            videoList.clear();
        } else if (videoEnd) {
            return;
        }

        videoLoading = true;
        if (refresh && videoProgress != null) {
            videoProgress.setVisibility(View.VISIBLE);
            if (videoEmpty != null) videoEmpty.setVisibility(View.GONE);
        }

        final int page = videoPage;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    final int code = ChannelApi.fetchChannelContent(channelId, "video", page, items, null);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            videoLoading = false;
                            if (videoProgress != null) videoProgress.setVisibility(View.GONE);
                            if (code == 1) videoEnd = true;
                            if (code < 0) {
                                Toast.makeText(ChannelDetailActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                            } else if (items.size() > 0) {
                                videoList.addAll(items);
                                videoPage++;
                                videoAdapter.notifyDataSetChanged();
                            }
                            if (videoEmpty != null) {
                                videoEmpty.setVisibility(videoList.size() == 0 ? View.VISIBLE : View.GONE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            videoLoading = false;
                            if (videoProgress != null) videoProgress.setVisibility(View.GONE);
                            Toast.makeText(ChannelDetailActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadBlogs(final boolean refresh) {
        if (blogLoading) return;
        if (refresh) {
            blogPage = 1;
            blogEnd = false;
            blogList.clear();
            blogLoadedOnce = true;
        } else if (blogEnd) {
            return;
        }

        blogLoading = true;
        if (refresh && blogProgress != null) {
            blogProgress.setVisibility(View.VISIBLE);
            if (blogEmpty != null) blogEmpty.setVisibility(View.GONE);
        }

        final int page = blogPage;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<BlogItem> items = new ArrayList<BlogItem>();
                    final int code = ChannelApi.fetchChannelContent(channelId, "blog", page, null, items);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            blogLoading = false;
                            if (blogProgress != null) blogProgress.setVisibility(View.GONE);
                            if (code == 1) blogEnd = true;
                            if (code < 0) {
                                Toast.makeText(ChannelDetailActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                            } else if (items.size() > 0) {
                                blogList.addAll(items);
                                blogPage++;
                                blogAdapter.notifyDataSetChanged();
                            }
                            if (blogEmpty != null) {
                                blogEmpty.setVisibility(blogList.size() == 0 ? View.VISIBLE : View.GONE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            blogLoading = false;
                            if (blogProgress != null) blogProgress.setVisibility(View.GONE);
                            Toast.makeText(ChannelDetailActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private class BlogSimpleAdapter extends BaseAdapter {
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
                row = LayoutInflater.from(ChannelDetailActivity.this).inflate(R.layout.item_blog, parent, false);
            }
            BlogItem item = blogList.get(position);
            TextView title = (TextView) row.findViewById(R.id.blog_title);
            TextView meta = (TextView) row.findViewById(R.id.blog_meta);
            ImageView avatar = (ImageView) row.findViewById(R.id.blog_avatar);

            title.setText(item.title != null && item.title.length() > 0 ? item.title : ("ob" + item.bid));
            meta.setText((item.username != null ? item.username : "") + " · " + (item.time != null ? item.time : ""));

            // 频道动态列表不显示封面/头像图
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
            return row;
        }
    }
}
