package shared;

public class VideoInfo {
    private final String name;
    private final String resolution;
    private final String format;

    public VideoInfo(String name, String resolution, String format) {
        this.name = name;
        this.resolution = resolution;
        this.format = format;
    }

    public String getName() { return name; }
    public String getResolution() { return resolution; }
    public String getFormat() { return format; }

    @Override
    public String toString() {
        return name + "-" + resolution + "." + format;
    }
}