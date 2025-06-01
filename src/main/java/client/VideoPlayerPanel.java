package client;

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.logging.Logger;

public class VideoPlayerPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(VideoPlayerPanel.class.getName());

    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;

    public VideoPlayerPanel() {
        setLayout(new BorderLayout());

        // Discover VLC
        boolean found = new NativeDiscovery().discover();
        if (!found) {
            logger.severe("VLC not found. Please install it or add to PATH.");
            JOptionPane.showMessageDialog(this, "VLC not found. Install VLC or add it to your system PATH.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Initialize media player component
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        add(mediaPlayerComponent, BorderLayout.CENTER);
    }

    public void playVideo(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            logger.warning("Video file missing: " + videoFile);
            JOptionPane.showMessageDialog(this, "Video file not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        logger.info("Playing video: " + videoFile.getAbsolutePath());
        mediaPlayerComponent.mediaPlayer().media().play(videoFile.getAbsolutePath());
    }

    public void stopVideo() {
        mediaPlayerComponent.mediaPlayer().controls().stop();
    }

    public void release() {
        mediaPlayerComponent.release();
    }
}
