. (Join-Path $PSScriptRoot 'env.ps1')
$projectDir = Split-Path $PSScriptRoot -Parent
$classesDir = Join-Path $projectDir 'artifacts\math-checks'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
$sourceDir = Join-Path $projectDir 'app\src\main\java\dev\astriavr\player'
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'Optics.java') (Join-Path $sourceDir 'FovTransition.java') (Join-Path $projectDir 'tests\TransitionChecks.java') (Join-Path $sourceDir 'PoseMath.java') (Join-Path $sourceDir 'ViewOrientation.java') (Join-Path $sourceDir 'GamepadState.java') (Join-Path $sourceDir 'PlaybackTuning.java') (Join-Path $sourceDir 'SeekAcceleration.java') (Join-Path $projectDir 'tests\GeometryChecks.java') (Join-Path $projectDir 'tests\ControllerChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Math checks did not compile.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir GeometryChecks
if ($LASTEXITCODE -ne 0) { throw 'Math checks failed.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir ControllerChecks
if ($LASTEXITCODE -ne 0) { throw 'Controller checks failed.' }
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'AppText.java') (Join-Path $sourceDir 'VideoProjection.java') (Join-Path $projectDir 'tests\ProjectionChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Projection checks did not compile.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir ProjectionChecks
if ($LASTEXITCODE -ne 0) { throw 'Projection checks failed.' }

& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir TransitionChecks
if ($LASTEXITCODE -ne 0) { throw 'Transition checks failed.' }

& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d $classesDir (Join-Path $sourceDir 'PlaybackPosition.java') (Join-Path $sourceDir 'FovTransition.java') (Join-Path $sourceDir 'ViewOrientation.java') (Join-Path $sourceDir 'PoseMath.java') (Join-Path $projectDir 'tests\PlaybackChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Playback checks did not compile.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir PlaybackChecks
if ($LASTEXITCODE -ne 0) { throw 'Playback checks failed.' }

& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $classesDir -d $classesDir (Join-Path $sourceDir 'CoverProjection.java') (Join-Path $sourceDir 'PlaylistNavigation.java') (Join-Path $projectDir 'tests\PlaylistChecks.java') (Join-Path $projectDir 'tests\FisheyeChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Playlist checks did not compile.' }
& "$env:JAVA_HOME\bin\java.exe" -cp $classesDir FisheyeChecks
if ($LASTEXITCODE -ne 0) { throw 'Fisheye checks failed.' }
& "$env:JAVA_HOME\bin\java.exe" '-Djava.awt.headless=true' -cp $classesDir PlaylistChecks (Join-Path $projectDir 'artifacts\cover-projection-check.png')
if ($LASTEXITCODE -ne 0) { throw 'Playlist checks failed.' }
