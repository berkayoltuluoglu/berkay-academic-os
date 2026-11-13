package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** GitHub public release reader and bounded, verified downloader. No profile or OAuth access. */
public final class ReleaseUpdateService implements AutoCloseable {
    public static final String REPOSITORY = "berkayoltuluoglu/berkay-academic-os";
    static final URI LATEST = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
    static final long MAX_INSTALLER = 512L * 1024 * 1024;
    private final Transport transport;
    public ReleaseUpdateService() { this(new HttpTransport()); }
    ReleaseUpdateService(Transport transport) { this.transport = transport; }

    public record Release(ReleaseVersion version, String tag, String notes, URI installer, URI checksum, long size) {
        public String filename() { return "BerkayAcademicOS-" + version + "-setup.exe"; }
    }
    record Response(int status, InputStream body) implements AutoCloseable {
        @Override public void close() throws IOException { body.close(); }
    }
    @FunctionalInterface interface Transport { Response open(URI uri) throws Exception; }

    public Optional<Release> check(ReleaseVersion current) throws Exception {
        try (Response response = transport.open(LATEST)) {
            if (response.status == 404) return Optional.empty();
            requireOk(response);
            JsonNode data = PlanningFiles.JSON.readTree(readLimited(response.body, 1024 * 1024));
            if (data == null || !data.isObject()) throw new IOException("Güncelleme sunucusu geçerli yayın bilgisi döndürmedi.");
            if (data.path("draft").asBoolean() || data.path("prerelease").asBoolean()) return Optional.empty();
            String tag = data.path("tag_name").asText();
            ReleaseVersion version = ReleaseVersion.parse(tag);
            if (version.compareTo(current) <= 0) return Optional.empty();
            String name = "BerkayAcademicOS-" + version + "-setup.exe";
            JsonNode installer = asset(data, name), checksum = asset(data, name + ".sha256");
            long size = installer.path("size").asLong();
            Release release = new Release(version, tag, data.path("body").asText(""),
                    URI.create(installer.path("browser_download_url").asText()),
                    URI.create(checksum.path("browser_download_url").asText()), size);
            validate(release);
            return Optional.of(release);
        }
    }

    public Path download(Release release, Path cache, BooleanSupplier cancelled, LongConsumer progress) throws Exception {
        validate(release); checkCancelled(cancelled);
        String expected;
        try (Response response = transport.open(release.checksum)) {
            requireOk(response);
            String text = new String(readLimited(response.body, 1024), StandardCharsets.US_ASCII).trim();
            String[] parts = text.split("\\s+");
            if (parts.length != 2 || !parts[0].matches("[a-fA-F0-9]{64}") || !parts[1].replaceFirst("^\\*", "").equals(release.filename()))
                throw new IOException("Güncellemenin SHA-256 dosyası geçersiz.");
            expected = parts[0].toLowerCase(Locale.ROOT);
        }
        checkCancelled(cancelled);
        Files.createDirectories(cache);
        Path target = cache.resolve(release.filename());
        if (Files.isSymbolicLink(target)) throw new IOException("Güncelleme dosyası bağlantı olamaz.");
        if (Files.isRegularFile(target) && Files.size(target) == release.size && sha256(target).equals(expected) && windowsExecutable(target)) {
            progress.accept(release.size); return target;
        }
        Path partial = Files.createTempFile(cache, "academic-update-", ".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (Response response = transport.open(release.installer); var output = Files.newOutputStream(partial)) {
                requireOk(response);
                byte[] buffer = new byte[64 * 1024];
                for (int count; (count = response.body.read(buffer)) != -1;) {
                    checkCancelled(cancelled); total += count;
                    if (total > release.size || total > MAX_INSTALLER) throw new IOException("İndirilen setup beklenen boyutu aşıyor.");
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); progress.accept(total);
                }
            }
            checkCancelled(cancelled);
            if (total != release.size || !HexFormat.of().formatHex(digest.digest()).equals(expected))
                throw new IOException("Güncelleme doğrulanamadı. Dosya eksik veya SHA-256 değeri farklı; yeniden indir.");
            if (!windowsExecutable(partial)) throw new IOException("İndirilen dosya Windows setup biçiminde değil.");
            try { Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(partial); }
    }
    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { byte[] buffer = new byte[64 * 1024]; for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static boolean windowsExecutable(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) { return input.read() == 'M' && input.read() == 'Z'; }
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("İndirme iptal edildi.");
    }
    private static JsonNode asset(JsonNode data, String name) throws IOException {
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode item : data.path("assets")) if (name.equals(item.path("name").asText()) && "uploaded".equals(item.path("state").asText())) matches.add(item);
        if (matches.size() != 1) throw new IOException("Yayın henüz hazır değil: " + name + " dosyası eksik veya yinelenmiş.");
        return matches.getFirst();
    }
    private static void validate(Release release) throws IOException {
        if (!ReleaseVersion.parse(release.tag).equals(release.version) || release.size < 2 || release.size > MAX_INSTALLER)
            throw new IOException("Güncelleme sürümü veya boyutu geçersiz.");
        String prefix = "https://github.com/" + REPOSITORY + "/releases/download/" + release.tag + "/";
        if (!release.installer.toString().equals(prefix + release.filename()) || !release.checksum.toString().equals(prefix + release.filename() + ".sha256"))
            throw new IOException("Güncelleme bağlantısı beklenen GitHub yayınına ait değil.");
    }
    private static byte[] readLimited(InputStream input, int max) throws IOException {
        byte[] data = input.readNBytes(max + 1);
        if (data.length > max) throw new IOException("Güncelleme yanıtı izin verilen boyutu aşıyor.");
        return data;
    }
    private static void requireOk(Response response) throws IOException {
        if (response.status == 403 || response.status == 429) throw new IOException("GitHub istek sınırına ulaşıldı. Daha sonra tekrar dene.");
        if (response.status != 200) throw new IOException("Güncelleme sunucusu yanıtı: " + response.status);
    }
    static final class HttpTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        private static final Set<String> HOSTS = Set.of("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com");
        private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("update-timeouts").factory());
        @Override public Response open(URI address) throws Exception {
            boolean installer = address.getPath().endsWith("-setup.exe");
            for (int redirects = 0; redirects < 6; redirects++) {
                if (!"https".equals(address.getScheme()) || !HOSTS.contains(address.getHost()) || address.getUserInfo() != null || address.getPort() != -1)
                    throw new IOException("Güncelleme sunucusu bağlantısı doğrulanamadı.");
                var request = HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "AcademicOS/" + ReleaseVersion.current())
                        .header("Accept", address.getHost().equals("api.github.com") ? "application/vnd.github+json" : "application/octet-stream").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                    try (var body = response.body()) {
                        address = address.resolve(response.headers().firstValue("location").orElseThrow(() -> new IOException("İndirme yönlendirmesi eksik.")));
                    }
                    continue;
                }
                InputStream stream = response.body();
                // InputStream body handlers finish after headers; close a stalled body independently.
                ScheduledFuture<?> deadline = TIMEOUTS.schedule(() -> { try { stream.close(); } catch (IOException ignored) {} }, installer ? 7200 : 30, TimeUnit.SECONDS);
                return new Response(response.statusCode(), new FilterInputStream(stream) {
                    @Override public void close() throws IOException { deadline.cancel(false); super.close(); }
                });
            }
            throw new IOException("Güncelleme sunucusu çok fazla yönlendirme yaptı.");
        }
    }
    @Override public void close() { if (transport instanceof HttpTransport http) http.client.shutdownNow(); }
}
