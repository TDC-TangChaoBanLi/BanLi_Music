# 架构

BanLiMusic 按“多来源、可长期演进”设计：Android 与桌面端共享 Kotlin Multiplatform 核心，来源能力通过 `MusicProvider` 暴露，UI 只依赖领域仓库。

## 源码结构

```
project.yaml                  Kotlin Toolchain 模块清单
libs.versions.toml            依赖版本目录
shared/                       KMP 共享核心（android + jvm）
  module.yaml
  composeResources/drawable   两端共用的图片资源（应用图标等）
  src/
    com/banlimusic/app/
      core/model              Track、来源、播放/下载信息模型
      core/common             错误、文本规范化、TTL 缓存、LRC、A-Z 首字母
      core/limiter            滑动窗口限流、请求调度
      core/datastore          设置模型、ProviderSettings、自定义来源预留模型
      core/network            OkHttp + kotlinx.serialization
      data/source             MusicProvider 接口、GD 适配器
      domain/playback         候选来源 URI 编解码、LRC 解析
      domain/search           标题版本识别与跨来源去重
      domain/sort             名称/歌手/专辑排序与 A-Z 分组
    com/banlimusic/shared/
      ui/theme                两端共用的 Material 3 主题与自定义主题色
      ui/navigation           两端共用的自适应应用外壳（侧边/底部导航）
      ui/screens              两端共用的首页、搜索、音乐库、下载、设置、播放页布局
      ui/components           两端共用的列表面板、表头、多选栏、曲目行、迷你播放条、播放操作与来源徽标等
      ui/player               两端共用的歌词视图
      ui/agreement            两端共用的用户协议界面
desktopApp/                   Compose Desktop 桌面端
  module.yaml
  src/main.kt                 窗口与共享应用外壳
  src/com/banlimusic/desktop/
    data/                     设置、收藏夹/历史/本地音乐、下载、缓存、仓库
    player/                   JLayer MP3 播放引擎
    ui/                       平台适配层：只负责数据映射、文件选择与图片加载
androidApp/
  module.yaml                 android/app 构建配置
  src/
    AndroidManifest.xml
    com/banlimusic/app/
      core/
        database/             Room 实体、DAO、缓存索引迁移
        datastore/            DataStore 设置存储与旧键迁移
        cache/                播放缓存、元数据缓存、缓存索引与分类统计
      data/
        repository/           搜索聚合、播放地址、收藏/历史、下载任务
      domain/
        download/             下载状态机与 WorkManager Worker
        playback/             流地址解析
      player/                 Media3 服务、队列持久化、收藏自定义命令
      feature/                共享页面的平台适配层与 ViewModel
      widget/                 RemoteViews 小组件、封面位图缓存
      ui/                     主题、通用组件与 A-Z 索引条
  res/
  test/
  schemas/                    Room schema 历史
```

跨平台代码位于 `shared/`（`kmp/lib`，platforms `android` + `jvm`），与平台无关的模型、Provider、GD 客户端、限流、文本/歌词解析、去重、URI 编解码、排序与首字母索引已下沉；Compose Multiplatform 主题、自适应应用外壳、首页/搜索/播放页布局、协议页、歌词、播放条、曲目行、列表表头、多选栏与 A-Z 索引也位于共享模块。`desktopApp/`（`jvm/app` + Compose Desktop）复用共享核心与共享 UI，使用 Compose Desktop + JLayer 实现桌面 UI 与 MP3 播放；设置使用系统 Preferences，收藏/历史和下载任务使用 JSON 文件。由于共享模块当前允许使用 `java.*` API，它面向 Android/JVM，不包含 iOS 目标。

### UI 单一来源

所有页面布局、列表、弹窗、导航与播放控件都在
`shared/src/com/banlimusic/shared/ui` 中实现。Android 的 `feature/*Screen.kt` 与桌面的 `ui/*Screen.kt` 只做平台适配：

- 把仓库/ViewModel 的 Flow 映射为共享页面的 `*UiState`；
- 实现共享页面的 `*Actions`，调用平台仓库或 ViewModel；
- 提供平台专属能力：媒体权限、SAF/文件选择器、系统剪贴板、Coil 与 AWT 图片加载；
- 处理平台返回键（通过共享页面的 `*ScreenController` 转发）。

因此新增或调整 UI 布局时只需修改 `shared` 中的对应页面，两端会同时生效；平台文件
不应再包含第二套布局。

## 关键原则

1. UI 只依赖 `MusicRepository`，不直接访问 GD API。
2. 播放状态以 Media3 Player/MediaSession 为唯一事实来源，播放页、通知栏、小组件共享它。
3. 播放地址延迟解析；媒体 URI 中携带完整候选来源列表，由 `ResolvingDataSource` 在播放线程解析并缓存。
4. 去重规则集中在 `TrackMerger`，有独立单元测试，可随需求调整。
5. 平台无关逻辑优先下沉到 `shared/`；Android SDK、Media3、Room、WorkManager、Glance、RemoteViews 只留在 `androidApp/`。
6. 来源能力通过 `ProviderCapabilities` 声明，UI 根据能力隐藏或禁用操作，不硬编码来源标识。

更细的架构约束、包体积限制、数据库迁移与云同步规则见仓库根目录的 [AGENTS.md](../AGENTS.md)。
