package cn.ottohub.oh2013;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import cn.ottohub.oh2013.api.BlogApi;
import cn.ottohub.oh2013.api.OttoAuthApi;
import cn.ottohub.oh2013.api.OttoApiUtil;
import cn.ottohub.oh2013.model.BlogItem;
import cn.ottohub.oh2013.util.CoverImageLoader;
import cn.ottohub.oh2013.util.DialogUtil;
import cn.ottohub.oh2013.util.MarkdownContentHelper;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * 动态详情：作者方头像 / OID、发布时间、OB号、内容接受度提示、Markdown 正文、点赞/收藏/评论。
 */
public class BlogDetailActivity extends BaseActivity {

    private long bid;
    private long uid;
    private int isGore;
    private String publishTime;
    private TextView tvTitle;
    private TextView tvUsername;
    private TextView tvOid;
    private TextView tvMeta;
    private TextView tvGoreTip;
    private ImageView ivAvatar;
    private LinearLayout contentContainer;
    private ProgressBar progressBar;
    private Button btnComment;
    private TextView btnLike;
    private TextView btnFavorite;
    private boolean ifLike;
    private boolean ifFavorite;
    private int likeCount;
    private int favoriteCount;
    private boolean likeBusy;
    private boolean favoriteBusy;
    private boolean deleteBusy;
    private TextView btnDelete;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_detail);
        initRoundTitleBar();

        bid = getIntent().getLongExtra("bid", 0);
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        String user = getIntent().getStringExtra("username");
        String avatar = getIntent().getStringExtra("avatar_url");
        uid = getIntent().getLongExtra("uid", 0);
        publishTime = getIntent().getStringExtra("time");
        isGore = getIntent().getIntExtra("is_gore", 0);

        tvTitle = (TextView) findViewById(R.id.blog_title);
        tvUsername = (TextView) findViewById(R.id.blog_username);
        tvOid = (TextView) findViewById(R.id.blog_oid);
        tvMeta = (TextView) findViewById(R.id.blog_meta);
        tvGoreTip = (TextView) findViewById(R.id.blog_gore_tip);
        ivAvatar = (ImageView) findViewById(R.id.blog_avatar);
        contentContainer = (LinearLayout) findViewById(R.id.blog_content_container);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        btnComment = (Button) findViewById(R.id.btn_comment);
        btnLike = (TextView) findViewById(R.id.btn_like);
        btnFavorite = (TextView) findViewById(R.id.btn_favorite);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btnDelete = (TextView) findViewById(R.id.btn_blog_delete);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete();
                }
            });
        }
        updateDeleteVisibility();

        final long finalUid = uid;
        View.OnClickListener openProfile = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (finalUid > 0) {
                    Intent intent = new Intent(BlogDetailActivity.this, UserProfileActivity.class);
                    intent.putExtra("mid", finalUid);
                    startActivity(intent);
                }
            }
        };
        if (ivAvatar != null) ivAvatar.setOnClickListener(openProfile);
        if (tvUsername != null) tvUsername.setOnClickListener(openProfile);

        btnComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bid <= 0) {
                    Toast.makeText(BlogDetailActivity.this, "无效动态", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(BlogDetailActivity.this, BlogCommentActivity.class);
                intent.putExtra("bid", bid);
                startActivity(intent);
            }
        });

        if (btnLike != null) {
            btnLike.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleLike();
                }
            });
        }
        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFavorite();
                }
            });
        }
        updateLikeFavoriteUi();

        bindHeader(title, user, avatar, uid, publishTime, isGore);
        if (content != null && content.length() > 0) {
            MarkdownContentHelper.renderInto(this, contentContainer, content);
        }

        // 始终拉详情，保证全文完整（列表可能截断）
        if (bid > 0) {
            fetchRemote();
        }
    }

    private void bindHeader(String title, String user, String avatar, long userId,
                            String time, int gore) {
        if (tvTitle != null) {
            tvTitle.setText(title != null && title.length() > 0 ? title : ("ob" + bid));
        }
        if (tvUsername != null) {
            tvUsername.setText(user != null && user.length() > 0 ? user : "用户");
        }
        if (tvOid != null) {
            tvOid.setText(userId > 0 ? ("oid:" + userId) : "");
        }
        if (tvMeta != null) {
            StringBuilder meta = new StringBuilder();
            if (time != null && time.length() > 0) {
                meta.append(time);
            }
            if (bid > 0) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append("ob").append(bid);
            }
            // 8+（is_gore=0）不显示内容接受度；4000+（is_gore=1）在 tip 中提示
            if (gore == 1) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append("4000+");
            }
            tvMeta.setText(meta.toString());
            tvMeta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);
        }
        if (tvGoreTip != null) {
            // 仅 4000+ 显示低内容接受度提示；8+ 不显示
            tvGoreTip.setVisibility(gore == 1 ? View.VISIBLE : View.GONE);
        }
        if (ivAvatar != null && avatar != null && avatar.length() > 0) {
            int size = (int) (getResources().getDisplayMetrics().density * 48);
            CoverImageLoader.loadIntoCircle(this, ivAvatar, avatar, size);
        }
    }

    private void applyLikeFavoriteFromBlog(BlogItem blog) {
        if (blog == null) return;
        ifLike = blog.ifLike;
        ifFavorite = blog.ifFavorite;
        likeCount = blog.likeCount;
        favoriteCount = blog.favoriteCount;
        updateLikeFavoriteUi();
    }

    private void updateLikeFavoriteUi() {
        if (btnLike != null) {
            String likeLabel = likeCount > 0 ? ("点赞 " + likeCount) : "点赞";
            btnLike.setText(likeLabel);
            btnLike.setTextColor(ifLike ? 0xFFFFD700 : 0xFFFF8C00);
        }
        if (btnFavorite != null) {
            String favLabel = favoriteCount > 0 ? ("收藏 " + favoriteCount) : "收藏";
            btnFavorite.setText(favLabel);
            btnFavorite.setTextColor(ifFavorite ? 0xFFFFD700 : 0xFFFF8C00);
        }
    }

    private void toggleLike() {
        if (bid <= 0) {
            Toast.makeText(this, "无效动态", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (likeBusy) return;
        likeBusy = true;
        final boolean wasLike = ifLike;
        final int wasCount = likeCount;
        // 乐观更新
        ifLike = !wasLike;
        likeCount = Math.max(0, wasCount + (ifLike ? 1 : -1));
        updateLikeFavoriteUi();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject resp = BlogApi.toggleLike(bid);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            likeBusy = false;
                            if (resp == null || !OttoApiUtil.isSuccess(resp)) {
                                ifLike = wasLike;
                                likeCount = wasCount;
                                updateLikeFavoriteUi();
                                Toast.makeText(BlogDetailActivity.this, "点赞失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            JSONObject data = resp.optJSONObject("data");
                            if (data == null) data = resp;
                            if (data.has("if_like") || resp.has("if_like")) {
                                JSONObject src = data.has("if_like") ? data : resp;
                                ifLike = OttoApiUtil.parseInt(src, "if_like") == 1;
                            }
                            if (data.has("like_count") || resp.has("like_count")) {
                                JSONObject src = data.has("like_count") ? data : resp;
                                likeCount = OttoApiUtil.parseInt(src, "like_count");
                            }
                            updateLikeFavoriteUi();
                            Toast.makeText(BlogDetailActivity.this,
                                    ifLike ? "已点赞" : "已取消点赞", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            likeBusy = false;
                            ifLike = wasLike;
                            likeCount = wasCount;
                            updateLikeFavoriteUi();
                            Toast.makeText(BlogDetailActivity.this,
                                    "点赞失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void toggleFavorite() {
        if (bid <= 0) {
            Toast.makeText(this, "无效动态", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (favoriteBusy) return;
        favoriteBusy = true;
        final boolean wasFav = ifFavorite;
        final int wasCount = favoriteCount;
        ifFavorite = !wasFav;
        favoriteCount = Math.max(0, wasCount + (ifFavorite ? 1 : -1));
        updateLikeFavoriteUi();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject resp = BlogApi.toggleFavorite(bid);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            favoriteBusy = false;
                            if (resp == null || !OttoApiUtil.isSuccess(resp)) {
                                ifFavorite = wasFav;
                                favoriteCount = wasCount;
                                updateLikeFavoriteUi();
                                Toast.makeText(BlogDetailActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            JSONObject data = resp.optJSONObject("data");
                            if (data == null) data = resp;
                            if (data.has("if_favorite") || resp.has("if_favorite")) {
                                JSONObject src = data.has("if_favorite") ? data : resp;
                                ifFavorite = OttoApiUtil.parseInt(src, "if_favorite") == 1;
                            }
                            if (data.has("favorite_count") || resp.has("favorite_count")) {
                                JSONObject src = data.has("favorite_count") ? data : resp;
                                favoriteCount = OttoApiUtil.parseInt(src, "favorite_count");
                            }
                            updateLikeFavoriteUi();
                            Toast.makeText(BlogDetailActivity.this,
                                    ifFavorite ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            favoriteBusy = false;
                            ifFavorite = wasFav;
                            favoriteCount = wasCount;
                            updateLikeFavoriteUi();
                            Toast.makeText(BlogDetailActivity.this,
                                    "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void fetchRemote() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BlogItem blog = BlogApi.fetchDetail(bid);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (blog == null) {
                                Toast.makeText(BlogDetailActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            uid = blog.uid;
                            bid = blog.bid > 0 ? blog.bid : bid;
                            publishTime = blog.time;
                            isGore = blog.isGore;
                            applyLikeFavoriteFromBlog(blog);
                            bindHeader(blog.title, blog.username, blog.avatarUrl, blog.uid,
                                    blog.time, blog.isGore);
                            updateDeleteVisibility();
                            String body = blog.content != null ? blog.content : "";
                            MarkdownContentHelper.renderInto(BlogDetailActivity.this, contentContainer, body);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(BlogDetailActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void updateDeleteVisibility() {
        if (btnDelete == null) return;
        long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        boolean own = OttoAuthApi.isLoggedIn() && selfMid > 0 && uid > 0 && selfMid == uid;
        btnDelete.setVisibility(own ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete() {
        if (bid <= 0) {
            Toast.makeText(this, "无效动态", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle("删除动态")
                .setMessage("确定删除这条动态？删除后不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doDelete();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doDelete() {
        if (deleteBusy) return;
        deleteBusy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject resp = BlogApi.deleteBlog(bid);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            deleteBusy = false;
                            if (resp != null && OttoApiUtil.isSuccess(resp)) {
                                Toast.makeText(BlogDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(BlogDetailActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            deleteBusy = false;
                            Toast.makeText(BlogDetailActivity.this,
                                    "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
