package org.example;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public final class DesktopIntegration {
    private DesktopIntegration() {}
    public static void browse(String address) throws IOException {
        URI uri = URI.create(address);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Yalnızca HTTP/HTTPS bağlantıları açılabilir.");
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(uri);
        else fallback(address);
    }
    public static void open(Path path) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(path.toFile());
        else fallback(path.toAbsolutePath().toString());
    }
    private static void fallback(String target) throws IOException {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target).start();
        else new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", target).start();
    }
}
