package org.example;

import java.util.List;

public record ClassroomData(int schemaVersion, String accountEmail, String syncedAt,
                            List<Course> courses, List<Work> work) {
    public ClassroomData { courses = List.copyOf(courses); work = List.copyOf(work); }
    public record Course(String id, String name, String section, String url) {}
    public record Work(String id, String courseId, String title, String description, String dueDate,
                       String dueTime, String state, String url) {
        public boolean submitted() { return "TURNED_IN".equals(state) || "RETURNED".equals(state); }
    }
    public static ClassroomData empty() { return new ClassroomData(1, "", "", List.of(), List.of()); }
}
