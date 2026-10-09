package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.example.CourseData.*;

class CourseStorageServiceTest {
    @TempDir Path dir;
    static Course course() {
        return new Course("circuits", "Devre Analizi", "EE201", "Öğretim elemanı", "B-204",
                "2026-09-01", "2027-01-31", List.of(new Lesson(3, "09:00", "10:30")));
    }
    static CourseData fixture() {
        return new CourseData(CourseData.VERSION, List.of(course()), List.of(new Work("lab", "circuits", "Laboratuvar raporu", "Ödev", "2026-10-02", false)));
    }
    @Test void roundTripBackupAndEmptyRemainEmpty() throws Exception {
        Path file = dir.resolve("courses.json");
        var store = new CourseStorageService(file);
        assertEquals(CourseData.empty(), store.load()); assertFalse(Files.exists(file));
        store.save(fixture());
        byte[] previous = Files.readAllBytes(file);
        assertEquals(fixture(), new CourseStorageService(file).load());
        store.save(CourseData.empty());
        assertArrayEquals(previous, Files.readAllBytes(dir.resolve("courses.json.bak")));
        assertEquals(CourseData.empty(), new CourseStorageService(file).load());
    }
    @Test void corruptInputAndInvalidRelationshipsNeverOverwriteOriginal() throws Exception {
        Path file = dir.resolve("courses.json");
        var store = new CourseStorageService(file); store.load(); store.save(fixture());
        byte[] original = Files.readAllBytes(file);
        assertThrows(Exception.class, () -> store.save(new CourseData(CourseData.VERSION, List.of(), fixture().work())));
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.writeString(file, "{broken}");
        var corrupt = new CourseStorageService(file);
        assertThrows(Exception.class, corrupt::load);
        assertThrows(Exception.class, () -> corrupt.save(CourseData.empty()));
        assertEquals("{broken}", Files.readString(file));
    }
    @Test void staleWriterAndFailedBackupPreserveExistingData() throws Exception {
        Path file = dir.resolve("courses.json");
        var a = new CourseStorageService(file); var b = new CourseStorageService(file);
        a.load(); b.load(); a.save(fixture());
        assertThrows(Exception.class, () -> b.save(CourseData.empty()));
        Path backup = Files.createDirectory(dir.resolve("courses.json.bak")); Files.writeString(backup.resolve("keep"), "keep");
        assertThrows(Exception.class, () -> a.save(CourseData.empty()));
        assertEquals(fixture(), new CourseStorageService(file).load());
    }
    @Test void rejectsInvalidDatesTimesAndDuplicateIds() {
        Course c = course();
        assertThrows(Exception.class, () -> CourseStorageService.validate(new CourseData(CourseData.VERSION, List.of(c, c), List.of())));
        var invalid = new Course(c.id(), c.name(), c.code(), c.instructor(), c.room(), c.startDate(), c.endDate(), List.of(new Lesson(3,"11:00","10:00")));
        assertThrows(Exception.class, () -> CourseStorageService.validate(new CourseData(CourseData.VERSION, List.of(invalid), List.of())));
        var dates = new Course(c.id(), c.name(), c.code(), c.instructor(), c.room(), "2027-02-01", "2027-01-01", c.lessons());
        assertThrows(Exception.class, () -> CourseStorageService.validate(new CourseData(CourseData.VERSION, List.of(dates), List.of())));
    }
}
