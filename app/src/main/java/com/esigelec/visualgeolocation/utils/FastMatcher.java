package com.esigelec.visualgeolocation.utils;

import android.content.Context;
import org.opencv.core.DMatch;
import org.opencv.core.MatOfDMatch;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.features2d.Feature2D;

public class FastMatcher extends BaseImageMatcher {
    public FastMatcher(Context context) {
        super(context,
              ORB.create(), // Use ORB as both detector and descriptor
              DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING),
              "FAST");
    }

    @Override
    protected double calculateSimilarity(MatOfDMatch matches) {
        // For FAST/ORB, we'll use a combination of match quality and ratio test
        DMatch[] matchArray = matches.toArray();
        double totalDistance = 0;
        int goodMatches = 0;
        double threshold = 50.0; // FAST/ORB typically uses a higher threshold

        for (DMatch match : matchArray) {
            if (match.distance < threshold) {
                goodMatches++;
                totalDistance += match.distance;
            }
        }

        if (goodMatches == 0) {
            return 0.0;
        }

        double averageDistance = totalDistance / goodMatches;

        // Similarity score combines:
        // 1. Ratio of good matches to total matches
        // 2. Inverse of average distance (lower distance = higher similarity)
        // 3. Square root to give more weight to higher match counts
        double matchRatio = goodMatches / (double)matchArray.length;
        return Math.sqrt(matchRatio) * (1.0 / (1.0 + averageDistance));
    }
} 