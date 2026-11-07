package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Calendar visibility and overrides belong to the verified account, never to another signed-in user. */
final class CalendarSourcesStore {
    record State(int schemaVersion,String account,List<GoogleCalendarService.Source> sources,Map<String,Boolean> overrides) {}
    private final Path root,file;
    private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    CalendarSourcesStore(Path root){this.root=root;file=root.resolve("data/calendar/sources.json");}
    State read()throws IOException {
        if(!Files.exists(file))return new State(1,"",List.of(),Map.of());
        State data=mapper.readValue(FinanceStorageService.readLimited(file),State.class);
        if(data==null||data.schemaVersion()!=1||data.account()==null||data.sources()==null||data.overrides()==null
                ||data.sources().size()>1000||data.overrides().size()>1000)throw new IOException("Takvim kaynak kaydı geçersiz.");
        Set<String> ids=new HashSet<>();for(var s:data.sources())if(s==null||s.id()==null||s.id().isBlank()||s.id().length()>2048||!ids.add(s.id())
                ||s.name()==null||s.name().length()>1000||s.color()==null||!s.color().matches("#[a-fA-F0-9]{6}")||s.role()==null)throw new IOException("Takvim kaynağı geçersiz.");
        for(var e:data.overrides().entrySet())if(e.getKey().isBlank()||e.getKey().length()>2048||e.getValue()==null)throw new IOException("Takvim seçimi geçersiz.");
        return data;
    }
    List<GoogleCalendarService.Source> sources(String account)throws IOException {
        var data=read();return account.equals(data.account())?data.sources().stream().map(s->s.selected(data.overrides().getOrDefault(s.id(),s.selected()))).toList():List.of();
    }
    boolean refresh(String account,List<GoogleCalendarService.Source> sources)throws IOException {
        var old=read();var overrides=account.equals(old.account())?old.overrides():Map.<String,Boolean>of();
        State next=new State(1,account,List.copyOf(sources),overrides);if(next.equals(old))return false;save(next);return true;
    }
    void select(String account,Set<String> selected)throws IOException {
        var old=read();if(!old.account().equals(account))throw new IOException("Takvim hesabı değişti. Yenile.");
        Set<String> known=new HashSet<>();old.sources().forEach(s->known.add(s.id()));if(!known.containsAll(selected))throw new IOException("Takvim listesi değişti. Yenile.");
        Map<String,Boolean> overrides=new LinkedHashMap<>();old.sources().forEach(s->overrides.put(s.id(),selected.contains(s.id())));save(new State(1,account,old.sources(),overrides));
    }
    List<CalendarEventItem> filter(String account,List<CalendarEventItem> events)throws IOException {
        var data=read();if(!data.account().equals(account))return events;Set<String> selected=new HashSet<>();sources(account).stream().filter(GoogleCalendarService.Source::selected).forEach(s->selected.add(s.id()));
        return events.stream().filter(e->selected.contains(e.getCalendarId())).toList();
    }
    boolean showLocal()throws IOException {Path path=root.resolve("data/calendar/view.properties");if(!Files.exists(path))return true;Properties p=new Properties();try(var r=Files.newBufferedReader(path)){p.load(r);}return Boolean.parseBoolean(p.getProperty("showLocal","true"));}
    void showLocal(boolean show)throws IOException {AtomicFileWriter.writeText(root.resolve("data/calendar/view.properties"),"showLocal="+show+"\n");}
    private void save(State data)throws IOException {byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);if(bytes.length>FinanceStorageService.MAX_BYTES)throw new IOException("Takvim kaynak kaydı çok büyük.");AtomicFileWriter.write(file,bytes);}
}
