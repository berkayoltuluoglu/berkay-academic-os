package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.example.PlannerCommand.*;
class PlannerPoolTest {
    @TempDir Path root;
    final LocalDate monday=LocalDate.of(2026,11,9);
    ClassroomData classroom(String title) {return new ClassroomData(1,"student@example.test","2026-11-01T00:00:00Z",List.of(new ClassroomData.Course("c","Fizik","","")),List.of(new ClassroomData.Work("w","c",title,"","2026-12-31","17:00","NEW",""),new ClassroomData.Work("other","c","Diğer","","","","NEW","")));}
    @Test void dropImportsOnlyOneWorkAndSecondDropKeepsStableIdentityAndManualFields() throws Exception {
        var data=classroom("Ödev");AtomicFileWriter.write(root.resolve("data/classroom/catalog.json"),PlanningFiles.JSON.writeValueAsBytes(data));
        var planner=new PlannerService(root);String id=ClassroomImportService.workId(data.accountEmail(),"c","w");
        planner.execute(new ScheduleBlock("classroom",id,monday.toString(),"10:00",60,"",ConflictPolicy.REJECT));
        planner.execute(new SetEstimate("work",id,180));
        planner.execute(new ScheduleBlock("classroom",id,monday.toString(),"12:00",60,"",ConflictPolicy.REJECT));
        var local=new CourseStorageService(root.resolve("data/courses/academic.json")).load();assertEquals(1,local.work().size());assertEquals(1,local.courses().size());assertEquals(180,local.work().getFirst().estimatedMinutes());
        new ClassroomImportService(root).refreshImported(data,classroom("Yeni başlık"));
        var merged=new CourseStorageService(root.resolve("data/courses/academic.json")).load();assertEquals(180,merged.work().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow().estimatedMinutes());
        assertTrue(planner.snapshot(monday).pool().stream().anyMatch(p->p.date().equals("2026-12-31")));
    }
    @Test void quickParserPreservesUnknownWordsAndDateLessTasksRoundTrip() throws Exception {
        var task=QuickTaskParser.parse("Fizik raporu cuma 17:00 2s 30dk",monday);assertEquals("Fizik raporu",task.getTitle());assertEquals(monday.plusDays(4),task.getDate());assertEquals(LocalTime.of(17,0),task.getDueTime());assertEquals(150,task.getEstimatedMinutes());
        var unknown=QuickTaskParser.parse("Belirsiz bir iş",monday);assertNull(unknown.getDate());assertEquals("Belirsiz bir iş",unknown.getTitle());
        new PlannerService(root).execute(new CreateTask(unknown));assertNull(new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().getFirst().getDate());assertTrue(new TodayService().load(root,monday).tasks().error()==null);
    }
}
