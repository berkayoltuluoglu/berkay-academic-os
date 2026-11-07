package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalTime;

public record AppPreferences(int schemaVersion, int focusMinutes, boolean remindersEnabled,
                             int reminderMinutes, String dateOnlyReminderTime, boolean keepRunningOnClose) {
    public static AppPreferences defaults() { return new AppPreferences(1, 25, true, 15, "09:00", true); }
    static Path file(Path root) { return root.resolve("data/preferences.json"); }
    public static AppPreferences load(Path root) throws IOException {
        var value = Files.exists(file(root)) ? new ObjectMapper().readValue(FinanceStorageService.readLimited(file(root)), AppPreferences.class) : defaults();
        validate(value); return value;
    }
    public void save(Path root) throws IOException {
        validate(this); AtomicFileWriter.write(file(root), new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(this));
    }
    private static void validate(AppPreferences value) throws IOException {
        try {
            if (value.schemaVersion() != 1 || value.focusMinutes() < 1 || value.focusMinutes() > 180
                    || value.reminderMinutes() < 0 || value.reminderMinutes() > 1440) throw new IllegalArgumentException();
            LocalTime.parse(value.dateOnlyReminderTime());
        } catch (RuntimeException error) { throw new IOException("Çalışma/bildirim ayarları geçersiz.", error); }
    }
}
