package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Versioned, validated local ledger storage, independent of the HTML view. */
public final class FinanceStorageService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String[] COLLECTIONS = {"debts", "flows", "accounts", "subs", "incomes", "goals"};
    private static final Set<String> MONEY_FIELDS = Set.of("limit", "balance", "principal", "avans", "amount",
            "saved", "kisitli", "taksit", "donemHedef", "donemOdenen");
    private static final Set<String> RATE_FIELDS = Set.of("rate", "rateOverride", "asgariOverride", "gecikmeOverride");
    private static final Set<String> DATE_FIELDS = Set.of("date", "due", "kesim", "alindi");
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private byte[] lastSaved;
    private boolean loaded;

    public FinanceStorageService(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public synchronized String load() throws IOException {
        byte[] bytes = Files.exists(file) ? readLimited(file) : null;
        String value = bytes == null ? "null" : validate(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        lastSaved = bytes;
        loaded = true;
        return value;
    }

    public synchronized void save(String json) throws IOException {
        if (!loaded) throw new IOException("Defter yüklenmeden kayıt yapılamaz");
        byte[] bytes = validate(json).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Defter başka bir uygulama tarafından kaydediliyor");
            byte[] current = Files.exists(file) ? readLimited(file) : null;
            if (!Arrays.equals(lastSaved, current)) {
                throw new IOException("Defter başka bir yerde değişti. Modülü yeniden açıp tekrar deneyin");
            }
            if (Arrays.equals(current, bytes)) return;
            if (current != null) AtomicFileWriter.write(backupPath(), current);
            AtomicFileWriter.write(file, bytes);
            lastSaved = bytes;
        } catch (java.nio.channels.OverlappingFileLockException ex) {
            throw new IOException("Defter başka bir uygulama tarafından kaydediliyor", ex);
        }
    }

    public Path backupPath() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    public synchronized void restoreBackup() throws IOException {
        byte[] backup = readLimited(backupPath());
        validate(new String(backup, java.nio.charset.StandardCharsets.UTF_8));
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Defter başka bir uygulama tarafından kullanılıyor");
            // Preserve the unreadable original too; never silently overwrite it during recovery.
            if (Files.exists(file)) {
                Path recovery = file.resolveSibling(file.getFileName() + ".recovery-" + java.util.UUID.randomUUID());
                Files.copy(file, recovery);
            }
            AtomicFileWriter.write(file, backup);
            loaded = false;
        }
    }

    public String validate(String json) throws IOException {
        if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("Defter boyutu en fazla 5 MB olabilir");
        }
        JsonNode parsed = mapper.readTree(json);
        require(parsed != null && parsed.isObject(), "Geçerli bir defter nesnesi gerekiyor");
        ObjectNode root = (ObjectNode) parsed;
        require(!root.has("schemaVersion") || (root.path("schemaVersion").isIntegralNumber()
                && root.path("schemaVersion").asInt() == 1), "Desteklenmeyen defter sürümü");
        checkTree(root, 0);
        for (String collection : COLLECTIONS) {
            JsonNode entries = root.get(collection);
            require(entries != null && entries.isArray(), "Eksik veya geçersiz kayıt grubu: " + collection);
            require(entries.size() <= 10000, "Bir kayıt grubunda en fazla 10000 kayıt olabilir");
            Set<String> ids = new HashSet<>();
            for (JsonNode entry : entries) {
                require(entry.isObject(), "Kayıt nesne olmalı: " + collection);
                String id = entry.path("id").asText();
                require(id.matches("[a-zA-Z0-9_-]{1,80}") && ids.add(id), "Geçersiz veya tekrarlanan kayıt kimliği");
                if (!collection.equals("flows")) require(entry.path("name").isTextual()
                        && !entry.path("name").asText().isBlank(), "Kayıt adı boş olamaz");
                for (String field : MONEY_FIELDS) optionalNumber(entry, field, new BigDecimal("1000000000000"), false);
                for (String field : RATE_FIELDS) optionalNumber(entry, field, new BigDecimal("100"), false);
                for (String field : DATE_FIELDS) {
                    JsonNode value = entry.get(field);
                    if (value != null && !value.isNull() && !value.asText().isBlank()) {
                        require(value.isTextual() && value.asText().matches("\\d{4}-\\d{2}-\\d{2}"), "Geçersiz tarih: " + field);
                        try { LocalDate.parse(value.asText()); }
                        catch (RuntimeException ex) { throw new IOException("Geçersiz tarih: " + field, ex); }
                    }
                }
                switch (collection) {
                    case "debts" -> {
                        String type = entry.path("type").asText();
                        require(Set.of("kredikarti", "kredi", "elden", "avans", "diger").contains(type), "Geçersiz borç türü");
                        if (type.equals("kredi")) {
                            require(number(entry.get("principal")).signum() > 0, "Kredi anaparası pozitif olmalı");
                            optionalNumber(entry, "vade", new BigDecimal("1200"), true);
                            require(number(entry.get("vade")).signum() > 0, "Vade pozitif olmalı");
                            optionalNumber(entry, "kalanTaksit", number(entry.get("vade")), true);
                        } else {
                            number(entry.get("balance"));
                        }
                        if (entry.hasNonNull("yon")) require(Set.of("", "borc", "alacak").contains(entry.path("yon").asText()), "Geçersiz borç yönü");
                    }
                    case "flows" -> {
                        require(Set.of("in", "out").contains(entry.path("type").asText()), "Geçersiz hareket yönü");
                        require(number(entry.get("amount")).signum() > 0, "Hareket tutarı pozitif olmalı");
                        require(entry.path("date").isTextual() && !entry.path("date").asText().isBlank(), "Hareket tarihi gerekiyor");
                    }
                    case "subs" -> {
                        optionalNumber(entry, "day", new BigDecimal("31"), true);
                        require(number(entry.get("day")).signum() > 0, "Ödeme günü 1–31 arasında olmalı");
                        require(number(entry.get("amount")).signum() > 0, "Abonelik tutarı pozitif olmalı");
                    }
                    case "accounts" -> require(number(entry.path("kisitli").isMissingNode() ? mapper.getNodeFactory().numberNode(0) : entry.get("kisitli"))
                            .compareTo(number(entry.get("balance"))) <= 0, "Kısıtlı tutar bakiyeyi aşamaz");
                    case "incomes" -> {
                        require(number(entry.get("amount")).signum() > 0, "Gelir tutarı pozitif olmalı");
                        require(entry.path("date").isTextual() && !entry.path("date").asText().isBlank(), "Gelir tarihi gerekiyor");
                        require(Set.of("tek", "aylik").contains(entry.path("repeat").asText()), "Geçersiz gelir tekrarı");
                    }
                    case "goals" -> require(number(entry.get("amount")).signum() > 0, "Hedef tutarı pozitif olmalı");
                }
            }
        }
        JsonNode settings = root.path("settings");
        require(settings.isObject(), "Defter ayarları eksik");
        JsonNode tiers = settings.path("ccTiers");
        require(tiers.isArray() && tiers.size() == 3, "Üç faiz kademesi gerekiyor");
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 0; i < tiers.size(); i++) {
            JsonNode tier = tiers.get(i);
            requiredRate(tier, "akdi"); requiredRate(tier, "gecikme");
            if (i == tiers.size() - 1) require(tier.path("max").isNull(), "Son faiz kademesi üst sınırı boş olmalı");
            else {
                BigDecimal maximum = number(tier.get("max"));
                require(maximum.compareTo(previous) > 0, "Faiz kademeleri artan sırada olmalı");
                previous = maximum;
            }
        }
        for (String kind : new String[]{"nakitAvans", "kurumsal"}) {
            requiredRate(settings.path(kind), "akdi"); requiredRate(settings.path(kind), "gecikme");
        }
        for (String field : new String[]{"asgariAlt", "asgariUst", "vergi"}) requiredRate(settings, field);
        require(number(settings.get("asgariEsik")).signum() >= 0, "Asgari eşiği negatif olamaz");
        optionalNumber(settings, "gunlukButce", new BigDecimal("1000000000000"), false);
        root.put("schemaVersion", 1);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private void checkTree(JsonNode node, int depth) throws IOException {
        require(depth <= 20, "Veri yapısı çok iç içe");
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                require(!Set.of("__proto__", "constructor", "prototype").contains(field.getKey()), "Geçersiz alan adı");
                checkTree(field.getValue(), depth + 1);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) checkTree(child, depth + 1);
        } else if (node.isTextual()) require(node.asText().length() <= 20000, "Metin alanı çok uzun");
    }

    private void requiredRate(JsonNode record, String key) throws IOException {
        BigDecimal value = number(record.get(key));
        require(value.signum() >= 0 && value.compareTo(new BigDecimal("100")) <= 0, "Oran 0–100 arasında olmalı: " + key);
    }

    private void optionalNumber(JsonNode record, String key, BigDecimal max, boolean integer) throws IOException {
        JsonNode node = record.get(key);
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) return;
        BigDecimal value = number(node);
        require(value.signum() >= 0 && value.compareTo(max) <= 0, "Geçersiz tutar veya oran: " + key);
        require(!integer || value.stripTrailingZeros().scale() <= 0, "Tam sayı gerekiyor: " + key);
    }

    private BigDecimal number(JsonNode node) throws IOException {
        require(node != null && (node.isNumber() || node.isTextual()), "Sayısal değer gerekiyor");
        try {
            String text = node.asText().trim();
            if (text.contains(",")) text = text.replace(".", "").replace(',', '.');
            BigDecimal value = new BigDecimal(text);
            require(value.abs().compareTo(new BigDecimal("1000000000000")) <= 0 && value.scale() <= 12, "Sayı desteklenen aralığın dışında");
            return value;
        } catch (NumberFormatException ex) { throw new IOException("Geçersiz sayı", ex); }
    }

    public static byte[] readLimited(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Dosya en fazla 5 MB olabilir");
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("Dosya en fazla 5 MB olabilir");
            return data;
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
