package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

/** An offline HTML/CSS panel; only this trusted document receives the narrow Java bridge. */
public final class FinanceModuleView {
    private final VBox root = new VBox(12);
    private final VBox workspace = new VBox();
    private ScholarshipModuleView scholarships;
    private javafx.scene.Parent themeRoot;
    private final javafx.collections.ListChangeListener<String> themeListener = change -> applyPanelTheme();
    private WebView webView;
    private Bridge bridge; // Strong reference required by the WebEngine JavaScript bridge.
    private Task<String> loading;
    private boolean disposed;
    private boolean ready;

    public VBox build(Path file) {
        FinanceStorageService storage = new FinanceStorageService(file);
        scholarships = new ScholarshipModuleView(file.resolveSibling("applications.json"));
        TabPane tabs = new TabPane(new Tab("Finans", root), new Tab("Burs ve başvurular", scholarships.build()));
        tabs.setId("finance-tabs"); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspace.getChildren().setAll(tabs); VBox.setVgrow(tabs, Priority.ALWAYS);
        load(storage);
        return workspace;
    }

    private void load(FinanceStorageService storage) {
        root.setPadding(new Insets(16));
        root.getChildren().setAll(new Label("Borç Defteri yükleniyor…"), new ProgressIndicator());
        loading = new Task<>() {
            @Override protected String call() throws Exception { return storage.load(); }
        };
        loading.setOnSucceeded(event -> {
            if (!disposed) showPanel(storage, loading.getValue());
        });
        loading.setOnFailed(event -> {
            if (!disposed) showLoadError(storage, loading.getException().getMessage());
        });
        Thread.ofVirtual().name("finance-load").start(loading);
    }

    private void showLoadError(FinanceStorageService storage, String message) {
        Label error = new Label("Borç Defteri açılamadı. Mevcut kayıtlar değiştirilmedi.\n" + message);
        error.setWrapText(true);
        Button retry = new Button("Yeniden dene");
        retry.setOnAction(event -> load(storage));
        Button recover = new Button("Son yedeği geri yükle");
        recover.setDisable(!java.nio.file.Files.isRegularFile(storage.backupPath()));
        recover.setOnAction(event -> {
            if (!confirm("Son başarılı kayıt yedeği geri yüklensin mi? Mevcut dosyanın bir kurtarma kopyası da saklanacak.")) return;
            Task<Void> restore = new Task<>() {
                @Override protected Void call() throws Exception { storage.restoreBackup(); return null; }
            };
            retry.setDisable(true);
            recover.setDisable(true);
            restore.setOnSucceeded(done -> { if (!disposed) load(storage); });
            restore.setOnFailed(done -> { if (!disposed) showLoadError(storage, restore.getException().getMessage()); });
            Thread.ofVirtual().name("finance-recovery").start(restore);
        });
        root.getChildren().setAll(error, retry, recover);
    }

    private void showPanel(FinanceStorageService storage, String initialData) {
        try {
            String html = resource("index.html").replace("/* FINANCE_CSS */", fontCss() + resource("styles.css"))
                    .replace("/* FINANCE_JS */", resource("ledger.js"));
            webView = new WebView();
            webView.setContextMenuEnabled(false);
            var engine = webView.getEngine();
            bridge = new Bridge(storage, () -> root.getScene() == null ? null : root.getScene().getWindow());
            engine.setConfirmHandler(this::confirm);
            engine.setPromptHandler(prompt -> {
                TextInputDialog dialog = new TextInputDialog(prompt.getDefaultValue());
                if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
                dialog.setTitle("Borç Defteri");
                dialog.setHeaderText(prompt.getMessage());
                return dialog.showAndWait().orElse(null);
            });
            engine.setOnAlert(event -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, event.getData(), ButtonType.OK);
                if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            engine.setCreatePopupHandler(features -> null);
            engine.locationProperty().addListener((observable, oldLocation, location) -> {
                if (location != null && !location.isEmpty() && !"about:blank".equals(location)) {
                    bridge.active = false;
                    engine.setJavaScriptEnabled(false);
                    engine.getLoadWorker().cancel();
                }
            });
            engine.getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
                if (disposed) return;
                if (state == Worker.State.SUCCEEDED && !ready) {
                    try {
                        JSObject window = (JSObject) engine.executeScript("window");
                        window.setMember("financeHost", bridge);
                        String encoded = new ObjectMapper().writeValueAsString(initialData);
                        engine.executeScript("window.financeApp.initialize(" + encoded + ")");
                        ready = true;
                        if (root.getScene() != null) {
                            themeRoot = root.getScene().getRoot(); themeRoot.getStyleClass().addListener(themeListener);
                        }
                        applyPanelTheme();
                    } catch (Exception ex) {
                        bridge.active = false;
                        showLoadError(storage, "Panel başlatılamadı: " + ex.getMessage());
                    }
                } else if (state == Worker.State.FAILED) {
                    showLoadError(storage, "Panel kaynakları yüklenemedi");
                }
            });
            root.setPadding(Insets.EMPTY);
            root.getChildren().setAll(webView);
            VBox.setVgrow(webView, Priority.ALWAYS);
            engine.loadContent(html);
        } catch (IOException ex) {
            showLoadError(storage, ex.getMessage());
        }
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/finance/" + name)) {
            if (stream == null) throw new IOException("Panel kaynağı bulunamadı: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String fontCss() throws IOException {
        StringBuilder css = new StringBuilder();
        for (String face : new String[]{"Regular", "SemiBold"}) try (var stream = App.class.getResourceAsStream("/ui/fonts/Poppins-" + face + ".ttf")) {
            if (stream == null) throw new IOException("Finans yazı tipi bulunamadı.");
            css.append("@font-face{font-family:Poppins;font-weight:").append(face.equals("Regular") ? 400 : 600)
                    .append(";src:url(data:font/ttf;base64,").append(java.util.Base64.getEncoder().encodeToString(stream.readAllBytes())).append(") format('truetype');}");
        }
        return css.toString();
    }
    private void applyPanelTheme() {
        if (!ready || disposed) return;
        boolean dark = root.getScene() != null && root.getScene().getRoot().getStyleClass().contains("dark");
        webView.getEngine().executeScript("document.documentElement.classList.toggle('dark'," + dark + ")");
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
        alert.setTitle("Borç Defteri");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public void dispose() {
        if (scholarships != null) scholarships.requireIdle();
        disposed = true;
        if (scholarships != null) scholarships.dispose();
        if (themeRoot != null) themeRoot.getStyleClass().removeListener(themeListener);
        if (loading != null) loading.cancel(true);
        if (bridge != null) bridge.active = false;
        if (webView != null) {
            webView.getEngine().getLoadWorker().cancel();
            webView.getEngine().load(null);
        }
    }

    public boolean isReady() { return ready; }
    // Package visibility for UI integration tests against the actual WebKit renderer.
    WebView webView() { return webView; }

    public static final class Bridge {
        private final FinanceStorageService storage;
        private final Supplier<Window> owner;
        private final ObjectMapper mapper = new ObjectMapper();
        private boolean active = true;

        Bridge(FinanceStorageService storage, Supplier<Window> owner) {
            this.storage = storage;
            this.owner = owner;
        }

        public String save(String json) {
            return respond(() -> { storage.save(json); return Map.of("ok", true); });
        }

        public String validate(String json) {
            return respond(() -> Map.of("ok", true, "data", mapper.readTree(storage.validate(json))));
        }

        public String exportFile(String name, String content) {
            return respond(() -> {
                if (content.getBytes(StandardCharsets.UTF_8).length > FinanceStorageService.MAX_BYTES) {
                    throw new IOException("Dışa aktarım en fazla 5 MB olabilir");
                }
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Borç Defteri yedeğini kaydet");
                chooser.setInitialFileName(name.replaceAll("[^a-zA-Z0-9._-]", "_"));
                String extension = name.endsWith(".csv") ? "csv" : "json";
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.toUpperCase(), "*." + extension));
                var file = chooser.showSaveDialog(owner.get());
                if (file == null) return Map.of("ok", true, "cancelled", true);
                AtomicFileWriter.writeText(file.toPath(), content);
                return Map.of("ok", true);
            });
        }

        public String importFile() {
            return respond(() -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Borç Defteri yedeğini aç");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Defter yedeği", "*.json", "*.csv"));
                var file = chooser.showOpenDialog(owner.get());
                if (file == null) return Map.of("ok", true, "cancelled", true);
                return Map.of("ok", true, "text", new String(FinanceStorageService.readLimited(file.toPath()), StandardCharsets.UTF_8));
            });
        }

        private String respond(Operation operation) {
            try {
                if (!active) throw new IOException("Bu panel kapatıldı");
                return mapper.writeValueAsString(operation.run());
            } catch (Exception ex) {
                try { return mapper.writeValueAsString(Map.of("ok", false, "error", ex.getMessage() == null ? "İşlem tamamlanamadı" : ex.getMessage())); }
                catch (Exception ignored) { return "{\"ok\":false,\"error\":\"İşlem tamamlanamadı\"}"; }
            }
        }

        @FunctionalInterface private interface Operation { Object run() throws Exception; }
    }
}
