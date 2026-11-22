package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.example.PlannerCommand.*;
import static org.junit.jupiter.api.Assertions.*;

class JournalServiceTest {
    @TempDir Path root;
    private final LocalDate date=LocalDate.of(2026,11,1);
    private TaskItem create(PlannerService planner)throws Exception{
        var task=new TaskItem("task","Sinyaller",null,"Not","Bekliyor");task.setEstimatedMinutes(120);
        planner.execute(List.of(new CreateTask(task),new PlaceTask("task","dersler","")),"user","Hızlı görev");return task;
    }
    private JournalStore.Activity activity(String id,String input,String task){
        var parsed=ActivityParser.parse(input);return new JournalStore.Activity(id,date.toString(),parsed.title(),parsed.start(),parsed.end(),parsed.minutes(),task,"");
    }
    @Test void legacyTasksRemainSharedAndListsPersistWithoutCreatingStudyBlocks()throws Exception{
        Files.createDirectories(root.resolve("data"));String legacy="[{\"title\":\"Eski görev\",\"date\":null,\"description\":\"Not\",\"status\":\"Bekliyor\"}]";
        Files.writeString(root.resolve("data/tasks.json"),legacy);
        String oldId=new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst().getId();
        var snapshot=JournalTimeline.load(root,date);assertEquals(legacy,Files.readString(root.resolve("data/tasks.json")));assertEquals(3,snapshot.journal().lists().size());
        var planner=new PlannerService(root);planner.execute(new CreateList("personal","Araştırma"));planner.execute(new PlaceTask(oldId,"personal",""));
        planner.execute(new RenameList("personal","Projeler"));
        assertEquals(oldId,new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst().getId());
        assertEquals("personal",JournalStore.load(root).placement(oldId).listId());
        planner.execute(new DeleteList("personal"));assertEquals("genel",JournalStore.load(root).placement(oldId).listId());
        assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
    }
    @Test void manualTimeUpdatesRemainingWorkAndUndoRedoWithoutCompletingTask()throws Exception{
        var planner=new PlannerService(root);create(planner);
        planner.execute(new SaveActivity(activity("first","30 dk kontrol çalıştım","task"),false));
        planner.execute(new SaveActivity(activity("second","10:30–11:15 Sinyaller çalıştım","task"),false));
        assertEquals(75,JournalTimeline.load(root,date).totalMinutes());
        var item=PlanningSnapshot.load(root,date).pool().stream().filter(p->p.id().equals("task")).findFirst().orElseThrow();
        assertEquals(75,item.studiedMinutes());assertEquals(45,item.remainingMinutes());
        assertEquals("Bekliyor",new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst().getStatus());
        planner.undo();assertEquals(30,JournalTimeline.load(root,date).totalMinutes());planner.redo();assertEquals(75,JournalTimeline.load(root,date).totalMinutes());
        planner.execute(new DeleteActivity("first"));assertEquals(45,JournalTimeline.load(root,date).totalMinutes());
    }
    @Test void mergingFocusCountsOnceAndPreservesOriginalCredit()throws Exception{
        var planner=new PlannerService(root);create(planner);
        var focus=new StudyTimeLogService(root);focus.credit(new StudyTimeLogService.Entry("focus","task","task",25,"2026-11-01T10:00:00Z"));
        String before=Files.readString(root.resolve("data/time/focus-log.json"));
        assertThrows(IOException.class,()->planner.execute(new SaveActivity(activity("duplicate","25 dk Sinyaller","task"),false)));
        planner.execute(new SaveActivity(new JournalStore.Activity("note",date.toString(),"Çıkmış sorular","","",0,"task","focus"),false));
        var day=JournalTimeline.load(root,date);assertEquals(25,day.totalMinutes());assertEquals(1,day.entries().size());assertEquals("Çıkmış sorular",day.entries().getFirst().title());
        assertEquals(25,PlanningSnapshot.load(root,date).pool().getFirst().studiedMinutes());assertEquals(before,Files.readString(root.resolve("data/time/focus-log.json")));
        planner.execute(new DeleteActivity("note"));assertEquals(25,JournalTimeline.load(root,date).totalMinutes());
    }
    @Test void crossMidnightFocusIsSplitBetweenLocalDays()throws Exception{
        var planner=new PlannerService(root);create(planner);
        new StudyTimeLogService(root).credit(new StudyTimeLogService.Entry("focus","task","task",25,"2026-11-01T21:10:00Z"));
        assertEquals(15,JournalTimeline.load(root,date).totalMinutes());assertEquals(10,JournalTimeline.load(root,date.plusDays(1)).totalMinutes());
    }
    @Test void deletingTaskPreservesActivitiesAndBlocksAndCanBeUndone()throws Exception{
        var planner=new PlannerService(root);create(planner);planner.execute(new SaveActivity(activity("first","30 dk Sinyaller","task"),false));
        var blocks=new StudyStorageService(root.resolve("data/study/sessions.json"));blocks.load();blocks.save(List.of(new StudySession("block","Sinyaller",date.toString(),"10:00","11:00","",false,"task","",30)));
        planner.execute(new DeleteTask("task"));assertEquals(0,new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().size());
        assertEquals("",JournalStore.load(root).activities().getFirst().taskId());assertEquals(30,JournalTimeline.load(root,date).totalMinutes());
        var block=new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst();assertEquals("",block.taskId());assertEquals(30,block.actualMinutes());
        planner.undo();assertEquals("task",new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().taskId());
    }
    @Test void staleFormCannotOverwriteAnotherWritersTaskChanges()throws Exception{
        var planner=new PlannerService(root);var task=create(planner);var baseline=JournalTimeline.load(root,date).baseline();
        var external=task.copy();external.setDescription("Başka pencere");new PlannerService(root).execute(new UpdateTask(external));
        planner.expectFiles(baseline);task.setTitle("Eski form");assertThrows(IOException.class,()->planner.execute(new UpdateTask(task)));
        assertEquals("Başka pencere",new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst().getDescription());
    }
    @Test void invalidOrFutureEntriesDoNotReplaceTheJournal()throws Exception{
        var planner=new PlannerService(root);create(planner);String before=Files.readString(root.resolve(JournalStore.FILE));
        var future=new JournalStore.Activity("future",LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(2).toString(),"Çalışma","","",30,"","");
        assertThrows(IOException.class,()->planner.execute(new SaveActivity(future,false)));
        assertEquals(before,Files.readString(root.resolve(JournalStore.FILE)));
        Files.writeString(root.resolve(JournalStore.FILE),"{broken");assertFalse(PlanningSnapshot.load(root,date).errors().isEmpty());
        assertThrows(IOException.class,()->JournalTimeline.load(root,date));
    }
    @Test void backupRestoresListsActivitiesAndSharedTasksTogether()throws Exception{
        var planner=new PlannerService(root);create(planner);planner.execute(new SaveActivity(activity("first","30 dk Tekrar","task"),false));
        Path archive=root.resolve("journal-backup.zip");var backup=new BackupService(root);backup.exportTo(archive);
        planner.execute(new DeleteActivity("first"));assertEquals(0,JournalStore.load(root).activities().size());
        backup.restoreFrom(archive);assertEquals(1,JournalStore.load(root).activities().size());
    }
    @Test void existingConfigurationGainsJournalNavigationWithoutWritingProfileConfig()throws Exception{
        var config=new ConfigLoader().load();config.getModules().removeIf(m->m.getId().equals("activity"));
        Path file=root.resolve("config/app-config.json");Files.createDirectories(file.getParent());
        String original=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config);Files.writeString(file,original);
        var loaded=new ConfigLoader().load(root,"Linux");
        assertTrue(loaded.getModules().stream().anyMatch(m->m.getId().equals("activity")));assertEquals(original,Files.readString(file));
    }

    @Test void deletingTaskKeepsTheFocusDescriptionAndAllowsEditingItsNote()throws Exception{
        var planner=new PlannerService(root);create(planner);
        new StudyTimeLogService(root).credit(new StudyTimeLogService.Entry("focus","task","task",25,"2026-11-01T10:00:00Z"));
        planner.execute(new DeleteTask("task"));var entry=JournalTimeline.load(root,date).entries().getFirst();
        assertEquals("Sinyaller",entry.title());assertEquals("",entry.taskId());assertEquals(25,entry.minutes());
        planner.execute(new SaveActivity(new JournalStore.Activity(entry.editId(),date.toString(),"Soru çözümü","","",0,"","focus"),false));
        assertEquals(25,JournalTimeline.load(root,date).totalMinutes());
    }

    @Test void corruptJournalIsReportedAndCannotBeOverwrittenByAPlannerWrite()throws Exception{
        Files.createDirectories(root.resolve(JournalStore.FILE).getParent());Files.writeString(root.resolve(JournalStore.FILE),"broken");
        assertFalse(PlanningSnapshot.load(root,date).errors().isEmpty());
        assertThrows(IOException.class,()->new PlannerService(root).execute(new CreateTask(new TaskItem("Yeni",null,"","Bekliyor"))));
        assertEquals("broken",Files.readString(root.resolve(JournalStore.FILE)));assertFalse(Files.exists(root.resolve("data/tasks.json")));
    }

}
