package org.example;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App extends Application {

    private BorderPane root;
    private VBox centerArea;
    private Label statusLabel;
    private WebView webView;
    private TextArea notesArea;
    private TextArea webNotesArea;
    private final WebNotesSession webNotesSession = new WebNotesSession();
    private final PauseTransition notesSaveDelay = new PauseTransition(Duration.millis(500));
    private AppConfig appConfig;
    private Path currentNotesRoot;
    private Path currentNotesDirectory;
    private Path currentOpenedFile;

    private final ScriptModuleRunner scriptModuleRunner = new ScriptModuleRunner();
    private final WebModuleView webModuleView = new WebModuleView();
    private final SidebarBuilder sidebarBuilder = new SidebarBuilder();
    private final UartConsoleView uartConsoleView = new UartConsoleView();
    private final ReferenceLibraryView referenceLibraryView = new ReferenceLibraryView();
    private final CalendarModuleView calendarModuleView = new CalendarModuleView();
    private final FileOperationService fileOperationService = new FileOperationService();
    private final PreviewService previewService = new PreviewService();
    private String configErrorMessage = null;

    private ModuleActionHandler moduleActionHandler;
    private ModuleItem currentWebModule;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        moduleActionHandler = new ModuleActionHandler(
                scriptModuleRunner,
                uartConsoleView,
                referenceLibraryView,
                calendarModuleView,
                getProjectRoot()
        );

        try {
            ConfigLoader configLoader = new ConfigLoader();
            appConfig = configLoader.load();
            configErrorMessage = null;
        } catch (Exception e) {
            appConfig = null;
            configErrorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            e.printStackTrace();
        }

        Label titleLabel = new Label("Berkay Academic OS");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        HBox topBar = new HBox(titleLabel);
        topBar.setPadding(new Insets(15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #2b2b2b;");

        VBox sidebar = sidebarBuilder.build(appConfig, this::handleModuleAction);

        centerArea = new VBox(new Label("Modül seçiniz"));
        centerArea.setAlignment(Pos.CENTER);
        centerArea.setStyle("-fx-background-color: white;");
        VBox.setVgrow(centerArea, Priority.ALWAYS);

        notesArea = new TextArea();
        notesArea.setWrapText(true);
        notesArea.setPromptText("Dosya içeriği...");
        webNotesArea = new TextArea();
        webNotesArea.setWrapText(true);
        webNotesArea.setPromptText("Bu siteye özel notlarını buraya yaz...");
        webNotesArea.textProperty().addListener((obs, oldText, newText) -> {
            if (currentWebModule != null) {
                webNotesSession.edit(newText);
                notesSaveDelay.playFromStart();
            }
        });
        notesSaveDelay.setOnFinished(event -> saveNotesForCurrentModule());

        String defaultStatus = "Hazır";
        if (appConfig != null && appConfig.getUi() != null
                && appConfig.getUi().getDefaultStatus() != null
                && !appConfig.getUi().getDefaultStatus().isBlank()) {
            defaultStatus = appConfig.getUi().getDefaultStatus();
        }

        statusLabel = new Label("Durum: " + defaultStatus);
        HBox bottomBar = new HBox(statusLabel);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setStyle("-fx-background-color: #e9e9e9;");

        if (appConfig == null) {
            statusLabel.setText("Durum: Config yüklenemedi -> " +
                    (configErrorMessage == null ? "Bilinmeyen hata" : configErrorMessage));
        }

        root.setTop(topBar);
        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setPrefWidth(sidebar.getPrefWidth() + 2);
        root.setLeft(sidebarScroll);
        root.setCenter(centerArea);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("Berkay Academic OS");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            try {
                leaveCurrentModule();
            } catch (Exception ex) {
                event.consume();
                statusLabel.setText("Durum: Notlar kaydedilemedi, pencere açık tutuldu: " + ex.getMessage());
            }
        });
        stage.show();
    }

    private void leaveCurrentModule() throws Exception {
        notesSaveDelay.stop();
        webNotesSession.close();
        currentWebModule = null;
        moduleActionHandler.cancelScript();
        if (webView != null) {
            webView.getEngine().getLoadWorker().cancel();
            webView.getEngine().load(null);
        }
    }

    @Override
    public void stop() throws Exception {
        leaveCurrentModule();
    }

    private Path getProjectRoot() {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        if (workingDir.getFileName() != null && workingDir.getFileName().toString().equals("app")) {
            return workingDir.getParent();
        }
        return workingDir;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_çÇğĞıİöÖşŞüÜ]", "_");
    }

    private Path getModuleRootPath(ModuleItem module) {
        if (module.getDataSources() != null
                && module.getDataSources().getLocal() != null
                && module.getDataSources().getLocal().isEnabled()
                && module.getDataSources().getLocal().getPath() != null
                && !module.getDataSources().getLocal().getPath().isBlank()) {
            return getProjectRoot().resolve(module.getDataSources().getLocal().getPath()).normalize();
        }

        if (module.getTarget() != null && !module.getTarget().isBlank()) {
            return getProjectRoot().resolve(module.getTarget()).normalize();
        }

        return null;
    }

    private void saveOpenedTextFile() {
        try {
            if (currentOpenedFile == null) {
                statusLabel.setText("Durum: Açık dosya yok");
                return;
            }
            fileOperationService.saveTextFile(currentOpenedFile, notesArea.getText());
            statusLabel.setText("Durum: Dosya kaydedildi");
        } catch (Exception e) {
            statusLabel.setText("Durum: Kaydetme hatası");
            e.printStackTrace();
        }
    }

    private Path getNotesFilePath(ModuleItem module) {
        String notesDirName = "data/notes";
        if (appConfig != null && appConfig.getStorage() != null && appConfig.getStorage().getNotesDir() != null) {
            notesDirName = appConfig.getStorage().getNotesDir();
        }
        return getProjectRoot().resolve(notesDirName).resolve(sanitizeFileName(module.getName()) + ".txt");
    }

    private void saveNotesForCurrentModule() {
        if (currentWebModule == null) return;
        notesSaveDelay.stop();
        try {
            webNotesSession.save();
            statusLabel.setText("Durum: Notlar kaydedildi");
        } catch (Exception ex) {
            statusLabel.setText("Durum: Notlar kaydedilemedi: " + ex.getMessage());
        }
    }

    private void showWebWithNotes(ModuleItem module) throws Exception {
        if (module.getTarget() == null || module.getTarget().isBlank()) {
            showTextInCenter("Web hedefi tanımlanmamış");
            statusLabel.setText("Durum: Hata");
            return;
        }

        String text = webNotesSession.open(getNotesFilePath(module));
        webNotesArea.setText(text);
        currentWebModule = module;
        if (webView == null) {
            webView = new WebView();
            VBox.setVgrow(webView, Priority.ALWAYS);
        }
        SplitPane splitPane = webModuleView.build(
                module, webView, webNotesArea, this::saveNotesForCurrentModule,
                () -> {
                    webNotesArea.clear();
                    saveNotesForCurrentModule();
                }
        );
        showNodeInCenter(splitPane);
        statusLabel.setText("Durum: " + module.getName() + " açık");
    }

    private Path resolveExplorerRoot(ModuleItem module) {
        if (module.getExplorerOptions() != null
                && module.getExplorerOptions().getDefaultRoot() != null
                && !module.getExplorerOptions().getDefaultRoot().isBlank()) {
            Path configuredRoot = Paths.get(module.getExplorerOptions().getDefaultRoot());
            if (!configuredRoot.isAbsolute()) {
                configuredRoot = getProjectRoot().resolve(module.getExplorerOptions().getDefaultRoot()).normalize();
            }
            return configuredRoot;
        }
        return getModuleRootPath(module);
    }

    private void showFileExplorer(ModuleItem module) {
        try {
            Path rootPath = resolveExplorerRoot(module);
            if (rootPath == null) {
                showTextInCenter("Belge merkezi kök klasörü tanımlanmamış");
                statusLabel.setText("Durum: Hata");
                return;
            }

            Files.createDirectories(rootPath);
            currentNotesRoot = rootPath;
            currentNotesDirectory = rootPath;
            currentOpenedFile = null;

            FileExplorerView view = new FileExplorerView(
                    notesArea,
                    path -> currentOpenedFile = path,
                    () -> currentOpenedFile,
                    path -> currentNotesDirectory = path,
                    () -> currentNotesDirectory,
                    path -> currentNotesRoot = path,
                    () -> currentNotesRoot
            );

            SplitPane pane = view.build(
                    module,
                    rootPath,
                    fileOperationService,
                    previewService,
                    this::saveOpenedTextFile,
                    text -> statusLabel.setText(text),
                    this::getProjectRoot
            );

            showNodeInCenter(pane);
            statusLabel.setText("Durum: " + module.getName() + " açık");
        } catch (Exception e) {
            showTextInCenter("Belge merkezi yüklenemedi: " + e.getMessage());
            statusLabel.setText("Durum: Hata");
            e.printStackTrace();
        }
    }

    private void showTextInCenter(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 18px;");
        showNodeInCenter(label);
    }

    private void showNodeInCenter(javafx.scene.Node node) {
        centerArea.getChildren().clear();
        centerArea.getChildren().add(node);
        centerArea.setAlignment(Pos.CENTER);
        VBox.setVgrow(node, Priority.ALWAYS);
    }

    private void handleModuleAction(ModuleItem module) {
        try {
            leaveCurrentModule();
        } catch (Exception ex) {
            statusLabel.setText("Durum: Notlar kaydedilemedi, modül değişmedi: " + ex.getMessage());
            return;
        }
        try {
            String type = module.getType();
            if (type == null || type.isBlank()) {
                showTextInCenter("Modül tipi tanımsız");
                statusLabel.setText("Durum: Hata");
                return;
            }

            switch (type.toLowerCase()) {
                case "internal":
                    moduleActionHandler.handleInternalModule(module, centerArea, text -> statusLabel.setText(text), this::showTextInCenter);
                    break;
                case "notes_browser":
                case "file_explorer":
                    showFileExplorer(module);
                    break;
                case "web":
                    showWebWithNotes(module);
                    break;
                case "external_web":
                    moduleActionHandler.handleExternalWebModule(module, this::showTextInCenter, text -> statusLabel.setText(text));
                    break;
                case "script":
                    moduleActionHandler.handleScriptModule(module, centerArea, text -> statusLabel.setText(text));
                    break;
                case "calendar":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(calendarModuleView.build(getProjectRoot().resolve("data/tasks.json")));
                    centerArea.setAlignment(Pos.CENTER);
                    statusLabel.setText("Durum: Takvim açık");
                    break;
                default:
                    showTextInCenter("Bilinmeyen modül tipi: " + type);
                    statusLabel.setText("Durum: Hata");
                    break;
            }
        } catch (Exception e) {
            showTextInCenter("İşlem hatası: " + e.getMessage());
            statusLabel.setText("Durum: Hata");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}