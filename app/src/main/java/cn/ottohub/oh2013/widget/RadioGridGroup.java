package cn.ottohub.oh2013.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

public class RadioGridGroup extends ViewGroup {
    private int mCheckedId;
    private int mColumnCount = 2;
    private CheckedStateTracker mChildOnCheckedChangeListener;
    private OnCheckedChangeListener mOnCheckedChangeListener;
    private PassThroughHierarchyChangeListener mPassThroughListener;
    private boolean mProtectFromCheckedChange;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGridGroup radioGridGroup, int i);
    }

    public RadioGridGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mCheckedId = -1;
        init();
    }

    public RadioGridGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadioGridGroup(Context context) {
        this(context, null);
    }

    private void init() {
        this.mChildOnCheckedChangeListener = new CheckedStateTracker();
        this.mPassThroughListener = new PassThroughHierarchyChangeListener();
        super.setOnHierarchyChangeListener(this.mPassThroughListener);
    }

    @Override
    public void setOnHierarchyChangeListener(ViewGroup.OnHierarchyChangeListener listener) {
        this.mPassThroughListener.mOnHierarchyChangeListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (this.mCheckedId != -1) {
            this.mProtectFromCheckedChange = true;
            setCheckedStateForView(this.mCheckedId, true);
            this.mProtectFromCheckedChange = false;
            setCheckedId(this.mCheckedId);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof android.widget.RadioButton) {
            android.widget.RadioButton button = (android.widget.RadioButton) child;
            if (button.isChecked()) {
                this.mProtectFromCheckedChange = true;
                if (this.mCheckedId != -1) {
                    setCheckedStateForView(this.mCheckedId, false);
                }
                this.mProtectFromCheckedChange = false;
                setCheckedId(button.getId());
            }
        }
        super.addView(child, index, params);
    }

    public void check(int id) {
        if (id == -1 || id != this.mCheckedId) {
            if (this.mCheckedId != -1) {
                setCheckedStateForView(this.mCheckedId, false);
            }
            if (id != -1) {
                setCheckedStateForView(id, true);
            }
            setCheckedId(id);
        }
    }

    private void setCheckedId(int id) {
        this.mCheckedId = id;
        if (this.mOnCheckedChangeListener != null) {
            this.mOnCheckedChangeListener.onCheckedChanged(this, this.mCheckedId);
        }
    }

    private void setCheckedStateForView(int viewId, boolean checked) {
        View checkedView = findViewById(viewId);
        if (checkedView != null && (checkedView instanceof android.widget.RadioButton)) {
            ((android.widget.RadioButton) checkedView).setChecked(checked);
        }
    }

    public int getCheckedRadioButtonId() {
        return this.mCheckedId;
    }

    public void clearCheck() {
        check(-1);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.mOnCheckedChangeListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int childWidth = 0;
        int childHeight = 0;
        int visibleCount = 0;

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                childWidth = Math.max(childWidth, child.getMeasuredWidth());
                childHeight = Math.max(childHeight, child.getMeasuredHeight());
                visibleCount++;
            }
        }

        if (visibleCount == 0) {
            setMeasuredDimension(0, 0);
            return;
        }

        int rows = (visibleCount + mColumnCount - 1) / mColumnCount;
        int totalWidth = getPaddingLeft() + getPaddingRight();
        totalWidth += childWidth * mColumnCount;
        int totalHeight = getPaddingTop() + getPaddingBottom();
        totalHeight += childHeight * rows;

        setMeasuredDimension(resolveSize(totalWidth, widthMeasureSpec),
                resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        int width = r - l;
        int colWidth = (width - getPaddingLeft() - getPaddingRight()) / mColumnCount;
        int visibleIndex = 0;

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;

            int row = visibleIndex / mColumnCount;
            int col = visibleIndex % mColumnCount;
            int left = getPaddingLeft() + col * colWidth;
            int top = getPaddingTop() + row * child.getMeasuredHeight();
            child.layout(left, top, left + colWidth, top + child.getMeasuredHeight());
            visibleIndex++;
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    private class CheckedStateTracker implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!RadioGridGroup.this.mProtectFromCheckedChange) {
                RadioGridGroup.this.mProtectFromCheckedChange = true;
                if (RadioGridGroup.this.mCheckedId != -1) {
                    RadioGridGroup.this.setCheckedStateForView(RadioGridGroup.this.mCheckedId, false);
                }
                RadioGridGroup.this.mProtectFromCheckedChange = false;
                int id = buttonView.getId();
                RadioGridGroup.this.setCheckedId(id);
            }
        }
    }

    private class PassThroughHierarchyChangeListener implements ViewGroup.OnHierarchyChangeListener {
        private ViewGroup.OnHierarchyChangeListener mOnHierarchyChangeListener;

        @Override
        public void onChildViewAdded(View parent, View child) {
            if (parent == RadioGridGroup.this && (child instanceof android.widget.RadioButton)) {
                int id = child.getId();
                if (id == -1) {
                    id = child.hashCode();
                    child.setId(id);
                }
                ((android.widget.RadioButton) child).setOnCheckedChangeListener(
                        RadioGridGroup.this.mChildOnCheckedChangeListener);
            }
            if (this.mOnHierarchyChangeListener != null) {
                this.mOnHierarchyChangeListener.onChildViewAdded(parent, child);
            }
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
            if (parent == RadioGridGroup.this && (child instanceof android.widget.RadioButton)) {
                ((android.widget.RadioButton) child).setOnCheckedChangeListener(null);
            }
            if (this.mOnHierarchyChangeListener != null) {
                this.mOnHierarchyChangeListener.onChildViewRemoved(parent, child);
            }
        }
    }
}
