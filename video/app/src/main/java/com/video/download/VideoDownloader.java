package com.video.download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class VideoDownloader {
    private static final String TAG = "VideoDownloader";
    private Context context;
    private DownloadListener listener;

    public interface DownloadListener {
        void onProgress(int progress);
        void onSuccess(String filePath);
        void onError(String error);
        void onStart();
    }

    public VideoDownloader(Context context) {
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

                    VideoInfo videoInfo = extractVideoInfo(url, quality);
                    if (videoInfo == null) {
                        if (listener != null) listener.onError("Failed to extract video information. Please check if the URL is valid and public.");
                        return;
                    }

                    String fileName = generateFileName(videoInfo.title, videoInfo.platform);
                    File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideoDownloader");
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    File outputFile = new File(downloadDir, fileName);
                    
                    // Download real video file
                    downloadRealVideo(videoInfo.downloadUrl, outputFile.getAbsolutePath());

                } catch (Exception e) {
                    Log.e(TAG, "Download error", e);
                    if (listener != null) listener.onError("Download failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private VideoInfo extractVideoInfo(String url, String quality) {
        try {
            String platform = detectPlatform(url);
            Log.d(TAG, "Detected platform: " + platform);

            switch (platform) {
                case "youtube":
                    return extractYouTubeInfo(url, quality);
                case "tiktok":
                    return extractTikTokInfo(url, quality);
                case "instagram":
                    return extractInstagramInfo(url, quality);
                case "facebook":
                    return extractFacebookInfo(url, quality);
                default:
                    return extractGenericVideoInfo(url, quality);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting video info", e);
            return null;
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
        return "generic";
    }

    private VideoInfo extractYouTubeInfo(String url, String quality) {
        try {
            String videoId = extractYouTubeVideoId(url);
            if (videoId == null) return null;

            VideoInfo info = new VideoInfo();
            info.platform = "youtube";
            info.title = "YouTube_" + videoId;
            
            // Use yt-dlp or similar service to get real download URL
            String downloadUrl = getYouTubeRealDownloadUrl(videoId, quality);
            if (downloadUrl != null) {
                info.downloadUrl = downloadUrl;
                return info;
            }

            // Fallback to alternative method
            downloadUrl = getYouTubeAlternativeUrl(videoId, quality);
            if (downloadUrl != null) {
                info.downloadUrl = downloadUrl;
                return info;
            }

            Log.w(TAG, "Could not get YouTube download URL");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "YouTube extraction error", e);
            return null;
        }
    }

    private String getYouTubeRealDownloadUrl(String videoId, String quality) {
        try {
            // Use a video extraction service
            String apiUrl = "https://api.vevioz.com/api/button/videos/" + videoId;
            
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            
            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    if (response.length() > 100000) break;
                }
                reader.close();
                connection.disconnect();
                
                // Parse the response to find download links
                String html = response.toString();
                Pattern pattern = Pattern.compile("href=\"([^\"]*\\.mp4[^\"]*)\"");
                Matcher matcher = pattern.matcher(html);
                
                if (matcher.find()) {
                    String downloadUrl = matcher.group(1);
                    if (!downloadUrl.startsWith("http")) {
                        downloadUrl = "https:" + downloadUrl;
                    }
                    return downloadUrl;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "YouTube real download URL failed", e);
        }
        return null;
    }

    private String getYouTubeAlternativeUrl(String videoId, String quality) {
        try {
            // Alternative method using different API
            String apiUrl = "https://loader.to/api/button/?url=https://www.youtube.com/watch?v=" + videoId + "&f=" + quality + "p";
            
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            
            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.readLine();
                reader.close();
                connection.disconnect();
                
                // Parse JSON response
                if (response != null && response.contains("download_url")) {
                    Pattern pattern = Pattern.compile("\"download_url\":\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(response);
                    if (matcher.find()) {
                        return matcher.group(1).replace("\\u002F", "/");
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "YouTube alternative URL failed", e);
        }
        return null;
    }

    private VideoInfo extractTikTokInfo(String url, String quality) {
        try {
            String expandedUrl = expandShortUrl(url);
            
            Pattern pattern = Pattern.compile("tiktok\\.com.*?/video/(\\d+)");
            Matcher matcher = pattern.matcher(expandedUrl);
            
            String videoId = null;
            if (matcher.find()) {
                videoId = matcher.group(1);
            }

            if (videoId == null) {
                pattern = Pattern.compile("tiktok\\.com.*?/(\\d+)");
                matcher = pattern.matcher(expandedUrl);
                if (matcher.find()) {
                    videoId = matcher.group(1);
                }
            }

            if (videoId != null) {
                VideoInfo info = new VideoInfo();
                info.platform = "tiktok";
                info.title = "TikTok_" + videoId;
                info.downloadUrl = getTikTokRealDownloadUrl(expandedUrl);
                return info;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "TikTok extraction error", e);
        }
        return null;
    }

    private String getTikTokRealDownloadUrl(String url) {
        try {
            // Use TikTok download service
            String apiUrl = "https://api.tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8");
            
            URL tikTokUrl = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) tikTokUrl.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/json");
            
            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                reader.close();
                connection.disconnect();
                
                // Parse JSON response
                String json = response.toString();
                Pattern pattern = Pattern.compile("\"play\":\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(json);
                
                if (matcher.find()) {
                    return matcher.group(1).replace("\\u002F", "/");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "TikTok real download URL failed", e);
        }
        return null;
    }

    private VideoInfo extractInstagramInfo(String url, String quality) {
        try {
            Pattern pattern = Pattern.compile("instagram\\.com/(?:p|reel|tv)/(\\w+)");
            Matcher matcher = pattern.matcher(url);
            
            if (matcher.find()) {
                String postId = matcher.group(1);
                VideoInfo info = new VideoInfo();
                info.platform = "instagram";
                info.title = "Instagram_" + postId;
                info.downloadUrl = getInstagramRealDownloadUrl(url);
                return info;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Instagram extraction error", e);
        }
        return null;
    }

    private String getInstagramRealDownloadUrl(String url) {
        try {
            // Use Instagram download service
            String apiUrl = "https://api.instagram.com/oembed/?url=" + URLEncoder.encode(url, "UTF-8");
            
            URL instagramUrl = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) instagramUrl.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            
            if (connection.getResponseCode() == 200) {
                // Get the actual post data
                String postUrl = url + "?__a=1&__d=dis";
                URL postDataUrl = new URL(postUrl);
                HttpURLConnection postConnection = (HttpURLConnection) postDataUrl.openConnection();
                postConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
                postConnection.setRequestProperty("Accept", "application/json");
                
                if (postConnection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(postConnection.getInputStream()));
                    String response = reader.readLine();
                    reader.close();
                    postConnection.disconnect();
                    
                    // Look for video URL in JSON response
                    if (response != null && response.contains("video_url")) {
                        Pattern pattern = Pattern.compile("\"video_url\":\"([^\"]+)\"");
                        Matcher matcher = pattern.matcher(response);
                        if (matcher.find()) {
                            return matcher.group(1).replace("\\u0026", "&");
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Instagram real download URL failed", e);
        }
        return null;
    }

    private VideoInfo extractFacebookInfo(String url, String quality) {
        try {
            Pattern pattern = Pattern.compile("facebook\\.com.*?/videos/(\\d+)");
            Matcher matcher = pattern.matcher(url);
            
            if (matcher.find()) {
                String videoId = matcher.group(1);
                VideoInfo info = new VideoInfo();
                info.platform = "facebook";
                info.title = "Facebook_" + videoId;
                info.downloadUrl = getFacebookRealDownloadUrl(url);
                return info;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Facebook extraction error", e);
        }
        return null;
    }

    private String getFacebookRealDownloadUrl(String url) {
        try {
            // Use Facebook video extraction service
            String apiUrl = "https://api.facebook.com/video/url?url=" + URLEncoder.encode(url, "UTF-8");
            
            URL facebookUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) facebookUrl.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
                if (response.length() > 100000) break;
            }
            
            reader.close();
            connection.disconnect();
            
            // Look for video URL in the response
            String html = response.toString();
            Pattern pattern = Pattern.compile("\"browser_native_hd_url\":\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);
            
            if (matcher.find()) {
                return matcher.group(1).replace("\\u0025", "%").replace("\\/", "/");
            }
            
            // Alternative pattern
            pattern = Pattern.compile("\"browser_native_sd_url\":\"([^\"]+)\"");
            matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1).replace("\\u0025", "%").replace("\\/", "/");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Facebook real download URL failed", e);
        }
        return null;
    }

    private VideoInfo extractGenericVideoInfo(String url, String quality) {
        try {
            // For direct video URLs or other platforms
            VideoInfo info = new VideoInfo();
            info.platform = "generic";
            info.title = "Video_" + System.currentTimeMillis();
            info.downloadUrl = url;
            return info;
        } catch (Exception e) {
            Log.e(TAG, "Generic video extraction error", e);
            return null;
        }
    }

    private String expandShortUrl(String shortUrl) {
        try {
            URL url = new URL(shortUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
            connection.connect();
            
            String expandedUrl = connection.getHeaderField("Location");
            connection.disconnect();
            
            return expandedUrl != null ? expandedUrl : shortUrl;
        } catch (Exception e) {
            Log.e(TAG, "Error expanding URL", e);
            return shortUrl;
        }
    }

    private String extractYouTubeVideoId(String url) {
        Pattern pattern = Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void downloadRealVideo(String videoUrl, String outputPath) {
        try {
            Log.d(TAG, "Downloading from: " + videoUrl);
            
            URL url = new URL(videoUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=0-");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Server returned HTTP " + responseCode + " " + connection.getResponseMessage());
            }

            int fileLength = connection.getContentLength();
            
            InputStream input = new BufferedInputStream(connection.getInputStream(), 8192);
            OutputStream output = new FileOutputStream(outputPath);

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            int lastProgress = 0;
            
            while ((count = input.read(data)) != -1) {
                total += count;
                
                // Update progress
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

            output.flush();
            output.close();
            input.close();
            connection.disconnect();

            // Verify the downloaded file
            File downloadedFile = new File(outputPath);
            if (downloadedFile.exists() && downloadedFile.length() > 0) {
                Log.d(TAG, "Download completed: " + outputPath);
                Log.d(TAG, "Downloaded file size: " + downloadedFile.length() + " bytes");
                
                if (listener != null) {
                    listener.onProgress(100);
                    listener.onSuccess(outputPath);
                }
            } else {
                throw new IOException("Downloaded file is empty or doesn't exist");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            
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

    private String generateFileName(String title, String platform) {
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
        if (cleanTitle.length() > 50) {
            cleanTitle = cleanTitle.substring(0, 50);
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        return platform + "_" + cleanTitle + "_" + timestamp + ".mp4";
    }

    public static class VideoInfo {
        public String title;
        public String downloadUrl;
        public String platform;
        public String quality;
        public long duration;
        public String thumbnail;
    }
}