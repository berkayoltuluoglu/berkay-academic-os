package org.example;

/** Stable release numbers; comparison is numeric, never lexicographic. */
public record ReleaseVersion(int major, int minor, int patch) implements Comparable<ReleaseVersion> {
    public static ReleaseVersion parse(String text) {
        if (text == null || !text.matches("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("Kararlı sürüm numarası okunamadı.");
        String[] parts = text.replaceFirst("^v", "").split("\\.");
        try { return new ReleaseVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Sürüm numarası çok büyük.", error); }
    }
    public static ReleaseVersion current() {
        // Read the binary's embedded version, not an older/restored user's app-config.json.
        try (var input = ReleaseVersion.class.getResourceAsStream("/config.json")) {
            return parse(PlanningFiles.JSON.readTree(input).path("app").path("version").asText());
        } catch (Exception error) { throw new IllegalStateException("Uygulama sürümü okunamadı.", error); }
    }
    @Override public int compareTo(ReleaseVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }
    @Override public String toString() { return major + "." + minor + "." + patch; }
}
