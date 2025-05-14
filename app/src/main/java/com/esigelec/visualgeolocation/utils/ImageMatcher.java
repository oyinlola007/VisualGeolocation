package com.esigelec.visualgeolocation.utils;

import android.net.Uri;
import java.util.List;

public interface ImageMatcher {
    List<MatchResult> findMatches(Uri testImage, List<Uri> referenceImages);
    
    class MatchResult {
        private final Uri imageUri;
        private final double similarity;
        private final String algorithm;

        public MatchResult(Uri imageUri, double similarity, String algorithm) {
            this.imageUri = imageUri;
            this.similarity = similarity;
            this.algorithm = algorithm;
        }

        public Uri getImageUri() {
            return imageUri;
        }

        public double getSimilarity() {
            return similarity;
        }

        public String getAlgorithm() {
            return algorithm;
        }
    }
} 