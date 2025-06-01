package server;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.*;
import shared.Constants;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class VideoGenerator {
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("mp4", "mkv", "avi");
    private static final List<String> SUPPORTED_RESOLUTIONS = Arrays.asList("240p", "360p", "480p", "720p", "1080p");

    public static void generateMissingVideos() throws IOException {
        File videosDir = new File(Constants.VIDEO_DIR);
        if (!videosDir.exists()) {
            videosDir.mkdirs();
            return;
        }

        FFmpeg ffmpeg = new FFmpeg(Constants.FFMPEG_PATH);
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);

        for (File file : videosDir.listFiles()) {
            if (file.isFile()) {
                String fileName = file.getName();
                if (fileName.contains("-")) {
                    String baseName = fileName.substring(0, fileName.lastIndexOf('-'));
                    String resolution = fileName.substring(fileName.lastIndexOf('-') + 1, fileName.lastIndexOf('.'));
                    String format = fileName.substring(fileName.lastIndexOf('.') + 1);

                    if (SUPPORTED_RESOLUTIONS.contains(resolution) && SUPPORTED_FORMATS.contains(format)) {
                        generateMissingVersions(executor, baseName, resolution, format);
                    }
                }
            }
        }
    }

    private static void generateMissingVersions(FFmpegExecutor executor, String baseName,
                                                String sourceResolution, String sourceFormat) throws IOException {
        File sourceFile = new File(Constants.VIDEO_DIR + baseName + "-" + sourceResolution + "." + sourceFormat);

        for (String targetFormat : SUPPORTED_FORMATS) {
            for (String targetResolution : SUPPORTED_RESOLUTIONS) {
                // Skip if resolution is higher than source
                if (getResolutionValue(targetResolution) > getResolutionValue(sourceResolution)) continue;

                // Skip if source format and resolution match target
                if (targetFormat.equals(sourceFormat) && targetResolution.equals(sourceResolution)) continue;

                String outputPath = Constants.VIDEO_DIR + baseName + "-" + targetResolution + "." + targetFormat;
                File outputFile = new File(outputPath);

                if (!outputFile.exists()) {
                    System.out.println("Generating: " + outputPath);
                    try {
                        FFmpegBuilder builder = createConversionBuilder(sourceFile, outputPath, targetFormat, targetResolution);
                        executor.createJob(builder).run();
                    } catch (Exception e) {
                        System.err.println("Failed to generate " + outputPath + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static FFmpegBuilder createConversionBuilder(File input, String outputPath,
                                                         String format, String resolution) {
        FFmpegOutputBuilder builder = new FFmpegBuilder()
                .setInput(input.getAbsolutePath())
                .overrideOutputFiles(true)
                .addOutput(outputPath)
                .setVideoFilter("scale=" + getResolutionWidth(resolution) + ":" + getResolutionHeight(resolution));

        // Universal settings that work with most FFmpeg builds
        builder.addExtraArgs("-c:v", "libx264")  // standard H.264 encoder
                .addExtraArgs("-preset", "fast")  // Faster encoding
                .addExtraArgs("-crf", "23")       // Quality factor
                .addExtraArgs("-c:a", "aac")      // Audio codec
                .addExtraArgs("-b:a", "128k");    // Audio bitrate

        // Format-specific settings
        switch (format) {
            case "mp4":
                builder.addExtraArgs("-movflags", "+faststart");
                break;
            case "mkv":
                // No additional flags needed for MKV
                break;
            case "avi":
                builder.addExtraArgs("-c:v", "mpeg4")  // More compatible with AVI
                        .addExtraArgs("-c:a", "mp3");   // Better for AVI
                break;
        }

        return builder.done();
    }

    private static int getResolutionValue(String resolution) {
        return switch (resolution) {
            case "240p" -> 240;
            case "360p" -> 360;
            case "480p" -> 480;
            case "720p" -> 720;
            case "1080p" -> 1080;
            default -> 0;
        };
    }

    private static int getResolutionWidth(String resolution) {
        return switch (resolution) {
            case "240p" -> 426;
            case "360p" -> 640;
            case "480p" -> 854;
            case "720p" -> 1280;
            case "1080p" -> 1920;
            default -> 854;
        };
    }

    private static int getResolutionHeight(String resolution) {
        return switch (resolution) {
            case "240p" -> 240;
            case "360p" -> 360;
            case "480p" -> 480;
            case "720p" -> 720;
            case "1080p" -> 1080;
            default -> 480;
        };
    }
}