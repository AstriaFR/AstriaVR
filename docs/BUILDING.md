# 构建与验证 / Building and verification

## 工具链 / Toolchain

- JDK 17 或 21 / JDK 17 or 21
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Gradle 9.1.0（Wrapper 固定版本 / pinned by the wrapper）
- Android Gradle Plugin 9.0.1
- AndroidX Media3 1.10.1

设置 `JAVA_HOME` 和 `ANDROID_HOME`，或在项目根目录的 `local.properties` 中写入 `sdk.dir=...`。Android Studio 也可导入项目。首次构建需要联网下载 Gradle 和 Maven 依赖。

Set `JAVA_HOME` and `ANDROID_HOME`, or provide `sdk.dir=...` in a local `local.properties` file. You can also import the project into Android Studio. The first build needs access to the Gradle distribution and Maven repositories.

## 调试构建 / Debug build

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux 使用 `./gradlew`。输出为 `app/build/outputs/apk/debug/app-debug.apk`。Android 工具链自动生成本机调试签名，包名为 `dev.astriavr.player.validation`，不覆盖正式应用。

On macOS / Linux, use `./gradlew`. The APK is signed with your local Android debug key and uses the application ID `dev.astriavr.player.validation`.

## 正式构建与签名 / Release build and signing

```powershell
.\gradlew.bat :app:assembleRelease
```

未配置签名时，产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`，不可直接安装。要签名，请在根目录创建被 Git 忽略的 `signing.properties`，并填入自己的密钥信息：

Without signing configuration, the output is `app/build/outputs/apk/release/app-release-unsigned.apk` and cannot be installed directly. To sign it, create an ignored `signing.properties` with your own values:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Windows 路径请使用正斜杠。配置后输出为 `app-release.apk`。不要提交该文件或任何密钥。仓库不包含作者的发行签名；自行签名的 APK 不能覆盖安装使用另一证书签名的 APK。

Use forward slashes for Windows paths. Signed output is `app-release.apk`. Never commit signing properties or keys. The author's distribution key is not included; an APK signed with another certificate cannot update an existing installation signed by that author.

## Windows 辅助脚本 / Windows helper scripts

```powershell
./scripts/build.ps1 -Debug
./scripts/build.ps1
./scripts/package.ps1
./scripts/check-language.ps1
./scripts/check-math.ps1
```

辅助脚本使用环境变量中的 JDK / SDK 和仓库 Wrapper；也支持作者原有的上级 `.android-toolchain` 布局。只有本机已缓存依赖后才使用 `-Offline`。素材已包含在仓库中，普通构建无需运行素材生成脚本。

Helper scripts use your configured JDK / SDK and the wrapper, with a fallback for the original sibling `.android-toolchain` layout. Use `-Offline` only after dependencies have been cached. Assets are already included; asset generation is not required for a normal build.

## 验证 / Verification

`tests` 下的 Java 检查可由对应 PowerShell 脚本运行；部分额外检查使用 Python 或 Node.js。仪器测试需要已授权的 Android 设备或模拟器：

Java checks are run with the matching PowerShell scripts; some additional checks use Python or Node.js. Instrumentation requires an authorized Android device or emulator:

```powershell
./scripts/check-device.ps1
```

设备检查脚本安装并在结束时移除其自身安装的 validation 测试包，不操作正式应用；若 validation 包已存在会退出。构建成功不等于完成真机显示与播放验证。

The device script installs and later removes the validation packages it installed, leaving the distribution app untouched. It stops if validation packages already exist. Successful compilation is not evidence of device playback or visual validation.
