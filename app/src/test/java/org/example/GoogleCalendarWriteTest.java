package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.calendar.Calendar;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarWriteTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVENT_ID = "abcdef123456";
    private static final String ACCOUNT = "calendar-test@example.com";
    private record Request(String method, String url, String ifMatch, JsonNode body) {}
    @FunctionalInterface private interface Response { MockLowLevelHttpResponse respond(Request request) throws IOException; }

    private GoogleCalendarService service(Response response) throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/google-calendar-credentials.json"), "{}");
        var transport = new MockHttpTransport() {
            @Override public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override public MockLowLevelHttpResponse execute() throws IOException {
                        if (url.endsWith("/calendars/primary")) return json(200, "{\"id\":\"" + ACCOUNT + "\"}");
                        String content = getContentAsString();
                        return response.respond(new Request(method, url, getFirstHeaderValue("If-Match"),
                                content.isBlank() ? null : JSON.readTree(content)));
                    }
                };
            }
        };
        var client = new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), request -> {})
                .setApplicationName("Calendar write test").build();
        var auth = new GoogleCalendarOAuthService() {
            @Override public Credential authorize(Path credentials, Path tokens) { return null; }
        };
        return new GoogleCalendarService(auth, directory) {
            @Override Calendar createCalendarService(Credential credential) { return client; }
        };
    }

    private static MockLowLevelHttpResponse json(int status, String content) {
        return new MockLowLevelHttpResponse().setStatusCode(status)
                .setContentType("application/json; charset=UTF-8").setContent(content);
    }

    private static String event(String id, String title, LocalDate start, LocalDate lastDay) {
        return "{\"id\":\"" + id + "\",\"etag\":\"revision-1\",\"summary\":\"" + title
                + "\",\"start\":{\"date\":\"" + start + "\"},\"end\":{\"date\":\"" + lastDay.plusDays(1) + "\"}}";
    }

    private static CalendarEventDraft allDay(String title) {
        return new CalendarEventDraft(title, "Notes", "Room", LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 13), null, null, true);
    }

    private CalendarEventItem loadOne(GoogleCalendarService service) throws Exception {
        service.connect();
        return service.loadEventsForMonth(YearMonth.of(2026, 10)).getFirst();
    }

    @Test void insertsAllDayRangeWithExclusiveGoogleEndAndStableId() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        var service = service(request -> {
            sent.set(request);
            return json(200, event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13)));
        });
        service.connect();
        var saved = service.createEvent(EVENT_ID, allDay("Exam"));
        assertEquals("POST", sent.get().method());
        assertEquals(EVENT_ID, sent.get().body().path("id").asText());
        assertEquals("2026-10-11", sent.get().body().path("start").path("date").asText());
        assertEquals("2026-10-14", sent.get().body().path("end").path("date").asText());
        assertEquals(LocalDate.of(2026, 10, 13), saved.getEndDate());
        assertTrue(saved.occursOn(LocalDate.of(2026, 10, 12)));
        assertFalse(saved.occursOn(LocalDate.of(2026, 10, 14)));
    }

    @Test void timedPayloadPreservesOvernightEndDateAndTimezone() {
        var draft = new CalendarEventDraft("Study", "", "", LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 12), LocalTime.of(23, 30), LocalTime.of(1, 0), false);
        var event = draft.toGoogleEvent(ZoneId.of("Europe/Istanbul"));
        assertEquals(Instant.parse("2026-10-11T20:30:00Z").toEpochMilli(), event.getStart().getDateTime().getValue());
        assertEquals(Instant.parse("2026-10-11T22:00:00Z").toEpochMilli(), event.getEnd().getDateTime().getValue());
        assertEquals("Europe/Istanbul", event.getEnd().getTimeZone());
        assertNull(event.getStart().getDate());
    }

    @Test void validatesDraftBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> allDay(" "));
        assertThrows(IllegalArgumentException.class, () -> new CalendarEventDraft("Exam", "", "",
                LocalDate.now(), LocalDate.now().minusDays(1), null, null, true));
        assertThrows(IllegalArgumentException.class, () -> new CalendarEventDraft("Exam", "", "",
                LocalDate.now(), LocalDate.now(), LocalTime.NOON, LocalTime.NOON, false));
    }

    @Test void patchesOnlyEditableFieldsWithEtagAndClearsTimedFieldsWhenSwitchingToAllDay() throws Exception {
        var writes = new ArrayList<Request>();
        var service = service(request -> {
            if (request.method().equals("GET")) return json(200, "{\"items\":["
                    + event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)) + "]}");
            writes.add(request);
            return json(200, event(EVENT_ID, "Updated", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13)));
        });
        var original = loadOne(service);
        service.updateEvent(original, allDay("Updated"));
        Request write = writes.getFirst();
        assertEquals("PATCH", write.method());
        assertEquals("revision-1", write.ifMatch());
        assertTrue(write.url().contains("/events/" + EVENT_ID));
        assertTrue(write.url().contains("conferenceDataVersion=1"));
        assertFalse(write.body().has("attendees"));
        assertFalse(write.body().has("reminders"));
        assertFalse(write.body().has("recurrence"));
        assertFalse(write.body().has("conferenceData"));
        assertTrue(write.body().path("start").path("dateTime").isNull());
        assertTrue(write.body().path("end").path("timeZone").isNull());
    }

    @Test void timedPatchExplicitlyClearsTheOldAllDayDate() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        var service = service(request -> {
            if (request.method().equals("GET")) return json(200, "{\"items\":["
                    + event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)) + "]}");
            sent.set(request);
            var body = (ObjectNode) request.body().deepCopy();
            body.put("id", EVENT_ID).put("etag", "revision-2");
            return json(200, body.toString());
        });
        var original = loadOne(service);
        var updated = service.updateEvent(original, new CalendarEventDraft("Timed", "", "",
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12), LocalTime.of(23, 0), LocalTime.MIDNIGHT, false));
        assertTrue(sent.get().body().path("start").path("date").isNull());
        assertFalse(updated.isAllDay());
        assertEquals(LocalDate.of(2026, 10, 12), updated.getEndDate());
        assertFalse(updated.occursOn(LocalDate.of(2026, 10, 12)));
    }

    @Test void conflictingEditAndDeleteAreRejectedWithoutUnconditionalRetry() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        var service = service(request -> {
            if (request.method().equals("GET")) return json(200, "{\"items\":["
                    + event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)) + "]}");
            writes.incrementAndGet();
            assertEquals("revision-1", request.ifMatch());
            return json(412, "{\"error\":{\"code\":412,\"message\":\"Precondition Failed\"}}");
        });
        var original = loadOne(service);
        IOException edit = assertThrows(IOException.class, () -> service.updateEvent(original, allDay("Updated")));
        assertTrue(edit.getMessage().contains("Google'da değişmiş"));
        IOException delete = assertThrows(IOException.class, () -> service.deleteEvent(original));
        assertTrue(delete.getMessage().contains("takvimi yenile"));
        assertEquals(2, writes.get());
    }

    @Test void deletionUsesGoogleEventIdAndCurrentEtag() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        var service = service(request -> {
            if (request.method().equals("GET")) return json(200, "{\"items\":["
                    + event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)) + "]}");
            sent.set(request);
            return json(204, "");
        });
        var original = loadOne(service);
        service.deleteEvent(original);
        assertEquals("DELETE", sent.get().method());
        assertEquals("revision-1", sent.get().ifMatch());
        assertTrue(sent.get().url().contains("/events/" + EVENT_ID));
        assertTrue(sent.get().url().contains("sendUpdates=none"));
        assertNull(service.getLastErrorMessage());
    }

    @Test void retryOfAnAlreadyInsertedEventFindsItInsteadOfCreatingADuplicate() throws Exception {
        List<String> methods = new ArrayList<>();
        var service = service(request -> {
            methods.add(request.method());
            if (request.method().equals("POST")) return json(409, "{\"error\":{\"code\":409,\"message\":\"Already exists\"}}");
            ObjectNode existing = (ObjectNode) JSON.readTree(event(EVENT_ID, "Exam",
                    LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13)));
            existing.put("description", "Notes").put("location", "Room");
            return json(200, existing.toString());
        });
        service.connect();
        assertEquals(EVENT_ID, service.createEvent(EVENT_ID, allDay("Exam")).getId());
        assertEquals(List.of("POST", "GET"), methods);
    }

    @Test void aChangedDraftIsNotReportedAsSavedAfterADuplicateInsert() throws Exception {
        var service = service(request -> request.method().equals("POST")
                ? json(409, "{\"error\":{\"code\":409,\"message\":\"Already exists\"}}")
                : json(200, event(EVENT_ID, "Old title", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13))));
        service.connect();
        var error = assertThrows(IOException.class, () -> service.createEvent(EVENT_ID, allDay("New title")));
        assertTrue(error.getMessage().contains("bilgileri farklı"));
    }

    @Test void recurringInstancesKeepTheirOwnIdAndSpecialEventsCannotBeWritten() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        var data = (ObjectNode) JSON.readTree(event(EVENT_ID, "Exam", LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)));
        data.put("recurringEventId", "parent-series");
        var service = service(request -> {
            if (request.method().equals("GET")) return json(200, "{\"items\":[" + data + "]}");
            sent.set(request);
            return json(200, data.toString());
        });
        var original = loadOne(service);
        assertTrue(original.isRecurringInstance());
        service.updateEvent(original, allDay("New title"));
        assertTrue(sent.get().url().contains("/events/" + EVENT_ID));
        data.put("eventType", "birthday");
        var special = service.loadEventsForMonth(YearMonth.of(2026, 10)).getFirst();
        assertFalse(special.isEditable());
        assertThrows(IllegalArgumentException.class, () -> service.deleteEvent(special));
    }

    @Test @EnabledIf("org.example.FxTestSupport#isGuiAvailable")
    void uiPreservesFailedDraftThenCreatesEditsAndDeletesOnlyAfterGoogleSuccess() throws Exception {
        FxTestSupport.initialize();
        var stored = new AtomicReference<String>();
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger nonEmptyLoads = new AtomicInteger();
        var service = service(request -> {
            if (request.method().equals("GET")) {
                if (stored.get() != null && nonEmptyLoads.incrementAndGet() == 1) return json(500,
                        "{\"error\":{\"code\":500,\"message\":\"Temporary read failure\"}}");
                return json(200, "{\"items\":[" + (stored.get() == null ? "" : stored.get()) + "]}");
            }
            if (request.method().equals("POST") && inserts.incrementAndGet() == 1) {
                return json(403, "{\"error\":{\"code\":403,\"message\":\"No write permission\"}}");
            }
            if (request.method().equals("DELETE")) {
                if (deletes.incrementAndGet() == 1) return json(412,
                        "{\"error\":{\"code\":412,\"message\":\"Precondition Failed\"}}");
                stored.set(null);
                return json(204, "");
            }
            ObjectNode body = (ObjectNode) request.body().deepCopy();
            body.put("id", EVENT_ID).put("etag", "revision-1");
            stored.set(body.toString());
            return json(200, body.toString());
        });
        Stage stage = fx(() -> {
            Stage window = new Stage();
            window.setScene(new Scene(new CalendarModuleView(service).build(directory.resolve("tasks.json")), 1050, 740));
            window.show();
            return window;
        });
        try {
            fire(stage, "#google-calendar-connect");
            waitFor(() -> label(stage, "#google-calendar-status").contains("Bu ay 0 kayıt"));
            fire(stage, "#google-calendar-add");
            fx(() -> {
                ((TextField) dialogNode("#google-event-title")).setText("My exam");
                ((CheckBox) dialogNode("#google-event-all-day")).setSelected(true);
                ((Button) dialogNode("#google-event-save")).fire();
                return null;
            });
            waitFor(() -> dialogText("#google-event-error").contains("No write permission"));
            assertEquals("My exam", fx(() -> ((TextField) dialogNode("#google-event-title")).getText()));
            assertNull(stored.get());
            fx(() -> { ((Button) dialogNode("#google-event-save")).fire(); return null; });
            waitFor(() -> listSize(stage) == 1);
            waitFor(() -> label(stage, "#google-calendar-status").contains("Temporary read failure"));
            assertEquals("My exam", eventTitle(stage));
            fire(stage, "#google-calendar-refresh");
            waitFor(() -> label(stage, "#google-calendar-status").contains("Bu ay 1 kayıt"));
            fx(() -> { events(stage).getSelectionModel().selectFirst(); return null; });
            fire(stage, "#google-calendar-edit");
            fx(() -> {
                ((TextField) dialogNode("#google-event-title")).setText("Updated exam");
                ((Button) dialogNode("#google-event-save")).fire();
                return null;
            });
            waitFor(() -> eventTitle(stage).equals("Updated exam"));
            fx(() -> { events(stage).getSelectionModel().selectFirst(); return null; });
            fire(stage, "#google-calendar-delete");
            fx(() -> { ((Button) dialogNode("#google-event-delete-confirm")).fire(); return null; });
            waitFor(() -> label(stage, "#google-calendar-status").contains("Silme başarısız"));
            assertEquals(1, listSize(stage));
            assertNotNull(stored.get());
            fire(stage, "#google-calendar-refresh");
            waitFor(() -> label(stage, "#google-calendar-status").contains("Bu ay 1 kayıt"));
            fx(() -> { events(stage).getSelectionModel().selectFirst(); return null; });
            fire(stage, "#google-calendar-delete");
            fx(() -> { ((Button) dialogNode("#google-event-delete-confirm")).fire(); return null; });
            waitFor(() -> listSize(stage) == 0);
            assertNull(stored.get());
            assertFalse(Files.exists(directory.resolve("tasks.json")), "Google writes must not modify local task storage");
        } finally { fx(() -> { stage.close(); return null; }); }
    }

    private static void fire(Stage stage, String selector) throws Exception {
        fx(() -> { ((Button) stage.getScene().lookup(selector)).fire(); return null; });
    }

    private static String label(Stage stage, String selector) {
        try { return fx(() -> ((Label) stage.getScene().lookup(selector)).getText()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    @SuppressWarnings("unchecked") private static ListView<CalendarEventItem> events(Stage stage) {
        return (ListView<CalendarEventItem>) stage.getScene().lookup("#google-calendar-events");
    }

    private static int listSize(Stage stage) {
        try { return fx(() -> events(stage).getItems().size()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    private static String eventTitle(Stage stage) {
        try { return fx(() -> events(stage).getItems().isEmpty() ? "" : events(stage).getItems().getFirst().getTitle()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    private static Node dialogNode(String selector) {
        return Window.getWindows().stream().filter(window -> window.isShowing() && window.getScene() != null)
                .map(window -> window.getScene().lookup(selector)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private static String dialogText(String selector) {
        try { return fx(() -> ((Label) dialogNode(selector)).getText()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.getAsBoolean());
    }
}
