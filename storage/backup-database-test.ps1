param([string]$DestinationRoot=(Join-Path $env:LOCALAPPDATA 'Zvonok\backups'))
$ErrorActionPreference = 'Stop'
$root=Get-Item -LiteralPath $DestinationRoot
if(!$root.PSIsContainer -or $root.PSProvider.Name -ne 'FileSystem'){throw 'DestinationRoot must be an existing filesystem directory'}
if($null -eq $root.PSDrive.Free -or $root.PSDrive.Free -lt 2GB){throw 'Destination free space unavailable or below 2 GiB'}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $root.FullName "stage5d-$stamp"
if ((Get-PSDrive C).Free -lt 8GB) { throw 'Insufficient host free space' }
New-Item -ItemType Directory $backupDirectory | Out-Null
icacls $backupDirectory /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(OI)(CI)F" | Out-Null
if ($LASTEXITCODE) { throw 'Cannot restrict backup permissions' }
$dump = Join-Path $backupDirectory 'postgres.dump'
$containerDump = "/tmp/stage5d-$stamp.dump"
$restoreDatabase = 'zvonok_stage5d_restore_' + ($stamp -replace '-','_')
try {
    docker exec zvonok-postgres pg_dump -U rashid -d zvonok -Fc -f $containerDump
    if ($LASTEXITCODE) { throw 'pg_dump failed' }
    docker cp "zvonok-postgres:$containerDump" $dump | Out-Null
    if ($LASTEXITCODE) { throw 'Backup copy failed' }
    docker exec zvonok-postgres createdb -U rashid $restoreDatabase
    if ($LASTEXITCODE) { throw 'Isolated restore database creation failed' }
    docker exec zvonok-postgres pg_restore -U rashid --no-owner --no-privileges --exit-on-error -d $restoreDatabase $containerDump
    if ($LASTEXITCODE) { throw 'Restore failed' }
    $rows = docker exec zvonok-postgres psql -U rashid -d $restoreDatabase -Atc 'SELECT count(*) FROM message_attachment'
    if ($LASTEXITCODE) { throw 'Restore verification failed' }
    $report = @{status='PASS';sha256=(Get-FileHash -Algorithm SHA256 $dump).Hash;bytes=(Get-Item $dump).Length;restoredAttachmentRows=[int]$rows;restoreDatabase=$restoreDatabase;objects='NOT INCLUDED - database restore only';durability='same physical disk - diagnostic backup only'}
    $report | ConvertTo-Json | Set-Content (Join-Path $backupDirectory 'manifest.json')
    New-Item -ItemType Directory -Force "$PSScriptRoot\evidence" | Out-Null
    $report | ConvertTo-Json | Set-Content "$PSScriptRoot\evidence\database-restore.json"
    $report | ConvertTo-Json
} finally {
    docker exec zvonok-postgres rm -f $containerDump
}
