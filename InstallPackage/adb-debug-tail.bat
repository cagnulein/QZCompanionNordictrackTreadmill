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

echo "Getting log in realtime. Press CTRL-C to stop and copy all the output from the screen"

:: pull live logcat and QZ Companion logcat
adb logcat -f



