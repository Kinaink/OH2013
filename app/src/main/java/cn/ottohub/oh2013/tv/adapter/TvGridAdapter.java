package cn.ottohub.oh2013.tv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import cn.ottohub.oh2013.R;
import cn.ottohub.oh2013.util.SdkHelper;

/**
 * TV 磁贴网格。不含 View.animate / setScaleX 等 API11+ 直接引用，
 * 飞入动画通过 {@link TvTileFlyHelper}（仅高版本加载）完成。
 */
public class TvGridAdapter extends BaseAdapter {

    private Context context;
    private String[] titles;
    private int[] icons;
    private OnTileClickListener listener;
    private FrameLayout rootContainer;

    public interface OnTileClickListener {
        void onTileClick(String label);
    }

    public TvGridAdapter(Context context) {
        this.context = context;
        this.titles = new String[]{"登录", "推荐", "时间线", "收藏", "搜索", "历史", "设置"};
        this.icons = new int[]{
                R.drawable.ic_tv_user,
                R.drawable.ic_tv,
                R.drawable.ic_tv_timeline,
                R.drawable.ic_tv_star,
                R.drawable.ic_tv_search,
                R.drawable.ic_tv_history,
                R.drawable.ic_action_refresh
        };
    }

    public void setRootContainer(FrameLayout container) {
        this.rootContainer = container;
    }

    public void setOnTileClickListener(OnTileClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return titles.length;
    }

    @Override
    public Object getItem(int position) {
        return titles[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View itemView = convertView;
        ViewHolder holder;
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.item_tv_tile, parent, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) itemView.findViewById(R.id.tile_icon);
            holder.label = (TextView) itemView.findViewById(R.id.tile_label);
            holder.backLabel = (TextView) itemView.findViewById(R.id.tile_back_label);
            holder.front = (RelativeLayout) itemView.findViewById(R.id.tile_front);
            holder.back = (RelativeLayout) itemView.findViewById(R.id.tile_back);
            itemView.setTag(holder);
        } else {
            holder = (ViewHolder) itemView.getTag();
        }

        holder.icon.setImageResource(icons[position]);
        holder.label.setText(titles[position]);
        if (holder.backLabel != null) {
            holder.backLabel.setText("正在打开 " + titles[position] + "...");
        }

        if (holder.front != null) {
            holder.front.setVisibility(View.VISIBLE);
            SdkHelper.setViewAlpha(holder.front, 1.0f);
        }
        if (holder.back != null) {
            holder.back.setVisibility(View.VISIBLE);
            SdkHelper.setViewAlpha(holder.back, 0.0f);
        }
        SdkHelper.setViewAlpha(itemView, 1.0f);
        itemView.clearAnimation();
        itemView.setEnabled(true);
        itemView.setVisibility(View.VISIBLE);
        itemView.setFocusable(true);
        itemView.setFocusableInTouchMode(true);
        itemView.setClickable(true);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (listener == null) return;
                final String label = titles[position];
                // 勿在旧机加载 TvTileFlyHelper（内含 View.animate，会 VerifyError）
                if (rootContainer == null || SdkHelper.getSdkInt() < 14) {
                    listener.onTileClick(label);
                    return;
                }
                try {
                    Class<?> cl = Class.forName("cn.ottohub.oh2013.tv.adapter.TvTileFlyHelper");
                    java.lang.reflect.Method fly = cl.getMethod("fly",
                            Context.class, FrameLayout.class, View.class,
                            int.class, String.class,
                            Class.forName("cn.ottohub.oh2013.tv.adapter.TvTileFlyHelper$Done"));
                    Object done = java.lang.reflect.Proxy.newProxyInstance(
                            cl.getClassLoader(),
                            new Class[]{Class.forName("cn.ottohub.oh2013.tv.adapter.TvTileFlyHelper$Done")},
                            new java.lang.reflect.InvocationHandler() {
                                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                                    if ("onDone".equals(method.getName()) && args != null && args.length > 0) {
                                        listener.onTileClick(String.valueOf(args[0]));
                                    }
                                    return null;
                                }
                            });
                    fly.invoke(null, context, rootContainer, v,
                            Integer.valueOf(icons[position]), label, done);
                } catch (Throwable t) {
                    listener.onTileClick(label);
                }
            }
        });

        return itemView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView label;
        TextView backLabel;
        RelativeLayout front;
        RelativeLayout back;
    }
}
