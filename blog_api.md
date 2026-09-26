# Blog 动态模块 API 文档

## 概述

Blog 模块提供动态列表、动态详情、用户动态、收藏动态、管理动态、点赞/收藏、删除、草稿保存、草稿读取、动态投稿、搜索与相关推荐等能力。动态审核请使用 **Moderation 模块**（`GET /api/moderation/blogs`）。

**基础信息**:
- **基础路径**: `/api/blog`
- **请求格式**:
  - 查询接口使用 Query String（GET）
  - 写入/草稿接口使用表单数据（POST），并兼容 JSON Body 读取 `token`
- **响应格式**: JSON
- **认证方式**: 需要登录的接口通过 `token` 参数传递
- **字符编码**: UTF-8

## 通用响应格式

**成功响应**:
```json
{
  "status": "success",
  "data": { ... }
}
```

**错误响应**:
```json
{
  "status": "error",
  "message": "错误信息代码"
}
```

## 通用错误码

- `missing_argument`: 缺少必需参数
- `error_type`: 参数类型错误
- `error_token`: Token无效或已过期
- `error_vid`: 视频ID错误
- `error_bid`: 动态ID错误
- `error_uid`: 用户ID无效或用户不存在
- `too_big_num`: 请求数量过大
- `system_error`: 系统错误
- `Not found`: 接口路径不存在

---

## 动态创建与草稿

每用户最多一条动态草稿（`blog_draft`）。草稿可存正式投稿允许的字段；**草稿不会一键转为正式动态**，正式内容仍走 `POST /api/blog/submit`。

### 1. 保存动态草稿

**请求**: `POST /api/blog/save`

**请求参数** (Form Data / JSON；除 `token` 外字段均可选，**传入则更新该字段**，未传不改)：
- `token` (string, 必需): 用户 token
- `title` (string, 可选): 草稿标题，最大 100 字
- `content` (string, 可选): 草稿正文，最大 10000 字
- `channel_id` (int, 可选): 所属频道 ID；为 `0` 时会将 `channel_section_id` 置 `0`
- `channel_section_id` (int, 可选): 所属频道二级分区 ID
- `attached_vid` (int, 可选): 关联视频 ID，`0` 表示无
- `blog_type` (int, 可选): `0`=普通动态，`1`=静画
- `tag` (string/array, 可选): 作者标签 JSON 数组，如 `["风景","赛博朋克"]`；最多 10 个，每项最长 30 字
- `copyright_type` (int, 可选): `0`=未设定，`1`=原创，`2`=转载，`3`=合作，`4`=AI生成，`5`=其他
- `is_gore` (int, 可选): 内容分级，默认 `0`；`0`=全年龄，`1`=恶心猎奇

**说明**:
- 无草稿则创建，已有草稿则只更新本次传入字段
- 除 `token` 外至少传入一个可写字段，否则 `missing_argument`
- 本次写入 `attached_vid>0` 时校验视频存在且 `is_deleted=0`、`audit_status=1`
- 本次写入 `channel_id` / `channel_section_id` 时校验频道存在、用户为正式成员、分区归属频道（规则同 `submit`）

**成功响应**:
```json
{
  "status": "success"
}
```

**错误码**:
- `missing_argument`
- `error_token`
- `title_too_long`
- `content_too_long`
- `error_type`
- `error_vid`
- `error_tag` / `tag_too_many`
- `channel_not_found`
- `not_channel_member`
- `channel_section_not_found`
- `channel_section_not_belong_to_channel`
- `system_error`

---

### 2. 读取动态草稿

**请求**: `POST /api/blog/load`

**请求参数** (Form Data / JSON):
- `token` (string, 必需): 用户 token

**成功响应**:
```json
{
  "status": "success",
  "title": "草稿标题",
  "content": "草稿正文",
  "channel_id": 0,
  "channel_section_id": 0,
  "attached_vid": 0,
  "blog_type": 0,
  "tag": [],
  "copyright_type": 0,
  "is_gore": 0,
  "draft_id": 1,
  "updated_at": "2026-07-22 12:00:00"
}
```

**说明**:
- 无草稿时仍返回 `status=success`，`title`/`content` 为空字符串，其余数值字段为默认值、`tag` 为 `[]`（此时可能无 `draft_id`）

**错误码**:
- `error_token`
- `system_error`

---

### 2.1 删除动态草稿

**请求**: `DELETE /api/blog/draft`

**请求参数** (Query 或 Body):
- `token` (string, 必需): 用户 token

**成功响应**:
```json
{
  "status": "success"
}
```

**错误码**:
- `error_token`
- `draft_not_found`
- `system_error`

**HTTP状态码**:
- `200`: 删除成功
- `400`: 无草稿（`draft_not_found`）
- `401`: Token 无效
- `500`: 系统错误

---

### 3. 提交动态

**请求**: `POST /api/blog/submit`

**请求参数** (Form Data):
- `token` (string, 必需): 用户 token
- `title` (string, 必需): 动态标题，1-100 字
- `content` (string, 必需): 动态正文，1-10000 字
- `channel_id` (int, 可选): 所属频道 ID，默认 0
- `channel_section_id` (int, 可选): 所属频道二级分区 ID，默认 0
- `attached_vid` (int, 可选): 关联视频 ID；如传入则必须是整数，且对应视频有效
- `blog_type` (int, 可选): 内容类型，默认 `0`
  - `0` = 普通动态
  - `1` = 静画
- `tag` (string/array, 可选): 作者标签，JSON 字符串数组，如 `["风景","赛博朋克"]`；最多 10 个，每项最长 30 字。**`blog_type=0` 可不传（存 `[]`）；`blog_type=1` 必传且至少 1 个有效标签**
- `copyright_type` (int, 可选): 版权性质，默认 `0`。取值：
  - `0` = 未设定
  - `1` = 原创
  - `2` = 转载
  - `3` = 合作
  - `4` = AI生成
  - `5` = 其他  
  **`blog_type=0` 可不传（存 `0`）；`blog_type=1` 必传且须为 `1`–`5`**
- `is_gore` (int, 可选): 内容分级，默认 `0`；`0`=全年龄，`1`=恶心猎奇（仅显式传 `1` 时标记为恶心猎奇）

**说明**:
- 投稿成功后会清空该用户的动态草稿（`blog_draft`）
- 正式投稿不从草稿表一键发布，需客户端带齐参数调用本接口

**成功响应**:
```json
{
  "status": "success",
  "if_add_experience": 1,
  "if_warn": 0
}
```

**响应字段说明**:
- `if_add_experience`: 本次是否获得经验值
- `if_warn`: 是否因敏感词进入审核，`1` 表示进入审核队列，`0` 表示直接通过

**错误码**:
- `missing_argument`
- `error_token`
- `error_vid`
- `error_type`: `blog_type` / `copyright_type` / `is_gore` 非法
- `copyright_required`: `blog_type=1` 时未提供有效 `copyright_type`（须 1–5）
- `tag_required`: `blog_type=1` 时未提供有效 `tag`
- `error_tag` / `tag_too_many`: 标签格式非法或超过 10 个
- `title_too_long`
- `content_too_long`
- `title_too_short`
- `content_too_short`
- `channel_not_found`
- `not_channel_member`
- `channel_section_not_found`
- `channel_section_not_belong_to_channel`
- `system_error`

**HTTP状态码**:
- `200`: 提交成功
- `400`: 参数错误
- `401`: Token 无效
- `500`: 系统错误

---

## 动态列表

### 4. 随机动态列表

**请求**: `GET /api/blog/random?num={num}`

**请求参数** (Query):
- `num` (int, 必需): 返回数量，最大 24
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**成功响应**:
```json
{
  "status": "success",
  "blog_list": [
    {
      "bid": 1001,
      "uid": 23,
      "title": "标题",
      "content": "内容",
      "time": "2026-04-18 12:00:00",
      "like_count": 11,
      "favorite_count": 4,
      "view_count": 120,
      "avatar_url": "https://example.com/avatar.jpg",
      "comment_count": 8,
      "thumbnails": ["https://example.com/1.jpg"],
      "attached_vid": 123,
      "is_gore": 0
    }
  ]
}
```

**错误码**:
- `missing_argument`
- `error_type`
- `too_big_num`
- `system_error`

---

### 5. 最新动态列表

**请求**: `GET /api/blog/latest?offset={offset}&num={num}`

**请求参数** (Query):
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**成功响应字段**:
- `blog_list`: 与随机动态列表结构一致

**错误码**:
- `missing_argument`
- `error_type`
- `too_big_num`
- `system_error`

---

### 6. 热门动态列表

**请求**: `GET /api/blog/popular?time_limit={time_limit}&offset={offset}&num={num}`

**请求参数** (Query):
- `time_limit` (int, 必需): 统计天数范围
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**排序说明**:
- 在 `time_limit` 天内、审核通过且未删除的动态中排序
- 热度分 = `0.5 * 归一化(独立评论人数) + 0.3 * 归一化(点赞数) + 0.2 * 归一化(浏览量)`
- 独立评论人数：`blog_comments` 中 `is_deleted=0` 且 `audit_status=1` 的 `COUNT(DISTINCT uid)`（同一人多次评论只计 1）
- 归一化：相对本时间窗候选集该指标最大值缩放到 `[0,1]`（避免浏览量数量级碾压评论/点赞）
- 同分时按 `bid` 降序

**成功响应字段**:
- `blog_list`: 与随机动态列表结构一致（`comment_count` 仍为评论总条数，与排序用的独立评论人数不同）

**错误码**:
- `missing_argument`
- `error_type`
- `too_big_num`
- `system_error`

---

### 7. 搜索动态

**请求**: `GET /api/blog/search?search_term={search_term}&offset={offset}&num={num}&bid_desc={bid_desc}&view_count_desc={view_count_desc}&like_count_desc={like_count_desc}&favorite_count_desc={favorite_count_desc}&uid={uid}&tag={tag}&is_gore={is_gore}`

**请求参数** (Query):
- `search_term` (string, 必需): 搜索词（可为空字符串，表示不限制关键词）。非空时在 `title`、`content`、`tag`（JSON）中模糊匹配
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24
- `bid_desc` (int, 可选): 按动态 ID 降序排序，默认 0
- `view_count_desc` (int, 可选): 按阅读量降序排序，默认 0
- `like_count_desc` (int, 可选): 按点赞数降序排序，默认 0
- `favorite_count_desc` (int, 可选): 按收藏数降序排序，默认 0
- `uid` (int, 可选): 仅搜索指定用户发布的动态
- `tag` (string, 可选): 按作者标签精确筛选（可多标签，全部命中）。支持 JSON 数组如 `["风景","赛博朋克"]`，或逗号分隔如 `风景,赛博朋克`。不传则不过滤（兼容旧客户端）
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**排序说明**:
- 仅一个排序参数为 `1` 时，按该字段单独排序
- 多个排序参数为 `1` 时，按阅读量 → 点赞数 → 收藏数 → 动态 ID 的组合顺序排序
- 均未指定时：有搜索词则按标题 → 标签 → 正文匹配优先，再按阅读量、动态 ID；无搜索词则按阅读量、动态 ID

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "blog_list": [
      {
        "bid": 1001,
        "uid": 23,
        "title": "标题",
        "content": "内容",
        "time": "2026-04-18 12:00:00",
        "like_count": 11,
        "favorite_count": 4,
        "view_count": 120,
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg",
        "comment_count": 3,
        "thumbnails": ["https://example.com/1.jpg"]
      }
    ],
    "total_count": 56
  }
}
```

**响应字段说明**:
- `blog_list`: 符合条件的动态列表
- `total_count`: 符合条件的动态总数（用于分页）
- `comment_count`: 该动态已通过审核的评论数

**错误码**:
- `missing_argument`: 缺少 `search_term`、`offset` 或 `num`
- `error_type`: 参数类型错误
- `error_tag`: `tag` 参数格式非法或标签过多
- `too_big_num`: 数量超过 24

**HTTP 状态码**:
- `200`: 获取成功
- `400`: 参数错误（`missing_argument`、`error_type`、`error_tag`、`too_big_num`）

---

### 8. 指定动态

**请求**: `GET /api/blog/{bid}`

**路径参数**:
- `bid` (int, 必需): 动态ID

**成功响应字段**:
- `blog_list`: 返回单元素数组，结构与随机动态列表一致

**错误码**:
- `error_type`
- `error_bid`
- `system_error`

---

### 9. 用户动态列表

**请求**: `GET /api/blog/users/{uid}/blogs?offset={offset}&num={num}`

**路径参数**:
- `uid` (int, 必需): 用户ID

**请求参数** (Query):
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**成功响应字段**:
- `blog_list`: 与随机动态列表结构一致

**错误码**:
- `missing_argument`
- `error_type`
- `too_big_num`
- `system_error`

---

## 动态详情与推荐

### 11. 动态详情

**请求**: `GET /api/blog/{bid}/detail?token={token}`

**路径参数**:
- `bid` (int, 必需): 动态ID

**请求参数** (Query):
- `token` (string, 可选): 登录 token

**可见性**:
- 默认仅返回 `is_deleted=0` 且 `audit_status=1` 的动态
- 若 `token` 为作者本人，可查看未删除但未过审（`audit_status≠1`）的自己的动态，便于编辑预览
- 已删除动态仍不可见
- 作者自看待审内容不计浏览量；响应额外返回 `audit_status`

**成功响应**:
```json
{
  "status": "success",
  "bid": 1001,
  "uid": 23,
  "title": "标题",
  "content": "正文",
  "time": "2026-04-18 12:00:00",
  "like_count": 11,
  "favorite_count": 4,
  "view_count": 120,
  "avatar_url": "https://example.com/avatar.jpg",
  "username": "otto",
  "comment_count": 8,
  "if_like": 1,
  "if_favorite": 0,
  "thumbnails": ["https://example.com/1.jpg"],
  "channel_id": 6,
  "attached_vid": 123,
  "channel_detail": {
    "channel_id": 6,
    "channel_name": "tech",
    "channel_title": "技术频道",
    "channel_description": "频道描述",
    "channel_cover_url": "https://example.com/channel.jpg"
  },
  "blog_type": 0,
  "tag": ["风景", "赛博朋克"],
  "copyright_type": 0,
  "is_gore": 0,
  "audit_status": 1
}
```

**新增响应字段**（旧客户端可忽略）:
- `blog_type` (int): `0`=普通动态，`1`=静画
- `tag` (array): 作者标签字符串数组，无标签时为 `[]`
- `copyright_type` (int): `0`=未设定，`1`=原创，`2`=转载，`3`=合作，`4`=AI生成，`5`=其他
- `is_gore` (int): `0`=全年龄，`1`=恶心猎奇
- `audit_status` (int): 审核状态（作者看待审时有用）

**错误码**:
- `error_type`
- `error_bid`
- `system_error`

---

### 12. 编辑动态元信息

**请求**: `PUT /api/blog/{bid}`（亦支持 `PATCH`）

**路径参数**:
- `bid` (int, 必需): 动态ID

**请求参数** (Body, JSON):
- `token` (string, 必需): 作者 token
- `tag` (string/array, 可选): 作者标签 JSON 数组，如 `["风景","赛博朋克"]`；最多 10 个，每项最长 30 字
- `is_gore` (int, 可选): `0`=全年龄，`1`=恶心猎奇
- `copyright_type` (int, 可选): `0`–`5`（含义同投稿）
- `blog_type` (int, 可选): `0`=普通动态，`1`=静画

至少传入上述可编辑字段之一。标题/正文/封面等暂不开放。

**校验**（与投稿一致，按合并后的最终值）:
- `blog_type=1` 时：`copyright_type` 须为 `1`–`5`，且至少 1 个有效 `tag`
- 修改成功后 `audit_status` 置为 `0`（重新待审）

**请求示例**:
```json
{
  "token": "abc123...",
  "tag": ["风景"],
  "is_gore": 0,
  "copyright_type": 1,
  "blog_type": 1
}
```

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "bid": 1001,
    "blog_type": 1,
    "tag": ["风景"],
    "copyright_type": 1,
    "is_gore": 0,
    "audit_status": 0
  }
}
```

**错误码**:
- `error_token`: Token 无效
- `error_type` / `error_bid` / `missing_argument`
- `error`: 非本人动态
- `copyright_required` / `tag_required` / `error_tag` / `tag_too_many`
- `system_error`

---

### 13. 相关推荐动态

**请求**: `GET /api/blog/related/{bid}?num={num}&offset={offset}`

**路径参数**:
- `bid` (int, 必需): 参考动态ID

**请求参数** (Query):
- `num` (int, 必需): 返回数量，最大 24
- `offset` (int, 可选): 偏移量，默认 0
- `is_gore` (int, 可选): 内容分级筛选；不传则不过滤；`0`=仅全年龄，`1`=仅恶心猎奇

**成功响应**:
```json
{
  "status": "success",
  "blog_list": [
    {
      "bid": 1002,
      "uid": 24,
      "title": "相关标题",
      "content": "相关内容",
      "time": "2026-04-17 10:00:00",
      "like_count": 8,
      "favorite_count": 2,
      "view_count": 99,
      "avatar_url": "https://example.com/avatar2.jpg",
      "username": "alice",
      "comment_count": 3,
      "thumbnails": ["https://example.com/2.jpg"],
      "similarity_score": 0.73,
      "attached_vid": 123
    }
  ]
}
```

**错误码**:
- `missing_argument`
- `error_type`
- `too_big_num`
- `system_error`

---

## 个人动态列表

### 12.5 历史动态列表

**请求**: `GET /api/blog/history-list`

**请求参数** (Query):
- `token` (string, 必需): 用户 token
- `offset` (int, 可选): 偏移量，默认 `0`
- `num` (int, 可选): 返回数量，默认 `20`，最大 `24`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "blog_list": [
      {
        "bid": 1001,
        "uid": 23,
        "title": "标题",
        "content": "内容",
        "time": "2026-04-18 12:00:00",
        "like_count": 11,
        "favorite_count": 4,
        "view_count": 120,
        "avatar_url": "https://example.com/avatar.jpg",
        "comment_count": 3,
        "thumbnails": ["https://example.com/1.jpg"],
        "attached_vid": 0,
        "username": "用户名",
        "last_opened_at": "2026-07-18 20:00:00"
      }
    ]
  }
}
```

**响应字段说明**:
- `last_opened_at`: 最近一次打开该动态详情的时间

**HTTP状态码**:
- `200`: 获取成功
- `401`: Token无效或未提供
- `400`: `error_type` / `too_big_num`
- `500`: 系统错误

---

### 13. 收藏动态列表

**请求**: `GET /api/blog/favorite-list?token={token}&offset={offset}&num={num}`

**请求参数** (Query):
- `token` (string, 必需): 用户 token
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "favorite_blog_count": 10,
    "blog_list": [
      {
        "bid": 1001,
        "uid": 23,
        "title": "标题",
        "content": "内容",
        "time": "2026-04-18 12:00:00",
        "like_count": 11,
        "favorite_count": 4,
        "view_count": 120,
        "is_deleted": 0,
        "audit_status": 1,
        "avatar_url": "https://example.com/avatar.jpg",
        "thumbnails": ["https://example.com/1.jpg"],
        "is_gore": 0
      }
    ]
  }
}
```

**响应字段说明**:
- `favorite_blog_count`: 收藏动态总数
- `blog_list`: 收藏动态列表；若动态已删除或未过审，标题与内容会显示为占位文案
- `is_gore`: `0`=全年龄，`1`=恶心猎奇

**错误码**:
- `missing_argument`: 缺少 `offset` 或 `num`
- `error_token`: Token 无效或未提供
- `error_uid`: 用户不存在
- `too_big_num`: 数量超过 24
- `system_error`: 系统错误

**HTTP 状态码**:
- `200`: 获取成功
- `400`: 参数错误（`missing_argument`、`too_big_num`、`error_uid`）
- `401`: Token 无效（`error_token`）
- `500`: 系统错误（`system_error`）

---

### 14. 管理动态列表

**请求**: `GET /api/blog/manage-list?token={token}&offset={offset}&num={num}`

**请求参数** (Query):
- `token` (string, 必需): 用户 token
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 24

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "manage_blog_count": 20,
    "blog_list": [
      {
        "bid": 1001,
        "uid": 23,
        "title": "标题",
        "content": "内容",
        "time": "2026-04-18 12:00:00",
        "like_count": 11,
        "favorite_count": 4,
        "view_count": 120,
        "is_deleted": 0,
        "audit_status": 1,
        "avatar_url": "https://example.com/avatar.jpg",
        "thumbnails": ["https://example.com/1.jpg"],
        "collection": "合集名称",
        "collection_sort_order": 0,
        "channel_id": 6,
        "is_gore": 0,
        "channel_detail": {
          "channel_id": 6,
          "channel_name": "tech",
          "channel_title": "技术频道",
          "description": "频道描述",
          "cover_url": "https://example.com/channel.jpg"
        }
      }
    ]
  }
}
```

**响应字段说明**:
- `manage_blog_count`: 当前用户发布的动态总数（未删除）
- `blog_list`: 当前用户发布的动态列表，按 `bid` 降序
- `channel_detail`: 若动态不属于有效频道，各字段为空字符串

**错误码**:
- `missing_argument`: 缺少 `offset` 或 `num`
- `error_token`: Token 无效或未提供
- `error_uid`: 用户不存在
- `too_big_num`: 数量超过 24
- `system_error`: 系统错误

**HTTP 状态码**:
- `200`: 获取成功
- `400`: 参数错误（`missing_argument`、`too_big_num`、`error_uid`）
- `401`: Token 无效（`error_token`）
- `500`: 系统错误（`system_error`）

---

## 互动与管理

### 15. 收藏/取消收藏动态

**请求**: `POST /api/blog/favorite/{bid}`

**请求参数** (Path):
- `bid` (int, 必需): 动态 ID

**请求参数** (Body):
- `token` (string, 必需): 用户 token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "if_favorite": 1,
    "favorite_count": 12
  }
}
```

**错误码**:
- `error_token`: Token 无效或未提供
- `error_type`: 动态 ID 类型错误
- `error_bid`: 动态不存在
- `error_uid`: 用户不存在
- `blog_deleted`: 动态已删除，无法收藏
- `system_error`: 系统错误

**HTTP 状态码**:
- `200`: 操作成功
- `400`: 参数错误（`error_type`、`error_bid`、`error_uid`、`blog_deleted`）
- `401`: Token 无效（`error_token`）
- `500`: 系统错误（`system_error`）

---

### 16. 点赞/取消点赞动态

**请求**: `POST /api/blog/like/{bid}`

**请求参数** (Path):
- `bid` (int, 必需): 动态 ID

**请求参数** (Body):
- `token` (string, 必需): 用户 token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "if_like": 1,
    "like_count": 25
  }
}
```

**错误码**:
- `error_token`: Token 无效或未提供
- `error_type`: 动态 ID 类型错误
- `error_bid`: 动态不存在
- `error_uid`: 用户不存在
- `system_error`: 系统错误

**HTTP 状态码**:
- `200`: 操作成功
- `400`: 参数错误（`error_type`、`error_bid`、`error_uid`）
- `401`: Token 无效（`error_token`）
- `500`: 系统错误（`system_error`）

---

### 17. 删除动态

**请求**: `DELETE /api/blog/{bid}`

**请求参数** (Path):
- `bid` (int, 必需): 动态 ID

**请求参数** (Body):
- `token` (string, 必需): 用户 token

**成功响应**:
```json
{
  "status": "success"
}
```

**错误码**:
- `error_token`: Token 无效或未提供
- `error_type`: 动态 ID 类型错误
- `error_uid`: 用户不存在
- `error`: 无权限删除该动态（非本人或已删除）
- `system_error`: 系统错误

**HTTP 状态码**:
- `200`: 删除成功
- `400`: 参数错误（`error_type`、`error_uid`）
- `401`: Token 无效（`error_token`）
- `403`: 无权限删除（`error`）
- `500`: 系统错误（`system_error`）

**说明**:
- 删除为软删除，并会清空该动态所属合集信息；若原属于某合集，会重新排列合集内其余动态的排序序号
