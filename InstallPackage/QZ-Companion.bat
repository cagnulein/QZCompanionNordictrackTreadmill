:: QZ-Companion App Installer
:: Author: Al Udell
:: Date: June 28, 2022
:: QZ on Facebook - https://www.facebook.com/groups/149984563348738

@echo off

::set current directory to the location of this script
@pushd %~dp0

echo.
echo *** Make sure your treadmill is powered-up and USB Debugging is turned on ***

echo.
set /p TMIP="Enter treadmill IP address: "

echo Log generated at %time% %date% for IP %TMIP% > QZ-Companion-log.txt

echo. | tee -a QZ-Companion-log.txt
echo Pinging %TMIP% ... | tee -a QZ-Companion-log.txt
ping -n 1 %TMIP% >> QZ-Companion-log.txt 2>&1
ping -n 1 %TMIP% | findstr /r /c:"[0-9] *ms" >nul
if %errorlevel% == 0 (
  echo Ping successful.
) else (
  echo Cannot ping %TMIP%.
  goto end
)
timeout 5

echo. | tee -a QZ-Companion-log.txt
echo Connecting to treadmill via ADB ... | tee -a QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt

:: fix for 'failed to authenticate to {IP:port}'
adb disconnect >nul 2>&1 
adb kill-server >nul 2>&1 
adb connect %TMIP% >nul 2>&1

adb disconnect >> QZ-Companion-log.txt 2>&1
adb kill-server >> QZ-Companion-log.txt 2>&1
adb connect %TMIP% >> QZ-Companion-log.txt 2>&1
adb connect %TMIP% | findstr /r /c:"connected to" >nul
if %errorlevel% == 0 (
  echo ADB connection successful.
) else (
  echo Cannot establish ADB connection.
  goto end
)
echo. >> QZ-Companion-log.txt
adb devices -l >> QZ-Companion-log.txt
timeout 5

:: report Android and SDK version
echo Android version: >> QZ-Companion-log.txt
adb shell getprop ro.build.version.release >> QZ-Companion-log.txt
echo SDK version: >> QZ-Companion-log.txt
adb shell getprop ro.build.version.sdk >> QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt

echo Checking for previous installation ... | tee -a QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt
adb shell pidof org.cagnulein.qzcompanionnordictracktreadmill | findstr /r /c:"[0-9]*" >nul
if %errorlevel% == 0 (
  echo QZ Companion found - removing | tee -a QZ-Companion-log.txt
  adb uninstall org.cagnulein.qzcompanionnordictracktreadmill >> QZ-Companion-log.txt 2>&1
) else (
  echo QZ Companion not installed | tee -a QZ-Companion-log.txt
)
timeout 5

echo. | tee -a QZ-Companion-log.txt
echo Installing QZ Companion ... | tee -a QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt
adb install QZCompanionNordictrackTreadmill.apk 2>&1 | tee -a QZ-Companion-log.txt
timeout 5

echo. | tee -a QZ-Companion-log.txt
echo Launching QZ Companion ... | tee -a QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt
adb shell monkey -p org.cagnulein.qzcompanionnordictracktreadmill 1 >> QZ-Companion-log.txt 2>&1
timeout 5

echo. | tee -a QZ-Companion-log.txt
echo Launching iFit ... | tee -a QZ-Companion-log.txt
echo. >> QZ-Companion-log.txt
adb shell monkey -p com.ifit.standalone 1 >> QZ-Companion-log.txt 2>&1
timeout 5

:: save ADB log
adb logcat -d > logcat.txt

echo. | tee -a QZ-Companion-log.txt
pause > nul | set/p = QZ Companion is installed. Press any key to reboot treadmill . . .
echo.
echo Rebooting treadmill ... | tee -a QZ-Companion-log.txt

adb reboot

echo.
echo Installation complete.

:end
echo.
echo Refer to QZ-Companion-log.txt for installation details.
echo.

pause


