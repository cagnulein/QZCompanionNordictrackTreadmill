package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;

public class Se9iEllipticalDevice extends GestureBikeDevice {

    public Se9iEllipticalDevice() {
        // Screen: assumed 1920px, but both trackX values deviate from APK-expected:
        //   incline trackX=57 (expected≈75, −18px), resistance trackX=1857 (+11.5px).
        // Asymmetric offsets suggest non-standard slider margins on this model.
        super(
            InclineSlider.live(   ScreenProfile.W1920.leftTrackX,  858, v -> 858 - (int)(v       * (858.0 - 208.0) / 20.0)),
            ResistanceSlider.live(ScreenProfile.W1920.rightTrackX, 858, v -> 858 - (int)((v - 1) * (858.0 - 208.0) / 23.0))
        );
    }

    @Override public String displayName() { return "SE9i Elliptical"; }
}
