# OH2013 虚拟主机公告 / 更新对接说明

把下面两个文件放到你的虚拟主机（Apache / Nginx / PHP 均可），然后把
`ApiConfig.ANNOUNCEMENT_URL` 和 `ApiConfig.UPDATE_URL` 改成你的真实地址。

---

## 1. 公告：`announcement.json`

客户端启动约 2 秒后 GET 此地址，需要返回 **纯 JSON**（`Content-Type: application/json`）。

### 推荐结构（多条公告）

```json
{
  "announcements": [
    {
      "id": "2026-08-28-welcome",
      "title": "OH2013 公告",
      "content": "欢迎使用 OH2013！\n这是测试公告内容。",
      "version": "",
      "minSdk": 3,
      "url": "https://www.ottohub.cn/",
      "buttonText": "打开网站",
      "showOnce": true,
      "forceShow": false,
      "priority": 1
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `id` | 唯一 ID，用于「只显示一次」记忆 |
| `title` | 弹窗标题 |
| `content` | 正文，可用 `\n` 换行 |
| `version` | 可选；填了则仅当 App 版本 **低于** 此版本时显示 |
| `minSdk` | 最低 Android API，一般 `3` |
| `url` | 可选；底部按钮跳转链接 |
| `buttonText` | 按钮文字，默认「查看」 |
| `showOnce` | `true` = 用户看过不再弹出 |
| `forceShow` | `true` = 忽略「已关闭」记录 |
| `priority` | 数字越小越先显示 |

也支持单条对象（无 `announcements` 数组）或顶层数组。

### 纯文本兜底（可选）

若只放 `announcement.txt`（纯文本），需在服务端用 PHP 包一层 JSON，例如：

```php
<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
$text = @file_get_contents(__DIR__ . '/announcement.txt');
if ($text === false) $text = '';
echo json_encode(array(
  'announcements' => array(array(
    'id' => 'txt-' . md5($text),
    'title' => '公告',
    'content' => $text,
    'minSdk' => 3,
    'showOnce' => false,
    'forceShow' => true,
    'priority' => 1
  ))
), JSON_UNESCAPED_UNICODE);
```

访问：`https://你的域名/oh2013/announcement.php`

---

## 2. 版本更新：`version.json`

```json
{
  "versionCode": 101,
  "versionName": "0.2.0",
  "forceUpdate": false,
  "changelog": "1. 修复登录\n2. 白橙主题",
  "downloadUrl": "https://你的域名/oh2013/OH2013-0.2.0.apk"
}
```

| 字段 | 说明 |
|------|------|
| `versionCode` | 整数，大于 App 内 `versionCode` 才会提示更新 |
| `versionName` | 显示给用户的版本名 |
| `forceUpdate` | `true` = 强制更新（尽量不可取消） |
| `changelog` | 更新说明 |
| `downloadUrl` | APK 直链 |

---

## 3. Nginx / Apache 注意点

1. **必须返回 JSON**，不要返回 HTML 错误页（否则客户端会解析失败并静默忽略）。
2. 建议响应头：
   - `Content-Type: application/json; charset=utf-8`
   - `Access-Control-Allow-Origin: *`（可选）
3. 不要强制 gzip 给超老设备；若开启 gzip，确保客户端能解（OH2013 已兼容 gzip）。
4. HTTPS 证书需被系统信任；调试可用 HTTP。
5. APK 下载目录关闭热链限制，或允许空 Referer。

---

## 4. 目录示例

```
/oh2013/
  announcement.json   ← ApiConfig.ANNOUNCEMENT_URL
  version.json        ← ApiConfig.UPDATE_URL
  OH2013-0.2.0.apk
```

改好 `ApiConfig.java` 里两个 URL 后重新打包即可。
