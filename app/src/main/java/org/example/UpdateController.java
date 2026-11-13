package org.example;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** One shared controller: leaving Settings does not cancel an automatic download. */
public final class UpdateController implements AutoCloseable {
    public enum Phase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, ERROR, INSTALLING }
    public record State(Phase phase, String message, ReleaseUpdateService.Release release, Path file, double progress) {}
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State(Phase.IDLE, "Güncellemeler henüz kontrol edilmedi.", null, null, 0));
    private final ReleaseUpdateService service;
    private final Path profile, cache;
    private final Optional<Path> installRoot;
    private final Consumer<Runnable> shutdown;
    private UpdatePreferences preferences;
    private Task<?> pending;
    private boolean closed;
    public UpdateController(Path profile, Consumer<Runnable> shutdown) {
        this(profile, WindowsMaintenance.cache(), WindowsMaintenance.installedRoot(), new ReleaseUpdateService(), shutdown);
    }
    UpdateController(Path profile, Path cache, Optional<Path> installRoot, ReleaseUpdateService service, Consumer<Runnable> shutdown) {
        this.profile = profile; this.cache = cache; this.installRoot = installRoot; this.service = service; this.shutdown = shutdown;
        try { preferences = UpdatePreferences.load(profile); }
        catch (Exception error) { preferences = new UpdatePreferences(false, false); set(Phase.ERROR, "Güncelleme tercihleri okunamadı: " + error.getMessage(), null, null, 0); }
    }
    public ReadOnlyObjectProperty<State> stateProperty() { return state.getReadOnlyProperty(); }
    public State state() { return state.get(); }
    public UpdatePreferences preferences() { return preferences; }
    public boolean supported() { return installRoot.isPresent(); }
    public boolean canUninstall() { return installRoot.map(path -> Files.isRegularFile(path.resolve("Uninstall.exe"))).orElse(false); }
    public void savePreferences(UpdatePreferences next) throws Exception { next.save(profile); preferences = next; }
    public void start() { if (supported() && preferences.checkOnStartup()) check(); }
    public void check() {
        if (!supported() || busy()) return;
        set(Phase.CHECKING, "Yeni sürüm kontrol ediliyor…", null, null, -1);
        execute(new Task<Optional<ReleaseUpdateService.Release>>() {
            @Override protected Optional<ReleaseUpdateService.Release> call() throws Exception { return service.check(ReleaseVersion.current()); }
        }, result -> {
            if (result.isEmpty()) set(Phase.IDLE, "Güncelsin · " + ReleaseVersion.current(), null, null, 0);
            else {
                var release = result.get();
                set(Phase.AVAILABLE, release.version() + " sürümü hazır · " + String.format(java.util.Locale.ROOT, "%.1f MiB", release.size() / 1048576.0), release, null, 0);
                if (preferences.autoDownload()) download();
            }
        });
    }
    public void download() {
        var release = state().release();
        if (!supported() || busy() || release == null) return;
        set(Phase.DOWNLOADING, "Güncelleme indiriliyor…", release, null, 0);
        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception { return service.download(release, cache, this::isCancelled, value -> updateProgress(value, release.size())); }
        };
        task.progressProperty().addListener((observable, old, value) -> {
            if (!closed && pending == task && state().phase() == Phase.DOWNLOADING) set(Phase.DOWNLOADING, "Güncelleme indiriliyor…", release, null, value.doubleValue());
        });
        execute(task, file -> set(Phase.READY, "Güncelleme indirildi ve doğrulandı. Kurmak için yeniden başlat.", release, file, 1));
    }
    public void cancel() {
        if (pending != null && state().phase() == Phase.DOWNLOADING) pending.cancel(true);
    }
    public void install() { maintain(false); }
    public void uninstall() { maintain(true); }
    void maintenanceFailed(String message) { set(Phase.ERROR, "İşlem başlatılamadı: " + message, state().release(), state().file(), 0); }
    private void maintain(boolean uninstall) {
        if (!supported() || busy() || (!uninstall && state().phase() != Phase.READY)) return;
        State before = state();
        Path file = uninstall ? installRoot.orElseThrow().resolve("Uninstall.exe") : before.file();
        if (file == null) return;
        set(Phase.INSTALLING, "Kapanış ve kurulum hazırlanıyor…", before.release(), before.file(), -1);
        execute(new Task<WindowsMaintenance.Job>() {
            @Override protected WindowsMaintenance.Job call() throws Exception {
                // Re-fetch the checksum and validate the cached installer immediately before approving it.
                if (!uninstall) service.download(before.release(), cache, () -> false, value -> {});
                return WindowsMaintenance.prepare(cache, installRoot.orElseThrow(), file, ReleaseUpdateService.sha256(file), uninstall ? "uninstall" : "update");
            }
        }, job -> {
            try {
                // The application first flushes pending edits; only then may the helper be launched.
                shutdown.accept(() -> {
                    try { WindowsMaintenance.launch(job); }
                    catch (Exception error) { throw new IllegalStateException(error.getMessage(), error); }
                });
            } catch (Exception error) { set(Phase.ERROR, "İşlem başlatılamadı: " + error.getMessage(), before.release(), before.file(), 0); }
        });
    }
    private boolean busy() { return closed || pending != null || state().phase() == Phase.INSTALLING; }
    private <T> void execute(Task<T> task, Consumer<T> success) {
        pending = task;
        task.setOnSucceeded(event -> { if (closed || pending != task) return; pending = null; success.accept(task.getValue()); });
        task.setOnFailed(event -> {
            if (closed || pending != task) return; pending = null;
            set(Phase.ERROR, "Güncelleme tamamlanamadı: " + task.getException().getMessage(), state().release(), state().file(), 0);
        });
        task.setOnCancelled(event -> {
            if (closed || pending != task) return; pending = null;
            set(Phase.AVAILABLE, "İndirme iptal edildi. Daha sonra yeniden indirebilirsin.", state().release(), null, 0);
        });
        Thread.ofVirtual().name("application-update").start(task);
    }
    private void set(Phase phase, String message, ReleaseUpdateService.Release release, Path file, double progress) { state.set(new State(phase, message, release, file, progress)); }
    @Override public void close() { closed = true; if (pending != null) pending.cancel(true); service.close(); }
}
