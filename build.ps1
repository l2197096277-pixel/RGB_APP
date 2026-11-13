#requires -Version 5.1
<#
  幻彩灯控（Aurora Light）零 Gradle 构建脚本。
  依赖：JDK 17、Android SDK（platforms;android-35、build-tools;35.0.0）。
  产物：dist\AuroraLight-v1.0.0.apk
#>
param(
    [string]$Sdk = (Join-Path $PSScriptRoot '..\.buildtools\android-sdk'),
    [string]$Jdk = '',
    [string]$VersionName = '1.1.1',
    [int]$VersionCode = 12,
    [string]$StorePassword = 'AuroraLight2026!',
    [string]$KeyAlias = 'aurora',
    [string]$Python = ''
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$app = Join-Path $root 'app\src\main'
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist'
$keystoreDir = Join-Path $root 'keystore'
$python = $Python
if (-not $python -or -not (Test-Path $python)) { $python = $env:PYTHON_EXE }
if (-not $python -or -not (Test-Path $python)) { $python = 'python' }

if (-not $Jdk) {
    $jdkRoot = Join-Path $root '..\.buildtools\jdk'
    $found = Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $Jdk = $found.FullName }
}
if (-not (Test-Path (Join-Path $Jdk 'bin\javac.exe'))) {
    throw "找不到 JDK：$Jdk"
}

$platforms = Join-Path $Sdk 'platforms'
$androidJar = $null
foreach ($candidate in @('android-35\android.jar', 'android-35-ext15\android.jar', 'android-36\android.jar')) {
    $path = Join-Path $platforms $candidate
    if (Test-Path $path) { $androidJar = $path; break }
}
if (-not $androidJar) { throw "找不到 android.jar，请先安装 Android 35 平台" }

$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
if (-not (Test-Path (Join-Path $buildTools 'aapt2.exe'))) {
    $any = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($any) { $buildTools = $any.FullName }
}
foreach ($tool in @('aapt2.exe', 'zipalign.exe', 'apksigner.bat')) {
    if (-not (Test-Path (Join-Path $buildTools $tool))) { throw "缺少构建工具：$tool" }
}

$bin = Join-Path $Jdk 'bin'
$env:JAVA_HOME = $Jdk
$env:PATH = "$bin;$buildTools;$env:PATH"

Write-Host "JDK       : $Jdk"
Write-Host "android.jar: $androidJar"
Write-Host "buildTools : $buildTools"

New-Item -ItemType Directory -Force -Path $build, $dist, $keystoreDir | Out-Null
Remove-Item (Join-Path $build '*') -Recurse -Force -ErrorAction SilentlyContinue

# 1. 资源编译
$resZip = Join-Path $build 'resources.zip'
& (Join-Path $buildTools 'aapt2.exe') compile --dir (Join-Path $app 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile 失败' }

# 2. 资源链接 + 生成 R.java
$unsigned = Join-Path $build 'app-unsigned.apk'
$gen = Join-Path $build 'gen'
New-Item -ItemType Directory -Force -Path $gen | Out-Null
& (Join-Path $buildTools 'aapt2.exe') link `
    -o $unsigned `
    -I $androidJar `
    --manifest (Join-Path $app 'AndroidManifest.xml') `
    --java $gen `
    --min-sdk-version 23 `
    --target-sdk-version 35 `
    --version-code $VersionCode `
    --version-name $VersionName `
    $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link 失败' }

# 3. javac
$sources = Get-ChildItem (Join-Path $app 'java'), $gen -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
$sourceList = Join-Path $build 'sources.txt'
$sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' } |
    Set-Content -Path $sourceList -Encoding ASCII
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$lambdaStubs = Join-Path $buildTools 'core-lambda-stubs.jar'
$bootClasspath = if (Test-Path $lambdaStubs) { "$androidJar;$lambdaStubs" } else { $androidJar }
& (Join-Path $bin 'javac.exe') `
    -encoding UTF-8 `
    -source 8 `
    -target 8 `
    -Xlint:-options `
    -bootclasspath $bootClasspath `
    -classpath $androidJar `
    -d $classes `
    "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'javac 失败' }

# 4. 打包 class 并用 d8 转 dex
$classesJar = Join-Path $build 'classes.jar'
& (Join-Path $bin 'jar.exe') --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar 失败' }
$dexDir = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
& (Join-Path $buildTools 'd8.bat') `
    --release `
    --min-api 23 `
    --lib $androidJar `
    --output $dexDir `
    $classesJar
if ($LASTEXITCODE -ne 0) { throw 'd8 失败' }

# 5. 把 classes.dex 写入 APK
$withDex = Join-Path $build 'app-dex.apk'
& $python (Join-Path $root 'tools\add_dex.py') $unsigned $withDex (Join-Path $dexDir 'classes.dex')
if ($LASTEXITCODE -ne 0) { throw '写入 dex 失败' }

# 6. 对齐与签名
$aligned = Join-Path $build 'app-aligned.apk'
& (Join-Path $buildTools 'zipalign.exe') -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign 失败' }

$keystore = Join-Path $keystoreDir 'aurora-release.jks'
if (-not (Test-Path $keystore)) {
    Write-Host '生成项目签名密钥（请妥善保存，后续升级必须使用同一密钥）'
    & (Join-Path $bin 'keytool.exe') -genkeypair -v `
        -keystore $keystore `
        -storepass $StorePassword `
        -keypass $StorePassword `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname 'CN=Aurora Light, OU=Personal, O=Aurora, L=Shanghai, ST=Shanghai, C=CN'
    if ($LASTEXITCODE -ne 0) { throw '生成签名密钥失败' }
}

$apkName = "AuroraLight-v$VersionName.apk"
$apk = Join-Path $dist $apkName
& (Join-Path $buildTools 'apksigner.bat') sign `
    --ks $keystore `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$StorePassword" `
    --key-pass "pass:$StorePassword" `
    --min-sdk-version 23 `
    --out $apk `
    $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner 失败' }

& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK 签名校验失败' }

$hash = (Get-FileHash $apk -Algorithm SHA256).Hash
$size = (Get-Item $apk).Length
@(
    "name=$apkName"
    "versionName=$VersionName"
    "versionCode=$VersionCode"
    "sizeBytes=$size"
    "sha256=$hash"
    "minSdk=23"
    "targetSdk=35"
) | Set-Content (Join-Path $dist 'BUILD-INFO.txt') -Encoding UTF8

Write-Host ''
Write-Host "构建完成：$apk"
Write-Host "大小：$([math]::Round($size / 1KB, 1)) KB"
Write-Host "SHA256：$hash"
