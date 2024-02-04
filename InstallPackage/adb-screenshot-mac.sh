#!/bin/bash

# Assumes Android treadmill USB debugging is turned on

read -p "Enter treadmill IP address: " TMIP

ping -c 4 $TMIP
sleep 5

./adb disconnect
./adb kill-server
./adb connect $TMIP
./adb devices -l
sleep 5

SAVESTAMP=$(date +"%Y-%m-%d@%H-%M-%S" | tr -d ' ')

./adb shell screencap -p /sdcard/$SAVESTAMP.png

./adb pull /sdcard/$SAVESTAMP.png
./adb shell rm /sdcard/$SAVESTAMP.png
