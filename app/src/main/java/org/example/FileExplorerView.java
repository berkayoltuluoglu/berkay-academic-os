package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FileExplorerView {

    private final TextArea editorArea;

    private final Consumer<Path> openedFileSetter;
    private final Supplier<Path> openedFileGetter;

    private final Consumer<Path> currentDirectorySetter;
    private final Supplier<Path> currentDirectoryGetter;

    private final Consumer<Path> rootDirectorySetter;
    private final Supplier<Path> rootDirectoryGetter;

    private javafx.concurrent.Task<List<ExplorerItem>> currentLoadTask;
    private long loadRequestVersion = 0;

    public FileExplorerView(
            TextArea editorArea,
            Consumer<Path> openedFileSetter,
            Supplier<Path> openedFileGetter,
            Consumer<Path> currentDirectorySetter,
            Supplier<Path> currentDirectoryGetter,
            Consumer<Path> rootDirectorySetter,
            Supplier<Path> rootDirectoryGetter
    ) {
        this.editorArea = editorArea;
        this.openedFileSetter = openedFileSetter;
        this.openedFileGetter = openedFileGetter;
        this.currentDirectorySetter = currentDirectorySetter;
        this.currentDirectoryGetter = currentDirectoryGetter;
        this.rootDirectorySetter = rootDirectorySetter;
        this.rootDirectoryGetter = rootDirectoryGetter;
    }

    private String readableErrorMessage(Exception ex, String fallback) {
        if (ex instanceof NoSuchFileException) {
            return "Durum: Dosya veya klasör bulunamadı";
        }
        if (ex instanceof AccessDeniedException) {
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                return "Durum: Erişim izni yok - " + message;
            }
            return "Durum: Erişim izni yok";
        }
        if (ex instanceof DirectoryNotEmptyException) {
            return "Durum: Klasör boş değil";
        }
        return fallback;
    }

    private void showTextPreview(VBox previewContainer, TextArea editorArea, String content, boolean editable) {
        previewContainer.getChildren().setAll(editorArea);
        editorArea.setEditable(editable);
        editorArea.setText(content);
        VBox.setVgrow(editorArea, Priority.ALWAYS);
    }

    private void showImagePreview(VBox previewContainer, javafx.scene.image.ImageView imageView, Path file, String metadataText) {
        javafx.scene.image.Image image = new javafx.scene.image.Image(file.toUri().toString(), true);
        imageView.setImage(image);

        Label metadataLabel = new Label(metadataText == null ? "" : metadataText);
        metadataLabel.setWrapText(true);
        metadataLabel.getStyleClass().add("soluk");

        ScrollPane imageScrollPane = new ScrollPane(imageView);
        imageScrollPane.setFitToWidth(true);
        imageScrollPane.setFitToHeight(true);
        VBox.setVgrow(imageScrollPane, Priority.ALWAYS);

        if (metadataText == null || metadataText.isBlank()) {
            previewContainer.getChildren().setAll(imageScrollPane);
        } else {
            previewContainer.getChildren().setAll(metadataLabel, imageScrollPane);
        }
    }

    private void showInfoPreview(VBox previewContainer, Label previewInfoLabel, String message) {
        previewInfoLabel.setText(message);
        previewContainer.getChildren().setAll(previewInfoLabel);
    }

    private void showFileInfoPreview(VBox previewContainer, Path file, String title, String message, String metadataText, boolean showOpenButton) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-baslik");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);

        Label metadataLabel = new Label(metadataText);
        metadataLabel.setWrapText(true);
        metadataLabel.getStyleClass().addAll("soluk", "metadata");

        if (showOpenButton) {
            Button openButton = new Button("Harici Uygulamada Aç");
            openButton.setOnAction(e -> {
                try {
                    DesktopIntegration.open(file);
                } catch (Exception ignored) {
                }
            });
            previewContainer.getChildren().setAll(titleLabel, messageLabel, metadataLabel, openButton);
        } else {
            previewContainer.getChildren().setAll(titleLabel, messageLabel, metadataLabel);
        }
    }

    private void applyButtonPermission(Button button, boolean allowed) {
        button.setDisable(!allowed);
        button.setVisible(allowed);
        button.setManaged(allowed);
    }

    private void applyDropAreaStyle(Label label, boolean active, boolean allowed) {
        if (!label.getStyleClass().contains("birakma-alani"))
            label.getStyleClass().addAll("birakma-alani", "soluk");
        label.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("surukleniyor"), active && allowed);
        label.setDisable(!allowed);
    }

    public SplitPane build(
            ModuleItem module,
            Path rootPath,
            FileOperationService fileOperationService,
            PreviewService previewService,
            Runnable saveOpenedFileAction,
            Consumer<String> statusUpdater,
            Supplier<Path> projectRootSupplier
    ) {
        FileExplorerOptions explorerOptions = module.getExplorerOptions() == null
                ? new FileExplorerOptions()
                : module.getExplorerOptions();

        boolean canCreateFolder = explorerOptions.canCreateFolder();
        boolean canCreateFile = explorerOptions.canCreateFile();
        boolean canRename = explorerOptions.canRename();
        boolean canDelete = explorerOptions.canDelete();
        boolean canEditFiles = explorerOptions.canEditFiles();
        boolean canDragDropImport = explorerOptions.canDragDropImport();
        boolean showPreviewPane = explorerOptions.isShowPreviewPane();

        Label leftTitle = new Label("Favoriler");
        leftTitle.getStyleClass().add("panel-baslik");

        Label middleTitle = new Label("İçerik");
        middleTitle.getStyleClass().add("panel-baslik");

        Label editorTitle = new Label(canEditFiles ? "Önizleme / Editör" : "Önizleme / Salt Okunur");
        editorTitle.getStyleClass().add("panel-baslik");

        Label pathLabel = new Label("Klasör: " + rootPath);
        pathLabel.setWrapText(true);
        pathLabel.getStyleClass().add("soluk");

        Label permissionLabel = new Label(canEditFiles ? "Düzenlenebilir" : "Salt okunur");
        permissionLabel.setTooltip(new Tooltip(explorerOptions.permissionSummary()));
        permissionLabel.setWrapText(true);
        permissionLabel.getStyleClass().add("alan-etiketi");
        Label dragDropImportLabel = new Label(canDragDropImport
                ? "Dosya veya klasörleri buraya sürükleyip bırak."
                : "Bu klasöre sürükleyerek dosya ekleme kapalı.");
        dragDropImportLabel.setWrapText(true);
        dragDropImportLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dragDropImportLabel.setMaxWidth(Double.MAX_VALUE);
        applyDropAreaStyle(dragDropImportLabel, false, canDragDropImport);

        ListView<FavoriteLocation> favoriteList = new ListView<>();
        ListView<ExplorerItem> itemList = new ListView<>();

        Label loadingLabel = new Label("Yükleniyor...");
        loadingLabel.setVisible(false);
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());

        favoriteList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FavoriteLocation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setGraphic(empty || item == null ? null : UiTheme.icon("folder"));
            }
        });

        itemList.setCellFactory(param -> {
            ListCell<ExplorerItem> cell = new ListCell<>() {
                @Override
                protected void updateItem(ExplorerItem item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getPath().getFileName().toString());
                        var icon = UiTheme.icon(item.isDirectory() ? "folder" : "file-text");
                        icon.getStyleClass().add(item.isDirectory() ? "klasor-ikonu" : "dosya-ikonu");
                        setGraphic(icon);
                    }
                }
            };

            cell.setOnMousePressed(event -> {
                if (cell.isEmpty()) {
                    event.consume();
                }
            });

            return cell;
        });

        ObservableList<FavoriteLocation> favorites = FXCollections.observableArrayList();
        if (module.getFavorites() != null) {
            favorites.addAll(module.getFavorites());
        }
        favoriteList.setItems(favorites);
        favoriteList.getStyleClass().add("favori-listesi");
        itemList.getStyleClass().add("file-list");
        itemList.setPlaceholder(new Label("Bu klasör boş"));
        favoriteList.setPlaceholder(new Label("Favori klasör yok"));
        editorArea.getStyleClass().remove("kod-editoru");
        editorArea.getStyleClass().add("kod-editoru");

        editorArea.clear();
        editorArea.setEditable(canEditFiles);
        editorArea.setPromptText(canEditFiles
                ? "Bir metin dosyası açıldığında içeriği burada görünür..."
                : "Salt okunur mod: Dosya içeriği görüntülenebilir ama düzenlenemez.");

        Button backButton = new Button("Geri");
        Button refreshButton = new Button("Yenile");
        Button newFolderButton = new Button("Klasör Ekle");
        Button newFileButton = new Button("Dosya Ekle");
        Button renameButton = new Button("Yeniden Adlandır");
        Button deleteButton = new Button("Sil");
        Button openExternalButton = new Button("Dışarıda Aç");
        Button saveFileButton = new Button("Dosyayı Kaydet");

        applyButtonPermission(newFolderButton, canCreateFolder);
        applyButtonPermission(newFileButton, canCreateFile);
        applyButtonPermission(renameButton, canRename);
        applyButtonPermission(deleteButton, canDelete);
        applyButtonPermission(saveFileButton, canEditFiles);

        UiTheme.iconOnly(backButton, "arrow-up");
        UiTheme.iconOnly(refreshButton, "refresh-cw");
        UiTheme.iconOnly(newFolderButton, "folder-plus");
        UiTheme.iconOnly(newFileButton, "file-plus");
        UiTheme.iconOnly(renameButton, "edit-2");
        UiTheme.iconOnly(deleteButton, "trash-2");
        UiTheme.iconOnly(openExternalButton, "external-link");
        UiTheme.decorate(saveFileButton, "save", "accent");
        FlowPane middleToolbar = new FlowPane(
                5, 5,
                backButton,
                refreshButton,
                newFolderButton,
                newFileButton,
                renameButton,
                deleteButton,
                openExternalButton
        );

        FlowPane editorToolbar = new FlowPane(8, 8, saveFileButton);

        Label previewInfoLabel = new Label("Henüz dosya açılmadı");
        previewInfoLabel.setWrapText(true);

        javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(420);
        imageView.setFitHeight(500);

        VBox previewContainer = new VBox();
        VBox.setVgrow(previewContainer, Priority.ALWAYS);
        showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");

        VBox leftPane = new VBox(10, leftTitle, favoriteList);
        leftPane.setPadding(new Insets(10));
        VBox.setVgrow(favoriteList, Priority.ALWAYS);

        ToggleButton favoritesToggle = new ToggleButton("Favorileri göster");
        ToggleButton previewToggle = new ToggleButton("Önizlemeyi göster");
        favoritesToggle.setSelected(true);
        previewToggle.setSelected(showPreviewPane);
        UiTheme.iconOnly(favoritesToggle, "sidebar");
        UiTheme.iconOnly(previewToggle, "columns");
        previewToggle.setVisible(showPreviewPane);
        previewToggle.setManaged(showPreviewPane);
        javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox middleHeading = new HBox(8, middleTitle, gap, favoritesToggle, previewToggle);
        middleHeading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox middlePane = new VBox(10, middleHeading, pathLabel, middleToolbar, permissionLabel, loadingLabel, itemList, dragDropImportLabel);
        middlePane.setPadding(new Insets(10));
        VBox.setVgrow(itemList, Priority.ALWAYS);

        VBox rightPane = new VBox(10, editorTitle, editorToolbar, previewContainer);
        rightPane.setPadding(new Insets(10));
        VBox.setVgrow(previewContainer, Priority.ALWAYS);

        leftPane.getStyleClass().add("panel");
        middlePane.getStyleClass().add("panel");
        rightPane.getStyleClass().add("panel");
        leftPane.setMinWidth(120);
        middlePane.setMinWidth(240);
        rightPane.setMinWidth(190);
        previewContainer.setMinHeight(0);
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("workspace-split");
        if (showPreviewPane) {
            splitPane.getItems().addAll(leftPane, middlePane, rightPane);
            splitPane.setDividerPositions(0.18, 0.61);
        } else {
            splitPane.getItems().addAll(leftPane, middlePane);
            splitPane.setDividerPositions(0.28);
        }

        Runnable arrangePanels = () -> {
            splitPane.getItems().clear();
            if (favoritesToggle.isSelected()) splitPane.getItems().add(leftPane);
            splitPane.getItems().add(middlePane);
            if (previewToggle.isSelected()) splitPane.getItems().add(rightPane);
            if (splitPane.getItems().size() == 3) splitPane.setDividerPositions(0.18, 0.61);
            else if (splitPane.getItems().size() == 2)
                splitPane.setDividerPositions(favoritesToggle.isSelected() ? 0.25 : 0.55);
        };
        favoritesToggle.setOnAction(e -> arrangePanels.run());
        previewToggle.setOnAction(e -> arrangePanels.run());

        Runnable refreshCurrentDirectory = () -> {
            Path currentDirectory = currentDirectoryGetter.get();
            if (currentDirectory == null) {
                return;
            }

            boolean showHiddenFiles = explorerOptions.isShowHiddenFiles();

            long requestVersion = ++loadRequestVersion;

            if (currentLoadTask != null && currentLoadTask.isRunning()) {
                currentLoadTask.cancel();
            }

            loadingLabel.setVisible(true);
            itemList.setDisable(true);
            pathLabel.setText("Klasör: " + currentDirectory);

            currentLoadTask = new javafx.concurrent.Task<>() {
                @Override
                protected List<ExplorerItem> call() throws Exception {
                    return fileOperationService.listExplorerItems(currentDirectory, showHiddenFiles);
                }
            };

            currentLoadTask.setOnSucceeded(event -> {
                if (requestVersion != loadRequestVersion) {
                    return;
                }

                itemList.setItems(FXCollections.observableArrayList(currentLoadTask.getValue()));
                itemList.getSelectionModel().clearSelection();
                itemList.getFocusModel().focus(-1);
                itemList.setDisable(false);
                loadingLabel.setVisible(false);
                if (currentLoadTask.getValue().isEmpty()) {
                    statusUpdater.accept("Durum: Klasör boş");
                } else {
                    statusUpdater.accept("Durum: Klasör yüklendi");
                }
            });

            currentLoadTask.setOnFailed(event -> {
                if (requestVersion != loadRequestVersion) {
                    return;
                }

                itemList.setDisable(false);
                loadingLabel.setVisible(false);

                Throwable throwable = currentLoadTask.getException();
                if (throwable instanceof Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Klasör okunamadı"));
                } else {
                    statusUpdater.accept("Durum: Klasör okunamadı");
                }
            });

            currentLoadTask.setOnCancelled(event -> {
                if (requestVersion != loadRequestVersion) {
                    return;
                }

                itemList.setDisable(false);
                loadingLabel.setVisible(false);
            });

            Thread thread = new Thread(currentLoadTask);
            thread.setDaemon(true);
            thread.start();
        };

        Supplier<Path> getSelectedPath = () -> {
            ExplorerItem selected = itemList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                return selected.getPath();
            }
            return openedFileGetter.get();
        };

        Consumer<List<Path>> importDroppedPaths = droppedPaths -> {
            if (!canDragDropImport) {
                statusUpdater.accept("Durum: Drag & Drop import izni kapalı");
                return;
            }

            Path targetDirectory = currentDirectoryGetter.get();
            if (targetDirectory == null || !Files.exists(targetDirectory) || !Files.isDirectory(targetDirectory)) {
                statusUpdater.accept("Durum: Import hedef klasörü geçersiz");
                return;
            }

            javafx.concurrent.Task<FileOperationService.ImportResult> importTask = new javafx.concurrent.Task<>() {
                @Override
                protected FileOperationService.ImportResult call() throws Exception {
                    return fileOperationService.importPaths(droppedPaths, targetDirectory, explorerOptions);
                }
            };

            importTask.setOnRunning(event -> {
                loadingLabel.setVisible(true);
                itemList.setDisable(true);
                statusUpdater.accept("Durum: Import ediliyor...");
            });

            importTask.setOnSucceeded(event -> {
                loadingLabel.setVisible(false);
                itemList.setDisable(false);
                refreshCurrentDirectory.run();
                statusUpdater.accept(importTask.getValue().toStatusMessage());
            });

            importTask.setOnFailed(event -> {
                loadingLabel.setVisible(false);
                itemList.setDisable(false);

                Throwable throwable = importTask.getException();
                if (throwable instanceof Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Import başarısız"));
                } else {
                    statusUpdater.accept("Durum: Import başarısız");
                }
            });

            Thread thread = new Thread(importTask);
            thread.setDaemon(true);
            thread.start();
        };

        middlePane.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (canDragDropImport && dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        middlePane.setOnDragEntered(event -> {
            Dragboard dragboard = event.getDragboard();
            if (canDragDropImport && dragboard.hasFiles()) {
                applyDropAreaStyle(dragDropImportLabel, true, true);
                dragDropImportLabel.setText("Bırakınca geçerli klasöre import edilecek: " + currentDirectoryGetter.get());
            }
            event.consume();
        });

        middlePane.setOnDragExited(event -> {
            applyDropAreaStyle(dragDropImportLabel, false, canDragDropImport);
            dragDropImportLabel.setText(canDragDropImport
                    ? "Dosya veya klasörleri buraya sürükleyip bırak."
                    : "Bu klasöre sürükleyerek dosya ekleme kapalı.");
            event.consume();
        });

        middlePane.setOnDragDropped(event -> {
            boolean completed = false;
            Dragboard dragboard = event.getDragboard();

            if (canDragDropImport && dragboard.hasFiles()) {
                List<Path> droppedPaths = dragboard.getFiles()
                        .stream()
                        .map(java.io.File::toPath)
                        .toList();
                importDroppedPaths.accept(droppedPaths);
                completed = true;
            } else if (!canDragDropImport) {
                statusUpdater.accept("Durum: Drag & Drop import izni kapalı");
            }

            applyDropAreaStyle(dragDropImportLabel, false, canDragDropImport);
            dragDropImportLabel.setText(canDragDropImport
                    ? "Dosya veya klasörleri buraya sürükleyip bırak."
                    : "Bu klasöre sürükleyerek dosya ekleme kapalı.");

            event.setDropCompleted(completed);
            event.consume();
        });

        favoriteList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.getPath() == null || newVal.getPath().isBlank()) {
                return;
            }

            try {
                Path selectedPath = Paths.get(newVal.getPath());

                if (!selectedPath.isAbsolute()) {
                    selectedPath = projectRootSupplier.get().resolve(newVal.getPath()).normalize();
                }

                if (!Files.exists(selectedPath) || !Files.isDirectory(selectedPath)) {
                    statusUpdater.accept("Durum: Favori klasör bulunamadı");
                    return;
                }

                rootDirectorySetter.accept(selectedPath);
                currentDirectorySetter.accept(selectedPath);
                openedFileSetter.accept(null);
                editorArea.clear();
                showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");

                refreshCurrentDirectory.run();
                statusUpdater.accept("Durum: " + newVal.getName() + " açıldı");
            } catch (Exception ex) {
                statusUpdater.accept(readableErrorMessage(ex, "Durum: Favori açılamadı"));
            }
        });

        itemList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) {
                return;
            }

            ExplorerItem selected = itemList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }

            try {
                if (selected.isDirectory()) {
                    currentDirectorySetter.accept(selected.getPath());
                    openedFileSetter.accept(null);
                    editorArea.clear();
                    showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");
                    refreshCurrentDirectory.run();
                    statusUpdater.accept("Durum: Alt klasör açıldı");
                } else {
                    Path file = selected.getPath();
                    openFileInPreview(
                            file,
                            module,
                            fileOperationService,
                            previewService,
                            statusUpdater,
                            previewContainer,
                            previewInfoLabel,
                            imageView,
                            canEditFiles,
                            showPreviewPane
                    );
                }
            } catch (Exception ex) {
                statusUpdater.accept(readableErrorMessage(ex, "Durum: Öğe açılamadı"));
            }
        });

        backButton.setOnAction(e -> {
            Path currentDirectory = currentDirectoryGetter.get();
            Path rootDirectory = rootDirectoryGetter.get();

            if (currentDirectory != null && !currentDirectory.equals(rootDirectory)) {
                currentDirectorySetter.accept(currentDirectory.getParent());
                openedFileSetter.accept(null);
                editorArea.clear();
                showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");
                refreshCurrentDirectory.run();
            }
        });

        refreshButton.setOnAction(e -> refreshCurrentDirectory.run());

        newFolderButton.setOnAction(e -> {
            if (!canCreateFolder) {
                statusUpdater.accept("Durum: Klasör oluşturma izni kapalı");
                return;
            }

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Yeni Klasör");
            dialog.setHeaderText("Yeni klasör adı");
            dialog.setContentText("Klasör adı:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                try {
                    if (name.isBlank()) {
                        statusUpdater.accept("Durum: Geçersiz klasör adı");
                        return;
                    }

                    fileOperationService.createFolder(currentDirectoryGetter.get(), name.trim(), explorerOptions);
                    refreshCurrentDirectory.run();
                    statusUpdater.accept("Durum: Klasör oluşturuldu");
                } catch (Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Klasör oluşturulamadı"));
                }
            });
        });

        newFileButton.setOnAction(e -> {
            if (!canCreateFile) {
                statusUpdater.accept("Durum: Dosya oluşturma izni kapalı");
                return;
            }

            TextInputDialog dialog = new TextInputDialog("yeni_not.txt");
            dialog.setTitle("Yeni Dosya");
            dialog.setHeaderText("Yeni dosya adı");
            dialog.setContentText("Dosya adı:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                try {
                    if (name.isBlank()) {
                        statusUpdater.accept("Durum: Geçersiz dosya adı");
                        return;
                    }

                    fileOperationService.createEmptyFile(currentDirectoryGetter.get(), name.trim(), explorerOptions);
                    refreshCurrentDirectory.run();
                    statusUpdater.accept("Durum: Dosya oluşturuldu");
                } catch (Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Dosya oluşturulamadı"));
                }
            });
        });

        renameButton.setOnAction(e -> {
            if (!canRename) {
                statusUpdater.accept("Durum: Yeniden adlandırma izni kapalı");
                return;
            }

            Path selectedPath = getSelectedPath.get();

            if (selectedPath == null || !Files.exists(selectedPath)) {
                statusUpdater.accept("Durum: Yeniden adlandırılacak öğe seçilmedi");
                return;
            }

            TextInputDialog dialog = new TextInputDialog(selectedPath.getFileName().toString());
            dialog.setTitle("Yeniden Adlandır");
            dialog.setHeaderText("Yeni adı gir");
            dialog.setContentText("Yeni ad:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newName -> {
                try {
                    if (newName.isBlank()) {
                        statusUpdater.accept("Durum: Geçersiz ad");
                        return;
                    }

                    fileOperationService.rename(selectedPath, newName.trim(), explorerOptions);
                    openedFileSetter.accept(null);
                    editorArea.clear();
                    showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");
                    refreshCurrentDirectory.run();
                    statusUpdater.accept("Durum: Öğe yeniden adlandırıldı");
                } catch (Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Yeniden adlandırma başarısız"));
                }
            });
        });

        deleteButton.setOnAction(e -> {
            if (!canDelete) {
                statusUpdater.accept("Durum: Silme izni kapalı");
                return;
            }

            Path selectedPath = getSelectedPath.get();

            if (selectedPath == null || !Files.exists(selectedPath)) {
                statusUpdater.accept("Durum: Silinecek öğe seçilmedi");
                return;
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Silme Onayı");
            alert.setHeaderText("Seçili öğe silinecek");
            alert.setContentText(selectedPath.getFileName().toString() + " silinsin mi?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    fileOperationService.delete(selectedPath, explorerOptions);
                    openedFileSetter.accept(null);
                    editorArea.clear();
                    showInfoPreview(previewContainer, previewInfoLabel, "Henüz dosya açılmadı");
                    refreshCurrentDirectory.run();
                    statusUpdater.accept("Durum: Öğe silindi");
                } catch (Exception ex) {
                    statusUpdater.accept(readableErrorMessage(ex, "Durum: Silme başarısız"));
                }
            }
        });

        openExternalButton.setOnAction(e -> {
            Path selectedPath = getSelectedPath.get();

            if (selectedPath == null || !Files.exists(selectedPath)) {
                statusUpdater.accept("Durum: Açılacak öğe seçilmedi");
                return;
            }

            try {
                DesktopIntegration.open(selectedPath);
                statusUpdater.accept("Durum: Dış uygulamada açıldı");
            } catch (Exception ex) {
                statusUpdater.accept(readableErrorMessage(ex, "Durum: Harici uygulama açılamadı"));
            }
        });

        saveFileButton.setOnAction(e -> {
            if (!canEditFiles) {
                statusUpdater.accept("Durum: Salt okunur modda dosya kaydedilemez");
                return;
            }

            if (openedFileGetter.get() == null) {
                statusUpdater.accept("Durum: Kaydedilecek açık dosya yok");
                return;
            }
            saveOpenedFileAction.run();
        });

        refreshCurrentDirectory.run();
        return splitPane;
    }

    private void openFileInPreview(
            Path file,
            ModuleItem module,
            FileOperationService fileOperationService,
            PreviewService previewService,
            Consumer<String> statusUpdater,
            VBox previewContainer,
            Label previewInfoLabel,
            javafx.scene.image.ImageView imageView,
            boolean canEditFiles,
            boolean showPreviewPane
    ) throws Exception {
        openedFileSetter.accept(file);

        if (!showPreviewPane) {
            statusUpdater.accept("Durum: Dosya seçildi, önizleme paneli config ile kapalı");
            return;
        }

        PreviewOptions previewOptions = module.getPreviewOptions();
        PreviewService.PreviewDecision decision = previewService.decide(file, previewOptions);
        String metadataText = previewService.buildMetadataText(file);
        boolean showMetadata = previewOptions == null || previewOptions.isShowFileMetadata();

        switch (decision.getMode()) {
            case EDITABLE_TEXT:
                String content = fileOperationService.readTextFile(file);
                if (showMetadata) {
                    Label metadataLabel = new Label(metadataText);
                    metadataLabel.setWrapText(true);
                    metadataLabel.getStyleClass().addAll("soluk", "metadata");
                    previewContainer.getChildren().setAll(metadataLabel, editorArea);
                    VBox.setVgrow(editorArea, Priority.ALWAYS);
                    editorArea.setEditable(canEditFiles);
                    editorArea.setText(content);
        VBox.setVgrow(editorArea, Priority.ALWAYS);
                } else {
                    showTextPreview(previewContainer, editorArea, content, canEditFiles);
                }
                statusUpdater.accept(canEditFiles ? "Durum: Metin dosyası açıldı" : "Durum: Metin dosyası salt okunur açıldı");
                break;

            case IMAGE:
                showImagePreview(previewContainer, imageView, file, showMetadata ? metadataText : "");
                statusUpdater.accept("Durum: Görsel önizleniyor");
                break;

            case PDF:
                showFileInfoPreview(
                        previewContainer,
                        file,
                        "PDF Önizleme",
                        decision.getMessage() + " PDF için harici görüntüleyici önerilir.",
                        metadataText,
                        true
                );
                statusUpdater.accept("Durum: PDF bilgi kartı gösteriliyor");
                break;

            case EXTERNAL:
                showFileInfoPreview(
                        previewContainer,
                        file,
                        "Harici Açılacak Dosya",
                        decision.getMessage(),
                        metadataText,
                        true
                );
                DesktopIntegration.open(file);
                statusUpdater.accept("Durum: Dosya harici görüntüleyicide açıldı");
                break;

            case UNSUPPORTED:
            default:
                showFileInfoPreview(
                        previewContainer,
                        file,
                        "Desteklenmeyen Önizleme",
                        decision.getMessage(),
                        metadataText,
                        false
                );
                statusUpdater.accept("Durum: Desteklenmeyen dosya türü");
                break;
        }
    }

}