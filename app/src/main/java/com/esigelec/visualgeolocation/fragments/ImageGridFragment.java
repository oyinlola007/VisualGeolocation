package com.esigelec.visualgeolocation.fragments;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.esigelec.visualgeolocation.R;
import com.esigelec.visualgeolocation.adapters.ImageGridAdapter;
import com.esigelec.visualgeolocation.databinding.FragmentImageGridBinding;
import com.esigelec.visualgeolocation.utils.AkazeMatcher;
import com.esigelec.visualgeolocation.utils.FastMatcher;
import com.esigelec.visualgeolocation.utils.ImageMatcher;
import com.esigelec.visualgeolocation.utils.OrbMatcher;
import com.esigelec.visualgeolocation.utils.SiftImageMatcher;
import com.esigelec.visualgeolocation.viewmodel.SharedViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageGridFragment extends Fragment implements ImageGridAdapter.OnImageClickListener {
    private FragmentImageGridBinding binding;
    private SharedViewModel viewModel;
    private ImageGridAdapter adapter;
    private ExecutorService executorService;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentImageGridBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        executorService = Executors.newSingleThreadExecutor();
        setupRecyclerView();
        observeImages();
        setupBatchAnalysisButton();
    }

    private void setupRecyclerView() {
        adapter = new ImageGridAdapter(viewModel.getSelectedImages().getValue(), this);
        binding.imageGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.imageGrid.setAdapter(adapter);
    }
    
    private void setupBatchAnalysisButton() {
        binding.batchAnalysisButton.setOnClickListener(v -> {
            List<Uri> images = viewModel.getSelectedImages().getValue();
            if (images != null && images.size() >= 2) {
                showBatchAnalysisAlgorithmDialog();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Not Enough Images")
                    .setMessage("You need at least 2 images to perform batch analysis.")
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    private void observeImages() {
        viewModel.getSelectedImages().observe(getViewLifecycleOwner(), images -> {
            if (images != null) {
                adapter = new ImageGridAdapter(images, this);
                binding.imageGrid.setAdapter(adapter);
            }
        });
    }

    @Override
    public void onImageClick(Uri imageUri) {
        showConfirmationDialog(imageUri);
    }

    private void showConfirmationDialog(Uri imageUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_confirmation, null);
        ImageView imagePreview = dialogView.findViewById(R.id.imagePreview);
        
        Glide.with(this)
                .load(imageUri)
                .centerCrop()
                .into(imagePreview);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Test Image")
                .setView(dialogView)
                .setMessage("Do you want to use this image as the test image?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    viewModel.setTestImage(imageUri);
                    showAlgorithmSelectionDialog(imageUri);
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showAlgorithmSelectionDialog(Uri testImage) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_algorithm_selection, null);
        RadioGroup algorithmGroup = dialogView.findViewById(R.id.algorithmGroup);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Algorithm")
                .setView(dialogView)
                .setPositiveButton("Start Matching", (dialog, which) -> {
                    int selectedId = algorithmGroup.getCheckedRadioButtonId();
                    String algorithm = "SIFT"; // Default
                    
                    if (selectedId == R.id.siftRadio) {
                        algorithm = "SIFT";
                    } else if (selectedId == R.id.fastRadio) {
                        algorithm = "FAST";
                    } else if (selectedId == R.id.akazeRadio) {
                        algorithm = "AKAZE";
                    } else if (selectedId == R.id.orbRadio) {
                        algorithm = "ORB";
                    }
                    
                    performMatching(testImage, algorithm);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void showBatchAnalysisAlgorithmDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_algorithm_selection, null);
        RadioGroup algorithmGroup = dialogView.findViewById(R.id.algorithmGroup);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Algorithm for Batch Analysis")
                .setView(dialogView)
                .setPositiveButton("Start Batch Analysis", (dialog, which) -> {
                    int selectedId = algorithmGroup.getCheckedRadioButtonId();
                    String algorithm = "SIFT"; // Default
                    
                    if (selectedId == R.id.siftRadio) {
                        algorithm = "SIFT";
                    } else if (selectedId == R.id.fastRadio) {
                        algorithm = "FAST";
                    } else if (selectedId == R.id.akazeRadio) {
                        algorithm = "AKAZE";
                    } else if (selectedId == R.id.orbRadio) {
                        algorithm = "ORB";
                    }
                    
                    startBatchAnalysis(algorithm);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performMatching(Uri testImage, String algorithm) {
        // Store the test image and algorithm in the ViewModel
        viewModel.setTestImage(testImage);
        viewModel.setSelectedAlgorithm(algorithm);
        
        // Navigate to results fragment
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_ImageGridFragment_to_MatchResultsFragment);
    }
    
    private void startBatchAnalysis(String algorithm) {
        // Store the algorithm in the ViewModel
        viewModel.setSelectedAlgorithm(algorithm);
        
        // Navigate to batch analysis fragment
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_ImageGridFragment_to_BatchAnalysisFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdown();
        binding = null;
    }
} 