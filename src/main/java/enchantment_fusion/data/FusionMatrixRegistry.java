package enchantment_fusion.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe, atomic snapshot of the 8x14 adaptive matrix profiles and repulsive exclusive groups.
 * Ships built-in vanilla defaults identical to the datapack JSONs and swaps atomically on {@code /reload}.
 */
public final class FusionMatrixRegistry {
    public record MatrixProfile(TriggerType trigger, PayloadType payload) {}

    public record Snapshot(
        Map<String, MatrixProfile> profiles,
        List<Set<String>> exclusiveGroups,
        double baseInstabilityChance
    ) {
        public MatrixProfile resolve(String enchantmentId) {
            MatrixProfile hit = profiles.get(enchantmentId);
            if (hit != null) {
                return hit;
            }
            // Deterministic fallback for third-party modded enchantments not explicitly listed in JSON
            int h = Math.abs(enchantmentId == null ? 0 : enchantmentId.hashCode());
            TriggerType[] triggers = TriggerType.values();
            PayloadType[] payloads = PayloadType.values();
            return new MatrixProfile(
                triggers[h % triggers.length],
                payloads[(h / triggers.length) % payloads.length]
            );
        }

        public boolean areRepulsiveExclusives(String a, String b) {
            if (a == null || b == null || a.equals(b)) {
                return false;
            }
            for (Set<String> group : exclusiveGroups) {
                if (group.contains(a) && group.contains(b)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static volatile Snapshot active = createBuiltInSnapshot();

    private FusionMatrixRegistry() {}

    public static Snapshot current() {
        return active;
    }

    public static void publish(Snapshot next) {
        if (next != null) {
            active = next;
        }
    }

    public static void resetToBuiltIn() {
        active = createBuiltInSnapshot();
    }

    public static Snapshot parseFromJson(JsonObject matrixObj, JsonObject repulsiveObj) {
        Snapshot defaults = createBuiltInSnapshot();
        Map<String, MatrixProfile> mergedProfiles = new HashMap<>(defaults.profiles());
        if (matrixObj != null && matrixObj.has("profiles") && matrixObj.get("profiles").isJsonObject()) {
            JsonObject rawProfiles = matrixObj.getAsJsonObject("profiles");
            for (Map.Entry<String, JsonElement> entry : rawProfiles.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                TriggerType trig = TriggerType.fromString(
                    p.has("trigger") ? p.get("trigger").getAsString() : null,
                    TriggerType.ON_MELEE_STRIKE
                );
                PayloadType pay = PayloadType.fromString(
                    p.has("payload") ? p.get("payload").getAsString() : null,
                    PayloadType.EDGE
                );
                mergedProfiles.put(entry.getKey().trim(), new MatrixProfile(trig, pay));
            }
        }

        List<Set<String>> groups = new ArrayList<>();
        double instability = defaults.baseInstabilityChance();
        if (repulsiveObj != null) {
            if (repulsiveObj.has("base_instability_chance")) {
                instability = Math.max(0.0, Math.min(1.0, repulsiveObj.get("base_instability_chance").getAsDouble()));
            }
            if (repulsiveObj.has("exclusive_groups") && repulsiveObj.get("exclusive_groups").isJsonArray()) {
                JsonArray arr = repulsiveObj.getAsJsonArray("exclusive_groups");
                for (JsonElement el : arr) {
                    if (!el.isJsonArray()) continue;
                    Set<String> g = new HashSet<>();
                    for (JsonElement idEl : el.getAsJsonArray()) {
                        g.add(idEl.getAsString().trim());
                    }
                    if (g.size() >= 2) {
                        groups.add(Collections.unmodifiableSet(g));
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            groups = defaults.exclusiveGroups();
        }
        return new Snapshot(
            Collections.unmodifiableMap(mergedProfiles),
            Collections.unmodifiableList(groups),
            instability
        );
    }

    public static Snapshot createBuiltInSnapshot() {
        Map<String, MatrixProfile> m = new HashMap<>();
        put(m, "minecraft:sharpness", TriggerType.ON_MELEE_STRIKE, PayloadType.EDGE);
        put(m, "minecraft:smite", TriggerType.ON_MELEE_STRIKE, PayloadType.BANE);
        put(m, "minecraft:bane_of_arthropods", TriggerType.ON_MELEE_STRIKE, PayloadType.BANE);
        put(m, "minecraft:fire_aspect", TriggerType.ON_MELEE_STRIKE, PayloadType.PYRO);
        put(m, "minecraft:knockback", TriggerType.ON_MELEE_STRIKE, PayloadType.GALE);
        put(m, "minecraft:breach", TriggerType.ON_MELEE_STRIKE, PayloadType.PIERCE);
        put(m, "minecraft:density", TriggerType.ON_MELEE_STRIKE, PayloadType.GRAVITY);
        put(m, "minecraft:lunge", TriggerType.ON_MELEE_STRIKE, PayloadType.PIERCE);
        put(m, "minecraft:sweeping_edge", TriggerType.ON_SWEEP_OR_CLEAVE, PayloadType.SCATTER);
        put(m, "minecraft:wind_burst", TriggerType.ON_SWEEP_OR_CLEAVE, PayloadType.GALE);
        put(m, "minecraft:power", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.EDGE);
        put(m, "minecraft:punch", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.GALE);
        put(m, "minecraft:flame", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.PYRO);
        put(m, "minecraft:infinity", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.MEND);
        put(m, "minecraft:multishot", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.SCATTER);
        put(m, "minecraft:piercing", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.PIERCE);
        put(m, "minecraft:quick_charge", TriggerType.ON_PROJECTILE_IMPACT, PayloadType.MEND);
        put(m, "minecraft:loyalty", TriggerType.ON_TRIDENT_CHANNEL, PayloadType.RETURN);
        put(m, "minecraft:riptide", TriggerType.ON_TRIDENT_CHANNEL, PayloadType.TIDE);
        put(m, "minecraft:channeling", TriggerType.ON_TRIDENT_CHANNEL, PayloadType.VOLT);
        put(m, "minecraft:impaling", TriggerType.ON_TRIDENT_CHANNEL, PayloadType.EDGE);
        put(m, "minecraft:efficiency", TriggerType.ON_BLOCK_HARVEST, PayloadType.EDGE);
        put(m, "minecraft:silk_touch", TriggerType.ON_BLOCK_HARVEST, PayloadType.AEGIS);
        put(m, "minecraft:fortune", TriggerType.ON_BLOCK_HARVEST, PayloadType.FORTUNE);
        put(m, "minecraft:protection", TriggerType.ON_DAMAGE_TAKEN, PayloadType.AEGIS);
        put(m, "minecraft:fire_protection", TriggerType.ON_DAMAGE_TAKEN, PayloadType.PYRO);
        put(m, "minecraft:blast_protection", TriggerType.ON_DAMAGE_TAKEN, PayloadType.SCATTER);
        put(m, "minecraft:projectile_protection", TriggerType.ON_DAMAGE_TAKEN, PayloadType.AEGIS);
        put(m, "minecraft:thorns", TriggerType.ON_DAMAGE_TAKEN, PayloadType.PIERCE);
        put(m, "minecraft:soul_speed", TriggerType.ON_DAMAGE_TAKEN, PayloadType.MEND);
        put(m, "minecraft:swift_sneak", TriggerType.ON_DAMAGE_TAKEN, PayloadType.MEND);
        put(m, "minecraft:feather_falling", TriggerType.ON_MOVEMENT_OR_LAND, PayloadType.GRAVITY);
        put(m, "minecraft:depth_strider", TriggerType.ON_MOVEMENT_OR_LAND, PayloadType.TIDE);
        put(m, "minecraft:frost_walker", TriggerType.ON_MOVEMENT_OR_LAND, PayloadType.CRYO);
        put(m, "minecraft:respiration", TriggerType.ON_MOVEMENT_OR_LAND, PayloadType.AEGIS);
        put(m, "minecraft:aqua_affinity", TriggerType.ON_MOVEMENT_OR_LAND, PayloadType.TIDE);
        put(m, "minecraft:looting", TriggerType.ON_UTILITY_CYCLE, PayloadType.FORTUNE);
        put(m, "minecraft:mending", TriggerType.ON_UTILITY_CYCLE, PayloadType.MEND);
        put(m, "minecraft:unbreaking", TriggerType.ON_UTILITY_CYCLE, PayloadType.AEGIS);
        put(m, "minecraft:luck_of_the_sea", TriggerType.ON_UTILITY_CYCLE, PayloadType.TIDE);
        put(m, "minecraft:lure", TriggerType.ON_UTILITY_CYCLE, PayloadType.RETURN);
        put(m, "minecraft:binding_curse", TriggerType.ON_DAMAGE_TAKEN, PayloadType.GRAVITY);
        put(m, "minecraft:vanishing_curse", TriggerType.ON_UTILITY_CYCLE, PayloadType.SCATTER);

        List<Set<String>> groups = List.of(
            Set.of("minecraft:infinity", "minecraft:mending"),
            Set.of("minecraft:silk_touch", "minecraft:fortune"),
            Set.of("minecraft:multishot", "minecraft:piercing"),
            Set.of("minecraft:loyalty", "minecraft:riptide"),
            Set.of("minecraft:depth_strider", "minecraft:frost_walker"),
            Set.of("minecraft:density", "minecraft:breach", "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"),
            Set.of("minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection")
        );
        return new Snapshot(Collections.unmodifiableMap(m), groups, 0.35);
    }

    private static void put(Map<String, MatrixProfile> map, String id, TriggerType t, PayloadType p) {
        map.put(id, new MatrixProfile(t, p));
    }
}
