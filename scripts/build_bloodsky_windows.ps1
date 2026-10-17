# BLOODSKY: free local Windows and self-hosted builds.
[CmdletBinding()]
param([ValidateSet('None','Full','Quick')][string]$UiProfile='None')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Step([string]$Label,[string]$Exe,[string[]]$Argv) {
  Write-Host "==== $Label ====" -ForegroundColor Cyan
  & $Exe @Argv
  if ($LASTEXITCODE -ne 0) { throw "$Label failed: exit code $LASTEXITCODE" }
}
function Require([string]$Exe) {
  if ($null -eq (Get-Command $Exe -ErrorAction SilentlyContinue)) {
    throw "$Exe is missing; see docs/LOCAL_CI_WITHOUT_GITHUB_MINUTES.md"
  }
}

Push-Location $root
try {
  foreach ($program in @('python','javac','gradle')) { Require $program }
  $sdk=$env:ANDROID_HOME
  if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk=$env:ANDROID_SDK_ROOT }
  if ([string]::IsNullOrWhiteSpace($sdk)) { throw 'ANDROID_HOME/ANDROID_SDK_ROOT unset; install Android Studio SDK 35' }
  $env:ANDROID_HOME=$sdk
  $androidJar=Join-Path $sdk 'platforms\android-35\android.jar'
  if (!(Test-Path $androidJar)) { throw "Missing Android 35 SDK: $androidJar" }

  Step 'Pinned Craft resources' 'python' @('tools/prepare_craft_resources.py')
  Step 'Full Python unit suite' 'python' @('-m','unittest','discover','-s','tools','-p','test_*.py','-v')
  Step 'Android JUnit and debug APK' 'gradle' @('testDebugUnitTest','assembleDebug','--stacktrace')
  $apk=Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
  if (!(Test-Path $apk)) { throw "APK missing at $apk" }
  Write-Host "Debug APK: $apk" -ForegroundColor Green

  if ($UiProfile -ne 'None') {
    Require 'adb'
    Require 'jar'
    $serial=(& adb get-serialno).Trim()
    $qemu=(& adb shell getprop ro.kernel.qemu).Trim()
    if (!($serial.StartsWith('emulator-') -and $qemu -eq '1')) {
      throw 'For safety, UI smoke runs only on an Android emulator, not a real phone.'
    }
    $d8=Join-Path $sdk 'build-tools\35.0.0\d8.bat'
    if (!(Test-Path $d8)) { throw 'Android SDK Build Tools 35.0.0 missing' }
    $base=Join-Path $root 'build\ui-input'
    $classes=Join-Path $base 'classes'
    $dex=Join-Path $base 'dex'
    New-Item -ItemType Directory -Force -Path $classes,$dex | Out-Null
    Step 'UI driver: javac' 'javac' @('-source','8','-target','8','-cp',$androidJar,'-d',$classes,'tools/android/BloodskyFingerTap.java')
    Step 'UI driver: D8' $d8 @('--min-api','23','--lib',$androidJar,'--output',$dex,(Join-Path $classes 'BloodskyFingerTap.class'))
    $jar=Join-Path $base 'bloodsky-ui-input.jar'
    if (Test-Path $jar) { Remove-Item $jar -Force }
    Step 'UI driver: JAR' 'jar' @('cf',$jar,'-C',$dex,'classes.dex')
    $test=if($UiProfile -eq 'Quick'){'tools/android_ui_fbd_quick.py'}else{'tools/android_ui_smoke.py'}
    if (!(Test-Path $test)) { throw "$test missing on this branch; choose -UiProfile Full" }
    Step "Android $UiProfile emulator UI test" 'python' @($test,'--apk',$apk)
  }
  Write-Host 'BLOODSKY LOCAL BUILD PASSED' -ForegroundColor Green
} finally { Pop-Location }
