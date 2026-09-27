package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ottohub.oh2013.api.FavoriteApi;
import cn.ottohub.oh2013.api.OttoCommentApi;
import cn.ottohub.oh2013.api.ReplyApi;
import cn.ottohub.oh2013.util.KeyBindingUtil;
import cn.ottohub.oh2013.util.ReplyHelper;
import cn.ottohub.oh2013.util.NetWorkUtil;
import cn.ottohub.oh2013.util.DialogUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class CommentFragment extends Fragment {

    private static final String TAG = "CommentFragment";

    private static final Map<String, CachedComments> commentCache = new HashMap<String, CachedComments>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private View footerView;
    private ProgressBar footerProgressBar;
    private TextView footerText;

    private CommentAdapter adapter;
    private List<CommentItem> commentList = new ArrayList<CommentItem>();
    private Set<Long> commentIdSet = new HashSet<Long>();

    private long aid;
    private long bid;
    private String bvid;
    private String nextCursor = "";
    private boolean isLoading = false;
    private boolean isEnd = false;
    private boolean isRestoring = false;
    private boolean isViewCreated = false;
    private boolean isVisibleToUser = true; // 独立 Activity 嵌入时默认可加载；ViewPager 会覆盖
    private boolean hasLoaded = false;

    private static class CachedComments {
        List<CommentItem> items;
        Set<Long> idSet;
        String nextCursor;
        boolean isEnd;
        long timestamp;
        int sortMode;
    }

    private String getCacheKey() {
        if (bid > 0) return "bid_" + bid;
        if (aid != 0) return "aid_" + aid;
        if (bvid != null) return "bvid_" + bvid;
        return null;
    }

    // 保存滚动位置（静态字段，跨 Fragment 销毁保持）
    private static int sExitScrollPosition = -1;
    private int savedScrollPosition = -1;

    // 排序控制
    private int currentSortMode = 0; // 0=时间,1=热度

    // 方向键选中的评论位置（-1 表示未选中）
    private int selectedCommentPosition = -1;
    private boolean sortExplicitlySet = false;
    private TextView sortTimeBtn, sortHotBtn;

    // 评论图片上传
    private static final int MAX_COMMENT_IMAGES = 9;
    private ArrayList<String> pendingImageDataList = new ArrayList<String>();
    private TextView dialogImageBtn = null;
    private CommentItem pendingNewComment = null;
    private static final int REQUEST_PICK_COMMENT_IMAGE = 1001;

    private static class PendingLikeUpdate {
        long rpid;
        boolean liked;
        int likeCount;
        PendingLikeUpdate(long rpid, boolean liked, int likeCount) {
            this.rpid = rpid;
            this.liked = liked;
            this.likeCount = likeCount;
        }
    }
    private static PendingLikeUpdate pendingLikeUpdate = null;
    private static CommentFragment activeInstance = null;

    public static void notifyRootLikeChanged(long rpid, boolean liked, int likeCount) {
        CommentFragment instance = activeInstance;
        if (instance != null) {
            instance.applyPendingLike(rpid, liked, likeCount);
        } else {
            pendingLikeUpdate = new PendingLikeUpdate(rpid, liked, likeCount);
        }
    }

    private void applyPendingLike(long rpid, boolean liked, int likeCount) {
        for (CommentItem item : commentList) {
            if (item.rpid == rpid) {
                item.liked = liked;
                item.likeCount = likeCount;
                break;
            }
        }
        commentCache.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static final String[] EMOJIS = {
        "( ゜- ゜)つロ", "_(:з」∠)_", "（⌒▽⌒）", "（￣▽￣）", "⌓‿⌓",
        "(=・ω・=)", "(*°▽°*)", "八(*°▽°*)♪", "✿ヽ(°▽°)ノ✿", "(¦3【▓▓】",
        "눈_눈", "(ಡωಡ)", "_(≧∇≦」∠)_", "━━━∑(ﾟ□ﾟ*川", "━(｀・ω・´)",
        "(￣3￣)", "✧(≖ ◡ ≖✿)", "(･∀･)", "(〜￣△￣)〜", "→_→",
        "(°∀°)ﾉ", "╮(￣▽￣)╭", "( ´_ゝ｀)", "←_←", "(;¬_¬)",
        "(ﾟДﾟ≡ﾟдﾟ)!?", "( ´･･)ﾉ", "(._.`)", "Σ(ﾟдﾟ;)", "Σ( ￣□￣||)",
        "<(´；ω；`)", "（/TДT)/", "(^・ω・^)", "(｡･ω･｡)", "(●￣(ｴ)￣●)",
        "ε=ε=(ノ≧∇≦)ノ", "(´･_･`)", "(-_-#)", "（￣へ￣）", "(￣ε(#￣)",
        "Σ(╯°口°)╯(┴—┴", "ヽ(`Д´)ﾉ", "(\"▔□▔)/", "(º﹃º )", "(๑>؂<๑）",
        "｡ﾟ(ﾟ´Д｀)ﾟ｡", "(∂ω∂)", "(┯_┯)", "(・ω< )★", "( ๑ˊ•̥▵•)੭₎₎",
        "¥ㄟ(´･ᴗ･`)ノ¥", "Σ_(꒪ཀ꒪」∠)_", "٩(๛ ˘ ³˘)۶❤", "(๑‾᷅^‾᷅๑)"
    };

    // 上下文

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment, container, false);

        listView = (ListView) view.findViewById(R.id.list_view);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        listView.setDivider(null);
        listView.setDividerHeight(0);
        // 禁用原生 selector 选中高亮（触屏点击不再残留），键盘光标高亮由 adapter 的 selectedPosition 控制
        listView.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));

        footerView = LayoutInflater.from(getActivity()).inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        listView.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bid = args.getLong("bid", 0);
            bvid = args.getString("bvid");
        }

        Log.e(TAG, "========== onCreateView ==========");
        Log.e(TAG, "aid=" + aid + ", bid=" + bid + ", bvid=" + bvid);

        adapter = new CommentAdapter(getActivity(), commentList, aid, this);
        adapter.setMid(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
        adapter.setBid(bid);
        adapter.setIsBlog(bid > 0);
        adapter.setReplyType(bid > 0 ? ReplyApi.REPLY_TYPE_DYNAMIC : ReplyApi.REPLY_TYPE_VIDEO);
        adapter.setBvid(bvid);
        listView.setAdapter(adapter);

        // 排序按钮（按时间 / 按热度）；整栏隐藏，避免顶栏与首条评论之间留白
        sortTimeBtn = (TextView) view.findViewById(R.id.sort_time);
        sortHotBtn = (TextView) view.findViewById(R.id.sort_hot);
        View sortBar = view.findViewById(R.id.sort_bar);
        if (sortBar != null) {
            sortBar.setVisibility(View.GONE);
        }
        if (sortTimeBtn != null && sortHotBtn != null) {
            sortTimeBtn.setVisibility(View.GONE);
            sortHotBtn.setVisibility(View.GONE);
        }

        adapter.setOnUserClickListener(new CommentAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(long mid, String userName) {
                if (mid != 0) {
                    Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.commentfragment_toast_65e0), Toast.LENGTH_SHORT).show();
                }
            }
        });

        adapter.setOnReplyClickListener(new CommentAdapter.OnReplyClickListener() {
            @Override
            public void onReplyClick(CommentItem comment, ReplyItem reply) {
                showReplyDialog(comment, reply);
            }
        });

        adapter.setOnLikeListener(new CommentAdapter.OnLikeListener() {
            @Override
            public void onLikeSuccess() {
                commentCache.clear();
            }
        });

        // 滚动监听 - 保存滚动位置
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    // 保存滚动位置
                    int firstVisible = view.getFirstVisiblePosition();
                    savedScrollPosition = firstVisible;

                    adapter.setScrolling(false);
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && !isRestoring && totalCount > 0) {
                        loadMoreComments();
                    }
                } else {
                    adapter.setScrolling(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && !isRestoring && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreComments();
                    }
                }
            }
        });

        // 检查是否有保存的滚动位置
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt("savedScrollPosition", -1);
        }

        isViewCreated = true;
        lazyLoad();

        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        this.isVisibleToUser = isVisibleToUser;
        if (isViewCreated) {
            lazyLoad();
        }
    }

    private void lazyLoad() {
        if (isVisibleToUser && !hasLoaded) {
            hasLoaded = true;
            loadComments();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("savedScrollPosition", savedScrollPosition);
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
        // 独立 Activity（如动态评论）可能从未收到 setUserVisibleHint，兜底加载
        if (isViewCreated && !hasLoaded) {
            hasLoaded = true;
            loadComments();
        }
        if (pendingLikeUpdate != null) {
            applyPendingLike(pendingLikeUpdate.rpid, pendingLikeUpdate.liked, pendingLikeUpdate.likeCount);
            pendingLikeUpdate = null;
        }
        if (!isRestoring) {
            restoreScrollPosition();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveCurrentScrollPosition();
        sExitScrollPosition = savedScrollPosition;
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQUEST_PICK_COMMENT_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            final android.net.Uri imageUri = data.getData();
            if (imageUri != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final android.app.Activity activity = getActivity();
                        if (activity == null) return;
                        try {
                            java.io.InputStream is = activity.getContentResolver().openInputStream(imageUri);
                            if (is == null) return;
                            byte[] imageBytes = NetWorkUtil.readStream(is);
                            is.close();

                            // 原图直传：不做尺寸缩放与质量压缩，保留原始图片数据
                            String mimeType = activity.getContentResolver().getType(imageUri);
                            String ext = "jpg";
                            if (mimeType != null) {
                                if (mimeType.contains("png")) {
                                    ext = "png";
                                } else if (mimeType.contains("gif")) {
                                    ext = "gif";
                                } else if (mimeType.contains("webp")) {
                                    ext = "webp";
                                }
                            }

                            final String fileName = "comment_" + System.currentTimeMillis() + "." + ext;
                            final String resultJson = ReplyApi.uploadReplyImage(aid != 0 ? aid : 0, imageBytes, fileName);

                            if (resultJson != null && pendingImageDataList.size() < MAX_COMMENT_IMAGES) {
                                pendingImageDataList.add(resultJson);
                                final int imgCount = pendingImageDataList.size();
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (dialogImageBtn != null) {
                                            dialogImageBtn.setText("图片(" + imgCount + ")");
                                        }
                                        Toast.makeText(activity, "图片已上传 (" + imgCount + ")", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, activity.getString(R.string.commentfragment_toast_56fe), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } catch (final Exception e) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(activity, "图片上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onStop() {
        super.onStop();
        saveCurrentScrollPosition();
        sExitScrollPosition = savedScrollPosition;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.clearCache();
        }
    }

    // 恢复滚动位置
    private void restoreScrollPosition() {
        if (savedScrollPosition < 0 || listView == null || adapter == null) return;
        if (savedScrollPosition >= adapter.getCount()) {
            savedScrollPosition = Math.max(0, adapter.getCount() - 1);
        }
        listView.setSelectionFromTop(savedScrollPosition, 0);
    }

    // 保存当前滚动位置
    private void saveCurrentScrollPosition() {
        if (listView == null) return;
        savedScrollPosition = listView.getFirstVisiblePosition();
    }

    // 刷新评论（供外部调用）
    public void refreshComments() {
        if (isLoading) return;
        String cacheKey = getCacheKey();
        if (cacheKey != null) {
            commentCache.remove(cacheKey);
        }
        nextCursor = "";
        isEnd = false;
        commentList.clear();
        commentIdSet.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        savedScrollPosition = 0;
        hasLoaded = true;
        loadComments();
    }

    private void applyCurrentSort() {
        if (commentList == null || commentList.size() < 2) return;
        // Separate pinned comments from the rest
        final List<CommentItem> pinned = new ArrayList<CommentItem>();
        final List<CommentItem> others = new ArrayList<CommentItem>();
        for (CommentItem item : commentList) {
            if (item.isTop) {
                pinned.add(item);
            } else {
                others.add(item);
            }
        }
        java.util.Collections.sort(others, new java.util.Comparator<CommentItem>() {
            @Override
            public int compare(CommentItem a, CommentItem b) {
                if (currentSortMode == 1) {
                    return Integer.valueOf(b.likeCount).compareTo(Integer.valueOf(a.likeCount));
                } else {
                    return Long.valueOf(b.time).compareTo(Long.valueOf(a.time));
                }
            }
        });
        commentList.clear();
        commentList.addAll(pinned);
        commentList.addAll(others);
    }

    private void sortAndRefreshComments() {
        if (commentList == null || commentList.size() == 0) {
            refreshComments();
            return;
        }
        applyCurrentSort();
        if (adapter != null) {
            adapter.updateData(commentList);
        }
        // 排序后回到顶部
        if (listView != null) {
            listView.setSelection(0);
        }
    }

    private void loadComments() {
        if (bid <= 0 && aid == 0 && (bvid == null || bvid.length() == 0)) {
            Log.e(TAG, "aid/bid/bvid 都无效，无法加载评论");
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        emptyView.setText(getString(R.string.commentfragment_settext_65e0));
                        emptyView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
            return;
        }

        if (isLoading) return;

        String cacheKey = getCacheKey();
        CachedComments cached = cacheKey != null ? commentCache.get(cacheKey) : null;

        // 用跨 Fragment 销毁的静态退出位置恢复滚动（bundle 可能因 setAdapter(null) 丢失）
        if (savedScrollPosition < 0 && sExitScrollPosition >= 0) {
            savedScrollPosition = sExitScrollPosition;
        }

        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            commentList.clear();
            commentIdSet.clear();
            commentList.addAll(cached.items);
            commentIdSet.addAll(cached.idSet);
            nextCursor = cached.nextCursor;
            isEnd = cached.isEnd;
            // 恢复排序状态但不重新排序（缓存数据已排好序）
            if (cached.sortMode == 1) {
                currentSortMode = 1;
                sortExplicitlySet = true;
            } else {
                currentSortMode = 0;
            }
            // 更新排序按钮颜色
            if (sortTimeBtn != null && sortHotBtn != null) {
                if (currentSortMode == 1) {
                    sortTimeBtn.setTextColor(0xFF999999);
                    sortHotBtn.setTextColor(0xFFFF8C00);
                } else {
                    sortTimeBtn.setTextColor(0xFFFF8C00);
                    sortHotBtn.setTextColor(0xFF999999);
                }
            }

            progressBar.setVisibility(View.GONE);
            adapter.updateData(commentList);
            if (commentList.size() == 0) {
                emptyView.setText(getString(R.string.commentfragment_settext_6682));
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
            isRestoring = true;
            restoreScrollPosition();
            listView.post(new Runnable() {
                @Override
                public void run() {
                    isRestoring = false;
                }
            });
            return;
        }

        isLoading = true;
        nextCursor = "";
        isEnd = false;

        commentList.clear();
        commentIdSet.clear();

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        footerView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                fetchCommentsFromNetwork();
            }
        }).start();
    }

    private void fetchCommentsFromNetwork() {
        try {
            final OttoCommentApi.FetchResult fetch;
            if (bid > 0) {
                Log.e(TAG, "使用 bid 请求动态评论: " + bid);
                fetch = OttoCommentApi.fetchBlogComments(bid, 0, 0);
            } else {
                final long vid;
                if (aid != 0) {
                    vid = aid;
                    Log.e(TAG, "使用 aid 请求评论: " + vid);
                } else {
                    try {
                        vid = Long.parseLong(bvid);
                    } catch (NumberFormatException e) {
                        showError("无效的视频 ID");
                        isLoading = false;
                        return;
                    }
                    Log.e(TAG, "使用 bvid 请求评论: " + vid);
                }
                fetch = OttoCommentApi.fetchVideoComments(vid, 0, 0);
            }
            Log.e(TAG, "评论 API code: " + fetch.code + ", message: " + fetch.message);

            if (fetch.code == OttoCommentApi.CODE_OK) {
                nextCursor = fetch.nextOffset;
                isEnd = fetch.isEnd;
                JSONArray replies = fetch.replies;
                if (replies != null && replies.length() > 0) {
                    Log.e(TAG, "获取到 " + replies.length() + " 条评论");
                    commentIdSet.clear();
                    List<CommentItem> items = parseFirstComments(replies);
                    if (items != null) {
                        saveCommentCache(items);
                        applyCommentList(items);
                    }
                } else {
                    Log.e(TAG, "没有评论");
                    showEmpty("暂无评论");
                }
            } else {
                Log.e(TAG, "API 返回错误: " + fetch.message);
                showError("加载失败: " + fetch.message);
            }
        } catch (final OutOfMemoryError e) {
            Log.e(TAG, "评论数据过大，内存不足");
            showError("评论数据过大，无法加载");
        } catch (final Exception e) {
            Log.e(TAG, "加载评论异常: " + e.getMessage(), e);
            showError("加载失败: " + e.getMessage());
        } finally {
            isLoading = false;
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    private void applyCommentList(final List<CommentItem> items) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentList.clear();
                commentList.addAll(items);
                if (sortExplicitlySet) {
                    applyCurrentSort();
                }
                // Re-pin the user's newly created comment to the top
                if (pendingNewComment != null) {
                    long targetRpid = pendingNewComment.rpid;
                    CommentItem existing = null;
                    for (CommentItem item : commentList) {
                        if (item.rpid == targetRpid) {
                            existing = item;
                            break;
                        }
                    }
                    if (existing != null) {
                        commentList.remove(existing);
                    }
                    int insertPos = 0;
                    // Find how many pinned comments are at the start
                    while (insertPos < commentList.size() && commentList.get(insertPos).isTop) {
                        insertPos++;
                    }
                    if (existing != null) {
                        commentList.add(insertPos, existing);
                    } else {
                        commentList.add(insertPos, pendingNewComment);
                    }
                    pendingNewComment = null;
                }
                isRestoring = true;
                adapter.updateData(commentList);

                if (commentList.size() == 0) {
                    emptyView.setText(getString(R.string.commentfragment_settext_6682));
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    if (!isEnd && nextCursor != null && nextCursor.length() > 0) {
                        footerView.setVisibility(View.VISIBLE);
                    }
                }
                restoreScrollPosition();
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        isRestoring = false;
                    }
                });
            }
        });
    }

    private void saveCommentCache(List<CommentItem> items) {
        String cacheKey = getCacheKey();
        if (cacheKey == null) return;
        saveCurrentScrollPosition();
        CachedComments cached = new CachedComments();
        cached.items = new ArrayList<CommentItem>(items);
        cached.idSet = new HashSet<Long>(commentIdSet);
        cached.nextCursor = nextCursor;
        cached.isEnd = isEnd;
        cached.timestamp = System.currentTimeMillis();
        cached.sortMode = currentSortMode;
        commentCache.put(cacheKey, cached);
    }

    private List<CommentItem> parseFirstComments(JSONArray replies) throws Exception {
        List<CommentItem> items = new ArrayList<CommentItem>();

        for (int i = 0; i < replies.length(); i++) {
            try {
                JSONObject reply = replies.getJSONObject(i);
                if (reply == null) continue;

                long replyId = reply.optLong("rpid", 0);
                if (replyId == 0) continue;

                if (commentIdSet.contains(replyId)) {
                    continue;
                }
                commentIdSet.add(replyId);

                CommentItem item = new CommentItem();
                item.rpid = replyId;
                item.root = reply.optLong("root", 0);
                item.parent = reply.optLong("parent", 0);
                // 顶级列表跳过子评，避免拿子评 id 去回复触发 error_parent
                if (item.parent > 0 || item.root > 0) {
                    continue;
                }
                item.replyCount = reply.optInt("rcount", 0);

                JSONObject member = reply.optJSONObject("member");
                if (member != null) {
                    item.userName = member.optString("uname", "匿名用户");
                    item.mid = member.optLong("mid", 0);
                    if (item.mid == 0) {
                        try {
                            item.mid = Long.parseLong(member.optString("mid", "0").trim());
                        } catch (Exception ignored) {
                            item.mid = 0;
                        }
                    }
                    String avatar = member.optString("avatar", "");
                    if (avatar != null && avatar.length() > 0) {
                        avatar = avatar.replace("/64", "/48");
                    }
                    item.userAvatar = avatar;
                } else {
                    item.userName = "匿名用户";
                    item.userAvatar = null;
                    item.mid = 0;
                }

                JSONObject content = reply.optJSONObject("content");
                if (content != null) {
                    item.message = normalizeCommentMessage(content.optString("message", ""));
                    // 解析图片
                    JSONArray pictures = content.optJSONArray("pictures");
                    if (pictures != null && pictures.length() > 0) {
                        item.pictureList = new ArrayList<String>();
                        for (int p = 0; p < pictures.length(); p++) {
                            JSONObject pic = pictures.getJSONObject(p);
                            String imgSrc = pic.optString("img_src", "");
                            if (imgSrc != null && imgSrc.length() > 0) {
                                if (imgSrc.startsWith("https://")) {
                                    imgSrc = "http://" + imgSrc.substring(8);
                                }
                                item.pictureList.add(imgSrc);
                            }
                        }
                    }
                } else {
                    item.message = "";
                }

                item.likeCount = reply.optInt("like", 0);
                item.liked = reply.optInt("action", 0) == 1;
                item.time = reply.optLong("ctime", 0);
                JSONObject replyCtrl = reply.optJSONObject("reply_control");
                item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);
                long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                int ifMy = parseIfMyComment(reply, member);
                item.isMine = ifMy == 1 || (selfMid != 0 && item.mid == selfMid);

                JSONArray replyReplies = reply.optJSONArray("replies");
                if (replyReplies != null && replyReplies.length() > 0) {
                    item.replies = new ArrayList<ReplyItem>();
                    for (int j = 0; j < replyReplies.length(); j++) {
                        try {
                            JSONObject rr = replyReplies.getJSONObject(j);
                            ReplyItem ri = new ReplyItem();
                            ri.rpid = rr.optLong("rpid", 0);
                            ri.root = rr.optLong("root", 0);
                            if (ri.root == 0) ri.root = item.rpid;
                            ri.parent = rr.optLong("parent", 0);
                            // parent 缺省时挂到一级评论，切勿写成子评自身 rpid
                            if (ri.parent == 0 || ri.parent == ri.rpid) ri.parent = item.rpid;
                            if (ri.root == ri.rpid) ri.root = item.rpid;
                            JSONObject rmember = rr.optJSONObject("member");
                            if (rmember != null) {
                                ri.userName = rmember.optString("uname", "");
                                ri.mid = rmember.optLong("mid", 0);
                            }
                            JSONObject rcontent = rr.optJSONObject("content");
                            String rawRi = rcontent != null ? rcontent.optString("message", "") : "";
                            ri.message = normalizeCommentMessage(rawRi);
                            item.replies.add(ri);
                        } catch (Exception e) { }
                    }
                }

                items.add(item);
            } catch (Exception e) {
                Log.e(TAG, "解析单条评论失败: " + e.getMessage());
            }
        }

        return items;
    }

    /** 把 HTML 提及转成 markdown，供 formatCommentRichText 渲染黄色 @ */
    public static String normalizeCommentMessage(String raw) {
        if (raw == null || raw.length() == 0) return "";
        String result = raw.replaceAll(
                "(?i)<a\\s+[^>]*href\\s*=\\s*[\"'](https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?)[\"'][^>]*>\\s*@?([^<]+?)\\s*</a>",
                "[@$3](https://www.ottohub.cn/u/$2)");
        result = result.replaceAll("<br\\s*/?>", "\n");
        // 去掉其余标签，保留 markdown 提及
        result = result.replaceAll("(?i)</?p[^>]*>", "\n");
        result = result.replaceAll("<[^>]+>", "");
        return result.trim();
    }

    private static int parseIfMyComment(JSONObject reply, JSONObject member) {
        int ifMy = 0;
        if (reply != null) {
            Object o = reply.opt("if_my_comment");
            ifMy = coerceIfMy(o);
        }
        if (ifMy == 0 && member != null) {
            ifMy = coerceIfMy(member.opt("if_my_comment"));
        }
        return ifMy;
    }

    private static int coerceIfMy(Object o) {
        if (o == null || o == JSONObject.NULL) return 0;
        if (o instanceof Boolean) return ((Boolean) o).booleanValue() ? 1 : 0;
        if (o instanceof Number) return ((Number) o).intValue() != 0 ? 1 : 0;
        String s = String.valueOf(o).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return 1;
        return 0;
    }

    private void loadMoreComments() {
        if (isLoading || isEnd) return;
        if (nextCursor == null || nextCursor.length() == 0) {
            isEnd = true;
            footerView.setVisibility(View.GONE);
            return;
        }

        isLoading = true;

        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        final boolean useBlog = bid > 0;
        final long vid;
        if (useBlog) {
            vid = bid;
        } else if (aid != 0) {
            vid = aid;
        } else {
            try {
                vid = Long.parseLong(bvid);
            } catch (NumberFormatException e) {
                isLoading = false;
                showLoadMoreError("无效的视频 ID");
                return;
            }
        }
        final String cursor = nextCursor;
        int offset = 0;
        try {
            offset = Integer.parseInt(cursor);
        } catch (NumberFormatException e) {
            offset = 0;
        }
        final int fetchOffset = offset;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OttoCommentApi.FetchResult fetch;
                    if (useBlog) {
                        fetch = OttoCommentApi.fetchBlogComments(vid, fetchOffset, 0);
                    } else {
                        fetch = OttoCommentApi.fetchVideoComments(vid, fetchOffset, 0);
                    }
                    Log.e(TAG, "加载更多 code: " + fetch.code);

                    if (fetch.code == OttoCommentApi.CODE_OK) {
                        nextCursor = fetch.nextOffset;
                        isEnd = fetch.isEnd;
                        JSONArray replies = fetch.replies;
                        if (replies != null && replies.length() > 0) {
                            appendMoreComments(replies);
                        } else {
                            isEnd = true;
                            showEnd();
                        }
                    } else {
                        showLoadMoreError(fetch.message != null ? fetch.message : "加载失败");
                    }
                } catch (final OutOfMemoryError e) {
                    Log.e(TAG, "评论数据过大，内存不足");
                    showLoadMoreError("评论数据过大");
                } catch (final Exception e) {
                    Log.e(TAG, "加载更多异常: " + e.getMessage(), e);
                    showLoadMoreError("加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void appendMoreComments(JSONArray replies) throws Exception {
        final List<CommentItem> items = new ArrayList<CommentItem>();

        for (int i = 0; i < replies.length(); i++) {
            try {
                JSONObject reply = replies.getJSONObject(i);
                if (reply == null) continue;

                long replyId = reply.optLong("rpid", 0);
                if (replyId == 0) continue;

                if (commentIdSet.contains(replyId)) {
                    continue;
                }
                commentIdSet.add(replyId);

                CommentItem item = new CommentItem();
                item.rpid = replyId;
                item.root = reply.optLong("root", 0);
                item.parent = reply.optLong("parent", 0);
                // 顶级列表跳过子评，避免拿子评 id 去回复触发 error_parent
                if (item.parent > 0 || item.root > 0) {
                    continue;
                }
                item.replyCount = reply.optInt("rcount", 0);

                JSONObject member = reply.optJSONObject("member");
                if (member != null) {
                    item.userName = member.optString("uname", "匿名用户");
                    item.mid = member.optLong("mid", 0);
                    if (item.mid == 0) {
                        try {
                            item.mid = Long.parseLong(member.optString("mid", "0").trim());
                        } catch (Exception ignored) {
                            item.mid = 0;
                        }
                    }
                    String avatar = member.optString("avatar", "");
                    if (avatar != null && avatar.length() > 0) {
                        avatar = avatar.replace("/64", "/48");
                    }
                    item.userAvatar = avatar;
                } else {
                    item.userName = "匿名用户";
                    item.userAvatar = null;
                    item.mid = 0;
                }

                JSONObject content = reply.optJSONObject("content");
                if (content != null) {
                    item.message = normalizeCommentMessage(content.optString("message", ""));
                    JSONArray pictures = content.optJSONArray("pictures");
                    if (pictures != null && pictures.length() > 0) {
                        item.pictureList = new ArrayList<String>();
                        for (int p = 0; p < pictures.length(); p++) {
                            JSONObject pic = pictures.getJSONObject(p);
                            String imgSrc = pic.optString("img_src", "");
                            if (imgSrc != null && imgSrc.length() > 0) {
                                if (imgSrc.startsWith("https://")) {
                                    imgSrc = "http://" + imgSrc.substring(8);
                                }
                                item.pictureList.add(imgSrc);
                            }
                        }
                    }
                } else {
                    item.message = "";
                }

                item.likeCount = reply.optInt("like", 0);
                item.liked = reply.optInt("action", 0) == 1;
                item.time = reply.optLong("ctime", 0);
                JSONObject replyCtrl = reply.optJSONObject("reply_control");
                item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);
                long selfMidMore = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                int ifMyMore = parseIfMyComment(reply, member);
                item.isMine = ifMyMore == 1 || (selfMidMore != 0 && item.mid == selfMidMore);

                JSONArray replyReplies = reply.optJSONArray("replies");
                if (replyReplies != null && replyReplies.length() > 0) {
                    item.replies = new ArrayList<ReplyItem>();
                    for (int j = 0; j < replyReplies.length(); j++) {
                        try {
                            JSONObject rr = replyReplies.getJSONObject(j);
                            ReplyItem ri = new ReplyItem();
                            ri.rpid = rr.optLong("rpid", 0);
                            ri.root = rr.optLong("root", 0);
                            if (ri.root == 0) ri.root = item.rpid;
                            ri.parent = rr.optLong("parent", 0);
                            if (ri.parent == 0 || ri.parent == ri.rpid) ri.parent = item.rpid;
                            if (ri.root == ri.rpid) ri.root = item.rpid;
                            JSONObject rmember = rr.optJSONObject("member");
                            if (rmember != null) {
                                ri.userName = rmember.optString("uname", "");
                                ri.mid = rmember.optLong("mid", 0);
                            }
                            JSONObject rcontent = rr.optJSONObject("content");
                            String rawRi = rcontent != null ? rcontent.optString("message", "") : "";
                            ri.message = normalizeCommentMessage(rawRi);
                            item.replies.add(ri);
                        } catch (Exception e) { }
                    }
                }

                items.add(item);
            } catch (Exception e) {
                Log.e(TAG, "解析单条评论失败: " + e.getMessage());
            }
        }

        if (getActivity() == null) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentList.addAll(items);
                if (sortExplicitlySet) {
                    applyCurrentSort();
                }
                adapter.updateData(commentList);
                saveCommentCache(commentList);

                footerProgressBar.setVisibility(View.GONE);
                isLoading = false;

                if (isEnd) {
                    if (footerProgressBar != null) {
                        footerProgressBar.setVisibility(View.GONE);
                    }
                    if (footerText != null) {
                        footerText.setText(getString(R.string.emoticon__no_more_data));
                        footerText.setVisibility(View.VISIBLE);
                    }
                    footerView.setVisibility(View.VISIBLE);
                } else {
                    footerView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /** 公开入口：弹出「发评论」对话框（comment == null → isNewComment）。 */
    public void openNewCommentDialog() {
        showReplyDialog(null, null);
    }

    private void showReplyDialog(final CommentItem comment, final ReplyItem reply) {
        if (getActivity() == null) return;
        final boolean isNewComment = (comment == null);

        String hint = "输入回复内容...";
        if (reply != null && reply.userName != null && reply.userName.length() > 0) {
            hint = "回复 " + reply.userName + " 的评论...";
        } else if (comment != null && comment.userName != null && comment.userName.length() > 0) {
            hint = "回复 " + comment.userName + " 的评论...";
        }

        final LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        final EditText input = new EditText(getActivity());
        input.setHint(hint);
        input.setLines(3);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        if (cn.ottohub.oh2013.util.SdkHelper.getSdkInt() >= 14) {
            android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
            inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            inputBg.setStroke(dpToPx(2), 0xFFD0D0D0);
            inputBg.setColor(0xFFFFFFFF);
            input.setBackgroundDrawable(inputBg);
        }

        // 顶部按钮行：表情（图片入口已移除）
        final LinearLayout btnRow = new LinearLayout(getActivity());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, dpToPx(6));

        final TextView emojiBtn = new TextView(getActivity());
        emojiBtn.setText(getString(R.string.commentfragment_settext_8868));
        emojiBtn.setTextSize(13);
        emojiBtn.setTextColor(0xFFFF8C00);
        emojiBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        emojiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmojiPicker(input);
            }
        });
        btnRow.addView(emojiBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(btnRow, lp);
        layout.addView(input, lp);

        final TextView clearText = new TextView(getActivity());
        clearText.setText(getString(R.string.commentfragment_settext_6e05));
        clearText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clearText.setPadding(0, 8, 0, 0);
        clearText.setTextSize(14);
        clearText.setTextColor(0xFF666666);
        clearText.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        clearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
            }
        });
        layout.addView(clearText, lp);

        final AlertDialog dialog = new AlertDialog.Builder(DialogUtil.wrap(getActivity()))
                .setTitle(isNewComment ? "发评论" : "发送回复")
                .setView(layout)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pendingImageDataList.clear();
                        d.dismiss();
                    }
                })
                .create();

        dialog.show();
        final android.widget.Button sendBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ReplyHelper.isSending()) {
                    Toast.makeText(getActivity(), "正在发送，请稍候", Toast.LENGTH_SHORT).show();
                    return;
                }
                String text = input.getText().toString().trim();
                if (text == null || text.length() == 0) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.commentfragment_toast_5185), Toast.LENGTH_SHORT).show();
                    return;
                }
                saveCurrentScrollPosition();
                sendBtn.setEnabled(false);
                sendBtn.setText("发送中...");

                final ReplyHelper.ReplyCallback cb = new ReplyHelper.ReplyCallback() {
                    @Override
                    public void onSuccess(String responseJson) {
                        dialog.dismiss();
                        if (isNewComment) {
                            Toast.makeText(getActivity(), getActivity().getString(R.string.commentfragment_toast_8bc4), Toast.LENGTH_SHORT).show();
                            // 勿用本地假 rpid 再插入：refresh 已含真实评论，否则会重复
                            pendingNewComment = null;
                        } else {
                            Toast.makeText(getActivity(), getActivity().getString(R.string.commentfragment_toast_56de), Toast.LENGTH_SHORT).show();
                        }
                        pendingImageDataList.clear();
                        refreshComments();
                    }
                    @Override
                    public void onFailed(String error) {
                        sendBtn.setEnabled(true);
                        sendBtn.setText("发送");
                    }
                };

                final boolean isBlog = bid > 0;
                final long oid = isBlog ? bid : aid;
                if (isNewComment) {
                    if (!isBlog && pendingImageDataList.size() > 0) {
                        String picturesJson = buildPicturesJson();
                        ReplyHelper.sendReplyWithPictures(getActivity(), oid, 0, 0, text, picturesJson, cb);
                    } else {
                        ReplyHelper.sendReply(getActivity(), oid, 0, 0, text, isBlog, cb);
                    }
                } else {
                    // OTTOhub 仅两级：parent_* = 一级评论 bcid/vcid；回复楼中楼也挂到一级
                    // 顶级列表里的 comment 即一级，优先用 comment.rpid，避免误发成 parent=0 的顶级评论
                    long rootId = comment.rpid;
                    if (reply != null) {
                        if (reply.root > 0 && reply.root != reply.rpid) {
                            rootId = reply.root;
                        } else if (reply.parent > 0 && reply.parent != reply.rpid) {
                            rootId = reply.parent;
                        }
                    } else if (comment.root > 0 && comment.root != comment.rpid) {
                        rootId = comment.root;
                    } else if (comment.parent > 0 && comment.parent != comment.rpid) {
                        rootId = comment.parent;
                    }
                    if (rootId <= 0) {
                        rootId = comment.rpid;
                    }
                    // 禁止把子评 id 当 parent（会触发 error_parent）
                    if (reply != null && reply.rpid > 0 && rootId == reply.rpid) {
                        rootId = comment.rpid;
                    }
                    if (rootId <= 0) {
                        sendBtn.setEnabled(true);
                        sendBtn.setText("发送");
                        Toast.makeText(getActivity(), "无法回复：评论无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ReplyHelper.sendReply(getActivity(), oid, rootId, rootId, text, isBlog, cb);
                }
            }
        });
    }

    private String buildPicturesJson() {
        try {
            JSONArray pics = new JSONArray();
            for (int i = 0; i < pendingImageDataList.size(); i++) {
                JSONObject imgData = new JSONObject(pendingImageDataList.get(i));
                JSONObject pic = new JSONObject();
                pic.put("img_src", imgData.optString("image_url", ""));
                pic.put("img_width", imgData.optInt("image_width", 0));
                pic.put("img_height", imgData.optInt("image_height", 0));
                pic.put("img_size", imgData.optDouble("img_size", 0));
                pics.put(pic);
            }
            return pics.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void showEmojiPicker(final EditText input) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(DialogUtil.wrap(getActivity()));
        builder.setTitle(getString(R.string.commentfragment_settitle_9009));

        final android.widget.ScrollView scroll = new android.widget.ScrollView(getActivity());
        final android.widget.LinearLayout list = new android.widget.LinearLayout(getActivity());
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        list.setPadding(0, dpToPx(8), 0, dpToPx(8));

        for (int i = 0; i < EMOJIS.length; i++) {
            final String emoji = EMOJIS[i];
            final android.widget.TextView tv = new android.widget.TextView(getActivity());
            tv.setText(emoji);
            tv.setTextSize(16);
            tv.setTextColor(0xFF333333);
            tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            tv.setClickable(true);
            android.graphics.drawable.GradientDrawable emojiNormal = new android.graphics.drawable.GradientDrawable();
            emojiNormal.setColor(0xFFF0F0F0);
            android.graphics.drawable.GradientDrawable emojiPressed = new android.graphics.drawable.GradientDrawable();
            emojiPressed.setColor(0x40FF8C00);
            android.graphics.drawable.StateListDrawable emojiBg = new android.graphics.drawable.StateListDrawable();
            emojiBg.addState(new int[]{android.R.attr.state_pressed}, emojiPressed);
            emojiBg.addState(new int[]{}, emojiNormal);
            tv.setBackgroundDrawable(emojiBg);
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int remaining = 1000 - input.length();
                    if (remaining <= 0) return;
                    String toInsert = emoji.length() <= remaining ? emoji : emoji.substring(0, remaining);
                    int pos = input.getSelectionStart();
                    if (pos < 0) pos = input.length();
                    input.getText().insert(pos, toInsert);
                }
            });
            list.addView(tv);

            if (i < EMOJIS.length - 1) {
                android.view.View divider = new android.view.View(getActivity());
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFDDDDDD);
                list.addView(divider);
            }
        }

        scroll.addView(list, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        builder.setView(scroll);
        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    private int dpToPx(int dp) {
        if (getActivity() == null) return dp;
        float density = getActivity().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private String extractCsrfFromCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return null;
        }
        Pattern p = Pattern.compile("bili_jct=([a-f0-9]+)");
        Matcher m = p.matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void showError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showEnd() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (footerProgressBar != null) {
                    footerProgressBar.setVisibility(View.GONE);
                }
                if (footerText != null) {
                    footerText.setText(getString(R.string.emoticon__no_more_data));
                    footerText.setVisibility(View.VISIBLE);
                }
                footerView.setVisibility(View.VISIBLE);
                isLoading = false;
            }
        });
    }

    private void showLoadMoreError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                footerProgressBar.setVisibility(View.GONE);
                isLoading = false;
                footerView.setVisibility(View.VISIBLE);
                if ("没有更多评论".equals(msg)) {
                    footerText.setText(getString(R.string.emoticon__no_more_data));
                } else {
                    footerText.setText(msg);
                }
                footerText.setVisibility(View.VISIBLE);
            }
        });
    }

    public void insertNewComment(CommentItem item) {
        // 发送接口返回的是本地占位 rpid，与服务端不一致；只刷新列表，避免重复插入
        pendingNewComment = null;
        refreshComments();
    }

    public static CommentItem parseCommentFromResponse(String responseJson) {
        try {
            JSONObject result = new JSONObject(responseJson);
            if (result.optInt("code", -1) != 0) return null;
            JSONObject data = result.optJSONObject("data");
            if (data == null) return null;
            JSONObject reply = data.optJSONObject("reply");
            if (reply == null) return null;

            long replyId = reply.optLong("rpid", 0);
            if (replyId == 0) return null;

            CommentItem item = new CommentItem();
            item.rpid = replyId;
            item.replyCount = reply.optInt("rcount", 0);

            JSONObject member = reply.optJSONObject("member");
            if (member != null) {
                item.userName = member.optString("uname", "匿名用户");
                item.mid = member.optLong("mid", 0);
                String avatar = member.optString("avatar", "");
                if (avatar != null && avatar.length() > 0) {
                    avatar = avatar.replace("/64", "/48");
                    if (avatar.startsWith("https://")) {
                        avatar = "http://" + avatar.substring(8);
                    }
                }
                item.userAvatar = avatar;
            } else {
                item.userName = "匿名用户";
                item.userAvatar = null;
                item.mid = 0;
            }

            JSONObject content = reply.optJSONObject("content");
            if (content != null) {
                item.message = normalizeCommentMessage(content.optString("message", ""));
                JSONArray pictures = content.optJSONArray("pictures");
                if (pictures != null && pictures.length() > 0) {
                    item.pictureList = new ArrayList<String>();
                    for (int p = 0; p < pictures.length(); p++) {
                        JSONObject pic = pictures.getJSONObject(p);
                        String imgSrc = pic.optString("img_src", "");
                        if (imgSrc != null && imgSrc.length() > 0) {
                            if (imgSrc.startsWith("https://")) {
                                imgSrc = "http://" + imgSrc.substring(8);
                            }
                            item.pictureList.add(imgSrc);
                        }
                    }
                }
            } else {
                item.message = "";
            }

            item.likeCount = reply.optInt("like", 0);
            item.liked = reply.optInt("action", 0) == 1;
            item.time = reply.optLong("ctime", 0);
            JSONObject replyCtrl = reply.optJSONObject("reply_control");
            item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);
            long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            int ifMy = parseIfMyComment(reply, member);
            item.isMine = ifMy == 1 || (selfMid != 0 && item.mid == selfMid);
            item.replies = null;
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 供 VideoDetailActivity.dispatchKeyEvent 调用：
     * 方向键在评论列表内上下移动光标（一个评论为一个焦点），
     * 确认键进入该评论的回复界面（有回复→回复列表；无回复→弹回复输入框）。
     * 返回 true 表示事件已被消费。
     */
    public boolean handleRemoteKey(KeyEvent event) {
        if (commentList == null || commentList.size() == 0 || listView == null) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = KeyBindingUtil.classify(event.getKeyCode());
        // 只处理 UP/DOWN/CONFIRM 与翻页键（2/8），其他键不消费（交给上层）
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM
                && action != KeyBindingUtil.ACTION_NUM_2
                && action != KeyBindingUtil.ACTION_NUM_8) {
            return false;
        }
        if (selectedCommentPosition < 0) {
            selectedCommentPosition = 0;
        }
        // 首次按下才移动光标；长按 repeat 只消费不移动，防止 ListView 内置滚动干扰
        if (event.getRepeatCount() == 0) {
            int count = commentList.size();
            if (action == KeyBindingUtil.ACTION_UP) {
                selectedCommentPosition = Math.max(0, selectedCommentPosition - 1);
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                selectedCommentPosition = Math.min(count - 1, selectedCommentPosition + 1);
            } else if (action == KeyBindingUtil.ACTION_NUM_2) {
                selectedCommentPosition = pageMove(-1);
            } else if (action == KeyBindingUtil.ACTION_NUM_8) {
                selectedCommentPosition = pageMove(1);
            } else if (action == KeyBindingUtil.ACTION_CONFIRM) {
                CommentItem item = commentList.get(selectedCommentPosition);
                openCommentReplies(item);
                return true;
            }
            applyCommentSelection();
        }
        // 所有 DOWN 事件都消费（含长按 repeat），避免列表自身滚动导致"回顶"
        return true;
    }

    /**
     * 数字键 2/8：按一屏（当前可见项数）快速翻页。
     * direction=-1 向上翻，+1 向下翻；返回新位置（已做边界钳制）。
     */
    private int pageMove(int direction) {
        if (listView == null) {
            return selectedCommentPosition;
        }
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedCommentPosition + direction * visibleCount;
        int count = commentList.size();
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    private void applyCommentSelection() {
        // 先刷新高亮，再立即定位到选中项（setSelection 无动画竞争，不会回顶）
        if (adapter != null) {
            adapter.setSelectedPosition(selectedCommentPosition);
        }
        if (listView != null) {
            listView.setSelection(selectedCommentPosition);
        }
    }

    private void openCommentReplies(CommentItem item) {
        if (item == null) {
            return;
        }
        // OTTOhub 父评不嵌套子评，有回复数就进回复页拉 parent_vcid
        if (item.replyCount > 0 || (item.replies != null && item.replies.size() > 0)) {
            showAllReplies(item);
        } else {
            showReplyDialog(item, null);
        }
    }

    // 显示全部回复
    public void showAllReplies(CommentItem item) {
        if (item == null) {
            return;
        }
        if (item.replyCount <= 0 && (item.replies == null || item.replies.size() == 0)) {
            Toast.makeText(getActivity(), getActivity().getString(R.string.commentfragment_toast_6682), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ReplyListActivity.class);
        int shown = item.replyCount > 0 ? item.replyCount
                : (item.replies != null ? item.replies.size() : 0);
        intent.putExtra("title", "全部回复（" + shown + "条）");
        intent.putExtra("root_user_name", item.userName);
        intent.putExtra("root_comment_message", item.message);
        intent.putExtra("root_mid", item.mid);
        intent.putExtra("root_time", item.time);
        intent.putExtra("root_avatar", item.userAvatar);
        if (item.pictureList != null && item.pictureList.size() > 0) {
            intent.putExtra("root_pictures", new ArrayList<String>(item.pictureList));
        }
        intent.putExtra("aid", aid);
        intent.putExtra("bid", bid);
        intent.putExtra("bvid", bvid);
        intent.putExtra("rpid", item.rpid);
        intent.putExtra("total_count", shown);
        intent.putExtra("root_like_count", item.likeCount);
        intent.putExtra("root_liked", item.liked);
        intent.putExtra("root_is_top", item.isTop);
        intent.putExtra("root_is_mine", item.isMine);
        startActivity(intent);
    }

    public static class ReplyItem {
        public String userName;
        public String message;
        public long mid;
        public long rpid;
        public long root;
        public long parent;
    }

    public static class CommentItem {
        public long rpid;
        public long root;
        public long parent;
        public String userName;
        public String userAvatar;
        public String message;
        public int likeCount;
        public boolean liked;
        public boolean isTop;
        public boolean isMine;
        public long time;
        public long mid;
        public int replyCount;
        public List<String> pictureList;
        public List<ReplyItem> replies;
    }
}