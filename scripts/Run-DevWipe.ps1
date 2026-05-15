<#
.SYNOPSIS
  Clears dev user activity: finance DB portfolios/trades/notifications + metrics OpenSearch indices.
  Does NOT modify market-data DB (historical prices).

.PARAMETER SkipPostgres
  Skip SQL wipe (only OpenSearch metrics).

.PARAMETER SkipOpenSearch
  Skip OpenSearch metrics wipe (only Postgres).

.PARAMETER PostgresContainer
  Default: nrs-postgres

.PARAMETER OpenSearchUrl
  Default: http://localhost:9200 (host port from docker-compose)
#>
param(
    [switch]$SkipPostgres,
    [switch]$SkipOpenSearch,
    [string]$PostgresContainer = "nrs-postgres",
    [string]$OpenSearchUrl = "http://localhost:9200"
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
}

if (-not $SkipOpenSearch) {
    $indexes = @(
        "metrics-trades",
        "metrics-whales",
        "metrics-suspicious",
        "investor-behavior-events"
    )
    $body = '{"query":{"match_all":{}}}'
    foreach ($idx in $indexes) {
        $uri = "$OpenSearchUrl/$idx/_delete_by_query?conflicts=proceed"
        Write-Host "OpenSearch delete_by_query: $idx"
        try {
            Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body $body | Out-Null
        } catch {
            $code = $_.Exception.Response.StatusCode.value__
            if ($code -eq 404) {
                Write-Host "  (index missing, skipped)"
            } else {
                throw
            }
        }
    }
    Write-Host "OpenSearch metrics wipe completed."
}

Write-Host "Done. Market-data / price history was not touched."
