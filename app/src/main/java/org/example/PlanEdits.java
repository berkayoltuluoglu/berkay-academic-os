package org.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.*;

/**
 * Field-level changes between two versions of a planning JSON file. Undo history stores these
 * instead of whole-file copies, so moving one block records a few hundred bytes, not every file.
 *
 * <p>Path segments: {@code .name} is an object field, {@code [id]} is the array element whose
 * {@code "id"} equals {@code id}. A {@code null} value means "absent" (missing field, missing element,
 * or for the empty path a missing file); JSON {@code null} is kept as a NullNode.
 */
final class PlanEdits {
    private PlanEdits() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Edit(String file, List<String> path, JsonNode before, JsonNode after, Integer beforeIndex, Integer afterIndex) {}

    /** Edits that turn {@code before} into {@code after}; files are raw bytes, null when absent. */
    static List<Edit> diff(String file, byte[] before, byte[] after) throws IOException {
        List<Edit> edits = new ArrayList<>();
        if (Arrays.equals(before, after)) return edits;
        diff(file, new ArrayList<>(), parse(before), parse(after), null, null, edits);
        return edits;
    }

    private static void diff(String file, List<String> path, JsonNode a, JsonNode b, Integer ai, Integer bi, List<Edit> out) {
        if (Objects.equals(a, b)) return;
        if (a instanceof ObjectNode x && b instanceof ObjectNode y) {
            Set<String> names = new LinkedHashSet<>(); x.fieldNames().forEachRemaining(names::add); y.fieldNames().forEachRemaining(names::add);
            for (String name : names) diff(file, with(path, "." + name), x.get(name), y.get(name), null, null, out);
            return;
        }
        if (a instanceof ArrayNode x && b instanceof ArrayNode y) {
            var left = byId(x); var right = byId(y);
            if (left != null && right != null && sameOrder(left.keySet(), right.keySet())) {
                List<String> ids = new ArrayList<>(left.keySet()); for (String id : right.keySet()) if (!left.containsKey(id)) ids.add(id);
                List<String> leftIds = new ArrayList<>(left.keySet()), rightIds = new ArrayList<>(right.keySet());
                for (String id : ids) {
                    int l = leftIds.indexOf(id), r = rightIds.indexOf(id);
                    diff(file, with(path, "[" + id + "]"), left.get(id), right.get(id), l < 0 ? null : l, r < 0 ? null : r, out);
                }
                return;
            }
        }
        out.add(new Edit(file, List.copyOf(path), a == null ? null : a.deepCopy(), b == null ? null : b.deepCopy(), ai, bi));
    }

    /** Applies edits to one file's current bytes, refusing when the current state is not the expected one. */
    static byte[] apply(byte[] current, List<Edit> edits, boolean undo) throws IOException {
        JsonNode doc = parse(current);
        List<Edit> ordered = new ArrayList<>(edits); if (undo) Collections.reverse(ordered);
        for (Edit edit : ordered) {
            JsonNode expected = undo ? edit.after() : edit.before(), target = undo ? edit.before() : edit.after();
            Integer index = undo ? edit.beforeIndex() : edit.afterIndex();
            if (!Objects.equals(get(doc, edit.path()), expected))
                throw new IOException("STALE_SNAPSHOT: Dosya değişti. Önce yenile; bu işlem geri alınamaz.");
            doc = set(doc, edit.path(), target == null ? null : target.deepCopy(), index);
        }
        return doc == null ? null : PlanningFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(doc);
    }

    static void validate(Edit edit, Collection<String> files) throws IOException {
        if (edit == null || !files.contains(edit.file()) || edit.path() == null || edit.path().size() > 32)
            throw new IOException("İşlem geçmişi geçersiz");
        for (String segment : edit.path())
            if (segment == null || !(segment.startsWith(".") || segment.length() >= 2 && segment.startsWith("[") && segment.endsWith("]")))
                throw new IOException("İşlem geçmişi geçersiz");
    }

    private static JsonNode get(JsonNode doc, List<String> path) {
        JsonNode node = doc;
        for (String segment : path) { if (node == null) return null; node = child(node, segment); }
        return node;
    }
    private static JsonNode child(JsonNode node, String segment) {
        if (segment.startsWith(".")) return node instanceof ObjectNode o ? o.get(segment.substring(1)) : null;
        if (!(node instanceof ArrayNode array)) return null;
        String id = segment.substring(1, segment.length() - 1);
        for (JsonNode element : array) if (element.path("id").isTextual() && element.get("id").asText().equals(id)) return element;
        return null;
    }
    private static JsonNode set(JsonNode doc, List<String> path, JsonNode value, Integer index) throws IOException {
        if (path.isEmpty()) return value;
        JsonNode parent = get(doc, path.subList(0, path.size() - 1));
        String last = path.getLast();
        if (last.startsWith(".") && parent instanceof ObjectNode o) {
            if (value == null) o.remove(last.substring(1)); else o.set(last.substring(1), value);
            return doc;
        }
        if (last.startsWith("[") && parent instanceof ArrayNode array) {
            String id = last.substring(1, last.length() - 1); int at = -1;
            for (int i = 0; i < array.size(); i++) if (array.get(i).path("id").isTextual() && array.get(i).get("id").asText().equals(id)) { at = i; break; }
            if (value == null) { if (at >= 0) array.remove(at); }
            else if (at >= 0) array.set(at, value);
            else array.insert(index == null ? array.size() : Math.max(0, Math.min(index, array.size())), value);
            return doc;
        }
        throw new IOException("STALE_SNAPSHOT: Dosya yapısı değişti. Önce yenile; bu işlem geri alınamaz.");
    }

    /** Elements keyed by their textual "id", or null when the array is not a list of uniquely identified objects. */
    private static LinkedHashMap<String, JsonNode> byId(ArrayNode array) {
        LinkedHashMap<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode element : array) {
            if (!(element instanceof ObjectNode) || !element.path("id").isTextual()) return null;
            if (result.put(element.get("id").asText(), element) != null) return null;
        }
        return result;
    }
    /** Shared ids keep their relative order; otherwise the whole array is recorded as one edit. */
    private static boolean sameOrder(Collection<String> left, Collection<String> right) {
        List<String> a = left.stream().filter(right::contains).toList(), b = right.stream().filter(left::contains).toList();
        return a.equals(b);
    }
    private static List<String> with(List<String> path, String segment) { List<String> next = new ArrayList<>(path); next.add(segment); return next; }
    private static JsonNode parse(byte[] bytes) throws IOException { return bytes == null ? null : PlanningFiles.JSON.readTree(bytes); }
}
