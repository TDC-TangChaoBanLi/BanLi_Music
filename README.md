# BanLiMusic

一个按“可长期演进的多来源音乐客户端”设计的 Android 应用骨架。当前接入 GD 音乐台（music.gdstudio.xyz）作为示例来源，未来可替换为自建音乐服务器，而不需要重写 UI 和播放器。

## 已实现

- **统一音乐模型**：`Track`、`TrackSource`、`TrackAction`，应用 ID（内容哈希生成，跨来源稳定）与来源 ID 分离。
- **`MusicProvider` 抽象**：GD 的 netease / joox / bilibili 是同一后端下的三个来源实例；可继续新增自建来源。
- **搜索**：300ms 防抖、取消过期请求、分页、多来源并发搜索、跨来源合并去重、相关度排序。
- **限流**：共享的 5 分钟 / 50 次滑动窗口限流器 + 并发调度器；429 冷却与 `Retry-After` 处理。
- **播放**：AndroidX Media3 `MediaSessionService`、后台播放、通知栏/锁屏媒体控制、队列、循环/随机、播放失败错误提示。
- **懒加载播放地址**：媒体 URI 中只保存候选来源，ExoPlayer 在真正播放时解析 URL 并缓存 5 分钟；失败时可按设置切换其他来源。
- **个人音乐库**：Room 持久化收藏、播放历史、搜索历史。
- **下载**：WorkManager + 断点续传（Range）、暂停/继续/取消/重试、进度持久化、前台进度通知、完成校验。
- **桌面小组件**：Glance 小组件，显示歌曲并支持播放/暂停、上一首/下一首。
- **界面**：Jetpack Compose + Material 3，首页、搜索、音乐库、下载、设置、全屏播放器、歌词页。
- **设置**：主题（跟随系统/浅色/深色）、动态取色、音质偏好、来源启用、移动网络播放/下载策略、获取地址失败时切换来源、请求状态、缓存清理。
- **测试**：去重算法、限流器、LRC 解析、来源 URI 编解码、GD 响应解析与 MockWebServer 集成测试，共 34 个 JVM 单元测试。

## 开发环境

### 已验证环境

| 项目 | 版本 / 路径 |
| --- | --- |
| 操作系统 | Fedora Linux 44（Workstation Edition），x86_64 |
| JDK | OpenJDK 25.0.4.1（Red Hat build） |
| `JAVA_HOME` | `/usr/lib/jvm/java-25-openjdk` |
| Android SDK | `~/Softwares/AndroidSDK` |
| SDK Platform | `android-37.0` |
| SDK Build Tools | `37.0.0` |
| 构建工具 | Kotlin Toolchain CLI 0.13.0（`abc7a5f`，2026-10-01） |
| Kotlin | 2.4.21 |
| KSP | 2.3.12（Kotlin Toolchain 默认） |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Media3 | 1.11.1 |
| JVM target | 17 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| applicationId / namespace | `com.banlimusic.app` |
| 推荐 IDE | VS Code + Kotlin 插件 |

### 环境变量与本地配置

- `JAVA_HOME`：指向 JDK 25（本机为 `/usr/lib/jvm/java-25-openjdk`）。
- `ANDROID_HOME` / `ANDROID_SDK_ROOT`：指向 Android SDK（本机为 `~/Softwares/AndroidSDK`）。
- `local.properties`：保存 `sdk.dir`，不提交 Git；Kotlin Toolchain 的 Android 构建会读取它。
- `.vscode/settings.json`：把上述路径注入 VS Code 终端，避免每次手动 export。
- 项目自带 `./kotlin`（Linux/macOS）和 `kotlin.bat`（Windows）wrapper；首次运行会下载并校验 Kotlin Toolchain 0.13.0。

### 首次构建与网络说明

- `androidApp/module.yaml` 已将默认 Google Maven 仓库覆盖为 `https://dl.google.com/dl/android/maven2`，避免本机无法访问 `maven.google.com` 时反复超时。
- Kotlin Toolchain 的 Android 打包阶段会在内部生成临时 Gradle 工程，通过 Gradle 9.5.0 + AGP 9.3.1 完成 APK/AAB。项目仓库不维护 Gradle 文件，日常只需使用 `./kotlin` 命令。
- 首次 Android 打包会下载 Gradle 9.5.0；如果 `services.gradle.org` 较慢，可预先从腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-9.5.0-bin.zip` 下载并放入 `~/.gradle/wrapper/dists/gradle-9.5.0-bin/` 对应缓存目录。缓存完成后可离线构建。

### 构建与测试

```bash
./kotlin build                 # 编译并生成 APK
./kotlin test                  # 运行 JVM 单元测试
./kotlin package -m androidApp # 生成发布 AAB（可选）
./kotlin run -m androidApp     # 安装并运行到已连接的设备/模拟器
```

当前 APK 输出路径为 `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`（该路径可能随 Toolchain 版本调整，以命令输出为准）。

首次构建需要下载依赖与工具链组件。Android SDK 路径来自 `local.properties` 或 `ANDROID_HOME`。新增 `shared/`、`desktopApp/` 等模块时，需要把目录名加入 `project.yaml` 的 `modules` 列表。

## 架构

```
project.yaml                  Kotlin Toolchain 模块清单
libs.versions.toml            依赖版本目录
androidApp/
  module.yaml                 android/app 构建配置
  src/
    AndroidManifest.xml
    com/banlimusic/app/
      core/
        model/                统一歌曲、来源、播放/下载信息模型
        network/              OkHttp + kotlinx.serialization，HTTP 错误映射
        limiter/              滑动窗口限流、请求调度
        database/             Room 实体、DAO、数据库
        datastore/            DataStore 设置
        common/               错误、文本规范化、TTL 缓存、LRC 行模型
      data/
        source/               MusicProvider 接口与 GD 适配器
        repository/           搜索聚合、播放地址、收藏/历史、下载任务
      domain/
        search/               标题版本识别与跨来源去重
        playback/             LRC 解析、候选来源 URI 编解码、流地址解析
        download/             下载状态机与 WorkManager Worker
      player/                 Media3 服务、MediaController 连接、MediaItem 映射
      feature/                Compose 页面与 ViewModel
      widget/                 Glance 小组件与操作回调
      ui/                     主题与通用组件
  res/
  test/
  schemas/                    Room schema 历史
```

未来若要支持 Windows/Linux，可把与平台无关的核心逻辑抽到 `shared/`（`kmp/lib` 或 `jvm/lib`），再新增 `desktopApp/`（`jvm/app` + Compose Desktop）或 `cli/`（`jvm/app`）。当前 Android 模块依赖 Media3、Room、WorkManager、Glance，不能直接编译为桌面应用。

关键原则：

1. UI 只依赖 `MusicRepository`，不直接访问 GD API。
2. 播放状态以 Media3 Player/MediaSession 为唯一事实来源，播放页、通知栏、小组件共享它。
3. 播放地址延迟解析；媒体 URI 中携带完整候选来源列表，由 `ResolvingDataSource` 在播放线程解析并缓存。
4. 去重规则集中在 `TrackMerger`，有独立单元测试，可随需求调整。

## API 与声明

音乐数据来自 **GD 音乐台（music.gdstudio.xyz）**，API 由 GD Studio 提供，采用 CC BY-NC 4.0 许可。

- 该平台仅用于学习参考，**请勿用于商业用途**，请勿下载、传播受版权保护的内容。
- 当前 API 限流为 5 分钟 50 次请求；应用已内置客户端限流，但服务端仍然可能拒绝请求。
- 播放地址通常带时效，应用只做短时缓存；不要把临时播放 URL 当作永久下载地址。
- 若要长期使用或分发，请改为接入拥有合法授权的自建音乐服务器。

## 已知限制

- 尚未实现歌单、本地音乐扫描、账号体系与自建服务器适配器。
- 列表页不主动请求专辑封面（播放页会加载并缓存），以避免很快耗尽 5 分钟 50 次的 API 请求额度。
- 下载文件保存在应用私有目录（`Android/data/com.banlimusic.app/files/Music/BanLiMusic`），卸载应用会删除。
- 桌面小组件不展示封面（仅显示标题、歌手与播放控制），避免小组件侧额外请求封面接口。
- 当前环境没有已连接的 Android 设备/模拟器，因此只完成了编译、单元测试与 APK 结构校验；真机安装验证待设备接入后进行。
