@echo off

::set current directory to the location of this script
@pushd %~dp0

echo *** Make sure your treadmill is powered-up and USB Debugging is turned on ***

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

adb disconnect >> QZ-Companion-log.txt 2>&1
adb kill-server >> QZ-Companion-log.txt 2>&1
adb connect %TMIP% >> QZ-Companion-log.txt 2>&1
adb devices -l >> QZ-Companion-log.txt
timeout 5


adb shell input swipe 100 200 300 400 200 >> QZ-Companion-log.txt
pause
