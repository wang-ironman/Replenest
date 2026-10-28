$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$jdkCandidates = @(
    $env:JAVA_HOME,
    'D:/others/KF-Note/.tools/jdk-21/jdk-21.0.12.1+1'
) | Where-Object { $_ }
$jdk = $jdkCandidates | Where-Object {
    (Test-Path (Join-Path $_ 'bin/javac.exe')) -and (Test-Path (Join-Path $_ 'bin/java.exe'))
} | Select-Object -First 1

if (-not $jdk) {
    throw 'Java 17 or newer is required. Set JAVA_HOME or install the configured fallback JDK.'
}

$outputDir = Join-Path $repoRoot '.tools/test-core'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$source = Join-Path $repoRoot 'android/app/src/main/java/com/replenest/app/Forecast.java'
$test = Join-Path $repoRoot 'tests/ForecastTest.java'
$numericSource = Join-Path $repoRoot 'android/app/src/main/java/com/replenest/app/NumericValue.java'
$numericTest = Join-Path $repoRoot 'tests/NumericValueTest.java'
& (Join-Path $jdk 'bin/javac.exe') --release 17 -d $outputDir $source $test $numericSource $numericTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $jdk 'bin/java.exe') -cp $outputDir ForecastTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $jdk 'bin/java.exe') -cp $outputDir NumericValueTest
exit $LASTEXITCODE
