package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.Ntex71021Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.ProformCarbonC10Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.ProformCarbonE7Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.ProformStudioBikePro22Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S15iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iNtex02117Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iNtex02121Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S27iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.Se9iEllipticalDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.Tdf10Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.Tdf10InclinationDevice;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.CommandDispatcher;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.GearSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies that GestureBikeDevice subclasses generate correct "input swipe" commands
 * for incline and resistance gestures.
 *
 * Tests install a capturing CommandExecutor so no Android or ADB calls happen.
 */
public class BikeDeviceTest {

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

    // ── S22iDevice ────────────────────────────────────────────────────────────
    // Single linear fit: targetThumbY(v) = (int)(622 - 18.57*v) for all v. initialY=622.
    // Calibration 2026-04-19 (positive) + 2026-04-22 (negative); intercept=622.

    @Test
    public void s22i_applyIncline_atZero_generatesCorrectSwipe() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // y2 = (int)(622 - 10*0) = 622; y1 = 622 (initial) — no travel, swipeY = toY
        assertEquals("input swipe 75 622 75 622 200", lastCommand);
    }

    @Test
    public void s22i_applyIncline_atNegativeTen_generatesCorrectSwipe() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(-10.0, dev);
        // unified formula: toY=(int)(622-18.57*(-10))=(int)(807.7)=807; h=0 → dispatch=807
        assertEquals("input swipe 75 622 75 807 200", lastCommand);
    }

    @Test
    public void s22i_applyIncline_atTen_generatesCorrectSwipe() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        // toY=(int)(622-18.57*10)=436; h=0 → dispatch=436
        assertEquals("input swipe 75 622 75 436 200", lastCommand);
    }

    @Test
    public void s22i_applyIncline_atTwenty_isAtTopOfRange() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(20.0, dev);
        // toY=(int)(622-18.57*20)=250; h=0 → dispatch=250
        assertEquals("input swipe 75 622 75 250 200", lastCommand);
    }

    @Test
    public void s22i_applyIncline_updatesCurrentY() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev); // toY=436; h=0 → dispatch=436; thumbY=436
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);  // fromY=436; toY=529; h=0 → dispatch=529; thumbY=529
        assertEquals("input swipe 75 436 75 529 200", lastCommand);
    }

    @Test
    public void s22i_isInstanceOfBikeDevice() {
        assertTrue(new S22iDevice() instanceof GestureBikeDevice);
    }

    @Test
    public void s22i_hasCorrectDisplayName() {
        assertEquals("S22i Bike", new S22iDevice().displayName());
    }

    // ── S22iNoAdbDevice ───────────────────────────────────────────────────────
    // Calls S22iDevice(0, 0) → hystLong=0, hystShort=0 → dispatchY = targetThumbY exactly.
    // S22iNoAdbDevice.swipe() routes to AccessibilityService and cannot be captured in a
    // unit test, so h=0 behaviour is verified via an anonymous S22iDevice(0, 0) subclass
    // that exercises the same Slider code path.

    @Test
    public void s22iNoAdb_applyIncline_atTen_ascending_noHysteresis() {
        S22iDevice dev = dev(new S22iDevice(0, 0) {});
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        // h=0 → dispatchY = targetThumbY(10) = (int)(622 - 18.57*10) = 436 (no overshoot)
        assertEquals("input swipe 75 622 75 436 200", lastCommand);
    }

    @Test
    public void s22iNoAdb_applyIncline_atTen_descending_noHysteresis() {
        S22iDevice dev = dev(new S22iDevice(0, 0) {});
        dev.sliderOf(InclineSlider.class).moveTo(12.0, dev); // toY=(int)(622-18.57*12)=399; h=0 → dispatch=399; thumbY=399
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev); // fromY=399; toY=436; h=0 → dispatch=436 (no overshoot)
        assertEquals("input swipe 75 399 75 436 200", lastCommand);
    }

    // ── S22iNtex02117Device ───────────────────────────────────────────────────
    // Same formula as S22i; verify via parent constructor.

    @Test
    public void s22iNtex02117_applyIncline_atZero_capturedByExecutor() {
        S22iDevice base = dev(new S22iDevice());
        base.sliderOf(InclineSlider.class).moveTo(0.0, base);
        // y2 = (int)(622 - 10*0) = 622; y1 = 622 (initial) — same formula as S22iDevice
        assertEquals("input swipe 75 622 75 622 200", lastCommand);
    }

    @Test
    public void s22iNtex02117_isInstanceOfS22iDevice() {
        assertTrue(new S22iNtex02117Device() instanceof S22iDevice);
    }

    @Test
    public void s22iNtex02117_isInstanceOfBikeDevice() {
        assertTrue(new S22iNtex02117Device() instanceof GestureBikeDevice);
    }

    // ── S22iNtex02121Device ───────────────────────────────────────────────────
    // incline: currentThumbY; targetInclineY(v)=800-(int)((v+10)*19), targetInclineY(0)=610

    @Test
    public void s22iNtex02121_isInstanceOfBikeDevice() {
        assertTrue(new S22iNtex02121Device() instanceof GestureBikeDevice);
    }

    @Test
    public void s22iNtex02121_hasCorrectDisplayName() {
        assertEquals("S22i Bike (NTEX02121.5)", new S22iNtex02121Device().displayName());
    }

    @Test
    public void s22iNtex02121_applyIncline_atFive_generatesCorrectSwipe() {
        S22iNtex02121Device dev = dev(new S22iNtex02121Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=610; y2=800-(int)(15*19)=800-285=515
        assertEquals("input swipe 75 610 75 515 200", lastCommand);
    }

    // ── S15iDevice ────────────────────────────────────────────────────────────
    // incline: currentThumbY, targetInclineY(v)=616-(int)(v*17.65), targetInclineY(0)=616
    // resistance: currentThumbY, targetResistanceY(v)=790-(int)(v*23.16), targetResistanceY(0)=790

    @Test
    public void s15i_isInstanceOfBikeDevice() {
        assertTrue(new S15iDevice() instanceof GestureBikeDevice);
    }

    @Test
    public void s15i_hasCorrectDisplayName() {
        assertEquals("S15i Bike", new S15iDevice().displayName());
    }

    @Test
    public void s15i_applyIncline_atFive_generatesCorrectSwipe() {
        S15iDevice dev = dev(new S15iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=616; y2=616-(int)(5*17.65)=616-(int)88.25=616-88=528
        assertEquals("input swipe 75 616 75 528 200", lastCommand);
    }

    @Test
    public void s15i_applyResistance_atFive_generatesCorrectSwipe() {
        S15iDevice dev = dev(new S15iDevice());
        dev.sliderOf(GearSlider.class).moveTo(5.0, dev);
        // fromY=790; y2=790-(int)(5*23.16)=790-(int)115.8=790-115=675
        assertEquals("input swipe 1845 790 1845 675 200", lastCommand);
    }

    // ── Tdf10Device ───────────────────────────────────────────────────────────

    @Test
    public void tdf10_isInstanceOfBikeDevice() {
        assertTrue(new Tdf10Device() instanceof GestureBikeDevice);
    }

    @Test
    public void tdf10_applyIncline_atZero_generatesCorrectSwipe() {
        Tdf10Device dev = dev(new Tdf10Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // targetInclineY(0) = (int)(619.91 - 0) = 619; initialY = 604 (constructor)
        assertEquals("input swipe 1205 604 1205 619 200", lastCommand);
    }

    // ── ProformStudioBikePro22Device ──────────────────────────────────────────

    @Test
    public void proformStudioBikePro22_isInstanceOfBikeDevice() {
        assertTrue(new ProformStudioBikePro22Device() instanceof GestureBikeDevice);
    }

    @Test
    public void proformStudioBikePro22_applyIncline_atZero_generatesCorrectSwipe() {
        ProformStudioBikePro22Device dev = dev(new ProformStudioBikePro22Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // targetInclineY(0) = (int)(826.25 - 0) = 826; initialY = 805 (constructor)
        assertEquals("input swipe 1845 805 1845 826 200", lastCommand);
    }

    // ── Se9iEllipticalDevice ──────────────────────────────────────────────────

    @Test
    public void se9iElliptical_isInstanceOfBikeDevice() {
        assertTrue(new Se9iEllipticalDevice() instanceof GestureBikeDevice);
    }

    // ── S22iDevice resistance slider (x=1845, calibrated) ────────────────────
    // Two-point calibration: resistance=1 → Y=802, resistance=5 → Y=697.
    // targetResistanceY(v) = (int)(802.0 - 26.25 * (v-1)); initialY = 802

    @Test
    public void s22i_applyResistance_atLevel1_isAtTopOfRange() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(1.0, dev);
        // targetResistanceY(1) = (int)(802 - 0) = 802; initialY = 802
        assertEquals("input swipe 1845 802 1845 802 200", lastCommand);
    }

    @Test
    public void s22i_applyResistance_atLevel24_isAtBottomOfRange() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(24.0, dev);
        // targetResistanceY(24) = (int)(802 - 26.25*23) = 198; initialY = 802
        assertEquals("input swipe 1845 802 1845 198 200", lastCommand);
    }

    @Test
    public void s22i_applyResistance_atLevel10_generatesCorrectSwipe() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(10.0, dev);
        // targetResistanceY(10) = (int)(802 - 26.25*9) = 565; initialY = 802
        assertEquals("input swipe 1845 802 1845 565 200", lastCommand);
    }

    // ── Ntex71021Device (no QZTelemetryUnicastingService calls, pure formula) ────────────────────
    // targetInclineY(v) = (int)(493 - 13.57 * v); initialY = 493

    @Test
    public void ntex71021_applyIncline_atZero_generatesCorrectSwipe() {
        Ntex71021Device dev = dev(new Ntex71021Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // targetInclineY(0) = (int)(493 - 0) = 493; initialY = 493
        assertEquals("input swipe 950 493 950 493 200", lastCommand);
    }

    @Test
    public void ntex71021_applyIncline_atTen_generatesCorrectSwipe() {
        Ntex71021Device dev = dev(new Ntex71021Device());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        // targetInclineY(10) = (int)(493 - 135.7) = (int)357.3 = 357; initialY = 493
        assertEquals("input swipe 950 493 950 357 200", lastCommand);
    }

    @Test
    public void ntex71021_applyIncline_updatesCurrentY() {
        Ntex71021Device dev = dev(new Ntex71021Device());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev); // y2=357; currentY becomes 357
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);  // y2=(int)(493 - 67.85) = (int)425.15 = 425; y1=357
        assertEquals("input swipe 950 357 950 425 200", lastCommand);
    }

    @Test
    public void ntex71021_isMonotonicallyDecreasing_forIncline() {
        Ntex71021Device dev = dev(new Ntex71021Device());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        int y0 = Integer.parseInt(lastCommand.split(" ")[5]);
        Ntex71021Device dev2 = dev(new Ntex71021Device());
        dev2.sliderOf(InclineSlider.class).moveTo(20.0, dev2);
        int y20 = Integer.parseInt(lastCommand.split(" ")[5]);
        assertTrue("Y at inclination=20 should be less than Y at inclination=0", y20 < y0);
    }

    // ── S27iDevice ────────────────────────────────────────────────────────────
    // incline: currentThumbY; targetInclineY(v)=803-(int)((v+10)*555/30), targetInclineY(0)=618
    // resistance: currentThumbY; targetResistanceY(v)=803-(int)((v-1)*555/23), targetResistanceY(0)=827

    @Test
    public void s27i_isInstanceOfBikeDevice() {
        assertTrue(new S27iDevice() instanceof GestureBikeDevice);
    }

    @Test
    public void s27i_hasCorrectDisplayName() {
        assertEquals("S27i Bike", new S27iDevice().displayName());
    }

    @Test
    public void s27i_applyIncline_atFive_generatesCorrectSwipe() {
        S27iDevice dev = dev(new S27iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=618; y2=803-(int)(15*18.5)=803-277=526
        assertEquals("input swipe 75 618 75 526 200", lastCommand);
    }

    @Test
    public void s27i_applyResistance_atFive_generatesCorrectSwipe() {
        S27iDevice dev = dev(new S27iDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(5.0, dev);
        // fromY=827; y2=803-(int)(4*555/23)=803-(int)96.52=803-96=707
        assertEquals("input swipe 1845 827 1845 707 200", lastCommand);
    }

    // ── ProformCarbonC10Device ────────────────────────────────────────────────
    // incline slot (no resistance slot); targetThumbY(v)=632-(int)(v*18.45), currentThumbY→resistance()
    // snapshot.resistance()=0 in tests → fromY=targetThumbY(0)=632

    @Test
    public void proformCarbonC10_isInstanceOfBikeDevice() {
        assertTrue(new ProformCarbonC10Device() instanceof GestureBikeDevice);
    }

    @Test
    public void proformCarbonC10_hasCorrectDisplayName() {
        assertEquals("ProForm Carbon C10 Bike", new ProformCarbonC10Device().displayName());
    }

    @Test
    public void proformCarbonC10_applyIncline_atFive_generatesCorrectSwipe() {
        ProformCarbonC10Device dev = dev(new ProformCarbonC10Device());
        dev.sliderOf(ResistanceSlider.class).moveTo(5.0, dev);
        // fromY=632; y2=632-(int)(5*18.45)=632-(int)92.25=632-92=540
        assertEquals("input swipe 1205 632 1205 540 200", lastCommand);
    }

    // ── ProformCarbonE7Device ─────────────────────────────────────────────────
    // incline: currentThumbY; targetInclineY(v)=440-(int)(v*11), targetInclineY(0)=440
    // resistance: currentThumbY; targetResistanceY(v)=440-(int)(v*9.16), targetResistanceY(0)=440

    @Test
    public void proformCarbonE7_isInstanceOfBikeDevice() {
        assertTrue(new ProformCarbonE7Device() instanceof GestureBikeDevice);
    }

    @Test
    public void proformCarbonE7_hasCorrectDisplayName() {
        assertEquals("ProForm Carbon E7 Bike", new ProformCarbonE7Device().displayName());
    }

    @Test
    public void proformCarbonE7_applyIncline_atFive_generatesCorrectSwipe() {
        ProformCarbonE7Device dev = dev(new ProformCarbonE7Device());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=440; y2=440-(int)(55)=385
        assertEquals("input swipe 74 440 74 385 200", lastCommand);
    }

    @Test
    public void proformCarbonE7_applyResistance_atFive_generatesCorrectSwipe() {
        ProformCarbonE7Device dev = dev(new ProformCarbonE7Device());
        dev.sliderOf(ResistanceSlider.class).moveTo(5.0, dev);
        // fromY=440; y2=440-(int)(5*9.16)=440-(int)45.8=440-45=395
        assertEquals("input swipe 950 440 950 395 200", lastCommand);
    }

    // ── Se9iEllipticalDevice — formula tests ──────────────────────────────────
    // incline: currentThumbY; targetInclineY(v)=858-(int)(v*650/20), targetInclineY(0)=858
    // resistance: currentThumbY; targetResistanceY(v)=858-(int)((v-1)*650/23), targetResistanceY(0)=886

    @Test
    public void se9iElliptical_hasCorrectDisplayName() {
        assertEquals("SE9i Elliptical", new Se9iEllipticalDevice().displayName());
    }

    @Test
    public void se9iElliptical_applyIncline_atFive_generatesCorrectSwipe() {
        Se9iEllipticalDevice dev = dev(new Se9iEllipticalDevice());
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=858; y2=858-(int)(5*32.5)=858-162=696
        assertEquals("input swipe 75 858 75 696 200", lastCommand);
    }

    @Test
    public void se9iElliptical_applyResistance_atFive_generatesCorrectSwipe() {
        Se9iEllipticalDevice dev = dev(new Se9iEllipticalDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(5.0, dev);
        // fromY=886; y2=858-(int)(4*650/23)=858-(int)113.04=858-113=745
        assertEquals("input swipe 1845 886 1845 745 200", lastCommand);
    }

    // ── applyCommand edge cases ────────────────────────────────────────────────

    @Test
    public void bikeDevice_nullResistanceSlider_resistanceCommandIsIgnored() {
        // ProformCarbonC10 has resistance slider = null (incline-only bike).
        // A 1-part message produces a ResistanceCommand but applyResistance is a no-op
        // because the resistance Slider is null.
        ProformCarbonC10Device dev = dev(new ProformCarbonC10Device());
        DeviceController ctrl = new DeviceController(dev, () -> 1000L);
        ctrl.onPacket(QZCommandPacket.parse("10.0"));
        assertNull("null resistance slider must not generate a swipe", lastCommand);
    }

    @Test
    public void bikeDevice_queueFIFO_olderThrottledValueAppliedFirst() {
        // Two resistance commands throttled back-to-back are held in a FIFO queue;
        // 11.0 drains before 12.0 when the window re-opens.
        // S15i resistanceY: 10→559, 11→536, 12→513
        final long[] t = {1_000L};
        S15iDevice device = dev(new S15iDevice());
        DeviceController ctrl = new DeviceController(device, () -> t[0]);

        ctrl.onPacket(QZCommandPacket.parse("10.0"));  // applied at t=1000 (resistanceY: 790→559)

        t[0] += 200;
        ctrl.onPacket(QZCommandPacket.parse("11.0"));  // throttled → queue=[11.0]

        t[0] += 100;
        ctrl.onPacket(QZCommandPacket.parse("12.0"));  // throttled → queue=[11.0, 12.0]

        t[0] = 1000 + CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        lastCommand = null;
        ctrl.onPacket(QZCommandPacket.parse("-1"));    // drains 11.0 first
        // S15i uses resistanceLive slider — fromY is always targetThumbY(0)=790 when no live metric
        assertEquals("input swipe 1845 790 1845 536 200", lastCommand);

        t[0] += CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        lastCommand = null;
        ctrl.onPacket(QZCommandPacket.parse("-1"));    // drains 12.0 next
        assertEquals("input swipe 1845 790 1845 513 200", lastCommand);
    }

    // ── Negative incline tests ─────────────────────────────────────────────────

    @Test
    public void s22i_applyIncline_atNegativeFive_generatesCorrectSwipe() {
        S22iDevice dev = dev(new S22iDevice());
        dev.sliderOf(InclineSlider.class).moveTo(-5.0, dev);
        // toY=(int)(622-18.57*(-5))=(int)(714.85)=714; h=0 → dispatch=714
        assertEquals("input swipe 75 622 75 714 200", lastCommand);
    }

    // ── Tdf10InclinationDevice ────────────────────────────────────────────────
    // no currentThumbY; inclineX=74, targetInclineY(v)=(int)(-12.499*v+482.2), initial=482

    @Test
    public void tdf10Inclination_isInstanceOfBikeDevice() {
        assertTrue(new Tdf10InclinationDevice() instanceof GestureBikeDevice);
    }

    @Test
    public void tdf10Inclination_hasCorrectDisplayName() {
        assertEquals("TDF10 Bike (Inclination)", new Tdf10InclinationDevice().displayName());
    }

    @Test
    public void tdf10Inclination_applyIncline_atZero_generatesCorrectSwipe() {
        Tdf10InclinationDevice dev = dev(new Tdf10InclinationDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        // fromY=482; y2=(int)(482.2)=482
        assertEquals("input swipe 75 482 75 482 200", lastCommand);
    }

    @Test
    public void tdf10Inclination_applyIncline_atFive_generatesCorrectSwipe() {
        Tdf10InclinationDevice dev = dev(new Tdf10InclinationDevice());
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev); // advance currentY to 482
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);
        // fromY=482; y2=(int)(-12.499*5+482.2)=(int)(419.705)=419
        assertEquals("input swipe 75 482 75 419 200", lastCommand);
    }

    // ── GestureBikeDevice.decodeCommands ─────────────────────────────────────────────

    @Test
    public void decodeCommands_onePart_setsResistanceLvl() {
        java.util.List<Command> cmds = new S22iDevice().decodeCommands(QZCommandPacket.parse("8.0"));
        assertEquals(1, cmds.size());
        assertTrue(cmds.get(0) instanceof ResistanceCommand);
        assertEquals(8.0f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_twoParts_setsInclinePct() {
        java.util.List<Command> cmds = new S22iDevice().decodeCommands(QZCommandPacket.parse("5.0;unused"));
        assertEquals(1, cmds.size());
        assertTrue(cmds.get(0) instanceof InclineCommand);
        assertEquals(5.0f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_roundsToOneDecimal() {
        java.util.List<Command> cmds = new S22iDevice().decodeCommands(QZCommandPacket.parse("8.25"));
        assertEquals(1, cmds.size());
        assertEquals(8.3f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_sentinelMinusOne_resistance_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("-1")).isEmpty());
    }

    @Test
    public void decodeCommands_sentinelMinusOneHundred_resistance_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("-100")).isEmpty());
    }

    @Test
    public void decodeCommands_endOfRideSentinel_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("-1;-100")).isEmpty());
    }

    @Test
    public void decodeCommands_negativeOnePercent_incline_parsesCorrectly() {
        // "-1.0;0" is a legitimate Zwift grade, not the sentinel; must not be swallowed.
        java.util.List<Command> cmds = new S22iDevice().decodeCommands(QZCommandPacket.parse("-1.0;0"));
        assertEquals(1, cmds.size());
        assertEquals(-1.0f, cmds.get(0).value, 0.001f);
    }

    @Test
    public void decodeCommands_heartbeat_incline_returnsEmpty() {
        // "-100;N" is QZ's "no grade" heartbeat (Zwift paused/loading).
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("-100;16")).isEmpty());
    }

    @Test
    public void decodeCommands_unparseable_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("abc")).isEmpty());
    }

    @Test
    public void decodeCommands_zeroParts_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("")).isEmpty());
    }

    @Test
    public void decodeCommands_threeParts_returnsEmpty() {
        assertTrue(new S22iDevice().decodeCommands(QZCommandPacket.parse("1.0;2.0;3.0")).isEmpty());
    }
}
