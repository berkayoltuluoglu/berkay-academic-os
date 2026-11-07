package org.example;

import java.nio.file.Path;
import java.util.*;

public final class StudyProgressService {
    public record Progress(int estimatedMinutes, int studiedMinutes, int remainingMinutes, int plannedMinutes) {}

    public Progress forTask(TaskItem task, List<StudySession> sessions) {
        return progress(task.getEstimatedMinutes(), task.getLoggedMinutes(),
                sessions.stream().filter(s -> s.taskId().equals(task.getId())).toList());
    }
    public Progress forTask(TaskItem task, List<StudySession> sessions, List<StudyTimeLogService.Entry> focus) {
        Progress base = forTask(task, sessions);
        int extra = extraFocus(focus.stream().filter(e -> e.kind().equals("task") && e.targetId().equals(task.getId())).toList(),
                sessions.stream().filter(s -> s.taskId().equals(task.getId())).toList());
        return new Progress(base.estimatedMinutes(), base.studiedMinutes() + extra, Math.max(0, base.remainingMinutes() - extra), base.plannedMinutes());
    }
    public Progress forWork(CourseData.Work work, List<StudySession> sessions) {
        return progress(work.estimatedMinutes(), work.loggedMinutes(),
                sessions.stream().filter(s -> s.workId().equals(work.id())).toList());
    }
    public Progress forWork(CourseData.Work work, List<StudySession> sessions, List<StudyTimeLogService.Entry> focus) {
        Progress base = forWork(work, sessions);
        int extra = extraFocus(focus.stream().filter(e -> e.kind().equals("work") && e.targetId().equals(work.id())).toList(),
                sessions.stream().filter(s -> s.workId().equals(work.id())).toList());
        return new Progress(base.estimatedMinutes(), base.studiedMinutes() + extra, Math.max(0, base.remainingMinutes() - extra), base.plannedMinutes());
    }
    private Progress progress(int estimate, int manual, List<StudySession> sessions) {
        int studied = manual + sessions.stream().mapToInt(StudySession::studiedMinutes).sum();
        int planned = sessions.stream().filter(s -> !s.done()).mapToInt(s -> Math.max(0, (int) java.time.Duration.between(
                java.time.LocalTime.parse(s.start()), java.time.LocalTime.parse(s.end())).toMinutes() - s.actualMinutes())).sum();
        return new Progress(estimate, studied, Math.max(0, estimate - studied), planned);
    }
    private int extraFocus(List<StudyTimeLogService.Entry> entries, List<StudySession> sessions) {
        int total = 0;
        for (var entry : entries) {
            var end = java.time.LocalDateTime.ofInstant(java.time.Instant.parse(entry.completedAt()), java.time.ZoneId.systemDefault());
            var start = end.minusMinutes(entry.minutes());
            // A declared completed session already includes focus periods entirely inside that block.
            boolean included = sessions.stream().filter(s -> s.done() || s.actualMinutes() > 0).anyMatch(s -> {
                var date = java.time.LocalDate.parse(s.date());
                return !start.isBefore(date.atTime(java.time.LocalTime.parse(s.start())))
                        && !end.isAfter(date.atTime(java.time.LocalTime.parse(s.end())));
            });
            if (!included) total += entry.minutes();
        }
        return total;
    }
    public List<StudySession> loadSessions(Path root) throws java.io.IOException {
        return new StudyStorageService(root.resolve("data/study/sessions.json")).load();
    }
}
