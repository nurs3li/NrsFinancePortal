# Katmanli mimari paket migrasyonu (notification-service / marketdata ile uyumlu)
$ErrorActionPreference = "Stop"
$root = "c:\Users\Nurseli\Desktop\NrsFinancePortal\log-consumer-service"
$mainBase = Join-Path $root "src\main\java\com\nurseli\logconsumer"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

function Move-PackageDir {
    param([string]$FromRel, [string]$ToRel)
    $from = Join-Path $mainBase $FromRel
    $to = Join-Path $mainBase $ToRel
    if (-not (Test-Path $from)) { return }
    $toParent = Split-Path $to -Parent
    if (-not (Test-Path $toParent)) { New-Item -ItemType Directory -Path $toParent -Force | Out-Null }
    if (Test-Path $to) {
        Get-ChildItem $from -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($from.Length + 1)
            $dest = Join-Path $to $rel
            $destDir = Split-Path $dest -Parent
            if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
            Move-Item $_.FullName $dest -Force
        }
        Remove-Item $from -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        Move-Item $from $to -Force
    }
}

function Move-FileTo {
    param([string]$FromRel, [string]$ToRel)
    $from = Join-Path $mainBase $FromRel
    $to = Join-Path $mainBase $ToRel
    if (-not (Test-Path $from)) { return }
    $toDir = Split-Path $to -Parent
    if (-not (Test-Path $toDir)) { New-Item -ItemType Directory -Path $toDir -Force | Out-Null }
    Move-Item $from $to -Force
}

# --- fiziksel tasima ---
Move-PackageDir "consumer" "infrastructure\messaging"
Move-PackageDir "logging" "infrastructure\logging"
Move-PackageDir "event" "application\event"
Move-FileTo "opensearch\ApplicationLogIndexerService.java" "application\ApplicationLogIndexerService.java"
Move-FileTo "opensearch\ApplicationLogsIndexInitializer.java" "infrastructure\opensearch\ApplicationLogsIndexInitializer.java"

$opensearchDir = Join-Path $mainBase "opensearch"
if ((Test-Path $opensearchDir) -and ((Get-ChildItem $opensearchDir -Recurse -Force | Measure-Object).Count -eq 0)) {
    Remove-Item $opensearchDir -Recurse -Force -ErrorAction SilentlyContinue
}

@("consumer", "logging", "event", "opensearch") | ForEach-Object {
    $p = Join-Path $mainBase $_
    if ((Test-Path $p) -and ((Get-ChildItem $p -Recurse -Force | Measure-Object).Count -eq 0)) {
        Remove-Item $p -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# --- paket/import guncellemeleri ---
$replacements = @(
    @("package com.nurseli.logconsumer.consumer;", "package com.nurseli.logconsumer.infrastructure.messaging;"),
    @("package com.nurseli.logconsumer.logging;", "package com.nurseli.logconsumer.infrastructure.logging;"),
    @("package com.nurseli.logconsumer.event;", "package com.nurseli.logconsumer.application.event;"),
    @("package com.nurseli.logconsumer.opensearch;", "package com.nurseli.logconsumer.infrastructure.opensearch;"),
    @("import com.nurseli.logconsumer.consumer.", "import com.nurseli.logconsumer.infrastructure.messaging."),
    @("import com.nurseli.logconsumer.logging.", "import com.nurseli.logconsumer.infrastructure.logging."),
    @("import com.nurseli.logconsumer.event.", "import com.nurseli.logconsumer.application.event."),
    @("import com.nurseli.logconsumer.opensearch.", "import com.nurseli.logconsumer.application."),
    @("com.nurseli.logconsumer.event", "com.nurseli.logconsumer.application.event"),
    @('packages="com.nurseli.logconsumer.logging"', 'packages="com.nurseli.logconsumer.infrastructure.logging"')
)

# ApplicationLogIndexerService -> application katmani
$indexerPath = Join-Path $mainBase "application\ApplicationLogIndexerService.java"
$initializerPath = Join-Path $mainBase "infrastructure\opensearch\ApplicationLogsIndexInitializer.java"

Get-ChildItem $root -Include *.java,*.xml,*.yml,*.yaml -Recurse -File |
    Where-Object { $_.FullName -notmatch "\\target\\" } |
    ForEach-Object {
        $content = [System.IO.File]::ReadAllText($_.FullName)
        $original = $content
        foreach ($pair in $replacements) {
            $content = $content.Replace($pair[0], $pair[1])
        }
        if ($content -ne $original) {
            if ($content.Length -gt 0 -and [int][char]$content[0] -eq 0xFEFF) {
                $content = $content.Substring(1)
            }
            [System.IO.File]::WriteAllText($_.FullName, $content, $utf8NoBom)
        }
    }

if (Test-Path $indexerPath) {
    $content = [System.IO.File]::ReadAllText($indexerPath)
    $content = $content.Replace("package com.nurseli.logconsumer.infrastructure.opensearch;", "package com.nurseli.logconsumer.application;")
    $content = $content.Replace("package com.nurseli.logconsumer.opensearch;", "package com.nurseli.logconsumer.application;")
    [System.IO.File]::WriteAllText($indexerPath, $content, $utf8NoBom)
}

if (Test-Path $initializerPath) {
    $content = [System.IO.File]::ReadAllText($initializerPath)
    if ($content -notmatch "import com\.nurseli\.logconsumer\.application\.ApplicationLogIndexerService") {
        $content = $content.Replace(
            "import org.opensearch.client.RestHighLevelClient;",
            "import com.nurseli.logconsumer.application.ApplicationLogIndexerService;`r`nimport org.opensearch.client.RestHighLevelClient;"
        )
    }
    [System.IO.File]::WriteAllText($initializerPath, $content, $utf8NoBom)
}

Write-Host "Migration complete."
