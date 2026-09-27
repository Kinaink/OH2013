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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.api.ApiConfig;
import cn.ottohub.oh2013.api.OttoApiUtil;
import cn.ottohub.oh2013.api.OttoAuthApi;
import cn.ottohub.oh2013.util.CoverImageLoader;

/** 私聊会话列表（im_api.md：num 最大 12） */
public class ImListFragment extends Fragment {

    private static class Conv {
        long uid;
        String name;
        String lastMsg;
        String time;
        String avatar;
    }

    private static class Holder {
        ImageView cover;
        TextView title;
        TextView desc;
        TextView meta;
        String avatarTag;
    }

    private final List<Conv> items = new ArrayList<Conv>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListView listView;
    private ProgressBar progressBar;
    private TextView empty;
    private BaseAdapter adapter;
    private boolean loading;
    private boolean ended;
    private int offset;
    private int avatarSizePx;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        listView = (ListView) view.findViewById(R.id.channel_list);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        empty = (TextView) view.findViewById(R.id.empty_view);
        avatarSizePx = (int) (getResources().getDisplayMetrics().density * 72 + 0.5f);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int p) { return items.get(p); }
            @Override public long getItemId(int p) { return items.get(p).uid; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Holder h;
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(getActivity()).inflate(R.layout.item_channel, parent, false);
                    h = new Holder();
                    h.cover = (ImageView) row.findViewById(R.id.channel_cover);
                    h.title = (TextView) row.findViewById(R.id.channel_title);
                    h.desc = (TextView) row.findViewById(R.id.channel_desc);
                    h.meta = (TextView) row.findViewById(R.id.channel_meta);
                    row.setTag(h);
                } else {
                    h = (Holder) row.getTag();
                }
                Conv c = items.get(position);
                h.title.setText(c.name != null ? c.name : "用户");
                h.desc.setText(c.lastMsg != null ? c.lastMsg : "");
                h.meta.setText(c.time != null ? c.time : "");

                // 固定头像尺寸，避免回收后 LayoutParams 错乱
                ViewGroup.LayoutParams lp = h.cover.getLayoutParams();
                if (lp != null) {
                    lp.width = avatarSizePx;
                    lp.height = avatarSizePx;
                    h.cover.setLayoutParams(lp);
                }
                h.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String url = c.avatar != null ? c.avatar : "";
                String tag = url + "#" + c.uid;
                // 先重置占位，避免旧头像闪一下造成「错乱」
                if (h.avatarTag == null || !h.avatarTag.equals(tag)) {
                    h.cover.setImageResource(R.drawable.bili_default_avatar);
                    h.avatarTag = tag;
                    h.cover.setTag(tag);
                    if (url.length() > 0 && getActivity() != null) {
                        CoverImageLoader.loadIntoCircle(getActivity(), h.cover, url, avatarSizePx);
                    }
                }
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (position < 0 || position >= items.size()) return;
                Conv c = items.get(position);
                Intent intent = new Intent(getActivity(), ImChatActivity.class);
                intent.putExtra("friend_uid", c.uid);
                intent.putExtra("friend_name", c.name);
                startActivity(intent);
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!loading && !ended && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    loadConversations(false);
                }
            }
        });
        loadConversations(true);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (items.size() == 0 && !loading) {
            loadConversations(true);
        }
    }

    private void loadConversations(final boolean refresh) {
        if (loading) return;
        if (!OttoAuthApi.isLoggedIn()) {
            items.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (empty != null) {
                empty.setText("暂无私聊，请先登录");
                empty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (refresh) {
            offset = 0;
            ended = false;
            items.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
        } else if (ended) {
            return;
        }

        loading = true;
        if (refresh && progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            if (empty != null) empty.setVisibility(View.GONE);
        }

        final int reqOffset = offset;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String errMsg = null;
                final List<Conv> batch = new ArrayList<Conv>();
                try {
                    JSONObject result = OttoApiUtil.getJsonWithToken(
                            "/api/im/conversations?offset=" + reqOffset
                                    + "&num=" + ApiConfig.PAGE_SIZE
                                    + "&if_time_desc=1");
                    if (OttoApiUtil.isSuccess(result)) {
                        JSONObject data = result.optJSONObject("data");
                        JSONArray users = data != null ? data.optJSONArray("user_list") : null;
                        if (users != null) {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject u = users.getJSONObject(i);
                                Conv c = new Conv();
                                c.uid = OttoApiUtil.parseLong(u, "uid");
                                c.name = u.optString("username", "用户");
                                c.lastMsg = u.optString("last_message", "");
                                c.time = u.optString("last_time", "");
                                c.avatar = u.optString("avatar_url", "");
                                batch.add(c);
                            }
                        }
                    } else {
                        errMsg = result != null ? result.optString("message", "加载失败") : "加载失败";
                        if ("error_token".equals(errMsg)) {
                            errMsg = "登录已失效，请重新登录";
                        }
                    }
                } catch (Exception e) {
                    errMsg = e.getMessage() != null ? e.getMessage() : "网络错误";
                }

                final String finalErr = errMsg;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (!isAdded()) return;
                        if (finalErr != null) {
                            if (items.size() == 0 && empty != null) {
                                empty.setText(finalErr);
                                empty.setVisibility(View.VISIBLE);
                            } else if (getActivity() != null) {
                                Toast.makeText(getActivity(), finalErr, Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        items.addAll(batch);
                        offset = items.size();
                        if (batch.size() < ApiConfig.PAGE_SIZE) {
                            ended = true;
                        }
                        adapter.notifyDataSetChanged();
                        if (empty != null) {
                            if (items.size() == 0) {
                                empty.setText("暂无私聊");
                                empty.setVisibility(View.VISIBLE);
                            } else {
                                empty.setVisibility(View.GONE);
                            }
                        }
                    }
                });
            }
        }).start();
    }
}
