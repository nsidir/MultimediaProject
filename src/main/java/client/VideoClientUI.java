package client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class VideoClientUI {
    private JFrame frame;
    private JComboBox<String> resolutionCombo;
    private JComboBox<String> protocolCombo;
    private JList<String> movieList;
    private JButton playButton; // Made a field to access it easily
    private JButton stopButton; // Made a field
    private final Map<String, List<String>> availableVideos;
    private final VideoPlayerPanel videoPlayerPanel;
    private final double connectionSpeed;
    private final String selectedFormat;
    private static final Logger logger = Logger.getLogger(VideoClientUI.class.getName());

    public VideoClientUI(Map<String, List<String>> availableVideos,
                         VideoPlayerPanel videoPlayerPanel,
                         double connectionSpeed, String selectedFormat) {
        this.availableVideos = availableVideos;
        this.videoPlayerPanel = videoPlayerPanel;
        this.connectionSpeed = connectionSpeed;
        this.selectedFormat = selectedFormat;
        initializeUI();
    }

    private void initializeUI() {
        frame = new JFrame("Movie Streamer - Connected at " + connectionSpeed + " Mbps (Format: " + selectedFormat.toUpperCase() + ")");
        frame.setSize(1200, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Add the video player panel
        frame.add(videoPlayerPanel, BorderLayout.CENTER);

        // Create sidebar for video selection
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setPreferredSize(new Dimension(350, 0)); // Adjusted width
        controlPanel.setBorder(BorderFactory.createTitledBorder("Video Selection & Controls"));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        if (availableVideos != null) {
            availableVideos.keySet().stream().sorted().forEach(listModel::addElement);
        } else {
            logger.warning("Available videos map is null during UI initialization.");
        }


        movieList = new JList<>(listModel);
        movieList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(movieList);

        resolutionCombo = new JComboBox<>();
        protocolCombo = new JComboBox<>(new String[]{"Auto", "TCP", "UDP", "RTP"}); // UDP and RTP are placeholders for now

        JPanel topSelectionPanel = new JPanel(new BorderLayout(5,5)); // Added some gap
        topSelectionPanel.add(new JLabel(" Select a movie:"), BorderLayout.NORTH);
        topSelectionPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel settingsPanel = new JPanel(new GridLayout(0, 2, 5, 5)); // 0 rows means flexible
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Stream Settings"));
        settingsPanel.add(new JLabel("Resolution:"));
        settingsPanel.add(resolutionCombo);
        settingsPanel.add(new JLabel("Protocol:"));
        settingsPanel.add(protocolCombo);
        settingsPanel.add(new JLabel("Selected Format:"));
        settingsPanel.add(new JLabel(selectedFormat.toUpperCase()));
        settingsPanel.add(new JLabel("Est. Speed:"));
        settingsPanel.add(new JLabel(String.format("%.2f Mbps", connectionSpeed)));


        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10)); // Centered with gaps
        playButton = new JButton("▶ Play");
        stopButton = new JButton("⏹ Stop");
        JButton exitButton = new JButton("Exit");

        Dimension buttonSize = new Dimension(100, 35);
        playButton.setPreferredSize(buttonSize);
        stopButton.setPreferredSize(buttonSize);
        exitButton.setPreferredSize(buttonSize);

        buttonPanel.add(playButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(exitButton);

        JPanel southControlPanel = new JPanel(new BorderLayout());
        southControlPanel.add(settingsPanel, BorderLayout.NORTH);
        southControlPanel.add(buttonPanel, BorderLayout.CENTER);


        controlPanel.add(topSelectionPanel, BorderLayout.CENTER);
        controlPanel.add(southControlPanel, BorderLayout.SOUTH);


        frame.add(controlPanel, BorderLayout.WEST);

        // Listeners
        movieList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedMovie = movieList.getSelectedValue();
                resolutionCombo.removeAllItems();
                if (selectedMovie != null && availableVideos != null && availableVideos.containsKey(selectedMovie)) {
                    List<String> resolutions = availableVideos.get(selectedMovie);
                    if (resolutions != null) {
                        resolutions.stream().sorted().forEach(resolutionCombo::addItem);
                        if (resolutionCombo.getItemCount() > 0) {
                            resolutionCombo.setSelectedIndex(0); // Select first available resolution
                        }
                    } else {
                        logger.warning("Resolutions list is null for movie: " + selectedMovie);
                    }
                }
            }
        });

        playButton.addActionListener(e -> {
            String selectedMovie = movieList.getSelectedValue();
            String selectedResolution = (String) resolutionCombo.getSelectedItem();
            String selectedProtocolChoice = (String) protocolCombo.getSelectedItem();

            if (selectedMovie == null) {
                JOptionPane.showMessageDialog(frame, "Please select a movie.",
                        "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectedResolution == null) {
                JOptionPane.showMessageDialog(frame, "Please select a resolution for '" + selectedMovie + "'.",
                        "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectedProtocolChoice == null) {
                JOptionPane.showMessageDialog(frame, "Please select a protocol.",
                        "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }


            String actualProtocol = selectedProtocolChoice;
            if ("Auto".equals(selectedProtocolChoice)) {
                actualProtocol = getAutoProtocol(selectedResolution);
                logger.info("Auto protocol selected. Determined protocol: " + actualProtocol + " for resolution " + selectedResolution);
            }

            playButton.setEnabled(false);
            playButton.setText("Loading...");
            stopButton.setEnabled(true); // Enable stop button when loading/playing

            // Define the callback to re-enable the play button
            Runnable onStreamCompletion = () -> {
                SwingUtilities.invokeLater(() -> {
                    playButton.setEnabled(true);
                    playButton.setText("▶ Play");
                    // Keep stop button enabled if needed, or disable if stream truly stopped/failed
                });
            };

            logger.info("Initiating stream: Movie=" + selectedMovie + ", Res=" + selectedResolution +
                    ", Format=" + selectedFormat + ", Protocol=" + actualProtocol);

            // ClientHandler.streamSelectedVideo is already async
            ClientHandler.streamSelectedVideo(selectedMovie, selectedResolution,
                    selectedFormat, actualProtocol, onStreamCompletion);

        });

        stopButton.addActionListener(e -> {
            logger.info("Stop button clicked.");
            videoPlayerPanel.stopVideo();
            playButton.setEnabled(true);
            playButton.setText("▶ Play");
            stopButton.setEnabled(false); // Disable stop button when not playing
        });
        stopButton.setEnabled(false); // Initially disabled

        exitButton.addActionListener(e -> {
            logger.info("Exit button clicked. Releasing resources and exiting.");
            videoPlayerPanel.release();
            frame.dispose();
            System.exit(0);
        });

        // Select first movie automatically if available
        if (!listModel.isEmpty()) {
            movieList.setSelectedIndex(0);
        } else {
            logger.info("Movie list is empty. No movie selected by default.");
            if (availableVideos == null || availableVideos.isEmpty()){
                JOptionPane.showMessageDialog(frame,
                        "No videos are available from the server for the selected criteria (Speed/Format).\nPlease check server status or try different settings.",
                        "No Videos Available", JOptionPane.WARNING_MESSAGE);
            }
        }

        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }

    private String getAutoProtocol(String resolution) {
        if (resolution == null) return "TCP"; // Default fallback
        return switch (resolution) {
            case "240p" -> "TCP";
            // Per project spec: 360p, 480p -> UDP; 720p, 1080p -> RTP/UDP
            // For now, VideoStreamer only fully implements TCP-like streaming via FFMPEG pipe.
            // So, we'll map these to TCP for the current server implementation.
            // Once UDP/RTP are distinct on server, these can change.
            case "360p", "480p" -> "TCP"; // Was "UDP", changed to TCP as server uses TCP pipe for all
            case "720p", "1080p" -> "TCP"; // Was "RTP", changed to TCP
            default -> "TCP";
        };
    }
}
