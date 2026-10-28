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
| Linux x64 | 不带 | `banlimusic-nojre_<version>_amd64.deb` | Linux | 已安装 Java 17+，包管理器安装 |
| Linux x64 | 不带 | `banlimusic-nojre-<version>-1.x86_64.rpm` | Linux | 已安装 Java 17+，包管理器安装 |
| Windows x64 | 带 | `BanLiMusic-<version>-windows-x64.zip`（app-image） | Windows | 免安装 Java，解压即用 |
| Windows x64 | 带 | `BanLiMusic-<version>.exe` / `.msi` | Windows（WiX） | 安装向导、开始菜单 |
| Windows x64 | 不带 | `BanLiMusic-<version>-windows-x64-nojre.zip` | 任意平台 | 已安装 Java 17+ |
| Windows x64 | 不带 | `BanLiMusic-<version>-nojre.msi` | Windows（WiX） | 已安装 Java 17+，MSI 安装 |

> `jpackage` 只能生成**当前平台**的产物：Linux 不能生成 Windows 的 `.exe`/`.msi`，Windows 不能生成 `.deb`/`.rpm`。无 runtime 包不包含平台运行时，可以由任意平台准备 jar 和启动脚本；无 runtime 的 deb/rpm/MSI 安装包需要手工构建。

### 准备打包输入

日常发布可以直接使用下方的按需打包脚本；本节保留手工流程，便于排查问题。所有 jpackage 包都从同一组运行时依赖开始：

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
  --win-upgrade-uuid "<UPGRADE_UUID>"
```

把 `--type exe` 换成 `--type msi` 即可生成 MSI；其余参数相同。

> Windows 的 MSI/EXE 使用 **per-machine** 安装（不传 `--win-per-user-install`），因此安装和卸载会请求管理员权限，并支持通过 `--win-dir-chooser` 安装到 D: 等非系统盘。
>
> 不要用 per-user 安装配合非系统盘：Windows Installer 会在目标盘根目录创建 `Config.Msi`，标准用户卸载时可能无法修改回滚脚本权限，从而反复提示 `Could not set file security ... Error 5`。已经以 per-user 方式安装在非系统盘的旧版本，如无法卸载，可先以管理员身份运行卸载，或使用 Microsoft 的“程序安装和卸载疑难解答”清理残留。

> 如需 Authenticode 签名，先单独生成 app-image，用 `signtool` 签其中的 `BanLiMusic.exe`，再用 `--app-image "<APP_IMAGE>"` 生成 exe/msi 并签安装包。直接由 jpackage 生成安装包时，内部的 app-image 无法插入签名步骤；打包脚本的 `-Sign` 已按此顺序处理。

可选使用 `signtool` 对 `.exe`/`.msi` 做代码签名；证书与口令保存在仓库外，不要写入命令行历史或文档。

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

exec "$JAVA_BIN" -Dbanlimusic.version="<version>" -cp "$APP_HOME/libs/*" com.banlimusic.desktop.MainKt "$@"
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
"%JAVA_BIN%" -Dbanlimusic.version="<version>" -cp "%APP_HOME%libs\*" com.banlimusic.desktop.MainKt %*
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

### 不带 runtime 的安装包（deb / rpm / MSI）

`jpackage` 的 `deb`、`rpm`、`exe`、`msi` 类型都会内嵌 runtime，**不能**用 `jpackage` 生成不带 runtime 的安装包。需要安装包形式的 no-jre 产物时只能手工构建，jar 集合与无 runtime 便携版相同：

- **deb**：准备 `opt/banlimusic-nojre`、`usr/bin` 启动脚本、desktop 文件与图标；`DEBIAN/control` 写入 `Depends: java17-runtime | java21-runtime | java25-runtime`；最后执行 `dpkg-deb --build --root-owner-group`。
- **rpm**：用 `rpmbuild -bb` 构建，spec 写入 `Requires: java-headless >= 17`、`AutoReqProv: no`，安装路径与 deb 保持一致。
- **Windows MSI**：用 WiX 3.14 的 `heat` 收集 jar，`candle`/`light` 链接 per-user MSI；安装到 `%LOCALAPPDATA%\Programs\BanLiMusic`，开始菜单快捷方式指向 `BanLiMusic.bat`。

这些安装包都不包含 JRE，启动脚本会检查 Java 17+。Windows 目前只提供 no-jre MSI，没有 no-jre EXE 向导；如需 EXE 向导，需要额外的 Burn/Inno Setup 工程。

Linux 桌面发行版**通常不预装 Java**，不能假设用户环境已有 JRE。面向普通用户时优先提供带 runtime 的 app-image/安装包；deb/rpm 通过包依赖自动安装 Java 17+；no-jre tar.gz 则需要在发布说明中明确要求用户自行安装 Java 17+。

固定升级标识（不得随意更改）：

- `jpackage` EXE/MSI 的 `--win-upgrade-uuid`：`96341748-fd12-47ca-a45f-db1d1c6a8358`
- 手工 no-jre MSI 的 `UpgradeCode`：`26e51776-c54b-45ed-8989-a94a5f55bba3`

### 按需打包脚本

打包脚本按构建平台拆分，避免在 Linux 上交叉生成 Windows 安装包：

- Linux：`scripts/package-linux.sh`，在 Linux 上执行，支持 app-image、tar.gz、deb、rpm 及对应的 no-jre 版本。
- Windows：`scripts/package-windows.ps1`，在 Windows 上执行，支持 app-image、zip、exe、msi 及 no-jre zip/MSI。

```bash
# Linux：全部目标，使用 jlink 裁剪 runtime
./scripts/package-linux.sh --jlink all-linux

# Linux：只打某个目标
./scripts/package-linux.sh --jlink linux-tar linux-nojre-deb

# Linux：打包并用 GPG 密钥签名
./scripts/package-linux.sh --jlink --gpg-key <GPG_KEY_ID> all-linux
```

```powershell
# Windows：全部目标，使用 jlink 裁剪 runtime
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 -Jlink

# Windows：只打 zip、exe 和 no-jre MSI
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
    -Targets zip,exe,nojre-msi -Jlink `
    -JdkHome <WIN_JDK> -WixDir <WIX_DIR>

# Windows：打包并用 Authenticode 证书签名
$env:BANLI_SIGN_PASSWORD = "<PFX_PASSWORD>"
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
    -Targets zip,exe,msi -Sign `
    -CertificatePath "<KEYSTORE_DIR>\banli.pfx"
```

脚本会自动构建 `desktopApp`、读取或生成运行时 classpath、按平台过滤 Skiko jar，并把产物写入 `dist/`，同时生成 `SHA256SUMS-<version>.txt`。打包时还会按源码实际引用裁剪 `material-icons-extended`（Linux 使用 `scripts/trim-icons.sh`，Windows 脚本内置同等逻辑），通常可把该 jar 从约 37 MB 降到约 200 KB。Linux 依赖 `jpackage`、`dpkg-deb`、`fakeroot`、`rpmbuild`；Windows 依赖 JDK 25 与 WiX 3.14。`--jlink`/`-Jlink` 使用以下模块集；桌面端 OneDrive 登录依赖 `jdk.httpserver`，中文 A-Z 首字母依赖 `jdk.charsets`，不要从列表中移除：

```text
java.base,java.desktop,java.instrument,java.logging,java.prefs,
jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.httpserver,jdk.zipfs
```

### 桌面端产物签名

桌面平台没有 Android 那样“未签名不能安装”的强制机制，但未签名产物会带来两类问题：Windows 的 SmartScreen 显示“未知发布者”，Linux 用户也无法确认下载文件来自发布者。签名是可选的发布步骤，私钥、证书和口令必须保存在仓库外。

#### 证书与密钥从哪里来

- **SignPath Foundation（推荐开源项目）**：为 OSS 提供免费代码签名，证书签发给 SignPath Foundation，私钥保存在其 HSM，通过 CI 提交签名请求。要求项目使用 OSI 认可许可证、公开仓库、已有发布、持续维护，并在项目主页声明代码签名策略、团队角色与隐私政策；每次发布需人工批准。申请：<https://signpath.org/apply>，条件：<https://signpath.org/terms>。
- **Certum Open Source Code Signing**：商业证书，面向使用开源许可证的项目，费用低于普通 OV/EV；证书可显示开源开发者信息。购买前确认私钥交付方式（PFX、硬件令牌或云端签名），当前脚本的 `-CertificatePath` 只适用于 PFX。参考：<https://www.certum.eu/en/code-signing-certificates/>。
- **Azure Artifact Signing（原 Trusted Signing）**：按量付费，证书由 Microsoft 管理，适合能使用 Azure 的团队；Public Trust 对个人开发者和组织有地区限制（个人目前限美国/加拿大，组织限指定国家/地区），中国大陆暂不适用。参考：<https://learn.microsoft.com/azure/artifact-signing/>。
- **传统 OV/EV 证书**：DigiCert、Sectigo、GlobalSign、SSL.com 等，费用较高；私钥通常放在硬件令牌或云端 HSM，CI 签名需要额外的云签名方案。
- **自签名证书**：免费，只适合内部测试；目标机器必须手动信任根证书，不能消除公开分发的“未知发布者”。
- **Linux GPG**：不需要 CA，免费生成并发布公钥即可；建议使用独立签名子密钥并将主密钥离线保存。公钥指纹应写入 README 和发布说明。

> 申请 SignPath/Certum 前，仓库必须包含 OSI 认可的开源许可证文件；当前仓库尚未添加 `LICENSE`。许可证选择需由项目维护者确认，并确保不违反上游 GD 音乐台的使用条款。

#### Windows（Authenticode）

`jpackage` 不会自动签名。Windows 脚本在传入 `-Sign` 后会：

1. 生成 app-image，并用 `signtool` 签名其中的 `BanLiMusic.exe`；
2. 用已签名的 app-image 生成 exe/msi，再签名安装包；
3. 对每个签名文件执行 `signtool verify /pa`，失败时终止打包。

使用 PFX 证书（口令通过环境变量传入，避免出现在命令历史中）：

```powershell
$env:BANLI_SIGN_PASSWORD = "<PFX_PASSWORD>"
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
    -Targets zip,exe,msi -Sign `
    -CertificatePath "<KEYSTORE_DIR>\banli.pfx" `
    -TimestampUrl "http://timestamp.digicert.com"
```

也可以使用当前用户证书存储中的证书：

```powershell
powershell ... -Targets exe,msi -Sign `
    -CertificateThumbprint "<CERT_SHA1_THUMBPRINT>"
```

`-SignTool` 可指定 `signtool.exe` 路径；未指定时从 `PATH` 或 Windows SDK 中查找。签名后手动验证：

```powershell
signtool verify /pa /v "dist\BanLiMusic-<version>.exe"
signtool verify /pa /v "dist\BanLiMusic-<version>.msi"
```

证书选择：

- **OV/EV 代码签名证书**：传统 PFX 方式；OV 仍需积累 SmartScreen 信誉，EV 通常更快建立信任。
- **Azure Trusted Signing**：按需付费，使用 `signtool` 的 Trusted Signing 参数；需要额外的元数据配置，本脚本未封装。
- **开源项目免费签名**：可申请 SignPath 等服务，在 CI 中完成签名。
- **自签名证书**：仅适合内部测试，目标机器必须先信任根证书，不能消除公开分发时的“未知发布者”。

#### Linux（GPG）

Linux 没有统一的“可执行文件签名”校验机制。发布脚本使用 GPG 分离签名，对每个产物生成 `.asc`，并签名 `SHA256SUMS-<version>.txt`：

```bash
./scripts/package-linux.sh --jlink --gpg-key "<GPG_KEY_ID>" all-linux

gpg --verify "dist/SHA256SUMS-<version>.txt.asc" "dist/SHA256SUMS-<version>.txt"
cd dist && sha256sum -c "SHA256SUMS-<version>.txt"
```

`<GPG_KEY_ID>` 可以是密钥 ID 或指纹；脚本会为 `SHA256SUMS-<version>.txt` 中列出的每个文件生成同名 `.asc`。CI 中应通过受保护的密钥环或 `gpg-agent` 提供私钥，不要把私钥或口令写入仓库。

如果通过 apt/dnf 仓库分发，还需要签名仓库元数据（apt 的 `Release`/`InRelease`、dnf 的 `repomd.xml`）；`dpkg -i` 与 `rpm -i` 默认不校验安装包签名，因此 deb/rpm 的 `.asc` 主要用于下载校验。

### 体积参考与选择

以当前 0.9.0 产物（`--jlink`）为例：

- Linux 带 runtime app-image：解压后约 136 MB，其中 `lib/runtime` 约 57 MB、`lib/app` 约 79 MB；tar.gz 约 109 MB。
- Linux 无 runtime 包：`libs` 约 79 MB，压缩包约 75 MB；其中 `material-icons-extended` 约 37 MB，是当前最大的依赖。
- deb/rpm：带 runtime 约 104 MB 与 109 MB；no-jre 约 72 MB 与 75 MB。
- Windows app-image：解压后约 126 MB（runtime 约 49 MB）；zip 约 106 MB；exe/msi 约 107 MB；no-jre zip/MSI 约 73 MB/71 MB。
- 不使用 jlink 时，Linux runtime 约 137 MB，app-image 约 216 MB；Windows app-image 约 217 MB。

选择建议：

- **面向普通用户**：优先提供带 runtime 的 app-image 或安装包，避免用户配置 Java 和 classpath。
- **面向开发者/已有 Java 的环境**：提供 `no-jre` 包，体积约减半。
- **减小带 runtime 包**：使用脚本的 `--jlink`/`-Jlink`；不要直接删除 app-image 的 `lib/runtime`，启动器依赖它。

### 跨平台构建建议

- Linux 的 app-image、deb、rpm 在 Linux 上构建；Windows 的 app-image、exe、msi 在 Windows 上构建。
- `scripts/package-linux.sh` 只处理 Linux 目标，`scripts/package-windows.ps1` 只处理 Windows 目标；不要用 Linux Bash 脚本交叉生成 Windows 安装包。
- jar 中的 Skiko 原生库决定它能运行在哪些平台；Linux 和 Windows 的打包输入分别保留对应平台的 Skiko jar。
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
- [ ] 如使用 Windows 代码签名，`BanLiMusic.exe` 与 exe/msi 均已通过 `signtool verify /pa`。
- [ ] 如使用 Linux GPG 签名，产物 `.asc` 与 `SHA256SUMS-<version>.txt.asc` 均已验证通过。
- [ ] 使用 `scripts/package-linux.sh` 与 `scripts/package-windows.ps1` 打包时，带 runtime 产物已验证 `--jlink`/`-Jlink` 裁剪后的启动、网络与资源加载。
- [ ] Linux 与 Windows 打包分别在对应系统执行，未在 Linux 上交叉生成 Windows 安装包。
- [ ] Linux/Windows 的无 runtime 便携包已在仅安装 Java 17+ 的干净环境验证。
- [ ] no-jre deb/rpm 已解包启动验证，并确认依赖 Java 17+。
- [ ] no-jre MSI 已按用户安装、启动并卸载验证。
- [ ] `dist/SHA256SUMS-<version>.txt` 已按当前版本重新生成。
- [ ] 桌面端产物未使用 executable-jar，`libs/` 或 `lib/app` 中的依赖 jar 完整。
- [ ] README 中的产物名保持 `BanLiMusic-<version>-release.apk` 等占位符，未硬编码版本号。
