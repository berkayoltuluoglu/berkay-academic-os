package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReminderServiceTest {
    @TempDir Path root;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T05:50:00Z"), ZoneId.of("Europe/Istanbul"));
    private void task(String status) {
        var task = new TaskItem("task", "Rapor", LocalDate.of(2026, 10, 12), "", status);
        task.setDueTime(LocalTime.of(9, 0));
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(task));
    }
    @Test void backgroundPollingDeliversOnceAndPersistsDeduplicationAcrossRestart() throws Exception {
        task("Bekliyor"); List<String> notices = new ArrayList<>();
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), clock)) { service.poll(); service.poll(); }
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), clock)) { service.poll(); }
        assertEquals(List.of("Rapor"), notices);
    }
    @Test void doneRecordsAndDisabledRemindersDoNotNotify() throws Exception {
        task("Tamamlandı"); List<String> notices = new ArrayList<>();
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), clock)) { service.poll(); }
        task("Bekliyor"); new AppPreferences(1, 25, false, 15, "09:00", true).save(root);
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), clock)) { service.poll(); }
        assertTrue(notices.isEmpty());
    }
    @Test void failedNotificationIsNotMarkedDeliveredAndCanBeRetried() throws Exception {
        task("Bekliyor"); List<String> notices = new ArrayList<>();
        try (var service = new ReminderService(root, (title, message) -> { throw new IllegalStateException("Unavailable"); }, clock)) {
            assertThrows(IllegalStateException.class, service::poll);
        }
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), clock)) { service.poll(); }
        assertEquals(1, notices.size());
    }
    @Test void aMidnightDeadlineCanBeRemindedOnThePreviousDay() throws Exception {
        var task = new TaskItem("midnight", "Teslim", LocalDate.of(2026, 10, 13), "", "Bekliyor"); task.setDueTime(LocalTime.of(0, 5));
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(task));
        Clock late = Clock.fixed(Instant.parse("2026-10-12T20:55:00Z"), ZoneId.of("Europe/Istanbul"));
        List<String> notices = new ArrayList<>();
        try (var service = new ReminderService(root, (title, message) -> notices.add(title), late)) { service.poll(); }
        assertEquals(List.of("Teslim"), notices);
    }
}
