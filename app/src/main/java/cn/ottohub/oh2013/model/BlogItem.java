package cn.ottohub.oh2013.model;

/**
 * OTTOhub 动态（博客）条目
 */
public class BlogItem {
    public long bid;
    public long uid;
    public String title;
    public String content;
    public String time;
    public String username;
    public String avatarUrl;
    public String thumbnail;
    public int viewCount;
    public int likeCount;
    public int favoriteCount;
    public int commentCount;
    /** 当前用户是否已点赞 / 已收藏（详情接口 if_like / if_favorite） */
    public boolean ifLike;
    public boolean ifFavorite;
    /** 内容分级：0=8+/全年龄，1=4000+/恶心猎奇（is_gore） */
    public int isGore;
}
