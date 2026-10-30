# 开发经验

本文记录 BanLiMusic 跨模块实现中的经验、取舍与常见坑，供开发者和自动化代理参考。强制约束见 [AGENTS.md](../../AGENTS.md)，模块边界见 [architecture.md](../architecture.md)，功能现状见 [features.md](../features.md)。

## 共享核心与 UI

- `shared/` 只放平台无关代码：可以使用 `java.*`，但 Android SDK、Media3、Room Android、WorkManager、Glance、RemoteViews 必须留在 `androidApp/`。
- 桌面端只通过 `//shared` 复用核心；桌面播放使用 JLayer（仅 MP3），不要下沉到 `shared/`，也不要为桌面端复制一份核心逻辑。
- 页面布局、列表、弹窗、导航与播放控件集中在 `shared/src/com/banlimusic/shared/ui`。平台层只做数据映射、权限、文件选择、图片加载和返回键转发，不要维护第二套布局。
- 新增或调整页面时先检查 `shared` 是否已有可复用布局；平台无关的解析、去重、排序、URI 编解码优先下沉。

## 来源、能力与设置

- 页面和 ViewModel 只依赖领域仓库，不要直接调用 GD API，也不要在 UI 中判断具体来源。
- 能力由 `ProviderCapabilities` 声明。来源不支持某项能力时，UI 应隐藏或禁用对应操作，不要假设搜索、歌词、下载、封面或歌单一定存在。
- 新增来源需要独立的 provider 实现、独立设置区块和独立 limiter，不要共享 GD 的 5 分钟额度。
- provider 专属字段放入 `AppSettings` 时必须使用清晰前缀，并在 `core/datastore` 集中迁移；更推荐新增 `ProviderSettings` 抽象。
- 新增来源后检查来源徽标、音质档位映射、歌词/封面加载和下载能力的降级路径。

## ID、播放与限流

- 应用级 `Track.id` 与来源级 `providerTrackId` 分离；收藏、下载、播放历史不能把来源 ID 当作唯一键。
- 下载任务 ID 必须包含应用级 track ID、来源和音质，否则不同来源或音质的同一首歌会互相覆盖。
- `MediaItem` 中只保存候选来源，由 `ResolvingDataSource` 在播放时解析；临时播放 URL 只做短时缓存，不写入数据库。
- 构建播放队列时先查已下载文件，本地文件优先于远程流；同一首歌有多个下载/缓存音质时选最高音质，不要按当前播放音质设置降级。
- 同一首歌再次下载其他音质必须走 `DownloadQualityPolicy` 与统一弹窗，批量下载和缓存转下载共用一次确认；并存文件仍写入清单，但列表只展示最高音质。
- GD 音乐台限制为 5 分钟 50 次请求；客户端更保守，并强制请求间隔至少 1 秒。
- 搜索、歌词、封面、批量收藏和批量下载都必须经过 `RequestScheduler` / `SlidingWindowRateLimiter`，不能直接发请求；429 按 `Retry-After` 等待。
- 播放失败按设置退避重试，并可轮转到同一首歌的其他来源；连续失败达到阈值后停止并通知用户。

## 数据与兼容

- Room 实体变更必须提升 `version` 并提供 `Migration`，不要用 `fallbackToDestructiveMigration` 丢弃用户数据。
- 序列化的 `Track` / `TrackSource` 新增字段必须提供默认值，保证旧收藏和下载记录可反序列化。
- DataStore 新增键必须提供默认值，并处理旧版本缺失键的情况；provider 字段迁移集中在 `core/datastore`。
- 下载清单 `banli-downloads.json` 只记录可迁移的歌曲信息、来源、歌词文本与海报文件名，不写临时播放 URL。

## 云同步

- 与平台无关的同步文档、合并算法和 Graph 客户端放 `shared/`；令牌存储、OAuth 回调、Worker 与设置 UI 留在平台层。
- 只访问 Graph `special/approot` 下的 `library.json`，权限使用 `Files.ReadWrite.AppFolder`；不要申请整个 OneDrive 的读写权限，也不要添加 client secret。
- 同步请求使用独立 OkHttp 与独立 `RequestScheduler`，不得占用 GD 的 5 分钟额度。
- 删除收藏/收藏夹必须写墓碑（`deletedAt`）并参与双向合并，禁止整库覆盖式同步；上传使用 eTag + `If-Match`，遇到 412 重新拉取并合并重试。
- Android 的 refresh token 用 Android Keystore 加密存入 `noBackupFilesDir`；桌面端令牌文件限制为当前用户可读写，并放在仓库外。
- OAuth 必须校验 `state` 与 PKCE verifier；Android 回调仅监听 `msauth://com.banlimusic.app`。
- 同步文档与同步实体新增字段必须提供默认值；`ProviderCapabilities` 不用于表达云同步能力。

## 依赖与包体积

- 先找 AndroidX、平台 API 和现有依赖；新增依赖前说明用途、替代方案和体积影响，不要为一个小功能引入整套 UI、图片、数据库或网络框架。
- 图标优先使用 `material-icons-extended` 中已有图标；桌面打包时按源码实际引用裁剪该 jar，避免整包进入产物。
- 本地音乐扫描使用 `MediaStore`，不要引入文件选择或媒体扫描第三方库。
- 桌面小组件使用平台 `AppWidgetProvider` + `RemoteViews`，状态持久化在 `PlayerWidgetUpdater`；播放中使用 `partiallyUpdateAppWidget` 局部刷新，避免整层替换造成延迟。自定义颜色/透明度需要运行时绘制圆角位图，位图边长必须限制在约 320px，避免 Binder 传输超限。

## UI 与交互

- 使用 Jetpack Compose + Material 3，保持现有主题和组件风格；新增页面先看共享 UI 是否已有同类布局。
- 手机与平板共用业务逻辑；`>= 720dp` 使用 NavigationRail / 双栏布局。手机不需要专门横屏布局，但不要阻止系统旋转。
- 设置页必须分类，来源专属配置独立成块，每个设置项使用图标。
- 首次启动必须展示用户协议与 GD 音乐台声明，用户同意后才能进入主界面。
- 桌面端遵循平板横屏布局：常驻底部播放条，播放页隐藏；关闭窗口后驻留系统托盘，Linux 托盘显示依赖桌面环境。托盘驻留期间必须用配置文件锁 + 回环端口实现单实例，二次启动只唤出已有窗口。

## 构建、测试与打包

- 项目使用 Kotlin Toolchain，不维护 Gradle 文件；环境与命令见 [environment.md](environment.md) 和 [build.md](build.md)。
- 提交前运行 `./kotlin test` 与 `./kotlin build`；去重、限流、解析、迁移、下载状态机等核心算法必须有 JVM 单元测试。
- 桌面端打包只使用 `scripts/package-linux.sh` 与 `scripts/package-windows.ps1`，流程见 [release.md](release.md)。
- `jpackage` 只能生成当前平台的产物：Linux 不能生成 Windows 的 exe/MSI，Windows 不能生成 deb/rpm；Windows 安装包需要 Windows 与 WiX。
- 不要用 `./kotlin package -m desktopApp -f executable-jar` 作为桌面打包输入：可执行 JAR 会去重同名 `runtime-desktop`，导致 Compose 运行时缺失。
- Windows MSI 使用 per-machine 安装；避免 per-user 安装到非系统盘触发 `Config.Msi` 权限问题。升级 UUID/UpgradeCode 固定，不要修改。
- 签名密钥、证书和口令必须放在仓库外，不写入文档、日志或命令行历史。

## 代码风格与注释

- Kotlin 官方风格，4 空格缩进；公共 API 使用清晰命名，避免在 UI 层写业务分支。
- 使用结构化并发；不要在主线程执行阻塞 IO；捕获异常时区分 `CancellationException`，不要吞掉取消。
- 对用户可见错误使用 `MusicException.userMessage()`。
- 每个 Kotlin 文件在 `package` 声明前写统一中文文件头，只说明职责，不重复类名或堆砌实现细节；移动或重命名后检查注释。直接参与 APP 的清单 XML 也要用中文注释说明作用。
- 文档只写当前项目状态，不写修改历史；路径、用户名和签名材料使用 `<ANDROID_SDK>`、`<JDK_HOME>`、`<KEYSTORE_DIR>`、`<version>` 等占位符。
