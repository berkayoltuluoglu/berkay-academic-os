package org.example;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalTime;
import java.util.*;

/** Shared optional task fields; old records have the same defaults as a new empty form. */
final class TaskMetadataEditor extends VBox {
    private final TextField due = new TextField();
    private final Label dueLabel = new Label("Son teslim saati");
    private final ComboBox<String> priority = new ComboBox<>();
    private final Spinner<Integer> estimate = new Spinner<>(0, 100000, 0, 15);
    private final Spinner<Integer> logged = new Spinner<>(0, 1000000, 0, 5);
    private final VBox subtasks = new VBox(5);
    private final Map<CheckBox, TaskItem.Subtask> values = new LinkedHashMap<>();
    private final Label progress = new Label();

    TaskMetadataEditor() { this(true); }
    TaskMetadataEditor(boolean includeSubtasks) {
        super(8);
        due.setPromptText("SS:DD · isteğe bağlı"); due.setId("task-due-time");
        priority.getItems().setAll("Düşük", "Normal", "Yüksek"); priority.setValue("Normal"); priority.setId("task-priority");
        estimate.setId("task-estimate"); logged.setId("task-logged");
        estimate.setEditable(true); logged.setEditable(true);
        TextField subtask = new TextField(); subtask.setPromptText("Yeni alt görev"); subtask.setId("task-subtask-title");
        Button add = new Button("Ekle"); add.setId("task-subtask-add");
        add.setOnAction(event -> {
            if (!subtask.getText().isBlank() && values.size() < 100) {
                add(new TaskItem.Subtask(UUID.randomUUID().toString(), subtask.getText().trim(), false)); subtask.clear();
            }
        });
        dueLabel.setLabelFor(due);
        getChildren().setAll(dueLabel, due, new Label("Öncelik"), priority,
                new Label("Tahmini çalışma (dakika)"), estimate, new Label("Ek çalışma kaydı (dakika)"), logged,
                new Label("Çalışma oturumlarında kaydedilen süre buna ayrıca eklenir."), progress);
        progress.setId("task-study-progress"); progress.setWrapText(true);
        if (includeSubtasks) getChildren().addAll(new Label("Alt görevler"), subtasks, new HBox(8, subtask, add));
        HBox.setHgrow(subtask, Priority.ALWAYS);
    }

    void showDueTime(boolean show) {
        due.setVisible(show); due.setManaged(show); dueLabel.setVisible(show); dueLabel.setManaged(show);
    }

    void load(TaskItem task) {
        due.setText(task == null || task.getDueTime() == null ? "" : task.getDueTime().toString());
        priority.setValue(task == null ? "Normal" : task.getPriority());
        estimate.getValueFactory().setValue(task == null ? 0 : task.getEstimatedMinutes());
        logged.getValueFactory().setValue(task == null ? 0 : task.getLoggedMinutes());
        subtasks.getChildren().clear(); values.clear();
        if (task != null) task.getSubtasks().forEach(this::add);
        showProgress(task == null ? null : new StudyProgressService().forTask(task, List.of()));
    }
    void showProgress(StudyProgressService.Progress value) {
        progress.setText(value == null ? "" : "Çalışıldı: " + value.studiedMinutes() + " dk · Kalan: " + value.remainingMinutes()
                + " dk · Planlı: " + value.plannedMinutes() + " dk");
    }

    void apply(TaskItem task) {
        try {
            task.setDueTime(!due.isManaged() || due.getText().isBlank() ? null : LocalTime.parse(due.getText().trim()));
            estimate.commitValue(); logged.commitValue();
            task.setPriority(priority.getValue()); task.setEstimatedMinutes(estimate.getValue()); task.setLoggedMinutes(logged.getValue());
            task.setSubtasks(values.entrySet().stream().map(entry -> new TaskItem.Subtask(
                    entry.getValue().id(), entry.getValue().title(), entry.getKey().isSelected())).toList());
            TaskStorageService.validateDetails(task);
        } catch (Exception error) { throw new IllegalArgumentException("Saat, çalışma süresi ve alt görevleri kontrol et: " + error.getMessage()); }
    }

    private void add(TaskItem.Subtask task) {
        CheckBox check = new CheckBox(task.title()); check.setSelected(task.done()); check.setWrapText(true);
        Button remove = new Button("×"); remove.setAccessibleText("Alt görevi kaldır");
        HBox row = new HBox(8, check, remove); HBox.setHgrow(check, Priority.ALWAYS);
        remove.setOnAction(event -> { values.remove(check); subtasks.getChildren().remove(row); });
        values.put(check, task); subtasks.getChildren().add(row);
    }
}
