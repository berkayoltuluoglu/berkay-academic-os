package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScholarshipTest {
    @TempDir Path root;
    static ScholarshipData.Application application(String status,String result) {
        return new ScholarshipData.Application("app", "Öğrenci bursu", "Burs", "2.500 TL / ay", LocalDate.now().toString(), "", status,result,
                LocalDate.now().toString(),List.of(new ScholarshipData.Document("document","Transkript, imzalı",true)),"Sonuç \"notu\"\nİkinci satır","https://example.com/apply");
    }
    @Test void readingCreatesNoFileAndRoundTripsDocumentsWithoutTouchingLedger() throws Exception {
        Path file=root.resolve("data/finance/applications.json"); var store=new ScholarshipStorageService(file);
        assertEquals(ScholarshipData.empty(),store.load()); assertFalse(Files.exists(file));
        var data=new ScholarshipData(1,List.of(application("Planlanıyor","Beklemede"))); store.save(data);
        assertEquals(data,new ScholarshipStorageService(file).load()); assertFalse(Files.exists(file.resolveSibling("ledger.json")));
    }
    @Test void staleWriterAndInvalidRecordCannotOverwriteExistingData() throws Exception {
        Path file=root.resolve("applications.json"); var first=new ScholarshipStorageService(file); var stale=new ScholarshipStorageService(file);
        first.load(); stale.load(); var data=new ScholarshipData(1,List.of(application("Planlanıyor","Beklemede"))); first.save(data);
        byte[] before=Files.readAllBytes(file); assertThrows(java.io.IOException.class,()->stale.save(ScholarshipData.empty()));
        assertThrows(java.io.IOException.class,()->first.save(new ScholarshipData(1,List.of(data.applications().getFirst(),data.applications().getFirst()))));
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void csvPreservesQuotesMultilineNotesDocumentPunctuationAndReadiness() throws Exception {
        var data=new ScholarshipData(1,List.of(application("Başvuruldu - Bekliyor","Yedek")));
        assertEquals(data,ScholarshipCsv.parse(ScholarshipCsv.export(data)));
        assertThrows(java.io.IOException.class,()->ScholarshipCsv.parse("\"Broken"));
    }
    @Test void importsTheOriginalNineColumnApplicationCsvIdempotently() throws Exception {
        String text="Burs Adı,Burs Miktarı,Son Başvuru Tarihi,Başvurulan Tarih,Gerekli Belgeler,Durum,Sonuç,Açıklanma Tarihi,Sonuç Notu\n"
                +"\"TEV\",\"2500 TL/Ay\",\"2026-10-30\",\"-\",\"Transkript, Öğrenci belgesi\",\"Evrak Toplanıyor\",\"Beklemede\",\"-\",\"-\"\n";
        var data=ScholarshipCsv.parse(text); assertEquals(data,ScholarshipCsv.parse(text));
        assertEquals(2,data.applications().getFirst().documents().size()); assertEquals("",data.applications().getFirst().appliedOn());
    }
    @Test void applicationDeadlineStopsAppearingAfterSubmissionAndResultDeadlineTakesItsPlace() throws Exception {
        var store=new ScholarshipStorageService(root.resolve("data/finance/applications.json")); store.load();
        store.save(new ScholarshipData(1,List.of(application("Planlanıyor","Beklemede"))));
        var today=LocalDate.now(); var day=new DailyAgendaService().load(root,today);
        assertTrue(day.todos().stream().anyMatch(t->t.id().equals("application-app")));
        store.save(new ScholarshipData(1,List.of(application("Başvuruldu - Bekliyor","Beklemede"))));
        day=new DailyAgendaService().load(root,today);
        assertFalse(day.todos().stream().anyMatch(t->t.id().equals("application-app")));
        assertTrue(day.todos().stream().anyMatch(t->t.id().equals("application-result-app")));
        store.save(new ScholarshipData(1,List.of(application("Olumlu Sonuçlandı","Kazanıldı"))));
        assertTrue(new DailyAgendaService().load(root,today).todos().isEmpty());
    }
    @Test void fullProfileBackupRestoresApplicationChecklist() throws Exception {
        var file=root.resolve("profile/data/finance/applications.json"); var store=new ScholarshipStorageService(file); store.load();
        var data=new ScholarshipData(1,List.of(application("Planlanıyor","Beklemede"))); store.save(data);
        var backup=new BackupService(root.resolve("profile")); Path archive=root.resolve("applications.zip"); backup.exportTo(archive);
        store.save(ScholarshipData.empty()); backup.restoreFrom(archive);
        assertEquals(data,new ScholarshipStorageService(file).load());
    }
}
