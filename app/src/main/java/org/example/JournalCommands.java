package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.example.PlannerCommand.*;

/** Called only on the planner's isolated staging profile, before its guarded commit. */
final class JournalCommands {
    static boolean apply(Path root, PlannerCommand command) throws Exception {
        if (!(command instanceof CreateList || command instanceof RenameList || command instanceof DeleteList
                || command instanceof PlaceTask || command instanceof SaveActivity || command instanceof DeleteActivity || command instanceof DeleteTask)) return false;
        var doc=JournalStore.load(root);
        if(command instanceof CreateList c){
            var lists=new ArrayList<>(doc.lists());if(lists.stream().anyMatch(l->l.id().equals(c.id())))throw new IOException("Liste kimliği zaten kullanılıyor.");
            lists.add(new JournalStore.TaskList(c.id(),c.name().trim()));doc=doc.withLists(lists);
        } else if(command instanceof RenameList c){
            requireList(doc,c.id());doc=doc.withLists(doc.lists().stream().map(l->l.id().equals(c.id())?new JournalStore.TaskList(l.id(),c.name().trim()):l).toList());
        } else if(command instanceof DeleteList c){
            requireList(doc,c.id());if(c.id().equals("genel"))throw new IOException("Genel listesi silinemez.");
            var placements=new LinkedHashMap<>(doc.placements());placements.replaceAll((id,p)->p.listId().equals(c.id())?new JournalStore.Placement("genel",p.courseId()):p);
            doc=doc.withPlacements(placements).withLists(doc.lists().stream().filter(l->!l.id().equals(c.id())).toList());
        } else if(command instanceof PlaceTask c){
            requireList(doc,c.listId());var tasks=new TaskStorageService(root.resolve("data/tasks.json")).loadTasks();
            if(tasks.stream().noneMatch(t->t.getId().equals(c.id())))throw new IOException("Görev bulunamadı; önce yenile.");
            if(!c.courseId().isBlank()&&new CourseStorageService(root.resolve("data/courses/academic.json")).load().courses().stream().noneMatch(course->course.id().equals(c.courseId())))throw new IOException("Ders bulunamadı.");
            var placements=new LinkedHashMap<>(doc.placements());placements.put(c.id(),new JournalStore.Placement(c.listId(),c.courseId()));doc=doc.withPlacements(placements);
        } else if(command instanceof SaveActivity c){
            var value=c.activity();var date=LocalDate.parse(value.date());var zone=ZoneId.of(PlanningPreferences.load(root).timezone());
            if(date.isAfter(LocalDate.now(zone)))throw new IOException("Gerçekleşen çalışma gelecek güne kaydedilemez.");
            var snapshot=JournalTimeline.load(root,date);
            if(!value.taskId().isBlank()&&snapshot.tasks().stream().noneMatch(t->t.getId().equals(value.taskId())))throw new IOException("Bağlı görev silinmiş; bağlantıyı kaldırıp yeniden dene.");
            if(!value.focusId().isBlank()){
                var source=snapshot.entries().stream().filter(e->e.focusId().equals(value.focusId())).findFirst().orElseThrow(()->new IOException("Birleştirilecek sayaç kaydı değişti; önce yenile."));
                if(!value.taskId().equals(source.taskId()))throw new IOException("Birleştirme sayaç kaydının görev bağlantısını değiştiremez.");
            } else if(!c.allowOverlap()){
                var parsed=new ActivityParser.Parsed(value.title(),value.start(),value.end(),value.minutes());
                if(JournalTimeline.conflict(parsed,value.taskId(),value.id(),snapshot.entries())!=null)throw new IOException("Bu çalışma sayaçta var. Birleştir veya ayrı kayıt tercihini açıkça seç.");
                boolean overlap=snapshot.entries().stream().filter(e->!e.id().equals(value.id())).anyMatch(e->
                    (!value.start().isBlank()&&!e.start().isBlank()&&value.start().compareTo(e.end())<0&&value.end().compareTo(e.start())>0)
                    || (e.title().equals(value.title())&&e.minutes()==value.minutes()&&e.start().equals(value.start())&&e.taskId().equals(value.taskId())));
                if(overlap)throw new IOException("Benzer veya çakışan kayıt var; ayrı kaydetmek için Alt+Enter kullan.");
            }
            var next=new ArrayList<>(doc.activities());next.removeIf(a->a.id().equals(value.id())||(!value.focusId().isBlank()&&a.focusId().equals(value.focusId())&&a.date().equals(value.date())));
            next.add(value);doc=doc.withActivities(next);
        } else if(command instanceof DeleteActivity c){
            if(doc.activities().stream().noneMatch(a->a.id().equals(c.id())))throw new IOException("Kayıt bulunamadı; sayaç süreleri odak kaynağında korunur.");
            doc=doc.withActivities(doc.activities().stream().filter(a->!a.id().equals(c.id())).toList());
        } else if(command instanceof DeleteTask c){
            var focus=new FocusTimerService(root.resolve("data/focus.properties"));
            if(focus.targetKind().equals("task")&&focus.targetId().equals(c.id())&&(focus.running()||focus.remainingMillis()!=focus.durationMillis()))throw new IOException("Bu görevin odağını önce bitir veya sıfırla.");
            var store=new TaskStorageService(root.resolve("data/tasks.json"));var tasks=store.loadTasks();
            var old=tasks.stream().filter(t->t.getId().equals(c.id())).findFirst().orElseThrow(()->new IOException("Görev bulunamadı."));
            store.saveTasks(tasks.stream().filter(t->!t.getId().equals(c.id())).toList());
            var sessions=new StudyStorageService(root.resolve("data/study/sessions.json"));var blocks=sessions.load();
            if(blocks.stream().anyMatch(b->b.taskId().equals(c.id())))sessions.save(blocks.stream().map(b->b.taskId().equals(c.id())?new StudySession(b.id(),b.title(),b.date(),b.start(),b.end(),b.courseId(),b.done(),"",b.workId(),b.actualMinutes()):b).toList());
            var placements=new LinkedHashMap<>(doc.placements());placements.remove(c.id());doc=doc.withPlacements(placements);
            doc=doc.withActivities(doc.activities().stream().map(a->a.taskId().equals(c.id())?new JournalStore.Activity(a.id(),a.date(),a.title(),a.start(),a.end(),a.minutes(),"",a.focusId()):a).toList());
            var kept=new ArrayList<>(doc.activities());var zone=ZoneId.of(PlanningPreferences.load(root).timezone());
            for(var record:new StudyTimeLogService(root).load())if(record.kind().equals("task")&&record.targetId().equals(c.id())){
                var end=LocalDateTime.ofInstant(Instant.parse(record.completedAt()),zone).withSecond(0).withNano(0);var start=end.minusMinutes(record.minutes());
                for(var day=start.toLocalDate();!day.isAfter(end.minusNanos(1).toLocalDate());day=day.plusDays(1)){
                    String date=day.toString();if(kept.stream().noneMatch(a->a.focusId().equals(record.id())&&a.date().equals(date)))
                        kept.add(new JournalStore.Activity(UUID.randomUUID().toString(),date,old.getTitle(),"","",0,"",record.id()));
                }
            }
            doc=doc.withActivities(kept);
        }
        JournalStore.save(root,doc);return true;
    }
    private static void requireList(JournalStore.Document doc,String id)throws IOException{if(doc.lists().stream().noneMatch(l->l.id().equals(id)))throw new IOException("Liste bulunamadı.");}
}
