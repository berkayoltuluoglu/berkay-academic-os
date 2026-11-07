package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Production calendar adds durable offline writes and incremental polling to the verified API adapter. */
public class CalendarSyncService extends GoogleCalendarService {
    private record State(int schemaVersion,String account,Map<String,String> tokens,List<Source> sources) {}
    private final Path root;
    private final CalendarWriteQueue queue;
    private final CalendarCacheStore cache;
    private final CalendarSourcesStore sources;
    private final ObjectMapper mapper=new ObjectMapper();
    private volatile boolean cachedView;
    private volatile String synchronizationError;
    private volatile String readError;
    public CalendarSyncService(Path root){this(root,new GoogleCalendarOAuthService());}
    CalendarSyncService(Path root,GoogleCalendarOAuthService auth){super(auth,root);this.root=root;queue=new CalendarWriteQueue(root);cache=new CalendarCacheStore(root);sources=new CalendarSourcesStore(root);}
    @Override public boolean canWrite(){return isConnected() || !storedAccount().isBlank();}
    @Override public String getWriteAccount(){return isConnected()?getAccountEmail():storedAccount();}
    private String storedAccount(){try{return cache.account();}catch(IOException error){return "";}}
    @Override public String getLastErrorMessage(){return readError!=null?readError:cachedView?null:super.getLastErrorMessage();}
    @Override public String getCacheWarning(){return cachedView?"Son yerel takvim kopyası gösteriliyor. "+Objects.toString(synchronizationError,""):super.getCacheWarning();}
    public String getSynchronizationError(){return synchronizationError;}
    List<CalendarWriteQueue.Operation> operations()throws IOException{return queue.load();}
    public int pendingCount(){try{return queue.load().size();}catch(IOException error){return -1;}}
    @Override public synchronized List<CalendarEventItem> loadEventsForMonth(YearMonth month){
        cachedView=false;readError=null;
        try{
            if(!isConnected()){cachedView=true;return cache.load(month);}
            var events=super.loadEventsForCalendars(month,calendarSources());
            if(super.getLastErrorMessage()!=null){synchronizationError=super.getLastErrorMessage();cachedView=true;return cache.load(month);}
            return sources.filter(getAccountEmail(),queue.overlay(getAccountEmail(),month,events));
        }catch(IOException error){synchronizationError=error.getMessage();readError=error.getMessage();return List.of();}
    }
    @Override public synchronized CalendarEventItem createEvent(String id,CalendarEventDraft draft)throws IOException{
        if(id==null || !id.matches("[0-9a-v]{5,1024}"))throw new IllegalArgumentException("Geçersiz etkinlik kimliği.");
        var op=queue.enqueue(requireAccount(),"CREATE",id,draft,null);return sendOrQueue(op);
    }
    @Override public synchronized CalendarEventItem updateEvent(CalendarEventItem original,CalendarEventDraft draft)throws IOException{
        editable(original); var op=queue.enqueue(requireAccount(),"UPDATE",original.getId(),draft,original);return sendOrQueue(op);
    }
    @Override public synchronized void deleteEvent(CalendarEventItem original)throws IOException{
        editable(original);var op=queue.enqueue(requireAccount(),"DELETE",original.getId(),draft(original),original);
        if(op!=null)sendOrQueue(op);
    }
    private CalendarEventItem sendOrQueue(CalendarWriteQueue.Operation op)throws IOException{
        if(!isConnected())return op.local();
        try{return send(op);}
        catch(IOException failure){boolean permanent=permanent(failure);queue.replace(op.attempting().failure(failure.getMessage(),permanent));synchronizationError=failure.getMessage();
            if(permanent)throw failure;return op.local();}
    }
    private CalendarEventItem send(CalendarWriteQueue.Operation operation)throws IOException{
        var op=operation.attempting();queue.replace(op);var value=op.draft().value();CalendarEventItem saved=null;
        try{
            switch(op.action()){
                case "CREATE"->saved=super.createEvent(op.eventId(),value);
                case "UPDATE"->saved=super.updateEvent(op.local(),value);
                case "DELETE"->{
                    var original=op.local();
                    if(op.etag().isBlank() || op.etag().equals("pending")){
                        original=super.getEvent(op.eventId());
                        if(op.sentDraft()!=null && !same(original,op.sentDraft().value()))throw new IOException("Etkinlik Google'da değişmiş. Silmeyi çözüm ekranından onayla.");
                    }
                    super.deleteEvent(original);
                }
            }
        }catch(IOException failure){
            int status=status(failure);
            if(op.action().equals("DELETE") && (status==404 || status==410)){/* already removed */}
            else if((status==409 || status==412) && !op.action().equals("DELETE")){
                var actual=super.getEvent(op.eventId());
                if(same(actual,value))saved=actual;
                else if(op.sentDraft()!=null && same(actual,op.sentDraft().value()))saved=super.updateEvent(actual,value);
                else throw failure;
            }else throw failure;
        }
        if(saved!=null)remember(saved);
        else if(op.action().equals("DELETE"))forget(op.eventId());
        queue.remove(op.id());return saved==null?op.local():saved;
    }
    private void remember(CalendarEventItem event)throws IOException{
        var months=new TreeSet<>(cache.months());months.add(YearMonth.from(event.getDate()));months.add(YearMonth.from(event.getEndDate()));
        for(var month=YearMonth.from(event.getDate());!month.isAfter(YearMonth.from(event.getEndDate()));month=month.plusMonths(1)){
            if(months.size()>1200)throw new IOException("Etkinlik tarih aralığı yerel kopya sınırını aşıyor.");months.add(month);
        }
        for(var month:months){var entries=new ArrayList<>(cache.loadRaw(month));entries.removeIf(e->e.isPrimaryCalendar()&&e.getId().equals(event.getId()));
            if(!event.getDate().isAfter(month.atEndOfMonth())&&!event.getEndDate().isBefore(month.atDay(1)))entries.add(event);cache.save(getAccountEmail(),month,entries);}
    }
    private void forget(String id)throws IOException{for(var month:cache.months()){var entries=new ArrayList<>(cache.loadRaw(month));entries.removeIf(e->e.isPrimaryCalendar()&&e.getId().equals(id));cache.save(getAccountEmail(),month,entries);}}
    @Override public synchronized List<Source> calendarSources() throws IOException {
        String account=isConnected()?getAccountEmail():storedAccount();
        if(isConnected())sources.refresh(account,readCalendarSources());
        return sources.sources(account);
    }
    @Override public synchronized void selectCalendars(Set<String> selected) throws IOException {
        sources.select(isConnected()?getAccountEmail():storedAccount(),selected);
    }
    @Override public String getSourceHint() {
        String account=isConnected()?getAccountEmail():storedAccount();if(account==null||account.isBlank())return "Google Calendar bağlı değil";
        try{var list=sources.sources(account);return "Google Calendar: "+account+" · "+(list.isEmpty()?"Ana takvim kopyası":list.stream().filter(Source::selected).count()+" takvim seçili")+(isConnected()?"":" · Çevrimdışı");}
        catch(IOException error){return "Google Calendar: "+account+" · Kaynak seçimi okunamadı";}
    }
    /** Each selected calendar has its own incremental token. Writes remain bound to the primary calendar/account. */
    public synchronized boolean synchronize()throws Exception {
        synchronizationError=null;if(!isConnected()&&!connectExisting())return false;
        var available=calendarSources();
        boolean writes=false;
        for(var op:queue.load())if(op.account().equals(getAccountEmail())&&!op.blocked()) {
            try{send(op);writes=true;}catch(IOException failure){queue.replace(op.attempting().failure(failure.getMessage(),permanent(failure)));synchronizationError=failure.getMessage();if(!permanent(failure))break;}
        }
        Path path=root.resolve("data/calendar/multi-sync-state.json");Map<String,String> old=Map.of();List<Source> oldSources=List.of();
        if(Files.exists(path)) {
            var state=mapper.readValue(FinanceStorageService.readLimited(path),State.class);
            if(state.schemaVersion()!=2||state.account()==null||state.tokens()==null||state.tokens().size()>1000||state.tokens().values().stream().anyMatch(Objects::isNull))throw new IOException("Takvim senkronizasyon kaydı geçersiz.");
            if(state.account().equals(getAccountEmail())){old=state.tokens();oldSources=state.sources()==null?List.of():state.sources();}
        }
        Map<String,String> next=new LinkedHashMap<>();boolean changed=!oldSources.equals(available)||writes;
        for(var source:available)if(source.selected()) {
            String token=old.getOrDefault(source.id(),"");Changes changes;
            try{changes=readChanges(source.id(),token);}catch(GoogleJsonResponseException failure){if(failure.getStatusCode()!=410)throw failure;token="";changes=readChanges(source.id(),"");}
            changed|=changes.changed()||token.isBlank();next.put(source.id(),changes.token());
        }
        changed|=!old.keySet().equals(next.keySet());
        if(changed) {
            var months=new TreeSet<>(cache.months());months.add(YearMonth.now());months.add(YearMonth.now().plusMonths(1));
            for(var month:months){super.loadEventsForCalendars(month,available);if(super.getLastErrorMessage()!=null||super.getCacheWarning()!=null)throw new IOException(Objects.toString(super.getLastErrorMessage(),super.getCacheWarning()));}
        }
        AtomicFileWriter.write(path,mapper.writeValueAsBytes(new State(2,getAccountEmail(),next,available)));cachedView=false;return changed;
    }
    synchronized void discard(String id)throws IOException{queue.remove(id);}
    synchronized void retry(String id,boolean useLocalVersion)throws IOException{
        var op=queue.load().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new IOException("Kuyruk kaydı artık yok."));
        if(!op.account().equals(getAccountEmail()))throw new IOException("Bu işlemin Google hesabına bağlan.");
        if(useLocalVersion){
            try{var current=super.getEvent(op.eventId());
                op=new CalendarWriteQueue.Operation(op.id(),op.account(),op.action().equals("CREATE")?"UPDATE":op.action(),op.eventId(),op.draft(),current.getEtag(),current.isRecurringInstance(),"",false,op.attempted(),op.sentDraft());
            }catch(GoogleJsonResponseException failure){
                if(failure.getStatusCode()!=404&&failure.getStatusCode()!=410)throw failure;
                if(op.action().equals("DELETE")){forget(op.eventId());queue.remove(op.id());return;}
                if(!op.action().equals("CREATE"))throw failure;
                op=op.failure("",false);
            }
        }else op=op.failure("",false);
        queue.replace(op);sendOrQueue(op);
    }
    private String requireAccount(){String value=getWriteAccount();if(value==null || value.isBlank())throw new IllegalStateException("Önce Google hesabına bağlan; çevrimdışı yazım son doğrulanmış hesapla yapılır.");return value;}
    private static CalendarEventDraft draft(CalendarEventItem e){return new CalendarEventDraft(e.getTitle(),e.getDescription(),e.getLocation(),e.getDate(),e.getEndDate(),e.getStartTime(),e.getEndTime(),e.isAllDay());}
    private static void editable(CalendarEventItem e){if(e==null || !e.isPrimaryCalendar() || !e.isEditable() || e.getEtag()==null || e.getEtag().isBlank())throw new IllegalArgumentException("Takvimi yenileyip düzenlenebilir bir etkinlik seç.");}
    private static boolean same(CalendarEventItem e,CalendarEventDraft d){return Objects.equals(e.getTitle(),d.title())&&Objects.equals(Objects.toString(e.getDescription(),""),d.description())
            &&Objects.equals(Objects.toString(e.getLocation(),""),d.location())&&e.getDate().equals(d.startDate())&&e.getEndDate().equals(d.endDate())&&e.isAllDay()==d.allDay()
            &&(d.allDay() || Objects.equals(e.getStartTime(),d.startTime())&&Objects.equals(e.getEndTime(),d.endTime()));}
    private static int status(Throwable error){while(error!=null){if(error instanceof com.google.api.client.http.HttpResponseException api)return api.getStatusCode();error=error.getCause();}return 0;}
    private static boolean permanent(IOException error){int status=status(error);return status>=400&&status<500&&status!=408&&status!=429 || status==0 && !(error.getCause() instanceof java.net.SocketException)
            && error.getMessage()!=null && (error.getMessage().contains("değişmiş")||error.getMessage().contains("farklı"));}
}
