package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.api.ChatApi;
import cn.ottohub.oh2013.api.OttoAuthApi;
import cn.ottohub.oh2013.util.MarkdownContentHelper;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;
import cn.ottohub.oh2013.util.SimpleWebSocket;

/**
 * 全站聊天室：HTTP 历史 + before_id 上滑加载更多；登录后 WebSocket 实时收发。
 * 参考 chat_api.md。
 */
public class ChatRoomFragment extends Fragment {

    private static class Row {
        ChatApi.ChatMessage msg;
    }

    private final List<Row> rows = new ArrayList<Row>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BaseAdapter adapter;
    private ListView listView;
    private EditText input;
    private SimpleWebSocket ws;
    private boolean wsReady;
    private boolean destroyed;
    private boolean loadingHistory;
    private boolean loadingMore;
    private boolean historyEnd;
    private boolean allowLoadOlder;
    private long replyToId;
    private String replyToHint;
    private final java.util.HashSet<Long> idSet = new java.util.HashSet<Long>();

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !isAdded()) return;
            if (!wsReady) {
                refreshLatest(false);
            }
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_room, container, false);
        View statusView = view.findViewById(R.id.chat_status);
        View announceView = view.findViewById(R.id.chat_announce);
        if (statusView != null) statusView.setVisibility(View.GONE);
        if (announceView != null) announceView.setVisibility(View.GONE);

        listView = (ListView) view.findViewById(R.id.chat_msg_list);
        input = (EditText) view.findViewById(R.id.chat_input);
        Button send = (Button) view.findViewById(R.id.chat_send);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return rows.get(i).msg != null ? rows.get(i).msg.id : i; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout root;
                if (convertView instanceof LinearLayout) {
                    root = (LinearLayout) convertView;
                } else {
                    root = new LinearLayout(getActivity());
                    root.setOrientation(LinearLayout.VERTICAL);
                    int pad = dp(8);
                    root.setPadding(pad, dp(6), pad, dp(6));
                }
                root.removeAllViews();

                final Row row = rows.get(position);
                final ChatApi.ChatMessage m = row.msg;
                long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                boolean mine = m != null && m.uid > 0 && selfMid > 0 && m.uid == selfMid;

                root.setBackgroundColor(mine ? 0x33FF8C00 : 0x00000000);

                TextView head = new TextView(getActivity());
                head.setTextSize(12);
                head.setTextColor(mine ? 0xFFE67300 : 0xFF666666);
                StringBuilder hb = new StringBuilder();
                if (m != null && m.username != null && m.username.length() > 0) {
                    hb.append(m.username);
                } else {
                    hb.append("匿名");
                }
                if (m != null && m.createdAt != null && m.createdAt.length() > 0) {
                    hb.append(" · ").append(m.createdAt);
                }
                head.setText(hb.toString());
                root.addView(head);

                if (m != null && m.replyToId > 0) {
                    TextView replyHint = new TextView(getActivity());
                    replyHint.setTextSize(11);
                    replyHint.setTextColor(0xFF999999);
                    replyHint.setPadding(0, dp(2), 0, dp(2));
                    String rc = m.replyContent != null ? m.replyContent : "";
                    if (rc.length() > 40) rc = rc.substring(0, 40) + "…";
                    replyHint.setText("回复 @"
                            + (m.replyUsername != null ? m.replyUsername : "")
                            + ": " + rc);
                    root.addView(replyHint);
                }

                LinearLayout body = new LinearLayout(getActivity());
                body.setOrientation(LinearLayout.VERTICAL);
                root.addView(body, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                String content = m != null ? m.content : "";
                // 聊天列表每行全量 Markdown+图片在旧机极卡；轻量渲染
                MarkdownContentHelper.renderInto(getActivity(), body, content, true);

                root.setClickable(true);
                root.setLongClickable(true);
                root.setOnClickListener(null);
                // 整条消息区域长按（含 Markdown 图片子视图）：放大判定范围
                root.setOnTouchListener(new View.OnTouchListener() {
                    private final android.graphics.Rect hit = new android.graphics.Rect();
                    private boolean waiting;
                    private final Runnable fire = new Runnable() {
                        @Override
                        public void run() {
                            if (waiting) {
                                waiting = false;
                                showMessageMenu(row);
                            }
                        }
                    };

                    @Override
                    public boolean onTouch(View v, android.view.MotionEvent event) {
                        switch (event.getAction()) {
                            case android.view.MotionEvent.ACTION_DOWN:
                                waiting = true;
                                v.getHitRect(hit);
                                v.postDelayed(fire, android.view.ViewConfiguration.getLongPressTimeout());
                                return true;
                            case android.view.MotionEvent.ACTION_MOVE:
                                if (waiting) {
                                    float x = event.getX();
                                    float y = event.getY();
                                    if (x < 0 || y < 0 || x > v.getWidth() || y > v.getHeight()) {
                                        waiting = false;
                                        v.removeCallbacks(fire);
                                    }
                                }
                                return true;
                            case android.view.MotionEvent.ACTION_UP:
                            case android.view.MotionEvent.ACTION_CANCEL:
                                waiting = false;
                                v.removeCallbacks(fire);
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                return root;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // 滑到顶部加载更早消息（首次定位到底部完成后再允许）
                if (allowLoadOlder && firstVisibleItem <= 0 && !loadingMore && !historyEnd && rows.size() > 0) {
                    loadOlder();
                }
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSend();
            }
        });

        refreshLatest(true);
        connectWsIfNeeded();
        handler.postDelayed(pollRunnable, 15000);
        return view;
    }

    @Override
    public void onDestroyView() {
        destroyed = true;
        handler.removeCallbacks(pollRunnable);
        if (ws != null) {
            ws.close();
            ws = null;
        }
        super.onDestroyView();
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    private void showMessageMenu(final Row row) {
        if (row == null || row.msg == null || getActivity() == null) return;
        final ChatApi.ChatMessage m = row.msg;
        long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final boolean mine = m.uid > 0 && selfMid > 0 && m.uid == selfMid;

        final String[] items = mine
                ? new String[]{"删除", "复制", "回复"}
                : new String[]{"复制", "回复"};

        new AlertDialog.Builder(getActivity())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String action = items[which];
                        if ("删除".equals(action)) {
                            doDelete(m.id);
                        } else if ("复制".equals(action)) {
                            try {
                                ClipboardManager cm = (ClipboardManager)
                                        getActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setText(m.content != null ? m.content : "");
                                    Toast.makeText(getActivity(), "已复制", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ignored) {
                            }
                        } else if ("回复".equals(action)) {
                            replyToId = m.id;
                            replyToHint = m.username;
                            if (input != null) {
                                input.setHint("回复 @" + (m.username != null ? m.username : "") + "…");
                                input.requestFocus();
                            }
                        }
                    }
                })
                .show();
    }

    private void doDelete(final long id) {
        if (id <= 0) return;
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChatApi.deleteMessage(id);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            removeById(id);
                            Toast.makeText(getActivity(), "已删除", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() != null) {
                                Toast.makeText(getActivity(),
                                        "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void removeById(long id) {
        idSet.remove(Long.valueOf(id));
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).msg != null && rows.get(i).msg.id == id) {
                rows.remove(i);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /** 首次/轮询：拉最新一页，合并进列表尾部 */
    private void refreshLatest(final boolean scrollEnd) {
        if (loadingHistory) return;
        loadingHistory = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ChatApi.RoomHistory history = ChatApi.fetchMessages(ChatApi.ROOM_MAIN, 50, 0);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingHistory = false;
                            if (!isAdded()) return;
                            boolean added = false;
                            // API 通常按时间正序或倒序；统一按 id 插入避免乱序
                            for (int i = 0; i < history.messages.size(); i++) {
                                ChatApi.ChatMessage m = history.messages.get(i);
                                if (m.id > 0 && idSet.contains(Long.valueOf(m.id))) continue;
                                if (m.id > 0) idSet.add(Long.valueOf(m.id));
                                Row r = new Row();
                                r.msg = m;
                                rows.add(r);
                                added = true;
                            }
                            if (added) {
                                sortRowsById();
                                adapter.notifyDataSetChanged();
                                if (scrollEnd && listView != null && rows.size() > 0) {
                                    listView.setSelection(rows.size() - 1);
                                    listView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            allowLoadOlder = true;
                                        }
                                    });
                                } else {
                                    allowLoadOlder = true;
                                }
                            } else if (scrollEnd) {
                                allowLoadOlder = true;
                            }
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingHistory = false;
                            if (!isAdded() || getActivity() == null) return;
                            if (scrollEnd && rows.size() == 0) {
                                Toast.makeText(getActivity(),
                                        "聊天室加载失败: " + (err != null ? err : "网络错误"),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /** 上滑：before_id = 当前最早消息 id */
    private void loadOlder() {
        if (loadingMore || historyEnd || rows.size() == 0) return;
        long oldest = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).msg != null && rows.get(i).msg.id > 0) {
                oldest = rows.get(i).msg.id;
                break;
            }
        }
        if (oldest <= 0) return;
        loadingMore = true;
        final long beforeId = oldest;
        final int oldTopId = (int) beforeId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ChatApi.RoomHistory history =
                            ChatApi.fetchMessages(ChatApi.ROOM_MAIN, 50, beforeId);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingMore = false;
                            if (!isAdded()) return;
                            int inserted = 0;
                            // 更早消息插到顶部（保持 id 升序）
                            List<Row> older = new ArrayList<Row>();
                            for (int i = 0; i < history.messages.size(); i++) {
                                ChatApi.ChatMessage m = history.messages.get(i);
                                if (m.id > 0 && idSet.contains(Long.valueOf(m.id))) continue;
                                if (m.id > 0) idSet.add(Long.valueOf(m.id));
                                Row r = new Row();
                                r.msg = m;
                                older.add(r);
                                inserted++;
                            }
                            if (inserted == 0) {
                                historyEnd = true;
                                return;
                            }
                            rows.addAll(0, older);
                            sortRowsById();
                            adapter.notifyDataSetChanged();
                            // 尽量保持当前阅读位置
                            if (listView != null) {
                                listView.setSelection(inserted);
                            }
                            if (!history.hasMore) {
                                historyEnd = true;
                            }
                        }
                    });
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingMore = false;
                        }
                    });
                }
            }
        }).start();
    }

    private void sortRowsById() {
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                long ia = a.msg != null ? a.msg.id : 0;
                long ib = b.msg != null ? b.msg.id : 0;
                return ia < ib ? -1 : (ia > ib ? 1 : 0);
            }
        });
    }

    private void connectWsIfNeeded() {
        if (!OttoAuthApi.isLoggedIn()) {
            return;
        }
        if (wsReady && ws != null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectWsOnce(false);
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded() || getActivity() == null) return;
                            // 不打断浏览；发送时会再尝试重连
                        }
                    });
                }
            }
        }).start();
    }

    private void connectWsOnce(final boolean retried) throws Exception {
        String token = ChatApi.ensureChatToken();
        String url = "wss://api-chat.ottohub.cn/ws?room=main&token="
                + URLEncoder.encode(token, "UTF-8");
        final SimpleWebSocket socket = new SimpleWebSocket(url, new SimpleWebSocket.Listener() {
            @Override
            public void onOpen() {
                wsReady = true;
                try {
                    ws.sendText("{\"type\":\"ping\"}");
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onMessage(String text) {
                handleWsMessage(text);
            }

            @Override
            public void onClose() {
                wsReady = false;
                // 断线后延迟重连（5.1 弱网常见）
                if (!destroyed && OttoAuthApi.isLoggedIn()) {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            if (!destroyed && !wsReady) connectWsIfNeeded();
                        }
                    }, 3000);
                }
            }

            @Override
            public void onError(Exception e) {
                wsReady = false;
                // 过期 chat_token 会导致 WS 失败：清缓存后重换一次
                if (!retried) {
                    ChatApi.clearChatToken();
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                connectWsOnce(true);
                            } catch (Exception ignored) {
                            }
                        }
                    }).start();
                } else if (!destroyed) {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            if (!destroyed && !wsReady) connectWsIfNeeded();
                        }
                    }, 5000);
                }
            }
        });
        ws = socket;
        socket.connect();
    }

    private void handleWsMessage(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type", "");
                    if ("pong".equals(type) || "ping".equals(type)) {
                        return;
                    }
                    if ("message_deleted".equals(type) || "deleted".equals(type)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data == null) data = json;
                        long delId = data.optLong("id", 0);
                        if (delId > 0) removeById(delId);
                        return;
                    }
                    JSONObject data = json.optJSONObject("data");
                    if (data == null) data = json;
                    String content = data.optString("content", "");
                    if (content.length() == 0) return;
                    ChatApi.ChatMessage m = ChatApi.parseMessage(data);
                    if (m.id > 0 && idSet.contains(Long.valueOf(m.id))) return;
                    if (m.id > 0) idSet.add(Long.valueOf(m.id));
                    if (m.content == null || m.content.length() == 0) m.content = content;
                    Row r = new Row();
                    r.msg = m;
                    rows.add(r);
                    sortRowsById();
                    adapter.notifyDataSetChanged();
                    if (listView != null && rows.size() > 0) {
                        listView.setSelection(rows.size() - 1);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void doSend() {
        if (input == null) return;
        final String text = input.getText().toString().trim();
        if (text.length() == 0) return;
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(getActivity(), "请先登录后再发言", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!wsReady || ws == null) {
            Toast.makeText(getActivity(), "正在连接聊天室…", Toast.LENGTH_SHORT).show();
            connectWsIfNeeded();
            // 短暂等待后若已连上则自动发出
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!isAdded()) return;
                    if (wsReady && ws != null) {
                        doSendNow(text, replyToId);
                    } else {
                        Toast.makeText(getActivity(),
                                "实时通道未就绪，请检查网络后重试", Toast.LENGTH_SHORT).show();
                        connectWsIfNeeded();
                    }
                }
            }, 1500);
            return;
        }
        doSendNow(text, replyToId);
    }

    private void doSendNow(final String text, final long replyId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ws == null || !wsReady) {
                        throw new Exception("未连接");
                    }
                    JSONObject payload = new JSONObject();
                    payload.put("type", "message");
                    payload.put("content", text);
                    if (replyId > 0) {
                        payload.put("reply_to", replyId);
                    }
                    ws.sendText(payload.toString());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (input != null) {
                                input.setText("");
                                input.setHint("输入消息");
                            }
                            replyToId = 0;
                            replyToHint = null;
                        }
                    });
                } catch (Exception e) {
                    final String msg = e.getMessage();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() != null) {
                                Toast.makeText(getActivity(), "发送失败: " + msg, Toast.LENGTH_SHORT).show();
                            }
                            connectWsIfNeeded();
                        }
                    });
                }
            }
        }).start();
    }
}
