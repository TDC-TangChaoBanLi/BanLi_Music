# OneDrive 云同步

收藏夹与收藏歌曲可以双向同步到用户自己的 OneDrive，不经过开发者服务器。本文记录实现细节、应用注册与安全约束；功能概览见 [features.md](features.md)。

## 数据与授权

- 数据位置：Graph `special/approot` 下的 `library.json`（即 `Apps/BanLiMusic/library.json`），只申请 `Files.ReadWrite.AppFolder` 与 `User.Read`，适用于个人 Microsoft 账号。
- 登录：OAuth 2.0 授权码 + PKCE，系统浏览器完成授权。Android 的 refresh token 使用 Android Keystore 加密保存在 `noBackupFilesDir`；桌面端保存在用户配置目录的 `onedrive-auth.json`，并尽量设置为仅当前用户可读写（POSIX `0600`）。
- 合并：按收藏夹/归属关系的 `updatedAt` 双向合并，删除写入墓碑（保留 180 天）；上传使用 eTag + `If-Match`，遇到 412 会自动重新拉取并合并重试。
- 触发：设置页“立即同步”、应用启动（开启自动同步且距上次超过 30 分钟）。Android 另有 WorkManager 定期任务（1–168 小时）；桌面端目前只在启动时检查一次。
- 网络：桌面端启动时启用 JVM 系统代理，并读取 `HTTPS_PROXY`/`HTTP_PROXY` 环境变量；登录回调使用系统分配的本机随机端口，不再固定 `53682`。如果登录提示网络失败，请先确认系统代理可用。
- 限流：同步请求使用独立 OkHttp 与独立 `RequestScheduler`，不占用 GD 音乐台的 5 分钟请求额度。

Android 实现位于 `androidApp/src/com/banlimusic/app/core/sync/` 与 `androidApp/src/com/banlimusic/app/data/sync/`；桌面端实现位于 `desktopApp/src/com/banlimusic/desktop/data/sync/`。平台无关的同步文档、合并算法与 Graph 客户端在 `shared/`。令牌存储、OAuth 回调与设置 UI 按平台分别实现。

## OneDrive 应用 ID（公开标识，不是密钥）

仓库内的 `OneDriveConfig.CLIENT_ID` 固定为官方应用注册的 Application (client) ID：

`188469c5-9b63-49a9-8449-ea8fea226247`

它是公开标识符，会随 APK 分发，也会出现在授权 URL 中，**不属于机密**。本项目不包含 client secret；真正需要保密的是用户令牌与签名密钥。

该 ID 与下面这组注册信息绑定：

- Android 包名：`com.banlimusic.app`
- Android 官方 release 签名 SHA-1：`02BEA47D976FE59D383BA6CD6B0A3153FA621A3C`
- Android 签名哈希（Base64）：`Ar6kfZdv5Z04O6bNawoxU/piGjw=`
- Android 回调 URI：`msauth://com.banlimusic.app/Ar6kfZdv5Z04O6bNawoxU%2FpiGjw%3D`
- 桌面端回调 URI：`http://localhost/callback`（Entra 会忽略 localhost 的端口）

Entra 会在 `/authorize` 阶段校验回调 URI。**如果你用另一把签名密钥构建，应用计算出的 Android 回调 URI 与注册值不一致，登录会被拒绝（AADSTS50011），这个 client ID 对你的构建不可用。** 如果你同时修改了 `REDIRECT_HOST`（通常随包名变化），也需要重新注册。桌面端还必须把上面的回环 URI 添加到同一个应用注册的“移动和桌面应用程序”平台。

## 自行构建 / Fork 时

1. 在自己的 Entra 租户注册应用，Supported account types 选择“仅个人 Microsoft 账号”。
2. Authentication → Add a platform → Android，填写你的包名与签名 SHA-1；设置页“云同步”会显示当前 APK 的包名、SHA-1 十六进制指纹、Base64 签名哈希与回调 URI，可直接复制。
3. 同一页面再添加“移动和桌面应用程序”平台，回环回调填写 `http://localhost/callback`，供桌面端使用；端口由应用运行时随机分配，Entra 对 localhost 会忽略端口。
4. API permissions 添加 Delegated 权限 `Files.ReadWrite.AppFolder`、`User.Read`，并授予用户同意；不要创建 client secret。
5. 把 `androidApp/src/com/banlimusic/app/core/sync/OneDriveConfig.kt` 与 `desktopApp/src/com/banlimusic/desktop/data/sync/DesktopOneDriveConfig.kt` 中的 `CLIENT_ID` 替换为你自己的 Application (client) ID。留空也可以：云同步页会显示“尚未配置”并给出回调信息，但无法登录。
6. 调试包若使用默认 debug 签名，需要单独注册 debug 的 SHA-1，或像本项目 release 流程一样用正式 keystore 重签。
7. 用注册时的签名安装后，在 设置 → 云同步 中登录。签名变化，或修改了 `REDIRECT_HOST` 时，需要在 Entra 中更新 Android 平台配置。

> 官方 ID 只用于官方签名分发的 APK；公开在仓库中是刻意为之，方便审计与自行构建，不需要按密钥处理。

## 安全与兼容约束

- 只允许访问 Graph `special/approot` 下的 `library.json`；不得申请整个 OneDrive 的读写权限。
- 禁止添加 client secret，也不得把令牌写入仓库。
- Android 的 refresh token 只能通过 Android Keystore 加密存放在 `noBackupFilesDir`，不得写入 DataStore、普通文件或日志。桌面端的令牌文件必须限制为当前用户可读写，并放在仓库外。
- 收藏/收藏夹删除必须写墓碑并参与双向合并，禁止整库覆盖式同步。
- Android OAuth 回调仅监听 `msauth://com.banlimusic.app`；桌面端只监听本机回环的随机端口。两端都必须校验 `state` 与 PKCE verifier。
- 同步文档与同步相关实体新增字段必须提供默认值；`ProviderCapabilities` 不用于表达云同步能力。
