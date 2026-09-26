# Comment 评论模块 API 文档

## 概述

评论模块提供动态评论、视频评论的查询、发表、删除等功能。

**基础信息**:
- **基础路径**: `/api/comment`
- **请求格式**: 查询参数（GET）或 JSON 请求体（POST / DELETE）
- **响应格式**: JSON
- **认证方式**: 列表接口 `token` 可选；写入/删除接口需登录
- **字符编码**: UTF-8

## 相关模块说明

以下接口请使用 moderation 模块，详见 `moderation_api.md`：

| 功能 | 新接口 |
|------|--------|
| 审核动态评论列表 | `GET /api/moderation/blog-comments` |
| 审核视频评论列表 | `GET /api/moderation/video-comments` |
| 通过/驳回动态评论 | `PUT /api/moderation/blog-comments/{bcid}/approve` / `reject` |
| 通过/驳回视频评论 | `PUT /api/moderation/video-comments/{vcid}/approve` / `reject` |
| 举报动态评论 | `POST /api/moderation/blog-comments/{bcid}/report` |
| 举报视频评论 | `POST /api/moderation/video-comments/{vcid}/report` |

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
- `error_token`: Token 无效或已过期
- `error_bid`: 动态 ID 无效
- `error_vid`: 视频 ID 无效
- `error_bcid`: 动态评论 ID 无效
- `error_vcid`: 视频评论 ID 无效
- `error_parent_bcid`: 父动态评论无效
- `error_parent_vcid`: 父视频评论无效
- `error_parent`: 不支持楼中楼中楼（仅支持两级评论）
- `content_too_long`: 评论内容超过 459 字
- `content_too_short`: 评论内容为空
- `too_big_num`: 请求数量过大（列表最大 12）
- `too_many_requests`: 请求频率过高
- `warn`: 触发敏感词（评论进入审核）
- `blocked`: 存在拉黑关系
- `no_permission`: 无权限
- `system_error`: 系统错误
- `Not found`: 接口路径不存在
- `Method not allowed`: HTTP 方法不允许

## 通用 HTTP 状态码

| HTTP 状态码 | 说明 | 常见 `message` |
|-------------|------|----------------|
| `200` | 请求成功 | — |
| `400` | 参数或业务校验错误 | `missing_argument`、`error_bid` 等 |
| `401` | 未登录或 Token 无效 | `error_token` |
| `403` | 无权限或拉黑 | `no_permission`、`blocked` |
| `404` | 接口路径不存在 | `Not found` |
| `405` | HTTP 方法不允许 | `Method not allowed` |
| `429` | 请求过于频繁 | `too_many_requests` |
| `500` | 服务器内部错误 | `system_error` |

---

## 动态评论

### 1. 动态评论列表

**请求**: `GET /api/comment/blogs/{bid}?parent_bcid={parent_bcid}&offset={offset}&num={num}&cid_asc={cid_asc}&token={token}`

**路径参数**:
- `bid` (int, 必需): 动态 ID

**请求参数** (Query):
- `parent_bcid` (int, 必需): 父评论 ID，顶级评论传 `0`
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 12
- `cid_asc` (int, 可选): 是否按评论 ID 升序，`1` 升序，`0` 或不传为降序
- `token` (string, 可选): 登录 token，用于判断 `if_my_comment`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "comment_list": [
      {
        "bcid": 1001,
        "parent_bcid": 0,
        "uid": 23,
        "content": "评论内容",
        "time": "2026-04-18 12:00:00",
        "child_comment_num": 3,
        "if_my_comment": 1,
        "username": "昵称",
        "honour": "荣誉",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP 状态码**:
- `200`: 获取成功
- `400`: 参数错误
- `500`: 系统错误

---

### 2. 发表动态评论

**请求**: `POST /api/comment/blogs/{bid}`

**路径参数**:
- `bid` (int, 必需): 动态 ID

**请求参数** (Body, JSON):
- `token` (string, 必需): 用户认证令牌
- `parent_bcid` (int, 必需): 父评论 ID，回复动态传 `0`，回复评论传父 `bcid`
- `content` (string, 必需): 评论内容，1-459 字；`\n` 表示换行

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "if_get_experience": 1,
    "if_warn": 0
  }
}
```

**响应字段说明**:
- `if_get_experience`: 是否获得经验值（当日经验已达上限时为 `0`）
- `if_warn`: 是否因敏感词进入审核，`1` 表示进入审核队列

**说明**: 5 秒内仅可发表一次评论。

**HTTP 状态码**:
- `200`: 发表成功
- `400`: 参数或内容校验错误
- `401`: Token 无效
- `403`: 存在拉黑关系（`blocked`）
- `429`: 评论过于频繁（`too_many_requests`）
- `500`: 系统错误

---

### 3. 删除动态评论

**请求**: `DELETE /api/comment/blog-comments/{bcid}`

**路径参数**:
- `bcid` (int, 必需): 动态评论 ID

**请求参数** (Body, JSON):
- `token` (string, 必需): 用户认证令牌

**说明**: 仅评论作者本人可删除。

**HTTP 状态码**:
- `200`: 删除成功
- `400`: 评论不存在（`error_bcid`）
- `401`: Token 无效
- `403`: 非本人评论（`no_permission`）
- `500`: 系统错误

---

## 视频评论

### 4. 视频评论列表

**请求**: `GET /api/comment/videos/{vid}?parent_vcid={parent_vcid}&offset={offset}&num={num}&cid_asc={cid_asc}&token={token}`

**路径参数**:
- `vid` (int, 必需): 视频 ID

**请求参数** (Query):
- `parent_vcid` (int, 必需): 父评论 ID，顶级评论传 `0`
- `offset` (int, 必需): 偏移量
- `num` (int, 必需): 返回数量，最大 12
- `cid_asc` (int, 可选): 是否按评论 ID 升序
- `token` (string, 可选): 登录 token

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "comment_list": [
      {
        "vcid": 2001,
        "parent_vcid": 0,
        "uid": 23,
        "content": "评论内容",
        "time": "2026-04-18 12:00:00",
        "child_comment_num": 2,
        "if_my_comment": 0,
        "username": "昵称",
        "honour": "荣誉",
        "avatar_url": "https://example.com/avatar.jpg"
      }
    ]
  }
}
```

**HTTP 状态码**: 同动态评论列表

---

### 5. 发表视频评论

**请求**: `POST /api/comment/videos/{vid}`

**路径参数**:
- `vid` (int, 必需): 视频 ID

**请求参数** (Body, JSON):
- `token` (string, 必需): 用户认证令牌
- `parent_vcid` (int, 必需): 父评论 ID，回复视频传 `0`
- `content` (string, 必需): 评论内容，1-459 字

**成功响应**: 同「发表动态评论」

**HTTP 状态码**: 同「发表动态评论」（`error_vid`、`error_parent_vcid` 等）

---

### 6. 删除视频评论

**请求**: `DELETE /api/comment/video-comments/{vcid}`

**路径参数**:
- `vcid` (int, 必需): 视频评论 ID

**请求参数** (Body, JSON):
- `token` (string, 必需): 用户认证令牌

**HTTP 状态码**:
- `200`: 删除成功
- `400`: 评论不存在（`error_vcid`）
- `401`: Token 无效
- `403`: 非本人评论（`no_permission`）
- `500`: 系统错误
