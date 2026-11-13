package org.example;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class UpdateInterfaceTest {
    @TempDir Path profile;
    UpdateController controller;
    UpdateSettingsView view;
    Stage stage;
    @BeforeEach void initialize() throws Exception { FxTestSupport.initialize(); }
    @AfterEach void close() throws Exception { fx(() -> { if (view != null) view.dispose(); if (controller != null) controller.close(); if (stage != null) stage.close(); return null; }); }
    void open(ReleaseUpdateService service, boolean supported, java.util.function.Consumer<Runnable> shutdown) throws Exception {
        fx(() -> {
            controller = new UpdateController(profile, profile.resolve("update-cache"), supported ? Optional.of(profile.resolve("installed")) : Optional.empty(), service, shutdown);
            view = new UpdateSettingsView(controller); stage = new Stage(); stage.setScene(new Scene(view.build(), 850, 700)); stage.show(); return null;
        });
    }
    @Test void automaticDownloadRendersNotesAndPreparedInstallDoesNotRunWithoutShutdownHandoff() throws Exception {
        var fixture = new ReleaseUpdateServiceTest();
        var service = fixture.service(fixture.metadata());
        AtomicInteger handoffs = new AtomicInteger();
        new UpdatePreferences(true, true).save(profile);
        open(service, true, launch -> handoffs.incrementAndGet());
        fx(() -> { controller.start(); return null; });
        waitFor(() -> phase() == UpdateController.Phase.READY);
        fx(() -> {
            assertTrue(((Label)stage.getScene().lookup("#updates-status")).getText().contains("doğrulandı"));
            assertEquals("Türkçe sürüm notları", ((TextArea)stage.getScene().lookup("#updates-notes")).getText());
            assertFalse(stage.getScene().lookup("#updates-install").isDisabled());
            assertEquals(0, handoffs.get());
            controller.install(); return null;
        });
        waitFor(() -> handoffs.get() == 1);
        assertFalse(Files.exists(profile.resolve("data/tasks.json")));
        assertFalse(Files.exists(profile.resolve("installed/BerkayAcademicOS.exe")));
        try (var paths = Files.list(profile.resolve("update-cache"))) { assertTrue(paths.anyMatch(path -> path.getFileName().toString().startsWith("handoff-"))); }
    }
    @Test void incorrectChecksumShowsErrorAndKeepsInstallDisabled() throws Exception {
        var fixture = new ReleaseUpdateServiceTest();
        open(fixture.service(fixture.metadata(), fixture.setup, "0".repeat(64) + "  " + fixture.filename, new AtomicInteger()), true, launch -> fail("Installer must not start"));
        fx(() -> { ((Button)stage.getScene().lookup("#updates-check")).fire(); return null; });
        waitFor(() -> phase() == UpdateController.Phase.AVAILABLE);
        fx(() -> { ((Button)stage.getScene().lookup("#updates-download")).fire(); return null; });
        waitFor(() -> phase() == UpdateController.Phase.ERROR);
        fx(() -> {
            assertTrue(stage.getScene().lookup("#updates-install").isDisabled());
            assertTrue(((Label)stage.getScene().lookup("#updates-status")).getText().contains("SHA-256"));
            ((CheckBox)stage.getScene().lookup("#updates-auto-download")).fire(); return null;
        });
        assertTrue(UpdatePreferences.load(profile).autoDownload());
    }
    @Test void sourceOrNonWindowsInstallDoesNotCheckNetwork() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        open(new ReleaseUpdateService(uri -> { requests.incrementAndGet(); throw new AssertionError("No network expected"); }), false, launch -> fail("No installer expected"));
        fx(() -> { controller.start(); controller.check(); assertNull(stage.getScene().lookup("#updates-check"));
            assertTrue(((Label)stage.getScene().lookup("#settings-app-version")).getText().contains("1.6.0")); return null; });
        assertEquals(0, requests.get());
    }
    UpdateController.Phase phase() { try { return fx(() -> controller.state().phase()); } catch (Exception error) { throw new RuntimeException(error); } }
    void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(30);
        assertTrue(condition.getAsBoolean());
    }
}
