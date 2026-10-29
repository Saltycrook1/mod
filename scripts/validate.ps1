param(
    [string]$GradleUserHome = ''
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectPath
try {
    $buildArgs = @('--no-daemon')
    if ($GradleUserHome) { $buildArgs += @('--gradle-user-home', $GradleUserHome) }
    & .\gradlew.bat @buildArgs clean build
    if ($LASTEXITCODE -ne 0) { throw "Build/unit tests failed: $LASTEXITCODE" }
    & .\gradlew.bat @buildArgs runGametest
    if ($LASTEXITCODE -ne 0) { throw "Headless integration failed: $LASTEXITCODE" }
    Write-Output 'Build, unit tests, and dedicated headless integration passed.'
    Write-Output 'Inspect build/reports/tests/test/index.html, build/gametest-results.xml, and build/adapter-benchmark.md.'
} finally {
    Pop-Location
}
