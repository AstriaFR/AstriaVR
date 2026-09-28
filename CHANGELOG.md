# 更新记录 / Changelog

## 4.3 · versionCode 27

English is the default on first launch; an existing language preference is retained. / 首次启动默认英语，保留已有语言偏好。

- 新增简体中文 / English 界面切换，语言偏好保存；切换时保留当前视频、进度和播放状态。
- 设置页在所有观看模式下使用左右双栏；普通模式的操控设置独占右栏，语言放在画面下方、占半排。
- 两选项设置直接切换；多选项保留弹窗。英文说明使用完整表述，片源投影和布局选项适当简写。
- 设置上栏按钮高 40dp；上栏和顶部留白合计比原布局节省约 12dp。
- 播放目录上栏由 52dp 改为 42dp；列表项最终为 76dp，上下内边距各 4dp，图片、字号及内容间距保持不变。
- 调试模式英文名称改为 Comfort tuning；删去说明中关于上下栏保留星空背景的句子。
- 关于署名加入 GitHub @AstriaFR。

English: adds persistent Chinese / English selection, preserves playback when switching languages, refines two-column settings, directly toggles binary choices, reduces header and playlist padding, and uses “Comfort tuning” for the lens-alignment mode.

## 4.2.1

- 播放上栏增加时间与电量显示，隐藏或进入后台后停止监听。
- 通过播放目录添加视频，简化操控说明与关于介绍。

English: adds time and battery status to playback controls and simplifies video selection and help text.

## 4.2

- 片尾停顿至少 0.6 秒后，可通过右侧屏幕双击或快进按钮双击播放下一项。
- 向前切片仅在 0 秒暂停并停顿至少 0.6 秒后，使用左侧屏幕双击或快退按钮双击。
- 按钮双击间隔小于 0.75 秒；连续快进到边界或手柄长按自动重复不触发切片。
- 相邻视频遵循播放目录实际排序并循环；自然播完停在片尾。
- 视频实际开始播放后自动退出调试模式；双击左上角标志也可退出。

English: adds deliberate boundary navigation to adjacent playlist videos, while natural playback remains stopped at the end. Comfort tuning ends when playback actually starts.
