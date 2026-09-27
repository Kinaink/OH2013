package cn.ottohub.oh2013;


import cn.ottohub.oh2013.util.NetWorkUtil;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ottohub.oh2013.api.ReplyApi;
import cn.ottohub.oh2013.util.GlobalImageCache;
import cn.ottohub.oh2013.util.DialogUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class CommentAdapter extends BaseAdapter {

    private static final Pattern MENTION_PATTERN = Pattern.compile(
            "\\[@([^\\]]+)\\]\\(\\s*https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION_HTML_PATTERN = Pattern.compile(
            "(?i)<a\\s+[^>]*href\\s*=\\s*[\"']https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?[\"'][^>]*>\\s*@?([^<]+?)\\s*</a>");
    private static final Pattern MENTION_BARE_PATTERN = Pattern.compile(
            "(?i)(?<!\\[)@([\\w\\u4e00-\\u9fff.-]{1,32})\\s*\\(\\s*https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?\\s*\\)");
    private static final Pattern OVOB_PATTERN = Pattern.compile("(?i)\\b(ov|ob)(\\d+)\\b");
    private static final int LINK_COLOR = 0xFFFFCC00;
    private static final int RICH_SPAN_FLAGS =
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_PRIORITY;

    private Context context;
    private List<CommentFragment.CommentItem> list;
    private long mAid;
    private String mBvid;
    private CommentFragment mFragment;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private java.util.Set<String> loadingUrls = new java.util.HashSet<String>();

    private long mMid;
    private long mBid;
    private boolean mIsBlog;
    private int mReplyType = 1;

    public void setMid(long mid) { mMid = mid; }
    public void setBid(long bid) { mBid = bid; }
    public void setIsBlog(boolean isBlog) { mIsBlog = isBlog; }
    public void setReplyType(int type) { mReplyType = type; }
    public void setBvid(String bvid) { mBvid = bvid; }

    private long getOid() {
        return mIsBlog && mBid > 0 ? mBid : mAid;
    }

    private boolean isScrolling = false;
    private float mDensity;

    // 键盘光标选中的项，-1 表示无选中（触屏不高亮）
    private int selectedPosition = -1;

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public interface OnUserClickListener {
        void onUserClick(long mid, String userName);
    }
    private OnUserClickListener userClickListener;

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    public interface OnReplyClickListener {
        void onReplyClick(CommentFragment.CommentItem comment, CommentFragment.ReplyItem reply);
    }

    private OnReplyClickListener replyClickListener;

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public interface OnLikeListener {
        void onLikeSuccess();
    }
    private OnLikeListener likeListener;

    public void setOnLikeListener(OnLikeListener listener) {
        this.likeListener = listener;
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private int getConfiguredThreadCount() {
        return cn.ottohub.oh2013.util.SdkHelper.getImageLoadThreads();
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = getConfiguredThreadCount();
        if (threadCount <= 1) {
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    public CommentAdapter(Context context, List<CommentFragment.CommentItem> list, long aid, CommentFragment fragment) {
        this.context = context;
        this.list = list;
        this.mAid = aid;
        this.mFragment = fragment;
        this.mMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        mDensity = context.getResources().getDisplayMetrics().density;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
        if (!scrolling) {
            notifyDataSetChanged();
        }
    }

    public void reloadExecutor() {
        initExecutor();
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
            holder.userNameView = (TextView) convertView.findViewById(R.id.user_name);
            holder.message = (TextView) convertView.findViewById(R.id.message);
            holder.likeIcon = (ImageView) convertView.findViewById(R.id.like_icon);
            holder.likeCount = (TextView) convertView.findViewById(R.id.like_count);
            holder.replyButton = (TextView) convertView.findViewById(R.id.reply_button);
            holder.btnDelete = (TextView) convertView.findViewById(R.id.btn_delete);
            holder.time = (TextView) convertView.findViewById(R.id.time);
            holder.repliesContainer = (LinearLayout) convertView.findViewById(R.id.replies_container);
            holder.repliesText = (TextView) convertView.findViewById(R.id.replies_text);
            holder.repliesMore = (TextView) convertView.findViewById(R.id.replies_more);
            holder.pictureContainer = (LinearLayout) convertView.findViewById(R.id.picture_container);
            convertView.setTag(holder);
            convertView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final ViewHolder h = (ViewHolder) v.getTag();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 不写死橙色，避免 UP/CANCEL 丢失后第一条永久橙色
                            h.copyText = ((TextView) h.message).getText().toString();
                            h.downRpid = h.rpid;
                            Log.e("CommentClick", "DOWN rpid=" + h.rpid + " downRpid=" + h.downRpid);
                            h.longPressFired = false;
                            h.longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    h.longPressFired = true;
                                    final long itemMid = h.mid;
                                    final String text = h.copyText;
                                    final boolean mine = h.isMine || (itemMid == mMid && mMid != 0);
                                    if (mine) {
                                        final long oid = h.oid;
                                        final long rpid = h.rpid;
                                        AlertDialog.Builder builder = new AlertDialog.Builder(DialogUtil.wrap(context));
                                        builder.setItems(new String[]{"复制评论", "删除评论"}, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (which == 0) {
                                                    copyToClipboard(text);
                                                } else if (which == 1) {
                                                    deleteComment(oid, rpid);
                                                }
                                            }
                                        });
                                        builder.show();
                                    } else {
                                        copyToClipboard(text);
                                    }
                                }
                            };
                            mainHandler.postDelayed(h.longPressRunnable, ViewConfiguration.getLongPressTimeout());
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (h.longPressRunnable != null) {
                                mainHandler.removeCallbacks(h.longPressRunnable);
                            }
                            // 触屏结束：恢复白底。若是键盘光标选中项则保留粉色高亮
                            if (h.boundPosition == selectedPosition && selectedPosition >= 0) {
                                v.setBackgroundColor(0x66FF8C00);
                            } else {
                                v.setBackgroundColor(0xFFFFFFFF);
                            }
                            break;
                    }
                    return false;
                }
            });
        } else {
            holder = (ViewHolder) convertView.getTag();
            convertView.setBackgroundColor(0xFFFFFFFF);
        }

        holder.boundPosition = position;

        // 键盘光标高亮（选中：半透明粉色；未选中：纯白，避免 pressed 残留橙色）
        if (selectedPosition >= 0 && position == selectedPosition) {
            convertView.setBackgroundColor(0x66FF8C00);
        } else {
            convertView.setBackgroundColor(0xFFFFFFFF);
        }

        final CommentFragment.CommentItem item = list.get(position);
        holder.copyText = item.message;
        holder.userNameView.setText(item.userName);
        String msgText = item.message != null ? item.message : "";
        if (item.isTop && msgText.startsWith("[置顶]")) {
            msgText = msgText.substring(4);
        }
        // setTextColor BEFORE setText so span colors are not wiped on some devices
        holder.message.setTextColor(0xFF333333);
        holder.message.setLinkTextColor(LINK_COLOR);
        holder.message.setHighlightColor(0x00000000);
        CharSequence richMsg = formatCommentRichText(context, msgText);
        if (item.isTop) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append("\u200B");
            ssb.setSpan(new BadgeSpan(mDensity), 0, 1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(richMsg);
            holder.message.setText(ssb, TextView.BufferType.SPANNABLE);
        } else {
            holder.message.setText(richMsg, TextView.BufferType.SPANNABLE);
        }
        holder.message.setMovementMethod(LinkMovementMethod.getInstance());
        holder.message.setLinksClickable(true);
        holder.message.setFocusable(false);
        holder.message.setLongClickable(false);
        // ListView 内 ClickableSpan 需转发触摸
        holder.message.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                TextView tv = (TextView) v;
                CharSequence text = tv.getText();
                if (text instanceof Spannable) {
                    return LinkMovementMethod.getInstance()
                            .onTouchEvent(tv, (Spannable) text, event);
                }
                return false;
            }
        });
        holder.mid = item.mid;
        // 每次绑定刷新本人 mid，避免登录态变化后删除键不显示
        mMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        holder.isMine = item.isMine || (item.mid != 0 && item.mid == mMid && mMid != 0);
        holder.oid = getOid();
        holder.rpid = item.rpid;
        Log.e("CommentClick", "bind pos=" + position + " rpid=" + item.rpid + " msg=" + (item.message != null ? item.message.substring(0, Math.min(20, item.message.length())) : "null"));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewHolder h = (ViewHolder) v.getTag();
                long clickedRpid = h.rpid > 0 ? h.rpid : h.downRpid;
                Log.e("CommentClick", "click rpid=" + clickedRpid);
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).rpid == clickedRpid) {
                        CommentFragment.CommentItem ci = list.get(i);
                        Intent intent = new Intent(v.getContext(), ReplyListActivity.class);
                        intent.putExtra("aid", mAid);
                        intent.putExtra("bid", mBid);
                        if (mBvid != null) intent.putExtra("bvid", mBvid);
                        intent.putExtra("rpid", ci.rpid);
                        if (ci.userName != null) intent.putExtra("root_user_name", ci.userName);
                        if (ci.message != null) intent.putExtra("root_comment_message", ci.message);
                        intent.putExtra("root_mid", ci.mid);
                        intent.putExtra("root_time", ci.time);
                        if (ci.userAvatar != null) intent.putExtra("root_avatar", ci.userAvatar);
                        if (ci.pictureList != null && ci.pictureList.size() > 0) {
                            intent.putExtra("root_pictures", new ArrayList<String>(ci.pictureList));
                        }
                        intent.putExtra("total_count", ci.replyCount);
                        intent.putExtra("root_like_count", ci.likeCount);
                        intent.putExtra("root_liked", ci.liked);
                        intent.putExtra("root_is_top", ci.isTop);
                        intent.putExtra("root_is_mine", ci.isMine);
                        v.getContext().startActivity(intent);
                        break;
                    }
                }
            }
        });

        if (holder.likeIcon != null) {
            holder.likeIcon.setVisibility(View.GONE);
        }
        if (holder.likeCount != null) {
            holder.likeCount.setVisibility(View.GONE);
        }
        /*
        holder.likeCount.setText(String.valueOf(item.likeCount));
        final ViewHolder h2 = holder;
        if (h2.likeIcon != null) {
            if (item.liked) {
                h2.likeCount.setTextColor(0xFFFF8C00);
                h2.likeIcon.setColorFilter(0xFFFF8C00, android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else {
                h2.likeCount.setTextColor(0xFF999999);
                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
            }
            h2.likeIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int pos = list.indexOf(item);
                    if (pos < 0) return;
                    final boolean wasLiked = item.liked;
                    item.liked = !wasLiked;
                    if (item.liked) {
                        item.likeCount++;
                        h2.likeCount.setTextColor(0xFFFF8C00);
                        h2.likeIcon.setColorFilter(0xFFFF8C00, android.graphics.PorterDuff.Mode.SRC_ATOP);
                    } else {
                        item.likeCount--;
                        h2.likeCount.setTextColor(0xFF999999);
                        h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                    }
                    h2.likeCount.setText(String.valueOf(item.likeCount));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int code;
                                if (item.liked) {
                                    code = ReplyApi.likeComment(mAid, item.rpid, mReplyType);
                                } else {
                                    code = ReplyApi.unlikeComment(mAid, item.rpid, mReplyType);
                                }
                                if (code != 0) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            item.liked = wasLiked;
                                            if (wasLiked) {
                                                item.likeCount++;
                                                h2.likeCount.setTextColor(0xFFFF8C00);
                                                h2.likeIcon.setColorFilter(0xFFFF8C00, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                            } else {
                                                item.likeCount--;
                                                h2.likeCount.setTextColor(0xFF999999);
                                                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                            }
                                            h2.likeCount.setText(String.valueOf(item.likeCount));
                                            Toast.makeText(context, context.getString(R.string.commentadapter_toast_64cd), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } else {
                                    if (likeListener != null) {
                                        likeListener.onLikeSuccess();
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        item.liked = wasLiked;
                                        if (wasLiked) {
                                            item.likeCount++;
                                            h2.likeCount.setTextColor(0xFFFF8C00);
                                            h2.likeIcon.setColorFilter(0xFFFF8C00, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                        } else {
                                            item.likeCount--;
                                            h2.likeCount.setTextColor(0xFF999999);
                                            h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                        }
                                        h2.likeCount.setText(String.valueOf(item.likeCount));
                                        Toast.makeText(context, context.getString(R.string.commentadapter_toast_7f51), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });
        }
        */

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        holder.time.setText(sdf.format(new Date(item.time * 1000)));
        holder.avatar.setImageResource(R.drawable.bili_default_avatar);
        addAvatarBorder(holder.avatar);

        // 显示图片
        if (holder.pictureContainer != null) {
            if (item.pictureList != null && item.pictureList.size() > 0) {
                holder.pictureContainer.removeAllViews();
                holder.pictureContainer.setVisibility(View.VISIBLE);

                // 限制最多显示3张
                int maxShow = Math.min(item.pictureList.size(), 3);
                for (int i = 0; i < maxShow; i++) {
                    final String imgUrl = item.pictureList.get(i);
                    final int clickIndex = i;
                    ImageView imgView = new ImageView(context);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                    holder.pictureContainer.addView(imgView);
                    loadCommentImage(imgView, imgUrl);
                    imgView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putStringArrayListExtra("imageList", new ArrayList<String>(item.pictureList));
                            intent.putExtra("index", clickIndex);
                            context.startActivity(intent);
                        }
                    });
                }

                if (item.pictureList.size() > 3) {
                    TextView moreTv = new TextView(context);
                    moreTv.setText("+" + (item.pictureList.size() - 3));
                    moreTv.setTextSize(14);
                    moreTv.setTextColor(0xFFFFFFFF);
                    moreTv.setGravity(Gravity.CENTER);
                    moreTv.setBackgroundColor(0x88000000);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    moreTv.setLayoutParams(lp);
                    holder.pictureContainer.addView(moreTv);
                    moreTv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putStringArrayListExtra("imageList", new ArrayList<String>(item.pictureList));
                            intent.putExtra("index", 3);
                            context.startActivity(intent);
                        }
                    });
                }
            } else {
                holder.pictureContainer.setVisibility(View.GONE);
            }
        }

        List<CommentFragment.ReplyItem> replies = item.replies;
        int totalReplyCount = item.replyCount;

        // 「共x条回复」放在「回复」按钮旁，一行文字，不再用灰底方块
        if (holder.repliesMore != null) {
            if (totalReplyCount > 0) {
                holder.repliesMore.setVisibility(View.VISIBLE);
                holder.repliesMore.setText("共" + totalReplyCount + "条回复");
                final CommentFragment.CommentItem finalItemMore = item;
                final int finalTotalMore = totalReplyCount;
                holder.repliesMore.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mFragment != null) {
                            mFragment.showAllReplies(finalItemMore);
                        } else {
                            openReplyList(finalItemMore, finalTotalMore);
                        }
                    }
                });
            } else {
                holder.repliesMore.setVisibility(View.GONE);
                holder.repliesMore.setOnClickListener(null);
            }
        }

        if (replies != null && replies.size() > 0) {
            holder.repliesContainer.setVisibility(View.VISIBLE);
            holder.repliesText.setVisibility(View.VISIBLE);

            int showCount = Math.min(replies.size(), 2);
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            for (int i = 0; i < showCount; i++) {
                CommentFragment.ReplyItem ri = replies.get(i);
                if (i > 0) ssb.append("\n");
                int start = ssb.length();
                String uname = ri.userName != null ? ri.userName : "";
                ssb.append(uname);
                ssb.append(": ");
                final long mid = ri.mid;
                final CommentFragment.ReplyItem finalRi = ri;
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        if (mid != 0) {
                            Intent intent = new Intent(context, UserProfileActivity.class);
                            intent.putExtra("mid", mid);
                            context.startActivity(intent);
                        } else {
                            Toast.makeText(context, context.getString(R.string.commentadapter_toast_65e0), Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void updateDrawState(TextPaint ds) {
                        ds.setColor(0xFF666666);
                        ds.setUnderlineText(false);
                    }
                }, start, start + uname.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int messageStart = ssb.length();
                String replyText = ri.message != null ? ri.message : "";
                CharSequence richReply = formatCommentRichText(context, replyText);
                ssb.append(richReply);
                if (replyClickListener != null && replyText.length() > 0) {
                    ssb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            replyClickListener.onReplyClick(item, finalRi);
                        }
                        @Override
                        public void updateDrawState(TextPaint ds) {
                            // 不改颜色，避免覆盖 @提及 / OV/OB 的黄色
                            ds.setUnderlineText(false);
                        }
                    }, messageStart, messageStart + richReply.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            holder.repliesText.setText(ssb, TextView.BufferType.SPANNABLE);
            holder.repliesText.setLinkTextColor(LINK_COLOR);
            holder.repliesText.setHighlightColor(0x00000000);
            holder.repliesText.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            holder.repliesContainer.setVisibility(View.GONE);
            holder.repliesText.setVisibility(View.GONE);
        }

        if (holder.replyButton != null) {
            holder.replyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (replyClickListener != null) {
                        replyClickListener.onReplyClick(item, null);
                    }
                }
            });
        }

        if (holder.btnDelete != null) {
            if (holder.isMine) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setText("删除");
                holder.btnDelete.setTextColor(0xFFE53935);
                holder.btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(DialogUtil.wrap(context))
                                .setMessage("确定删除这条评论？")
                                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteComment(getOid(), item.rpid);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            } else {
                holder.btnDelete.setVisibility(View.GONE);
                holder.btnDelete.setOnClickListener(null);
            }
        }

        final long mid = item.mid;
        final String userName = item.userName;

        View.OnClickListener userClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mid != 0) {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, context.getString(R.string.commentadapter_toast_65e0), Toast.LENGTH_SHORT).show();
                }
            }
        };

        holder.avatar.setOnClickListener(userClickListener);
        holder.userNameView.setOnClickListener(userClickListener);

        if (item.userAvatar != null && item.userAvatar.length() > 0) {
            float density = context.getResources().getDisplayMetrics().density;
            int avSize = (int) (40 * density + 0.5f);
            cn.ottohub.oh2013.util.CoverImageLoader.loadIntoCircle(
                    context, holder.avatar, item.userAvatar, avSize);
            addAvatarBorder(holder.avatar);
        }

        return convertView;
    }

    private void loadCommentImage(final ImageView imageView, String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;
        if (urlStr == null || urlStr.length() == 0) return;

        Bitmap cached = GlobalImageCache.getInstance().get(urlStr);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        synchronized (loadingUrls) {
            if (loadingUrls.contains(urlStr)) return;
            loadingUrls.add(urlStr);
        }

        final String finalUrl = urlStr;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                java.io.File tempFile = null;
                try {
                    conn = NetWorkUtil.openCompat(finalUrl);
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setRequestProperty("Accept-Encoding", "identity");
                    conn.connect();

                    tempFile = new java.io.File(context.getCacheDir(), "cmt_" + finalUrl.hashCode() + ".tmp");
                    InputStream is = conn.getInputStream();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    is.close();
                    fos.close();

                    if (!tempFile.exists() || tempFile.length() == 0) return;

                    int targetSize = dpToPx(80);
                    Bitmap bitmap = GlobalImageCache.decodeFileSafely(tempFile, targetSize, targetSize, 2);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        final Bitmap resultBitmap = bitmap;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(resultBitmap);
                            }
                        });
                    }
                } catch (OutOfMemoryError e) {
                    GlobalImageCache.getInstance().freeAllUnreferenced();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    synchronized (loadingUrls) {
                        loadingUrls.remove(finalUrl);
                    }
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception e) {}
                    }
                    if (tempFile != null && tempFile.exists()) {
                        try { tempFile.delete(); } catch (Exception e) {}
                    }
                }
            }
        });
    }

    private void addAvatarBorder(ImageView imageView) {
        // no-op: circular avatars via CoverImageLoader.loadIntoCircle
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private Bitmap downloadImage(String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        HttpURLConnection conn = null;
        java.io.File tempFile = null;
        try {
            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.connect();

            tempFile = new java.io.File(context.getCacheDir(), "cmt_" + urlStr.hashCode() + ".tmp");
            InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            int targetSize = dpToPx(48);
            return GlobalImageCache.decodeFileSafely(tempFile, targetSize, targetSize, 2);
        } catch (OutOfMemoryError e) {
            GlobalImageCache.getInstance().freeAllUnreferenced();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) {}
            }
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception e) {}
            }
        }
    }

    public void updateData(List<CommentFragment.CommentItem> newList) {
        this.list = newList;
        loadingMap.clear();
        notifyDataSetChanged();
    }

    public void clearCache() {
        loadingMap.clear();
        loadingUrls.clear();
    }

    private void copyToClipboard(String text) {
        if (text != null && text.length() > 0) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.vibrate(50);
            } catch (Exception e) { e.printStackTrace(); }
            Toast.makeText(context, context.getString(R.string.commentadapter_toast_5df2_1), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteComment(final long oid, final long rpid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int code = ReplyApi.deleteComment(oid, rpid, mReplyType);
                    if (code == 0) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                int pos = -1;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i).rpid == rpid) {
                                        pos = i;
                                        break;
                                    }
                                }
                                if (pos >= 0) {
                                    list.remove(pos);
                                    notifyDataSetChanged();
                                }
                                Toast.makeText(context, context.getString(R.string.commentadapter_toast_5df2), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, context.getString(R.string.commentadapter_toast_5220), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 渲染 OttoHub @提及与 OV/OB 自动链接为可点击黄色文本。
     * 识别：[@用户名](https://www.ottohub.cn/u/UID)
     */
    public static CharSequence formatCommentRichText(Context context, String raw) {
        if (raw == null || raw.length() == 0) {
            return "";
        }
        // 先把 HTML 提及转成 markdown 形态，统一后续匹配
        String normalized = MENTION_HTML_PATTERN.matcher(raw).replaceAll(
                "[@$2](https://www.ottohub.cn/u/$1)");
        normalized = MENTION_BARE_PATTERN.matcher(normalized).replaceAll(
                "[@$1](https://www.ottohub.cn/u/$2)");
        final Context ctx = context;
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Matcher mentionMatcher = MENTION_PATTERN.matcher(normalized);
        int last = 0;
        while (mentionMatcher.find()) {
            if (mentionMatcher.start() > last) {
                ssb.append(normalized.substring(last, mentionMatcher.start()));
            }
            String username = mentionMatcher.group(1);
            if (username != null) {
                username = username.trim();
                if (username.startsWith("@")) {
                    username = username.substring(1);
                }
            }
            long midVal = 0;
            try {
                midVal = Long.parseLong(mentionMatcher.group(2));
            } catch (NumberFormatException e) {
                midVal = 0;
            }
            final long mid = midVal;
            int start = ssb.length();
            ssb.append("@");
            ssb.append(username != null ? username : "");
            int end = ssb.length();
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (mid <= 0) return;
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    ctx.startActivity(intent);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(LINK_COLOR);
                    ds.setUnderlineText(false);
                }
            }, start, end, RICH_SPAN_FLAGS);
            ssb.setSpan(new ForegroundColorSpan(LINK_COLOR), start, end, RICH_SPAN_FLAGS);
            last = mentionMatcher.end();
        }
        if (last < normalized.length()) {
            ssb.append(normalized.substring(last));
        }

        String plain = ssb.toString();
        Matcher ovobMatcher = OVOB_PATTERN.matcher(plain);
        ArrayList<int[]> matches = new ArrayList<int[]>();
        ArrayList<String> kinds = new ArrayList<String>();
        ArrayList<Long> ids = new ArrayList<Long>();
        while (ovobMatcher.find()) {
            ClickableSpan[] existing = ssb.getSpans(ovobMatcher.start(), ovobMatcher.end(), ClickableSpan.class);
            if (existing != null && existing.length > 0) {
                continue;
            }
            long idVal = 0;
            try {
                idVal = Long.parseLong(ovobMatcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            if (idVal <= 0) continue;
            matches.add(new int[]{ovobMatcher.start(), ovobMatcher.end()});
            kinds.add(ovobMatcher.group(1).toLowerCase(Locale.US));
            ids.add(Long.valueOf(idVal));
        }
        for (int i = 0; i < matches.size(); i++) {
            final String kind = kinds.get(i);
            final long targetId = ids.get(i).longValue();
            int start = matches.get(i)[0];
            int end = matches.get(i)[1];
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if ("ob".equals(kind)) {
                        Intent intent = new Intent(ctx, BlogDetailActivity.class);
                        intent.putExtra("bid", targetId);
                        ctx.startActivity(intent);
                    } else {
                        Intent intent = new Intent(ctx, VideoDetailActivity.class);
                        intent.putExtra("aid", targetId);
                        ctx.startActivity(intent);
                    }
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(LINK_COLOR);
                    ds.setUnderlineText(false);
                }
            }, start, end, RICH_SPAN_FLAGS);
            ssb.setSpan(new ForegroundColorSpan(LINK_COLOR), start, end, RICH_SPAN_FLAGS);
        }
        return ssb;
    }

    private void openReplyList(CommentFragment.CommentItem item, int totalCount) {
        Intent intent = new Intent(context, ReplyListActivity.class);
        intent.putExtra("aid", mAid);
        intent.putExtra("bid", mBid);
        if (mBvid != null) intent.putExtra("bvid", mBvid);
        intent.putExtra("rpid", item.rpid);
        if (item.userName != null) intent.putExtra("root_user_name", item.userName);
        if (item.message != null) intent.putExtra("root_comment_message", item.message);
        intent.putExtra("root_mid", item.mid);
        intent.putExtra("root_time", item.time);
        if (item.userAvatar != null) intent.putExtra("root_avatar", item.userAvatar);
        if (item.pictureList != null && item.pictureList.size() > 0) {
            intent.putExtra("root_pictures", new ArrayList<String>(item.pictureList));
        }
        intent.putExtra("total_count", totalCount);
        intent.putExtra("root_like_count", item.likeCount);
        intent.putExtra("root_liked", item.liked);
        intent.putExtra("root_is_top", item.isTop);
        intent.putExtra("root_is_mine", item.isMine);
        context.startActivity(intent);
    }

    static class ViewHolder {
        ImageView avatar;
        TextView userNameView;
        TextView message;
        ImageView likeIcon;
        TextView likeCount;
        TextView replyButton;
        TextView btnDelete;
        TextView time;
        LinearLayout repliesContainer;
        TextView repliesText;
        TextView repliesMore;
        LinearLayout pictureContainer;
        String copyText;
        Runnable longPressRunnable;
        boolean longPressFired;
        long mid;
        boolean isMine;
        long oid;
        long rpid;
        long downRpid;
        int boundPosition = -1;
    }

    private static class BadgeSpan extends android.text.style.ReplacementSpan {
        private int mWidth;
        private int mPaddingPx;
        private int mGapPx;
        private int mCornerPx;
        private float mStrokePx;
        private int mTextMarginPx;  // 标签和文字之间的间距
        private static final String TEXT = "置顶";

        BadgeSpan(float density) {
            mPaddingPx = (int)(4 * density + 0.5f);
            mGapPx = (int)(1 * density + 0.5f);
            mCornerPx = (int)(2 * density + 0.5f);
            mStrokePx = 1 * density + 0.5f;
            mTextMarginPx = (int)(1 * density + 0.5f);  // 标签右边和评论文字的间距
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float textWidth = paint.measureText(TEXT);
            // 总宽度 = 标签宽度 + 左边距 + 标签和文字间距
            mWidth = (int)(textWidth + mPaddingPx * 2 + mStrokePx * 2 + mGapPx + mTextMarginPx);
            if (fm != null) {
                android.graphics.Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                fm.ascent = pfm.ascent - mPaddingPx;
                fm.descent = pfm.descent + mPaddingPx;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return mWidth;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            int origColor = paint.getColor();
            android.graphics.Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();
            boolean origAntiAlias = paint.isAntiAlias();

            android.graphics.Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            float half = mStrokePx / 2f;
            float rectTop = y + fm.ascent - mPaddingPx + half;
            float rectBottom = y + fm.descent + mPaddingPx - half;

            float textWidth = paint.measureText(TEXT);
            float rectWidth = textWidth + mPaddingPx * 2;

            // mGapPx 作为左边距偏移（只在 draw 里用）
            float offsetX = mGapPx;

            android.graphics.RectF rect = new android.graphics.RectF(
                    x + offsetX,
                    rectTop,
                    x + offsetX + rectWidth,
                    rectBottom
            );

            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(mStrokePx);
            paint.setColor(0xFFFF8C00);
            paint.setAntiAlias(true);
            canvas.drawRoundRect(rect, mCornerPx, mCornerPx, paint);

            paint.setColor(0xFFFF8C00);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            float centerY = (rectTop + rectBottom) / 2 - (fm.ascent + fm.descent) / 2;
            canvas.drawText(TEXT, x + offsetX + mPaddingPx, centerY, paint);

            paint.setColor(origColor);
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);
            paint.setAntiAlias(origAntiAlias);
        }
    }
}