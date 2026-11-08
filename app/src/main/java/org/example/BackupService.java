package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Checks every file before restore, backs up the current profile, and rolls back failed replacements. */
public final class BackupService {
    private static final long MAX_TOTAL = 500L * 1024 * 1024;
    private static final int MAX_FILES = 10000;
    record Entry(String path, long size, String sha256) {}
    record Manifest(int schemaVersion, String createdAt, List<Entry> files) {}
    public record Preview(int files, long bytes, String createdAt) {}
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    public BackupService(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Preview exportTo(Path destination) throws IOException {
        return AtomicFileWriter.exclusive(() -> { try (var locks = lockRecords()) { return exportUnlocked(destination); } });
    }
    private Preview exportUnlocked(Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        if (target.startsWith(root) && eligible(root.relativize(target).toString().replace('\\', '/'))) throw new IOException("Yedeği veri/not klasörlerinin dışında kaydet.");
        List<Path> files = files(); List<Entry> entries = new ArrayList<>(); long total = 0;
        for (Path file : files) {
            long size = Files.size(file); total += size;
            if (total > MAX_TOTAL || files.size() > MAX_FILES) throw new IOException("Yedek en fazla 500 MB / 10.000 dosya olabilir.");
            entries.add(new Entry(relative(file), size, hash(file)));
        }
        String created = Instant.now().toString();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".backup-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(mapper.writeValueAsBytes(new Manifest(1, created, entries))); zip.closeEntry();
                for (int i = 0; i < files.size(); i++) {
                    zip.putNextEntry(new ZipEntry(entries.get(i).path())); Files.copy(files.get(i), zip); zip.closeEntry();
                    if (Files.size(files.get(i)) != entries.get(i).size() || !hash(files.get(i)).equals(entries.get(i).sha256()))
                        throw new IOException("Yedek sırasında dosya değişti; tekrar dene.");
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return new Preview(entries.size(), total, created);
    }

    public Preview inspect(Path archive) throws IOException {
        Files.createDirectories(root);
        Path stage = Files.createTempDirectory(root, ".restore-check-");
        try { return unpack(archive, stage); }
        finally { removeTemporary(stage); }
    }

    public Path restoreFrom(Path archive) throws IOException {
        return AtomicFileWriter.exclusive(() -> { try (var locks = lockRecords()) {
            Files.createDirectories(root);
            Path stage = Files.createTempDirectory(root, ".restore-");
            Path rollback = Files.createTempDirectory(root, ".restore-rollback-");
            try {
                unpack(archive, stage);
                List<Path> oldFiles = files();
                Map<String, String> baselines = new HashMap<>();
                for (Path file : oldFiles) {
                    String relative = relative(file); Path copy = rollback.resolve(relative);
                    Files.createDirectories(copy.getParent()); Files.copy(file, copy);
                    baselines.put(relative, hash(file));
                }
                Path before = root.resolve("backups/before-restore-" + java.util.UUID.randomUUID() + ".zip");
                exportUnlocked(before);
                List<Path> restored;
                try (var paths = Files.walk(stage)) { restored = paths.filter(Files::isRegularFile).toList(); }
                Set<String> next = new HashSet<>(); restored.forEach(file -> next.add(stage.relativize(file).toString().replace('\\', '/')));
                for (Path file : restored) {
                    Path target = safeTarget(stage.relativize(file).toString().replace('\\', '/'));
                    if (Files.exists(target) && !Files.isRegularFile(target)) throw new IOException("Geri yükleme hedefinde dosya/klasör çakışması var: " + target);
                }
                for (Path file : oldFiles) if (!baselines.get(relative(file)).equals(hash(file)))
                    throw new IOException("Geri yükleme sırasında kayıtlar değişti. Yeniden dene.");
                List<Path> changed = new ArrayList<>();
                try {
                    for (Path file : restored) {
                        Path target = safeTarget(stage.relativize(file).toString().replace('\\', '/'));
                        changed.add(target); AtomicFileWriter.copy(file, target);
                    }
                    for (Path file : oldFiles) if (!next.contains(relative(file))) { changed.add(file); Files.delete(file); }
                } catch (IOException failure) {
                    for (Path target : changed.reversed()) {
                        try {
                            Path original = rollback.resolve(relative(target));
                            if (Files.exists(original)) AtomicFileWriter.copy(original, target); else Files.deleteIfExists(target);
                        } catch (IOException recovery) { failure.addSuppressed(recovery); }
                    }
                    throw new IOException("Geri yükleme tamamlanamadı. Önceki durum yedeği: " + before, failure);
                }
                return before;
            } finally { removeTemporary(stage); removeTemporary(rollback); }
        }});
    }

    private Preview unpack(Path archive, Path stage) throws IOException {
        try (var zip = new ZipFile(archive.toFile())) {
            var manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) throw new IOException("Bu dosya Academic OS yedeği değil.");
            byte[] manifestBytes;
            try (var input = zip.getInputStream(manifestEntry)) { manifestBytes = input.readNBytes(2 * 1024 * 1024 + 1); }
            if (manifestBytes.length > 2 * 1024 * 1024) throw new IOException("Yedek manifesti çok büyük.");
            Manifest manifest = mapper.readValue(manifestBytes, Manifest.class);
            if (manifest.schemaVersion() != 1 || manifest.files().size() > MAX_FILES) throw new IOException("Yedek sürümü/sayısı desteklenmiyor.");
            Set<String> expected = new HashSet<>(); long total = 0;
            for (Entry entry : manifest.files()) {
                if (!expected.add(entry.path()) || !eligible(entry.path()) || entry.size() < 0 || entry.size() > MAX_TOTAL)
                    throw new IOException("Yedekte geçersiz veya tekrar eden dosya yolu var.");
                safeTarget(entry.path()); total += entry.size();
                if (total > MAX_TOTAL) throw new IOException("Yedek 500 MB sınırını aşıyor.");
                ZipEntry source = zip.getEntry(entry.path());
                if (source == null || source.isDirectory()) throw new IOException("Yedekte eksik dosya var: " + entry.path());
                Path target = stage.resolve(entry.path()); Files.createDirectories(target.getParent());
                long written = 0;
                try (InputStream input = zip.getInputStream(source); OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        written += count; if (written > entry.size()) throw new IOException("Yedekte dosya boyutu uyuşmuyor."); output.write(buffer, 0, count);
                    }
                }
                if (written != entry.size() || !hash(target).equals(entry.sha256())) throw new IOException("Yedek dosyası bozulmuş: " + entry.path());
            }
            Set<String> actual = new HashSet<>();
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!actual.add(entry.getName())) throw new IOException("Yedekte tekrar eden ZIP girdisi var.");
                if (!entry.getName().equals("manifest.json") && !entry.isDirectory() && !expected.contains(entry.getName()))
                    throw new IOException("Yedekte beklenmeyen dosya var.");
            }
            validateRecords(stage);
            return new Preview(expected.size(), total, manifest.createdAt());
        }
    }

    private void validateRecords(Path stage) throws IOException {
        try {
            PlanningPreferences.load(stage); PlannerService.notes(stage);PlannerService.history(stage); // validates every recorded edit's file and path
            new ProposalService(stage).list(null);
            if (Files.exists(stage.resolve("data/tasks.json"))) new TaskStorageService(stage.resolve("data/tasks.json")).loadTasks();
            if (Files.exists(stage.resolve("data/courses/academic.json"))) new CourseStorageService(stage.resolve("data/courses/academic.json")).load();
            if (Files.exists(stage.resolve("data/courses/curricula.json"))) new CurriculumStorageService(stage.resolve("data/courses/curricula.json")).load();
            if (Files.exists(stage.resolve("data/study/sessions.json"))) new StudyStorageService(stage.resolve("data/study/sessions.json")).load();
            if (Files.exists(stage.resolve("data/finance/ledger.json"))) new FinanceStorageService(stage.resolve("data/finance/ledger.json")).load();
            if (Files.exists(stage.resolve("data/finance/applications.json"))) new ScholarshipStorageService(stage.resolve("data/finance/applications.json")).load();
            if (Files.exists(AppPreferences.file(stage))) AppPreferences.load(stage);
            if (Files.exists(stage.resolve("config/app-config.json"))) new ConfigLoader().load(stage);
            if(Files.exists(stage.resolve("data/focus.properties"))){var focus=new FocusTimerService(stage.resolve("data/focus.properties"));if(focus.error()!=null)throw new IOException(focus.error());}
            if (Files.exists(stage.resolve("data/time/focus-log.json"))) new StudyTimeLogService(stage).load();
            if (Files.exists(stage.resolve("data/calendar/sources.json"))) new CalendarSourcesStore(stage).read();
            if (Files.exists(stage.resolve("data/calendar/outbox.json"))) new CalendarWriteQueue(stage).load();
            if (Files.exists(stage.resolve("data/sync/drive.json"))) new DriveSyncService(stage).state();
        } catch (Exception failure) { throw new IOException("Yedek kayıtları geçersiz; mevcut veriler korunuyor: " + failure.getMessage(), failure); }
    }

    private List<Path> files() throws IOException {
        List<Path> result = new ArrayList<>();
        for (String directory : List.of("data", "notes")) {
            Path base = root.resolve(directory); if (!Files.exists(base)) continue;
            if (Files.isSymbolicLink(base)) throw new IOException("Yedek kaynağı sembolik bağlantı olamaz.");
            try (var paths = Files.walk(base)) {
                for (Path file : paths.toList()) {
                    if (Files.isSymbolicLink(file)) throw new IOException("Yedek kaynağında sembolik bağlantı var.");
                    if (Files.isRegularFile(file)) {
                        String name = relative(file);
                        if (name.endsWith(".lock") || name.startsWith("data/cache/") || name.contains("/.academic-os-")) continue;
                        if (!eligible(name)) throw new IOException("Yedeklenemeyen dosya adı: " + name);
                        result.add(file);
                    }
                }
            }
        }
        Path config = root.resolve("config/app-config.json"); if (Files.isRegularFile(config)) { safeTarget("config/app-config.json"); result.add(config); }
        result.sort(Comparator.comparing(this::relative)); return result;
    }
    private boolean eligible(String name) {
        return !name.contains("\\") && !name.contains(":") && !name.startsWith("/")
                && Arrays.stream(name.split("/")).noneMatch(part -> part.equals("..") || part.equals(".") || part.isBlank())
                && (name.startsWith("data/") || name.startsWith("notes/") || name.equals("config/app-config.json"))
                && !name.endsWith(".lock") && !name.contains("/.academic-os-") && !name.startsWith("data/cache/");
    }
    private Path safeTarget(String name) throws IOException {
        if (!eligible(name)) throw new IOException("Geçersiz yedek yolu.");
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IOException("Yedek yolu veri klasörünün dışına çıkıyor.");
        for (Path current = target; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Geri yükleme yolu sembolik bağlantı içeriyor.");
        return target;
    }
    private String relative(Path file) { return root.relativize(file).toString().replace('\\', '/'); }
    private RecordLocks lockRecords() throws IOException {
        var held = new RecordLocks();
        try {
            for (String name : List.of("data/tasks.json", "data/courses/academic.json", "data/courses/curricula.json", "data/study/sessions.json", "data/finance/ledger.json", "data/finance/applications.json", PlanningPreferences.FILE, PlannerService.HISTORY, "data/planning/notes.json")) {
                Path path = safeTarget(name + ".lock", true); Files.createDirectories(path.getParent());
                var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                held.channels.add(channel);
                var lock = channel.tryLock();
                if (lock == null) throw new IOException("Kayıtlar başka bir yerde yazılıyor; yedeklemeyi tekrar dene.");
                held.locks.add(lock);
            }
            return held;
        } catch (java.nio.channels.OverlappingFileLockException failure) {
            held.close(); throw new IOException("Kayıtlar başka bir yerde yazılıyor; tekrar dene.", failure);
        } catch (IOException failure) { held.close(); throw failure; }
    }
    private Path safeTarget(String name, boolean lockFile) throws IOException {
        if (!lockFile) return safeTarget(name);
        Path target = root.resolve(name).normalize();
        for (Path current = target; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Kayıt kilidi bağlantılı dosya olamaz.");
        return target;
    }
    private static final class RecordLocks implements AutoCloseable {
        final List<java.nio.channels.FileChannel> channels = new ArrayList<>();
        final List<java.nio.channels.FileLock> locks = new ArrayList<>();
        @Override public void close() throws IOException {
            IOException failure = null;
            for (var lock : locks.reversed()) try { lock.close(); } catch (IOException error) { failure = error; }
            for (var channel : channels.reversed()) try { channel.close(); } catch (IOException error) { failure = error; }
            if (failure != null) throw failure;
        }
    }
    private String hash(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) { byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void removeTemporary(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
}
