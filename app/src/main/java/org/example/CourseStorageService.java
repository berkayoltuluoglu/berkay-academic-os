package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Atomic saves with backup, validation and stale-writer detection. */
public final class CourseStorageService {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private byte[] baseline;
    private boolean loaded;

    public CourseStorageService(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized CourseData load() throws IOException {
        byte[] bytes = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
        CourseData data;
        try { data = bytes == null ? CourseData.empty() : mapper.readValue(bytes, CourseData.class); }
        catch (Exception ex) { throw new IOException("Ders dosyası okunamadı; mevcut dosya korunuyor", ex); }
        validate(data);
        // Reading never rewrites the legacy file; the next save backs it up before migration.
        if (data.schemaVersion() == 1) data = new CourseData(CourseData.VERSION, data.courses(), data.work(), data.sections());
        baseline = bytes;
        loaded = true;
        return data;
    }

    public synchronized void save(CourseData data) throws IOException {
        if (!loaded) throw new IOException("Dersler yüklenmeden kayıt yapılamaz");
        validate(data);
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Ders dosyası en fazla 5 MB olabilir");
        Files.createDirectories(file.getParent());
        try (var channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Dersler başka bir yerde kaydediliyor");
            byte[] current = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
            if (!Arrays.equals(current, baseline)) throw new IOException("Dersler başka bir yerde değişti. Önce yeniden yükle.");
            if (Arrays.equals(bytes, current)) return;
            if (current != null) AtomicFileWriter.write(file.resolveSibling(file.getFileName() + ".bak"), current);
            AtomicFileWriter.write(file, bytes);
            baseline = bytes;
        } catch (java.nio.channels.OverlappingFileLockException ex) {
            throw new IOException("Dersler başka bir yerde kaydediliyor", ex);
        }
    }

    static void validate(CourseData data) throws IOException {
        try {
            require(data != null && (data.schemaVersion() == 1 || data.schemaVersion() == CourseData.VERSION), "Desteklenmeyen ders dosyası sürümü");
            require(data.courses().size() <= 500 && data.work().size() <= 10000, "Çok fazla ders veya teslim kaydı");
            require(data.sections().size() <= 5000, "Katalogda en fazla 5000 şube olabilir");
            Set<String> sections = new HashSet<>();
            for (var section : data.sections()) {
                require(section != null && validId(section.id()) && sections.add(section.id()), "Tekrarlanan veya geçersiz şube kimliği");
                text(section.code(), 50, true); text(section.fullCode(), 80, true);
                text(section.name(), 200, true); text(section.instructor(), 200, false);
                amount(section.credit()); amount(section.ects()); lessons(section.lessons());
            }
            Set<String> courses = new HashSet<>();
            for (var course : data.courses()) {
                require(course != null && validId(course.id()) && courses.add(course.id()), "Tekrarlanan veya geçersiz ders kimliği");
                text(course.name(), 200, true);
                text(course.code(), 50, false); text(course.instructor(), 200, false); text(course.room(), 200, false);
                text(course.section(), 80, false);
                require(course.sectionId().isEmpty() || sections.contains(course.sectionId()), "Seçilen şube katalogda bulunamadı");
                amount(course.credit()); amount(course.ects());
                LocalDate start = LocalDate.parse(course.startDate()), end = LocalDate.parse(course.endDate());
                require(!end.isBefore(start), "Dönem bitişi başlangıçtan önce olamaz");
                lessons(course.lessons());
            }
            Set<String> workIds = new HashSet<>();
            for (var work : data.work()) {
                require(work != null && validId(work.id()) && workIds.add(work.id()), "Tekrarlanan veya geçersiz teslim kimliği");
                require(courses.contains(work.courseId()), "Ödev veya sınav bir derse bağlı olmalı");
                text(work.title(), 300, true);
                require(Set.of("Ödev", "Sınav").contains(work.kind()), "Geçersiz teslim türü");
                LocalDate.parse(work.date());
            }
        } catch (RuntimeException ex) { throw new IOException("Ders bilgileri geçersiz: " + ex.getMessage(), ex); }
    }

    private static void amount(java.math.BigDecimal value) throws IOException {
        require(value == null || (value.signum() >= 0 && value.compareTo(new java.math.BigDecimal("1000")) <= 0
                && value.stripTrailingZeros().scale() <= 2), "Kredi/AKTS 0–1000 arasında, en fazla iki ondalıklı olmalı");
    }

    private static void lessons(List<CourseData.Lesson> values) throws IOException {
        require(values.size() <= 30, "Bir ders için en fazla 30 haftalık oturum olabilir");
        Set<String> unique = new HashSet<>();
        for (var lesson : values) {
            require(lesson != null && lesson.day() >= 1 && lesson.day() <= 7, "Ders günü geçersiz");
            LocalTime start = LocalTime.parse(lesson.start()), end = LocalTime.parse(lesson.end());
            require(end.isAfter(start), "Ders bitiş saati başlangıçtan sonra olmalı");
            text(lesson.room(), 200, false);
            require(unique.add(lesson.day() + ":" + start + ":" + end), "Aynı ders saati iki kez eklenemez");
        }
    }

    private static boolean validId(String id) { return id != null && id.matches("[a-zA-Z0-9_-]{1,80}"); }
    private static void text(String value, int limit, boolean required) throws IOException {
        require(value != null && value.length() <= limit && (!required || !value.isBlank()), "Metin alanı boş veya çok uzun");
    }
    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
