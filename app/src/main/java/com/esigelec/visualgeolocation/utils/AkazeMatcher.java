package com.esigelec.visualgeolocation.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.Feature2D;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AkazeMatcher extends BaseImageMatcher {
    private static final String TAG = "AkazeMatcher";
    private static final Size STANDARD_SIZE = new Size(500, 500);
    private static final float RATIO_THRESHOLD = 0.7f;

    public AkazeMatcher(Context context) {
        super(context,
              AKAZE.create(),
              DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING),
              "AKAZE");
    }

    @Override
    protected double calculateSimilarity(MatOfDMatch matches) {
        DMatch[] matchArray = matches.toArray();
        if (matchArray.length == 0) return 0.0;

        // Count good matches using ratio test
        int goodMatches = 0;
        for (DMatch match : matchArray) {
            if (match.distance < 50.0) { // AKAZE threshold
                goodMatches++;
            }
        }

        // Calculate similarity based on number of good matches
        return (double) goodMatches / 100.0; // Normalize by expected number of matches
    }

    @Override
    public List<MatchResult> findMatches(Uri testImage, List<Uri> referenceImages) {
        try {
            Log.d(TAG, "Starting AKAZE matching process");
            Mat testMat = loadImage(testImage);

            Imgproc.resize(testMat, testMat, STANDARD_SIZE);
            
            MatOfKeyPoint testKeypoints = new MatOfKeyPoint();
            Mat testDescriptors = new Mat();
            
            Log.d(TAG, "Computing descriptors for test image");
            detector.detectAndCompute(testMat, new Mat(), testKeypoints, testDescriptors);
            Log.d(TAG, "Test image descriptors: " + testDescriptors.rows());
            
            List<MatchResult> results = new ArrayList<>();
            
            for (Uri refImage : referenceImages) {
                if (refImage.equals(testImage)) continue;
                
                Log.d(TAG, "Processing reference image: " + refImage);
                Mat refMat = loadImage(refImage);

                Imgproc.resize(refMat, refMat, STANDARD_SIZE);
                
                MatOfKeyPoint refKeypoints = new MatOfKeyPoint();
                Mat refDescriptors = new Mat();
                
                detector.detectAndCompute(refMat, new Mat(), refKeypoints, refDescriptors);
                Log.d(TAG, "Reference image descriptors: " + refDescriptors.rows());
                
                // Use knnMatch for ratio test
                List<MatOfDMatch> knnMatches = new ArrayList<>();
                matcher.knnMatch(testDescriptors, refDescriptors, knnMatches, 2);
                
                // Apply ratio test
                List<DMatch> goodMatches = new ArrayList<>();
                for (MatOfDMatch matOfDMatch : knnMatches) {
                    DMatch[] matches = matOfDMatch.toArray();
                    if (matches.length >= 2 && matches[0].distance < RATIO_THRESHOLD * matches[1].distance) {
                        goodMatches.add(matches[0]);
                    }
                }
                
                MatOfDMatch matches = new MatOfDMatch();
                matches.fromList(goodMatches);
                
                double similarity = calculateSimilarity(matches);
                Log.d(TAG, "Similarity score: " + similarity);
                if (similarity > 0) {
                    results.add(new MatchResult(refImage, similarity, algorithmName));
                }
                
                // Clean up
                refMat.release();
                refKeypoints.release();
                refDescriptors.release();
            }
            
            // Clean up test image resources
            testMat.release();
            testKeypoints.release();
            testDescriptors.release();
            
            Collections.sort(results, (a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            return results.subList(0, Math.min(3, results.size()));
            
        } catch (Exception e) {
            Log.e(TAG, "Error during matching", e);
            return new ArrayList<>();
        }
    }
} 