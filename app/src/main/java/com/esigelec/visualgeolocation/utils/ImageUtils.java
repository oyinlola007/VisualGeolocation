package com.esigelec.visualgeolocation.utils;

import android.content.Context;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for image operations
 */
public class ImageUtils {
    private static final String TAG = "ImageUtils";

    /**
     * Get GPS coordinates from image EXIF data if available
     * 
     * @param context The application context
     * @param imageUri URI of the image
     * @return double array with [latitude, longitude] or null if not available
     */
    public static double[] getImageCoordinates(Context context, Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                return null;
            }
            
            ExifInterface exif = new ExifInterface(inputStream);
            
            String latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            String latitudeStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String longitudeStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            
            if (latitudeRef == null || longitudeRef == null || latitudeStr == null || longitudeStr == null) {
                inputStream.close();
                return null;
            }
            
            double latitude = parseExifCoordinate(latitudeStr);
            double longitude = parseExifCoordinate(longitudeStr);
            
            // If southern hemisphere or western, the coordinate is negative
            if (latitudeRef.equals("S")) {
                latitude = -latitude;
            }
            if (longitudeRef.equals("W")) {
                longitude = -longitude;
            }
            
            inputStream.close();
            
            return new double[] {latitude, longitude};
        } catch (IOException e) {
            Log.e(TAG, "Error extracting coordinates", e);
            return null;
        }
    }
    
    /**
     * Parse EXIF GPS coordinate format (dd/1,mm/1,ss/1) to decimal degrees
     * 
     * @param exifCoordinate The EXIF coordinate string
     * @return Decimal degrees value
     */
    private static double parseExifCoordinate(String exifCoordinate) {
        try {
            String[] components = exifCoordinate.split(",");
            if (components.length != 3) {
                return 0;
            }
            
            String[] degreeComponents = components[0].split("/");
            String[] minuteComponents = components[1].split("/");
            String[] secondComponents = components[2].split("/");
            
            double degrees = Double.parseDouble(degreeComponents[0]) / Double.parseDouble(degreeComponents[1]);
            double minutes = Double.parseDouble(minuteComponents[0]) / Double.parseDouble(minuteComponents[1]);
            double seconds = Double.parseDouble(secondComponents[0]) / Double.parseDouble(secondComponents[1]);
            
            return degrees + (minutes / 60.0) + (seconds / 3600.0);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing EXIF coordinate: " + exifCoordinate, e);
            return 0;
        }
    }
} 