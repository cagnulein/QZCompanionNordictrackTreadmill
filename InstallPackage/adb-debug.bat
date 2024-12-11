:: Assumes treadmill USB Debugging is turned on 

@echo off

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

:: fix for 'failed to authenticate to {IP:port}'
adb disconnect >nul 2>&1 
adb kill-server >nul 2>&1 
adb connect %TMIP% >nul 2>&1

adb disconnect
adb kill-server
adb connect %TMIP%
adb connect %TMIP% | findstr /r /c:"connected to" >nul
if %errorlevel% == 0 (
  echo ADB connection successful.
) else (
  echo Cannot establish ADB connection.
  goto end
)
adb devices -l
timeout 5

:: pull live logcat and QZ Companion logcat
adb logcat -d > logcat.txt
adb pull /sdcard/logcat.log

:: pull all wolflogs
adb pull /sdcard/.wolflogs/
adb pull /sdcard/eru/

echo.
echo Debug files generated - logcat.log, logcat.txt, and \.wolflogs
echo.

:end
pause




