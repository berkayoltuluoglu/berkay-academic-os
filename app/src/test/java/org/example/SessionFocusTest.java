package org.example;

import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.example.PlannerCommand.*;
class SessionFocusTest {
    @TempDir Path root;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"),ZoneId.of("Europe/Istanbul"));
    @Test void sessionFocusResumesAndCreditsBlockExactlyOnceIncludingAcrossReview()throws Exception{
        var task=new TaskItem("task","Fizik",LocalDate.of(2026,11,10),"","Bekliyor");task.setEstimatedMinutes(120);
        var planner=new PlannerService(root);planner.execute(new CreateTask(task));planner.execute(new SaveBlock(new StudySession("session","Fizik","2026-11-09","10:00","11:00","",false,"task","",0),true,ConflictPolicy.REJECT));
        Path file=root.resolve("data/focus.properties");var focus=new FocusTimerService(file,clock);focus.startSession("session",60);
        var midway=new FocusTimerService(file,Clock.offset(clock,Duration.ofMinutes(15)));assertEquals(45*60000,midway.remainingMillis());
        var after=new FocusTimerService(file,Clock.offset(clock,Duration.ofMinutes(61)));after.creditCompleted();after.creditCompleted();
        var sessions=new StudyStorageService(root.resolve("data/study/sessions.json")).load();assertEquals(60,sessions.getFirst().actualMinutes());assertEquals(1,new StudyTimeLogService(root).load().size());
        planner.execute(new CompleteBlock("session",55,true));after.creditCompleted();sessions=new StudyStorageService(root.resolve("data/study/sessions.json")).load();
        assertEquals(55,new StudyProgressService().forTask(task,sessions,new StudyTimeLogService(root).load()).studiedMinutes());
    }
    @Test void versionOneTargetsReadAndPartialFinishAndBreakDoNotDoubleCredit()throws Exception{
        Path file=root.resolve("data/focus.properties");Files.createDirectories(file.getParent());Files.writeString(file,"version=1\nremaining=1500000\ndeadline=0\nduration=1500000\ntargetKind=task\ntargetId=task\nfocusId=\n");
        var focus=new FocusTimerService(file,clock);assertEquals("task",focus.targetId());focus.toggle();
        var midway=new FocusTimerService(file,Clock.offset(clock,Duration.ofMinutes(10)));midway.finish();midway.creditCompleted();assertEquals(10,new StudyTimeLogService(root).load().getFirst().minutes());
        midway.startBreak(5);new FocusTimerService(file,Clock.offset(clock,Duration.ofMinutes(16))).creditCompleted();assertEquals(1,new StudyTimeLogService(root).load().size());
    }
}
