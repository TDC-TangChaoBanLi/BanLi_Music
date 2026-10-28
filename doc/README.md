# 开发文档

本目录面向参与 BanLiMusic 开发的工程师与自动化代理，记录架构、功能现状、环境准备、构建、发布和云同步等技术信息。产品介绍、截图与合规声明请见仓库根目录的 [README](../README.md)。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [architecture.md](architecture.md) | 模块划分、源码目录、关键设计原则 |
| [features.md](features.md) | 已实现功能现状与已知限制 |
| [development/environment.md](development/environment.md) | JDK、Android SDK、Kotlin Toolchain、环境变量与本地配置 |
| [development/build.md](development/build.md) | 构建、测试、运行与桌面端启动 |
| [development/release.md](development/release.md) | Android/桌面端签名发布、桌面端便携版与安装包打包 |
| [cloud-sync.md](cloud-sync.md) | OneDrive 云同步实现、应用注册与安全约束 |
| [../GD_MUSIC_API.md](../GD_MUSIC_API.md) | 上游 GD 音乐台 API 参考（外部资料） |
| [picture/](picture/) | README 使用的产品截图与演示动图 |

## 文档维护约定

- 文档中的路径、用户名、签名材料统一使用占位符，例如 `<ANDROID_SDK>`、`<JDK_HOME>`、`<KEYSTORE_DIR>`、`<version>`，不要写入本机绝对路径或真实用户名。
- 修改功能、架构、构建命令或发布流程时，同步更新对应文档；新增文档后在本索引中登记。
- 产品截图与演示动图放在 `doc/picture/`，README 只引用该目录下的图片。
- 版本号只以 `androidApp/module.yaml` 的 `versionCode` / `versionName` 为准，文档中使用 `<version>` 占位。
