package org.example;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/** Stable source IDs and a three-way merge preserve manual planning fields on later Classroom refreshes. */
public final class ClassroomImportService {
    private final Path root;
    public ClassroomImportService(Path root) { this.root = root; }
    public void importCourse(ClassroomData data, String courseId) throws Exception {
        merge(data, data, Set.of(courseId), true);
    }
    /** Imports exactly one assignment and its course shell; stable IDs make repeated drops idempotent. */
    public void importWork(ClassroomData data, String courseId, String workId) throws Exception {
        var selected = data.work().stream().filter(w -> w.courseId().equals(courseId) && w.id().equals(workId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Classroom ödevi yerel kopyada bulunamadı"));
        if (data.courses().stream().noneMatch(c -> c.id().equals(courseId))) throw new IllegalArgumentException("Classroom dersi bulunamadı");
        var subset = new ClassroomData(data.schemaVersion(), data.accountEmail(), data.syncedAt(), data.courses(), List.of(selected));
        merge(data, subset, Set.of(courseId), true);
    }
    void refreshImported(ClassroomData previous, ClassroomData next) throws Exception {
        Set<String> ids = new HashSet<>();
        var local = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
        for (var course : next.courses()) if (local.courses().stream().anyMatch(c -> c.id().equals(courseId(next.accountEmail(), course.id())))) ids.add(course.id());
        if (!ids.isEmpty()) merge(previous, next, ids, false);
    }
    private void merge(ClassroomData previous, ClassroomData next, Set<String> selected, boolean create) throws Exception {
        var storage = new CourseStorageService(root.resolve("data/courses/academic.json")); var local = storage.load();
        List<CourseData.Course> courses = new ArrayList<>(local.courses()); List<CourseData.Work> works = new ArrayList<>(local.work());
        for (var source : next.courses()) if (selected.contains(source.id())) {
            String id = courseId(next.accountEmail(), source.id());
            var existing = courses.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
            if (existing == null) {
                if (!create) continue;
                courses.add(new CourseData.Course(id, cut(source.name(), 200), "", "", "", LocalDate.now().toString(), LocalDate.now().plusMonths(6).toString(),
                        List.of(), cut(source.section(), 80), null, null, "", false));
            } else {
                var old = previous.courses().stream().filter(c -> c.id().equals(source.id())).findFirst().orElse(null);
                if (old != null && existing.name().equals(cut(old.name(), 200))) {
                    courses.remove(existing);
                    courses.add(new CourseData.Course(existing.id(), cut(source.name(), 200), existing.code(), existing.instructor(), existing.room(),
                            existing.startDate(), existing.endDate(), existing.lessons(), existing.section(), existing.credit(), existing.ects(), existing.sectionId(), existing.planned()));
                }
            }
            for (var remote : next.work()) if (remote.courseId().equals(source.id())) {
                String workId = workId(next.accountEmail(), source.id(), remote.id());
                var current = works.stream().filter(w -> w.id().equals(workId)).findFirst().orElse(null);
                if (current == null) {
                    // Later new work from an already imported course is also added without creating duplicates.
                    works.add(new CourseData.Work(workId, id, cut(remote.title(), 300), "Ödev", remote.dueDate(), remote.submitted(), remote.dueTime(), 0, 0, "Normal"));
                } else {
                    var old = previous.work().stream().filter(w -> w.courseId().equals(source.id()) && w.id().equals(remote.id())).findFirst().orElse(null);
                    String title = old != null && current.title().equals(cut(old.title(), 300)) ? cut(remote.title(), 300) : current.title();
                    String date = old != null && current.date().equals(old.dueDate()) ? remote.dueDate() : current.date();
                    String time = old != null && current.dueTime().equals(old.dueTime()) ? remote.dueTime() : current.dueTime();
                    boolean done = old != null && current.done() == old.submitted() ? remote.submitted() : current.done();
                    works.remove(current); works.add(new CourseData.Work(current.id(), current.courseId(), title, current.kind(), date, done,
                            time, current.estimatedMinutes(), current.loggedMinutes(), current.priority()));
                }
            }
        }
        var candidate = new CourseData(CourseData.VERSION, courses, works, local.sections());
        if (!candidate.equals(local)) storage.save(candidate);
    }
    public static String courseId(String account, String id) { return "gc-" + UUID.nameUUIDFromBytes((account + ":" + id).getBytes(StandardCharsets.UTF_8)); }
    public static String workId(String account, String course, String work) { return "gw-" + UUID.nameUUIDFromBytes((account + ":" + course + ":" + work).getBytes(StandardCharsets.UTF_8)); }
    private static String cut(String value, int max) {
        if (value.length() <= max) return value;
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
}
