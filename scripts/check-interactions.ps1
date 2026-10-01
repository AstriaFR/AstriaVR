. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'artifacts\interaction-checks'
$sourceDir = Join-Path $projectDir 'app\src\main\java\dev\astriavr\player'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'PlaybackTapSequence.java') (Join-Path $sourceDir 'PlaylistDragOrder.java') (Join-Path $sourceDir 'FlatVideoGeometry.java') (Join-Path $sourceDir 'SeekAcceleration.java') (Join-Path $projectDir 'tests\InteractionChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Interaction checks did not compile.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir InteractionChecks
if ($LASTEXITCODE -ne 0) { throw 'Interaction checks failed.' }
