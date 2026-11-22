package org.example;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.regex.Pattern;

/** One-line input for completed activities, independent of planning slots. */
final class ActivityParser {
    record Parsed(String title, String start, String end, int minutes) {
        boolean timed() { return !start.isBlank(); }
    }
    private static final Pattern RANGE = Pattern.compile("^(\\d{1,2})[:.](\\d{2})\\s*(?:[–—-]|ile)\\s*(\\d{1,2})[:.](\\d{2})\\s*(.*)$");
    private static final Pattern DURATION = Pattern.compile("^(?:(\\d+(?:[.,]\\d+)?)\\s*(?:saat|sa|s)\\.?(?=\\s|\\d|$))?\\s*(?:(\\d+)\\s*(?:dakika|dk|d)\\.?(?=\\s|$))?\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    static Parsed parse(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Saat aralığı veya süre ve ne yaptığını yaz.");
        var range = RANGE.matcher(value);
        if (range.matches()) {
            LocalTime start = time(range.group(1), range.group(2)), end = time(range.group(3), range.group(4));
            int minutes = (int) java.time.Duration.between(start, end).toMinutes();
            if (minutes <= 0) throw new IllegalArgumentException("Bitiş başlangıçtan sonra olmalı. Gece yarısını geçen çalışmayı iki kayda böl.");
            return new Parsed(title(range.group(5)), format(start), format(end), minutes);
        }
        if (value.matches("^\\d{1,2}[:.]\\d{2}.*")) throw new IllegalArgumentException("Bitiş saatini de yaz: 10:30–11:15 açıklama.");
        var duration = DURATION.matcher(value);
        if (duration.matches() && (duration.group(1) != null || duration.group(2) != null)) {
            try {
                BigDecimal hours = new BigDecimal(duration.group(1) == null ? "0" : duration.group(1).replace(',', '.'));
                int minutes = hours.multiply(new BigDecimal(60)).add(new BigDecimal(duration.group(2) == null ? "0" : duration.group(2))).intValueExact();
                if (minutes < 1 || minutes > 960) throw new IllegalArgumentException("Süre 1 dakika ile 16 saat arasında olmalı.");
                return new Parsed(title(duration.group(3)), "", "", minutes);
            } catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException("Süreyi tam dakika olarak yaz: 30 dk veya 1,5 saat."); }
        }
        throw new IllegalArgumentException("Başa saat aralığı veya süre ekle: 10:30–11:15 açıklama ya da 30 dk açıklama.");
    }
    private static String title(String value) {
        if (value.isBlank()) throw new IllegalArgumentException("Ne yaptığını da yaz.");
        if (value.length() > 1000) throw new IllegalArgumentException("Açıklama en fazla 1000 karakter olabilir.");
        return value.trim();
    }
    private static LocalTime time(String hour, String minute) {
        try { return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute)); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Saat 00–23, dakika 00–59 arasında olmalı."); }
    }
    private static String format(LocalTime value) { return value.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")); }
}
