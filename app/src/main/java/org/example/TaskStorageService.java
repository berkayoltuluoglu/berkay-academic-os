package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class TaskStorageService {

    private final Path filePath;
    private final ObjectMapper mapper;
    private byte[] baseline;
    private boolean loaded;

    public TaskStorageService(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
    }

    public synchronized List<TaskItem> loadTasks() {
        try {
            if (filePath != null && Files.exists(filePath)) {
                byte[] bytes = FinanceStorageService.readLimited(filePath);
                List<TaskRecord> records = mapper.readValue(
                        bytes,
                        new TypeReference<List<TaskRecord>>() {}
                );

                List<TaskItem> tasks = new ArrayList<>();
                var ids = new HashSet<String>();
                int position = 0;
                for (TaskRecord record : records) {
                    int index = position++;
                    if (record == null) {
                        continue;
                    }

                    LocalDate parsedDate;
                    try {
                        parsedDate = record.date == null || record.date.isBlank()
                                ? null
                                : LocalDate.parse(record.date);
                    } catch (Exception invalidDate) {
                        throw new IllegalStateException("Görev tarihi geçersiz; dosya korunuyor", invalidDate);
                    }

                    // Legacy IDs are deterministic until the first successful save persists them.
                    String id = record.id == null ? UUID.nameUUIDFromBytes((index + ":" + record.title + ":" + record.date
                            + ":" + record.description).getBytes(StandardCharsets.UTF_8)).toString() : record.id;
                    if (!id.matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(id)) throw new IllegalStateException("Görev kimlikleri geçersiz");
                    TaskItem task = new TaskItem(id,
                            record.title == null ? "" : record.title,
                            parsedDate,
                            record.description == null ? "" : record.description,
                            record.status == null || record.status.isBlank() ? "Bekliyor" : record.status
                    );
                    task.setDueTime(record.dueTime == null || record.dueTime.isBlank() ? null : java.time.LocalTime.parse(record.dueTime));
                    task.setPriority(record.priority);
                    task.setEstimatedMinutes(record.estimatedMinutes);
                    task.setLoggedMinutes(record.loggedMinutes);
                    task.setSubtasks(record.subtasks);
                    validateDetails(task);
                    tasks.add(task);
                }
                baseline = bytes; loaded = true;
                return tasks;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Görevler yüklenemedi: " + e.getMessage(), e);
        }
        baseline = null; loaded = true;
        return new ArrayList<>();
    }

    public synchronized void saveTasks(List<TaskItem> tasks) {
        try {
            if (filePath == null) {
                throw new IllegalArgumentException("Görev dosyası tanımlanmamış");
            }
            if (!loaded) loadTasks();

            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<TaskRecord> records = new ArrayList<>();
            var ids = new HashSet<String>();
            if (tasks != null) {
                for (TaskItem task : tasks) {
                    if (task == null) {
                        continue;
                    }
                    if (task.getId() == null || !task.getId().matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(task.getId()))
                        throw new IllegalStateException("Görev kimlikleri geçersiz");
                    validateDetails(task);

                    TaskRecord record = new TaskRecord();
                    record.id = task.getId();
                    record.title = task.getTitle();
                    record.date = task.getDate() == null ? null : task.getDate().toString();
                    record.description = task.getDescription();
                    record.status = task.getStatus();
                    record.dueTime = task.getDueTime() == null ? null : task.getDueTime().toString();
                    record.priority = task.getPriority();
                    record.estimatedMinutes = task.getEstimatedMinutes();
                    record.loggedMinutes = task.getLoggedMinutes();
                    record.subtasks = task.getSubtasks();
                    records.add(record);
                }
            }

            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(records);
            if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IllegalStateException("Görev dosyası en fazla 5 MB olabilir");
            try (var channel = FileChannel.open(filePath.resolveSibling(filePath.getFileName() + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
                if (lock == null) throw new IllegalStateException("Görevler başka bir yerde kaydediliyor");
                byte[] current = Files.exists(filePath) ? FinanceStorageService.readLimited(filePath) : null;
                if (!Arrays.equals(current, baseline)) throw new IllegalStateException("Görevler başka bir yerde değişti. Önce yenile.");
                if (Arrays.equals(bytes, current)) return;
                if (current != null) AtomicFileWriter.write(filePath.resolveSibling(filePath.getFileName() + ".bak"), current);
                AtomicFileWriter.write(filePath, bytes); baseline = bytes;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Görevler kaydedilemedi: " + e.getMessage(), e);
        }
    }

    private static class TaskRecord {
        public String id;
        public String title;
        public String date;
        public String description;
        public String status;
        public String dueTime;
        public String priority;
        public int estimatedMinutes;
        public int loggedMinutes;
        public List<TaskItem.Subtask> subtasks;
    }

    static void validateDetails(TaskItem task) {
        if (!java.util.Set.of("Düşük", "Normal", "Yüksek").contains(task.getPriority())
                || task.getEstimatedMinutes() < 0 || task.getEstimatedMinutes() > 100000
                || task.getLoggedMinutes() < 0 || task.getLoggedMinutes() > 1000000
                || task.getSubtasks().size() > 100)
            throw new IllegalArgumentException("Görev önceliği veya çalışma süresi geçersiz.");
        var ids = new HashSet<String>();
        for (var subtask : task.getSubtasks()) {
            if (subtask == null || subtask.id() == null || !subtask.id().matches("[a-zA-Z0-9_-]{1,80}")
                    || !ids.add(subtask.id()) || subtask.title() == null || subtask.title().isBlank() || subtask.title().length() > 300)
                throw new IllegalArgumentException("Alt görev bilgileri geçersiz.");
        }
    }
}
