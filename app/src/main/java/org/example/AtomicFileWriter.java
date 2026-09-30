package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Writes beside the destination before replacing it, preserving the old file on write failure. */
public final class AtomicFileWriter {
    private AtomicFileWriter() {}

    public static void writeText(Path path, String text) throws IOException {
        write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path path, byte[] bytes) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) target = target.toRealPath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".academic-os-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
