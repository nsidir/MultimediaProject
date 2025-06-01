package server;

import shared.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String command = in.readLine();
            logger.info("Request type: " + command);

            if (Protocol.LIST.equals(command)) {
                handleListRequest(in, out);
            } else if (Protocol.STREAM.equals(command)) {
                handleStreamRequest(in, out);
            }
        } catch (Exception e) {
            logger.warning("ClientHandler exception: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleListRequest(BufferedReader in, PrintWriter out) throws IOException {
        double clientSpeed = Double.parseDouble(in.readLine());
        String format = in.readLine();
        logger.info("Client speed: " + clientSpeed + " Mbps, preferred format: " + format);

        File videoDir = new File(Constants.VIDEO_DIR);
        File[] files = videoDir.listFiles();
        Map<String, List<String>> available = new HashMap<>();

        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith("." + format)) continue;
            // Parse: MovieName-Resolution.format
            int dashIdx = name.lastIndexOf('-');
            int dotIdx = name.lastIndexOf('.');
            if (dashIdx == -1 || dotIdx == -1) continue;
            String movie = name.substring(0, dashIdx);
            String resolution = name.substring(dashIdx + 1, dotIdx);
            if (!isAllowed(resolution, clientSpeed)) continue;
            available.computeIfAbsent(movie, k -> new ArrayList<>()).add(resolution);
        }

        // Send response: count, then movie,resolution
        int count = 0;
        for (List<String> resList : available.values()) count += resList.size();
        out.println(count);
        for (Map.Entry<String, List<String>> entry : available.entrySet()) {
            for (String res : entry.getValue()) {
                out.println(entry.getKey() + "," + res);
            }
        }
    }

    private boolean isAllowed(String res, double speed) {
        return switch (res) {
            case "240p" -> speed >= Constants.BITRATE_240P;
            case "360p" -> speed >= Constants.BITRATE_360P;
            case "480p" -> speed >= Constants.BITRATE_480P;
            case "720p" -> speed >= Constants.BITRATE_720P;
            case "1080p" -> speed >= Constants.BITRATE_1080P;
            default -> false;
        };
    }

    private void handleStreamRequest(BufferedReader in, PrintWriter out) throws IOException {
        String movie = in.readLine();
        String resolution = in.readLine();
        String format = in.readLine();
        String protocol = in.readLine();
        String portStr = in.readLine();
        int port = portStr != null && !portStr.isBlank() ? Integer.parseInt(portStr) : -1;
        logger.info("Stream request - Movie: " + movie + ", Resolution: " + resolution +
                ", Format: " + format + ", Protocol: " + protocol + ", Port: " + port);

        String videoPath = Constants.VIDEO_DIR + movie + "-" + resolution + "." + format;
        File file = new File(videoPath);
        if (!file.exists()) {
            out.println(Protocol.NOT_FOUND);
            return;
        }

        // Always acknowledge so client can start ffplay
        out.println(Protocol.STREAMING);

        // Small sleep to ensure client is ready (UDP/RTP)
        if (protocol.equalsIgnoreCase("UDP") || protocol.equalsIgnoreCase("RTP")) {
            try { Thread.sleep(700); } catch (InterruptedException ignored) {}
        }

        if (protocol.equalsIgnoreCase("TCP")) {
            VideoStreamer.streamViaTCP(videoPath, port);
        } else if (protocol.equalsIgnoreCase("UDP")) {
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            VideoStreamer.streamViaUDP(videoPath, clientIP, port);
        } else if (protocol.equalsIgnoreCase("RTP")) {
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            // RTP streaming (video only, ffplay expects SDP)
            File sdp = File.createTempFile("rtp_", ".sdp");
            VideoStreamer.streamViaRTP(videoPath, clientIP, port, sdp.getAbsolutePath());
            // Send SDP to client
            out.println("SDP");
            try (BufferedReader sdpIn = new BufferedReader(new FileReader(sdp))) {
                String line;
                while ((line = sdpIn.readLine()) != null) out.println(line);
            }
            out.println("END_SDP");
        }
    }
}
