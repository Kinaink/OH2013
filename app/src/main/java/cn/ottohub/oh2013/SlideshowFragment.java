package cn.ottohub.oh2013;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ottohub.oh2013.api.SlideshowApi;
import cn.ottohub.oh2013.model.SlideshowItem;
import cn.ottohub.oh2013.util.CoverImageLoader;

/** 资讯：最多 5 条，大图居中，不显示标题 */
public class SlideshowFragment extends Fragment {

    private static final int MAX_SLIDES = 5;

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private final List<SlideshowItem> items = new ArrayList<SlideshowItem>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        listView = (ListView) view.findViewById(R.id.channel_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);
        emptyView.setText("暂无资讯");
        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int p) {
                return items.get(p);
            }

            @Override
            public long getItemId(int p) {
                return p;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(getActivity()).inflate(R.layout.item_slideshow, parent, false);
                }
                SlideshowItem item = items.get(position);
                ImageView cover = (ImageView) row.findViewById(R.id.slide_image);
                int w = getResources().getDisplayMetrics().widthPixels - (int) (16 * getResources().getDisplayMetrics().density);
                int h = (int) (getResources().getDisplayMetrics().density * 180);
                CoverImageLoader.loadInto(getActivity(), cover, item.imgUrl, w, h);
                return row;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                openHref(items.get(position).href);
            }
        });
        load();
        return view;
    }

    private void load() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<SlideshowItem> list = SlideshowApi.fetchActive();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            items.clear();
                            int n = list.size() > MAX_SLIDES ? MAX_SLIDES : list.size();
                            for (int i = 0; i < n; i++) {
                                items.add(list.get(i));
                            }
                            ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                            emptyView.setVisibility(items.size() == 0 ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getActivity(), "加载资讯失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void openHref(String href) {
        if (href == null || href.length() == 0 || getActivity() == null) return;
        Matcher vm = Pattern.compile("/v/(\\d+)").matcher(href);
        if (vm.find()) {
            Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
            intent.putExtra("aid", Long.parseLong(vm.group(1)));
            startActivity(intent);
            return;
        }
        Matcher bm = Pattern.compile("/b/(\\d+)").matcher(href);
        if (bm.find()) {
            Intent intent = new Intent(getActivity(), BlogDetailActivity.class);
            intent.putExtra("bid", Long.parseLong(bm.group(1)));
            startActivity(intent);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(href)));
        } catch (Exception ignored) {
        }
    }
}
