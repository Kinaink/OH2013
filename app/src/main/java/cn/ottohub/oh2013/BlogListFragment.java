package cn.ottohub.oh2013;

import android.content.Intent;
import android.graphics.Bitmap;
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

import cn.ottohub.oh2013.api.ApiConfig;
import cn.ottohub.oh2013.api.BlogApi;
import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.util.CoverImageLoader;

/** 动态列表（最新 / 热门），分页 num≤12 */
public class BlogListFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private final List<BlogItem> items = new ArrayList<BlogItem>();
    private BlogAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int page = 1;
    private boolean loading;
    private boolean end;

    private String getFeed() {
        Bundle args = getArguments();
        if (args != null) {
            String f = args.getString("feed");
            if (f != null) return f;
        }
        return BlogApi.FEED_LATEST;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        listView = (ListView) view.findViewById(R.id.channel_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        emptyView.setText("暂无动态");
        adapter = new BlogAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (position < 0 || position >= items.size()) return;
                BlogItem item = items.get(position);
                Intent intent = new Intent(getActivity(), BlogDetailActivity.class);
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
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!loading && !end && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    load(false);
                }
            }
        });
        load(true);
        return view;
    }

    private void load(final boolean refresh) {
        if (loading) return;
        if (refresh) {
            page = 1;
            end = false;
            items.clear();
            // FEED_RANDOM 已废弃
            if (BlogApi.FEED_RANDOM.equals(getFeed())) {
                end = true;
                loading = false;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                return;
            }
        } else if (end) {
            return;
        }
        loading = true;
        if (refresh) progressBar.setVisibility(View.VISIBLE);
        final int offset = (page - 1) * ApiConfig.PAGE_SIZE;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<BlogItem> batch = new ArrayList<BlogItem>();
                    BlogApi.fetchList(getFeed(), offset, batch);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            progressBar.setVisibility(View.GONE);
                            if (batch.size() > 0) {
                                items.addAll(batch);
                                page++;
                                adapter.notifyDataSetChanged();
                            }
                            if (batch.size() < ApiConfig.PAGE_SIZE) {
                                end = true;
                            }
                            emptyView.setVisibility(items.size() == 0 ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            progressBar.setVisibility(View.GONE);
                            if (getActivity() != null) {
                                Toast.makeText(getActivity(), "加载动态失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private class BlogAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).bid;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getActivity()).inflate(R.layout.item_blog, parent, false);
            }
            BlogItem item = items.get(position);
            TextView title = (TextView) row.findViewById(R.id.blog_title);
            TextView meta = (TextView) row.findViewById(R.id.blog_meta);
            ImageView avatar = (ImageView) row.findViewById(R.id.blog_avatar);

            title.setText(item.title != null && item.title.length() > 0 ? item.title : ("ob" + item.bid));
            meta.setText((item.username != null ? item.username : "") + " · " + (item.time != null ? item.time : ""));

            avatar.setImageResource(R.drawable.bili_default_avatar);
            int size = (int) (getResources().getDisplayMetrics().density * 48);
            String url = item.avatarUrl != null && item.avatarUrl.length() > 0
                    ? item.avatarUrl : item.thumbnail;
            CoverImageLoader.loadIntoCircle(getActivity(), avatar, url, size);
            return row;
        }
    }
}
