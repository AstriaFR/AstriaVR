# 4.3 validation / 双语版本验证

The public source builds successfully as debug, instrumentation-test and unsigned release APKs. The bilingual catalog passes 1,367 checks across 314 entries. English is the initial language; saved language preferences are retained. No device or emulator was connected, so instrumentation and visual checks have not been executed on a device. The original distribution signing key is not included.

- 版本：4.3，versionCode 27；保持原应用 ID，首次启动默认英语，保留已有语言偏好。公开源码不包含发行签名，调试构建使用本机自动生成的调试证书。设置上栏按钮高 40dp、上栏高 46dp、顶部留白 6dp，比最初布局增加 12dp 选项区空间。
- 播放目录上栏 42dp（原 52dp）；列表项高 76dp、上下内边距各 4dp（上一版 80dp、6dp），内部可用高度仍为 68dp。封面、字号、内容间距不变。关于署名为 `bilibili @雨星衡  ·  github @AstriaFR`。
- 调试模式的中英文说明已删去上下栏保留星空背景的句子。
- 双语目录：314 项，覆盖设置、播放操作、播放列表、手柄说明、元数据、错误提示和无障碍标签。设置页投影选项使用简写，“Comfort tuning”名称与相关说明保持一致。
- `check-language.ps1`：检查双语非空、英文残留中文、参数与格式符一致性、文件名原样插入，以及中英文文件名投影识别不受界面语言影响。
- `check-math.ps1`：几何、手柄、投影、视场过渡、播放状态、鱼眼及播放列表回归通过。
- Android 仪器测试用例覆盖语言按钮直接切换后的播放/暂停状态、进度、设置页、语言持久化，以及两种语言、三种观看模式、600/740/840dp 宽度下的按钮布局。断言：观看模式按钮 156×40dp，所有模式保持左右双栏、语言选项占左侧半排且位于画面下方；普通模式右栏只有操控卡片。两选项按钮点击两次恢复原值且不弹窗，多选项仍打开选择器。
- 当前没有连接 Android 设备，仪器测试及截图检查尚未实机执行。不能将“测试代码编译通过”视为真机布局验证通过。

## 手动验收

1. 全新安装默认显示英语；已有语言偏好应保持。点击 Picture 下方的 Language 切换中文，再切回英语，确认即时生效且重启后保持。各模式语言卡片均在左栏、占半排。
2. 切回简体中文，确认按钮尺寸与英文一致；检查双镜头、单镜头和普通视频模式。
3. 分别在播放、暂停、视频结尾切换语言，确认视频和进度保留，视频结尾不会自动重播。
4. 检查投影选择、屏幕尺寸弹窗、手柄指南、播放列表搜索和视频操作面板的换行与边界。
5. 文件名与作者署名应保留原文，系统文件选择器遵循 Android 系统语言。
