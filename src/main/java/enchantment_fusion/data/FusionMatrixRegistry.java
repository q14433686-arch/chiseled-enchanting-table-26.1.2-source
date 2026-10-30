package enchantment_fusion.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import enchantment_fusion.model.FusedPair;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, atomic snapshot of the 8x14 adaptive matrix profiles, repulsive exclusive groups,
 * and full-curve datapack-configurable economy parameters.
 * Supports {@code "replace": true} and explicit empty {@code "exclusive_groups": []} overrides (EF-006 / DATA-05),
 * tag &amp; slot-based inference (including {@code OFFHAND}, NUM-3) for third-party enchantments,
 * strict {@code serverSnapshot()} vs {@code clientView()} separation (API-2), and dedicated-server client sync (NET-01).
 */
public final class FusionMatrixRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("enchantment_fusion");
    public static final int SUPPORTED_ECONOMY_SCHEMA_VERSION = 2;

    public record MatrixProfile(TriggerType trigger, PayloadType payload) {}

    public record EconomyConfig(
        double lowFailChance,
        double midFailChance,
        double highFailChance,
        double repulsiveFailChance,
        double repulsiveMinFloor,
        double aberrantSocketFailChance,
        double failCeilingLow,
        double failCeilingMid,
        double failCeilingHigh,
        double failCeilingRepulsive,
        double failCeilingConversion,
        double failCeilingSocket,
        double stabilizerReductionRepulsive,
        double stabilizerReductionNormal,
        int maxRepulsiveStabilizers,
        int maxNormalStabilizers,
        int maxLowStabilizers,
        double normalFailFeeRatio,
        int repulsiveBaseXp,
        int repulsiveMaxBonusXp,
        int repulsiveMaxPairReagents,
        int repulsiveDefaultReagents,
        int highBaseXp,
        int highMaxXp,
        int highReagents,
        int midBaseXp,
        int midMinXp,
        int midMaxXp,
        int midMaxReagents,
        int lowBaseXp,
        int lowMinXp,
        int lowMaxReagents,
        int socketBookXp,
        int topUpXp,
        int expandVesselXp,
        int manageFlipSplitXp,
        int purifyAberrantXp,
        int purifyAberrantStabilizers,
        double meleeMinCharge,
        double meleeFullCharge,
        Map<TriggerType, Integer> triggerCooldowns
    ) {
        public static final Map<TriggerType, Integer> DEFAULT_TRIGGER_COOLDOWNS = buildDefaultTriggerCooldowns();

        private static Map<TriggerType, Integer> buildDefaultTriggerCooldowns() {
            Map<TriggerType, Integer> m = new EnumMap<>(TriggerType.class);
            for (TriggerType t : TriggerType.values()) {
                m.put(t, t.cooldownTicks());
            }
            return Collections.unmodifiableMap(m);
        }

        public static final EconomyConfig DEFAULT = new EconomyConfig(
            0.0,   // lowFailChance
            0.18,  // midFailChance
            0.28,  // highFailChance
            0.45,  // repulsiveFailChance
            0.08,  // repulsiveMinFloor
            0.15,  // aberrantSocketFailChance
            0.50,  // failCeilingLow
            0.65,  // failCeilingMid
            0.75,  // failCeilingHigh
            0.85,  // failCeilingRepulsive
            0.60,  // failCeilingConversion
            0.40,  // failCeilingSocket
            0.13,  // stabilizerReductionRepulsive
            0.18,  // stabilizerReductionNormal
            3,     // maxRepulsiveStabilizers
            2,     // maxNormalStabilizers
            1,     // maxLowStabilizers
            0.50,  // normalFailFeeRatio
            7,     // repulsiveBaseXp
            4,     // repulsiveMaxBonusXp
            3,     // repulsiveMaxPairReagents
            2,     // repulsiveDefaultReagents
            4,     // highBaseXp
            9,     // highMaxXp
            2,     // highReagents
            2,     // midBaseXp
            3,     // midMinXp
            7,     // midMaxXp
            3,     // midMaxReagents
            1,     // lowBaseXp
            2,     // lowMinXp
            4,     // lowMaxReagents
            2,     // socketBookXp
            2,     // topUpXp
            5,     // expandVesselXp
            2,     // manageFlipSplitXp
            25,    // purifyAberrantXp
            3,     // purifyAberrantStabilizers
            0.55,  // meleeMinCharge
            0.90,  // meleeFullCharge
            DEFAULT_TRIGGER_COOLDOWNS
        );

        public EconomyConfig {
            Map<TriggerType, Integer> copy = new EnumMap<>(TriggerType.class);
            for (TriggerType t : TriggerType.values()) {
                int raw = (triggerCooldowns != null && triggerCooldowns.containsKey(t))
                    ? triggerCooldowns.get(t)
                    : t.cooldownTicks();
                copy.put(t, Math.clamp(raw, 4, 1200));
            }
            triggerCooldowns = Collections.unmodifiableMap(copy);
        }

        /** Compact 8-parameter convenience constructor delegating remaining curve fields to {@link #DEFAULT}. */
        public EconomyConfig(
            double lowFailChance,
            double midFailChance,
            double highFailChance,
            double repulsiveFailChance,
            double repulsiveMinFloor,
            double stabilizerReductionRepulsive,
            double stabilizerReductionNormal,
            int maxRepulsiveStabilizers
        ) {
            this(
                lowFailChance,
                midFailChance,
                highFailChance,
                repulsiveFailChance,
                repulsiveMinFloor,
                DEFAULT.aberrantSocketFailChance,
                Math.max(DEFAULT.failCeilingLow, lowFailChance),
                Math.max(DEFAULT.failCeilingMid, midFailChance),
                Math.max(DEFAULT.failCeilingHigh, highFailChance),
                Math.max(DEFAULT.failCeilingRepulsive, repulsiveFailChance),
                Math.max(DEFAULT.failCeilingConversion, highFailChance),
                DEFAULT.failCeilingSocket,
                stabilizerReductionRepulsive,
                stabilizerReductionNormal,
                maxRepulsiveStabilizers,
                DEFAULT.maxNormalStabilizers,
                DEFAULT.maxLowStabilizers,
                DEFAULT.normalFailFeeRatio,
                DEFAULT.repulsiveBaseXp,
                DEFAULT.repulsiveMaxBonusXp,
                DEFAULT.repulsiveMaxPairReagents,
                DEFAULT.repulsiveDefaultReagents,
                DEFAULT.highBaseXp,
                DEFAULT.highMaxXp,
                DEFAULT.highReagents,
                DEFAULT.midBaseXp,
                DEFAULT.midMinXp,
                DEFAULT.midMaxXp,
                DEFAULT.midMaxReagents,
                DEFAULT.lowBaseXp,
                DEFAULT.lowMinXp,
                DEFAULT.lowMaxReagents,
                DEFAULT.socketBookXp,
                DEFAULT.topUpXp,
                DEFAULT.expandVesselXp,
                DEFAULT.manageFlipSplitXp,
                DEFAULT.purifyAberrantXp,
                DEFAULT.purifyAberrantStabilizers,
                DEFAULT.meleeMinCharge,
                DEFAULT.meleeFullCharge,
                DEFAULT_TRIGGER_COOLDOWNS
            );
        }

        public int cooldownTicksFor(TriggerType trigger) {
            if (trigger == null) {
                return 20;
            }
            return triggerCooldowns.getOrDefault(trigger, trigger.cooldownTicks());
        }
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

    /**
     * Authoritative matrix + economy snapshot without duplicated instability fields (API-1).
     */
    public record Snapshot(
        Map<String, MatrixProfile> profiles,
        List<Set<String>> exclusiveGroups,
        EconomyConfig economy
    ) {
        public Snapshot {
            economy = economy != null ? economy : EconomyConfig.DEFAULT;
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
                // 2. Semantic slot inference from Enchantment.definition().slots() (including OFFHAND, NUM-3)
                if (inferredTrigger == null || inferredPayload == null) {
                    List<EquipmentSlotGroup> slots = holder.value().definition().slots();
                    if (slots.contains(EquipmentSlotGroup.FEET)) {
                        if (inferredTrigger == null) inferredTrigger = TriggerType.ON_MOVEMENT_OR_LAND;
                        if (inferredPayload == null) inferredPayload = PayloadType.TIDE;
                    } else if (slots.contains(EquipmentSlotGroup.ARMOR)
                        || slots.contains(EquipmentSlotGroup.HEAD)
                        || slots.contains(EquipmentSlotGroup.CHEST)
                        || slots.contains(EquipmentSlotGroup.LEGS)
                        || slots.contains(EquipmentSlotGroup.OFFHAND)) {
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
            // Unclassified third-party enchantments use a predictable, conservative utility
            // profile. Datapack profiles and explicit tags remain authoritative.
            TriggerType finalT = inferredTrigger != null ? inferredTrigger : TriggerType.ON_UTILITY_CYCLE;
            PayloadType finalP = inferredPayload != null ? inferredPayload : PayloadType.MEND;
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

    /** Client UI / tooltip view preferring synced dedicated-server snapshot over local defaults (API-2). */
    public static Snapshot clientView() {
        Snapshot override = clientOverride;
        return override != null ? override : serverActive;
    }

    /** Authoritative server-side snapshot unaffected by client sync state in Singleplayer/LAN (API-2). */
    public static Snapshot serverSnapshot() {
        return serverActive;
    }

    /** Side-explicit resolver for shared menu/preview helpers. */
    public static Snapshot snapshotFor(boolean clientSide) {
        return clientSide ? clientView() : serverSnapshot();
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
        boolean replaceProfiles = readBool(matrixObj, "replace", false);
        Map<String, MatrixProfile> mergedProfiles = replaceProfiles
            ? new HashMap<>()
            : new HashMap<>(defaults.profiles());

        if (matrixObj != null && matrixObj.has("profiles") && matrixObj.get("profiles").isJsonObject()) {
            JsonObject rawProfiles = matrixObj.getAsJsonObject("profiles");
            for (Map.Entry<String, JsonElement> entry : rawProfiles.entrySet()) {
                String key = FusedPair.canonicalizeEnchantId(entry.getKey());
                if (entry.getValue().isJsonNull()) {
                    mergedProfiles.remove(key);
                    continue;
                }
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                if (readBool(p, "remove", false)) {
                    mergedProfiles.remove(key);
                    continue;
                }
                TriggerType trig = TriggerType.fromString(
                    readString(p, "trigger", null),
                    TriggerType.ON_MELEE_STRIKE
                );
                PayloadType pay = PayloadType.fromString(
                    readString(p, "payload", null),
                    PayloadType.EDGE
                );
                mergedProfiles.put(key, new MatrixProfile(trig, pay));
            }
        }

        double fallbackRepulsiveFail = defaults.economy().repulsiveFailChance();
        List<Set<String>> groups;
        if (repulsiveObj != null) {
            if (repulsiveObj.has("base_instability_chance") && repulsiveObj.get("base_instability_chance").isJsonPrimitive()) {
                fallbackRepulsiveFail = Math.clamp(repulsiveObj.get("base_instability_chance").getAsDouble(), 0.0, 1.0);
            }
            if (repulsiveObj.has("exclusive_groups") && repulsiveObj.get("exclusive_groups").isJsonArray()) {
                // Explicit exclusive_groups array in JSON is authoritative — empty [] disables built-in groups! (EF-006)
                boolean replaceGroups = readBool(repulsiveObj, "replace", true);
                List<Set<String>> parsedGroups = replaceGroups ? new ArrayList<>() : new ArrayList<>(defaults.exclusiveGroups());
                JsonArray arr = repulsiveObj.getAsJsonArray("exclusive_groups");
                if (arr.isEmpty()) {
                    parsedGroups.clear();
                } else {
                    for (JsonElement el : arr) {
                        if (!el.isJsonArray()) continue;
                        Set<String> g = new HashSet<>();
                        for (JsonElement idEl : el.getAsJsonArray()) {
                            if (idEl != null && idEl.isJsonPrimitive()) {
                                String trimmed = idEl.getAsString().trim();
                                if (!trimmed.isEmpty()) {
                                    g.add(FusedPair.canonicalizeEnchantId(trimmed));
                                }
                            }
                        }
                        if (g.size() >= 2) {
                            Set<String> immutableGroup = Collections.unmodifiableSet(g);
                            if (!parsedGroups.contains(immutableGroup)) {
                                parsedGroups.add(immutableGroup);
                            }
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

        EconomyConfig eco = parseEconomy(economyObj, fallbackRepulsiveFail);
        return new Snapshot(
            Collections.unmodifiableMap(mergedProfiles),
            Collections.unmodifiableList(groups),
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
        int schemaVer = readInt(obj, "schema_version", SUPPORTED_ECONOMY_SCHEMA_VERSION, 1, 999);
        if (schemaVer > SUPPORTED_ECONOMY_SCHEMA_VERSION) {
            LOGGER.warn(
                "economy.json declares unsupported schema_version={} (supported <= {}); falling back to built-in economy defaults",
                schemaVer,
                SUPPORTED_ECONOMY_SCHEMA_VERSION
            );
            return d;
        }

        double lowFail = readDouble(obj, "low_fail_chance", d.lowFailChance(), 0.0, 1.0);
        double midFail = readDouble(obj, "mid_fail_chance", d.midFailChance(), 0.0, 1.0);
        double highFail = readDouble(obj, "high_fail_chance", d.highFailChance(), 0.0, 1.0);
        double repFail = readDouble(obj, "repulsive_fail_chance", fallbackRepulsiveFail, 0.0, 1.0);
        double abSockFail = readDouble(obj, "aberrant_socket_fail_chance", d.aberrantSocketFailChance(), 0.0, 1.0);

        // Ceilings always accommodate at least the configured base fail chance so high custom fail rates are never silently capped below base (NUM-1)
        double ceilLow = Math.max(lowFail, readDouble(obj, "fail_ceiling_low", d.failCeilingLow(), 0.0, 1.0));
        double ceilMid = Math.max(midFail, readDouble(obj, "fail_ceiling_mid", d.failCeilingMid(), 0.0, 1.0));
        double ceilHigh = Math.max(highFail, readDouble(obj, "fail_ceiling_high", d.failCeilingHigh(), 0.0, 1.0));
        double ceilRep = Math.max(repFail, readDouble(obj, "fail_ceiling_repulsive", d.failCeilingRepulsive(), 0.0, 1.0));
        double ceilConv = Math.max(highFail, readDouble(obj, "fail_ceiling_conversion", d.failCeilingConversion(), 0.0, 1.0));
        double ceilSock = Math.max(abSockFail, readDouble(obj, "fail_ceiling_socket", d.failCeilingSocket(), 0.0, 1.0));

        Map<TriggerType, Integer> cooldowns = new EnumMap<>(d.triggerCooldowns());
        if (obj.has("trigger_cooldowns") && obj.get("trigger_cooldowns").isJsonObject()) {
            JsonObject cdObj = obj.getAsJsonObject("trigger_cooldowns");
            for (TriggerType t : TriggerType.values()) {
                String k = t.name();
                if (cdObj.has(k) && cdObj.get(k).isJsonPrimitive()) {
                    cooldowns.put(t, Math.clamp(cdObj.get(k).getAsInt(), 4, 1200));
                }
            }
        }

        double minCharge = readDouble(obj, "melee_min_charge", d.meleeMinCharge(), 0.05, 1.0);
        double fullCharge = Math.max(minCharge, readDouble(obj, "melee_full_charge", d.meleeFullCharge(), 0.10, 1.0));

        return new EconomyConfig(
            lowFail,
            midFail,
            highFail,
            repFail,
            readDouble(obj, "repulsive_min_floor", d.repulsiveMinFloor(), 0.0, 1.0),
            abSockFail,
            ceilLow,
            ceilMid,
            ceilHigh,
            ceilRep,
            ceilConv,
            ceilSock,
            readDouble(obj, "stabilizer_reduction_repulsive", d.stabilizerReductionRepulsive(), 0.01, 1.0),
            readDouble(obj, "stabilizer_reduction_normal", d.stabilizerReductionNormal(), 0.01, 1.0),
            readInt(obj, "max_repulsive_stabilizers", d.maxRepulsiveStabilizers(), 0, 16),
            readInt(obj, "max_normal_stabilizers", d.maxNormalStabilizers(), 0, 16),
            readInt(obj, "max_low_stabilizers", d.maxLowStabilizers(), 0, 16),
            readDouble(obj, "normal_fail_fee_ratio", d.normalFailFeeRatio(), 0.0, 1.0),
            readInt(obj, "repulsive_base_xp", d.repulsiveBaseXp(), 0, 100),
            readInt(obj, "repulsive_max_bonus_xp", d.repulsiveMaxBonusXp(), 0, 50),
            readInt(obj, "repulsive_max_pair_reagents", d.repulsiveMaxPairReagents(), 0, 64),
            readInt(obj, "repulsive_default_reagents", d.repulsiveDefaultReagents(), 0, 64),
            readInt(obj, "high_base_xp", d.highBaseXp(), 0, 100),
            readInt(obj, "high_max_xp", d.highMaxXp(), 0, 100),
            readInt(obj, "high_reagents", d.highReagents(), 0, 64),
            readInt(obj, "mid_base_xp", d.midBaseXp(), 0, 100),
            readInt(obj, "mid_min_xp", d.midMinXp(), 0, 100),
            readInt(obj, "mid_max_xp", d.midMaxXp(), 0, 100),
            readInt(obj, "mid_max_reagents", d.midMaxReagents(), 0, 64),
            readInt(obj, "low_base_xp", d.lowBaseXp(), 0, 100),
            readInt(obj, "low_min_xp", d.lowMinXp(), 0, 100),
            readInt(obj, "low_max_reagents", d.lowMaxReagents(), 0, 64),
            readInt(obj, "socket_book_xp", d.socketBookXp(), 0, 100),
            readInt(obj, "top_up_xp", d.topUpXp(), 0, 100),
            readInt(obj, "expand_vessel_xp", d.expandVesselXp(), 0, 100),
            readInt(obj, "manage_flip_split_xp", d.manageFlipSplitXp(), 0, 100),
            readInt(obj, "purify_aberrant_xp", d.purifyAberrantXp(), 0, 200),
            readInt(obj, "purify_aberrant_stabilizers", d.purifyAberrantStabilizers(), 1, 64),
            minCharge,
            fullCharge,
            cooldowns
        );
    }

    private static boolean readBool(@Nullable JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()
            ? el.getAsBoolean()
            : def;
    }

    private static @Nullable String readString(@Nullable JsonObject obj, String key, @Nullable String def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : def;
    }

    private static double readDouble(JsonObject obj, String key, double def, double lo, double hi) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
            ? Math.clamp(obj.get(key).getAsDouble(), lo, hi)
            : def;
    }

    private static int readInt(JsonObject obj, String key, int def, int lo, int hi) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
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
            EconomyConfig.DEFAULT
        );
    }

    private static void put(Map<String, MatrixProfile> map, String id, TriggerType t, PayloadType p) {
        map.put(id, new MatrixProfile(t, p));
    }
}
