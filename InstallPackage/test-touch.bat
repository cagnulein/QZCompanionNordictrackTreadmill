@echo off

::set current directory to the location of this script
@pushd %~dp0

echo *** Make sure your treadmill is powered-up and USB Debugging is turned on ***

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

adb disconnect
adb kill-server
adb connect %TMIP%
adb devices -l
timeout 5


adb shell input swipe 100 200 300 400 200
pause
