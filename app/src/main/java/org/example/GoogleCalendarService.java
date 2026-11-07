package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;

public class GoogleCalendarService {

    private final GoogleCalendarOAuthService authService;
    private final Path projectRoot;

    private record Connection(Calendar client, String accountEmail) {}
    private volatile Connection connection;
    private volatile String lastErrorMessage;
    private volatile String cacheWarning;

    public GoogleCalendarService() {
        this(new GoogleCalendarOAuthService(), resolveProjectRoot());
    }

    GoogleCalendarService(GoogleCalendarOAuthService authService, Path projectRoot) {
        this.authService = authService;
        this.projectRoot = projectRoot;
    }

    public synchronized void connect() throws Exception {
        lastErrorMessage = null;
        connection = null;
        try {
            Path credentialsPath = GoogleCredentials.resolve(projectRoot, "google-calendar-credentials.json");
            Path tokensDir = projectRoot.resolve("config/google-calendar-tokens");
            Files.createDirectories(tokensDir);
            Credential credential = authService.authorize(credentialsPath, tokensDir);
            Calendar client = createCalendarService(credential);
            var primaryCalendar = client.calendars().get("primary").execute();
            if (primaryCalendar.getId() == null || primaryCalendar.getId().isBlank()) {
                throw new IllegalStateException("Google bağlı hesabın ana takvimini doğrulayamadı.");
            }
            if (Thread.currentThread().isInterrupted()) throw new IOException("Google bağlantısı iptal edildi.");
            connection = new Connection(client, primaryCalendar.getId());
            try { new CalendarCacheStore(projectRoot).setAccount(primaryCalendar.getId()); }
            catch (IOException error) { cacheWarning = "Bağlı takvim hesabı yerel olarak kaydedilemedi: " + error.getMessage(); }
        } catch (Exception e) {
            lastErrorMessage = describeError(e);
            throw e;
        }
    }

    public synchronized boolean connectExisting() throws Exception {
        if (connection != null) return true;
        if (!Files.isRegularFile(projectRoot.resolve("config/google-calendar-tokens/" + authService.tokenDirectory() + "/StoredCredential"))) return false;
        Path credentials = GoogleCredentials.resolve(projectRoot,"google-calendar-credentials.json");
        Credential saved = authService.authorizeExisting(credentials,projectRoot.resolve("config/google-calendar-tokens"));
        if (saved == null) return false;
        Calendar client = createCalendarService(saved); var primary = client.calendars().get("primary").execute();
        if (primary.getId() == null || primary.getId().isBlank()) throw new IOException("Google hesabı doğrulanamadı.");
        connection = new Connection(client,primary.getId()); new CalendarCacheStore(projectRoot).setAccount(primary.getId());
        return true;
    }
    public boolean canWrite() { return isConnected(); }
    public String getWriteAccount() { return getAccountEmail(); }
    public record Changes(String token, boolean changed) {}
    public record Source(String id,String name,String color,boolean primary,boolean selected,String role) {
        public Source selected(boolean value) { return new Source(id,name,color,primary,value,role); }
        @Override public String toString() { return name; }
    }
    public synchronized List<Source> calendarSources() throws IOException {
        if (!isConnected()) return List.of();
        return List.of(new Source("primary","Ana takvim","#4285f4",true,true,"owner"));
    }
    public synchronized void selectCalendars(java.util.Set<String> ids) throws IOException {
        throw new IOException("Bu bağlantı yalnızca ana takvimle çalışıyor.");
    }
    protected synchronized List<Source> readCalendarSources() throws IOException {
        var query=requireConnection().client().calendarList().list().setShowHidden(true).setMinAccessRole("reader").setMaxResults(250)
                .setFields("items(id,summary,summaryOverride,backgroundColor,primary,selected,accessRole,deleted),nextPageToken");
        List<Source> result=new ArrayList<>();java.util.Set<String> pages=new java.util.HashSet<>(),ids=new java.util.HashSet<>();
        while(true) {
            var page=query.execute();
            if(page.getItems()!=null)for(var item:page.getItems()) {
                if(Boolean.TRUE.equals(item.getDeleted()))continue;
                if(item.getId()==null||item.getId().isBlank())throw new IOException("Google takvim kimliği eksik.");
                boolean primary=Boolean.TRUE.equals(item.getPrimary())||item.getId().equals(getAccountEmail());
                String id=primary?"primary":item.getId();if(!ids.add(id))continue;
                String name=item.getSummaryOverride()!=null?item.getSummaryOverride():item.getSummary();
                String color=item.getBackgroundColor();if(color==null||!color.matches("#[a-fA-F0-9]{6}"))color="#4285f4";
                result.add(new Source(id,name==null||name.isBlank()?(primary?"Ana takvim":id):name,color,primary,primary||Boolean.TRUE.equals(item.getSelected()),
                        item.getAccessRole()==null?"reader":item.getAccessRole()));
                if(result.size()>1000)throw new IOException("En fazla 1000 Google takvimi okunabilir.");
            }
            String next=page.getNextPageToken();if(next==null||next.isBlank())break;
            if(!pages.add(next)||pages.size()>1000)throw new IOException("Takvim listesi sayfalaması tekrarlıyor.");query.setPageToken(next);
        }
        result.sort(Comparator.comparing((Source item)->!item.primary()).thenComparing(Source::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(result);
    }
    /** Global change feed is a trigger: the existing bounded month query still expands recurring instances. */
    public synchronized Changes readChanges(String token) throws IOException {
        return readChanges("primary",token);
    }
    public synchronized Changes readChanges(String calendarId,String token) throws IOException {
        Connection current = requireConnection();
        var query=current.client().events().list(calendarId).setSingleEvents(false).setShowDeleted(true)
                .setMaxResults(2500).setFields("items(id),nextPageToken,nextSyncToken");
        if (token != null && !token.isBlank()) query.setSyncToken(token);
        boolean changed=false; String next=null; java.util.Set<String> seen=new java.util.HashSet<>();
        do {
            var page=query.execute(); changed |= page.getItems()!=null && !page.getItems().isEmpty();
            next=page.getNextPageToken();
            if (next==null || next.isBlank()) {
                if (page.getNextSyncToken()==null || page.getNextSyncToken().isBlank()) throw new IOException("Google senkronizasyon işareti eksik.");
                return new Changes(page.getNextSyncToken(),changed);
            }
            if (!seen.add(next) || seen.size()>1000) throw new IOException("Google senkronizasyon sayfalaması geçersiz.");
            query.setPageToken(next);
        } while (true);
    }
    public synchronized CalendarEventItem getEvent(String id) throws IOException {
        return requireEvent(requireConnection().client().events().get("primary",id).execute());
    }

    Calendar createCalendarService(Credential credential) throws Exception {
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setHttpRequestInitializer(request -> {
                    credential.initialize(request);
                    request.setConnectTimeout(15000);
                    request.setReadTimeout(20000);
                })
                .setApplicationName("Berkay Academic OS")
                .build();
    }

    public boolean isConnected() {
        return connection != null;
    }

    public String getAccountEmail() {
        Connection current = connection;
        return current == null ? null : current.accountEmail();
    }

    public synchronized List<CalendarEventItem> loadEventsForMonth(YearMonth month) {
        return loadEventsForCalendars(month,List.of(new Source("primary","Ana takvim","#4285f4",true,true,"owner")));
    }
    protected synchronized List<CalendarEventItem> loadEventsForCalendars(YearMonth month,List<Source> sources) {
        lastErrorMessage=null;Connection current=connection;if(current==null)return List.of();
        try {
            Instant first=month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant last=month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            List<CalendarEventItem> items=new ArrayList<>();java.util.Set<String> keys=new java.util.HashSet<>();
            for(Source source:sources)if(source.selected()) {
                var query=current.client().events().list(source.id()).setTimeMin(new com.google.api.client.util.DateTime(first.toEpochMilli()))
                        .setTimeMax(new com.google.api.client.util.DateTime(last.toEpochMilli())).setOrderBy("startTime").setSingleEvents(true).setMaxResults(250);
                java.util.Set<String> pages=new java.util.HashSet<>();
                while(true) {
                    Events page=query.execute();if(page.getItems()!=null)for(Event event:page.getItems()) {
                        CalendarEventItem item=convert(event);if(item!=null) {
                            item.withSource(source.id(),source.name(),source.color());
                            if(keys.add(item.key()))items.add(item);if(items.size()>20000)throw new IOException("Bu ay çok fazla Google kaydı var.");
                        }
                    }
                    String next=page.getNextPageToken();if(next==null||next.isBlank())break;
                    if(!pages.add(next)||pages.size()>1000)throw new IOException(source.name()+": etkinlik sayfalaması tekrarlıyor.");query.setPageToken(next);
                }
            }
            items.sort(Comparator.comparing(CalendarEventItem::getDate).thenComparing(e->e.getStartTime()==null?LocalTime.MIN:e.getStartTime()).thenComparing(CalendarEventItem::getTitle,String.CASE_INSENSITIVE_ORDER));
            try{new CalendarCacheStore(projectRoot).save(current.accountEmail(),month,items);cacheWarning=null;}
            catch(IOException error){cacheWarning="Google kayıtları okundu, yerel kopya kaydedilemedi: "+error.getMessage();}
            return items;
        }catch(Exception error){lastErrorMessage=describeError(error);return List.of();}
    }

    public String getSourceHint() {
        Connection current = connection;
        return current == null ? "Google Calendar bağlı değil"
                : "Google Calendar: " + current.accountEmail() + " · Ana takvim";
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    public String getCacheWarning() { return cacheWarning; }
    public void cancelAuthorization() { authService.cancelAuthorization(); }
    public synchronized void disconnect() throws IOException {
        connection = null; lastErrorMessage = null; new CalendarCacheStore(projectRoot).clearAccount();
    }

    /** Uses a stable client-generated ID, so retrying an uncertain insert cannot create a duplicate. */
    public synchronized CalendarEventItem createEvent(String eventId, CalendarEventDraft draft) throws IOException {
        lastErrorMessage = null;
        Connection current = requireConnection();
        if (eventId == null || !eventId.matches("[0-9a-v]{5,1024}")) {
            throw new IllegalArgumentException("Geçersiz etkinlik kimliği.");
        }
        try {
            Event body = draft.toGoogleEvent(ZoneId.systemDefault()).setId(eventId);
            Event created;
            try {
                created = current.client().events().insert("primary", body)
                        .setConferenceDataVersion(1).setSendUpdates("none").execute();
            } catch (GoogleJsonResponseException error) {
                if (error.getStatusCode() != 409) throw error;
                // A previous attempt may have succeeded while its response was lost.
                created = current.client().events().get("primary", eventId).execute();
                if (!sameDraft(created, body)) {
                    throw new IOException("Bu etkinlik önceki denemede kaydedilmiş ve bilgileri farklı. "
                            + "Pencereyi kapatıp takvimi yenile; mevcut etkinliği düzenleyebilirsin.", error);
                }
            }
            return requireEvent(created);
        } catch (IOException error) {
            lastErrorMessage = describeError(error);
            throw new IOException(lastErrorMessage, error);
        }
    }

    public synchronized CalendarEventItem updateEvent(CalendarEventItem original, CalendarEventDraft draft) throws IOException {
        lastErrorMessage = null;
        Connection current = requireConnection();
        requireEditable(original);
        try {
            // Patch only editable fields; attendees, reminders, recurrence and Meet data remain on Google.
            Event body = draft.toGoogleEvent(ZoneId.systemDefault());
            // Explicit JSON nulls clear the old date/dateTime when switching between timed and all-day.
            if (draft.allDay()) {
                body.getStart().setDateTime(com.google.api.client.util.Data.NULL_DATE_TIME);
                body.getEnd().setDateTime(com.google.api.client.util.Data.NULL_DATE_TIME);
                body.getStart().setTimeZone(com.google.api.client.util.Data.NULL_STRING);
                body.getEnd().setTimeZone(com.google.api.client.util.Data.NULL_STRING);
            } else {
                body.getStart().setDate(com.google.api.client.util.Data.NULL_DATE_TIME);
                body.getEnd().setDate(com.google.api.client.util.Data.NULL_DATE_TIME);
            }
            var request = current.client().events().patch("primary", original.getId(), body)
                    .setConferenceDataVersion(1).setSendUpdates("none");
            request.getRequestHeaders().setIfMatch(original.getEtag());
            return requireEvent(request.execute());
        } catch (IOException error) {
            lastErrorMessage = describeError(error);
            throw new IOException(lastErrorMessage, error);
        }
    }

    public synchronized void deleteEvent(CalendarEventItem original) throws IOException {
        lastErrorMessage = null;
        Connection current = requireConnection();
        requireEditable(original);
        try {
            var request = current.client().events().delete("primary", original.getId()).setSendUpdates("none");
            request.getRequestHeaders().setIfMatch(original.getEtag());
            request.execute();
        } catch (GoogleJsonResponseException error) {
            if (error.getStatusCode() == 404 || error.getStatusCode() == 410) return; // Already deleted.
            lastErrorMessage = describeError(error);
            throw new IOException(lastErrorMessage, error);
        } catch (IOException error) {
            lastErrorMessage = describeError(error);
            throw new IOException(lastErrorMessage, error);
        }
    }

    private Connection requireConnection() {
        Connection current = connection;
        if (current == null) throw new IllegalStateException("Önce Google Calendar'a bağlan.");
        return current;
    }

    private void requireEditable(CalendarEventItem item) {
        if (item == null || !item.isPrimaryCalendar() || !item.isEditable() || item.getId() == null || item.getEtag() == null || item.getEtag().isBlank()) {
            throw new IllegalArgumentException("Bu etkinlik düzenlenemiyor. Takvimi yenileyip tekrar seç.");
        }
    }

    private CalendarEventItem requireEvent(Event event) throws IOException {
        CalendarEventItem item = convert(event);
        if (item == null) throw new IOException("Google etkinlik yanıtı eksik. Takvimi yenileyip kontrol et.");
        return item;
    }

    private boolean sameDraft(Event actual, Event requested) {
        return actual != null && java.util.Objects.equals(actual.getSummary(), requested.getSummary())
                && java.util.Objects.equals(empty(actual.getDescription()), empty(requested.getDescription()))
                && java.util.Objects.equals(empty(actual.getLocation()), empty(requested.getLocation()))
                && sameTime(actual.getStart(), requested.getStart()) && sameTime(actual.getEnd(), requested.getEnd());
    }

    private String empty(String value) { return value == null ? "" : value; }

    private boolean sameTime(com.google.api.services.calendar.model.EventDateTime a,
                             com.google.api.services.calendar.model.EventDateTime b) {
        if (a == null || b == null) return a == b;
        if (a.getDate() != null || b.getDate() != null) return java.util.Objects.equals(a.getDate(), b.getDate());
        return a.getDateTime() != null && b.getDateTime() != null
                && a.getDateTime().getValue() == b.getDateTime().getValue();
    }

    private String describeError(Exception error) {
        if (error instanceof GoogleJsonResponseException apiError) {
            if (apiError.getStatusCode() == 412) {
                return "Etkinlik Google'da değişmiş. Pencereyi kapat, takvimi yenile ve güncel etkinliği tekrar seç.";
            }
            if (apiError.getStatusCode() == 403) {
                return "Google işlemi için izin yok. Bağlantıyı doğrula ve düzenleme izinlerini onayla. "
                        + (apiError.getDetails() == null ? "" : apiError.getDetails().getMessage());
            }
        }
        if (error instanceof TokenResponseException tokenError && tokenError.getDetails() != null
                && "invalid_grant".equals(tokenError.getDetails().getError())) {
            return "Google bağlantı izni sona ermiş veya kaldırılmış. Hesabı yeniden yetkilendirmen gerekiyor.";
        }
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private CalendarEventItem convert(Event event) {
        if (event == null || event.getStart() == null || "cancelled".equals(event.getStatus())) {
            return null;
        }

        String id = event.getId();
        String title = event.getSummary() == null || event.getSummary().isBlank()
                ? "(Başlıksız Etkinlik)" : event.getSummary();

        String description = event.getDescription();
        String location = event.getLocation();
        boolean editable = (event.getEventType() == null || "default".equals(event.getEventType()))
                && !Boolean.TRUE.equals(event.getLocked());
        boolean recurring = event.getRecurringEventId() != null;

        if (hasValue(event.getStart().getDate())) {
            LocalDate date = LocalDate.parse(event.getStart().getDate().toStringRfc3339().substring(0, 10));
            LocalDate lastDay = event.getEnd() != null && hasValue(event.getEnd().getDate())
                    ? LocalDate.parse(event.getEnd().getDate().toStringRfc3339().substring(0, 10)).minusDays(1) : date;
            return CalendarEventItem.allDay(id, title, date, description, location)
                    .withGoogleMetadata(lastDay, event.getEtag(), editable, recurring);
        }

        if (hasValue(event.getStart().getDateTime())) {
            Instant startInstant = Instant.ofEpochMilli(event.getStart().getDateTime().getValue());
            Instant endInstant = event.getEnd() != null && hasValue(event.getEnd().getDateTime())
                    ? Instant.ofEpochMilli(event.getEnd().getDateTime().getValue())
                    : startInstant.plusSeconds(3600);

            LocalDateTime start = LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault());
            LocalDateTime end = LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault());

            return CalendarEventItem.timed(id, title, start, end, description, location)
                    .withGoogleMetadata(end.toLocalDate(), event.getEtag(), editable, recurring);
        }

        return null;
    }

    private boolean hasValue(Object value) {
        return value != null && !com.google.api.client.util.Data.isNull(value);
    }

    private static Path resolveProjectRoot() {
        return AppPaths.root();
    }
}
