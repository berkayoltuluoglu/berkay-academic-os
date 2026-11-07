package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanningFeaturesTest {
    @TempDir Path root;
    private final LocalDate date = LocalDate.of(2026, 10, 12);
    private TaskItem task() {
        TaskItem task = new TaskItem("task", "Rapor", date, "Açıklama", "Bekliyor");
        task.setDueTime(LocalTime.of(17, 30)); task.setPriority("Yüksek"); task.setEstimatedMinutes(120); task.setLoggedMinutes(10);
        task.setSubtasks(List.of(new TaskItem.Subtask("sub", "Kaynakları oku", true))); return task;
    }
    @Test void taskDetailsRoundTripAndCompletionPreservesEveryField() throws Exception {
        var store = new TaskStorageService(root.resolve("data/tasks.json")); store.saveTasks(List.of(task()));
        var todo = new DailyAgendaService().load(root, date).todos().getFirst();
        new DailyAgendaService().setDone(root, todo, true);
        TaskItem value = new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst();
        assertEquals("task", value.getId()); assertEquals(LocalTime.of(17, 30), value.getDueTime());
        assertEquals("Yüksek", value.getPriority()); assertEquals(120, value.getEstimatedMinutes()); assertEquals(10, value.getLoggedMinutes());
        assertTrue(value.getSubtasks().getFirst().done()); assertEquals("Tamamlandı", value.getStatus());
    }
    @Test void oldRecordsReadWithoutBeingRewrittenAndReceiveDefaults() throws Exception {
        Path file = root.resolve("data/tasks.json"); Files.createDirectories(file.getParent());
        String old = "[{\"title\":\"Eski görev\",\"date\":\"2026-10-12\",\"description\":\"Not\",\"status\":\"Bekliyor\"}]";
        Files.writeString(file, old); TaskItem value = new TaskStorageService(file).loadTasks().getFirst();
        assertNull(value.getDueTime()); assertEquals("Normal", value.getPriority()); assertEquals(0, value.getEstimatedMinutes());
        assertTrue(value.getSubtasks().isEmpty()); assertEquals(old, Files.readString(file));
    }
    @Test void invalidDetailsCannotOverwriteThePreviousTaskFile() throws Exception {
        Path file = root.resolve("data/tasks.json"); var store = new TaskStorageService(file); store.saveTasks(List.of(task())); String before = Files.readString(file);
        var invalidEstimate = task(); invalidEstimate.setEstimatedMinutes(-1);
        assertThrows(IllegalStateException.class, () -> store.saveTasks(List.of(invalidEstimate))); assertEquals(before, Files.readString(file));
        var invalid = task(); invalid.setSubtasks(List.of(new TaskItem.Subtask("same", "Bir", false), new TaskItem.Subtask("same", "İki", false)));
        TaskItem duplicate = invalid; assertThrows(IllegalStateException.class, () -> store.saveTasks(List.of(duplicate))); assertEquals(before, Files.readString(file));
    }
    @Test void linkedSessionsPreserveLinksWhenMovedAndComputeRemainingTime() throws Exception {
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(task()));
        var done = new StudySession("done", "Okuma", date.toString(), "10:00", "11:00", "", true, "task", "", 45);
        var planned = new StudySession("planned", "Yazım", date.toString(), "12:00", "13:00", "", false, "task", "", 0);
        var service = new DailyAgendaService(); service.saveSession(root, done, true); service.saveSession(root, planned, true);
        service.saveSession(root, planned.moved(date.toString(), "13:00", "14:00"), false);
        var sessions = new StudyStorageService(root.resolve("data/study/sessions.json")).load();
        assertEquals("task", sessions.get(1).taskId());
        var progress = new StudyProgressService().forTask(task(), sessions);
        assertEquals(55, progress.studiedMinutes()); assertEquals(65, progress.remainingMinutes()); assertEquals(60, progress.plannedMinutes());
        assertThrows(Exception.class, () -> service.saveSession(root, new StudySession("missing", "X", date.toString(), "15:00", "16:00", "", false, "missing-task", "", 0), true));
    }
    @Test void focusTimeInsideACompletedSessionIsNotCountedTwice() {
        var session = new StudySession("session", "Çalışma", date.toString(), "10:00", "11:00", "", true, "task", "", 0);
        String end = date.atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant().toString();
        var inside = new StudyTimeLogService.Entry("focus-1", "task", "task", 25, end);
        var outside = new StudyTimeLogService.Entry("focus-2", "task", "task", 25, date.atTime(12, 30).atZone(ZoneId.systemDefault()).toInstant().toString());
        var progress = new StudyProgressService().forTask(task(), List.of(session), List.of(inside, outside));
        assertEquals(95, progress.studiedMinutes()); assertEquals(25, progress.remainingMinutes());
    }
    @Test void completedFocusPeriodIsCreditedOnlyOnceAcrossRestart() throws Exception {
        Path file = root.resolve("data/focus.properties");
        Clock start = Clock.fixed(Instant.parse("2026-10-12T07:00:00Z"), ZoneId.of("Europe/Istanbul"));
        var focus = new FocusTimerService(file, start); focus.selectTarget("task", "task"); focus.toggle();
        Clock later = Clock.offset(start, Duration.ofMinutes(26));
        new FocusTimerService(file, later).creditCompleted(); new FocusTimerService(file, later).creditCompleted();
        var entries = new StudyTimeLogService(root).load(); assertEquals(1, entries.size()); assertEquals(25, entries.getFirst().minutes());
        assertEquals("task", entries.getFirst().targetId());
        new FocusTimerService(file, later).toggle(); assertEquals(1, new StudyTimeLogService(root).load().size());
    }
    @Test void dateLessCourseworkDoesNotBreakDayAndDeliverySummaries() throws Exception {
        var store = new CourseStorageService(root.resolve("data/courses/academic.json")); store.load();
        var course = new CourseData.Course("course", "Ders", "", "", "", date.toString(), date.plusMonths(4).toString(), List.of());
        store.save(new CourseData(2, List.of(course), List.of(new CourseData.Work("work", "course", "Araştırma", "Ödev", "", false))));
        assertTrue(new DailyAgendaService().load(root, date).errors().isEmpty());
        assertNull(new TodayService().load(root, date).coursework().error());
        assertEquals(1, PlanningTarget.load(root).size());
    }
}
