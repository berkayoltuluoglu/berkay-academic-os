package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.example.PlannerCommand.*;

/** All planning writes are validated on an isolated copy, then committed under record locks. */
public final class PlannerService {
    static final List<String> DOMAIN = List.of("data/tasks.json", "data/courses/academic.json", "data/study/sessions.json",
            PlanningPreferences.FILE, "data/planning/notes.json");
    static final String HISTORY = "data/planning/history.json";
    /** One undoable step: only the fields that changed, never whole-file copies (see {@link PlanEdits}). */
    public record PlanChange(String id, String at, String actor, String reason, List<PlanEdits.Edit> edits) {}
    static final int HISTORY_VERSION = 2;
    public record History(int schemaVersion, List<PlanChange> undo, List<PlanChange> redo) {}
    public record Notes(int schemaVersion, Map<String,String> notes) {}
    public record Result(PlanChange change, List<StudySession> blocks, List<String> notices) {}
    private final Path root;
    private CourseData snapshotCourses=CourseData.empty();
    CourseData snapshotCourses(){return snapshotCourses;}
    private List<TaskItem> snapshotTasks = List.of();
    List<TaskItem> snapshotTasks() { return snapshotTasks; }
    private String expected;
    private LocalDate expectedWeek;
    public PlannerService(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public synchronized PlanningSnapshot snapshot(LocalDate week) throws Exception {
        var snapshot = PlanningSnapshot.load(root, week, data -> { snapshotTasks = data.tasks();snapshotCourses=data.courses(); }); expected = snapshot.fingerprint(); expectedWeek = LocalDate.parse(snapshot.week()); return snapshot;
    }
    static List<String> snapshotFiles(LocalDate week) {
        List<String> names = new ArrayList<>(DOMAIN);
        names.addAll(List.of("data/time/focus-log.json", "data/classroom/catalog.json", "data/calendar/account.properties",
                "data/calendar/sources.json", "data/calendar/outbox.json", "data/finance/applications.json"));
        for (var m = YearMonth.from(week); !m.isAfter(YearMonth.from(week.plusDays(6))); m = m.plusMonths(1)) names.add("data/calendar/" + m + ".json");
        return List.copyOf(names);
    }
    static String fingerprint(Path root, LocalDate week) throws IOException {
        var hashes = new StringBuilder();
        for (String name : snapshotFiles(week)) { byte[] bytes = PlanningFiles.bytes(root, name); hashes.append(name).append(':').append(bytes == null ? "absent" : PlanningFiles.hash(bytes)).append('\n'); }
        return PlanningFiles.hash(hashes.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public synchronized Result execute(PlannerCommand command) throws Exception { return execute(List.of(command), "user", command.getClass().getSimpleName()); }
    public synchronized Result execute(List<PlannerCommand> commands, String actor, String reason) throws Exception {
        return transact(commands, actor, reason, true, Map.of());
    }
    /** Additional proposal result files join the same atomic/rollback unit as the plan. */
    synchronized Result transact(List<PlannerCommand> commands, String actor, String reason, boolean apply, Map<String,byte[]> extra) throws Exception {
        return transact(commands,actor,reason,apply,extra,Map.of());
    }
    synchronized Result transact(List<PlannerCommand> commands, String actor, String reason, boolean apply, Map<String,byte[]> extra, Map<String,byte[]> extraBefore) throws Exception {
        if (commands == null || commands.isEmpty() || commands.size() > 500) throw new IOException("INVALID_COMMAND: 1–500 komut gerekli");
        if (actor == null || actor.isBlank() || actor.length() > 300 || reason == null || reason.length() > 5000) throw new IOException("İşlem açıklaması geçersiz");
        if (expected != null && !expected.equals(fingerprint(root, expectedWeek))) throw new IOException("STALE_SNAPSHOT: Dosya değişti. Önce yenile.");
        PlanningPreferences.load(root); notes(root); history(root);
        Map<String, byte[]> before = PlanningFiles.capture(root, DOMAIN);
        Map<String, byte[]> guards = new LinkedHashMap<>(before);
        // Protect all read dependencies throughout the transaction, without exposing any credentials.
        for (String name : List.of("data/classroom/catalog.json", "data/time/focus-log.json", "data/calendar/account.properties", "data/calendar/sources.json", "data/calendar/outbox.json", "data/finance/applications.json"))
            guards.put(name, PlanningFiles.bytes(root, name));
        Set<YearMonth> months = new TreeSet<>();
        for (var command : commands) {
            if (command instanceof SaveBlock c) months.add(YearMonth.from(LocalDate.parse(c.block().date())));
            if (command instanceof ScheduleBlock c) months.add(YearMonth.from(LocalDate.parse(c.date())));
            if (command instanceof MoveBlock c) months.add(YearMonth.from(LocalDate.parse(c.date())));
        }
        // Existing blocks may be resized/completed and use other month caches.
        for (var block : new StudyStorageService(root.resolve(DOMAIN.get(2))).load()) months.add(YearMonth.from(LocalDate.parse(block.date())));
        for (var month : months) guards.put("data/calendar/" + month + ".json", PlanningFiles.bytes(root, "data/calendar/" + month + ".json"));
        Path stage = Files.createTempDirectory("academic-plan-");
        List<String> notices = new ArrayList<>();
        try {
            for (var entry : guards.entrySet()) if (entry.getValue() != null) {
                Path target = stage.resolve(entry.getKey()); Files.createDirectories(target.getParent()); Files.write(target, entry.getValue());
            }
            for (var command : commands) apply(stage, command, notices);
            Map<String, byte[]> after = PlanningFiles.capture(stage, DOMAIN);
            List<PlanEdits.Edit> edits = new ArrayList<>();
            for (String name : DOMAIN) edits.addAll(PlanEdits.diff(name, before.get(name), after.get(name)));
            var change = new PlanChange(UUID.randomUUID().toString(), Instant.now().toString(), actor, reason, List.copyOf(edits));
            if (apply) {
                var history = history(root); List<PlanChange> undo = new ArrayList<>(history.undo()); undo.add(change);
                while (undo.size() > 50) undo.removeFirst();
                guards.put(HISTORY, PlanningFiles.bytes(root, HISTORY));
                after.put(HISTORY, historyBytes(undo, new ArrayList<>()));
                for (var entry : extra.entrySet()) {
                    if (!entry.getKey().matches("data/agent/(proposals|archive)/[a-zA-Z0-9_-]{1,80}\\.json")) throw new IOException("Öneri yolu geçersiz");
                    guards.put(entry.getKey(), extraBefore.containsKey(entry.getKey()) ? extraBefore.get(entry.getKey()) : PlanningFiles.bytes(root, entry.getKey())); after.put(entry.getKey(), entry.getValue());
                }
                PlanningFiles.commit(root, guards, after);
                if (expected != null) expected = fingerprint(root, expectedWeek);
            }
            return new Result(change, new StudyStorageService(stage.resolve(DOMAIN.get(2))).load(), List.copyOf(notices));
        } finally {
            try (var paths = Files.walk(stage)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    public synchronized PlanChange undo() throws Exception { return travel(false); }
    public synchronized PlanChange redo() throws Exception { return travel(true); }
    private PlanChange travel(boolean redo) throws Exception {
        var history = history(root); var from = new ArrayList<>(redo ? history.redo() : history.undo());
        if (from.isEmpty()) throw new IOException(redo ? "Yinelenecek işlem yok" : "Geri alınacak işlem yok");
        var change = from.removeLast();
        // Each edit checks the field still holds the value this step left, so external edits are never overwritten.
        Map<String,byte[]> required = new LinkedHashMap<>(), target = new LinkedHashMap<>(), domain = PlanningFiles.capture(root, DOMAIN);
        Map<String,List<PlanEdits.Edit>> byFile = new LinkedHashMap<>();
        for (var edit : change.edits()) byFile.computeIfAbsent(edit.file(), k -> new ArrayList<>()).add(edit);
        for (var e : byFile.entrySet()) {
            byte[] current = domain.get(e.getKey()), next = PlanEdits.apply(current, e.getValue(), !redo);
            required.put(e.getKey(), current); target.put(e.getKey(), next); domain.put(e.getKey(), next);
        }
        validateDomain(domain);
        var to = new ArrayList<>(redo ? history.undo() : history.redo()); to.add(change);
        required.put(HISTORY, PlanningFiles.bytes(root, HISTORY));
        target.put(HISTORY, historyBytes(redo ? to : from, redo ? from : to));
        PlanningFiles.commit(root, required, target);
        if (expected != null) expected = fingerprint(root, expectedWeek);
        return change;
    }
    static void validateDomain(Map<String,byte[]> files)throws IOException{
        if(!files.keySet().equals(new LinkedHashSet<>(DOMAIN)))throw new IOException("İşlem geçmişi yolları geçersiz");
        Path stage=Files.createTempDirectory("academic-undo-check-");
        try{
            for(var e:files.entrySet())if(e.getValue()!=null){Path target=stage.resolve(e.getKey());Files.createDirectories(target.getParent());Files.write(target,e.getValue());}
            var tasks=new TaskStorageService(stage.resolve(DOMAIN.getFirst())).loadTasks();
            var courses=new CourseStorageService(stage.resolve(DOMAIN.get(1))).load();
            var blocks=new StudyStorageService(stage.resolve(DOMAIN.get(2))).load();PlanningPreferences.load(stage);notes(stage);
            for(var block:blocks){
                if(!block.taskId().isBlank()&&tasks.stream().noneMatch(t->t.getId().equals(block.taskId())))throw new IOException("Geçmişteki görev bağlantısı geçersiz");
                if(!block.workId().isBlank()&&courses.work().stream().noneMatch(w->w.id().equals(block.workId())))throw new IOException("Geçmişteki ödev bağlantısı geçersiz");
                if(!block.courseId().isBlank()&&courses.courses().stream().noneMatch(c->c.id().equals(block.courseId())))throw new IOException("Geçmişteki ders bağlantısı geçersiz");
            }
        }catch(RuntimeException error){throw new IOException("İşlem geçmişi verileri geçersiz; dosyalar korunuyor",error);}
        finally{try(var paths=Files.walk(stage)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    /** Safety net for the 5 MB write limit; field-level steps are tiny, but a bulk import could still be large. */
    static final int HISTORY_BUDGET = 3 * 1024 * 1024;
    static byte[] historyBytes(List<PlanChange> undo, List<PlanChange> redo) throws IOException {
        byte[] bytes = PlanningFiles.JSON.writeValueAsBytes(new History(HISTORY_VERSION, List.copyOf(undo), List.copyOf(redo)));
        while (bytes.length > HISTORY_BUDGET && undo.size() + redo.size() > 1) {
            if (undo.size() > (redo.isEmpty() ? 1 : 0)) undo.removeFirst(); else redo.removeFirst();
            bytes = PlanningFiles.JSON.writeValueAsBytes(new History(HISTORY_VERSION, List.copyOf(undo), List.copyOf(redo)));
        }
        return bytes;
    }
    static History history(Path root) throws IOException {
        byte[] bytes = PlanningFiles.bytes(root, HISTORY);
        if (bytes == null) return new History(HISTORY_VERSION, List.of(), List.of());
        History value;
        try {
            var tree = PlanningFiles.JSON.readTree(bytes);
            // Version 1 stored whole-file snapshots (~600 KB per step); it is dropped once and replaced on the next write.
            if (tree.path("schemaVersion").asInt() == 1) return new History(HISTORY_VERSION, List.of(), List.of());
            value = PlanningFiles.JSON.treeToValue(tree, History.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JacksonException error) { throw new IOException("İşlem geçmişi geçersiz", error); }
        if (value.schemaVersion() != HISTORY_VERSION || value.undo() == null || value.redo() == null || value.undo().size() + value.redo().size() > 50) throw new IOException("İşlem geçmişi geçersiz");
        for (var c : java.util.stream.Stream.concat(value.undo().stream(), value.redo().stream()).toList()) {
            if (c == null || c.edits() == null || c.edits().size() > 100_000) throw new IOException("İşlem geçmişi geçersiz");
            for (var edit : c.edits()) PlanEdits.validate(edit, DOMAIN);
            try { Instant.parse(c.at()); } catch (RuntimeException error) { throw new IOException("İşlem geçmişi geçersiz", error); }
        }
        return value;
    }
    static Notes notes(Path root) throws IOException {
        var value = PlanningFiles.read(root, DOMAIN.get(4), Notes.class, new Notes(1, Map.of()));
        if (value.schemaVersion() != 1 || value.notes() == null || value.notes().size() > 10000) throw new IOException("Planlama notları geçersiz");
        for (var e : value.notes().entrySet()) if (!e.getKey().matches("[a-zA-Z0-9_-]{1,80}") || e.getValue() == null || e.getValue().length() > 10000) throw new IOException("Planlama notu geçersiz");
        return value;
    }
    static String applicationTaskId(String id){return "application-"+UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static StudySession block(Path root, String id) throws IOException {
        return new StudyStorageService(root.resolve(DOMAIN.get(2))).load().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> new IOException("NOT_FOUND: Çalışma bloğu bulunamadı"));
    }
    private static void apply(Path root, PlannerCommand command, List<String> notices) throws Exception {
        var agenda = new DailyAgendaService();
        if (command instanceof SaveBlock c) { saveBlock(root, c.block(), c.create(), c.policy(), notices); }
        else if (command instanceof ScheduleBlock c) {
            String task = "", work = "", course = "", title = c.title();
            if ("classroom".equals(c.kind())) {
                var data = new GoogleClassroomService(root).loadCached();
                var remote = data.work().stream().filter(w -> ClassroomImportService.workId(data.accountEmail(), w.courseId(), w.id()).equals(c.itemId())).findFirst().orElseThrow(() -> new IOException("Classroom ödevi bulunamadı"));
                new ClassroomImportService(root).importWork(data, remote.courseId(), remote.id()); work = c.itemId();
            } else if ("task".equals(c.kind())) task = c.itemId();
            else if ("work".equals(c.kind())) work = c.itemId();
            else if (!"".equals(c.kind()) && !"application".equals(c.kind())) throw new IOException("Bağlantı türü geçersiz");
            if("application".equals(c.kind())) {
                var application=new ScholarshipStorageService(root.resolve("data/finance/applications.json")).load().applications().stream().filter(a->a.id().equals(c.itemId())).findFirst().orElseThrow(()->new IOException("Başvuru bulunamadı"));
                task=applicationTaskId(c.itemId());String linkedId=task;
                var store=new TaskStorageService(root.resolve(DOMAIN.getFirst()));var all=new ArrayList<>(store.loadTasks());
                if(all.stream().noneMatch(t->t.getId().equals(linkedId))){all.add(new TaskItem(task,application.name(),application.deadline().isBlank()?null:LocalDate.parse(application.deadline()),"Burs / başvuru için çalışma","Bekliyor"));store.saveTasks(all);}
            }
            if (!work.isBlank()) {
                String id = work; var w = new CourseStorageService(root.resolve(DOMAIN.get(1))).load().work().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IOException("Ödev bulunamadı"));
                course = w.courseId(); if (title == null || title.isBlank()) title = w.title();
            }
            if (!task.isBlank()) { String id = task; var t = new TaskStorageService(root.resolve(DOMAIN.getFirst())).loadTasks().stream().filter(v -> v.getId().equals(id)).findFirst().orElseThrow(() -> new IOException("Görev bulunamadı")); if (title == null || title.isBlank()) title = t.getTitle(); }
            int start = PlanningPreferences.minute(c.start());
            if (c.minutes() < 15 || c.minutes() > 1439 || start + c.minutes() > 1439) throw new IOException("Blok aynı gün içinde en az 15 dakika olmalı; son saat 23:59");
            saveBlock(root, new StudySession(UUID.randomUUID().toString(), title, c.date(), c.start(), PlanningPreferences.time(start + c.minutes()), course, false, task, work, 0), true, c.policy(), notices);
        } else if (command instanceof MoveBlock c) {
            var b = block(root,c.id()); int minutes = PlanningPreferences.minute(b.end()) - PlanningPreferences.minute(b.start()), start = PlanningPreferences.minute(c.start());
            if (start + minutes > 1439) throw new IOException("Blok gece yarısını geçemez");
            saveBlock(root,b.moved(c.date(),c.start(),PlanningPreferences.time(start+minutes)),false,c.policy(),notices);
        } else if (command instanceof ResizeBlock c) {
            var b = block(root,c.id()); int end = PlanningPreferences.minute(b.start())+c.minutes();
            if (c.minutes() < 15 || end > 1439) throw new IOException("Blok süresi geçersiz");
            saveBlock(root,b.moved(b.date(),b.start(),PlanningPreferences.time(end)),false,c.policy(),notices);
        } else if (command instanceof DeleteBlock c) agenda.deleteSession(root,c.id());
        else if (command instanceof CompleteBlock c) {
            var b = block(root,c.id()); if (c.actualMinutes() < 0 || c.actualMinutes() > 1440) throw new IOException("Gerçek süre geçersiz");
            saveBlock(root,new StudySession(b.id(),b.title(),b.date(),b.start(),b.end(),b.courseId(),c.done(),b.taskId(),b.workId(),c.actualMinutes()),false,ConflictPolicy.ALLOW,notices);
        } else if (command instanceof CreateTask c || command instanceof UpdateTask) {
            var task = command instanceof CreateTask create ? create.task() : ((UpdateTask)command).task();
            if (task == null || task.getTitle() == null || task.getTitle().isBlank() || task.getTitle().length() > 300 || task.getDescription() == null || task.getDescription().length() > 10000) throw new IOException("Görev başlığı/notu geçersiz");
            var store = new TaskStorageService(root.resolve(DOMAIN.getFirst())); var tasks = new ArrayList<>(store.loadTasks());
            boolean exists = tasks.stream().anyMatch(t -> t.getId().equals(task.getId()));
            if ((command instanceof CreateTask) == exists) throw new IOException("Görev değişti. Önce yenile.");
            tasks.removeIf(t -> t.getId().equals(task.getId())); tasks.add(task.copy()); store.saveTasks(tasks);
        } else if (command instanceof UpdateWork c) {
            var store=new CourseStorageService(root.resolve(DOMAIN.get(1)));var data=store.load();
            if(data.work().stream().noneMatch(w->w.id().equals(c.work().id())))throw new IOException("Ödev değişti. Önce yenile.");
            store.save(data.withWork(data.work().stream().map(w->w.id().equals(c.work().id())?c.work():w).toList()));
        } else if (command instanceof CompleteItem c) {
            if (!Set.of("task","work").contains(c.kind())) throw new IOException("İş türü geçersiz");
            agenda.setDone(root,new DailyAgendaService.Todo(c.id(),"",LocalDate.now(),"",c.kind().equals("work"),false),c.done());
        } else if (command instanceof SetEstimate c) {
            if (c.minutes() < 0 || c.minutes() > 100000) throw new IOException("Tahmin geçersiz");
            if ("task".equals(c.kind())) {
                var store = new TaskStorageService(root.resolve(DOMAIN.getFirst())); var tasks = store.loadTasks();
                var t = tasks.stream().filter(v -> v.getId().equals(c.id())).findFirst().orElseThrow(() -> new IOException("Görev bulunamadı")); t.setEstimatedMinutes(c.minutes()); store.saveTasks(tasks);
            } else if ("work".equals(c.kind())) {
                var store = new CourseStorageService(root.resolve(DOMAIN.get(1))); var data = store.load();
                if (data.work().stream().noneMatch(w -> w.id().equals(c.id()))) throw new IOException("Ödev bulunamadı");
                store.save(data.withWork(data.work().stream().map(w -> w.id().equals(c.id()) ? new CourseData.Work(w.id(),w.courseId(),w.title(),w.kind(),w.date(),w.done(),w.dueTime(),c.minutes(),w.loggedMinutes(),w.priority(),w.endTime()) : w).toList()));
            } else throw new IOException("İş türü geçersiz");
        } else if (command instanceof ImportClassroomWork c) new ClassroomImportService(root).importWork(new GoogleClassroomService(root).loadCached(),c.courseId(),c.workId());
        else if (command instanceof SetPreferences c) { c.preferences().validate(); AtomicFileWriter.write(root.resolve(PlanningPreferences.FILE),PlanningFiles.JSON.writeValueAsBytes(c.preferences())); }
        else if (command instanceof SetNote c) {
            if (c.id() == null || !c.id().matches("[a-zA-Z0-9_-]{1,80}") || c.note() == null || c.note().length()>10000) throw new IOException("Not geçersiz");
            var all = new LinkedHashMap<>(notes(root).notes()); all.put(c.id(),c.note());
            AtomicFileWriter.write(root.resolve(DOMAIN.get(4)),PlanningFiles.JSON.writeValueAsBytes(new Notes(1,all)));
        } else throw new IOException("INVALID_COMMAND: Bilinmeyen komut");
    }
    private static void saveBlock(Path root, StudySession block, boolean create, ConflictPolicy policy, List<String> notices) throws Exception {
        if (policy == null) throw new IOException("Çakışma politikası gerekli");
        LocalDate date = LocalDate.parse(block.date()); int start=PlanningPreferences.minute(block.start()), end=PlanningPreferences.minute(block.end());
        if (start >= end || end > 1439) throw new IOException("Blok saatleri geçersiz");
        var day = new DailyAgendaService().load(root,date);
        if (!day.errors().isEmpty()) throw new IOException(String.join("; ",day.errors()));
        var prefs = PlanningPreferences.load(root);
        String blockId = block.id();
        var occupied = new ArrayList<>(day.events().stream().filter(e -> !(e.study() && e.id().equals(blockId))).toList());
        for(var todo:day.todos())if(todo.external()&&todo.module().equals("calendar")&&todo.date().equals(date))occupied.add(new DailyAgendaService.Event(todo.id(),todo.title(),date,LocalTime.MIN,LocalTime.of(23,59),"Google",false,false,"Google"));
        int requested = start;
        if (!block.done() && conflicts(occupied,prefs,date,start,end)) {
            if (policy == ConflictPolicy.REJECT) throw new IOException("CONFLICT: Bu saatte başka bir ders veya çalışma var");
            if (policy == ConflictPolicy.NEAREST) {
                int duration=end-start, best=-1, distance=Integer.MAX_VALUE;
                for (int candidate=PlanningPreferences.minute(prefs.start());candidate+duration<=Math.min(1439,PlanningPreferences.minute(prefs.end()));candidate+=15) {
                    if (!conflicts(occupied,prefs,date,candidate,candidate+duration) && Math.abs(candidate-start)<distance) { best=candidate;distance=Math.abs(candidate-start); }
                }
                if (best<0) throw new IOException("NO_SLOT: Bu gün için uygun boş aralık bulunamadı");
                start=best;end=best+duration;
                block=block.moved(block.date(),PlanningPreferences.time(start),PlanningPreferences.time(end));
                notices.add("Çakışma nedeniyle "+PlanningPreferences.time(requested)+" yerine "+block.start()+" saatine kaydırıldı · Geri al");
            } else notices.add("Saat çakışmasına izin verildi");
        }
        // Validate links even in ALLOW mode; only the overlap check is bypassed.
        var service = new DailyAgendaService(); service.saveSessionUncheckedOverlap(root, block, create);
    }
    private static boolean conflicts(List<DailyAgendaService.Event> occupied, PlanningPreferences prefs, LocalDate date, int start, int end) {
        return occupied.stream().anyMatch(e -> (!e.done() || !e.study()) && start<PlanningPreferences.minute(e.end().toString()) && end>PlanningPreferences.minute(e.start().toString()))
                || prefs.closed().stream().anyMatch(c -> (date.toString().equals(c.date()) || ((c.date()==null || c.date().isBlank()) && c.day()==date.getDayOfWeek().getValue())) && start<PlanningPreferences.minute(c.end()) && end>PlanningPreferences.minute(c.start()));
    }
}
