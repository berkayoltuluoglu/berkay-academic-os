package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.example.PlannerCommand.*;
import static org.junit.jupiter.api.Assertions.*;

class ProposalPlacementTest {
    @TempDir Path root;
    final LocalDate week = LocalDate.of(2026, 11, 9);
    // UTC is still Monday; Istanbul has already reached Tuesday.
    final Clock clock = Clock.fixed(Instant.parse("2026-11-09T21:30:00Z"), ZoneOffset.UTC);

    PlanProposal.Action schedule(String date, String start, int minutes) {
        return new PlanProposal.Action("schedule", null, "", "", "Tekrar", date, start, minutes,
                "Boş aralıkta tekrar", null);
    }

    PlanProposal proposal(PlanProposal.Action... actions) throws Exception {
        var snapshot = new PlannerService(root).snapshot(week);
        return new PlanProposal(1, "placement", "test", "Tarih ve saat kontrolü", null, week.toString(),
                snapshot.fingerprint(), List.of(actions), "pending", "", List.of(), null);
    }

    @Test void scheduleMustStayInProposalWeekAndUseProfileLocalDate() throws Exception {
        var service = new ProposalService(root, clock);
        var cases = Map.of(
                "2026-11-08", "OUTSIDE_WEEK",
                "2026-11-16", "OUTSIDE_WEEK",
                "2026-11-09", "PAST_DATE");
        for (var entry : cases.entrySet()) {
            var p = proposal(schedule(entry.getKey(), "10:00", 60));
            var result = service.validate(p);
            assertFalse(result.valid());
            assertEquals(entry.getValue(), result.issues().getFirst().code());
            assertThrows(Exception.class, () -> service.propose(p));
        }
        assertTrue(service.validate(proposal(schedule("2026-11-10", "10:00", 60))).valid());
        assertTrue(service.validate(proposal(schedule("2026-11-15", "22:45", 60))).valid());
        assertFalse(Files.exists(root.resolve("data")), "Kuru doğrulama profile yazmamalı");
    }

    @Test void gridRejectsOffGridStartDurationAndSeconds() throws Exception {
        var service = new ProposalService(root, clock);
        for (var action : List.of(schedule("2026-11-10", "10:07", 60),
                schedule("2026-11-10", "10:00", 50), schedule("2026-11-10", "10:00:30", 60),
                schedule("2026-11-10", "10:00:00", 60))) {
            var result = service.validate(proposal(action));
            assertFalse(result.valid());
            assertEquals("TIME_GRID", result.issues().getFirst().code());
        }
        assertTrue(service.validate(proposal(schedule("2026-11-10", "10:15", 45))).valid());
    }

    @Test void missedBlockCanMoveToLaterWeekButAcceptanceRechecksDatesAndGrid() throws Exception {
        var planner = new PlannerService(root);
        planner.execute(new ScheduleBlock("", "", week.toString(), "10:00", 60, "Kaçırılan blok", ConflictPolicy.REJECT));
        var id = new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().id();
        var move = new PlanProposal.Action("move", id, null, null, null, "2026-11-16", "11:00", null,
                "Kaçırılan çalışmayı ileri haftaya taşı", null);
        var p = proposal(move);
        var service = new ProposalService(root, clock);
        assertTrue(service.validate(p).valid());
        service.propose(p);
        for (var invalid : List.of(
                new PlanProposal.Action("move", id, null, null, null, "2026-11-09", "11:00", null, "Taşı", null),
                new PlanProposal.Action("move", id, null, null, null, "2026-11-16", "11:07", null, "Taşı", null),
                new PlanProposal.Action("resize", id, null, null, null, null, null, 50, "Süreyi değiştir", null))) {
            assertFalse(service.validate(proposal(invalid)).valid());
            assertThrows(Exception.class, () -> service.decide(p.id(), Map.of(0, invalid), ""));
            assertEquals("pending", service.status(p.id()).status());
            assertEquals(week.toString(), new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().date());
        }
        service.decide(p.id(), Map.of(0, move), "");
        assertEquals("2026-11-16", new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().date());
    }

    @Test void acceptedProposalCannotBecomeAPastPlanWhileWaiting() throws Exception {
        var service = new ProposalService(root, clock);
        var p = proposal(schedule("2026-11-10", "10:00", 60));
        service.propose(p);
        var tomorrow = new ProposalService(root, Clock.offset(clock, Duration.ofDays(1)));
        assertFalse(tomorrow.validate(p).valid());
        assertThrows(Exception.class, () -> tomorrow.decide(p.id(), Map.of(0, p.commands().getFirst()), ""));
        assertEquals("pending", service.status(p.id()).status());
        assertFalse(Files.exists(root.resolve("data/study/sessions.json")));
        // Historical acceptance feedback remains readable, and the user can still reject an old proposal.
        assertEquals("rejected", tomorrow.decide(p.id(), Map.of(), "Tarihi geçti").status());
    }

    @Test void resizeUsesTheDateOfAnEarlierMoveInTheSameProposal() throws Exception {
        var planner = new PlannerService(root);
        planner.execute(new ScheduleBlock("", "", week.toString(), "10:00", 60, "Kaçırılan blok", ConflictPolicy.REJECT));
        planner.execute(new ScheduleBlock("", "", "2026-11-17", "15:00", 60, "Başka haftadaki blok", ConflictPolicy.REJECT));
        var blocks = new StudyStorageService(root.resolve("data/study/sessions.json")).load();
        var id = blocks.stream().filter(block -> block.date().equals(week.toString())).findFirst().orElseThrow().id();
        var otherId = blocks.stream().filter(block -> block.date().equals("2026-11-17")).findFirst().orElseThrow().id();
        var move = new PlanProposal.Action("move", id, null, null, null, "2026-11-16", "11:00", null, "İleri haftaya taşı", null);
        var resize = new PlanProposal.Action("resize", id, null, null, null, null, null, 90, "Yeni güne göre uzat", null);
        var unrelated = new PlanProposal.Action("resize", otherId, null, null, null, null, null, 90, "Süreyi değiştir", null);
        var service = new ProposalService(root, clock);
        assertEquals("NOT_FOUND", service.validate(proposal(move, unrelated)).issues().getFirst().code());
        var p = proposal(move, resize);
        assertTrue(service.validate(p).valid());
        service.propose(p);
        service.decide(p.id(), Map.of(0, move, 1, resize), "");
        var changed = new StudyStorageService(root.resolve("data/study/sessions.json")).load().stream()
                .filter(block -> block.id().equals(id)).findFirst().orElseThrow();
        assertEquals("2026-11-16", changed.date());
        assertEquals("12:30", changed.end());
    }

    @Test void resizeAndModifiedAcceptanceCannotBypassTheGridOrWeek() throws Exception {
        var planner = new PlannerService(root);
        planner.execute(new ScheduleBlock("", "", "2026-11-10", "10:00", 60, "Tekrar", ConflictPolicy.REJECT));
        var id = new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().id();
        var resize = new PlanProposal.Action("resize", id, null, null, null, null, null, 50, "Süreyi değiştir", null);
        var service = new ProposalService(root, clock);
        assertEquals("TIME_GRID", service.validate(proposal(resize)).issues().getFirst().code());

        var p = proposal(schedule("2026-11-10", "12:00", 60));
        service.propose(p);
        assertThrows(Exception.class, () -> service.decide(p.id(), Map.of(0, schedule("2026-11-16", "12:00", 60)), ""));
        assertThrows(Exception.class, () -> service.decide(p.id(), Map.of(0, schedule("2026-11-10", "12:07", 60)), ""));
        assertEquals("pending", service.status(p.id()).status());
        assertEquals(1, new StudyStorageService(root.resolve("data/study/sessions.json")).load().size());
    }

    @Test void emptyAndExpiredSuggestionsHaveNoInvalidProposal() throws Exception {
        var output = new ByteArrayOutputStream();
        assertEquals(0, AgentCli.run(new String[]{"--profile", root.toString(), "suggest", "--week", week.toString()},
                new PrintStream(output), clock));
        var json = PlanningFiles.JSON.readTree(output.toString());
        assertTrue(json.has("proposal"));
        assertTrue(json.get("proposal").isNull());
        assertTrue(json.path("unscheduled").isEmpty());
        assertFalse(json.path("message").asText().isBlank());
        assertFalse(Files.exists(root.resolve("data")));

        var planner = new PlannerService(root);
        var task = new TaskItem("expired", "Eski rapor", week, "", "Bekliyor");
        task.setEstimatedMinutes(60);
        planner.execute(new CreateTask(task));
        var result = new AutomaticPlanner().suggest(planner.snapshot(week), clock);
        assertNull(result.proposal());
        assertEquals("Teslim tarihi geçmiş", result.unscheduled().getFirst().reason());
        assertEquals(60, result.unscheduled().getFirst().minutes());
    }

    @Test void unschedulableWorkIsDifferentFromAnEmptyPoolAndMixedSuggestionsStillValidate() throws Exception {
        var planner = new PlannerService(root);
        planner.execute(new CreateTask(new TaskItem("expired", "Eski rapor", week, "", "Bekliyor")));
        planner.execute(new CreateTask(new TaskItem("next", "Yeni rapor", week.plusDays(6), "", "Bekliyor")));
        var result = new AutomaticPlanner().suggest(planner.snapshot(week), clock.withZone(ZoneId.of("Europe/Istanbul")));
        assertNotNull(result.proposal());
        assertTrue(new ProposalService(root, clock).validate(result.proposal()).valid());
        assertEquals(1, result.unscheduled().size());

        planner.execute(new SetPreferences(new PlanningPreferences(1, "Europe/Istanbul", "08:00", "08:15",
                240, 30, 120, List.of(), false, false, 25, 5, 15, 4, true)));
        result = new AutomaticPlanner().suggest(planner.snapshot(week), clock.withZone(ZoneId.of("Europe/Istanbul")));
        assertNull(result.proposal());
        assertEquals(2, result.unscheduled().size());
        assertTrue(result.unscheduled().stream().anyMatch(item -> item.id().equals("next") && item.reason().contains("yeterli boş aralık yok")));
    }
}
