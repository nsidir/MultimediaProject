package server;

public class VideoInfo {
    private final String name;
    private final String resolution;

    public VideoInfo(String name, String resolution) {
        this.name = name;
        this.resolution = resolution;
    }

    public String getName() {
        return name;
    }

    public String getResolution() {
        return resolution;
    }
}