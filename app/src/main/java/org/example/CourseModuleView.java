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

    public CourseModuleView(Path file) { storage = new CourseStorageService(file); }

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
        disposed = true;
        if (pending != null) pending.cancel();
    }

    private void render() {
        if (tabs == null) {
            planner = new CoursePlannerView(this::save, this::editCourse);
            tabs = new TabPane(); tabs.setId("courses-tabs");
            tabs.getTabs().setAll(new Tab("Planlayıcı", planner), new Tab("Ders kayıtları"), new Tab("Ödev ve sınavlar"));
            tabs.getTabs().forEach(tab -> tab.setClosable(false));
            body.getChildren().setAll(tabs); VBox.setVgrow(tabs, Priority.ALWAYS);
        }
        planner.update(data);
        tabs.getTabs().get(1).setContent(courseList());
        tabs.getTabs().get(2).setContent(workList());
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
            confirm.initOwner(root.getScene().getWindow());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                save(new CourseData(VERSION, data.courses().stream().filter(c -> !c.id().equals(selected.id())).toList(),
                        data.work().stream().filter(w -> !w.courseId().equals(selected.id())).toList(), data.sections()));
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
                        courseName(w.courseId()) + " · " + w.kind() + " · " + w.date());
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
                    ? new Work(w.id(), w.courseId(), w.title(), w.kind(), w.date(), !w.done()) : w).toList();
            save(data.withWork(next));
        });
        remove.setOnAction(e -> {
            Work selected = list.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, selected.title() + " silinsin mi?", ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(root.getScene().getWindow());
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
        DatePicker date = new DatePicker(old == null ? LocalDate.now() : LocalDate.parse(old.date())); date.setId("work-date");
        CheckBox done = new CheckBox("Tamamlandı"); done.setSelected(old != null && old.done());
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error");
        VBox form = new VBox(8, new Label("Ders *"), course, new Label("Başlık *"), title,
                new Label("Tür"), kind, new Label("Teslim / sınav tarihi *"), date, done, error);
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        formDialog(old == null ? "Ödev / sınav ekle" : "Kaydı düzenle", form, error, () -> {
            Work work = new Work(id, course.getValue().id(), title.getText().trim(), kind.getValue(),
                    Objects.requireNonNull(date.getValue(), "Tarih gerekiyor").toString(), done.isSelected());
            List<Work> next = new ArrayList<>(data.work()); next.removeIf(w -> w.id().equals(id)); next.add(work);
            return data.withWork(next);
        }).ifPresent(this::save);
    }

    private Optional<CourseData> formDialog(String title, VBox form, Label error, Supplier<CourseData> candidate) {
        Dialog<CourseData> dialog = new Dialog<>(); dialog.setTitle(title); dialog.initOwner(root.getScene().getWindow());
        ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(550); scroll.setPrefViewportHeight(Math.min(610, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 190));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().getStylesheets().add(CourseModuleView.class.getResource("/ui/style.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("uygulama");
        if (root.getScene().getRoot().getStyleClass().contains("dark")) dialog.getDialogPane().getStyleClass().add("dark");
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
        return dialog.showAndWait();
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
