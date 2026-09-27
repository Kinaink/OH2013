package cn.ottohub.oh2013;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class PartitionDetailActivity extends BaseActivity implements ViewPager.OnPageChangeListener, TabHost.OnTabChangeListener {

    private ViewPager mPager;
    private TabHost mTabHost;
    private LinearLayout mTabContainer;
    private List<View> mTabViews = new ArrayList<View>();
    private boolean mIsUpdating = false;

    private int[] mTabTypes;
    private String[] mTabNames;
    private int mMajorTid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMajorTid = getIntent().getIntExtra("rid", TidData.TID_PART);
        String partitionName = getIntent().getStringExtra("name");
        if (partitionName == null) partitionName = TidData.getNameByTid(mMajorTid);
        mTabTypes = TidData.getTabTypes(mMajorTid);
        mTabNames = TidData.getTabNames(mMajorTid);

        boolean useTabHost = cn.ottohub.oh2013.util.SdkHelper.getSdkInt() >= 4 && mTabTypes.length > 1;
        if (useTabHost) {
            setContentView(R.layout.activity_partition_detail);
        } else {
            setContentView(R.layout.activity_partition_detail_v3);
        }
        initRoundTitleBar();

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new CategoryPagerAdapter(getSupportFragmentManager()));
        mPager.setOnPageChangeListener(this);

        if (useTabHost) {
            initTabHost();
        } else if (mTabTypes.length > 1) {
            initSimpleTabs();
        }

        if (mTabHost != null) {
            mTabHost.setCurrentTab(0);
        } else if (mTabViews.size() > 0) {
            selectTab(0);
        }

        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setText(partitionName);
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
        View btnSearch = findViewById(R.id.btn_search);
        if (btnSearch != null) {
            // 动态分区：右上角改为「发布动态」（铅笔图标）；其它分区仍为搜索
            if (mMajorTid == TidData.TID_DOUGA) {
                if (btnSearch instanceof ImageView) {
                    ((ImageView) btnSearch).setImageResource(android.R.drawable.ic_menu_edit);
                }
                btnSearch.setContentDescription("发布动态");
                btnSearch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!cn.ottohub.oh2013.api.OttoAuthApi.isLoggedIn()) {
                            android.widget.Toast.makeText(PartitionDetailActivity.this,
                                    "请先登录", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startActivity(new Intent(PartitionDetailActivity.this, BlogPublishActivity.class));
                    }
                });
            } else {
                btnSearch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(PartitionDetailActivity.this, SearchActivity.class));
                    }
                });
            }
        }
    }

    private void initTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.setOnTabChangedListener(this);
        mTabHost.clearAllTabs();
        for (int i = 0; i < mTabTypes.length; i++) {
            String tag = String.valueOf(i);
            View tabView = LayoutInflater.from(this).inflate(R.layout.bili_category_tab_label, null);
            ((TextView) tabView).setText(mTabNames[i]);
            TabHost.TabSpec spec = mTabHost.newTabSpec(tag);
            try {
                TabHost.TabSpec.class.getMethod("setIndicator", View.class).invoke(spec, tabView);
            } catch (Exception e) {
                spec.setIndicator(mTabNames[i]);
            }
            mTabHost.addTab(spec.setContent(new DummyTabFactory(this)));
        }
        updateTabWidgetSelection(0);
    }

    private void updateTabWidgetSelection(int current) {
        if (mTabHost == null) return;
        TabWidget tw = mTabHost.getTabWidget();
        if (tw == null) return;
        for (int i = 0; i < tw.getChildCount(); i++) {
            View child = tw.getChildAt(i);
            if (child != null) {
                child.setSelected(i == current);
            }
        }
    }

    private void initSimpleTabs() {
        mTabContainer = (LinearLayout) findViewById(R.id.tab_container);
        if (mTabContainer != null) {
            mTabContainer.setVisibility(View.VISIBLE);
        }
        for (int i = 0; i < mTabTypes.length; i++) {
            final int idx = i;
            TextView tab = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.bili_category_tab_label, mTabContainer, false);
            tab.setText(mTabNames[i]);
            tab.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            tab.setFocusable(true);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(idx);
                    mPager.setCurrentItem(idx, false);
                }
            });
            mTabContainer.addView(tab);
            mTabViews.add(tab);
        }
    }

    private void selectTab(int position) {
        for (int i = 0; i < mTabViews.size(); i++) {
            mTabViews.get(i).setSelected(i == position);
        }
    }

    @Override
    public void onTabChanged(String tabId) {
        if (mIsUpdating) return;
        mIsUpdating = true;
        try {
            int index = Integer.parseInt(tabId);
            updateTabWidgetSelection(index);
            mPager.setCurrentItem(index, false);
        } catch (NumberFormatException ignored) {
        } finally {
            mIsUpdating = false;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (mIsUpdating) return;
        mIsUpdating = true;
        try {
            if (mTabHost != null) {
                mTabHost.setCurrentTab(position);
                updateTabWidgetSelection(position);
            } else {
                selectTab(position);
            }
        } finally {
            mIsUpdating = false;
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private static class DummyTabFactory implements TabHost.TabContentFactory {
        private final Context mContext;
        public DummyTabFactory(Context context) { this.mContext = context; }
        @Override
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    private class CategoryPagerAdapter extends FragmentStatePagerAdapter {
        public CategoryPagerAdapter(FragmentManager fm) { super(fm); }
        @Override
        public Fragment getItem(int position) {
            int type = mTabTypes[position];
            Bundle args = new Bundle();
            if (type == TidData.TAB_VIDEO_NEW) {
                args.putString("feed", cn.ottohub.oh2013.api.RecommendApi.FEED_NEW);
                RecommendFragment f = new RecommendFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_VIDEO_HOT) {
                args.putString("feed", cn.ottohub.oh2013.api.RecommendApi.FEED_POPULAR);
                RecommendFragment f = new RecommendFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_VIDEO_RANDOM) {
                args.putString("feed", cn.ottohub.oh2013.api.RecommendApi.FEED_RANDOM);
                RecommendFragment f = new RecommendFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_BLOG_NEW) {
                args.putString("feed", cn.ottohub.oh2013.api.BlogApi.FEED_LATEST);
                BlogListFragment f = new BlogListFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_BLOG_HOT) {
                args.putString("feed", cn.ottohub.oh2013.api.BlogApi.FEED_POPULAR);
                BlogListFragment f = new BlogListFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_BLOG_RANDOM) {
                args.putString("feed", cn.ottohub.oh2013.api.BlogApi.FEED_RANDOM);
                BlogListFragment f = new BlogListFragment();
                f.setArguments(args);
                return f;
            }
            if (type == TidData.TAB_IM_CHAT) {
                return new ImListFragment();
            }
            if (type == TidData.TAB_IM_ROOM) {
                return new ChatRoomFragment();
            }
            if (type == TidData.TAB_SLIDESHOW) {
                return new SlideshowFragment();
            }
            if (type == TidData.TAB_CHANNEL_LIST) {
                return new ChannelFragment();
            }
            return PartitionPageFragment.newInstance(type);
        }
        @Override
        public int getCount() { return mTabTypes.length; }
        @Override
        public CharSequence getPageTitle(int position) {
            return mTabNames[position];
        }
    }

    public static Intent createIntent(Context context, int tid) {
        Intent intent = new Intent(context, PartitionDetailActivity.class);
        intent.putExtra("rid", tid);
        intent.putExtra("name", TidData.getNameByTid(tid));
        return intent;
    }
}
