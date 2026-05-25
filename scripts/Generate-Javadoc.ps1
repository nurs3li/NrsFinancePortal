# Generates HTML Javadoc for all backend Maven modules.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

Write-Host "Generating Javadoc for finance-service, marketdata, notification-service, log-consumer-service..."
$mvn = Join-Path $root "mvnw.cmd"
if (-not (Test-Path $mvn)) { $mvn = "mvn" }
& $mvn javadoc:javadoc -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Open in browser:"
Write-Host "  finance-service\target\reports\apidocs\index.html"
Write-Host "  marketdata\target\reports\apidocs\index.html"
Write-Host "  notification-service\target\reports\apidocs\index.html"
Write-Host "  log-consumer-service\target\reports\apidocs\index.html"
