package shared;

public class Constants {
    public static final int PORT = 5300;
    public static final String SERVER_IP = "localhost";
    public static final String VIDEO_DIR = "videos/";
    public static final String FFMPEG_PATH = "ffmpeg";
    public static final String SPEED_TEST_SERVER = "http://ipv4.ikoula.testdebit.info/1M.iso";

    // Bitrate limits for each resolution (in Mbps)
    public static final double BITRATE_240P = 0.7;
    public static final double BITRATE_360P = 1.5;
    public static final double BITRATE_480P = 3.0;
    public static final double BITRATE_720P = 5.0;
    public static final double BITRATE_1080P = 8.0;
}