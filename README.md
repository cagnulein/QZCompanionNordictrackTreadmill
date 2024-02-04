# QZCompanionNordictrackTreadmill
Companion App of QZ for Nordictrack Treadmills & Bikes

Reference: https://github.com/cagnulein/qdomyos-zwift/issues/815

**QZ-Companion Installation**

Authors: Al Udell and Roberto Viola

Updated: December 15, 2022

QZ and QZ Companion software development: Roberto Viola

QZ on Facebook - <https://www.facebook.com/groups/149984563348738>

Watch the accompanying Youtube video below for a quick overview of this guide.

<div align="center">
    <a href="https://www.youtube.com/watch?v=_KRMSHxo5AQ"><img src="https://img.youtube.com/vi/_KRMSHxo5AQ/0.jpg" alt="Companion App of QZ for Nordictrack Treadmills & Bikes" style="width:100%;"></a>
</div>
<br>

**Technical Overview:** The QZ Companion app, when installed on your
NordicTrack treadmill or bike, communicates live speed and inclination to the QZ app
running on another device (e.g. Windows PC or laptop, Android phone or
tablet, or iOS iPhone or iPad). QZ then communicates this information to
Zwift running on a 3rd device (e.g. Windows PC or laptop, Android
phone or tablet, or iOS iPhone or iPad). A 2nd device could be used to
run both QZ and Zwift, eliminating the need for a 3rd device, as long
as QZ runs in the background while Zwift runs in the foreground.

**New Feature:** QZ Companion now works with QZ to control the incline of your treadmill (auto-incline),
and speed as well (auto-speed), or in the case of a bike, it automatically controls incline and resistance. 
For example, you can choose to have Zwift control your
treadmill or bike incline as you run or cycle through virtual worlds; choose to have QZ make speed (or resistance) and
incline changes as you progress through a Zwift workout, or, you can choose to load
a GPX file in QZ and have the treadmill or bike follow the incline recorded in the file.

**Disclaimer**: We have tested this solution on several models of NordicTrack and Pro-Form treadmills
and bikes and many are supported now. We began development on a NordicTrack Commercial 2950 (2021 model) 
treadmill with a built-in 22" touchscreen. Note that this solution should work on any iFit-enabled/iFit-embedded
NordicTrack or Pro-Form treadmill or bike with built-in Android tablet. Refer to
<https://www.ifit.com/equipment/treadmills> and <https://www.ifit.com/equipment/bikes> for available models. 
We are constantly adding QZ Companion support for more iFit-enabled/iFit-embedded
NordicTrack and Pro-Form treadmills and bikes and will update this document as
models are tested and confirmed to work.

**Important**: Please let us know if you get QZ Companion to work on your treadmill or bike so we can
add it to the list of compatible machines.

![A picture containing sport, exercise device Description automatically
generated](media/image1.jpg)

NordicTrack Commercial 2950 (2021 model)

**About QZ Companion**:

-   This solution works on iFit-enabled/iFit-embedded treadmills and bikes. It
    transmits speed (or resistance) and inclination directly to Zwift. Also, auto-inclination and auto-speed
    (or auto-resistance) control works on most models of NordicTrack and ProForm treadmills and bikes.

-   The QZ Companion app always runs in the background on your
    treadmill or bike, using minimal memory and CPU.

-   QZ Companion will auto-start when the treadmill or bike is powered on. There
    is no need to enter the treadmill or bikes "privileged mode" after initial
    installation.

-   QZ Companion is not affected by Android or iFit updates. It
    continues to auto-start and run in the background after iFit and
    Android updates.

-   You can use flexible equipment startup sequences - e.g. start your
    treadmill or bike first, HRM monitor second, QZ app third, Zwift last, or
    change the sequence. QZ will always communicate with QZ Companion
    regardless of your startup sequence.
    
**About QZ (QZ Fitness)**:

-   QZ is a cross-platform app (iOS, Android, Raspberry, Windows, and Mac) that acts as a native Bluetooth protocol bridge for many exercise machines (spin bikes, treadmills, bike trainers, rowers, and ellipticals) to FTMS (FiTness Machine Service protocol) Bluetooth for direct connection to Zwift and other compatible apps.

-   Wahoo Direct Connect (DIRCON) protocol was recently added to QZ in order to bypass Bluetooth connection drop-outs that are common during a bike or running race (Wifi and Ethernet is considerably more stable than Bluetooth).

**About iFit**: To workout in Zwift, you will need to logon to iFit on
your treadmill in order to use manual mode. However, you do not need an
iFit subscription to use the treadmills manual mode.

**Installation Instructions:** 

QZ Companion can be installed in one of two ways. Both methods make no changes to the underlying Android or iFit structure and can be easily removed via an uninstall script or by doing a treadmill or bike factory reset.

**Note:** The first install method is strongly recommended if you can get an ADB connection established between a Windows PC 
and your treadmill or bike. The second method is provided for those who cannot get an ADB connection established, but is harder to troubleshoot if you encounter problems.

If you have USB debugging mode enabled
and know your treadmill or bikes IP, you can skip to step 4.

1.  Factory reset the treadmill or bike. It is highly recommended that you
    factory reset your treadmill or bike before continuing. For example, a reset
    is performed on a NT C2950 treadmill by pressing in and holding the pinhole
    style reset button on in the left-side on the console while
    simultaneously turning on the treadmill with the power switch. I use
    a paper-clip to push the reset button in. The reset button must be
    released after 10 seconds of turning the treadmill on.
    Unfortunately, the reset button and power switch are far apart and
    may require two people to coordinate the reset. After the 10 second
    reset, the console will display the reset and progress animation.
    The reset usually takes about 5 -7 minutes on this treadmill. When
    it is done, iFit will prompt you do login and select your Wifi
    network. Note that reset may be done by pinhole or within the iFit settings,
    or some other way on your machine. Refer to your user manual to find out how.

3.  Enable Privileged mode. When you see the main iFit dashboard screen,
    tap on an area of white that doesn't activate anything (e.g. the top
    of the screen) 10 times, count 7 seconds, then tap on the same spot
    10 more times. If done correctly, you will see the Privileged mode
    activation screen. Open the website https://getresponsecode.com and
    enter the first 6-digit code you see on your treadmill screen and
    click Submit. The website will provide a response code which you
    will enter in the treadmill or bike console via the onscreen keyboard. If
    done correctly, a message at the bottom of the screen appears
    confirming privileged mode is enabled, and the Android desktop will
    appear.

4.  Enable USB debugging. From the treadmill or bikes Android desktop, swipe up
    from the bottom of the screen to open the installed apps screen.
    Select Settings, System, Advanced settings, and About tablet. Take
    note of the treadmill or bikes IP address (e.g. 10.0.0.124), and also look
    for the Build number. Tap on the Build number 7 times. You will get
    a message at the bottom of the screen confirming Developer options
    is unlocked. Next, select the back button/arrow to return to the
    previous Advanced settings screen. Select Developer options and look
    for USB Debugging -- turn it on. Select Ok when the 'Enable USB
    debugging' prompt appears.

![Graphical user interface, text Description automatically generated
with medium confidence](media/image2.png)
    Enable USB Debugging on your treadmill

**QZ Companion Installation Method 1:  Over Wifi via an ADB script run from a Windows PC or Mac. Choose this method if you can successfully establish an ADB connection between treadmill and Windows PC.**

4.  Install the QZ Companion app on your treadmill or bike. Download the QZ
    Companion installation package from this Github repository and extract
    it to your computer. 

    **If you are using a Windows PC:**
    Go into the extracted folder, open the folder InstallPackage and
    run `qz-companion.bat` by either double-clicking it or running it from the
    command-line. 

    **If you are using a Mac:**
    Go into the extracted folder and open the folder InstallPackage.
    Right click the file `QZ-Companion-MacOS` and select open **OR** navigate
    to this folder in Terminal and enter `./QZ-Companion-MacOS`
        _Important note: Double clicking this file will cause MacOS to throw
        a security warning, you must use one of the methods above._

    When prompted to enter the treadmill or bikes IP address,
    enter the same IP as noted in previous Step 3 and hit enter. The
    script will ping the IP address first to ensure it is reachable on
    the network, then proceed to open an ADB connection and install the
    QZ Companion app. When completed, the script will prompt you to 
    press any key to reboot the treadmill or bike. 
    
    Once rebooted, you will have iFit running in the foreground and QZ Companion running 
    in the background. You need to bring QZ Companion to the foreground for a one-time setup. You should still have access 
    to the Android system by swiping up from the bottom of the screen to see the Android navigation bar. If you don't see 
    the navigation bar, you will need to enable Privileged mode again (see Step #3 above), however Privileged mode should be permanently enabled as part of the
    `QZ-Companion.bat` or `QZ-Companion-MacOS` installation script. The Android navigation bar displays 3 navigation controls: Back, Home, and App Overview. 
    Hit the App Overview button and swipe over to QZ Companion. In the QZ Companion screen, select your specific exercise machine (e.g. "NordicTrack C2950").
    Once again, swipe up from the bottom of the screen to display the Android navigation bar, select App Overview, and swipe over to iFit. Note that in 
    some cases, you may need to reboot your treadmill or bike once more for the QZ Companion selection to work.
        
    Next, proceed to login to iFit. At this point,
    QZ Companion is running in the background and is ready to transmit
    treadmill speed (or resistance) and incline data to QZ.
    
    If it reads all 0's, try going to the treadmill's Settings > Apps > QZ Companion app > Permissions, and enabling all permissions.

![](media/image3.png)
    Run `QZ-Companion.bat` or `QZ-Companion-MacOS` script on a WiFi connected computer

**QZ Installation Method 2: Via the treadmill or bikes build-in web browser.**

4.	To install the QZ Companion app on your treadmill or bike via the treadmill built-in web browser, 
    you must first enable Privileged mode from the main iFit dashboard screen - see Step #3 above. Note that there is
  	no need to enable USB debugging for this method. Next, from the treadmills Android desktop, swipe up from the bottom of the screen to open the installed apps screen. 
    Select Browser.

![1 - browser](https://user-images.githubusercontent.com/63697253/200885852-75909cbd-e084-4e99-8f71-8201fc4d4f1a.jpg)
    Open the treadmill or bikes build-in web browser

-   Enter the following short URL address – https://tinyurl.com/qz-companion. This link will download the latest QZ Companion APK to the browser download folder on your treadmill or bike.

![2 - apk downloaded](https://user-images.githubusercontent.com/63697253/200886187-19eb5515-54fb-4003-834c-1e587cab5976.jpg)
    QZ Companion APK downloaded

-   Next, select the APK to start installation. You will get a warning message stating your tablet is not allowed to install 
apps from unknown sources. Rest assured, QZ Companion is safe to install. Select “Settings” which will bring you to a 
screen where you can toggle on “allow from this source” and confirm “do you want to install this application?”. 
QZ Companion will proceed to install. You will get a confirmation screen when done.

![3 - unknown app](https://user-images.githubusercontent.com/63697253/200886407-b7050778-7720-4ce8-897c-e8b8ea10e8f1.jpg)
![4 - allow from this source](https://user-images.githubusercontent.com/63697253/200886432-9cbb4ba9-6ba7-4f52-abd8-421aa4bf196e.jpg)
![5 - install confirmation](https://user-images.githubusercontent.com/63697253/200886460-77fd108f-45fb-465b-be9e-9e7af7ade9c1.jpg)
![6 - app installed](https://user-images.githubusercontent.com/63697253/200886487-c8150e6a-ce0e-44bd-9b5a-a4696b986703.jpg)
    QZ Companion APK installation screens

-   Next, you need to bring QZ Companion to the foreground for a one-time setup. You should still have access 
    to the Android system by swiping up from the bottom of the screen to see the Android navigation bar. If you don't see 
    the navigation bar, you will need to enable Privileged mode again (see Step #3 above). The Android navigation bar displays 3 navigation controls: Back, Home, and App Overview. 
    Hit the Home button to view the available app icons. Hit the QZ Companion icon to launch. In the QZ Companion screen, select your specific exercise machine (e.g. "NordicTrack C2950").
    Once again, swipe up from the bottom of the screen to display the Android navigation bar, select App Overview, and swipe over to iFit. Note that in 
    some cases, you may need to reboot your exercise machine once more for the QZ Companion selection to work. When the treadmill or bike is rebooted,  you will have iFit running
    in the foreground and QZ Companion running in the background. 
    
-   Proceed to login to iFit. At this point, QZ Companion is running in the background and is ready to transmit treadmill speed (or resistance) and incline data to QZ.
  
-   If you wish to have Zwift control incline of your treadmill or bike (auto-incline), and speed (auto-speed), or in the case of a bike, auto-resistance, you must first have Privileged mode
    and USB debugging turned on. Note that Privileged mode is permanently enabled as part of the `QZ-Companion.bat` or `QZ-Companion-MacOS` installation script in Method #1. However, for Method #2,
    unfortunately you must manually enable them first as they will get disabled on every treadmill or bike reboot. Once you have Privileged mode and USB debugging turned on, you must enable the
    onscreen treadmill speed and incline sliders; for a bike it is the onscreen resistance and incline sliders. To enable these sliders, start a manual workout. Once you see the manual workout
    screen, tap on any empty area of the screen. This will bring up the "Controls" option. Tap on Controls and you will now see the onscreen sliders on each side of the
    screen. Repeat the same procedure to disable the sliders if you don't need auto-speed/incline/resistance.

![onscreen-controls](https://github.com/cagnulein/QZCompanionNordictrackTreadmill/assets/63697253/d9191fb8-0942-4db4-bb96-21db94a74fe7)
    NT C2950 screen with onscreen speed and incline sliders enabled

**Now that QZ Companion is installed and running on your treadmill or bike, it’s time to configure QZ to communicate with QZ Companion.**

5.  Configure QZ to communicate with QZ Companion. On your 2nd QZ
    device (Windows PC or laptop, Android phone or tablet, or iOS iPhone
    or iPad), open QZ and go to Settings. Expand Treadmill or Bike Options,
    Proform/NordicTrack Options, choose your treadmill or bike from the dropdown and enter the treadmill or bikes IP address under
    NordicTrack 2950 IP and hit OK. Exit and relaunch QZ to activate the
    change. If done correctly, QZ will display live data tiles to
    indicate it is communicating directly with QZ Companion on the
    treadmill or bike. Next in QZ, expand Experimental Features and turn on
    Enable Virtual Device, Virtual Device Bluetooth, and Wahoo Direct
    Connect. Exit and relaunch QZ again to activate the changes. Wahoo
    direct connect will now transmit the treadmills speed (or bike resistance) and incline
    data to Zwift via Bluetooth.

![](media/image4.png)
    Enter your treadmill or bike IP address in QZ

![Graphical user interface, text, application Description automatically
generated](media/image5.png)

Turn on Enable Virtual Device, Virtual Device Bluetooth in QZ

![Graphical user interface, text, application Description automatically
generated](media/image6.png)
    Turn on Wahoo Direct Connect in QZ

6.  Start an iFit manual workout to confirm QZ Companion communication.
    From the main iFit dashboard screen on your treadmill or bike, select Manual
    workout. QZ Companion will immediately begin to transmit live speed
    (or bike resistance) and incline changes to QZ which will display in the live data tiles.
    At this point, you can take advantage of the many features of QZ
    while working out or simply use it to transmit data to Zwift.

![](media/image8.png)
    QZ receiving live speed and incline from QZ Companion

7.  Finally, launch and configure Zwift to use the QZ Bluetooth device.
    Now that QZ is receiving live treadmill data from QZ Companion,
    Zwift can be configured to receive this data from QZ over Bluetooth.
    At the Zwift Paired Devices screen, under treadmill Run Speed, search and
    select Wahoo Tread device (this is QZ). Optionally select your Heart
    Rate and Cadence devices and proceed to start a Zwift workout. Zwift
    will receive live speed and incline data from your treadmill or bike via QZ
    and QZ Companion data link.

![](media/image9.png)
    Select Wahoo Tread device as Run Device in Zwift

![Graphical user interface Description automatically
 generated](media/image10.png)
    Start a Zwift workout and control speed from your treadmill or bike

**The QZ Companion installation package (qz-companion.zip) contains**:

-   `QZCompanionNordictrackTreadmill.apk` (QZ Companion Android app).

-   `QZ-Companion.bat` (batch script used to install QZ Companion via
    ADB on Windows).

-   `QZ-Companion-MacOS` (shell script used to install QZ Companion via
    ADB on MacOS).

-   `QZ-Companion-simple.bat` (alternative batch script to use if you wish
    to run the commands separately for debugging and troubleshooting
    purposes).

-   `Uninstall-QZ-Companion.bat` (batch script used to uninstall/remove QZ
    Companion).

-   All other files (`adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll`, `tee.exe`) are required for the scripts. Do not delete them.

**Troubleshooting**:

-   The primary reason QZ Companion installation will likely fail is
    because your Windows PC can't communicate with the treadmill or bike IP
    address over your Wifi network. Ensure both devices are connected to
    the same Wifi network. The installation script will first attempt to
    ping your treadmill IP address. If this ping fails, you will need to
    troubleshoot the connection.

-   When executed, the QZ-Companion.bat installation script will
    generate a log file named QZ-Companion-log.txt. If communication fails
    or the app fails to install on your treadmill or bike, refer to this log to
    troubleshoot specific errors. You may be required to share this log
    to obtain technical support.
