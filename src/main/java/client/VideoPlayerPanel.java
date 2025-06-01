package client;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import javax.swing.*;
import java.awt.*;
import java.io.File;

public class VideoPlayerPanel extends JPanel {
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;

    public VideoPlayerPanel() {
        setLayout(new BorderLayout());
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        add(mediaPlayerComponent, BorderLayout.CENTER);
    }

    public void playVideo(File videoFile) {
        // Stop any currently playing media first
        mediaPlayerComponent.mediaPlayer().controls().stop();

        // Start playing the new media
        String mrl = videoFile.getAbsolutePath().startsWith("/") ?
                videoFile.getAbsolutePath() :
                videoFile.getAbsolutePath().replace("\\", "/");

        System.out.println("Attempting to play: " + mrl); // Debug log
        mediaPlayerComponent.mediaPlayer().media().start(mrl);
    }

    public void release() {
        mediaPlayerComponent.release();
    }
}