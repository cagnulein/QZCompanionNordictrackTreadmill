package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure-Java port of the discover-device.py linear calibration math. */
public final class CalibrationFit {

    private CalibrationFit() {}

    public static final int COARSE_STEP = 50;
    public static final int COARSE_MARGIN = 200;
    public static final int FINE_STEP = 25;

    public static final class Point {
        public final int y;
        public final double metric;

        public Point(int y, double metric) {
            this.y = y;
            this.metric = metric;
        }
    }

    public static final class FitResult {
        public final double origin;
        public final double scale;
        public final double r2;
        public final List<Point> points;
        public final Point rejectedOutlier;

        private FitResult(double origin, double scale, double r2,
                          List<Point> points, Point rejectedOutlier) {
            this.origin = origin;
            this.scale = scale;
            this.r2 = r2;
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            this.rejectedOutlier = rejectedOutlier;
        }

        public boolean isValid() {
            return points.size() >= 3 && Double.isFinite(origin) && Double.isFinite(scale)
                    && scale > 0.0 && r2 >= 0.0;
        }

        public boolean isWarningQuality() {
            return r2 < 0.97;
        }
    }

    public static final class TrackProfile {
        public final int inclineTrackX;
        public final int resistanceTrackX;

        private TrackProfile(int inclineTrackX, int resistanceTrackX) {
            this.inclineTrackX = inclineTrackX;
            this.resistanceTrackX = resistanceTrackX;
        }
    }

    public static TrackProfile trackProfileForWidth(int width) {
        if (width >= 1920) return new TrackProfile(57, 1845);
        if (width >= 1280) return new TrackProfile(57, 1205);
        if (width >= 1024) return new TrackProfile(56, 950);
        return new TrackProfile(55, 725);
    }

    public static int[] findActiveRange(List<Point> readings, int screenHeight) {
        if (readings.size() < 3) return null;
        int yTop = Math.max(COARSE_MARGIN, readings.get(0).y - COARSE_STEP);
        int yBottom = Math.min(screenHeight - COARSE_MARGIN,
                readings.get(readings.size() - 1).y + COARSE_STEP);
        if (yBottom - yTop < FINE_STEP * 3) return null;
        return new int[]{yTop, yBottom};
    }

    public static List<Point> filterMonotonicDescending(List<Point> raw, double tolerance) {
        List<Point> points = new ArrayList<>();
        Double previousMetric = null;
        for (Point point : raw) {
            if (previousMetric != null && point.metric > previousMetric + tolerance) {
                previousMetric = point.metric;
            } else {
                points.add(point);
                previousMetric = point.metric;
            }
        }
        return points;
    }

    public static FitResult fitLinear(List<Point> inputPoints) {
        if (inputPoints.size() < 3) {
            throw new IllegalArgumentException("At least 3 calibration points are required");
        }

        List<Point> points = new ArrayList<>(inputPoints);
        double[] fit = ols(points);
        double intercept = fit[0];
        double slope = fit[1];

        double[] residuals = residuals(points, intercept, slope);
        List<Double> sortedAbs = new ArrayList<>();
        for (double residual : residuals) sortedAbs.add(Math.abs(residual));
        Collections.sort(sortedAbs);
        double mar = sortedAbs.get(sortedAbs.size() / 2);
        double threshold = Math.max(3.0 * mar, 5.0);

        int worstIdx = 0;
        for (int i = 1; i < residuals.length; i++) {
            if (Math.abs(residuals[i]) > Math.abs(residuals[worstIdx])) worstIdx = i;
        }

        Point rejected = null;
        if (Math.abs(residuals[worstIdx]) > threshold && points.size() - 1 >= 3) {
            rejected = points.remove(worstIdx);
            fit = ols(points);
            intercept = fit[0];
            slope = fit[1];
        }

        double origin = intercept;
        double scale = -slope;
        double r2 = r2(points, intercept, slope);
        return new FitResult(origin, scale, r2, points, rejected);
    }

    private static double[] ols(List<Point> points) {
        int n = points.size();
        double sx = 0.0, sy = 0.0, sxx = 0.0, sxy = 0.0;
        for (Point p : points) {
            sx += p.metric;
            sy += p.y;
            sxx += p.metric * p.metric;
            sxy += p.metric * p.y;
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-9) {
            throw new IllegalArgumentException("Degenerate fit: metric values did not vary");
        }
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{intercept, slope};
    }

    private static double[] residuals(List<Point> points, double intercept, double slope) {
        double[] residuals = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            residuals[i] = p.y - (intercept + slope * p.metric);
        }
        return residuals;
    }

    private static double r2(List<Point> points, double intercept, double slope) {
        double ySum = 0.0;
        for (Point p : points) ySum += p.y;
        double yMean = ySum / points.size();

        double ssTot = 0.0, ssRes = 0.0;
        for (Point p : points) {
            ssTot += Math.pow(p.y - yMean, 2);
            ssRes += Math.pow(p.y - (intercept + slope * p.metric), 2);
        }
        return ssTot > 0.0 ? 1.0 - ssRes / ssTot : 1.0;
    }
}
