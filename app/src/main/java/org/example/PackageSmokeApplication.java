package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.nio.file.*;

/** Explicit test mode uses a temporary profile and does not start background notifications/OAuth. */
public final class PackageSmokeApplication extends Application {
    private App app;
    private int ticks;
    @Override public void start(Stage stage) {
        Path root = AppPaths.root(); app = new App(root); app.start(stage);
        stage.setTitle("Academic OS Package Verification");
        Timeline check = new Timeline();
        check.getKeyFrames().add(new KeyFrame(Duration.millis(250), e -> {
            try {
                if (++ticks > 80) { check.stop(); Platform.exit(); return; }
                var status = (javafx.scene.control.Label) stage.getScene().lookup("#today-status");
                if (status != null && status.getText().isBlank()
                        && stage.getScene().getWidth() >= 800 && stage.getScene().getHeight() >= 500) {
                    var image = stage.getScene().snapshot(null); int width = (int) image.getWidth(), height = (int) image.getHeight();
                    int[] pixels = new int[width * height]; image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                    var output = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    output.setRGB(0, 0, width, height, pixels, 0, width); Files.createDirectories(root);
                    javax.imageio.ImageIO.write(output, "png", root.resolve("package-smoke.png").toFile());
                    Files.writeString(root.resolve("package-smoke.ok"), "JavaFX shell rendered successfully");
                    check.stop(); Platform.exit();
                }
            } catch (Exception failure) { check.stop(); failure.printStackTrace(); Platform.exit(); }
        }));
        check.setCycleCount(Timeline.INDEFINITE); check.play();
    }
    @Override public void stop() throws Exception { if (app != null) app.stop(); }
}
