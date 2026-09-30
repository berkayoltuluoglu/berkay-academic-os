package org.example;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

public class ModuleActionHandler {

    private final ScriptModuleRunner scriptModuleRunner;
    private final UartConsoleView uartConsoleView;
    private final ReferenceLibraryView referenceLibraryView;
    private final CalendarModuleView calendarModuleView;
    private final Path projectRoot;
    private ScriptModuleView scriptView;

    public ModuleActionHandler(
            ScriptModuleRunner scriptModuleRunner,
            UartConsoleView uartConsoleView,
            ReferenceLibraryView referenceLibraryView,
            CalendarModuleView calendarModuleView,
            Path projectRoot
    ) {
        this.scriptModuleRunner = scriptModuleRunner;
        this.uartConsoleView = uartConsoleView;
        this.referenceLibraryView = referenceLibraryView;
        this.calendarModuleView = calendarModuleView;
        this.projectRoot = projectRoot;
    }

    public boolean handleInternalModule(
            ModuleItem module,
            VBox centerArea,
            StatusUpdater statusUpdater,
            TextCenterShower textCenterShower
    ) {
        try {
            String moduleId = module.getId();

            if (moduleId == null || moduleId.isBlank()) {
                textCenterShower.show("Internal modül kimliği tanımsız");
                statusUpdater.update("Durum: Hata");
                return true;
            }

            switch (moduleId.toLowerCase()) {
                case "uart":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(uartConsoleView.build());
                    centerArea.setAlignment(Pos.CENTER);
                    statusUpdater.update("Durum: UART Konsolu açık");
                    return true;

                case "reference":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(referenceLibraryView.build(projectRoot.resolve("data/reference-topics.json")));
                    centerArea.setAlignment(Pos.CENTER);
                    statusUpdater.update("Durum: Referans açık");
                    return true;

                case "calendar":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(calendarModuleView.build(projectRoot.resolve("data/tasks.json")));
                    centerArea.setAlignment(Pos.CENTER);
                    statusUpdater.update("Durum: Takvim açık");
                    return true;

                default:
                    textCenterShower.show(module.getMessage() != null ? module.getMessage() : "Internal modül açıldı");
                    statusUpdater.update("Durum: " + module.getName() + " aktif");
                    return true;
            }
        } catch (Exception e) {
            textCenterShower.show("Internal modül açılırken hata: " + e.getMessage());
            statusUpdater.update("Durum: Hata");
            e.printStackTrace();
            return true;
        }
    }

    public void handleScriptModule(ModuleItem module, VBox centerArea, StatusUpdater statusUpdater) {
        cancelScript();
        scriptView = new ScriptModuleView();
        VBox view = scriptView.build(module, projectRoot, scriptModuleRunner, statusUpdater);
        centerArea.getChildren().setAll(view);
        VBox.setVgrow(view, javafx.scene.layout.Priority.ALWAYS);
    }

    public void cancelScript() {
        if (scriptView != null) {
            scriptView.dispose();
            scriptView = null;
        }
    }

    public boolean handleExternalWebModule(
            ModuleItem module,
            TextCenterShower textCenterShower,
            StatusUpdater statusUpdater
    ) {
        try {
            if (module.getTarget() == null || module.getTarget().isBlank()) {
                textCenterShower.show("Web hedefi tanımlanmamış");
                statusUpdater.update("Durum: Hata");
                return true;
            }

            new ProcessBuilder("xdg-open", module.getTarget()).start();
            textCenterShower.show(module.getMessage() != null ? module.getMessage() : module.getName() + " dış tarayıcıda açıldı");
            statusUpdater.update("Durum: " + module.getName() + " dış tarayıcıda açıldı");
            return true;
        } catch (Exception e) {
            textCenterShower.show("İşlem hatası: " + e.getMessage());
            statusUpdater.update("Durum: Hata");
            e.printStackTrace();
            return true;
        }
    }

    @FunctionalInterface
    public interface StatusUpdater {
        void update(String text);
    }

    @FunctionalInterface
    public interface TextCenterShower {
        void show(String text);
    }
}