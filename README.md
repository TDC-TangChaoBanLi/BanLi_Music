# BanLiMusic

一个按“可长期演进的多来源音乐客户端”设计的 Android 应用骨架。当前接入 GD 音乐台（music.gdstudio.xyz）作为示例来源，未来可替换为自建音乐服务器，而不需要重写 UI 和播放器。

## 已实现

- **统一音乐模型**：`Track`、`TrackSource`、`TrackAction`，应用 ID（内容哈希生成，跨来源稳定）与来源 ID 分离。
- **`MusicProvider` 抽象**：GD 的 netease / joox / bilibili 是同一后端下的三个来源实例；可继续新增自建来源。
- **搜索**：手动触发、取消过期请求、分页、可配置每页数量、多来源并发搜索、跨来源合并去重、相关度排序、结果多选/全选收藏或下载。
- **限流**：共享的 5 分钟滑动窗口限流器 + 并发调度器；请求上限可配置（最高 50），GD 音乐台请求间隔强制至少 1 秒，429 冷却与 `Retry-After` 处理。
- **播放**：AndroidX Media3 `MediaSessionService`、后台播放、通知栏/锁屏媒体控制、队列、循环/随机；失败按 2/4/8/16 秒退避重试，可配置重试次数与连续失败停止阈值，连续失败会发送系统通知并停止。
- **已下载优先**：构建播放队列时自动查找已完成的下载文件，优先使用本地音频。
- **懒加载播放地址**：媒体 URI 中只保存候选来源，ExoPlayer 在真正播放时解析 URL 并缓存 5 分钟；失败时按设置重试，不再自动切换来源。
- **歌词**：全屏播放器左滑进入歌词页，取消独立歌词按钮；可设置默认显示歌词。
- **用户协议**：首次启动必须阅读并同意用户协议及 GD 音乐台声明后才能进入主界面。
- **个人音乐库与搜索**：Room 持久化收藏、播放历史、搜索历史；收藏支持新建分类文件夹、分类筛选、按名称/歌手/专辑升降排序、JSON 导入导出（覆盖或追加）。音乐库右上角支持本地搜索，可搜索歌名、专辑名、歌手名，并按收藏/最近播放/本地与下载筛选；收藏夹、最近播放、本地与下载、搜索结果均提供右侧 #A-Z 索引，长按拖动可快速滚动到对应首字母。
- **本地音乐**：通过 MediaStore 手动或按设置自动导入本地音频，不复制原始文件；已下载完成的歌曲自动加入本地列表，长按可查看所在目录或 URI。
- **下载与缓存**：WorkManager + 断点续传（Range）、暂停/继续/取消/重试、进度持久化、前台进度通知、完成校验；Android 10+ 默认通过 MediaStore 写入系统 `Music/BanLiMusic`，可设置下载目录、默认下载音质，以及是否同时下载歌词/海报。播放缓存使用稳定 key + Room 索引，可分别查看歌曲、海报、歌词和其他缓存大小，自定义上限（50 MB–2 GB），查看缓存歌曲详细列表并转换为下载；下载完成后自动清除该歌曲的播放缓存。已下载列表只显示标准/较高/高品/无损档位。列表页默认只读取已缓存海报，可在“下载与缓存”设置中开启列表页主动请求海报；开启“播放时自动加载海报”后，切歌时会为系统媒体控件与桌面小组件补全当前海报。清理全部缓存、分类缓存或单首缓存歌曲前都会弹出二次确认。
- **桌面小组件**：平台 `RemoteViews` 实现 3x1 与 4x1 两种可调单行尺寸；显示标题、歌手、封面（大号）、收藏（4x1）、播放控制，进度条固定在组件底部并留出圆角间距，支持黑色/白色/跟随系统的半透明磨砂风格。播放中按 0.5 秒采样并按经过时间插值进度，通过 `partiallyUpdateAppWidget` 局部刷新图标/文本/进度，避免整层 RemoteViews 替换造成的延迟；服务被回收后会尝试恢复上次队列。小组件按钮通过透明 Activity + MediaController 执行，兼容对 PendingIntent/Broadcast 支持不佳的启动器。
- **系统媒体控制**：Media3 通知/任务栏播放控件通过自定义 SessionCommand 提供收藏与播放顺序按钮；收藏图标随当前歌曲状态切换，播放顺序按钮在随机播放、列表循环、单曲循环之间切换并同步图标。
- **界面**：Jetpack Compose + Material 3，首页、搜索、音乐库、下载、设置、全屏播放器、歌词页；手机使用底部导航，平板使用 NavigationRail，播放器在平板上使用双栏布局。
- **设置**：二级菜单分类为外观、播放、播放源、下载与缓存、音乐库与收藏、关于；桌面小组件并入外观；外观支持在关闭动态取色后自定义主题颜色（7 个预设色块或 1 个自定义颜色，支持色相与饱和度调节）；底部导航 5 个栏目全部保留、仅可排序，并可设置默认栏目；播放音质统一放在“播放”设置中，使用标准/较高/高品/无损四档，来源档位不足时映射、多于四档时舍弃；GD 音乐台作为播放源模块，预留自定义 API 来源设置（名称/代码、曲目 ID、音质、搜索/取流/歌词/海报参数映射，暂不发起请求）；每个设置项均有图标。
- **跨平台与桌面端**：`shared/`（Kotlin Multiplatform：Android + JVM）承载模型、Provider、GD 客户端、限流、解析、排序等核心；`desktopApp/`（Compose Desktop）已实现首页、手动搜索与搜索历史、收藏夹分类、最近播放、本地音乐扫描、多选批量收藏/下载、下载管理、MP3 播放、歌词/播放队列、缓存、主题和来源设置。桌面端当前使用 JLayer 解码 MP3，暂不提供无损音质；自动更新仍留待后续。
- **测试**：去重算法、限流器、LRC 解析、来源 URI 编解码、音质档位映射、收藏 JSON 序列化、GD 响应解析与 MockWebServer 集成测试，共 54 个 JVM 单元测试。
- **开发约束**：`AGENTS.md` 记录多来源扩展、包体积控制、限流、数据库迁移和 UI 约定。

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
| 推荐 IDE | VS Code + Kotlin 扩展（`fwcd.kotlin`） |

### 安装前的系统依赖

Kotlin Toolchain wrapper 只依赖常见的命令行工具（`curl` 或 `wget`、`tar`、`sha256sum` 或 `shasum`）；Android 构建需要 JDK 17 及以上，本项目使用 JDK 25。首次安装 Toolchain、SDK 和依赖缓存建议预留至少 5 GB 磁盘空间。

Fedora：

```bash
sudo dnf install -y java-25-openjdk-devel curl wget tar unzip coreutils
```

Debian / Ubuntu（仓库中没有 JDK 25 时，改用下一节的 Temurin 或 SDKMAN）：

```bash
sudo apt update
sudo apt install -y openjdk-25-jdk curl wget tar unzip coreutils
```

验证：

```bash
java -version
javac -version
```

设置 `JAVA_HOME`（路径按实际安装位置修改）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

> `androidApp/module.yaml` 中声明了 `jvm.jdk.version: 25`、`selectionMode: auto`。如果本机只安装了 JDK 17/21，请安装 JDK 25，或把该值改成已安装的版本。

### 其他 JDK 安装方式

如果发行版仓库没有合适的 JDK，可以使用 Eclipse Temurin 或 SDKMAN：

- Temurin 下载页：<https://adoptium.net/temurin/releases/?version=25>
- SDKMAN：

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk list java            # 查看可用的 25.x 标识
sdk install java <版本标识>
```

安装后确认 `java -version` 输出 25.x，并确保 `JAVA_HOME` 指向该 JDK。

### 安装 Kotlin Toolchain

推荐使用官方一键安装脚本。当前脚本会安装 Kotlin CLI **0.13.0** 到 `~/.local/bin`，自动加入 `PATH`，并首次执行一次 `kotlin --version` 触发 Toolchain 分发包下载。

Linux / macOS：

```bash
curl -fsSL https://kotl.in/install.sh | sh
```

Windows PowerShell：

```powershell
powershell -ExecutionPolicy ByPass -c "irm 'https://kotl.in/install.ps1' | iex"
```

> 如果希望在执行前审查脚本，可先下载：`curl -fsSL https://kotl.in/install.sh -o install.sh`，确认内容后再运行 `sh install.sh`。

重新打开终端后验证：

```bash
kotlin --version
```

脚本行为：

- 安装 wrapper 到 `~/.local/bin/kotlin`（Windows 为 `%USERPROFILE%\.local\bin\kotlin.bat`）。
- 自动把 `~/.local/bin` 写入当前 shell 的配置文件（zsh 为 `.zshrc` / `.zprofile`；bash 在 Linux 为 `.bashrc` / `.profile`，macOS 为 `.bash_profile` / `.profile`）；设置 `KOTLIN_CLI_NO_MODIFY_PATH=1` 可跳过。
- 通过 `KOTLIN_CLI_DOWNLOAD_ROOT` 可指定镜像下载源。
- 分发包约 400 MB，缓存在：

| 系统 | 缓存目录 |
| --- | --- |
| Linux | `~/.cache/JetBrains/Kotlin/cli/kotlin-cli-0.13.0` |
| macOS | `~/Library/Caches/JetBrains/Kotlin/cli/kotlin-cli-0.13.0` |
| Windows | `%LOCALAPPDATA%\JetBrains\Kotlin\cli\kotlin-cli-0.13.0` |

> 本项目自带的 `./kotlin`（Linux/macOS）和 `kotlin.bat`（Windows）wrapper 同样固定 0.13.0，首次构建会自动完成相同的下载和校验，因此**不执行全局安装也能直接构建**。全局安装适合希望在任意目录直接使用 `kotlin` 命令的场景。

相关环境变量：

| 变量 | 作用 |
| --- | --- |
| `KOTLIN_CLI_DOWNLOAD_ROOT` | 覆盖下载源（Maven 布局），默认 `https://packages.jetbrains.team/maven/p/amper/amper` |
| `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR` | 覆盖 Toolchain 缓存目录 |
| `KOTLIN_CLI_NO_MODIFY_PATH` | 安装脚本不修改 `PATH`（仅一键安装脚本使用） |
| `KOTLIN_CLI_JAVA_HOME` | 运行 Toolchain CLI 自身的 JRE，不影响应用的编译目标 |
| `KOTLIN_CLI_JAVA_OPTIONS` | 传给 Toolchain CLI JVM 的参数 |

### 安装 Android SDK

本项目只需要 Android SDK 命令行工具，不需要安装完整的 Android Studio。如果已经安装 Android Studio，也可以在 **SDK Manager** 中勾选 Android 37.0、Build-Tools 37.0.0 和 Platform-Tools，效果与下面的命令行安装相同。

#### 1. 下载命令行工具

官方页面：<https://developer.android.com/studio#command-line-tools-only>

当前最新为 **Command-line Tools 23.0**（2026-10 查询）：

| 平台 | 下载地址 |
| --- | --- |
| Linux x86_64 | <https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip> |
| Windows x86_64 | <https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip> |
| macOS Intel | <https://dl.google.com/android/repository/commandlinetools-mac_x86_64-16111833_latest.zip> |
| macOS Apple Silicon | <https://dl.google.com/android/repository/commandlinetools-mac_arm64-16111833_latest.zip> |

> `16111833` 是构建号。若链接失效，请以官方页面或 <https://dl.google.com/android/repository/repository2-3.xml> 中 `cmdline-tools;latest` 的地址为准。

#### 2. 解压到标准目录

标准目录结构是 `<SDK>/cmdline-tools/latest/`，`sdkmanager` 通过它推断 SDK 根目录；如果使用其他目录名，调用时需要显式传入 `--sdk_root`：

```bash
export ANDROID_HOME="$HOME/Softwares/AndroidSDK"
mkdir -p "$ANDROID_HOME/cmdline-tools"

unzip commandlinetools-linux-16111833_latest.zip -d /tmp/android-cmdline-tools
mv /tmp/android-cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --version
```

如果只需要 `adb`，也可以直接下载 platform-tools：

- Linux：<https://dl.google.com/android/repository/platform-tools-latest-linux.zip>
- macOS：<https://dl.google.com/android/repository/platform-tools-latest-darwin.zip>
- Windows：<https://dl.google.com/android/repository/platform-tools-latest-windows.zip>

#### 3. 接受许可并安装组件

```bash
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses

sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;37.0.0"
```

注意平台包名是 `platforms;android-37.0`（带 `.0`），对应 `compileSdk = 37`。`build-tools` 中提供 `aapt2`、`d8`、`zipalign`、`apksigner` 等打包/签名工具。

验证：

```bash
adb version
sdkmanager --sdk_root="$ANDROID_HOME" --list_installed
"$ANDROID_HOME/build-tools/37.0.0/apksigner" version
```

#### 4. 可选：安装模拟器

```bash
sdkmanager --sdk_root="$ANDROID_HOME" \
  "emulator" \
  "system-images;android-37.0;google_apis_ps16k;x86_64"

echo no | avdmanager create avd \
  -n BanLiMusic_API37 \
  -k "system-images;android-37.0;google_apis_ps16k;x86_64"

emulator -avd BanLiMusic_API37
```

使用真机时，开启开发者选项与 USB 调试，连接后执行 `adb devices` 确认设备可见。Linux 上如果显示 `no permissions`，请按官方文档配置 udev 规则：<https://developer.android.com/studio/run/device#setting-up>。

### 环境变量与本地配置

建议写入 shell 配置（`~/.bashrc` 或 `~/.zshrc`）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export ANDROID_HOME="$HOME/Softwares/AndroidSDK"
export ANDROID_SDK_ROOT="$ANDROID_HOME"   # 兼容仍读取旧变量的工具，可选
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

- `local.properties`：保存 `sdk.dir`，不提交 Git；Kotlin Toolchain 的 Android 构建会读取它。`ANDROID_HOME` 已设置时可省略。

  ```properties
  sdk.dir=/home/<user>/Softwares/AndroidSDK
  ```

  Windows 路径需要转义：

  ```properties
  sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
  ```

- `.vscode/settings.json`：把上述路径注入 VS Code 终端，避免每次手动 export。该文件被 Git 跟踪，里面的绝对路径是示例，克隆后请改成自己的实际路径。
- VS Code 扩展：安装 Kotlin 语言支持（扩展 ID `fwcd.kotlin`，提供补全、跳转、诊断等），构建和测试仍以 `./kotlin` 命令为准。
- 项目自带 `./kotlin`（Linux/macOS）和 `kotlin.bat`（Windows）wrapper；首次运行会下载并校验 Kotlin Toolchain 0.13.0，详见上一节。

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

当前 debug APK 输出路径为 `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`（该路径可能随 Toolchain 版本调整，以命令输出为准）。

首次构建需要下载依赖与工具链组件。Android SDK 路径来自 `local.properties` 或 `ANDROID_HOME`。新增 `shared/`、`desktopApp/` 等模块时，需要把目录名加入 `project.yaml` 的 `modules` 列表。

### 发布 APK

`./kotlin build -v release` 会生成未签名的 release APK：

```text
build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk
```

使用 Android SDK 的 `apksigner` 签名（示例）：

```bash
apksigner sign \
  --ks /path/to/release.keystore \
  --ks-key-alias banlimusic \
  --out dist/BanLiMusic-<version>-release.apk \
  build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk
```

本次已在 `dist/BanLiMusic-<version>-release.apk` 生成签名 APK；签名密钥保存在本机 `~/.local/share/BanLiMusic/keystore/`，未提交到仓库。正式发布时请使用你自己的长期 release keystore，并妥善备份。

## 架构

```
project.yaml                  Kotlin Toolchain 模块清单
libs.versions.toml            依赖版本目录
shared/                       KMP 共享核心（android + jvm）
  module.yaml
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
desktopApp/                   Compose Desktop 桌面端
  module.yaml
  src/main.kt                 窗口与 NavigationRail
  src/com/banlimusic/desktop/
    data/                     设置、收藏夹/历史/本地音乐、下载、缓存、仓库
    player/                   JLayer MP3 播放引擎
    ui/                       首页、搜索、音乐库、下载、设置、播放页
androidApp/
  module.yaml                 android/app 构建配置
  src/
    AndroidManifest.xml
    com/banlimusic/app/
      core/
        database/             Room 实体、DAO、v3→v4 缓存索引迁移
        datastore/            DataStore 设置存储与旧键迁移
        cache/                播放缓存、元数据缓存、缓存索引与分类统计
      data/
        repository/           搜索聚合、播放地址、收藏/历史、下载任务
      domain/
        download/             下载状态机与 WorkManager Worker
        playback/             流地址解析
      player/                 Media3 服务、队列持久化、收藏自定义命令
      feature/                Compose 页面与 ViewModel
      widget/                 RemoteViews 小组件、封面位图缓存
      ui/                     主题、通用组件与 A-Z 索引条
  res/
  test/
  schemas/                    Room schema 历史
```

跨平台代码位于 `shared/`（`kmp/lib`，platforms `android` + `jvm`），与平台无关的模型、Provider、GD 客户端、限流、文本/歌词解析、去重、URI 编解码、排序与首字母索引已下沉。`desktopApp/`（`jvm/app` + Compose Desktop）复用共享核心，使用 Compose Desktop + JLayer 实现桌面 UI 与 MP3 播放；设置使用系统 Preferences，收藏/历史和下载任务使用 JSON 文件。由于共享模块当前允许使用 `java.*` API，它面向 Android/JVM，不包含 iOS 目标。

桌面端运行：

```bash
./kotlin build -m desktopApp
./kotlin run -m desktopApp
```

Windows x64 便携版产物为 `dist/BanLiMusic-<version>-windows-x64.zip`，包含：

- `BanLiMusic.exe`：Launch4j 生成的 Windows 启动器（未签名）
- `app/`：应用 classpath 与依赖，包含 Windows Skiko 原生库
- `runtime/`：BellSoft Liberica JRE 21（Windows x64）
- `BanLiMusic.bat` / `BanLiMusic-console.bat`：启动器被安全软件拦截时的备用入口
- `README.txt`、`THIRD_PARTY_NOTICES.txt` 和 `.zip.sha256`

该包可以在 Linux/WSL 内交叉生成，不依赖 Windows 主机或 Wine；`desktopApp/module.yaml` 同时保留 Linux 与 Windows 的 Compose Desktop 原生运行时。由于 Amper 的 executable jar 会对不同 group 下同名的 `runtime-desktop` 做文件名去重，Windows 包使用普通 classpath 启动器，而不是 Spring Boot 可执行 jar。

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
- 列表页默认不主动请求专辑封面（播放页会加载并缓存），可在“下载与缓存”设置中开启；开启后会占用 5 分钟 50 次的 API 请求额度。
- Android 10+ 默认通过 MediaStore 写入系统 `Music/BanLiMusic`；自定义目录不可写或旧系统会回退到应用外部音乐目录。卸载应用不会自动删除已写入系统媒体库的文件。
- 4x1 桌面小组件会加载封面；封面由服务端/播放页缓存提供，失败时显示占位图。
- 中文 A-Z 首字母使用轻量 GB2312 映射，多音字和生僻字可能出现近似结果。
- 已在真机验证主要播放、下载与小组件流程；不同厂商桌面和系统版本仍需分别验证。
