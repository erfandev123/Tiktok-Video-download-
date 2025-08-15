package com.video.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileManager {
    private static final String TAG = "FileManager";
    private static final String APP_FOLDER_NAME = "VideoDownloader";
    private Context context;

    public FileManager(Context context) {
        this.context = context;
    }

    /**
     * Get the main download directory for the app
     */
    public File getDownloadDirectory() {
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_FOLDER_NAME);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        return downloadDir;
    }

    /**
     * Get platform-specific subdirectory
     */
    public File getPlatformDirectory(String platform) {
        File platformDir = new File(getDownloadDirectory(), platform.toLowerCase());
        if (!platformDir.exists()) {
            platformDir.mkdirs();
        }
        return platformDir;
    }

    /**
     * Generate a unique filename for downloaded video
     */
    public String generateFileName(String title, String platform, String videoId) {
        // Clean the title for filename
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
        if (cleanTitle.length() > 30) {
            cleanTitle = cleanTitle.substring(0, 30);
        }
        
        // Add timestamp to ensure uniqueness
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        
        return platform + "_" + cleanTitle + "_" + videoId + "_" + timestamp + ".mp4";
    }

    /**
     * Get the full path for a new download
     */
    public String getDownloadPath(String title, String platform, String videoId) {
        File platformDir = getPlatformDirectory(platform);
        String fileName = generateFileName(title, platform, videoId);
        return new File(platformDir, fileName).getAbsolutePath();
    }

    /**
     * Get list of all downloaded videos
     */
    public List<DownloadedVideo> getDownloadedVideos() {
        List<DownloadedVideo> videos = new ArrayList<>();
        File downloadDir = getDownloadDirectory();
        
        if (!downloadDir.exists()) {
            return videos;
        }

        // Check each platform subdirectory
        String[] platforms = {"youtube", "tiktok", "instagram", "facebook"};
        
        for (String platform : platforms) {
            File platformDir = new File(downloadDir, platform);
            if (platformDir.exists() && platformDir.isDirectory()) {
                File[] files = platformDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".mp4")) {
                            DownloadedVideo video = parseVideoFile(file, platform);
                            if (video != null) {
                                videos.add(video);
                            }
                        }
                    }
                }
            }
        }

        // Sort by date (newest first)
        // Simple bubble sort for compatibility
        for (int i = 0; i < videos.size() - 1; i++) {
            for (int j = 0; j < videos.size() - i - 1; j++) {
                if (videos.get(j).downloadDate < videos.get(j + 1).downloadDate) {
                    DownloadedVideo temp = videos.get(j);
                    videos.set(j, videos.get(j + 1));
                    videos.set(j + 1, temp);
                }
            }
        }
        
        return videos;
    }

    /**
     * Parse video file information
     */
    private DownloadedVideo parseVideoFile(File file, String platform) {
        try {
            String fileName = file.getName();
            String[] parts = fileName.replace(".mp4", "").split("_");
            
            if (parts.length >= 4) {
                DownloadedVideo video = new DownloadedVideo();
                video.filePath = file.getAbsolutePath();
                video.fileName = fileName;
                video.platform = platform;
                video.title = parts[1]; // This would be the cleaned title
                video.videoId = parts[2];
                video.downloadDate = file.lastModified();
                video.fileSize = file.length();
                return video;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing video file: " + file.getName(), e);
        }
        return null;
    }

    /**
     * Open video with default video player
     */
    public void openVideo(DownloadedVideo video) {
        try {
            File file = new File(video.filePath);
            if (!file.exists()) {
                Log.e(TAG, "Video file not found: " + video.filePath);
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri videoUri = Uri.fromFile(file);
            
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Log.w(TAG, "No video player app found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening video", e);
        }
    }

    /**
     * Share video file
     */
    public void shareVideo(DownloadedVideo video) {
        try {
            File file = new File(video.filePath);
            if (!file.exists()) {
                Log.e(TAG, "Video file not found: " + video.filePath);
                return;
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            Uri videoUri = Uri.fromFile(file);
            
            shareIntent.setType("video/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this video I downloaded!");
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            Intent chooser = Intent.createChooser(shareIntent, "Share Video");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (chooser.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(chooser);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sharing video", e);
        }
    }

    /**
     * Delete video file
     */
    public boolean deleteVideo(DownloadedVideo video) {
        try {
            File file = new File(video.filePath);
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting video", e);
        }
        return false;
    }

    /**
     * Get total storage used by downloaded videos
     */
    public long getTotalStorageUsed() {
        long totalSize = 0;
        List<DownloadedVideo> videos = getDownloadedVideos();
        for (DownloadedVideo video : videos) {
            totalSize += video.fileSize;
        }
        return totalSize;
    }

    /**
     * Format file size to human readable format
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.getDefault(), "%.1f %sB", 
            bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Format date to human readable format
     */
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Clean up old files (optional maintenance function)
     */
    public void cleanupOldFiles(int maxFiles) {
        List<DownloadedVideo> videos = getDownloadedVideos();
        if (videos.size() > maxFiles) {
            // Remove oldest files
            for (int i = maxFiles; i < videos.size(); i++) {
                deleteVideo(videos.get(i));
            }
        }
    }

    /**
     * Check if external storage is available
     */
    public static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * Data class for downloaded video information
     */
    public static class DownloadedVideo {
        public String filePath;
        public String fileName;
        public String title;
        public String platform;
        public String videoId;
        public long downloadDate;
        public long fileSize;

        @Override
        public String toString() {
            return "DownloadedVideo{" +
                    "fileName='" + fileName + '\'' +
                    ", platform='" + platform + '\'' +
                    ", title='" + title + '\'' +
                    ", fileSize=" + formatFileSize(fileSize) +
                    ", downloadDate=" + formatDate(downloadDate) +
                    '}';
        }
    }
}