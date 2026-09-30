package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TaskStorageServiceTest {
    @TempDir Path directory;

    @Test void savesTasksAndKeepsPreviousVersionAsBackup() throws Exception {
        Path file = directory.resolve("tasks.json");
        TaskStorageService storage = new TaskStorageService(file);
        TaskItem task = new TaskItem("Çalışma", LocalDate.of(2026, 9, 30), "Ödev", "Bekliyor");
        storage.saveTasks(List.of(task));
        String original = Files.readString(file);
        TaskItem loaded = storage.loadTasks().getFirst();
        assertEquals(task.getTitle(), loaded.getTitle());
        assertEquals(task.getDate(), loaded.getDate());
        assertEquals(task.getDescription(), loaded.getDescription());
        storage.saveTasks(List.of());
        assertTrue(storage.loadTasks().isEmpty());
        assertEquals(original, Files.readString(directory.resolve("tasks.json.bak")));
    }

    @Test void corruptFileReportsFailureInsteadOfAnEmptyList() throws Exception {
        Path file = directory.resolve("tasks.json");
        Files.writeString(file, "{broken");
        assertThrows(IllegalStateException.class, () -> new TaskStorageService(file).loadTasks());
        assertEquals("{broken", Files.readString(file));
    }

    @Test void failedSavePreservesPreviousDataAndReportsFailure() throws Exception {
        Path file = directory.resolve("tasks.json");
        TaskStorageService storage = new TaskStorageService(file);
        storage.saveTasks(List.of(new TaskItem("Koru", LocalDate.now(), "", "Bekliyor")));
        String original = Files.readString(file);
        Path blockedBackup = Files.createDirectory(directory.resolve("tasks.json.bak"));
        Files.writeString(blockedBackup.resolve("keep"), "keep");
        assertThrows(IllegalStateException.class, () -> storage.saveTasks(List.of()));
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
