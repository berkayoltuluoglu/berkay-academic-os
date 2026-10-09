package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FinanceStorageServiceTest {
    @TempDir Path directory;

    @Test void roundTripBackupAndRecoveryPreserveThePreviousLedger() throws Exception {
        Path file = directory.resolve("ledger.json");
        FinanceStorageService storage = new FinanceStorageService(file);
        assertEquals("null", storage.load());
        storage.save(FinanceTestData.populated().toString());
        String original = Files.readString(file);
        storage.save(FinanceTestData.empty().toString());
        assertEquals(original, Files.readString(storage.backupPath()));
        Files.writeString(file, "broken");
        assertThrows(IOException.class, storage::load);
        assertEquals("broken", Files.readString(file));
        storage.restoreBackup();
        assertEquals(original, Files.readString(file));
        assertTrue(storage.load().contains("Örnek kart"));
        try (var files = Files.list(directory)) {
            Path recovery = files.filter(p -> p.getFileName().toString().startsWith("ledger.json.recovery-")).findFirst().orElseThrow();
            assertEquals("broken", Files.readString(recovery));
        }
    }

    @Test void invalidDataAndDuplicateIdsDoNotReplaceAnExistingLedger() throws Exception {
        Path file = directory.resolve("ledger.json");
        FinanceStorageService storage = new FinanceStorageService(file);
        storage.load();
        storage.save(FinanceTestData.populated().toString());
        String original = Files.readString(file);
        var invalid = FinanceTestData.populated();
        invalid.withArray("debts").add(invalid.withArray("debts").get(0).deepCopy());
        assertThrows(IOException.class, () -> storage.save(invalid.toString()));
        assertThrows(IOException.class, () -> storage.save("{}"));
        assertEquals(original, Files.readString(file));
    }

    @Test void rejectsInvalidDatesAmountsAndUnsafeIds() throws Exception {
        FinanceStorageService storage = new FinanceStorageService(directory.resolve("ledger.json"));
        for (String pair : new String[]{"balance|-1", "due|2026-02-30", "id|bad\"onclick", "balance|1abc"}) {
            String[] value = pair.split("\\|", 2);
            var data = FinanceTestData.populated();
            ((com.fasterxml.jackson.databind.node.ObjectNode)data.withArray("debts").get(0)).put(value[0], value[1]);
            assertThrows(IOException.class, () -> storage.validate(data.toString()));
        }
        var future = FinanceTestData.empty().put("schemaVersion", 99);
        assertThrows(IOException.class, () -> storage.validate(future.toString()));
    }

    @Test void staleWriterCannotOverwriteAnotherInstance() throws Exception {
        Path file = directory.resolve("ledger.json");
        FinanceStorageService first = new FinanceStorageService(file);
        FinanceStorageService second = new FinanceStorageService(file);
        first.load(); second.load();
        first.save(FinanceTestData.populated().toString());
        String saved = Files.readString(file);
        assertThrows(IOException.class, () -> second.save(FinanceTestData.empty().toString()));
        assertEquals(saved, Files.readString(file));
    }

    @Test void failedBackupPreventsSaveAndKeepsCurrentData() throws Exception {
        Path file = directory.resolve("ledger.json");
        FinanceStorageService storage = new FinanceStorageService(file);
        storage.load();
        storage.save(FinanceTestData.populated().toString());
        String saved = Files.readString(file);
        Files.createDirectory(storage.backupPath());
        Files.writeString(storage.backupPath().resolve("keep"), "keep");
        assertThrows(IOException.class, () -> storage.save(FinanceTestData.empty().toString()));
        assertEquals(saved, Files.readString(file));
    }
}
