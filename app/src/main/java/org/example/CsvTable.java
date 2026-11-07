package org.example;

import java.io.IOException;
import java.util.*;

/** RFC 4180 text cells, including multiline fields; used by application import/export. */
final class CsvTable {
    static String cell(String value) { return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\""; }
    static List<List<String>> parse(String text) throws IOException {
        if (text.length() > FinanceStorageService.MAX_BYTES) throw new IOException("CSV çok büyük.");
        text = text.startsWith("\uFEFF") ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i <= text.length(); i++) {
            char c = i == text.length() ? '\n' : text.charAt(i);
            if (quoted) {
                if (i == text.length()) throw new IOException("CSV tırnağı kapanmamış.");
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; } } else field.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); closed = false;
                if (row.size() > 100) throw new IOException("CSV'de çok fazla sütun.");
                if (c != ',') { if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(List.copyOf(row)); row.clear();
                    if (rows.size() > 10001) throw new IOException("CSV'de çok fazla satır.");
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++; }
            } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
            else if (closed || c == '"') throw new IOException("CSV tırnak veya ayırıcı hatası.");
            else field.append(c);
        }
        return rows;
    }
}
