package cn.ottohub.oh2013;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.api.ChannelApi;

/**
 * OTTOhub 频道列表（替代原「新番专题」Tab）。
 */
public class ChannelFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private ChannelAdapter adapter;
    private final List<ChannelApi.ChannelItem> channelList = new ArrayList<ChannelApi.ChannelItem>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isEnd = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        listView = (ListView) view.findViewById(R.id.channel_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        adapter = new ChannelAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (position < 0 || position >= channelList.size()) return;
                ChannelApi.ChannelItem item = channelList.get(position);
                Intent intent = new Intent(getActivity(), ChannelDetailActivity.class);
                intent.putExtra("channel_id", item.channelId);
                intent.putExtra("channel_title", item.channelTitle);
                startActivity(intent);
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    loadChannels(false);
                }
            }
        });

        loadChannels(true);
        return view;
    }

    private void loadChannels(final boolean refresh) {
        if (isLoading) return;
        if (refresh) {
            currentPage = 1;
            isEnd = false;
            channelList.clear();
        } else if (isEnd) {
            return;
        }

        isLoading = true;
        if (refresh) {
            progressBar.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }

        final int page = currentPage;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ChannelApi.ChannelItem> items = ChannelApi.fetchChannelList(page);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            progressBar.setVisibility(View.GONE);
                            if (items.size() < cn.ottohub.oh2013.api.ApiConfig.PAGE_SIZE) {
                                isEnd = true;
                            }
                            if (items.size() > 0) {
                                channelList.addAll(items);
                                currentPage++;
                                adapter.notifyDataSetChanged();
                            }
                            if (channelList.size() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                            } else {
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            progressBar.setVisibility(View.GONE);
                            if (getActivity() != null) {
                                Toast.makeText(getActivity(), "加载频道失败", Toast.LENGTH_SHORT).show();
                            }
                            if (channelList.size() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private class ChannelAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return channelList.size();
        }

        @Override
        public Object getItem(int position) {
            return channelList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return channelList.get(position).channelId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getActivity()).inflate(R.layout.item_channel, parent, false);
            }
            final ChannelApi.ChannelItem item = channelList.get(position);
            TextView title = (TextView) row.findViewById(R.id.channel_title);
            TextView desc = (TextView) row.findViewById(R.id.channel_desc);
            TextView meta = (TextView) row.findViewById(R.id.channel_meta);
            final ImageView cover = (ImageView) row.findViewById(R.id.channel_cover);

            title.setText(item.channelTitle);
            desc.setText(item.description != null && item.description.length() > 0
                    ? item.description : "暂无简介");
            meta.setText(item.memberCount + " 成员 · " + item.followerCount + " 关注");

            cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            // 回收复用时强制恢复固定尺寸，避免被 CoverImageLoader 改坏
            int coverSize = (int) (getResources().getDisplayMetrics().density * 72 + 0.5f);
            ViewGroup.LayoutParams clp = cover.getLayoutParams();
            if (clp != null) {
                clp.width = coverSize;
                clp.height = coverSize;
                cover.setLayoutParams(clp);
            }
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cn.ottohub.oh2013.util.CoverImageLoader.loadInto(
                    getActivity(), cover, item.coverUrl, coverSize, coverSize);
            return row;
        }
    }
}
