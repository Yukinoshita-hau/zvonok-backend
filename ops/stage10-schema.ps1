$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$label=docker inspect --format '{{index .Config.Labels "zvonok.acceptance"}}' zvonok-stage10-pg
if ($LASTEXITCODE -or $label -ne 'stage10') { throw 'Expected isolated Stage10 container' }
New-Item -ItemType Directory -Force stage10-evidence | Out-Null
foreach ($pass in 1..2) {
 foreach ($file in @('stage6-call-media-observations.sql','stage6-message-upload-idempotency.sql')) {
  Get-Content "ops/sql/$file" | docker exec -i zvonok-stage10-pg psql -U stage10 -d zvonok_stage10 -v ON_ERROR_STOP=1 >> stage10-evidence/schema.log 2>&1
  if ($LASTEXITCODE) { throw "Stage6 SQL failed: $file pass $pass" }
 }
}
