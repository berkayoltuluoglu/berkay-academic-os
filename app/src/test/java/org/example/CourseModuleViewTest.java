package org.example;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.example.FxTestSupport.fx;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class CourseModuleViewTest {
    @TempDir Path root;
    private CourseModuleView view;
    private TodayModuleView todayView;
    private Stage stage;
    private Path file;
    @BeforeAll static void toolkit() throws Exception { FxTestSupport.initialize(); }
    @BeforeEach void open() throws Exception {
        file = root.resolve("data/courses/academic.json");
        fx(() -> {
            UiTheme.loadFonts();
            view = new CourseModuleView(file);
            VBox node = view.build();
            new UiTheme(root.resolve("ui.properties"), "light").apply(node);
            stage = new Stage(); Scene scene = new Scene(node, 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/ui/style.css").toExternalForm());
            stage.setScene(scene); stage.show(); return null;
        });
        ready();
    }
    @AfterEach void close() throws Exception {
        fx(() -> { if (view != null) view.dispose(); if (todayView != null) todayView.dispose(); stage.close(); stage.setScene(null); return null; });
    }
    @Test void createsEditsAndCompletesCourseworkThroughActualForms() throws Exception {
        dialog("#course-add", form -> {
            ((TextField) form.lookup("#course-name")).setText("Devre Analizi");
            ((TextField) form.lookup("#course-code")).setText("EE201");
            ((TextField) form.lookup("#course-credit")).setText("3,5");
            ((TextField) form.lookup("#course-ects")).setText("5");
            ((TextField) form.lookup("#course-room")).setText("B-204");
            ((ComboBox<?>) form.lookup("#lesson-day")).getSelectionModel().select(LocalDate.now().getDayOfWeek().getValue() - 1);
            ((Button) form.lookup("#lesson-add")).fire();
        }); ready();
        assertEquals(1, new CourseStorageService(file).load().courses().size());
        assertEquals(new java.math.BigDecimal("3.5"), new CourseStorageService(file).load().courses().getFirst().credit());
        fx(() -> { ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(2); return null; });
        dialog("#work-add", form -> {
            ((TextField) form.lookup("#work-title")).setText("Laboratuvar raporu");
            ((DatePicker) form.lookup("#work-date")).setValue(LocalDate.now().plusDays(2));
        }); ready();
        assertEquals(1, new CourseStorageService(file).load().work().size());
        fx(() -> { ((ListView<?>) scene().lookup("#course-work-list")).getSelectionModel().selectFirst(); return null; });
        dialog("#work-edit", form -> ((TextField) form.lookup("#work-title")).setText("Güncel rapor")); ready();
        assertEquals("Güncel rapor", new CourseStorageService(file).load().work().getFirst().title());
        fx(() -> {
            ((ListView<?>) scene().lookup("#course-work-list")).getSelectionModel().selectFirst();
            ((Button) scene().lookup("#work-done")).fire(); return null;
        }); ready();
        assertTrue(new CourseStorageService(file).load().work().getFirst().done());
        assertTrue(new TodayService().load(root, LocalDate.now()).coursework().entries().isEmpty());
        fx(() -> {
            ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(0);
            screenshot("courses-week");
            view.dispose(); view = null;
            todayView = new TodayModuleView(root, id -> {});
            VBox node = todayView.build(); new UiTheme(root.resolve("ui.properties"), "light").apply(node);
            stage.getScene().setRoot(node); return null;
        });
        await(() -> !((Button) scene().lookup("#today-refresh")).isDisabled());
        fx(() -> { screenshot("today-preview"); return null; });
    }
    @Test void failedSaveRetainsCourseAndShowsFailure() throws Exception {
        dialog("#course-add", form -> ((TextField) form.lookup("#course-name")).setText("Matematik")); ready();
        Path backup = Files.createDirectory(file.resolveSibling("academic.json.bak")); Files.writeString(backup.resolve("keep"), "keep");
        fx(() -> { ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(1); ((ListView<?>) scene().lookup("#course-list")).getSelectionModel().selectFirst(); return null; });
        dialog("#course-edit", form -> ((TextField) form.lookup("#course-name")).setText("Kaydedilemeyen")); ready();
        assertEquals("Matematik", new CourseStorageService(file).load().courses().getFirst().name());
        fx(() -> { assertTrue(((Label) scene().lookup("#courses-status")).getText().contains("Kaydedilemedi")); return null; });
    }
    @Test void importsPreviewsSelectsAndSwitchesSectionsWithoutLosingCoursework() throws Exception {
        dialog("#course-add", form -> {
            ((TextField) form.lookup("#course-name")).setText("Mevcut ders");
            ((TextField) form.lookup("#course-code")).setText("EE201");
        }); ready();
        String id = new CourseStorageService(file).load().courses().getFirst().id();
        fx(() -> { ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(2); return null; });
        dialog("#work-add", form -> ((TextField) form.lookup("#work-title")).setText("Korunacak ödev")); ready();
        fx(() -> { ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(0); return null; });
        importCsv(CourseCsvImporterTest.CSV, true); ready();
        var imported = new CourseStorageService(file).load();
        assertEquals(2, imported.sections().size()); assertEquals("Mevcut ders", imported.courses().getFirst().name());
        fx(() -> { ((TextField) scene().lookup("#section-search")).setText("EE201"); return null; });
        for (var section : imported.sections()) {
            toggleSection(section.id()); ready();
            fx(() -> {
                assertEquals(0, ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().getSelectedIndex());
                assertEquals("EE201", ((TextField) scene().lookup("#section-search")).getText());
                assertEquals(section.lessons().size(), scene().lookupAll(".planner-block").size());
                assertTrue(((ToggleButton) scene().lookup("#section-toggle-" + section.id())).isSelected());
                return null;
            });
        }
        var saved = new CourseStorageService(file).load();
        assertEquals(1, saved.courses().size()); assertEquals(id, saved.courses().getFirst().id());
        assertEquals("EE201.2", saved.courses().getFirst().section());
        assertEquals(id, saved.work().getFirst().courseId()); assertEquals("Korunacak ödev", saved.work().getFirst().title());
        fx(() -> {
            screenshot("courses-planner-light");
            new UiTheme(root.resolve("ui.properties"), "dark").apply(scene());
            screenshot("courses-planner-dark");
            for (var node : scene().lookup("#section-list").lookupAll(".list-cell"))
                if (node instanceof ListCell<?> cell && !cell.isEmpty())
                    assertTrue(cell.getHeight() >= 60, "Şube saatleri liste hücresinde kesilmemeli");
            return null;
        });
        String chosen = saved.courses().getFirst().sectionId();
        toggleSection(chosen); ready();
        var unplanned = new CourseStorageService(file).load();
        assertFalse(unplanned.courses().getFirst().planned());
        assertEquals(saved.work(), unplanned.work());
        fx(() -> { assertTrue(scene().lookupAll(".planner-block").isEmpty()); return null; });
        toggleSection(chosen); ready();
        assertEquals(saved, new CourseStorageService(file).load());
        // An ordinary coursework save must preserve the catalog too.
        fx(() -> {
            ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(2);
            ((ListView<?>) scene().lookup("#course-work-list")).getSelectionModel().selectFirst();
            ((Button) scene().lookup("#work-done")).fire(); return null;
        }); ready();
        assertEquals(2, new CourseStorageService(file).load().sections().size());
        fx(() -> { ((TabPane) scene().lookup("#courses-tabs")).getSelectionModel().select(0); return null; });
        byte[] before = Files.readAllBytes(file);
        importCsv(CourseCsvImporterTest.HEADER + "EE201.3;Hatalı;Hoca;Pazartesi 09:00-10:00 | Salı 13.00-14.00;3;5", false);
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void failedSectionSaveRestoresSelectionAndCalendar() throws Exception {
        importCsv(CourseCsvImporterTest.CSV, true); ready();
        var sections = new CourseStorageService(file).load().sections();
        fx(() -> { ((TextField) scene().lookup("#section-search")).setText("EE201"); return null; });
        toggleSection(sections.getFirst().id()); ready();
        byte[] before = Files.readAllBytes(file);
        Files.deleteIfExists(file.resolveSibling("academic.json.bak"));
        Path backup = Files.createDirectory(file.resolveSibling("academic.json.bak"));
        Files.writeString(backup.resolve("keep"), "keep");
        toggleSection(sections.get(1).id()); ready();
        assertArrayEquals(before, Files.readAllBytes(file));
        fx(() -> {
            scene().applyCss(); scene().layout();
            assertTrue(((Label) scene().lookup("#courses-status")).getText().contains("Kaydedilemedi"));
            assertTrue(((ToggleButton) scene().lookup("#section-toggle-" + sections.getFirst().id())).isSelected());
            assertFalse(((ToggleButton) scene().lookup("#section-toggle-" + sections.get(1).id())).isSelected());
            assertEquals(2, scene().lookupAll(".planner-block").size());
            return null;
        });
    }

    @Test void filtersCollisionsAndShowsConflictingAndWeekendMeetingsTogether() throws Exception {
        var sections = new CourseCsvImporter().parse(CourseCsvImporterTest.HEADER
                + "MAT101.1;Matematik;A;Pazartesi 09:00-11:00 [D1];3;5\n"
                + "FIZ101.1;Fizik;B;Pazartesi 10:00-12:00 [D2];3;5\n"
                + "TAR101.1;Tarih;C;Cumartesi 13:00-15:00 [UZEM];2;3\n");
        var storage = new CourseStorageService(file); storage.load();
        storage.save(CourseCatalogService.merge(CourseData.empty(), sections));
        fx(() -> { ((Button) scene().lookup("#courses-reload")).fire(); return null; }); ready();
        fx(() -> { ((TextField) scene().lookup("#section-search")).setText("101"); return null; });
        toggleSection(sections.getFirst().id()); ready();
        fx(() -> {
            ((CheckBox) scene().lookup("#planner-compatible")).fire(); scene().applyCss(); scene().layout();
            assertNull(scene().lookup("#section-toggle-" + sections.get(1).id()));
            assertNotNull(scene().lookup("#section-toggle-" + sections.get(2).id()));
            ((CheckBox) scene().lookup("#planner-compatible")).fire(); return null;
        });
        toggleSection(sections.get(1).id()); ready();
        toggleSection(sections.get(2).id()); ready();
        fx(() -> {
            screenshot("courses-planner-conflicts");
            assertEquals(3, scene().lookupAll(".planner-block").size());
            var conflicts = scene().lookupAll(".planner-block-conflict").stream().toList();
            assertEquals(2, conflicts.size());
            assertNotEquals(conflicts.get(0).getLayoutX(), conflicts.get(1).getLayoutX());
            assertEquals(7, scene().lookupAll(".planner-day").size());
            assertTrue(((Label) scene().lookup("#planner-week-hint")).getText().contains("1 saat çakışması"));
            ((CheckBox) scene().lookup("#planner-selected-only")).fire();
            assertTrue(((Label) scene().lookup("#planner-summary")).getText().contains("3 ders seçili"));
            return null;
        });
    }

    private void toggleSection(String id) throws Exception {
        fx(() -> {
            scene().applyCss(); scene().layout();
            ListView<?> list = (ListView<?>) scene().lookup("#section-list");
            for (int i = 0; scene().lookup("#section-toggle-" + id) == null && i < list.getItems().size(); i++) {
                list.scrollTo(i); scene().applyCss(); scene().layout();
            }
            ((ToggleButton) scene().lookup("#section-toggle-" + id)).fire(); return null;
        });
    }

    private void importCsv(String csv, boolean accept) throws Exception {
        Path source = root.resolve("import.csv"); Files.writeString(source, csv);
        CompletableFuture<Void> closed = new CompletableFuture<>();
        fx(() -> { Platform.runLater(() -> {
            try { ((Button) scene().lookup("#course-import")).fire(); closed.complete(null); }
            catch (Throwable ex) { closed.completeExceptionally(ex); }
        }); return null; });
        await(() -> Window.getWindows().stream().anyMatch(w -> w != stage && w.isShowing() && w.getScene().lookup("#csv-path") != null));
        fx(() -> {
            Parent form = csvWindow().getScene().getRoot();
            ((TextField) form.lookup("#csv-path")).setText(source.toString());
            ((Button) form.lookup("#csv-preview")).fire(); return null;
        });
        await(() -> !((Button) csvWindow().getScene().lookup("#csv-preview")).isDisabled());
        fx(() -> {
            Window popup = csvWindow(); Parent form = popup.getScene().getRoot();
            Button commit = (Button) form.lookup("#csv-import-save");
            if (accept) {
                assertFalse(commit.isDisabled(), ((Label) form.lookup("#csv-status")).getText());
                assertEquals(2, ((ListView<?>) form.lookup("#csv-sections")).getItems().size());
                commit.fire();
            } else {
                assertTrue(commit.isDisabled());
                assertTrue(((Label) form.lookup("#csv-status")).getText().contains("satır 2"));
                popup.hide();
            }
            return null;
        });
        closed.get(5, TimeUnit.SECONDS);
    }
    private Window csvWindow() {
        return Window.getWindows().stream().filter(w -> w != stage && w.isShowing() && w.getScene().lookup("#csv-path") != null).findFirst().orElseThrow();
    }
    private Parent scene() { return stage.getScene().getRoot(); }
    private void ready() throws Exception { await(() -> !((Button) scene().lookup("#courses-reload")).isDisabled()); }
    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition) && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(fx(condition));
    }
    private void dialog(String trigger, Consumer<Parent> edit) throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        fx(() -> {
            Platform.runLater(() -> {
                Window popup = Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow();
                try {
                    Parent form = popup.getScene().getRoot(); edit.accept(form);
                    ((Button) form.lookup("#course-form-save")).fire(); completed.complete(null);
                } catch (Throwable ex) { popup.hide(); completed.completeExceptionally(ex); }
            });
            ((Button) scene().lookup(trigger)).fire(); return null;
        });
        completed.get(5, TimeUnit.SECONDS);
    }
    private void screenshot(String name) throws Exception {
        Parent node = scene(); node.resize(1100, 720); node.applyCss(); node.layout();
        var image = node.snapshot(null, null); var reader = image.getPixelReader();
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, reader.getArgb(x,y));
        Path output = Path.of("build/reports/" + name + ".png"); Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(png, "png", output.toFile());
    }
}
