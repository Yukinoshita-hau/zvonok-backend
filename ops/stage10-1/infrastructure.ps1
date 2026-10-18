param([switch]$Stop)
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$names=@('zvonok-stage10-1-s3','zvonok-stage10-1-livekit')
foreach($name in $names){
 $present=docker ps -a --filter "name=^/$name`$" --format '{{.Names}}'
 if($present){
  $label=docker inspect --format '{{index .Config.Labels "zvonok.acceptance"}}' $name
  if($label -ne 'stage10-1'){throw "Refusing unknown container $name"}
  if($Stop){docker stop $name | Out-Null}else{docker start $name | Out-Null}
  if($LASTEXITCODE){throw "Container operation failed: $name"}
 }elseif($Stop){continue}
}
if($Stop){return}
if(!(Test-Path 'stage10-1-evidence/runtime.env')){throw 'Initialize fresh dedicated database with start.ps1 first. Never substitute production credentials.'}
$state=Get-Content 'stage10-1-evidence/runtime.env' -Raw | ConvertFrom-Json
if(!(docker ps -a --filter 'name=^/zvonok-stage10-1-s3$' --format '{{.Names}}')){
 docker build -f ops/stage10-1/minio.Dockerfile -t zvonok-stage10-1-minio:source-pinned .
 if($LASTEXITCODE){throw 'Pinned upstream MinIO build failed'}
 $env:MINIO_ROOT_USER=$state.s3User;$env:MINIO_ROOT_PASSWORD=$state.s3Password
 try{docker run -d --name zvonok-stage10-1-s3 --label zvonok.acceptance=stage10-1 --memory 1g --cpus 2 --tmpfs /data:rw,size=512m,mode=1777 -p 127.0.0.1:19001:9000 -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD zvonok-stage10-1-minio:source-pinned | Out-Null;if($LASTEXITCODE){throw 'MinIO startup failed'}}finally{Remove-Item Env:MINIO_ROOT_USER,Env:MINIO_ROOT_PASSWORD}
}
if(!(docker ps -a --filter 'name=^/zvonok-stage10-1-livekit$' --format '{{.Names}}')){
 docker run -d --name zvonok-stage10-1-livekit --label zvonok.acceptance=stage10-1 -p 127.0.0.1:17880:7880 -p 127.0.0.1:7881:7881 -p 127.0.0.1:7882:7882/udp livekit/livekit-server:v1.13.10@sha256:89ca9ce0791e18d6e03bf6e2c49ceb27e1c754eae8bd50c989386c1a5963791a --dev --bind 0.0.0.0 --node-ip 127.0.0.1 | Out-Null
 if($LASTEXITCODE){throw 'LiveKit startup failed'}
}
