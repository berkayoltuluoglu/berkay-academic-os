package org.example;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import java.awt.*;
import java.awt.image.BufferedImage;

/** The window may be hidden while this process keeps reminders running. */
final class TrayController implements AutoCloseable {
    private TrayIcon icon;
    boolean install(Runnable show, Runnable quit) {
        if (!SystemTray.isSupported()) return false;
        try {
            BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics(); graphics.setColor(new Color(40, 54, 74));
            graphics.fillRoundRect(0, 0, 48, 48, 12, 12); graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); graphics.drawString("AO", 6, 32); graphics.dispose();
            PopupMenu menu = new PopupMenu(); MenuItem open = new MenuItem("Academic OS'u aç"), exit = new MenuItem("Tamamen çık");
            open.addActionListener(e -> Platform.runLater(show)); exit.addActionListener(e -> Platform.runLater(quit));
            menu.add(open); menu.add(exit); icon = new TrayIcon(image, "Berkay Academic OS", menu); icon.setImageAutoSize(true);
            icon.addActionListener(e -> Platform.runLater(show)); SystemTray.getSystemTray().add(icon); return true;
        } catch (AWTException | RuntimeException failure) { icon = null; return false; }
    }
    void setCaption(String caption) { if(icon!=null)icon.setToolTip(caption); }
    boolean isAvailable() { return icon != null; }
    void notify(String title, String message) {
        if (icon != null) icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        else Platform.runLater(() -> { Alert alert = new Alert(Alert.AlertType.INFORMATION, message); alert.setTitle("Academic OS"); alert.setHeaderText(title); alert.show(); });
    }
    @Override public void close() { if (icon != null) { SystemTray.getSystemTray().remove(icon); icon = null; } }
}
