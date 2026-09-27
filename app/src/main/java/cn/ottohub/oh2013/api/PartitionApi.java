package cn.ottohub.oh2013.api;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import cn.ottohub.oh2013.model.VideoCard;

/**
 * OH2013：分区视频列表走 OTTOhub（不再请求 B 站 tag/WBI）。
 */
public class PartitionApi {

    /**
     * @param rid  兼容旧调用；大于 1000 时按 TidData Tab 类型区分最新/热门
     * @param page 从 1 开始
     */
    public static void getRegionVideos(List<VideoCard> videoCardList, int rid, int page)
            throws IOException, JSONException {
        int offset = Math.max(0, (page - 1) * ApiConfig.PAGE_SIZE);
        String feed = RecommendApi.FEED_POPULAR;
        if (rid == cn.ottohub.oh2013.TidData.TAB_VIDEO_NEW) {
            feed = RecommendApi.FEED_NEW;
        } else if (rid == cn.ottohub.oh2013.TidData.TAB_VIDEO_HOT) {
            feed = RecommendApi.FEED_POPULAR;
        }
        RecommendApi.fetchVideoList(feed, offset, videoCardList);
    }
}
