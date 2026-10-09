package org.example;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.FlowPane;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
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
    private Label pageTitle;
    private Label pageDescription;
    private ModuleItem selectedModule;
    private UiTheme theme;
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
                getProjectRoot(),
                this::openModuleById
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

        UiTheme.loadFonts();
        theme = new UiTheme(getProjectRoot().resolve("data/ui.properties"),
                appConfig != null && appConfig.getApp() != null ? appConfig.getApp().getTheme() : "light");
        theme.apply(root);
        root.getStyleClass().add("app-shell");

        pageTitle = new Label("Çalışma alanın");
        pageTitle.getStyleClass().add("sayfa-baslik");
        pageDescription = new Label("Belgelerin, araçların ve planların tek yerde");
        pageDescription.getStyleClass().add("soluk");
        VBox heading = new VBox(3, pageTitle, pageDescription);
        heading.setMinWidth(180);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setId("module-search");
        search.setPromptText("Modül ara…  Ctrl+K");
        search.setAccessibleText("Modül ara");
        search.setPrefWidth(245);
        search.setMinWidth(150);
        search.textProperty().addListener((obs, oldValue, value) -> sidebarBuilder.filter(value));
        search.setOnAction(e -> sidebarBuilder.openFirstMatch());
        HBox topBar = new HBox(16, heading, spacer, search);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("ust-cubuk");

        VBox sidebar = sidebarBuilder.build(appConfig, this::handleModuleAction);
        ToggleButton themeSwitch = new ToggleButton("Koyu tema");
        themeSwitch.setId("theme-toggle");
        themeSwitch.setSelected(theme.isDark());
        UiTheme.decorate(themeSwitch, "moon", "theme-toggle");
        themeSwitch.setMaxWidth(Double.MAX_VALUE);
        themeSwitch.setOnAction(e -> {
            try {
                theme.setDark(themeSwitch.isSelected(), root);
            } catch (Exception ex) {
                themeSwitch.setSelected(theme.isDark());
                statusLabel.setText("Durum: Tema tercihi kaydedilemedi: " + ex.getMessage());
            }
        });
        VBox themeRow = new VBox(themeSwitch);
        themeRow.getStyleClass().add("tema-satiri");
        sidebar.getChildren().add(themeRow);

        centerArea = new VBox();
        centerArea.setAlignment(Pos.TOP_LEFT);
        centerArea.getStyleClass().add("module-content");
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
        statusLabel.getStyleClass().add("durum-metni");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        Label localBadge = new Label("Kişisel çalışma alanı", UiTheme.icon("grid"));
        localBadge.getStyleClass().add("durum-rozet");
        HBox bottomBar = new HBox(12, statusLabel, localBadge);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.getStyleClass().add("durum-cubugu");

        if (appConfig == null) {
            statusLabel.setText("Durum: Config yüklenemedi -> " +
                    (configErrorMessage == null ? "Bilinmeyen hata" : configErrorMessage));
        }

        VBox workspace = new VBox(topBar, centerArea, bottomBar);
        root.setLeft(sidebar);
        root.setCenter(workspace);
        showWelcome();

        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(1440, bounds.getWidth() - 40),
                Math.min(900, bounds.getHeight() - 60));
        scene.getStylesheets().add(App.class.getResource("/ui/style.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.K) {
                search.requestFocus();
                search.selectAll();
                event.consume();
            }
            if (event.getCode() == KeyCode.ESCAPE && search.isFocused()) search.clear();
        });
        stage.setMinWidth(Math.min(900, bounds.getWidth()));
        stage.setMinHeight(Math.min(600, bounds.getHeight()));
        stage.setTitle("Berkay Academic OS");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            try {
                leaveCurrentModule();
            } catch (Exception ex) {
                event.consume();
                statusLabel.setText("Durum: Değişiklikler kaydedilemedi, pencere açık tutuldu: " + ex.getMessage());
            }
        });
        stage.show();
        if (appConfig != null) openModuleById("today");
    }

    private void leaveCurrentModule() throws Exception {
        notesSaveDelay.stop();
        webNotesSession.close();
        currentWebModule = null;
        moduleActionHandler.leaveModule();
        webModuleView.dispose();
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
        centerArea.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(node, Priority.ALWAYS);
    }

    private void openModuleById(String id) {
        if (appConfig == null || appConfig.getModules() == null) return;
        var module = appConfig.getModules().stream().filter(m -> m.isEnabled() && id.equals(m.getId())).findFirst();
        if (module.isPresent()) handleModuleAction(module.get());
        else statusLabel.setText("Durum: Bu modül etkin değil: " + id);
    }

    private void handleModuleAction(ModuleItem module) {
        try {
            leaveCurrentModule();
        } catch (Exception ex) {
            sidebarBuilder.select(selectedModule);
            statusLabel.setText("Durum: Değişiklikler kaydedilemedi, modül değişmedi: " + ex.getMessage());
            return;
        }
        try {
            String type = module.getType();
            if (type == null || type.isBlank()) {
                showTextInCenter("Modül tipi tanımsız");
                statusLabel.setText("Durum: Hata");
                return;
            }

            selectedModule = module;
            sidebarBuilder.select(module);
            pageTitle.setText(module.getName());
            pageDescription.setText(moduleDescription(module));
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
                    var calendar = calendarModuleView.build(getProjectRoot().resolve("data/tasks.json"));
                    centerArea.getChildren().add(calendar);
                    VBox.setVgrow(calendar, Priority.ALWAYS);
                    centerArea.setAlignment(Pos.TOP_LEFT);
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

    private void showWelcome() {
        Label title = new Label("Bugün ne üzerinde çalışıyorsun?");
        title.getStyleClass().add("welcome-title");
        Label description = new Label("Bir modül seçerek kaldığın yerden devam et.");
        description.getStyleClass().add("soluk");
        FlowPane shortcuts = new FlowPane(12, 12);
        if (appConfig != null && appConfig.getModules() != null) {
            for (ModuleItem module : appConfig.getModules()) {
                if (!module.isEnabled() || "external_web".equals(module.getType())
                        || "web".equals(module.getType())) continue;
                Button shortcut = new Button(module.getName());
                shortcut.getStyleClass().add("shortcut-card");
                shortcut.setOnAction(e -> handleModuleAction(module));
                shortcuts.getChildren().add(shortcut);
            }
        }
        VBox welcome = new VBox(12, title, description, shortcuts);
        welcome.getStyleClass().addAll("panel", "welcome");
        showNodeInCenter(welcome);
    }

    private String moduleDescription(ModuleItem module) {
        return switch (java.util.Objects.toString(module.getId(), "")) {
            case "today" -> "Günün programı, yaklaşan işler ve ödemeler";
            case "courses" -> "Derslerin, haftalık programın ve teslim tarihlerin";
            case "documents" -> "Dosyalarını düzenle, görüntüle ve yönet";
            case "uart" -> "Seri port bağlantısı ve canlı veri akışı";
            case "scripts" -> "Çalıştır, çıktıyı izle ve yönet";
            case "reference" -> "Bilgilerini ve çalışma notlarını düzenle";
            case "calendar" -> "Takvimin ve görev planın";
            case "finance" -> "Borçların, hesapların ve ödeme planın";
            default -> java.util.Objects.toString(module.getCategory(), "Çalışma alanı");
        };
    }

    public static void main(String[] args) {
        launch();
    }
}