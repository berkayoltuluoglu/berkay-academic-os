package org.example;

import java.util.List;
import java.math.BigDecimal;

/** Dates and times use ISO strings in the versioned file format. */
public record CourseData(int schemaVersion, List<Course> courses, List<Work> work, List<Section> sections) {
    public static final int VERSION = 2;
    public CourseData {
        courses = List.copyOf(courses);
        work = List.copyOf(work);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
    public CourseData(int schemaVersion, List<Course> courses, List<Work> work) {
        this(schemaVersion, courses, work, List.of());
    }
    public static CourseData empty() { return new CourseData(VERSION, List.of(), List.of()); }
    public CourseData withCourses(List<Course> value) { return new CourseData(VERSION, value, work, sections); }
    public CourseData withWork(List<Work> value) { return new CourseData(VERSION, courses, value, sections); }
    public record Lesson(int day, String start, String end, String room) {
        public Lesson { room = room == null ? "" : room; }
        public Lesson(int day, String start, String end) { this(day, start, end, ""); }
    }
    public record Course(String id, String name, String code, String instructor, String room,
                         String startDate, String endDate, List<Lesson> lessons,
                         String section, BigDecimal credit, BigDecimal ects, String sectionId, Boolean planned) {
        public Course {
            lessons = List.copyOf(lessons);
            section = section == null ? "" : section;
            sectionId = sectionId == null ? "" : sectionId;
            planned = planned == null ? true : planned;
        }
        public Course(String id, String name, String code, String instructor, String room,
                      String startDate, String endDate, List<Lesson> lessons,
                      String section, BigDecimal credit, BigDecimal ects, String sectionId) {
            this(id, name, code, instructor, room, startDate, endDate, lessons, section, credit, ects, sectionId, true);
        }
        public Course(String id, String name, String code, String instructor, String room,
                      String startDate, String endDate, List<Lesson> lessons) {
            this(id, name, code, instructor, room, startDate, endDate, lessons, "", null, null, "");
        }
        @Override public String toString() { return code.isBlank() ? name : code + " · " + name; }
        public Course withPlanned(boolean value) {
            return new Course(id, name, code, instructor, room, startDate, endDate, lessons, section, credit, ects, sectionId, value);
        }
    }
    public record Section(String id, String code, String fullCode, String name, String instructor,
                          BigDecimal credit, BigDecimal ects, List<Lesson> lessons) {
        public Section { lessons = List.copyOf(lessons); }
    }
    public record Work(String id, String courseId, String title, String kind, String date, boolean done,
                       String dueTime, int estimatedMinutes, int loggedMinutes, String priority) {
        public Work {
            date = date == null ? "" : date;
            dueTime = dueTime == null ? "" : dueTime;
            priority = priority == null ? "Normal" : priority;
        }
        public Work(String id, String courseId, String title, String kind, String date, boolean done) {
            this(id, courseId, title, kind, date, done, "", 0, 0, "Normal");
        }
        public Work withDone(boolean value) {
            return new Work(id, courseId, title, kind, date, value, dueTime, estimatedMinutes, loggedMinutes, priority);
        }
    }
}
