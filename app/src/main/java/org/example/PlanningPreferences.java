package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public record PlanningPreferences(int schemaVersion, String timezone, String start, String end,
                                  int dailyMinutes, int minMinutes, int maxMinutes, List<Closed> closed,
                                  boolean agentWrites, boolean pomodoro, int workMinutes, int shortBreak,
                                  int longBreak, int cycles, boolean deadlineNotifications) {
    public record Closed(String date, int day, String start, String end) {}
    public static final String FILE = "data/planning/preferences.json";
    public PlanningPreferences { closed = closed == null ? List.of() : List.copyOf(closed); }
    public static PlanningPreferences defaults() {
        return new PlanningPreferences(1, "Europe/Istanbul", "08:00", "24:00", 240, 15, 120, List.of(), false, false, 25, 5, 15, 4, true);
    }
    public PlanningPreferences withAgentWrites(boolean value) {
        return new PlanningPreferences(schemaVersion, timezone, start, end, dailyMinutes, minMinutes, maxMinutes, closed,
                value, pomodoro, workMinutes, shortBreak, longBreak, cycles, deadlineNotifications);
    }
    static PlanningPreferences load(Path root) throws IOException {
        var value = PlanningFiles.read(root, FILE, PlanningPreferences.class, defaults()); value.validate(); return value;
    }
    void validate() throws IOException {
        try {
            ZoneId.of(timezone);
            if (schemaVersion != 1 || minute(start) >= minute(end) || minute(start) < 0 || minute(end) > 1440
                    || dailyMinutes < 15 || dailyMinutes > 1440 || minMinutes < 15 || maxMinutes > 180 || minMinutes > maxMinutes
                    || workMinutes < 1 || workMinutes > 180 || shortBreak < 1 || shortBreak > 60
                    || longBreak < 1 || longBreak > 120 || cycles < 1 || cycles > 12 || closed.size() > 1000) throw new IllegalArgumentException();
            for (var c : closed) {
                if (c.date() != null && !c.date().isBlank()) LocalDate.parse(c.date());
                else if (c.day() < 1 || c.day() > 7) throw new IllegalArgumentException();
                if (minute(c.start()) >= minute(c.end())) throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) { throw new IOException("Planlama tercihleri geçersiz", e); }
    }
    static int minute(String time) {
        if ("24:00".equals(time)) return 1440;
        LocalTime parsed = LocalTime.parse(time); if (parsed.getSecond() != 0 || parsed.getNano() != 0) throw new IllegalArgumentException("Dakika kullan");
        return parsed.getHour() * 60 + parsed.getMinute();
    }
    static String time(int minute) {
        if (minute == 1440) return "23:59"; // Native schema 1 cannot represent an end at 24:00.
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
