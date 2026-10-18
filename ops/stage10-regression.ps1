param([Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path "$JavaHome/bin/java.exe")) { throw 'Supply JDK 21 home' }
$env:JAVA_HOME=$JavaHome
$env:Path="$JavaHome/bin;$env:Path"
New-Item -ItemType Directory -Force stage10-evidence | Out-Null
& mvn.cmd -B test *> stage10-evidence/backend-default-final.log
if ($LASTEXITCODE) { throw 'Default regression failed' }
& mvn.cmd -B '-Dtest=*Test,*IT' package *> stage10-evidence/backend-extended.log
if ($LASTEXITCODE) { throw 'Extended regression failed' }
& mvn.cmd -B '-Dtest=CorePerformanceIT' '-Dstage8.optimized=true' test *> stage10-evidence/postgres-performance.log
if ($LASTEXITCODE) { throw 'PostgreSQL query invariants failed' }
# *IT owns its PostgreSQLContainer datasource. Never substitute an external DB URL.
