package org.example;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarOAuthServiceTest {
    @TempDir Path directory;

    private Path credentials() throws Exception {
        Path path = directory.resolve("credentials.json");
        Files.writeString(path, """
                {"installed":{"client_id":"test-client","client_secret":"test-secret",
                "auth_uri":"https://accounts.google.com/o/oauth2/auth",
                "token_uri":"https://oauth2.googleapis.com/token",
                "redirect_uris":["http://localhost"]}}
                """);
        return path;
    }

    private static TokenResponse response(String scope) {
        return new TokenResponse().setAccessToken("test-access").setTokenType("Bearer")
                .setExpiresInSeconds(3600L).setScope(scope);
    }

    @Test void oldReadOnlyTokensArePreservedAndCannotSkipWriteConsent() throws Exception {
        Path path = credentials();
        Path tokens = directory.resolve("tokens");
        Files.createDirectories(tokens);
        Path oldToken = tokens.resolve("StoredCredential");
        byte[] legacy = "old read-only store must remain untouched".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(oldToken, legacy);
        AtomicInteger browser = new AtomicInteger();
        var auth = new GoogleCalendarOAuthService() {
            @Override TokenResponse requestAuthorization(GoogleAuthorizationCodeFlow flow) {
                browser.incrementAndGet();
                assertEquals(java.util.Set.copyOf(GoogleCalendarOAuthService.SCOPES), java.util.Set.copyOf(flow.getScopes()));
                return response(String.join(" ", GoogleCalendarOAuthService.SCOPES));
            }
        };
        auth.authorize(path, tokens);
        assertEquals(1, browser.get());
        assertArrayEquals(legacy, Files.readAllBytes(oldToken));
        assertTrue(Files.exists(tokens.resolve(GoogleCalendarOAuthService.WRITE_TOKEN_DIRECTORY).resolve("StoredCredential")));
        auth.authorize(path, tokens);
        assertEquals(1, browser.get(), "A valid write token should reconnect without another browser prompt");
    }

    @Test void partialConsentIsRejectedAndNotStoredAsAWriteConnection() throws Exception {
        Path path = credentials();
        Path tokens = directory.resolve("tokens");
        var auth = new GoogleCalendarOAuthService() {
            @Override TokenResponse requestAuthorization(GoogleAuthorizationCodeFlow flow) {
                return response(CalendarScopes.CALENDAR_CALENDARS_READONLY);
            }
        };
        var error = assertThrows(IllegalStateException.class, () -> auth.authorize(path, tokens));
        assertTrue(error.getMessage().contains("ikisini de onaylaman"));
        var flow = auth.createFlow(path, tokens.resolve(GoogleCalendarOAuthService.WRITE_TOKEN_DIRECTORY));
        assertNull(flow.loadCredential("user"));
    }

    @Test void expiredRevokedWriteConsentStartsFreshAuthorization() throws Exception {
        Path path = credentials();
        Path tokens = directory.resolve("tokens");
        AtomicInteger browser = new AtomicInteger();
        var auth = new GoogleCalendarOAuthService() {
            @Override GoogleAuthorizationCodeFlow createFlow(Path credentials, Path writeDirectory) throws Exception {
                var transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(
                        new MockLowLevelHttpResponse().setStatusCode(400).setContentType("application/json")
                                .setContent("{\"error\":\"invalid_grant\"}")).build();
                return new GoogleAuthorizationCodeFlow.Builder(transport, GsonFactory.getDefaultInstance(),
                        "test-client", "test-secret", SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(writeDirectory.toFile())).build();
            }
            @Override TokenResponse requestAuthorization(GoogleAuthorizationCodeFlow flow) {
                browser.incrementAndGet();
                return response(String.join(" ", SCOPES)).setRefreshToken("new-refresh");
            }
        };
        var flow = auth.createFlow(path, tokens.resolve(GoogleCalendarOAuthService.WRITE_TOKEN_DIRECTORY));
        flow.createAndStoreCredential(response(String.join(" ", GoogleCalendarOAuthService.SCOPES))
                .setExpiresInSeconds(-1L).setRefreshToken("revoked-refresh"), "user");
        assertEquals("new-refresh", auth.authorize(path, tokens).getRefreshToken());
        assertEquals(1, browser.get());
    }

    @Test void authorizationUsesOfflineAccessMinimalWriteScopesAndPkce() throws Exception {
        var auth = new GoogleCalendarOAuthService();
        var flow = auth.createFlow(credentials(), directory.resolve("tokens"));
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        var url = auth.authorizationUrl(flow, "http://localhost:12345/Callback", verifier);
        assertEquals("S256", url.get("code_challenge_method"));
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.get("code_challenge"));
        assertEquals("consent select_account", url.get("prompt"));
        assertEquals("offline", url.getAccessType());
        assertFalse(flow.getScopes().contains(CalendarScopes.CALENDAR));
        assertFalse(flow.getScopes().contains(CalendarScopes.CALENDAR_READONLY));
        assertTrue(flow.getScopes().contains(CalendarScopes.CALENDAR_EVENTS_OWNED));
        assertTrue(flow.getScopes().contains(CalendarScopes.CALENDAR_CALENDARS_READONLY));
        assertTrue(flow.getScopes().contains(CalendarScopes.CALENDAR_EVENTS_READONLY));
        assertTrue(flow.getScopes().contains(CalendarScopes.CALENDAR_CALENDARLIST_READONLY));
    }
}
