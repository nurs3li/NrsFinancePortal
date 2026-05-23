<#
.SYNOPSIS
  nrs_market (market-data EVDS geçmişi) veritabanını Docker Postgres içinde pg_dump ile yedekler.

.EXAMPLE
  .\scripts\ops\Backup-NrsMarketDb.ps1
#>
param(
    [string]$PostgresContainer = "nrs-postgres",
    [string]$Database = "nrs_market",
    [string]$OutDir = "backups"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$outPath = Join-Path $root $OutDir
New-Item -ItemType Directory -Force -Path $outPath | Out-Null

$pgUser = $env:POSTGRES_USER
if ([string]::IsNullOrWhiteSpace($pgUser)) { $pgUser = "nrs" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dumpName = "nrs_market-$stamp.dump"
$containerTmp = "/tmp/$dumpName"
$localFile = Join-Path $outPath $dumpName

Write-Host "Backing up $Database on $PostgresContainer (user=$pgUser) -> $localFile"

docker exec $PostgresContainer pg_dump -U $pgUser -d $Database -Fc -f $containerTmp
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)" }

docker cp "${PostgresContainer}:${containerTmp}" $localFile
if ($LASTEXITCODE -ne 0) { throw "docker cp failed (exit $LASTEXITCODE)" }

docker exec $PostgresContainer rm -f $containerTmp | Out-Null
Write-Host "Done. Size: $((Get-Item $localFile).Length) bytes"
