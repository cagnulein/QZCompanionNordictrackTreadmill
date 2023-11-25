
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

adb shell pm grant org.cagnulein.qzcompanionnordictracktreadmill android.permission.READ_LOGS
pause