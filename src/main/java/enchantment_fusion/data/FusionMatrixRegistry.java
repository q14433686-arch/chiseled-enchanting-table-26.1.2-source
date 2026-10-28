package enchantment_fusion.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe, atomic snapshot of the 8x14 adaptive matrix profiles, repulsive exclusive groups,
 * and datapack-configurable economy parameters.
 * Supports {@code "replace": true} and explicit empty {@code "exclusive_groups": []} overrides (EF-006 / DATA-05),
 * tag & slot-based inference for third-party enchantments (HC-03 / API-06), and dedicated-server client sync (NET-01).
 */
public final class FusionMatrixRegistry {
    public record MatrixProfile(TriggerType trigger, PayloadType payload) {}

    public record EconomyConfig(
        double lowFailChance,
        double midFailChance,
        double highFailChance,
        double repulsiveFailChance,
        double repulsiveMinFloor,
        double stabilizerReductionRepulsive,
        double stabilizerReductionNormal,
        int maxRepulsiveStabilizers
    ) {
        public static final EconomyConfig DEFAULT = new EconomyConfig(
            0.0, 0.18, 0.28, 0.45, 0.08, 0.13, 0.18, 3
        );
    }

    private static final Map<TriggerType, TagKey<Enchantment>> TRIGGER_TAGS = buildTriggerTags();
    private static final Map<PayloadType, TagKey<Enchantment>> PAYLOAD_TAGS = buildPayloadTags();

    private static Map<TriggerType, TagKey<Enchantment>> buildTriggerTags() {
        Map<TriggerType, TagKey<Enchantment>> map = new EnumMap<>(TriggerType.class);
        for (TriggerType t : TriggerType.values()) {
            map.put(t, TagKey.create(
                Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath("enchantment_fusion", "trigger/" + t.name().toLowerCase(Locale.ROOT))
            ));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<PayloadType, TagKey<Enchantment>> buildPayloadTags() {
        Map<PayloadType, TagKey<Enchantment>> map = new EnumMap<>(PayloadType.class);
        for (PayloadType p : PayloadType.values()) {
            map.put(p, TagKey.create(
                Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath("enchantment_fusion", "payload/" + p.name().toLowerCase(Locale.ROOT))
            ));
        }
        return Collections.unmodifiableMap(map);
    }

    public record Snapshot(
        Map<String, MatrixProfile> profiles,
        List<Set<String>> exclusiveGroups,
        double baseInstabilityChance,
        EconomyConfig economy
    ) {
        public Snapshot(
            Map<String, MatrixProfile> profiles,
            List<Set<String>> exclusiveGroups,
            double baseInstabilityChance
        ) {
            this(profiles, exclusiveGroups, baseInstabilityChance, EconomyConfig.DEFAULT);
        }

        public MatrixProfile resolve(String enchantmentId) {
            return resolve(enchantmentId, null);
        }

        public MatrixProfile resolve(String enchantmentId, @Nullable Holder<Enchantment> holder) {
            MatrixProfile hit = profiles.get(enchantmentId);
            if (hit != null) {
                return hit;
            }
            // 1. Check explicit #enchantment_fusion:trigger/* and #enchantment_fusion:payload/* tags (HC-03)
            TriggerType inferredTrigger = null;
            PayloadType inferredPayload = null;
            if (holder != null) {
                for (Map.Entry<TriggerType, TagKey<Enchantment>> e : TRIGGER_TAGS.entrySet()) {
                    if (holder.is(e.getValue())) {
                        inferredTrigger = e.getKey();
                        break;
                    }
                }
                for (Map.Entry<PayloadType, TagKey<Enchantment>> e : PAYLOAD_TAGS.entrySet()) {
                    if (holder.is(e.getValue())) {
                        inferredPayload = e.getKey();
                        break;
                    }
                }
                // 2. Semantic slot inference from Enchantment.definition().slots()
                if (inferredTrigger == null || inferredPayload == null) {
                    List<EquipmentSlotGroup> slots = holder.value().definition().slots();
                    if (slots.contains(EquipmentSlotGroup.FEET)) {
                        if (inferredTrigger == null) inferredTrigger = TriggerType.ON_MOVEMENT_OR_LAND;
                        if (inferredPayload == null) inferredPayload = PayloadType.TIDE;
                    } else if (slots.contains(EquipmentSlotGroup.ARMOR)
                        || slots.contains(EquipmentSlotGroup.HEAD)
                        || slots.contains(EquipmentSlotGroup.CHEST)
                        || slots.contains(EquipmentSlotGroup.LEGS)) {
                        if (inferredTrigger == null) inferredTrigger = TriggerType.ON_DAMAGE_TAKEN;
                        if (inferredPayload == null) inferredPayload = PayloadType.AEGIS;
                    } else if (slots.contains(EquipmentSlotGroup.MAINHAND) || slots.contains(EquipmentSlotGroup.HAND)) {
                        if (inferredTrigger == null) inferredTrigger = TriggerType.ON_MELEE_STRIKE;
                        if (inferredPayload == null) inferredPayload = PayloadType.EDGE;
                    } else if (slots.contains(EquipmentSlotGroup.ANY)) {
                        if (inferredTrigger == null) inferredTrigger = TriggerType.ON_UTILITY_CYCLE;
                        if (inferredPayload == null) inferredPayload = PayloadType.MEND;
                    }
                }
            }
            // 3. Overflow-safe deterministic floorMod fallback when queried by bare ID without registry context (API-06)
            int h = enchantmentId == null ? 0 : enchantmentId.hashCode();
            TriggerType[] triggers = TriggerType.values();
            PayloadType[] payloads = PayloadType.values();
            TriggerType finalT = inferredTrigger != null
                ? inferredTrigger
                : triggers[Math.floorMod(h, triggers.length)];
            PayloadType finalP = inferredPayload != null
                ? inferredPayload
                : payloads[Math.floorMod(h / triggers.length, payloads.length)];
            return new MatrixProfile(finalT, finalP);
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

    private static volatile Snapshot serverActive = createBuiltInSnapshot();
    private static volatile @Nullable Snapshot clientOverride = null;

    private FusionMatrixRegistry() {}

    public static Snapshot current() {
        Snapshot override = clientOverride;
        return override != null ? override : serverActive;
    }

    public static Snapshot serverSnapshot() {
        return serverActive;
    }

    public static void publish(Snapshot next) {
        if (next != null) {
            serverActive = next;
        }
    }

    public static void setClientSnapshot(@Nullable Snapshot synced) {
        clientOverride = synced;
    }

    public static Snapshot parseFromJson(JsonObject matrixObj, JsonObject repulsiveObj) {
        return parseFromJson(matrixObj, repulsiveObj, null);
    }

    public static Snapshot parseFromJson(
        @Nullable JsonObject matrixObj,
        @Nullable JsonObject repulsiveObj,
        @Nullable JsonObject economyObj
    ) {
        Snapshot defaults = createBuiltInSnapshot();
        boolean replaceProfiles = matrixObj != null
            && matrixObj.has("replace")
            && matrixObj.get("replace").isJsonPrimitive()
            && matrixObj.get("replace").getAsBoolean();
        Map<String, MatrixProfile> mergedProfiles = replaceProfiles
            ? new HashMap<>()
            : new HashMap<>(defaults.profiles());

        if (matrixObj != null && matrixObj.has("profiles") && matrixObj.get("profiles").isJsonObject()) {
            JsonObject rawProfiles = matrixObj.getAsJsonObject("profiles");
            for (Map.Entry<String, JsonElement> entry : rawProfiles.entrySet()) {
                String key = entry.getKey().trim();
                if (entry.getValue().isJsonNull()) {
                    mergedProfiles.remove(key);
                    continue;
                }
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                if (p.has("remove") && p.get("remove").getAsBoolean()) {
                    mergedProfiles.remove(key);
                    continue;
                }
                TriggerType trig = TriggerType.fromString(
                    p.has("trigger") ? p.get("trigger").getAsString() : null,
                    TriggerType.ON_MELEE_STRIKE
                );
                PayloadType pay = PayloadType.fromString(
                    p.has("payload") ? p.get("payload").getAsString() : null,
                    PayloadType.EDGE
                );
                mergedProfiles.put(key, new MatrixProfile(trig, pay));
            }
        }

        double instability = defaults.baseInstabilityChance();
        List<Set<String>> groups;
        if (repulsiveObj != null) {
            if (repulsiveObj.has("base_instability_chance")) {
                instability = Math.clamp(repulsiveObj.get("base_instability_chance").getAsDouble(), 0.0, 1.0);
            }
            if (repulsiveObj.has("exclusive_groups") && repulsiveObj.get("exclusive_groups").isJsonArray()) {
                // Explicit exclusive_groups array in JSON is authoritative — empty [] disables built-in groups! (EF-006)
                boolean replaceGroups = !repulsiveObj.has("replace") || repulsiveObj.get("replace").getAsBoolean();
                List<Set<String>> parsedGroups = replaceGroups ? new ArrayList<>() : new ArrayList<>(defaults.exclusiveGroups());
                JsonArray arr = repulsiveObj.getAsJsonArray("exclusive_groups");
                if (arr.isEmpty()) {
                    parsedGroups.clear();
                } else {
                    if (!replaceGroups) {
                        parsedGroups = new ArrayList<>(defaults.exclusiveGroups());
                    }
                    for (JsonElement el : arr) {
                        if (!el.isJsonArray()) continue;
                        Set<String> g = new HashSet<>();
                        for (JsonElement idEl : el.getAsJsonArray()) {
                            g.add(idEl.getAsString().trim());
                        }
                        if (g.size() >= 2) {
                            parsedGroups.add(Collections.unmodifiableSet(g));
                        }
                    }
                }
                groups = parsedGroups;
            } else {
                groups = defaults.exclusiveGroups();
            }
        } else {
            groups = defaults.exclusiveGroups();
        }

        EconomyConfig eco = parseEconomy(economyObj, instability);
        return new Snapshot(
            Collections.unmodifiableMap(mergedProfiles),
            Collections.unmodifiableList(groups),
            eco.repulsiveFailChance(),
            eco
        );
    }

    private static EconomyConfig parseEconomy(@Nullable JsonObject obj, double fallbackRepulsiveFail) {
        EconomyConfig d = EconomyConfig.DEFAULT;
        if (obj == null) {
            return new EconomyConfig(
                d.lowFailChance(),
                d.midFailChance(),
                d.highFailChance(),
                fallbackRepulsiveFail,
                d.repulsiveMinFloor(),
                d.stabilizerReductionRepulsive(),
                d.stabilizerReductionNormal(),
                d.maxRepulsiveStabilizers()
            );
        }
        return new EconomyConfig(
            readDouble(obj, "low_fail_chance", d.lowFailChance(), 0.0, 1.0),
            readDouble(obj, "mid_fail_chance", d.midFailChance(), 0.0, 1.0),
            readDouble(obj, "high_fail_chance", d.highFailChance(), 0.0, 1.0),
            readDouble(obj, "repulsive_fail_chance", fallbackRepulsiveFail, 0.0, 1.0),
            readDouble(obj, "repulsive_min_floor", d.repulsiveMinFloor(), 0.0, 1.0),
            readDouble(obj, "stabilizer_reduction_repulsive", d.stabilizerReductionRepulsive(), 0.01, 1.0),
            readDouble(obj, "stabilizer_reduction_normal", d.stabilizerReductionNormal(), 0.01, 1.0),
            readInt(obj, "max_repulsive_stabilizers", d.maxRepulsiveStabilizers(), 0, 16)
        );
    }

    private static double readDouble(JsonObject obj, String key, double def, double lo, double hi) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
            ? Math.clamp(obj.get(key).getAsDouble(), lo, hi)
            : def;
    }

    private static int readInt(JsonObject obj, String key, int def, int lo, int hi) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
            ? Math.clamp(obj.get(key).getAsInt(), lo, hi)
            : def;
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
            Set.of("minecraft:channeling", "minecraft:riptide"),
            Set.of("minecraft:depth_strider", "minecraft:frost_walker"),
            Set.of("minecraft:density", "minecraft:breach", "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"),
            Set.of("minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection")
        );
        return new Snapshot(
            Collections.unmodifiableMap(m),
            groups,
            EconomyConfig.DEFAULT.repulsiveFailChance(),
            EconomyConfig.DEFAULT
        );
    }

    private static void put(Map<String, MatrixProfile> map, String id, TriggerType t, PayloadType p) {
        map.put(id, new MatrixProfile(t, p));
    }
}
