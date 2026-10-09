package org.example;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.ListView;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class AppTest {
    @TempDir Path directory;
    private App app;
    private Stage stage;

    @BeforeAll static void startToolkit() throws Exception {
        FxTestSupport.initialize();
    }

    @BeforeEach void openApp() throws Exception {
        fx(() -> {
            app = new App();
            stage = new Stage();
            app.start(stage);
            stage.hide();
            AppConfig config = field("appConfig");
            config.getStorage().setNotesDir(directory.toString());
            return null;
        });
    }

    @AfterEach void closeApp() throws Exception {
        fx(() -> {
            if (app != null) app.stop();
            if (stage != null) { stage.close(); stage.setScene(null); }
            return null;
        });
    }

    @Test void browserIsLazyAndNotesSurviveSaveSwitchAndClose() throws Exception {
        fx(() -> {
            assertNull(field("webView"));
            open(module("first", "web"));
            assertNotNull(field("webView"));
            TextArea notes = field("webNotesArea");
            notes.setText("Birinci not");
            findButton(field("centerArea"), "Kaydet").fire();
            assertEquals("Birinci not", Files.readString(directory.resolve("first.txt")));
            notes.setText("Son değişiklik");
            open(module("second", "web"));
            assertEquals("Son değişiklik", Files.readString(directory.resolve("first.txt")));
            assertEquals("", notes.getText());
            notes.setText("İkinci not");
            app.stop();
            assertEquals("İkinci not", Files.readString(directory.resolve("second.txt")));
            return null;
        });
    }

    @Test void fileEditorCannotOverwriteWebNotes() throws Exception {
        fx(() -> {
            open(module("webnotes", "web"));
            TextArea notes = field("webNotesArea");
            notes.setText("Web içeriği");
            TextArea fileEditor = field("notesArea");
            fileEditor.setText("Başka dosyanın içeriği");
            open(module("other", "internal"));
            assertEquals("Web içeriği", Files.readString(directory.resolve("webnotes.txt")));
            return null;
        });
    }

    @Test void failedSaveBlocksNavigationAndRetainsTextForRetry() throws Exception {
        fx(() -> {
            open(module("blocked", "web"));
            TextArea notes = field("webNotesArea");
            notes.setText("Kaybolmamalı");
            Path blocked = Files.createDirectory(directory.resolve("blocked.txt"));
            VBox center = field("centerArea");
            Object currentScreen = center.getChildren().getFirst();
            open(module("other", "internal"));
            assertSame(currentScreen, center.getChildren().getFirst());
            Label status = field("statusLabel");
            assertTrue(status.getText().contains("kaydedilemedi"));
            Files.delete(blocked);
            open(module("other", "internal"));
            assertEquals("Kaybolmamalı", Files.readString(blocked));
            return null;
        });
    }

    @Test void longScriptDoesNotBlockNavigationOrReplaceNextScreen() throws Exception {
        Path script = directory.resolve("long.py");
        Path pidFile = directory.resolve("pid.txt");
        Files.writeString(script, "import os, time\nfrom pathlib import Path\nPath(r'" + pidFile + "').write_text(str(os.getpid()))\nprint('Çalışıyor', flush=True)\ntime.sleep(60)\n");
        fx(() -> {
            ModuleItem module = module("long", "script");
            module.setTarget(script.toString());
            open(module);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(pidFile) && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(Files.exists(pidFile));
        long pid = Long.parseLong(Files.readString(pidFile));
        fx(() -> {
            open(module("next", "internal"));
            return null;
        });
        var process = ProcessHandle.of(pid);
        if (process.isPresent() && process.get().isAlive()) process.get().onExit().get(5, TimeUnit.SECONDS);
        fx(() -> {
            VBox center = field("centerArea");
            assertEquals("next", ((Label) center.getChildren().getFirst()).getText());
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
            return null;
        });
    }

    @Test void notesAutosaveAfterTypingStops() throws Exception {
        CountDownLatch saved = new CountDownLatch(1);
        fx(() -> {
            open(module("auto", "web"));
            Label status = field("statusLabel");
            status.textProperty().addListener((obs, oldText, text) -> {
                if ("Durum: Notlar kaydedildi".equals(text)) saved.countDown();
            });
            TextArea notes = field("webNotesArea");
            notes.setText("Otomatik kayıt");
            return null;
        });
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        assertEquals("Otomatik kayıt", Files.readString(directory.resolve("auto.txt")));
    }

    @Test void failedTaskSaveDoesNotChangeDisplayedDataOrReportSuccess() throws Exception {
        Path file = directory.resolve("tasks.json");
        new TaskStorageService(file).saveTasks(List.of(new TaskItem("Görev", LocalDate.now(), "", "Bekliyor")));
        Path backup = Files.createDirectory(directory.resolve("tasks.json.bak"));
        Files.writeString(backup.resolve("keep"), "keep");
        fx(() -> {
            CalendarModuleView view = new CalendarModuleView();
            view.build(file);
            Tab tab = readField(view, "taskTab");
            ListView<TaskItem> list = readField(view, "taskListView");
            list.getSelectionModel().selectFirst();
            findButton((Parent) tab.getContent(), "Tamamlandı Yap").fire();
            assertEquals("Bekliyor", list.getItems().getFirst().getStatus());
            Label status = readField(view, "taskInfoLabel");
            assertTrue(status.getText().contains("kaydedilemedi"));
            findButton((Parent) tab.getContent(), "Sil").fire();
            assertEquals(1, list.getItems().size());
            assertTrue(status.getText().contains("kaydedilemedi"));
            return null;
        });
        assertEquals("Bekliyor", new TaskStorageService(file).loadTasks().getFirst().getStatus());
    }

    @Test void searchNavigationAndThemePreferenceWorkWithNewShell() throws Exception {
        fx(() -> {
            var root = stage.getScene().getRoot();
            var search = (javafx.scene.control.TextField) root.lookup("#module-search");
            search.setText("TAKVİM");
            var calendar = (javafx.scene.control.ToggleButton) root.lookup("#nav-calendar");
            var documents = (javafx.scene.control.ToggleButton) root.lookup("#nav-documents");
            assertTrue(calendar.isVisible());
            assertFalse(documents.isManaged());
            search.setText("eşleşmeyen-modül");
            assertFalse(calendar.isVisible());
            search.clear();
            assertTrue(documents.isManaged());

            UiTheme testTheme = new UiTheme(directory.resolve("ui.properties"), "light");
            Field field = App.class.getDeclaredField("theme");
            field.setAccessible(true);
            field.set(app, testTheme);
            var toggle = (javafx.scene.control.ToggleButton) root.lookup("#theme-toggle");
            toggle.setSelected(false);
            toggle.fire();
            assertTrue(root.getStyleClass().contains("dark"));
            assertTrue(new UiTheme(directory.resolve("ui.properties"), "light").isDark());
            toggle.fire();
            assertFalse(root.getStyleClass().contains("dark"));
            assertFalse(new UiTheme(directory.resolve("ui.properties"), "dark").isDark());

            Files.delete(directory.resolve("ui.properties"));
            Files.createDirectory(directory.resolve("ui.properties"));
            toggle.fire();
            assertFalse(toggle.isSelected());
            assertFalse(root.getStyleClass().contains("dark"));
            return null;
        });
    }

    @Test void redesignedExplorerKeepsRealEditingAndCollapsiblePanes() throws Exception {
        Files.writeString(directory.resolve("ders-notlari.md"), "# Çalışma notları\n\nBugünkü hedefler\n");
        Files.createDirectory(directory.resolve("Dersler"));
        fx(() -> {
            ModuleItem module = module("Belgeler", "file_explorer");
            module.setId("documents");
            module.setTarget(directory.toString());
            FileExplorerOptions options = new FileExplorerOptions();
            options.setShowPreviewPane(true);
            options.setAllowCreateFolder(true);
            options.setAllowCreateFile(true);
            options.setAllowRename(true);
            options.setAllowDelete(true);
            options.setAllowDragDropImport(true);
            module.setExplorerOptions(options);
            FavoriteLocation favorite = new FavoriteLocation();
            favorite.setName("Çalışma notları");
            favorite.setPath(directory.toString());
            module.setFavorites(List.of(favorite));
            open(module);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> ((ListView<?>) stage.getScene().getRoot().lookup(".file-list")).getItems().isEmpty())
                && System.nanoTime() < deadline) Thread.sleep(20);
        fx(() -> {
            @SuppressWarnings("unchecked")
            ListView<ExplorerItem> files = (ListView<ExplorerItem>) stage.getScene().getRoot().lookup(".file-list");
            ExplorerItem note = files.getItems().stream().filter(item -> !item.isDirectory()).findFirst().orElseThrow();
            files.getSelectionModel().select(note);
            files.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 2, false, false, false, false,
                    false, false, false, false, false, true, null));
            TextArea editor = field("notesArea");
            assertTrue(editor.getText().contains("Çalışma notları"));
            editor.setText("# Çalışma notları\n\n- Devre analizi\n- Laboratuvar raporu\n");
            findButton(field("centerArea"), "Dosyayı Kaydet").fire();
            assertEquals(editor.getText(), Files.readString(note.getPath()));
            VBox center = field("centerArea");
            SplitPane split = (SplitPane) center.getChildren().getFirst();
            var toggles = split.lookupAll(".toggle-button").stream()
                    .filter(node -> node instanceof javafx.scene.control.ToggleButton)
                    .map(node -> (javafx.scene.control.ToggleButton) node).toList();
            assertEquals(2, toggles.size());
            toggles.forEach(javafx.scene.control.ToggleButton::fire);
            assertEquals(1, split.getItems().size());
            toggles.forEach(javafx.scene.control.ToggleButton::fire);
            assertEquals(3, split.getItems().size());
            assertTrue(editor.getText().contains("Devre analizi"));
            snapshot("design-documents-light", 1440, 900, false);
            snapshot("design-documents-dark", 1440, 900, true);
            snapshot("design-documents-compact", 1000, 680, false);
            return null;
        });
    }

    @Test void nativeModuleLayoutsRenderInBothThemes() throws Exception {
        fx(() -> {
            VBox center = field("centerArea");
            var calendar = new CalendarModuleView().build(directory.resolve("tasks.json"));
            center.getChildren().setAll(calendar);
            VBox.setVgrow(calendar, javafx.scene.layout.Priority.ALWAYS);
            snapshot("design-calendar", 1440, 900, false);
            var tabs = (javafx.scene.control.TabPane) calendar.getChildren().getFirst();
            tabs.getSelectionModel().select(1);
            snapshot("design-tasks", 1000, 680, true);
            var reference = new ReferenceLibraryView().build(directory.resolve("reference.json"));
            center.getChildren().setAll(reference);
            VBox.setVgrow(reference, javafx.scene.layout.Priority.ALWAYS);
            snapshot("design-reference", 1440, 900, false);
            var uart = new UartConsoleView().build();
            center.getChildren().setAll(uart);
            VBox.setVgrow(uart, javafx.scene.layout.Priority.ALWAYS);
            snapshot("design-uart", 1000, 680, true);
            return null;
        });
    }

    private void snapshot(String name, int width, int height, boolean dark) throws Exception {
        stage.show();
        Parent root = stage.getScene().getRoot();
        UiTheme theme = new UiTheme(directory.resolve("preview.properties"), dark ? "dark" : "light");
        theme.apply(root);
        root.resize(width, height);
        root.applyCss();
        root.layout();
        root.applyCss();
        root.layout();
        var image = root.snapshot(null, null);
        var pixels = image.getPixelReader();
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++)
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, pixels.getArgb(x, y));
        Path output = Path.of("build/reports/" + name + ".png");
        Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(png, "png", output.toFile());
        assertTrue(width >= root.getLayoutBounds().getWidth());
    }

    private ModuleItem module(String name, String type) {
        ModuleItem module = new ModuleItem();
        module.setId(name);
        module.setName(name);
        module.setMessage(name);
        module.setType(type);
        module.setTarget("about:blank");
        return module;
    }

    private void open(ModuleItem module) throws Exception {
        var method = App.class.getDeclaredMethod("handleModuleAction", ModuleItem.class);
        method.setAccessible(true);
        method.invoke(app, module);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        return readField(app, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private Button findButton(Parent parent, String text) {
        var children = parent instanceof SplitPane split ? split.getItems()
                : parent instanceof javafx.scene.control.ScrollPane scroll ? List.of(scroll.getContent())
                : parent.getChildrenUnmodifiable();
        for (var node : children) {
            if (node instanceof Button button && text.equals(button.getText())) return button;
            if (node instanceof Parent child) {
                Button found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
