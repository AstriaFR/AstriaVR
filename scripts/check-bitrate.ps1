. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'artifacts/bitrate-checks'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $projectDir 'app/src/main/java/dev/astriavr/player/VideoBitrateMeter.java') (Join-Path $projectDir 'tests/VideoBitrateChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Bitrate checks did not compile.' }
& "$env:JAVA_HOME/bin/java.exe" -cp $classesDir VideoBitrateChecks
if ($LASTEXITCODE -ne 0) { throw 'Bitrate checks failed.' }
