package org.example;

import java.nio.file.Path;
import java.util.*;

public record PlanningTarget(String id, String title, String kind, String courseId) {
    @Override public String toString() { return kind + " · " + title; }
    static List<PlanningTarget> load(Path root) throws Exception {
        List<PlanningTarget> targets = new ArrayList<>();
        for (var task : new TaskStorageService(root.resolve("data/tasks.json")).loadTasks())
            targets.add(new PlanningTarget(task.getId(), task.getTitle(), "Görev", ""));
        for (var work : new CourseStorageService(root.resolve("data/courses/academic.json")).load().work())
            targets.add(new PlanningTarget(work.id(), work.title(), work.kind(), work.courseId()));
        return targets;
    }
}
