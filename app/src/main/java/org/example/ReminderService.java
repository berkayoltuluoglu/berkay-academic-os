package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** A single background scheduler; persisted delivery keys prevent duplicate alerts after restart. */
public final class ReminderService implements AutoCloseable {
    @FunctionalInterface public interface Notifier { void show(String title, String message) throws Exception; }
    public record Notice(String key, String title, String message, Instant at, Instant due) {}
    private final Path root;
    private final Clock clock;
    private final Notifier notifier;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "academic-os-reminders"); thread.setDaemon(true); return thread;
    });
    private Map<String, Long> delivered;
    private volatile String error;
    private java.util.function.Consumer<String> statusListener = value -> {};

    public ReminderService(Path root, Notifier notifier) { this(root, notifier, Clock.systemDefaultZone()); }
    ReminderService(Path root, Notifier notifier, Clock clock) { this.root = root; this.notifier = notifier; this.clock = clock; }
    public void start() { worker.scheduleWithFixedDelay(this::pollSafely, 0, 30, TimeUnit.SECONDS); }
    public String getError() { return error; }
    public void setStatusListener(java.util.function.Consumer<String> listener) { statusListener = listener; }
    private void pollSafely() {
        try { poll(); } catch (Exception failure) { error = "Bildirim hatası: " + failure.getMessage(); }
        statusListener.accept(error);
    }

    synchronized void poll() throws Exception {
        error = null;
        Path focusFile = root.resolve("data/focus.properties");
        if (Files.exists(focusFile)) try { new FocusTimerService(focusFile, clock).creditCompleted(); }
        catch (IOException failure) { error = "Odak süresi kaydedilemedi: " + failure.getMessage(); }
        AppPreferences preferences = AppPreferences.load(root);
        if (!preferences.remindersEnabled()) return;
        Path state = root.resolve("data/reminders/delivered.json");
        if (delivered == null) delivered = Files.exists(state)
                ? mapper.readValue(FinanceStorageService.readLimited(state), new TypeReference<Map<String, Long>>() {}) : new HashMap<>();
        Instant now = clock.instant();
        delivered.entrySet().removeIf(entry -> entry.getValue() < now.minus(Duration.ofDays(7)).toEpochMilli());
        boolean changed = false;
        var activeFocus=new FocusTimerService(focusFile,clock);
        var planning=PlanningPreferences.load(root);
        for (Notice notice : collect(preferences)) {
            if(activeFocus.running() && !activeFocus.breakMode() && (!planning.deadlineNotifications() || !java.util.Set.of("tasks","courses","classroom","finance").contains(notice.key().split(":",2)[0])))continue;
            if (!notice.at().isAfter(now) && notice.due().isAfter(now.minus(Duration.ofHours(1))) && !delivered.containsKey(notice.key())) {
                notifier.show(notice.title(), notice.message());
                delivered.put(notice.key(), now.toEpochMilli()); changed = true;
            }
        }
        if (changed) AtomicFileWriter.write(state, mapper.writeValueAsBytes(delivered));
    }

    List<Notice> collect(AppPreferences preferences) throws IOException {
        List<Notice> notices = new ArrayList<>(); Set<String> keys = new HashSet<>();
        LocalDate today = LocalDate.now(clock);
        for (int offset = 0; offset <= 1; offset++) {
            var day = new DailyAgendaService().load(root, today.plusDays(offset));
            if (!day.errors().isEmpty()) error = String.join(" · ", day.errors());
            for (var todo : day.todos()) if (!todo.done() && !todo.date().isBefore(today)) {
                LocalTime time = todo.dueTime() == null ? LocalTime.parse(preferences.dateOnlyReminderTime()) : todo.dueTime();
                String label = todo.coursework() && todo.detail().startsWith("Sınav ·") ? "Sınav başlangıcı: " : "Son teslim: ";
                add(notices, keys, todo.module() + ":" + todo.id(), todo.title(), label + todo.date() + " " + time,
                        todo.date().atTime(time), preferences.reminderMinutes());
            }
            for (var event : day.events()) if (!event.done() && (event.study() || "Google".equals(event.kind())))
                add(notices, keys, event.kind() + ":" + event.id(), event.title(), "Başlangıç: " + event.date() + " " + event.start(),
                        event.date().atTime(event.start()), preferences.reminderMinutes());
        }
        try{for(var proposal:new ProposalService(root).list("pending"))notices.add(new Notice("proposal:"+proposal.id(),"Yeni plan önerisi",proposal.agent()+" · "+proposal.note(),Instant.parse(proposal.createdAt()),clock.instant()));}
        catch(Exception failure){error="Öneriler okunamadı: "+failure.getMessage();}
        Path focus = root.resolve("data/focus.properties");
        if (Files.exists(focus)) {
            Properties p = new Properties(); try (var reader = Files.newBufferedReader(focus)) { p.load(reader); }
            long deadline;
            try { deadline = Long.parseLong(p.getProperty("deadline", "0")); }
            catch (NumberFormatException failure) { throw new IOException("Odak sayacı kaydı geçersiz.", failure); }
            if (deadline > 0) {
                Instant due = Instant.ofEpochMilli(deadline);
                notices.add(new Notice("focus:" + deadline, "Odak oturumu tamamlandı", "Kısa bir mola verebilirsin.", due, due));
            }
        }
        return notices;
    }
    private void add(List<Notice> notices, Set<String> keys, String id, String title, String message, LocalDateTime dateTime, int lead) {
        Instant due = dateTime.atZone(clock.getZone()).toInstant();
        String key = id + ":" + due + ":" + lead;
        if (keys.add(key)) notices.add(new Notice(key, title, message, due.minus(Duration.ofMinutes(lead)), due));
    }
    @Override public void close() { worker.shutdownNow(); }
}
