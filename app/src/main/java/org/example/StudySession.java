package org.example;

/** A dated work block, separate from a course meeting or an assignment deadline. */
public record StudySession(String id, String title, String date, String start, String end,
                           String courseId, boolean done, String taskId, String workId, int actualMinutes) {
    public StudySession {
        courseId = courseId == null ? "" : courseId;
        taskId = taskId == null ? "" : taskId;
        workId = workId == null ? "" : workId;
    }
    public StudySession(String id, String title, String date, String start, String end, String courseId, boolean done) {
        this(id, title, date, start, end, courseId, done, "", "", 0);
    }
    public StudySession moved(String newDate, String newStart, String newEnd) {
        return new StudySession(id, title, newDate, newStart, newEnd, courseId, done, taskId, workId, actualMinutes);
    }
    public int studiedMinutes() {
        return actualMinutes > 0 ? actualMinutes : done
                ? (int) java.time.Duration.between(java.time.LocalTime.parse(start), java.time.LocalTime.parse(end)).toMinutes() : 0;
    }
}
