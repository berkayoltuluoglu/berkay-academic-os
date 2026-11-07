package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Last verified Google events, shared by the agenda and background reminders. */
final class CalendarCacheStore {
    record Item(String id, String title, String date, String endDate, String startTime, String endTime,
                String description, String location, boolean allDay, String etag, boolean editable, boolean recurring,
                String calendarId, String sourceName, String sourceColor) {}
    record Month(int schemaVersion, String account, String month, String syncedAt, List<Item> items) {}
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    CalendarCacheStore(Path root) { this.root = root; }
    void setAccount(String account) throws IOException {
        java.util.Properties p = new java.util.Properties(); p.setProperty("account", account);
        var writer = new java.io.StringWriter(); p.store(writer, "Calendar cache account");
        AtomicFileWriter.writeText(root.resolve("data/calendar/account.properties"), writer.toString());
    }
    void clearAccount() throws IOException { Files.deleteIfExists(root.resolve("data/calendar/account.properties")); }
    String account() throws IOException {
        Path path=root.resolve("data/calendar/account.properties"); if (!Files.exists(path)) return "";
        var properties=new java.util.Properties(); try(var reader=Files.newBufferedReader(path)){ properties.load(reader); }
        return properties.getProperty("account","");
    }
    Set<YearMonth> months() throws IOException {
        Set<YearMonth> result=new TreeSet<>(); Path directory=root.resolve("data/calendar");
        if (Files.isDirectory(directory)) try(var files=Files.list(directory)){ for(var path:files.toList()) {
            String name=path.getFileName().toString(); if(name.matches("\\d{4}-\\d{2}\\.json")) result.add(YearMonth.parse(name.substring(0,7)));
        }} return result;
    }
    void save(String account, YearMonth month, List<CalendarEventItem> events) throws IOException {
        var items = events.stream().map(e -> new Item(e.getId(), e.getTitle(), e.getDate().toString(), e.getEndDate().toString(),
                e.getStartTime() == null ? "" : e.getStartTime().toString(), e.getEndTime() == null ? "" : e.getEndTime().toString(),
                e.getDescription(), e.getLocation(), e.isAllDay(), e.getEtag(), e.isEditable(), e.isRecurringInstance(),
                e.getCalendarId(), e.getSourceName(), e.getSourceColor())).toList();
        byte[] bytes = mapper.writeValueAsBytes(new Month(2, account, month.toString(), Instant.now().toString(), items));
        if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Takvim kopyası 5 MB sınırını aşıyor.");
        AtomicFileWriter.write(root.resolve("data/calendar/" + month + ".json"), bytes);
    }
    List<CalendarEventItem> load(YearMonth month) throws IOException {
        return new CalendarSourcesStore(root).filter(account(),new CalendarWriteQueue(root).overlay(account(),month,loadRaw(month)));
    }
    List<CalendarEventItem> loadRaw(YearMonth month) throws IOException {
        Path path = root.resolve("data/calendar/" + month + ".json");
        if (!Files.exists(path)) return List.of();
        Path identity = root.resolve("data/calendar/account.properties");
        if (!Files.exists(identity)) return List.of();
        var p = new java.util.Properties(); try (var reader = Files.newBufferedReader(identity)) { p.load(reader); }
        Month data = mapper.readValue(FinanceStorageService.readLimited(path), Month.class);
        if ((data.schemaVersion() != 1 && data.schemaVersion() != 2) || !data.month().equals(month.toString())) throw new IOException("Takvim kopyası geçersiz.");
        if (!data.account().equals(p.getProperty("account"))) return List.of();
        List<CalendarEventItem> result = new ArrayList<>();
        for (Item e : data.items()) {
            LocalDate date = LocalDate.parse(e.date()), endDate = LocalDate.parse(e.endDate());
            CalendarEventItem value = e.allDay() ? CalendarEventItem.allDay(e.id(), e.title(), date, e.description(), e.location())
                    : CalendarEventItem.timed(e.id(), e.title(), date.atTime(LocalTime.parse(e.startTime())),
                    endDate.atTime(LocalTime.parse(e.endTime())), e.description(), e.location());
            result.add(value.withGoogleMetadata(endDate, e.etag(), e.editable(), e.recurring()).withSource(e.calendarId(),e.sourceName(),e.sourceColor()));
        }
        return result;
    }
}
