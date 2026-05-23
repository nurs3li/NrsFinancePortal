# Katmanli mimari paket migrasyonu (notification-service ile uyumlu)
$ErrorActionPreference = "Stop"
$root = "c:\Users\Nurseli\Desktop\NrsFinancePortal\marketdata"
$mainBase = Join-Path $root "src\main\java\com\nurseli\marketdata"
$testBase = Join-Path $root "src\test\java\com\nurseli\marketdata"
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

function Move-TestPackageDir {
    param([string]$FromRel, [string]$ToRel)
    $from = Join-Path $testBase $FromRel
    $to = Join-Path $testBase $ToRel
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

# --- fiziksel tasima ---
Move-PackageDir "controller" "api"
Move-PackageDir "repository" "infrastructure\persistence"
Move-PackageDir "logging" "infrastructure\logging"
Move-PackageDir "scheduler" "application\scheduler"
Move-PackageDir "bootstrap" "application\bootstrap"
Move-PackageDir "realtime" "api\realtime"
Move-PackageDir "viop\domain" "domain\viop"

# runner -> application/bootstrap
$runnerFrom = Join-Path $mainBase "runner"
$runnerTo = Join-Path $mainBase "application\bootstrap"
if (Test-Path $runnerFrom) {
    if (-not (Test-Path $runnerTo)) { New-Item -ItemType Directory -Path $runnerTo -Force | Out-Null }
    Get-ChildItem $runnerFrom -File -Filter *.java | ForEach-Object {
        Move-Item $_.FullName (Join-Path $runnerTo $_.Name) -Force
    }
    Remove-Item $runnerFrom -Recurse -Force -ErrorAction SilentlyContinue
}

Move-TestPackageDir "controller" "api"
Move-TestPackageDir "bootstrap" "application\bootstrap"

# --- paket/import guncellemeleri ---
$replacements = @(
    @("package com.nurseli.marketdata.controller;", "package com.nurseli.marketdata.api;"),
    @("package com.nurseli.marketdata.repository;", "package com.nurseli.marketdata.infrastructure.persistence;"),
    @("package com.nurseli.marketdata.logging;", "package com.nurseli.marketdata.infrastructure.logging;"),
    @("package com.nurseli.marketdata.scheduler;", "package com.nurseli.marketdata.application.scheduler;"),
    @("package com.nurseli.marketdata.bootstrap;", "package com.nurseli.marketdata.application.bootstrap;"),
    @("package com.nurseli.marketdata.runner;", "package com.nurseli.marketdata.application.bootstrap;"),
    @("package com.nurseli.marketdata.realtime;", "package com.nurseli.marketdata.api.realtime;"),
    @("package com.nurseli.marketdata.viop.domain;", "package com.nurseli.marketdata.domain.viop;"),
    @("import com.nurseli.marketdata.controller.", "import com.nurseli.marketdata.api."),
    @("import com.nurseli.marketdata.repository.", "import com.nurseli.marketdata.infrastructure.persistence."),
    @("import com.nurseli.marketdata.logging.", "import com.nurseli.marketdata.infrastructure.logging."),
    @("import com.nurseli.marketdata.scheduler.", "import com.nurseli.marketdata.application.scheduler."),
    @("import com.nurseli.marketdata.bootstrap.", "import com.nurseli.marketdata.application.bootstrap."),
    @("import com.nurseli.marketdata.runner.", "import com.nurseli.marketdata.application.bootstrap."),
    @("import com.nurseli.marketdata.realtime.", "import com.nurseli.marketdata.api.realtime."),
    @("import com.nurseli.marketdata.viop.domain.", "import com.nurseli.marketdata.domain.viop."),
    @('packages="com.nurseli.marketdata.logging"', 'packages="com.nurseli.marketdata.infrastructure.logging"')
)

# bos kalan kok paketleri temizle
@("controller", "repository", "logging", "scheduler", "bootstrap", "realtime", "runner", "viop") | ForEach-Object {
    $p = Join-Path $mainBase $_
    if ((Test-Path $p) -and ((Get-ChildItem $p -Recurse -Force | Measure-Object).Count -eq 0)) {
        Remove-Item $p -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Get-ChildItem $root -Include *.java,*.xml -Recurse -File |
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

Write-Host "Migration complete."
