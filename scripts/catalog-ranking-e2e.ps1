[CmdletBinding()]
param(
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$infraCompose = Join-Path $projectRoot "docker\infra-compose.yml"
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$buildDirectory = Join-Path $projectRoot "build"
$databaseName = "loopers_round9_e2e"
$redisDatabase = 15
$productId = 990000001L
$brandId = 990000001L
$apiPort = 18080
$apiManagementPort = 18082
$streamerPort = 18081
$streamerManagementPort = 18083
$runId = Get-Date -Format "yyyyMMddHHmmss"
$topic = "catalog-round9-e2e-$runId"
$metricsGroup = "catalog-metrics-round9-e2e-$runId"
$rankingGroup = "catalog-ranking-round9-e2e-$runId"
$apiLog = Join-Path $buildDirectory "catalog-ranking-e2e-api-$runId.log"
$apiErrorLog = Join-Path $buildDirectory "catalog-ranking-e2e-api-$runId.err.log"
$streamerLog = Join-Path $buildDirectory "catalog-ranking-e2e-streamer-$runId.log"
$streamerErrorLog = Join-Path $buildDirectory "catalog-ranking-e2e-streamer-$runId.err.log"
$apiProcess = $null
$streamerProcess = $null

function Invoke-Docker {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
    return $output
}

function Invoke-MySqlScalar {
    param(
        [Parameter(Mandatory)]
        [string]$Sql
    )

    $output = Invoke-Docker -Arguments @(
        "compose", "-f", $infraCompose, "exec", "-T", "mysql",
        "mysql", "-N", "-s", "-uapplication", "-papplication", $databaseName, "-e", $Sql
    )
    if ($null -eq $output) {
        return ""
    }
    return ([string]($output | Select-Object -Last 1)).Trim()
}

function Get-RedisValue {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $command = @(
        "exec", "redis-master", "redis-cli", "-n", "$redisDatabase", "--raw"
    ) + $Arguments
    $output = Invoke-Docker -Arguments $command
    if ($null -eq $output) {
        return ""
    }
    return ([string]($output | Select-Object -Last 1)).Trim()
}

function Wait-Until {
    param(
        [Parameter(Mandatory)]
        [string]$Description,

        [Parameter(Mandatory)]
        [scriptblock]$Condition,

        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            if (& $Condition) {
                return
            }
        } catch {
            # The dependency may still be starting. Retry until the deadline.
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description."
}

function Wait-Application {
    param(
        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [int]$Port,

        [Parameter(Mandatory)]
        [System.Diagnostics.Process]$Process,

        [Parameter(Mandatory)]
        [string]$LogPath
    )

    $deadline = (Get-Date).AddSeconds(120)
    do {
        $Process.Refresh()
        if ($Process.HasExited) {
            break
        }

        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $connect = $client.ConnectAsync("127.0.0.1", $Port)
            if ($connect.Wait(1000) -and $client.Connected) {
                return
            }
        } catch {
            # The application server may not be listening yet.
        } finally {
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    $tail = if (Test-Path $LogPath) {
        Get-Content -Encoding UTF8 $LogPath -Tail 40 | Out-String
    } else {
        "No log was created."
    }
    throw "$Name did not become healthy.`n$tail"
}

function Start-GradleApplication {
    param(
        [Parameter(Mandatory)]
        [string]$Task,

        [Parameter(Mandatory)]
        [string[]]$Properties,

        [Parameter(Mandatory)]
        [string]$OutputLog,

        [Parameter(Mandatory)]
        [string]$ErrorLog
    )

    $applicationArguments = $Properties -join " "
    return Start-Process `
        -FilePath $gradleWrapper `
        -ArgumentList @("--no-daemon", $Task, "--args=`"$applicationArguments`"") `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $OutputLog `
        -RedirectStandardError $ErrorLog `
        -WindowStyle Hidden `
        -PassThru
}

function Stop-ProcessTree {
    param(
        [System.Diagnostics.Process]$Process
    )

    if ($null -eq $Process) {
        return
    }

    $Process.Refresh()
    if ($Process.HasExited) {
        return
    }

    & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
}

function Get-ConsumerGroupRows {
    param(
        [Parameter(Mandatory)]
        [string]$GroupId
    )

    $output = & docker exec kafka kafka-consumer-groups.sh `
        --bootstrap-server localhost:9092 `
        --describe `
        --group $GroupId 2>$null
    if ($LASTEXITCODE -ne 0) {
        return @()
    }

    return @($output | Where-Object {
        $_ -match "^\s*$([regex]::Escape($GroupId))\s+$([regex]::Escape($topic))\s+"
    })
}

function Get-ConsumerGroupLag {
    param(
        [Parameter(Mandatory)]
        [string]$GroupId
    )

    $rows = Get-ConsumerGroupRows -GroupId $GroupId
    if ($rows.Count -ne 3) {
        return $null
    }

    $lag = 0L
    foreach ($row in $rows) {
        $columns = $row.Trim() -split "\s+"
        $lag += [long]$columns[5]
    }
    return $lag
}

function Get-FlowState {
    $dailyKey = "ranking:all:{$rankingDate}"
    $handledKey = "ranking:handled:{$rankingDate}"
    $hourlyKeys = @(
        Invoke-Docker -Arguments @(
            "exec", "redis-master", "redis-cli", "-n", "$redisDatabase", "--raw",
            "--scan", "--pattern", "ranking:hourly:{$rankingDate}:*"
        )
    )
    $hourlyKey = $hourlyKeys | Select-Object -First 1
    $hourlyScore = if ($null -eq $hourlyKey -or [string]::IsNullOrWhiteSpace($hourlyKey)) {
        ""
    } else {
        Get-RedisValue -Arguments @("ZSCORE", $hourlyKey, "$productId")
    }

    return [ordered]@{
        outboxPublished = [int](Invoke-MySqlScalar -Sql "select count(*) from catalog_event_outbox where aggregate_id = $productId and status = 'PUBLISHED'")
        metricViews = [long](Invoke-MySqlScalar -Sql "select coalesce(sum(view_count), 0) from product_metrics where metric_date = str_to_date('$rankingDate', '%Y%m%d') and product_id = $productId")
        hourlyViews = [long](Invoke-MySqlScalar -Sql "select coalesce(sum(view_count), 0) from product_metric_hourly where product_id = $productId")
        databaseHandled = [int](Invoke-MySqlScalar -Sql "select count(*) from event_handled where aggregate_id = $productId")
        dailyScore = Get-RedisValue -Arguments @("ZSCORE", $dailyKey, "$productId")
        hourlyScore = $hourlyScore
        redisHandled = [int](Get-RedisValue -Arguments @("SCARD", $handledKey))
    }
}

function Test-FlowState {
    param(
        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$State,

        [Parameter(Mandatory)]
        [int]$ExpectedCount
    )

    $expectedScore = $ExpectedCount * 0.1
    $dailyScore = if ([string]::IsNullOrWhiteSpace([string]$State.dailyScore)) {
        [double]::NaN
    } else {
        [double]$State.dailyScore
    }
    $hourlyScore = if ([string]::IsNullOrWhiteSpace([string]$State.hourlyScore)) {
        [double]::NaN
    } else {
        [double]$State.hourlyScore
    }

    return $State.outboxPublished -eq $ExpectedCount `
        -and $State.metricViews -eq $ExpectedCount `
        -and $State.hourlyViews -eq $ExpectedCount `
        -and $State.databaseHandled -eq $ExpectedCount `
        -and [Math]::Abs($dailyScore - $expectedScore) -lt 0.000000001 `
        -and [Math]::Abs($hourlyScore - $expectedScore) -lt 0.000000001 `
        -and $State.redisHandled -eq $ExpectedCount
}

New-Item -ItemType Directory -Force -Path $buildDirectory | Out-Null

try {
    Write-Host "[1/8] Starting isolated infrastructure dependencies..."
    Invoke-Docker -Arguments @(
        "compose", "-f", $infraCompose, "up", "-d", "mysql", "redis-master", "redis-readonly", "kafka"
    ) | Out-Null

    Write-Host "[2/8] Resetting the dedicated MySQL database and Redis DB $redisDatabase..."
    Invoke-Docker -Arguments @(
        "compose", "-f", $infraCompose, "exec", "-T", "mysql", "mysql", "-uroot", "-proot", "-e",
        "drop database if exists $databaseName; create database $databaseName character set utf8mb4 collate utf8mb4_general_ci; grant all privileges on $databaseName.* to 'application'@'%'; flush privileges;"
    ) | Out-Null
    Invoke-Docker -Arguments @(
        "exec", "redis-master", "redis-cli", "-n", "$redisDatabase", "FLUSHDB"
    ) | Out-Null

    Write-Host "[3/8] Starting Commerce API on port $apiPort..."
    $apiProcess = Start-GradleApplication `
        -Task ":apps:commerce-api:bootRun" `
        -Properties @(
            "--server.port=$apiPort",
            "--management.server.port=$apiManagementPort",
            "--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/$databaseName",
            "--spring.jpa.hibernate.ddl-auto=create",
            "--spring.jpa.show-sql=false",
            "--datasource.redis.database=$redisDatabase",
            "--commerce.catalog-event-outbox.topic-name=$topic",
            "--commerce.catalog-event-outbox.relay-delay-ms=200",
            "--commerce.product-like-summary.sync-enabled=false",
            "--commerce.coupon-issue.auto-startup=false",
            "--commerce.queue.admit-enabled=false",
            "--commerce.queue.order-gate-enabled=false",
            "--commerce.queue.order-bulkhead-enabled=false",
            "--commerce.payment.recovery.enabled=false"
        ) `
        -OutputLog $apiLog `
        -ErrorLog $apiErrorLog
    Wait-Application `
        -Name "Commerce API" `
        -Port $apiPort `
        -Process $apiProcess `
        -LogPath $apiLog

    $seedSql = "insert into brands (id, name, description, created_at, updated_at, deleted_at) values ($brandId, 'E2E Test Brand', 'Round 9 end-to-end test brand', now(6), now(6), null); insert into product (id, brand_id, name, description, price, created_at, updated_at, deleted_at) values ($productId, $brandId, 'E2E Test Product', 'Round 9 end-to-end test product', 25000, now(6), now(6), null); insert into product_like_summary (product_id, brand_id, like_count) values ($productId, $brandId, 0);"
    Invoke-MySqlScalar -Sql $seedSql | Out-Null

    Write-Host "[4/8] Starting Commerce Streamer with both Metrics and Ranking consumers..."
    $streamerProcess = Start-GradleApplication `
        -Task ":apps:commerce-streamer:bootRun" `
        -Properties @(
            "--server.port=$streamerPort",
            "--management.server.port=$streamerManagementPort",
            "--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/$databaseName",
            "--spring.jpa.hibernate.ddl-auto=update",
            "--spring.jpa.show-sql=false",
            "--datasource.redis.database=$redisDatabase",
            "--commerce.metrics.catalog.topic-name=$topic",
            "--commerce.metrics.catalog.group-id=$metricsGroup",
            "--commerce.ranking.catalog.group-id=$rankingGroup",
            "--commerce.ranking.catalog.auto-startup=true"
        ) `
        -OutputLog $streamerLog `
        -ErrorLog $streamerErrorLog
    Wait-Application `
        -Name "Commerce Streamer" `
        -Port $streamerPort `
        -Process $streamerProcess `
        -LogPath $streamerLog

    Wait-Until -Description "Metrics consumer assignment" -TimeoutSeconds 60 -Condition {
        (Get-ConsumerGroupRows -GroupId $metricsGroup).Count -eq 3
    }
    Wait-Until -Description "Ranking consumer assignment" -TimeoutSeconds 60 -Condition {
        (Get-ConsumerGroupRows -GroupId $rankingGroup).Count -eq 3
    }

    $rankingDate = Get-Date -Format "yyyyMMdd"
    $productEndpoint = "http://localhost:$apiPort/api/v1/products/$productId"
    $rankingEndpoint = "http://localhost:$apiPort/api/v1/rankings?date=$rankingDate&page=0&size=20"

    Write-Host "[5/8] Requesting the product detail and waiting for the first event..."
    $firstDetail = Invoke-RestMethod -Method Get -Uri $productEndpoint
    $firstRankProperty = $firstDetail.data.PSObject.Properties["rank"]
    $firstRank = if ($null -eq $firstRankProperty) { $null } else { $firstRankProperty.Value }
    if ($firstDetail.data.id -ne $productId -or $null -ne $firstRank) {
        throw "The first product detail response did not contain the expected product with a null rank."
    }

    Wait-Until -Description "first event propagation" -TimeoutSeconds 60 -Condition {
        $state = Get-FlowState
        Test-FlowState -State $state -ExpectedCount 1
    }

    Write-Host "[6/8] Verifying the Ranking API and product detail rank..."
    $rankingResponse = $null
    Wait-Until -Description "Ranking API response" -TimeoutSeconds 30 -Condition {
        $script:rankingResponse = Invoke-RestMethod -Method Get -Uri $rankingEndpoint
        return $script:rankingResponse.data.content.Count -eq 1 `
            -and $script:rankingResponse.data.content[0].rank -eq 1 `
            -and $script:rankingResponse.data.content[0].product.id -eq $productId
    }

    $rankedDetail = Invoke-RestMethod -Method Get -Uri $productEndpoint
    if ($rankedDetail.data.rank -ne 1) {
        throw "The product detail response did not expose rank 1."
    }

    Write-Host "[7/8] Waiting for the second view event and zero consumer lag..."
    $finalState = $null
    Wait-Until -Description "second event propagation" -TimeoutSeconds 60 -Condition {
        $script:finalState = Get-FlowState
        Test-FlowState -State $script:finalState -ExpectedCount 2
    }
    Wait-Until -Description "zero lag for both consumer groups" -TimeoutSeconds 30 -Condition {
        (Get-ConsumerGroupLag -GroupId $metricsGroup) -eq 0 `
            -and (Get-ConsumerGroupLag -GroupId $rankingGroup) -eq 0
    }

    Write-Host "[8/8] Round 9 catalog-to-ranking E2E passed."
    [ordered]@{
        topic = $topic
        productId = $productId
        productName = $rankedDetail.data.name
        rank = $rankedDetail.data.rank
        outboxPublished = $finalState.outboxPublished
        metricViews = $finalState.metricViews
        hourlyViews = $finalState.hourlyViews
        databaseHandled = $finalState.databaseHandled
        dailyScore = [double]$finalState.dailyScore
        hourlyScore = [double]$finalState.hourlyScore
        redisHandled = $finalState.redisHandled
        metricsConsumerLag = Get-ConsumerGroupLag -GroupId $metricsGroup
        rankingConsumerLag = Get-ConsumerGroupLag -GroupId $rankingGroup
        apiLog = $apiLog
        streamerLog = $streamerLog
    } | ConvertTo-Json
} finally {
    if ($KeepRunning) {
        Write-Host "Applications remain available on ports $apiPort and $streamerPort."
    } else {
        Stop-ProcessTree -Process $streamerProcess
        Stop-ProcessTree -Process $apiProcess
    }
}
