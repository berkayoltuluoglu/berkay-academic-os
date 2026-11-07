package org.example;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** The end date is inclusive for all-day events, and the actual end date for timed events. */
public record CalendarEventDraft(String title, String description, String location,
                                 LocalDate startDate, LocalDate endDate,
                                 LocalTime startTime, LocalTime endTime, boolean allDay) {
    public CalendarEventDraft {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description;
        location = location == null ? "" : location.trim();
        if (title.isBlank()) throw new IllegalArgumentException("Etkinlik başlığı boş olamaz.");
        if (startDate == null || endDate == null) throw new IllegalArgumentException("Başlangıç ve bitiş tarihi seç.");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("Bitiş tarihi başlangıçtan önce olamaz.");
        if (!allDay) {
            if (startTime == null || endTime == null) throw new IllegalArgumentException("Saatleri SS:DD biçiminde yaz (ör. 14:30).");
            if (!endDate.atTime(endTime).isAfter(startDate.atTime(startTime))) {
                throw new IllegalArgumentException("Bitiş zamanı başlangıçtan sonra olmalı.");
            }
        }
    }

    Event toGoogleEvent(ZoneId zone) {
        Event event = new Event().setSummary(title).setDescription(description).setLocation(location);
        if (allDay) {
            event.setStart(new EventDateTime().setDate(new DateTime(startDate.toString())));
            // Google's all-day end is exclusive; the editor uses the last included day.
            event.setEnd(new EventDateTime().setDate(new DateTime(endDate.plusDays(1).toString())));
        } else {
            event.setStart(new EventDateTime().setDateTime(new DateTime(
                    startDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli())).setTimeZone(zone.getId()));
            event.setEnd(new EventDateTime().setDateTime(new DateTime(
                    endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli())).setTimeZone(zone.getId()));
        }
        return event;
    }
}
