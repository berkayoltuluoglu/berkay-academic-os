package org.example;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.*;
import com.google.api.services.calendar.Calendar;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarSyncServiceTest {
    @TempDir Path root;
    private final ObjectMapper json=new ObjectMapper();
    private final Map<String,ObjectNode> remote=new LinkedHashMap<>();
    private final List<String> urls=new ArrayList<>();
    private CalendarSyncService service;
    private boolean offline,lostResponse,expired;
    private int revision,writeCount;
    private String account="student@example.com";
    private static final String ID="abcdef123456";
    @BeforeEach void setup()throws Exception{
        Files.createDirectories(root.resolve("config"));Files.writeString(root.resolve("config/google-calendar-credentials.json"),"{}");
        var transport=new MockHttpTransport(){@Override public LowLevelHttpRequest buildRequest(String method,String address){return new MockLowLevelHttpRequest(address){
            @Override public MockLowLevelHttpResponse execute()throws IOException{
                urls.add(address);if(offline)throw new SocketException("Offline");
                if(address.endsWith("/calendars/primary"))return reply(200,"{\"id\":\""+account+"\"}");
                GenericUrl url=new GenericUrl(address);String path=url.getRawPath();
                if(path.endsWith("/users/me/calendarList"))return reply(200,"{\"items\":[{\"id\":\""+account+"\",\"summary\":\"Main\",\"primary\":true,\"selected\":true,\"accessRole\":\"owner\"}]}");
                if(method.equals("GET")&&path.endsWith("/events")){
                    if(address.contains("singleEvents=false")){
                        if(expired&&url.get("syncToken")!=null){expired=false;return reply(410,"{\"error\":{\"code\":410,\"message\":\"Sync token expired\"}}");}
                        ArrayNode items=json.createArrayNode();if(!("v"+revision).equals(url.get("syncToken")))items.addObject().put("id","changed");
                        ObjectNode feed=json.createObjectNode();feed.set("items",items);feed.put("nextSyncToken","v"+revision);return reply(200,feed.toString());
                    }
                    return reply(200,json.createObjectNode().set("items",json.valueToTree(remote.values())).toString());
                }
                String id=path.substring(path.lastIndexOf('/')+1);
                if(method.equals("GET"))return remote.containsKey(id)?reply(200,remote.get(id).toString()):reply(404,"{\"error\":{\"code\":404}}");
                JsonNode body=getContentAsString().isBlank()?null:json.readTree(getContentAsString());
                if(method.equals("POST")){
                    id=body.path("id").asText();if(remote.containsKey(id))return reply(409,"{\"error\":{\"code\":409}}");
                    assertTrue(Files.exists(root.resolve("data/calendar/outbox.json")),"The write must be durable before the HTTP request");
                    ObjectNode saved=(ObjectNode)body.deepCopy();saved.put("etag","e"+(++revision));remote.put(id,saved);writeCount++;
                    if(lostResponse){lostResponse=false;throw new SocketException("Response lost after insert");}return reply(200,saved.toString());
                }
                if(!remote.containsKey(id))return reply(404,"{\"error\":{\"code\":404}}");
                if(!remote.get(id).path("etag").asText().equals(getFirstHeaderValue("If-Match")))return reply(412,"{\"error\":{\"code\":412,\"message\":\"Changed elsewhere\"}}");
                if(method.equals("PATCH")){remote.get(id).setAll((ObjectNode)body);remote.get(id).put("etag","e"+(++revision));writeCount++;return reply(200,remote.get(id).toString());}
                remote.remove(id);revision++;writeCount++;return reply(204,"");
            }
        };}};
        Calendar client=new Calendar.Builder(transport,GsonFactory.getDefaultInstance(),r->{}).setApplicationName("Sync tests").build();
        var auth=new GoogleCalendarOAuthService(){@Override public Credential authorize(Path c,Path t){return null;}};
        service=new CalendarSyncService(root,auth){@Override Calendar createCalendarService(Credential c){return client;}};
        service.connect();
    }
    private static MockLowLevelHttpResponse reply(int code,String body){return new MockLowLevelHttpResponse().setStatusCode(code).setContentType("application/json").setContent(body);}
    private static CalendarEventDraft draft(String title){return new CalendarEventDraft(title,"","",LocalDate.now(),LocalDate.now(),null,null,true);}
    @Test void offlineWritesSurviveRestartAndAppearInTheSharedAgenda()throws Exception{
        offline=true;service.createEvent(ID,draft("Offline plan"));assertEquals(1,service.operations().size());
        assertTrue(new CalendarWriteQueue(root).load().getFirst().attempted());
        assertTrue(new DailyAgendaService().load(root,LocalDate.now()).todos().stream().anyMatch(t->t.title().equals("Offline plan")));
        offline=false;service.synchronize();assertEquals(1,remote.size());assertTrue(service.operations().isEmpty());assertEquals(1,writeCount);
    }
    @Test void anUncertainInsertRetriesTheSameIdAndNeverDuplicatesTheEvent()throws Exception{
        lostResponse=true;service.createEvent(ID,draft("Plan"));assertEquals(1,remote.size());assertEquals(1,service.operations().size());
        service.synchronize();assertEquals(1,remote.size());assertEquals(1,writeCount);assertTrue(service.operations().isEmpty());
    }
    @Test void remoteEditsBlockOfflineUpdatesUntilAnExplicitDecision()throws Exception{
        var created=service.createEvent(ID,draft("Before"));offline=true;service.updateEvent(created,draft("Local edit"));
        offline=false;remote.get(ID).put("summary","Remote edit").put("etag","e"+(++revision));service.synchronize();
        assertEquals("Remote edit",remote.get(ID).path("summary").asText());assertTrue(service.operations().getFirst().blocked());
        service.retry(service.operations().getFirst().id(),true);assertEquals("Local edit",remote.get(ID).path("summary").asText());assertTrue(service.operations().isEmpty());
    }
    @Test void switchingGoogleAccountsDoesNotSendThePreviousAccountsQueue()throws Exception{
        offline=true;service.createEvent(ID,draft("Private plan"));offline=false;account="other@example.com";service.connect();service.synchronize();
        assertTrue(remote.isEmpty());assertEquals(1,service.operations().size());
    }
    @Test void cancellingANeverSentOfflineCreateDoesNotIssueADelete()throws Exception{
        var restarted=new CalendarSyncService(root);var item=restarted.createEvent(ID,draft("Cancelled"));restarted.deleteEvent(item);
        assertTrue(restarted.operations().isEmpty());assertEquals(0,writeCount);
    }
    @Test void globalIncrementalQueriesUseSyncTokensAndRecoverFrom410WithoutDroppingPendingWrites()throws Exception{
        service.synchronize();assertTrue(urls.stream().anyMatch(u->u.contains("singleEvents=false")));
        service.synchronize();assertTrue(urls.stream().anyMatch(u->u.contains("syncToken=v0")));
        expired=true;service.synchronize();assertFalse(expired);
        for(String address:urls)if(address.contains("syncToken=")){assertFalse(address.contains("timeMin="));assertFalse(address.contains("orderBy="));}
    }
    @Test void firstRunDoesNotOpenAnAuthorizationBrowserInTheBackground()throws Exception{
        var untouched=new CalendarSyncService(root.resolve("fresh-profile"));assertFalse(untouched.synchronize());assertFalse(untouched.isConnected());
    }
}
