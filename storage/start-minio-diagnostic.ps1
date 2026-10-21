$ErrorActionPreference = 'Stop'
if (docker ps -a --filter 'name=^/zvonok-stage5d-minio$' --format '{{.Names}}') { throw 'Inspect existing diagnostic MinIO first' }
docker run --rm --network none --mount type=volume,source=zvonok-stage5d-minio-data,target=/data --entrypoint chown postgres:16-alpine 65534:65534 /data
if ($LASTEXITCODE) { throw 'Diagnostic volume permission initialization failed' }
foreach ($line in Get-Content (Join-Path $env:LOCALAPPDATA 'Zvonok\local-s3\rustfs.env')) {
    $pair=$line -split '=',2
    if($pair[0] -eq 'RUSTFS_ACCESS_KEY') { $env:MINIO_ROOT_USER=$pair[1] }
    if($pair[0] -eq 'RUSTFS_SECRET_KEY') { $env:MINIO_ROOT_PASSWORD=$pair[1] }
}
docker run -d --name zvonok-stage5d-minio --label zvonok.acceptance=stage5d --network zvonok-net --network-alias zvonok-local-minio --restart unless-stopped --stop-timeout 60 --log-opt max-size=10m --log-opt max-file=3 -p 127.0.0.1:19010:9000 --mount type=volume,source=zvonok-stage5d-minio-data,target=/data -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD -e MINIO_BROWSER=off sha256:7ca316baeb2c0b98b995b361d23ae170597d604630cbc635d3f39e2ef1b49006 server /data
if ($LASTEXITCODE) { throw 'Diagnostic MinIO start failed' }
