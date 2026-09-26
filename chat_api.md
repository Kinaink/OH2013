# 全站聊天室（Chat Room）API 文档

聊天室独立服务，Base：`https://api-chat.ottohub.cn`。发言走 WebSocket，历史与管理走 HTTP。

**基础信息**:
- **HTTP Base**: `https://api-chat.ottohub.cn`
- **WebSocket**: `wss://api-chat.ottohub.cn/ws`
- **默认房间**: `main`
- **请求格式**: JSON
- **响应格式**: JSON
- **字符编码**: UTF-8

---

## 认证说明

| 令牌 | 用途 | 传递方式 |
|------|------|----------|
| `main_token` | 主站登录令牌（仅换票用） | Body：`main_token` |
| `chat_token` | 聊天室会话令牌 | HTTP：`Authorization: Bearer {chat_token}`；WS：Query `?token=` |

### 通用请求头

```http
Content-Type: application/json
Authorization: Bearer {chat_token}
```

未登录可读公开消息列表，可不带 `Authorization`。

### 通用响应格式

**成功**:
```json
{
  "status": "success",
  "data": { }
}
```

**失败**:
```json
{
  "status": "error",
  "message": "error_token"
}
```

### 错误码

| message | 说明 |
|---------|------|
| `missing_argument` | 缺少必要参数 |
| `error_token` | 登录失效，需重新 exchange |
| `auth_required` | 需登录后才能发言 |
| `no_permission` | 无权限 |
| `resource_not_found` | 资源不存在 |
| `already_deleted` | 消息已删除 |
| `too_short_message` | 消息为空 |
| `too_long_message` | 超过 300 字 |
| `rate_limited` / `too_many_requests` | 频率限制 |
| `warn` | 包含敏感词 |
| `muted` | 已被禁言 |
| `account_banned` | 账号不可用 |
| `invalid_reply_to` | 回复目标无效 |
| `reply_target_deleted` | 原消息已删除 |

---

## 调用教程

```text
1. POST /api/auth/exchange          # main_token → chat_token
2. GET  /api/auth/me                # 聊天身份（可选）
3. GET  /api/rooms/main/messages?limit=50
4. GET  /api/blocks                 # 拉黑列表（已登录）
5. WS   /ws?room=main&token=...     # 实时收发
```

### curl

```bash
# 换票
curl -X POST 'https://api-chat.ottohub.cn/api/auth/exchange' \
  -H 'Content-Type: application/json' \
  -d '{"main_token":"YOUR_MAIN_TOKEN"}'

# 身份
curl 'https://api-chat.ottohub.cn/api/auth/me' \
  -H 'Authorization: Bearer YOUR_CHAT_TOKEN'

# 历史（可不登录）
curl 'https://api-chat.ottohub.cn/api/rooms/main/messages?limit=50'

# 删除自己的消息
curl -X DELETE 'https://api-chat.ottohub.cn/api/messages/2334' \
  -H 'Authorization: Bearer YOUR_CHAT_TOKEN' \
  -H 'Content-Type: application/json'
```

### JavaScript

```js
const CHAT = 'https://api-chat.ottohub.cn';

async function enterChat(mainToken) {
  const ex = await fetch(`${CHAT}/api/auth/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ main_token: mainToken })
  }).then((r) => r.json());
  const chatToken = ex.data.chat_token;

  const me = await fetch(`${CHAT}/api/auth/me`, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${chatToken}`
    }
  }).then((r) => r.json());

  const hist = await fetch(`${CHAT}/api/rooms/main/messages?limit=50`).then((r) => r.json());

  const ws = new WebSocket(
    `wss://api-chat.ottohub.cn/ws?room=main&token=${encodeURIComponent(chatToken)}`
  );
  ws.onopen = () => ws.send(JSON.stringify({ type: 'ping' }));
  ws.onmessage = (ev) => console.log(JSON.parse(ev.data));

  function send(content, replyTo) {
    const payload = { type: 'message', content };
    if (replyTo) payload.reply_to = replyTo;
    ws.send(JSON.stringify(payload));
  }

  return { me, hist, ws, send };
}
```

本地可把 `chat_token` 缓存在 `localStorage` 键 `otto-chat-token:{uid}`。

---

## 1. 换票

**请求**: `POST /api/auth/exchange`

**请求头**:
```http
Content-Type: application/json
```

**请求体**:
```json
{
  "main_token": "YOUR_MAIN_TOKEN"
}
```

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "chat_token": "CHAT_TOKEN_STRING",
    "uid": 17854,
    "username": "示例用户",
    "is_admin": 0
  }
}
```

| 字段 | 说明 |
|------|------|
| `chat_token` | 后续 HTTP / WS 凭据 |
| `is_admin` | `1` 可使用管理接口 |

**错误码**: `error_token`、`missing_argument`

---

## 2. 当前身份

**请求**: `GET /api/auth/me`

**请求头**:
```http
Content-Type: application/json
Authorization: Bearer {chat_token}
```

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "uid": 17854,
    "username": "示例用户",
    "is_admin": 0,
    "mute": null
  }
}
```

禁言时 `mute` 示例：
```json
{
  "id": 1,
  "uid": 17854,
  "room_id": null,
  "muted_until": "2026-08-22 12:00:00",
  "reason": "刷屏",
  "created_by": 1,
  "created_at": "2026-08-21 12:00:00"
}
```

**HTTP 状态码**: `200` 成功；`401` 未授权

---

## 3. 登出

**请求**: `POST /api/auth/logout`

**请求头**: `Authorization: Bearer {chat_token}`

**成功响应**:
```json
{
  "status": "success",
  "data": {}
}
```

---

## 4. 拉取房间消息

**请求**: `GET /api/rooms/{room}/messages?limit={limit}&before_id={before_id}`

**路径参数**:
- `room` (string, 必需): 房间名，默认 `main`

**Query**:
- `limit` (int, 可选): 条数，默认 `50`
- `before_id` (int, 可选): 向前翻页（更早消息）

**认证**: 可选

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "room": "main",
    "pinned_announcement": {
      "room": "main",
      "content": "聊天请遵守《平台内容管理规范》，严禁讨论敏感内容",
      "pinned": true,
      "updated_by": 1,
      "updated_at": "2026-08-21 13:28:09"
    },
    "message_list": [
      {
        "id": 2331,
        "room": "main",
        "uid": 8254,
        "username": "DJxd",
        "content": "消息正文，支持 Markdown / 简单 HTML",
        "created_at": "2026-08-21 22:37:40",
        "reply": null
      }
    ]
  }
}
```

回复消息时 `reply` 示例：
```json
{
  "id": 2300,
  "uid": 100,
  "username": "被回复者",
  "content": "原消息内容",
  "deleted": false
}
```

---

## 5. 删除自己的消息

**请求**: `DELETE /api/messages/{id}`

**路径参数**:
- `id` (int, 必需): 消息 ID

**请求头**:
```http
Content-Type: application/json
Authorization: Bearer {chat_token}
```

**成功响应**:
```json
{
  "status": "success",
  "data": {}
}
```

**错误码**: `already_deleted`、`no_permission`、`error_token`、`resource_not_found`

管理员删任意消息见下方管理接口。

---

## 6. 拉黑列表

**请求**: `GET /api/blocks`

**请求头**: `Authorization: Bearer {chat_token}`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "block_list": [
      {
        "uid": 12345,
        "username": "被拉黑用户",
        "created_at": "2026-08-20 10:00:00"
      }
    ]
  }
}
```

**HTTP 状态码**: `200`；未登录 `401`

---

## 7. 拉黑用户

**请求**: `POST /api/blocks`

**请求头**:
```http
Content-Type: application/json
Authorization: Bearer {chat_token}
```

**请求体**:
```json
{
  "uid": 12345
}
```

**成功响应**:
```json
{
  "status": "success",
  "data": {}
}
```

---

## 8. 取消拉黑

**请求**: `DELETE /api/blocks/{uid}`

**请求头**: `Authorization: Bearer {chat_token}`

**成功响应**:
```json
{
  "status": "success",
  "data": {}
}
```

---

## 9. 用户名联想（@）

**请求**: `GET /api/user/username-match?match={keyword}&num={num}`

**Query**:
- `match` (string, 必需): 关键词
- `num` (int, 可选): 数量，默认 `10`

**成功响应**:
```json
{
  "status": "success",
  "data": {
    "user_list": [
      { "uid": 17854, "username": "示例用户" }
    ]
  }
}
```

---

## 10. WebSocket

**连接**:
```text
wss://api-chat.ottohub.cn/ws?room=main&token={chat_token}
```

- `room`: 房间，默认 `main`
- `token`: 发言需要；只读可省略

### 客户端 → 服务端

**心跳**:
```json
{ "type": "ping" }
```

**发送消息**（不走 HTTP）:
```json
{
  "type": "message",
  "content": "你好",
  "reply_to": 2331
}
```

| 字段 | 必需 | 说明 |
|------|------|------|
| `type` | 是 | 固定 `message` |
| `content` | 是 | 正文，最长 300 字 |
| `reply_to` | 否 | 被回复消息 ID |

### 服务端 → 客户端

```json
{
  "type": "message",
  "data": {
    "id": 2337,
    "room": "main",
    "uid": 17854,
    "username": "示例用户",
    "content": "你好",
    "created_at": "2026-08-21 23:10:00",
    "reply": null
  }
}
```

断线后客户端应重连；用心跳 `ping` 保活。

---

## 11. 管理接口（需 `is_admin=1`）

| 方法 | 路径 | 说明 |
|------|------|------|
| `DELETE` | `/api/admin/messages/{id}` | 删除任意消息 |
| `GET` | `/api/admin/sensitive-words` | 敏感词列表 |
| `POST` / `DELETE` | `/api/admin/sensitive-words/...` | 增删敏感词 |
| `GET` / `POST` / `DELETE` | `/api/admin/mutes`、`/api/admin/mutes/{id}` | 禁言管理 |
| `PUT` | `/api/admin/rooms/{room}/announcement` | Body: `{ "content": "..." }` 置顶公告 |
| `DELETE` | `/api/admin/rooms/{room}/announcement` | 清空公告 |

均需：
```http
Content-Type: application/json
Authorization: Bearer {chat_token}
```
