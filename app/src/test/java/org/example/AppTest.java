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
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
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
            if (stage != null) stage.close();
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
        var children = parent instanceof SplitPane split ? split.getItems() : parent.getChildrenUnmodifiable();
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
