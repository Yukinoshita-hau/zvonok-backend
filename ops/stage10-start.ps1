param([string]$JavaHome = 'C:\Users\Пользователь\.jdks\semeru-21.0.9')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path "$JavaHome/bin/java.exe")) { throw 'JDK 21 required' }
if (!(Test-Path 'target/zvonok-0.0.1-SNAPSHOT.jar')) { throw 'Run Maven package first' }
# Fixed isolated names, no user volumes, no access to host PostgreSQL :5432.
foreach ($name in @('zvonok-stage10-pg','zvonok-stage10-livekit')) {
    $existing = docker ps -a --filter "name=^/$name`$" --format '{{.Names}}'
    if ($existing) { throw "Container already exists: $name; inspect before reuse" }
}
$password = [guid]::NewGuid().ToString('N')
$env:POSTGRES_PASSWORD = $password
docker run -d --name zvonok-stage10-pg --label zvonok.acceptance=stage10 -e POSTGRES_PASSWORD -e POSTGRES_USER=stage10 -e POSTGRES_DB=zvonok_stage10 -p 127.0.0.1:15432:5432 postgres:16-alpine | Out-Null
if ($LASTEXITCODE) { throw 'PostgreSQL startup failed' }
docker run -d --name zvonok-stage10-livekit --label zvonok.acceptance=stage10 -p 127.0.0.1:17880:7880 -p 127.0.0.1:7881:7881 -p 127.0.0.1:7882:7882/udp livekit/livekit-server:v1.9.0 --dev --bind 0.0.0.0 --node-ip 127.0.0.1 | Out-Null
if ($LASTEXITCODE) { throw 'LiveKit startup failed' }
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:15432/zvonok_stage10'
$env:SPRING_DATASOURCE_USERNAME='stage10'
$env:SPRING_DATASOURCE_PASSWORD=$password
$env:APP_JWT_SECRET=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
$env:LIVEKIT_SERVER_URL='ws://127.0.0.1:17880'
$env:LIVEKIT_API_URL='http://127.0.0.1:17880'
$env:LIVEKIT_API_KEY='devkey'
$env:LIVEKIT_API_SECRET='secret'
# Storage is explicitly unavailable; never fall through to configured credentials.
$env:S3_ENDPOINT='http://127.0.0.1:19000'
$env:S3_LOCAL_ENDPOINT='http://127.0.0.1:18080/api/s3/download'
$env:S3_ACCESS_KEY='stage10-unavailable'
$env:S3_SECRET_ACCESS_KEY='stage10-unavailable'
$env:SECURITY_ALLOWED_ORIGINS='http://127.0.0.1:13000'
$env:APP_PUBLIC_URL='http://127.0.0.1:13000'
$env:LOGGING_LEVEL_APP='INFO'
$env:JAVA_LSP_ENABLED='false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO='create'
# Explicitly fresh disposable DB only. After boot run ops/stage10-schema.ps1.
# A later restart of a preserved DB must use explicit test credentials and validate.
& "$JavaHome/bin/java.exe" -jar target/zvonok-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
exit $LASTEXITCODE
