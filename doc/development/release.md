# 发布与打包

本文说明 Android 发布 APK 的生成、签名，以及桌面端带/不带 runtime 的便携版与安装包打包方式。版本号规则以仓库根目录的 [AGENTS.md](../../AGENTS.md) 为准。

## Android 发布 APK

`./kotlin build -v release` 会生成未签名的 release APK：

```text
build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk
```

使用 Android SDK 的 `apksigner` 签名（示例）：

```bash
"<ANDROID_SDK>/build-tools/37.0.0/apksigner" sign \
  --ks "<KEYSTORE_DIR>/release.keystore" \
  --ks-pass "file:<KEYSTORE_DIR>/keystore.pass" \
  --ks-key-alias <KEY_ALIAS> \
  --out "dist/BanLiMusic-<version>-release.apk" \
  build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk
```

签名密钥保存在仓库外的安全位置，不要提交到 Git，也不要把密码写入文档或日志。正式发布应使用长期维护的 release keystore，并妥善备份。

发布前需要：

1. 更新 `androidApp/module.yaml` 中的 `versionCode` 与 `versionName`，这是唯一的版本号来源。
2. 运行 `./kotlin test` 与 `./kotlin build`。
3. 执行 `./kotlin build -v release`，重新签名并校验产物。
4. 在提交信息或发布说明中记录版本号变化及原因。

## 桌面端发布包

桌面端提供**带 runtime**与**不带 runtime**两类发布包，并按目标平台提供便携版和安装包。版本号与 Android 共用 `androidApp/module.yaml` 的 `versionName`。

### 产物矩阵

| 目标平台 | runtime | 产物 | 构建平台 | 适用场景 |
| --- | --- | --- | --- | --- |
| Linux x64 | 带 | `BanLiMusic-<version>-linux-x64.tar.gz`（app-image） | Linux | 免安装 Java，解压即用 |
| Linux x64 | 带 | `banlimusic_<version>_amd64.deb` | Linux | Debian / Ubuntu 包管理 |
| Linux x64 | 带 | `banlimusic-<version>-1.x86_64.rpm` | Linux | Fedora / RHEL 包管理 |
| Linux x64 | 不带 | `BanLiMusic-<version>-linux-x64-nojre.tar.gz` | 任意平台 | 已安装 Java 17+ |
| Windows x64 | 带 | `BanLiMusic-<version>-windows-x64.zip`（app-image） | Windows | 免安装 Java，解压即用 |
| Windows x64 | 带 | `BanLiMusic-<version>.exe` / `.msi` | Windows（WiX） | 安装向导、开始菜单 |
| Windows x64 | 不带 | `BanLiMusic-<version>-windows-x64-nojre.zip` | 任意平台 | 已安装 Java 17+ |

> `jpackage` 只能生成**当前平台**的产物：Linux 不能生成 Windows 的 `.exe`/`.msi`，Windows 不能生成 `.deb`/`.rpm`。无 runtime 包不包含平台运行时，可以由任意平台准备 jar 和启动脚本。

### 准备打包输入

所有 jpackage 包都从同一组运行时依赖开始：

```bash
APP_IMAGE="<APP_IMAGE>"
PKG_INPUT="build/desktop-package/input"

rm -rf "$PKG_INPUT"
mkdir -p "$PKG_INPUT"
cp "$APP_IMAGE"/lib/app/*.jar "$PKG_INPUT"/
```

`<APP_IMAGE>` 是已有的 app-image 目录。首次打包时可先运行 `./kotlin run -m desktopApp --no-compose-hot-reload`，从进程的 `-classpath` 中收集全部 jar 复制到 `$PKG_INPUT`。主 jar 以 `lib/app/*.cfg` 中第一条 `app.classpath` 为准（当前为 `000-desktopApp-jvm.jar`，重新构建后序号可能变化）。

> 不要使用 `./kotlin package -m desktopApp -f executable-jar` 的结果作为输入：Spring Boot 可执行 JAR 会把不同 group 下同名的 `runtime-desktop` 去重，导致 Compose 运行时缺失。

### 图标资源

桌面端窗口图标通过 shared 的 Compose 资源加载，安装包图标同样使用 `shared/composeResources/drawable/banli_logo.png`。Linux 的 `jpackage` 直接接受该 PNG；Windows 需要 `.ico`，可从同一 PNG 转换后作为打包输入，生成的 ICO 不要放回 `composeResources/drawable`。

### 带 runtime：Linux 便携版

```bash
rm -rf build/desktop-package/BanLiMusic

jpackage \
  --type app-image \
  --name BanLiMusic \
  --app-version "<version>" \
  --input "$PKG_INPUT" \
  --main-jar 000-desktopApp-jvm.jar \
  --main-class com.banlimusic.desktop.MainKt \
  --icon shared/composeResources/drawable/banli_logo.png \
  --dest build/desktop-package

tar -czf "dist/BanLiMusic-<version>-linux-x64.tar.gz" \
  -C build/desktop-package BanLiMusic
```

解压后运行 `BanLiMusic/bin/BanLiMusic`。包内 `lib/runtime` 是 jpackage 生成的精简 JRE，目标机器无需安装 Java。

### 带 runtime：Linux 安装包（deb / rpm）

工具依赖：

- deb：`fakeroot`、`dpkg-deb`
- rpm：`rpmbuild`

在 Debian / Ubuntu 上通常只需安装 `fakeroot`（`dpkg-deb` 随 `dpkg` 提供）；Fedora 可安装 `fakeroot dpkg rpm-build`。为 Linux 专用包准备独立的输入目录，并删除 `*windows-x64*.jar`，避免把 Windows Skiko 原生库打进 Linux 包，同时也避免影响其他产物。

```bash
jpackage \
  --type deb \
  --name BanLiMusic \
  --app-version "<version>" \
  --input "$PKG_INPUT" \
  --main-jar 000-desktopApp-jvm.jar \
  --main-class com.banlimusic.desktop.MainKt \
  --icon shared/composeResources/drawable/banli_logo.png \
  --dest dist \
  --linux-shortcut \
  --linux-menu-group "Audio" \
  --linux-package-name banlimusic \
  --linux-deb-maintainer "<MAINTAINER_EMAIL>"
```

生成 RPM 时把 `--type deb` 换成 `--type rpm`，移除 `--linux-deb-maintainer`，并按需添加 `--linux-rpm-license-type "<LICENSE_ID>"`（项目未声明许可证时可省略）。

安装与升级：

```bash
sudo dpkg -i "dist/banlimusic_<version>_amd64.deb"
sudo rpm -Uvh "dist/banlimusic-<version>-1.x86_64.rpm"
```

deb/rpm 会安装到系统目录并注册菜单项，升级通过包管理器完成。安装包应使用仓库外的发布签名/GPG 密钥签名，不要把私钥或口令写入仓库。

### 带 runtime：Windows 便携版

Windows 产物必须在 Windows 上构建；app-image 本身不需要 WiX。准备 JDK 25（含 `jpackage`）和同一组 jar。为 Windows 专用包准备独立输入目录，并删除 `*linux-x64*.jar` 可减小体积：

```powershell
Remove-Item -Recurse -Force build\desktop-package\BanLiMusic -ErrorAction SilentlyContinue
$PKG_INPUT = "build\desktop-package\input"

jpackage `
  --type app-image `
  --name BanLiMusic `
  --app-version "<version>" `
  --input $PKG_INPUT `
  --main-jar 000-desktopApp-jvm.jar `
  --main-class com.banlimusic.desktop.MainKt `
  --icon <ICON_ICO> `
  --dest build\desktop-package

Compress-Archive `
  -Path build\desktop-package\BanLiMusic `
  -DestinationPath "dist\BanLiMusic-<version>-windows-x64.zip"
```

解压后运行 `BanLiMusic\BanLiMusic.exe`。包内包含 Windows runtime，目标机器无需安装 Java。

### 带 runtime：Windows 安装包（exe / msi）

在 Windows 上额外安装 **WiX Toolset 3.x**（兼容性最好），并确保 `candle.exe`、`light.exe` 在 `PATH` 中。`--win-upgrade-uuid` 首次发布时用 `[guid]::NewGuid()` 生成一次，后续版本保持不变，才能覆盖升级。

```powershell
$PKG_INPUT = "build\desktop-package\input"

jpackage `
  --type exe `
  --name BanLiMusic `
  --app-version "<version>" `
  --input $PKG_INPUT `
  --main-jar 000-desktopApp-jvm.jar `
  --main-class com.banlimusic.desktop.MainKt `
  --icon <ICON_ICO> `
  --dest dist `
  --win-shortcut `
  --win-menu `
  --win-dir-chooser `
  --win-per-user-install `
  --win-upgrade-uuid "<UPGRADE_UUID>"
```

把 `--type exe` 换成 `--type msi` 即可生成 MSI；其余参数相同。去掉 `--win-per-user-install` 可改为全局安装。

可选使用 `signtool` 对 `.exe`/`.msi` 做代码签名，证书与口令保存在仓库外，不要写入命令行历史或文档。

### 不带 runtime：Linux / Windows

无 runtime 包只包含 jar 和启动脚本，要求目标机器已安装 **Java 17 或更高版本**。它可以复用带 runtime 包的 `lib/app` jar：Java 类 jar 与平台无关，但必须保留目标平台的 Skiko runtime jar。

Linux 目录结构：

```text
BanLiMusic-<version>-linux-x64-nojre/
  bin/BanLiMusic
  libs/*.jar
```

`bin/BanLiMusic`：

```bash
#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "未找到 Java，请安装 Java 17 或更高版本。" >&2
  exit 1
fi

JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 \
  | awk -F'"' '/version/ {print $2}' \
  | awk -F. '{print ($1 == 1 ? $2 : $1)}')
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then
  echo "需要 Java 17 或更高版本，当前为 ${JAVA_MAJOR:-未知}。" >&2
  exit 1
fi

exec "$JAVA_BIN" -cp "$APP_HOME/libs/*" com.banlimusic.desktop.MainKt "$@"
```

打包：

```bash
NOJRE_DIR="build/desktop-nojre/BanLiMusic"
mkdir -p "$NOJRE_DIR/bin" "$NOJRE_DIR/libs"
cp "$PKG_INPUT"/*.jar "$NOJRE_DIR/libs"/
# 将上面的启动脚本保存为 "$NOJRE_DIR/bin/BanLiMusic"
chmod +x "$NOJRE_DIR/bin/BanLiMusic"

tar -czf "dist/BanLiMusic-<version>-linux-x64-nojre.tar.gz" \
  -C build/desktop-nojre BanLiMusic
```

Windows 目录结构：

```text
BanLiMusic-<version>-windows-x64-nojre\
  BanLiMusic.bat
  libs\*.jar
```

`BanLiMusic.bat`：

```bat
@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)
"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (
  echo 未找到 Java，请安装 Java 17 或更高版本。
  exit /b 1
)
"%JAVA_BIN%" -cp "%APP_HOME%libs\*" com.banlimusic.desktop.MainKt %*
```

打包：

```powershell
$NOJRE = "build\desktop-nojre\BanLiMusic"
New-Item -ItemType Directory -Force "$NOJRE\libs" | Out-Null
Copy-Item "$PKG_INPUT\*.jar" "$NOJRE\libs"
# 将 BanLiMusic.bat 保存到 $NOJRE
Compress-Archive -Path $NOJRE `
  -DestinationPath "dist\BanLiMusic-<version>-windows-x64-nojre.zip"
```

为减小体积，可以在 Linux 包中删除 `*windows-x64*.jar`，在 Windows 包中删除 `*linux-x64*.jar`；不要删除 `skiko-awt-*.jar`。无 runtime 包应在发布说明中明确标注“需要 Java 17+”。

### 体积参考与选择

以当前 0.9.0 产物为例：

- Linux 带 runtime app-image：解压后约 216 MB，其中 `lib/runtime` 约 137 MB、`lib/app` 约 79 MB；tar.gz 约 125 MB。
- Linux 无 runtime 包：`libs` 约 79 MB，压缩包约 75 MB；其中 `material-icons-extended` 约 37 MB，是当前最大的依赖。
- deb/rpm：分别约 108 MB 与 110 MB，安装后占用的空间接近解压后的 app-image。
- Windows 包的体积构成类似，具体随依赖版本变化。

选择建议：

- **面向普通用户**：优先提供带 runtime 的 app-image 或安装包，避免用户配置 Java 和 classpath。
- **面向开发者/已有 Java 的环境**：提供 `no-jre` 包，体积约减半。
- **希望进一步减小带 runtime 包**：用 `jlink` 裁剪 runtime，再通过 `jpackage --runtime-image` 使用该 runtime；不要直接删除 app-image 的 `lib/runtime`，启动器依赖它。

### 跨平台构建建议

- Linux 的 app-image、deb、rpm 在 Linux 上构建；Windows 的 app-image、exe、msi 在 Windows 上构建。
- 无 runtime 包可以在 Linux 上准备 jar 和脚本，再在目标平台补充启动脚本；jar 中的 Skiko 原生库决定它能运行在哪些平台。
- CI 建议使用 `ubuntu-latest` 构建 Linux 产物、`windows-latest` 构建 Windows 产物；两个任务共用同一版本号和输入 jar。
- 桌面端目前没有自动更新；Windows 安装包依赖固定的 `--win-upgrade-uuid` 实现覆盖升级，Linux 通过 deb/rpm 包管理器升级。

### 常见问题

- `jlink` 报 `已修改 .../conf/security/java.security`：当前 JDK 安装已被修改，换用干净的 JDK（例如 Kotlin Toolchain 缓存的 JDK）执行 `jpackage`，或重新安装 JDK。
- `--type deb` 报类型无效或不受支持：通常是缺少 `dpkg-deb`。安装 `dpkg` 后重试，或把可用的 `dpkg-deb` 加入 `PATH`。
- Windows 安装包构建失败：确认构建主机是 Windows，并且 WiX 的 `candle.exe`、`light.exe` 在 `PATH` 中；Linux 无法交叉生成 `.exe`/`.msi`。

## 发布检查清单

- [ ] `androidApp/module.yaml` 版本号已按 MAJOR.MINOR.PATCH 规则更新。
- [ ] `./kotlin test` 通过。
- [ ] `./kotlin build` 通过。
- [ ] release APK 已使用仓库外的 keystore 签名，并在真机验证安装与启动。
- [ ] Linux 带 runtime 的 app-image 与 deb/rpm 已安装验证。
- [ ] Windows 带 runtime 的 app-image、exe/msi 已在 Windows 上安装验证。
- [ ] Linux/Windows 的无 runtime 包已在仅安装 Java 17+ 的干净环境验证。
- [ ] 桌面端产物未使用 executable-jar，`libs/` 或 `lib/app` 中的依赖 jar 完整。
- [ ] README 中的产物名保持 `BanLiMusic-<version>-release.apk` 等占位符，未硬编码版本号。
