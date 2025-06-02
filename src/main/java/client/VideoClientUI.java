package client;

import shared.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class VideoClientUI {
    private JFrame frame;
    private JComboBox<String> resolutionCombo;
    private JComboBox<String> protocolCombo;
    private JList<String> movieList;
    private JButton playButton, stopButton, saveButton;
    private final Map<String, List<String>> availableVideos;
    private Process ffplayProcess;
    private static final Logger logger = Logger.getLogger(VideoClientUI.class.getName());
    private double connectionSpeed;
    private String selectedFormat;
    private volatile boolean isPlaying = false;

    public VideoClientUI(Map<String, List<String>> availableVideos, double connectionSpeed, String selectedFormat) {
        this.availableVideos = availableVideos;
        this.connectionSpeed = connectionSpeed;
        this.selectedFormat = selectedFormat;
        initializeUI();
    }

    private void initializeUI() {
        frame = new JFrame("Movie Streamer");
        frame.setSize(900, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setPreferredSize(new Dimension(350, 0));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Video Selection & Controls"));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        availableVideos.keySet().stream().sorted().forEach(listModel::addElement);

        movieList = new JList<>(listModel);
        movieList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(movieList);

        resolutionCombo = new JComboBox<>();
        protocolCombo = new JComboBox<>(new String[]{"Auto", "TCP", "UDP", "RTP"});

        JPanel topSelectionPanel = new JPanel(new BorderLayout(5,5));
        topSelectionPanel.add(new JLabel(" Select a movie:"), BorderLayout.NORTH);
        topSelectionPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel settingsPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Stream Settings"));
        settingsPanel.add(new JLabel("Resolution:"));
        settingsPanel.add(resolutionCombo);
        settingsPanel.add(new JLabel("Protocol:"));
        settingsPanel.add(protocolCombo);
        settingsPanel.add(new JLabel("Connection Speed:"));
        JLabel speedLabel = new JLabel(String.format("%.2f Mbps", connectionSpeed));
        settingsPanel.add(speedLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        playButton = new JButton("▶ Play");
        stopButton = new JButton("⏹ Stop");
        saveButton = new JButton("💾 Save");
        JButton exitButton = new JButton("Exit");
        Dimension buttonSize = new Dimension(100, 35);
        playButton.setPreferredSize(buttonSize);
        stopButton.setPreferredSize(buttonSize);
        saveButton.setPreferredSize(buttonSize);
        exitButton.setPreferredSize(buttonSize);

        buttonPanel.add(playButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(exitButton);

        JPanel southControlPanel = new JPanel(new BorderLayout());
        southControlPanel.add(settingsPanel, BorderLayout.NORTH);
        southControlPanel.add(buttonPanel, BorderLayout.CENTER);

        controlPanel.add(topSelectionPanel, BorderLayout.CENTER);
        controlPanel.add(southControlPanel, BorderLayout.SOUTH);
        frame.add(controlPanel, BorderLayout.CENTER);

        movieList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedMovie = movieList.getSelectedValue();
                resolutionCombo.removeAllItems();
                if (selectedMovie != null && availableVideos.containsKey(selectedMovie)) {
                    // Only show allowed resolutions for the user's current speed
                    availableVideos.get(selectedMovie).stream()
                            .sorted()
                            .filter(res -> allowed(res, connectionSpeed))
                            .forEach(resolutionCombo::addItem);
                    if (resolutionCombo.getItemCount() > 0) resolutionCombo.setSelectedIndex(0);
                }
            }
        });


        playButton.addActionListener(e -> playVideo());
        stopButton.addActionListener(e -> stopVideo());
        stopButton.setEnabled(false);

        saveButton.addActionListener(e -> saveVideo());
        exitButton.addActionListener(e -> {
            stopVideo();
            frame.dispose();
            System.exit(0);
        });

        if (!listModel.isEmpty()) movieList.setSelectedIndex(0);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static int findFreeUDPPort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void playVideo() {
        String selectedMovie = movieList.getSelectedValue();
        String selectedResolution = (String) resolutionCombo.getSelectedItem();
        String selectedProtocolChoice = (String) protocolCombo.getSelectedItem();

        if (selectedMovie == null || selectedResolution == null || selectedProtocolChoice == null) {
            JOptionPane.showMessageDialog(frame, "Please select movie, resolution, and protocol.");
            return;
        }

        String actualProtocol = selectedProtocolChoice.equals("Auto")
                ? getAutoProtocol(selectedResolution) : selectedProtocolChoice;

        playButton.setEnabled(false);
        playButton.setText("Loading...");
        stopButton.setEnabled(true);
        isPlaying = true;

        new Thread(() -> requestAndPlay(selectedMovie, selectedResolution, selectedFormat, actualProtocol)).start();
    }

    private boolean allowed(String res, double speed) {
        return switch (res) {
            case "240p" -> speed >= Constants.BITRATE_240P;
            case "360p" -> speed >= Constants.BITRATE_360P;
            case "480p" -> speed >= Constants.BITRATE_480P;
            case "720p" -> speed >= Constants.BITRATE_720P;
            case "1080p" -> speed >= Constants.BITRATE_1080P;
            default -> false;
        };
    }

    private void saveVideo() {
        String selectedMovie = movieList.getSelectedValue();
        String selectedResolution = (String) resolutionCombo.getSelectedItem();
        if (selectedMovie == null || selectedResolution == null) {
            JOptionPane.showMessageDialog(frame, "Please select movie and resolution.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(selectedMovie + "-" + selectedResolution + "." + selectedFormat));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File outFile = chooser.getSelectedFile();
        playButton.setEnabled(false);
        saveButton.setEnabled(false);
        new Thread(() -> {
            try (Socket socket = StreamingClient.createSocket(Constants.SERVER_IP, Constants.PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 InputStream in = socket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                out.println(Protocol.SAVE);
                out.println(selectedMovie);
                out.println(selectedResolution);
                out.println(selectedFormat);
                out.flush();
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    fos.write(buffer, 0, read);
                }
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Saved video to: " + outFile.getAbsolutePath());
                    playButton.setEnabled(true);
                    saveButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage());
                    playButton.setEnabled(true);
                    saveButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void requestAndPlay(String movie, String resolution, String format, String protocol) {
        ffplayProcess = null;
        try (Socket socket = StreamingClient.createSocket(Constants.SERVER_IP, Constants.PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            int chosenPort = findFreeUDPPort();
            String ip = "127.0.0.1";

            // If UDP, open ffplay first
            if (protocol.equalsIgnoreCase("UDP")) {
                ffplayProcess = new ProcessBuilder(
                        "ffplay", "-autoexit", "-fflags", "nobuffer", "udp://" + ip + ":" + chosenPort)
                        .inheritIO()
                        .start();
                Thread.sleep(500);
            }

            out.println(Protocol.STREAM);
            out.println(movie);
            out.println(resolution);
            out.println(format);
            out.println(protocol);
            out.println(chosenPort);
            out.flush();

            String response = in.readLine();
            if (!Protocol.STREAMING.equals(response)) {
                JOptionPane.showMessageDialog(frame, "Server error: " + response, "Error", JOptionPane.ERROR_MESSAGE);
                playButton.setEnabled(true);
                playButton.setText("▶ Play");
                stopButton.setEnabled(false);
                return;
            }

            if (protocol.equalsIgnoreCase("TCP")) {
                ffplayProcess = new ProcessBuilder(
                        "ffplay", "-autoexit", "-fflags", "nobuffer", "tcp://" + ip + ":" + chosenPort)
                        .inheritIO()
                        .start();
            } else if (protocol.equalsIgnoreCase("RTP")) {
                String sdpMarker = in.readLine();
                if ("SDP".equals(sdpMarker)) {
                    StringBuilder sdpBuilder = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null && !"END_SDP".equals(line)) {
                        sdpBuilder.append(line).append("\n");
                    }
                    File sdpFile = File.createTempFile("rtp_", ".sdp");
                    try (FileWriter fw = new FileWriter(sdpFile)) {
                        fw.write(sdpBuilder.toString());
                    }
                    ffplayProcess = new ProcessBuilder(
                            "ffplay", "-autoexit", "-protocol_whitelist", "file,rtp,udp", "-i", sdpFile.getAbsolutePath())
                            .inheritIO()
                            .start();
                }
            }
            if (ffplayProcess != null) ffplayProcess.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            playButton.setEnabled(true);
            playButton.setText("▶ Play");
            stopButton.setEnabled(false);
            isPlaying = false;
        }
    }


    private String getAutoProtocol(String resolution) {
        if (resolution == null) return "TCP";
        return switch (resolution) {
            case "240p" -> "TCP";
            case "360p", "480p" -> "UDP";
            case "720p", "1080p" -> "RTP";
            default -> "TCP";
        };
    }

    public void stopVideo() {
        try {
            if (ffplayProcess != null && ffplayProcess.isAlive()) {
                ffplayProcess.destroy();
                ffplayProcess.destroyForcibly();
                ffplayProcess.waitFor();
            }
            ffplayProcess = null;
            isPlaying = false;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

