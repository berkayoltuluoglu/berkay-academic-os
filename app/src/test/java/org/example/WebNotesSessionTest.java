package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class WebNotesSessionTest {
    @TempDir Path directory;

    @Test void switchingAndClosingSaveTheCorrectDocument() throws Exception {
        Path first = directory.resolve("first.txt");
        Path second = directory.resolve("second.txt");
        Files.writeString(first, "İlk not");
        Files.writeString(second, "İkinci not");
        WebNotesSession session = new WebNotesSession();
        assertEquals("İlk not", session.open(first));
        session.edit("İlk not değişti");
        assertEquals("İkinci not", session.open(second));
        assertEquals("İlk not değişti", Files.readString(first));
        session.edit("İkinci not değişti");
        session.close();
        session.edit("Dosya düzenleyicisi");
        session.save();
        assertEquals("İkinci not değişti", Files.readString(second));
    }

    @Test void failedReadDoesNotReplaceExistingNotesWithAnErrorMessage() throws Exception {
        Path file = directory.resolve("notes.txt");
        WebNotesSession session = new WebNotesSession();
        session.open(file);
        session.edit("Sakla");
        assertThrows(IOException.class, () -> session.open(directory));
        assertEquals("Sakla", Files.readString(file));
        session.edit("Tekrar dene");
        session.save();
        assertEquals("Tekrar dene", Files.readString(file));
    }

    @Test void failedSaveRemainsPendingAndCanBeRetried() throws Exception {
        Path file = directory.resolve("notes.txt");
        WebNotesSession session = new WebNotesSession();
        session.open(file);
        session.edit("Kaybolmasın");
        Files.createDirectory(file);
        assertThrows(IOException.class, session::close);
        Files.delete(file);
        session.close();
        assertEquals("Kaybolmasın", Files.readString(file));
    }
}
