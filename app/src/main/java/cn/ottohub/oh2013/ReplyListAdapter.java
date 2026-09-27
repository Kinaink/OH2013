package cn.ottohub.oh2013;


import cn.ottohub.oh2013.util.NetWorkUtil;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ReplacementSpan;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cn.ottohub.oh2013.api.ReplyApi;
import cn.ottohub.oh2013.util.GlobalImageCache;
import cn.ottohub.oh2013.util.DialogUtil;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

public class ReplyListAdapter extends BaseAdapter {

    private Context context;
    private List<ReplyListActivity.ReplyData> list;
    private OnReplyClickListener replyClickListener;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private Set<String> loadingUrls = new HashSet<String>();
    private boolean isScrolling = false;
    private Set<Long> expandedRpids = new HashSet<Long>();
    private float mDensity;

    private long mOid;
    private int mReplyType = 1;
    private long mMid;

    // 键盘光标选中的项，-1 表示无选中（触屏不高亮）
    private int selectedPosition = -1;
    private boolean hideHighlight = true;

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public void setHideHighlight(boolean hide) {
        this.hideHighlight = hide;
        notifyDataSetChanged();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
        // 滚动时隐藏高亮；结束后保持隐藏，直到按键导航再次激活
        this.hideHighlight = true;
        if (scrolling) {
            this.selectedPosition = -1;
        }
    }

    public void setOid(long oid) { mOid = oid; }
    public void setReplyType(int type) { mReplyType = type; }
    public void setMid(long mid) { mMid = mid; }

    public interface OnReplyClickListener {
        void onReplyClick(ReplyListActivity.ReplyData reply);
    }

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public ReplyListAdapter(Context context, List<ReplyListActivity.ReplyData> list) {
        this.context = context;
        this.list = list;
        mDensity = context.getResources().getDisplayMetrics().density;
        initExecutor();
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
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
            holder.userName = (TextView) convertView.findViewById(R.id.user_name);
            holder.message = (TextView) convertView.findViewById(R.id.message);
            holder.time = (TextView) convertView.findViewById(R.id.time);
            holder.likeIcon = (ImageView) convertView.findViewById(R.id.like_icon);
            holder.likeCount = (TextView) convertView.findViewById(R.id.like_count);
            holder.replyButton = (TextView) convertView.findViewById(R.id.reply_button);
            holder.btnDelete = (TextView) convertView.findViewById(R.id.btn_delete);
            holder.pictureContainer = (LinearLayout) convertView.findViewById(R.id.picture_container);
            convertView.setTag(holder);
            convertView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final ViewHolder h = (ViewHolder) v.getTag();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 不写死橙色背景，避免 UP/CANCEL 丢失后第一条永久橙色
                            h.copyText = ((TextView) h.message).getText().toString();
                            h.longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    final long itemMid = h.mid;
                                    final String text = h.copyText;
                                    if (itemMid == mMid && mMid != 0) {
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
                            applyRowBackground(v, h.boundPosition);
                            break;
                    }
                    return false;
                }
            });
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.boundPosition = position;
        // 先强制白底，避免 ListView 焦点/selector 让第一条一直橙色
        convertView.setBackgroundColor(0xFFFFFFFF);
        final ReplyListActivity.ReplyData rd = list.get(position);
        holder.copyText = rd.message;
        holder.userName.setText(rd.userName != null ? rd.userName : "用户");
        String msgText = rd.message != null ? rd.message : "";
        if (rd.isTop && msgText.startsWith("[置顶]")) {
            msgText = msgText.substring(4);
        }
        holder.message.setTextColor(0xFF333333);
        holder.message.setLinkTextColor(0xFFFFCC00);
        holder.message.setHighlightColor(0x00000000);
        CharSequence richMsg = CommentAdapter.formatCommentRichText(context, msgText);
        if (rd.isTop) {
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
        holder.message.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                TextView tv = (TextView) v;
                CharSequence text = tv.getText();
                if (text instanceof android.text.Spannable) {
                    return LinkMovementMethod.getInstance()
                            .onTouchEvent(tv, (android.text.Spannable) text, event);
                }
                return false;
            }
        });
        holder.mid = rd.mid;
        holder.oid = mOid;
        holder.rpid = rd.rpid;
        mMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        boolean isMine = rd.isMine || (rd.mid != 0 && rd.mid == mMid && mMid != 0);

        if (rd.time > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            holder.time.setText(sdf.format(new Date(rd.time * 1000)));
        } else {
            holder.time.setText("刚刚");
        }

        // 点赞 UI 已移除（OTTOhub 无评论点赞）
        if (holder.likeCount != null) {
            holder.likeCount.setVisibility(View.GONE);
        }
        if (holder.likeIcon != null) {
            holder.likeIcon.setVisibility(View.GONE);
            holder.likeIcon.setOnClickListener(null);
        }

        //显示回复按钮
        if (holder.replyButton != null) {
            holder.replyButton.setVisibility(View.VISIBLE);
            holder.replyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (replyClickListener != null) {
                        replyClickListener.onReplyClick(rd);
                    }
                }
            });
        }

        if (holder.btnDelete != null) {
            if (isMine) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setTextColor(0xFFE53935);
                holder.btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(DialogUtil.wrap(context))
                                .setMessage("确定删除这条评论？")
                                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteComment(mOid, rd.rpid);
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

        // 显示图片
        if (holder.pictureContainer != null) {
            if (rd.pictureList != null && rd.pictureList.size() > 0) {
                holder.pictureContainer.removeAllViews();
                holder.pictureContainer.setVisibility(View.VISIBLE);

                boolean expanded = expandedRpids.contains(rd.rpid);
                int showCount = expanded ? Math.min(rd.pictureList.size(), 9) : Math.min(rd.pictureList.size(), 3);
                int imgSize = dpToPx(80);
                int margin = dpToPx(4);

                holder.pictureContainer.setOrientation(LinearLayout.VERTICAL);
                int cols = 3;
                for (int i = 0; i < showCount; i++) {
                    if (i % cols == 0) {
                        LinearLayout row = new LinearLayout(context);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        holder.pictureContainer.addView(row);
                    }
                    LinearLayout row = (LinearLayout) holder.pictureContainer.getChildAt(
                            holder.pictureContainer.getChildCount() - 1);

                    final String imgUrl = rd.pictureList.get(i);
                    final int clickIndex = i;
                    ImageView imgView = new ImageView(context);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(imgSize, imgSize);
                    lp.rightMargin = margin;
                    lp.bottomMargin = (i / cols < (showCount - 1) / cols) ? margin : 0;
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                    row.addView(imgView);
                    loadReplyImage(imgView, imgUrl);
                    imgView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putStringArrayListExtra("imageList", new ArrayList<String>(rd.pictureList));
                            intent.putExtra("index", clickIndex);
                            context.startActivity(intent);
                        }
                    });
                }

                if (rd.pictureList.size() > 3) {
                    View toggleView;
                    if (!expanded) {
                        TextView moreTv = new TextView(context);
                        moreTv.setText("+" + (rd.pictureList.size() - 3));
                        moreTv.setTextSize(14);
                        moreTv.setTextColor(0xFFFFFFFF);
                        moreTv.setGravity(Gravity.CENTER);
                        moreTv.setBackgroundColor(0x88000000);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(imgSize, imgSize);
                        lp.leftMargin = margin;
                        moreTv.setLayoutParams(lp);
                        toggleView = moreTv;
                    } else {
                        TextView collapseTv = new TextView(context);
                        collapseTv.setText("收起");
                        collapseTv.setTextSize(12);
                        collapseTv.setTextColor(0xFFFF8C00);
                        collapseTv.setGravity(Gravity.CENTER);
                        collapseTv.setPadding(0, dpToPx(4), 0, 0);
                        toggleView = collapseTv;
                    }
                    toggleView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (expandedRpids.contains(rd.rpid)) {
                                expandedRpids.remove(rd.rpid);
                            } else {
                                expandedRpids.add(rd.rpid);
                            }
                            notifyDataSetChanged();
                        }
                    });
                    holder.pictureContainer.addView(toggleView);
                }
            } else {
                holder.pictureContainer.setVisibility(View.GONE);
            }
        }

        holder.avatar.setImageResource(R.drawable.bili_default_avatar);
        try {
            holder.avatar.setBackgroundDrawable(null);
            holder.avatar.setPadding(0, 0, 0, 0);
        } catch (Throwable ignored) {
        }

        // 加载头像（CoverImageLoader 支持 GIF 圆形，无边框）
        if (rd.avatar != null && rd.avatar.length() > 0) {
            float density = context.getResources().getDisplayMetrics().density;
            int avSize = (int) (40 * density + 0.5f);
            cn.ottohub.oh2013.util.CoverImageLoader.loadIntoCircle(
                    context, holder.avatar, rd.avatar, avSize);
        }

        final long mid = rd.mid;
        if (mid != 0) {
            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    context.startActivity(intent);
                }
            };
            holder.avatar.setOnClickListener(clickListener);
            holder.userName.setOnClickListener(clickListener);
        }

        // 键盘光标高亮（仅 key-nav 选中；触屏默认 selectedPosition=-1）
        applyRowBackground(convertView, position);

        return convertView;
    }

    private void applyRowBackground(View v, int position) {
        if (v == null) return;
        if (!hideHighlight && selectedPosition >= 0 && position == selectedPosition) {
            v.setBackgroundColor(0x66FF8C00);
        } else {
            // 纯白，避免 selector pressed 状态粘在第一条上
            v.setBackgroundColor(0xFFFFFFFF);
        }
    }

    private void loadReplyImage(final ImageView imageView, String urlStr) {
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

                    tempFile = new java.io.File(context.getCacheDir(), "rpl_" + finalUrl.hashCode() + ".tmp");
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
                    System.gc();
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

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void copyToClipboard(String text) {
        if (text != null && text.length() > 0) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.vibrate(50);
            } catch (Exception e) { e.printStackTrace(); }
            Toast.makeText(context, context.getString(R.string.replylistadapter_toast_5df2_1), Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(context, context.getString(R.string.replylistadapter_toast_5df2), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, context.getString(R.string.replylistadapter_toast_5220), Toast.LENGTH_SHORT).show();
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

    public void updateData(List<ReplyListActivity.ReplyData> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void clearCache() {
        loadingMap.clear();
    }

    static class ViewHolder {
        ImageView avatar;
        TextView userName;
        TextView message;
        TextView time;
        ImageView likeIcon;
        TextView likeCount;
        TextView replyButton;
        TextView btnDelete;
        LinearLayout pictureContainer;
        String copyText;
        Runnable longPressRunnable;
        long mid;
        long oid;
        long rpid;
        int boundPosition = -1;
    }

    private static class BadgeSpan extends android.text.style.ReplacementSpan {
        private int mWidth;
        private int mPaddingPx;
        private int mGapPx;
        private int mCornerPx;
        private float mStrokePx;
        private static final String TEXT = "置顶";

        BadgeSpan(float density) {
            mPaddingPx = (int)(4 * density + 0.5f);
            mGapPx = (int)(4 * density + 0.5f);
            mCornerPx = (int)(2 * density + 0.5f);
            mStrokePx = 1 * density + 0.5f;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            mWidth = (int)(paint.measureText(TEXT) + mPaddingPx * 2 + mStrokePx * 2 + mGapPx);
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
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            int origColor = paint.getColor();
            android.graphics.Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();
            boolean origAntiAlias = paint.isAntiAlias();

            android.graphics.Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            float half = mStrokePx / 2f;
            float rectTop = y + pfm.ascent - mPaddingPx + half;
            float rectBottom = y + pfm.descent + mPaddingPx - half;

            android.graphics.RectF rect = new android.graphics.RectF(
                x + half, rectTop,
                x + mWidth - mGapPx - half, rectBottom
            );

            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(mStrokePx);
            paint.setColor(0xFFFF8C00);
            paint.setAntiAlias(true);
            canvas.drawRoundRect(rect, mCornerPx, mCornerPx, paint);

            paint.setColor(0xFFFF8C00);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawText(TEXT, x + mPaddingPx, y, paint);

            paint.setColor(origColor);
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);
            paint.setAntiAlias(origAntiAlias);
        }
    }
}