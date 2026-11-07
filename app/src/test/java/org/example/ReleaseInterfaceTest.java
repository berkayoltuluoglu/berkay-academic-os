package org.example;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class ReleaseInterfaceTest {
    @TempDir Path directory;
    private App app;
    private Stage stage;
    @BeforeEach void open() throws Exception {
        FxTestSupport.initialize();
        var task = new TaskItem("task", "Rapor", LocalDate.now(), "Kaynaklar", "Bekliyor"); task.setEstimatedMinutes(90);
        new TaskStorageService(directory.resolve("data/tasks.json")).saveTasks(List.of(task));
        fx(() -> { app = new App(directory); stage = new Stage(); app.start(stage); return null; });
        waitFor(() -> text("#today-status").isBlank());
    }
    @AfterEach void close() throws Exception { if (app != null) fx(() -> { app.stop(); stage.close(); return null; }); }
    private void navigate(String id) throws Exception { fx(() -> {
        ((ButtonBase) stage.getScene().lookup("#nav-" + id)).fire();
        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return null;
    }); }
    private String text(String id) {
        try { return fx(() -> ((Label) stage.getScene().lookup(id)).getText()); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }
    @Test void editsRichTaskFieldsThroughTheActualFormAndPreservesThemOnDone() throws Exception {
        navigate("tasks");
        fx(() -> {
            @SuppressWarnings("unchecked") ListView<TaskItem> tasks = (ListView<TaskItem>) stage.getScene().lookup("#task-list");
            tasks.getSelectionModel().selectFirst();
            ((TextField) stage.getScene().lookup("#task-due-time")).setText("17:30");
            @SuppressWarnings("unchecked") ComboBox<String> priority = (ComboBox<String>) stage.getScene().lookup("#task-priority"); priority.setValue("Yüksek");
            ((TextField) stage.getScene().lookup("#task-subtask-title")).setText("Oku"); ((Button) stage.getScene().lookup("#task-subtask-add")).fire();
            findButton("Seçiliyi Güncelle").fire(); findButton("Tamamlandı Yap").fire(); return null;
        });
        TaskItem task = new TaskStorageService(directory.resolve("data/tasks.json")).loadTasks().getFirst();
        assertEquals(LocalTime.of(17, 30), task.getDueTime()); assertEquals("Yüksek", task.getPriority()); assertEquals(1, task.getSubtasks().size()); assertEquals("Tamamlandı", task.getStatus());
    }
    @Test void weeklyAgendaRendersAndSettingsPersistWorkingPreferences() throws Exception {
        navigate("agenda");
        waitFor(() -> text("#weekly-agenda-status").contains("birlikte gösteriliyor"));
        fx(() -> {
            var tabs = (TabPane) stage.getScene().lookup(".tab-pane"); assertEquals(2, tabs.getTabs().size()); tabs.getSelectionModel().select(1);
            ((Button) stage.getScene().lookup("#week-next")).fire(); return null;
        });
        waitFor(() -> text("#weekly-agenda-status").contains("birlikte gösteriliyor"));
        navigate("settings");
        fx(() -> {
            @SuppressWarnings("unchecked") Spinner<Integer> duration = (Spinner<Integer>) stage.getScene().lookup("#settings-focus-minutes"); duration.getValueFactory().setValue(35);
            ((CheckBox) stage.getScene().lookup("#settings-background")).setSelected(true);
            ((Button) stage.getScene().lookup("#settings-save")).fire(); return null;
        });
        assertTrue(text("#settings-status").contains("kaydedildi")); assertEquals(35, AppPreferences.load(directory).focusMinutes());
        navigate("today"); waitFor(() -> text("#today-status").isBlank()); assertEquals("35:00", text("#focus-time"));
    }
    @Test void savedGoogleAndClassroomItemsAppearTogetherWithNativeWork() throws Exception {
        var date = LocalDate.now(); var month = YearMonth.from(date); var cache = new CalendarCacheStore(directory);
        cache.setAccount("calendar@example.com"); cache.save("calendar@example.com", month,
                List.of(CalendarEventItem.timed("google", "Google görüşmesi", date.atTime(13, 0), date.atTime(14, 0), "", "")));
        var classroom = new ClassroomData(1, "student@example.com", Instant.now().toString(), List.of(new ClassroomData.Course("course", "Ders", "", "")),
                List.of(new ClassroomData.Work("work", "course", "Classroom raporu", "", date.toString(), "16:00", "NEW", "https://classroom.google.com/test")));
        AtomicFileWriter.write(directory.resolve("data/classroom/catalog.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(classroom));
        navigate("agenda"); waitFor(() -> text("#weekly-agenda-status").contains("birlikte gösteriliyor"));
        var day = new DailyAgendaService().load(directory, date);
        assertTrue(day.events().stream().anyMatch(event -> event.title().equals("Google görüşmesi")));
        assertTrue(day.todos().stream().anyMatch(todo -> todo.title().equals("Classroom raporu")));
        assertTrue(day.todos().stream().anyMatch(todo -> todo.title().equals("Rapor"))); assertTrue(day.errors().isEmpty());
    }
    private Button findButton(String title) {
        return stage.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> title.equals(button.getText())).findFirst().orElseThrow();
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(20);
        assertTrue(condition.getAsBoolean());
    }
}
