package org.example;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

public final class ClassroomModuleView {
    private final GoogleClassroomService service;
    private final Consumer<String> navigate;
    private final VBox root = new VBox(12);
    private final Label status = new Label(), account = new Label();
    private final Button connect = new Button("Classroom'a bağlan"), refresh = new Button("Yenile");
    private final ListView<ClassroomData.Course> courses = new ListView<>();
    private final ListView<ClassroomData.Work> work = new ListView<>();
    private ClassroomData data = ClassroomData.empty();
    private Task<ClassroomData> pending;

    public ClassroomModuleView(GoogleClassroomService service, Consumer<String> navigate) { this.service = service; this.navigate = navigate; }
    public VBox build() {
        root.setId("classroom-module"); status.setId("classroom-status"); account.setId("classroom-account"); status.setWrapText(true);
        connect.setId("classroom-connect"); refresh.setId("classroom-refresh");
        connect.setOnAction(e -> run(true)); refresh.setOnAction(e -> run(false));
        Button settings = new Button("Bağlantı ayarları"); settings.setOnAction(e -> navigate.accept("settings"));
        Button cancel = new Button("İptal"); cancel.setId("classroom-cancel");
        cancel.disableProperty().bind(connect.disabledProperty().not());
        cancel.setOnAction(e -> { service.cancelAuthorization(); if (pending != null) pending.cancel(true); });
        root.getChildren().setAll(new FlowPane(10, 8, connect, refresh, cancel, settings), account, status);
        courses.setId("classroom-courses"); work.setId("classroom-work");
        courses.getStyleClass().add("classroom-list"); work.getStyleClass().add("classroom-list"); courses.setMinWidth(200);
        courses.setCellFactory(v -> new ListCell<>() {
            { setPrefWidth(0); } // lets long titles wrap instead of forcing horizontal scroll
            @Override protected void updateItem(ClassroomData.Course item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                setGraphic(empty || item == null ? null : card(item.name(), item.section().isBlank() ? "Şube bilgisi yok" : item.section()));
            }
        });
        work.setCellFactory(v -> new ListCell<>() {
            { setPrefWidth(0); } // lets long titles wrap instead of forcing horizontal scroll
            @Override protected void updateItem(ClassroomData.Work item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                setGraphic(empty || item == null ? null : card(item.title(), (item.dueDate().isBlank() ? "Son tarih yok"
                        : item.dueDate() + " " + item.dueTime()) + " · " + state(item.state())));
            }
        });
        courses.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> filter());
        work.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openWork(); });
        Button open = new Button("Classroom'da aç"); open.setOnAction(e -> openWork());
        Button importCourse = new Button("Seçili dersi ve ödevlerini aktar"); importCourse.setId("classroom-import");
        importCourse.disableProperty().bind(courses.getSelectionModel().selectedItemProperty().isNull().or(connect.disabledProperty()));
        importCourse.setOnAction(e -> {
            var selected = courses.getSelectionModel().getSelectedItem(); if (selected == null || pending != null) return;
            Task<ClassroomData> task = new Task<>() {
                @Override protected ClassroomData call() throws Exception { service.importCourse(data, selected.id()); return data; }
            };
            pending = task; updateControls(); status.setText("Ders ve ödevler aktarılıyor…");
            task.setOnSucceeded(done -> { pending = null; updateControls(); status.setText("Aktarıldı. Dersler ekranında ödevlere çalışma süresi ekleyip oturum planlayabilirsin. Ders saatlerini ayrıca gir."); });
            task.setOnFailed(done -> { pending = null; updateControls(); status.setText(task.getException().getMessage()); });
            task.setOnCancelled(done -> { pending = null; updateControls(); status.setText("Aktarım durduruldu; yenileyerek son kayıtları görebilirsin."); });
            Thread.ofVirtual().name("classroom-import").start(task);
        });
        open.disableProperty().bind(work.getSelectionModel().selectedItemProperty().isNull());
        work.setPlaceholder(new Label("Ödev yok veya ders seçilmedi")); courses.setPlaceholder(new Label("Henüz ders yok"));
        TextArea description = new TextArea(); description.setEditable(false); description.setWrapText(true); description.setPrefRowCount(3);
        description.setId("classroom-work-description");
        work.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> description.setText(selected == null ? "" : selected.description()));
        Label importHint = new Label("Aktarılan kaydın yerel tamamlanma işareti Classroom'a teslim göndermez."); importHint.setWrapText(true);
        VBox works = new VBox(10, new Label("Ödevler ve teslim durumu"), work, description, new FlowPane(8, 8, open, importCourse), importHint); VBox.setVgrow(work, Priority.ALWAYS);
        SplitPane split = new SplitPane(courses, works); split.setDividerPositions(0.3);
        root.getChildren().add(split); VBox.setVgrow(split, Priority.ALWAYS);
        try { data = service.loadCached(); render(); }
        catch (Exception error) { status.setText(error.getMessage()); }
        updateControls(); return root;
    }
    private void run(boolean authorize) {
        if (pending != null) return;
        Task<ClassroomData> task = new Task<>() {
            @Override protected ClassroomData call() throws Exception { if (authorize) service.connect(); return service.sync(); }
        };
        pending = task; updateControls(); status.setText(authorize ? "Google izni bekleniyor…" : "Classroom okunuyor…");
        task.setOnSucceeded(e -> { pending = null; data = task.getValue(); render(); updateControls(); });
        task.setOnFailed(e -> { pending = null; status.setText("Classroom hatası: " + task.getException().getMessage()); updateControls(); });
        task.setOnCancelled(e -> { pending = null; status.setText("Classroom işlemi iptal edildi."); updateControls(); });
        Thread.ofVirtual().name("classroom-sync").start(task);
    }
    private void render() {
        account.setText(data.accountEmail().isBlank() ? "Classroom bağlı değil" : data.accountEmail());
        courses.getItems().setAll(data.courses()); if (!courses.getItems().isEmpty()) courses.getSelectionModel().selectFirst();
        filter(); status.setText(data.courses().size() + " ders · " + data.work().size() + " ödev"
                + (data.syncedAt().isBlank() ? "" : " · Son okuma: " + data.syncedAt()));
    }
    private void filter() {
        var selected = courses.getSelectionModel().getSelectedItem();
        work.getItems().setAll(data.work().stream().filter(w -> selected == null || w.courseId().equals(selected.id()))
                .sorted(Comparator.comparing(ClassroomData.Work::submitted).thenComparing(w -> w.dueDate().isBlank() ? "9999" : w.dueDate())).toList());
    }
    private void updateControls() { connect.setDisable(pending != null); refresh.setDisable(pending != null || !service.isConnected()); }
    private void openWork() {
        var item = work.getSelectionModel().getSelectedItem(); if (item == null) return;
        try { DesktopIntegration.browse(item.url()); } catch (Exception error) { status.setText(error.getMessage()); }
    }
    private static VBox card(String title, String detail) {
        Label name = new Label(title); name.getStyleClass().add("classroom-card-title"); name.setWrapText(true);
        Label sub = new Label(detail); sub.getStyleClass().add("soluk"); sub.setWrapText(true);
        VBox box = new VBox(3, name, sub); box.getStyleClass().add("classroom-card"); box.setMinWidth(0); box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
    private static String state(String value) { return switch (value) {
        case "TURNED_IN" -> "Teslim edildi"; case "RETURNED" -> "Öğretmen geri gönderdi";
        case "RECLAIMED_BY_STUDENT" -> "Teslim geri alındı"; default -> "Teslim bekliyor";
    }; }
    public void dispose() { if (pending != null) { service.cancelAuthorization(); pending.cancel(true); } }
}
