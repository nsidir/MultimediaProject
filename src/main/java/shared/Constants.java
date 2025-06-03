package shared;

public class Constants {
    public static final int PORT = 5300;
    public static final int RTP_PORT = 54475;
    public static final String SERVER_IP = "localhost";
    public static final String VIDEO_DIR = "videos/";
    public static final String FFMPEG_PATH = "ffmpeg";
    public static final String SPEED_TEST_SERVER = "http://speedtest.tele2.net/10MB.zip";
    public static final double BITRATE_240P = 0.7;
    public static final double BITRATE_360P = 1.0;
    public static final double BITRATE_480P = 2.0;
    public static final double BITRATE_720P = 4.0;
    public static final double BITRATE_1080P = 6.0;
    public static final String[] RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};
    public static final String[] FORMATS = {"mp4", "mkv", "avi"};
    public static final int NUM_SERVERS = 3;
    public static final boolean USE_SSL = true;
    public static final String KEYSTORE_PATH = "keystore.jks";
    public static final String KEYSTORE_PASSWORD = "password";
    public static final String TRUSTSTORE_PATH = "clienttruststore.jks";
    public static final String TRUSTSTORE_PASSWORD = "password";
    public static final String LOAD_BALANCER_IP = "localhost";
    public static final int LOAD_BALANCER_PORT = Constants.PORT + Constants.NUM_SERVERS;
}
