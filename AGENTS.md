# AGENTS.md

本文件是参与 BanLiMusic 开发的强制约束，面向开发者与自动化代理。实现经验、取舍与常见坑见 [开发经验](doc/development/experience.md)，文档职责见 [开发文档索引](doc/README.md)。未经用户明确指令，**严禁修改本文档**。

## 架构边界

- UI 与 ViewModel 只依赖 `MusicRepository`、`LibraryRepository`、`DownloadRepository` 等领域接口，不直接调用来源 API，也不在 UI 中硬编码 `netease`、`joox`、`bilibili`、`gd-*` 等来源标识。
- 具体来源实现放在 `data/source/<source>/`，通过 `MusicProvider` 暴露能力；能力由 `ProviderCapabilities` 声明，UI 对不支持的能力隐藏或禁用。
- 新增来源必须提供独立的 provider 设置与独立限流额度；provider 专属字段放入 `AppSettings` 时必须使用清晰前缀，并在 `core/datastore` 集中迁移。
- 应用级 `Track.id` 与来源级 `providerTrackId` 分离；收藏、下载、播放历史不得使用来源 ID 作为唯一键。下载任务 ID 必须包含应用级 track ID、来源和音质。
- 播放地址懒解析：`MediaItem` 只保存候选来源，临时播放 URL 不写入数据库或长期缓存；已下载的本地文件优先。
- 所有来源请求必须经过 `RequestScheduler` / `SlidingWindowRateLimiter`，批量请求也不能绕过；每个后端使用独立额度。
- Room 实体变更必须提升 `version` 并提供 `Migration`，禁止使用 `fallbackToDestructiveMigration`；序列化模型与 DataStore 新字段必须提供默认值。
- 云同步只访问 OneDrive `special/approot` 下的 `library.json`，使用独立 OkHttp 与独立限流器；删除写墓碑并双向合并，禁止整库覆盖；令牌按平台安全存储；禁止 client secret。
- `shared/` 保持平台无关，不得引入 Android SDK、Media3、Room Android、WorkManager、Glance、RemoteViews；`desktopApp/` 只通过 `//shared` 复用核心。

## 依赖与 UI

- 优先复用现有依赖、AndroidX 与平台 API。新增依赖前说明用途、替代方案和体积影响；不要为一个小功能引入整套 UI、图片、数据库或网络框架。
- 图标优先使用 `material-icons-extended` 已有图标；本地音乐使用 `MediaStore`；桌面小组件使用 `AppWidgetProvider` + `RemoteViews`，不引入第三方远程视图框架。
- 使用 Jetpack Compose + Material 3，保持现有主题和组件风格；手机与平板共用业务逻辑，平板以 `>= 720dp` 切换 NavigationRail / 双栏布局。
- 不阻止系统旋转；设置页必须分类，来源专属配置独立成块；首次启动必须展示用户协议与 GD 音乐台声明，同意后才能进入主界面。

## 网络与合规

- GD 音乐台资源仅用于学习参考，不得用于商业用途，不得传播受版权保护的内容。
- 不得实现绕过服务端限流、鉴权或版权保护的功能。
- 下载目录、歌词/封面附带下载等行为必须尊重用户设置与平台存储权限；临时播放 URL 只做短时缓存。

## 构建、测试与发布

- 使用 Kotlin Toolchain，不维护 Gradle 文件。构建与环境要求见 [build.md](doc/development/build.md) 与 [environment.md](doc/development/environment.md)。
- 提交前至少运行 `./kotlin test` 与 `./kotlin build`；新增去重、限流、解析、迁移、下载状态机等核心算法必须补充 JVM 单元测试。
- 版本号只以 `androidApp/module.yaml` 的 `versionCode` / `versionName` 为准：MAJOR 仅在用户明确要求时提升，MINOR 用于功能或较大重构，PATCH 用于修复。不要把版本号硬编码到源码。
- 发版流程、签名和桌面端打包见 [release.md](doc/development/release.md)；新增依赖、数据库迁移和云同步变更必须同步更新对应文档。

## 代码与提交

- Kotlin 官方风格，4 空格缩进；公共 API 命名清晰，不把业务分支写进 UI。
- 使用结构化并发，不要在主线程执行阻塞 IO；捕获异常时区分 `CancellationException`，不要吞掉取消。
- 对用户可见错误使用 `MusicException.userMessage()`。
- 每个 Kotlin 文件必须在 `package` 声明前包含统一中文文件头：

  ```kotlin
  /**
   * 文件作用：一句话说明该文件在应用中的职责。
   */
  ```

  注释只说明职责，不重复类名；移动或重命名文件后检查注释。直接参与 APP 的清单 XML 也要用中文注释说明作用。
- 不要提交 `.vscode/`、`local.properties`、`build/`、`dist/`、`*.keystore`、`keystore.pass`、签名密码或包含本机绝对路径的配置。
- 文档只写当前项目状态，不写修改历史或过时实现；路径、用户名、签名材料使用 `<ANDROID_SDK>`、`<JDK_HOME>`、`<KEYSTORE_DIR>`、`<version>` 等占位符。文档职责与同步要求见 [doc/README.md](doc/README.md)。
