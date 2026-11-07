package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DailyAgendaServiceTest {
    @TempDir Path root;
    private final LocalDate date = LocalDate.of(2026, 10, 9);
    private final DailyAgendaService service = new DailyAgendaService();
    private Path tasksFile() { return root.resolve("data/tasks.json"); }
    private Path studyFile() { return root.resolve("data/study/sessions.json"); }
    private StudySession session(String id, String start, String end) { return new StudySession(id, "Çalışma", date.toString(), start, end, "", false); }

    @Test void legacyTaskIdsAreStableAndReadingDoesNotMigrateTheFile() throws Exception {
        Files.createDirectories(tasksFile().getParent());
        String legacy = "[{\"title\":\"Aynı görev\",\"date\":\"2026-10-09\",\"description\":\"\",\"status\":\"Bekliyor\"},{\"title\":\"Aynı görev\",\"date\":\"2026-10-09\",\"description\":\"\",\"status\":\"Bekliyor\"}]";
        Files.writeString(tasksFile(), legacy);
        var first = service.load(root, date); var second = service.load(root, date);
        assertEquals(first.todos(), second.todos()); assertEquals(legacy, Files.readString(tasksFile()));
        assertNotEquals(first.todos().get(0).id(), first.todos().get(1).id());
        service.setDone(root, first.todos().getFirst(), true);
        var tasks = new TaskStorageService(tasksFile()).loadTasks();
        assertEquals(1, tasks.stream().filter(t -> "Tamamlandı".equals(t.getStatus())).count());
        assertEquals(legacy, Files.readString(tasksFile().resolveSibling("tasks.json.bak")));
        assertEquals(first.todos().getFirst().id(), service.load(root, date).todos().stream().filter(DailyAgendaService.Todo::done).findFirst().orElseThrow().id());
    }

    @Test void courseworkCompletionPreservesCourseAndCatalogRecords() throws Exception {
        var store = new CourseStorageService(root.resolve("data/courses/academic.json")); store.load();
        var section = new CourseData.Section("section", "EE201", "EE201.1", "Devre Analizi", "Hoca", null, null, List.of());
        var course = new CourseData.Course("course", "Devre Analizi", "EE201", "Hoca", "", date.toString(), date.plusMonths(4).toString(), List.of(), "EE201.1", null, null, "section");
        var data = new CourseData(2, List.of(course), List.of(new CourseData.Work("work", "course", "Rapor", "Ödev", date.toString(), false)), List.of(section)); store.save(data);
        service.setDone(root, service.load(root, date).todos().getFirst(), true);
        var saved = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
        assertTrue(saved.work().getFirst().done()); assertEquals(data.courses(), saved.courses()); assertEquals(data.sections(), saved.sections());
    }

    @Test void studyReschedulingRejectsCollisionsButAllowsAdjacentHours() throws Exception {
        service.saveSession(root, session("first", "10:00", "11:00"), true);
        service.saveSession(root, session("second", "11:00", "12:00"), true);
        String before = Files.readString(studyFile());
        assertThrows(Exception.class, () -> service.saveSession(root, session("second", "10:30", "11:30"), false));
        assertEquals(before, Files.readString(studyFile()));
        service.saveSession(root, session("second", "12:00", "13:00"), false);
        var day = service.load(root, date);
        assertEquals(LocalTime.NOON, day.events().get(1).start());
        assertEquals(before, Files.readString(studyFile().resolveSibling("sessions.json.bak")));
        service.deleteSession(root, "first"); assertEquals(1, service.load(root, date).events().size());
    }

    @Test void studySessionsCannotOverlapActualTermBoundedLessons() throws Exception {
        var store = new CourseStorageService(root.resolve("data/courses/academic.json")); store.load();
        store.save(new CourseData(2, List.of(new CourseData.Course("course", "Ders", "EE201", "", "D204", date.toString(), date.plusDays(6).toString(),
                List.of(new CourseData.Lesson(date.getDayOfWeek().getValue(), "13:00", "14:30")))), List.of()));
        assertThrows(Exception.class, () -> service.saveSession(root, session("study", "14:00", "15:00"), true));
        assertFalse(Files.exists(studyFile()));
        service.saveSession(root, session("study", "14:30", "15:30"), true);
        assertEquals(2, service.load(root, date).events().size());
        assertEquals("D204", service.load(root, date).events().getFirst().detail());
    }

    @Test void corruptedStudyDataDoesNotHideTasksOrPermitOverwrite() throws Exception {
        new TaskStorageService(tasksFile()).saveTasks(List.of(new TaskItem("Görev", date, "", "Bekliyor")));
        Files.createDirectories(studyFile().getParent()); Files.writeString(studyFile(), "broken");
        var day = service.load(root, date); assertEquals(1, day.todos().size()); assertFalse(day.errors().isEmpty());
        assertThrows(Exception.class, () -> service.saveSession(root, session("new", "09:00", "10:00"), true));
        assertEquals("broken", Files.readString(studyFile()));
    }

    @Test void staleWritersAndFailedBackupsPreserveStudyAndTaskData() throws Exception {
        var first = new StudyStorageService(studyFile()); var second = new StudyStorageService(studyFile()); first.load(); second.load();
        first.save(List.of(session("first", "10:00", "11:00"))); String original = Files.readString(studyFile());
        assertThrows(Exception.class, () -> second.save(List.of()));
        var backup = studyFile().resolveSibling("sessions.json.bak"); Files.createDirectory(backup); Files.writeString(backup.resolve("keep"), "keep");
        assertThrows(Exception.class, () -> first.save(List.of())); assertEquals(original, Files.readString(studyFile()));
        var a = new TaskStorageService(tasksFile()); var b = new TaskStorageService(tasksFile()); a.loadTasks(); b.loadTasks();
        a.saveTasks(List.of(new TaskItem("Görev", date, "", "Bekliyor")));
        assertThrows(Exception.class, () -> b.saveTasks(List.of())); assertEquals(1, new TaskStorageService(tasksFile()).loadTasks().size());
    }
}
