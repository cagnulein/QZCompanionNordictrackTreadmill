# QZCompanionNordictrackTreadmill
Companion App of QZ for Nordictrack Treadmills

Reference: https://github.com/cagnulein/qdomyos-zwift/issues/815

**QZ-Companion Installation**

Authors: Al Udell and Roberto Viola

June 28, 2022

QZ and QZ Companion software development: Roberto Viola

QZ on Facebook - <https://www.facebook.com/groups/149984563348738>

**Technical Overview:** The new QZ Companion app, when installed on your
treadmill, communicates live speed and inclination to the QZ app
running on another device (e.g. Windows PC or laptop, Android phone or
tablet, or iOS iPhone or iPad). QZ then communicates this information to
Zwift running on a 3rd device (e.g. Windows PC or laptop, Android
phone or tablet, or iOS iPhone or iPad). A 2nd device could be used to
run both QZ and Zwift, eliminating the need for a 3rd device, as long
as QZ runs in the background while Zwift runs in the foreground. This only
works consistently on Windows and iOS, but not Android.

**About QZ Companion**:

-   This solution works on iFit-enabled/iFit-embedded treadmills. It
    transmits speed and inclination directly to Zwift.

-   The QZ Companion app always runs in the background on your
    treadmill, using minimal memory and CPU.

-   QZ Companion will auto-start when the treadmill is powered on. There
    is no need to enter the treadmills "privileged mode" after initial
    installation.

-   QZ Companion is not affected by Android or iFit updates. It
    continues to auto-start and run in the background after iFit and
    Android updates.

-   You can use flexible equipment startup sequences - e.g. start your
    treadmill first, HRM monitor second, QZ app third, Zwift last, or
    change the sequence. QZ will always communicate with QZ Companion
    regardless of your startup sequence.

-   QZ Companion is installed over Wifi via an ADB script run from a
    Windows PC. It makes no changes to the underlying Android or iFit
    structure and can be easily removed via an uninstall script or by
    doing a treadmill factory reset.
    
**About QZ (QZ Fitness)**:

-   QZ is a cross-platform app (iOS, Android, Raspberry, Windows, and Mac) that acts as a native Bluetooth protocol bridge for many exercise machines (spin bikes, treadmills, bike trainers, rowers, and ellipticals) to FTMS (FiTness Machine Service protocol) Bluetooth for direct connection to Zwift and other compatible apps.

-   Wahoo Direct Connect (DIRCON) protocol was recently added to QZ in order to bypass Bluetooth connection drop-outs that are common during a bike or running race (Wifi and Ethernet is considerably more stable than Bluetooth).

**Disclaimer**: I have only tested this solution on a
NordicTrack Commercial 2950 (2021 model) treadmill with a built-in 22"
touchscreen. However, it should work on any iFit-enabled/iFit-embedded
NordicTrack or Pro-Form treadmill with built-in Android tablet. Refer to
<https://www.ifit.com/equipment/treadmills> for more details.

**Important**: Please let us know if you get this to work on your treadmill so we can
compile a list of compatible machines.

![A picture containing sport, exercise device Description automatically
generated](media/image1.jpg)

NordicTrack Commercial 2950 (2021 model)

**About iFit**: To workout in Zwift, you will need to logon to iFit on
your treadmill in order to use manual mode. However, you do not need an
iFit subscription to use the treadmills manual mode.

**Installation Instructions:** If you have USB debugging mode enabled
and know your treadmills IP, you can skip to step 4.

1.  Factory reset the treadmill. It is highly recommended that you
    factory reset your treadmill before continuing. In my case, a reset
    is performed on my NT C2950 by pressing in and holding the pinhole
    style reset button on in the left-side on the console while
    simultaneously turning on the treadmill with the power switch. I use
    a paper-clip to push the reset button in. The reset button must be
    released after 10 seconds of turning the treadmill on.
    Unfortunately, the reset button and power switch are far apart and
    may require two people to coordinate the reset. After the 10 second
    reset, the console will display the reset and progress animation.
    The reset usually takes about 5 -7 minutes on my treadmill. When
    it is done, iFit will prompt you do login and select your Wifi
    network.

2.  Enable Privileged mode. When you see the main iFit dashboard screen,
    tap on an area of white that doesn't activate anything (e.g. the top
    of the screen) 10 times, count 7 seconds, then tap on the same spot
    10 more times. If done correctly, you will see the Privileged mode
    activation screen. Open the website https://getresponsecode.com and
    enter the first 6-digit code you see on your treadmill screen and
    click Submit. The website will provide a response code which you
    will enter in the treadmill console via the onscreen keyboard. If
    done correctly, a message at the bottom of the screen appears
    confirming privileged mode is enabled, and the Android desktop will
    appear.

3.  Enable USB debugging. From the treadmills Android desktop, swipe up
    from the bottom of the screen to open the installed apps screen.
    Select Settings, System, Advanced settings, and About tablet. Take
    note of the treadmills IP address (e.g. 10.0.0.124), and also look
    for the Build number. Tap on the Build number 7 times. You will get
    a message at the bottom of the screen confirming Developer options
    is unlocked. Next, select the back button/arrow to return to the
    previous Advanced settings screen. Select Developer options and look
    for USB Debugging -- turn it on. Select Ok when the 'Enable USB
    debugging' prompt appears.

![Graphical user interface, text Description automatically generated
with medium confidence](media/image2.png)

Enable USB Debugging on your treadmill

4.  Install the QZ Companion app on your treadmill. Download the QZ
    Companion installation package from this Github repository and extract
    it to your Windows PC. Go into the extracted folder and run
    qz-companion.bat by either double-clicking it or running it from the
    command-line. When prompted to enter the treadmills IP address,
    enter the same IP as noted in previous Step 3 and hit enter. The
    script will ping the IP address first to ensure it is reachable on
    the network, then proceed to open an ADB connection and install the
    QZ Companion app. When completed, the script will reboot the
    treadmill. Once rebooted, proceed to login to iFit. At this point,
    QZ Companion is running in the background and is ready to transmit
    treadmill speed and incline data to QZ.

![](media/image3.png)

Run QZ-Companion.bat on a Wifi connected Windows PC

5.  Configure QZ to communicate with QZ Companion. On your 2nd QZ
    device (Windows PC or laptop, Android phone or tablet, or iOS iPhone
    or iPad), open QZ and go to Settings. Expand Treadmill Options,
    Proform/NordicTrack Options, and enter the treadmills IP address under
    NordicTrack 2950 IP and hit OK. Exit and relaunch QZ to activate the
    change. If done correctly, QZ will display live data tiles to
    indicate it is communicating directly with QZ Companion on the
    treadmill. Next in QZ, expand Experimental Features and turn on
    Enable Virtual Device, Virtual Device Bluetooth, and Wahoo Direct
    Connect. Exit and relaunch QZ again to activate the changes. Wahoo
    direct connect will now transmit the treadmills speed and incline
    data to Zwift via Bluetooth.

![](media/image4.png)

Enter your treadmill IP address in QZ

![Graphical user interface, text, application Description automatically
generated](media/image5.png)

Turn on Enable Virtual Device, Virtual Device Bluetooth in QZ

![Graphical user interface, text, application Description automatically
generated](media/image6.png)

Turn on Wahoo Direct Connect in QZ

6.  Start an iFit manual workout to confirm QZ Companion communication.
    From the main iFit dashboard screen on your treadmill, select Manual
    workout. QZ Companion will immediately begin to transmit live speed
    and incline changes to QZ which will display in the live data tiles.
    At this point, you can take advantage of the many features of QZ
    while working out or simply use it to transmit data to Zwift.

![Diagram Description automatically
 generated](media/image7.png)

Start an iFit manual workout

![](media/image8.png)

QZ receiving live speed and incline from QZ Companion

7.  Finally, launch and configure Zwift to use the QZ Bluetooth device.
    Now that QZ is receiving live treadmill data from QZ Companion,
    Zwift can be configured to receive this data from QZ over Bluetooth.
    At the Zwift Paired Devices screen, under Run Speed, search and
    select Wahoo Tread device (this is QZ). Optionally select your Heart
    Rate and Cadence devices and proceed to start a Zwift workout. Zwift
    will receive live speed and incline data from your treadmill via QZ
    and QZ Companion data link.

![](media/image9.png)

Select Wahoo Tread device as Run Device in Zwift

![Graphical user interface Description automatically
 generated](media/image10.png)

Start a Zwift workout and control speed from your treadmill

**The QZ Companion installation package (qz-companion.zip) contains**:

-   QZCompanionNordictrackTreadmill.apk (QZ Companion Android app).

-   QZ-Companion.bat (batch script used to install QZ Companion via
    ADB).

-   QZ-Companion-simple.bat (alternative batch script to use if you wish
    to run the commands separately for debugging and troubleshooting
    purposes).

-   Uninstall-QZ-Companion.bat (batch script used to uninstall/remove QZ
    Companion).

-   QZ Companion Installation.pdf (this document)

-   All other files (adb.exe, AdbWinApi.dll, tee.exe) are required for
    the scripts. Do not delete them).

**Troubleshooting**:

-   The primary reason QZ Companion installation will likely fail is
    because your Windows PC can't communicate with the treadmill IP
    address over your Wifi network. Ensure both devices are connected to
    the same Wifi network. The installation script will first attempt to
    ping your treadmill IP address. If this ping fails, you will need to
    troubleshoot the connection.

-   When executed, the QZ-Companion.bat installation script will
    generate a log file named QZ-Companion-log.txt. If communication fails
    or the app fails to install on your treadmill, refer to this log to
    troubleshoot specific errors. You may be required to share this log
    to obtain technical support.
