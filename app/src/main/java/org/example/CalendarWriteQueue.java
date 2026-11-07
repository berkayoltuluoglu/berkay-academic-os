package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Durable account-bound writes; merging preserves the first ETag instead of hiding remote conflicts. */
final class CalendarWriteQueue {
    record Draft(String title,String description,String location,String date,String endDate,String start,String end,boolean allDay) {
        static Draft of(CalendarEventDraft value){return new Draft(value.title(),value.description(),value.location(),value.startDate().toString(),value.endDate().toString(),
                value.startTime()==null?"":value.startTime().toString(),value.endTime()==null?"":value.endTime().toString(),value.allDay());}
        CalendarEventDraft value(){return new CalendarEventDraft(title,description,location,LocalDate.parse(date),LocalDate.parse(endDate),start.isBlank()?null:LocalTime.parse(start),end.isBlank()?null:LocalTime.parse(end),allDay);}
    }
    record Operation(String id,String account,String action,String eventId,Draft draft,String etag,boolean recurring,String error,boolean blocked,boolean attempted,Draft sentDraft) {
        Operation failure(String message,boolean permanent){return new Operation(id,account,action,eventId,draft,etag,recurring,message,permanent,attempted,sentDraft);}
        Operation attempting(){return new Operation(id,account,action,eventId,draft,etag,recurring,"",false,true,sentDraft==null?draft:sentDraft);}
        CalendarEventItem local(){var d=draft.value(); var item=d.allDay()?CalendarEventItem.allDay(eventId,d.title(),d.startDate(),d.description(),d.location())
                :CalendarEventItem.timed(eventId,d.title(),d.startDate().atTime(d.startTime()),d.endDate().atTime(d.endTime()),d.description(),d.location());
            return item.withGoogleMetadata(d.endDate(),etag.isBlank()?"pending":etag,true,recurring).withSyncState(blocked?"Çakışma / hata":"Gönderim bekliyor");}
    }
    record Data(int schemaVersion,List<Operation> operations) {}
    private final Path file;
    private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    CalendarWriteQueue(Path root){file=root.resolve("data/calendar/outbox.json");}
    List<Operation> load() throws IOException {
        if(!Files.exists(file))return List.of();
        Data data=mapper.readValue(FinanceStorageService.readLimited(file),Data.class);
        if(data.schemaVersion()!=1 || data.operations()==null || data.operations().size()>1000)throw new IOException("Takvim gönderim kuyruğu geçersiz.");
        Set<String> ids=new HashSet<>(),targets=new HashSet<>();
        try { for(var op:data.operations()){
            if(op.id()==null || !op.id().matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(op.id()) || op.account()==null || op.account().isBlank()
                    || !List.of("CREATE","UPDATE","DELETE").contains(op.action()) || !op.eventId().matches("[a-zA-Z0-9_-]{1,1024}")
                    || !targets.add(op.account()+":"+op.eventId()) || op.etag()==null || op.error()==null)throw new IOException("Takvim kuyruk kaydı geçersiz.");
            op.draft().value();
        }}catch(RuntimeException error){throw new IOException("Takvim kuyruk alanları geçersiz.",error);}
        return List.copyOf(data.operations());
    }
    Operation enqueue(String account,String action,String id,CalendarEventDraft draft,CalendarEventItem original) throws IOException {
        return change(entries->{var old=entries.stream().filter(o->o.account().equals(account)&&o.eventId().equals(id)).findFirst().orElse(null);
            if(old!=null)entries.remove(old);
            if(action.equals("DELETE") && old!=null && old.action().equals("CREATE") && !old.attempted())return null;
            var value=new Operation(old==null?UUID.randomUUID().toString():old.id(),account,old!=null&&old.action().equals("CREATE")&&!action.equals("DELETE")?"CREATE":action,id,
                    Draft.of(draft),old==null?(original==null?"":original.getEtag()):old.etag(),original!=null&&original.isRecurringInstance(),"",false,old!=null&&old.attempted(),old==null?null:old.sentDraft());
            entries.add(value); return value;
        });
    }
    void remove(String id)throws IOException{change(entries->{entries.removeIf(o->o.id().equals(id));return null;});}
    void replace(Operation value)throws IOException{change(entries->{int i=0;while(i<entries.size()&&!entries.get(i).id().equals(value.id()))i++;
        if(i==entries.size())throw new IOException("Kuyruk kaydı değişti. Yenile."); entries.set(i,value);return null;});}
    List<CalendarEventItem> overlay(String account,YearMonth month,List<CalendarEventItem> source)throws IOException{
        if(account.isBlank())return source; var events=new ArrayList<>(source);
        for(var op:load())if(op.account().equals(account)){
            events.removeIf(e->e.isPrimaryCalendar()&&op.eventId().equals(e.getId())); if(!op.action().equals("DELETE")){
                var e=op.local(); if(!e.getDate().isAfter(month.atEndOfMonth())&&!e.getEndDate().isBefore(month.atDay(1)))events.add(e);
            }
        }
        events.sort(Comparator.comparing(CalendarEventItem::getDate).thenComparing(e->e.getStartTime()==null?LocalTime.MIN:e.getStartTime()));return events;
    }
    private <T>T change(Update<T> action)throws IOException{
        return AtomicFileWriter.exclusive(()->{
            Files.createDirectories(file.getParent());
            try(var channel=FileChannel.open(file.resolveSibling("outbox.json.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()){
                if(lock==null)throw new IOException("Takvim kuyruğu başka bir işlemde kullanılıyor.");
                var list=new ArrayList<>(load());T result=action.apply(list);if(list.size()>1000)throw new IOException("Takvimde en fazla 1000 bekleyen değişiklik olabilir.");byte[]bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Data(1,list));
                if(bytes.length>FinanceStorageService.MAX_BYTES)throw new IOException("Takvim kuyruğu çok büyük."); AtomicFileWriter.write(file,bytes);return result;
            }catch(java.nio.channels.OverlappingFileLockException error){throw new IOException("Takvim kuyruğu başka bir işlemde kullanılıyor.",error);}
        });
    }
    @FunctionalInterface private interface Update<T>{T apply(ArrayList<Operation> operations)throws IOException;}
}
