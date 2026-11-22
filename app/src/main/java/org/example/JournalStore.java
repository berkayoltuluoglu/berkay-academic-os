package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Lists and manual completed activities; task records and focus credits retain their existing stores. */
final class JournalStore {
    static final String FILE = "data/planning/journal.json";
    record TaskList(String id, String name) { @Override public String toString() { return name; } }
    record Placement(String listId, String courseId) {}
    record Activity(String id, String date, String title, String start, String end, int minutes, String taskId, String focusId) {}
    record Document(int schemaVersion, List<TaskList> lists, Map<String, Placement> placements, List<Activity> activities) {
        Document withLists(List<TaskList> value) { return new Document(1,List.copyOf(value),placements,activities); }
        Document withPlacements(Map<String,Placement> value) { return new Document(1,lists,Map.copyOf(value),activities); }
        Document withActivities(List<Activity> value) { return new Document(1,lists,placements,List.copyOf(value)); }
        Placement placement(String taskId) { return placements.getOrDefault(taskId,new Placement("genel","")); }
    }
    static Document empty() { return new Document(1,List.of(new TaskList("genel","Genel"),new TaskList("dersler","Dersler"),new TaskList("kisisel","Kişisel")),Map.of(),List.of()); }
    static Document load(Path root) throws IOException {
        try {
            Document value=PlanningFiles.read(root,FILE,Document.class,empty()); validate(value); return value;
        } catch (RuntimeException error) { throw new IOException("Yapılacaklar ve günlük verileri geçersiz; kayıtlar korundu.",error); }
    }
    static void save(Path root, Document value) throws IOException {
        validate(value); AtomicFileWriter.write(PlanningFiles.safe(root,FILE),PlanningFiles.JSON.writeValueAsBytes(value));
    }
    static void validate(Document value) throws IOException {
        require(value != null && value.schemaVersion()==1 && value.lists()!=null && value.placements()!=null && value.activities()!=null,"Günlük şeması geçersiz.");
        require(value.lists().size()<=200 && value.placements().size()<=10000 && value.activities().size()<=10000,"Günlük kayıt sınırı aşıldı.");
        Set<String> ids=new HashSet<>(), names=new HashSet<>();
        for(var list:value.lists()) {
            require(list!=null && id(list.id()) && ids.add(list.id()) && text(list.name(),80),"Liste bilgileri geçersiz.");
            require(names.add(list.name().trim().toLowerCase(Locale.forLanguageTag("tr"))),"Aynı adla iki liste oluşturulamaz.");
        }
        require(ids.contains("genel"),"Genel listesi bulunmalı.");
        for(var e:value.placements().entrySet()) require(id(e.getKey()) && e.getValue()!=null && ids.contains(e.getValue().listId())
                && optionalId(e.getValue().courseId()),"Görev listesi veya ders bağlantısı geçersiz.");
        ids.clear();Set<String> overlays=new HashSet<>();
        for(var entry:value.activities()) {
            require(entry!=null && id(entry.id()) && ids.add(entry.id()) && text(entry.title(),1000)
                    && optionalId(entry.taskId()) && optionalId(entry.focusId()) && entry.start()!=null && entry.end()!=null,"Faaliyet bilgileri geçersiz.");
            try {
                require(entry.date()!=null && entry.date().matches("\\d{4}-\\d{2}-\\d{2}"),"Faaliyet tarihi gerekiyor."); LocalDate.parse(entry.date());
                if(!entry.focusId().isBlank()) {
                    require(entry.minutes()==0 && entry.start().isBlank() && entry.end().isBlank()
                            && overlays.add(entry.date()+":"+entry.focusId()),"Sayaç notu geçersiz veya tekrarlanmış.");
                } else {
                    require(entry.minutes()>0 && entry.minutes()<=1439,"Faaliyet süresi geçersiz.");
                    require(entry.start().isBlank()==entry.end().isBlank(),"Başlangıç ve bitiş birlikte olmalı.");
                    if(!entry.start().isBlank()) {
                        require(entry.start().matches("\\d{2}:\\d{2}") && entry.end().matches("\\d{2}:\\d{2}"),"Saat biçimi HH:mm olmalı.");
                        require(java.time.Duration.between(LocalTime.parse(entry.start()),LocalTime.parse(entry.end())).toMinutes()==entry.minutes(),"Saat aralığı ve süre uyuşmuyor.");
                    }
                }
            } catch (RuntimeException error) { throw new IOException("Faaliyet tarihi veya saati geçersiz.",error); }
        }
    }
    static boolean id(String value) { return value!=null && value.matches("[a-zA-Z0-9_-]{1,80}"); }
    private static boolean optionalId(String value) { return value!=null && (value.isEmpty() || id(value)); }
    private static boolean text(String value,int limit) { return value!=null && !value.isBlank() && value.length()<=limit; }
    private static void require(boolean valid,String message) throws IOException { if(!valid) throw new IOException(message); }
}
