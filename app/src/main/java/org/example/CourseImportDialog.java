package org.example;

import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Path;
import java.util.Optional;

/** Parse in the background, preview, then return a validated candidate without writing files. */
final class CourseImportDialog {
    static Optional<CourseData> show(Parent owner, CourseData base) {
        Dialog<CourseData> dialog = new Dialog<>();
        dialog.setTitle("CSV'den şube aktar"); dialog.initOwner(owner.getScene().getWindow());
        dialog.setResizable(true);
        TextField path = new TextField(); path.setPromptText("UTF-8 CSV dosyasının yolu"); path.setId("csv-path");
        Button browse = new Button("Dosya seç"), read = new Button("Önizle"); read.setId("csv-preview");
        Label info = new Label("Virgül, noktalı virgül ve sekme ayırıcıları desteklenir.\n"
                + "Sütunlar: Ders Kodu, Ders Adı, Öğretim Elemanı, Gün Saat, Kredi, AKTS.\n"
                + "Örnek saat: Pazartesi 09:00-10:50 [D101] | Çarşamba 13:00-14:50 [Lab]");
        info.setWrapText(true);
        Label message = new Label("Dosyayı önizleyip kataloğa aktarın. Programa alınacak şubeleri daha sonra seçebilirsiniz.");
        message.setWrapText(true); message.setId("csv-status");
        ListView<CourseData.Section> list = new ListView<>(); list.setId("csv-sections"); list.setPrefHeight(280);
        list.getStyleClass().add("course-detail-list");
        list.setCellFactory(v -> new ListCell<>() {
            { setWrapText(true); setMinHeight(Region.USE_PREF_SIZE); }
            @Override protected void updateItem(CourseData.Section s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : CourseModuleView.sectionDescription(s));
            }
        });
        HBox fileRow = new HBox(8, path, browse, read); HBox.setHgrow(path, Priority.ALWAYS);
        VBox content = new VBox(12, info, fileRow, message, list); content.setPadding(new javafx.geometry.Insets(12));
        VBox.setVgrow(list, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().setPrefWidth(760);
        dialog.getDialogPane().getStylesheets().add(CourseImportDialog.class.getResource("/ui/style.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("uygulama");
        if (owner.getStyleClass().contains("dark")) dialog.getDialogPane().getStyleClass().add("dark");
        ButtonType accept = new ButtonType("Kataloğa aktar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, accept);
        Button commit = (Button) dialog.getDialogPane().lookupButton(accept); commit.setId("csv-import-save"); commit.setDisable(true);
        final CourseData[] candidate = new CourseData[1];
        final Task<?>[] pending = new Task<?>[1];
        path.textProperty().addListener((o, a, b) -> { candidate[0] = null; commit.setDisable(true); list.getItems().clear(); });
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Ders CSV'si seç");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV dosyası", "*.csv", "*.CSV"));
            var file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) { path.setText(file.getAbsolutePath()); read.fire(); }
        });
        read.setOnAction(e -> {
            candidate[0] = null; commit.setDisable(true); list.getItems().clear();
            String file = path.getText().trim();
            if (file.isBlank()) { message.setText("CSV dosyasını seçin."); return; }
            fileRow.setDisable(true); message.setText("CSV okunuyor…");
            Task<java.util.List<CourseData.Section>> task = new Task<>() {
                @Override protected java.util.List<CourseData.Section> call() throws Exception {
                    return new CourseCsvImporter().read(Path.of(file));
                }
            };
            pending[0] = task;
            task.setOnSucceeded(done -> {
                if (!dialog.isShowing()) return;
                fileRow.setDisable(false);
                try {
                    candidate[0] = CourseCatalogService.merge(base, task.getValue());
                    list.getItems().setAll(task.getValue());
                    long missing = task.getValue().stream().filter(s -> s.lessons().isEmpty()).count();
                    int added = candidate[0].sections().size() - base.sections().size();
                    message.setText(task.getValue().size() + " benzersiz şube · " + added + " yeni kayıt. "
                            + (missing > 0 ? missing + " şubenin saati yok; programda saat bloğu gösterilmez. " : "")
                            + "Mevcut ders ve ödevler değişmez. Aynı kayıtlar tekrar eklenmez.");
                    commit.setDisable(false);
                } catch (Exception ex) { message.setText("Aktarılamadı: " + ex.getMessage()); }
            });
            task.setOnFailed(done -> {
                if (dialog.isShowing()) { fileRow.setDisable(false); message.setText("CSV okunamadı: " + task.getException().getMessage()); }
            });
            Thread.ofVirtual().name("course-csv-import").start(task);
        });
        dialog.setOnHidden(e -> { if (pending[0] != null) pending[0].cancel(); });
        dialog.setResultConverter(type -> type == accept ? candidate[0] : null);
        return dialog.showAndWait();
    }
}
