package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Read-only aggregation: opening the dashboard never creates or modifies user data. */
public final class TodayService {
    public record Entry(String title, LocalDate date, String detail, String module, BigDecimal amount) {}
    public record Section(List<Entry> entries, String error) {
        public Section { entries = List.copyOf(entries); }
    }
    public record Snapshot(LocalDate date, Section tasks, Section lessons, Section coursework, Section payments) {}

    public Snapshot load(Path root, LocalDate today) {
        LocalDate until = today.plusDays(7);
        Section tasks;
        try {
            tasks = ok(new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().stream()
                    .filter(t -> !"Tamamlandı".equals(t.getStatus()) && t.getDate() != null && !t.getDate().isAfter(until))
                    .map(t -> new Entry(t.getTitle(), t.getDate(), t.getStatus(), "calendar", null))
                    .sorted(Comparator.comparing(Entry::date)).toList());
        } catch (Exception ex) { tasks = error("Görevler okunamadı: " + ex.getMessage()); }
        Section lessons, coursework;
        try {
            CourseData data = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
            lessons = ok(lessonsOn(data, today));
            Map<String, String> names = new HashMap<>();
            data.courses().forEach(c -> names.put(c.id(), c.name()));
            coursework = ok(data.work().stream().filter(w -> !w.done() && !w.date().isBlank() && !LocalDate.parse(w.date()).isAfter(until))
                    .map(w -> new Entry(w.title(), LocalDate.parse(w.date()), w.kind() + " · " + names.get(w.courseId())
                            + (w.timeLabel().isBlank() ? "" : " · " + w.timeLabel()), "courses", null))
                    .sorted(Comparator.comparing(Entry::date)).toList());
        } catch (Exception ex) { lessons = error("Dersler okunamadı: " + ex.getMessage()); coursework = lessons; }
        Section payments;
        try {
            String raw = new FinanceStorageService(root.resolve("data/finance/ledger.json")).load();
            JsonNode ledger = new ObjectMapper().readTree(raw);
            payments = ok(ledger == null || ledger.isNull() ? List.of() : paymentEntries(ledger, today, until));
        } catch (Exception ex) { payments = error("Borç Defteri okunamadı: " + ex.getMessage()); }
        return new Snapshot(today, tasks, lessons, coursework, payments);
    }

    static List<Entry> lessonsOn(CourseData data, LocalDate date) {
        List<Entry> result = new ArrayList<>();
        for (var course : data.courses()) {
            for (var lesson : course.lessons()) if (CourseSchedule.occursOn(data, course, lesson, date))
                result.add(new Entry(course.name(), date, lesson.start() + "–" + lesson.end()
                        + (course.section().isBlank() ? "" : " · " + course.section())
                        + ((lesson.room().isBlank() ? course.room() : lesson.room()).isBlank() ? "" : " · "
                        + (lesson.room().isBlank() ? course.room() : lesson.room())), "courses", null));
        }
        result.sort(Comparator.comparing(Entry::detail));
        return List.copyOf(result);
    }

    private List<Entry> paymentEntries(JsonNode ledger, LocalDate today, LocalDate until) {
        List<Entry> entries = new ArrayList<>();
        for (JsonNode debt : ledger.path("debts")) {
            if ("alacak".equals(debt.path("yon").asText()) || debt.path("due").asText().isBlank()) continue;
            LocalDate due = LocalDate.parse(debt.path("due").asText());
            if (due.isAfter(until)) continue;
            BigDecimal remaining = periodRemaining(debt, ledger.path("settings"));
            if (remaining.signum() > 0) entries.add(new Entry(debt.path("name").asText(), due,
                    "kredikarti".equals(debt.path("type").asText()) || "avans".equals(debt.path("type").asText())
                            ? "Defterdeki asgari ödeme planı" : "Defterdeki ödeme planı", "finance", remaining));
        }
        for (JsonNode sub : ledger.path("subs")) {
            if (!sub.path("active").asBoolean(true)) continue;
            int day = number(sub, "day").intValueExact();
            YearMonth month = YearMonth.from(today);
            LocalDate date = month.atDay(Math.min(day, month.lengthOfMonth()));
            if (date.isBefore(today)) { month = month.plusMonths(1); date = month.atDay(Math.min(day, month.lengthOfMonth())); }
            if (!date.isAfter(until)) entries.add(new Entry(sub.path("name").asText(), date,
                    "Abonelik planı · ödeme durumu izlenmiyor", "finance", number(sub, "amount")));
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::title));
        return entries;
    }

    // Mirrors the ledger's current-period target, including frozen targets after a partial payment.
    static BigDecimal periodRemaining(JsonNode debt, JsonNode settings) {
        String type = debt.path("type").asText();
        BigDecimal base;
        if (type.equals("kredi")) {
            int periods = number(debt, "vade").intValueExact();
            int remaining = present(debt, "kalanTaksit") ? number(debt, "kalanTaksit").intValueExact() : periods;
            if (remaining <= 0) return BigDecimal.ZERO;
            if (present(debt, "taksit")) base = number(debt, "taksit");
            else {
                double principal = number(debt, "principal").doubleValue();
                double rate = number(debt, "rate").doubleValue() / 100;
                // Equivalent annuity formula avoids overflow at long durations.
                base = money(BigDecimal.valueOf(rate == 0 ? principal / periods
                        : principal * rate / (1 - Math.pow(1 + rate, -periods))));
            }
        } else {
            BigDecimal balance = number(debt, "balance");
            if (balance.signum() <= 0) return BigDecimal.ZERO;
            base = balance;
            if (type.equals("kredikarti") || type.equals("avans")) {
                BigDecimal rate = present(debt, "asgariOverride") ? number(debt, "asgariOverride")
                        : number(settings, number(debt, "limit").compareTo(number(settings, "asgariEsik")) > 0 ? "asgariUst" : "asgariAlt");
                base = money(balance.multiply(rate).divide(BigDecimal.valueOf(100)));
            }
        }
        String period = debt.path("due").asText();
        if (period.isBlank()) period = "acik:" + debt.path("id").asText();
        if (period.equals(debt.path("donemAnahtari").asText())) {
            if (present(debt, "donemHedef")) base = number(debt, "donemHedef");
            base = base.subtract(number(debt, "donemOdenen"));
        }
        return money(base.max(BigDecimal.ZERO));
    }

    private static boolean present(JsonNode n, String key) { return n.hasNonNull(key) && !n.path(key).asText().isBlank(); }
    private static BigDecimal number(JsonNode n, String key) {
        if (!present(n, key)) return BigDecimal.ZERO;
        String text = n.path(key).asText().trim();
        if (text.contains(",")) text = text.replace(".", "").replace(',', '.');
        return new BigDecimal(text);
    }
    private static BigDecimal money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }
    private static Section ok(List<Entry> entries) { return new Section(entries, null); }
    private static Section error(String error) { return new Section(List.of(), error); }
}
