package enchantment_fusion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.model.TriggerType;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Atomic JSON reader/writer for {@code config/enchantment_fusion.json}, modeled directly after
 * sister mod {@code Chiseled Enchanting Table (纂刻附魔台)}.
 */
public final class ConfigFileStore {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile @Nullable Path customPathOverride = null;

    private ConfigFileStore() {}

    public static void setCustomPathForTesting(@Nullable Path override) {
        customPathOverride = override;
    }

    public static Path configPath() {
        Path override = customPathOverride;
        if (override != null) {
            return override;
        }
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("enchantment_fusion.json");
        } catch (Throwable ignored) {
            return Path.of("config", "enchantment_fusion.json");
        }
    }

    public static ModRuntimeConfig.Snapshot loadOrCreate() {
        Path path = configPath();
        if (!Files.exists(path)) {
            ModRuntimeConfig.Snapshot defaults = ModRuntimeConfig.Snapshot.DEFAULT;
            save(defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                ModRuntimeConfig.Snapshot loaded = fromJson(parsed.getAsJsonObject());
                save(loaded);
                return loaded;
            }
        } catch (Exception ex) {
            EnchantmentFusion.LOGGER.warn("Failed to read {}; falling back to defaults", path, ex);
        }
        return ModRuntimeConfig.Snapshot.DEFAULT;
    }

    public static void save(ModRuntimeConfig.Snapshot snapshot) {
        Path path = configPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(snapshot), writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            EnchantmentFusion.LOGGER.warn("Failed to save {}", path, ex);
        }
    }

    public static JsonObject toJson(ModRuntimeConfig.Snapshot s) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);

        JsonObject sys = new JsonObject();
        sys.addProperty("override_datapacks", s.overrideDatapacks());
        sys.addProperty("frosted_ice_no_water", s.frostedIceNoWater());
        sys.addProperty("background_ritual_enabled", s.backgroundRitualEnabled());
        sys.addProperty("ritual_normal_ticks", s.ritualNormalTicks());
        sys.addProperty("ritual_high_risk_ticks", s.ritualHighRiskTicks());
        sys.addProperty("trident_fused_loyalty_return", s.tridentFusedLoyaltyReturn());
        sys.addProperty("block_vanilla_anvil_cross_domain", s.blockVanillaAnvilCrossDomain());
        root.add("system", sys);

        EconomyConfig ec = s.economy();
        JsonObject econ = new JsonObject();
        econ.addProperty("low_fail_chance", ec.lowFailChance());
        econ.addProperty("mid_fail_chance", ec.midFailChance());
        econ.addProperty("high_fail_chance", ec.highFailChance());
        econ.addProperty("repulsive_fail_chance", ec.repulsiveFailChance());
        econ.addProperty("repulsive_min_floor", ec.repulsiveMinFloor());
        econ.addProperty("aberrant_socket_fail_chance", ec.aberrantSocketFailChance());
        econ.addProperty("fail_ceiling_low", ec.failCeilingLow());
        econ.addProperty("fail_ceiling_mid", ec.failCeilingMid());
        econ.addProperty("fail_ceiling_high", ec.failCeilingHigh());
        econ.addProperty("fail_ceiling_repulsive", ec.failCeilingRepulsive());
        econ.addProperty("fail_ceiling_conversion", ec.failCeilingConversion());
        econ.addProperty("fail_ceiling_socket", ec.failCeilingSocket());
        econ.addProperty("stabilizer_reduction_repulsive", ec.stabilizerReductionRepulsive());
        econ.addProperty("stabilizer_reduction_normal", ec.stabilizerReductionNormal());
        econ.addProperty("max_repulsive_stabilizers", ec.maxRepulsiveStabilizers());
        econ.addProperty("max_normal_stabilizers", ec.maxNormalStabilizers());
        econ.addProperty("max_low_stabilizers", ec.maxLowStabilizers());
        econ.addProperty("normal_fail_fee_ratio", ec.normalFailFeeRatio());
        econ.addProperty("repulsive_base_xp", ec.repulsiveBaseXp());
        econ.addProperty("repulsive_max_bonus_xp", ec.repulsiveMaxBonusXp());
        econ.addProperty("repulsive_max_pair_reagents", ec.repulsiveMaxPairReagents());
        econ.addProperty("repulsive_default_reagents", ec.repulsiveDefaultReagents());
        econ.addProperty("high_base_xp", ec.highBaseXp());
        econ.addProperty("high_max_xp", ec.highMaxXp());
        econ.addProperty("high_reagents", ec.highReagents());
        econ.addProperty("mid_base_xp", ec.midBaseXp());
        econ.addProperty("mid_min_xp", ec.midMinXp());
        econ.addProperty("mid_max_xp", ec.midMaxXp());
        econ.addProperty("mid_max_reagents", ec.midMaxReagents());
        econ.addProperty("low_base_xp", ec.lowBaseXp());
        econ.addProperty("low_min_xp", ec.lowMinXp());
        econ.addProperty("low_max_reagents", ec.lowMaxReagents());
        econ.addProperty("socket_book_xp", ec.socketBookXp());
        econ.addProperty("top_up_xp", ec.topUpXp());
        econ.addProperty("expand_vessel_xp", ec.expandVesselXp());
        econ.addProperty("manage_flip_split_xp", ec.manageFlipSplitXp());
        econ.addProperty("purify_aberrant_xp", ec.purifyAberrantXp());
        econ.addProperty("purify_aberrant_stabilizers", ec.purifyAberrantStabilizers());
        econ.addProperty("melee_min_charge", ec.meleeMinCharge());
        econ.addProperty("melee_full_charge", ec.meleeFullCharge());

        JsonObject cds = new JsonObject();
        for (TriggerType t : TriggerType.values()) {
            cds.addProperty(t.name().toLowerCase(Locale.ROOT), ec.cooldownTicksFor(t));
        }
        econ.add("trigger_cooldowns", cds);
        root.add("economy", econ);

        JsonArray disSig = new JsonArray();
        for (String id : new TreeSet<>(s.disabledSignatures())) {
            disSig.add(id);
        }
        root.add("disabled_signatures", disSig);

        JsonArray disTrans = new JsonArray();
        for (String id : new TreeSet<>(s.disabledTransmutations())) {
            disTrans.add(id);
        }
        root.add("disabled_transmutations", disTrans);
        return root;
    }

    public static ModRuntimeConfig.Snapshot fromJson(@Nullable JsonObject root) {
        ModRuntimeConfig.Snapshot d = ModRuntimeConfig.Snapshot.DEFAULT;
        if (root == null) {
            return d;
        }
        JsonObject sys = obj(root, "system");
        JsonObject econ = obj(root, "economy");
        EconomyConfig ed = EconomyConfig.DEFAULT;

        Map<TriggerType, Integer> cds = new EnumMap<>(TriggerType.class);
        JsonObject cdObj = obj(econ, "trigger_cooldowns");
        for (TriggerType t : TriggerType.values()) {
            String k = t.name().toLowerCase(Locale.ROOT);
            cds.put(t, readInt(cdObj, k, t.cooldownTicks(), 4, 1200));
        }
        double minCharge = readDouble(econ, "melee_min_charge", ed.meleeMinCharge(), 0.0, 1.0);
        double fullCharge = Math.max(minCharge, readDouble(econ, "melee_full_charge", ed.meleeFullCharge(), 0.1, 1.0));

        EconomyConfig parsedEcon = new EconomyConfig(
            readDouble(econ, "low_fail_chance", ed.lowFailChance(), 0.0, 1.0),
            readDouble(econ, "mid_fail_chance", ed.midFailChance(), 0.0, 1.0),
            readDouble(econ, "high_fail_chance", ed.highFailChance(), 0.0, 1.0),
            readDouble(econ, "repulsive_fail_chance", ed.repulsiveFailChance(), 0.0, 1.0),
            readDouble(econ, "repulsive_min_floor", ed.repulsiveMinFloor(), 0.0, 1.0),
            readDouble(econ, "aberrant_socket_fail_chance", ed.aberrantSocketFailChance(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_low", ed.failCeilingLow(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_mid", ed.failCeilingMid(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_high", ed.failCeilingHigh(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_repulsive", ed.failCeilingRepulsive(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_conversion", ed.failCeilingConversion(), 0.0, 1.0),
            readDouble(econ, "fail_ceiling_socket", ed.failCeilingSocket(), 0.0, 1.0),
            readDouble(econ, "stabilizer_reduction_repulsive", ed.stabilizerReductionRepulsive(), 0.0, 1.0),
            readDouble(econ, "stabilizer_reduction_normal", ed.stabilizerReductionNormal(), 0.0, 1.0),
            readInt(econ, "max_repulsive_stabilizers", ed.maxRepulsiveStabilizers(), 0, 8),
            readInt(econ, "max_normal_stabilizers", ed.maxNormalStabilizers(), 0, 8),
            readInt(econ, "max_low_stabilizers", ed.maxLowStabilizers(), 0, 8),
            readDouble(econ, "normal_fail_fee_ratio", ed.normalFailFeeRatio(), 0.0, 1.0),
            readInt(econ, "repulsive_base_xp", ed.repulsiveBaseXp(), 0, 100),
            readInt(econ, "repulsive_max_bonus_xp", ed.repulsiveMaxBonusXp(), 0, 100),
            readInt(econ, "repulsive_max_pair_reagents", ed.repulsiveMaxPairReagents(), 0, 16),
            readInt(econ, "repulsive_default_reagents", ed.repulsiveDefaultReagents(), 0, 16),
            readInt(econ, "high_base_xp", ed.highBaseXp(), 0, 100),
            readInt(econ, "high_max_xp", ed.highMaxXp(), 0, 100),
            readInt(econ, "high_reagents", ed.highReagents(), 0, 16),
            readInt(econ, "mid_base_xp", ed.midBaseXp(), 0, 100),
            readInt(econ, "mid_min_xp", ed.midMinXp(), 0, 100),
            readInt(econ, "mid_max_xp", ed.midMaxXp(), 0, 100),
            readInt(econ, "mid_max_reagents", ed.midMaxReagents(), 0, 16),
            readInt(econ, "low_base_xp", ed.lowBaseXp(), 0, 100),
            readInt(econ, "low_min_xp", ed.lowMinXp(), 0, 100),
            readInt(econ, "low_max_reagents", ed.lowMaxReagents(), 0, 16),
            readInt(econ, "socket_book_xp", ed.socketBookXp(), 0, 100),
            readInt(econ, "top_up_xp", ed.topUpXp(), 0, 100),
            readInt(econ, "expand_vessel_xp", ed.expandVesselXp(), 0, 100),
            readInt(econ, "manage_flip_split_xp", ed.manageFlipSplitXp(), 0, 100),
            readInt(econ, "purify_aberrant_xp", ed.purifyAberrantXp(), 0, 100),
            readInt(econ, "purify_aberrant_stabilizers", ed.purifyAberrantStabilizers(), 0, 16),
            minCharge,
            fullCharge,
            cds
        );

        return new ModRuntimeConfig.Snapshot(
            readBool(sys, "override_datapacks", d.overrideDatapacks()),
            readBool(sys, "frosted_ice_no_water", d.frostedIceNoWater()),
            readBool(sys, "background_ritual_enabled", d.backgroundRitualEnabled()),
            readInt(sys, "ritual_normal_ticks", d.ritualNormalTicks(), 6, 120),
            readInt(sys, "ritual_high_risk_ticks", d.ritualHighRiskTicks(), 8, 160),
            readBool(sys, "trident_fused_loyalty_return", d.tridentFusedLoyaltyReturn()),
            readBool(sys, "block_vanilla_anvil_cross_domain", d.blockVanillaAnvilCrossDomain()),
            parsedEcon,
            readStringSet(root, "disabled_signatures"),
            readStringSet(root, "disabled_transmutations")
        );
    }

    private static @Nullable JsonObject obj(@Nullable JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
            ? parent.getAsJsonObject(key)
            : null;
    }

    private static boolean readBool(@Nullable JsonObject o, String key, boolean def) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()
            ? o.get(key).getAsBoolean()
            : def;
    }

    private static int readInt(@Nullable JsonObject o, String key, int def, int lo, int hi) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()
            ? Math.clamp(o.get(key).getAsInt(), lo, hi)
            : def;
    }

    private static double readDouble(@Nullable JsonObject o, String key, double def, double lo, double hi) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()
            ? Math.clamp(o.get(key).getAsDouble(), lo, hi)
            : def;
    }

    private static Set<String> readStringSet(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement el : o.getAsJsonArray(key)) {
            if (el != null && el.isJsonPrimitive()) {
                String v = el.getAsString().trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return Set.copyOf(out);
    }
}
