# androidApp/res 资源说明

本目录存放 Android APK 的图标、界面资源、桌面小组件布局和配置。新增、删除或重命名资源时，请同步更新本文件。

## 子目录

| 目录 | 作用 |
| --- | --- |
| `drawable/` | 矢量图标、通知小图标、播放控制图标、小组件背景与进度条等 XML drawable。 |
| `drawable-nodpi/` | 不按屏幕密度缩放的位图资源，目前用于应用 LOGO。 |
| `layout/` | 桌面小组件的 `RemoteViews` 布局。 |
| `mipmap-anydpi-v26/` | Android 8.0+ 自适应启动图标（普通与圆形）。 |
| `values/` | 通用颜色、字符串和主题定义。 |
| `values-night/` | 深色模式下的主题覆盖。 |
| `xml/` | AppWidgetProvider 的元数据配置。 |

## 文件明细

### drawable/

- `ic_favorite.xml` / `ic_favorite_dark.xml`：小组件“已收藏”图标，分别用于深色和浅色背景。
- `ic_favorite_border.xml` / `ic_favorite_border_dark.xml`：小组件“未收藏”图标，分别用于深色和浅色背景。
- `ic_launcher_background.xml`：自适应启动图标的板栗棕渐变背景。
- `ic_launcher_foreground.xml`：自适应启动图标前景，包含板栗与音符图形。
- `ic_launcher_monochrome.xml`：Android 13+ 主题图标使用的单色音符图形。
- `ic_notification.xml`：通知栏与前台服务使用的小图标。
- `ic_pause.xml` / `ic_pause_dark.xml`：小组件暂停按钮，分别用于深色和浅色背景。
- `ic_play.xml` / `ic_play_dark.xml`：小组件播放按钮，分别用于深色和浅色背景。
- `ic_skip_next.xml` / `ic_skip_next_dark.xml`：小组件下一首按钮，分别用于深色和浅色背景。
- `ic_skip_previous.xml` / `ic_skip_previous_dark.xml`：小组件上一首按钮，分别用于深色和浅色背景。
- `widget_artwork_placeholder.xml`：小组件没有海报时的占位图形。
- `widget_bg_dark.xml` / `widget_bg_light.xml`：小组件黑色/白色磨砂背景。
- `widget_progress.xml`：小组件底部播放进度条背景。

### drawable-nodpi/

- `banli_logo.png`：`BanLiMusic-LOGO.png` 的应用内 LOGO 资源，用于“关于”页面。

### layout/

- `widget_player_small.xml`：3×1 桌面小组件布局，显示歌名、歌手、播放控件和进度条。
- `widget_player_large.xml`：4×1 桌面小组件布局，在 3×1 基础上增加海报与收藏按钮。

### mipmap-anydpi-v26/

- `ic_launcher.xml`：普通自适应启动图标，引用背景、前景和单色图层。
- `ic_launcher_round.xml`：圆形自适应启动图标，图层与普通图标一致。

### values/

- `colors.xml`：应用启动图标背景色和窗口背景色。
- `strings.xml`：应用名称、桌面小组件名称等字符串。
- `themes.xml`：应用主主题、透明主题和启动主题。

### values-night/

- `themes.xml`：深色模式下的主题与窗口背景覆盖。

### xml/

- `player_widget_info.xml`：3×1 小组件的尺寸、更新周期和初始布局配置。
- `player_widget_large_info.xml`：4×1 小组件的尺寸、更新周期和初始布局配置。

## 维护约定

- 资源文件名使用小写字母、数字和下划线，避免大写与空格。
- 自适应图标必须同时保留 `background`、`foreground` 和可选的 `monochrome` 图层。
- 桌面小组件布局被 `WidgetRenderer` 通过 `RemoteViews` 引用；修改控件 ID 时需同步更新 Kotlin 代码。
- 新增资源后请运行 `./kotlin build`，确认资源引用与打包正常。
