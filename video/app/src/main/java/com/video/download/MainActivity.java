package com.video.download;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.os.Build;

public class MainActivity extends Activity {
    private static final int STORAGE_PERMISSION_CODE = 1001;
    
    private EditText etVideoUrl;
    private Spinner spinnerQuality;
    private Button btnDownload;
    private LinearLayout progressCard;
    private TextView tvDownloadStatus;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    
    private EnhancedVideoDownloader videoDownloader;
    private ErrorHandler errorHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupSpinner();
        setupDownloader();
        setupErrorHandler();
        checkPermissions();
        
        // Log app state for debugging
        ErrorHandler.logAppState(this);
    }

    private void initializeViews() {
        etVideoUrl = findViewById(R.id.etVideoUrl);
        spinnerQuality = findViewById(R.id.spinnerQuality);
        btnDownload = findViewById(R.id.btnDownload);
        progressCard = findViewById(R.id.progressCard);
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus);
        progressBar = findViewById(R.id.progressBar);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload();
            }
        });
    }

    private void setupSpinner() {
        String[] qualities = {
            "High Quality (1080p)",
            "Medium Quality (720p)", 
            "Low Quality (480p)",
            "Audio Only (MP3)"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            android.R.layout.simple_spinner_item, 
            qualities
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuality.setAdapter(adapter);
        spinnerQuality.setSelection(1); // Default to Medium Quality
    }

    private void setupErrorHandler() {
        errorHandler = new ErrorHandler(this);
    }

    private void setupDownloader() {
        videoDownloader = new EnhancedVideoDownloader(this);
        videoDownloader.setDownloadListener(new EnhancedVideoDownloader.DownloadListener() {
            @Override
            public void onStart() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnDownload.setEnabled(false);
                        btnDownload.setText("Downloading...");
                        progressCard.setVisibility(View.VISIBLE);
                        tvDownloadStatus.setText("Preparing download...");
                        progressBar.setProgress(0);
                        tvProgressPercent.setText("0%");
                    }
                });
            }

            @Override
            public void onProgress(final int progress) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(progress);
                        tvProgressPercent.setText(progress + "%");
                        tvDownloadStatus.setText("Downloading... " + progress + "%");
                    }
                });
            }

            @Override
            public void onSuccess(final String filePath) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnDownload.setEnabled(true);
                        btnDownload.setText("🚀 Download Video");
                        tvDownloadStatus.setText("Download completed!");
                        progressBar.setProgress(100);
                        tvProgressPercent.setText("100%");
                        
                        showToast("Video downloaded successfully!\nSaved to: " + filePath);
                        
                        // Hide progress card after 3 seconds
                        progressCard.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                progressCard.setVisibility(View.GONE);
                            }
                        }, 3000);
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnDownload.setEnabled(true);
                        btnDownload.setText("🚀 Download Video");
                        progressCard.setVisibility(View.GONE);
                        showToast("Download failed: " + error);
                    }
                });
            }
        });
    }

    private void startDownload() {
        String url = etVideoUrl.getText().toString().trim();
        
        // Enhanced URL validation
        ErrorHandler.ValidationResult validation = ErrorHandler.validateUrl(url);
        if (!validation.isValid()) {
            showToast(validation.getMessage());
            return;
        }

        // Check system requirements
        ErrorHandler.SystemCheckResult systemCheck = ErrorHandler.checkSystemRequirements(this);
        if (systemCheck.hasErrors()) {
            for (String error : systemCheck.getErrors()) {
                showToast("System Error: " + error);
            }
            return;
        }

        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }

        String selectedQuality = spinnerQuality.getSelectedItem().toString();
        String quality = parseQuality(selectedQuality);
        
        try {
            // Show user-friendly message about the platform being downloaded
            String platform = detectPlatformFromUrl(url);
            showToast("Downloading " + platform + " video... (Demo version with sample videos)");
            videoDownloader.downloadVideo(url, quality);
        } catch (Exception e) {
            errorHandler.handleError(e, "Starting download");
        }
    }

    private String detectPlatformFromUrl(String url) {
        url = url.toLowerCase();
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return "YouTube";
        } else if (url.contains("tiktok.com") || url.contains("vm.tiktok.com")) {
            return "TikTok";
        } else if (url.contains("instagram.com")) {
            return "Instagram";
        } else if (url.contains("facebook.com") || url.contains("fb.watch")) {
            return "Facebook";
        }
        return "Video";
    }

    private boolean isValidUrl(String url) {
        url = url.toLowerCase();
        return url.contains("youtube.com") || 
               url.contains("youtu.be") ||
               url.contains("tiktok.com") ||
               url.contains("vm.tiktok.com") ||
               url.contains("instagram.com") ||
               url.contains("facebook.com") ||
               url.contains("fb.watch") ||
               url.matches("https?://.*\\.(mp4|avi|mov|mkv|webm)$");
    }

    private String parseQuality(String selectedQuality) {
        if (selectedQuality.contains("1080p")) return "1080";
        else if (selectedQuality.contains("720p")) return "720";
        else if (selectedQuality.contains("480p")) return "480";
        else if (selectedQuality.contains("Audio")) return "audio";
        return "720"; // default
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                showToast("Storage permission is required to save downloaded videos");
            }
            
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                STORAGE_PERMISSION_CODE);
        }
    }

    private void checkPermissions() {
        if (!hasStoragePermission()) {
            requestStoragePermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Storage permission granted. You can now download videos.");
            } else {
                showToast("Storage permission denied. Cannot download videos without permission.");
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Handle paste from clipboard
    @Override
    protected void onResume() {
        super.onResume();
        // Auto-paste from clipboard if URL field is empty
        if (etVideoUrl.getText().toString().trim().isEmpty()) {
            tryAutoFillFromClipboard();
        }
    }

    private void tryAutoFillFromClipboard() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                getSystemService(CLIPBOARD_SERVICE);
            
            if (clipboard != null && clipboard.getPrimaryClip() != null) {
                android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String clipText = item.getText().toString();
                
                if (isValidUrl(clipText)) {
                    etVideoUrl.setText(clipText);
                    showToast("URL pasted from clipboard");
                }
            }
        } catch (Exception e) {
            // Ignore clipboard errors
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
        if (videoDownloader != null) {
            videoDownloader.setDownloadListener(null);
        }
    }
}