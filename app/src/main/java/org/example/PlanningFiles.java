package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, strict JSON and a compare-and-swap commit shared by UI and CLI. */
final class PlanningFiles {
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static Path safe(Path root, String name) throws IOException {
        Path base = root.toAbsolutePath().normalize(), path = base.resolve(name).normalize();
        if (!path.startsWith(base) || path.equals(base)) throw new IOException("Profil dışına yazılamaz");
        for (Path p = path; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Planlama yolu sembolik bağlantı içeriyor");
        return path;
    }
    static byte[] bytes(Path root, String name) throws IOException {
        Path file = safe(root, name);
        return Files.exists(file) ? FinanceStorageService.readLimited(file) : null;
    }
    static <T> T read(Path root, String name, Class<T> type, T fallback) throws IOException {
        byte[] bytes = bytes(root, name); return bytes == null ? fallback : JSON.readValue(bytes, type);
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static Map<String, byte[]> capture(Path root, Collection<String> names) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String name : names) result.put(name, bytes(root, name)); return result;
    }
    static Map<String,byte[]> consistentCapture(Path root,Collection<String> names) throws IOException {
        return AtomicFileWriter.exclusive(()->{
            List<FileChannel> channels=new ArrayList<>();List<FileLock> locks=new ArrayList<>();
            try{
                // Existing record locks are opened read-only. Readers create no profile files.
                for(String name:new TreeSet<>(names)){
                    Path lockPath=safe(root,name+".lock");if(!Files.exists(lockPath))continue;
                    var channel=FileChannel.open(lockPath,StandardOpenOption.READ);channels.add(channel);
                    var lock=channel.tryLock(0,Long.MAX_VALUE,true);if(lock==null)throw new IOException("BUSY: Kayıt başka yerde yazılıyor; önce yenile");locks.add(lock);
                }
                Map<String,String> stamps=new LinkedHashMap<>();for(String name:names)stamps.put(name,stamp(safe(root,name)));
                var result=capture(root,names);
                for(String name:names)if(!stamps.get(name).equals(stamp(safe(root,name))))throw new IOException("STALE_SNAPSHOT: Okuma sırasında dosya değişti. Önce yenile.");
                return result;
            }catch(OverlappingFileLockException busy){throw new IOException("BUSY: Kayıt başka yerde yazılıyor; önce yenile",busy);}
            finally{for(var lock:locks.reversed())lock.close();for(var channel:channels.reversed())channel.close();}
        });
    }
    private static String stamp(Path path)throws IOException{
        if(!Files.exists(path))return "absent";
        var a=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);
        return a.size()+":"+a.lastModifiedTime()+":"+a.fileKey();
    }
    static void commit(Path root, Map<String, byte[]> before, Map<String, byte[]> after) throws IOException {
        AtomicFileWriter.exclusive(() -> {
            List<FileChannel> channels = new ArrayList<>(); List<FileLock> locks = new ArrayList<>();
            List<String> written = new ArrayList<>();
            try {
                for (String name : new TreeSet<>(after.keySet())) {
                    byte[] next = after.get(name);
                    if (next != null && next.length > FinanceStorageService.MAX_BYTES) throw new IOException("Planlama dosyası 5 MB sınırını aşıyor");
                    Path file = safe(root, name + ".lock"); Files.createDirectories(file.getParent());
                    var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE); channels.add(channel);
                    var lock = channel.tryLock(); if (lock == null) throw new IOException("BUSY: Kayıt başka yerde yazılıyor"); locks.add(lock);
                }
                for (String name : before.keySet()) if (!Arrays.equals(bytes(root, name), before.get(name)))
                    throw new IOException("STALE_SNAPSHOT: Dosya değişti. Önce yenile.");
                for (String name : after.keySet()) {
                    if (Arrays.equals(before.get(name), after.get(name))) continue;
                    Path file = safe(root, name); byte[] old = before.get(name), next = after.get(name);
                    if (old != null) AtomicFileWriter.write(safe(root, name + ".bak"), old);
                    written.add(name);
                    if (next == null) Files.deleteIfExists(file); else AtomicFileWriter.write(file, next);
                }
            } catch (IOException | RuntimeException failure) {
                // Only our writes are rolled back, while their record locks remain held.
                for (String name : written.reversed()) try {
                    byte[] old = before.get(name);
                    if (old == null) Files.deleteIfExists(safe(root, name)); else AtomicFileWriter.write(safe(root, name), old);
                } catch (IOException recovery) { failure.addSuppressed(recovery); }
                if (failure instanceof OverlappingFileLockException) throw new IOException("BUSY: Kayıt başka yerde yazılıyor", failure);
                throw failure;
            } finally {
                for (var lock : locks.reversed()) lock.close();
                for (var channel : channels.reversed()) channel.close();
            }
            return null;
        });
    }
    static Map<String, String> encode(Map<String, byte[]> files) {
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k,v) -> result.put(k, v == null ? null : Base64.getEncoder().encodeToString(v))); return result;
    }
    static Map<String, byte[]> decode(Map<String, String> files) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try { files.forEach((k,v) -> result.put(k, v == null ? null : Base64.getDecoder().decode(v))); }
        catch (IllegalArgumentException error) { throw new IOException("İşlem geçmişi geçersiz", error); }
        return result;
    }
}
