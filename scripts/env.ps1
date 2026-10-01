$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path (Split-Path $projectDir -Parent) '.android-toolchain'
if (Test-Path -LiteralPath $toolRoot) {
    if (!$env:JAVA_HOME) {
        $jdkEntry = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1
        if ($jdkEntry) { $env:JAVA_HOME = $jdkEntry.FullName }
    }
    if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $toolRoot 'sdk' }
    if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-user' }
}
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 or 21 installation. See docs/BUILDING.md.'
}
if (!$env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if ($env:ANDROID_HOME) { $env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH" }
$gradleExe = Join-Path $toolRoot 'gradle-9.1.0/bin/gradle.bat'
if (!(Test-Path -LiteralPath $gradleExe)) { $gradleExe = Join-Path $projectDir 'gradlew.bat' }
