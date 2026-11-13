package org.example;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Supplier;
import static org.example.CourseData.*;

public final class CourseModuleView {
    private static final Locale TR = Locale.forLanguageTag("tr");
    private final VBox root = new VBox(12);
    private final VBox body = new VBox();
    private final Label status = new Label();
    private final Button reload = new Button("Yenile");
    private final CourseStorageService storage;
    private CourseData data = CourseData.empty();
    private Task<?> pending;
    private boolean saving, disposed;
    private TabPane tabs;
    private CoursePlannerView planner;
    private AdvancedCoursePlannerView advancedPlanner;
    private final Path profileRoot;

    public CourseModuleView(Path file) {
        storage = new CourseStorageService(file);
        Path parent = file.toAbsolutePath().getParent();
        profileRoot = parent.getFileName().toString().equals("courses") ? parent.getParent().getParent() : parent;
    }

    public VBox build() {
        UiTheme.decorate(reload, "refresh-cw", "flat");
        reload.setId("courses-reload");
        status.setId("courses-status");
        status.setWrapText(true);
        Button csv = button("CSV yükle", "upload", "accent", "course-import");
        Button add = button("Ders ekle", "plus", "flat", "course-add");
        csv.disableProperty().bind(reload.disableProperty()); add.disableProperty().bind(reload.disableProperty());
        csv.setOnAction(e -> CourseImportDialog.show(root, data).ifPresent(next -> {
            tabs.getSelectionModel().select(0); save(next);
        }));
        add.setOnAction(e -> editCourse(null));
        FlowPane bar = new FlowPane(12, 8, csv, add, reload, status);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        root.getChildren().setAll(bar, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        reload.setOnAction(e -> load());
        load();
        return root;
    }

    private void load() {
        if (pending != null) pending.cancel();
        run(false, storage::load);
    }

    private void save(CourseData candidate) {
        run(true, () -> { storage.save(candidate); return candidate; });
    }

    private void run(boolean write, IoAction action) {
        saving = write;
        reload.setDisable(true);
        body.setDisable(true);
        status.setText(write ? "Kaydediliyor…" : "Dersler yükleniyor…");
        Task<CourseData> task = new Task<>() {
            @Override protected CourseData call() throws Exception { return action.run(); }
        };
        pending = task;
        task.setOnSucceeded(e -> {
            if (disposed || pending != task) return;
            saving = false;
            data = task.getValue();
            render();
            body.setDisable(false); reload.setDisable(false);
            status.setText(write ? "Dersler kaydedildi" : data.courses().size() + " ders · " + data.work().size() + " ödev / sınav");
        });
        task.setOnFailed(e -> {
            if (disposed || pending != task) return;
            saving = false;
            reload.setDisable(false);
            body.setDisable(!write); // Failed reads must not permit writing an empty/stale document.
            if (planner != null) planner.update(data); // Undo an unsuccessful selection visually as well.
            status.setText((write ? "Kaydedilemedi: " : "Yüklenemedi: ") + task.getException().getMessage());
        });
        Thread.ofVirtual().name("course-storage").start(task);
    }

    public void dispose() {
        if (saving) throw new IllegalStateException("Ders kaydı sürüyor; tamamlanmasını bekle");
        if (advancedPlanner != null) advancedPlanner.dispose();
        disposed = true;
        if (pending != null) pending.cancel();
    }

    private void render() {
        if (tabs == null) {
            planner = new CoursePlannerView(this::save, this::editCourse, this::editWork);
            advancedPlanner = new AdvancedCoursePlannerView(profileRoot, this::save);
            tabs = new TabPane(); tabs.setId("courses-tabs");
            tabs.getTabs().setAll(new Tab("Planlayıcı", planner), new Tab("Ders kayıtları"), new Tab("Ödev ve sınavlar"), new Tab("Müfredat / ÇAP ve alternatifler", advancedPlanner), new Tab("Ders iptalleri"));
            tabs.getTabs().forEach(tab -> tab.setClosable(false));
            body.getChildren().setAll(tabs); VBox.setVgrow(tabs, Priority.ALWAYS);
        }
        planner.update(data);
        advancedPlanner.update(data);
        tabs.getTabs().get(1).setContent(courseList());
        tabs.getTabs().get(2).setContent(workList());
        tabs.getTabs().get(4).setContent(cancellationList());
    }

    private Node courseList() {
        ListView<Course> list = new ListView<>();
        list.setId("course-list");
        list.getStyleClass().add("course-detail-list");
        list.getItems().setAll(data.courses());
        list.setPlaceholder(new Label("İlk dersini ekleyerek başla"));
        list.setCellFactory(v -> new ListCell<>() {
            { setWrapText(true); setMinHeight(Region.USE_PREF_SIZE); }
            @Override protected void updateItem(Course c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c + "\n" + c.instructor()
                        + (c.section().isBlank() ? "" : " · Şube: " + c.section())
                        + " · " + amount(c.credit()) + " kredi / " + amount(c.ects()) + " AKTS"
                        + (c.planned() ? "" : " · Program dışında")
                        + "\n" + c.startDate() + " — " + c.endDate());
            }
        });
        Button edit = button("Düzenle", "edit-2", "flat", "course-edit");
        Button remove = button("Sil", "trash-2", "danger", "course-delete");
        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> editCourse(list.getSelectionModel().getSelectedItem()));
        remove.setOnAction(e -> {
            Course selected = list.getSelectionModel().getSelectedItem();
            long count = data.work().stream().filter(w -> w.courseId().equals(selected.id())).count();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    selected.name() + " dersi, programı ve bağlı " + count + " ödev/sınav silinsin mi?", ButtonType.CANCEL, ButtonType.OK);
            UiTheme.styleDialog(confirm, root);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                save(new CourseData(VERSION, data.courses().stream().filter(c -> !c.id().equals(selected.id())).toList(),
                        data.work().stream().filter(w -> !w.courseId().equals(selected.id())).toList(), data.sections(),
                        data.cancellations().stream().filter(c -> !c.courseId().equals(selected.id())).toList()));
        });
        VBox box = new VBox(12, new FlowPane(8, 8, edit, remove), list);
        box.getStyleClass().add("course-tab");
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    private void editCourse(Course old) {
        TextField name = field("Ders adı", old == null ? "" : old.name(), "course-name");
        TextField code = field("Ders kodu", old == null ? "" : old.code(), "course-code");
        TextField instructor = field("Öğretim elemanı", old == null ? "" : old.instructor(), "course-instructor");
        TextField room = field("Derslik / bağlantı", old == null ? "" : old.room(), "course-room");
        TextField section = field("Örn. EE201.1", old == null ? "" : old.section(), "course-section");
        TextField credit = field("Bilinmiyorsa boş bırak", old == null || old.credit() == null ? "" : amount(old.credit()), "course-credit");
        TextField ects = field("Bilinmiyorsa boş bırak", old == null || old.ects() == null ? "" : amount(old.ects()), "course-ects");
        CheckBox planned = new CheckBox("Haftalık programda göster"); planned.setSelected(old == null || old.planned()); planned.setId("course-planned");
        DatePicker start = new DatePicker(old == null ? LocalDate.now() : LocalDate.parse(old.startDate()));
        DatePicker end = new DatePicker(old == null ? LocalDate.now().plusMonths(4) : LocalDate.parse(old.endDate()));
        start.setId("course-start"); end.setId("course-end");
        ListView<Lesson> lessons = new ListView<>();
        lessons.setId("course-lessons");
        lessons.setPrefHeight(105);
        if (old != null) lessons.getItems().setAll(old.lessons());
        lessons.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Lesson l, boolean empty) {
                super.updateItem(l, empty);
                setText(empty || l == null ? null : dayName(l.day()) + " · " + l.start() + "–" + l.end()
                        + (l.room().isBlank() ? "" : " · " + l.room()));
            }
        });
        ComboBox<String> day = new ComboBox<>();
        for (int i = 1; i <= 7; i++) day.getItems().add(dayName(i));
        day.getSelectionModel().selectFirst(); day.setId("lesson-day");
        TextField from = field("09:00", "09:00", "lesson-start"); from.setPrefWidth(80);
        TextField to = field("10:00", "10:00", "lesson-end"); to.setPrefWidth(80);
        TextField lessonRoom = field("Oturuma özel derslik", "", "lesson-room"); lessonRoom.setPrefWidth(160);
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error");
        Button add = new Button("Saat ekle"); add.setId("lesson-add");
        Button remove = new Button("Saati kaldır");
        remove.disableProperty().bind(lessons.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> lessons.getItems().remove(lessons.getSelectionModel().getSelectedItem()));
        add.setOnAction(e -> {
            try {
                LocalTime a = LocalTime.parse(from.getText().trim()), b = LocalTime.parse(to.getText().trim());
                if (!b.isAfter(a)) throw new IllegalArgumentException();
                Lesson lesson = new Lesson(day.getSelectionModel().getSelectedIndex() + 1, a.toString(), b.toString(), lessonRoom.getText().trim());
                if (lessons.getItems().stream().anyMatch(l -> l.day() == lesson.day() && l.start().equals(lesson.start()) && l.end().equals(lesson.end()))) throw new IllegalArgumentException();
                lessons.getItems().add(lesson); error.setText("");
            } catch (Exception ex) { error.setText("Saatleri 09:00 biçiminde gir; bitiş daha geç olmalı ve oturum tekrarlanmamalı."); }
        });
        VBox form = new VBox(8, new Label("Ders adı *"), name, new Label("Ders kodu"), code,
                new Label("Öğretim elemanı"), instructor, new Label("Derslik / bağlantı"), room,
                new Label("Şube"), section, new Label("Kredi / AKTS (boş = bilinmiyor)"), new FlowPane(8, 8, credit, ects),
                new Label("Dönem başlangıcı ve bitişi *"), new FlowPane(8, 8, start, end),
                new Label("Haftalık saatler (isteğe bağlı)"), new FlowPane(8, 8, day, from, to, lessonRoom, add, remove), lessons, planned, error);
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        formDialog(old == null ? "Ders ekle" : "Dersi düzenle", form, error, () -> {
            Course course = new Course(id, name.getText().trim(), code.getText().trim(), instructor.getText().trim(),
                    room.getText().trim(), Objects.requireNonNull(start.getValue(), "Başlangıç tarihi gerekiyor").toString(),
                    Objects.requireNonNull(end.getValue(), "Bitiş tarihi gerekiyor").toString(), List.copyOf(lessons.getItems()),
                    section.getText().trim(), parseAmount(credit.getText()), parseAmount(ects.getText()), "", planned.isSelected());
            List<Course> next = new ArrayList<>(data.courses());
            next.removeIf(c -> c.id().equals(id)); next.add(course);
            return data.withCourses(next);
        }).ifPresent(this::save);
    }

    private Node workList() {
        ListView<Work> list = new ListView<>();
        list.setId("course-work-list");
        list.getItems().setAll(data.work().stream().sorted(Comparator.comparing(Work::done).thenComparing(Work::date)).toList());
        list.setPlaceholder(new Label("Henüz ödev veya sınav yok"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Work w, boolean empty) {
                super.updateItem(w, empty);
                setText(empty || w == null ? null : (w.done() ? "✓ " : "") + w.title() + "\n" +
                        courseName(w.courseId()) + " · " + w.kind() + " · " + (w.date().isBlank() ? "Son tarih yok" : w.date())
                        + (w.dueTime().isBlank() ? "" : " " + w.timeLabel()) + " · " + w.priority());
            }
        });
        Button add = button("Ödev / sınav ekle", "plus", "accent", "work-add");
        Button edit = button("Düzenle", "edit-2", "flat", "work-edit");
        Button done = button("Tamamlandı / geri al", "check", "flat", "work-done");
        Button remove = button("Sil", "trash-2", "danger", "work-delete");
        add.setDisable(data.courses().isEmpty());
        for (Button b : List.of(edit, done, remove)) b.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(e -> editWork(null));
        edit.setOnAction(e -> editWork(list.getSelectionModel().getSelectedItem()));
        done.setOnAction(e -> {
            Work selected = list.getSelectionModel().getSelectedItem();
            List<Work> next = data.work().stream().map(w -> w.id().equals(selected.id())
                    ? w.withDone(!w.done()) : w).toList();
            save(data.withWork(next));
        });
        remove.setOnAction(e -> {
            Work selected = list.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, selected.title() + " silinsin mi?", ButtonType.CANCEL, ButtonType.OK);
            UiTheme.styleDialog(confirm, root);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                save(data.withWork(data.work().stream().filter(w -> !w.id().equals(selected.id())).toList()));
        });
        VBox box = new VBox(12, new FlowPane(8, 8, add, edit, done, remove), list);
        box.getStyleClass().add("course-tab");
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    private void editWork(Work old) {
        ComboBox<Course> course = new ComboBox<>(); course.getItems().setAll(data.courses()); course.setId("work-course");
        course.setMaxWidth(Double.MAX_VALUE);
        course.setValue(old == null ? data.courses().getFirst() : data.courses().stream().filter(c -> c.id().equals(old.courseId())).findFirst().orElseThrow());
        TextField title = field("Başlık", old == null ? "" : old.title(), "work-title");
        ComboBox<String> kind = new ComboBox<>(); kind.getItems().setAll("Ödev", "Sınav"); kind.setValue(old == null ? "Ödev" : old.kind());
        kind.setId("work-kind");
        DatePicker date = new DatePicker(old == null ? LocalDate.now() : old.date().isBlank() ? null : LocalDate.parse(old.date())); date.setId("work-date");
        CheckBox noDue = new CheckBox("Son tarih yok"); noDue.setSelected(old != null && old.date().isBlank()); noDue.setId("work-no-due");
        date.disableProperty().bind(noDue.selectedProperty());
        TextField examStart = field("SS:DD", old == null || old.dueTime().isBlank() ? "09:00" : old.dueTime(), "work-start-time");
        TextField examEnd = field("SS:DD", old == null ? "10:00" : old.endTime(), "work-end-time");
        VBox examTimes = new VBox(8, label("Sınav başlangıç saati *", examStart), examStart,
                label("Sınav bitiş saati *", examEnd), examEnd,
                new Label("Aynı gün içinde, örn. 15:00–16:20. Çalışmalar sınav başlangıcından önce planlanır."));
        CheckBox done = new CheckBox("Tamamlandı"); done.setSelected(old != null && old.done());
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error");
        error.setId("work-form-error");
        TaskMetadataEditor metadata = new TaskMetadataEditor(false);
        Runnable updateKind = () -> {
            boolean exam = "Sınav".equals(kind.getValue());
            examTimes.setVisible(exam); examTimes.setManaged(exam); metadata.showDueTime(!exam);
            noDue.setVisible(!exam); noDue.setManaged(!exam);
            if (exam) noDue.setSelected(false);
        };
        kind.valueProperty().addListener((o, a, b) -> updateKind.run()); updateKind.run();
        if (old != null) {
            TaskItem fields = new TaskItem(old.title(), old.date().isBlank() ? LocalDate.now() : LocalDate.parse(old.date()), "", "Bekliyor");
            fields.setDueTime(old.dueTime().isBlank() ? null : LocalTime.parse(old.dueTime()));
            fields.setPriority(old.priority()); fields.setEstimatedMinutes(old.estimatedMinutes()); fields.setLoggedMinutes(old.loggedMinutes());
            metadata.load(fields);
            try { metadata.showProgress(new StudyProgressService().forWork(old,
                    new StudyStorageService(profileRoot.resolve("data/study/sessions.json")).load(), new StudyTimeLogService(profileRoot).load())); }
            catch (Exception failure) { status.setText("Çalışma süresi okunamadı: " + failure.getMessage()); }
        }
        VBox form = new VBox(8, label("Ders *", course), course, label("Başlık *", title), title,
                label("Tür", kind), kind, label("Teslim / sınav tarihi", date), date, noDue, examTimes, metadata, done, error);
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        formDialog(old == null ? "Ödev / sınav ekle" : "Kaydı düzenle", form, error, () -> {
            date.commitValue();
            boolean exam = "Sınav".equals(kind.getValue());
            TaskItem fields = new TaskItem(title.getText().trim(), date.getValue(), "", "Bekliyor");
            metadata.apply(fields);
            if (exam && (examStart.getText().isBlank() || examEnd.getText().isBlank()))
                throw new IllegalArgumentException("Sınav başlangıç ve bitiş saatini SS:DD biçiminde gir");
            Work work = new Work(id, course.getValue().id(), title.getText().trim(), kind.getValue(),
                    noDue.isSelected() ? "" : Objects.requireNonNull(date.getValue(), "Tarih gerekiyor").toString(), done.isSelected(),
                    exam ? examStart.getText().trim() : fields.getDueTime() == null ? "" : fields.getDueTime().toString(),
                    fields.getEstimatedMinutes(), fields.getLoggedMinutes(), fields.getPriority(), exam ? examEnd.getText().trim() : "");
            List<Work> next = new ArrayList<>(data.work()); next.removeIf(w -> w.id().equals(id)); next.add(work);
            return data.withWork(next);
        }).ifPresent(this::save);
    }

    private Optional<CourseData> formDialog(String title, VBox form, Label error, Supplier<CourseData> candidate) {
        Dialog<CourseData> dialog = new Dialog<>(); dialog.setTitle(title); UiTheme.styleDialog(dialog, root);
        dialog.setResizable(true);
        ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(550); scroll.setPrefViewportHeight(Math.min(610, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 190));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(scroll);
        ButtonType save = new ButtonType("Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save); saveButton.setId("course-form-save");
        final CourseData[] accepted = new CourseData[1];
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try { accepted[0] = candidate.get(); CourseStorageService.validate(accepted[0]); }
            catch (Exception ex) { error.setText(ex.getMessage()); e.consume(); }
        });
        dialog.setResultConverter(type -> type == save ? accepted[0] : null);
        dialog.setOnShown(e -> javafx.application.Platform.runLater(() -> {
            Node first = form.lookup("#work-title");
            if (first == null) first = form.lookup(".text-field");
            if (first != null) first.requestFocus();
        }));
        return dialog.showAndWait();
    }

    private Node cancellationList() {
        ListView<LessonCancellation> list = new ListView<>(); list.setId("course-cancellation-list");
        list.getItems().setAll(data.cancellations().stream().sorted(Comparator.comparing(LessonCancellation::fromDate)).toList());
        list.setPlaceholder(new Label("Ders iptali yok"));
        list.setCellFactory(v -> new ListCell<>() {
            { setWrapText(true); }
            @Override protected void updateItem(LessonCancellation c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.fromDate() + " — " + c.toDate() + " · "
                        + (c.courseId().isEmpty() ? "Tüm dersler" : courseName(c.courseId()))
                        + (c.reason().isBlank() ? "" : "\n" + c.reason()));
            }
        });
        Button add = button("Tarih aralığında ders iptal et", "calendar", "accent", "cancellation-add");
        Button edit = button("Düzenle", "edit-2", "flat", "cancellation-edit");
        Button restore = button("İptali kaldır", "rotate-ccw", "flat", "cancellation-remove");
        add.setDisable(data.courses().isEmpty());
        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        restore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(e -> editCancellation(null));
        edit.setOnAction(e -> editCancellation(list.getSelectionModel().getSelectedItem()));
        restore.setOnAction(e -> {
            var selected = list.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bu tarih aralığındaki ders iptali kaldırılsın mı? Diğer iptaller geçerliyse korunur.", ButtonType.CANCEL, ButtonType.OK);
            UiTheme.styleDialog(confirm, root);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                save(data.withCancellations(data.cancellations().stream().filter(c -> !c.id().equals(selected.id())).toList()));
        });
        Label explanation = new Label("Başlangıç ve bitiş günleri dahildir. Yalnızca haftalık ders saatleri iptal edilir; sınavlar ve Google etkinlikleri korunur. Diğer haftalar değişmez.");
        explanation.setWrapText(true);
        VBox box = new VBox(12, explanation, new FlowPane(8, 8, add, edit, restore), list);
        box.getStyleClass().add("course-tab"); VBox.setVgrow(list, Priority.ALWAYS); return box;
    }

    private void editCancellation(LessonCancellation old) {
        ComboBox<String> course = new ComboBox<>(); course.setId("cancellation-course");
        course.getItems().add("Tüm dersler"); data.courses().forEach(c -> course.getItems().add(c.toString()));
        int selected = 0;
        if (old != null && !old.courseId().isEmpty())
            for (int i = 0; i < data.courses().size(); i++) if (data.courses().get(i).id().equals(old.courseId())) selected = i + 1;
        course.getSelectionModel().select(selected); course.setMaxWidth(Double.MAX_VALUE);
        DatePicker from = new DatePicker(old == null ? LocalDate.now() : LocalDate.parse(old.fromDate())); from.setId("cancellation-from");
        DatePicker to = new DatePicker(old == null ? LocalDate.now() : LocalDate.parse(old.toDate())); to.setId("cancellation-to");
        TextField reason = field("Örn. vize haftası", old == null ? "" : old.reason(), "cancellation-reason");
        Label error = new Label(); error.setId("cancellation-error"); error.setWrapText(true); error.getStyleClass().add("form-error");
        VBox form = new VBox(8, label("Kapsam", course), course, label("İlk gün (dahil)", from), from,
                label("Son gün (dahil)", to), to, label("Açıklama", reason), reason, error);
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        formDialog("Tarih bazlı ders iptali", form, error, () -> {
            from.commitValue(); to.commitValue();
            int index = course.getSelectionModel().getSelectedIndex();
            var cancellation = new LessonCancellation(id, index == 0 ? "" : data.courses().get(index - 1).id(),
                    Objects.requireNonNull(from.getValue(), "İlk gün gerekiyor").toString(),
                    Objects.requireNonNull(to.getValue(), "Son gün gerekiyor").toString(), reason.getText().trim());
            List<LessonCancellation> next = new ArrayList<>(data.cancellations()); next.removeIf(c -> c.id().equals(id)); next.add(cancellation);
            return data.withCancellations(next);
        }).ifPresent(this::save);
    }

    private static Label label(String text, javafx.scene.control.Control control) {
        Label label = new Label(text); label.setLabelFor(control); control.setAccessibleText(text); return label;
    }

    static String sectionDescription(Section s) {
        return s.fullCode() + " · " + s.name() + "\n" + s.instructor() + " · " + amount(s.credit())
                + " kredi / " + amount(s.ects()) + " AKTS\n" + lessonDescription(s.lessons());
    }
    static String lessonDescription(List<Lesson> lessons) {
        return lessons.isEmpty() ? "Saat bilgisi yok" : lessons.stream().map(l -> dayName(l.day()) + " " + l.start() + "–" + l.end()
                + (l.room().isBlank() ? "" : " [" + l.room() + "]")).collect(java.util.stream.Collectors.joining(" · "));
    }
    private static String amount(java.math.BigDecimal value) { return value == null ? "?" : value.toPlainString(); }
    private static java.math.BigDecimal parseAmount(String value) {
        try { return CourseCsvImporter.number(value.trim()); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException(ex.getMessage()); }
    }

    private String courseName(String id) { return data.courses().stream().filter(c -> c.id().equals(id)).map(Course::name).findFirst().orElse(""); }
    private static String dayName(int day) { return DayOfWeek.of(day).getDisplayName(TextStyle.FULL, TR); }
    private static TextField field(String prompt, String value, String id) { TextField f = new TextField(value); f.setPromptText(prompt); f.setId(id); return f; }
    private static Button button(String label, String icon, String style, String id) { Button b = new Button(label); UiTheme.decorate(b, icon, style); b.setId(id); return b; }
    @FunctionalInterface private interface IoAction { CourseData run() throws Exception; }
}
