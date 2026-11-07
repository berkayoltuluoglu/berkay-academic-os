package org.example;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Small deterministic Turkish grammar; unrecognized text stays in the title. */
public final class QuickTaskParser {
    private static final Locale TR = Locale.forLanguageTag("tr");
    public static TaskItem parse(String input, LocalDate today) {
        String title = input.trim(); LocalDate due = null; LocalTime time = null; int minutes = 0;
        Matcher duration = Pattern.compile("(?iu)(?<!\\S)(\\d+)\\s*(s|saat|dk|dakika)(?!\\S)").matcher(title);
        StringBuffer cleaned = new StringBuffer();
        while (duration.find()) {
            long amount = Long.parseLong(duration.group(1)); long total = minutes + amount * (Set.of("s","saat").contains(duration.group(2).toLowerCase(TR)) ? 60 : 1);
            if (total > 100000) return new TaskItem(input.trim(), null, "", "Bekliyor");
            minutes = (int)total; duration.appendReplacement(cleaned, "");
        }
        duration.appendTail(cleaned); title=cleaned.toString();
        Matcher clock = Pattern.compile("(?<!\\S)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\S)").matcher(title);
        if (clock.find()) { time=LocalTime.of(Integer.parseInt(clock.group(1)),Integer.parseInt(clock.group(2))); title=clock.replaceFirst(""); }
        String[] names={"pazartesi","salı","çarşamba","perşembe","cuma","cumartesi","pazar"};
        Matcher date = Pattern.compile("(?iu)(?<!\\S)(bugün|yarın|pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar|\\d{4}-\\d{2}-\\d{2})(?!\\S)").matcher(title);
        if (date.find()) {
            String token=date.group(1).toLowerCase(TR);
            try {
                if (token.equals("bugün")) due=today;
                else if (token.equals("yarın")) due=today.plusDays(1);
                else if (token.matches("\\d{4}-.*")) due=LocalDate.parse(token);
                else { int day=Arrays.asList(names).indexOf(token)+1; due=today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(day))); }
                title=date.replaceFirst("");
            } catch (RuntimeException ignored) { return new TaskItem(input.trim(),null,"","Bekliyor"); }
        }
        title=title.trim().replaceAll("\\s+"," ");
        if (title.isBlank()) title=input.trim();
        var task=new TaskItem(title,due,"","Bekliyor");task.setDueTime(time);task.setEstimatedMinutes(minutes);return task;
    }
}
