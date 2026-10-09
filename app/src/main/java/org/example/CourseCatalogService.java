package org.example;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import static org.example.CourseData.*;

/** Pure transformations: preserve coursework IDs and commit only through CourseStorageService. */
public final class CourseCatalogService {
    private CourseCatalogService() {}

    public static CourseData merge(CourseData data, List<Section> imported) throws IOException {
        Map<String, Section> catalog = new LinkedHashMap<>();
        data.sections().forEach(s -> catalog.put(s.id(), s));
        for (Section section : imported) {
            Section previous = catalog.putIfAbsent(section.id(), section);
            if (previous != null && !previous.equals(section)) throw new IOException("Şube kimliği çakışıyor");
        }
        CourseData next = new CourseData(VERSION, data.courses(), data.work(), List.copyOf(catalog.values()));
        CourseStorageService.validate(next);
        return next;
    }

    public static CourseData select(CourseData data, String sectionId, LocalDate start, LocalDate end) throws IOException {
        if (start == null || end == null || end.isBefore(start)) throw new IOException("Geçerli dönem başlangıcı ve bitişi girin");
        Section section = data.sections().stream().filter(s -> s.id().equals(sectionId)).findFirst()
                .orElseThrow(() -> new IOException("Şube bulunamadı"));
        List<Course> existing = data.courses().stream().filter(c -> CourseCsvImporter.normalize(c.code()).equals(CourseCsvImporter.normalize(section.code()))
                && !LocalDate.parse(c.endDate()).isBefore(start) && !LocalDate.parse(c.startDate()).isAfter(end)).toList();
        if (existing.size() > 1) throw new IOException("Bu kodla aynı dönemde birden fazla ders var. Önce Dersler ekranından düzenleyin.");
        Course old = existing.isEmpty() ? null : existing.getFirst();
        // Course IDs are independent of section choice, keeping all assignment/exam links intact.
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        Course selected = new Course(id, section.name(), section.code(), section.instructor(), "", start.toString(), end.toString(),
                section.lessons(), section.fullCode(), section.credit(), section.ects(), section.id());
        List<Course> courses = new ArrayList<>(data.courses());
        if (old == null) courses.add(selected); else courses.set(courses.indexOf(old), selected);
        CourseData next = data.withCourses(courses);
        CourseStorageService.validate(next);
        return next;
    }

    public static CourseData unplan(CourseData data, String courseId) throws IOException {
        if (data.courses().stream().noneMatch(c -> c.id().equals(courseId))) throw new IOException("Ders bulunamadı");
        return data.withCourses(data.courses().stream().map(c -> c.id().equals(courseId) ? c.withPlanned(false) : c).toList());
    }

    static List<Course> active(CourseData data, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) return List.of();
        return data.courses().stream().filter(c -> c.planned() && !LocalDate.parse(c.endDate()).isBefore(start)
                && !LocalDate.parse(c.startDate()).isAfter(end)).toList();
    }

    static List<Course> conflicts(CourseData data, Section section, LocalDate start, LocalDate end) {
        return active(data, start, end).stream()
                .filter(c -> !CourseCsvImporter.normalize(c.code()).equals(CourseCsvImporter.normalize(section.code())))
                .filter(c -> section.lessons().stream().anyMatch(a -> c.lessons().stream().anyMatch(b -> {
                    LocalDate first = LocalDate.parse(c.startDate()).isAfter(start) ? LocalDate.parse(c.startDate()) : start;
                    LocalDate last = LocalDate.parse(c.endDate()).isBefore(end) ? LocalDate.parse(c.endDate()) : end;
                    first = first.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(a.day())));
                    return !first.isAfter(last) && overlaps(a, b);
                }))).toList();
    }

    static boolean overlaps(Lesson a, Lesson b) {
        return a.day() == b.day() && java.time.LocalTime.parse(a.start()).isBefore(java.time.LocalTime.parse(b.end()))
                && java.time.LocalTime.parse(b.start()).isBefore(java.time.LocalTime.parse(a.end()));
    }
}
