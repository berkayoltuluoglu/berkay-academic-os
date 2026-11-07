package org.example;

import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Reads the native records without writing or injecting demonstration data. */
public class DailyAgendaService {
    public record Event(String id, String title, LocalDate date, LocalTime start, LocalTime end,
                        String detail, boolean study, boolean done, String kind) {
        public Event(String id, String title, LocalDate date, LocalTime start, LocalTime end,
                     String detail, boolean study, boolean done) { this(id, title, date, start, end, detail, study, done, study ? "Çalışma" : "Ders"); }
    }
    public record Todo(String id, String title, LocalDate date, String detail, boolean coursework, boolean done,
                       LocalTime dueTime, String priority, boolean external, String module) {
        public Todo(String id, String title, LocalDate date, String detail, boolean coursework, boolean done) {
            this(id, title, date, detail, coursework, done, null, "Normal", false, coursework ? "courses" : "tasks");
        }
    }
    public record Day(LocalDate date, List<Event> events, List<Todo> todos,
                      List<CourseData.Course> courses, List<StudySession> sessions, List<String> errors) {}

    record Snapshot(CourseData courses,List<TaskItem> tasks,List<StudySession> sessions,
                            List<CalendarEventItem> google,ClassroomData classroom,ScholarshipData applications,List<String> errors) {}
    Snapshot readSnapshot(Path root,YearMonth month) {
        List<String> errors=new ArrayList<>();CourseData courses=CourseData.empty();List<TaskItem> tasks=List.of();List<StudySession> sessions=List.of();
        List<CalendarEventItem> google=List.of();ClassroomData classroom=ClassroomData.empty();ScholarshipData applications=ScholarshipData.empty();
        try{courses=new CourseStorageService(root.resolve("data/courses/academic.json")).load();}catch(Exception e){errors.add("Dersler: "+e.getMessage());}
        try{tasks=new TaskStorageService(root.resolve("data/tasks.json")).loadTasks();}catch(Exception e){errors.add("Görevler: "+e.getMessage());}
        try{sessions=new StudyStorageService(root.resolve("data/study/sessions.json")).load();}catch(Exception e){errors.add("Çalışma planı: "+e.getMessage());}
        try{google=new CalendarCacheStore(root).load(month);}catch(Exception e){errors.add("Google takvim kopyası: "+e.getMessage());}
        try{classroom=new GoogleClassroomService(root).loadCached();}catch(Exception e){errors.add("Classroom: "+e.getMessage());}
        try{applications=new ScholarshipStorageService(root.resolve("data/finance/applications.json")).load();}catch(Exception e){errors.add("Burs ve başvurular: "+e.getMessage());}
        return new Snapshot(courses,tasks,sessions,google,classroom,applications,List.copyOf(errors));
    }
    public Day load(Path root, LocalDate date) { return assemble(readSnapshot(root,YearMonth.from(date)),date); }
    public List<Day> loadMonth(Path root,YearMonth month) {
        return loadRange(root, month.atDay(1), month.atEndOfMonth());
    }
    /** Inclusive range: native stores once, each calendar month once. */
    public List<Day> loadRange(Path root, LocalDate start, LocalDate end) {
        return assembleRange(readRange(root, start, end), start, end);
    }
    Snapshot readRange(Path root, LocalDate start, LocalDate end) {
        if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366)
            throw new IllegalArgumentException("Tarih aralığı 1–367 gün olmalı");
        var first = readSnapshot(root, YearMonth.from(start));
        var events = new ArrayList<>(first.google()); var errors = new ArrayList<>(first.errors());
        for (var month = YearMonth.from(start).plusMonths(1); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            try { events.addAll(new CalendarCacheStore(root).load(month)); }
            catch (Exception error) { errors.add("Google " + month + ": " + error.getMessage()); }
        }
        // A multi-day Google event can appear in both month caches.
        Map<String, CalendarEventItem> unique = new LinkedHashMap<>();
        events.forEach(e -> unique.put(googleIdentity(e), e));
        return new Snapshot(first.courses(), first.tasks(), first.sessions(), List.copyOf(unique.values()), first.classroom(), first.applications(), List.copyOf(errors));
    }
    List<Day> assembleRange(Snapshot snapshot, LocalDate start, LocalDate end) {
        return start.datesUntil(end.plusDays(1)).map(date -> assemble(snapshot, date)).toList();
    }
    private Day assemble(Snapshot snapshot,LocalDate date) {
        List<Event> events=new ArrayList<>();List<Todo> todos=new ArrayList<>();List<String> errors=new ArrayList<>(snapshot.errors());
        List<CourseData.Course> courses=snapshot.courses().courses();List<StudySession> sessions=snapshot.sessions();
        try {
            var data = snapshot.courses();
            courses = data.courses();
            for (var c : courses) {
                if (!c.planned() || date.isBefore(LocalDate.parse(c.startDate())) || date.isAfter(LocalDate.parse(c.endDate()))) continue;
                for (var l : c.lessons()) if (l.day() == date.getDayOfWeek().getValue()) {
                    String room = l.room().isBlank() ? c.room() : l.room();
                    events.add(new Event(c.id(), c.name(), date, LocalTime.parse(l.start()), LocalTime.parse(l.end()),
                            room.isBlank() ? c.code() : room, false, false));
                }
            }
            Map<String, String> names = new HashMap<>(); courses.forEach(c -> names.put(c.id(), c.name()));
            for (var w : data.work()) if (!w.date().isBlank() && include(LocalDate.parse(w.date()), w.done(), date))
                todos.add(new Todo(w.id(), w.title(), LocalDate.parse(w.date()), w.kind() + " · " + names.get(w.courseId()), true, w.done(),
                        w.dueTime().isBlank() ? null : LocalTime.parse(w.dueTime()), w.priority(), false, "courses"));
        } catch (Exception ex) { errors.add("Dersler: " + ex.getMessage()); }
        try {
            for (var t : snapshot.tasks()) if (include(t.getDate(), "Tamamlandı".equals(t.getStatus()), date))
                todos.add(new Todo(t.getId(), t.getTitle(), t.getDate(), t.getDescription(), false, "Tamamlandı".equals(t.getStatus()),
                        t.getDueTime(), t.getPriority(), false, "tasks"));
        } catch (Exception ex) { errors.add("Görevler: " + ex.getMessage()); }
        try {
            sessions = snapshot.sessions();
            for (var s : sessions) if (date.equals(LocalDate.parse(s.date())))
                events.add(new Event(s.id(), s.title(), date, LocalTime.parse(s.start()), LocalTime.parse(s.end()), "Çalışma", true, s.done()));
        } catch (Exception ex) { errors.add("Çalışma planı: " + ex.getMessage()); }
        try {
            var cache = snapshot.google();
            for (var e : cache) if (e.occursOn(date)) {
                if (e.isAllDay()) todos.add(new Todo("google-" + googleIdentity(e), e.getTitle(), date, e.getSourceName()+" · Tüm gün", false, false,
                        null, "Normal", true, "calendar"));
                else events.add(new Event(googleIdentity(e), e.getTitle(), date, date.equals(e.getDate()) ? e.getStartTime() : LocalTime.MIN,
                        date.equals(e.getEndDate()) ? e.getEndTime() : LocalTime.of(23, 59), e.getSourceName()+" · " + Objects.toString(e.getLocation(), ""), false, false, "Google"));
            }
        } catch (Exception ex) { errors.add("Google takvim kopyası: " + ex.getMessage()); }
        try {
            var classroom = snapshot.classroom();
            Set<String> imported = new HashSet<>();
            snapshot.courses().work().forEach(w -> imported.add(w.id()));
            for (var w : classroom.work()) if (!w.dueDate().isBlank()
                    && !imported.contains(ClassroomImportService.workId(classroom.accountEmail(), w.courseId(), w.id()))
                    && include(LocalDate.parse(w.dueDate()), w.submitted(), date))
                todos.add(new Todo("classroom-" + w.courseId() + "-" + w.id(), w.title(), LocalDate.parse(w.dueDate()), "Classroom · " + w.state(),
                        true, w.submitted(), w.dueTime().isBlank() ? null : LocalTime.parse(w.dueTime()), "Normal", true, "classroom"));
        } catch (Exception ex) { errors.add("Classroom: " + ex.getMessage()); }
        try {
            var applications = snapshot.applications();
            for (var a : applications.applications()) {
                if (a.awaitingApplication() && !a.deadline().isBlank() && include(LocalDate.parse(a.deadline()),false,date))
                    todos.add(new Todo("application-"+a.id(), a.name(), LocalDate.parse(a.deadline()), a.kind()+" · Son başvuru · "+a.status(), false,false,null,"Yüksek",true,"finance"));
                if (a.awaitingResult() && !a.resultDate().isBlank() && include(LocalDate.parse(a.resultDate()),false,date))
                    todos.add(new Todo("application-result-"+a.id(), a.name(), LocalDate.parse(a.resultDate()), a.kind()+" · Sonuç açıklanması", false,false,null,"Normal",true,"finance"));
            }
        } catch (Exception error) { errors.add("Burs ve başvurular: "+error.getMessage()); }
        events.sort(Comparator.comparing(Event::start).thenComparing(Event::title));
        todos.sort(Comparator.comparing(Todo::done).thenComparing(Todo::date)
                .thenComparing(t -> t.dueTime() == null ? LocalTime.MAX : t.dueTime())
                .thenComparing(t -> switch (t.priority()) { case "Yüksek" -> 0; case "Düşük" -> 2; default -> 1; }).thenComparing(Todo::title));
        return new Day(date, List.copyOf(events), List.copyOf(todos), courses, sessions, List.copyOf(errors));
    }

    private static String googleIdentity(CalendarEventItem item) {
        return item.isPrimaryCalendar()?item.getId():Base64.getUrlEncoder().withoutPadding().encodeToString(item.getCalendarId().getBytes(java.nio.charset.StandardCharsets.UTF_8))+"/"+item.getId();
    }
    private static boolean include(LocalDate due, boolean done, LocalDate date) {
        return due != null && (done ? due.equals(date) : !due.isAfter(date.plusDays(7)));
    }
    public static boolean overlaps(Event a, Event b) {
        return a.date().equals(b.date()) && !a.done() && !b.done() && a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }

    public void setDone(Path root, Todo todo, boolean done) throws Exception {
        if (todo.external()) throw new IllegalArgumentException("Bu kayıt kaynak uygulamada yönetilir.");
        if (todo.coursework()) {
            var storage = new CourseStorageService(root.resolve("data/courses/academic.json"));
            var data = storage.load();
            if (data.work().stream().noneMatch(w -> w.id().equals(todo.id()))) throw new IllegalStateException("Teslim kaydı bulunamadı. Yenile.");
            storage.save(data.withWork(data.work().stream().map(w -> w.id().equals(todo.id())
                    ? w.withDone(done) : w).toList()));
        } else {
            var storage = new TaskStorageService(root.resolve("data/tasks.json"));
            var tasks = storage.loadTasks();
            var task = tasks.stream().filter(t -> t.getId().equals(todo.id())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Görev bulunamadı. Yenile."));
            task.setStatus(done ? "Tamamlandı" : "Bekliyor"); storage.saveTasks(tasks);
        }
    }

    public void saveSession(Path root, StudySession value, boolean create) throws Exception {
        saveSession(root, value, create, true);
    }
    void saveSessionUncheckedOverlap(Path root, StudySession value, boolean create) throws Exception {
        saveSession(root, value, create, false);
    }
    private void saveSession(Path root, StudySession value, boolean create, boolean rejectOverlap) throws Exception {
        var storage = new StudyStorageService(root.resolve("data/study/sessions.json"));
        var sessions = new ArrayList<>(storage.load());
        if (!value.taskId().isBlank() && new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().stream()
                .noneMatch(t -> t.getId().equals(value.taskId()))) throw new IllegalStateException("Bağlı görev artık mevcut değil");
        if (!value.workId().isBlank()) {
            var data = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
            var work = data.work().stream().filter(w -> w.id().equals(value.workId())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Bağlı ödev/sınav artık mevcut değil"));
            if (!value.courseId().isBlank() && !value.courseId().equals(work.courseId()))
                throw new IllegalStateException("Seçilen ders, bağlı teslimin dersiyle aynı olmalı");
        }
        if (!value.courseId().isBlank()) {
            var courses = new CourseStorageService(root.resolve("data/courses/academic.json")).load();
            if (courses.courses().stream().noneMatch(c -> c.id().equals(value.courseId()))) throw new IllegalStateException("Bağlı ders artık mevcut değil");
        }
        int index = -1;
        for (int i = 0; i < sessions.size(); i++) if (sessions.get(i).id().equals(value.id())) { index = i; break; }
        if (create && index == -1) sessions.add(value);
        else if (!create && index >= 0) sessions.set(index, value);
        else throw new IllegalStateException("Oturum değişti. Yenile.");
        if (!value.done() && rejectOverlap) {
            var day = load(root, LocalDate.parse(value.date()));
            if (!day.errors().isEmpty()) throw new IllegalStateException("Çakışmaları kontrol etmek için kayıtlar okunamadı: " + String.join("; ", day.errors()));
            var proposed = new Event(value.id(), value.title(), LocalDate.parse(value.date()), LocalTime.parse(value.start()), LocalTime.parse(value.end()), "", true, false);
            if (day.events().stream().filter(e -> !(e.study() && e.id().equals(value.id()))).anyMatch(e -> overlaps(e, proposed)))
                throw new IllegalStateException("Bu saatte başka bir ders veya çalışma var");
        }
        storage.save(sessions);
    }

    public void deleteSession(Path root, String id) throws Exception {
        var storage = new StudyStorageService(root.resolve("data/study/sessions.json"));
        var sessions = storage.load();
        if (sessions.stream().noneMatch(s -> s.id().equals(id))) throw new IllegalStateException("Oturum bulunamadı. Yenile.");
        storage.save(sessions.stream().filter(s -> !s.id().equals(id)).toList());
    }
}
