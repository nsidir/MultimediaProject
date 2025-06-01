package server;

import shared.Constants;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class VideoGenerator {
    private static final Logger logger = Logger.getLogger(VideoGenerator.class.getName());

    public static void generateMissingVideos() {
        File videoDir = new File(Constants.VIDEO_DIR);
        if (!videoDir.exists() || !videoDir.isDirectory()) {
            logger.warning("Video directory not found: " + Constants.VIDEO_DIR);
            return;
        }

        // Gather all original videos (regardless of format/resolution)
        Map<String, Set<String>> movieSources = new HashMap<>();
        for (File f : Objects.requireNonNull(videoDir.listFiles())) {
            String name = f.getName();
            for (String res : Constants.RESOLUTIONS) {
                for (String fmt : Constants.FORMATS) {
                    if (name.endsWith("-" + res + "." + fmt)) {
                        String movie = name.substring(0, name.indexOf("-" + res + "." + fmt));
                        movieSources.computeIfAbsent(movie, k -> new HashSet<>()).add(res + "|" + fmt);
                    }
                }
            }
        }

        // Now, for each movie, create any missing format/resolution, **but only up to the maximum available original**
        for (String movie : movieSources.keySet()) {
            Set<String> existing = movieSources.get(movie);

            // Find max resolution available in originals
            int maxResIdx = -1;
            for (String key : existing) {
                String[] parts = key.split("\\|");
                int idx = Arrays.asList(Constants.RESOLUTIONS).indexOf(parts[0]);
                if (idx > maxResIdx) maxResIdx = idx;
            }

            for (int i = 0; i <= maxResIdx; i++) {
                String res = Constants.RESOLUTIONS[i];
                for (String fmt : Constants.FORMATS) {
                    String fileName = movie + "-" + res + "." + fmt;
                    File target = new File(Constants.VIDEO_DIR, fileName);
                    if (!target.exists()) {
                        // Find a source file to transcode from (use any existing res <= desired res, prefer highest)
                        File source = null;
                        for (int j = maxResIdx; j >= 0; j--) {
                            String srcRes = Constants.RESOLUTIONS[j];
                            for (String sFmt : Constants.FORMATS) {
                                File candidate = new File(Constants.VIDEO_DIR, movie + "-" + srcRes + "." + sFmt);
                                if (candidate.exists()) {
                                    source = candidate;
                                    break;
                                }
                            }
                            if (source != null) break;
                        }
                        if (source != null) {
                            logger.info("Generating missing: " + fileName);
                            try {
                                ProcessBuilder pb = new ProcessBuilder(
                                        Constants.FFMPEG_PATH, "-y",
                                        "-i", source.getAbsolutePath(),
                                        "-vf", "scale=-2:" + res.replace("p", ""),
                                        new File(Constants.VIDEO_DIR, fileName).getAbsolutePath()
                                );
                                pb.redirectErrorStream(true);
                                Process p = pb.start();
                                p.waitFor();
                            } catch (IOException | InterruptedException e) {
                                logger.warning("Failed to create: " + fileName + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }
}
