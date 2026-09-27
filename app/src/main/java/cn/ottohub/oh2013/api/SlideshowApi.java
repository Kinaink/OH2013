package cn.ottohub.oh2013.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.ottohub.oh2013.model.SlideshowItem;

public class SlideshowApi {

    public static List<SlideshowItem> fetchActive() throws IOException, JSONException {
        JSONObject result = OttoApiUtil.getJson("/api/slideshow/active");
        ArrayList<SlideshowItem> list = new ArrayList<SlideshowItem>();
        if (!OttoApiUtil.isSuccess(result)) return list;
        JSONObject data = result.optJSONObject("data");
        JSONArray slides = data != null ? data.optJSONArray("slides") : null;
        if (slides == null) return list;
        for (int i = 0; i < slides.length(); i++) {
            JSONObject s = slides.getJSONObject(i);
            SlideshowItem item = new SlideshowItem();
            item.imgUrl = s.optString("img_url", "");
            item.title = s.optString("title", "");
            item.href = s.optString("href", "");
            list.add(item);
        }
        return list;
    }
}
