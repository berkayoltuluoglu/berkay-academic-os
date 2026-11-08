package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** The silver home screen displays and edits native application records. */
public final class TodayModuleView {
    private static final Locale TR = Locale.forLanguageTag("tr");
    private record Loaded(DailyAgendaService.Day day, TodayService.Snapshot summary, String focusWarning, List<PlanningTarget> targets) {}
    private final Path projectRoot;
    private final Consumer<String> navigate;
    private final FocusTimerService focus;
    private final boolean agenda;
    private final DailyAgendaService service = new DailyAgendaService();
    private final PlannerService planner;
    private final VBox root = new VBox(20), content = new VBox(24), flow = new VBox(24), rail = new VBox(18);
    private final GridPane columns = new GridPane();
    private final Label status = new Label(), dateLabel = new Label(), timerLabel = new Label(), focusHint = new Label();
    private final Button refresh = new Button("Yenile"), focusToggle = new Button("Başlat"), focusReset = new Button("Sıfırla");
    private final Arc progressArc = new Arc(0, 0, 84, 84, 90, -360);
    private final DailyTimelineView timeline = new DailyTimelineView(this::openEvent, this::moveEvent);
    private final Timeline pulse = new Timeline(new KeyFrame(Duration.seconds(1), e -> pulse()));
    private Task<Loaded> pending;
    private DailyAgendaService.Day day;
    private LocalDate displayedDate = LocalDate.now();
    private boolean disposed, saving, stacked;
    private int lastMinute = -1;
    private ComboBox<PlanningTarget> focusTarget;
    private List<PlanningTarget> targets=List.of();

    public TodayModuleView(Path root, Consumer<String> navigate) {
        this(root, navigate, new FocusTimerService(root.resolve("data/focus.properties")), false);
    }
    TodayModuleView(Path root, Consumer<String> navigate, FocusTimerService focus, boolean agenda) {
        this.planner = new PlannerService(root); this.projectRoot = root; this.navigate = navigate; this.focus = focus; this.agenda = agenda;
    }

    public VBox build() {
        root.setId("silver-today"); root.getStyleClass().add("silver-today"); root.setMinWidth(0);
        Label title = new Label(agenda ? "Ajanda" : "Bugün"); title.getStyleClass().add("silver-page-title");
        dateLabel.getStyleClass().add("silver-date");
        Region gap = new Region(); HBox.setHgrow(gap, Priority.ALWAYS);
        UiTheme.iconOnly(refresh, "refresh-cw"); refresh.setId("today-refresh"); refresh.setOnAction(e -> refresh());
        MenuButton add = new MenuButton(); add.setGraphic(UiTheme.icon("plus")); add.setId("today-add");
        add.setAccessibleText("Görev veya çalışma oturumu ekle"); add.getStyleClass().add("silver-add");
        MenuItem task = new MenuItem("Görev"), session = new MenuItem("Çalışma oturumu");
        task.setOnAction(e -> addTask()); session.setOnAction(e -> sessionDialog(null)); add.getItems().setAll(task, session);
        HBox heading = new HBox(10, new VBox(5, title, dateLabel), gap, refresh, add); heading.setAlignment(Pos.CENTER_LEFT);
        columns.setHgap(24); columns.setVgap(24); columns.setMinWidth(0); flow.setMinWidth(0); rail.setMinWidth(0);
        ColumnConstraints main = new ColumnConstraints(); main.setHgrow(Priority.ALWAYS); main.setMinWidth(0);
        columns.getColumnConstraints().add(main);
        content.getChildren().setAll(columns); content.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setMinWidth(0); VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.viewportBoundsProperty().addListener((o, a, b) -> arrange(b.getWidth() < 760));
        status.setId("today-status"); status.setWrapText(true); status.getStyleClass().add("soluk");
        status.managedProperty().bind(status.textProperty().isNotEmpty()); status.visibleProperty().bind(status.managedProperty());
        root.getChildren().setAll(heading, scroll, status);
        if (agenda) root.getChildren().add(1, dateNavigation());
        arrange(false); pulse.setCycleCount(Timeline.INDEFINITE); pulse.play(); refresh(); return root;
    }

    private Node dateNavigation() {
        Button prev = new Button("Önceki gün"), next = new Button("Sonraki gün"), today = new Button("Bugün");
        UiTheme.iconOnly(prev, "chevron-left"); UiTheme.iconOnly(next, "chevron-right");
        DatePicker picker = new DatePicker(displayedDate); picker.setId("agenda-date");
        picker.valueProperty().addListener((o, a, b) -> { if (b != null && !saving) { displayedDate = b; refresh(); } });
        prev.setOnAction(e -> picker.setValue(displayedDate.minusDays(1)));
        next.setOnAction(e -> picker.setValue(displayedDate.plusDays(1)));
        today.setOnAction(e -> picker.setValue(LocalDate.now()));
        return new HBox(8, prev, today, next, picker);
    }

    private void arrange(boolean value) {
        if (!columns.getChildren().isEmpty() && stacked == value) return;
        stacked = value; columns.getChildren().clear();
        columns.getColumnConstraints().removeIf(c -> c.getHgrow() != Priority.ALWAYS);
        columns.add(flow, 0, 0);
        if (stacked) { rail.setMaxWidth(Double.MAX_VALUE); columns.add(rail, 0, 1); }
        else {
            ColumnConstraints side = new ColumnConstraints(280, 300, 320); columns.getColumnConstraints().add(side);
            rail.setMaxWidth(320); columns.add(rail, 1, 0);
        }
    }

    private Loaded load(LocalDate date) {
        String warning = null;
        try { focus.creditCompleted(); } catch (Exception failure) { warning = "Odak kaydı: " + failure.getMessage(); }
        List<PlanningTarget> loadedTargets=List.of();
        try{loadedTargets=PlanningTarget.load(projectRoot);}catch(Exception error){warning=error.getMessage();}
        return new Loaded(service.load(projectRoot, date), new TodayService().load(projectRoot, date), warning,loadedTargets);
    }
    private void refresh() {
        if (disposed || saving) return;
        if (pending != null) pending.cancel();
        run(null);
    }
    void refreshFromBackground() { refresh(); }
    private void mutate(IoAction action) { if (!saving && !disposed) run(action); }
    private void run(IoAction action) {
        saving = action != null; refresh.setDisable(true); root.setDisable(saving);
        status.setText(saving ? "Kaydediliyor…" : "Yükleniyor…");
        LocalDate date = displayedDate;
        Task<Loaded> task = new Task<>() {
            @Override protected Loaded call() throws Exception { if (action != null) action.run(); return load(date); }
        };
        pending = task;
        task.setOnSucceeded(e -> {
            if (disposed || pending != task) return;
            saving = false; root.setDisable(false); refresh.setDisable(false);
            day = task.getValue().day(); targets=task.getValue().targets(); render(task.getValue());
            var errors = new ArrayList<>(day.errors());
            if (task.getValue().focusWarning() != null) errors.add(task.getValue().focusWarning());
            if (task.getValue().summary().payments().error() != null) errors.add(task.getValue().summary().payments().error());
            status.setText(String.join(" · ", errors));
        });
        task.setOnFailed(e -> {
            if (disposed || pending != task) return;
            saving = false; root.setDisable(false); refresh.setDisable(false);
            // Restore controls from the last successfully loaded records.
            if (day != null) renderTodos(); updateFocus();
            status.setText("Kaydedilemedi: " + task.getException().getMessage());
        });
        Thread.ofVirtual().name("silver-agenda").start(task);
    }

    private void render(Loaded loaded) {
        dateLabel.setText(displayedDate.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", TR)));
        var dues = deadlines(); timeline.onDeadline(this::openDeadline);
        timeline.update(displayedDate, day.events(), dues);
        flow.getChildren().setAll(nextEvent(), dueBanner(dues), timeline);
        if (!day.events().isEmpty() && day.events().stream().anyMatch(a -> day.events().stream().anyMatch(b -> a != b && DailyAgendaService.overlaps(a, b)))) {
            Label warning = new Label("Programda saat çakışması var"); warning.getStyleClass().add("form-error"); flow.getChildren().add(1, warning);
        }
        TitledPane reminders = new TitledPane("Yaklaşan teslimler ve ödemeler", reminders(loaded.summary()));
        reminders.setExpanded(false); reminders.getStyleClass().add("silver-reminders"); flow.getChildren().add(reminders);
        rail.getChildren().setAll(focusCard()); renderTodos(); updateFocus();
    }

    private Node nextEvent() {
        LocalTime now = displayedDate.equals(LocalDate.now()) ? LocalTime.now() : LocalTime.MIN;
        var next = day.events().stream().filter(e -> !e.done() && e.end().isAfter(now)).findFirst();
        Label tag = new Label(next.filter(e -> !e.start().isAfter(now)).isPresent() ? "Şimdi" : "Sıradaki");
        tag.getStyleClass().add("soluk");
        var dot = new Circle(3); dot.getStyleClass().add("silver-blue-dot");
        Label name = new Label(next.map(DailyAgendaService.Event::title).orElse("Günün geri kalanı sana ait"));
        name.getStyleClass().add("silver-event-name");
        Label detail = new Label(next.map(e -> e.start() + " · " + e.detail()).orElse("Bir çalışma oturumu planlayabilirsin")); detail.getStyleClass().add("soluk");
        VBox text = new VBox(4, name, detail); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(14, dot, tag, new Separator(javafx.geometry.Orientation.VERTICAL), text, UiTheme.icon("chevron-right"));
        row.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button(); button.setId("today-next"); button.setGraphic(row); button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("silver-next"); button.setAccessibleText(name.getText());
        button.setOnAction(e -> { if (next.isPresent()) openEvent(next.get()); else sessionDialog(null); });
        return button;
    }

    private VBox focusCard() {
        Label title = new Label("Odak"); title.getStyleClass().add("silver-section-title");
        var track = new Circle(100, 100, 84); track.getStyleClass().add("focus-track");
        progressArc.setCenterX(100); progressArc.setCenterY(100);
        progressArc.setType(ArcType.OPEN); progressArc.getStyleClass().add("focus-progress");
        timerLabel.setId("focus-time"); timerLabel.getStyleClass().add("focus-time");
        Pane graphics = new Pane(track, progressArc); graphics.setMinSize(200, 200); graphics.setMaxSize(200, 200);
        StackPane ring = new StackPane(graphics, timerLabel); ring.setPrefHeight(200); ring.setMinHeight(200);
        focusToggle.setId("focus-toggle"); focusToggle.getStyleClass().add("silver-focus-button");
        focusToggle.setOnAction(e -> mutate(focus::toggle));
        UiTheme.iconOnly(focusReset, "rotate-ccw"); focusReset.setId("focus-reset"); focusReset.setOnAction(e -> mutate(focus::reset));
        HBox buttons = new HBox(8, focusToggle, focusReset); buttons.setAlignment(Pos.CENTER);
        focusHint.getStyleClass().add("soluk"); focusHint.setWrapText(true);
        focusTarget = new ComboBox<>(); focusTarget.setId("focus-target"); focusTarget.setPromptText("Çalışma hedefi seç"); focusTarget.setMaxWidth(Double.MAX_VALUE);
        focusTarget.getItems().add(null);
        focusTarget.getItems().addAll(targets);
        focusTarget.getItems().stream().filter(Objects::nonNull).filter(t -> t.id().equals(focus.targetId())
                && (t.kind().equals("Görev") ? "task" : "work").equals(focus.targetKind())).findFirst().ifPresent(focusTarget::setValue);
        focusTarget.valueProperty().addListener((observable, previous, value) -> mutate(() -> focus.selectTarget(
                value == null ? "" : value.kind().equals("Görev") ? "task" : "work", value == null ? "" : value.id())));
        VBox box = new VBox(16, title, ring, focusTarget, buttons, focusHint);
        if(day!=null)day.sessions().stream().filter(s->s.date().equals(displayedDate.toString())&&!s.done()&&LocalTime.parse(s.end()).isAfter(LocalTime.now())).sorted(Comparator.comparing(StudySession::start)).findFirst().ifPresent(s->{
            Button startBlock=new Button(s.start()+" "+s.title()+" — Başla");startBlock.setId("focus-next-block");startBlock.setWrapText(true);
            startBlock.setOnAction(e->{Task<Void> startTask=new Task<>(){@Override protected Void call()throws Exception{focus.startSession(s.id(),Math.min(180,Math.max(1,PlanningPreferences.minute(s.end())-PlanningPreferences.minute(s.start())-s.actualMinutes())));return null;}};startTask.setOnSucceeded(event->new FocusModeView(projectRoot,focus).show(root.getScene().getWindow()));startTask.setOnFailed(event->status.setText(startTask.getException().getMessage()));Thread.ofVirtual().start(startTask);});box.getChildren().add(startBlock);
        }); box.getStyleClass().addAll("panel", "silver-focus-card");
        return box;
    }

    private void updateFocus() {
        long seconds = (focus.remainingMillis() + 999) / 1000;
        timerLabel.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        progressArc.setLength(-360.0 * focus.remainingMillis() / focus.durationMillis());
        focusToggle.setText(focus.running() ? "Duraklat" : seconds == 0 ? "Yeniden başlat" : "Başlat");
        if (focusTarget != null) focusTarget.setDisable(focus.running() || focus.remainingMillis() != focus.durationMillis());
        focusHint.setText(focus.error() != null ? focus.error() : seconds == 0 ? "Odak oturumu tamamlandı" : focus.running() ? "Şimdi tek bir işe odaklan" : focus.durationMillis() / 60000 + " dakika, tek bir iş");
    }

    private void renderTodos() {
        if (rail.getChildren().size() > 1) rail.getChildren().remove(1, rail.getChildren().size());
        Label title = new Label("Yapılacaklar"); title.getStyleClass().add("silver-section-title");
        VBox box = new VBox(4, title); box.getStyleClass().addAll("panel", "silver-todo-card");
        if (day.todos().isEmpty()) { Label empty = new Label("Bugün yapılacak bir şey yok"); empty.getStyleClass().add("soluk"); box.getChildren().add(empty); }
        for (var todo : day.todos().stream().limit(8).toList()) {
            CheckBox check = new CheckBox(); check.setSelected(todo.done());
            check.setDisable(todo.external());
            check.setId("todo-" + (todo.coursework() ? "work-" : "task-") + todo.id());
            check.setAccessibleText(todo.title() + " tamamlandı");
            check.setOnAction(e -> mutate(() -> planner.execute(new PlannerCommand.CompleteItem(todo.coursework() ? "work" : "task", todo.id(), check.isSelected()))));
            Label name = new Label(todo.title()); name.setMaxWidth(Double.MAX_VALUE);
            name.getStyleClass().add(todo.done() ? "silver-todo-done" : "silver-todo-name");
            VBox text = new VBox(2, name); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            String due = (todo.date().equals(displayedDate) ? "Bugün" : todo.date().format(DateTimeFormatter.ofPattern("d MMM", TR)))
                    + (todo.dueTime() == null ? "" : " " + todo.dueTime());
            Label date = new Label(due); date.getStyleClass().add(todo.date().isBefore(displayedDate) && !todo.done() ? "form-error" : "silver-todo-date");
            HBox row = new HBox(10, check, text, date); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("silver-todo-row");
            Tooltip.install(row, new Tooltip(todo.title() + (todo.detail().isBlank() ? "" : "\n" + todo.detail())));
            name.setOnMouseClicked(e -> details(todo.title(), due + (todo.detail().isBlank() ? "" : "\n\n" + todo.detail()), todo.module()));
            box.getChildren().add(row);
        }
        long done = day.todos().stream().filter(DailyAgendaService.Todo::done).count();
        ProgressBar progress = new ProgressBar(day.todos().isEmpty() ? 0 : (double) done / day.todos().size()); progress.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(progress, Priority.ALWAYS);
        Label count = new Label(done + "/" + day.todos().size()); count.getStyleClass().add("silver-todo-date");
        HBox footer = new HBox(10, progress, count); footer.setAlignment(Pos.CENTER_LEFT); box.getChildren().add(footer);
        if (day.todos().size() > 8) { Button more = new Button("Tüm görevler"); more.setOnAction(e -> navigate.accept("tasks")); box.getChildren().add(more); }
        rail.getChildren().add(box);
    }

    private VBox reminders(TodayService.Snapshot snapshot) {
        VBox box = new VBox(10);
        for (var section : List.of(snapshot.coursework(), snapshot.payments())) {
            if (section.error() != null) { Label error = new Label(section.error()); error.setWrapText(true); error.getStyleClass().add("form-error"); box.getChildren().add(error); }
            for (var entry : section.entries().stream().limit(8).toList()) {
                Label text = new Label(entry.title() + " · " + entry.date().format(DateTimeFormatter.ofPattern("d MMM", TR))
                        + (entry.amount() == null ? "" : " · " + NumberFormat.getCurrencyInstance(TR).format(entry.amount())));
                text.setWrapText(true); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
                Button open = new Button("Aç"); open.setOnAction(e -> navigate.accept(entry.module()));
                box.getChildren().add(new HBox(10, text, open));
            }
        }
        if (box.getChildren().isEmpty()) { Label empty = new Label("Yaklaşan teslim veya ödeme yok"); empty.getStyleClass().add("soluk"); box.getChildren().add(empty); }
        if (snapshot.payments().error() != null) status.setText(snapshot.payments().error());
        return box;
    }

    private void openEvent(DailyAgendaService.Event event) {
        if (event.study()) {
            day.sessions().stream().filter(s -> s.id().equals(event.id())).findFirst().ifPresent(this::sessionDialog);
        } else details(event.title(), event.start() + "–" + event.end() + "\n" + event.detail(), event.kind().equals("Google") ? "calendar" : "courses");
    }
    /** Open coursework, tasks and application deadlines on the displayed day (all-day Google events are not deadlines). */
    private List<DailyAgendaService.Todo> deadlines() {
        return day.todos().stream().filter(t -> !t.done() && t.date().equals(displayedDate)
                        && !t.id().startsWith("google-") && !t.id().startsWith("application-result-"))
                .sorted(Comparator.comparing((DailyAgendaService.Todo t) -> t.dueTime() == null ? LocalTime.MAX : t.dueTime())).toList();
    }
    private Node dueBanner(List<DailyAgendaService.Todo> dues) {
        VBox box = new VBox(6); box.setId("today-due-banner");
        if (dues.isEmpty()) { box.setManaged(false); box.setVisible(false); return box; }
        Label title = new Label("Bugün teslim · " + dues.size()); title.getStyleClass().add("today-due-title"); box.getChildren().add(title);
        boolean today = displayedDate.equals(LocalDate.now());
        for (var d : dues) {
            String when = d.dueTime() == null ? "Gün içinde" : d.dueTime().toString();
            if (today && d.dueTime() != null) {
                long left = java.time.Duration.between(LocalTime.now(), d.dueTime()).toMinutes();
                when += left < 0 ? " · süresi geçti" : " · " + (left >= 60 ? left / 60 + " sa " : "") + left % 60 + " dk kaldı";
            }
            Label name = new Label(d.title()); name.getStyleClass().add("silver-event-name"); name.setWrapText(true); name.setMinWidth(0);
            Label time = new Label(when); time.getStyleClass().add("today-due-time"); time.setWrapText(true); time.setMinWidth(0);
            VBox row = new VBox(2, name, time); row.setMinWidth(0);
            Button open = new Button(); open.setGraphic(row); open.setMaxWidth(Double.MAX_VALUE); open.setMinWidth(0); open.setAlignment(Pos.CENTER_LEFT); open.getStyleClass().add("today-due-row");
            open.setId("today-due-" + d.id()); open.setAccessibleText(d.title() + " " + when); open.setOnAction(e -> openDeadline(d));
            box.getChildren().add(open);
        }
        box.getStyleClass().add("today-due-banner");
        return box;
    }
    private void openDeadline(DailyAgendaService.Todo todo) {
        String when = (todo.date().equals(displayedDate) ? "Bugün" : todo.date().format(DateTimeFormatter.ofPattern("d MMM", TR)))
                + (todo.dueTime() == null ? "" : " " + todo.dueTime());
        details(todo.title(), "Son teslim: " + when + (todo.detail().isBlank() ? "" : "\n\n" + todo.detail()), todo.module());
    }
    private void details(String title, String detail, String module) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(detail);
        ButtonType open = new ButtonType("Modülde aç", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(open, ButtonType.CLOSE); UiTheme.styleDialog(alert, root);
        if (alert.showAndWait().orElse(ButtonType.CLOSE) == open) navigate.accept(module);
    }

    private void addTask() {
        Dialog<TaskItem> dialog = new Dialog<>(); dialog.setTitle("Görev ekle");
        TextField title = new TextField(); title.setPromptText("Ne yapacaksın?"); title.setId("quick-task-title");
        DatePicker date = new DatePicker(displayedDate); date.setId("quick-task-date");
        TextArea description = new TextArea(); description.setPromptText("Not (isteğe bağlı)"); description.setPrefRowCount(3);
        TaskMetadataEditor metadata = new TaskMetadataEditor();
        ButtonType save = new ButtonType("Ekle", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        ScrollPane taskForm = new ScrollPane(new VBox(12, new Label("Görev"), title, new Label("Son tarih"), date, description, metadata));
        taskForm.setFitToWidth(true); taskForm.setPrefViewportWidth(460); taskForm.setPrefViewportHeight(520);
        dialog.getDialogPane().setContent(taskForm);
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(title.textProperty().isEmpty().or(date.valueProperty().isNull()));
        UiTheme.styleDialog(dialog, root);
        Label validation = new Label(); validation.getStyleClass().add("form-error");
        ((VBox) taskForm.getContent()).getChildren().add(validation);
        final TaskItem[] accepted = new TaskItem[1];
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                accepted[0] = new TaskItem(title.getText().trim(), date.getValue(), description.getText(), "Bekliyor");
                metadata.apply(accepted[0]);
            } catch (Exception error) { validation.setText(error.getMessage()); event.consume(); }
        });
        dialog.setResultConverter(button -> button == save ? accepted[0] : null);
        dialog.showAndWait().ifPresent(value -> mutate(() -> {
            if (value.getTitle().isBlank()) throw new IllegalArgumentException("Başlık boş olamaz");
            planner.execute(new PlannerCommand.CreateTask(value));
        }));
    }

    private void sessionDialog(StudySession existing) {
        if (day == null) return;
        Dialog<StudySession> dialog = new Dialog<>(); dialog.setTitle(existing == null ? "Çalışma oturumu" : "Oturumu düzenle");
        TextField title = new TextField(existing == null ? "" : existing.title()); title.setPromptText("Ne üzerine çalışacaksın?"); title.setId("study-title");
        DatePicker date = new DatePicker(existing == null ? displayedDate : LocalDate.parse(existing.date())); date.setId("study-date");
        TextField start = new TextField(existing == null ? "15:00" : existing.start()), end = new TextField(existing == null ? "16:00" : existing.end());
        start.setId("study-start"); end.setId("study-end"); start.setPrefColumnCount(6); end.setPrefColumnCount(6);
        ComboBox<CourseData.Course> course = new ComboBox<>(); course.setId("study-course"); course.setPromptText("Derse bağla (isteğe bağlı)"); course.setMaxWidth(Double.MAX_VALUE);
        course.getItems().add(null); course.getItems().addAll(day.courses());
        if (existing != null) day.courses().stream().filter(c -> c.id().equals(existing.courseId())).findFirst().ifPresent(course::setValue);
        CheckBox done = new CheckBox("Tamamlandı"); done.setSelected(existing != null && existing.done()); done.setId("study-done");
        ComboBox<PlanningTarget> target = new ComboBox<>(); target.setId("study-target"); target.setPromptText("Göreve / ödeve bağla (isteğe bağlı)");
        target.setMaxWidth(Double.MAX_VALUE); target.getItems().add(null);
        target.getItems().addAll(targets);
        if (existing != null) target.getItems().stream().filter(Objects::nonNull)
                .filter(t -> t.id().equals(t.kind().equals("Görev") ? existing.taskId() : existing.workId()))
                .findFirst().ifPresent(target::setValue);
        Spinner<Integer> actual = new Spinner<>(0, 1440, existing == null ? 0 : existing.actualMinutes(), 5);
        actual.setEditable(true); actual.setId("study-actual-minutes");
        target.valueProperty().addListener((observable, previous, value) -> {
            if (value != null && !value.courseId().isBlank()) day.courses().stream().filter(c -> c.id().equals(value.courseId())).findFirst().ifPresent(course::setValue);
        });
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true);
        ButtonType save = new ButtonType("Kaydet", ButtonBar.ButtonData.OK_DONE), delete = new ButtonType("Sil", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (existing != null) dialog.getDialogPane().getButtonTypes().add(delete);
        VBox form = new VBox(10, new Label("Çalışma"), title, date, new HBox(8, new Label("Başlangıç"), start, new Label("Bitiş"), end), course, target, done,
                new Label("Gerçekleşen çalışma (dakika)"), actual,
                new Label("0 bırakılırsa tamamlanan oturumun planlanan süresi sayılır."), error);
        form.setPrefWidth(410); dialog.getDialogPane().setContent(form); UiTheme.styleDialog(dialog, root);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                if (title.getText().isBlank() || title.getText().length() > 300 || date.getValue() == null) throw new IllegalArgumentException("Başlık ve tarih gir");
                if (!LocalTime.parse(end.getText().trim()).isAfter(LocalTime.parse(start.getText().trim()))) throw new IllegalArgumentException("Bitiş başlangıçtan sonra olmalı");
                actual.commitValue();
            } catch (Exception ex) { error.setText("Başlık ve HH:mm biçimindeki saatleri kontrol et"); e.consume(); }
        });
        dialog.setResultConverter(button -> button == save ? new StudySession(existing == null ? UUID.randomUUID().toString() : existing.id(), title.getText().trim(), date.getValue().toString(),
                LocalTime.parse(start.getText().trim()).toString(), LocalTime.parse(end.getText().trim()).toString(), course.getValue() == null ? "" : course.getValue().id(), done.isSelected(),
                target.getValue() != null && target.getValue().kind().equals("Görev") ? target.getValue().id() : "",
                target.getValue() != null && !target.getValue().kind().equals("Görev") ? target.getValue().id() : "", actual.getValue()) : null);
        if (existing != null) dialog.getDialogPane().lookupButton(delete).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bu çalışma oturumu silinsin mi?", ButtonType.OK, ButtonType.CANCEL);
            UiTheme.styleDialog(confirm, dialog.getDialogPane());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) mutate(() -> planner.execute(new PlannerCommand.DeleteBlock(existing.id())));
            else e.consume();
        });
        dialog.showAndWait().ifPresent(value -> mutate(() -> planner.execute(new PlannerCommand.SaveBlock(value, existing == null, PlannerCommand.ConflictPolicy.REJECT))));
    }

    private void moveEvent(DailyTimelineView.Move value) {
        var existing = day.sessions().stream().filter(s -> s.id().equals(value.event().id())).findFirst().orElse(null);
        if (existing == null) return;
        long minutes = java.time.Duration.between(value.event().start(), value.event().end()).toMinutes();
        mutate(() -> planner.execute(new PlannerCommand.SaveBlock(existing.moved(displayedDate.toString(), value.start().toString(),
                value.start().plusMinutes(minutes).toString()), false, PlannerCommand.ConflictPolicy.REJECT)));
    }
    void showDate(LocalDate date) { displayedDate = date; refresh(); }

    private void pulse() {
        if (disposed) return; updateFocus();
        if (!agenda && !displayedDate.equals(LocalDate.now()) && !saving) { displayedDate = LocalDate.now(); refresh(); }
        if (LocalTime.now().getMinute() != lastMinute) {
            lastMinute = LocalTime.now().getMinute(); timeline.draw();
            if (day != null && !flow.getChildren().isEmpty()) flow.getChildren().set(0, nextEvent());
            if (day != null && flow.getChildren().size() > 1 && "today-due-banner".equals(flow.getChildren().get(1).getId()))
                flow.getChildren().set(1, dueBanner(deadlines()));
        }
    }
    public void dispose() {
        if (saving) throw new IllegalStateException("Kayıt sürüyor; tamamlanmasını bekle");
        disposed = true; pulse.stop(); if (pending != null) pending.cancel();
    }
    @FunctionalInterface private interface IoAction { void run() throws Exception; }
}
