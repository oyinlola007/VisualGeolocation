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
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SiftImageMatcher extends BaseImageMatcher {
    private static final String TAG = "SiftImageMatcher";
    private static final float RATIO_THRESHOLD = 0.85f; // Lowe's ratio test threshold
    private static final int MIN_FEATURE_MATCHES = 30; // Minimum number of feature matches required

    public SiftImageMatcher(Context context) {
        super(context,
              SIFT.create(),
              BFMatcher.create(Core.NORM_L2, false), // Use BFMatcher instead of FLANN
              "SIFT");
    }

    @Override
    protected double calculateSimilarity(MatOfDMatch matches) {
        DMatch[] matchArray = matches.toArray();
        if (matchArray.length == 0) return 0.0;

        // Calculate similarity based on number of good matches
        return (double) matchArray.length / 100.0; // Normalize by expected number of matches
    }

    /**
     * Find bidirectional matches between two images
     * This ensures symmetric matching (A→B same as B→A)
     */
    private List<DMatch> findBidirectionalMatches(Mat descriptors1, Mat descriptors2) {
        // Match in both directions: 1→2 and 2→1
        List<MatOfDMatch> knnMatches12 = new ArrayList<>();
        List<MatOfDMatch> knnMatches21 = new ArrayList<>();
        
        // Forward matching (descriptors1 → descriptors2)
        matcher.knnMatch(descriptors1, descriptors2, knnMatches12, 2);
        
        // Backward matching (descriptors2 → descriptors1)
        matcher.knnMatch(descriptors2, descriptors1, knnMatches21, 2);
        
        // Apply ratio test to forward matches
        List<DMatch> goodMatches12 = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : knnMatches12) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches.length >= 2 && matches[0].distance < RATIO_THRESHOLD * matches[1].distance) {
                goodMatches12.add(matches[0]);
            }
        }
        
        // Apply ratio test to backward matches
        List<DMatch> goodMatches21 = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : knnMatches21) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches.length >= 2 && matches[0].distance < RATIO_THRESHOLD * matches[1].distance) {
                goodMatches21.add(matches[0]);
            }
        }
        
        // Filter for bidirectional/symmetric matches
        List<DMatch> bidirectionalMatches = new ArrayList<>();
        for (DMatch match12 : goodMatches12) {
            // Check if there's a corresponding match in the reverse direction
            for (DMatch match21 : goodMatches21) {
                if (match12.trainIdx == match21.queryIdx && match12.queryIdx == match21.trainIdx) {
                    bidirectionalMatches.add(match12);
                    break;
                }
            }
        }
        
        Log.d(TAG, "Forward matches: " + goodMatches12.size() + 
              ", Backward matches: " + goodMatches21.size() + 
              ", Bidirectional matches: " + bidirectionalMatches.size());
        
        return bidirectionalMatches;
    }

    @Override
    public List<MatchResult> findMatches(Uri testImage, List<Uri> referenceImages) {
        try {
            Log.d(TAG, "Starting SIFT matching process");
            Mat testMat = loadImage(testImage);
            
            // Resize to standard size
            Size standardSize = new Size(500, 500);
            Imgproc.resize(testMat, testMat, standardSize);
            
            MatOfKeyPoint testKeypoints = new MatOfKeyPoint();
            Mat testDescriptors = new Mat();
            
            Log.d(TAG, "Computing descriptors for test image");
            detector.detectAndCompute(testMat, new Mat(), testKeypoints, testDescriptors);
            Log.d(TAG, "Test image keypoints: " + testKeypoints.rows());
            Log.d(TAG, "Test image descriptors: " + testDescriptors.rows());
            
            List<MatchResult> results = new ArrayList<>();
            
            for (Uri refImage : referenceImages) {
                if (refImage.equals(testImage)) continue;
                
                Log.d(TAG, "Processing reference image: " + refImage);
                Mat refMat = loadImage(refImage);
                
                // Resize to standard size
                Imgproc.resize(refMat, refMat, standardSize);
                
                MatOfKeyPoint refKeypoints = new MatOfKeyPoint();
                Mat refDescriptors = new Mat();
                
                detector.detectAndCompute(refMat, new Mat(), refKeypoints, refDescriptors);
                Log.d(TAG, "Reference image keypoints: " + refKeypoints.rows());
                Log.d(TAG, "Reference image descriptors: " + refDescriptors.rows());
                
                // Find bidirectional matches (symmetric matching)
                List<DMatch> bidirectionalMatches = findBidirectionalMatches(testDescriptors, refDescriptors);
                
                // Log the number of feature matches found
                Log.d(TAG, "Number of bidirectional matches found: " + bidirectionalMatches.size() + 
                      " for image " + refImage.getLastPathSegment());
                
                // Only include matches that exceed our minimum threshold
                if (bidirectionalMatches.size() >= MIN_FEATURE_MATCHES) {
                    MatOfDMatch matches = new MatOfDMatch();
                    matches.fromList(bidirectionalMatches);
                    
                    double similarity = calculateSimilarity(matches);
                    Log.d(TAG, "Similarity score: " + similarity + " with " + bidirectionalMatches.size() + " matches");
                    results.add(new MatchResult(refImage, similarity, algorithmName));
                } else {
                    Log.d(TAG, "Skipping image " + refImage.getLastPathSegment() + 
                          " due to insufficient bidirectional matches: " + bidirectionalMatches.size() + 
                          " < " + MIN_FEATURE_MATCHES);
                }
                
                refMat.release();
                refKeypoints.release();
                refDescriptors.release();
            }
            
            // Clean up test image resources
            testMat.release();
            testKeypoints.release();
            testDescriptors.release();
            
            if (results.isEmpty()) {
                Log.d(TAG, "No matches exceeded the minimum feature match threshold of " + MIN_FEATURE_MATCHES);
                return results;
            }
            
            Collections.sort(results, (a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            return results.subList(0, Math.min(3, results.size()));
            
        } catch (Exception e) {
            Log.e(TAG, "Error during matching", e);
            return new ArrayList<>();
        }
    }
}