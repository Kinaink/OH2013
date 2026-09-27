package cn.ottohub.oh2013.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ottohub.oh2013.BlogDetailActivity;
import cn.ottohub.oh2013.ImageViewerActivity;
import cn.ottohub.oh2013.R;
import cn.ottohub.oh2013.UserProfileActivity;
import cn.ottohub.oh2013.VideoDetailActivity;

/**
 * 轻量 Markdown / HTML 渲染：填入 LinearLayout（TextView + 可点击 ImageView）。
 * 支持 **bold**、*italic*、`code`、链接、图片、标题、换行；HTML 走 Html.fromHtml。
 */
public final class MarkdownContentHelper {

    private static final int LINK_COLOR = 0xFFFFCC00;

    private static final Pattern IMG_MD = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(\\s*(https?://[^\\s\\)]+|/?[^\\s\\)]+)\\s*\\)");
    private static final Pattern IMG_HTML = Pattern.compile(
            "(?i)<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>");
    /** Negative lookbehind so image markdown {@code ![alt](url)} is not treated as a link. */
    private static final Pattern LINK_MD = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern BOLD_MD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC_MD = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    private static final Pattern CODE_MD = Pattern.compile("`([^`]+)`");
    private static final Pattern HEADER_MD = Pattern.compile("(?m)^(#{1,3})\\s+(.+)$");
    private static final Pattern OTTO_ID = Pattern.compile(
            "(?i)(?<![a-zA-Z0-9])(ov|ob)(\\d+)(?![a-zA-Z0-9])");
    private static final Pattern MENTION_MD = Pattern.compile(
            "\\[@([^\\]]+)\\]\\(\\s*https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)/?\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE_URL = Pattern.compile(
            "(?i)https?://(?:www\\.)?ottohub\\.cn/u/(\\d+)");

    private MarkdownContentHelper() {
    }

    public static void renderInto(final Context context, LinearLayout container, String raw) {
        renderInto(context, container, raw, false);
    }

    /**
     * @param lite true=列表/聊天室轻量模式：不拆图片 ImageView，仅 Html/纯文本，旧机防卡顿 OOM
     */
    public static void renderInto(final Context context, LinearLayout container, String raw, boolean lite) {
        if (container == null || context == null) return;
        container.removeAllViews();
        if (raw == null) raw = "";

        if (lite || SdkHelper.isAncientDevice()) {
            TextView tv = new TextView(context);
            tv.setTextSize(15);
            tv.setTextColor(0xFF333333);
            tv.setLineSpacing(6, 1f);
            CharSequence spanned = fromHtmlSafe(markdownToSimpleHtml(raw));
            spanned = applyOttoLinks(context, spanned);
            tv.setText(spanned);
            try {
                tv.setMovementMethod(LinkMovementMethod.getInstance());
            } catch (Throwable t) {
            }
            container.addView(tv);
            return;
        }

        final ArrayList<String> allImages = new ArrayList<String>();
        String lower = raw.toLowerCase();
        boolean looksHtml = lower.indexOf("<p") >= 0 || lower.indexOf("<div") >= 0
                || lower.indexOf("<br") >= 0 || lower.indexOf("<img") >= 0
                || lower.indexOf("<h1") >= 0 || lower.indexOf("<h2") >= 0
                || lower.indexOf("<ul") >= 0 || lower.indexOf("<li") >= 0
                || lower.indexOf("<strong") >= 0 || lower.indexOf("<em") >= 0
                || lower.indexOf("<a ") >= 0 || lower.indexOf("<span") >= 0;

        ArrayList<Block> blocks;
        if (IMG_MD.matcher(raw).find()) {
            blocks = splitMarkdown(raw, allImages);
        } else if (looksHtml) {
            blocks = splitHtml(raw, allImages);
        } else {
            blocks = splitMarkdown(raw, allImages);
        }

        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (4 * density + 0.5f);
        int maxH = (int) ((SdkHelper.isPreJellyBean() ? 120 : 180) * density + 0.5f);

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.type == Block.TYPE_IMAGE) {
                // 外包一层 wrap_content，避免 GIF ImageView 被撑成整屏灰底
                android.widget.FrameLayout wrap = new android.widget.FrameLayout(context);
                LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                wrapLp.topMargin = pad;
                wrapLp.bottomMargin = pad;
                wrap.setLayoutParams(wrapLp);
                wrap.setBackgroundColor(0x00000000);

                ImageView iv = new ImageView(context);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                iv.setLayoutParams(lp);
                iv.setMinimumHeight(0);
                iv.setMinimumWidth(0);
                iv.setMaxHeight(maxH);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackgroundColor(0x00000000);
                iv.setPadding(0, 0, 0, 0);
                final String imgUrl = b.text;
                final int index = indexOfUrl(allImages, imgUrl);
                iv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, ImageViewerActivity.class);
                        intent.putStringArrayListExtra("imageList", new ArrayList<String>(allImages));
                        intent.putExtra("index", index >= 0 ? index : 0);
                        context.startActivity(intent);
                    }
                });
                wrap.addView(iv);
                container.addView(wrap);
                int w = (int) (context.getResources().getDisplayMetrics().widthPixels
                        - context.getResources().getDisplayMetrics().density * 32);
                if (w < 1) w = context.getResources().getDisplayMetrics().widthPixels;
                CoverImageLoader.loadInto(context, iv, imgUrl, w, maxH);
            } else if (b.text != null && b.text.length() > 0) {
                TextView tv = new TextView(context);
                tv.setTextSize(15);
                tv.setTextColor(0xFF333333);
                tv.setLineSpacing(6, 1f);
                tv.setPadding(0, pad / 2, 0, pad / 2);
                String chunk = b.text;
                String cl = chunk.toLowerCase();
                boolean chunkHtml = looksHtml
                        || cl.indexOf("<br") >= 0 || cl.indexOf("<p") >= 0
                        || cl.indexOf("<b") >= 0 || cl.indexOf("<i") >= 0
                        || cl.indexOf("<a ") >= 0 || cl.indexOf("&nbsp;") >= 0;
                CharSequence spanned;
                if (chunkHtml) {
                    // 基本 HTML；若仍含 markdown 行内再补一层
                    spanned = fromHtmlSafe(chunk);
                    if (chunk.indexOf("**") >= 0 || chunk.indexOf('[') >= 0) {
                        spanned = markdownInlineToSpanned(chunk);
                    }
                } else {
                    spanned = markdownInlineToSpanned(chunk);
                }
                spanned = applyOttoLinks(context, spanned);
                tv.setText(spanned);
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                container.addView(tv);
            }
        }

        if (blocks.size() == 0) {
            TextView tv = new TextView(context);
            tv.setText("");
            tv.setTextColor(0xFF999999);
            container.addView(tv);
        }
    }

    private static int indexOfUrl(ArrayList<String> list, String url) {
        for (int i = 0; i < list.size(); i++) {
            if (url != null && url.equals(list.get(i))) return i;
        }
        return -1;
    }

    private static CharSequence fromHtmlSafe(String html) {
        if (html == null) return "";
        try {
            return Html.fromHtml(html);
        } catch (Throwable t) {
            return html;
        }
    }

    /**
     * Post-process spanned text: OV/OB ids, leftover {@code [@name](ottohub.cn/u/N)} markdown,
     * and URLSpan → in-app profile / styled links.
     */
    private static CharSequence applyOttoLinks(final Context context, CharSequence text) {
        if (text == null) return "";
        if (context == null) return text;
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // Leftover mention markdown → yellow @name → UserProfileActivity
        String plain = ssb.toString();
        Matcher mm = MENTION_MD.matcher(plain);
        if (mm.find()) {
            SpannableStringBuilder rebuilt = new SpannableStringBuilder();
            int last = 0;
            mm.reset();
            while (mm.find()) {
                if (mm.start() > last) {
                    rebuilt.append(ssb.subSequence(last, mm.start()));
                }
                String display = "@" + mm.group(1);
                int start = rebuilt.length();
                rebuilt.append(display);
                final long mid = Long.parseLong(mm.group(2));
                rebuilt.setSpan(newProfileSpan(context, mid), start, rebuilt.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                last = mm.end();
            }
            if (last < ssb.length()) {
                rebuilt.append(ssb.subSequence(last, ssb.length()));
            }
            ssb = rebuilt;
        }

        // Replace URLSpans (profile links open UserProfileActivity)
        stripUrlUnderline(context, ssb);

        // Auto-link OV{n} / OB{n}
        Matcher om = OTTO_ID.matcher(ssb.toString());
        while (om.find()) {
            int start = om.start();
            int end = om.end();
            ClickableSpan[] existing = ssb.getSpans(start, end, ClickableSpan.class);
            if (existing != null && existing.length > 0) continue;

            final boolean isVideo = "ov".equalsIgnoreCase(om.group(1));
            final long id;
            try {
                id = Long.parseLong(om.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (isVideo) {
                        Intent intent = new Intent(context, VideoDetailActivity.class);
                        intent.putExtra("aid", id);
                        context.startActivity(intent);
                    } else {
                        Intent intent = new Intent(context, BlogDetailActivity.class);
                        intent.putExtra("bid", id);
                        context.startActivity(intent);
                    }
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(LINK_COLOR);
                    ds.setUnderlineText(true);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    private static ClickableSpan newProfileSpan(final Context context, final long mid) {
        return new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("mid", mid);
                context.startActivity(intent);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setColor(LINK_COLOR);
                ds.setUnderlineText(true);
            }
        };
    }

    private static void stripUrlUnderline(final Context context, SpannableStringBuilder ssb) {
        if (ssb == null) return;
        URLSpan[] urls = ssb.getSpans(0, ssb.length(), URLSpan.class);
        if (urls == null) return;
        for (int i = 0; i < urls.length; i++) {
            final URLSpan span = urls[i];
            int start = ssb.getSpanStart(span);
            int end = ssb.getSpanEnd(span);
            if (start < 0 || end < 0) continue;
            final String href = span.getURL();
            ssb.removeSpan(span);

            Matcher pm = PROFILE_URL.matcher(href != null ? href : "");
            if (pm.find()) {
                long mid;
                try {
                    mid = Long.parseLong(pm.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                ssb.setSpan(newProfileSpan(context, mid), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        if (href == null || href.length() == 0) return;
                        try {
                            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(href)));
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        ds.setColor(LINK_COLOR);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static ArrayList<Block> splitHtml(String html, ArrayList<String> images) {
        ArrayList<Block> blocks = new ArrayList<Block>();
        Matcher m = IMG_HTML.matcher(html);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                blocks.add(Block.text(html.substring(last, m.start())));
            }
            String src = m.group(1);
            if (src != null && src.length() > 0) {
                images.add(src);
                blocks.add(Block.image(src));
            }
            last = m.end();
        }
        if (last < html.length()) {
            blocks.add(Block.text(html.substring(last)));
        }
        return blocks;
    }

    private static ArrayList<Block> splitMarkdown(String src, ArrayList<String> images) {
        ArrayList<Block> blocks = new ArrayList<Block>();
        Matcher m = IMG_MD.matcher(src);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                blocks.add(Block.text(src.substring(last, m.start())));
            }
            String url = m.group(2);
            if (url != null && url.length() > 0) {
                images.add(url.trim());
                blocks.add(Block.image(url.trim()));
            }
            last = m.end();
        }
        if (last < src.length()) {
            blocks.add(Block.text(src.substring(last)));
        }
        return blocks;
    }

    /** 将剩余 markdown 文本转为带样式的 Spanned（图片已拆出）。 */
    public static CharSequence markdownInlineToSpanned(String src) {
        if (src == null) return "";
        String s = src.replace("\r\n", "\n").replace("\r", "\n");

        // headers → 加粗行
        Matcher hm = HEADER_MD.matcher(s);
        StringBuffer hsb = new StringBuffer();
        while (hm.find()) {
            hm.appendReplacement(hsb, Matcher.quoteReplacement("**" + hm.group(2) + "**"));
        }
        hm.appendTail(hsb);
        s = hsb.toString();

        // 先转义再插入占位，最后用 Spannable 套样式
        // 简化：转成 HTML 再 Html.fromHtml
        String html = markdownToSimpleHtml(s);
        return fromHtmlSafe(html);
    }

    public static String markdownToSimpleHtml(String src) {
        if (src == null) return "";
        String s = src.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        // Strip leftover images so LINK_MD cannot match inside ![alt](url)
        s = IMG_MD.matcher(s).replaceAll("");

        Matcher bm = BOLD_MD.matcher(s);
        StringBuffer bb = new StringBuffer();
        while (bm.find()) {
            bm.appendReplacement(bb, "<b>" + Matcher.quoteReplacement(bm.group(1)) + "</b>");
        }
        bm.appendTail(bb);
        s = bb.toString();

        Matcher im = ITALIC_MD.matcher(s);
        StringBuffer ib = new StringBuffer();
        while (im.find()) {
            im.appendReplacement(ib, "<i>" + Matcher.quoteReplacement(im.group(1)) + "</i>");
        }
        im.appendTail(ib);
        s = ib.toString();

        Matcher cm = CODE_MD.matcher(s);
        StringBuffer cb = new StringBuffer();
        while (cm.find()) {
            cm.appendReplacement(cb, "<tt>" + Matcher.quoteReplacement(cm.group(1)) + "</tt>");
        }
        cm.appendTail(cb);
        s = cb.toString();

        Matcher lm = LINK_MD.matcher(s);
        StringBuffer lb = new StringBuffer();
        while (lm.find()) {
            String text = lm.group(1);
            String url = lm.group(2);
            lm.appendReplacement(lb, "<a href=\"" + Matcher.quoteReplacement(url) + "\">"
                    + Matcher.quoteReplacement(text) + "</a>");
        }
        lm.appendTail(lb);
        s = lb.toString();

        s = s.replace("\n\n", "</p><p>").replace("\n", "<br/>");
        return "<p>" + s + "</p>";
    }

    private static final class Block {
        static final int TYPE_TEXT = 0;
        static final int TYPE_IMAGE = 1;
        int type;
        String text;

        static Block text(String t) {
            Block b = new Block();
            b.type = TYPE_TEXT;
            b.text = t;
            return b;
        }

        static Block image(String url) {
            Block b = new Block();
            b.type = TYPE_IMAGE;
            b.text = url;
            return b;
        }
    }
}
