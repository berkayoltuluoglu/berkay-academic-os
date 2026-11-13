package org.example;

import java.time.LocalDate;

/** The same occurrence rules are shared by the dashboard, calendars and planning snapshot. */
final class CourseSchedule {
    private CourseSchedule() {}

    static boolean occursOn(CourseData data, CourseData.Course course, CourseData.Lesson lesson, LocalDate date) {
        return course.planned() && lesson.day() == date.getDayOfWeek().getValue()
                && !date.isBefore(LocalDate.parse(course.startDate())) && !date.isAfter(LocalDate.parse(course.endDate()))
                && data.cancellations().stream().noneMatch(c ->
                    (c.courseId().isEmpty() || c.courseId().equals(course.id()))
                    && !date.isBefore(LocalDate.parse(c.fromDate())) && !date.isAfter(LocalDate.parse(c.toDate())));
    }
}
