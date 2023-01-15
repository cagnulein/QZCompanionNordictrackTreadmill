:: Assumes treadmill USB Debugging is turned on 

@echo off

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

adb disconnect
adb kill-server
adb connect %TMIP%
adb devices -l
timeout 5

:: pull live logcat and QZ Companion logcat
adb logcat -d > logcat.txt
adb pull /sdcard/logcat.log

:: pull all wolflogs
adb pull /sdcard/.wolflogs/
::adb pull /sdcard/eru/

echo.
echo Debug files generated - logcat.log, logcat.txt, and \.wolflogs
echo.

pause




