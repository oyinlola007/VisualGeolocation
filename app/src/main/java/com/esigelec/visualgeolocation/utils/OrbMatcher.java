package com.esigelec.visualgeolocation.utils;

import android.content.Context;
import org.opencv.core.DMatch;
import org.opencv.core.MatOfDMatch;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;

public class OrbMatcher extends BaseImageMatcher {
    public OrbMatcher(Context context) {
        super(context,
              ORB.create(),
              DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING),
              "ORB");
    }

    @Override
    protected double calculateSimilarity(MatOfDMatch matches) {
        // For ORB, we'll use a combination of match quality and ratio test
        DMatch[] matchArray = matches.toArray();
        double totalDistance = 0;
        int goodMatches = 0;
        double threshold = 50.0; // ORB typically uses a higher threshold for Hamming distance

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

        // ORB similarity score combines:
        // 1. Ratio of good matches to total matches
        // 2. Inverse of average distance (lower distance = higher similarity)
        // 3. Square root to give more weight to higher match counts
        double matchRatio = goodMatches / (double)matchArray.length;
        return Math.sqrt(matchRatio) * (1.0 / (1.0 + averageDistance));
    }
} 