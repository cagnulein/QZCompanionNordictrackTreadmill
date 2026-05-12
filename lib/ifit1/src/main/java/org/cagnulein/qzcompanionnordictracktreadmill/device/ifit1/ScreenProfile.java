package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

/**
 * Screen geometry profiles for iFit console displays.
 *
 * Constants derived from iFit APK 2.6.90 (versionCode 4963), package com.ifit.standalone.
 * Source files: res/layout/inworkouttablet.xml, res/values/dimens.xml.
 *   workout_slider_margin = 12 dp
 *   workout_slider_width  = 125 dp
 *   left track centre     = margin + width / 2 = 12 + 62.5 = 74.5 dp
 *
 * If the iFit app is updated and these layout constants change, re-derive leftTrackX and
 * rightTrackX from the new APK before updating device classes.
 *
 * Both leftTrackX and rightTrackX are stored independently (not derived as width − left)
 * because Android's dp→px rounding can produce asymmetric results at the pixel boundary.
 *
 * Usage in device constructors:
 *   new Slider(ScreenProfile.W1920.leftTrackX, initialY)    // incline / grade slider
 *   new Slider(ScreenProfile.W1920.rightTrackX, initialY)   // speed / resistance slider
 */
public enum ScreenProfile {
    W1920(75, 1845),
    W1280(75, 1205),
    W1024(74,  950),
    W800 (73,  725);

    public final int leftTrackX;
    public final int rightTrackX;

    ScreenProfile(int leftTrackX, int rightTrackX) {
        this.leftTrackX  = leftTrackX;
        this.rightTrackX = rightTrackX;
    }
}
