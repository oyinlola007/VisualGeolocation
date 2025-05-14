# Visual Geolocation

An Android application that uses computer vision algorithms to match images and predict geographical locations based on visual similarity.

## Overview

Visual Geolocation leverages OpenCV's feature detection and matching algorithms to analyze visual similarities between images with known geographical coordinates. When provided with an unlocated test image, the application processes it against a dataset of geotagged reference images to predict its likely geographic location.

## Features

- **Multiple Image Matching Algorithms**:
  - **SIFT (Scale-Invariant Feature Transform)**: Robust to scaling, rotation, and lighting changes
  - **AKAZE (Accelerated-KAZE)**: Good performance with nonlinear scale spaces
  - **ORB (Oriented FAST and Rotated BRIEF)**: Fast and efficient binary descriptor

- **Bidirectional Matching in SIFT**: Implements symmetric matching only in the SIFT algorithm to ensure consistent results regardless of image order (A→B equals B→A)

- **Location Prediction Methods**:
  - **Non-weighted averaging**: Simple average of coordinates from matched images
  - **Similarity-weighted averaging**: Coordinates weighted by visual similarity scores

- **Batch Analysis**: Process each image in a dataset against all others to evaluate location prediction accuracy

- **Location Accuracy Analysis**:
  - Euclidean distance calculations using the Haversine formula
  - Detailed error metrics for both weighted and non-weighted predictions
  - Results exportable as CSV files

## Technical Implementation

### Image Processing Pipeline

1. **Feature Detection and Description**:
   - Each algorithm extracts keypoints and descriptors from images
   - Images are standardized to 500x500 pixels for consistent processing

2. **Feature Matching**:
   - Keypoint descriptors from the test image are matched against reference images
   - Lowe's ratio test (0.85 threshold) is applied to filter poor matches
   - A minimum threshold of feature matches (30 by default) is required for valid matching

3. **Bidirectional Matching (SIFT only)**:
   - In the SIFT algorithm, matches in both directions (A→B and B→A) are computed
   - Only matches present in both directions are kept, ensuring symmetry
   - This eliminates asymmetric results when matching different image pairs
   - Other algorithms (AKAZE, ORB, FAST) use standard unidirectional matching

4. **Similarity Calculation**:
   - Similarity scores are calculated based on the number and quality of matches
   - For SIFT: Normalized count of good matches
   - For ORB: Combination of match ratio and inverse average distance

### Location Prediction Process

1. **EXIF Metadata Extraction**:
   - Original coordinates are extracted from image EXIF data using `ExifInterface`
   - Coordinates are parsed from the standard format (dd/1,mm/1,ss/1) to decimal degrees

2. **Location Aggregation**:
   - For each test image, coordinates from matching reference images are collected
   - Each match is stored as a `WeightedLocation` with latitude, longitude, and similarity weight

3. **Coordinate Averaging**:
   - **Non-weighted**: Simple arithmetic mean of all matched coordinates
     ```
     avgLat = sumLat / numberOfMatches
     avgLng = sumLng / numberOfMatches
     ```
   - **Weighted**: Sum of (coordinate × similarity) / sum of similarities
     ```
     weightedAvgLat = (Σ match.lat × match.similarity) / (Σ match.similarity)
     weightedAvgLng = (Σ match.lng × match.similarity) / (Σ match.similarity)
     ```
   - Higher similarity scores have stronger influence on the weighted average location
   - This prioritizes locations from visually similar images in the prediction

4. **Distance Calculation**:
   - Haversine formula calculates geodesic distance between points on a sphere:
     ```
     a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlong/2)
     c = 2 × atan2(√a, √(1-a))
     distance = R × c
     ```
   - Where R is Earth's radius (6,371,000 meters)

### Batch Analysis System

1. The app allows batch processing of images using an ExecutorService for concurrent execution
2. Each image is compared against every other image in the dataset
3. Progress tracking is implemented for each source image with visual feedback
4. Results are presented in a detailed table with error metrics
5. Results can be exported to CSV with the algorithm name in the filename

## Implementation Details

- Built with Java and Android SDK
- Uses OpenCV 4.5.3 for computer vision algorithms
- Implements Material Design components for adaptive UI (works in both light and dark themes)
- Proper file sharing with FileProvider for exporting results
- Multi-threaded processing for improved performance
