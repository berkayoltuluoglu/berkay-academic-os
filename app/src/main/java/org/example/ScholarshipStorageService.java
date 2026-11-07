package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public final class ScholarshipStorageService {
    static final List<String> STATUSES = List.of("Planlanıyor", "Evrak Toplanıyor", "Başvuruldu - Bekliyor", "Olumlu Sonuçlandı", "Olumsuz");
    static final List<String> RESULTS = List.of("Beklemede", "Kazanıldı", "Yedek", "Olumsuz", "İptal / Vazgeçildi");
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private byte[] baseline;
    private boolean loaded;
    public ScholarshipStorageService(Path file) { this.file = file; }
    public synchronized ScholarshipData load() throws IOException {
        loaded = false;
        byte[] current = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
        ScholarshipData data = current == null ? ScholarshipData.empty() : mapper.readValue(current, ScholarshipData.class);
        validate(data); baseline=current; loaded = true; return data;
    }
    public synchronized void save(ScholarshipData data) throws IOException {
        if (!loaded) throw new IOException("Önce başvuru kayıtlarını yükle.");
        validate(data); byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Başvuru kayıtları en fazla 5 MB olabilir.");
        Files.createDirectories(file.getParent());
        try (var channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Başvurular başka bir işlemde kaydediliyor.");
            byte[] current = Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
            if (!Arrays.equals(baseline, current)) throw new IOException("Başvurular başka bir yerde değişti. Yenileyip tekrar dene.");
            if (Arrays.equals(current, bytes)) return;
            if (current != null) AtomicFileWriter.write(file.resolveSibling(file.getFileName() + ".bak"), current);
            AtomicFileWriter.write(file, bytes); baseline = bytes;
        } catch (java.nio.channels.OverlappingFileLockException error) { throw new IOException("Başvurular başka bir işlemde kaydediliyor.", error); }
    }
    public static void validate(ScholarshipData data) throws IOException {
        require(data != null && data.schemaVersion() == 1 && data.applications().size() <= 10000, "Başvuru kayıtları geçersiz.");
        Set<String> ids = new HashSet<>();
        for (var a : data.applications()) {
            require(a.id() != null && a.id().matches("[a-zA-Z0-9_-]{1,80}") && ids.add(a.id()), "Başvuru kimliği geçersiz veya tekrarlı.");
            text(a.name(), 200); require(!a.name().isBlank(), "Kurum / başvuru adı gerekli.");
            require(List.of("Burs", "Staj", "Diğer").contains(a.kind()), "Başvuru türü geçersiz.");
            text(a.amount(), 1000); text(a.note(), 20000); text(a.url(), 2048);
            require(a.url().isBlank() || a.url().matches("https?://[^\\s]+"), "Bağlantı http veya https ile başlamalı.");
            require(STATUSES.contains(a.status()) && RESULTS.contains(a.result()), "Başvuru durumu veya sonucu geçersiz.");
            date(a.deadline()); date(a.appliedOn()); date(a.resultDate());
            require(a.documents().size() <= 100, "En fazla 100 belge eklenebilir.");
            Set<String> documents = new HashSet<>();
            for (var d : a.documents()) { require(d.id() != null && d.id().matches("[a-zA-Z0-9_-]{1,80}") && documents.add(d.id()), "Belge kimliği tekrarlı.");
                text(d.name(), 500); require(!d.name().isBlank(), "Belge adı boş olamaz."); }
        }
    }
    private static void date(String value) throws IOException {
        text(value, 10); if (value.isBlank()) return;
        try { require(value.matches("\\d{4}-\\d{2}-\\d{2}"), "Tarih YYYY-AA-GG biçiminde olmalı."); LocalDate.parse(value); }
        catch (RuntimeException error) { throw new IOException("Başvuru tarihi geçersiz.", error); }
    }
    private static void text(String value, int size) throws IOException { require(value != null && value.length() <= size, "Başvuru metni eksik veya çok uzun."); }
    private static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
}
