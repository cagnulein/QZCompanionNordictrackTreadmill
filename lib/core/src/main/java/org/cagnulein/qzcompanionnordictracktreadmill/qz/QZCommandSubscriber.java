package org.cagnulein.qzcompanionnordictracktreadmill.qz;

public interface QZCommandSubscriber {
    void onPacket(QZCommandPacket packet);
}
