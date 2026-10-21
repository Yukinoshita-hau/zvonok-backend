param([ValidateSet('rustfs','minio')][string]$Provider='rustfs')
$ErrorActionPreference = 'Stop'
if (docker ps -a --filter 'name=^/zvonok-stage5d-backend$' --format '{{.Names}}') { throw 'Inspect existing Stage5D backend first' }
$config = (docker inspect zvonok-backend | ConvertFrom-Json)[0]
$database = if ($Provider -eq 'rustfs') { 'zvonok_stage5d_rustfs_diag' } else { 'zvonok_stage5d_diag' }
$exists = docker exec zvonok-postgres psql -U rashid -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='$database'"
if (!$exists) {
    docker exec zvonok-postgres createdb -U rashid $database
    if ($LASTEXITCODE) { throw 'Cannot create isolated diagnostic database' }
    $schema = docker exec zvonok-postgres pg_dump -U rashid -d zvonok --schema-only --no-owner --no-privileges
    if ($LASTEXITCODE) { throw 'Diagnostic schema export failed' }
    $schema | docker exec -i zvonok-postgres psql -U rashid -d $database -v ON_ERROR_STOP=1 | Out-Null
    if ($LASTEXITCODE) { throw 'Diagnostic schema copy failed' }
}
$argsList = @('run','-d','--name','zvonok-stage5d-backend','--label','zvonok.acceptance=stage5d','--network','zvonok-net','-p','127.0.0.1:18081:8080','--mount','type=bind,source=C:/project/zvonok-backend/target/zvonok-0.0.1-SNAPSHOT.jar,target=/opt/app/app.jar,readonly')
foreach ($entry in $config.Config.Env) {
    $pair = $entry -split '=',2
    if ($pair[0] -in @('PATH','JAVA_HOME','LANG','LANGUAGE','LC_ALL','JAVA_VERSION')) { continue }
    if ($pair[0] -eq 'SPRING_DATASOURCE_URL') { $pair[1] = $pair[1] -replace '/zvonok(?=\?|$)',"/$database" }
    [Environment]::SetEnvironmentVariable($pair[0],$pair[1],'Process')
    $argsList += @('-e',$pair[0])
}
$secretFile = Join-Path $env:LOCALAPPDATA 'Zvonok\local-s3\rustfs.env'
$secrets = @{}
foreach ($line in Get-Content -LiteralPath $secretFile) { $pair=$line -split '=',2; $secrets[$pair[0]]=$pair[1] }
$overrides = @{
    S3_PROVIDER='rustfs'; S3_ENDPOINT='http://zvonok-local-s3:9000'; S3_SIGNING_ENDPOINT='http://127.0.0.1:19000'
    S3_BUCKET='zvonok-stage5d-data'; S3_REGION='us-east-1'; S3_ACCESS_KEY=$secrets.RUSTFS_ACCESS_KEY
    S3_SECRET_ACCESS_KEY=$secrets.RUSTFS_SECRET_KEY; S3_ALLOW_LOOPBACK_HTTP='true'
    SPRING_JPA_HIBERNATE_DDL_AUTO='validate'; S3_UPLOAD_EXECUTION_TIMEOUT_MS='30000'
    SECURITY_ALLOWED_ORIGINS='http://localhost:3001'; SPRING_JPA_SHOW_SQL='false'
    LOGGING_LEVEL_APP='INFO'; LOGGING_LEVEL_SECURITY='WARN'
}
if ($Provider -eq 'minio') {
    $overrides.S3_PROVIDER='minio'; $overrides.S3_ENDPOINT='http://zvonok-local-minio:9000'
    $overrides.S3_SIGNING_ENDPOINT='http://127.0.0.1:19010'
}
# Overrides follow inherited entries. Only this new container receives them.
foreach ($name in $overrides.Keys) { [Environment]::SetEnvironmentVariable($name,$overrides[$name],'Process'); $argsList+=@('-e',$name) }
$argsList += $config.Config.Image
docker @argsList
if ($LASTEXITCODE) { throw 'Diagnostic backend failed' }
