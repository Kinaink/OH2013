# Image 图片模块 API 文档

## 概述

图片模块提供通用图片上传能力，用于动态正文插图等场景。原 creator 模块的 `submit_image` 已迁移至本模块。

**基础信息**:
- **基础路径**: `/api/image`
- **请求格式**: `multipart/form-data`（POST）
- **响应格式**: JSON
- **认证方式**: 需登录，通过 `token` 传递
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
- `error_token`: Token 无效或已过期
- `file_not_found`: 未上传文件或上传失败
- `error_file`: 文件格式不支持
- `too_big_file`: 文件超过大小限制
- `system_error`: 系统错误
- `Not found`: 接口路径不存在
- `Method not allowed`: HTTP 方法不允许

## 通用 HTTP 状态码

| HTTP 状态码 | 说明 | 常见 `message` |
|-------------|------|----------------|
| `200` | 上传成功 | — |
| `400` | 参数或文件错误 | `file_not_found`、`error_file`、`too_big_file` |
| `401` | 未登录或 Token 无效 | `error_token` |
| `404` | 接口路径不存在 | `Not found` |
| `405` | HTTP 方法不允许 | `Method not allowed` |
| `500` | 服务器内部错误 | `system_error` |

---

## 图片上传

### 1. 上传图片

**请求**: `POST /api/image/upload`

**请求参数** (Form Data):
- `token` (string, 必需): 用户认证令牌
- `file_img` (file, 必需): 图片文件

**支持格式**: `jpg`、`png`、`gif`、`webp`（按文件 MIME 类型校验）

**大小限制**: 最大 10 MB

**说明**:
- 不压缩，直接上传原图至腾讯云 COS
- 返回的 `image_url` 可用于动态正文 Markdown/HTML 引用

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "image_url": "https://img.ottohub.cn/image/1710000000_1234_56.jpg",
    "file_name": "1710000000_1234_56.jpg",
    "file_size": 204800
  }
}
```

**响应字段说明**:
- `image_url`: 图片访问 URL
- `file_name`: COS 存储文件名
- `file_size`: 文件大小（字节）

**错误码**:
- `error_token`: Token 无效
- `file_not_found`: 未上传文件
- `error_file`: 格式不支持或不是有效图片
- `too_big_file`: 超过 10 MB
- `system_error`: 上传失败

**HTTP 状态码**:
- `200`: 上传成功
- `400`: 参数或文件错误
- `401`: Token 无效
- `500`: 系统错误

---

## 与头像/封面的区别

| 模块 | 接口 | 用途 |
|------|------|------|
| **image** | `POST /api/image/upload` | 动态/正文等通用插图 |
| **profile** | `POST /api/profile/avatar` | 用户头像（需审核） |
| **profile** | `POST /api/profile/cover` | 用户封面（需审核） |
