package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.testing.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class GoogleClassroomServiceTest {
    @TempDir Path root;
    private final AtomicReference<String> title = new AtomicReference<>("Rapor");
    private final AtomicInteger failure = new AtomicInteger();
    private final List<String> requests = new ArrayList<>();
    private final AtomicBoolean failCache = new AtomicBoolean();

    private static MockLowLevelHttpResponse json(int status, String content) {
        return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(content);
    }
    private GoogleClassroomService service() throws Exception {
        Files.createDirectories(root.resolve("config")); Files.writeString(root.resolve("config/google-calendar-credentials.json"), "{}");
        var transport = new MockHttpTransport() {
            @Override public LowLevelHttpRequest buildRequest(String method, String address) {
                assertEquals("GET", method);
                return new MockLowLevelHttpRequest(address) {
                    @Override public MockLowLevelHttpResponse execute() {
                        requests.add(address); GenericUrl url = new GenericUrl(address);
                        if (address.contains("userinfo")) return json(200, "{\"email\":\"student@example.com\"}");
                        if ("1".equals(url.get("pageSize"))) return json(200, "{\"courses\":[]}");
                        if (failure.get() != 0) return json(failure.get(), "{\"error\":{\"message\":\"Forbidden\"}}");
                        if (address.contains("studentSubmissions")) return json(200, "{\"studentSubmissions\":[{\"courseWorkId\":\"work\",\"state\":\"TURNED_IN\"}]}");
                        if (address.contains("/courseWork")) return json(200, "{\"courseWork\":[{\"id\":\"work\",\"title\":\"" + title.get()
                                + "\",\"dueDate\":{\"year\":2026,\"month\":10,\"day\":12},\"dueTime\":{\"hours\":21,\"minutes\":30},\"alternateLink\":\"https://classroom.google.com/test\"},"
                                + "{\"id\":\"undated\",\"title\":\"Araştırma\"}]}");
                        if (address.contains("pageToken=second")) return json(200, "{\"courses\":[{\"id\":\"course2\",\"name\":\"Matematik\"}]}");
                        return json(200, "{\"courses\":[{\"id\":\"course\",\"name\":\"Devre\",\"section\":\"A\"}],\"nextPageToken\":\"second\"}");
                    }
                };
            }
        };
        var auth = new GoogleCalendarOAuthService() { @Override public Credential authorize(Path credentials, Path tokens) { return null; } };
        return new GoogleClassroomService(root, auth) {
            @Override HttpRequestFactory createRequests(Credential credential) { return transport.createRequestFactory(); }
            @Override void persistSnapshot(byte[] bytes) throws IOException {
                if (failCache.get()) throw new IOException("Cache write failed"); super.persistSnapshot(bytes);
            }
        };
    }
    @Test void readsEveryPageOwnSubmissionsAndKeepsDateLessWork() throws Exception {
        var service = service(); service.connect(); var data = service.sync();
        assertEquals("student@example.com", service.getAccountEmail()); assertEquals(2, data.courses().size()); assertEquals(4, data.work().size());
        assertTrue(data.work().getFirst().submitted()); assertTrue(data.work().get(1).dueDate().isBlank());
        assertTrue(requests.stream().anyMatch(url -> url.contains("pageToken=second")));
        assertTrue(requests.stream().filter(url -> url.contains("studentSubmissions")).allMatch(url -> url.contains("userId=me")));
        assertEquals(data, service.loadCached()); assertFalse(Files.exists(root.resolve("data/courses/academic.json")));
    }
    @Test void convertsUtcDeadlinesAcrossTheLocalDateBoundary() throws Exception {
        var json = new ObjectMapper().readTree("{\"dueDate\":{\"year\":2026,\"month\":10,\"day\":12},\"dueTime\":{\"hours\":21,\"minutes\":30}}");
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 30), GoogleClassroomService.parseDue(json, ZoneId.of("Europe/Istanbul")));
        assertNull(GoogleClassroomService.parseDue(new ObjectMapper().readTree("{}"), ZoneId.of("Europe/Istanbul")));
    }
    @Test void apiFailurePreservesTheLastSuccessfulSnapshot() throws Exception {
        var service = service(); service.connect(); service.sync(); byte[] before = Files.readAllBytes(root.resolve("data/classroom/catalog.json"));
        failure.set(403); IOException error = assertThrows(IOException.class, service::sync);
        assertTrue(error.getMessage().contains("Classroom API")); assertArrayEquals(before, Files.readAllBytes(root.resolve("data/classroom/catalog.json")));
    }
    @Test void importIsIdempotentAndCreatesPlannableLocalCourseworkWithoutInventingLessons() throws Exception {
        var service = service(); service.connect(); var data = service.sync(); service.importCourse(data, "course"); service.importCourse(data, "course");
        var local = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
        assertEquals(1, local.courses().size()); assertEquals(2, local.work().size()); assertFalse(local.courses().getFirst().planned());
        assertTrue(local.courses().getFirst().lessons().isEmpty()); assertTrue(local.work().stream().anyMatch(work -> work.date().isBlank()));
        assertEquals(2, PlanningTarget.load(root).size());
        String id = ClassroomImportService.workId(data.accountEmail(), "course", "work");
        assertEquals(1, new DailyAgendaService().load(root, LocalDate.parse(local.work().stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow().date()))
                .todos().stream().filter(todo -> todo.id().equals(id)).count());
    }
    @Test void refreshUpdatesImportedFieldsAndPreservesManualEstimatesAndTitles() throws Exception {
        var service = service(); service.connect(); var data = service.sync(); service.importCourse(data, "course");
        var store = new CourseStorageService(root.resolve("data/courses/academic.json")); var local = store.load();
        store.save(local.withWork(local.work().stream().map(work -> new CourseData.Work(work.id(), work.courseId(), "Benim başlığım", work.kind(),
                work.date(), work.done(), work.dueTime(), 90, 15, "Yüksek")).toList()));
        title.set("Google'da değişti"); service.sync();
        var changed = new CourseStorageService(root.resolve("data/courses/academic.json")).load().work().getFirst();
        assertEquals("Benim başlığım", changed.title()); assertEquals(90, changed.estimatedMinutes()); assertEquals(15, changed.loggedMinutes()); assertEquals("Yüksek", changed.priority());
    }
    @Test void failedSnapshotCommitRollsBackTheLocalCourseUpdateAsWell() throws Exception {
        var service = service(); service.connect(); var data = service.sync(); service.importCourse(data, "course");
        byte[] before = Files.readAllBytes(root.resolve("data/courses/academic.json")); byte[] cache = Files.readAllBytes(root.resolve("data/classroom/catalog.json"));
        title.set("Updated"); failCache.set(true); assertThrows(IOException.class, service::sync);
        assertArrayEquals(before, Files.readAllBytes(root.resolve("data/courses/academic.json")));
        assertArrayEquals(cache, Files.readAllBytes(root.resolve("data/classroom/catalog.json")));
    }
}
