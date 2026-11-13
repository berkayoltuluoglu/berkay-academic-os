package org.example;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public record UpdatePreferences(boolean checkOnStartup, boolean autoDownload) {
    public static UpdatePreferences load(Path profile) throws IOException {
        Path file = profile.resolve("data/updates.properties");
        if (!Files.exists(file)) return new UpdatePreferences(true, false);
        Properties values = new Properties();
        try (var input = Files.newInputStream(file)) { values.load(input); }
        return new UpdatePreferences(Boolean.parseBoolean(values.getProperty("checkOnStartup", "true")),
                Boolean.parseBoolean(values.getProperty("autoDownload", "false")));
    }
    public void save(Path profile) throws IOException {
        Properties values = new Properties();
        values.setProperty("checkOnStartup", Boolean.toString(checkOnStartup));
        values.setProperty("autoDownload", Boolean.toString(autoDownload));
        ByteArrayOutputStream output = new ByteArrayOutputStream(); values.store(output, "Academic OS updates");
        AtomicFileWriter.write(profile.resolve("data/updates.properties"), output.toByteArray());
    }
}
