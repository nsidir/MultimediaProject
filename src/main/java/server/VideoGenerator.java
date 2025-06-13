package server;

import shared.Constants;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VideoGenerator {
    private static final Logger logger = LogManager.getLogger(VideoGenerator.class);

    private static final int THREADS = Math.max(
            2,
            Integer.parseInt(System.getProperty(
                    "videogen.threads",
                    String.valueOf(Runtime.getRuntime().availableProcessors() / 2))));

    // queue slots = 2 x threads
    private static final int QUEUE_CAPACITY = THREADS * 2;

    public static void generateMissingVideos() throws InterruptedException {
        File videoDir = new File(Constants.VIDEO_DIR);
        if (!videoDir.exists() || !videoDir.isDirectory()) {
            logger.warn("Video directory not found: {}", Constants.VIDEO_DIR);
            return;
        }

        // Συλλέγουμε τα υπάρχοντα βίντεο και τις πηγές τους
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

        // Log ταινίες που υπάρχουν
        BlockingQueue<Runnable> work = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "vid-gen-" + r.hashCode());
            t.setDaemon(false);
            return t;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                THREADS,
                THREADS,
                0L, TimeUnit.MILLISECONDS,
                work,
                tf,
                new ThreadPoolExecutor.CallerRunsPolicy()); // πιεση στην κύρια ροή αν γεμίσει η ουρά

        // Transcode τα βίντεο που λείπουν
        for (String movie : movieSources.keySet()) {
            Set<String> existing = movieSources.get(movie);
            int maxResIdx = existing.stream()
                    .map(key -> key.split("\\|")[0])
                    .mapToInt(res -> Arrays.asList(Constants.RESOLUTIONS).indexOf(res))
                    .max().orElse(-1);

            for (int i = 0; i <= maxResIdx; i++) {
                String res = Constants.RESOLUTIONS[i];
                for (String fmt : Constants.FORMATS) {
                    String fileName = movie + "-" + res + "." + fmt;
                    File target = new File(Constants.VIDEO_DIR, fileName);
                    if (!target.exists()) {
                        pool.execute(() -> transcodeSingle(movie, res, fmt, maxResIdx));
                    }
                }
            }
        }

        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    private static void transcodeSingle(String movie, String res, String fmt, int maxResIdx) {
        String outName = movie + "-" + res + "." + fmt;
        File outFile = new File(Constants.VIDEO_DIR, outName);
        if (outFile.exists()) return; // το έκανε άλλο thread

        // Best available source (<= maxResIdx)
        File source = null;
        for (int j = maxResIdx; j >= 0 && source == null; j--) {
            String srcRes = Constants.RESOLUTIONS[j];
            for (String sFmt : Constants.FORMATS) {
                File candidate = new File(Constants.VIDEO_DIR, movie + "-" + srcRes + "." + sFmt);
                if (candidate.exists()) {
                    source = candidate;
                    break;
                }
            }
        }
        if (source == null) {
            logger.warn("No source found for {}", outName);
            return;
        }

        logger.info("[{}] → {}", Thread.currentThread().getName(), outName);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Constants.FFMPEG_PATH,
                    "-hide_banner",
                    "-loglevel", "error", //error output only
                    "-y",
                    "-threads", "1",
                    "-i", source.getAbsolutePath(),
                    "-vf", "scale=-2:" + res.replace("p", ""),
                    outFile.getAbsolutePath());

            // Χωρίς *combined* stdout/stderr ώστε οι buffers να μην γεμίζουν.
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);

            int exit = pb.start().waitFor();
            if (exit != 0) {
                logger.warn("ffmpeg exited {} while creating {}", exit, outName);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to create {}: {}", outName, e.toString());
            Thread.currentThread().interrupt();
        }
    }
}
