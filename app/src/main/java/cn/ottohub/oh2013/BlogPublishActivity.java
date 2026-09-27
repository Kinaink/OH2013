package cn.ottohub.oh2013;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import org.json.JSONObject;

import cn.ottohub.oh2013.api.BlogApi;
import cn.ottohub.oh2013.api.OttoApiUtil;
import cn.ottohub.oh2013.api.OttoAuthApi;

/**
 * 发布动态：标题、内容、8+/4000+（is_gore）、发布。参考 blog_api.md submit。
 */
public class BlogPublishActivity extends BaseActivity {

    private EditText editTitle;
    private EditText editContent;
    private RadioButton gore8;
    private RadioButton gore4000;
    private Button btnPublish;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean publishing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_publish);
        initRoundTitleBar();

        if (!OttoAuthApi.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        editTitle = (EditText) findViewById(R.id.edit_title);
        editContent = (EditText) findViewById(R.id.edit_content);
        gore8 = (RadioButton) findViewById(R.id.gore_8);
        gore4000 = (RadioButton) findViewById(R.id.gore_4000);
        btnPublish = (Button) findViewById(R.id.btn_publish);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPublish();
            }
        });
    }

    private void doPublish() {
        if (publishing) return;
        final String title = editTitle.getText().toString().trim();
        final String content = editContent.getText().toString().trim();
        if (title.length() == 0) {
            Toast.makeText(this, "请填写标题", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() == 0) {
            Toast.makeText(this, "请填写内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.length() > 100) {
            Toast.makeText(this, "标题过长", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() > 10000) {
            Toast.makeText(this, "内容过长", Toast.LENGTH_SHORT).show();
            return;
        }
        final int isGore = (gore4000 != null && gore4000.isChecked()) ? 1 : 0;
        publishing = true;
        btnPublish.setEnabled(false);
        btnPublish.setText("发布中…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject resp = BlogApi.submitBlog(title, content, isGore);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            publishing = false;
                            btnPublish.setEnabled(true);
                            btnPublish.setText("发布");
                            if (resp != null && OttoApiUtil.isSuccess(resp)) {
                                int warn = resp.optInt("if_warn", 0);
                                Toast.makeText(BlogPublishActivity.this,
                                        warn == 1 ? "已提交（进入审核）" : "发布成功",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                String msg = resp != null
                                        ? resp.optString("message", "发布失败") : "发布失败";
                                Toast.makeText(BlogPublishActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            publishing = false;
                            btnPublish.setEnabled(true);
                            btnPublish.setText("发布");
                            Toast.makeText(BlogPublishActivity.this,
                                    "发布失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
