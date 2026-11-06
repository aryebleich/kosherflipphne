@echo off
REM KosherFlip installer for Windows - see docs\INSTALL.md
REM Put this file in the same folder as the APK files and adb.exe (platform-tools), then double-click it.
setlocal enabledelayedexpansion
cd /d "%~dp0"
set APPS=KosherGuard FlipLauncher KosherBrowser KosherAI KosherKeys

where adb >nul 2>nul
if errorlevel 1 if not exist adb.exe (
  echo adb was not found. Download "SDK Platform-Tools for Windows", unzip it, and copy this file
  echo and the APK files into the platform-tools folder. See docs\INSTALL.md step 1.
  pause & exit /b 1
)

echo.
echo Before you continue:
echo   - The phone was factory reset (or has NO accounts in Settings - Accounts)
echo   - Developer options - USB debugging is ON, and the phone is plugged in
echo.
pause

echo Waiting for the phone (accept "Allow USB debugging" on the phone)...
adb wait-for-device

for %%A in (%APPS%) do (
  if not exist "%%A.apk" (
    echo Missing %%A.apk in this folder. Download all files from the GitHub release.
    pause & exit /b 1
  )
  echo Installing %%A...
  adb install -r -g "%%A.apk"
  if errorlevel 1 ( echo Install of %%A failed. & pause & exit /b 1 )
)

adb shell pm disable com.kosherflip.guard/.SetupActivity >nul 2>nul
adb shell pm grant com.kosherflip.guard android.permission.WRITE_SECURE_SETTINGS

echo Making KosherGuard the device owner (the filter)...
adb shell dpm set-device-owner com.kosherflip.guard/.GuardAdminReceiver
if errorlevel 1 (
  echo.
  echo Android did not accept the device owner. Remove ALL accounts from the phone
  echo ^(or factory reset it^) and run this again. If a different filter is already
  echo built into the phone, only its vendor can remove it.
  pause & exit /b 1
)

adb shell cmd package set-home-activity com.kosherflip.launcher/.HomeActivity >nul 2>nul
adb shell ime enable com.kosherflip.keys/.T9Service >nul 2>nul
adb shell am start -n com.kosherflip.guard/.AdminActivity >nul

echo.
echo Done! On the phone: choose an admin PIN, pick a profile, optionally add a free
echo Gemini key and a Google account, then turn on "USB debugging lock".
pause
