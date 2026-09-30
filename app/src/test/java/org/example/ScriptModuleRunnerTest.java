package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ScriptModuleRunnerTest {
    @TempDir Path directory;
    private final ScriptModuleRunner runner = new ScriptModuleRunner();

    private ModuleItem script(String source) throws Exception {
        Files.writeString(directory.resolve("test.py"), source);
        ModuleItem module = new ModuleItem();
        module.setTarget("test.py");
        module.setMessage("Tamam");
        module.setScriptOptions(new ScriptOptions());
        return module;
    }

    @Test void streamsUnicodeOutputAndUsesConfiguredWorkingDirectory() throws Exception {
        ModuleItem module = script("import os\nprint(os.getcwd())\nprint('Çalışıyor')\n");
        Path work = Files.createDirectory(directory.resolve("work"));
        module.getScriptOptions().setWorkingDirectory("work");
        AtomicReference<String> streamed = new AtomicReference<>();
        String result = runner.run(module, directory, streamed::set);
        assertTrue(result.contains(work.toString()));
        assertTrue(result.contains("Çalışıyor"));
        assertEquals(result, streamed.get());
    }

    @Test void disabledCaptureDoesNotPublishOutput() throws Exception {
        ModuleItem module = script("print('hidden')\n");
        module.getScriptOptions().setCaptureOutput(false);
        assertEquals("Tamam", runner.run(module, directory, output -> fail("Output must be hidden")));
    }

    @Test void nonzeroExitReportsStderrEvenWhenCaptureIsDisabled() throws Exception {
        ModuleItem module = script("import sys\nprint('hata', file=sys.stderr)\nsys.exit(7)\n");
        module.getScriptOptions().setCaptureOutput(false);
        Exception error = assertThrows(IllegalStateException.class, () -> runner.run(module, directory));
        assertTrue(error.getMessage().contains("7"));
        assertTrue(error.getMessage().contains("hata"));
    }

    @Test void outputIsBoundedEvenWithoutNewlines() throws Exception {
        ModuleItem module = script("import sys\nsys.stdout.write('x' * 500000 + 'SON')\n");
        String result = runner.run(module, directory);
        assertEquals(ScriptModuleRunner.MAX_OUTPUT_CHARS, result.length());
        assertTrue(result.endsWith("SON"));
    }

    @Test void timeoutTerminatesProcess() throws Exception {
        ModuleItem module = script("import os, time\nprint(os.getpid(), flush=True)\ntime.sleep(60)\n");
        module.getScriptOptions().setTimeoutSeconds(1);
        AtomicReference<String> output = new AtomicReference<>();
        assertThrows(TimeoutException.class, () -> runner.run(module, directory, output::set));
        assertStopped(Long.parseLong(output.get().trim()));
    }

    @Test void interruptTerminatesProcessAndChildren() throws Exception {
        ModuleItem module = script("import os, subprocess, sys, time\np = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])\nprint(str(os.getpid()) + ',' + str(p.pid), flush=True)\ntime.sleep(60)\n");
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> output = new AtomicReference<>();
        FutureTask<String> future = new FutureTask<>(() -> runner.run(module, directory, text -> {
            output.set(text);
            started.countDown();
        }));
        Thread worker = Thread.ofVirtual().start(future);
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertInstanceOf(InterruptedException.class, error.getCause());
            String[] pids = output.get().trim().split(",");
            for (String pid : pids) assertStopped(Long.parseLong(pid));
        } finally {
            worker.interrupt();
        }
    }

    private void assertStopped(long pid) throws Exception {
        var handle = ProcessHandle.of(pid);
        if (handle.isPresent() && handle.get().isAlive()) handle.get().onExit().get(5, TimeUnit.SECONDS);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }
}
