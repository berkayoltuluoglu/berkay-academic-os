package org.example;

import java.util.List;

/** Scholarship and internship applications are plans, not automatic income entries. */
public record ScholarshipData(int schemaVersion, List<Application> applications) {
    public ScholarshipData { applications = List.copyOf(applications); }
    public static ScholarshipData empty() { return new ScholarshipData(1, List.of()); }
    public record Document(String id, String name, boolean ready) {}
    public record Application(String id, String name, String kind, String amount, String deadline,
                              String appliedOn, String status, String result, String resultDate,
                              List<Document> documents, String note, String url) {
        public Application { documents = List.copyOf(documents); }
        public boolean awaitingApplication() { return List.of("Planlanıyor", "Evrak Toplanıyor").contains(status)
                && !List.of("Olumsuz", "İptal / Vazgeçildi", "Kazanıldı").contains(result); }
        public boolean awaitingResult() { return status.equals("Başvuruldu - Bekliyor") && result.equals("Beklemede"); }
        @Override public String toString() { return name; }
    }
}
