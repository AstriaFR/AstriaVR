param([switch]$Offline, [switch]$Clean, [switch]$Debug)
. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
Push-Location $projectDir
try {
    $variant = if ($Debug) { 'debug' } else { 'release' }
    $taskVariant = if ($Debug) { 'Debug' } else { 'Release' }
    $buildArgs = @('--no-daemon', '--console=plain', ":app:assemble$taskVariant")
    if (!$Debug) { $buildArgs += @('-x', ':app:lintVitalAnalyzeRelease', '-x', ':app:lintVitalReportRelease', '-x', ':app:lintVitalRelease') }
    if ($Clean) { $buildArgs = @('clean') + $buildArgs }
    if ($Offline) { $buildArgs += '--offline' }
    & $gradleExe @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android packaging failed.' }
    $buildText = Get-Content -LiteralPath 'app/build.gradle.kts' -Raw
    $versionName = [regex]::Match($buildText, 'versionName\s*=\s*"([0-9A-Za-z.-]+)"').Groups[1].Value
    $suffix = if ($Debug) { '-debug' } else { '' }
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    $apkPath = Join-Path 'artifacts' "astriavr-$versionName$suffix.apk"
    $buildApk = "app/build/outputs/apk/$variant/app-$variant.apk"
    if (!$Debug -and !(Test-Path -LiteralPath 'signing.properties')) {
        $buildApk = 'app/build/outputs/apk/release/app-release-unsigned.apk'
        $apkPath = Join-Path 'artifacts' "astriavr-$versionName-unsigned.apk"
    }
    Copy-Item -LiteralPath $buildApk -Destination $apkPath
    Write-Output (Join-Path $projectDir $apkPath)
} finally { Pop-Location }
