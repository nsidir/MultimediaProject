package server;

import shared.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFmpegProcessor {
    public static List<VideoInfo> getAvailableVideos() {
        List<VideoInfo> videos = new ArrayList<>();
        File directory = new File(Constants.VIDEO_DIR);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
            if (files != null) {
                Pattern pattern = Pattern.compile("(.+)-(\\d+p)\\.mp4");
                for (File file : files) {
                    Matcher matcher = pattern.matcher(file.getName());
                    if (matcher.matches()) {
                        videos.add(new VideoInfo(matcher.group(1), matcher.group(2)));
                    }
                }
            }
        }
        return videos;
    }
}