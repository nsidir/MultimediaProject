package client;

import shared.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;
import javax.net.ssl.*;

public class StreamingClient {
    private static final Logger logger = Logger.getLogger(StreamingClient.class.getName());

    public static void main(String[] args) {
        // Set SSL truststore before any connection if using SSL
        if (Constants.USE_SSL) {
            System.setProperty("javax.net.ssl.trustStore", Constants.TRUSTSTORE_PATH);
            System.setProperty("javax.net.ssl.trustStorePassword", Constants.TRUSTSTORE_PASSWORD);
        }

        logger.info("Starting Video Streaming Client...");
        try {
            double connectionSpeed = NetworkSpeedTest.measureDownloadSpeed();
            logger.info("Connection speed: " + connectionSpeed + " Mbps");

            String[] formats = Constants.FORMATS;
            String selectedFormat = (String) JOptionPane.showInputDialog(
                    null, "Select video format:", "Format Selection",
                    JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);
            if (selectedFormat == null) System.exit(0);

            Map<String, List<String>> availableVideos = getAvailableVideos(connectionSpeed, selectedFormat);

            if (!availableVideos.isEmpty()) {
                SwingUtilities.invokeLater(() ->
                        new VideoClientUI(availableVideos, connectionSpeed, selectedFormat));
            } else {
                JOptionPane.showMessageDialog(null, "No videos available from server.",
                        "No Videos", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            logger.severe("Connection error: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Cannot connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Map<String, List<String>> getAvailableVideos(double speed, String format) throws IOException {
        try (Socket socket = createSocket(Constants.LOAD_BALANCER_IP, Constants.LOAD_BALANCER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println(Protocol.LIST);
            out.println(speed);
            out.println(format);
            out.flush();

            int videoCount = Integer.parseInt(in.readLine());
            Map<String, List<String>> availableVideos = new HashMap<>();
            for (int i = 0; i < videoCount; i++) {
                String[] parts = in.readLine().split(",");
                if (parts.length == 2) {
                    availableVideos.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
                }
            }
            return availableVideos;
        }
    }

    public static Socket createSocket(String host, int port) throws IOException {

        if (!Constants.USE_SSL) return new Socket(Constants.LOAD_BALANCER_IP, Constants.LOAD_BALANCER_PORT);
        try {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            return sf.createSocket(Constants.LOAD_BALANCER_IP, Constants.LOAD_BALANCER_PORT);
        } catch (Exception e) {
            throw new IOException("Could not create SSL socket", e);
        }
    }

}
