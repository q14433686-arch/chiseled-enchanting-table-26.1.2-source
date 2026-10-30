package enchantment_fusion.config;

import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.model.TriggerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Declarative metadata catalog driving {@code EnchantmentFusionConfigScreen} across 4 tabs:
 * System &amp; Rituals, Economy &amp; Risk Curve (38 fields), Combat Triggers (2 charge + 8 cooldowns),
 * and Per-Entry Switches (150 Signatures + 116 Transmutations).
 */
public final class ConfigCatalog {

    public enum Category {
        SYSTEM("gui.enchantment_fusion.config.cat.system"),
        ECONOMY("gui.enchantment_fusion.config.cat.economy"),
        TRIGGERS("gui.enchantment_fusion.config.cat.triggers"),
        ENTRIES("gui.enchantment_fusion.config.cat.entries");

        private final String titleKey;

        Category(String titleKey) {
            this.titleKey = titleKey;
        }

        public String titleKey() {
            return titleKey;
        }
    }

    public enum ValueKind {
        BOOLEAN,
        INT,
        PERCENT
    }

    public record FieldSpec(
        String id,
        Category category,
        ValueKind kind,
        double min,
        double max,
        double step,
        String unitSuffix,
        Function<ModRuntimeConfig.Draft, Boolean> boolGetter,
        BiConsumer<ModRuntimeConfig.Draft, Boolean> boolSetter,
        ToIntFunction<ModRuntimeConfig.Draft> intGetter,
        ObjIntConsumer<ModRuntimeConfig.Draft> intSetter,
        ToDoubleFunction<ModRuntimeConfig.Draft> doubleGetter,
        ObjDoubleConsumer<ModRuntimeConfig.Draft> doubleSetter
    ) {
        public String labelKey() {
            return "gui.enchantment_fusion.config.field." + id;
        }

        public String descKey() {
            return "gui.enchantment_fusion.config.desc." + id;
        }

        public double normalized(ModRuntimeConfig.Draft d) {
            if (kind == ValueKind.BOOLEAN || max <= min) {
                return 0.0;
            }
            double v = kind == ValueKind.INT ? intGetter.applyAsInt(d) : doubleGetter.applyAsDouble(d);
            return Math.clamp((v - min) / (max - min), 0.0, 1.0);
        }

        public void applyNormalized(ModRuntimeConfig.Draft d, double ratio) {
            double r = Math.clamp(ratio, 0.0, 1.0);
            double raw = min + r * (max - min);
            if (step > 0.0) {
                raw = Math.round(raw / step) * step;
            }
            raw = Math.clamp(raw, min, max);
            if (kind == ValueKind.INT) {
                intSetter.accept(d, (int) Math.round(raw));
            } else if (kind == ValueKind.PERCENT) {
                double rounded = Math.round(raw * 100.0) / 100.0;
                doubleSetter.accept(d, rounded);
            }
        }

        public void resetField(ModRuntimeConfig.Draft d) {
            ModRuntimeConfig.Draft def = new ModRuntimeConfig.Draft(ModRuntimeConfig.Snapshot.DEFAULT);
            switch (kind) {
                case BOOLEAN -> boolSetter.accept(d, boolGetter.apply(def));
                case INT -> intSetter.accept(d, intGetter.applyAsInt(def));
                case PERCENT -> doubleSetter.accept(d, doubleGetter.applyAsDouble(def));
            }
        }

        public boolean isDefault(ModRuntimeConfig.Draft d) {
            ModRuntimeConfig.Draft def = new ModRuntimeConfig.Draft(ModRuntimeConfig.Snapshot.DEFAULT);
            return switch (kind) {
                case BOOLEAN -> boolGetter.apply(d).equals(boolGetter.apply(def));
                case INT -> intGetter.applyAsInt(d) == intGetter.applyAsInt(def);
                case PERCENT -> Math.abs(doubleGetter.applyAsDouble(d) - doubleGetter.applyAsDouble(def)) < 1e-4;
            };
        }

        public String formatValue(ModRuntimeConfig.Draft d) {
            return switch (kind) {
                case BOOLEAN -> "";
                case INT -> {
                    int v = intGetter.applyAsInt(d);
                    if ("t".equals(unitSuffix)) {
                        yield String.format(Locale.ROOT, "%dt (%.1fs)", v, v / 20.0);
                    }
                    yield unitSuffix.isEmpty() ? Integer.toString(v) : (v + " " + unitSuffix);
                }
                case PERCENT -> String.format(Locale.ROOT, "%d%%", Math.round(doubleGetter.applyAsDouble(d) * 100.0));
            };
        }
    }

    private static final List<FieldSpec> ALL_FIELDS = buildFields();

    private ConfigCatalog() {}

    public static List<FieldSpec> allFields() {
        return ALL_FIELDS;
    }

    public static List<FieldSpec> fieldsFor(Category category) {
        List<FieldSpec> out = new ArrayList<>();
        for (FieldSpec f : ALL_FIELDS) {
            if (f.category() == category) {
                out.add(f);
            }
        }
        return out;
    }

    private static List<FieldSpec> buildFields() {
        List<FieldSpec> list = new ArrayList<>(64);
        EconomyConfig ed = EconomyConfig.DEFAULT;

        // 1. SYSTEM & RITUAL
        addBool(list, "override_datapacks", Category.SYSTEM, d -> d.overrideDatapacks, (d, v) -> d.overrideDatapacks = v);
        addBool(list, "frosted_ice_no_water", Category.SYSTEM, d -> d.frostedIceNoWater, (d, v) -> d.frostedIceNoWater = v);
        addBool(list, "background_ritual_enabled", Category.SYSTEM, d -> d.backgroundRitualEnabled, (d, v) -> d.backgroundRitualEnabled = v);
        addInt(list, "ritual_normal_ticks", Category.SYSTEM, 6, 120, 2, "t", d -> d.ritualNormalTicks, (d, v) -> d.ritualNormalTicks = v);
        addInt(list, "ritual_high_risk_ticks", Category.SYSTEM, 8, 160, 2, "t", d -> d.ritualHighRiskTicks, (d, v) -> d.ritualHighRiskTicks = v);
        addBool(list, "trident_fused_loyalty_return", Category.SYSTEM, d -> d.tridentFusedLoyaltyReturn, (d, v) -> d.tridentFusedLoyaltyReturn = v);
        addBool(list, "block_vanilla_anvil_cross_domain", Category.SYSTEM, d -> d.blockVanillaAnvilCrossDomain, (d, v) -> d.blockVanillaAnvilCrossDomain = v);

        // 2. ECONOMY & RISK (all 38 curve fields)
        addPct(list, "low_fail_chance", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.lowFailChance, (d, v) -> d.lowFailChance = v);
        addPct(list, "mid_fail_chance", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.midFailChance, (d, v) -> d.midFailChance = v);
        addPct(list, "high_fail_chance", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.highFailChance, (d, v) -> d.highFailChance = v);
        addPct(list, "repulsive_fail_chance", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.repulsiveFailChance, (d, v) -> d.repulsiveFailChance = v);
        addPct(list, "repulsive_min_floor", Category.ECONOMY, 0.0, 0.50, 0.01, d -> d.repulsiveMinFloor, (d, v) -> d.repulsiveMinFloor = v);
        addPct(list, "aberrant_socket_fail_chance", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.aberrantSocketFailChance, (d, v) -> d.aberrantSocketFailChance = v);
        addPct(list, "fail_ceiling_low", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingLow, (d, v) -> d.failCeilingLow = v);
        addPct(list, "fail_ceiling_mid", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingMid, (d, v) -> d.failCeilingMid = v);
        addPct(list, "fail_ceiling_high", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingHigh, (d, v) -> d.failCeilingHigh = v);
        addPct(list, "fail_ceiling_repulsive", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingRepulsive, (d, v) -> d.failCeilingRepulsive = v);
        addPct(list, "fail_ceiling_conversion", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingConversion, (d, v) -> d.failCeilingConversion = v);
        addPct(list, "fail_ceiling_socket", Category.ECONOMY, 0.0, 1.0, 0.01, d -> d.failCeilingSocket, (d, v) -> d.failCeilingSocket = v);
        addPct(list, "stabilizer_reduction_repulsive", Category.ECONOMY, 0.0, 0.50, 0.01, d -> d.stabilizerReductionRepulsive, (d, v) -> d.stabilizerReductionRepulsive = v);
        addPct(list, "stabilizer_reduction_normal", Category.ECONOMY, 0.0, 0.50, 0.01, d -> d.stabilizerReductionNormal, (d, v) -> d.stabilizerReductionNormal = v);
        addInt(list, "max_repulsive_stabilizers", Category.ECONOMY, 0, 8, 1, "", d -> d.maxRepulsiveStabilizers, (d, v) -> d.maxRepulsiveStabilizers = v);
        addInt(list, "max_normal_stabilizers", Category.ECONOMY, 0, 8, 1, "", d -> d.maxNormalStabilizers, (d, v) -> d.maxNormalStabilizers = v);
        addInt(list, "max_low_stabilizers", Category.ECONOMY, 0, 8, 1, "", d -> d.maxLowStabilizers, (d, v) -> d.maxLowStabilizers = v);
        addPct(list, "normal_fail_fee_ratio", Category.ECONOMY, 0.0, 1.0, 0.05, d -> d.normalFailFeeRatio, (d, v) -> d.normalFailFeeRatio = v);
        addInt(list, "repulsive_base_xp", Category.ECONOMY, 0, 50, 1, "Lv", d -> d.repulsiveBaseXp, (d, v) -> d.repulsiveBaseXp = v);
        addInt(list, "repulsive_max_bonus_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.repulsiveMaxBonusXp, (d, v) -> d.repulsiveMaxBonusXp = v);
        addInt(list, "repulsive_max_pair_reagents", Category.ECONOMY, 0, 8, 1, "", d -> d.repulsiveMaxPairReagents, (d, v) -> d.repulsiveMaxPairReagents = v);
        addInt(list, "repulsive_default_reagents", Category.ECONOMY, 0, 8, 1, "", d -> d.repulsiveDefaultReagents, (d, v) -> d.repulsiveDefaultReagents = v);
        addInt(list, "high_base_xp", Category.ECONOMY, 0, 40, 1, "Lv", d -> d.highBaseXp, (d, v) -> d.highBaseXp = v);
        addInt(list, "high_max_xp", Category.ECONOMY, 0, 60, 1, "Lv", d -> d.highMaxXp, (d, v) -> d.highMaxXp = v);
        addInt(list, "high_reagents", Category.ECONOMY, 0, 8, 1, "", d -> d.highReagents, (d, v) -> d.highReagents = v);
        addInt(list, "mid_base_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.midBaseXp, (d, v) -> d.midBaseXp = v);
        addInt(list, "mid_min_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.midMinXp, (d, v) -> d.midMinXp = v);
        addInt(list, "mid_max_xp", Category.ECONOMY, 0, 50, 1, "Lv", d -> d.midMaxXp, (d, v) -> d.midMaxXp = v);
        addInt(list, "mid_max_reagents", Category.ECONOMY, 0, 8, 1, "", d -> d.midMaxReagents, (d, v) -> d.midMaxReagents = v);
        addInt(list, "low_base_xp", Category.ECONOMY, 0, 20, 1, "Lv", d -> d.lowBaseXp, (d, v) -> d.lowBaseXp = v);
        addInt(list, "low_min_xp", Category.ECONOMY, 0, 20, 1, "Lv", d -> d.lowMinXp, (d, v) -> d.lowMinXp = v);
        addInt(list, "low_max_reagents", Category.ECONOMY, 0, 8, 1, "", d -> d.lowMaxReagents, (d, v) -> d.lowMaxReagents = v);
        addInt(list, "socket_book_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.socketBookXp, (d, v) -> d.socketBookXp = v);
        addInt(list, "top_up_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.topUpXp, (d, v) -> d.topUpXp = v);
        addInt(list, "expand_vessel_xp", Category.ECONOMY, 0, 40, 1, "Lv", d -> d.expandVesselXp, (d, v) -> d.expandVesselXp = v);
        addInt(list, "manage_flip_split_xp", Category.ECONOMY, 0, 30, 1, "Lv", d -> d.manageFlipSplitXp, (d, v) -> d.manageFlipSplitXp = v);
        addInt(list, "purify_aberrant_xp", Category.ECONOMY, 0, 80, 1, "Lv", d -> d.purifyAberrantXp, (d, v) -> d.purifyAberrantXp = v);
        addInt(list, "purify_aberrant_stabilizers", Category.ECONOMY, 0, 8, 1, "", d -> d.purifyAberrantStabilizers, (d, v) -> d.purifyAberrantStabilizers = v);

        // 3. COMBAT TRIGGERS & CHARGE
        addPct(list, "melee_min_charge", Category.TRIGGERS, 0.0, 1.0, 0.05, d -> d.meleeMinCharge, (d, v) -> d.meleeMinCharge = v);
        addPct(list, "melee_full_charge", Category.TRIGGERS, 0.1, 1.0, 0.05, d -> d.meleeFullCharge, (d, v) -> d.meleeFullCharge = v);
        for (TriggerType t : TriggerType.values()) {
            String key = "cd_" + t.name().toLowerCase(Locale.ROOT);
            int maxCd = Math.max(200, ed.cooldownTicksFor(t) * 3);
            addInt(
                list,
                key,
                Category.TRIGGERS,
                4,
                maxCd,
                2,
                "t",
                d -> d.triggerCooldowns.getOrDefault(t, t.cooldownTicks()),
                (d, v) -> d.triggerCooldowns.put(t, v)
            );
        }
        return Collections.unmodifiableList(list);
    }

    private static void addBool(
        List<FieldSpec> list,
        String id,
        Category cat,
        Function<ModRuntimeConfig.Draft, Boolean> g,
        BiConsumer<ModRuntimeConfig.Draft, Boolean> s
    ) {
        list.add(new FieldSpec(id, cat, ValueKind.BOOLEAN, 0, 1, 1, "", g, s, d -> 0, (d, v) -> {}, d -> 0.0, (d, v) -> {}));
    }

    private static void addInt(
        List<FieldSpec> list,
        String id,
        Category cat,
        int min,
        int max,
        int step,
        String unit,
        ToIntFunction<ModRuntimeConfig.Draft> g,
        ObjIntConsumer<ModRuntimeConfig.Draft> s
    ) {
        list.add(new FieldSpec(id, cat, ValueKind.INT, min, max, step, unit, d -> false, (d, v) -> {}, g, s, d -> 0.0, (d, v) -> {}));
    }

    private static void addPct(
        List<FieldSpec> list,
        String id,
        Category cat,
        double min,
        double max,
        double step,
        ToDoubleFunction<ModRuntimeConfig.Draft> g,
        ObjDoubleConsumer<ModRuntimeConfig.Draft> s
    ) {
        list.add(new FieldSpec(id, cat, ValueKind.PERCENT, min, max, step, "%", d -> false, (d, v) -> {}, d -> 0, (d, v) -> {}, g, s));
    }
}
