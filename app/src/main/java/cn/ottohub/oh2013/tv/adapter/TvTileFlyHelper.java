package cn.ottohub.oh2013.tv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import cn.ottohub.oh2013.R;
import cn.ottohub.oh2013.util.SdkHelper;

/**
 * TV 磁贴飞入动画（ViewPropertyAnimator，API 12+）。
 * 独立类：避免在 API&lt;12 上因引用 animate()/setScaleX 等导致整类 VerifyError。
 */
public final class TvTileFlyHelper {

    private TvTileFlyHelper() {
    }

    public static boolean supported() {
        return SdkHelper.getSdkInt() >= 14;
    }

    public interface Done {
        void onDone(String label);
    }

    public static void fly(
            final Context context,
            final FrameLayout rootContainer,
            final View sourceTile,
            int iconRes,
            final String label,
            final Done done) {
        if (context == null || rootContainer == null || sourceTile == null || done == null) {
            return;
        }
        if (!supported()) {
            done.onDone(label);
            return;
        }

        sourceTile.setEnabled(false);

        int screenW = rootContainer.getWidth();
        int screenH = rootContainer.getHeight();
        int[] location = new int[2];
        sourceTile.getLocationOnScreen(location);
        int tileW = sourceTile.getWidth();
        int tileH = sourceTile.getHeight();

        final View flyingTile = LayoutInflater.from(context)
                .inflate(R.layout.item_tv_tile, rootContainer, false);
        ImageView fIcon = (ImageView) flyingTile.findViewById(R.id.tile_icon);
        TextView fLabel = (TextView) flyingTile.findViewById(R.id.tile_label);
        final TextView fBackLabel = (TextView) flyingTile.findViewById(R.id.tile_back_label);
        final RelativeLayout fFront = (RelativeLayout) flyingTile.findViewById(R.id.tile_front);
        final RelativeLayout fBack = (RelativeLayout) flyingTile.findViewById(R.id.tile_back);

        if (fIcon != null) fIcon.setImageResource(iconRes);
        if (fLabel != null) fLabel.setText(label);

        if (fFront != null) {
            fFront.setVisibility(View.VISIBLE);
            SdkHelper.setViewAlpha(fFront, 1.0f);
        }
        if (fBack != null) {
            fBack.setVisibility(View.VISIBLE);
            SdkHelper.setViewAlpha(fBack, 0.0f);
        }
        if (fBackLabel != null) {
            try {
                fBackLabel.setRotationY(0f);
            } catch (Throwable t) {
            }
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(tileW, tileH);
        params.leftMargin = location[0];
        params.topMargin = location[1];
        rootContainer.addView(flyingTile, params);
        sourceTile.setVisibility(View.INVISIBLE);

        float centerX = location[0] + tileW / 2f;
        float centerY = location[1] + tileH / 2f;
        float screenCenterX = screenW / 2f;
        float screenCenterY = screenH / 2f;
        float transX = screenCenterX - centerX;
        float transY = screenCenterY - centerY;
        float scaleX = tileW > 0 ? (float) screenW / tileW : 1f;
        float scaleY = tileH > 0 ? (float) screenH / tileH : 1f;

        try {
            flyingTile.setPivotX(tileW / 2f);
            flyingTile.setPivotY(tileH / 2f);
            flyingTile.setRotationY(0f);
        } catch (Throwable t) {
        }

        final int duration = 350;
        try {
            flyingTile.animate()
                    .scaleX(scaleX)
                    .scaleY(scaleY)
                    .translationX(transX)
                    .translationY(transY)
                    .rotationY(75f)
                    .setDuration(duration)
                    .withEndAction(new Runnable() {
                        public void run() {
                            if (fFront != null) SdkHelper.setViewAlpha(fFront, 0.0f);
                            if (fBack != null) SdkHelper.setViewAlpha(fBack, 1.0f);
                            try {
                                flyingTile.animate()
                                        .rotationY(180f)
                                        .setDuration(duration)
                                        .withEndAction(new Runnable() {
                                            public void run() {
                                                try {
                                                    rootContainer.removeView(flyingTile);
                                                } catch (Throwable t) {
                                                }
                                                done.onDone(label);
                                            }
                                        })
                                        .start();
                            } catch (Throwable t) {
                                done.onDone(label);
                            }
                        }
                    })
                    .start();
        } catch (Throwable t) {
            try {
                rootContainer.removeView(flyingTile);
            } catch (Throwable ignored) {
            }
            sourceTile.setVisibility(View.VISIBLE);
            sourceTile.setEnabled(true);
            done.onDone(label);
        }
    }
}
