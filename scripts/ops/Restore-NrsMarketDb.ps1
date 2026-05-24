<#
.SYNOPSIS
  Backup-NrsMarketDb.ps1 ile alınan .dump dosyasını nrs_market'e geri yükler (pg_restore).

.PARAMETER DumpFile
  backups\nrs_market-YYYYMMDD-HHmmss.dump yolu (repo köküne göre veya mutlak).

.EXAMPLE
  .\scripts\ops\Restore-NrsMarketDb.ps1 -DumpFile backups\nrs_market-20260522-120000.dump
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$DumpFile,
    [string]$PostgresContainer = "nrs-postgres",
    [string]$Database = "nrs_market",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolved = if ([System.IO.Path]::IsPathRooted($DumpFile)) { $DumpFile } else { Join-Path $root $DumpFile }
if (-not (Test-Path $resolved)) { throw "Dump not found: $resolved" }

$pgUser = $env:POSTGRES_USER
if ([string]::IsNullOrWhiteSpace($pgUser)) { $pgUser = "nrs" }

$dumpName = [System.IO.Path]::GetFileName($resolved)
$containerTmp = "/tmp/$dumpName"

Write-Host "Restoring $resolved -> $Database on $PostgresContainer (user=$pgUser)"
if ($Clean) { Write-Host "Clean mode: mevcut nesneler drop edilerek üzerine yazılır." }

docker cp $resolved "${PostgresContainer}:${containerTmp}"
if ($LASTEXITCODE -ne 0) { throw "docker cp failed (exit $LASTEXITCODE)" }

$restoreArgs = @("pg_restore", "-U", $pgUser, "-d", $Database, "--no-owner", "--no-privileges")
if ($Clean) { $restoreArgs += @("--clean", "--if-exists") }
$restoreArgs += $containerTmp
docker exec $PostgresContainer @restoreArgs
if ($LASTEXITCODE -ne 0) {
    docker exec $PostgresContainer rm -f $containerTmp | Out-Null
    throw "pg_restore failed (exit $LASTEXITCODE). Dump formatı -Fc olmalı."
}

docker exec $PostgresContainer rm -f $containerTmp | Out-Null
Write-Host "Restore completed."
