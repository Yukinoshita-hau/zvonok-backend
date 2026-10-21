$ErrorActionPreference = 'Stop'
$bindDirectory = Join-Path $PSScriptRoot 'diagnostic-disk'
New-Item -ItemType Directory -Force $bindDirectory | Out-Null
$results = @()
foreach ($kind in @('volume','bind')) {
    $mount = if ($kind -eq 'volume') { 'type=volume,source=zvonok-stage5d-io,target=/bench' } else { "type=bind,source=$bindDirectory,target=/bench" }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $output = docker run --rm --network none --mount $mount --entrypoint sh postgres:16-alpine -c 'dd if=/dev/zero of=/bench/stage5d.bin bs=1M count=32 conv=fsync' 2>&1
    if ($LASTEXITCODE) { throw "Disk benchmark failed: $kind" }
    $watch.Stop()
    $results += @{kind=$kind;bytes=33554432;containerStartAndWriteMs=$watch.ElapsedMilliseconds;dd=($output -join "`n");note='Single sequential zero-write fsync probe, warm cache; not an S3 benchmark'}
    docker run --rm --network none --mount $mount --entrypoint rm postgres:16-alpine -f /bench/stage5d.bin
    if ($LASTEXITCODE) { throw 'Cannot clean diagnostic IO file' }
}
New-Item -ItemType Directory -Force "$PSScriptRoot\evidence" | Out-Null
$results | ConvertTo-Json | Set-Content "$PSScriptRoot\evidence\disk-probe.json"
$results | ConvertTo-Json
