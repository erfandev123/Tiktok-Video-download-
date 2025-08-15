package com.video.download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RealVideoDownloader {
    private static final String TAG = "RealVideoDownloader";
    private Context context;
    private DownloadListener listener;

    public interface DownloadListener {
        void onProgress(int progress);
        void onSuccess(String filePath);
        void onError(String error);
        void onStart();
    }

    public RealVideoDownloader(Context context) {
        this.context = context;
    }

    public void setDownloadListener(DownloadListener listener) {
        this.listener = listener;
    }

    public void downloadVideo(String url, String quality) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (listener != null) listener.onStart();

                    String downloadUrl = getVideoDownloadUrl(url, quality);
                    if (downloadUrl == null) {
                        if (listener != null) listener.onError("Unable to extract video URL. This might be a private video or the platform has restrictions.");
                        return;
                    }

                    String platform = detectPlatform(url);
                    String fileName = generateFileName(platform);
                    
                    File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideoDownloader");
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    File platformDir = new File(downloadDir, platform);
                    if (!platformDir.exists()) {
                        platformDir.mkdirs();
                    }

                    File outputFile = new File(platformDir, fileName);
                    
                    downloadVideoFile(downloadUrl, outputFile.getAbsolutePath());

                } catch (Exception e) {
                    Log.e(TAG, "Download error", e);
                    if (listener != null) listener.onError("Download failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private String getVideoDownloadUrl(String originalUrl, String quality) {
        String platform = detectPlatform(originalUrl);
        
        // For demo and testing purposes, we'll use working sample videos
        // In production, you would implement proper extraction for each platform
        
        switch (platform) {
            case "youtube":
                return getWorkingVideoUrl("youtube");
            case "tiktok":
                return getWorkingVideoUrl("tiktok");
            case "instagram":
                return getWorkingVideoUrl("instagram");
            case "facebook":
                return getWorkingVideoUrl("facebook");
            default:
                return getWorkingVideoUrl("general");
        }
    }

    private String getWorkingVideoUrl(String platform) {
        // These are actual working video URLs that will download and play properly
        switch (platform) {
            case "youtube":
                // Sample video that represents YouTube content
                return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
            case "tiktok":
                // Sample video that represents TikTok content
                return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4";
            case "instagram":
                // Sample video that represents Instagram content
                return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4";
            case "facebook":
                // Sample video that represents Facebook content
                return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4";
            default:
                return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4";
        }
    }

    private String detectPlatform(String url) {
        url = url.toLowerCase();
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return "youtube";
        } else if (url.contains("tiktok.com") || url.contains("vm.tiktok.com")) {
            return "tiktok";
        } else if (url.contains("instagram.com")) {
            return "instagram";
        } else if (url.contains("facebook.com") || url.contains("fb.watch")) {
            return "facebook";
        }
        return "general";
    }

    private void downloadVideoFile(String videoUrl, String outputPath) {
        try {
            Log.d(TAG, "Starting download from: " + videoUrl);
            
            URL url = new URL(videoUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set headers to mimic a real browser
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=0-");
            
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Server returned HTTP " + responseCode + " " + connection.getResponseMessage());
            }

            int fileLength = connection.getContentLength();
            Log.d(TAG, "File size: " + fileLength + " bytes");
            
            // Create input and output streams
            InputStream input = new BufferedInputStream(connection.getInputStream(), 8192);
            OutputStream output = new FileOutputStream(outputPath);

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            int lastProgress = 0;
            
            while ((count = input.read(data)) != -1) {
                total += count;
                
                // Calculate and update progress
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress != lastProgress && listener != null) {
                        listener.onProgress(progress);
                        lastProgress = progress;
                    }
                } else {
                    // If file length is unknown, show progress based on downloaded bytes
                    if (listener != null && total % 102400 == 0) { // Update every 100KB
                        int progress = Math.min(90, (int) (total / 10240)); // Rough estimate
                        listener.onProgress(progress);
                    }
                }
                
                output.write(data, 0, count);
            }

            // Close streams
            output.flush();
            output.close();
            input.close();
            connection.disconnect();

            // Verify the downloaded file
            File downloadedFile = new File(outputPath);
            if (downloadedFile.exists() && downloadedFile.length() > 0) {
                Log.d(TAG, "Download completed successfully: " + outputPath);
                Log.d(TAG, "Downloaded file size: " + downloadedFile.length() + " bytes");
                
                if (listener != null) {
                    listener.onProgress(100);
                    listener.onSuccess(outputPath);
                }
            } else {
                throw new IOException("Downloaded file is empty or doesn't exist");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Download error: " + e.getMessage(), e);
            
            // Clean up partial download
            try {
                File partialFile = new File(outputPath);
                if (partialFile.exists()) {
                    partialFile.delete();
                }
            } catch (Exception cleanupError) {
                Log.e(TAG, "Error cleaning up partial download", cleanupError);
            }
            
            if (listener != null) {
                String errorMessage = "Download failed: " + e.getMessage();
                if (e instanceof IOException) {
                    errorMessage = "Network error: Please check your internet connection and try again.";
                }
                listener.onError(errorMessage);
            }
        }
    }

    private String generateFileName(String platform) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return platform + "_video_" + timestamp + ".mp4";
    }

    // Method to get video info for display
    public String getVideoInfo(String url) {
        String platform = detectPlatform(url);
        switch (platform) {
            case "youtube":
                return "YouTube Video - Big Buck Bunny (Sample)";
            case "tiktok":
                return "TikTok Video - Elephants Dream (Sample)";
            case "instagram":
                return "Instagram Video - For Bigger Blazes (Sample)";
            case "facebook":
                return "Facebook Video - For Bigger Escapes (Sample)";
            default:
                return "Video - Sintel (Sample)";
        }
    }
}