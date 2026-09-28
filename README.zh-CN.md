<p align="center"><img src="app/src/main/res/drawable-nodpi/astria_launcher_art.webp" width="128" alt="AstriaVR 图标"></p>

# AstriaVR

**在 Android 手机上观看本地普通视频与 VR 全景视频。**

[English](README.md) · 简体中文

AstriaVR 支持手机直看和双镜头 VR 观看，提供播放列表、进度记忆、手柄操作与镜头调节。界面采用蓝紫色星云背景，支持中文和英语。

| 项目 | 信息 |
| --- | --- |
| 当前版本 | 4.3（versionCode 27） |
| 系统要求 | Android 9 / API 28 及以上，OpenGL ES 2.0 |
| 开发语言 | Kotlin、Java |
| 播放引擎 | AndroidX Media3 / ExoPlayer |
| 应用 ID | `dev.astriavr.player` |
| 源码许可 | 暂未添加开源许可证 |

## 功能

- **普通视频与 VR 播放**：支持普通平面视频、180° / 360° 等距柱状全景，以及依观看模式提供的 180° 鱼眼、立体鱼眼、Cubemap 3×2 和 EAC 投影。
- **视频布局**：支持左右分屏（SBS）、上下分屏（TB）和单画面；可自动识别部分片源，也可手动选择。
- **播放目录**：列表和网格显示、视频封面、排序，以及播放进度保存。
- **观看调节**：瞳距、视场角、镜头畸变补偿、亮度与播放速度；调试模式（Comfort tuning）提供对齐网格，方便调整观看舒适度。
- **触控与手柄**：屏幕操作、手柄输入、陀螺仪方向控制；应用内提供操控说明。
- **双语界面**：设置、播放列表、提示和操控指南支持简体中文与 English，首次启动默认英语，保留已有语言偏好。切换语言时保留视频、进度和播放状态。

具体解码能力取决于设备和 Android 系统提供的解码器；不保证所有编码、分辨率或 VR 文件变体均可播放。陀螺仪为可选硬件。

## 开始使用

1. 安装自行编译的 APK，打开播放目录，通过系统文件选择器添加本地视频。
2. 在设置中选择观看模式，并按片源选择投影与视频布局；自动识别不正确时可手动调整。
3. 根据手机和 VR 眼镜调整瞳距、视场角与镜头补偿。
4. 需要中文时，在设置左栏 Picture 下方点击 Language。

列表会记录播放进度。自然播放结束后停留在片尾，不会自动跳到下一个视频。边界切片的操作细节见 [更新记录](CHANGELOG.md)。


## 从源码构建

使用 JDK 17 或 21、Android SDK 36 / Build Tools 36.0.0。项目使用 Android Gradle Plugin 9.0.1 和 Gradle 9.1.0。

```powershell
# Windows；先设置 JAVA_HOME、ANDROID_HOME
.\gradlew.bat :app:assembleDebug
```

```sh
# macOS / Linux
./gradlew :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，应用 ID 为 `dev.astriavr.player.validation`，可与正式版并存。未配置签名时，release 构建产出未签名 APK；签名与回归检查见 [构建说明](docs/BUILDING.md)。

## 源码结构

| 路径 | 内容 |
| --- | --- |
| `app/src/main` | 播放、VR 渲染、界面、资源及本地化 |
| `app/src/androidTest` | Android 仪器测试与测试素材 |
| `tests` | 几何、投影、播放、操控、缓存和翻译检查 |
| `scripts` | 构建、检查、源码打包及素材生成脚本 |
| `docs/design-assets` | 当前采用的图标和背景源素材 |

仓库仅包含当前版本源码与相关素材；签名密钥、SDK、缓存和本地构建产物不随源码上传。

## 数据与权限

本版本通过系统文件选择器访问用户选择的视频，清单未声明互联网权限。播放列表、偏好、进度和封面缓存保存在设备上；应用备份已关闭。系统文件选择器及其文件提供方由 Android 和相应应用管理。

## 反馈与署名

欢迎通过 [Issues](../../issues) 报告问题或提出建议。请附应用版本、Android 版本、设备型号、片源投影及复现步骤，避免上传私人视频和敏感日志。详见 [贡献与反馈说明](CONTRIBUTING.md)。

bilibili @雨星衡 · GitHub [@AstriaFR](https://github.com/AstriaFR)

## 许可说明

本仓库公开源码，但目前**未添加开源许可证**，不将公开可见视为额外授予使用、修改或再分发许可。第三方依赖按各自许可证提供；本说明不改变其许可条件。
