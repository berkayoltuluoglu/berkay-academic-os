package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TodayServiceTest {
    @TempDir Path root;
    private final LocalDate today = LocalDate.of(2026, 9, 30);
    @Test void emptyDashboardDoesNotCreateData() throws Exception {
        var result = new TodayService().load(root, today);
        assertTrue(result.tasks().entries().isEmpty()); assertNull(result.tasks().error());
        assertTrue(result.payments().entries().isEmpty()); assertNull(result.payments().error());
        assertFalse(Files.exists(root.resolve("data")));
    }
    @Test void combinesOpenTasksCourseworkAndTermBoundedLessons() throws Exception {
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(
                new TaskItem("Geciken", today.minusDays(3), "", "Bekliyor"),
                new TaskItem("Bitti", today, "", "Tamamlandı"),
                new TaskItem("İleride", today.plusDays(8), "", "Bekliyor")));
        var courses = new CourseStorageService(root.resolve("data/courses/academic.json"));
        courses.load(); courses.save(CourseStorageServiceTest.fixture());
        var result = new TodayService().load(root, today);
        assertEquals(List.of("Geciken"), result.tasks().entries().stream().map(TodayService.Entry::title).toList());
        assertEquals(1, result.lessons().entries().size());
        assertEquals("Laboratuvar raporu", result.coursework().entries().getFirst().title());
        assertTrue(new TodayService().load(root, LocalDate.of(2027, 2, 3)).lessons().entries().isEmpty());
        var fixture = CourseStorageServiceTest.fixture();
        courses.save(new CourseData(CourseData.VERSION, fixture.courses(), List.of(new CourseData.Work("lab", "circuits", "Laboratuvar raporu", "Ödev", "2026-10-02", true))));
        assertTrue(new TodayService().load(root, today).coursework().entries().isEmpty());
    }
    @Test void paymentSummaryRespectsPartialPaymentsClosedLoansAndReceivables() throws Exception {
        var ledger = FinanceTestData.populated();
        var card = (com.fasterxml.jackson.databind.node.ObjectNode) ledger.path("debts").get(0);
        card.put("due", today.toString()).put("balance", 950).put("donemAnahtari", today.toString())
                .put("donemHedef", 200).put("donemOdenen", 50);
        ((com.fasterxml.jackson.databind.node.ObjectNode) ledger.path("debts").get(1)).put("due", today.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) ledger.path("debts").get(2)).put("due", today.toString()).put("kalanTaksit", "0");
        var sub = (com.fasterxml.jackson.databind.node.ObjectNode) ledger.path("subs").get(0); sub.put("day", 31);
        var store = new FinanceStorageService(root.resolve("data/finance/ledger.json")); store.load(); store.save(ledger.toString());
        var payments = new TodayService().load(root, today).payments();
        assertNull(payments.error()); assertEquals(2, payments.entries().size());
        assertEquals(150, payments.entries().stream().filter(e -> e.title().equals("Örnek kart")).findFirst().orElseThrow().amount().intValue());
        assertEquals(today, payments.entries().stream().filter(e -> e.title().equals("İnternet")).findFirst().orElseThrow().date());
        sub.put("active", false); store.save(ledger.toString());
        assertEquals(1, new TodayService().load(root, today).payments().entries().size());
    }
    @Test void corruptSourceIsReportedWithoutHidingOtherSectionsOrWritingFiles() throws Exception {
        Path finance = root.resolve("data/finance/ledger.json"); Files.createDirectories(finance.getParent()); Files.writeString(finance, "broken");
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(new TaskItem("Çalış", today, "", "Bekliyor")));
        var result = new TodayService().load(root, today);
        assertNotNull(result.payments().error()); assertEquals(1, result.tasks().entries().size());
        assertEquals("broken", Files.readString(finance));
    }
}
