# 发布与打包

本文说明 BanLiMusic 的发布产物、版本号维护和打包方式。架构与发布约束以 [AGENTS.md](../../AGENTS.md) 为准，构建与环境准备见 [build.md](build.md) 和 [environment.md](environment.md)。

## 发布产物

每个版本只上传以下 5 个文件：

| 平台 | 发布文件 | 构建平台 | 说明 |
| --- | --- | --- | --- |
| Android | `BanLiMusic-<version>-release.apk` | 任意 | 支持 Android 8.0（API 26）及以上 |
| Linux x64 | `banlimusic-nojre_<version>_amd64.deb` | Linux | Debian / Ubuntu；需要 Java 17+ |
| Linux x64 | `banlimusic-nojre-<version>-1.x86_64.rpm` | Linux | Fedora / RHEL；需要 Java 17+ |
| Windows x64 | `BanLiMusic-<version>.msi` | Windows | 内置 runtime，per-machine 安装 |
| Windows x64 | `BanLiMusic-<version>-windows-x64.zip` | Windows | 内置 runtime，解压即用 |

桌面端产物统一由 `scripts/` 下的打包脚本生成。脚本还支持 app-image、带 runtime 的 tar.gz/deb/rpm、exe、no-jre zip/MSI 等本地目标，但对外发布只保留上表 5 个文件。

## 版本号

版本号只以 `androidApp/module.yaml` 的 `versionCode` 与 `versionName` 为准，采用 `MAJOR.MINOR.PATCH`：

- MAJOR：仅在用户明确要求时变更，代理不得自行提升。
- MINOR：新增功能、较大范围重构、数据模型或跨模块行为调整。
- PATCH：Bug 修复、文案修正和小范围兼容性处理。

每次发版必须同时递增 `versionCode`、更新 `versionName`，并确保源码中没有硬编码版本号。`versionCode` 单调递增，不复用；版本变化及原因需记录在提交信息或发布说明中。

## Android APK

```bash
./kotlin test
./kotlin build
./kotlin build -v release
```

release 构建输出未签名 APK，路径以命令输出为准，当前为：

```text
build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk
```

使用仓库外的 release keystore 签名：

```bash
mkdir -p dist

"<ANDROID_SDK>/build-tools/37.0.0/apksigner" sign \
  --ks "<KEYSTORE_DIR>/release.keystore" \
  --ks-pass "file:<KEYSTORE_DIR>/keystore.pass" \
  --ks-key-alias <KEY_ALIAS> \
  --out "dist/BanLiMusic-<version>-release.apk" \
  build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk

"<ANDROID_SDK>/build-tools/37.0.0/apksigner" verify --verbose \
  "dist/BanLiMusic-<version>-release.apk"
```

keystore、口令与签名配置必须保存在仓库外，不得提交或写入日志。

## 桌面端发布包

桌面端打包脚本会自动构建 `desktopApp` release 产物、收集运行时 classpath、按平台过滤 Skiko jar，并把产物写入 `dist/`，同时生成 `SHA256SUMS-<version>.txt`。脚本默认从 `androidApp/module.yaml` 读取版本号。

### Linux：no-jre deb / rpm

依赖 Linux x64、JDK 25（含 `jpackage`）、`dpkg-deb`、`fakeroot` 与 `rpmbuild`。

```bash
./scripts/package-linux.sh linux-nojre-deb linux-nojre-rpm
```

产物：

```text
dist/banlimusic-nojre_<version>_amd64.deb
dist/banlimusic-nojre-<version>-1.x86_64.rpm
```

安装包不包含 JRE，deb/rpm 声明 Java 17+ 依赖，启动脚本也会检查 `java` 版本。安装与升级：

```bash
sudo dpkg -i "dist/banlimusic-nojre_<version>_amd64.deb"
sudo rpm -Uvh "dist/banlimusic-nojre-<version>-1.x86_64.rpm"
```

### Windows：内置 runtime 的 MSI / ZIP

依赖 Windows x64、JDK 25 与 WiX Toolset 3.x；确保 `candle.exe`、`light.exe` 在 `PATH` 中，或通过 `-WixDir` 指定 WiX 目录。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
    -Targets zip,msi -Jlink
```

产物：

```text
dist/BanLiMusic-<version>.msi
dist/BanLiMusic-<version>-windows-x64.zip
```

MSI 使用 per-machine 安装，需要管理员权限，支持选择 D: 等非系统盘目录。升级标识固定在脚本中，不要修改，否则无法覆盖升级。

## 签名

Android APK 必须使用长期维护的 release keystore 签名。桌面端产物默认不签名，Windows 可能显示 SmartScreen“未知发布者”提示。

如需为桌面端签名，使用脚本提供的选项：

```bash
# Linux：为产物和 SHA256SUMS 生成 GPG 分离签名
./scripts/package-linux.sh linux-nojre-deb linux-nojre-rpm --gpg-key <GPG_KEY_ID>
```

```powershell
# Windows：使用 PFX 证书签名 app-image 内的 exe 与 MSI
$env:BANLI_SIGN_PASSWORD = "<PFX_PASSWORD>"
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
    -Targets zip,msi -Jlink -Sign -CertificatePath "<KEYSTORE_DIR>\banli.pfx"
```

私钥、证书和口令必须保存在仓库外。签名后使用 `gpg --verify` 或 `signtool verify /pa` 校验。

## 发布检查

- [ ] `androidApp/module.yaml` 的 `versionCode` 与 `versionName` 已更新。
- [ ] `./kotlin test` 与 `./kotlin build` 通过。
- [ ] release APK 已签名，并在真机验证安装与启动。
- [ ] Linux no-jre deb/rpm 已在安装 Java 17+ 的环境验证安装与启动。
- [ ] Windows MSI/ZIP 已在内置 runtime 下验证安装、启动与卸载。
- [ ] 发布页只上传本文“发布产物”表中的 5 个文件。
- [ ] 如执行了桌面端签名，签名校验通过。
