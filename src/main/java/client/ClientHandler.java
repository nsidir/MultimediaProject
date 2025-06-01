package client;

import shared.Constants;
import shared.Protocol;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private static VideoPlayerPanel videoPlayerPanel;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds

    public static void setVideoPlayerPanel(VideoPlayerPanel panel) {
        videoPlayerPanel = panel;
    }

    public static void streamSelectedVideo(String movieName, String resolution, String format, String protocol, Runnable onCompletionCallback) {
        logger.info("Requesting stream - Movie: " + movieName + ", Resolution: " + resolution +
                ", Format: " + format + ", Protocol: " + protocol);

        executor.execute(() -> {
            Socket socket = null;
            try {
                socket = new Socket(Constants.SERVER_IP, Constants.PORT);
                socket.setSoTimeout(CONNECTION_TIMEOUT);

                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println(Protocol.STREAM);
                    out.println(movieName);
                    out.println(resolution);
                    out.println(format);
                    out.println(protocol);
                    // out.flush(); // PrintWriter with autoFlush=true flushes on println

                    logger.info("Sent stream request to server. Waiting for response...");
                    String response = in.readLine(); // This might block if server doesn't respond promptly
                    logger.info("Received response from server: " + response);

                    if (Protocol.STREAMING.equals(response)) {
                        logger.info("Server confirmed STREAMING. Proceeding to stream to file.");
                        streamToFile(socket.getInputStream(), movieName + "-" + resolution, onCompletionCallback);
                    } else if (Protocol.NOT_FOUND.equals(response)) {
                        logger.warning("Video not found on server: " + movieName + "-" + resolution + "." + format);
                        showError("Video not found on server: " + movieName + "-" + resolution + "." + format);
                        if (onCompletionCallback != null) {
                            onCompletionCallback.run();
                        }
                    } else {
                        logger.warning("Unknown or unexpected server response: " + response);
                        showError("Unknown server response: " + response);
                        if (onCompletionCallback != null) {
                            onCompletionCallback.run();
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Streaming error: " + e.getMessage(), e);
                showError("Streaming error: " + e.getMessage());
                if (onCompletionCallback != null) {
                    onCompletionCallback.run();
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        // Important: If streamToFile is handling the inputStream,
                        // the socket should only be closed after streamToFile is completely done.
                        // However, streamToFile now takes InputStream, so socket can be closed here
                        // if streamToFile has finished reading.
                        // For safety, ensure streamToFile fully consumes the stream or handles closure.
                        // If streamToFile runs asynchronously itself beyond this executor's task,
                        // socket closure needs careful management.
                        // Given streamToFile is called synchronously within this task, this should be okay.
                        if (!socket.isInputShutdown()) socket.shutdownInput(); // Signal no more reading
                        if (!socket.isOutputShutdown()) socket.shutdownOutput(); // Signal no more writing
                        socket.close();
                        logger.info("Client socket closed.");
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Error closing client socket: " + ex.getMessage(), ex);
                    }
                }
            }
        });
    }

    // Changed to accept InputStream directly
    private static void streamToFile(InputStream socketInputStream, String videoName, Runnable onCompletionCallback) {
        File tempFile = null;
        long totalBytes = 0;
        try {
            // DEBUG: Keep the temporary file for inspection
            tempFile = File.createTempFile("stream_" + videoName + "_", ".mkv");
            // tempFile.deleteOnExit(); // Comment out for debugging to inspect the file

            logger.info("Streaming to temporary file: " + tempFile.getAbsolutePath() + " (File will be kept for debugging)");

            // Use the passed InputStream from the socket
            try (BufferedInputStream bufferedSocketIn = new BufferedInputStream(socketInputStream);
                 FileOutputStream fileOut = new FileOutputStream(tempFile);
                 BufferedOutputStream bufferedFileOut = new BufferedOutputStream(fileOut, 65536)) {

                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;

                logger.info("Starting to read from socket input stream and write to file...");
                while ((bytesRead = bufferedSocketIn.read(buffer)) != -1) {
                    bufferedFileOut.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;

                    if (totalBytes % (1024 * 1024) == 0) { // Log every MB
                        logger.info("Streamed " + (totalBytes / (1024 * 1024)) + " MB so far...");
                        // bufferedFileOut.flush(); // Flush periodically
                    }
                }
                bufferedFileOut.flush(); // Ensure all data is written
                logger.info("Finished reading from socket input stream. Total bytes received: " + totalBytes);
            }

            logger.info("Stream completed. Temporary file size: " + tempFile.length() + " bytes. Path: " + tempFile.getAbsolutePath());

            if (totalBytes == 0) {
                logger.warning("Stream completed but received 0 bytes. The temporary file might be empty or contain only headers if FFmpeg failed on server.");
                // Optionally, try to read a few bytes from the file to see if it's text (error message)
                try (BufferedReader tempFileReader = new BufferedReader(new FileReader(tempFile))) {
                    String firstLine = tempFileReader.readLine();
                    if (firstLine != null) {
                        logger.warning("First line of temp file: " + firstLine.substring(0, Math.min(firstLine.length(), 100)));
                    }
                } catch (IOException ex) { /* ignore if cannot read */}
            }


            final File finalTempFile = tempFile; // Effectively final for lambda
            SwingUtilities.invokeLater(() -> {
                try {
                    if (videoPlayerPanel != null) {
                        if (finalTempFile.exists() && finalTempFile.length() > 0) {
                            logger.info("Passing temporary file to VideoPlayerPanel: " + finalTempFile.getAbsolutePath());
                            videoPlayerPanel.playVideo(finalTempFile);
                        } else {
                            logger.severe("Temporary file is empty or does not exist, cannot play. Path: " + finalTempFile.getAbsolutePath());
                            showError("Failed to stream video: Received empty or no data from server.");
                        }
                    } else {
                        logger.warning("VideoPlayerPanel is null. Cannot play video.");
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Playback error: " + e.getMessage(), e);
                    showError("Failed to play video: " + e.getMessage());
                } finally {
                    if (onCompletionCallback != null) {
                        onCompletionCallback.run();
                    }
                    // DEBUG: Do not delete the file yet
                    // if (finalTempFile != null && finalTempFile.exists()) {
                    //     if (finalTempFile.delete()) {
                    //         logger.info("Temporary file deleted: " + finalTempFile.getAbsolutePath());
                    //     } else {
                    //         logger.warning("Failed to delete temporary file: " + finalTempFile.getAbsolutePath());
                    //     }
                    // }
                }
            });

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error during streaming to file: " + e.getMessage(), e);
            showError("Error writing stream to file: " + e.getMessage());
            if (onCompletionCallback != null) {
                SwingUtilities.invokeLater(onCompletionCallback); // Ensure UI updates are on EDT
            }
            // DEBUG: Log if the temp file was created and its size even on error
            if (tempFile != null && tempFile.exists()) {
                logger.info("(Error context) Temp file exists: " + tempFile.getAbsolutePath() + ", size: " + tempFile.length());
            } else if (tempFile != null) {
                logger.info("(Error context) Temp file path was: " + tempFile.getAbsolutePath() + " but it doesn't exist.");
            }
        }
    }


    private static void showError(String message) {
        // Ensure error messages are shown on the Event Dispatch Thread
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE));
    }
}
