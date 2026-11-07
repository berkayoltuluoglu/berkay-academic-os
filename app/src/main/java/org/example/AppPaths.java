package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Installed builds use a user profile; source launches keep the existing project data. */
public final class AppPaths {
    private AppPaths() {}
    public static Path root() {
        String override = System.getProperty("academic.os.home");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        Path base;
        if (!Boolean.getBoolean("academic.os.packaged")) {
            Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            base = working.getFileName() != null && "app".equals(working.getFileName().toString()) ? working.getParent() : working;
        } else base = defaultProfile(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")));
        Path location = locationFile(base);
        if (Files.exists(location)) {
            try (var reader = Files.newBufferedReader(location)) {
                Properties p = new Properties(); p.load(reader);
                String selected = p.getProperty("dataRoot");
                if (selected != null && !selected.isBlank()) return Path.of(selected).toAbsolutePath().normalize();
            } catch (IOException failure) { throw new IllegalStateException("Veri klasörü ayarı okunamadı", failure); }
        }
        return base;
    }
    private static Path locationFile(Path base) { return base.resolve(Boolean.getBoolean("academic.os.packaged") ? "location.properties" : "config/profile-location.properties"); }
    static void selectRoot(Path destination) throws IOException {
        validateDestination(destination);
        if (System.getProperty("academic.os.home") != null) throw new IOException("Veri klasörü başlatıcı tarafından sabitlenmiş.");
        Path base;
        if (Boolean.getBoolean("academic.os.packaged")) base = defaultProfile(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")));
        else {
            Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            base = working.getFileName() != null && working.getFileName().toString().equals("app") ? working.getParent() : working;
        }
        Properties p = new Properties(); p.setProperty("dataRoot", destination.toAbsolutePath().normalize().toString());
        var output = new java.io.StringWriter(); p.store(output, "Academic OS data location");
        AtomicFileWriter.writeText(locationFile(base), output.toString());
    }
    static void validateDestination(Path destination) throws IOException {
        if (Boolean.getBoolean("academic.os.packaged")) {
            Path install = installationRoot();
            if (install != null && destination.toAbsolutePath().normalize().startsWith(install))
                throw new IOException("Veri klasörü program kurulum klasörünün dışında olmalı.");
        }
    }
    static Path installationRoot() throws IOException {
        try {
            Path artifact = Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return artifact.getFileName().toString().endsWith(".jar") ? artifact.getParent().getParent().getParent() : null;
        } catch (java.net.URISyntaxException failure) { throw new IOException("Kurulum konumu doğrulanamadı.", failure); }
    }

    static Path defaultProfile(String os, Map<String, String> environment, Path home) {
        String lower = os.toLowerCase(Locale.ROOT);
        if (lower.contains("win")) {
            String local = environment.get("LOCALAPPDATA");
            return (local == null || local.isBlank() ? home.resolve("AppData/Local") : Path.of(local)).resolve("BerkayAcademicOS");
        }
        if (lower.contains("mac")) return home.resolve("Library/Application Support/BerkayAcademicOS");
        String xdg = environment.get("XDG_DATA_HOME");
        return (xdg == null || xdg.isBlank() ? home.resolve(".local/share") : Path.of(xdg)).resolve("berkay-academic-os");
    }
}
