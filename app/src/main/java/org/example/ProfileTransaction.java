package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Small related record files commit together or return to their previous bytes. */
final class ProfileTransaction {
    static <T> T run(Path root, List<String> names, AtomicFileWriter.IoSupplier<T> action) throws IOException {
        return AtomicFileWriter.exclusive(() -> {
            Map<Path, byte[]> before = new LinkedHashMap<>();
            for (String name : names) {
                Path file = root.resolve(name);
                before.put(file, Files.exists(file) ? FinanceStorageService.readLimited(file) : null);
            }
            try { return action.get(); }
            catch (IOException | RuntimeException failure) {
                boolean interrupted = Thread.interrupted();
                IOException result = new IOException(failure.getMessage(), failure);
                for (var entry : before.entrySet()) {
                    try {
                        if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                        else AtomicFileWriter.write(entry.getKey(), entry.getValue());
                    } catch (IOException recovery) { result.addSuppressed(recovery); }
                }
                if (interrupted) Thread.currentThread().interrupt();
                throw result;
            }
        });
    }
}
