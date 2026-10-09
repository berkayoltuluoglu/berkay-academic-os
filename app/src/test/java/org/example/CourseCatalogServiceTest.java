package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.example.CourseData.*;
import static org.junit.jupiter.api.Assertions.*;

class CourseCatalogServiceTest {
    @TempDir Path dir;

    @Test void switchingSectionsPreservesWorkAndCourseIdentityAndDoesNotDuplicateOnReimport() throws Exception {
        var original = CourseStorageServiceTest.fixture();
        var sections = new CourseCsvImporter().parse(CourseCsvImporterTest.CSV);
        var catalog = CourseCatalogService.merge(original, sections);
        assertEquals(original.courses(), catalog.courses()); assertEquals(original.work(), catalog.work());
        assertEquals(catalog, CourseCatalogService.merge(catalog, sections));
        LocalDate start = LocalDate.parse("2026-09-01"), end = LocalDate.parse("2027-01-31");
        var first = CourseCatalogService.select(catalog, sections.getFirst().id(), start, end);
        var second = CourseCatalogService.select(first, sections.get(1).id(), start, end);
        assertEquals(1, second.courses().size()); assertEquals("circuits", second.courses().getFirst().id());
        assertEquals(original.work(), second.work()); assertEquals("Ali", second.courses().getFirst().instructor());
        assertEquals("D102", second.courses().getFirst().lessons().getFirst().room());
        Path file = dir.resolve("data/courses/academic.json");
        var storage = new CourseStorageService(file); storage.load(); storage.save(second);
        assertEquals(second, new CourseStorageService(file).load());
        var today = new TodayService().load(dir, LocalDate.parse("2026-10-06"));
        assertEquals(1, today.lessons().entries().size());
        assertTrue(today.lessons().entries().getFirst().detail().contains("EE201.2"));
        assertTrue(today.lessons().entries().getFirst().detail().contains("D102"));
    }

    @Test void separateSemestersPreservePastCoursesAndAmbiguousMatchesAreRejected() throws Exception {
        var catalog = CourseCatalogService.merge(CourseStorageServiceTest.fixture(), new CourseCsvImporter().parse(CourseCsvImporterTest.CSV));
        String section = catalog.sections().getFirst().id();
        var nextTerm = CourseCatalogService.select(catalog, section, LocalDate.parse("2027-09-01"), LocalDate.parse("2028-01-31"));
        assertEquals(2, nextTerm.courses().size()); assertEquals(catalog.courses().getFirst(), nextTerm.courses().getFirst());
        assertThrows(Exception.class, () -> CourseCatalogService.select(catalog, section, null, LocalDate.now()));
        Course c = catalog.courses().getFirst();
        Course duplicate = new Course("other", c.name(), c.code(), c.instructor(), c.room(), c.startDate(), c.endDate(), c.lessons());
        var ambiguous = catalog.withCourses(List.of(c, duplicate));
        assertThrows(Exception.class, () -> CourseCatalogService.select(ambiguous, section, LocalDate.parse(c.startDate()), LocalDate.parse(c.endDate())));
    }

    @Test void v1MigrationIsReadOnlyUntilSaveAndBacksUpExactOriginal() throws Exception {
        Path file = dir.resolve("academic.json");
        String legacy = """
                {"schemaVersion":1,"courses":[{"id":"old","name":"Eski Ders","code":"MAT101",
                "instructor":"Hoca","room":"D1","startDate":"2026-09-01","endDate":"2027-01-31",
                "lessons":[{"day":1,"start":"09:00","end":"10:00"}]}],
                "work":[{"id":"hw","courseId":"old","title":"Eski ödev","kind":"Ödev","date":"2026-10-02","done":false}]}
                """;
        Files.writeString(file, legacy);
        var storage = new CourseStorageService(file); var migrated = storage.load();
        assertEquals(VERSION, migrated.schemaVersion()); assertEquals(legacy, Files.readString(file));
        assertNull(migrated.courses().getFirst().credit()); assertTrue(migrated.sections().isEmpty());
        assertTrue(migrated.courses().getFirst().planned());
        var catalog = CourseCatalogService.merge(migrated, new CourseCsvImporter().parse(CourseCsvImporterTest.CSV));
        storage.save(catalog);
        assertEquals(legacy, Files.readString(dir.resolve("academic.json.bak")));
        var reopened = new CourseStorageService(file).load();
        assertEquals(migrated.work(), reopened.work()); assertEquals(migrated.courses(), reopened.courses());
        assertEquals(catalog, reopened);
    }

    @Test void unplanningPersistsWithoutLosingWorkAndReselectionRestoresSameCourse() throws Exception {
        var catalog = CourseCatalogService.merge(CourseStorageServiceTest.fixture(), new CourseCsvImporter().parse(CourseCsvImporterTest.CSV));
        var selected = CourseCatalogService.select(catalog, catalog.sections().get(1).id(), LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-31"));
        var course = selected.courses().getFirst();
        var unplanned = CourseCatalogService.unplan(selected, course.id());
        assertEquals(selected.work(), unplanned.work());
        assertEquals(course.lessons(), unplanned.courses().getFirst().lessons());
        Path file = dir.resolve("data/courses/academic.json");
        var storage = new CourseStorageService(file); storage.load(); storage.save(unplanned);
        assertFalse(new CourseStorageService(file).load().courses().getFirst().planned());
        assertTrue(new TodayService().load(dir, LocalDate.parse("2026-10-06")).lessons().entries().isEmpty());
        assertEquals(selected, CourseCatalogService.select(unplanned, course.sectionId(), LocalDate.parse(course.startDate()), LocalDate.parse(course.endDate())));
    }

    @Test void conflictCheckUsesActualTeachingDatesAndAllowsAdjacentHoursAndSectionReplacement() {
        Course c = new Course("math", "Matematik", "MAT101", "", "", "2026-10-01", "2026-10-31", List.of(new Lesson(1, "09:00", "10:00")));
        var data = CourseData.empty().withCourses(List.of(c));
        var section = new Section("s", "EE201", "EE201.1", "Devre", "", null, null, List.of(new Lesson(1, "09:30", "10:30")));
        LocalDate start = LocalDate.parse("2026-10-01"), end = LocalDate.parse("2026-10-31");
        assertEquals(List.of(c), CourseCatalogService.conflicts(data, section, start, end));
        assertTrue(CourseCatalogService.conflicts(data, section, start, start.plusDays(2)).isEmpty());
        assertTrue(CourseCatalogService.conflicts(data.withCourses(List.of(c.withPlanned(false))), section, start, end).isEmpty());
        assertFalse(CourseCatalogService.overlaps(new Lesson(1, "09:00", "10:00"), new Lesson(1, "10:00", "11:00")));
        var replacement = new Section("r", "MAT101", "MAT101.2", "Matematik", "", null, null, section.lessons());
        assertTrue(CourseCatalogService.conflicts(data, replacement, start, end).isEmpty());
    }
}
