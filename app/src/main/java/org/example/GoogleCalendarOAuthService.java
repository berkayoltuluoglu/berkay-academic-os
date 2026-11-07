package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class GoogleCalendarOAuthService {

    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_EVENTS_OWNED,
            CalendarScopes.CALENDAR_CALENDARS_READONLY, CalendarScopes.CALENDAR_EVENTS_READONLY,
            CalendarScopes.CALENDAR_CALENDARLIST_READONLY);
    // A separate store forces consent for write access instead of reusing a read-only token.
    static final String WRITE_TOKEN_DIRECTORY = "all-calendars-v2";
    private static final String USER_ID = "user";
    private volatile LocalServerReceiver activeReceiver;

    public Credential authorize(Path credentialsPath, Path tokensDir) throws Exception {
        if (credentialsPath == null || !Files.exists(credentialsPath)) {
            throw new IllegalStateException("Google credentials dosyası bulunamadı: " + credentialsPath);
        }

        if (tokensDir == null) {
            throw new IllegalStateException("Google token klasörü null olamaz");
        }
        Path writeTokensDir = tokensDir.resolve(tokenDirectory());
        Files.createDirectories(writeTokensDir);

        GoogleAuthorizationCodeFlow flow = createFlow(credentialsPath, writeTokensDir);
        Credential existingCredential = flow.loadCredential(USER_ID);
        if (existingCredential != null) {
            try {
                Long expiresIn = existingCredential.getExpiresInSeconds();
                if (expiresIn == null || expiresIn > 60 || existingCredential.refreshToken()) {
                    return existingCredential;
                }
            } catch (TokenResponseException error) {
                if (error.getDetails() == null || !"invalid_grant".equals(error.getDetails().getError())) throw error;
                // Revoked/expired consent needs a new browser authorization, not another cached token.
            }
        }

        TokenResponse response = requestAuthorization(flow);
        Set<String> granted = response.getScope() == null ? Set.of()
                : Set.copyOf(List.of(response.getScope().trim().split("\\s+")));
        validateGrantedScopes(granted);
        return flow.createAndStoreCredential(response, USER_ID);
    }

    protected List<String> requestedScopes() { return SCOPES; }
    protected String tokenDirectory() { return WRITE_TOKEN_DIRECTORY; }
    /** Background reconnect never opens a browser or treats revoked consent as a connected account. */
    public Credential authorizeExisting(Path credentialsPath, Path tokensDir) throws Exception {
        Path store = tokensDir.resolve(tokenDirectory());
        if (!Files.isRegularFile(store.resolve("StoredCredential"))) return null;
        var flow = createFlow(credentialsPath, store); var credential = flow.loadCredential(USER_ID);
        if (credential == null) return null;
        try {
            Long expiry = credential.getExpiresInSeconds();
            return expiry == null || expiry > 60 || credential.refreshToken() ? credential : null;
        } catch (TokenResponseException error) {
            if (error.getDetails() != null && "invalid_grant".equals(error.getDetails().getError())) return null;
            throw error;
        }
    }
    public void forget(Path tokensDir) throws java.io.IOException {
        Path store = tokensDir.resolve(tokenDirectory());
        if (Files.exists(store)) new FileDataStoreFactory(store.toFile())
                .getDataStore(com.google.api.client.auth.oauth2.StoredCredential.DEFAULT_DATA_STORE_ID).delete(USER_ID);
    }
    protected void validateGrantedScopes(Set<String> granted) {
        boolean fullCalendar = granted.contains(CalendarScopes.CALENDAR);
        boolean events = fullCalendar || granted.contains(CalendarScopes.CALENDAR_EVENTS)
                || granted.contains(CalendarScopes.CALENDAR_EVENTS_OWNED);
        boolean metadata = fullCalendar || granted.contains(CalendarScopes.CALENDAR_CALENDARS_READONLY);
        boolean read = fullCalendar || granted.contains(CalendarScopes.CALENDAR_READONLY)
                || granted.contains(CalendarScopes.CALENDAR_EVENTS_READONLY) || granted.contains(CalendarScopes.CALENDAR_EVENTS);
        boolean list = fullCalendar || granted.contains(CalendarScopes.CALENDAR_READONLY)
                || granted.contains(CalendarScopes.CALENDAR_CALENDARLIST_READONLY) || granted.contains(CalendarScopes.CALENDAR_CALENDARLIST);
        metadata |= granted.contains(CalendarScopes.CALENDAR_READONLY);
        if (!events || !metadata || !read || !list) {
            throw new IllegalStateException("İki yönlü takvim için etkinlikleri düzenleme ve takvim bilgilerini okuma "
                    + "izinlerinin ikisini de onaylaman; diğer takvimleri görmek için takvim listesi ve tüm etkinlikleri okuma izinlerini de vermen gerekiyor. Tekrar Bağlan düğmesine bas.");
        }
    }

    GoogleAuthorizationCodeFlow createFlow(Path credentialsPath, Path writeTokensDir) throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets;
        try (var in = Files.newInputStream(credentialsPath)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        var builder = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                requestedScopes()
        )
                .setDataStoreFactory(new FileDataStoreFactory(writeTokensDir.toFile()))
                .setAccessType("offline")
                .setRequestInitializer(request -> {
                    request.setConnectTimeout(15000);
                    request.setReadTimeout(20000);
                });
        return builder.build();
    }

    GoogleAuthorizationCodeRequestUrl authorizationUrl(GoogleAuthorizationCodeFlow flow, String redirectUri,
                                                      String verifier) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var url = flow.newAuthorizationUrl().setRedirectUri(redirectUri);
        url.set("prompt", "consent select_account");
        url.set("code_challenge", challenge);
        url.set("code_challenge_method", "S256");
        return url;
    }

    TokenResponse requestAuthorization(GoogleAuthorizationCodeFlow flow) throws Exception {
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(-1).build();
        activeReceiver = receiver;
        try {
            String redirectUri = receiver.getRedirectUri();
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            AuthorizationCodeInstalledApp.browse(authorizationUrl(flow, redirectUri, verifier).build());
            String code = receiver.waitForCode();
            return flow.newTokenRequest(code).setRedirectUri(redirectUri).set("code_verifier", verifier).execute();
        } finally {
            activeReceiver = null;
            receiver.stop();
        }
    }
    public void cancelAuthorization() {
        LocalServerReceiver receiver = activeReceiver;
        if (receiver != null) try { receiver.stop(); } catch (java.io.IOException ignored) { /* The worker reports its terminal state. */ }
    }
}
