package cn.ottohub.oh2013;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.View;

/**
 * 动态评论页：嵌入 CommentFragment（bid）。
 */
public class BlogCommentActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_comment);
        initRoundTitleBar();

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        long bid = getIntent().getLongExtra("bid", 0);
        if (bid == 0) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            CommentFragment fragment = new CommentFragment();
            Bundle args = new Bundle();
            args.putLong("bid", bid);
            fragment.setArguments(args);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.comment_container, fragment, "blog_comment");
            ft.commit();
        }

        findViewById(R.id.btn_write_comment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommentFragment fragment = findCommentFragment();
                if (fragment != null) {
                    fragment.openNewCommentDialog();
                } else {
                    // Fragment 尚未就绪时稍后再试
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            CommentFragment f = findCommentFragment();
                            if (f != null) {
                                f.openNewCommentDialog();
                            }
                        }
                    });
                }
            }
        });
    }

    private CommentFragment findCommentFragment() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("blog_comment");
        if (f instanceof CommentFragment) {
            return (CommentFragment) f;
        }
        f = getSupportFragmentManager().findFragmentById(R.id.comment_container);
        if (f instanceof CommentFragment) {
            return (CommentFragment) f;
        }
        return null;
    }
}
