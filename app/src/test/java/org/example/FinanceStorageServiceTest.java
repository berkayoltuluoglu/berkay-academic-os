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

    @Test void cardStatementAndTotalBalancesAreValidatedWithoutRewritingLegacyRecords() throws Exception {
        FinanceStorageService storage = new FinanceStorageService(directory.resolve("ledger.json"));
        var data = FinanceTestData.populated();
        var card = (com.fasterxml.jackson.databind.node.ObjectNode)data.withArray("debts").get(0);
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(storage.validate(data.toString()))
                .path("debts").get(0).has("totalBalance"));
        card.put("balance", 0).put("totalBalance", 12440.52);
        storage.validate(data.toString());
        card.put("balance", .01);
        storage.validate(data.toString());
        card.put("totalBalance", 0);
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
        card.put("totalBalance", -1);
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
        card.put("totalBalance", "not-a-number");
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
    }

    @Test void flowAccountAndReservedCashEffectsCannotBeMalformed() throws Exception {
        FinanceStorageService storage = new FinanceStorageService(directory.resolve("ledger.json"));
        var data = FinanceTestData.populated();
        var flow = data.withArray("flows").addObject().put("id", "flow").put("type", "out")
                .put("amount", 4980).put("date", "2026-11-22").put("accountId", "bank").put("restrictedUsed", 480);
        storage.validate(data.toString());
        flow.put("restrictedUsed", 4981);
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
        flow.put("restrictedUsed", 1).put("type", "in");
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
        flow.put("type", "out").put("accountId", "");
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
        flow.put("restrictedUsed", 0).put("accountId", "bad\"id");
        assertThrows(IOException.class, () -> storage.validate(data.toString()));
    }

    @Test void legacyLedgerLoadsNewCollectionsWithoutWritingTheOriginalFile() throws Exception {
        Path path=directory.resolve("ledger.json");
        String original=FinanceTestData.populated().put("schemaVersion",1).toString(); Files.writeString(path,original);
        FinanceStorageService storage=new FinanceStorageService(path);
        String loaded=storage.load();
        var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(loaded);
        assertTrue(root.path("installments").isArray());assertTrue(root.path("statements").isArray());
        assertEquals(original,Files.readString(path));
        assertEquals(2,root.path("schemaVersion").asInt());
        storage.save(loaded);
        assertEquals(original,Files.readString(storage.backupPath()));
    }

    @Test void installmentPlansRejectMissingCardsFractionalCountsAndInvalidDates() throws Exception {
        FinanceStorageService storage=new FinanceStorageService(directory.resolve("ledger.json"));
        var data=FinanceTestData.populated();
        var plan=data.putArray("installments").addObject().put("id","purchase").put("name","Kitap")
                .put("cardId","card").put("amount",100).put("count",3).put("paidCount",1).put("firstDue","2027-01-31");
        storage.validate(data.toString());
        plan.put("amount","100,00").put("count","3,00").put("paidCount","1,00");
        var normalized=new com.fasterxml.jackson.databind.ObjectMapper().readTree(storage.validate(data.toString())).path("installments").get(0);
        assertTrue(normalized.path("amount").isNumber()); assertEquals(3,normalized.path("count").asInt());
        plan.put("cardId","loan"); assertThrows(IOException.class,()->storage.validate(data.toString()));
        plan.put("cardId","card").put("count",1.5); assertThrows(IOException.class,()->storage.validate(data.toString()));
        plan.put("count",3).put("paidCount",4); assertThrows(IOException.class,()->storage.validate(data.toString()));
        plan.put("paidCount",0).put("firstDue","2027-02-30"); assertThrows(IOException.class,()->storage.validate(data.toString()));
        plan.put("firstDue","2027-01-31").put("amount",.01); assertThrows(IOException.class,()->storage.validate(data.toString()));
    }

    @Test void transferReferencesAndStatementRowsAreValidatedBeforeSaving() throws Exception {
        FinanceStorageService storage=new FinanceStorageService(directory.resolve("ledger.json"));
        var data=FinanceTestData.populated();
        data.withArray("accounts").addObject().put("id","wallet").put("name","Nakit").put("balance",100);
        var flow=data.withArray("flows").addObject().put("id","transfer").put("type","transfer").put("amount",20)
                .put("restrictedAmount",5).put("accountId","bank").put("targetAccountId","wallet").put("date","2026-11-22");
        storage.validate(data.toString());
        flow.put("targetAccountId","bank");assertThrows(IOException.class,()->storage.validate(data.toString()));
        flow.put("targetAccountId","missing");assertThrows(IOException.class,()->storage.validate(data.toString()));
        flow.put("targetAccountId","wallet").put("restrictedAmount",21);assertThrows(IOException.class,()->storage.validate(data.toString()));
        flow.put("restrictedAmount",5);
        var report=data.putArray("statements").addObject().put("id","statement").put("name","Ekstre").put("accountId","bank")
                .put("from","2026-11-01").put("to","2026-11-30");
        var row=report.putArray("rows").addObject().put("date","2026-11-22").put("type","out").put("amount",20).put("note","Transfer");
        storage.validate(data.toString());
        row.put("date","2026-02-30");assertThrows(IOException.class,()->storage.validate(data.toString()));
        row.put("date","2026-11-22").put("amount",-20);assertThrows(IOException.class,()->storage.validate(data.toString()));
        row.put("amount",20);report.put("from","2026-12-01");assertThrows(IOException.class,()->storage.validate(data.toString()));
    }
}
