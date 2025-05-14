package com.esigelec.visualgeolocation.viewmodel;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.esigelec.visualgeolocation.utils.ImageMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SharedViewModel extends ViewModel {
    private final MutableLiveData<List<Uri>> selectedImages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Uri> testImage = new MutableLiveData<>();
    private final MutableLiveData<String> selectedAlgorithm = new MutableLiveData<>();
    private final MutableLiveData<List<ImageMatcher.MatchResult>> matchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Map<String, Double>>> batchResults = new MutableLiveData<>(new HashMap<>());

    public void setSelectedImages(List<Uri> images) {
        selectedImages.setValue(images);
    }

    public LiveData<List<Uri>> getSelectedImages() {
        return selectedImages;
    }

    public void addImage(Uri image) {
        List<Uri> currentList = selectedImages.getValue();
        if (currentList != null) {
            currentList.add(image);
            selectedImages.setValue(currentList);
        }
    }

    public void clearImages() {
        selectedImages.setValue(new ArrayList<>());
        testImage.setValue(null);
        selectedAlgorithm.setValue(null);
        matchResults.setValue(new ArrayList<>());
        batchResults.setValue(new HashMap<>());
    }

    public void setTestImage(Uri image) {
        testImage.setValue(image);
    }

    public LiveData<Uri> getTestImage() {
        return testImage;
    }

    public void setSelectedAlgorithm(String algorithm) {
        selectedAlgorithm.setValue(algorithm);
    }

    public LiveData<String> getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    public void setMatchResults(List<ImageMatcher.MatchResult> results) {
        matchResults.setValue(results);
    }

    public LiveData<List<ImageMatcher.MatchResult>> getMatchResults() {
        return matchResults;
    }
    
    public void setBatchResults(Map<String, Map<String, Double>> results) {
        batchResults.setValue(results);
    }
    
    public void updateBatchResult(String sourceImageId, String targetImageId, double similarity) {
        Map<String, Map<String, Double>> currentResults = batchResults.getValue();
        if (currentResults == null) {
            currentResults = new HashMap<>();
        }
        
        Map<String, Double> sourceResults = currentResults.getOrDefault(sourceImageId, new HashMap<>());
        sourceResults.put(targetImageId, similarity);
        currentResults.put(sourceImageId, sourceResults);
        
        batchResults.postValue(currentResults);
    }
    
    public LiveData<Map<String, Map<String, Double>>> getBatchResults() {
        return batchResults;
    }
} 