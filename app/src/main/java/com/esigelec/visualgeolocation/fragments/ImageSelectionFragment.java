package com.esigelec.visualgeolocation.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.esigelec.visualgeolocation.R;
import com.esigelec.visualgeolocation.databinding.FragmentImageSelectionBinding;
import com.esigelec.visualgeolocation.viewmodel.SharedViewModel;

import java.util.ArrayList;
import java.util.List;

public class ImageSelectionFragment extends Fragment {
    private FragmentImageSelectionBinding binding;
    private SharedViewModel viewModel;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentImageSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        setupImagePickerLauncher();
        setupPermissionLauncher();
        setupClickListeners();
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                    if (result.getData().getClipData() != null) {
                        List<Uri> selectedUris = new ArrayList<>();
                        for (int i = 0; i < result.getData().getClipData().getItemCount(); i++) {
                            selectedUris.add(result.getData().getClipData().getItemAt(i).getUri());
                        }
                        viewModel.setSelectedImages(selectedUris);
                        navigateToImageGrid();
                    } else if (result.getData().getData() != null) {
                        List<Uri> singleUri = new ArrayList<>();
                        singleUri.add(result.getData().getData());
                        viewModel.setSelectedImages(singleUri);
                        navigateToImageGrid();
                    }
                }
            }
        );
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openImagePicker();
                } else {
                    Toast.makeText(requireContext(), "Permission required to select images", Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    private void setupClickListeners() {
        binding.selectImagesButton.setOnClickListener(v -> checkPermissionAndOpenPicker());
    }

    private void checkPermissionAndOpenPicker() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }

    private void navigateToImageGrid() {
        List<Uri> images = viewModel.getSelectedImages().getValue();
        if (images != null && !images.isEmpty()) {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_ImageSelectionFragment_to_ImageGridFragment);
        } else {
            Toast.makeText(requireContext(), "Please select at least one image", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 