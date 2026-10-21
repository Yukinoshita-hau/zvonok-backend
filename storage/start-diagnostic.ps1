$ErrorActionPreference = 'Stop'
# Credentials live outside the repository and are never printed.
$secretDirectory = Join-Path $env:LOCALAPPDATA 'Zvonok\local-s3'
New-Item -ItemType Directory -Force $secretDirectory | Out-Null
$env:RUSTFS_ENV_FILE = Join-Path $secretDirectory 'rustfs.env'
if (!(Test-Path -LiteralPath $env:RUSTFS_ENV_FILE)) {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 32
    $random.GetBytes($bytes)
    $secret = [Convert]::ToBase64String($bytes)
    $access = 'zvonok-' + [guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($env:RUSTFS_ENV_FILE, "RUSTFS_ACCESS_KEY=$access`nRUSTFS_SECRET_KEY=$secret`n")
    & icacls $env:RUSTFS_ENV_FILE /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(R,W)" | Out-Null
    if ($LASTEXITCODE) { throw 'Unable to restrict secret file permissions' }
}
if ((Get-PSDrive C).Free -lt 8GB) { throw 'At least 8 GiB host free space required for diagnostic deployment' }
docker compose -f "$PSScriptRoot\compose.yaml" up -d
if ($LASTEXITCODE) { throw 'RustFS deployment failed' }
