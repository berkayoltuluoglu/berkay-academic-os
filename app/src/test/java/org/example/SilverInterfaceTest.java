package org.example;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class SilverInterfaceTest {
    @TempDir Path directory;
    private App app;
    private Stage stage;
    private String taskId;

    @BeforeAll static void toolkit() throws Exception { FxTestSupport.initialize(); }
    @BeforeEach void open() throws Exception {
        LocalDate date = LocalDate.now();
        var task = new TaskItem("Raporu tamamla", date, "Laboratuvar raporunu teslim et", "Bekliyor"); taskId = task.getId();
        new TaskStorageService(directory.resolve("data/tasks.json")).saveTasks(List.of(task,
                new TaskItem("Notları düzenle", date, "", "Tamamlandı"), new TaskItem("Ödemeyi kontrol et", date.plusDays(2), "", "Bekliyor")));
        var courses = new CourseStorageService(directory.resolve("data/courses/academic.json")); courses.load();
        courses.save(new CourseData(2, List.of(new CourseData.Course("circuits", "Devre Analizi", "EE201", "", "D204", date.toString(), date.plusMonths(4).toString(),
                List.of(new CourseData.Lesson(date.getDayOfWeek().getValue(), "13:00", "14:30")))), List.of()));
        new DailyAgendaService().saveSession(directory, new StudySession("project", "Proje çalışması", date.toString(), "15:00", "16:00", "", false), true);
        fx(() -> { app = new App(directory); stage = new Stage(); app.start(stage); return null; }); ready();
    }
    @AfterEach void close() throws Exception { fx(() -> { if (app != null) app.stop(); if (stage != null) stage.close(); return null; }); }
    private Parent root() { return stage.getScene().getRoot(); }
    private void ready() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            if (fx(() -> root().lookup("#today-refresh") instanceof Button button && !button.isDisabled()
                    && root().lookup(".module-content").getOpacity() > .99)) return;
            Thread.sleep(30);
        }
        fail("Günlük ekran yüklenmedi");
    }

    @Test void realShellRendersLightDarkAndCompactAndFocusSurvivesNavigation() throws Exception {
        fx(() -> {
            assertNotNull(root().lookup("#nav-agenda")); assertNotNull(root().lookup("#nav-tasks"));
            assertNotNull(root().lookup("#today-event-circuits-13-00"));
            assertNotNull(root().lookup("#today-event-project-15-00"));
            assertEquals("25:00", ((Label) root().lookup("#focus-time")).getText());
            screenshot("silver-home-light", 1440, 940);
            ((ToggleButton) root().lookup("#theme-toggle")).fire(); screenshot("silver-home-dark", 1440, 940);
            ((ToggleButton) root().lookup("#theme-toggle")).fire(); screenshot("silver-home-compact", 940, 720);
            ((Button) root().lookup("#focus-toggle")).fire(); return null;
        }); ready();
        assertTrue(new FocusTimerService(directory.resolve("data/focus.properties")).running());
        fx(() -> { ((ToggleButton) root().lookup("#nav-tasks")).fire(); return null; });
        assertTrue(new FocusTimerService(directory.resolve("data/focus.properties")).running());
        fx(() -> { ((ToggleButton) root().lookup("#nav-today")).fire(); return null; }); ready();
        fx(() -> { assertEquals("Duraklat", ((Button) root().lookup("#focus-toggle")).getText()); ((Button) root().lookup("#focus-toggle")).fire(); return null; }); ready();
        assertFalse(new FocusTimerService(directory.resolve("data/focus.properties")).running());
        fx(() -> { ((ToggleButton) root().lookup("#motion-toggle")).fire(); ((ToggleButton) root().lookup("#theme-toggle")).fire(); return null; });
        var preferences = new UiTheme(directory.resolve("data/ui.properties"), "light");
        assertTrue(preferences.isReducedMotion()); assertTrue(preferences.isDark());
    }

    @Test void checkboxCommitsTheRealTaskAndFailedSaveRollsItBack() throws Exception {
        Path file = directory.resolve("data/tasks.json");
        fx(() -> { ((CheckBox) root().lookup("#todo-task-" + taskId)).fire(); return null; }); ready();
        assertEquals("Tamamlandı", new TaskStorageService(file).loadTasks().getFirst().getStatus());
        Path backup = file.resolveSibling("tasks.json.bak"); Files.delete(backup); Files.createDirectory(backup); Files.writeString(backup.resolve("keep"), "keep");
        fx(() -> { ((CheckBox) root().lookup("#todo-task-" + taskId)).fire(); return null; }); ready();
        assertEquals("Tamamlandı", new TaskStorageService(file).loadTasks().getFirst().getStatus());
        fx(() -> { assertTrue(((CheckBox) root().lookup("#todo-task-" + taskId)).isSelected()); assertTrue(((Label) root().lookup("#today-status")).getText().contains("Kaydedilemedi")); return null; });
    }

    @Test void addMenuCreatesPersistentTaskAndStudyAndDateNavigationShowsThem() throws Exception {
        dialog(0, form -> { ((TextField) form.lookup("#quick-task-title")).setText("Yeni görev"); }); ready();
        assertEquals(4, new TaskStorageService(directory.resolve("data/tasks.json")).loadTasks().size());
        dialog(1, form -> {
            ((TextField) form.lookup("#study-title")).setText("Sınava hazırlık");
            ((TextField) form.lookup("#study-start")).setText("16:00"); ((TextField) form.lookup("#study-end")).setText("17:00");
        }); ready();
        assertEquals(2, new StudyStorageService(directory.resolve("data/study/sessions.json")).load().size());
        fx(() -> { ((ToggleButton) root().lookup("#nav-agenda")).fire(); return null; }); ready();
        fx(() -> { ((DatePicker) root().lookup("#agenda-date")).setValue(LocalDate.now().plusDays(1)); return null; }); ready();
        fx(() -> { assertNull(root().lookup("#today-event-project-15-00")); ((DatePicker) root().lookup("#agenda-date")).setValue(LocalDate.now()); return null; }); ready();
        fx(() -> { assertNotNull(root().lookup("#today-event-project-15-00")); return null; });
    }

    private void dialog(int menuItem, Consumer<Parent> fill) throws Exception {
        CompletableFuture<Void> filled = new CompletableFuture<>();
        fx(() -> {
            Platform.runLater(() -> {
                Window popup = Window.getWindows().stream().filter(w -> w != stage && w.isShowing() && w.getScene().getRoot() instanceof DialogPane).findFirst().orElseThrow();
                try {
                    DialogPane pane = (DialogPane) popup.getScene().getRoot(); fill.accept(pane);
                    var button = pane.getButtonTypes().stream().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(button)).fire(); filled.complete(null);
                } catch (Throwable ex) { popup.hide(); filled.completeExceptionally(ex); }
            });
            ((MenuButton) root().lookup("#today-add")).getItems().get(menuItem).fire(); return null;
        }); filled.get(5, TimeUnit.SECONDS);
    }
    private void screenshot(String name, int width, int height) throws Exception {
        stage.setWidth(width); stage.setHeight(height); root().resize(width - 4, height - 36); root().applyCss(); root().layout(); root().applyCss(); root().layout();
        var image = root().snapshot(null, null); var reader = image.getPixelReader();
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, reader.getArgb(x, y));
        Path output = Path.of("build/reports/" + name + ".png"); Files.createDirectories(output.getParent()); javax.imageio.ImageIO.write(png, "png", output.toFile());
    }
}
