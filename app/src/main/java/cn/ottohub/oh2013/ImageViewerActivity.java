package cn.ottohub.oh2013;


import cn.ottohub.oh2013.util.NetWorkUtil;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import cn.ottohub.oh2013.util.CoverImageLoader;
import cn.ottohub.oh2013.util.FileProviderCompat;
import cn.ottohub.oh2013.util.GlobalImageCache;
import cn.ottohub.oh2013.util.PermissionUtil;
import cn.ottohub.oh2013.widget.PhotoView;
import cn.ottohub.oh2013.widget.PhotoViewPager;

import cn.ottohub.oh2013.util.SdkHelper;
public class ImageViewerActivity extends BaseActivity {

    private static final boolean IS_MODERN_DEVICE;

    static {
        boolean isModern = false;
        try {
            if (SdkHelper.getSdkInt() >= 14) {
                long maxMemory = Runtime.getRuntime().maxMemory();
                if (maxMemory > 48 * 1024 * 1024) {
                    isModern = true;
                }
            }
        } catch (Exception e) {
            isModern = false;
        }
        IS_MODERN_DEVICE = isModern;
    }

    private PhotoViewPager viewPager;
    private TextView textView;
    private ArrayList<String> imageList;
    private ImagePagerAdapter adapter;
    private boolean isDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        final Intent intent = getIntent();
        imageList = intent.getStringArrayListExtra("imageList");
        final int startIndex = intent.getIntExtra("index", 0);

        viewPager = (PhotoViewPager) findViewById(R.id.viewPager);
        textView = (TextView) findViewById(R.id.text_page);

        adapter = new ImagePagerAdapter();
        viewPager.setAdapter(adapter);

        if (startIndex > 0 && startIndex < imageList.size()) {
            viewPager.setCurrentItem(startIndex);
        }

        textView.setText("第" + (viewPager.getCurrentItem() + 1) + "/" + imageList.size() + "张");

        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                textView.setText("第" + (position + 1) + "/" + imageList.size() + "张");
            }

            @Override
            public void onPageSelected(int position) {
                textView.setText("第" + (position + 1) + "/" + imageList.size() + "张");
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        ImageButton btnDownload = (ImageButton) findViewById(R.id.btn_download);
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = viewPager.getCurrentItem();
                if (position < 0 || position >= imageList.size()) return;
                saveImageToGallery(imageList.get(position));
            }
        });
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        super.onDestroy();
    }

    private class ImagePagerAdapter extends PagerAdapter {
        private SparseArray<PhotoView> views = new SparseArray<PhotoView>();

        @Override
        public int getCount() {
            return imageList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            String url = imageList.get(position);
            final boolean isGif = CoverImageLoader.isLikelyGifUrl(url);

            if (isGif) {
                // GIF：普通 ImageView + Movie 渲染（PhotoView MATRIX 会破坏动图）
                android.widget.ImageView gifView = new android.widget.ImageView(ImageViewerActivity.this);
                gifView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                gifView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                gifView.setBackgroundColor(0x00000000);
                gifView.setAdjustViewBounds(true);
                int w = getResources().getDisplayMetrics().widthPixels;
                int h = getResources().getDisplayMetrics().heightPixels;
                CoverImageLoader.loadInto(ImageViewerActivity.this, gifView, url, w, h);
                container.addView(gifView);
                return gifView;
            }

            PhotoView photoView = new PhotoView(ImageViewerActivity.this);
            photoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            photoView.setBackgroundColor(0x00000000);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            CoverImageLoader.loadInto(ImageViewerActivity.this, photoView, url, w, h);
            setMaxScale(photoView, 6.25f);

            container.addView(photoView);
            views.put(position, photoView);
            return photoView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            views.remove(position);
        }
    }

    private void setMaxScale(PhotoView photoView, float maxScale) {
        if (photoView == null) return;
        try {
            java.lang.reflect.Method method = PhotoView.class.getMethod("setMaximumScale", float.class);
            method.invoke(photoView, maxScale);
        } catch (Exception e) {
        }
    }

    private void saveImageToGallery(final String url) {
        if (!PermissionUtil.hasWriteStorage(this)) {
            runWithStoragePermission(new Runnable() {
                @Override
                public void run() {
                    saveImageToGallery(url);
                }
            });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap originalBitmap = downloadOriginalImage(url);
                    if (originalBitmap == null || originalBitmap.isRecycled()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ImageViewerActivity.this, ImageViewerActivity.this.getString(R.string.imagevieweractivity_toast_83b7), Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    String fileName = "BiliClassic_" + System.currentTimeMillis() + ".jpg";
                    final File file = saveBitmapToFile(originalBitmap, fileName);
                    originalBitmap.recycle();
                    if (file == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ImageViewerActivity.this, ImageViewerActivity.this.getString(R.string.imagevieweractivity_toast_4fdd), Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    notifyGallery(file);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageViewerActivity.this, "已保存: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageViewerActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private Bitmap downloadOriginalImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            if (urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            int sampleSize = 1;
            int maxDimension = Math.max(opts.outWidth, opts.outHeight);
            if (maxDimension > 4000) {
                sampleSize = 2;
            }
            if (maxDimension > 8000) {
                sampleSize = 4;
            }

            conn.disconnect();
            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private File saveBitmapToFile(Bitmap bitmap, String fileName) throws Exception {
        File targetFile = null;

        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) && PermissionUtil.hasWriteStorage(this)) {
                File picturesDir;
                File extPubDir = getExternalStoragePublicDirectoryPictures();
                if (extPubDir != null) {
                    picturesDir = new File(extPubDir, "BiliClassic");
                } else {
                    picturesDir = new File(Environment.getExternalStorageDirectory(), "Pictures/BiliClassic");
                }
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs();
                }
                if (picturesDir.exists() && picturesDir.canWrite()) {
                    File externalFile = new File(picturesDir, fileName);
                    FileOutputStream fos = new FileOutputStream(externalFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                    targetFile = externalFile;
                }
            }
        } catch (Exception e) {
        }

        if (targetFile == null) {
            File internalDir = new File(getFilesDir(), "Pictures");
            if (!internalDir.exists()) {
                internalDir.mkdirs();
            }
            File internalFile = new File(internalDir, fileName);
            FileOutputStream fos = new FileOutputStream(internalFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            targetFile = internalFile;
        }

        return targetFile;
    }

    private void notifyGallery(File file) {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = SdkHelper.getSdkInt() >= 24
                    ? FileProviderCompat.getUriForFile(this, file)
                    : android.net.Uri.fromFile(file);
            mediaScanIntent.setData(uri);
            sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
        }
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            if (urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            int sampleSize;

            if (IS_MODERN_DEVICE) {
                sampleSize = 1;
            } else {
                sampleSize = 4;
                int maxDimension = Math.max(opts.outWidth, opts.outHeight);
                if (maxDimension > 2000) {
                    sampleSize = 4;
                }
                if (maxDimension > 3000) {
                    sampleSize = 6;
                }
                if (maxDimension > 4000) {
                    sampleSize = 8;
                }
                if (maxDimension > 6000) {
                    sampleSize = 12;
                }
            }

            conn.disconnect();
            conn = NetWorkUtil.openCompat(urlStr);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeStream(is, null, opts);
        } catch (OutOfMemoryError e) {
            System.gc();
            try {
                if (conn != null) conn.disconnect();
                conn = NetWorkUtil.openCompat(urlStr);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
                is = conn.getInputStream();
                BitmapFactory.Options optsRetry = new BitmapFactory.Options();
                optsRetry.inSampleSize = 8;
                optsRetry.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(is, null, optsRetry);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static File getExternalStoragePublicDirectoryPictures() {
        try {
            String type = (String) Environment.class.getField("DIRECTORY_PICTURES").get(null);
            return (File) Environment.class.getMethod("getExternalStoragePublicDirectory", String.class).invoke(null, type);
        } catch (Exception e) {
            return null;
        }
    }
}
