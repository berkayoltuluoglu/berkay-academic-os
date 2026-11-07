package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.calendar.Calendar;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarServiceTest {
    @TempDir Path directory;
    private static final String ACCOUNT = "calendar-test@example.com";
    private static final String PRIMARY = "{\"id\":\"" + ACCOUNT + "\",\"summary\":\"Primary\"}";

    private GoogleCalendarService service(Function<String, MockLowLevelHttpResponse> response) throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/google-calendar-credentials.json"), "{}");
        var transport = new MockHttpTransport() {
            @Override public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override public MockLowLevelHttpResponse execute() { return response.apply(url); }
                };
            }
        };
        var client = new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), request -> {})
                .setApplicationName("Calendar test").build();
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

    @Test void connectionVerifiesGoogleAndExposesItsAccount() throws Exception {
        List<String> requests = new ArrayList<>();
        var service = service(url -> { requests.add(url); return json(200, PRIMARY); });
        assertFalse(service.isConnected());
        assertNull(service.getAccountEmail());
        service.connect();
        assertEquals(1, requests.size());
        assertTrue(requests.getFirst().endsWith("/calendars/primary"));
        assertTrue(service.isConnected());
        assertEquals(ACCOUNT, service.getAccountEmail());
        assertTrue(service.getSourceHint().contains(ACCOUNT));
    }

    @Test void failedValidationClearsThePreviousAccountInsteadOfClaimingSuccess() throws Exception {
        var content = new AtomicReference<>(PRIMARY);
        var service = service(url -> json(200, content.get()));
        service.connect();
        content.set("{}");
        assertThrows(IllegalStateException.class, service::connect);
        assertFalse(service.isConnected());
        assertNull(service.getAccountEmail());
        assertNotNull(service.getLastErrorMessage());
    }

    @Test void successfulEmptyMonthAndApiFailureHaveDifferentStatus() throws Exception {
        var status = new AtomicInteger(200);
        var service = service(url -> url.contains("/events?")
                ? json(status.get(), status.get() == 200 ? "{\"items\":[]}"
                    : "{\"error\":{\"code\":403,\"message\":\"Calendar access denied\"}}")
                : json(200, PRIMARY));
        service.connect();
        assertTrue(service.loadEventsForMonth(YearMonth.now()).isEmpty());
        assertNull(service.getLastErrorMessage());
        status.set(403);
        assertTrue(service.loadEventsForMonth(YearMonth.now()).isEmpty());
        assertTrue(service.getLastErrorMessage().contains("Calendar access denied"));
        status.set(200);
        service.loadEventsForMonth(YearMonth.now());
        assertNull(service.getLastErrorMessage());
    }

    @Test void loadsEveryPageAndSortsEvents() throws Exception {
        List<String> requests = new ArrayList<>();
        var service = service(url -> {
            requests.add(url);
            if (!url.contains("/events?")) return json(200, PRIMARY);
            if (url.contains("pageToken=second")) return json(200,
                    "{\"items\":[{\"id\":\"early\",\"summary\":\"Early\",\"start\":{\"date\":\"2026-10-02\"}}]}");
            return json(200, "{\"nextPageToken\":\"second\",\"items\":[{\"id\":\"late\",\"summary\":\"Late\",\"start\":{\"date\":\"2026-10-03\"}}]}");
        });
        service.connect();
        var events = service.loadEventsForMonth(YearMonth.of(2026, 10));
        assertEquals(List.of("Early", "Late"), events.stream().map(CalendarEventItem::getTitle).toList());
        assertEquals(3, requests.size());
        assertTrue(requests.get(1).contains("singleEvents=true"));
        assertTrue(requests.get(2).contains("pageToken=second"));
    }

    @Test void expiredConsentIsReportedAndDoesNotConnect() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/google-calendar-credentials.json"), "{}");
        var auth = new GoogleCalendarOAuthService() {
            @Override public Credential authorize(Path credentials, Path tokens) throws Exception {
                var transport = new MockHttpTransport.Builder()
                        .setLowLevelHttpResponse(json(400, "{\"error\":\"invalid_grant\"}")).build();
                new TokenRequest(transport, GsonFactory.getDefaultInstance(),
                        new GenericUrl("https://example.com/token"), "refresh_token").execute();
                return null;
            }
        };
        var service = new GoogleCalendarService(auth, directory);
        assertThrows(TokenResponseException.class, service::connect);
        assertFalse(service.isConnected());
        assertTrue(service.getLastErrorMessage().contains("yeniden yetkilendirmen"));
    }

    @Test void readingAccountDoesNotWaitForAnEventNetworkRequest() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = service(url -> {
            if (url.contains("/events?")) {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Request timed out"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return json(200, "{\"items\":[]}");
            }
            return json(200, PRIMARY);
        });
        service.connect();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var loading = executor.submit(() -> service.loadEventsForMonth(YearMonth.now()));
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                var account = executor.submit(service::getAccountEmail);
                assertEquals(ACCOUNT, account.get(1, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            loading.get(2, TimeUnit.SECONDS);
        }
    }

    @Test @EnabledIf("org.example.FxTestSupport#isGuiAvailable")
    void calendarShowsAccountAndDistinguishesAnEmptyMonthFromAnApiError() throws Exception {
        FxTestSupport.initialize();
        var status = new AtomicInteger(200);
        var service = service(url -> url.contains("/events?")
                ? json(status.get(), status.get() == 200 ? "{\"items\":[]}"
                    : "{\"error\":{\"code\":403,\"message\":\"Calendar access denied\"}}")
                : json(200, PRIMARY));
        Stage stage = fx(() -> {
            var window = new Stage();
            window.setScene(new Scene(new CalendarModuleView(service).build(directory.resolve("tasks.json")), 1000, 700));
            window.show();
            return window;
        });
        try {
            assertTrue(fxText(stage, "#google-calendar-account").contains("bağlı değil"));
            fx(() -> {
                assertFalse(((Button) stage.getScene().lookup("#google-calendar-refresh")).isDisabled());
                ((Button) stage.getScene().lookup("#google-calendar-connect")).fire();
                return null;
            });
            waitFor(() -> fxText(stage, "#google-calendar-status").contains("Bu ay 0 kayıt"));
            assertTrue(fxText(stage, "#google-calendar-account").contains(ACCOUNT));
            status.set(403);
            fx(() -> { ((Button) stage.getScene().lookup("#google-calendar-refresh")).fire(); return null; });
            waitFor(() -> fxText(stage, "#google-calendar-status").contains("Takvim hatası"));
            assertTrue(fxText(stage, "#google-calendar-status").contains("Calendar access denied"));
            assertTrue(fxText(stage, "#google-calendar-account").contains(ACCOUNT));
            status.set(200);
            fx(() -> { ((Button) stage.getScene().lookup("#google-calendar-refresh")).fire(); return null; });
            waitFor(() -> fxText(stage, "#google-calendar-status").contains("Bu ay 0 kayıt"));
        } finally { fx(() -> { stage.close(); return null; }); }
    }

    private static String fxText(Stage stage, String selector) {
        try { return fx(() -> ((Label) stage.getScene().lookup(selector)).getText()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.getAsBoolean());
    }
}
