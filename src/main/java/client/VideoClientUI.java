package client;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class VideoClientUI {
    private JFrame frame;
    private JComboBox<String> resolutionCombo;
    private JList<String> movieList;
    private final Map<String, List<String>> availableVideos;
    private final VideoPlayerPanel videoPlayerPanel;

    // Updated constructor
    public VideoClientUI(Map<String, List<String>> availableVideos,
                         VideoPlayerPanel videoPlayerPanel) {
        this.availableVideos = availableVideos;
        this.videoPlayerPanel = videoPlayerPanel;
        initializeUI();
    }

    private void initializeUI() {
        // Setup main frame
        frame = new JFrame("Movie Streamer");
        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Add the video player panel
        frame.add(videoPlayerPanel, BorderLayout.CENTER);

        // Create sidebar for video selection
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setPreferredSize(new Dimension(300, 0));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        availableVideos.keySet().forEach(listModel::addElement);

        movieList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(movieList);

        resolutionCombo = new JComboBox<>();

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel(" Select a movie:"), BorderLayout.NORTH);
        topPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel resolutionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resolutionPanel.add(new JLabel("Resolution:"));
        resolutionPanel.add(resolutionCombo);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton playButton = new JButton("Play");
        JButton exitButton = new JButton("Exit");
        buttonPanel.add(playButton);
        buttonPanel.add(exitButton);

        controlPanel.add(topPanel, BorderLayout.CENTER);
        controlPanel.add(resolutionPanel, BorderLayout.NORTH);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(controlPanel, BorderLayout.WEST);

        // Listeners
        movieList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedMovie = movieList.getSelectedValue();
                resolutionCombo.removeAllItems();
                if (selectedMovie != null) {
                    availableVideos.get(selectedMovie).forEach(resolutionCombo::addItem);
                }
            }
        });

        playButton.addActionListener(e -> {
            String selectedMovie = movieList.getSelectedValue();
            String selectedResolution = (String) resolutionCombo.getSelectedItem();

            if (selectedMovie != null && selectedResolution != null) {
                System.out.println("Requesting video: " + selectedMovie + " at " + selectedResolution);
                new Thread(() -> {
                    try {
                        ClientHandler.streamSelectedVideo(selectedMovie, selectedResolution);
                    } catch (Exception ex) {
                        System.err.println("Video request failed: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }).start();
            } else {
                JOptionPane.showMessageDialog(frame, "Please select both movie and resolution",
                        "Selection Required", JOptionPane.WARNING_MESSAGE);
            }
        });

        exitButton.addActionListener(e -> {
            frame.dispose();
            System.exit(0);
        });

        // Select first movie automatically
        if (!listModel.isEmpty()) {
            movieList.setSelectedIndex(0);
        }

        frame.setVisible(true);
    }
}