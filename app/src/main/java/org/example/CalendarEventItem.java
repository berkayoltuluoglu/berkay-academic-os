package org.example;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CalendarEventItem {
    private String id;
    private String title;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String description;
    private String location;
    private boolean allDay;
    private LocalDate endDate;
    private String etag;
    private boolean editable = true;
    private boolean recurringInstance;
    private String syncState = "";
    private String calendarId = "primary";
    private String sourceName = "Ana takvim";
    private String sourceColor = "#4285f4";
    private boolean done;

    public CalendarEventItem() {
    }

    public CalendarEventItem(String id, String title, LocalDate date, LocalTime startTime, LocalTime endTime,
                             String description, String location, boolean allDay) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
        this.location = location;
        this.allDay = allDay;
        this.endDate = date;
    }

    public static CalendarEventItem allDay(String id, String title, LocalDate date, String description, String location) {
        return new CalendarEventItem(id, title, date, null, null, description, location, true);
    }

    public static CalendarEventItem timed(String id, String title, LocalDateTime start, LocalDateTime end,
                                          String description, String location) {
        CalendarEventItem item = new CalendarEventItem(
                id,
                title,
                start.toLocalDate(),
                start.toLocalTime(),
                end == null ? null : end.toLocalTime(),
                description,
                location,
                false
        );
        item.endDate = end == null ? start.toLocalDate() : end.toLocalDate();
        return item;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public boolean isAllDay() { return allDay; }
    public LocalDate getEndDate() { return endDate == null ? date : endDate; }
    public String getEtag() { return etag; }
    public boolean isEditable() { return editable; }
    public boolean isRecurringInstance() { return recurringInstance; }
    public String getSyncState() { return syncState; }
    public String getCalendarId() { return calendarId; }
    public String getSourceName() { return sourceName; }
    public String getSourceColor() { return sourceColor; }
    public boolean isDone() { return done; }
    public boolean isPrimaryCalendar() { return "primary".equals(calendarId); }
    public String key() { return calendarId + "\u0000" + id; }
    CalendarEventItem withSource(String id, String name, String color) {
        calendarId = id == null || id.isBlank() ? "primary" : id;
        sourceName = name == null || name.isBlank() ? "Ana takvim" : name;
        sourceColor = color != null && color.matches("#[a-fA-F0-9]{6}") ? color : "#4285f4";
        if (!isPrimaryCalendar()) editable = false;
        return this;
    }
    CalendarEventItem withDone(boolean value) { done=value; return this; }
    CalendarEventItem withSyncState(String value) { syncState=value; return this; }

    CalendarEventItem withGoogleMetadata(LocalDate endDate, String etag, boolean editable, boolean recurringInstance) {
        this.endDate = endDate;
        this.etag = etag;
        this.editable = editable;
        this.recurringInstance = recurringInstance;
        return this;
    }

    public boolean occursOn(LocalDate day) {
        if (date == null || day.isBefore(date) || day.isAfter(getEndDate())) return false;
        // A timed event ending at midnight does not occupy its end date.
        return allDay || !day.equals(getEndDate()) || endTime == null || !endTime.equals(LocalTime.MIDNIGHT);
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        if (allDay || startTime == null) {
            builder.append("[Tüm Gün] ");
        } else {
            builder.append(startTime);
            if (endTime != null) {
                builder.append(" - ").append(endTime);
            }
            builder.append(" | ");
        }
        builder.append(title == null ? "(Başlıksız)" : title);
        builder.append(" · ").append(sourceName);
        if (done) builder.append(" · Tamamlandı");
        if (!syncState.isBlank()) builder.append(" · ").append(syncState);
        if (getEndDate() != null && !getEndDate().equals(date)) {
            builder.append(" (").append(date).append(" → ").append(getEndDate()).append(")");
        }
        if (location != null && !location.isBlank()) {
            builder.append(" @ ").append(location);
        }
        return builder.toString();
    }
}
