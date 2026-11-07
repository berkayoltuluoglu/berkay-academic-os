package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {
    @TempDir Path directory;
    private Path profile() { return directory.resolve("profile"); }
    private Path archive() { return directory.resolve("backup.zip"); }
    private void put(String name, String content) throws IOException {
        Path file = profile().resolve(name); Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    @Test void roundTripRestoresRecordsAndNotesButNeverCopiesGoogleTokens() throws Exception {
        var root = profile(); new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(new TaskItem("Görev", LocalDate.of(2026, 10, 12), "", "Bekliyor")));
        put("data/notes/deneme.md", "Not içeriği"); put("config/google-calendar-tokens/StoredCredential", "private token");
        new AppPreferences(1, 35, true, 20, "09:00", true).save(root);
        var service = new BackupService(root); assertEquals(3, service.exportTo(archive()).files());
        try (var zip = new ZipFile(archive().toFile())) { assertNull(zip.getEntry("config/google-calendar-tokens/StoredCredential")); }
        put("data/notes/deneme.md", "Değişti"); put("data/notes/extra.md", "Yeni");
        Path before = service.restoreFrom(archive());
        assertTrue(Files.exists(before)); assertEquals("Not içeriği", Files.readString(root.resolve("data/notes/deneme.md")));
        assertFalse(Files.exists(root.resolve("data/notes/extra.md"))); assertEquals(35, AppPreferences.load(root).focusMinutes());
        assertEquals("private token", Files.readString(root.resolve("config/google-calendar-tokens/StoredCredential")));
        assertEquals(1, new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().size());
    }
    @Test void damagedChecksumIsRejectedBeforeCurrentRecordsChange() throws Exception {
        put("data/notes/a.md", "Before"); var service = new BackupService(profile()); service.exportTo(archive());
        Path damaged = directory.resolve("damaged.zip");
        try (var source = new ZipFile(archive().toFile()); var output = new ZipOutputStream(Files.newOutputStream(damaged))) {
            for (var items = source.entries(); items.hasMoreElements();) {
                var item = items.nextElement(); output.putNextEntry(new ZipEntry(item.getName()));
                if (item.getName().equals("data/notes/a.md")) output.write("Broken".getBytes()); else try (var input = source.getInputStream(item)) { input.transferTo(output); }
                output.closeEntry();
            }
        }
        assertThrows(IOException.class, () -> service.restoreFrom(damaged));
        assertEquals("Before", Files.readString(profile().resolve("data/notes/a.md")));
    }
    @Test void pathTraversalIsRejectedWithoutWritingOutsideTheProfile() throws Exception {
        put("data/notes/a.md", "Keep");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive()))) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write("{\"schemaVersion\":1,\"createdAt\":\"2026-10-12\",\"files\":[{\"path\":\"data/../../outside.txt\",\"size\":1,\"sha256\":\"x\"}]}".getBytes()); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/../../outside.txt")); zip.write('x'); zip.closeEntry();
        }
        assertThrows(IOException.class, () -> new BackupService(profile()).restoreFrom(archive()));
        assertFalse(Files.exists(directory.resolve("outside.txt"))); assertEquals("Keep", Files.readString(profile().resolve("data/notes/a.md")));
    }
    @Test void corruptNativeRecordsAreRejectedEvenIfArchiveChecksumIsValid() throws Exception {
        put("data/tasks.json", "broken json"); new BackupService(profile()).exportTo(archive()); put("data/tasks.json", "[]");
        assertThrows(IOException.class, () -> new BackupService(profile()).restoreFrom(archive()));
        assertEquals("[]", Files.readString(profile().resolve("data/tasks.json")));
    }
    @Test void activeRecordWriterPreventsBackupInsteadOfTakingAnInconsistentCopy() throws Exception {
        put("data/tasks.json", "[]");
        try (var channel = FileChannel.open(profile().resolve("data/tasks.json.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertThrows(IOException.class, () -> new BackupService(profile()).exportTo(archive())); assertFalse(Files.exists(archive()));
        }
    }
    @Test void symlinkDestinationsAreRejectedBeforeRestore() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("win")) return;
        put("data/notes/a.md", "Saved"); var service = new BackupService(profile()); service.exportTo(archive());
        Path notes = profile().resolve("data/notes"); Files.delete(notes.resolve("a.md")); Files.delete(notes);
        Path outside = directory.resolve("outside"); Files.createDirectories(outside); Files.createSymbolicLink(notes, outside);
        assertThrows(IOException.class, () -> service.restoreFrom(archive())); assertFalse(Files.exists(outside.resolve("a.md")));
    }
    @Test void backupCannotBeSavedInsideItsOwnDataTree() throws Exception {
        put("data/tasks.json", "[]"); assertThrows(IOException.class, () -> new BackupService(profile()).exportTo(profile().resolve("data/backup.zip")));
    }
}
