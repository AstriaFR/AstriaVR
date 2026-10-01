param([string]$Python)
$ErrorActionPreference = 'Stop'
if (!$Python) {
    $installed = Get-Command python.exe -ErrorAction SilentlyContinue | Where-Object { $_.Source -notlike '*WindowsApps*' } | Select-Object -First 1
    if ($installed) { $Python = $installed.Source }
    else {
        $bundled = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
        if (Test-Path -LiteralPath $bundled) { $Python = $bundled }
    }
}
if (!$Python) { throw 'Python 3 is required for SQLite regression checks. Pass build.ps1 -Python <python.exe>.' }
& $Python (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests/playlist_sql_checks.py')
if ($LASTEXITCODE -ne 0) { throw 'SQLite migration / ordering checks failed.' }
