<p align="center"><img src="app/src/main/res/drawable-nodpi/astria_launcher_art.webp" width="128" alt="AstriaVR icon"></p>

# AstriaVR

**A local standard and VR video player for Android.**

English · [简体中文](README.zh-CN.md)

Watch on your phone or through a dual-lens VR headset, with playlists, saved playback positions, controller input and adjustable optics. AstriaVR features a blue-purple nebula theme and Chinese / English interfaces.

| Item | Details |
| --- | --- |
| Version | 4.3 (versionCode 27) |
| Requirements | Android 9 / API 28+, OpenGL ES 2.0 |
| Languages | Kotlin, Java |
| Playback | AndroidX Media3 / ExoPlayer |
| Application ID | `dev.astriavr.player` |
| Source license | No open-source license selected |

## Features

- Standard video and 180° / 360° equirectangular panoramas, with 180° fisheye, stereo fisheye, Cubemap 3×2 and EAC options depending on viewing mode.
- SBS, top/bottom and mono layouts, with automatic detection for some sources and manual overrides.
- List and grid playlists, thumbnails, sorting and saved playback positions.
- IPD, field of view, lens correction, brightness and playback speed adjustments. Comfort tuning provides an alignment grid.
- Touch, gamepad and gyroscope controls, plus an in-app controller guide.
- English by default, with Chinese and English settings, playlists, prompts and control instructions. Switching languages retains the current video, position and playback state.

Codec, resolution and format support depends on the device and available Android decoders. A gyroscope is optional.

## Getting started

1. Install an APK built from this source, open the playlist and add local videos through the system file picker.
2. Select a viewing mode, source projection and video layout. Override detection when necessary.
3. Adjust IPD, field of view and lens correction for your phone and headset.
4. Switch to English using **Language**, below **Picture** in the left settings column.

Playback positions are saved. Natural playback stops at the end rather than automatically advancing to another video.

## Version 4.3

Adds Chinese / English switching, keeps settings in two columns, places Language below Picture and directly toggles settings with two choices. More compact headers and playlist rows preserve thumbnail and text sizes.

[Changelog](CHANGELOG.md) · [Build guide](docs/BUILDING.md) · [Report an issue](../../issues)

## Build

Install JDK 17 or 21 and Android SDK 36 with Build Tools 36.0.0. The project uses AGP 9.0.1 and Gradle 9.1.0.

```powershell
# Windows: configure JAVA_HOME and ANDROID_HOME first
.\gradlew.bat :app:assembleDebug
```

```sh
# macOS / Linux
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use `dev.astriavr.player.validation` and can coexist with the distribution app. Release builds are unsigned unless you configure your own signing credentials. See the [build guide](docs/BUILDING.md) for signing and verification.

## Project layout

- `app/src/main`: playback, VR rendering, UI, resources and localization.
- `app/src/androidTest`: device instrumentation tests and fixtures.
- `tests`: geometry, projection, playback, controller, cache and translation checks.
- `scripts`: build, verification, source packaging and asset generation.
- `docs/design-assets`: source artwork used by the app.

Signing keys, SDKs, caches and local build outputs are excluded.

## Data and permissions

This version accesses user-selected video files through the Android system picker and declares no Internet permission. Playlists, preferences, playback positions and cover caches are stored on the device. App backup is disabled. The system picker and its file providers are managed by Android and their respective apps.

## Feedback and credits

Use [Issues](../../issues) for bug reports and suggestions. Include the app version, Android version, device model, projection and reproduction steps. Do not attach private videos or sensitive logs. See [contribution guidance](CONTRIBUTING.md).

bilibili @雨星衡 · GitHub [@AstriaFR](https://github.com/AstriaFR)

## License status

The source is public, but **no open-source license has been added**. Public visibility is not an additional grant of permission to use, modify or redistribute the project. Third-party dependencies retain their own license terms.
