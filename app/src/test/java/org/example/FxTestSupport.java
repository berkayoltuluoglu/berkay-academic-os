package org.example;

import javafx.application.Platform;
import java.util.concurrent.*;

final class FxTestSupport {
    private FxTestSupport() {}
    static boolean isGuiAvailable() {
        if (!Boolean.getBoolean("academic.os.test.gui")) return false;
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                || (System.getenv("DISPLAY") != null && !System.getenv("DISPLAY").isBlank())
                || System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    static void initialize() throws Exception {
        if (!isGuiAvailable()) throw new IllegalStateException("Arayüz testleri için açıkça -PguiTests=true ve uygun test ekranı gerekli");
        CountDownLatch ready = new CountDownLatch(1);
        Runnable started = () -> { Platform.setImplicitExit(false); ready.countDown(); };
        try { Platform.startup(started); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(started); }
        if (!ready.await(10, TimeUnit.SECONDS)) throw new TimeoutException("JavaFX başlamadı");
    }

    static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
