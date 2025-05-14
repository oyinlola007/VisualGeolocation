package com.esigelec.visualgeolocation.utils;

import android.location.Location;

/**
 * Utility class for location-related operations
 */
public class LocationUtils {

    private static final double EARTH_RADIUS = 6371000; // Earth radius in meters

    /**
     * Calculate distance between two coordinates using the Haversine formula
     * 
     * @param lat1 Latitude of first point in degrees
     * @param lng1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lng2 Longitude of second point in degrees
     * @return Distance in meters
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // If either location is all zeros, return 0
        if ((lat1 == 0 && lng1 == 0) || (lat2 == 0 && lng2 == 0)) {
            return 0;
        }
        
        // Convert degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lng1Rad = Math.toRadians(lng1);
        double lat2Rad = Math.toRadians(lat2);
        double lng2Rad = Math.toRadians(lng2);
        
        // Haversine formula
        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c; // Distance in meters
    }
    
    /**
     * Calculate bearing between two coordinates
     *
     * @param lat1 Latitude of first point in degrees
     * @param lng1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lng2 Longitude of second point in degrees
     * @return Bearing in degrees (0-360)
     */
    public static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        // Convert degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lng1Rad = Math.toRadians(lng1);
        double lat2Rad = Math.toRadians(lat2);
        double lng2Rad = Math.toRadians(lng2);
        
        double y = Math.sin(lng2Rad - lng1Rad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(lng2Rad - lng1Rad);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        
        // Normalize to 0-360
        return (bearing + 360) % 360;
    }
} 