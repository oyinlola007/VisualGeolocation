package com.esigelec.visualgeolocation.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.esigelec.visualgeolocation.R;
import com.esigelec.visualgeolocation.utils.AkazeMatcher;
import com.esigelec.visualgeolocation.utils.FastMatcher;
import com.esigelec.visualgeolocation.utils.ImageMatcher;
import com.esigelec.visualgeolocation.utils.OrbMatcher;
import com.esigelec.visualgeolocation.utils.SiftImageMatcher;
import com.esigelec.visualgeolocation.viewmodel.SharedViewModel;
import com.esigelec.visualgeolocation.utils.ImageUtils;
import com.esigelec.visualgeolocation.utils.LocationUtils;
import android.location.Location;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import android.media.ExifInterface;
import android.graphics.Matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MatchResultsFragment extends Fragment {
    private static final String TAG = "MatchResultsFragment";
    private SharedViewModel viewModel;
    private ProgressBar progressBar;
    private TextView progressText;
    private ImageView testImageView;
    private TextView coordinatesText;
    private ExecutorService executorService;
    private final AtomicBoolean isMatching = new AtomicBoolean(false);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        executorService = Executors.newSingleThreadExecutor();

        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isMatching.get()) {
                    executorService.shutdownNow();
                    executorService = Executors.newSingleThreadExecutor();
                    isMatching.set(false);
                }
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_match_results, container, false);
        progressBar = view.findViewById(R.id.progressBar);
        progressText = view.findViewById(R.id.progressText);
        testImageView = view.findViewById(R.id.testImageView);
        coordinatesText = view.findViewById(R.id.coordinatesText);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadTestImage();
        performMatching();
    }

    private void loadTestImage() {
        try {
            Uri testImage = viewModel.getTestImage().getValue();
            if (testImage != null) {
                InputStream inputStream = requireContext().getContentResolver().openInputStream(testImage);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                
                // Get EXIF orientation
                ExifInterface exif = new ExifInterface(requireContext().getContentResolver().openInputStream(testImage));
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                
                // Rotate bitmap based on orientation
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        break;
                }
                
                if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
                
                testImageView.setImageBitmap(bitmap);
                
                // Get original coordinates
                double[] coordinates = ImageUtils.getImageCoordinates(requireContext(), testImage);
                if (coordinates != null) {
                    coordinatesText.setText(String.format("Original Location:\nLat: %.6f\nLon: %.6f", 
                        coordinates[0], 
                        coordinates[1]));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading test image", e);
        }
    }

    private void performMatching() {
        if (isMatching.get()) return;
        isMatching.set(true);
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        View matchesContainer = requireView().findViewById(R.id.matchesContainer);
        TextView matchingResultsTitle = requireView().findViewById(R.id.matchingResultsTitle);
        matchesContainer.setVisibility(View.GONE);
        matchingResultsTitle.setVisibility(View.GONE);

        executorService.execute(() -> {
            try {
                Uri testImage = viewModel.getTestImage().getValue();
                String algorithm = viewModel.getSelectedAlgorithm().getValue();
                List<Uri> images = viewModel.getSelectedImages().getValue();

                if (testImage == null || algorithm == null || images == null) {
                    Log.e(TAG, "Missing required data for matching");
                    return;
                }

                Log.d(TAG, "Using algorithm: " + algorithm);
                Log.d(TAG, "Test image: " + testImage);
                Log.d(TAG, "Number of images to match: " + images.size());
                
                ImageMatcher matcher = createMatcher(algorithm);
                
                // Set up progress tracking
                int totalImages = images.size();
                progressBar.setMax(totalImages);
                
                List<ImageMatcher.MatchResult> results = new ArrayList<>();
                for (int i = 0; i < totalImages && !Thread.currentThread().isInterrupted(); i++) {
                    Uri image = images.get(i);
                    Log.d(TAG, "Processing image " + (i + 1) + "/" + totalImages + ": " + image);
                    
                    List<ImageMatcher.MatchResult> matchResults = matcher.findMatches(testImage, List.of(image));
                    Log.d(TAG, "Match results for image " + image + ": " + matchResults.size() + " matches");
                    
                    if (!matchResults.isEmpty()) {
                        ImageMatcher.MatchResult result = matchResults.get(0);
                        Log.d(TAG, "Adding match result - Image: " + result.getImageUri() + 
                            ", Similarity: " + result.getSimilarity());
                        results.add(result);
                    }
                    
                    // Update progress on UI thread
                    int currentProgress = i + 1;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setProgress(currentProgress);
                            progressText.setText(String.format("Matching in progress: %d/%d checked", 
                                currentProgress, totalImages));
                        });
                    }
                }

                if (!Thread.currentThread().isInterrupted()) {
                    // Sort results by similarity
                    results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
                    Log.d(TAG, "Final results count: " + results.size());
                    
                    // Calculate predicted location using weighted average
                    double totalWeight = 0;
                    double weightedLat = 0;
                    double weightedLon = 0;
                    
                    // Use top 3 matches for prediction
                    int numMatches = Math.min(3, results.size());
                    for (int i = 0; i < numMatches; i++) {
                        ImageMatcher.MatchResult result = results.get(i);
                        double[] refCoordinates = ImageUtils.getImageCoordinates(requireContext(), result.getImageUri());
                        if (refCoordinates != null) {
                            double weight = result.getSimilarity();
                            totalWeight += weight;
                            weightedLat += refCoordinates[0] * weight;
                            weightedLon += refCoordinates[1] * weight;
                        }
                    }
                    
                    if (totalWeight > 0) {
                        weightedLat /= totalWeight;
                        weightedLon /= totalWeight;
                        
                        double[] originalCoordinates = ImageUtils.getImageCoordinates(requireContext(), testImage);
                        if (originalCoordinates != null) {
                            double errorDistance = LocationUtils.calculateDistance(
                                originalCoordinates[0], originalCoordinates[1],
                                weightedLat, weightedLon
                            );
                            
                            // Create final copies of the variables for use in the lambda
                            final double finalWeightedLat = weightedLat;
                            final double finalWeightedLon = weightedLon;
                            final double finalErrorDistance = errorDistance;
                            
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    coordinatesText.setText(String.format(
                                        "Original Location:\nLat: %.6f\nLon: %.6f\n\n" +
                                        "Predicted Location:\nLat: %.6f\nLon: %.6f\n\n" +
                                        "Error Distance: %.2f meters",
                                        originalCoordinates[0],
                                        originalCoordinates[1],
                                        finalWeightedLat,
                                        finalWeightedLon,
                                        finalErrorDistance
                                    ));
                                });
                            }
                        }
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            progressText.setVisibility(View.GONE);
                            matchesContainer.setVisibility(View.VISIBLE);
                            matchingResultsTitle.setVisibility(View.VISIBLE);
                            
                            if (results.isEmpty()) {
                                Log.d(TAG, "No matches found, showing empty state");
                                // TODO: Show empty state message
                            } else {
                                Log.d(TAG, "Displaying top " + numMatches + " matches");
                                // Display top 3 matches
                                for (int i = 0; i < numMatches; i++) {
                                    ImageMatcher.MatchResult result = results.get(i);
                                    View matchView = requireView().findViewById(
                                        i == 0 ? R.id.match1 : (i == 1 ? R.id.match2 : R.id.match3));
                                    
                                    ImageView imageView = matchView.findViewById(R.id.matchImageView);
                                    TextView similarityText = matchView.findViewById(R.id.similarityText);
                                    
                                    // Load and display the image
                                    try {
                                        InputStream inputStream = requireContext().getContentResolver()
                                            .openInputStream(result.getImageUri());
                                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                        
                                        // Get EXIF orientation
                                        ExifInterface exif = new ExifInterface(requireContext().getContentResolver()
                                            .openInputStream(result.getImageUri()));
                                        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 
                                            ExifInterface.ORIENTATION_NORMAL);
                                        
                                        // Rotate bitmap based on orientation
                                        Matrix matrix = new Matrix();
                                        switch (orientation) {
                                            case ExifInterface.ORIENTATION_ROTATE_90:
                                                matrix.postRotate(90);
                                                break;
                                            case ExifInterface.ORIENTATION_ROTATE_180:
                                                matrix.postRotate(180);
                                                break;
                                            case ExifInterface.ORIENTATION_ROTATE_270:
                                                matrix.postRotate(270);
                                                break;
                                        }
                                        
                                        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), 
                                                bitmap.getHeight(), matrix, true);
                                        }
                                        
                                        imageView.setImageBitmap(bitmap);
                                    } catch (IOException e) {
                                        Log.e(TAG, "Error loading match image", e);
                                    }
                                    
                                    // Display similarity score
                                    similarityText.setText(String.format("Match #%d: %.2f",
                                        i + 1, result.getSimilarity() * 100));
                                }
                            }
                            
                            isMatching.set(false);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during matching", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        progressText.setVisibility(View.GONE);
                        matchesContainer.setVisibility(View.VISIBLE);
                        matchingResultsTitle.setVisibility(View.VISIBLE);
                        isMatching.set(false);
                    });
                }
            }
        });
    }

    private ImageMatcher createMatcher(String algorithm) {
        switch (algorithm) {
            case "FAST":
                return new FastMatcher(requireContext());
            case "AKAZE":
                return new AkazeMatcher(requireContext());
            case "ORB":
                return new OrbMatcher(requireContext());
            default:
                return new SiftImageMatcher(requireContext());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
} 