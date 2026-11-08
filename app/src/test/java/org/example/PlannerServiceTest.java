package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.example.PlannerCommand.*;

class PlannerServiceTest {
    @TempDir Path root;
    final LocalDate week = LocalDate.of(2026,11,9);
    StudySession block(String id,String start,String end) { return new StudySession(id,"Çalışma",week.toString(),start,end,"",false); }
    @Test void nearestPolicyPreservesDurationAndUndoRedoSurviveRestart() throws Exception {
        var service = new PlannerService(root);
        service.execute(new SaveBlock(block("a","10:00","11:00"),true,ConflictPolicy.REJECT));
        var result = service.execute(new SaveBlock(block("b","10:15","11:15"),true,ConflictPolicy.NEAREST));
        assertEquals("11:00",result.blocks().get(1).start()); assertFalse(result.notices().isEmpty());
        new PlannerService(root).undo(); assertEquals(1,new StudyStorageService(root.resolve("data/study/sessions.json")).load().size());
        new PlannerService(root).redo(); assertEquals(2,new StudyStorageService(root.resolve("data/study/sessions.json")).load().size());
    }
    @Test void historyRecordsOnlyChangedFieldsEvenWithLargeCourseFiles() throws Exception {
        // A real profile's academic.json carries a ~160 KB course catalog the planner never touches.
        List<CourseData.Course> catalog = new ArrayList<>();
        for (int i = 0; i < 400; i++) catalog.add(new CourseData.Course("katalog-" + i, "Katalog dersi " + i, "KAT" + i, "Öğretim üyesi " + i, "D-" + i,
                "2026-09-21", "2027-01-08", List.of(new CourseData.Lesson(1 + i % 5, "09:30", "11:20", "D-" + i)), "", null, null, "", false));
        var courseStore = new CourseStorageService(root.resolve("data/courses/academic.json")); courseStore.load(); courseStore.save(new CourseData(2, catalog, List.of(), List.of()));
        assertTrue(Files.size(root.resolve("data/courses/academic.json")) > 100_000);
        var planner = new PlannerService(root);
        planner.execute(new SaveBlock(block("a","10:00","11:00"),true,ConflictPolicy.REJECT));
        for (int i = 0; i < 30; i++) planner.execute(new MoveBlock("a", week.toString(), (i % 2 == 0 ? "12:00" : "10:00"), ConflictPolicy.REJECT));
        long size = Files.size(root.resolve(PlannerService.HISTORY));
        assertTrue(size < 60_000, "geçmiş " + size + " bayt");
        var last = PlannerService.history(root).undo().getLast();
        assertTrue(last.edits().stream().allMatch(e -> e.file().equals("data/study/sessions.json")), last.edits().toString());
        assertEquals(List.of(".sessions", "[a]", ".start"), last.edits().getFirst().path());
        planner.undo(); assertEquals("12:00", new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().start());
        planner.redo(); assertEquals("10:00", new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().start());
        assertTrue(Files.readString(root.resolve("data/courses/academic.json")).contains("katalog-399"));
    }
    @Test void undoRestoresDeletedBlockAtItsPlaceAndLegacyHistoryIsReset() throws Exception {
        Files.createDirectories(root.resolve("data/planning"));
        Files.writeString(root.resolve(PlannerService.HISTORY), "{\"schemaVersion\":1,\"undo\":[],\"redo\":[]}");
        assertTrue(PlannerService.history(root).undo().isEmpty());
        var planner = new PlannerService(root);
        planner.execute(List.of(new SaveBlock(block("a","08:00","09:00"),true,ConflictPolicy.REJECT),new SaveBlock(block("b","10:00","11:00"),true,ConflictPolicy.REJECT),
                new SaveBlock(block("c","12:00","13:00"),true,ConflictPolicy.REJECT)),"user","üç blok");
        planner.execute(new DeleteBlock("b"));
        planner.undo();
        assertEquals(List.of("a","b","c"), new StudyStorageService(root.resolve("data/study/sessions.json")).load().stream().map(StudySession::id).toList());
        planner.undo(); assertTrue(new StudyStorageService(root.resolve("data/study/sessions.json")).load().isEmpty());
        planner.redo(); assertEquals(3, new StudyStorageService(root.resolve("data/study/sessions.json")).load().size());
    }
    @Test void undoRefusesWhenTheFieldWasChangedElsewhere() throws Exception {
        var planner = new PlannerService(root);
        planner.execute(new SaveBlock(block("a","10:00","11:00"),true,ConflictPolicy.REJECT));
        planner.execute(new MoveBlock("a", week.toString(), "12:00", ConflictPolicy.REJECT));
        Path sessions = root.resolve("data/study/sessions.json");
        Files.writeString(sessions, Files.readString(sessions).replace("\"12:00\"", "\"14:00\"").replace("\"13:00\"", "\"15:00\""));
        assertTrue(assertThrows(Exception.class, planner::undo).getMessage().contains("geri alınamaz"));
        assertEquals("14:00", new StudyStorageService(sessions).load().getFirst().start());
    }
    @Test void overlapPermissionDoesNotBypassLinkValidationAndBatchRollsBack() throws Exception {
        var planner = new PlannerService(root);
        assertThrows(Exception.class,()->planner.execute(List.of(new SaveBlock(block("a","09:00","10:00"),true,ConflictPolicy.ALLOW),
                new SaveBlock(new StudySession("b","İş",week.toString(),"10:00","11:00","",false,"missing","",0),true,ConflictPolicy.ALLOW)),"test","batch"));
        assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
    }
    @Test void staleSnapshotAndUndoCannotOverwriteExternalEdits() throws Exception {
        var a = new PlannerService(root); a.snapshot(week);
        var b = new PlannerService(root); b.execute(new SaveBlock(block("a","09:00","10:00"),true,ConflictPolicy.REJECT));
        assertTrue(assertThrows(Exception.class,()->a.execute(new SaveBlock(block("b","11:00","12:00"),true,ConflictPolicy.REJECT))).getMessage().contains("Önce yenile"));
        var store = new StudyStorageService(root.resolve("data/study/sessions.json")); store.load(); store.save(List.of(block("changed","12:00","13:00")));
        assertThrows(Exception.class,b::undo); assertEquals("changed",store.load().getFirst().id());
    }
    @Test void corruptedSettingsAndSymlinkTargetsAreNeverOverwritten() throws Exception {
        Path file=root.resolve(PlanningPreferences.FILE); Files.createDirectories(file.getParent()); Files.writeString(file,"{\"schemaVersion\":99}");
        assertThrows(Exception.class,()->new PlannerService(root).execute(new SetPreferences(PlanningPreferences.defaults())));
        assertEquals("{\"schemaVersion\":99}",Files.readString(file));
        if (System.getProperty("os.name").contains("Linux")) {
            Path outside=Files.createTempDirectory("planner-outside-");
            try { Files.createSymbolicLink(root.resolve("link"),outside); assertThrows(Exception.class,()->PlanningFiles.commit(root,Map.of(),Map.of("link/value",new byte[]{1}))); assertFalse(Files.exists(outside.resolve("value"))); }
            finally { Files.deleteIfExists(outside); }
        }
    }
    @Test void corruptedUndoPayloadIsRejectedBeforeItCanOverwriteThePlan() throws Exception {
        var planner=new PlannerService(root);planner.execute(new SaveBlock(block("a","10:00","11:00"),true,ConflictPolicy.REJECT));
        var history=PlannerService.history(root);var change=history.undo().getFirst();var current=PlanningFiles.JSON.readTree(root.resolve("data/study/sessions.json").toFile());
        var invalid=new PlanEdits.Edit("data/study/sessions.json",List.of(),new com.fasterxml.jackson.databind.node.TextNode("broken"),current,null,null);
        var bad=new PlannerService.PlanChange(change.id(),change.at(),change.actor(),change.reason(),List.of(invalid));Files.write(root.resolve(PlannerService.HISTORY),PlanningFiles.JSON.writeValueAsBytes(new PlannerService.History(PlannerService.HISTORY_VERSION,List.of(bad),List.of())));
        String before=Files.readString(root.resolve("data/study/sessions.json"));assertThrows(Exception.class,planner::undo);assertEquals(before,Files.readString(root.resolve("data/study/sessions.json")));
    }
    @Test void allDayGoogleEventsAreFixedAndCannotBeScheduledOver() throws Exception {
        var cache=new CalendarCacheStore(root);cache.setAccount("test@example.test");cache.save("test@example.test",YearMonth.from(week),List.of(CalendarEventItem.allDay("all-day","Sabit gün",week,"","")));
        var planner=new PlannerService(root);var snapshot=planner.snapshot(week);assertTrue(snapshot.fixedBlocks().stream().anyMatch(b->b.id().contains("all-day")));assertTrue(snapshot.freeSlots().stream().noneMatch(s->s.date().equals(week.toString())));
        assertThrows(Exception.class,()->planner.execute(new SaveBlock(block("a","10:00","11:00"),true,ConflictPolicy.REJECT)));
    }
    @Test void snapshotsRespectAnExternalRecordLockAndRemainReadOnly() throws Exception {
        Path file=root.resolve("data/study/sessions.json.lock");Files.createDirectories(file.getParent());
        try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
            assertTrue(assertThrows(Exception.class,()->new PlannerService(root).snapshot(week)).getMessage().contains("BUSY"));
        }
        assertFalse(Files.exists(root.resolve("data/tasks.json")));assertFalse(Files.exists(root.resolve(PlannerService.HISTORY)));
    }
    @Test void rangeLoadsNativeStoresOnceEvenWhenWeekCrossesMonthBoundary() {
        var reads=new int[1]; var service=new DailyAgendaService() {
            @Override Snapshot readSnapshot(Path path,YearMonth month) { reads[0]++;return super.readSnapshot(path,month); }
        };
        var days=service.loadRange(root,LocalDate.of(2026,11,30),LocalDate.of(2026,12,6));
        assertEquals(1,reads[0]);assertEquals(7,days.size()); assertTrue(days.stream().allMatch(d->d.errors().isEmpty()));
    }
}
