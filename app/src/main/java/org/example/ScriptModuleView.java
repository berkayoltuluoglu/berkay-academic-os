package org.example;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

public class ScriptModuleView {
    private Task<String> task;
    private boolean disposed;

    public VBox build(ModuleItem module, Path root, ScriptModuleRunner runner,
                      ModuleActionHandler.StatusUpdater statusUpdater) {
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        Label state = new Label();
        Button run = new Button("Yeniden Çalıştır");
        Button cancel = new Button("İptal");
        VBox view = new VBox(10, new Label(module.getName()), new HBox(10, run, cancel, state), output);
        view.setPadding(new Insets(15));
        VBox.setVgrow(output, Priority.ALWAYS);

        Runnable start = () -> {
            output.clear();
            run.setDisable(true);
            cancel.setDisable(false);
            state.setText("Çalışıyor…");
            statusUpdater.update("Durum: Script çalışıyor");
            Task<String> next = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return runner.run(module, root, this::updateMessage);
                }
            };
            task = next;
            next.messageProperty().addListener((observable, oldText, text) -> {
                if (!disposed && task == next) output.setText(text);
            });
            next.setOnSucceeded(event -> {
                if (!disposed && task == next) {
                    output.setText(next.getValue());
                    finish("Script tamamlandı", run, cancel, state, statusUpdater);
                }
            });
            next.setOnFailed(event -> {
                if (!disposed && task == next) {
                    output.appendText("\n" + next.getException().getMessage());
                    finish("Script hatası", run, cancel, state, statusUpdater);
                }
            });
            next.setOnCancelled(event -> {
                if (!disposed && task == next) finish("Script iptal edildi", run, cancel, state, statusUpdater);
            });
            Thread.ofVirtual().name("script-runner").start(next);
        };
        run.setOnAction(event -> start.run());
        cancel.setOnAction(event -> task.cancel(true));
        start.run();
        return view;
    }

    private void finish(String message, Button run, Button cancel, Label state,
                        ModuleActionHandler.StatusUpdater statusUpdater) {
        run.setDisable(false);
        cancel.setDisable(true);
        state.setText(message);
        statusUpdater.update("Durum: " + message);
    }

    public void dispose() {
        disposed = true;
        if (task != null) task.cancel(true);
    }
}
