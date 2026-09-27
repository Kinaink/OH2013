package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.ottohub.oh2013.api.OttoApiUtil;
import cn.ottohub.oh2013.util.DialogUtil;

/**
 * 私聊会话详情：拉取 /api/im/conversations/{uid}/messages，可发送/撤回消息。
 * 默认拉最新消息（if_time_desc=1 后反转为时间正序），滑到顶部加载更早记录。
 */
public class ImChatActivity extends BaseActivity {

    private static final int PAGE_SIZE = 50;

    private static class Msg {
        long msgId;
        long sender;
        String name;
        String content;
        String time;
        boolean mine;
    }

    private long friendUid;
    private String friendName;
    private final List<Msg> messages = new ArrayList<Msg>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BaseAdapter adapter;
    private ListView listView;
    private ProgressBar loadingBar;
    private EditText input;
    private long myUid;
    private boolean loading;
    private boolean historyEnd;
    private int historyOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_im_chat);
        initRoundTitleBar();

        friendUid = getIntent().getLongExtra("friend_uid", 0);
        friendName = getIntent().getStringExtra("friend_name");
        if (friendName == null || friendName.length() == 0) {
            friendName = "用户" + friendUid;
        }
        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null) titleText.setText(friendName);
        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        myUid = cn.ottohub.oh2013.util.SharedPreferencesUtil.getLong(
                cn.ottohub.oh2013.util.SharedPreferencesUtil.mid, 0);

        listView = (ListView) findViewById(R.id.im_msg_list);
        loadingBar = (ProgressBar) findViewById(R.id.im_loading);
        input = (EditText) findViewById(R.id.im_input);
        Button send = (Button) findViewById(R.id.im_send);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return messages.size(); }
            @Override public Object getItem(int i) { return messages.get(i); }
            @Override public long getItemId(int i) {
                return messages.get(i).msgId > 0 ? messages.get(i).msgId : i;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = new TextView(ImChatActivity.this);
                    tv.setPadding(16, 12, 16, 12);
                    tv.setTextSize(14);
                }
                final Msg m = messages.get(position);
                String who = m.mine ? "我" : (m.name != null ? m.name : friendName);
                tv.setText(who + "  " + (m.time != null ? m.time : "") + "\n" + m.content);
                tv.setTextColor(m.mine ? 0xFFE67300 : 0xFF333333);
                tv.setBackgroundColor(m.mine ? 0x22FF8C00 : 0x00000000);
                tv.setClickable(true);
                tv.setLongClickable(m.mine && m.msgId > 0);
                int padH = (int) (getResources().getDisplayMetrics().density * 14 + 0.5f);
                int padV = (int) (getResources().getDisplayMetrics().density * 12 + 0.5f);
                tv.setPadding(padH, padV, padH, padV);
                if (m.mine && m.msgId > 0) {
                    tv.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            confirmDelete(m);
                            return true;
                        }
                    });
                } else {
                    tv.setOnLongClickListener(null);
                }
                return tv;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (firstVisibleItem == 0 && !loading && !historyEnd && messages.size() > 0) {
                    loadOlderMessages();
                }
            }
        });

        if (send != null) {
            send.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendMessage();
                }
            });
        }
        loadLatestMessages();
    }

    private void setLoading(boolean show) {
        if (loadingBar != null) {
            loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void confirmDelete(final Msg m) {
        if (m == null || m.msgId <= 0) return;
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle("撤回消息")
                .setMessage("确定删除（撤回）这条消息吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteMessage(m);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteMessage(final Msg m) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject resp = OttoApiUtil.deleteJsonWithToken(
                            "/api/im/messages/" + m.msgId);
                    if (!OttoApiUtil.isSuccess(resp)) {
                        throw new Exception(resp.optString("message", "删除失败"));
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            messages.remove(m);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(ImChatActivity.this, "已撤回", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImChatActivity.this,
                                    "撤回失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 首次加载：取最新一页并反转为正序，滚到底部 */
    private void loadLatestMessages() {
        if (loading) return;
        loading = true;
        historyOffset = 0;
        historyEnd = false;
        setLoading(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Msg> batch = fetchPage(0);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            setLoading(false);
                            messages.clear();
                            messages.addAll(batch);
                            historyOffset = batch.size();
                            if (batch.size() < PAGE_SIZE) {
                                historyEnd = true;
                            }
                            adapter.notifyDataSetChanged();
                            if (messages.size() > 0) {
                                listView.setSelection(messages.size() - 1);
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            setLoading(false);
                            Toast.makeText(ImChatActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 滑到顶部：加载更早的消息并保持滚动位置 */
    private void loadOlderMessages() {
        if (loading || historyEnd) return;
        loading = true;
        setLoading(true);
        final int reqOffset = historyOffset;
        final int oldCount = messages.size();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Msg> older = fetchPage(reqOffset);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            setLoading(false);
                            if (older.size() == 0) {
                                historyEnd = true;
                                return;
                            }
                            historyOffset = reqOffset + older.size();
                            if (older.size() < PAGE_SIZE) {
                                historyEnd = true;
                            }
                            messages.addAll(0, older);
                            adapter.notifyDataSetChanged();
                            // 保持视觉位置：新插入条数之后仍停在原先第一条附近
                            int first = listView.getFirstVisiblePosition();
                            View child = listView.getChildAt(0);
                            int top = child != null ? child.getTop() : 0;
                            listView.setSelectionFromTop(first + older.size(), top);
                            // 避免立刻再次触发顶部加载
                            if (oldCount == 0) {
                                listView.setSelection(messages.size() - 1);
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            setLoading(false);
                            Toast.makeText(ImChatActivity.this,
                                    "加载更早消息失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * if_time_desc=1：服务端按时间倒序返回（新→旧）。
     * 反转后变为正序（旧→新），便于 ListView 底部是最新。
     */
    private List<Msg> fetchPage(int offset) throws Exception {
        JSONObject result = OttoApiUtil.getJsonWithToken(
                "/api/im/conversations/" + friendUid
                        + "/messages?offset=" + offset
                        + "&num=" + PAGE_SIZE
                        + "&if_time_desc=1");
        List<Msg> batch = new ArrayList<Msg>();
        if (OttoApiUtil.isSuccess(result)) {
            JSONObject data = result.optJSONObject("data");
            JSONArray list = data != null ? data.optJSONArray("message_list") : null;
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.getJSONObject(i);
                    Msg m = new Msg();
                    m.msgId = OttoApiUtil.parseLong(o, "msg_id");
                    m.sender = OttoApiUtil.parseLong(o, "sender");
                    m.content = o.optString("content", "");
                    m.time = o.optString("time", "");
                    m.name = o.optString("sender_name", "");
                    m.mine = (m.sender == myUid);
                    batch.add(m);
                }
            }
        }
        Collections.reverse(batch);
        return batch;
    }

    private void sendMessage() {
        if (input == null) return;
        final String text = input.getText().toString().trim();
        if (text.length() == 0) return;
        input.setText("");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("receiver", friendUid);
                    body.put("content", text);
                    JSONObject resp = OttoApiUtil.postJsonWithToken("/api/im/messages", body);
                    if (!OttoApiUtil.isSuccess(resp)) {
                        throw new Exception(resp.optString("message", "发送失败"));
                    }
                    long newId = 0;
                    JSONObject data = resp.optJSONObject("data");
                    if (data != null) {
                        newId = OttoApiUtil.parseLong(data, "msg_id");
                    }
                    final long finalId = newId;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Msg m = new Msg();
                            m.msgId = finalId;
                            m.mine = true;
                            m.content = text;
                            m.name = "我";
                            m.time = "";
                            messages.add(m);
                            adapter.notifyDataSetChanged();
                            listView.setSelection(messages.size() - 1);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImChatActivity.this,
                                    "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
