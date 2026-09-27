package cn.ottohub.oh2013.widget;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * ViewPager that preferentially intercepts horizontal swipes so nested ListViews
 * do not steal tab-switch gestures.
 */
public class SwipeableViewPager extends ViewPager {

    private float startX;
    private float startY;
    private boolean deciding;
    private boolean horizontal;
    private int touchSlop;

    public SwipeableViewPager(Context context) {
        super(context);
        init(context);
    }

    public SwipeableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (touchSlop < 12) touchSlop = 12;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                deciding = true;
                horizontal = false;
                // 让子 ListView 可以竖滑；同时把 DOWN 交给 ViewPager 记录
                try {
                    super.onInterceptTouchEvent(ev);
                } catch (Exception ignored) {
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (deciding) {
                    float dx = Math.abs(ev.getX() - startX);
                    float dy = Math.abs(ev.getY() - startY);
                    if (dx > touchSlop || dy > touchSlop) {
                        deciding = false;
                        horizontal = dx > dy * 1.1f;
                    }
                }
                if (horizontal) {
                    // 明确拦截横向滑动，切视频/动态页
                    try {
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                deciding = false;
                horizontal = false;
                break;
        }
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            return super.onTouchEvent(ev);
        } catch (Exception e) {
            return false;
        }
    }
}
