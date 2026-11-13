package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Starts a helper outside the install tree so the old bundled JVM can be replaced. */
public final class WindowsMaintenance {
    private WindowsMaintenance() {}
    public static Optional<Path> installedRoot() {
        if (!Boolean.getBoolean("academic.os.packaged") || !System.getProperty("os.name", "").startsWith("Windows")) return Optional.empty();
        try {
            Path jar = Path.of(WindowsMaintenance.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path lib = jar.getParent();
            if (!Files.isRegularFile(jar) || !lib.getFileName().toString().equals("lib") || !lib.getParent().getFileName().toString().equals("app")) return Optional.empty();
            Path root = lib.getParent().getParent();
            return Files.isRegularFile(root.resolve("app/install-marker.ini")) && Files.isRegularFile(root.resolve("BerkayAcademicOS.exe")) ? Optional.of(root) : Optional.empty();
        } catch (Exception error) { return Optional.empty(); }
    }
    public static Path cache() {
        String local = System.getenv("LOCALAPPDATA");
        return (local == null || local.isBlank() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(local)).resolve("BerkayAcademicOS-updates");
    }
    record Job(Path script, Path file, Path ready) {}
    static Job prepare(Path cache, Path installRoot, Path executable, String expectedHash, String mode) throws Exception {
        if (!Set.of("update", "uninstall").contains(mode)) throw new IOException("Bilinmeyen bakım işlemi.");
        if (Files.isSymbolicLink(executable) || !Files.isRegularFile(executable) || !ReleaseUpdateService.sha256(executable).equals(expectedHash))
            throw new IOException("Kurulum dosyası değişmiş. Güncellemeyi yeniden indir.");
        if (mode.equals("update") && executable.toAbsolutePath().normalize().startsWith(installRoot.toAbsolutePath().normalize()))
            throw new IOException("Güncelleme dosyası program klasörünün dışında olmalı.");
        Path directory = Files.createDirectories(cache.resolve("handoff-" + UUID.randomUUID()));
        Path script = directory.resolve("maintenance.ps1"), file = directory.resolve("job.json"), ready = directory.resolve("ready");
        try (var input = WindowsMaintenance.class.getResourceAsStream("/maintenance/windows-maintenance.ps1")) {
            if (input == null) throw new IOException("Güncelleme yardımcısı pakette yok.");
            Files.copy(input, script);
        }
        List<Long> waitFor = new ArrayList<>(); waitFor.add(ProcessHandle.current().pid());
        ProcessHandle.current().parent().filter(parent -> parent.info().command()
                .map(command -> Path.of(command).getFileName().toString().equalsIgnoreCase("BerkayAcademicOS.exe")).orElse(false))
                .ifPresent(parent -> waitFor.add(parent.pid()));
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("mode", mode); job.put("installRoot", installRoot.toAbsolutePath().toString());
        job.put("executable", executable.toAbsolutePath().toString()); job.put("sha256", expectedHash);
        job.put("waitFor", waitFor); job.put("ready", ready.toAbsolutePath().toString());
        job.put("restart", mode.equals("update"));
        job.put("result", cache.resolve("last-maintenance.json").toAbsolutePath().toString());
        AtomicFileWriter.write(file, PlanningFiles.JSON.writeValueAsBytes(job));
        return new Job(script, file, ready);
    }
    static void launch(Job job) throws Exception {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null) throw new IOException("Windows PowerShell bulunamadı.");
        Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        if (!Files.isRegularFile(powershell)) throw new IOException("Windows PowerShell bulunamadı.");
        Process process = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", job.script.toString(), "-JobFile", job.file.toString())
                .redirectErrorStream(true).redirectOutput(job.file.resolveSibling("helper.log").toFile()).start();
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(job.ready) && process.isAlive() && System.nanoTime() < until) Thread.sleep(50);
        if (!Files.exists(job.ready)) { process.destroy(); throw new IOException("Güncelleme yardımcısı başlatılamadı. Uygulama açık kaldı; kurulum dosyasını elle çalıştırabilirsin."); }
    }
}
