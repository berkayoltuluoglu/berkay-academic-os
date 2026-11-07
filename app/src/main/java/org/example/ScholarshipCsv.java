package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

final class ScholarshipCsv {
    static final List<String> HEADERS = List.of("Burs Adı", "Burs Miktarı", "Son Başvuru Tarihi", "Başvurulan Tarih", "Gerekli Belgeler", "Durum", "Sonuç", "Açıklanma Tarihi", "Sonuç Notu", "Kimlik", "Tür", "Bağlantı", "Hazır Belge Kimlikleri", "Belge Kimlikleri");
    static String export(ScholarshipData data) {
        List<List<String>> rows = new ArrayList<>(); rows.add(HEADERS);
        for (var a : data.applications()) rows.add(List.of(a.name(), a.amount(), a.deadline(), a.appliedOn(),
                a.documents().stream().map(ScholarshipData.Document::name).collect(Collectors.joining("\n")),
                a.status(), a.result(), a.resultDate(), a.note(), a.id(), a.kind(), a.url(),
                a.documents().stream().filter(ScholarshipData.Document::ready).map(ScholarshipData.Document::id).collect(Collectors.joining(";")),
                a.documents().stream().map(ScholarshipData.Document::id).collect(Collectors.joining(";"))));
        return "\uFEFF" + rows.stream().map(row -> row.stream().map(CsvTable::cell).collect(Collectors.joining(","))).collect(Collectors.joining("\r\n"));
    }
    static ScholarshipData parse(String text) throws IOException {
        var rows = CsvTable.parse(text); if (rows.isEmpty()) throw new IOException("Başvuru CSV başlığı eksik.");
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < rows.getFirst().size(); i++) if (headers.put(rows.getFirst().get(i).trim(), i) != null) throw new IOException("CSV sütunu tekrarlı.");
        for (String name : HEADERS.subList(0, 9)) if (!headers.containsKey(name)) throw new IOException("CSV sütunu eksik: " + name);
        List<ScholarshipData.Application> result = new ArrayList<>();
        for (int n = 1; n < rows.size(); n++) {
            var row = rows.get(n); if (row.size() != rows.getFirst().size()) throw new IOException("CSV satır " + (n+1) + " sütun sayısı hatalı.");
            java.util.function.Function<String, String> get = name -> { int index = headers.getOrDefault(name, -1); String value = index < 0 ? "" : row.get(index).trim(); return value.equals("-") ? "" : value; };
            String id = get.apply("Kimlik"); if (id.isBlank()) id = "legacy-" + UUID.nameUUIDFromBytes(String.join("\u001f", row).getBytes(StandardCharsets.UTF_8));
            var names = get.apply("Gerekli Belgeler").isBlank() ? List.<String>of() : Arrays.stream(get.apply("Gerekli Belgeler").split(headers.containsKey("Belge Kimlikleri") ? "\\n" : "\\n|,\\s*" )).map(String::trim).filter(s -> !s.isEmpty()).toList();
            var docIds = get.apply("Belge Kimlikleri").split(";"); Set<String> ready = Set.copyOf(Arrays.asList(get.apply("Hazır Belge Kimlikleri").split(";")));
            List<ScholarshipData.Document> docs = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) docs.add(new ScholarshipData.Document(i < docIds.length && !docIds[i].isBlank() ? docIds[i] : UUID.nameUUIDFromBytes((id + ":" + i).getBytes(StandardCharsets.UTF_8)).toString(), names.get(i), i < docIds.length && ready.contains(docIds[i])));
            result.add(new ScholarshipData.Application(id, get.apply("Burs Adı"), get.apply("Tür").isBlank() ? "Burs" : get.apply("Tür"), get.apply("Burs Miktarı"),
                    get.apply("Son Başvuru Tarihi"), get.apply("Başvurulan Tarih"), get.apply("Durum"), get.apply("Sonuç").isBlank() ? "Beklemede" : get.apply("Sonuç"),
                    get.apply("Açıklanma Tarihi"), docs, get.apply("Sonuç Notu"), get.apply("Bağlantı")));
        }
        var data = new ScholarshipData(1, result); ScholarshipStorageService.validate(data); return data;
    }
}
