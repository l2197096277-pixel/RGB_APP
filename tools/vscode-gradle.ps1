# VS Code 使用的环境探测与 Gradle 任务脚本
# 自动寻找 JDK 17 / Android SDK 35，然后执行 Gradle、adb 安装或启动。
param(
    [string]$Task = 'assembleDebug',
    [switch]$Install,
    [switch]$Launch,
    [switch]$Logcat,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'),
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jre')
    )
    foreach ($base in @(
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Microsoft')
    )) {
        if (Test-Path $base) {
            $candidates += Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk|jbr' } |
                ForEach-Object { $_.FullName }
        }
    }
    $localJdkRoot = Join-Path (Split-Path $root -Parent) '.buildtools\jdk'
    if (Test-Path $localJdkRoot) {
        $candidates += Get-ChildItem $localJdkRoot -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FullName }
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\javac.exe'))) {
            return $candidate
        }
    }
    return $null
}

function Find-Sdk {
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { $candidates += $env:ANDROID_HOME }
    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties |
            Where-Object { $_ -match '^sdk.dir=' } |
            Select-Object -First 1
        if ($line) {
            $sdkPath = $line.Substring(8).Replace('\:', ':').Replace('\\', '\')
            $candidates += $sdkPath
        }
    }
    $candidates += @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:ProgramFiles 'Android\Sdk'),
        (Join-Path (Split-Path $root -Parent) '.buildtools\android-sdk')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platforms'))) {
            return $candidate
        }
    }
    return $null
}

$jdk = Find-Jdk
$sdk = Find-Sdk
if (-not $jdk) {
    throw '找不到 JDK 17。请安装 Android Studio，或设置 JAVA_HOME。'
}
if (-not $sdk) {
    throw '找不到 Android SDK。请安装 Android Studio / SDK 35，或设置 ANDROID_HOME。'
}

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$(Join-Path $jdk 'bin');$(Join-Path $sdk 'platform-tools');$env:PATH"
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$gradlew = Join-Path $root 'gradlew.bat'

Write-Host "JDK : $jdk"
Write-Host "SDK : $sdk"

if ($Logcat) {
    if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }
    & $adb logcat -s AuroraBle AndroidRuntime
    exit $LASTEXITCODE
}

if ($Clean) {
    & $gradlew -p $root --console=plain --no-daemon clean
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& $gradlew -p $root --console=plain --no-daemon $Task
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Install -or $Launch) {
    if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }
    if ($Install) {
        $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
        if (-not (Test-Path $apk)) { throw "找不到 APK：$apk，请先执行 assembleDebug" }
        & $adb install -r $apk
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    if ($Launch) {
        & $adb shell am start -n com.aurora.light/.MainActivity
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}

Write-Host '完成。'
