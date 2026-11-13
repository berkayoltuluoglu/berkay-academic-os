package org.example;

import javafx.application.Application;
import java.nio.file.*;

/** A plain main class also launches JavaFX correctly from a bundled classpath runtime. */
public final class AppLauncher {
    private AppLauncher() {}
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--check-instance")) {
            Path lock = AppPaths.root().resolve(".instance.lock");
            if (!Files.exists(lock)) return;
            try (var channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.WRITE); var held = channel.tryLock()) {
                if (held == null) System.exit(2);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("--self-check")) {
            for (String resource : new String[]{"/config.json", "/ui/style.css", "/ui/fonts/Poppins-Regular.ttf", "/finance/index.html", "/finance/ledger.js",
                    "/agent-guide/AGENTS.md", "/agent-guide/CLAUDE.md", "/agent-guide/CLI.md", "/agent-guide/planner-proposal.json",
                    "/maintenance/windows-maintenance.ps1", "/branding/logo.png", "/branding/logo-32.png",
                    "/branding/logo-64.png", "/branding/logo-128.png", "/branding/logo-256.png", "/branding/app-icon.ico"}) {
                if (AppLauncher.class.getResource(resource) == null) throw new IllegalStateException("Eksik paket kaynağı: " + resource);
            }
            Class.forName("javafx.scene.web.WebView", false, AppLauncher.class.getClassLoader());
            Class.forName("com.google.api.services.calendar.Calendar", false, AppLauncher.class.getClassLoader());
            if (Runtime.version().feature() < 21) throw new IllegalStateException("Java 21 gerekiyor.");
            System.out.println("Academic OS package check OK · Java " + Runtime.version()); return;
        }
        if (args.length > 0 && args[0].equals("--smoke-test")) {
            if (args.length > 1) System.setProperty("academic.os.home", Path.of(args[1]).toAbsolutePath().toString());
            if (System.getProperty("academic.os.home") == null) throw new IllegalArgumentException("Smoke testi için geçici academic.os.home belirt.");
            Application.launch(PackageSmokeApplication.class, args); return;
        }
        Application.launch(App.class, args);
    }
}
