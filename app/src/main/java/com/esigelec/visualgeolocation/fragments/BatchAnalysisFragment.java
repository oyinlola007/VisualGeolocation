package com.esigelec.visualgeolocation.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.esigelec.visualgeolocation.R;
import com.esigelec.visualgeolocation.utils.AkazeMatcher;
import com.esigelec.visualgeolocation.utils.FastMatcher;
import com.esigelec.visualgeolocation.utils.ImageMatcher;
import com.esigelec.visualgeolocation.utils.ImageUtils;
import com.esigelec.visualgeolocation.utils.LocationUtils;
import com.esigelec.visualgeolocation.utils.OrbMatcher;
import com.esigelec.visualgeolocation.utils.SiftImageMatcher;
import com.esigelec.visualgeolocation.viewmodel.SharedViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchAnalysisFragment extends Fragment {
    private static final String TAG = "BatchAnalysisFragment";
    private SharedViewModel viewModel;
    private LinearLayout progressContainer;
    private TextView statusText;
    private TextView resultsTitle;
    private TableLayout resultsTable;
    private View tableScrollView;
    private Button exportButton;
    private ExecutorService executorService;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final Map<String, ProgressData> progressMap = new HashMap<>();
    private final Map<String, LocationData> locationDataMap = new HashMap<>();
    private String selectedAlgorithm;
    private double totalNonWeightedDistance = 0;
    private double totalWeightedDistance = 0;
    private int validLocationCount = 0;

    private static class ProgressData {
        View progressView;
        ProgressBar progressBar;
        TextView progressText;
        TextView progressLabel;
        int max;
        int current;
    }
    
    private static class LocationData {
        double originalLat;
        double originalLng;
        double avgLat;
        double avgLng;
        double weightedAvgLat;
        double weightedAvgLng;
        double nonWeightedDistance;
        double weightedDistance;
        List<WeightedLocation> matchedLocations = new ArrayList<>();
    }
    
    private static class WeightedLocation {
        double lat;
        double lng;
        double weight;
        
        WeightedLocation(double lat, double lng, double weight) {
            this.lat = lat;
            this.lng = lng;
            this.weight = weight;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        executorService = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isProcessing.get()) {
                    showCancellationDialog();
                } else {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_batch_analysis, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        progressContainer = view.findViewById(R.id.progressContainer);
        statusText = view.findViewById(R.id.statusText);
        resultsTitle = view.findViewById(R.id.resultsTitle);
        resultsTable = view.findViewById(R.id.resultsTable);
        tableScrollView = view.findViewById(R.id.tableScrollView);
        exportButton = view.findViewById(R.id.exportButton);
        
        exportButton.setOnClickListener(v -> exportResultsToCSV());
        
        startBatchAnalysis();
    }
    
    private void showCancellationDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Analysis")
            .setMessage("Are you sure you want to cancel the batch analysis?")
            .setPositiveButton("Yes", (dialog, which) -> {
                stopProcessing();
                requireActivity().getSupportFragmentManager().popBackStack();
            })
            .setNegativeButton("No", null)
            .show();
    }
    
    private void stopProcessing() {
        if (isProcessing.get()) {
            executorService.shutdownNow();
            executorService = Executors.newFixedThreadPool(
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            isProcessing.set(false);
        }
    }
    
    private void startBatchAnalysis() {
        if (isProcessing.get()) return;
        isProcessing.set(true);
        
        selectedAlgorithm = viewModel.getSelectedAlgorithm().getValue();
        List<Uri> images = viewModel.getSelectedImages().getValue();
        
        if (selectedAlgorithm == null || images == null || images.size() < 2) {
            statusText.setText("Not enough images to perform batch analysis");
            return;
        }
        
        statusText.setText("Comparing all images using " + selectedAlgorithm + " algorithm");
        
        // Initialize the progress views for each image
        for (int i = 0; i < images.size(); i++) {
            Uri image = images.get(i);
            initProgressView(image, i, images.size() - 1); // Each image is compared against all others except itself
            
            // Initialize location data map
            String imageId = image.getLastPathSegment();
            locationDataMap.put(imageId, new LocationData());
            
            // Try to extract original location from image EXIF
            try {
                double[] coordinates = ImageUtils.getImageCoordinates(requireContext(), image);
                if (coordinates != null && coordinates.length == 2) {
                    LocationData locationData = locationDataMap.get(imageId);
                    locationData.originalLat = coordinates[0];
                    locationData.originalLng = coordinates[1];
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract coordinates for " + imageId, e);
            }
        }
        
        final ImageMatcher matcher = createMatcher(selectedAlgorithm);
        final AtomicInteger completedTasks = new AtomicInteger(0);
        final int totalTasks = images.size() * (images.size() - 1);
        
        // For each image, compare against all other images
        for (int i = 0; i < images.size(); i++) {
            final Uri sourceImage = images.get(i);
            final String sourceId = sourceImage.getLastPathSegment();
            
            final int sourceIdx = i;
            executorService.execute(() -> {
                List<Uri> targetImages = new ArrayList<>(images);
                targetImages.remove(sourceImage); // Don't compare with self
                
                for (int j = 0; j < targetImages.size() && !Thread.currentThread().isInterrupted(); j++) {
                    Uri targetImage = targetImages.get(j);
                    String targetId = targetImage.getLastPathSegment();
                    
                    try {
                        // Find matches between the two images
                        List<ImageMatcher.MatchResult> matchResults = matcher.findMatches(sourceImage, List.of(targetImage));
                        double similarity = 0.0;
                        
                        if (!matchResults.isEmpty()) {
                            similarity = matchResults.get(0).getSimilarity();
                            
                            // Store location data for weighted average calculation
                            LocationData sourceLocationData = locationDataMap.get(sourceId);
                            LocationData targetLocationData = locationDataMap.get(targetId);
                            
                            if (targetLocationData.originalLat != 0 && targetLocationData.originalLng != 0) {
                                WeightedLocation weightedLocation = new WeightedLocation(
                                    targetLocationData.originalLat,
                                    targetLocationData.originalLng,
                                    similarity
                                );
                                sourceLocationData.matchedLocations.add(weightedLocation);
                            }
                        }
                        
                        // Store the result in the ViewModel - this will use postValue now
                        viewModel.updateBatchResult(sourceId, targetId, similarity);
                        
                        // Update progress on the UI thread
                        final int currentProgress = j + 1;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // Update the progress for this specific image
                                updateProgress(sourceId, currentProgress);
                                
                                // Update overall progress
                                int completed = completedTasks.incrementAndGet();
                                statusText.setText(String.format("Progress: %d/%d comparisons completed", 
                                        completed, totalTasks));
                                
                                // If all tasks are completed, show results
                                if (completed >= totalTasks) {
                                    calculateLocationAverages();
                                    displayDetailedResults(images);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error comparing images", e);
                    }
                }
            });
        }
    }
    
    private void calculateLocationAverages() {
        totalNonWeightedDistance = 0;
        totalWeightedDistance = 0;
        validLocationCount = 0;
        
        for (Map.Entry<String, LocationData> entry : locationDataMap.entrySet()) {
            LocationData locationData = entry.getValue();
            List<WeightedLocation> matches = locationData.matchedLocations;
            
            if (matches.isEmpty() || locationData.originalLat == 0 || locationData.originalLng == 0) {
                continue;
            }
            
            // Calculate non-weighted average
            double sumLat = 0;
            double sumLng = 0;
            for (WeightedLocation match : matches) {
                sumLat += match.lat;
                sumLng += match.lng;
            }
            locationData.avgLat = sumLat / matches.size();
            locationData.avgLng = sumLng / matches.size();
            
            // Calculate weighted average
            double totalWeight = 0;
            double weightedSumLat = 0;
            double weightedSumLng = 0;
            for (WeightedLocation match : matches) {
                weightedSumLat += match.lat * match.weight;
                weightedSumLng += match.lng * match.weight;
                totalWeight += match.weight;
            }
            
            if (totalWeight > 0) {
                locationData.weightedAvgLat = weightedSumLat / totalWeight;
                locationData.weightedAvgLng = weightedSumLng / totalWeight;
            } else {
                locationData.weightedAvgLat = locationData.avgLat;
                locationData.weightedAvgLng = locationData.avgLng;
            }
            
            // Calculate Euclidean distances
            locationData.nonWeightedDistance = LocationUtils.calculateDistance(
                locationData.originalLat, locationData.originalLng,
                locationData.avgLat, locationData.avgLng
            );
            
            locationData.weightedDistance = LocationUtils.calculateDistance(
                locationData.originalLat, locationData.originalLng,
                locationData.weightedAvgLat, locationData.weightedAvgLng
            );
            
            // Add to totals for average calculation
            totalNonWeightedDistance += locationData.nonWeightedDistance;
            totalWeightedDistance += locationData.weightedDistance;
            validLocationCount++;
        }
    }
    
    private void initProgressView(Uri image, int index, int max) {
        if (getActivity() == null) return;
        
        getActivity().runOnUiThread(() -> {
            View progressView = getLayoutInflater().inflate(R.layout.item_batch_progress, null);
            TextView progressLabel = progressView.findViewById(R.id.progressLabel);
            ProgressBar progressBar = progressView.findViewById(R.id.progressBar);
            TextView progressText = progressView.findViewById(R.id.progressText);
            
            String imageId = image.getLastPathSegment();
            progressLabel.setText("Image " + (index + 1) + ": " + imageId);
            progressBar.setMax(max);
            progressBar.setProgress(0);
            progressText.setText("0/" + max);
            
            ProgressData data = new ProgressData();
            data.progressView = progressView;
            data.progressBar = progressBar;
            data.progressText = progressText;
            data.progressLabel = progressLabel;
            data.max = max;
            data.current = 0;
            
            progressMap.put(imageId, data);
            progressContainer.addView(progressView);
        });
    }
    
    private void updateProgress(String imageId, int progress) {
        ProgressData data = progressMap.get(imageId);
        if (data != null) {
            data.current = progress;
            data.progressBar.setProgress(progress);
            data.progressText.setText(progress + "/" + data.max);
        }
    }
    
    private void displayDetailedResults(List<Uri> images) {
        isProcessing.set(false);
        
        // Hide progress and show results
        progressContainer.setVisibility(View.GONE);
        resultsTitle.setVisibility(View.VISIBLE);
        tableScrollView.setVisibility(View.VISIBLE);
        exportButton.setVisibility(View.VISIBLE);
        
        resultsTitle.setText("Location Analysis Results (" + selectedAlgorithm + ")");
        
        // Get results from ViewModel
        Map<String, Map<String, Double>> results = viewModel.getBatchResults().getValue();
        if (results == null || results.isEmpty()) {
            resultsTitle.setText("No results found");
            return;
        }
        
        // Clear previous table
        resultsTable.removeAllViews();
        
        // Create header row
        TableRow headerRow = new TableRow(requireContext());
        headerRow.setBackgroundColor(Color.LTGRAY);
        
        // Add column headers
        String[] headers = {
            "Image", 
            "Original Lat/Lng", 
            "Avg Lat/Lng", 
            "Weighted Avg Lat/Lng", 
            "Dist (Non-weighted)", 
            "Dist (Weighted)"
        };
        
        for (String header : headers) {
            TextView headerCell = new TextView(requireContext());
            headerCell.setPadding(16, 16, 16, 16);
            headerCell.setText(header);
            headerCell.setTextColor(Color.BLACK);
            headerRow.addView(headerCell);
        }
        resultsTable.addView(headerRow);
        
        // Add data rows
        for (Uri image : images) {
            String imageId = image.getLastPathSegment();
            LocationData locationData = locationDataMap.get(imageId);
            
            // Skip images without original location data or without any matches
            if (locationData == null || 
                locationData.originalLat == 0 || 
                locationData.originalLng == 0 ||
                locationData.matchedLocations.isEmpty()) {
                
                Log.d(TAG, "Skipping image " + imageId + " - Has location: " + 
                      (locationData != null && locationData.originalLat != 0 && locationData.originalLng != 0) + 
                      ", Matches: " + (locationData != null ? locationData.matchedLocations.size() : 0));
                continue;
            }
            
            TableRow dataRow = new TableRow(requireContext());
            
            // Image ID cell
            TextView imageCell = new TextView(requireContext());
            imageCell.setPadding(16, 16, 16, 16);
            imageCell.setText(imageId);
            dataRow.addView(imageCell);
            
            // Original Lat/Lng
            TextView originalLocCell = new TextView(requireContext());
            originalLocCell.setPadding(16, 16, 16, 16);
            originalLocCell.setText(String.format(Locale.US, "%.6f, %.6f", 
                    locationData.originalLat, locationData.originalLng));
            dataRow.addView(originalLocCell);
            
            // Average Lat/Lng
            TextView avgLocCell = new TextView(requireContext());
            avgLocCell.setPadding(16, 16, 16, 16);
            avgLocCell.setText(String.format(Locale.US, "%.6f, %.6f", 
                    locationData.avgLat, locationData.avgLng));
            dataRow.addView(avgLocCell);
            
            // Weighted Average Lat/Lng
            TextView weightedAvgLocCell = new TextView(requireContext());
            weightedAvgLocCell.setPadding(16, 16, 16, 16);
            weightedAvgLocCell.setText(String.format(Locale.US, "%.6f, %.6f", 
                    locationData.weightedAvgLat, locationData.weightedAvgLng));
            dataRow.addView(weightedAvgLocCell);
            
            // Non-weighted Distance
            TextView nonWeightedDistCell = new TextView(requireContext());
            nonWeightedDistCell.setPadding(16, 16, 16, 16);
            nonWeightedDistCell.setText(String.format(Locale.US, "%.2f m", 
                    locationData.nonWeightedDistance));
            dataRow.addView(nonWeightedDistCell);
            
            // Weighted Distance
            TextView weightedDistCell = new TextView(requireContext());
            weightedDistCell.setPadding(16, 16, 16, 16);
            weightedDistCell.setText(String.format(Locale.US, "%.2f m", 
                    locationData.weightedDistance));
            dataRow.addView(weightedDistCell);
            
            resultsTable.addView(dataRow);
        }
        
        // Add summary row if we have valid location data
        if (validLocationCount > 0) {
            TableRow summaryRow = new TableRow(requireContext());
            summaryRow.setBackgroundColor(Color.rgb(230, 230, 230));
            
            // Title cell
            TextView titleCell = new TextView(requireContext());
            titleCell.setPadding(16, 16, 16, 16);
            titleCell.setText("AVERAGE");
            titleCell.setTextColor(Color.BLACK);
            titleCell.setTypeface(null, android.graphics.Typeface.BOLD);
            summaryRow.addView(titleCell);
            
            // Empty cells for lat/lng columns
            for (int i = 0; i < 3; i++) {
                TextView emptyCell = new TextView(requireContext());
                emptyCell.setPadding(16, 16, 16, 16);
                emptyCell.setText("");
                summaryRow.addView(emptyCell);
            }
            
            // Average non-weighted distance
            TextView avgNonWeightedCell = new TextView(requireContext());
            avgNonWeightedCell.setPadding(16, 16, 16, 16);
            avgNonWeightedCell.setText(String.format(Locale.US, "%.2f m", 
                    totalNonWeightedDistance / validLocationCount));
            avgNonWeightedCell.setTypeface(null, android.graphics.Typeface.BOLD);
            summaryRow.addView(avgNonWeightedCell);
            
            // Average weighted distance
            TextView avgWeightedCell = new TextView(requireContext());
            avgWeightedCell.setPadding(16, 16, 16, 16);
            avgWeightedCell.setText(String.format(Locale.US, "%.2f m", 
                    totalWeightedDistance / validLocationCount));
            avgWeightedCell.setTypeface(null, android.graphics.Typeface.BOLD);
            summaryRow.addView(avgWeightedCell);
            
            resultsTable.addView(summaryRow);
        }
    }
    
    private void exportResultsToCSV() {
        if (locationDataMap.isEmpty()) {
            Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = sdf.format(new Date());
        String fileName = "location_analysis_" + selectedAlgorithm + "_" + timestamp + ".csv";
        
        try {
            File csvFile = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            FileOutputStream fos = new FileOutputStream(csvFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            
            // Write header
            writer.write("Image,Original Latitude,Original Longitude,Average Latitude,Average Longitude," +
                    "Weighted Average Latitude,Weighted Average Longitude,Non-weighted Distance (m),Weighted Distance (m)\n");
            
            // Write data rows
            for (Map.Entry<String, LocationData> entry : locationDataMap.entrySet()) {
                String imageId = entry.getKey();
                LocationData data = entry.getValue();
                
                // Skip images without location data or without any matches
                if (data.originalLat == 0 || data.originalLng == 0 || data.matchedLocations.isEmpty()) {
                    continue;
                }
                
                writer.write(String.format(Locale.US, 
                        "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.2f,%.2f\n",
                        imageId,
                        data.originalLat, data.originalLng,
                        data.avgLat, data.avgLng,
                        data.weightedAvgLat, data.weightedAvgLng,
                        data.nonWeightedDistance, data.weightedDistance));
            }
            
            // Write summary
            if (validLocationCount > 0) {
                writer.write(String.format(Locale.US, 
                        "AVERAGE,,,,,,,%,.2f,%,.2f\n",
                        totalNonWeightedDistance / validLocationCount,
                        totalWeightedDistance / validLocationCount));
            }
            
            writer.close();
            fos.close();
            
            // Share the file
            Uri fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getApplicationContext().getPackageName() + ".provider",
                    csvFile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Location Analysis Results (" + selectedAlgorithm + ")");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Export Results"));
            
            Toast.makeText(requireContext(), "CSV file saved: " + fileName, Toast.LENGTH_LONG).show();
            
        } catch (IOException e) {
            Log.e(TAG, "Error exporting CSV file", e);
            Toast.makeText(requireContext(), "Error exporting CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        stopProcessing();
    }
} 