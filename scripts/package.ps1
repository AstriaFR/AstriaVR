param([switch]$Build, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
if ($Build) { & (Join-Path $PSScriptRoot 'build.ps1') -Offline:$Offline }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifactDir = Join-Path $projectDir 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$buildText = Get-Content -LiteralPath (Join-Path $projectDir 'app/build.gradle.kts') -Raw
$versionName = [regex]::Match($buildText, 'versionName\s*=\s*"([0-9A-Za-z.-]+)"').Groups[1].Value
if (!$versionName) { throw 'Missing versionName for the source archive.' }
$archive = Join-Path $artifactDir "astriavr-$versionName-source.zip"
# Explicit source whitelist: never include local signing material or build caches.
$sourceFiles = @(
    foreach ($folder in @('app/src', 'scripts', 'tests', 'docs', 'gradle', '.github')) {
        Get-ChildItem -LiteralPath (Join-Path $projectDir $folder) -Recurse -File
    }
    foreach ($file in @('README.md', 'README.zh-CN.md', 'CONTRIBUTING.md', '.gitignore', '.gitattributes', 'gradlew', 'gradlew.bat', 'build.gradle.kts', 'settings.gradle.kts',
        'gradle.properties', 'toolchain.lock.json', 'app/build.gradle.kts', 'app/proguard-rules.pro')) {
        Get-Item -LiteralPath (Join-Path $projectDir $file)
    }
)
$stream = [System.IO.File]::Open($archive, [System.IO.FileMode]::Create)
$zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $sourceFiles) {
        $relative = $file.FullName.Substring($projectDir.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName,
            "v$versionName/$relative", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose(); $stream.Dispose() }
Write-Output $archive
