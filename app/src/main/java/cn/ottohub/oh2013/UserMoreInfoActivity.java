package cn.ottohub.oh2013;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import cn.ottohub.oh2013.api.OttoAuthApi;
import cn.ottohub.oh2013.api.UserInfoApi;
import cn.ottohub.oh2013.model.UserInfo;
import cn.ottohub.oh2013.util.SharedPreferencesUtil;

/**
 * 用户「更多」信息：等级、性别、头衔、注册时间、经验；底部关注/取消关注。
 */
public class UserMoreInfoActivity extends BaseActivity {

    private long mid;
    private TextView infoText;
    private Button btnFollow;
    private ProgressBar progressBar;
    private UserInfo userInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean followBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_more_info);
        initRoundTitleBar();

        mid = getIntent().getLongExtra("mid", 0);
        if (mid == 0) {
            Toast.makeText(this, "无效用户", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        infoText = (TextView) findViewById(R.id.more_info_text);
        btnFollow = (Button) findViewById(R.id.btn_follow);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnFollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFollow();
            }
        });

        long selfMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (selfMid == mid) {
            btnFollow.setVisibility(View.GONE);
        }

        loadUser();
    }

    private void loadUser() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo info = UserInfoApi.getUserInfo(mid);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (info == null) {
                                Toast.makeText(UserMoreInfoActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            userInfo = info;
                            bindInfo(info);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(UserMoreInfoActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void bindInfo(UserInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("等级: Lv.").append(info.level).append("\n");
        String sex = info.sex != null && info.sex.length() > 0 ? info.sex : "未知";
        sb.append("性别: ").append(sex).append("\n");

        String honourText = "无";
        if (info.honours != null && info.honours.size() > 0) {
            StringBuilder hb = new StringBuilder();
            for (int i = 0; i < info.honours.size(); i++) {
                if (i > 0) hb.append("、");
                hb.append(info.honours.get(i));
            }
            honourText = hb.toString();
        }
        sb.append("头衔: ").append(honourText).append("\n");

        String time = info.registerTime != null && info.registerTime.length() > 0
                ? info.registerTime : "未知";
        sb.append("注册时间: ").append(time).append("\n");
        sb.append("经验数: ").append(info.experience);

        if (infoText != null) {
            infoText.setText(sb.toString());
        }
        updateFollowButton(info.followed);
    }

    private void updateFollowButton(boolean followed) {
        if (btnFollow == null || btnFollow.getVisibility() != View.VISIBLE) return;
        btnFollow.setText(followed ? "取消关注" : "关注");
    }

    private void toggleFollow() {
        if (followBusy) return;
        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        followBusy = true;
        btnFollow.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int status = UserInfoApi.toggleFollow(mid, userInfo != null && userInfo.followed, true);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            followBusy = false;
                            btnFollow.setEnabled(true);
                            if (status < 0) {
                                Toast.makeText(UserMoreInfoActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            boolean followed = status == 1;
                            if (userInfo != null) userInfo.followed = followed;
                            updateFollowButton(followed);
                            Toast.makeText(UserMoreInfoActivity.this,
                                    followed ? "已关注" : "已取消关注", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            followBusy = false;
                            btnFollow.setEnabled(true);
                            Toast.makeText(UserMoreInfoActivity.this,
                                    "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
