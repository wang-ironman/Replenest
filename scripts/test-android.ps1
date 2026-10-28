param([string]$Serial = 'emulator-5554', [string]$Adb = 'D:\others\KF-Note\.tools\android-sdk\platform-tools\adb.exe')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
if (!$Serial.StartsWith('emulator-')) { throw '此验收会重置测试应用数据，请指定 Android 模拟器序列号。' }
& $Adb -s $Serial install -r (Join-Path $taskRoot 'artifacts/Replenest-0.1.5-debug.apk')
if ($LASTEXITCODE -ne 0) { throw '安装应用失败' }
& $Adb -s $Serial install -r (Join-Path $taskRoot 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw '安装验收包失败，请先 build-android.ps1 -TestApk' }
foreach ($taskRunner in @('acceptance', 'api', 'toggle', 'numeric')) {
    $taskResult = & $Adb -s $Serial shell am instrument -w -e suite $taskRunner 'com.replenest.app.test/com.replenest.app.AcceptanceRunner'
    $taskResult | Write-Output
    if ($LASTEXITCODE -ne 0 -or !($taskResult -match 'INSTRUMENTATION_RESULT: success=') -or ($taskResult -match 'INSTRUMENTATION_RESULT: failure=')) { throw "$taskRunner 验收失败" }
}
