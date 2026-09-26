# OH2013 编译打包指南

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| **JDK** | **8** (1.8) | Gradle 3.3 不支持 JDK 17+ |
| **Android SDK** | API **26** | `compileSdkVersion 26` |
| **Build-Tools** | **25.0.0** | 见 `app/build.gradle` |
| **Gradle** | 3.3 | 已包含 Wrapper，无需单独安装 |

## 命令行打包

```powershell
# 使用 JDK 8（示例路径按本机修改）
$env:JAVA_HOME = "C:\Path\To\jdk8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 配置 Android SDK 路径（首次）
# 在项目根目录创建 local.properties：
# sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk

# 配置签名（首次）
# 复制 keystore.properties.example 为 keystore.properties 并填写；
# 自行用 keytool 生成 JKS，或按 example 中的字段准备密钥库。

.\gradlew.bat assembleRelease
```

产物：

- `app/build/outputs/apk/OH2013-<versionName>.apk`

当前 `versionName` 见 `app/build.gradle`（发行版 **0.3.2**）。

## 签名密钥说明

| 文件 | 说明 |
|------|------|
| `keystore.properties.example` | 字段模板（可提交） |
| `keystore.properties` | 本地密钥路径与密码（**勿提交**） |
| 密钥库文件（如 `oh2013.jks`） | 发布签名（**务必备份，勿提交**） |

丢失密钥后无法覆盖安装已发布的版本。

## Android SDK 安装

若未安装 Android SDK，推荐：

1. 安装 [Android Studio](https://developer.android.com/studio)
2. SDK Manager 中安装：
   - Android SDK Platform **26**
   - Android SDK Build-Tools **25.0.0**
3. SDK 默认路径：`%LOCALAPPDATA%\Android\Sdk`

或设置环境变量：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

## 常见问题

### `Could not determine java version from '17.x'`

当前 Java 版本过高。必须使用 **JDK 8**。

### `SDK location not found`

创建 `local.properties`：

```
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk
```

### 与哔哩经典冲突

本应用 `applicationId` / 包名为 `cn.ottohub.oh2013`，可与哔哩经典（`tv.biliclassic`）同机安装。
