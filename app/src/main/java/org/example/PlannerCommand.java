package org.example;

/** Typed commands used by both user actions and external proposals. */
public sealed interface PlannerCommand {
    record SaveBlock(StudySession block, boolean create, ConflictPolicy policy) implements PlannerCommand {}
    record ScheduleBlock(String kind, String itemId, String date, String start, int minutes, String title, ConflictPolicy policy) implements PlannerCommand {}
    record MoveBlock(String id, String date, String start, ConflictPolicy policy) implements PlannerCommand {}
    record ResizeBlock(String id, int minutes, ConflictPolicy policy) implements PlannerCommand {}
    record DeleteBlock(String id) implements PlannerCommand {}
    record CompleteBlock(String id, int actualMinutes, boolean done) implements PlannerCommand {}
    record CreateTask(TaskItem task) implements PlannerCommand {}
    record UpdateTask(TaskItem task) implements PlannerCommand {}
    record UpdateWork(CourseData.Work work) implements PlannerCommand {}
    record CompleteItem(String kind, String id, boolean done) implements PlannerCommand {}
    record SetEstimate(String kind, String id, int minutes) implements PlannerCommand {}
    record ImportClassroomWork(String courseId, String workId) implements PlannerCommand {}
    record SetPreferences(PlanningPreferences preferences) implements PlannerCommand {}
    record SetNote(String id, String note) implements PlannerCommand {}
    enum ConflictPolicy { REJECT, NEAREST, ALLOW }
}
