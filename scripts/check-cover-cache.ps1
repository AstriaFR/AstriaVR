. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'app/build/cover-cache-checks'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $projectDir 'app/src/main/java/dev/astriavr/player/CoverCacheFiles.java') (Join-Path $projectDir 'tests/CoverCacheChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Cover cache checks did not compile.' }
& "$env:JAVA_HOME/bin/java.exe" -cp $classesDir CoverCacheChecks
if ($LASTEXITCODE -ne 0) { throw 'Cover cache checks failed.' }
