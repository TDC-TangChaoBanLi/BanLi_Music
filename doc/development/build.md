# 构建与测试

本文说明 BanLiMusic 的日常构建、测试、运行和桌面端启动方式。环境准备见 [environment.md](environment.md)，发布流程见 [release.md](release.md)。

## 首次构建与网络说明

- `androidApp/module.yaml` 已将默认 Google Maven 仓库覆盖为 `https://dl.google.com/dl/android/maven2`，避免本机无法访问 `maven.google.com` 时反复超时。
- Kotlin Toolchain 的 Android 打包阶段会在内部生成临时 Gradle 工程，通过 Gradle 9.5.0 + AGP 9.3.1 完成 APK/AAB。项目仓库不维护 Gradle 文件，日常只需使用 `./kotlin` 命令。
- 首次 Android 打包会下载 Gradle 9.5.0；如果 `services.gradle.org` 较慢，可预先从腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-9.5.0-bin.zip` 下载并放入 `~/.gradle/wrapper/dists/gradle-9.5.0-bin/` 对应缓存目录。缓存完成后可离线构建。

## 构建与测试

```bash
./kotlin build                 # 编译并生成 APK
./kotlin test                  # 运行 JVM 单元测试
./kotlin package -m androidApp # 生成发布 AAB（可选）
./kotlin run -m androidApp     # 安装并运行到已连接的设备/模拟器
```

当前 debug APK 输出路径为 `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`（该路径可能随 Toolchain 版本调整，以命令输出为准）。

首次构建需要下载依赖与工具链组件。Android SDK 路径来自 `local.properties` 或 `ANDROID_HOME`。新增 `shared/`、`desktopApp/` 等模块时，需要把目录名加入 `project.yaml` 的 `modules` 列表。

提交前至少运行：

```bash
./kotlin test
./kotlin build
```

新增核心算法（去重、限流、解析、迁移、下载状态机）必须补充 JVM 单元测试。

## 桌面端

桌面端复用 `shared/` 的核心与共享 UI，使用 Compose Desktop + JLayer 播放 MP3。

```bash
./kotlin build -m desktopApp
./kotlin run -m desktopApp
```

如果 Compose Hot Reload 的 JetBrains Runtime 下载失败，可使用 `./kotlin run -m desktopApp --no-compose-hot-reload` 启动普通 JVM 模式。

桌面端设置使用系统 Preferences，收藏/历史和下载任务使用 JSON 文件。当前仅支持 MP3 播放，暂不提供无损音质与自动更新。

桌面端便携版与安装包打包见 [release.md](release.md#桌面端发布包)。
