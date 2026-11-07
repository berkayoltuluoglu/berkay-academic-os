package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ReferenceLibraryView {

    private final ObservableList<ReferenceEntry> visibleEntries = FXCollections.observableArrayList();
    private final List<ReferenceEntry> allEntries = new ArrayList<>();

    private ReferenceFileStore fileStore;
    private ListView<String> categoryListView;
    private ListView<ReferenceEntry> topicListView;

    private TextField titleField;
    private TextField categoryField;
    private TextField tagsField;
    private TextArea summaryArea;
    private TextArea contentArea;
    private Label pathLabel;
    private Label infoLabel;

    private Button saveButton;
    private Button newButton;
    private Button deleteButton;
    private Button reloadButton;

    private ReferenceEntry currentSelection;

    public VBox build() {
        return build(resolveDefaultDataFile());
    }

    public VBox build(Path dataFilePath) {
        this.fileStore = new ReferenceFileStore(dataFilePath);
        allEntries.clear();
        allEntries.addAll(fileStore.loadEntries());

        Label title = new Label("Referans Kütüphanesi");
        title.getStyleClass().add("panel-baslik");

        infoLabel = new Label("Hazır");
        infoLabel.setWrapText(true);

        HBox topBar = new HBox(12, title, infoLabel);
        topBar.setPadding(new Insets(10));

        categoryListView = new ListView<>();
        categoryListView.setPrefWidth(180);

        topicListView = new ListView<>(visibleEntries);
        topicListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ReferenceEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String summary = item.summary == null ? "" : item.summary.trim();
                    if (summary.length() > 60) {
                        summary = summary.substring(0, 60) + "...";
                    }
                    setText(item.title + "\n" + summary);
                }
            }
        });

        titleField = new TextField();
        titleField.setPromptText("Konu başlığı");

        categoryField = new TextField();
        categoryField.setPromptText("Kategori");

        tagsField = new TextField();
        tagsField.setPromptText("Etiketler (virgülle)");

        summaryArea = new TextArea();
        summaryArea.setPromptText("Kısa özet");
        summaryArea.setPrefRowCount(3);
        summaryArea.setWrapText(true);

        contentArea = new TextArea();
        contentArea.setPromptText("Markdown / düz metin içerik");
        contentArea.setWrapText(true);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        pathLabel = new Label("-");
        pathLabel.setWrapText(true);

        saveButton = new Button("Kaydet");
        newButton = new Button("Yeni Konu");
        deleteButton = new Button("Sil");
        reloadButton = new Button("Diskten Yenile");

        UiTheme.decorate(saveButton, "save", "accent");
        UiTheme.decorate(newButton, "plus", "flat");
        UiTheme.decorate(deleteButton, "trash-2", "danger");
        UiTheme.decorate(reloadButton, "refresh-cw", "flat");
        FlowPane actionBar = new FlowPane(8, 8, saveButton, newButton, deleteButton, reloadButton);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Başlık"), 0, 0);
        form.add(titleField, 1, 0);
        form.add(new Label("Kategori"), 0, 1);
        form.add(categoryField, 1, 1);
        form.add(new Label("Etiketler"), 0, 2);
        form.add(tagsField, 1, 2);
        form.add(new Label("Özet"), 0, 3);
        form.add(summaryArea, 1, 3);
        form.add(new Label("Dosya"), 0, 4);
        form.add(pathLabel, 1, 4);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(categoryField, Priority.ALWAYS);
        GridPane.setHgrow(tagsField, Priority.ALWAYS);
        GridPane.setHgrow(summaryArea, Priority.ALWAYS);
        GridPane.setVgrow(summaryArea, Priority.NEVER);

        VBox editorPane = new VBox(10,
                new Label("Konu Düzenleyici"),
                form,
                new Label("İçerik"),
                contentArea,
                actionBar
        );
        editorPane.setPadding(new Insets(10));
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane();
        VBox leftPane = new VBox(8, new Label("Kategoriler"), categoryListView);
        leftPane.setPadding(new Insets(10));
        VBox middlePane = new VBox(8, new Label("Konular"), topicListView);
        middlePane.setPadding(new Insets(10));
        VBox.setVgrow(categoryListView, Priority.ALWAYS);
        VBox.setVgrow(topicListView, Priority.ALWAYS);

        leftPane.getStyleClass().add("panel");
        middlePane.getStyleClass().add("panel");
        editorPane.getStyleClass().add("panel");
        leftPane.setMinWidth(110);
        middlePane.setMinWidth(160);
        editorPane.setMinWidth(280);
        categoryListView.getStyleClass().add("favori-listesi");
        pathLabel.getStyleClass().add("soluk");
        splitPane.getStyleClass().add("workspace-split");
        splitPane.getItems().addAll(leftPane, middlePane, editorPane);
        splitPane.setDividerPositions(0.20, 0.45);

        VBox root = new VBox(10, topBar, splitPane);
        root.setPadding(Insets.EMPTY);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        refreshCategoryList();
        applyCategoryFilter(null);

        categoryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("Tümü".equals(newVal)) {
                applyCategoryFilter(null);
            } else {
                applyCategoryFilter(newVal);
            }
        });

        topicListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            currentSelection = newVal;
            bindSelectionToEditor(newVal);
        });

        newButton.setOnAction(e -> createNewEntry());
        saveButton.setOnAction(e -> saveCurrentEntry());
        deleteButton.setOnAction(e -> deleteCurrentEntry());
        reloadButton.setOnAction(e -> reloadFromDisk());

        deleteButton.setDisable(true);
        saveButton.setDisable(false);

        if (!visibleEntries.isEmpty()) {
            topicListView.getSelectionModel().selectFirst();
        } else {
            createNewEntry();
        }

        return root;
    }

    private Path resolveDefaultDataFile() {
        return AppPaths.root().resolve("data/reference-library/index.json");
    }

    private void refreshCategoryList() {
        Set<String> categories = allEntries.stream()
                .map(entry -> entry.category == null || entry.category.isBlank() ? "Genel" : entry.category.trim())
                .collect(Collectors.toCollection(TreeSet::new));

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("Tümü");
        items.addAll(categories);
        categoryListView.setItems(items);

        if (categoryListView.getSelectionModel().isEmpty() && !items.isEmpty()) {
            categoryListView.getSelectionModel().selectFirst();
        }
    }

    private void applyCategoryFilter(String category) {
        visibleEntries.clear();
        for (ReferenceEntry entry : allEntries) {
            String normalized = entry.category == null || entry.category.isBlank() ? "Genel" : entry.category.trim();
            if (category == null || category.isBlank() || normalized.equalsIgnoreCase(category)) {
                visibleEntries.add(entry);
            }
        }
        visibleEntries.sort(Comparator.comparing(e -> e.title == null ? "" : e.title, String.CASE_INSENSITIVE_ORDER));
        infoLabel.setText(visibleEntries.size() + " konu gösteriliyor");
    }

    private void bindSelectionToEditor(ReferenceEntry entry) {
        boolean hasEntry = entry != null;
        deleteButton.setDisable(!hasEntry);

        if (!hasEntry) {
            titleField.clear();
            categoryField.clear();
            tagsField.clear();
            summaryArea.clear();
            contentArea.clear();
            pathLabel.setText("-");
            return;
        }

        titleField.setText(nullToEmpty(entry.title));
        categoryField.setText(nullToEmpty(entry.category));
        tagsField.setText(String.join(", ", entry.tags));
        summaryArea.setText(nullToEmpty(entry.summary));
        contentArea.setText(nullToEmpty(entry.content));
        pathLabel.setText(entry.contentFile == null ? "-" : entry.contentFile);
    }

    private void createNewEntry() {
        ReferenceEntry entry = new ReferenceEntry();
        entry.id = UUID.randomUUID().toString();
        entry.title = "Yeni Konu";
        entry.category = "Genel";
        entry.summary = "";
        entry.content = "";
        entry.tags = new ArrayList<>();
        entry.updatedAt = LocalDateTime.now().toString();

        currentSelection = entry;
        bindSelectionToEditor(entry);
        topicListView.getSelectionModel().clearSelection();
        infoLabel.setText("Yeni konu oluşturuldu, kaydetmeyi unutma");
    }

    private void saveCurrentEntry() {
        if (currentSelection == null) {
            createNewEntry();
        }

        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isBlank()) {
            infoLabel.setText("Başlık boş olamaz");
            return;
        }

        String previousId = currentSelection.id;
        currentSelection.title = title;
        currentSelection.category = normalizeCategory(categoryField.getText());
        currentSelection.summary = summaryArea.getText() == null ? "" : summaryArea.getText().trim();
        currentSelection.content = contentArea.getText() == null ? "" : contentArea.getText();
        currentSelection.tags = parseTags(tagsField.getText());
        currentSelection.updatedAt = LocalDateTime.now().toString();

        currentSelection.contentFile = fileStore.computeMarkdownRelativePath(currentSelection);

        if (previousId == null || previousId.isBlank()) {
            currentSelection.id = UUID.randomUUID().toString();
        }

        Optional<ReferenceEntry> existing = allEntries.stream()
                .filter(entry -> Objects.equals(entry.id, currentSelection.id))
                .findFirst();

        if (existing.isPresent()) {
            ReferenceEntry target = existing.get();
            target.title = currentSelection.title;
            target.category = currentSelection.category;
            target.summary = currentSelection.summary;
            target.content = currentSelection.content;
            target.tags = new ArrayList<>(currentSelection.tags);
            target.updatedAt = currentSelection.updatedAt;
            target.contentFile = currentSelection.contentFile;
            currentSelection = target;
        } else {
            allEntries.add(currentSelection);
        }

        fileStore.saveEntries(allEntries);
        refreshCategoryList();
        applyCategoryFilter(categoryListView.getSelectionModel().getSelectedItem());

        ReferenceEntry entryToSelect = allEntries.stream()
                .filter(entry -> Objects.equals(entry.id, currentSelection.id))
                .findFirst()
                .orElse(null);

        if (entryToSelect != null) {
            topicListView.getSelectionModel().select(entryToSelect);
            bindSelectionToEditor(entryToSelect);
        }

        infoLabel.setText("Konu kaydedildi");
    }

    private void deleteCurrentEntry() {
        ReferenceEntry selected = topicListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            infoLabel.setText("Silmek için önce bir konu seç");
            return;
        }

        allEntries.removeIf(entry -> Objects.equals(entry.id, selected.id));
        fileStore.deleteEntryFiles(selected);
        fileStore.saveEntries(allEntries);

        refreshCategoryList();
        applyCategoryFilter(categoryListView.getSelectionModel().getSelectedItem());
        topicListView.getSelectionModel().clearSelection();
        currentSelection = null;
        bindSelectionToEditor(null);
        infoLabel.setText("Konu silindi");
    }

    private void reloadFromDisk() {
        allEntries.clear();
        allEntries.addAll(fileStore.loadEntries());
        refreshCategoryList();
        applyCategoryFilter(categoryListView.getSelectionModel().getSelectedItem());
        if (!visibleEntries.isEmpty()) {
            topicListView.getSelectionModel().selectFirst();
        } else {
            bindSelectionToEditor(null);
        }
        infoLabel.setText("Diskteki veriler yeniden yüklendi");
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Genel";
        }
        return raw.trim();
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static class ReferenceEntry {
        public String id;
        public String title;
        public String category;
        public String summary;
        public List<String> tags = new ArrayList<>();
        public String content;
        public String contentFile;
        public String updatedAt;
    }

    private static class ReferenceFileStore {
        private final Path indexFile;
        private final Path baseDir;
        private final Path markdownDir;
        private final ObjectMapper mapper = new ObjectMapper();

        ReferenceFileStore(Path indexFile) {
            this.indexFile = indexFile;
            this.baseDir = indexFile.getParent() == null ? Paths.get(".") : indexFile.getParent();
            this.markdownDir = this.baseDir.resolve("topics");
        }

        List<ReferenceEntry> loadEntries() {
            try {
                Files.createDirectories(baseDir);
                Files.createDirectories(markdownDir);

                if (!Files.exists(indexFile)) {
                    List<ReferenceEntry> seeded = seedDefaults();
                    saveEntries(seeded);
                    return seeded;
                }

                List<ReferenceEntry> entries = mapper.readValue(
                        indexFile.toFile(),
                        new TypeReference<List<ReferenceEntry>>() {}
                );

                for (ReferenceEntry entry : entries) {
                    if (entry.tags == null) {
                        entry.tags = new ArrayList<>();
                    }
                    if (entry.contentFile != null && !entry.contentFile.isBlank()) {
                        Path contentPath = baseDir.resolve(entry.contentFile).normalize();
                        if (Files.exists(contentPath)) {
                            entry.content = Files.readString(contentPath, StandardCharsets.UTF_8);
                        }
                    }
                    if (entry.content == null) {
                        entry.content = "";
                    }
                }

                return entries;
            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        void saveEntries(List<ReferenceEntry> entries) {
            try {
                Files.createDirectories(baseDir);
                Files.createDirectories(markdownDir);

                for (ReferenceEntry entry : entries) {
                    if (entry.id == null || entry.id.isBlank()) {
                        entry.id = UUID.randomUUID().toString();
                    }

                    entry.contentFile = computeMarkdownRelativePath(entry);
                    Path contentPath = baseDir.resolve(entry.contentFile).normalize();
                    if (contentPath.getParent() != null) {
                        Files.createDirectories(contentPath.getParent());
                    }
                    Files.writeString(contentPath, entry.content == null ? "" : entry.content, StandardCharsets.UTF_8);
                }

                List<ReferenceEntry> metadataOnly = new ArrayList<>();
                for (ReferenceEntry entry : entries) {
                    ReferenceEntry copy = new ReferenceEntry();
                    copy.id = entry.id;
                    copy.title = entry.title;
                    copy.category = entry.category;
                    copy.summary = entry.summary;
                    copy.tags = entry.tags == null ? new ArrayList<>() : new ArrayList<>(entry.tags);
                    copy.updatedAt = entry.updatedAt;
                    copy.contentFile = entry.contentFile;
                    copy.content = null;
                    metadataOnly.add(copy);
                }

                mapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), metadataOnly);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String computeMarkdownRelativePath(ReferenceEntry entry) {
            String category = sanitize(entry.category == null ? "genel" : entry.category);
            String title = sanitize(entry.title == null ? "konu" : entry.title);
            return "topics/" + category + "/" + title + ".md";
        }

        void deleteEntryFiles(ReferenceEntry entry) {
            try {
                if (entry != null && entry.contentFile != null && !entry.contentFile.isBlank()) {
                    Files.deleteIfExists(baseDir.resolve(entry.contentFile).normalize());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private String sanitize(String input) {
            String normalized = input.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9çğıöşü_-]+", "-")
                    .replaceAll("-{2,}", "-")
                    .replaceAll("^-|-$", "");

            return normalized.isBlank() ? "icerik" : normalized;
        }

        private List<ReferenceEntry> seedDefaults() {
            List<ReferenceEntry> defaults = new ArrayList<>();

            defaults.add(createDefault(
                    "Ohm Kanunu",
                    "Elektronik",
                    "Gerilim, akım ve direnç ilişkisi",
                    List.of("devre", "ohm", "temel"),
                    "# Ohm Kanunu\n\nV = I * R ilişkisini tanımlar.\n\n## Kullanım\n- V biliniyorsa I veya R hesaplanır.\n- Temel DC devre analizinin çekirdeğidir."
            ));

            defaults.add(createDefault(
                    "UART Temelleri",
                    "Embedded",
                    "Seri haberleşme başlangıç notları",
                    List.of("uart", "seri", "esp32"),
                    "# UART Temelleri\n\nUART asenkron seri haberleşmedir.\n\n## Kritik Parametreler\n- baud rate\n- data bits\n- parity\n- stop bits"
            ));

            defaults.add(createDefault(
                    "Çift Katlı İntegral",
                    "Matematik",
                    "Alan ve hacim hesabı için kısa özet",
                    List.of("integral", "calculus"),
                    "# Çift Katlı İntegral\n\nBölge üzerinde toplam etkiyi ölçmek için kullanılır.\n\n## Tipik Kullanım\n- alan\n- hacim\n- kütle"
            ));

            return defaults;
        }

        private ReferenceEntry createDefault(String title, String category, String summary, List<String> tags, String content) {
            ReferenceEntry entry = new ReferenceEntry();
            entry.id = UUID.randomUUID().toString();
            entry.title = title;
            entry.category = category;
            entry.summary = summary;
            entry.tags = new ArrayList<>(tags);
            entry.content = content;
            entry.updatedAt = LocalDateTime.now().toString();
            entry.contentFile = computeMarkdownRelativePath(entry);
            return entry;
        }
    }
}
