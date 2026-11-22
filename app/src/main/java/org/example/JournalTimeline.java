package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Read-only projection of manual entries and the existing, once-credited focus log. */
final class JournalTimeline {
    record Entry(String id,String date,String title,String start,String end,int minutes,String taskId,String source,String focusId,String editId) {
        boolean timed() { return !start.isBlank(); }
    }
    record Snapshot(JournalStore.Document journal,List<TaskItem> tasks,CourseData courses,List<StudySession> sessions,
                    List<StudyTimeLogService.Entry> focus,List<Entry> entries,ZoneId timezone,Map<String,byte[]> baseline) {
        int totalMinutes() { return entries.stream().mapToInt(Entry::minutes).sum(); }
    }
    static final List<String> FILES=List.of("data/tasks.json","data/courses/academic.json","data/study/sessions.json",
            "data/time/focus-log.json",JournalStore.FILE,PlanningPreferences.FILE);
    static Snapshot load(Path root,LocalDate date) throws IOException {
        var captured=PlanningFiles.consistentCapture(root,FILES);Path stage=Files.createTempDirectory("academic-journal-read-");
        try {
            for(var e:captured.entrySet()) if(e.getValue()!=null){Path target=stage.resolve(e.getKey());Files.createDirectories(target.getParent());Files.write(target,e.getValue());}
            var journal=JournalStore.load(stage);var tasks=new TaskStorageService(stage.resolve("data/tasks.json")).loadTasks();
            var courses=new CourseStorageService(stage.resolve("data/courses/academic.json")).load();
            var sessions=new StudyStorageService(stage.resolve("data/study/sessions.json")).load();
            var focus=new StudyTimeLogService(stage).load();var zone=ZoneId.of(PlanningPreferences.load(stage).timezone());
            return new Snapshot(journal,tasks,courses,sessions,focus,entries(journal,tasks,courses,sessions,focus,zone,date),zone,Collections.unmodifiableMap(captured));
        } catch(RuntimeException error) {throw new IOException("Yapılacaklar veya günlük okunamadı: "+error.getMessage(),error);}
        finally {try(var paths=Files.walk(stage)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    static List<Entry> entries(JournalStore.Document journal,List<TaskItem> tasks,CourseData courses,List<StudySession> sessions,
                               List<StudyTimeLogService.Entry> focus,ZoneId zone,LocalDate date) {
        List<Entry> entries=new ArrayList<>();
        for(var record:focus) {
            var end=LocalDateTime.ofInstant(Instant.parse(record.completedAt()),zone).withSecond(0).withNano(0);
            var start=end.minusMinutes(record.minutes());var dayStart=date.atStartOfDay();var dayEnd=date.plusDays(1).atStartOfDay();
            var from=start.isBefore(dayStart)?dayStart:start;var to=end.isAfter(dayEnd)?dayEnd:end;
            int minutes=(int)Duration.between(from,to).toMinutes();if(minutes<=0)continue;
            String taskId=record.kind().equals("task")&&tasks.stream().anyMatch(t->t.getId().equals(record.targetId()))?record.targetId():"";
            String title="Odak çalışması";
            if(record.kind().equals("session")) {
                var block=sessions.stream().filter(s->s.id().equals(record.targetId())).findFirst().orElse(null);
                if(block!=null){title=block.title();taskId=block.taskId();}
            } else if(record.kind().equals("task")) title=tasks.stream().filter(t->t.getId().equals(record.targetId())).map(TaskItem::getTitle).findFirst().orElse("Görev odağı");
            else title=courses.work().stream().filter(w->w.id().equals(record.targetId())).map(CourseData.Work::title).findFirst().orElse("Ödev odağı");
            var note=journal.activities().stream().filter(a->a.date().equals(date.toString())&&a.focusId().equals(record.id())).findFirst().orElse(null);
            entries.add(new Entry("focus-"+record.id(),date.toString(),note==null?title:note.title(),time(from),to.equals(dayEnd)?"24:00":time(to),
                    minutes,taskId,note==null?"Sayaç":"Sayaç · not",record.id(),note==null?"":note.id()));
        }
        for(var activity:journal.activities()) if(activity.date().equals(date.toString())&&activity.focusId().isBlank())
            entries.add(new Entry(activity.id(),activity.date(),activity.title(),activity.start(),activity.end(),activity.minutes(),activity.taskId(),"Elle","",activity.id()));
        entries.sort(Comparator.comparing((Entry e)->e.start().isBlank()?"99:99":e.start()).thenComparing(Entry::id));return List.copyOf(entries);
    }
    static Entry conflict(ActivityParser.Parsed parsed,String taskId,String excluded,List<Entry> entries){
        return entries.stream().filter(e->!e.focusId().isBlank()&&(excluded.isBlank()||(!e.id().equals(excluded)&&!e.editId().equals(excluded))))
                .filter(e->parsed.timed()?parsed.start().compareTo(e.end())<0&&parsed.end().compareTo(e.start())>0
                        :!taskId.isBlank()&&e.taskId().equals(taskId)).findFirst().orElse(null);
    }
    private static String time(LocalDateTime value) {return value.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));}
}
