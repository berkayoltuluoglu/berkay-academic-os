package org.example;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.example.CourseData.*;

/** Strict UTF-8 CSV import. A bad row rejects the whole import, never a partial timetable. */
public final class CourseCsvImporter {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final com.fasterxml.jackson.databind.ObjectMapper ID_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final List<String> DAYS = List.of("pazartesi", "sali", "carsamba", "persembe", "cuma", "cumartesi", "pazar");
    private static final Pattern SLOT = Pattern.compile("(?iu)([\\p{L}]+)\\s+(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})(?:\\s*\\[([^\\[\\]]*)\\])?");
    private record Row(int line, List<String> cells) {}

    public List<Section> read(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("CSV dosyası en fazla 10 MB olabilir");
        try { return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()); }
        catch (java.nio.charset.CharacterCodingException ex) { throw new IOException("CSV dosyasını UTF-8 olarak kaydedin", ex); }
    }

    public List<Section> parse(String input) throws IOException {
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        if (input.length() > MAX_BYTES) throw new IOException("CSV dosyası çok büyük");
        List<Row> rows = rows(input, delimiter(input));
        if (rows.size() < 2) throw new IOException("CSV başlığı ve en az bir ders satırı gerekiyor");
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < rows.getFirst().cells().size(); i++) {
            String header = normalize(rows.getFirst().cells().get(i));
            if (headers.put(header, i) != null) throw new IOException("Tekrarlanan CSV sütunu: " + header);
        }
        int code = column(headers, "ders kodu", "derskodu", "code");
        if (code < 0) throw new IOException("Ders Kodu sütunu bulunamadı");
        int name = column(headers, "ders adi", "dersadi", "name");
        int instructor = column(headers, "ogretim elemani", "instructor");
        int schedule = column(headers, "gun saat", "gun saat derslik", "gun/saat", "schedule", "program");
        if (schedule < 0) schedule = headers.entrySet().stream()
                .filter(e -> e.getKey().contains("gun") && e.getKey().contains("saat"))
                .mapToInt(Map.Entry::getValue).min().orElse(-1);
        if (schedule < 0) throw new IOException("Gün Saat sütunu bulunamadı (saatsiz derslerde hücre boş kalabilir)");
        int credit = column(headers, "kredi", "credit", "credits", "ders kredisi", "ulusal kredi");
        int ects = column(headers, "akts", "ects", "akts kredisi");
        Map<String, Section> result = new LinkedHashMap<>();
        for (Row row : rows.subList(1, rows.size())) {
            try {
                if (row.cells().size() != rows.getFirst().cells().size()) throw new IOException("Sütun sayısı başlıkla uyuşmuyor");
                String fullCode = cell(row, code).toUpperCase(Locale.ROOT);
                if (!fullCode.matches("[\\p{L}0-9][\\p{L}0-9_.-]{0,79}")) throw new IOException("Ders kodu boş veya geçersiz");
                // The source planner uses the part before the first dot as the course code.
                String rootCode = fullCode.split("\\.", 2)[0];
                String title = cell(row, name).isBlank() ? rootCode : cell(row, name);
                List<Lesson> lessons = parseSchedule(cell(row, schedule));
                BigDecimal credits = number(cell(row, credit)), akts = number(cell(row, ects));
                String teacher = cell(row, instructor);
                String identity = ID_MAPPER.writeValueAsString(
                        Arrays.asList(fullCode, title, teacher, credits, akts, lessons));
                String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
                Section section = new Section(id, rootCode, fullCode, title, teacher, credits, akts, lessons);
                CourseStorageService.validate(new CourseData(VERSION, List.of(), List.of(), List.of(section)));
                result.putIfAbsent(id, section);
                if (result.size() > 5000) throw new IOException("En fazla 5000 şube aktarılabilir");
            } catch (Exception ex) { throw new IOException("CSV satır " + row.line() + ": " + ex.getMessage(), ex); }
        }
        return List.copyOf(result.values());
    }

    static List<Lesson> parseSchedule(String value) throws IOException {
        if (value.isBlank()) return List.of();
        List<Lesson> result = new ArrayList<>();
        String remaining = value.trim();
        while (!remaining.isEmpty()) {
            var match = SLOT.matcher(remaining);
            // Every character must be consumed: whitespace-separated university exports
            // and pipe-separated planner exports are both accepted without dropping fragments.
            if (!match.lookingAt()) throw new IOException("Ders saati okunamadı: " + remaining
                    + ". Örnek: Pazartesi 09:00-10:50 [D101] | Çarşamba 13:00-14:50 [Lab]");
            String part = match.group();
            int day = DAYS.indexOf(normalize(match.group(1))) + 1;
            try {
                LocalTime start = time(match.group(2)), end = time(match.group(3));
                if (day == 0 || !end.isAfter(start)) throw new IllegalArgumentException();
                Lesson slot = new Lesson(day, start.toString(), end.toString(), match.group(4) == null ? "" : match.group(4).trim());
                // University exports may repeat an identical meeting in the same cell.
                if (!result.contains(slot)) {
                    if (result.stream().anyMatch(l -> l.day() == day && start.isBefore(LocalTime.parse(l.end()))
                            && LocalTime.parse(l.start()).isBefore(end))) throw new IOException("Şubenin kendi saatleri çakışıyor");
                    result.add(slot);
                }
            } catch (RuntimeException ex) { throw new IOException("Geçersiz gün/saat: " + part.trim(), ex); }
            String tail = remaining.substring(match.end());
            if (!tail.isEmpty() && !Character.isWhitespace(tail.charAt(0)) && tail.charAt(0) != '|')
                throw new IOException("Ders oturumları arasında boşluk veya | gerekiyor: " + tail);
            remaining = tail.trim();
            if (remaining.startsWith("|")) {
                remaining = remaining.substring(1).trim();
                if (remaining.isEmpty()) throw new IOException("Ayırıcıdan sonra ders oturumu eksik");
            }
        }
        result.sort(Comparator.comparingInt(Lesson::day).thenComparing(Lesson::start));
        return List.copyOf(result);
    }

    private static LocalTime time(String value) {
        String[] parts = value.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    static BigDecimal number(String value) throws IOException {
        if (value.isBlank()) return null;
        if (!value.matches("\\d+(?:[.,]\\d{1,2})?")) throw new IOException("Geçersiz kredi/AKTS: " + value);
        BigDecimal number = new BigDecimal(value.replace(',', '.')).stripTrailingZeros();
        if (number.compareTo(new BigDecimal("1000")) > 0) throw new IOException("Kredi/AKTS en fazla 1000 olabilir");
        return number;
    }
    static String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('ı', 'i').replaceAll("\\s+", " ");
    }
    private static int column(Map<String, Integer> headers, String... names) {
        for (String name : names) if (headers.containsKey(name)) return headers.get(name);
        return -1;
    }
    private static String cell(Row row, int index) { return index < 0 ? "" : row.cells().get(index).trim(); }
    private static char delimiter(String text) {
        boolean quote = false;
        int[] counts = new int[3]; char[] separators = {',', ';', '\t'};
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quote = !quote;
            if (!quote && (c == '\n' || c == '\r')) break;
            if (!quote) for (int j = 0; j < separators.length; j++) if (c == separators[j]) counts[j]++;
        }
        int best = 0;
        for (int j = 1; j < counts.length; j++) if (counts[j] > counts[best]) best = j;
        return separators[best];
    }

    /** Handles escaped quotes, embedded newlines and CRLF; rejects malformed quoting. */
    private static List<Row> rows(String text, char delimiter) throws IOException {
        List<Row> rows = new ArrayList<>(); List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        int line = 1, rowLine = 1;
        for (int i = 0; i <= text.length(); i++) {
            char c = i == text.length() ? '\n' : text.charAt(i);
            if (quoted) {
                if (i == text.length()) throw new IOException("CSV satır " + rowLine + ": Kapanmamış tırnak");
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else { field.append(c); if (c == '\n') line++; }
            } else if (c == delimiter || c == '\n' || c == '\r') {
                cells.add(field.toString()); field.setLength(0); closed = false;
                if (cells.size() > 100) throw new IOException("CSV satır " + rowLine + ": Çok fazla sütun");
                if (c != delimiter) {
                    if (cells.stream().anyMatch(v -> !v.isBlank())) rows.add(new Row(rowLine, List.copyOf(cells)));
                    if (rows.size() > 10001) throw new IOException("CSV en fazla 10000 veri satırı içerebilir");
                    cells.clear();
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    rowLine = ++line;
                }
            } else if (c == '"' && field.isEmpty() && !closed) { quoted = true; }
            else if (closed || c == '"') throw new IOException("CSV satır " + line + ": Geçersiz tırnak veya ayırıcı");
            else field.append(c);
        }
        return rows;
    }
}
