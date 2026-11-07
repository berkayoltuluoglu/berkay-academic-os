package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Completed focus periods are stored once, independently of the editable task/course files. */
public final class StudyTimeLogService {
    public record Entry(String id, String targetId, String kind, int minutes, String completedAt) {}
    public record Document(int schemaVersion, List<Entry> entries) {}
    private final Path file;
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    public StudyTimeLogService(Path root) { this.root=root; file = root.resolve("data/time/focus-log.json"); }
    public List<Entry> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        Document data = mapper.readValue(FinanceStorageService.readLimited(file), Document.class);
        if ((data.schemaVersion() != 1 && data.schemaVersion() != 2) || data.entries().size() > 50000) throw new IOException("Çalışma süresi kayıtları geçersiz.");
        Set<String> ids = new HashSet<>();
        for (Entry entry : data.entries()) {
            if (entry == null || !entry.id().matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(entry.id())
                    || !entry.targetId().matches("[a-zA-Z0-9_-]{1,80}") || !Set.of("task", "work", "session").contains(entry.kind())
                    || entry.minutes() < 1 || entry.minutes() > 180) throw new IOException("Çalışma süresi kaydı geçersiz.");
            try { Instant.parse(entry.completedAt()); } catch (RuntimeException error) { throw new IOException("Çalışma kaydı tarihi geçersiz.", error); }
        }
        return List.copyOf(data.entries());
    }
    public void credit(Entry entry) throws IOException {
        if (entry == null || entry.id() == null || !entry.id().matches("[a-zA-Z0-9_-]{1,80}")
                || entry.targetId() == null || !entry.targetId().matches("[a-zA-Z0-9_-]{1,80}")
                || !Set.of("task", "work", "session").contains(entry.kind()) || entry.minutes() < 1 || entry.minutes() > 180)
            throw new IOException("Çalışma süresi kaydı geçersiz.");
        try { Instant.parse(entry.completedAt()); } catch (RuntimeException failure) { throw new IOException("Çalışma kaydı tarihi geçersiz.", failure); }
        AtomicFileWriter.exclusive(() -> {
            var before=PlanningFiles.capture(root,List.of("data/time/focus-log.json","data/study/sessions.json"));
            List<Entry> entries = new ArrayList<>(load());
            var existing = entries.stream().filter(value -> value.id().equals(entry.id())).findFirst();
            if (existing.isPresent() && !existing.get().equals(entry)) throw new IOException("Odak kaydının kimliği başka bir kayıtla çakışıyor.");
            if (existing.isEmpty()) {
                entries.add(entry); byte[] bytes = mapper.writeValueAsBytes(new Document(2, entries));
                if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Çalışma süresi kayıtları 5 MB sınırını aşıyor.");
                Map<String,byte[]> after=new LinkedHashMap<>();after.put("data/time/focus-log.json",bytes);
                if (entry.kind().equals("session")) {
                    var store=new StudyStorageService(root.resolve("data/study/sessions.json"));var sessions=store.load();
                    if(sessions.stream().noneMatch(s->s.id().equals(entry.targetId())))throw new IOException("Odak bloğu silinmiş; süre kaydı korunuyor, önce bloğu geri al");
                    var next=sessions.stream().map(s->s.id().equals(entry.targetId())?new StudySession(s.id(),s.title(),s.date(),s.start(),s.end(),s.courseId(),s.done(),s.taskId(),s.workId(),Math.min(1440,s.actualMinutes()+entry.minutes())):s).toList();
                    after.put("data/study/sessions.json",mapper.writeValueAsBytes(new StudyStorageService.Document(1,next)));
                }
                PlanningFiles.commit(root,before,after);
            }
            return null;
        });
    }
}
