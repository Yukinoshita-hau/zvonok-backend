param([int]$JwtMilliseconds=15000,[switch]$Resume,[string]$JavaHome='C:\Users\Пользователь\.jdks\semeru-21.0.9')
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$statePath='stage10-1-evidence/runtime.env'
if (!(Test-Path "$JavaHome/bin/java.exe")) { throw 'JDK21 required' }
$label=docker inspect --format '{{index .Config.Labels "zvonok.acceptance"}}' zvonok-stage10-pg
if ($LASTEXITCODE -or $label -ne 'stage10') { throw 'Refusing unknown PostgreSQL container' }
docker start zvonok-stage10-pg | Out-Null
if($LASTEXITCODE){throw 'Test PostgreSQL unavailable'}
if (!$Resume) {
 if(Test-Path $statePath){throw 'Stage10.1 state already exists; inspect and use Resume'}
 $pgConfig=docker inspect --format '{{json .Config.Env}}' zvonok-stage10-pg | ConvertFrom-Json
 $pgPassword=($pgConfig | Where-Object { $_.StartsWith('POSTGRES_PASSWORD=') }).Substring(18)
 $state=@{dbPassword=$pgPassword;jwtSecret=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64));s3User=('s101'+[guid]::NewGuid().ToString('N').Substring(0,12));s3Password=[guid]::NewGuid().ToString('N')}
 $state | ConvertTo-Json | Set-Content $statePath
 docker exec zvonok-stage10-pg createdb -U stage10 zvonok_stage10_1
 if($LASTEXITCODE){throw 'Dedicated fresh DB creation failed'}
} else { $state=Get-Content $statePath -Raw | ConvertFrom-Json }
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:15432/zvonok_stage10_1'
$env:SPRING_DATASOURCE_USERNAME='stage10'
$env:SPRING_DATASOURCE_PASSWORD=$state.dbPassword
$env:APP_JWT_SECRET=$state.jwtSecret
$env:APP_JWT_EXPIRATION_MS="$JwtMilliseconds"
$env:LIVEKIT_SERVER_URL='ws://127.0.0.1:17880'
$env:LIVEKIT_API_URL='http://127.0.0.1:17880'
$env:LIVEKIT_API_KEY='devkey'
$env:LIVEKIT_API_SECRET='secret'
$env:S3_ENDPOINT='http://127.0.0.1:19000'
$env:S3_LOCAL_ENDPOINT='http://127.0.0.1:18080/api/s3/download'
$env:S3_ACCESS_KEY=$state.s3User
$env:S3_SECRET_ACCESS_KEY=$state.s3Password
$env:SECURITY_ALLOWED_ORIGINS='http://127.0.0.1:13000'
$env:APP_PUBLIC_URL='http://127.0.0.1:13000'
$env:LOGGING_LEVEL_APP='INFO'
$env:LOGGING_LEVEL_SECURITY='WARN'
$env:JAVA_LSP_ENABLED='false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO=if($Resume){'validate'}else{'create'}
& "$JavaHome/bin/java.exe" -jar target/zvonok-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080 --logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR
exit $LASTEXITCODE
