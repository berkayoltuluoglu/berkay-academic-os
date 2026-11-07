package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.*;
import com.google.api.services.calendar.Calendar;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiCalendarServiceTest {
    @TempDir Path root;
    final ObjectMapper json=new ObjectMapper();
    final List<String> requests=new ArrayList<>();
    CalendarSyncService service;
    String account="student@example.com",mainVersion="m1",classVersion="c1";
    boolean secondary=true,classExpired,failClass;
    int writes;
    final YearMonth month=YearMonth.now();
    @BeforeEach void setup()throws Exception {
        Files.createDirectories(root.resolve("config"));Files.writeString(root.resolve("config/google-calendar-credentials.json"),"{}");
        var transport=new MockHttpTransport(){@Override public LowLevelHttpRequest buildRequest(String method,String address){return new MockLowLevelHttpRequest(address){@Override public MockLowLevelHttpResponse execute()throws IOException {
            requests.add(address);if(!method.equals("GET"))writes++;
            var url=new GenericUrl(address);String path=url.getRawPath();
            if(path.endsWith("/calendars/primary"))return reply(200,"{\"id\":\""+account+"\"}");
            if(path.endsWith("/users/me/calendarList")) {
                String main="{\"id\":\""+account+"\",\"summary\":\"My calendar\",\"primary\":true,\"selected\":true,\"accessRole\":\"owner\"}";
                if(url.get("pageToken")==null&&secondary)return reply(200,"{\"items\":["+main+"],\"nextPageToken\":\"more\"}");
                return reply(200,"{\"items\":["+(secondary?"{\"id\":\"class-calendar\",\"summary\":\"Classroom\",\"backgroundColor\":\"#0b8043\",\"selected\":true,\"accessRole\":\"reader\"}":main)+"]}");
            }
            boolean classroom=path.contains("/class-calendar/");String version=classroom?classVersion:mainVersion;
            if(classroom&&failClass)return reply(403,"{\"error\":{\"code\":403,\"message\":\"Classroom calendar denied\"}}");
            if(address.contains("singleEvents=false")) {
                if(classroom&&classExpired&&url.get("syncToken")!=null){classExpired=false;return reply(410,"{\"error\":{\"code\":410}}");}
                boolean changed=!version.equals(url.get("syncToken"));return reply(200,"{\"items\":"+(changed?"[{\"id\":\"change\"}]":"[]")+",\"nextSyncToken\":\""+version+"\"}");
            }
            if(path.endsWith("/events"))return reply(200,"{\"items\":["+event("same-id",classroom?"Assignment": "Meeting")+"]}");
            return reply(404,"{\"error\":{\"code\":404}}");
        }};}};
        Calendar client=new Calendar.Builder(transport,GsonFactory.getDefaultInstance(),r->{}).setApplicationName("Calendar tests").build();
        var auth=new GoogleCalendarOAuthService(){@Override public Credential authorize(Path c,Path t){return null;}};
        service=new CalendarSyncService(root,auth){@Override Calendar createCalendarService(Credential c){return client;}};service.connect();
    }
    String event(String id,String title){return "{\"id\":\""+id+"\",\"summary\":\""+title+"\",\"etag\":\"e1\",\"start\":{\"date\":\""+month.atDay(13)+"\"},\"end\":{\"date\":\""+month.atDay(14)+"\"}}";}
    static MockLowLevelHttpResponse reply(int code,String body){return new MockLowLevelHttpResponse().setStatusCode(code).setContentType("application/json").setContent(body);}
    @Test void paginatedCalendarListAndIdenticalEventIdsRemainSeparateAndCacheTheirSources()throws Exception {
        var events=service.loadEventsForMonth(month);assertEquals(2,events.size());assertNotEquals(events.get(0).key(),events.get(1).key());
        var classroom=events.stream().filter(e->!e.isPrimaryCalendar()).findFirst().orElseThrow();assertEquals("Classroom",classroom.getSourceName());assertEquals("#0b8043",classroom.getSourceColor());assertFalse(classroom.isEditable());
        assertEquals(2,new CalendarCacheStore(root).load(month).size());assertTrue(requests.stream().anyMatch(u->u.contains("pageToken=more")));
        assertThrows(IllegalArgumentException.class,()->service.deleteEvent(classroom));assertEquals(0,writes);
    }
    @Test void selectedCalendarsStayHiddenOfflineAndTheirSelectionDoesNotLeakAcrossAccounts()throws Exception {
        service.loadEventsForMonth(month);service.selectCalendars(Set.of("primary"));assertEquals(1,service.loadEventsForMonth(month).size());
        var offline=new CalendarSyncService(root);assertEquals(1,offline.loadEventsForMonth(month).size());assertTrue(offline.loadEventsForMonth(month).getFirst().isPrimaryCalendar());
        account="other@example.com";service.connect();assertTrue(service.calendarSources().stream().allMatch(GoogleCalendarService.Source::selected));
    }
    @Test void eachCalendarGetsAnIncrementalTokenAnd410ResetsOnlyTheExpiredCalendar()throws Exception {
        service.synchronize();requests.clear();classVersion="c2";assertTrue(service.synchronize());
        assertTrue(requests.stream().anyMatch(u->u.contains("class-calendar")&&u.contains("syncToken=c1")));assertTrue(requests.stream().anyMatch(u->u.contains("primary")&&u.contains("syncToken=m1")));
        requests.clear();classExpired=true;service.synchronize();assertFalse(classExpired);assertEquals(2,requests.stream().filter(u->u.contains("class-calendar")&&u.contains("singleEvents=false")).count());
        assertEquals(1,requests.stream().filter(u->u.contains("primary")&&u.contains("singleEvents=false")).count());
    }
    @Test void selectingOrRemovingACalendarRefreshesCachesEvenWhenEventsHaveNotChanged()throws Exception {
        service.synchronize();service.selectCalendars(Set.of("primary"));assertTrue(service.synchronize());assertEquals(1,new CalendarCacheStore(root).loadRaw(month).size());
        service.selectCalendars(Set.of("primary","class-calendar"));assertTrue(service.synchronize());assertEquals(2,new CalendarCacheStore(root).loadRaw(month).size());secondary=false;assertTrue(service.synchronize());assertEquals(1,new CalendarCacheStore(root).load(month).size());
    }
    @Test void oneFailedCalendarDoesNotEraseTheLastCompleteCopyOrAdvanceTokens()throws Exception {
        service.synchronize();byte[] baseline=Files.readAllBytes(root.resolve("data/calendar/multi-sync-state.json"));failClass=true;
        assertThrows(IOException.class,service::synchronize);assertArrayEquals(baseline,Files.readAllBytes(root.resolve("data/calendar/multi-sync-state.json")));assertEquals(2,service.loadEventsForMonth(month).size());assertNotNull(service.getCacheWarning());
    }
    @Test void pendingPrimaryWritesDoNotReplaceAnIdenticalIdOnAnotherCalendar()throws Exception {
        var other=CalendarEventItem.allDay("abcdef123456","Classroom",month.atDay(13),"","").withSource("class-calendar","Classroom","#0b8043");
        var queue=new CalendarWriteQueue(root);queue.enqueue(account,"CREATE","abcdef123456",new CalendarEventDraft("Local pending","","",month.atDay(13),month.atDay(13),null,null,true),null);
        var overlaid=queue.overlay(account,month,List.of(other));assertEquals(2,overlaid.size());assertTrue(overlaid.stream().anyMatch(e->e.getTitle().equals("Classroom")));
    }
    @Test void oldPrimaryOnlyCachesStillLoadAndNewConsentIsRequiredWithoutTouchingOldTokens()throws Exception {
        var cache=new CalendarCacheStore(root);cache.setAccount(account);Files.writeString(root.resolve("data/calendar/"+month+".json"),"{\"schemaVersion\":1,\"account\":\""+account+"\",\"month\":\""+month+"\",\"syncedAt\":\"old\",\"items\":[{\"id\":\"old\",\"title\":\"Legacy\",\"date\":\""+month.atDay(13)+"\",\"endDate\":\""+month.atDay(13)+"\",\"startTime\":\"\",\"endTime\":\"\",\"description\":\"\",\"location\":\"\",\"allDay\":true,\"etag\":\"e1\",\"editable\":true,\"recurring\":false}]}");assertTrue(cache.load(month).getFirst().isPrimaryCalendar());
        Path old=root.resolve("config/google-calendar-tokens/events-write-v1/StoredCredential");Files.createDirectories(old.getParent());Files.writeString(old,"Legacy token must be untouched");assertFalse(new CalendarSyncService(root).connectExisting());assertEquals("Legacy token must be untouched",Files.readString(old));
    }
}
