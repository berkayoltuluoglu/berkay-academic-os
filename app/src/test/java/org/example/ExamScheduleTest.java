package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.example.CourseData.*;
import static org.example.PlannerCommand.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamScheduleTest {
    @TempDir Path root;
    static final LocalDate WEEK = LocalDate.of(2026, 11, 16);
    static CourseData fixture() {
        String[] names = {"Kontrol Sistemleri", "Teknik Yabancı Dil I", "İleri Sayısal Tasarım", "Ayrık Matematik",
                "Veritabanı Yönetim Sistemleri", "Güç Sistemleri Analizi", "Haberleşme Sistemleri", "Sayısal İşaret İşleme"};
        int[] days = {0, 1, 2, 2, 3, 3, 4, 4};
        String[] starts = {"15:00", "15:00", "09:00", "15:00", "10:30", "15:00", "15:00", "17:00"};
        String[] ends = {"16:20", "16:20", "10:20", "16:20", "11:50", "16:20", "16:20", "18:20"};
        List<Course> courses = new ArrayList<>(); List<Work> exams = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            courses.add(new Course("c" + i, names[i], "C" + i, "", "", "2026-09-01", "2027-01-31",
                    List.of(new Lesson(days[i] + 1, "08:00", "09:00"))));
            exams.add(new Work("e" + i, "c" + i, names[i] + " vize", "Sınav", WEEK.plusDays(days[i]).toString(),
                    false, starts[i], 120, 0, "Yüksek", ends[i]));
        }
        return new CourseData(VERSION, courses, exams).withCancellations(List.of(
                new LessonCancellation("vize-week", "", "2026-11-16", "2026-11-20", "Vize haftası")));
    }
    private CourseStorageService save(CourseData data) throws Exception {
        var store = new CourseStorageService(root.resolve("data/courses/academic.json")); store.load(); store.save(data); return store;
    }

    @Test void allEightExamsRemainFixedWhileOnlyExamWeekLessonsAreCancelled() throws Exception {
        CourseData data = fixture(); save(data);
        var calendar = new CalendarCacheStore(root); calendar.setAccount("test@example.test");
        calendar.save("test@example.test", YearMonth.from(WEEK), List.of(CalendarEventItem.allDay("google", "Google etkinliği", WEEK, "", "")));
        var agenda = new DailyAgendaService();
        var days = agenda.loadRange(root, WEEK, WEEK.plusDays(6));
        assertTrue(days.stream().allMatch(d -> d.errors().isEmpty()));
        assertEquals(8, days.stream().flatMap(d -> d.events().stream()).filter(e -> e.kind().equals("Sınav")).count());
        assertEquals(0, days.stream().flatMap(d -> d.events().stream()).filter(e -> e.kind().equals("Ders")).count());
        assertTrue(days.getFirst().todos().stream().anyMatch(t -> t.module().equals("calendar")));
        for (int day = 0; day < 5; day++) {
            LocalDate date = WEEK.plusDays(day);
            assertTrue(TodayService.lessonsOn(data, date).isEmpty());
            long expected = data.courses().stream().filter(c -> c.lessons().getFirst().day() == date.getDayOfWeek().getValue()).count();
            assertEquals(expected, TodayService.lessonsOn(data, date.minusWeeks(1)).size());
            assertEquals(expected, TodayService.lessonsOn(data, date.plusWeeks(1)).size());
        }
        var snapshot = PlanningSnapshot.load(root, WEEK);
        assertTrue(snapshot.errors().isEmpty());
        assertEquals(8, snapshot.fixedBlocks().stream().filter(b -> b.kind().equals("Sınav")).count());
        for (var exam : data.work()) {
            var fixed = snapshot.fixedBlocks().stream().filter(b -> b.id().equals("exam-" + exam.id())).findFirst().orElseThrow();
            assertEquals(exam.dueTime(), fixed.start()); assertEquals(exam.endTime(), fixed.end());
            assertTrue(snapshot.freeSlots().stream().noneMatch(s -> s.date().equals(exam.date())
                    && s.start().compareTo(exam.endTime()) < 0 && s.end().compareTo(exam.dueTime()) > 0));
        }
        assertEquals("09:00", snapshot.pool().stream().filter(p -> p.id().equals("e2")).findFirst().orElseThrow().dueTime());
    }

    @Test void courseSpecificAndOverlappingCancellationsAreReversibleWithoutChangingWeeklyLessons() throws Exception {
        var original = fixture().withCancellations(List.of());
        var one = new LessonCancellation("one", "c0", "2026-11-16", "2026-11-16", "Tek ders");
        var all = new LessonCancellation("all", "", "2026-11-16", "2026-11-20", "Sınavlar");
        var data = original.withCancellations(List.of(one, all)); var store = save(data);
        assertTrue(TodayService.lessonsOn(data, WEEK).isEmpty());
        data = data.withCancellations(List.of(one)); store.save(data);
        assertTrue(TodayService.lessonsOn(data, WEEK).isEmpty());
        assertFalse(TodayService.lessonsOn(data, WEEK.plusDays(1)).isEmpty());
        store.save(data.withCancellations(List.of()));
        assertEquals(original, store.load());
        assertFalse(TodayService.lessonsOn(store.load(), WEEK).isEmpty());
    }

    @Test void completedExamStillBlocksTimeAndPlannerEditsPreserveEndTimeAndCancellations() throws Exception {
        var data = fixture(); save(data); var planner = new PlannerService(root);
        planner.execute(new SetEstimate("work", "e2", 180));
        planner.execute(new CompleteItem("work", "e2", true));
        var stored = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
        assertEquals(data.cancellations(), stored.cancellations());
        assertEquals("10:20", stored.work().get(2).endTime()); assertEquals(180, stored.work().get(2).estimatedMinutes());
        assertTrue(PlanningSnapshot.load(root, WEEK).fixedBlocks().stream().anyMatch(b -> b.id().equals("exam-e2")));
        assertThrows(Exception.class, () -> planner.execute(new SaveBlock(new StudySession("clash", "Çalışma", "2026-11-18", "10:00", "10:30", "", false), true, ConflictPolicy.REJECT)));
        planner.execute(new SaveBlock(new StudySession("adjacent", "Çalışma", "2026-11-18", "10:30", "11:00", "", false), true, ConflictPolicy.REJECT));
        planner.undo(); planner.undo();
        assertEquals("10:20", new CourseStorageService(root.resolve("data/courses/academic.json")).load().work().get(2).endTime());
    }

    @Test void v2LoadDoesNotInventAnExamDurationAndFirstSaveBacksUpExactBytes() throws Exception {
        Path file = root.resolve("academic.json");
        var tree = PlanningFiles.JSON.valueToTree(fixture());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("schemaVersion", 2).remove("cancellations");
        tree.get("work").forEach(w -> ((com.fasterxml.jackson.databind.node.ObjectNode) w).remove("endTime"));
        byte[] legacy = PlanningFiles.JSON.writeValueAsBytes(tree); Files.write(file, legacy);
        var store = new CourseStorageService(file); var data = store.load();
        assertEquals(VERSION, data.schemaVersion()); assertTrue(data.cancellations().isEmpty());
        assertTrue(data.work().stream().noneMatch(Work::scheduledExam));
        assertArrayEquals(legacy, Files.readAllBytes(file));
        store.save(data); assertArrayEquals(legacy, Files.readAllBytes(root.resolve("academic.json.bak")));
        assertEquals(data, new CourseStorageService(file).load());
    }

    @Test void monthCalendarShowsEachExamOnceWithItsFullRangeAndSource() throws Exception {
        save(fixture());
        var month = new CalendarMonthService().load(root, YearMonth.from(WEEK), List.of(), true);
        assertTrue(month.warnings().isEmpty());
        var exams = month.items().stream().filter(i -> i.getSourceName().equals("Sınav")).toList();
        assertEquals(8, exams.size());
        for (var exam : fixture().work()) {
            var entries = month.items().stream().filter(i -> i.getTitle().equals(exam.title())).toList();
            assertEquals(1, entries.size());
            assertEquals(LocalTime.parse(exam.dueTime()), entries.getFirst().getStartTime());
            assertEquals(LocalTime.parse(exam.endTime()), entries.getFirst().getEndTime());
            assertFalse(entries.getFirst().isEditable());
        }
        assertTrue(month.items().stream().noneMatch(i -> i.getSourceName().equals("Ders")
                && !i.getDate().isBefore(WEEK) && !i.getDate().isAfter(WEEK.plusDays(4))));
    }

    @Test void agentProposalsAvoidExamTimesAndFinishPreparationBeforeExamStart() throws Exception {
        save(fixture());
        var snapshot = PlanningSnapshot.load(root, WEEK);
        var proposals = new ProposalService(root, Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneOffset.UTC));
        for (var entry : Map.of("08:00", "", "09:00", "AFTER_DEADLINE", "10:30", "AFTER_DEADLINE").entrySet()) {
            var action = new PlanProposal.Action("schedule", null, "work", "e2", null, "2026-11-18", entry.getKey(), 60, "Sınava hazırlık", null);
            var proposal = new PlanProposal(1, null, "test", "Sınavdan önce çalışma", null, WEEK.toString(), snapshot.fingerprint(), List.of(action), null, null, null, null);
            var validation = proposals.validate(proposal);
            assertEquals(entry.getValue().isEmpty(), validation.valid());
            if (!validation.valid()) assertEquals(entry.getValue(), validation.issues().getFirst().code());
        }
        assertFalse(Files.exists(root.resolve("data/study/sessions.json")), "Doğrulama kayıt oluşturmamalı");
    }

    @Test void invalidExamTimesAndCancellationRangesAreRejected() {
        var base = fixture(); var exam = base.work().getFirst();
        for (String end : List.of("14:00", "15:00", "25:00", "16:20:01"))
            assertThrows(Exception.class, () -> CourseStorageService.validate(base.withWork(List.of(
                    new Work(exam.id(), exam.courseId(), exam.title(), "Sınav", exam.date(), false, "15:00", 0, 0, "Normal", end)))));
        assertThrows(Exception.class, () -> CourseStorageService.validate(base.withWork(List.of(
                new Work("bad", "c0", "Ödev", "Ödev", "2026-11-16", false, "15:00", 0, 0, "Normal", "16:20")))));
        for (var cancellation : List.of(new LessonCancellation("bad", "missing", "2026-11-16", "2026-11-20", ""),
                new LessonCancellation("bad", "", "2026-11-20", "2026-11-16", ""),
                new LessonCancellation("bad", "", "2026-11-16", "2028-11-16", "")))
            assertThrows(Exception.class, () -> CourseStorageService.validate(base.withCancellations(List.of(cancellation))));
    }
}
