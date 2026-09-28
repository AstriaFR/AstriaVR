. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'app/build/boundary-checks'
$sourceDir = Join-Path $projectDir 'app/src/main/java/dev/astriavr/player'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'BoundarySeekSequence.java') (Join-Path $sourceDir 'PlaybackTapSequence.java') (Join-Path $sourceDir 'PlaybackPosition.java') (Join-Path $projectDir 'tests/BoundarySeekChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Boundary checks did not compile.' }
& "$env:JAVA_HOME/bin/java.exe" -cp $classesDir BoundarySeekChecks
if ($LASTEXITCODE -ne 0) { throw 'Boundary checks failed.' }
