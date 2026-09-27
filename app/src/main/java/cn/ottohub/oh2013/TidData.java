package cn.ottohub.oh2013;



import java.util.HashMap;

import java.util.Map;



/**

 * 分区导航分类（OH2013 / OTTOhub）

 */

public class TidData {



    public static final int TID_CHANNEL = -1;

    public static final int TID_PART = 11;

    public static final int TID_DOUGA = 1;

    public static final int TID_ENT = 5;

    public static final int TID_MUSIC = 3;



    /** 子 Tab 类型（用于 PartitionDetailActivity） */

    public static final int TAB_VIDEO_NEW = 1001;

    public static final int TAB_VIDEO_HOT = 1002;

    public static final int TAB_VIDEO_RANDOM = 1003;

    public static final int TAB_BLOG_NEW = 2001;

    public static final int TAB_BLOG_HOT = 2002;

    public static final int TAB_BLOG_RANDOM = 2003;

    public static final int TAB_IM_CHAT = 3001;

    public static final int TAB_IM_ROOM = 3002;

    public static final int TAB_SLIDESHOW = 4001;

    public static final int TAB_CHANNEL_LIST = 5001;



    private static final Map<Integer, String> TID_NAME_MAP = new HashMap<Integer, String>();

    private static final Map<Integer, int[]> TID_TAB_MAP = new HashMap<Integer, int[]>();

    private static final Map<Integer, String[]> TID_TAB_NAME_MAP = new HashMap<Integer, String[]>();



    static {

        TID_NAME_MAP.put(TID_CHANNEL, "频道");

        TID_NAME_MAP.put(TID_PART, "视频");

        TID_NAME_MAP.put(TID_DOUGA, "动态");

        TID_NAME_MAP.put(TID_ENT, "聊天");

        TID_NAME_MAP.put(TID_MUSIC, "资讯");



        TID_TAB_MAP.put(TID_CHANNEL, new int[]{TAB_CHANNEL_LIST});

        TID_TAB_NAME_MAP.put(TID_CHANNEL, new String[]{"频道"});



        TID_TAB_MAP.put(TID_PART, new int[]{TAB_VIDEO_NEW, TAB_VIDEO_HOT, TAB_VIDEO_RANDOM});

        TID_TAB_NAME_MAP.put(TID_PART, new String[]{"最新", "热门", "随机"});



        TID_TAB_MAP.put(TID_DOUGA, new int[]{TAB_BLOG_NEW, TAB_BLOG_HOT});

        TID_TAB_NAME_MAP.put(TID_DOUGA, new String[]{"最新", "热门"});



        TID_TAB_MAP.put(TID_ENT, new int[]{TAB_IM_CHAT, TAB_IM_ROOM});

        TID_TAB_NAME_MAP.put(TID_ENT, new String[]{"私聊", "聊天室"});



        TID_TAB_MAP.put(TID_MUSIC, new int[]{TAB_SLIDESHOW});

        TID_TAB_NAME_MAP.put(TID_MUSIC, new String[]{"资讯"});

    }



    public static int[] getMainCategories() {

        return new int[]{TID_CHANNEL, TID_PART, TID_DOUGA, TID_ENT, TID_MUSIC};

    }



    public static int[] getTabTypes(int majorTid) {

        int[] tabs = TID_TAB_MAP.get(majorTid);

        return tabs != null ? tabs : new int[0];

    }



    public static String[] getTabNames(int majorTid) {

        String[] names = TID_TAB_NAME_MAP.get(majorTid);

        return names != null ? names : new String[0];

    }



    /** 兼容旧代码 */

    public static int[] getTidGroup(int tid) {

        return getTabTypes(tid);

    }



    public static String getNameByTid(int tid) {

        String name = TID_NAME_MAP.get(tid);

        if (name != null) return name;

        if (tid == TAB_VIDEO_NEW || tid == TAB_BLOG_NEW) return "最新";

        if (tid == TAB_VIDEO_HOT || tid == TAB_BLOG_HOT) return "热门";

        if (tid == TAB_VIDEO_RANDOM) return "随机";

        if (tid == TAB_BLOG_RANDOM) return "随机";

        if (tid == TAB_IM_CHAT) return "私聊";

        if (tid == TAB_IM_ROOM) return "聊天室";

        if (tid == TAB_SLIDESHOW) return "资讯";

        if (tid == TAB_CHANNEL_LIST) return "频道";

        return "未知";

    }

}

