package org.example;

import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Public JSON DTO deliberately contains no paths, account tokens or credentials. */
public record PlanningSnapshot(int schemaVersion, String week, String until, String timezone, String fingerprint,
                               List<Block> fixedBlocks, List<StudySession> blocks, List<PoolItem> pool,
                               List<Slot> freeSlots, PlanningPreferences preferences, List<String> errors) {
    public record Block(String id, String title, String date, String start, String end, String kind) {}
    public record Slot(String date, String start, String end, int minutes) {}
    public record PoolItem(String kind, String id, String title, String date, String dueTime, String courseId,
                           String priority, int estimatedMinutes, int studiedMinutes, int remainingMinutes, int plannedMinutes,
                           String sourceCourseId, String sourceWorkId) {
        public int unplannedMinutes() { return estimatedMinutes == 0 ? Math.max(0, 60 - plannedMinutes) : Math.max(0, remainingMinutes - plannedMinutes); }
    }
    public static PlanningSnapshot load(Path root, LocalDate start) throws Exception {
        return load(root, start, tasks -> {});
    }
    static PlanningSnapshot load(Path root, LocalDate start, java.util.function.Consumer<DailyAgendaService.Snapshot> taskReader) throws Exception {
        LocalDate monday=start.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var files=PlannerService.snapshotFiles(monday);
        var captured=PlanningFiles.consistentCapture(root,files);
        Path temporary=java.nio.file.Files.createTempDirectory("academic-snapshot-");
        try {
            for(var entry:captured.entrySet())if(entry.getValue()!=null){Path target=temporary.resolve(entry.getKey());java.nio.file.Files.createDirectories(target.getParent());java.nio.file.Files.write(target,entry.getValue());}
            return assemble(temporary,monday,taskReader);
        } finally {try(var paths=java.nio.file.Files.walk(temporary)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);}}
    }
    private static PlanningSnapshot assemble(Path root, LocalDate start, java.util.function.Consumer<DailyAgendaService.Snapshot> taskReader) throws Exception {
        start = start.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var service = new DailyAgendaService(); var data = service.readRange(root, start, start.plusDays(6));
        taskReader.accept(data);
        var prefs = PlanningPreferences.load(root);
        var focus = new StudyTimeLogService(root).load(); var progress = new StudyProgressService();
        List<PoolItem> pool = new ArrayList<>();
        for (var t : data.tasks()) if (!"Tamamlandı".equals(t.getStatus()) && !t.getId().startsWith("application-")) {
            var p = progress.forTask(t, data.sessions(), focus);
            pool.add(new PoolItem("task", t.getId(), t.getTitle(), t.getDate() == null ? "" : t.getDate().toString(),
                    Objects.toString(t.getDueTime(), ""), "", t.getPriority(), p.estimatedMinutes(), p.studiedMinutes(), p.remainingMinutes(), p.plannedMinutes(), "", ""));
        }
        for (var w : data.courses().work()) if (!w.done()) {
            var p = progress.forWork(w, data.sessions(), focus);
            pool.add(new PoolItem("work", w.id(), w.title(), w.date(), w.dueTime(), w.courseId(), w.priority(),
                    p.estimatedMinutes(), p.studiedMinutes(), p.remainingMinutes(), p.plannedMinutes(), "", ""));
        }
        var classroom = data.classroom(); Set<String> imported = new HashSet<>(); data.courses().work().forEach(w -> imported.add(w.id()));
        for (var w : classroom.work()) {
            String id = ClassroomImportService.workId(classroom.accountEmail(), w.courseId(), w.id());
            if (!w.submitted() && !imported.contains(id)) pool.add(new PoolItem("classroom", id, w.title(), w.dueDate(), w.dueTime(),
                    ClassroomImportService.courseId(classroom.accountEmail(), w.courseId()), "Normal", 0, 0, 0, 0, w.courseId(), w.id()));
        }
        for (var a : data.applications().applications()) if (a.awaitingApplication()) {
            String linkedId=PlannerService.applicationTaskId(a.id());
            var task=data.tasks().stream().filter(t->t.getId().equals(linkedId)).findFirst().orElse(null);
            if(task!=null && "Tamamlandı".equals(task.getStatus()))continue;
            var p=task==null?new StudyProgressService.Progress(0,0,0,0):progress.forTask(task,data.sessions(),focus);
            pool.add(new PoolItem("application", a.id(), a.name(), a.deadline(), "", "", "Yüksek", p.estimatedMinutes(),p.studiedMinutes(),p.remainingMinutes(),p.plannedMinutes(), "", ""));
        }
        pool.sort(Comparator.comparing((PoolItem p) -> p.date().isBlank() ? "9999-12-31" : p.date())
                .thenComparingInt(p -> priority(p.priority())).thenComparing(PoolItem::id));
        var days = service.assembleRange(data, start, start.plusDays(6));
        List<Block> fixed = new ArrayList<>(days.stream().flatMap(d -> d.events().stream()).filter(e -> !e.study())
                .map(e -> new Block(e.id(), e.title(), e.date().toString(), e.start().toString(), e.end().toString(), e.kind())).toList());
        for(var day:days)for(var event:data.google())if(event.isAllDay()&&event.occursOn(day.date()))fixed.add(new Block(event.getCalendarId()+":"+event.getId(),event.getTitle(),day.date().toString(),"00:00","23:59","Google"));
        LocalDate end = start.plusDays(6);
        List<StudySession> blocks = data.sessions().stream().filter(s -> !LocalDate.parse(s.date()).isBefore(days.getFirst().date())
                && !LocalDate.parse(s.date()).isAfter(end)).toList();
        String fingerprint = PlannerService.fingerprint(root, start);
        return new PlanningSnapshot(1, start.toString(), end.toString(), prefs.timezone(), fingerprint, fixed, blocks, List.copyOf(pool),
                slots(start, fixed, blocks, prefs), prefs, data.errors());
    }
    static int priority(String value) { return "Yüksek".equals(value) ? 0 : "Düşük".equals(value) ? 2 : 1; }
    static List<Slot> slots(LocalDate week, List<Block> fixed, List<StudySession> blocks, PlanningPreferences prefs) {
        List<Slot> result = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = week.plusDays(day); String iso = date.toString();
            boolean[] busy = new boolean[1440];
            for (var b : fixed) if (b.date().equals(iso)) occupy(busy, b.start(), b.end());
            for (var b : blocks) if (b.date().equals(iso)) occupy(busy, b.start(), b.end());
            for (var c : prefs.closed()) if (iso.equals(c.date()) || ((c.date() == null || c.date().isBlank()) && c.day() == date.getDayOfWeek().getValue()))
                occupy(busy, c.start(), c.end());
            int used = blocks.stream().filter(b -> b.date().equals(iso)).mapToInt(b -> PlanningPreferences.minute(b.end()) - PlanningPreferences.minute(b.start())).sum();
            if (used >= prefs.dailyMinutes()) continue;
            int begin = PlanningPreferences.minute(prefs.start()), end = Math.min(1439, PlanningPreferences.minute(prefs.end()));
            int cursor = begin;
            while (cursor < end) {
                while (cursor < end && busy[cursor]) cursor++;
                int from = cursor;
                while (cursor < end && !busy[cursor]) cursor++;
                if (cursor > from) result.add(new Slot(iso, PlanningPreferences.time(from), PlanningPreferences.time(cursor), cursor - from));
            }
        }
        return List.copyOf(result);
    }
    private static void occupy(boolean[] busy, String start, String end) {
        Arrays.fill(busy, Math.max(0, PlanningPreferences.minute(start)), Math.min(1440, PlanningPreferences.minute(end)), true);
    }
}
