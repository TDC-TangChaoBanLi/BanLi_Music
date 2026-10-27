# 开发环境

本文说明构建 BanLiMusic 所需的系统依赖、JDK、Kotlin Toolchain、Android SDK 与本地配置。构建命令见 [build.md](build.md)，发布流程见 [release.md](release.md)。

## 已验证环境

| 项目 | 版本 / 路径 |
| --- | --- |
| 操作系统 | Fedora Linux 44（Workstation Edition），x86_64 |
| JDK | OpenJDK 25.0.4.1（Red Hat build） |
| `JAVA_HOME` | `<JDK_HOME>`（指向 JDK 25 安装目录） |
| Android SDK | `<ANDROID_SDK>`（Android SDK 根目录） |
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

## 安装前的系统依赖

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
export JAVA_HOME="<JDK_HOME>"
export PATH="$JAVA_HOME/bin:$PATH"
```

> `androidApp/module.yaml` 中声明了 `jvm.jdk.version: 25`、`selectionMode: auto`。如果本机只安装了 JDK 17/21，请安装 JDK 25，或把该值改成已安装的版本。

## 其他 JDK 安装方式

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

## 安装 Kotlin Toolchain

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

## 安装 Android SDK

本项目只需要 Android SDK 命令行工具，不需要安装完整的 Android Studio。如果已经安装 Android Studio，也可以在 **SDK Manager** 中勾选 Android 37.0、Build-Tools 37.0.0 和 Platform-Tools，效果与下面的命令行安装相同。

### 1. 下载命令行工具

官方页面：<https://developer.android.com/studio#command-line-tools-only>

当前最新为 **Command-line Tools 23.0**（2026-10 查询）：

| 平台 | 下载地址 |
| --- | --- |
| Linux x86_64 | <https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip> |
| Windows x86_64 | <https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip> |
| macOS Intel | <https://dl.google.com/android/repository/commandlinetools-mac_x86_64-16111833_latest.zip> |
| macOS Apple Silicon | <https://dl.google.com/android/repository/commandlinetools-mac_arm64-16111833_latest.zip> |

> `16111833` 是构建号。若链接失效，请以官方页面或 <https://dl.google.com/android/repository/repository2-3.xml> 中 `cmdline-tools;latest` 的地址为准。

### 2. 解压到标准目录

标准目录结构是 `<ANDROID_SDK>/cmdline-tools/latest/`，`sdkmanager` 通过它推断 SDK 根目录；如果使用其他目录名，调用时需要显式传入 `--sdk_root`：

```bash
export ANDROID_HOME="<ANDROID_SDK>"
mkdir -p "$ANDROID_HOME/cmdline-tools"

unzip commandlinetools-linux-16111833_latest.zip -d android-cmdline-tools
mv android-cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --version
```

如果只需要 `adb`，也可以直接下载 platform-tools：

- Linux：<https://dl.google.com/android/repository/platform-tools-latest-linux.zip>
- macOS：<https://dl.google.com/android/repository/platform-tools-latest-darwin.zip>
- Windows：<https://dl.google.com/android/repository/platform-tools-latest-windows.zip>

### 3. 接受许可并安装组件

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

### 4. 可选：安装模拟器

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

## 环境变量与本地配置

建议写入 shell 配置（`~/.bashrc` 或 `~/.zshrc`）：

```bash
export JAVA_HOME="<JDK_HOME>"
export ANDROID_HOME="<ANDROID_SDK>"
export ANDROID_SDK_ROOT="$ANDROID_HOME"   # 兼容仍读取旧变量的工具，可选
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

- `local.properties`：保存 `sdk.dir`，不提交 Git；Kotlin Toolchain 的 Android 构建会读取它。`ANDROID_HOME` 已设置时可省略。

  ```properties
  sdk.dir=<ANDROID_SDK>
  ```

  Windows 路径需要转义：

  ```properties
  sdk.dir=C\:\\<SDK_PATH>
  ```

- `.vscode/settings.json`：本地配置，用于把上述路径注入 VS Code 终端，避免每次手动 export。`.vscode/` 已被 Git 忽略，不要提交；克隆后请改成自己的实际路径。
- VS Code 扩展：安装 Kotlin 语言支持（扩展 ID `fwcd.kotlin`，提供补全、跳转、诊断等），构建和测试仍以 `./kotlin` 命令为准。
- 项目自带 `./kotlin`（Linux/macOS）和 `kotlin.bat`（Windows）wrapper；首次运行会下载并校验 Kotlin Toolchain 0.13.0，详见上一节。
