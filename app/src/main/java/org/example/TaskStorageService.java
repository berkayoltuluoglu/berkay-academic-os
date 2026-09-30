package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskStorageService {

    private final Path filePath;
    private final ObjectMapper mapper;

    public TaskStorageService(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
    }

    public List<TaskItem> loadTasks() {
        try {
            if (filePath != null && Files.exists(filePath)) {
                List<TaskRecord> records = mapper.readValue(
                        filePath.toFile(),
                        new TypeReference<List<TaskRecord>>() {}
                );

                List<TaskItem> tasks = new ArrayList<>();
                for (TaskRecord record : records) {
                    if (record == null) {
                        continue;
                    }

                    LocalDate parsedDate;
                    try {
                        parsedDate = record.date == null || record.date.isBlank()
                                ? LocalDate.now()
                                : LocalDate.parse(record.date);
                    } catch (Exception ignored) {
                        parsedDate = LocalDate.now();
                    }

                    tasks.add(new TaskItem(
                            record.title == null ? "" : record.title,
                            parsedDate,
                            record.description == null ? "" : record.description,
                            record.status == null || record.status.isBlank() ? "Bekliyor" : record.status
                    ));
                }
                return tasks;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Görevler yüklenemedi: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public void saveTasks(List<TaskItem> tasks) {
        try {
            if (filePath == null) {
                throw new IllegalArgumentException("Görev dosyası tanımlanmamış");
            }

            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<TaskRecord> records = new ArrayList<>();
            if (tasks != null) {
                for (TaskItem task : tasks) {
                    if (task == null) {
                        continue;
                    }

                    TaskRecord record = new TaskRecord();
                    record.title = task.getTitle();
                    record.date = task.getDate() == null ? LocalDate.now().toString() : task.getDate().toString();
                    record.description = task.getDescription();
                    record.status = task.getStatus();
                    records.add(record);
                }
            }

            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(records);
            if (Files.exists(filePath)) {
                Path backup = filePath.resolveSibling(filePath.getFileName() + ".bak");
                AtomicFileWriter.write(backup, Files.readAllBytes(filePath));
            }
            AtomicFileWriter.write(filePath, bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Görevler kaydedilemedi: " + e.getMessage(), e);
        }
    }

    private static class TaskRecord {
        public String title;
        public String date;
        public String description;
        public String status;
    }
}