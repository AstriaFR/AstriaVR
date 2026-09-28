. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'artifacts/language-checks'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
$sourceDir = Join-Path $projectDir 'app/src/main/java/dev/astriavr/player'
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'AppText.java') (Join-Path $sourceDir 'VideoProjection.java') (Join-Path $projectDir 'tests/LanguageChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Language checks did not compile.' }
& "$env:JAVA_HOME/bin/java.exe" -cp $classesDir LanguageChecks
if ($LASTEXITCODE -ne 0) { throw 'Language checks failed.' }
