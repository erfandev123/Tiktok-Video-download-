package com.video.download;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class ErrorHandler {
    private static final String TAG = "ErrorHandler";
    private Context context;

    public ErrorHandler(Context context) {
        this.context = context;
    }

    /**
     * Handle different types of errors and provide user-friendly messages
     */
    public void handleError(Throwable throwable, String operation) {
        String userMessage = getUserFriendlyMessage(throwable, operation);
        String technicalMessage = getTechnicalMessage(throwable, operation);
        
        // Log the technical details
        Log.e(TAG, technicalMessage, throwable);
        
        // Show user-friendly message
        showUserMessage(userMessage);
    }

    /**
     * Get user-friendly error message
     */
    private String getUserFriendlyMessage(Throwable throwable, String operation) {
        if (throwable instanceof UnknownHostException) {
            return "No internet connection. Please check your network and try again.";
        } else if (throwable instanceof SocketTimeoutException) {
            return "Connection timeout. Please check your internet speed and try again.";
        } else if (throwable instanceof IOException) {
            if (throwable.getMessage() != null && throwable.getMessage().contains("Permission denied")) {
                return "Permission denied. Please grant storage permission to download videos.";
            } else if (throwable.getMessage() != null && throwable.getMessage().contains("No space left")) {
                return "Not enough storage space. Please free up some space and try again.";
            } else {
                return "File operation failed. Please check your storage and try again.";
            }
        } else if (throwable instanceof SecurityException) {
            return "Permission denied. Please grant necessary permissions in app settings.";
        } else if (throwable instanceof IllegalArgumentException) {
            if (operation.contains("URL")) {
                return "Invalid video URL. Please check the URL and try again.";
            } else {
                return "Invalid input. Please check your entries and try again.";
            }
        } else if (throwable instanceof RuntimeException) {
            if (throwable.getMessage() != null && throwable.getMessage().contains("platform not supported")) {
                return "This platform is not supported yet. Currently supports YouTube, TikTok, Instagram, and Facebook.";
            } else if (throwable.getMessage() != null && throwable.getMessage().contains("video not found")) {
                return "Video not found or unavailable. The video might be private or deleted.";
            } else {
                return "An unexpected error occurred. Please try again.";
            }
        } else {
            return "Something went wrong. Please try again later.";
        }
    }

    /**
     * Get technical error message for logging
     */
    private String getTechnicalMessage(Throwable throwable, String operation) {
        StringBuilder message = new StringBuilder();
        message.append("Error during operation: ").append(operation).append("\n");
        message.append("Exception type: ").append(throwable.getClass().getSimpleName()).append("\n");
        message.append("Exception message: ").append(throwable.getMessage()).append("\n");
        
        if (throwable.getCause() != null) {
            message.append("Caused by: ").append(throwable.getCause().getClass().getSimpleName());
            message.append(" - ").append(throwable.getCause().getMessage());
        }
        
        return message.toString();
    }

    /**
     * Show user message via Toast
     */
    private void showUserMessage(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Validate URL format and platform support
     */
    public static ValidationResult validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new ValidationResult(false, "Please enter a video URL");
        }

        url = url.trim().toLowerCase();
        
        // Check if it's a valid URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return new ValidationResult(false, "URL must start with http:// or https://");
        }

        // Check supported platforms
        boolean isSupported = url.contains("youtube.com") || 
                             url.contains("youtu.be") ||
                             url.contains("tiktok.com") ||
                             url.contains("vm.tiktok.com") ||
                             url.contains("instagram.com") ||
                             url.contains("facebook.com") ||
                             url.contains("fb.watch");

        if (!isSupported) {
            return new ValidationResult(false, 
                "Unsupported platform. Currently supports YouTube, TikTok, Instagram, and Facebook.");
        }

        return new ValidationResult(true, "URL is valid");
    }

    /**
     * Check system requirements and permissions
     */
    public static SystemCheckResult checkSystemRequirements(Context context) {
        SystemCheckResult result = new SystemCheckResult();
        
        // Check external storage availability
        if (!FileManager.isExternalStorageAvailable()) {
            result.addError("External storage is not available");
        }

        // Check internet permission (this should always be granted for our app)
        if (!hasPermission(context, android.Manifest.permission.INTERNET)) {
            result.addError("Internet permission not granted");
        }

        // Check storage permission
        if (!hasPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            result.addWarning("Storage permission not granted - downloads will fail");
        }

        // Check network state permission
        if (!hasPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            result.addWarning("Network state permission not granted - network checks unavailable");
        }

        return result;
    }

    private static boolean hasPermission(Context context, String permission) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Log app state for debugging
     */
    public static void logAppState(Context context) {
        Log.d(TAG, "=== App State Debug Info ===");
        Log.d(TAG, "Package: " + context.getPackageName());
        Log.d(TAG, "External storage available: " + FileManager.isExternalStorageAvailable());
        
        SystemCheckResult systemCheck = checkSystemRequirements(context);
        Log.d(TAG, "System check - Errors: " + systemCheck.getErrors().size() + 
                   ", Warnings: " + systemCheck.getWarnings().size());
        
        for (String error : systemCheck.getErrors()) {
            Log.e(TAG, "System Error: " + error);
        }
        
        for (String warning : systemCheck.getWarnings()) {
            Log.w(TAG, "System Warning: " + warning);
        }
        
        Log.d(TAG, "=== End App State ===");
    }

    /**
     * Result class for URL validation
     */
    public static class ValidationResult {
        private boolean isValid;
        private String message;

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }

        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
    }

    /**
     * Result class for system requirements check
     */
    public static class SystemCheckResult {
        private java.util.List<String> errors = new java.util.ArrayList<>();
        private java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        
        public java.util.List<String> getErrors() { return errors; }
        public java.util.List<String> getWarnings() { return warnings; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}