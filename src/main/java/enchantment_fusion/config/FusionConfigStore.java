package enchantment_fusion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.menu.FusionNetworking.MatrixSyncPayload;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative, sparse overrides of the merged datapack tables. Overrides are deliberately
 * applied AFTER datapack merging. Objects merge by key; arrays replace the complete datapack array.
 * Only existing leaves can be edited in game. The original datapack is never modified.
 */
public final class FusionConfigStore {
    public static final List<String> FILES = List.of(
        "economy", "matrix_profiles", "repulsive_pairs", "signatures", "transmutations");
    public static final int MAX_DOCUMENT_CHARS = 262_144;
    public static final int MAX_VALUE_CHARS = 16_384;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static State state = new State(new JsonObject(), new JsonObject(), new JsonObject(), 0);

    public record View(JsonObject effective, JsonObject overrides, long revision) {}
    public record UpdateResult(boolean success, String reason) {}
    private record State(JsonObject base, JsonObject effective, JsonObject overrides, long revision) {}
    private record Prepared(JsonObject effective, FusionMatrixRegistry.Snapshot matrix,
                            SignatureCatalog.Snapshot signatures, TransmuteCatalog.Snapshot transmutes) {}

    private FusionConfigStore() {}

    public static synchronized View view() {
        return new View(state.effective().deepCopy(), state.overrides().deepCopy(), state.revision());
    }

    public static synchronized long revision() {
        return state.revision();
    }

    /** Stable across saves/restarts: a pending transaction must not use changed rules. */
    public static synchronized String rulesFingerprint() {
        try {
            byte[] bytes = state.effective().toString().getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Called by the datapack reload listener, never by a remote client. */
    public static synchronized void reload(JsonObject base) {
        JsonObject copy = base.deepCopy();
        JsonObject overrides = readOverrides();
        Prepared prepared;
        try {
            validateOverrides(copy, overrides);
            prepared = prepare(copy, overrides);
        } catch (RuntimeException ex) {
            // Preserve the file for an administrator to repair; don't partially apply it.
            EnchantmentFusion.LOGGER.error("Invalid Enchantment Fusion overrides; loading datapack values instead", ex);
            overrides = new JsonObject();
            prepared = prepare(copy, overrides);
        }
        publish(copy, overrides, prepared);
    }

    /** Main-menu Mod Menu editing uses bundled datapacks until a world/server is running. */
    public static synchronized void loadBundledForMenu() {
        JsonObject base = new JsonObject();
        for (String name : FILES) {
            String path = "/data/enchantment_fusion/enchantment_fusion/" + name + ".json";
            try (InputStream stream = FusionConfigStore.class.getResourceAsStream(path)) {
                if (stream != null) {
                    JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    if (json.isJsonObject()) base.add(name, json);
                }
            } catch (Exception ex) {
                EnchantmentFusion.LOGGER.warn("Unable to load bundled configuration {}", name, ex);
            }
        }
        reload(base);
    }

    /** Apply one validated leaf (or remove its override). The revision prevents lost updates. */
    public static synchronized UpdateResult update(String file, List<String> path, @Nullable JsonElement value,
                                                    boolean reset, long expectedRevision) {
        if (expectedRevision != state.revision()) return new UpdateResult(false, "stale");
        try {
            if (!FILES.contains(file) || path == null || path.isEmpty() || path.size() > 8) {
                throw new IllegalArgumentException("unknown field");
            }
            JsonElement current = getPath(state.effective(), file, path);
            if (current == null || current.isJsonObject() || current.isJsonNull()) {
                throw new IllegalArgumentException("unknown field");
            }
            if (isReserved(path)) throw new IllegalArgumentException("reserved field");
            if (!reset) validateLeaf(file, path, current, value);
            JsonObject nextOverrides = state.overrides().deepCopy();
            if (reset) removePath(nextOverrides, file, path);
            else setPath(nextOverrides, file, path, value);
            validateOverrides(state.base(), nextOverrides);
            Prepared prepared = prepare(state.base(), nextOverrides);
            String serialized = GSON.toJson(nextOverrides);
            if (serialized.length() > MAX_DOCUMENT_CHARS || prepared.effective().toString().length() > MAX_DOCUMENT_CHARS) {
                throw new IllegalArgumentException("configuration too large");
            }
            writeOverrides(serialized);
            publish(state.base(), nextOverrides, prepared);
            return new UpdateResult(true, "saved");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            EnchantmentFusion.LOGGER.debug("Rejected config update: {}", ex.getMessage());
            return new UpdateResult(false, "invalid");
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Failed to save Enchantment Fusion configuration", ex);
            return new UpdateResult(false, "io_error");
        }
    }

    private static Prepared prepare(JsonObject base, JsonObject overrides) {
        JsonObject effective = base.deepCopy();
        mergeOverrides(effective, overrides);
        if (effective.toString().length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException("configuration exceeds sync limit");
        }
        validateRelationships(effective);
        // Unlike normal datapack layers, the UI's array override is an absolute replacement,
        // including built-in exclusive groups. Reset restores normal datapack merge semantics.
        JsonObject repulsiveOverride = object(overrides, "repulsive_pairs");
        JsonObject effectiveRepulsive = object(effective, "repulsive_pairs");
        if (repulsiveOverride != null && repulsiveOverride.has("exclusive_groups") && effectiveRepulsive != null) {
            effectiveRepulsive.addProperty("replace", true);
        }
        FusionMatrixRegistry.Snapshot matrix = FusionMatrixRegistry.parseFromJson(
            object(effective, "matrix_profiles"), object(effective, "repulsive_pairs"), object(effective, "economy"));
        MatrixSyncPayload.validateSnapshot(matrix);
        SignatureCatalog.Snapshot signatures = SignatureCatalog.parseFromJson(object(effective, "signatures"));
        TransmuteCatalog.Snapshot transmutes = TransmuteCatalog.parseFromJson(object(effective, "transmutations"));
        return new Prepared(effective, matrix, signatures, transmutes);
    }

    private static void publish(JsonObject base, JsonObject overrides, Prepared prepared) {
        FusionMatrixRegistry.publish(prepared.matrix());
        SignatureCatalog.publish(prepared.signatures());
        TransmuteCatalog.publish(prepared.transmutes());
        state = new State(base.deepCopy(), prepared.effective(), overrides.deepCopy(), state.revision() + 1);
    }

    private static @Nullable JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static void mergeOverrides(JsonObject target, JsonObject overlay) {
        for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
            if (entry.getValue().isJsonObject() && target.has(entry.getKey()) && target.get(entry.getKey()).isJsonObject()) {
                mergeOverrides(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            } else {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    public static @Nullable JsonElement getPath(JsonObject root, String file, List<String> path) {
        JsonElement node = root.get(file);
        for (String part : path) {
            if (node == null || !node.isJsonObject() || part == null || part.isEmpty() || part.length() > 256) return null;
            node = node.getAsJsonObject().get(part);
        }
        return node;
    }

    private static void setPath(JsonObject root, String file, List<String> path, JsonElement value) {
        JsonObject node = root;
        List<String> parts = concat(file, path);
        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            if (!node.has(part) || !node.get(part).isJsonObject()) node.add(part, new JsonObject());
            node = node.getAsJsonObject(part);
        }
        node.add(parts.getLast(), value.deepCopy());
    }

    private static List<String> concat(String file, List<String> path) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>(path.size() + 1);
        parts.add(file);
        parts.addAll(path);
        return parts;
    }

    private static void removePath(JsonObject root, String file, List<String> path) {
        removePathRecursive(root, concat(file, path), 0);
    }

    private static void removePathRecursive(JsonObject node, List<String> parts, int index) {
        String key = parts.get(index);
        if (index == parts.size() - 1) node.remove(key);
        else if (node.has(key) && node.get(key).isJsonObject()) {
            JsonObject child = node.getAsJsonObject(key);
            removePathRecursive(child, parts, index + 1);
            if (child.isEmpty()) node.remove(key);
        }
    }

    private static boolean isReserved(List<String> path) {
        return path.size() == 1 && (path.getFirst().equals("schema_version") || path.getFirst().equals("replace"));
    }

    private static void validateOverrides(JsonObject base, JsonObject overrides) {
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            if (!FILES.contains(entry.getKey()) || !entry.getValue().isJsonObject()) throw new IllegalArgumentException("unknown file");
            validateTree(base, entry.getKey(), entry.getValue().getAsJsonObject(), new java.util.ArrayList<>());
        }
    }

    private static void validateTree(JsonObject base, String file, JsonObject node, java.util.ArrayList<String> path) {
        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            path.add(entry.getKey());
            JsonElement existing = getPath(base, file, path);
            if (existing == null || path.size() > 8 || isReserved(path)) throw new IllegalArgumentException("unknown override");
            if (entry.getValue().isJsonObject() && existing.isJsonObject()) {
                validateTree(base, file, entry.getValue().getAsJsonObject(), path);
            } else {
                validateLeaf(file, path, existing, entry.getValue());
            }
            path.removeLast();
        }
    }

    private static void validateLeaf(String file, List<String> path, JsonElement previous, @Nullable JsonElement value) {
        if (value == null || value.isJsonNull() || value.isJsonObject() || value.toString().length() > MAX_VALUE_CHARS) {
            throw new IllegalArgumentException("invalid value");
        }
        String key = path.getLast();
        if (previous.isJsonArray()) {
            if (!value.isJsonArray()) throw new IllegalArgumentException("expected array");
            validateArray(file, key, path, value.getAsJsonArray());
            return;
        }
        if (!previous.isJsonPrimitive() || !value.isJsonPrimitive()) throw new IllegalArgumentException("wrong type");
        var old = previous.getAsJsonPrimitive();
        var next = value.getAsJsonPrimitive();
        if (old.isBoolean()) {
            if (!next.isBoolean()) throw new IllegalArgumentException("expected boolean");
        } else if (old.isNumber()) {
            if (!next.isNumber()) throw new IllegalArgumentException("expected number");
            double n = next.getAsDouble();
            if (!Double.isFinite(n)) throw new IllegalArgumentException("invalid number");
            double min = 0, max = 1;
            if (path.contains("trigger_cooldowns")) { min = 4; max = 1200; }
            else if (key.equals("melee_min_charge")) { min = 0.05; max = 1; }
            else if (key.equals("melee_full_charge")) { min = 0.10; max = 1; }
            else if (key.startsWith("stabilizer_reduction_")) { min = 0.01; max = 1; }
            else if (key.startsWith("max_") && key.endsWith("stabilizers")) max = 16;
            else if (key.equals("purify_aberrant_stabilizers")) { min = 1; max = 64; }
            else if (key.equals("purify_aberrant_xp")) max = 200;
            else if (key.equals("repulsive_max_bonus_xp")) max = 50;
            else if (key.endsWith("_xp")) max = 100;
            else if (key.endsWith("_reagents")) max = 64;
            boolean integral = !old.getAsString().contains(".") && !old.getAsString().toLowerCase(java.util.Locale.ROOT).contains("e");
            if (n < min || n > max || (integral && n != Math.rint(n))) throw new IllegalArgumentException("number out of range");
        } else if (old.isString()) {
            if (!next.isString()) throw new IllegalArgumentException("expected string");
            String text = next.getAsString();
            if (text.isBlank() || text.length() > 128) throw new IllegalArgumentException("invalid string");
            if (key.equals("trigger") && TriggerType.fromString(text, null) == null) throw new IllegalArgumentException("unknown trigger");
            if (key.equals("payload") && PayloadType.fromString(text, null) == null) throw new IllegalArgumentException("unknown payload");
            if ((key.equals("primary") || key.equals("secondary") || key.equals("source"))
                && !(key.equals("primary") && text.equals("*"))) {
                FusedPair.canonicalizeEnchantId(text);
            }
        } else throw new IllegalArgumentException("unsupported type");
    }

    private static void validateArray(String file, String key, List<String> path, JsonArray array) {
        if (array.size() > 256) throw new IllegalArgumentException("array too large");
        if (key.equals("exclusive_groups")) {
            for (JsonElement group : array) {
                if (!group.isJsonArray() || group.getAsJsonArray().size() < 2 || group.getAsJsonArray().size() > 64) throw new IllegalArgumentException("invalid group");
                for (JsonElement entry : group.getAsJsonArray()) validateIdentifier(entry);
            }
        } else if (key.equals("roles") || path.contains("extra_source_roles")) {
            if (array.isEmpty()) throw new IllegalArgumentException("empty roles");
            for (JsonElement role : array) {
                if (!role.isJsonPrimitive() || !role.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("invalid role");
                TargetRole.valueOf(role.getAsString());
            }
        } else if (key.equals("extra_sources")) {
            for (JsonElement source : array) validateIdentifier(source);
        } else {
            throw new IllegalArgumentException("unsupported array");
        }
    }

    private static void validateIdentifier(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("invalid identifier");
        }
        FusedPair.canonicalizeEnchantId(value.getAsString());
    }

    private static void validateRelationships(JsonObject docs) {
        JsonObject eco = object(docs, "economy");
        if (eco != null) {
            for (String[] pair : new String[][]{
                {"low_fail_chance", "fail_ceiling_low"}, {"mid_fail_chance", "fail_ceiling_mid"},
                {"high_fail_chance", "fail_ceiling_high"}, {"high_fail_chance", "fail_ceiling_conversion"},
                {"repulsive_fail_chance", "fail_ceiling_repulsive"}, {"aberrant_socket_fail_chance", "fail_ceiling_socket"},
                {"melee_min_charge", "melee_full_charge"}
            }) {
                if (eco.has(pair[0]) && eco.has(pair[1]) && eco.get(pair[0]).getAsDouble() > eco.get(pair[1]).getAsDouble()) {
                    throw new IllegalArgumentException("minimum exceeds maximum");
                }
            }
        }
        JsonObject signatures = object(docs, "signatures");
        if (signatures != null && object(signatures, "signatures") != null) {
            Set<String> used = new java.util.HashSet<>();
            for (Map.Entry<String, JsonElement> entry : signatures.getAsJsonObject("signatures").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject definition = entry.getValue().getAsJsonObject();
                if (!definition.has("primary") || !definition.has("secondary")) continue;
                String rawPrimary = definition.get("primary").getAsString();
                String a = "*".equals(rawPrimary) ? rawPrimary : FusedPair.canonicalizeEnchantId(rawPrimary);
                String b = FusedPair.canonicalizeEnchantId(definition.get("secondary").getAsString());
                if (a.equals(b) || !used.add(a + ">" + b)) throw new IllegalArgumentException("duplicate signature pair");
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("enchantment_fusion").resolve("overrides.json");
    }

    private static JsonObject readOverrides() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) return new JsonObject();
        try {
            if (Files.size(path) > MAX_DOCUMENT_CHARS * 3L) throw new IllegalArgumentException("file too large");
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() > MAX_DOCUMENT_CHARS) throw new IllegalArgumentException("file too large");
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("not an object");
            return parsed.getAsJsonObject();
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.error("Unable to read Enchantment Fusion overrides at {}; ignoring but preserving the file", path, ex);
            return new JsonObject();
        }
    }

    private static void writeOverrides(String serialized) throws Exception {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "overrides-", ".tmp");
        try {
            Files.writeString(temp, serialized + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
