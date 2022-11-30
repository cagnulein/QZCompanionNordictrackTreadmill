:: QZ-Companion App Restart after USB Debugging turned on
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

ping %TMIP%
timeout 5

adb disconnect
adb kill-server
adb connect %TMIP%
adb devices -l
timeout 5

echo.
echo Stopping QZ Companion ...
adb shell am force-stop org.cagnulein.qzcompanionnordictracktreadmill
timeout 5

echo.
echo Stopping iFit ...
adb shell am force-stop com.ifit.standalone
timeout 5

echo.
echo Launching QZ Companion ...
adb shell monkey -p org.cagnulein.qzcompanionnordictracktreadmill 1
timeout 5

echo.
echo Launching iFit ...
adb shell monkey -p com.ifit.standalone 1
timeout 5

echo.
pause > nul | set/p = QZ Companion is restarted. Press any key to exit . . .

echo.
echo Restart complete.


