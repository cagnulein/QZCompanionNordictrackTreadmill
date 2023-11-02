::assumes android treadmill usb debugging is turned on 

@echo off

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

adb disconnect
adb kill-server
adb connect %TMIP%
adb devices -l
timeout 5

set SAVESTAMP=%DATE:/=-%@%TIME::=-%
set SAVESTAMP=%SAVESTAMP: =%

adb shell screencap -p /sdcard/%SAVESTAMP%.png

adb pull /sdcard/%SAVESTAMP%.png
adb shell rm /sdcard/%SAVESTAMP%.png
