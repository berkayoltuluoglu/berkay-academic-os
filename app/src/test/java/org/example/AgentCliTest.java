package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.example.PlannerCommand.*;
class AgentCliTest {
    @TempDir Path root;
    final LocalDate week=LocalDate.of(2026,11,9);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"),ZoneId.of("Europe/Istanbul"));
    record Output(int code,JsonNode json,String raw){}
    Output cli(String...args)throws Exception{var all=new ArrayList<>(List.of("--profile",root.toString()));all.addAll(List.of(args));var bytes=new ByteArrayOutputStream();int code=AgentCli.run(all.toArray(String[]::new),new PrintStream(bytes),clock);String raw=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);return new Output(code,PlanningFiles.JSON.readTree(raw),raw);}
    PlanProposal.Action action(String start,int minutes){return new PlanProposal.Action("schedule",null,"","","Okuma",week.toString(),start,minutes,"Boş aralıkta çalışma",null);}
    PlanProposal proposal(List<PlanProposal.Action> actions)throws Exception{var snap=new PlannerService(root).snapshot(week);return new PlanProposal(1,"proposal","test-agent","Haftalık çalışma",null,week.toString(),snap.fingerprint(),actions,"pending","",List.of(),null);}
    Path write(PlanProposal p)throws Exception{Path file=root.resolve("proposal.json");Files.write(file,PlanningFiles.JSON.writeValueAsBytes(p));return file;}
    @Test void readCommandsReturnJsonWithoutJavafxOrSecretsAndInvalidRequestsFail()throws Exception{
        Files.createDirectories(root.resolve("config/google-calendar-tokens"));Files.writeString(root.resolve("config/google-calendar-tokens/secret"),"NEVER_OUTPUT_THIS_SECRET");
        for(var args:List.of(new String[]{"snapshot","--week",week.toString()},new String[]{"pool","--until","2026-12-31"},new String[]{"free-slots","--week",week.toString()},new String[]{"schema"},new String[]{"proposals","--status","pending"},new String[]{"suggest","--week",week.toString()},new String[]{"snapshot","--help"})){
            var result=cli(args);assertEquals(0,result.code(),result.raw());assertFalse(result.raw().contains("NEVER_OUTPUT_THIS_SECRET"));assertFalse(result.raw().contains("client_secret"));assertTrue(result.json().isObject());
        }
        assertNotEquals(0,cli("snapshot","--week","bad").code());assertNotEquals(0,cli("pool","--unknown","yes").code());
        assertFalse(Files.exists(root.resolve("data/planning/preferences.json")));
    }
    @Test void validationSubmissionPartialAcceptanceFeedbackAndUndoAreEndToEnd()throws Exception{
        var p=proposal(List.of(action("10:00",60),action("12:00",60)));Path file=write(p);
        assertEquals(0,cli("validate",file.toString()).code());assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
        assertEquals(0,cli("propose",file.toString()).code());assertEquals("pending",cli("proposal-status",p.id()).json().path("status").asText());
        var result=new ProposalService(root,clock).decide(p.id(),Map.of(0,p.commands().getFirst()),"İkincisi cumartesi olsun");assertEquals("partially_accepted",result.status());
        assertEquals("İkincisi cumartesi olsun",cli("proposal-status",p.id()).json().path("userNote").asText());
        assertEquals(1,new StudyStorageService(root.resolve("data/study/sessions.json")).load().size());
        new PlannerService(root).undo();assertTrue(new StudyStorageService(root.resolve("data/study/sessions.json")).load().isEmpty());
        assertThrows(Exception.class,()->new ProposalService(root,clock).decide(p.id(),Map.of(1,p.commands().get(1)),""));
    }
    @Test void invalidBatchCannotWriteAndStaleUnaffectedProposalCanStillBeAccepted()throws Exception{
        var p=proposal(List.of(action("10:00",60),action("10:30",60)));var invalid=cli("validate",write(p).toString());assertEquals(2,invalid.code());assertFalse(invalid.json().path("valid").asBoolean());assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
        p=proposal(List.of(action("10:00",60)));new ProposalService(root,clock).propose(p);
        new PlannerService(root).execute(new CreateTask(new TaskItem("Rapor",week.plusDays(5),"","Bekliyor")));
        assertTrue(new ProposalService(root,clock).validate(p).stale());new ProposalService(root,clock).decide(p.id(),Map.of(0,p.commands().getFirst()),"");
    }
    @Test void directWritesRequireBothApplyAndUserSettingAndRespectLocks()throws Exception{
        assertEquals("APPLY_REQUIRED",cli("task","add","--title","Rapor").json().path("error").path("code").asText());
        assertEquals("DIRECT_WRITES_DISABLED",cli("task","add","--title","Rapor","--apply").json().path("error").path("code").asText());
        new PlannerService(root).execute(new SetPreferences(PlanningPreferences.defaults().withAgentWrites(true)));
        assertNotEquals(0,cli("task","add","--title","Rapor","--apply","--status","pending").code());assertTrue(new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().isEmpty());
        assertEquals(0,cli("task","add","--title","Rapor","--apply").code());assertTrue(PlannerService.history(root).undo().getLast().actor().equals("agent-cli"));
        Path lockFile=root.resolve("data/study/sessions.json.lock");Files.createDirectories(lockFile.getParent());
        try(var channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
            assertEquals("BUSY",cli("block","add","--title","Okuma","--date",week.toString(),"--start","10:00","--apply").json().path("error").path("code").asText());
        }
        assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
    }
    @Test void deadlineAndBackupValidationCoverProposalFiles()throws Exception{
        var task=new TaskItem("task","Rapor",week,"","Bekliyor");task.setDueTime(LocalTime.of(10,30));new PlannerService(root).execute(new CreateTask(task));
        var late=new PlanProposal.Action("schedule",null,"task","task","Rapor",week.toString(),"10:00",60,"Teslime hazırlan",null);
        var p=proposal(List.of(late));assertFalse(new ProposalService(root,clock).validate(p).valid());
        var good=proposal(List.of(action("11:00",60)));new ProposalService(root,clock).propose(good);
        Path zip=root.resolve("backup.zip");new BackupService(root).exportTo(zip);assertTrue(new BackupService(root.resolve("restore")).inspect(zip).files()>0);
        Files.writeString(root.resolve("data/agent/proposals/invalid.json"),"{\"schemaVersion\":99}");new BackupService(root).exportTo(zip);assertThrows(Exception.class,()->new BackupService(root.resolve("bad-restore")).inspect(zip));
    }
    @Test void aProposalCanCreateAndScheduleALinkedTaskInOneUndoStep()throws Exception{
        var create=new PlanProposal.Action("create-task","new-task",null,null,"Yeni rapor",week.plusDays(1).toString(),null,120,"Yeni işi kaydet",null);
        var schedule=new PlanProposal.Action("schedule",null,"task","new-task",null,week.toString(),"10:00",60,"Teslim öncesi çalış",null);
        var p=proposal(List.of(create,schedule));assertTrue(new ProposalService(root,clock).validate(p).valid());new ProposalService(root,clock).propose(p);new ProposalService(root,clock).decide(p.id(),Map.of(0,create,1,schedule),"");
        assertEquals("new-task",new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().taskId());new PlannerService(root).undo();assertTrue(new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().isEmpty());
    }
    @Test void proposalsRespectDailyLimitsAndClosedTimesEvenWhenManuallyPrepared()throws Exception{
        var p=PlanningPreferences.defaults();new PlannerService(root).execute(new SetPreferences(new PlanningPreferences(1,p.timezone(),"08:00","18:00",60,15,120,List.of(new PlanningPreferences.Closed(week.toString(),0,"12:00","13:00")),false,false,25,5,15,4,true)));
        assertFalse(new ProposalService(root,clock).validate(proposal(List.of(action("10:00",60),action("14:00",60)))).valid());
        assertFalse(new ProposalService(root,clock).validate(proposal(List.of(action("12:00",30)))).valid());
        assertFalse(new ProposalService(root,clock).validate(proposal(List.of(action("19:00",30)))).valid());
    }
    @Test void automaticPlacementIsDeterministicRespectsDeadlinesAndDailyLimits()throws Exception{
        var planner=new PlannerService(root);var t=new TaskItem("task","Rapor",week.plusDays(2),"","Bekliyor");t.setEstimatedMinutes(600);planner.execute(new CreateTask(t));
        var prefs=PlanningPreferences.defaults();planner.execute(new SetPreferences(new PlanningPreferences(1,prefs.timezone(),"08:00","12:00",120,15,60,List.of(),false,false,25,5,15,4,true)));
        var snapshot=planner.snapshot(week);Clock clock=Clock.fixed(week.atStartOfDay(ZoneId.of(prefs.timezone())).toInstant(),ZoneId.of(prefs.timezone()));
        var a=new AutomaticPlanner().suggest(snapshot,clock);var b=new AutomaticPlanner().suggest(snapshot,clock);assertEquals(a,b);
        Map<String,Integer> load=new HashMap<>();for(var c:a.proposal().commands()){assertFalse(LocalDate.parse(c.date()).isAfter(week.plusDays(2)));load.merge(c.date(),c.minutes(),Integer::sum);}assertTrue(load.values().stream().allMatch(v->v<=120));assertFalse(a.unscheduled().isEmpty());assertTrue(new ProposalService(root,clock).validate(a.proposal()).valid());
    }
}
