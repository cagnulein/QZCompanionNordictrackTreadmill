#!/bin/bash

# Assumes Android treadmill USB debugging is turned on

read -p "Enter treadmill IP address: " TMIP

ping -c 4 $TMIP
sleep 5

# fix for 'failed to authenticate to {IP:port}'
{
  adb disconnect
  adb kill-server
  adb connect "$TMIP"
} > /dev/null 2>&1

{
  adb disconnect
  adb kill-server
  adb connect "$TMIP"
} >> QZ-Companion-log.txt 2>&1

if adb connect "$TMIP" | grep -q 'connected to'; then
  echo -e "ADB connection successful.\n"
else
  echo "Cannot establish ADB connection."
  exit 1
fi
echo >> QZ-Companion-log.txt
adb devices -l >> QZ-Companion-log.txt
sleep 5

SAVESTAMP=$(date +"%Y-%m-%d@%H-%M-%S" | tr -d ' ')

adb shell screencap -p /sdcard/$SAVESTAMP.png

adb pull /sdcard/$SAVESTAMP.png
adb shell rm /sdcard/$SAVESTAMP.png
