# 开发文档

本目录面向参与 BanLiMusic 开发的工程师与自动化代理，记录架构、功能现状、开发经验、环境准备、构建、发布和云同步等技术信息。产品介绍、截图与合规声明请见仓库根目录的 [README](../README.md)。

## 文档索引

| 文档 | 职责 | 同步要求 |
| --- | --- | --- |
| [../README.md](../README.md) | 产品介绍、界面截图、下载安装与合规声明 | 只引用 `doc/picture/` 下的图片；不写本机路径、环境步骤和内部类名；功能变化时同步更新亮点与用户相关的已知限制 |
| [architecture.md](architecture.md) | 模块划分、源码目录、关键设计原则 | 目录结构或模块职责变化时更新；与 [AGENTS.md](../AGENTS.md) 保持一致 |
| [features.md](features.md) | 已实现功能现状与已知限制 | 功能、设置项、测试数量或限制变化时更新 |
| [development/experience.md](development/experience.md) | 开发经验、实现取舍与常见坑 | 跨模块做法、兼容性处理或打包经验变化时更新 |
| [development/environment.md](development/environment.md) | JDK、Android SDK、Kotlin Toolchain、环境变量与本地配置 | 工具链版本、SDK 版本或 JDK 要求变化时更新 |
| [development/build.md](development/build.md) | 构建、测试、运行与桌面端启动 | 构建命令、产物路径或模块变化时更新 |
| [development/release.md](development/release.md) | 发布产物、版本号、Android 签名与桌面端脚本打包 | 版本流程、签名方式或打包方式变化时更新 |
| [cloud-sync.md](cloud-sync.md) | Android/桌面端 OneDrive 同步实现、应用注册与安全约束 | 同步协议、权限、回调或 `CLIENT_ID` 变化时更新 |
| [picture/](picture/) | README 插图与演示动图 | 图片必须被 README 引用；文件名使用可读中文或英文，避免空格与 `&` 等需要转义的字符 |
| [GD_MUSIC_API.md](GD_MUSIC_API.md) | 上游 GD 音乐台 API 参考 | 仅随上游 API 文档更新；不写入本机信息 |

## 文档维护约定

- 文档中的路径、用户名、签名材料统一使用占位符，例如 `<ANDROID_SDK>`、`<JDK_HOME>`、`<KEYSTORE_DIR>`、`<version>`，不要写入本机绝对路径或真实用户名。
- 修改功能、架构、构建命令或发布流程时，同步更新对应文档；新增、删除或重命名文档后更新本索引。
- 产品截图与演示动图放在 `doc/picture/`，README 只引用该目录下的图片。
- 版本号只以 `androidApp/module.yaml` 的 `versionCode` / `versionName` 为准，文档中使用 `<version>` 占位。
- 所有文档只写当前项目状态，不写修改历史或过时实现。
- 本文档与 [AGENTS.md](../AGENTS.md) 冲突时，以 `AGENTS.md` 的强制约束为准。
