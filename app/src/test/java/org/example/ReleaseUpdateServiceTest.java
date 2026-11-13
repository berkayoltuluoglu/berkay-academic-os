package org.example;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseUpdateServiceTest {
    @TempDir Path root;
    final byte[] setup = "MZ windows installer fixture".getBytes(StandardCharsets.UTF_8);
    final String filename = "BerkayAcademicOS-1.6.1-setup.exe";
    final String prefix = "https://github.com/" + ReleaseUpdateService.REPOSITORY + "/releases/download/v1.6.1/";
    ObjectNode metadata() {
        var data = PlanningFiles.JSON.createObjectNode();
        data.put("tag_name", "v1.6.1"); data.put("draft", false); data.put("prerelease", false); data.put("body", "Türkçe sürüm notları");
        var assets = data.putArray("assets");
        assets.addObject().put("name", filename).put("state", "uploaded").put("size", setup.length).put("browser_download_url", prefix + filename);
        assets.addObject().put("name", filename + ".sha256").put("state", "uploaded").put("size", 100).put("browser_download_url", prefix + filename + ".sha256");
        return data;
    }
    String hash(byte[] content) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
    ReleaseUpdateService service(ObjectNode metadata, byte[] content, String checksum, AtomicInteger downloads) {
        return new ReleaseUpdateService(uri -> {
            byte[] bytes;
            if (uri.equals(ReleaseUpdateService.LATEST)) bytes = PlanningFiles.JSON.writeValueAsBytes(metadata);
            else if (uri.toString().equals(prefix + filename + ".sha256")) bytes = checksum.getBytes(StandardCharsets.US_ASCII);
            else if (uri.toString().equals(prefix + filename)) { downloads.incrementAndGet(); bytes = content; }
            else throw new AssertionError("Unexpected network address: " + uri);
            return new ReleaseUpdateService.Response(200, new ByteArrayInputStream(bytes));
        });
    }
    ReleaseUpdateService service(ObjectNode metadata) throws Exception { return service(metadata, setup, hash(setup) + "  " + filename, new AtomicInteger()); }
    @Test void versionsAreNumericAndOnlyStableNumbersAreAccepted() {
        assertTrue(ReleaseVersion.parse("1.10.0").compareTo(ReleaseVersion.parse("v1.6.9")) > 0);
        assertTrue(ReleaseVersion.parse("2.0.0").compareTo(ReleaseVersion.parse("1.99.99")) > 0);
        for (String bad : List.of("1.6.0-beta.1", "../1.6.0", "1.06.0", "1.6", "1.6.0.exe", "999999999999.0.0")) assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(bad));
        assertEquals("1.6.0", ReleaseVersion.current().toString());
    }
    @Test void findsNewWindowsReleaseAndPreservesNotes() throws Exception {
        var release = service(metadata()).check(ReleaseVersion.parse("1.6.0")).orElseThrow();
        assertEquals("1.6.1", release.version().toString()); assertEquals("Türkçe sürüm notları", release.notes());
        assertEquals(filename, release.filename());
    }
    @Test void sameOlderDraftAndPrereleaseDoNotOfferUpdates() throws Exception {
        assertTrue(service(metadata()).check(ReleaseVersion.parse("1.6.1")).isEmpty());
        assertTrue(service(metadata()).check(ReleaseVersion.parse("2.0.0")).isEmpty());
        assertTrue(service(metadata().put("draft", true)).check(ReleaseVersion.parse("1.6.0")).isEmpty());
        assertTrue(service(metadata().put("prerelease", true)).check(ReleaseVersion.parse("1.6.0")).isEmpty());
    }
    @Test void incompleteAndDuplicateAssetsAreRejected() throws Exception {
        ObjectNode missing = metadata(); ((com.fasterxml.jackson.databind.node.ArrayNode)missing.path("assets")).remove(1);
        assertThrows(IOException.class, () -> service(missing).check(ReleaseVersion.parse("1.6.0")));
        ObjectNode duplicate = metadata(); ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("assets")).add(duplicate.path("assets").get(0));
        assertThrows(IOException.class, () -> service(duplicate).check(ReleaseVersion.parse("1.6.0")));
    }
    @Test void unexpectedHostRepositoryAndPathCannotSupplyInstaller() throws Exception {
        for (String uri : List.of("https://evil.example/setup.exe", "https://github.com/somebody/other/releases/download/v1.6.1/" + filename,
                prefix + "../" + filename, prefix + filename + "?redirect=evil")) {
            ObjectNode data = metadata(); ((ObjectNode)data.path("assets").get(0)).put("browser_download_url", uri);
            assertThrows(IOException.class, () -> service(data).check(ReleaseVersion.parse("1.6.0")));
        }
    }
    @Test void oversizedInstallerIsRejectedBeforeDownload() throws Exception {
        ObjectNode data = metadata(); ((ObjectNode)data.path("assets").get(0)).put("size", ReleaseUpdateService.MAX_INSTALLER + 1);
        assertThrows(IOException.class, () -> service(data).check(ReleaseVersion.parse("1.6.0")));
    }
    @Test void rateLimitIsAnErrorAndMissingReleaseIsEmpty() throws Exception {
        var limited = new ReleaseUpdateService(uri -> new ReleaseUpdateService.Response(403, InputStream.nullInputStream()));
        assertTrue(assertThrows(IOException.class, () -> limited.check(ReleaseVersion.parse("1.6.0"))).getMessage().contains("sınır"));
        assertTrue(new ReleaseUpdateService(uri -> new ReleaseUpdateService.Response(404, InputStream.nullInputStream())).check(ReleaseVersion.parse("1.6.0")).isEmpty());
    }
    @Test void verifiedDownloadIsReusedAndModifiedCacheIsDownloadedAgain() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        var service = service(metadata(), setup, hash(setup) + "  " + filename, downloads);
        var release = service.check(ReleaseVersion.parse("1.6.0")).orElseThrow();
        Path file = service.download(release, root, () -> false, bytes -> {});
        assertArrayEquals(setup, Files.readAllBytes(file)); assertEquals(1, downloads.get());
        assertEquals(file, service.download(release, root, () -> false, bytes -> {})); assertEquals(1, downloads.get());
        Files.writeString(file, "modified"); service.download(release, root, () -> false, bytes -> {});
        assertEquals(2, downloads.get()); assertArrayEquals(setup, Files.readAllBytes(file));
    }
    @Test void checksumMismatchWrongFilenameAndTruncationNeverProduceReadyFile() throws Exception {
        for (String checksum : List.of("0".repeat(64) + "  " + filename, hash(setup) + "  other.exe", "bad")) {
            var service = service(metadata(), setup, checksum, new AtomicInteger());
            var release = service.check(ReleaseVersion.parse("1.6.0")).orElseThrow();
            assertThrows(IOException.class, () -> service.download(release, root, () -> false, bytes -> {}));
            assertFalse(Files.exists(root.resolve(filename)));
        }
        var truncated = service(metadata(), new byte[]{'M', 'Z'}, hash(setup) + "  " + filename, new AtomicInteger());
        var release = truncated.check(ReleaseVersion.parse("1.6.0")).orElseThrow();
        assertThrows(IOException.class, () -> truncated.download(release, root, () -> false, bytes -> {}));
        try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
    }
    @Test void cancellationRemovesPartialFileAndDoesNotReplaceExistingFile() throws Exception {
        var service = service(metadata()); var release = service.check(ReleaseVersion.parse("1.6.0")).orElseThrow();
        Files.writeString(root.resolve(filename), "old incomplete file");
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(CancellationException.class, () -> service.download(release, root, cancel::get, bytes -> cancel.set(true)));
        assertEquals("old incomplete file", Files.readString(root.resolve(filename)));
        try (var paths = Files.list(root)) { assertEquals(1, paths.count()); }
    }
    @Test void aValidHashDoesNotMakeNonExecutableContentAnInstaller() throws Exception {
        byte[] content = "not a Windows exe".getBytes(StandardCharsets.UTF_8);
        ObjectNode data = metadata(); ((ObjectNode)data.path("assets").get(0)).put("size", content.length);
        var service = service(data, content, hash(content) + "  " + filename, new AtomicInteger());
        var release = service.check(ReleaseVersion.parse("1.6.0")).orElseThrow();
        assertThrows(IOException.class, () -> service.download(release, root, () -> false, bytes -> {}));
        assertFalse(Files.exists(root.resolve(filename)));
    }
    @Test void preferencesDefaultToCheckOnlyAndPreserveOtherData() throws Exception {
        assertEquals(new UpdatePreferences(true, false), UpdatePreferences.load(root));
        assertFalse(Files.exists(root.resolve("data")));
        Files.createDirectories(root.resolve("data")); Files.writeString(root.resolve("data/tasks.json"), "user fixture");
        new UpdatePreferences(false, true).save(root);
        assertEquals(new UpdatePreferences(false, true), UpdatePreferences.load(root));
        assertEquals("user fixture", Files.readString(root.resolve("data/tasks.json")));
    }
    @Test void handoffUsesJsonForUnicodePathsAndRejectsChangedExecutables() throws Exception {
        Path cache = root.resolve("Türkçe ' update cache"), install = root.resolve("Program Files ' Academic OS");
        Files.createDirectories(cache); Path executable = cache.resolve(filename); Files.write(executable, setup);
        var job = WindowsMaintenance.prepare(cache, install, executable, hash(setup), "update");
        var json = PlanningFiles.JSON.readTree(Files.readAllBytes(job.file()));
        assertEquals(executable.toAbsolutePath().toString(), json.path("executable").asText());
        assertEquals(install.toAbsolutePath().toString(), json.path("installRoot").asText());
        assertEquals(ProcessHandle.current().pid(), json.path("waitFor").get(0).asLong());
        assertTrue(Files.isRegularFile(job.script())); assertFalse(Files.exists(job.ready()));
        // Windows PowerShell 5.1 requires a UTF-8 BOM for the Turkish helper messages.
        assertArrayEquals(new byte[]{(byte)0xef, (byte)0xbb, (byte)0xbf}, Arrays.copyOf(Files.readAllBytes(job.script()), 3));
        Files.writeString(executable, "changed");
        assertThrows(IOException.class, () -> WindowsMaintenance.prepare(cache, install, executable, hash(setup), "update"));
    }
}
