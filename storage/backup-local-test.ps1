param([string]$DestinationRoot=(Join-Path $env:LOCALAPPDATA 'Zvonok\backups'),
    [string]$DeploymentEnvFile, [ValidateSet('dev','prod')][string]$DeploymentMode='dev')
$ErrorActionPreference='Stop'
if($DeploymentEnvFile){
    $snapshot=Join-Path $PSScriptRoot '../../zvonok-frontend/deployment/snapshot.ps1'
    $destination=Join-Path $DestinationRoot ('deployment-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    & $snapshot -Action backup -EnvFile $DeploymentEnvFile -Mode $DeploymentMode -Destination $destination
    exit
}
$root=Get-Item -LiteralPath $DestinationRoot
if(!$root.PSIsContainer -or $root.PSProvider.Name -ne 'FileSystem'){throw 'DestinationRoot must be an existing filesystem directory'}
if($null -eq $root.PSDrive.Free -or $root.PSDrive.Free -lt 2GB){throw 'Destination free space unavailable or below 2 GiB; use a drive with measurable capacity'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$directory=Join-Path $root.FullName "local-$stamp"
if((Get-PSDrive C).Free -lt 2GB){throw 'Insufficient space for bounded diagnostic backup'}
New-Item -ItemType Directory $directory | Out-Null
icacls $directory /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(OI)(CI)F" | Out-Null
if($LASTEXITCODE){throw 'Cannot restrict backup permissions'}
$database='zvonok_stage5d_rustfs_diag'
$restoreDatabase='zvonok_stage5d_local_restore_'+($stamp -replace '-','_')
$containerDump="/tmp/stage5d-local-$stamp.dump"
docker stop zvonok-stage5d-backend | Out-Null
if($LASTEXITCODE){throw 'Cannot quiesce diagnostic uploads'}
try{
    docker exec zvonok-postgres pg_dump -U rashid -d $database -Fc -f $containerDump
    if($LASTEXITCODE){throw 'Diagnostic pg_dump failed'}
    docker cp "zvonok-postgres:$containerDump" (Join-Path $directory 'postgres.dump') | Out-Null
    if($LASTEXITCODE){throw 'Backup copy failed'}
    $references=docker exec zvonok-postgres psql -U rashid -d $database -Atc "SELECT coalesce(json_agg(x),'[]') FROM (SELECT DISTINCT storage_key AS key,size_bytes AS bytes FROM message_attachment ORDER BY storage_key) x"
    if($LASTEXITCODE){throw 'Reference snapshot failed'}
    [IO.File]::WriteAllText((Join-Path $directory 'references.json'),($references -join "`n"))
    foreach($line in Get-Content (Join-Path $env:LOCALAPPDATA 'Zvonok\local-s3\rustfs.env')){$pair=$line -split '=',2;[Environment]::SetEnvironmentVariable($pair[0],$pair[1],'Process')}
    $env:RUSTFS_ACCEPTANCE='true';$env:RUSTFS_BACKUP_DIRECTORY=$directory;$env:RUSTFS_BACKUP_ROOT=$root.FullName
    $env:JAVA_HOME='C:\Users\Пользователь\.jdks\semeru-21.0.9';$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try{mvn -q '-Dtest=RustFsCompatibilityTest#coordinatedDiagnosticObjectRestore' test *> storage/coordinated-backup.log;if($LASTEXITCODE){throw 'Object restore test failed'}}finally{Pop-Location}
    docker exec zvonok-postgres createdb -U rashid $restoreDatabase
    if($LASTEXITCODE){throw 'Restore database creation failed'}
    docker exec zvonok-postgres pg_restore -U rashid --no-owner --no-privileges --exit-on-error -d $restoreDatabase $containerDump
    if($LASTEXITCODE){throw 'Diagnostic DB restore failed'}
    $restored=docker exec zvonok-postgres psql -U rashid -d $restoreDatabase -Atc "SELECT coalesce(json_agg(x),'[]') FROM (SELECT DISTINCT storage_key AS key,size_bytes AS bytes FROM message_attachment ORDER BY storage_key) x"
    if($LASTEXITCODE){throw 'Restored DB reference query failed'}
    if(($restored -join '') -ne ($references -join '')){throw 'Restored DB reference mismatch'}
    $objects=Get-Content (Join-Path $directory 'objects-manifest.json') -Raw | ConvertFrom-Json
    $report=@{status='PASS';restoreDatabase=$restoreDatabase;restoreBucket=$objects.restoreBucket;objects=$objects.objects.Count;databaseSha256=(Get-FileHash (Join-Path $directory 'postgres.dump') -Algorithm SHA256).Hash;referencesMatch=$true;scope='Quiesced isolated RustFS diagnostic database and referenced objects';durability='BLOCKED - destination physical independence not verified'}
    $report.objectsManifestSha256=(Get-FileHash (Join-Path $directory 'objects-manifest.json') -Algorithm SHA256).Hash
    $report.referencesSha256=(Get-FileHash (Join-Path $directory 'references.json') -Algorithm SHA256).Hash
    $report | ConvertTo-Json | Set-Content (Join-Path $directory 'manifest.json')
    $report | ConvertTo-Json | Set-Content "$PSScriptRoot\evidence\coordinated-restore.json"
    $report | ConvertTo-Json
    Write-Output "Backup directory: $directory"
}finally{
    docker exec zvonok-postgres rm -f $containerDump
    docker start zvonok-stage5d-backend | Out-Null
}
