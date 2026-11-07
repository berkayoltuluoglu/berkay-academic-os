package org.example;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TaskItem {
    private final String id;
    private String title;
    private LocalDate date;
    private String description;
    private String status;
    private LocalTime dueTime;
    private String priority = "Normal";
    private int estimatedMinutes;
    private int loggedMinutes;
    private List<Subtask> subtasks = List.of();

    public record Subtask(String id, String title, boolean done) {}

    public TaskItem(String title, LocalDate date, String description, String status) {
        this(java.util.UUID.randomUUID().toString(), title, date, description, status);
    }

    public TaskItem(String id, String title, LocalDate date, String description, String status) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.description = description;
        this.status = status;
    }

    public String getId() { return id; }
    public LocalTime getDueTime() { return dueTime; }
    public String getPriority() { return priority; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public int getLoggedMinutes() { return loggedMinutes; }
    public List<Subtask> getSubtasks() { return subtasks; }
    public void setDueTime(LocalTime value) { dueTime = value; }
    public void setPriority(String value) { priority = value == null ? "Normal" : value; }
    public void setEstimatedMinutes(int value) { estimatedMinutes = value; }
    public void setLoggedMinutes(int value) { loggedMinutes = value; }
    public void setSubtasks(List<Subtask> value) { subtasks = value == null ? List.of() : List.copyOf(value); }

    public TaskItem copy() {
        TaskItem value = new TaskItem(id, title, date, description, status);
        value.dueTime = dueTime; value.priority = priority;
        value.estimatedMinutes = estimatedMinutes; value.loggedMinutes = loggedMinutes; value.subtasks = subtasks;
        return value;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return title + " | " + date + " | " + status;
    }
}
