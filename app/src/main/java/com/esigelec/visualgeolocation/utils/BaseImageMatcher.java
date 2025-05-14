package com.esigelec.visualgeolocation.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfDMatch;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class BaseImageMatcher implements ImageMatcher {
    private static final String TAG = "BaseImageMatcher";
    protected final Context context;
    protected final Feature2D detector;
    protected final DescriptorMatcher matcher;
    protected final String algorithmName;

    protected BaseImageMatcher(Context context, Feature2D detector, DescriptorMatcher matcher, String algorithmName) {
        this.context = context;
        this.detector = detector;
        this.matcher = matcher;
        this.algorithmName = algorithmName;
    }

    @Override
    public List<MatchResult> findMatches(Uri testImage, List<Uri> referenceImages) {
        try {
            Log.d(TAG, "Loading test image: " + testImage);
            Mat testMat = loadImage(testImage);
            Log.d(TAG, "Test image loaded, size: " + testMat.size());
            
            MatOfKeyPoint testKeypoints = new MatOfKeyPoint();
            Mat testDescriptors = new Mat();
            
            // Detect keypoints and compute descriptors for test image
            Log.d(TAG, "Detecting keypoints and computing descriptors for test image");
            detector.detectAndCompute(testMat, new Mat(), testKeypoints, testDescriptors);
            Log.d(TAG, "Test image keypoints: " + testKeypoints.size());
            Log.d(TAG, "Test image descriptors: " + testDescriptors.size());
            
            List<MatchResult> results = new ArrayList<>();
            
            // Compare with each reference image
            for (Uri refImage : referenceImages) {
                // Skip if this is the same image
                if (refImage.equals(testImage)) {
                    Log.d(TAG, "Skipping self-match for image: " + refImage);
                    continue;
                }
                
                Log.d(TAG, "Processing reference image: " + refImage);
                Mat refMat = loadImage(refImage);
                Log.d(TAG, "Reference image loaded, size: " + refMat.size());
                
                MatOfKeyPoint refKeypoints = new MatOfKeyPoint();
                Mat refDescriptors = new Mat();
                
                // Detect keypoints and compute descriptors for reference image
                Log.d(TAG, "Detecting keypoints and computing descriptors for reference image");
                detector.detectAndCompute(refMat, new Mat(), refKeypoints, refDescriptors);
                Log.d(TAG, "Reference image keypoints: " + refKeypoints.size());
                Log.d(TAG, "Reference image descriptors: " + refDescriptors.size());
                
                // Match descriptors
                Log.d(TAG, "Matching descriptors");
                MatOfDMatch matches = new MatOfDMatch();
                matcher.match(testDescriptors, refDescriptors, matches);
                Log.d(TAG, "Number of matches found: " + matches.size());
                
                // Calculate similarity score
                double similarity = calculateSimilarity(matches);
                Log.d(TAG, "Similarity score: " + similarity);
                
                results.add(new MatchResult(refImage, similarity, algorithmName));
                
                // Clean up
                refMat.release();
                refKeypoints.release();
                refDescriptors.release();
            }
            
            // Sort results by similarity (descending)
            Collections.sort(results, (a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            
            // Return top 3 matches
            return results.subList(0, Math.min(3, results.size()));
            
        } catch (Exception e) {
            Log.e(TAG, "Error during matching", e);
            return new ArrayList<>();
        }
    }

    protected Mat loadImage(Uri imageUri) throws IOException {
        Log.d(TAG, "Loading image from URI: " + imageUri);
        InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        Log.d(TAG, "Bitmap decoded, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        Log.d(TAG, "Mat created, size: " + mat.size());
        return mat;
    }

    protected abstract double calculateSimilarity(MatOfDMatch matches);
} 