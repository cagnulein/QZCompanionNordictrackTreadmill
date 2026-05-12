package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.C1750Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.C1750MphMinus3GradeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.C1750Ntl14122Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.C1750_2020Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.C1750_2021Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Elite1000Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Elite900Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Exp7iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Nordictrack2450Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Nordictrack2950Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Nordictrack2950MaxSpeed22Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.Proform2000Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.ProformCarbonT14Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.ProformPro9000Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.S40Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.T65sDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.T85sDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.T95sDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X14iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X22iV2Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X32iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X32iNtl39019Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X32iNtl39221Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X9iDevice;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.CommandDispatcher;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.command.SpeedCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies that GestureTreadmillDevice subclasses generate correct "input swipe" commands
 * for speed and incline gestures.
 *
 * Tests install a capturing CommandExecutor so no Android or ADB calls happen.
 */
public class TreadmillDeviceTest {

    /** Captures the last command sent via Device.commandExecutor. */
    private String lastCommand;

    /** Wraps a device to capture its swipe commands into lastCommand. */
    private <T extends Device> T dev(T d) {
        d.commandExecutor = cmd -> lastCommand = cmd;
        return d;
    }

    @Before
    public void installCaptureExecutor() {
        lastCommand = null;
    }

    @After
    public void restoreDefaultExecutor() {
    }

    // ── X11iDevice ────────────────────────────────────────────────────────────
    // speedX=1207, targetSpeedY(v)=(int)(621.997 - 21.785*v), initialSpeedY=600
    // inclineX=75, targetInclineY(v)=(int)(565.491 - 8.44*v), initialInclineY=557

    @Test
    public void x11i_applySpeed_atZero_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // y2=(int)(621.997 - 0) = 621; y1=600
        assertEquals("input swipe 1205 600 1205 621 200", lastCommand);
    }

    @Test
    public void x11i_applySpeed_atTen_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(10.0, dev);
        // y2=(int)(621.997 - 217.85) = (int)404.147 = 404; y1=600
        assertEquals("input swipe 1205 600 1205 404 200", lastCommand);
    }

    @Test
    public void x11i_applySpeed_updatesCurrentY() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(10.0, dev); // y2=404; currentSpeedY becomes 404
        dev.sliderOf(SpeedSlider.class).moveTo(5.0, dev);  // y2=(int)(621.997 - 108.925) = (int)513.072 = 513; y1=404
        assertEquals("input swipe 1205 404 1205 513 200", lastCommand);
    }

    @Test
    public void x11i_applyIncline_atZero_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // y2=(int)(565.491 - 0) = 565; y1=557
        assertEquals("input swipe 75 557 75 565 200", lastCommand);
    }

    @Test
    public void x11i_applyIncline_atTen_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        // y2=(int)(565.491 - 84.4) = (int)481.091 = 481; y1=557
        assertEquals("input swipe 75 557 75 481 200", lastCommand);
    }

    @Test
    public void x11i_applyIncline_updatesCurrentY() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev); // y2=481; currentInclineY becomes 481
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);  // y2=(int)(565.491 - 42.2) = (int)523.291 = 523; y1=481
        assertEquals("input swipe 75 481 75 523 200", lastCommand);
    }

    @Test
    public void x11i_isInstanceOfTreadmillDevice() {
        assertTrue(new X11iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x11i_hasCorrectDisplayName() {
        assertEquals("X11i Treadmill", new X11iDevice().displayName());
    }

    // ── X32iDevice ────────────────────────────────────────────────────────────

    @Test
    public void x32i_isInstanceOfTreadmillDevice() {
        assertTrue(new X32iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x32i_hasCorrectDisplayName() {
        assertEquals("X32i Treadmill", new X32iDevice().displayName());
    }

    // ── Nordictrack2950Device ─────────────────────────────────────────────────

    @Test
    public void nordictrack2950_isInstanceOfTreadmillDevice() {
        assertTrue(new Nordictrack2950Device() instanceof GestureTreadmillDevice);
    }

    // ── C1750Device ───────────────────────────────────────────────────────────

    @Test
    public void c1750_isInstanceOfTreadmillDevice() {
        assertTrue(new C1750Device() instanceof GestureTreadmillDevice);
    }

    // ── T65sDevice ────────────────────────────────────────────────────────────

    @Test
    public void t65s_isInstanceOfTreadmillDevice() {
        assertTrue(new T65sDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void t65s_hasCorrectDisplayName() {
        assertEquals("T6.5s Treadmill", new T65sDevice().displayName());
    }

    // ── Elite1000Device ───────────────────────────────────────────────────────

    @Test
    public void elite1000_isInstanceOfTreadmillDevice() {
        assertTrue(new Elite1000Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void elite1000_hasCorrectDisplayName() {
        assertEquals("Elite 1000 Treadmill", new Elite1000Device().displayName());
    }

    // ── Speed monotonicity ────────────────────────────────────────────────────

    @Test
    public void x11i_speedY_isMonotonicallyDecreasing() {
        // Higher speed → lower Y (slider moves up = less Y value)
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(5.0, dev);
        int y5 = Integer.parseInt(lastCommand.split(" ")[5]);
        // Reset by using a fresh device
        X11iDevice dev2 = dev(new X11iDevice());
        dev2.sliderOf(SpeedSlider.class).moveTo(10.0, dev2);
        int y10 = Integer.parseInt(lastCommand.split(" ")[5]);
        assertTrue("Y at speed=10 should be less than Y at speed=5", y10 < y5);
    }

    @Test
    public void x11i_inclineY_isMonotonicallyDecreasing() {
        // Higher incline → lower Y (slider moves up = less Y value)
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        int y5 = Integer.parseInt(lastCommand.split(" ")[5]);
        X11iDevice dev2 = dev(new X11iDevice());
        dev2.sliderOf(InclineSlider.class).moveTo(10.0, dev2);
        int y10 = Integer.parseInt(lastCommand.split(" ")[5]);
        assertTrue("Y at incline=10 should be less than Y at incline=5", y10 < y5);
    }

    // ── X32iDevice — formula tests ────────────────────────────────────────────
    // speedX=1845, targetSpeedY(v)=(int)(834.85-26.946*v), initialSpeedY=927
    // inclineX=76, targetInclineY(v)=(int)(734.07-12.297*v), initialInclineY=881

    @Test
    public void x32i_applySpeed_atZero_generatesCorrectSwipe() {
        X32iDevice dev = dev(new X32iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // y2=(int)(834.85)=834; y1=927
        assertEquals("input swipe 1845 927 1845 834 200", lastCommand);
    }

    @Test
    public void x32i_applySpeed_atTen_generatesCorrectSwipe() {
        X32iDevice dev = dev(new X32iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 834
        dev.sliderOf(SpeedSlider.class).moveTo(10.0, dev);
        // y2=(int)(834.85-269.46)=(int)565.39=565; y1=834
        assertEquals("input swipe 1845 834 1845 565 200", lastCommand);
    }

    @Test
    public void x32i_applyIncline_atZero_generatesCorrectSwipe() {
        X32iDevice dev = dev(new X32iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // y2=(int)(734.07)=734; y1=881
        assertEquals("input swipe 75 881 75 734 200", lastCommand);
    }

    @Test
    public void x32i_applyIncline_atFive_generatesCorrectSwipe() {
        X32iDevice dev = dev(new X32iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 734
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // y2=(int)(734.07-61.485)=(int)672.585=672; y1=734
        assertEquals("input swipe 75 734 75 672 200", lastCommand);
    }

    // ── C1750Device — formula tests ───────────────────────────────────────────
    // speed: currentThumbY→targetThumbY(0)=816; incline: no currentThumbY, initial=700
    // speedX=1845, targetSpeedY(v)=785-(int)((v-1)*31.42)
    // inclineX=79, targetInclineY(v)=(int)(700-34.9*v)

    @Test
    public void c1750_applySpeed_atZero_generatesCorrectSwipe() {
        C1750Device dev = dev(new C1750Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=targetThumbY(0)=785-(int)(-31.42)=785+31=816; y2=816
        assertEquals("input swipe 1845 816 1845 816 200", lastCommand);
    }

    @Test
    public void c1750_applySpeed_atEight_generatesCorrectSwipe() {
        C1750Device dev = dev(new C1750Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=816 (snapshot-derived); y2=785-(int)(7*31.42)=785-219=566
        assertEquals("input swipe 1845 816 1845 566 200", lastCommand);
    }

    @Test
    public void c1750_applyIncline_atZero_generatesCorrectSwipe() {
        C1750Device dev = dev(new C1750Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=700 (initial); y2=(int)(700-0)=700
        assertEquals("input swipe 75 700 75 700 200", lastCommand);
    }

    @Test
    public void c1750_applyIncline_atFive_generatesCorrectSwipe() {
        C1750Device dev = dev(new C1750Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 700
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=700; y2=(int)(700-174.5)=(int)525.5=525
        assertEquals("input swipe 75 700 75 525 200", lastCommand);
    }

    // ── Nordictrack2950Device — formula tests ─────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1845, targetSpeedY(v)=807-(int)((v-1)*31), targetSpeedY(0)=838
    // inclineX=75, targetInclineY(v)=807-(int)((v+3)*31.1), targetInclineY(0)=714

    @Test
    public void nordictrack2950_applySpeed_atZero_generatesCorrectSwipe() {
        Nordictrack2950Device dev = dev(new Nordictrack2950Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=targetThumbY(0)=838; y2=838
        assertEquals("input swipe 1845 838 1845 838 200", lastCommand);
    }

    @Test
    public void nordictrack2950_applySpeed_atEight_generatesCorrectSwipe() {
        Nordictrack2950Device dev = dev(new Nordictrack2950Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=838; y2=807-(int)(7*31)=807-217=590
        assertEquals("input swipe 1845 838 1845 590 200", lastCommand);
    }

    @Test
    public void nordictrack2950_applyIncline_atZero_generatesCorrectSwipe() {
        Nordictrack2950Device dev = dev(new Nordictrack2950Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=targetThumbY(0)=714; y2=714
        assertEquals("input swipe 75 714 75 714 200", lastCommand);
    }

    @Test
    public void nordictrack2950_applyIncline_atFive_generatesCorrectSwipe() {
        Nordictrack2950Device dev = dev(new Nordictrack2950Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=714; y2=807-(int)(8*31.1)=807-248=559
        assertEquals("input swipe 75 714 75 559 200", lastCommand);
    }

    // ── T65sDevice — formula tests ────────────────────────────────────────────
    // no currentThumbY on either slider
    // speedX=1205, targetSpeedY(v)=(int)(578.36-35.866*v*0.621371), initial=495
    // inclineX=74, targetInclineY(v)=(int)(576.91-34.182*v), initial=585

    @Test
    public void t65s_applySpeed_atZero_generatesCorrectSwipe() {
        T65sDevice dev = dev(new T65sDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=495; y2=(int)(578.36)=578
        assertEquals("input swipe 1205 495 1205 578 200", lastCommand);
    }

    @Test
    public void t65s_applySpeed_atEight_generatesCorrectSwipe() {
        T65sDevice dev = dev(new T65sDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 578
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=578; y2=(int)(578.36-35.866*8*0.621371)=(int)(578.36-178.283)=(int)400.077=400
        assertEquals("input swipe 1205 578 1205 400 200", lastCommand);
    }

    @Test
    public void t65s_applyIncline_atZero_generatesCorrectSwipe() {
        T65sDevice dev = dev(new T65sDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=585; y2=(int)(576.91)=576
        assertEquals("input swipe 75 585 75 576 200", lastCommand);
    }

    @Test
    public void t65s_applyIncline_atFive_generatesCorrectSwipe() {
        T65sDevice dev = dev(new T65sDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 576
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=576; y2=(int)(576.91-34.182*5); FP gives 405 not 406
        assertEquals("input swipe 75 576 75 405 200", lastCommand);
    }

    // ── Elite1000Device — formula tests ───────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1209, targetSpeedY(v)=600-(int)(v*0.621371*31.33), targetSpeedY(0)=600
    // inclineX=76, targetInclineY(v)=589-(int)(v*32.8), targetInclineY(0)=589

    @Test
    public void elite1000_applySpeed_atEight_generatesCorrectSwipe() {
        Elite1000Device dev = dev(new Elite1000Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=600; y2=600-(int)(4.970968*31.33)=600-(int)155.691=600-155=445
        assertEquals("input swipe 1205 600 1205 445 200", lastCommand);
    }

    @Test
    public void elite1000_applyIncline_atFive_generatesCorrectSwipe() {
        Elite1000Device dev = dev(new Elite1000Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=589; y2=589-(int)(5*32.8)=589-164=425
        assertEquals("input swipe 75 589 75 425 200", lastCommand);
    }

    // ── X22iDevice (shell runtime, overrides execute) ─────────────────────────
    // no currentThumbY; dev() replaces shell executor after construction
    // speedX=1845, targetSpeedY(v)=(int)(785-23.636...*v), initial=785
    // inclineX=75, targetInclineY(v)=(int)(785-11.304...*(v+6)), initial=785

    @Test
    public void x22i_isInstanceOfTreadmillDevice() {
        assertTrue(new X22iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x22i_hasCorrectDisplayName() {
        assertEquals("X22i Treadmill", new X22iDevice().displayName());
    }

    @Test
    public void x22i_applySpeed_atZero_generatesCorrectSwipe() {
        X22iDevice dev = dev(new X22iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=785; y2=(int)(785)=785
        assertEquals("input swipe 1845 785 1845 785 200", lastCommand);
    }

    @Test
    public void x22i_applySpeed_atEight_generatesCorrectSwipe() {
        X22iDevice dev = dev(new X22iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 785
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=785; y2=(int)(785-23.636...*8)=(int)(785-189.09)=(int)595.909=595
        assertEquals("input swipe 1845 785 1845 595 200", lastCommand);
    }

    @Test
    public void x22i_applyIncline_atZero_generatesCorrectSwipe() {
        X22iDevice dev = dev(new X22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=785; y2=(int)(785-11.304...*6)=(int)(785-67.826)=(int)717.173=717
        assertEquals("input swipe 75 785 75 717 200", lastCommand);
    }

    @Test
    public void x22i_applyIncline_atFive_generatesCorrectSwipe() {
        X22iDevice dev = dev(new X22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 717
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=717; y2=(int)(785-11.304...*11)=(int)(785-124.347)=(int)660.652=660
        assertEquals("input swipe 75 717 75 660 200", lastCommand);
    }

    // ── X9iDevice ────────────────────────────────────────────────────────────
    // no currentThumbY; dev() replaces shell executor after construction
    // speedX=725, targetSpeedY(v)=(int)(345.6315-13.6315*v), initial=332
    // inclineX=73, targetInclineY(v)=(int)(311.91-3.3478*v), initial=332

    @Test
    public void x9i_isInstanceOfTreadmillDevice() {
        assertTrue(new X9iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x9i_hasCorrectDisplayName() {
        assertEquals("X9i Treadmill", new X9iDevice().displayName());
    }

    @Test
    public void x9i_applySpeed_atZero_generatesCorrectSwipe() {
        X9iDevice dev = dev(new X9iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=332; y2=(int)(345.6315)=345
        assertEquals("input swipe 725 332 725 345 200", lastCommand);
    }

    @Test
    public void x9i_applySpeed_atEight_generatesCorrectSwipe() {
        X9iDevice dev = dev(new X9iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 345
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=345; y2=(int)(345.6315-13.6315*8)=(int)(345.6315-109.052)=(int)236.579=236
        assertEquals("input swipe 725 345 725 236 200", lastCommand);
    }

    @Test
    public void x9i_applyIncline_atZero_generatesCorrectSwipe() {
        X9iDevice dev = dev(new X9iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=332; y2=(int)(311.91)=311
        assertEquals("input swipe 73 332 73 311 200", lastCommand);
    }

    @Test
    public void x9i_applyIncline_atFive_generatesCorrectSwipe() {
        X9iDevice dev = dev(new X9iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 311
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=311; y2=(int)(311.91-3.3478*5)=(int)(311.91-16.739)=(int)295.171=295
        assertEquals("input swipe 73 311 73 295 200", lastCommand);
    }

    // ── S40Device ─────────────────────────────────────────────────────────────
    // no currentThumbY
    // speedX=949, targetSpeedY(v)=(int)(507-12.5*v), initial=482
    // inclineX=75, targetInclineY(v)=(int)(490-21.4*v), initial=490

    @Test
    public void s40_isInstanceOfTreadmillDevice() {
        assertTrue(new S40Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void s40_hasCorrectDisplayName() {
        assertEquals("S40 Treadmill", new S40Device().displayName());
    }

    @Test
    public void s40_applySpeed_atZero_generatesCorrectSwipe() {
        S40Device dev = dev(new S40Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=507 (initial); y2=(int)(507)=507
        assertEquals("input swipe 950 507 950 507 200", lastCommand);
    }

    @Test
    public void s40_applySpeed_atEight_generatesCorrectSwipe() {
        S40Device dev = dev(new S40Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 507
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=507; y2=(int)(507-100)=407
        assertEquals("input swipe 950 507 950 407 200", lastCommand);
    }

    @Test
    public void s40_applyIncline_atZero_generatesCorrectSwipe() {
        S40Device dev = dev(new S40Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=490; y2=(int)(490)=490
        assertEquals("input swipe 74 490 74 490 200", lastCommand);
    }

    @Test
    public void s40_applyIncline_atFive_generatesCorrectSwipe() {
        S40Device dev = dev(new S40Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=490; y2=(int)(490-107)=383
        assertEquals("input swipe 74 490 74 383 200", lastCommand);
    }

    // ── T85sDevice ────────────────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1207, targetSpeedY(v)=(int)(629.81-20.81*v), targetSpeedY(0)=629
    // inclineX=75, targetInclineY(v)=(int)(609-36.417*v), targetInclineY(0)=609

    @Test
    public void t85s_isInstanceOfTreadmillDevice() {
        assertTrue(new T85sDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void t85s_hasCorrectDisplayName() {
        assertEquals("T8.5s Treadmill", new T85sDevice().displayName());
    }

    @Test
    public void t85s_applySpeed_atEight_generatesCorrectSwipe() {
        T85sDevice dev = dev(new T85sDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=629; y2=(int)(629.81-166.48)=(int)463.33=463
        assertEquals("input swipe 1205 629 1205 463 200", lastCommand);
    }

    @Test
    public void t85s_applyIncline_atFive_generatesCorrectSwipe() {
        T85sDevice dev = dev(new T85sDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=609; y2=(int)(609-182.085)=(int)426.915=426
        assertEquals("input swipe 75 609 75 426 200", lastCommand);
    }

    // ── Exp7iDevice ───────────────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=950, targetSpeedY(v)=453-(int)((v*0.621371-1)*22.702), targetSpeedY(0)=475
    // inclineX=74, targetInclineY(v)=442-(int)(v*21.802), targetInclineY(0)=442

    @Test
    public void exp7i_isInstanceOfTreadmillDevice() {
        assertTrue(new Exp7iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void exp7i_hasCorrectDisplayName() {
        assertEquals("EXP 7i Treadmill", new Exp7iDevice().displayName());
    }

    @Test
    public void exp7i_applySpeed_atEight_generatesCorrectSwipe() {
        Exp7iDevice dev = dev(new Exp7iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=8*0.621371=4.970968; (4.970968-1)*22.702=90.141; (int)=90; 453-90=363; fromY=475
        assertEquals("input swipe 950 475 950 363 200", lastCommand);
    }

    @Test
    public void exp7i_applyIncline_atFive_generatesCorrectSwipe() {
        Exp7iDevice dev = dev(new Exp7iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=442; y2=442-(int)(5*21.802)=442-(int)109.01=442-109=333
        assertEquals("input swipe 74 442 74 333 200", lastCommand);
    }

    // ── Nordictrack2450Device ─────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1845, targetSpeedY(v)=(int)(-26.33*(v*0.621371)+831.39), targetSpeedY(0)=831
    // inclineX=72, targetInclineY(v)=715-(int)((v+3)*29.26), targetInclineY(0)=628

    @Test
    public void nordictrack2450_isInstanceOfTreadmillDevice() {
        assertTrue(new Nordictrack2450Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void nordictrack2450_hasCorrectDisplayName() {
        assertEquals("NordicTrack 2450 Treadmill", new Nordictrack2450Device().displayName());
    }

    @Test
    public void nordictrack2450_applySpeed_atEight_generatesCorrectSwipe() {
        Nordictrack2450Device dev = dev(new Nordictrack2450Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=4.970968; -26.33*4.970968=-130.855; 831.39-130.855=700.535; (int)=700; fromY=831
        assertEquals("input swipe 1845 831 1845 700 200", lastCommand);
    }

    @Test
    public void nordictrack2450_applyIncline_atFive_generatesCorrectSwipe() {
        Nordictrack2450Device dev = dev(new Nordictrack2450Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=628; y2=715-(int)(8*29.26)=715-(int)234.08=715-234=481
        assertEquals("input swipe 75 628 75 481 200", lastCommand);
    }

    // ── Nordictrack2950MaxSpeed22Device ───────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1845, targetSpeedY(v)=682-(int)((v-1)*26.5), targetSpeedY(0)=708
    // inclineX=75, targetInclineY(v)=807-(int)((v+3)*31.1), targetInclineY(0)=714

    @Test
    public void nordictrack2950MaxSpeed22_isInstanceOfTreadmillDevice() {
        assertTrue(new Nordictrack2950MaxSpeed22Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void nordictrack2950MaxSpeed22_hasCorrectDisplayName() {
        assertEquals("NordicTrack 2950 Treadmill (Max Speed 22)", new Nordictrack2950MaxSpeed22Device().displayName());
    }

    @Test
    public void nordictrack2950MaxSpeed22_applySpeed_atEight_generatesCorrectSwipe() {
        Nordictrack2950MaxSpeed22Device dev = dev(new Nordictrack2950MaxSpeed22Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=708; y2=682-(int)(7*26.5)=682-(int)185.5=682-185=497
        assertEquals("input swipe 1845 708 1845 497 200", lastCommand);
    }

    @Test
    public void nordictrack2950MaxSpeed22_applyIncline_atFive_generatesCorrectSwipe() {
        Nordictrack2950MaxSpeed22Device dev = dev(new Nordictrack2950MaxSpeed22Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=714; y2=807-(int)(8*31.1)=807-(int)248.8=807-248=559
        assertEquals("input swipe 75 714 75 559 200", lastCommand);
    }

    // ── Proform2000Device ─────────────────────────────────────────────────────
    // speed: no currentThumbY, initial=598
    // incline: currentThumbY, targetInclineY(0)=455
    // speedX=1205, targetSpeedY(v)=(int)(631.03-19.921*v)
    // inclineX=79, targetInclineY(v)=520-(int)((v+3)*21.804)

    @Test
    public void proform2000_isInstanceOfTreadmillDevice() {
        assertTrue(new Proform2000Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void proform2000_hasCorrectDisplayName() {
        assertEquals("ProForm 2000 Treadmill", new Proform2000Device().displayName());
    }

    @Test
    public void proform2000_applySpeed_atZero_generatesCorrectSwipe() {
        Proform2000Device dev = dev(new Proform2000Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=598; y2=(int)(631.03)=631
        assertEquals("input swipe 1205 598 1205 631 200", lastCommand);
    }

    @Test
    public void proform2000_applySpeed_atEight_generatesCorrectSwipe() {
        Proform2000Device dev = dev(new Proform2000Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 631
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=631; y2=(int)(631.03-159.368)=(int)471.662=471
        assertEquals("input swipe 1205 631 1205 471 200", lastCommand);
    }

    @Test
    public void proform2000_applyIncline_atFive_generatesCorrectSwipe() {
        Proform2000Device dev = dev(new Proform2000Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=455; y2=520-(int)(8*21.804)=520-(int)174.432=520-174=346
        assertEquals("input swipe 75 455 75 346 200", lastCommand);
    }

    // ── ProformCarbonT14Device ────────────────────────────────────────────────
    // no currentThumbY on either slider
    // speedX=1845, targetSpeedY(v)=(int)(810-52.8*v*0.621371), initial=807
    // inclineX=76, targetInclineY(v)=(int)(844-46.833*v), initial=844

    @Test
    public void proformCarbonT14_isInstanceOfTreadmillDevice() {
        assertTrue(new ProformCarbonT14Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void proformCarbonT14_hasCorrectDisplayName() {
        assertEquals("ProForm Carbon T14 Treadmill", new ProformCarbonT14Device().displayName());
    }

    @Test
    public void proformCarbonT14_applySpeed_atZero_generatesCorrectSwipe() {
        ProformCarbonT14Device dev = dev(new ProformCarbonT14Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=810 (initial); y2=(int)(810)=810
        assertEquals("input swipe 1845 810 1845 810 200", lastCommand);
    }

    @Test
    public void proformCarbonT14_applySpeed_atEight_generatesCorrectSwipe() {
        ProformCarbonT14Device dev = dev(new ProformCarbonT14Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 810
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=810; y2=(int)(810-52.8*4.970968)=(int)(810-262.467)=(int)547.533=547
        assertEquals("input swipe 1845 810 1845 547 200", lastCommand);
    }

    @Test
    public void proformCarbonT14_applyIncline_atFive_generatesCorrectSwipe() {
        ProformCarbonT14Device dev = dev(new ProformCarbonT14Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=844; y2=(int)(844-234.165)=(int)609.835=609
        assertEquals("input swipe 75 844 75 609 200", lastCommand);
    }

    // ── ProformPro9000Device ──────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1825, targetSpeedY(v)=800-(int)((v*0.621371-1)*41.6666), targetSpeedY(0)=841
    // inclineX=90, targetInclineY(v)=720-(int)(v*34.583), targetInclineY(0)=720

    @Test
    public void proformPro9000_isInstanceOfTreadmillDevice() {
        assertTrue(new ProformPro9000Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void proformPro9000_hasCorrectDisplayName() {
        assertEquals("ProForm Pro 9000 Treadmill", new ProformPro9000Device().displayName());
    }

    @Test
    public void proformPro9000_applySpeed_atEight_generatesCorrectSwipe() {
        ProformPro9000Device dev = dev(new ProformPro9000Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=4.970968; (4.970968-1)*41.6666=165.456; (int)=165; 800-165=635; fromY=841
        assertEquals("input swipe 1845 841 1845 635 200", lastCommand);
    }

    @Test
    public void proformPro9000_applyIncline_atFive_generatesCorrectSwipe() {
        ProformPro9000Device dev = dev(new ProformPro9000Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=720; y2=720-(int)(5*34.583)=720-(int)172.915=720-172=548
        assertEquals("input swipe 75 720 75 548 200", lastCommand);
    }

    // ── C1750_2020Device ──────────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1205, targetSpeedY(v)=575-(int)((v*0.621371-1)*28.91), targetSpeedY(0)=603
    // inclineX=75, targetInclineY(v)=520-(int)(v*20), targetInclineY(0)=520

    @Test
    public void c1750_2020_isInstanceOfTreadmillDevice() {
        assertTrue(new C1750_2020Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void c1750_2020_hasCorrectDisplayName() {
        assertEquals("C1750 Treadmill (2020)", new C1750_2020Device().displayName());
    }

    @Test
    public void c1750_2020_applySpeed_atEight_generatesCorrectSwipe() {
        C1750_2020Device dev = dev(new C1750_2020Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=4.970968; (4.970968-1)*28.91=114.769; (int)=114; 575-114=461; fromY=603
        assertEquals("input swipe 1205 603 1205 461 200", lastCommand);
    }

    @Test
    public void c1750_2020_applyIncline_atFive_generatesCorrectSwipe() {
        C1750_2020Device dev = dev(new C1750_2020Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=520; y2=520-(int)(100)=420
        assertEquals("input swipe 75 520 75 420 200", lastCommand);
    }

    // ── C1750_2021Device ──────────────────────────────────────────────────────
    // speed: currentThumbY, targetSpeedY(0)=640
    // incline: no currentThumbY, initial=547
    // speedX=1205, targetSpeedY(v)=620-(int)((v-1)*20.73)
    // inclineX=79, targetInclineY(v)=(int)(553-22*v)

    @Test
    public void c1750_2021_isInstanceOfTreadmillDevice() {
        assertTrue(new C1750_2021Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void c1750_2021_hasCorrectDisplayName() {
        assertEquals("C1750 Treadmill (2021)", new C1750_2021Device().displayName());
    }

    @Test
    public void c1750_2021_applySpeed_atEight_generatesCorrectSwipe() {
        C1750_2021Device dev = dev(new C1750_2021Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=640; y2=620-(int)(7*20.73)=620-(int)145.11=620-145=475
        assertEquals("input swipe 1205 640 1205 475 200", lastCommand);
    }

    @Test
    public void c1750_2021_applyIncline_atZero_generatesCorrectSwipe() {
        C1750_2021Device dev = dev(new C1750_2021Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=553 (initial); y2=(int)(553)=553
        assertEquals("input swipe 75 553 75 553 200", lastCommand);
    }

    @Test
    public void c1750_2021_applyIncline_atFive_generatesCorrectSwipe() {
        C1750_2021Device dev = dev(new C1750_2021Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 553
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=553; y2=(int)(553-110)=443
        assertEquals("input swipe 75 553 75 443 200", lastCommand);
    }

    // ── C1750MphMinus3GradeDevice ─────────────────────────────────────────────
    // both sliders have currentThumbY; speed clamped to [0.5,12] mph
    // speedX=1206, targetSpeedY(0)=603; inclineX=75, targetInclineY(0)=538

    @Test
    public void c1750MphMinus3Grade_isInstanceOfTreadmillDevice() {
        assertTrue(new C1750MphMinus3GradeDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void c1750MphMinus3Grade_hasCorrectDisplayName() {
        assertEquals("C1750 Treadmill (MPH -3% Grade)", new C1750MphMinus3GradeDevice().displayName());
    }

    @Test
    public void c1750MphMinus3Grade_applySpeed_atEight_generatesCorrectSwipe() {
        C1750MphMinus3GradeDevice dev = dev(new C1750MphMinus3GradeDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=4.970968; (4.970968-0.5)*34=152.013; (int)=152; 603-152=451; fromY=603
        assertEquals("input swipe 1205 603 1205 451 200", lastCommand);
    }

    @Test
    public void c1750MphMinus3Grade_applyIncline_atFive_generatesCorrectSwipe() {
        C1750MphMinus3GradeDevice dev = dev(new C1750MphMinus3GradeDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=538; y2=603-(int)(8*21.7222222)=603-(int)173.777=603-173=430
        assertEquals("input swipe 75 538 75 430 200", lastCommand);
    }

    // ── C1750Ntl14122Device ───────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1850, targetSpeedY(v)=787-(int)(v*43.5), targetSpeedY(0)=787
    // inclineX=70, targetInclineY(v)=787-(int)((v+3)*29), targetInclineY(0)=700

    @Test
    public void c1750Ntl14122_isInstanceOfTreadmillDevice() {
        assertTrue(new C1750Ntl14122Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void c1750Ntl14122_hasCorrectDisplayName() {
        assertEquals("C1750 Treadmill (NTL14122.2 MPH)", new C1750Ntl14122Device().displayName());
    }

    @Test
    public void c1750Ntl14122_applySpeed_atEight_generatesCorrectSwipe() {
        C1750Ntl14122Device dev = dev(new C1750Ntl14122Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=787; y2=787-(int)(8*43.5)=787-348=439
        assertEquals("input swipe 1845 787 1845 439 200", lastCommand);
    }

    @Test
    public void c1750Ntl14122_applyIncline_atFive_generatesCorrectSwipe() {
        C1750Ntl14122Device dev = dev(new C1750Ntl14122Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=700; y2=787-(int)(8*29)=787-232=555
        assertEquals("input swipe 75 700 75 555 200", lastCommand);
    }

    // ── X14iDevice ────────────────────────────────────────────────────────────
    // speed: currentThumbY, targetSpeedY(0)=838; incline: table lookup, targetInclineY(0)=783
    // dev() replaces shell executor after construction

    @Test
    public void x14i_isInstanceOfTreadmillDevice() {
        assertTrue(new X14iDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x14i_hasCorrectDisplayName() {
        assertEquals("X14i Treadmill", new X14iDevice().displayName());
    }

    @Test
    public void x14i_applySpeed_atEight_generatesCorrectSwipe() {
        X14iDevice dev = dev(new X14iDevice());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=838; y2=807-(int)(7*31)=807-217=590
        assertEquals("input swipe 1845 838 1845 590 200", lastCommand);
    }

    @Test
    public void x14i_applyIncline_atZero_generatesCorrectSwipe() {
        X14iDevice dev = dev(new X14iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // table lookup: 0<=0.0 → 783; fromY=783; y2=783
        assertEquals("input swipe 75 783 75 783 200", lastCommand);
    }

    @Test
    public void x14i_applyIncline_atTen_generatesCorrectSwipe() {
        X14iDevice dev = dev(new X14iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        // table lookup: 10.0 → 665; fromY=783; y2=665
        assertEquals("input swipe 75 783 75 665 200", lastCommand);
    }

    // ── X22iV2Device ──────────────────────────────────────────────────────────
    // no currentThumbY
    // speedX=1845, targetSpeedY(v)=(int)(838-26.136*v), initial=785
    // inclineX=75, targetInclineY(v)=(int)(742-11.9*(v+6)), initial=785

    @Test
    public void x22iV2_isInstanceOfTreadmillDevice() {
        assertTrue(new X22iV2Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x22iV2_hasCorrectDisplayName() {
        assertEquals("X22i V2 Treadmill", new X22iV2Device().displayName());
    }

    @Test
    public void x22iV2_applySpeed_atZero_generatesCorrectSwipe() {
        X22iV2Device dev = dev(new X22iV2Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=838 (initial); y2=(int)(838)=838
        assertEquals("input swipe 1845 838 1845 838 200", lastCommand);
    }

    @Test
    public void x22iV2_applySpeed_atEight_generatesCorrectSwipe() {
        X22iV2Device dev = dev(new X22iV2Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 838
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=838; y2=(int)(838-26.136*8)=(int)(838-209.088)=(int)628.912=628
        assertEquals("input swipe 1845 838 1845 628 200", lastCommand);
    }

    @Test
    public void x22iV2_applyIncline_atZero_generatesCorrectSwipe() {
        X22iV2Device dev = dev(new X22iV2Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=742 (initial); y2=(int)(742-11.9*6)=(int)(742-71.4)=(int)670.6=670
        assertEquals("input swipe 75 742 75 670 200", lastCommand);
    }

    @Test
    public void x22iV2_applyIncline_atFive_generatesCorrectSwipe() {
        X22iV2Device dev = dev(new X22iV2Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 670
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=670; y2=(int)(742-11.9*11)=(int)(742-130.9)=(int)611.1=611
        assertEquals("input swipe 75 670 75 611 200", lastCommand);
    }

    // ── X32iNtl39019Device ────────────────────────────────────────────────────
    // no currentThumbY
    // speedX=1845, targetSpeedY(v)=(int)(817.5-42.5*v*0.621371), initial=779
    // inclineX=74, targetInclineY(v)=(int)(749-11.8424*v), initial=749

    @Test
    public void x32iNtl39019_isInstanceOfTreadmillDevice() {
        assertTrue(new X32iNtl39019Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x32iNtl39019_hasCorrectDisplayName() {
        assertEquals("X32i Treadmill (NTL39019)", new X32iNtl39019Device().displayName());
    }

    @Test
    public void x32iNtl39019_applySpeed_atZero_generatesCorrectSwipe() {
        X32iNtl39019Device dev = dev(new X32iNtl39019Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev);
        // fromY=779; y2=(int)(817.5)=817
        assertEquals("input swipe 1845 779 1845 817 200", lastCommand);
    }

    @Test
    public void x32iNtl39019_applySpeed_atEight_generatesCorrectSwipe() {
        X32iNtl39019Device dev = dev(new X32iNtl39019Device());
        dev.sliderOf(SpeedSlider.class).moveTo(0.0, dev); // advance currentY to 817
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=817; y2=(int)(817.5-42.5*4.970968)=(int)(817.5-211.266)=(int)606.234=606
        assertEquals("input swipe 1845 817 1845 606 200", lastCommand);
    }

    @Test
    public void x32iNtl39019_applyIncline_atZero_generatesCorrectSwipe() {
        X32iNtl39019Device dev = dev(new X32iNtl39019Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=749 (initial); y2=(int)(749)=749
        assertEquals("input swipe 75 749 75 749 200", lastCommand);
    }

    @Test
    public void x32iNtl39019_applyIncline_atFive_generatesCorrectSwipe() {
        X32iNtl39019Device dev = dev(new X32iNtl39019Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 749
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=749; y2=(int)(749-11.8424*5)=(int)(749-59.212)=(int)689.788=689
        assertEquals("input swipe 75 749 75 689 200", lastCommand);
    }

    // ── X32iNtl39221Device ────────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=1845, targetSpeedY(v)=(int)(900.26-46.63*v*0.621371), targetSpeedY(0)=900
    // inclineX=75, targetInclineY(v)=750-(int)(v*12.05), targetInclineY(0)=750

    @Test
    public void x32iNtl39221_isInstanceOfTreadmillDevice() {
        assertTrue(new X32iNtl39221Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void x32iNtl39221_hasCorrectDisplayName() {
        assertEquals("X32i Treadmill (NTL39221)", new X32iNtl39221Device().displayName());
    }

    @Test
    public void x32iNtl39221_applySpeed_atEight_generatesCorrectSwipe() {
        X32iNtl39221Device dev = dev(new X32iNtl39221Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // mph=4.970968; 46.63*4.970968=231.899; 900.26-231.899=668.361; (int)=668; fromY=900
        assertEquals("input swipe 1845 900 1845 668 200", lastCommand);
    }

    @Test
    public void x32iNtl39221_applyIncline_atFive_generatesCorrectSwipe() {
        X32iNtl39221Device dev = dev(new X32iNtl39221Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=750; y2=750-(int)(5*12.05)=750-(int)60.25=750-60=690
        assertEquals("input swipe 75 750 75 690 200", lastCommand);
    }

    // ── Elite900Device ────────────────────────────────────────────────────────
    // both sliders have currentThumbY
    // speedX=950, targetSpeedY(v)=450-(int)(v*14.705), targetSpeedY(0)=450
    // inclineX=76, targetInclineY(v)=450-(int)(v*20.83), targetInclineY(0)=450

    @Test
    public void elite900_isInstanceOfTreadmillDevice() {
        assertTrue(new Elite900Device() instanceof GestureTreadmillDevice);
    }

    @Test
    public void elite900_hasCorrectDisplayName() {
        assertEquals("Elite 900 Treadmill", new Elite900Device().displayName());
    }

    @Test
    public void elite900_applySpeed_atEight_generatesCorrectSwipe() {
        Elite900Device dev = dev(new Elite900Device());
        dev.sliderOf(SpeedSlider.class).moveTo(8.0, dev);
        // fromY=450; y2=450-(int)(8*14.705)=450-(int)117.64=450-117=333
        assertEquals("input swipe 950 450 950 333 200", lastCommand);
    }

    @Test
    public void elite900_applyIncline_atFive_generatesCorrectSwipe() {
        Elite900Device dev = dev(new Elite900Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=450; y2=450-(int)(5*20.83)=450-(int)104.15=450-104=346
        assertEquals("input swipe 74 450 74 346 200", lastCommand);
    }

    // ── Negative incline ──────────────────────────────────────────────────────

    @Test
    public void x11i_applyIncline_atNegativeTwo_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(-2.0, dev);
        // y2=(int)(565.491 - 8.44*(-2.0))=(int)(565.491+16.88)=(int)582.371=582; y1=557
        assertEquals("input swipe 75 557 75 582 200", lastCommand);
    }

    @Test
    public void x11i_applyIncline_atNegativeFive_generatesCorrectSwipe() {
        X11iDevice dev = dev(new X11iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(-5.0, dev);
        // y2=(int)(565.491 - 8.44*(-5.0))=(int)(565.491+42.2)=(int)607.691=607; y1=557
        assertEquals("input swipe 75 557 75 607 200", lastCommand);
    }

    @Test
    public void x32i_applyIncline_atNegativeFive_generatesCorrectSwipe() {
        X32iDevice dev = dev(new X32iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(-5.0, dev);
        // y2=(int)(734.07 - 12.297*(-5.0))=(int)(734.07+61.485)=(int)795.555=795; y1=881
        assertEquals("input swipe 75 881 75 795 200", lastCommand);
    }

    // ── Speed/incline boundary values ────────────────────────────────────────

    @Test
    public void treadmill_speedGateAtExactlyZero_cachesSpeed() {
        // guard is speed() > 0; device that previously moved but is now stopped at 0.0f
        // must still cache the command (not apply it).
        X11iDevice device = dev(new X11iDevice());
        device.applyTelemetry(new SpeedTelemetry(5.0f)); // was moving
        device.applyTelemetry(new SpeedTelemetry(0.0f)); // now stopped
        DeviceController ctrl = new DeviceController(device, () -> 1_000L + CommandDispatcher.SWIPE_THROTTLE_MS + 100);
        ctrl.onPacket(QZCommandPacket.parse("8.0;-100"));
        assertNull("speed=0.0 exactly must not pass the gate", lastCommand);
    }

    @Test
    public void treadmill_queueFIFO_olderThrottledValueAppliedFirst() {
        // Two successive throttled speeds are held in a FIFO queue;
        // 9.0 drains before 10.0 when the window re-opens.
        // X11i targetSpeedY(9.0)=425; targetSpeedY(10.0)=404; fromY=447 (after 8.0)
        final long[] t = {1_000L};
        X11iDevice device = dev(new X11iDevice());
        DeviceController ctrl = new DeviceController(device, () -> t[0]);

        device.applyTelemetry(new SpeedTelemetry(5.0f));
        ctrl.onPacket(QZCommandPacket.parse("8.0;-100"));  // applied at t=1000 (speedY: 600→447)

        t[0] += 200;
        ctrl.onPacket(QZCommandPacket.parse("9.0;-100"));  // throttled → queue=[9.0]

        t[0] += 100;
        ctrl.onPacket(QZCommandPacket.parse("10.0;-100")); // throttled → queue=[9.0, 10.0]

        t[0] = 1_000L + CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        lastCommand = null;
        ctrl.onPacket(QZCommandPacket.parse("-1;-100"));   // drains 9.0 first → y:447→425
        assertEquals("input swipe 1205 447 1205 425 200", lastCommand);

        t[0] += CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        lastCommand = null;
        ctrl.onPacket(QZCommandPacket.parse("-1;-100"));   // drains 10.0 next → y:425→404
        assertEquals("input swipe 1205 425 1205 404 200", lastCommand);
    }

    // ── T95sDevice ────────────────────────────────────────────────────────────

    @Test
    public void t95s_isInstanceOfTreadmillDevice() {
        assertTrue(new T95sDevice() instanceof GestureTreadmillDevice);
    }

    @Test
    public void t95s_hasCorrectDisplayName() {
        assertEquals("T9.5s Treadmill", new T95sDevice().displayName());
    }

    // ── GestureTreadmillDevice.decodeCommands ───────────────────────────────────────

    @Test
    public void decodeCommands_twoParts_setsBothFields() {
        java.util.List<Command> cmds = new X22iDevice().decodeCommands(QZCommandPacket.parse("8.0;5.0"));
        assertEquals(2, cmds.size());
        assertEquals(8.0f, cmds.get(0).value, 0.001f);
        assertEquals(5.0f, cmds.get(1).value, 0.001f);
    }

    @Test
    public void decodeCommands_roundsToOneDecimal() {
        java.util.List<Command> cmds = new X22iDevice().decodeCommands(QZCommandPacket.parse("8.25;5.14"));
        assertEquals(2, cmds.size());
        assertEquals(8.3f, cmds.get(0).value, 0.001f);
        assertEquals(5.1f, cmds.get(1).value, 0.001f);
    }

    @Test
    public void decodeCommands_sentinelMinusOneHundred_speed_returnsInclineOnly() {
        java.util.List<Command> cmds = new X22iDevice().decodeCommands(QZCommandPacket.parse("-100;5.0"));
        assertEquals(1, cmds.size());
        assertTrue(cmds.get(0) instanceof InclineCommand);
        assertEquals(5.0f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_sentinelMinusOneHundred_incline_returnsSpeedOnly() {
        java.util.List<Command> cmds = new X22iDevice().decodeCommands(QZCommandPacket.parse("8.0;-100"));
        assertEquals(1, cmds.size());
        assertTrue(cmds.get(0) instanceof SpeedCommand);
        assertEquals(8.0f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_bothSentinels_returnsEmpty() {
        assertTrue(new X22iDevice().decodeCommands(QZCommandPacket.parse("-100;-100")).isEmpty());
    }

    @Test
    public void decodeCommands_sentinelMinusOne_speed_returnsEmpty() {
        // -1 is the QZ no-op speed flush sentinel; it must not produce a speed command.
        assertTrue(new X22iDevice().decodeCommands(QZCommandPacket.parse("-1;-100")).isEmpty());
    }

    @Test
    public void decodeCommands_onePart_returnsEmpty() {
        assertTrue(new X22iDevice().decodeCommands(QZCommandPacket.parse("8.0")).isEmpty());
    }

    @Test
    public void decodeCommands_zeroParts_returnsEmpty() {
        assertTrue(new X22iDevice().decodeCommands(QZCommandPacket.parse("")).isEmpty());
    }

    @Test
    public void decodeCommands_threeParts_returnsEmpty() {
        assertTrue(new X22iDevice().decodeCommands(QZCommandPacket.parse("1.0;2.0;3.0")).isEmpty());
    }

    // ── Belt-gate self-flush ──────────────────────────────────────────────────

    @Test
    public void beltGate_selfFlushes_whenBeltStarts() {
        // Speed cached while belt stopped; applyTelemetry(SpeedTelemetry > 0) fires it immediately —
        // no sentinel or subsequent dispatch() call needed.
        // X11i targetSpeedY(8.0) = 447; fromY = 600 (initialSpeedY)
        java.util.List<String> commands = new java.util.ArrayList<>();
        X11iDevice dev = new X11iDevice();
        dev.commandExecutor = commands::add;

        dev.applyCommand(new SpeedCommand(8.0f));  // belt stopped → cached, no swipe
        assertEquals(0, commands.size());

        dev.applyTelemetry(new SpeedTelemetry(5.0f));  // belt starts → self-flush fires
        assertEquals(1, commands.size());
        assertEquals("input swipe 1205 600 1205 447 200", commands.get(0));

        // Cache cleared — subsequent applyTelemetry does not re-fire
        dev.applyTelemetry(new SpeedTelemetry(6.0f));
        assertEquals(1, commands.size());
    }
}
