package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Versioned work blocks with the same save protections as course records. */
public final class StudyStorageService {
    public record Document(int schemaVersion, List<StudySession> sessions) {
        public Document { sessions = List.copyOf(sessions); }
    }
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private byte[] baseline;
    private boolean loaded;

    public StudyStorageService(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized List<StudySession> load() throws IOException {
        byte[] bytes = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
        try {
            Document document = bytes == null ? new Document(1, List.of()) : mapper.readValue(bytes, Document.class);
            if (document.schemaVersion() != 1) throw new IOException("Desteklenmeyen çalışma planı sürümü");
            validate(document.sessions());
            baseline = bytes; loaded = true;
            return document.sessions();
        } catch (RuntimeException ex) { throw new IOException("Çalışma planı okunamadı; mevcut dosya korunuyor", ex); }
    }

    public synchronized void save(List<StudySession> sessions) throws IOException {
        if (!loaded) throw new IOException("Çalışma planı yüklenmeden kaydedilemez");
        validate(sessions);
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Document(1, sessions));
        if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Çalışma planı en fazla 5 MB olabilir");
        Files.createDirectories(file.getParent());
        try (var channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Çalışma planı başka bir yerde kaydediliyor");
            byte[] current = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
            if (!Arrays.equals(current, baseline)) throw new IOException("Çalışma planı değişti. Önce yenile.");
            if (Arrays.equals(bytes, current)) return;
            if (current != null) AtomicFileWriter.write(file.resolveSibling(file.getFileName() + ".bak"), current);
            AtomicFileWriter.write(file, bytes); baseline = bytes;
        } catch (java.nio.channels.OverlappingFileLockException ex) {
            throw new IOException("Çalışma planı başka bir yerde kaydediliyor", ex);
        }
    }

    private static void validate(List<StudySession> sessions) throws IOException {
        if (sessions.size() > 10000) throw new IOException("Çok fazla çalışma oturumu");
        Set<String> ids = new HashSet<>();
        for (var s : sessions) {
            if (s == null || s.id() == null || !s.id().matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(s.id())
                    || s.title() == null || s.title().isBlank() || s.title().length() > 300
                    || (!s.courseId().isEmpty() && !s.courseId().matches("[a-zA-Z0-9_-]{1,80}")))
                throw new IOException("Çalışma oturumu bilgileri geçersiz");
            if ((!s.taskId().isEmpty() && !s.taskId().matches("[a-zA-Z0-9_-]{1,80}"))
                    || (!s.workId().isEmpty() && !s.workId().matches("[a-zA-Z0-9_-]{1,80}"))
                    || (!s.taskId().isEmpty() && !s.workId().isEmpty()) || s.actualMinutes() < 0 || s.actualMinutes() > 1440)
                throw new IOException("Oturum bağlantısı veya gerçekleşen çalışma süresi geçersiz");
            try {
                LocalDate.parse(s.date());
                if (!LocalTime.parse(s.end()).isAfter(LocalTime.parse(s.start())))
                    throw new IOException("Bitiş saati başlangıçtan sonra olmalı");
            } catch (RuntimeException ex) { throw new IOException("Oturum tarihi veya saati geçersiz", ex); }
        }
    }
}
