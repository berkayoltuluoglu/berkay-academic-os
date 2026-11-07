package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Read-only student integration using Google's authenticated HTTP client and documented REST API. */
public class GoogleClassroomService {
    private final Path root;
    private final GoogleCalendarOAuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile HttpRequestFactory requests;
    private volatile String account;

    public GoogleClassroomService(Path root) { this(root, new GoogleClassroomOAuthService()); }
    GoogleClassroomService(Path root, GoogleCalendarOAuthService auth) { this.root = root; this.auth = auth; }
    public boolean isConnected() { return requests != null; }
    public String getAccountEmail() { return account; }
    public void cancelAuthorization() { auth.cancelAuthorization(); }
    public synchronized void disconnect() throws IOException {
        requests = null; account = null; Files.deleteIfExists(root.resolve("data/classroom/catalog.json"));
    }

    public synchronized void connect() throws Exception {
        requests = null; account = null;
        Path local = root.resolve("config/google-classroom-credentials.json");
        Path credentials = Files.exists(local) ? local : GoogleCredentials.resolve(root, "google-calendar-credentials.json");
        Credential credential = auth.authorize(credentials, root.resolve("config/google-calendar-tokens"));
        HttpRequestFactory factory = createRequests(credential);
        JsonNode identity = get(factory, "https://www.googleapis.com/oauth2/v2/userinfo", Map.of());
        String email = identity.path("email").asText();
        if (email.isBlank()) throw new IOException("Google bağlı Classroom hesabını doğrulayamadı.");
        // Verify API availability as well as OAuth, without changing any Classroom resources.
        get(factory, "https://classroom.googleapis.com/v1/courses", Map.of("studentId", "me", "pageSize", 1));
        if (Thread.currentThread().isInterrupted()) throw new IOException("Classroom bağlantısı iptal edildi.");
        account = email; requests = factory;
    }

    HttpRequestFactory createRequests(Credential credential) throws Exception {
        return GoogleNetHttpTransport.newTrustedTransport().createRequestFactory(request -> {
            credential.initialize(request); request.setConnectTimeout(15000); request.setReadTimeout(20000);
        });
    }

    public synchronized ClassroomData sync() throws IOException {
        if (requests == null) throw new IOException("Önce Classroom'a bağlan.");
        List<ClassroomData.Course> courses = new ArrayList<>();
        List<ClassroomData.Work> work = new ArrayList<>();
        for (JsonNode course : pages("https://classroom.googleapis.com/v1/courses", "courses",
                Map.of("studentId", "me", "courseStates", "ACTIVE", "pageSize", 100))) {
            String id = requiredId(course, "id");
            courses.add(new ClassroomData.Course(id, course.path("name").asText(), course.path("section").asText(), course.path("alternateLink").asText()));
            Map<String, String> submissions = new HashMap<>();
            for (JsonNode submission : pages("https://classroom.googleapis.com/v1/courses/" + id + "/courseWork/-/studentSubmissions",
                    "studentSubmissions", Map.of("userId", "me", "pageSize", 100)))
                submissions.put(submission.path("courseWorkId").asText(), submission.path("state").asText());
            for (JsonNode item : pages("https://classroom.googleapis.com/v1/courses/" + id + "/courseWork", "courseWork", Map.of("pageSize", 100))) {
                LocalDateTime due = parseDue(item, ZoneId.systemDefault());
                String workId = requiredId(item, "id");
                work.add(new ClassroomData.Work(workId, id, item.path("title").asText(), item.path("description").asText(),
                        due == null ? "" : due.toLocalDate().toString(), due == null ? "" : due.toLocalTime().toString(),
                        submissions.getOrDefault(workId, "NEW"), item.path("alternateLink").asText()));
            }
        }
        ClassroomData data = new ClassroomData(1, account, Instant.now().toString(), courses, work);
        if (Thread.currentThread().isInterrupted()) throw new IOException("Classroom okuması iptal edildi.");
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Classroom kayıtları 5 MB sınırını aşıyor.");
        ClassroomData previous = loadCached();
        ProfileTransaction.run(root, List.of("data/courses/academic.json", "data/courses/academic.json.bak", "data/classroom/catalog.json"), () -> {
            if (previous.accountEmail().equals(account)) {
                try { new ClassroomImportService(root).refreshImported(previous, data); }
                catch (Exception failure) { throw new IOException("Classroom yerel ders güncellemesi tamamlanamadı: " + failure.getMessage(), failure); }
            }
            persistSnapshot(bytes); return null;
        });
        return data;
    }
    void persistSnapshot(byte[] bytes) throws IOException { AtomicFileWriter.write(root.resolve("data/classroom/catalog.json"), bytes); }

    public ClassroomData loadCached() throws IOException {
        Path path = root.resolve("data/classroom/catalog.json");
        if (!Files.exists(path)) return ClassroomData.empty();
        ClassroomData data = mapper.readValue(FinanceStorageService.readLimited(path), ClassroomData.class);
        if (data.schemaVersion() != 1) throw new IOException("Classroom kayıt sürümü desteklenmiyor.");
        return data;
    }
    public void importCourse(ClassroomData data, String courseId) throws Exception { new ClassroomImportService(root).importCourse(data, courseId); }

    private List<JsonNode> pages(String address, String key, Map<String, Object> parameters) throws IOException {
        List<JsonNode> all = new ArrayList<>(); Set<String> seen = new HashSet<>(); String token = null;
        do {
            Map<String, Object> query = new HashMap<>(parameters);
            if (token != null) query.put("pageToken", token);
            JsonNode page = get(requests, address, query);
            for (JsonNode item : page.path(key)) all.add(item);
            if (all.size() > 10000) throw new IOException("Classroom'da çok fazla kayıt var.");
            token = page.path("nextPageToken").asText("");
            if (!token.isBlank() && !seen.add(token)) throw new IOException("Classroom sayfalama yanıtı geçersiz.");
        } while (!token.isBlank());
        return all;
    }

    private JsonNode get(HttpRequestFactory factory, String address, Map<String, ?> parameters) throws IOException {
        GenericUrl url = new GenericUrl(address); parameters.forEach(url::set);
        HttpResponse response = null;
        try {
            response = factory.buildGetRequest(url).execute();
            byte[] bytes = response.getContent().readNBytes(FinanceStorageService.MAX_BYTES + 1);
            if (bytes.length > FinanceStorageService.MAX_BYTES) throw new IOException("Google yanıtı çok büyük.");
            return mapper.readTree(bytes);
        } catch (HttpResponseException error) {
            String reason = "Google isteği başarısız (" + error.getStatusCode() + ").";
            if (error.getStatusCode() == 403) reason += " Google Cloud projesinde Classroom API açık olmalı ve hesap izinleri onaylanmalı.";
            throw new IOException(reason, error);
        } finally { if (response != null) response.disconnect(); }
    }

    static LocalDateTime parseDue(JsonNode item, ZoneId zone) throws IOException {
        if (!item.has("dueDate")) return null;
        try {
            JsonNode date = item.path("dueDate"), time = item.path("dueTime");
            LocalDate utcDate = LocalDate.of(date.path("year").asInt(), date.path("month").asInt(), date.path("day").asInt());
            LocalTime utcTime = LocalTime.of(time.path("hours").asInt(), time.path("minutes").asInt(), time.path("seconds").asInt());
            return utcDate.atTime(utcTime).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
        } catch (RuntimeException error) { throw new IOException("Classroom son teslim tarihi geçersiz.", error); }
    }
    private String requiredId(JsonNode item, String key) throws IOException {
        String value = item.path(key).asText();
        if (!value.matches("[a-zA-Z0-9_-]{1,200}")) throw new IOException("Classroom kimliği geçersiz.");
        return value;
    }
}
