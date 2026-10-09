package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CourseCsvImporterTest {
    @TempDir Path dir;
    static final String HEADER = "Ders Kodu;Ders Adı;Öğretim Elemanı;Gün Saat;Kredi;AKTS\n";
    static final String CSV = HEADER
            + "EE201.1;Devre Analizi;Ayşe;Pazartesi 9:00-10:50 [D101] | Çarşamba 13:00-14:50 [Lab];3,5;5\n"
            + "EE201.2;Devre Analizi;Ali;Salı 11:00-12:50 [D102];3,5;5\n";

    @Test void importsTurkishBomQuotedMultilineFieldsAndSessionRooms() throws Exception {
        String csv = "\uFEFFDers Kodu,Ders Adı,Öğretim Elemanı,Gün Saat,Credit,ECTS\r\n"
                + "EE201.1,\"Devre, analiz\nve uygulama\",\"Ayşe \"\"Hoca\"\"\",\"Pazartesi 9:00-10:50 [D101] | Çarşamba 13:00-14:50 [Lab]\",\"3,5\",5\r\n";
        var sections = new CourseCsvImporter().parse(csv);
        var s = sections.getFirst();
        assertEquals("EE201", s.code()); assertEquals("EE201.1", s.fullCode());
        assertEquals("Devre, analiz\nve uygulama", s.name()); assertEquals("Ayşe \"Hoca\"", s.instructor());
        assertEquals(new BigDecimal("3.5"), s.credit());
        assertEquals(2, s.lessons().size()); assertEquals("09:00", s.lessons().getFirst().start());
        assertEquals("Lab", s.lessons().get(1).room());
    }

    @Test void partialScheduleNeverSilentlyDropsSecondMeeting() {
        for (String schedule : new String[]{"Pazartesi 09:00-10:00 [D1] | Salı 13.00-14.00 [D2]",
                "Pazartesi 09:00-10:00 |", "Pazartesi 09:00-10:00 ek açıklama",
                "Pazartesi 09:00-10:00 | Salı 13:00-25:00", "Pazartesi 10:00-09:00",
                "Pazartesi 09:00-11:00 | Pazartesi 10:00-12:00"}) {
            IOException error = assertThrows(IOException.class, () -> new CourseCsvImporter().parse(HEADER + "EE201.1;Ders;Hoca;" + schedule + ";3;5"));
            assertTrue(error.getMessage().contains("satır 2"), error.getMessage());
        }
    }

    @Test void universityExportAcceptsRoomHeaderAndAdjacentMeetingsWithoutLosingHours() throws Exception {
        String csv = "Ders Kodu,Ders Adı,Öğretim Elemanı,Teorik,Uygulama,Kredi,Kontenjan,Gün Saat Derslik,Uzem Dersi\n"
                + "ATA121.1,Tarih,Hoca,2,0,2,0,Cumartesi 15:00 - 15:50 [UZEM] Cumartesi 16:00 - 16:50 [UZEM] ,Evet\n"
                + "MAT1087.1,Lineer Cebir,Hoca,3,0,5,0,Salı 08:30 - 09:20 [T2-Z07] Salı 09:30 - 10:20 [T2-Z07] Salı 10:30 - 11:20 [T2-Z07] ,Hayır\n";
        var imported = new CourseCsvImporter().parse(csv);
        assertEquals(2, imported.getFirst().lessons().size());
        assertEquals(3, imported.get(1).lessons().size());
        assertEquals("10:30", imported.get(1).lessons().get(2).start());
        assertNull(imported.getFirst().ects());
        var repeated = new CourseCsvImporter().parse(csv.replace("Cumartesi 16:00 - 16:50 [UZEM]", "Cumartesi 16:00 - 16:50 [UZEM] Cumartesi 15:00 - 15:50 [UZEM]"));
        assertEquals(imported, repeated, "Aynı gün/saat/derslik tekrarları program yükünü artırmamalı");
        assertThrows(IOException.class, () -> new CourseCsvImporter().parse(csv.replace("16:00 - 16:50", "16.00 - 16.50")));
    }

    @Test void rejectsMalformedRowsQuotingHeadersAndNumbers() {
        for (String csv : new String[]{HEADER + "EE201.1;Ders;Hoca;;3;5;extra", HEADER + ";Ders;Hoca;;3;5",
                HEADER + "EE201.1;\"Ders;Hoca;;3;5", HEADER + "EE201.1;Ders;Hoca;;abc;5",
                HEADER + "EE201.1;Ders;Hoca;;-1;5", HEADER + "EE201.1;Ders;Hoca;;1001;5",
                "Ders Kodu;Ders Kodu;Gün Saat\nA;A;", "Ders Kodu;Ders Adı\nA;Ad"}) {
            assertThrows(IOException.class, () -> new CourseCsvImporter().parse(csv), csv);
        }
    }

    @Test void missingHoursAndCreditsStayUnknownAndDuplicateImportsHaveStableIds() throws Exception {
        var importer = new CourseCsvImporter();
        var sections = importer.parse(HEADER + "ABC.1;Adsız;;;0;\nABC.1;Adsız;;;0;\n");
        assertEquals(1, sections.size()); assertTrue(sections.getFirst().lessons().isEmpty());
        assertEquals(BigDecimal.ZERO, sections.getFirst().credit()); assertNull(sections.getFirst().ects());
        assertEquals(importer.parse(CSV), importer.parse(CSV));
        assertEquals(2, importer.parse(CSV.replace(';', '\t')).size());
    }

    @Test void fileReadRejectsMalformedUtf8AndOversizedInput() throws Exception {
        var file = dir.resolve("courses.csv");
        Files.write(file, new byte[]{(byte)0xc3, 0x28});
        assertTrue(assertThrows(IOException.class, () -> new CourseCsvImporter().read(file)).getMessage().contains("UTF-8"));
        try (var stream = Files.newOutputStream(file)) { stream.write(new byte[10 * 1024 * 1024 + 1]); }
        assertThrows(IOException.class, () -> new CourseCsvImporter().read(file));
        Files.writeString(file, CSV);
        assertEquals(2, new CourseCsvImporter().read(file).size());
    }
}
