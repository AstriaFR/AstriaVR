param([string]$Serial, [switch]$Offline)
. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (!$Serial) {
    $devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($devices.Count -ne 1) { throw 'Connect one authorized device or specify -Serial.' }
    $Serial = $devices[0]
}
$package = 'dev.astriavr.player.validation'
if ((& $adb -s $Serial shell pm list packages $package) -match "^package:$([regex]::Escape($package))(\.test)?$") {
    throw 'Validation package already exists. Remove only that test package or select another device.'
}
$buildArgs = @('--no-daemon', '--console=plain', '-p', $projectDir, ':app:assembleDebug', ':app:assembleDebugAndroidTest')
if ($Offline) { $buildArgs += '--offline' }
& $gradleExe @buildArgs
if ($LASTEXITCODE -ne 0) { throw 'Validation build failed.' }
$installed = $false; $testInstalled = $false
try {
    & $adb -s $Serial install (Join-Path $projectDir 'app/build/outputs/apk/debug/app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Validation APK install failed.' }
    $installed = $true
    & $adb -s $Serial install (Join-Path $projectDir 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation APK install failed.' }
    $testInstalled = $true
    New-Item -ItemType Directory -Force -Path (Join-Path $projectDir 'artifacts') | Out-Null
    $result = & $adb -s $Serial shell am instrument -w -r "$package.test/android.test.InstrumentationTestRunner"
    $result | Set-Content -LiteralPath (Join-Path $projectDir 'artifacts/device-checks.txt') -Encoding utf8
    $result
    foreach ($picture in @('playlist-check.png', 'settings-check.png')) {
        & $adb -s $Serial pull "/sdcard/Android/data/$package/files/$picture" (Join-Path $projectDir "artifacts/$picture")
    }
    if (($result -join "`n") -notmatch 'OK \(\d+ tests\)') { throw 'Device regression checks failed; see artifacts/device-checks.txt.' }
} finally {
    # Only remove test packages installed by this invocation; never touch the distribution app.
    if ($testInstalled) { & $adb -s $Serial uninstall "$package.test" }
    if ($installed) { & $adb -s $Serial uninstall $package }
}
