package org.example;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ScriptModuleRunner {
    static final int MAX_OUTPUT_CHARS = 120_000;

    public String run(ModuleItem module, Path projectRoot) throws Exception {
        return run(module, projectRoot, output -> {});
    }

    /** Call from a worker thread. Interrupting it also terminates the script. */
    public String run(ModuleItem module, Path projectRoot, Consumer<String> onOutput) throws Exception {
        if (module.getTarget() == null || module.getTarget().isBlank()) {
            throw new IllegalArgumentException("Script hedefi tanımlanmamış");
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        Path scriptPath = root.resolve(module.getTarget()).normalize();
        if (!Files.isRegularFile(scriptPath)) {
            throw new IllegalArgumentException("Script bulunamadı: " + scriptPath);
        }

        ScriptOptions options = module.getScriptOptions() == null ? new ScriptOptions() : module.getScriptOptions();
        String interpreter = options.getInterpreter();
        if (interpreter == null || interpreter.isBlank()) interpreter = "python3";
        String configuredDirectory = options.getWorkingDirectory();
        Path directory = configuredDirectory == null || configuredDirectory.isBlank()
                || "project_root".equals(configuredDirectory) ? root : root.resolve(configuredDirectory).normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Çalışma klasörü bulunamadı: " + directory);
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        ProcessBuilder builder = new ProcessBuilder(interpreter, scriptPath.toString());
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("PYTHONUNBUFFERED", "1");
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        FutureTask<Void> reader = new FutureTask<>(() -> {
            try (var stream = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    output.append(buffer, 0, count);
                    if (output.length() > MAX_OUTPUT_CHARS) output.delete(0, output.length() - MAX_OUTPUT_CHARS);
                    if (options.isCaptureOutput()) onOutput.accept(output.toString());
                }
            }
            return null;
        });
        Thread readerThread = Thread.ofVirtual().name("script-output").start(reader);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.getTimeoutSeconds());
        try {
            // No interactive terminal: input() receives EOF instead of hanging indefinitely.
            process.getOutputStream().close();
            if (!process.waitFor(options.getTimeoutSeconds(), TimeUnit.SECONDS)) throw new TimeoutException();
            reader.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Script hata ile bitti (kod " + process.exitValue() + "):\n" + output);
            }
            return !options.isCaptureOutput() || output.toString().isBlank()
                    ? (module.getMessage() == null ? "Script tamamlandı" : module.getMessage()) : output.toString();
        } catch (TimeoutException ex) {
            throw new TimeoutException("Script " + options.getTimeoutSeconds() + " saniyelik süre sınırını aştı");
        } finally {
            process.descendants().forEach(child -> child.destroyForcibly());
            if (process.isAlive()) process.destroyForcibly();
            reader.cancel(true);
            readerThread.interrupt();
        }
    }
}
