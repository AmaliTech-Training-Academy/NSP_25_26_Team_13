# LogStream Aggregator - Unified QA Test Runner
# This script executes both Python (Data Engineering) and Java (Backend) test suites.

$RepoRoot = Resolve-Path ".."
$QaDir = Get-Location
$DataEngDir = "$RepoRoot\data-engineering"
$ApiTestsDir = "$QaDir\api-tests"

Write-Host "`n=== [Phase 1: Python Data Engineering Tests] ===" -ForegroundColor Cyan
Set-Location $DataEngDir
if (Test-Path ".venv") {
    & .venv\Scripts\pytest.exe --cov=scripts --cov-report=term-missing
} else {
    Write-Warning "Python virtual environment not found. Ensure Phase 1 group project steps were followed."
}

Write-Host "`n=== [Phase 2: Java Backend API Tests] ===" -ForegroundColor Cyan
Set-Location $ApiTestsDir
& C:\maven\apache-maven-3.9.6\bin\mvn.cmd test

Write-Host "`n=== [Unified QA Execution Complete] ===" -ForegroundColor Green
Set-Location $QaDir
