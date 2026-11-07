package org.example;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class ScholarshipModuleView {
    private final ScholarshipStorageService storage;
    private final VBox root = new VBox(12), documents = new VBox(6);
    private final ListView<ScholarshipData.Application> list = new ListView<>();
    private final TextField name = field("scholarship-name"), amount = field("scholarship-amount"), url = field("scholarship-url"), documentName = field("scholarship-document-name");
    private final TextArea note = new TextArea();
    private final DatePicker deadline = new DatePicker(), applied = new DatePicker(), announced = new DatePicker();
    private final ComboBox<String> kind = new ComboBox<>(), status = new ComboBox<>(), result = new ComboBox<>();
    private final Label message = new Label(), summary = new Label();
    private ScholarshipData data = ScholarshipData.empty();
    private final List<ScholarshipData.Document> draftDocuments = new ArrayList<>();
    private String editing;
    private Task<ScholarshipData> pending;
    private boolean writing, disposed;
    public ScholarshipModuleView(Path file) { storage = new ScholarshipStorageService(file); }
    public VBox build() {
        root.setId("scholarships-view"); message.setId("scholarship-message"); message.setWrapText(true);
        list.setId("scholarship-list"); deadline.setId("scholarship-deadline"); applied.setId("scholarship-applied"); announced.setId("scholarship-result-date");
        status.setId("scholarship-status"); result.setId("scholarship-result"); kind.setId("scholarship-kind"); note.setId("scholarship-note");
        kind.getItems().setAll("Burs", "Staj", "Diğer"); status.getItems().setAll(ScholarshipStorageService.STATUSES); result.getItems().setAll(ScholarshipStorageService.RESULTS);
        note.setWrapText(true); note.setPrefRowCount(3); documentName.setPromptText("Öğrenci belgesi, transkript…");
        Button fresh = button("Yeni başvuru", "scholarship-new", () -> clear());
        Button save = button("Kaydet", "scholarship-save", () -> save());
        Button remove = button("Seçiliyi sil", "scholarship-delete", () -> remove());
        Button refresh = button("Yenile", "scholarship-refresh", () -> load());
        Button export = button("CSV dışa aktar", "scholarship-export", () -> exportCsv());
        Button importButton = button("CSV içe aktar", "scholarship-import", () -> importCsv());
        Button open = button("Başvuru bağlantısını aç", "scholarship-open", () -> {
            try { DesktopIntegration.browse(url.getText().trim()); } catch (Exception error) { message.setText(error.getMessage()); }
        });
        TextField search = field("scholarship-search"); search.setPromptText("Kurum veya notlarda ara");
        ComboBox<String> filter = new ComboBox<>(); filter.getItems().setAll("Tümü", "Başvuru hazırlanıyor", "Sonuç bekleniyor", "Kazanıldı", "Yedek", "Olumsuz"); filter.setValue("Tümü");
        Runnable renderList = () -> list.getItems().setAll(data.applications().stream()
                .filter(a -> (a.name() + " " + a.note()).toLowerCase(Locale.forLanguageTag("tr")).contains(search.getText().toLowerCase(Locale.forLanguageTag("tr"))))
                .filter(a -> switch (filter.getValue()) { case "Başvuru hazırlanıyor" -> a.awaitingApplication(); case "Sonuç bekleniyor" -> a.awaitingResult();
                    case "Kazanıldı", "Yedek", "Olumsuz" -> a.result().equals(filter.getValue()); default -> true; })
                .sorted(Comparator.comparing((ScholarshipData.Application a) -> !(a.awaitingApplication() || a.awaitingResult()))
                        .thenComparing(a -> a.deadline().isBlank() ? "9999" : a.deadline()).thenComparing(ScholarshipData.Application::name)).toList());
        search.textProperty().addListener((o,a,b) -> renderList.run()); filter.valueProperty().addListener((o,a,b) -> renderList.run());
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(ScholarshipData.Application a, boolean empty) {
                super.updateItem(a, empty); setText(empty || a == null ? null : a.name() + " · " + a.kind() + "\n" + a.status() + " · " + a.result()
                        + (a.deadline().isBlank() ? "" : "\nSon başvuru " + a.deadline() + " · " + countdown(a.deadline())));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { if (b != null) edit(b); });
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(10);
        String[] labels = {"Kurum / başvuru adı", "Tür", "Miktar / kapsam", "Son başvuru", "Başvurulan tarih", "Durum", "Sonuç", "Sonuç açıklanma", "Başvuru bağlantısı", "Sonuç notu"};
        javafx.scene.Node[] inputs = {name, kind, amount, deadline, applied, status, result, announced, url, note};
        for (int i=0; i<labels.length; i++) { form.add(new Label(labels[i]), 0, i); form.add(inputs[i], 1, i); GridPane.setHgrow(inputs[i], Priority.ALWAYS); }
        Button addDoc = button("Belge ekle", "scholarship-document-add", () -> {
            if (documentName.getText().isBlank()) return;
            draftDocuments.add(new ScholarshipData.Document(UUID.randomUUID().toString(), documentName.getText().trim(), false)); documentName.clear(); renderDocuments();
        });
        VBox editor = new VBox(10, form, new Label("Gerekli belgeler"), documents, new HBox(8, documentName, addDoc), new FlowPane(8,8,fresh,save,remove,open));
        HBox.setHgrow(documentName, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(editor); editorScroll.setFitToWidth(true);
        SplitPane split = new SplitPane(list, editorScroll); split.setDividerPositions(0.36); VBox.setVgrow(split, Priority.ALWAYS);
        root.getChildren().setAll(new FlowPane(8,8,search,filter,refresh,export,importButton), summary, split, message);
        root.getProperties().put("renderList", renderList); clear(); load(); return root;
    }
    private static String countdown(String iso) { long days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso)); return days == 0 ? "Bugün" : days > 0 ? days + " gün kaldı" : -days + " gün geçti"; }
    private void load() { if (pending != null) return; run(() -> storage.load(), false, () -> { clear(); render(); message.setText(""); }); }
    private void save() {
        if (pending != null) return;
        var value = new ScholarshipData.Application(editing == null ? UUID.randomUUID().toString() : editing, name.getText().trim(), kind.getValue(), amount.getText().trim(), iso(deadline), iso(applied),
                status.getValue(), result.getValue(), iso(announced), List.copyOf(draftDocuments), note.getText().trim(), url.getText().trim());
        List<ScholarshipData.Application> entries = new ArrayList<>(data.applications()); int index = -1;
        for (int i=0; i<entries.size(); i++) if (entries.get(i).id().equals(value.id())) index = i;
        if (index < 0) entries.add(value); else entries.set(index, value);
        var next = new ScholarshipData(1, entries);
        run(() -> { storage.save(next); return next; }, true, () -> { render(); editing = value.id(); message.setText("Başvuru kaydedildi."); });
    }
    private void remove() {
        if (editing == null || pending != null) return;
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Seçili başvuru silinsin mi?", ButtonType.YES, ButtonType.NO); confirmation.initOwner(root.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        var next = new ScholarshipData(1, data.applications().stream().filter(a -> !a.id().equals(editing)).toList());
        run(() -> { storage.save(next); return next; }, true, () -> { clear(); render(); message.setText("Başvuru silindi."); });
    }
    private void clear() { editing = null; name.clear(); amount.clear(); url.clear(); note.clear(); deadline.setValue(null); applied.setValue(null); announced.setValue(null);
        kind.setValue("Burs"); status.setValue("Planlanıyor"); result.setValue("Beklemede"); draftDocuments.clear(); renderDocuments(); }
    private void edit(ScholarshipData.Application a) { editing = a.id(); name.setText(a.name()); amount.setText(a.amount()); url.setText(a.url()); note.setText(a.note());
        kind.setValue(a.kind()); status.setValue(a.status()); result.setValue(a.result()); deadline.setValue(date(a.deadline())); applied.setValue(date(a.appliedOn())); announced.setValue(date(a.resultDate()));
        draftDocuments.clear(); draftDocuments.addAll(a.documents()); renderDocuments(); }
    private void renderDocuments() {
        documents.getChildren().clear();
        for (int i=0; i<draftDocuments.size(); i++) { int index=i; var doc = draftDocuments.get(i);
            CheckBox ready = new CheckBox(doc.name()); ready.setSelected(doc.ready());
            ready.setOnAction(e -> draftDocuments.set(index, new ScholarshipData.Document(doc.id(),doc.name(),ready.isSelected())));
            Button remove = new Button("Kaldır"); remove.setOnAction(e -> { draftDocuments.remove(index); renderDocuments(); });
            documents.getChildren().add(new HBox(8,ready,remove)); }
    }
    private void render() { ((Runnable)root.getProperties().get("renderList")).run(); summary.setText(data.applications().size() + " başvuru · "
            + data.applications().stream().filter(ScholarshipData.Application::awaitingApplication).count() + " hazırlanıyor · "
            + data.applications().stream().filter(ScholarshipData.Application::awaitingResult).count() + " sonuç bekliyor"); }
    private void exportCsv() {
        if (pending != null) return; FileChooser chooser = new FileChooser(); chooser.setInitialFileName("basvurular-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Başvuru tablosu", "*.csv")); var file = chooser.showSaveDialog(root.getScene().getWindow()); if (file == null) return;
        String csv = ScholarshipCsv.export(data); run(() -> { AtomicFileWriter.writeText(file.toPath(),csv); return data; }, true, () -> message.setText("CSV kaydedildi."));
    }
    private void importCsv() {
        if (pending != null) return; FileChooser chooser = new FileChooser(); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Başvuru tablosu", "*.csv"));
        var file = chooser.showOpenDialog(root.getScene().getWindow()); if (file == null) return;
        run(() -> ScholarshipCsv.parse(new String(FinanceStorageService.readLimited(file.toPath()), java.nio.charset.StandardCharsets.UTF_8)), false, () -> {} , incoming -> {
            var choice = new Alert(Alert.AlertType.CONFIRMATION, incoming.applications().size() + " başvuru okundu. Aynı kimlikli kayıtlar güncellenerek mevcut listeye eklensin mi?", ButtonType.YES, ButtonType.NO);
            choice.initOwner(root.getScene().getWindow()); if (choice.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            Map<String,ScholarshipData.Application> entries = new LinkedHashMap<>(); data.applications().forEach(a -> entries.put(a.id(),a)); incoming.applications().forEach(a -> entries.put(a.id(),a));
            var next = new ScholarshipData(1,List.copyOf(entries.values())); run(() -> { storage.save(next); return next; },true,() -> { clear(); render(); message.setText("Başvurular aktarıldı."); });
        });
    }
    private void run(Operation operation, boolean write, Runnable succeeded) { run(operation, write, succeeded, null); }
    private void run(Operation operation, boolean write, Runnable succeeded, java.util.function.Consumer<ScholarshipData> preview) {
        if (pending != null) return; writing = write; root.setDisable(true); message.setText(write ? "Kaydediliyor…" : "Okunuyor…");
        Task<ScholarshipData> task = new Task<>() { @Override protected ScholarshipData call() throws Exception { return operation.run(); } }; pending = task;
        task.setOnSucceeded(e -> { pending=null; writing=false; root.setDisable(false); if (disposed) return;
            if (preview != null) preview.accept(task.getValue()); else { data=task.getValue(); succeeded.run(); } });
        task.setOnFailed(e -> { pending=null; writing=false; root.setDisable(false); if (!disposed) message.setText(task.getException().getMessage()); });
        task.setOnCancelled(e -> { pending=null; writing=false; root.setDisable(false); }); Thread.ofVirtual().name("scholarship-records").start(task);
    }
    public void requireIdle() { if (writing) throw new IllegalStateException("Başvuru kaydının tamamlanmasını bekle."); }
    public void dispose() { requireIdle(); disposed=true; if (pending != null) pending.cancel(true); }
    private static TextField field(String id) { var result = new TextField(); result.setId(id); return result; }
    private Button button(String text,String id,Runnable action) { var button = new Button(text); button.setId(id); button.setOnAction(e -> { try { action.run(); } catch (RuntimeException failure) { message.setText(failure.getMessage()); } }); return button; }
    private static String iso(DatePicker picker) { picker.commitValue(); return picker.getValue() == null ? "" : picker.getValue().toString(); }
    private static LocalDate date(String value) { return value.isBlank() ? null : LocalDate.parse(value); }
    @FunctionalInterface private interface Operation { ScholarshipData run() throws Exception; }
}
