# Video 视频模块 API 文档

## 概述

视频模块提供视频的获取、搜索、收藏、点赞、删除、草稿投稿与 R2 更新等功能。旧轨 multipart 投稿/修改（`submit`、`update`）已下线。

**基础信息**:
- **基础路径**: `/api/video`
- **请求格式**: JSON（POST）或查询参数（GET）
- **响应格式**: JSON
- **认证方式**: 部分接口通过 `token` 参数传递（GET请求）或请求体（POST请求）
- **字符编码**: UTF-8

## 通用响应格式

**成功响应**:
```json
{
  "status": "success",
  "data": { ... }
}
```

**列表类接口**：视频列表统一使用嵌套格式，`data` 内包含 `video_list` 数组；若接口带总数或分页信息，则同时包含 `total_count`、`favorite_video_count`、`manage_video_count` 等字段。

**错误响应**:
```json
{
  "status": "error",
  "message": "错误信息代码"
}
```

## 通用错误码

- `missing_argument`: 缺少必需参数
- `error_token`: Token无效或已过期
- `system_error`: 系统错误
- `error_type`: 参数类型错误
- `error_uid`: 用户ID无效
- `error_vid`: 视频ID无效

---

## 视频获取

### 1. 随机视频列表

**请求**: `GET /api/video/random?num={num}`

**请求参数** (Query):
- `num` (int, 可选): 视频数量，默认20

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 2. 最新视频列表

**请求**: `GET /api/video/new?offset={offset}&num={num}&type={type}`

**请求参数** (Query):
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20
- `type` (string, 可选): 视频类型，默认all

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 3. 热门视频列表

**请求**: `GET /api/video/popular?time_limit={time_limit}&offset={offset}&num={num}`

**请求参数** (Query):
- `time_limit` (int, 可选): 时间限制（天数），默认7
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 4. 分类视频列表

**请求**: `GET /api/video/category/{category}?num={num}`

**请求参数** (Path):
- `category` (string, 必需): 视频分类

**请求参数** (Query):
- `num` (int, 可选): 视频数量，默认20

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 5. 搜索视频列表

**请求**: `GET /api/video/search?search_term={search_term}&offset={offset}&num={num}&...`

**请求参数** (Query):
- `search_term` (string, 可选): 搜索关键词
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20
- `vid_desc` (int, 可选): 按视频ID降序排序，默认0
- `view_count_desc` (int, 可选): 按观看次数降序排序，默认0
- `like_count_desc` (int, 可选): 按点赞次数降序排序，默认0
- `favorite_count_desc` (int, 可选): 按收藏次数降序排序，默认0
- `uid` (int, 可选): 用户ID
- `type` (string, 可选): 视频类型

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg",
        "intro": "简介",
        "tag": "标签",
        "collection": "合集",
        "type": 1,
        "category": "分类",
        "duration": 120
      }
    ],
    "total_count": 100
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 6. 获取视频详情

**请求**: `GET /api/video/{vid}?token={token}&no_history={no_history}`

**请求参数** (Path):
- `vid` (int, 必需): 视频ID

**请求参数** (Query):
- `token` (string, 可选): 用户Token
- `no_history` (int, 可选): 是否不计入历史记录，0-计入（默认），1-不计入

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "vid": 1,
    "uid": 123,
    "title": "视频标题",
    "time": "2023-01-01 00:00:00",
    "like_count": 100,
    "favorite_count": 50,
    "view_count": 1000,
    "is_deleted": 0,
    "audit_status": 1,
    "cover_url": "https://example.com/cover.jpg",
    "username": "用户名",
    "avatar_url": "https://example.com/avatar.jpg",
    "if_like": 0,
    "if_favorite": 0
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `400`: 参数错误
- `500`: 系统错误

**注意**:
- `no_history` 为 1 时，视频播放量正常计算，但不会计入用户的历史记录
- 适用于后台播放、预览等场景
- 默认仅返回已通过审核（`audit_status=1`）的视频；作者携带本人 `token` 时可查看自己的待审核视频（`audit_status=0`），方便提交后在待审期间编辑；待审核自审预览不计入历史记录与播放量

---

### 7. 用户视频列表

**请求**: `GET /api/video/user/{uid}?offset={offset}&num={num}`

**请求参数** (Path):
- `uid` (int, 必需): 用户ID

**请求参数** (Query):
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `400`: 参数错误
- `500`: 系统错误

---

### 8. 相关视频列表

**请求**: `GET /api/video/related/{vid}?num={num}&offset={offset}`

**请求参数** (Path):
- `vid` (int, 必需): 视频ID

**请求参数** (Query):
- `num` (int, 可选): 视频数量，默认20
- `offset` (int, 可选): 偏移量，默认0

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `500`: 系统错误

---

### 9. 收藏视频列表

**请求**: `GET /api/video/favorite-list?offset={offset}&num={num}`

**请求参数** (Query):
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20
- `token` (string, 必需): 用户Token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ],
    "favorite_video_count": 10
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `401`: Token无效或未提供
- `500`: 系统错误

---

### 10. 管理视频列表

**请求**: `GET /api/video/manage-list?offset={offset}&num={num}`

**请求参数** (Query):
- `offset` (int, 可选): 偏移量，默认0
- `num` (int, 可选): 视频数量，默认20
- `token` (string, 必需): 用户Token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "is_deleted": 0,
        "audit_status": 1,
        "cover_url": "https://example.com/cover.jpg",
        "collection": "合集名称",
        "collection_sort_order": 0,
        "channel_id": 1,
        "channel_detail": {
          "channel_id": 1,
          "channel_name": "频道名称",
          "channel_title": "频道标题",
          "description": "频道描述",
          "cover_url": "https://example.com/channel_cover.jpg"
        }
      }
    ],
    "manage_video_count": 20
  }
}
```

**HTTP状态码**:
- `200`: 获取成功
- `401`: Token无效或未提供
- `500`: 系统错误

---

### 11. 历史视频列表

**请求**: `GET /api/video/history-list`

**请求参数** (Query):
- `token` (string, 必需): 用户Token
- `offset` (int, 可选): 偏移量，默认 `0`（旧客户端不传亦可）
- `num` (int, 可选): 返回数量，默认 `30`，最大 `30`（旧客户端不传亦可）

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "video_list": [
      {
        "vid": 1,
        "uid": 123,
        "title": "视频标题",
        "time": "2023-01-01 00:00:00",
        "like_count": 100,
        "favorite_count": 50,
        "view_count": 1000,
        "cover_url": "https://example.com/cover.jpg",
        "username": "用户名",
        "avatar_url": "https://example.com/avatar.jpg",
        "duration": 120,
        "last_watch_second": 65,
        "last_opened_at": "2026-07-18 20:00:00"
      }
    ]
  }
}
```

**响应字段说明**:
- `duration`: 视频总时长（秒），可与 `last_watch_second` 计算观看进度
- `last_watch_second`: 最后观看到的秒数；**-1 表示已看完**，0 表示从头，正整数为进度秒数
- `last_opened_at`: 最近一次打开该视频详情的时间（列表按此字段倒序）

**HTTP状态码**:
- `200`: 获取成功
- `401`: Token无效或未提供
- `400`: `error_type` / `too_big_num`
- `500`: 系统错误

---

## 视频操作

### 12. 保存视频观看历史

**请求**: `POST /api/video/watch-history`

**请求参数** (Body，JSON 或 form)：
- `token` (string, 必需): 用户 Token
- `vid` (int, 必需): 视频 ID（VID）
- `last_watch_second` (int, 必需): 最后观看到的秒数；**-1 表示已看完**，0 表示从头开始，正整数表示当前播放到的秒数

**成功响应**:
```json
{
  "status": "success"
}
```

**错误码**:
- `missing_argument`: 缺少 vid 或 last_watch_second 或 token
- `error_token`: Token 无效或已过期
- `error_type`: vid / last_watch_second 非数字
- `error_vid`: 视频不存在

**HTTP状态码**:
- `200`: 保存成功
- `400`: 参数错误（缺少参数、类型错误、视频不存在）
- `401`: Token 无效或未提供
- `500`: 系统错误

---

### 13. 收藏/取消收藏视频

**请求**: `POST /api/video/favorite/{vid}`

**请求参数** (Path):
- `vid` (int, 必需): 视频ID

**请求参数** (Body):
- `token` (string, 必需): 用户Token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "if_favorite": 1,
    "favorite_count": 51
  }
}
```

**HTTP状态码**:
- `200`: 操作成功
- `400`: 参数错误
- `401`: Token无效或未提供
- `500`: 系统错误

---

### 14. 点赞/取消点赞视频

**请求**: `POST /api/video/like/{vid}`

**请求参数** (Path):
- `vid` (int, 必需): 视频ID

**请求参数** (Body):
- `token` (string, 必需): 用户Token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "if_like": 1,
    "like_count": 101
  }
}
```

**HTTP状态码**:
- `200`: 操作成功
- `400`: 参数错误
- `401`: Token无效或未提供
- `500`: 系统错误

---

### 15. 删除视频

**请求**: `DELETE /api/video/{vid}`

**请求参数** (Path):
- `vid` (int, 必需): 视频ID

**请求参数** (Body):
- `token` (string, 必需): 用户Token

**成功响应**:
```json
{
  "status": "success"
}
```

**HTTP状态码**:
- `200`: 删除成功
- `401`: Token无效或未提供
- `403`: 无权限删除该视频
- `500`: 系统错误

---

### 16. 获取视频预上传地址

**请求**: `GET /api/video/video-presigned?token={token}&extension={extension}`

**请求参数** (Query):
- `token` (string, 必需): 用户 Token
- `extension` (string, 必需): 文件后缀，支持 `mp4`、`mov`、`m4v`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "url": "https://example.r2.cloudflarestorage.com/bucket/video/video_video/aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
    "file_name": "aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2",
    "path": "/video/video_video/aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2",
    "video_index": 5,
    "max_file_size": 419430400
  }
}
```

**响应字段说明**:
- `url`: Cloudflare R2 预签名上传地址
- `file_name`: 加密后的文件名（存储在 R2 中的名称）
- `path`: 无域名路径，写入草稿 `video_url` 时使用
- `video_index`: 本次使用的投稿序号（有草稿冻结值则沿用）
- `max_file_size`: 最大文件大小限制（字节）

**错误码**:
- `missing_argument`: 缺少参数
- `error_token`: Token 无效或已过期
- `invalid_file_extension`: 文件格式不支持（仅支持 mp4 / mov / m4v）
- `system_error`: 系统错误

**HTTP状态码**:
- `200`: 获取成功
- `400`: 参数错误
- `401`: Token 无效或未提供
- `500`: 系统错误

---

### 17. 获取封面预上传地址

**请求**: `GET /api/video/cover-presigned?token={token}&extension={extension}`

**请求参数** (Query):
- `token` (string, 必需): 用户 Token
- `extension` (string, 必需): 文件后缀，支持 `jpg`, `jpeg`, `png`, `gif`, `webp`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "url": "https://example.r2.cloudflarestorage.com/bucket/video/video_cover/XyZ7wV6uT5sR4qP3oN2mM1lK0jI9hG8f?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
    "file_name": "XyZ7wV6uT5sR4qP3oN2mM1lK0jI9hG8f",
    "path": "/video/video_cover/XyZ7wV6uT5sR4qP3oN2mM1lK0jI9hG8f",
    "video_index": 5,
    "max_file_size": 3145728
  }
}
```

**响应字段说明**:
- `url`: Cloudflare R2 预签名上传地址
- `file_name`: 加密后的文件名（存储在 R2 中的名称）
- `path`: 无域名路径，写入草稿 `cover_url` 时使用
- `video_index`: 本次使用的投稿序号（有草稿冻结值则沿用）
- `max_file_size`: 最大文件大小限制（字节）

**错误码**:
- `missing_argument`: 缺少参数
- `error_token`: Token 无效或已过期
- `invalid_file_extension`: 文件格式不支持
- `system_error`: 系统错误

**HTTP状态码**:
- `200`: 获取成功
- `400`: 参数错误
- `401`: Token 无效或未提供
- `500`: 系统错误

---

## 视频草稿（R2 直传配套）

每用户仅允许一条草稿。`video_url` / `cover_url` 仅接受**无域名的路径**（如 `/video/video_video/xxx`），且须符合预上传命名算法。首次写入文件路径时冻结 `video_index`；校验与预签名均使用冻结序号。发布时若冻结序号已不等于当前 `COUNT+1`（例如期间又走了旧轨投稿），返回 `index_conflict`，需清空草稿文件字段后重新直传。

`duration`：前端本地探测的视频时长（秒，整数）。草稿阶段可与 `video_url` 一并写入（允许暂为 0）；**发布时必须为 1–28800**，否则 `error_duration`。清空 `video_url` 时服务端会将 `duration` 置 0。

### 18. 获取视频草稿

**请求**: `GET /api/video/draft?token={token}`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "draft_id": 1,
    "uid": 123,
    "title": "标题",
    "intro": "简介",
    "type": 1,
    "category": 0,
    "tag": "#标签",
    "channel_id": 0,
    "channel_section_id": 0,
    "video_url": "/video/video_video/xxxx",
    "cover_url": "/video/video_cover/yyyy",
    "video_preview_url": "https://file.ottohub.org/video/video_video/xxxx?t=1720000000",
    "cover_preview_url": "https://file.ottohub.org/video/video_cover/yyyy?t=1720000000",
    "video_index": 5,
    "duration": 125,
    "created_at": "2026-07-13 12:00:00",
    "updated_at": "2026-07-13 12:00:00"
  }
}
```

**错误码**: `error_token`、`draft_not_found`、`system_error`

**HTTP状态码**: `200` / `400` / `401` / `500`

---

### 19. 保存视频草稿

**请求**: `POST /api/video/draft`

**请求参数** (Body, JSON，字段均可选；有值则校验):
- `token` (string, 必需)
- `title`、`intro`、`type`、`category`、`tag`、`channel_id`、`channel_section_id`
- `video_url`、`cover_url`：无域名路径；非空时校验算法
- `duration` (int, 可选): 视频时长（秒）；有 `video_url` 时建议一并写入；合法范围 0–28800（0 表示尚未探测）

**成功响应**:
```json
{
  "status": "success",
  "data": { "draft_id": 1 }
}
```

**错误码**:
- `draft_exists`: 已有草稿，请先修改或删除
- `error_video_url` / `error_cover_url`: 路径不合法或不符合预上传算法
- `error_duration`: 时长非法
- `title_too_long` / `intro_too_long` / `error_type` / `error_category` / `tag_too_many` / `error_tag`
- `channel_not_found` / `not_channel_member` / `channel_section_not_found` / `channel_section_not_belong_to_channel`
- `error_token` / `system_error`

**HTTP状态码**: `200` / `400` / `401` / `500`

---

### 20. 修改视频草稿

**请求**: `PUT /api/video/draft`（亦支持 `PATCH`）

**说明**: 必须已有草稿；未传入的字段保持原值；传入的 `video_url` / `cover_url` 仍做算法校验。

**成功响应**:
```json
{ "status": "success" }
```

**错误码**: `draft_not_found`，其余同保存草稿

**HTTP状态码**: `200` / `400` / `401` / `500`

---

### 21. 删除视频草稿

**请求**: `DELETE /api/video/draft`

**请求参数**: Body 或 Query 中的 `token`

**成功响应**:
```json
{ "status": "success" }
```

**错误码**: `error_token`、`draft_not_found`、`system_error`

**HTTP状态码**: `200` / `400` / `401` / `500`

---

### 22. 发布视频草稿

**请求**: `POST /api/video/draft/publish`

**说明**:
- 将草稿写入 `video` 表并分配 `vid`，写入 `video_url`、`cover_url`、`duration`
- 发布时 `video_url`、`cover_url` 必填且须通过算法校验
- 发布时草稿 `duration` 必须为 **1–28800**（前端上传前探测并写入草稿），否则 `error_duration`
- `type` 须为 1/2/3；标题/简介/标签为空时使用与旧投稿相同的默认文案
- 成功后删除该用户草稿（旧轨 `submit` 已下线，投稿请走本接口）

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "vid": 123,
    "if_add_experience": 1
  }
}
```

**错误码**: `draft_not_found`、`error_video_url`、`error_cover_url`、`error_duration`、`error_type`、`index_conflict` 等，以及 `error_token`、`system_error`

**说明（index_conflict）**: 草稿冻结的 `video_index` 与当前 `COUNT(video)+1` 不一致（例如草稿期间他人已发布占号）。处理：PUT 草稿将 `video_url`/`cover_url` 置空（解冻），重新取预签名并上传后再 publish。

**HTTP状态码**: `200` / `400` / `401` / `500`

---

## 视频信息更新（R2）

旧轨 `POST /api/video/update/{vid}`（multipart + COS）已下线。文字规则相同；换封面/视频时先拿对应预签名直传 R2，再把返回的无域名 `path` 写入本接口。

预签名按「该视频是此用户第几次投稿」计算（`COUNT(video WHERE uid=? AND vid<=?)`，与投稿时 `COUNT+1` 一致）。每次预签名因加密随机 IV 会得到新对象名，更新后以新路径为准。

### 23. 修改用视频预上传地址

**请求**: `GET /api/video/update-video-presigned?token={token}&vid={vid}&extension={extension}`

**请求参数** (Query):
- `token` (string, 必需)
- `vid` (int, 必需): 要修改的视频 ID（须本人所有）
- `extension` (string, 必需): `mp4` / `mov` / `m4v`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "url": "https://....r2.cloudflarestorage.com/...?X-Amz-Algorithm=...",
    "file_name": "aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2",
    "path": "/video/video_video/aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2",
    "video_index": 5,
    "max_file_size": 419430400
  }
}
```

**说明**: 客户端 `PUT` 到 `url` 上传完成后，将 `path` 作为 `video_url` 传给 `update-r2`。

**错误码**: `error_vid`、`error_token`、`video_not_found_or_not_owned`、`invalid_file_extension`、`system_error`

**HTTP状态码**: `200` / `400` / `401` / `403` / `500`

---

### 24. 修改用封面预上传地址

**请求**: `GET /api/video/update-cover-presigned?token={token}&vid={vid}&extension={extension}`

**请求参数** (Query):
- `token` (string, 必需)
- `vid` (int, 必需)
- `extension` (string, 必需): `jpg` / `jpeg` / `png` / `gif` / `webp`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "url": "https://....r2.cloudflarestorage.com/...?X-Amz-Algorithm=...",
    "file_name": "XyZ7wV6uT5sR4qP3oN2mM1lK0jI9hG8f",
    "path": "/video/video_cover/XyZ7wV6uT5sR4qP3oN2mM1lK0jI9hG8f",
    "video_index": 5,
    "max_file_size": 3145728
  }
}
```

**说明**: 上传完成后将 `path` 作为 `cover_url` 传给 `update-r2`。

**错误码**: 同修改用视频预上传

**HTTP状态码**: `200` / `400` / `401` / `403` / `500`

---

### 25. 视频信息更新（R2）

**请求**: `POST /api/video/update-r2/{vid}`

**请求参数** (Body, JSON；字段均可选，传入则更新):
- `token` (string, 必需)
- `title` (string, 可选): 最长 100；空则默认「点击输入标题」
- `intro` (string, 可选): 最长 2000；空则默认「这个人写了一条滚木。」
- `tag` (string, 可选): 最多 10 个 `#`；空则默认 `#滚木`
- `category` (int, 可选): 0–7（`2` 会归一为 `1`）
- `copyright_type` (int, 可选): 版权性质，仅允许 `1` / `2` / `3`（写入库字段 `type`）；**不传则不修改**（兼容旧客户端）
- `cover_url` (string, 可选): 无域名路径，须来自 `update-cover-presigned` 的 `path`，且符合该视频的序号算法
- `video_url` (string, 可选): 无域名路径，须来自 `update-video-presigned` 的 `path`
- `duration` (int, **换源时必需**): 新视频时长（秒，1–28800）；仅在传 `video_url` 时使用，禁止单独改时长

**成功响应**:
```json
{
  "status": "success"
}
```

**说明**:
- 仅作者可更新
- 写入 `cover_url` / `video_url` 时自动 `cover_version` / `video_version` +1；换 `video_url` 时同步写入 `duration`
- 修改标题/简介/标签/版权性质/封面/视频后进入待审核（`audit_status=0`）；**仅改分区不触发重新审核**

**错误码**:
- `error_token` / `error_vid` / `video_not_found_or_not_owned`
- `title_too_long` / `intro_too_long` / `tag_too_many` / `error_tag` / `error_category` / `error_type`
- `error_cover_url` / `error_video_url` / `error_duration`
- `system_error`

**HTTP状态码**: `200` / `400` / `401` / `403` / `500`

---

## 接口使用流程示例

### 获取视频列表流程

1. **获取最新视频**
   ```
   GET /api/video/new?offset=0&num=20
   ```

2. **获取热门视频**
   ```
   GET /api/video/popular?time_limit=7&offset=0&num=20
   ```

3. **搜索视频**
   ```
   GET /api/video/search?search_term=关键词&offset=0&num=20&like_count_desc=1
   ```

### 视频交互流程

1. **获取视频详情**
   ```
   GET /api/video/123?token=abc123def456...
   ```

2. **点赞视频**
   ```
   POST /api/video/like/123
   Body: { "token": "abc123def456..." }
   ```

3. **收藏视频**
   ```
   POST /api/video/favorite/123
   Body: { "token": "abc123def456..." }
   ```

4. **保存观看历史**
   ```
   POST /api/video/watch-history
   Body: { "token": "abc123def456...", "vid": 123, "last_watch_second": 65 }
   ```
   （`last_watch_second` 为 -1 表示已看完）

### 视频投稿与更新流程

1. **投稿视频**（R2 直传 + 草稿）
   ```
   GET /api/video/video-presigned?token=...&extension=mp4
   → PUT 到返回的 url 上传视频
   GET /api/video/cover-presigned?token=...&extension=jpg
   → PUT 到返回的 url 上传封面
   POST /api/video/draft  （或 PUT /api/video/draft 更新）
   POST /api/video/draft/publish
   ```

2. **更新视频信息或封面/视频文件**（R2 直传）
   ```
   GET /api/video/update-cover-presigned?token=...&vid=123&extension=jpg
   → PUT 到返回的 url 上传封面
   GET /api/video/update-video-presigned?token=...&vid=123&extension=mp4
   → PUT 到返回的 url 上传视频（可选）
   POST /api/video/update-r2/123
   Body: { "token": "...", "title": "...", "cover_url": "/video/video_cover/xxx", "video_url": "/video/video_video/yyy", "duration": 120 }
   ```
   （仅改正文时可直接调 `update-r2`，不必先拿预签名）

### 视频管理流程

1. **获取管理视频列表**
   ```
   GET /api/video/manage-list?offset=0&num=20&token=abc123def456...
   ```

2. **删除视频**
   ```
   DELETE /api/video/123
   Body: { "token": "abc123def456..." }
   ```

3. **获取收藏视频列表**
   ```
   GET /api/video/favorite-list?offset=0&num=20&token=abc123def456...
   ```

4. **获取历史视频列表**
   ```
   GET /api/video/history-list?token=abc123def456...
   ```

---

## 安全说明

1. **认证安全**:
   - 部分接口需要提供有效的 `token` 进行身份认证
   - Token应妥善保管，不要泄露
   - Token失效后需要重新登录获取

2. **权限控制**:
   - 删除视频操作只能删除自己的视频

3. **参数验证**:
   - 所有接口都会对输入参数进行验证
   - 无效参数会返回相应的错误信息

4. **速率限制**:
   - 部分接口可能有请求频率限制
   - 超过限制会返回相应的错误信息

---

## 常见问题

**Q: 为什么获取视频详情时需要提供token？**
A: 提供token可以获取用户对该视频的点赞和收藏状态。

**Q: 为什么删除视频失败？**
A: 可能的原因包括：
   - Token无效或未提供
   - 视频不存在或已被删除
   - 无权限删除该视频（只能删除自己的视频）

**Q: 为什么收藏/点赞视频失败？**
A: 可能的原因包括：
   - Token无效或未提供
   - 视频不存在或已被删除
   - 系统错误

**Q: 如何获取更多视频？**
A: 使用 `offset` 和 `num` 参数进行分页查询。

**Q: 如何排序搜索结果？**
A: 使用 `vid_desc`、`view_count_desc`、`like_count_desc`、`favorite_count_desc` 参数进行排序。
