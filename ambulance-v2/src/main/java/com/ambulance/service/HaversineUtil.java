package com.ambulance.service;

public class HaversineUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double BASE_SPEED_KMH = 60.0;

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** ETA in minutes = distance / (baseSpeed * trafficFactor) * 60 */
    public static int eta(double distanceKm) {
        double trafficFactor = 0.6 + Math.random() * 0.6; // 0.6–1.2 random traffic
        double hours = distanceKm / (BASE_SPEED_KMH * trafficFactor);
        return Math.max(1, (int) Math.ceil(hours * 60));
    }

    /** Priority weight: HIGH=0.5, MEDIUM=1.0, LOW=1.5 (lower = faster dispatch) */
    public static double priorityWeight(String priority) {
        if (priority == null) return 1.0;
        return switch (priority.toUpperCase()) {
            case "HIGH"   -> 0.5;
            case "LOW"    -> 1.5;
            default       -> 1.0;
        };
    }

    /** Dispatch score: lower is better */
    public static double score(double distanceKm, String priority) {
        return distanceKm * priorityWeight(priority);
    }
}
