param([ValidateSet('rustfs','minio')][string]$Provider='rustfs',[string]$BackupRoot=(Join-Path $env:LOCALAPPDATA 'Zvonok\backups'))
$ErrorActionPreference='Stop'
$port=if($Provider -eq 'rustfs'){19000}else{19010}
$path=if($Provider -eq 'rustfs'){'health/ready'}else{'minio/health/ready'}
$free=(Get-PSDrive C).Free
$available=$false
try { $available=(Invoke-WebRequest "http://127.0.0.1:$port/$path" -TimeoutSec 5).StatusCode -eq 200 } catch { }
$backup=Get-ChildItem -LiteralPath $BackupRoot -Filter manifest.json -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$backupStatus='No verified coordinated backup; off-host backup NOT CONFIGURED'
if($backup){$manifest=Get-Content $backup.FullName -Raw | ConvertFrom-Json;if($manifest.referencesMatch -eq $true -and $manifest.status -eq 'PASS'){$backupStatus='Coordinated diagnostic backup verified; production off-host backup NOT CONFIGURED'}}
$report=@{provider=$Provider;storageReady=$available;hostFreeBytes=$free;minimumFreeBytes=8GB;diskStatus=if($free -ge 8GB){'PASS'}else{'FAIL'};backupAgeHours=if($backup){[math]::Round(((Get-Date)-$backup.LastWriteTime).TotalHours,2)}else{$null};backupStatus=$backupStatus;disasterRecovery='BLOCKED - independent destination not confirmed'}
$report | ConvertTo-Json
if(!$available -or $free -lt 8GB){exit 1}
