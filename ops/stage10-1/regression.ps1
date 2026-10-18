param([string]$JavaHome='C:\Users\Пользователь\.jdks\semeru-21.0.9')
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$env:JAVA_HOME=$JavaHome;$env:Path="$JavaHome/bin;$env:Path"
& mvn.cmd -B test
if($LASTEXITCODE){throw 'Default regression failed'}
& mvn.cmd -B '-Dtest=*Test,*IT' test
if($LASTEXITCODE){throw 'Extended regression failed'}
& mvn.cmd -B '-Dtest=CorePerformanceIT' '-Dstage8.optimized=true' test
if($LASTEXITCODE){throw 'PostgreSQL query invariants failed'}
