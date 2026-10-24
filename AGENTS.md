# AGENTS.md

本文件面向参与本项目的开发者与自动化代理，描述修改代码时必须遵守的约束。

## 项目定位

BanLiMusic 是一个 Android 音乐客户端，当前接入 GD 音乐台作为示例来源。项目按“多来源、可长期演进”设计，未来会接入自建音乐服务器、本地音乐，并可能拆分共享核心以支持 Windows/Linux。

## 核心架构约束

1. **UI 不直接访问具体音乐 API**
   - Compose 页面和 ViewModel 只依赖 `MusicRepository`、`LibraryRepository`、`DownloadRepository` 等领域接口。
   - 具体来源实现放在 `data/source/<source>/`，并通过 `MusicProvider` 暴露能力。
   - 不要在 UI 中硬编码 `netease`、`joox`、`bilibili`、`gd-*` 等来源标识。

2. **来源能力通过 `ProviderCapabilities` 表达**
   - 搜索、播放、歌词、下载、封面、歌单等能力必须由 provider 声明。
   - 新来源不支持某项能力时，UI 应隐藏或禁用对应操作，而不是假设它一定存在。

3. **来源设置必须模块化**
   - 通用设置（主题、缓存、导航栏等）与来源专属设置分离。
   - GD 音乐台的音源选择、频率限制、音质等配置集中在“GD 音乐台”设置模块。
   - 新增来源时，新增独立的 provider 设置模型与设置区块，不要把来源字段继续堆进通用 `AppSettings` 的 UI 逻辑里。
   - provider 专属字段可以暂时放在 `AppSettings` 中，但必须使用清晰前缀，并在 `core/datastore` 中集中迁移；更推荐引入 `ProviderSettings` 抽象。

4. **ID 分层**
   - 应用级 `Track.id` 与来源级 `providerTrackId` 必须分离。
   - 不要用来源 ID 作为收藏、下载、播放历史的唯一键。
   - 下载任务 ID 必须包含应用级 track ID、来源和音质，避免不同来源/音质互相覆盖。

5. **播放地址懒解析**
   - `MediaItem` 中只保存候选来源（`StreamUriCodec`），由 `ResolvingDataSource` 在播放时解析。
   - 不要把临时播放 URL 写入数据库或长期缓存。
   - 已下载的本地文件优先于远程流；播放队列构建时应检查下载记录。

6. **限流是全局约束**
   - GD 音乐台限制为 5 分钟 50 次请求；客户端默认更保守，并强制请求间隔至少 1 秒。
   - 所有 provider 请求必须经过 `RequestScheduler` / `SlidingWindowRateLimiter`。
   - 批量下载、批量收藏、搜索、歌词、封面请求都不能绕过限流。
   - 新增来源时，为每个后端创建独立 limiter，不要与 GD 共享额度。

7. **数据库与兼容性**
   - Room 实体变更必须提升 `version` 并提供 `Migration`；不要依赖 `fallbackToDestructiveMigration` 丢弃用户数据。
   - 序列化的 `Track` / `TrackSource` 新增字段必须提供默认值，保证旧下载记录和收藏记录可反序列化。
   - DataStore 新增键必须提供默认值，并处理旧版本缺失键的情况。

## 包体积与依赖

- **尽量不引入大的外部包**。优先使用 AndroidX、平台 API、现有依赖。
- 新增依赖前必须说明用途、替代方案和预计体积影响。
- 不要为了一个小功能引入整套 UI 框架、图片库、数据库或网络库。
- 图标优先使用 `material-icons-extended` 中已有图标；避免引入图标资源包。
- 本地音乐扫描使用 `MediaStore`，不要引入文件选择/媒体扫描第三方库。
- 小组件继续使用 Glance，不要引入远程视图布局框架。

## UI 约束

- 使用 Jetpack Compose + Material 3，保持现有主题和组件风格。
- 手机与平板共用业务逻辑；平板使用宽度判断（建议 `>= 720dp`）切换 NavigationRail / 双栏布局。
- 手机不需要专门的横屏布局，但不要阻止系统旋转。
- 设置页必须分类；来源专属配置独立成块。
- 首次启动必须展示用户协议与 GD 音乐台声明，用户同意后才能进入主界面。

## 网络与合规

- GD 音乐台资源仅用于学习参考，不得用于商业用途，不得传播受版权保护的内容。
- 不要实现绕过服务端限流、鉴权或版权保护的功能。
- 下载默认目录、歌词/封面附带下载等行为必须尊重用户设置与平台存储权限。
- 临时播放 URL 只做短时缓存。

## 构建与测试

项目使用 Kotlin Toolchain，不维护 Gradle 文件。

```bash
./kotlin build
./kotlin test
./kotlin build -v release
```

环境要求：

- JDK 17+（当前使用 JDK 25）
- Android SDK：Platform 37.0、Build-Tools 37.0.0、Platform-Tools
- `local.properties` 中的 `sdk.dir` 或 `ANDROID_HOME`

提交前至少运行：

```bash
./kotlin test
./kotlin build
```

新增核心算法（去重、限流、解析、迁移、下载状态机）必须补充 JVM 单元测试。

## 版本与发布

版本号采用 `MAJOR.MINOR.PATCH`：

- **MAJOR（第一位）**：只在用户明确要求时变更，代理不得自行提升。
- **MINOR（第二位）**：功能新增、架构/数据模型/UI 大改、跨模块行为调整等较大改动时 +1。
- **PATCH（第三位）**：Bug 修复、文案修正、小范围兼容性处理等小改动时 +1。

每次版本变更必须同时：

1. 更新 `androidApp/module.yaml` 中的 `versionCode`（单调递增，不复用）和 `versionName`；这是唯一的版本号来源。
2. 不要在 Kotlin 源码中硬编码版本号。运行时版本通过 `PackageManager` 读取；HTTP `User-Agent` 不携带版本号。README 中的产物名使用 `BanLiMusic-<version>-release.apk` 占位符。
3. 运行 `./kotlin test` 与 `./kotlin build`；发布时执行 `./kotlin build -v release` 并重新签名。
4. 在提交信息或发布说明中记录版本号变化及原因。

判断示例：

- 只修复缺陷：`0.3.0` → `0.3.1`（PATCH）。
- 新增功能或较大范围重构：`0.3.1` → `0.4.0`（MINOR）。
- 第一位版本号变化必须先获得用户明确指令，不得自动修改。

当前版本为 `0.4.1`，`versionCode` 为 6。

## 不要提交的文件

- `.vscode/`（已从 Git 历史移除，本地可保留）
- `local.properties`
- `build/`、`dist/`
- `*.keystore`、`keystore.pass`、签名密码
- 任何包含本机绝对路径的配置

## 代码风格

- Kotlin 官方风格，4 空格缩进。
- 公共 API 使用清晰命名；避免在 UI 层写业务分支。
- 协程使用结构化并发；不要在主线程执行阻塞 IO。
- 捕获异常时区分 `CancellationException`，不要吞掉取消。
- 对用户可见错误使用 `MusicException.userMessage()`。
