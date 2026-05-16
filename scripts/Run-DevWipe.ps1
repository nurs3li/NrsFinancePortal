<#
.SYNOPSIS
  Clears dev user activity in the finance Postgres database (portfolios, notifications, etc.).
  Does NOT modify market-data DB (historical prices).

.PARAMETER SkipPostgres
  When set, the script performs no database changes (no-op).

.PARAMETER PostgresContainer
  Default: nrs-postgres

# NOTE: OpenSearch metrics indices were removed; use OpenSearch UI/API directly if you need to delete legacy indices.
#>
param(
    [switch]$SkipPostgres,
    [string]$PostgresContainer = "nrs-postgres"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $root "scripts\dev-wipe-user-activity.sql"

if (-not $SkipPostgres) {
    $pgUser = $env:POSTGRES_USER
    if ([string]::IsNullOrWhiteSpace($pgUser)) {
        Write-Host "POSTGRES_USER not set; defaulting to 'postgres'."
        $pgUser = "postgres"
    }
    $pgDb = $env:POSTGRES_DB
    if ([string]::IsNullOrWhiteSpace($pgDb)) {
        Write-Host "POSTGRES_DB not set; defaulting to 'nrs_finance'."
        $pgDb = "nrs_finance"
    }
    if (-not (Test-Path $sqlPath)) {
        throw "Missing SQL file: $sqlPath"
    }
    Write-Host "Wiping Postgres database '$pgDb' on container '$PostgresContainer'..."
    Get-Content -Raw $sqlPath | docker exec -i $PostgresContainer psql -U $pgUser -d $pgDb -v ON_ERROR_STOP=1
    Write-Host "Postgres wipe completed."
} else {
    Write-Host "SkipPostgres: no database wipe."
}

Write-Host "Done. Market-data / price history was not touched."
