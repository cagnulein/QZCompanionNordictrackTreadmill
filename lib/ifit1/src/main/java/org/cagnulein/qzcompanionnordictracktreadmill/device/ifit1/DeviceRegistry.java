package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.*;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.*;


import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry of all supported fitness devices.
 * Owns the DeviceId enum and the map from id to Device instance.
 * Neither CommandListenerService nor MainActivity need to know about
 * concrete device classes — they refer only to DeviceId values.
 */
public class DeviceRegistry {

    public enum Category { BIKE, TREADMILL, OTHER }

    public enum DeviceId {
        // Bikes
        s15i(Category.BIKE),
        s22i(Category.BIKE),
        s22i_NTEX02121_5(Category.BIKE),
        s22i_NTEX02117_2(Category.BIKE),
        s27i(Category.BIKE),
        NTEX71021(Category.BIKE),
        tdf10(Category.BIKE),
        tdf10_inclination(Category.BIKE),
        proform_studio_bike_pro22(Category.BIKE),
        proform_carbon_e7(Category.BIKE),
        proform_carbon_c10(Category.BIKE),
        custom_calibrated(Category.BIKE),
        // Treadmills
        x9i(Category.TREADMILL),
        x11i(Category.TREADMILL),
        x14i(Category.TREADMILL),
        x22i(Category.TREADMILL),
        x22i_v2(Category.TREADMILL),
        x32i(Category.TREADMILL),
        x32i_NTL39019(Category.TREADMILL),
        x32i_NTL39221(Category.TREADMILL),
        c1750(Category.TREADMILL),
        c1750_2020(Category.TREADMILL),
        c1750_2020_kph(Category.TREADMILL),
        c1750_mph_minus3grade(Category.TREADMILL),
        c1750_NTL14122_2_MPH(Category.TREADMILL),
        c1750_2021(Category.TREADMILL),
        t65s(Category.TREADMILL),
        t75s(Category.TREADMILL),
        t85s(Category.TREADMILL),
        t95s(Category.TREADMILL),
        s40(Category.TREADMILL),
        nordictrack_2450(Category.TREADMILL),
        nordictrack_2950(Category.TREADMILL),
        nordictrack_2950_maxspeed22(Category.TREADMILL),
        proform_2000(Category.TREADMILL),
        proform_carbon_t14(Category.TREADMILL),
        proform_pro_9000(Category.TREADMILL),
        proform_pro_2000(Category.TREADMILL),
        elite900(Category.TREADMILL),
        elite1000(Category.TREADMILL),
        exp7i(Category.TREADMILL),
        grand_tour_pro(Category.TREADMILL),
        // Other
        se9i_elliptical(Category.OTHER),
        other(Category.OTHER);

        public final Category category;
        DeviceId(Category category) { this.category = category; }
    }

    private static final Map<DeviceId, Device> DEVICES;
    static {
        Map<DeviceId, Device> m = new EnumMap<>(DeviceId.class);
        m.put(DeviceId.x11i,                        new X11iDevice());
        m.put(DeviceId.nordictrack_2950,             new Nordictrack2950Device());
        m.put(DeviceId.other,                        new OtherDevice());
        m.put(DeviceId.proform_2000,                 new Proform2000Device());
        m.put(DeviceId.s22i,                         new S22iDevice());
        m.put(DeviceId.tdf10,                        new Tdf10Device());
        m.put(DeviceId.t85s,                         new T85sDevice());
        m.put(DeviceId.s40,                          new S40Device());
        m.put(DeviceId.exp7i,                        new Exp7iDevice());
        m.put(DeviceId.x32i,                         new X32iDevice());
        m.put(DeviceId.c1750,                        new C1750Device());
        m.put(DeviceId.t65s,                         new T65sDevice());
        m.put(DeviceId.nordictrack_2950_maxspeed22,  new Nordictrack2950MaxSpeed22Device());
        m.put(DeviceId.t75s,                         new T75sDevice());
        m.put(DeviceId.grand_tour_pro,               new GrandTourProDevice());
        m.put(DeviceId.proform_studio_bike_pro22,    new ProformStudioBikePro22Device());
        m.put(DeviceId.x32i_NTL39019,               new X32iNtl39019Device());
        m.put(DeviceId.x22i,                         new X22iDevice());
        m.put(DeviceId.NTEX71021,                    new Ntex71021Device());
        m.put(DeviceId.c1750_2021,                   new C1750_2021Device());
        m.put(DeviceId.s22i_NTEX02121_5,            new S22iNtex02121Device());
        m.put(DeviceId.s22i_NTEX02117_2,            new S22iNtex02117Device());
        m.put(DeviceId.x32i_NTL39221,               new X32iNtl39221Device());
        m.put(DeviceId.c1750_2020,                   new C1750_2020Device());
        m.put(DeviceId.elite1000,                    new Elite1000Device());
        m.put(DeviceId.x14i,                         new X14iDevice());
        m.put(DeviceId.x9i,                          new X9iDevice());
        m.put(DeviceId.nordictrack_2450,             new Nordictrack2450Device());
        m.put(DeviceId.c1750_2020_kph,              new C1750_2020KphDevice());
        m.put(DeviceId.tdf10_inclination,            new Tdf10InclinationDevice());
        m.put(DeviceId.proform_carbon_t14,           new ProformCarbonT14Device());
        m.put(DeviceId.x22i_v2,                      new X22iV2Device());
        m.put(DeviceId.s15i,                         new S15iDevice());
        m.put(DeviceId.proform_pro_9000,             new ProformPro9000Device());
        m.put(DeviceId.proform_carbon_e7,            new ProformCarbonE7Device());
        m.put(DeviceId.t95s,                         new T95sDevice());
        m.put(DeviceId.proform_carbon_c10,           new ProformCarbonC10Device());
        m.put(DeviceId.elite900,                     new Elite900Device());
        m.put(DeviceId.c1750_mph_minus3grade,        new C1750MphMinus3GradeDevice());
        m.put(DeviceId.s27i,                         new S27iDevice());
        m.put(DeviceId.c1750_NTL14122_2_MPH,        new C1750Ntl14122Device());
        m.put(DeviceId.proform_pro_2000,             new ProformPro2000Device());
        m.put(DeviceId.se9i_elliptical,              new Se9iEllipticalDevice());
        m.put(DeviceId.custom_calibrated,            new CalibratedBikeDevice());
        DEVICES = Collections.unmodifiableMap(m);
    }

    public static Device forId(DeviceId id) {
        if (id == DeviceId.custom_calibrated) return new CalibratedBikeDevice();
        Device device = DEVICES.get(id);
        if (device == null) throw new IllegalArgumentException("No device registered for id: " + id);
        return device;
    }
}
