package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tracks a web document independently of the file explorer's editor. */
public class WebNotesSession {
    private Path path;
    private String text = "";
    private boolean dirty;

    public String open(Path nextPath) throws IOException {
        save();
        // Reading must succeed before changing the active document.
        String nextText = Files.exists(nextPath) ? Files.readString(nextPath, StandardCharsets.UTF_8) : "";
        path = nextPath;
        text = nextText;
        dirty = false;
        return text;
    }

    public void edit(String newText) {
        if (path != null && !text.equals(newText)) {
            text = newText;
            dirty = true;
        }
    }

    public void save() throws IOException {
        if (path != null && dirty) {
            AtomicFileWriter.writeText(path, text);
            dirty = false;
        }
    }

    public void close() throws IOException {
        save();
        path = null;
        text = "";
    }
}
