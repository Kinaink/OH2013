# OH2013

面向旧设备的 **OTTOhub 客户端**，基于 [BiliClassic](https://github.com/AktuelleKamera/BiliClassic) 魔改，还原 2013 年前后的经典界面与交互体验。

**当前发行版：0.3.2** · 应用包名 `cn.ottohub.oh2013`（可与哔哩经典同机安装）

## 目标平台

- **Android 1.5+**（API 3，Cupcake 及以上）

## 功能概览

| 模块 | 状态 |
|------|------|
| 热门 / 最新 / 推荐视频 | ✅ |
| 视频搜索 | ✅ |
| 视频详情与播放（含离线缓存） | ✅ |
| 滚幕（JSON → XML 适配） | ✅ |
| 邮箱 / UID 密码登录 | ✅ |
| 评论列表 / 发表 / 删除 | ✅ |
| 点赞 / 收藏 / 历史 | ✅ |
| 用户空间 / 关注列表 | ✅ |
| 博客 / 频道 | ✅ |
| 私信 / 聊天室 | ✅ |

## API 配置

- 主站 API：`https://api.ottohub.cn`
- 列表分页：`offset` + `num`，**单次最多 12 条**（客户端硬上限）
- 视频 ID：OTTOhub `vid`（内部映射到原项目的 `aid` 字段）

API 文档见项目根目录 `*_api.md`。

## 构建

详见 [BUILD.md](BUILD.md)。

需要 **JDK 8** + **Android SDK 26**（Build-Tools 25.0.0）。

```powershell
# 设置 JDK 8 后
.\gradlew.bat assembleRelease
```

签名说明见 `keystore.properties.example`。产物默认：`app/build/outputs/apk/OH2013-0.3.2.apk`。

## 项目结构

```
OH2013/
├── app/                    # 主应用（UI + API 适配层）
├── player/                 # IJKPlayer 封装
├── DanmakuFlameMaster/     # 滚幕引擎
├── gradle/                 # Gradle Wrapper
├── *_api.md                # OTTOhub 接口文档
├── BUILD.md                # 编译打包说明
└── HOST_ANNOUNCEMENT.md    # 公告 / 更新对接说明
```

## 致谢

- [BiliClassic](https://github.com/AktuelleKamera/BiliClassic) — 一只毛子球
- [BiliTerminal](https://github.com/RobinNotBad/BiliTerminal) — RobinNotBad
- [WristBilibili](https://github.com/luern0313/WristBilibili) — luern0313

## 许可证

遵循 GNU GPL v3+（继承自 BiliClassic）。
