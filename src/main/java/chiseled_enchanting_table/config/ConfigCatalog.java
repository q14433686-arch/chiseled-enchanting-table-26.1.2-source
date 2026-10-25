package chiseled_enchanting_table.config;

import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Declarative schema &amp; multi-dimensional applicability engine for all in-game configurable
 * options across {@link ModRuntimeConfig}.
 *
 * <p>Evaluates four distinct applicability layers for every option:</p>
 * <ol>
 *   <li><b>Session / Authority mode</b> ({@link SessionMode}): offline local file, live singleplayer,
 *       authenticated dedicated-server admin, or read-only server inspector;</li>
 *   <li><b>Parent -> Child conditional activation</b> ({@link OptionEntry#activeWhen}): dims &amp;
 *       locks dependent options with a concrete localized reason whenever their prerequisite mode
 *       or toggle makes them inapplicable;</li>
 *   <li><b>Cross-field coherence</b> ({@link Draft#normalizeAfterEdit}): preserves valid min/max
 *       ladders and bounded scan volumes live during editing;</li>
 *   <li><b>Effect timing scope</b> ({@link EffectScope}): distinguishes immediate live updates from
 *       new-chunk/chest generation rules and bootstrap-only restart settings.</li>
 * </ol>
 */
public final class ConfigCatalog {
    private ConfigCatalog() {}

    public enum CategoryId {
        GENERAL("general", 0xFF7A52B3),
        BOOKSHELVES("bookshelves", 0xFF287385),
        TOMES_ECONOMY("tomes_economy", 0xFF8C6220),
        EPIPHANY("epiphany", 0xFF6B3FA0),
        CLEANSE_TRANSMUTE("cleanse_transmute", 0xFF237A5B),
        WORLD_LOOT("world_loot", 0xFF87443A);

        private final String id;
        private final int accentColor;

        CategoryId(String id, int accentColor) {
            this.id = id;
            this.accentColor = accentColor;
        }

        public String id() {
            return id;
        }

        public int accentColor() {
            return accentColor;
        }

        public String titleKey() {
            return "config.chiseled_enchanting_table.cat." + id;
        }

        public String subtitleKey() {
            return "config.chiseled_enchanting_table.cat." + id + ".sub";
        }
    }

    public enum EffectScope {
        IMMEDIATE("config.chiseled_enchanting_table.scope.immediate", 0xFF56E39F),
        NEW_GENERATION("config.chiseled_enchanting_table.scope.new_gen", 0xFF6CD4FF),
        RESTART_REQUIRED("config.chiseled_enchanting_table.scope.restart", 0xFFFFBA52);

        private final String badgeKey;
        private final int color;

        EffectScope(String badgeKey, int color) {
            this.badgeKey = badgeKey;
            this.color = color;
        }

        public String badgeKey() {
            return badgeKey;
        }

        public int color() {
            return color;
        }
    }

    public enum SessionMode {
        OFFLINE_CLIENT("config.chiseled_enchanting_table.session.offline", true),
        SINGLEPLAYER_LIVE("config.chiseled_enchanting_table.session.singleplayer", true),
        DEDICATED_ADMIN("config.chiseled_enchanting_table.session.dedicated_admin", true),
        DEDICATED_READONLY("config.chiseled_enchanting_table.session.dedicated_readonly", false),
        MODPACK_LOCKED("config.chiseled_enchanting_table.session.modpack_locked", false);

        private final String bannerKey;
        private final boolean writable;

        SessionMode(String bannerKey, boolean writable) {
            this.bannerKey = bannerKey;
            this.writable = writable;
        }

        public String bannerKey() {
            return bannerKey;
        }

        public boolean writable() {
            return writable;
        }
    }

    public enum ControlKind {
        BOOLEAN,
        INT_SLIDER,
        DOUBLE_SLIDER,
        ENUM_CYCLE
    }

    public record OptionAvailability(
        boolean editable,
        boolean parentApplicable,
        @Nullable String inactiveReasonKey,
        EffectScope effectScope,
        boolean modifiedFromDefault
    ) {}

    public static final class OptionEntry {
        private final String key;
        private final CategoryId category;
        private final ControlKind kind;
        private final EffectScope effectScope;
        private final double min;
        private final double max;
        private final double step;
        private final Function<Draft, Object> getter;
        private final BiConsumer<Draft, Object> setter;
        private final Function<Draft, String> valueLabeler;
        private final BiConsumer<Draft, Integer> enumCycler;
        private final Function<Draft, Double> sliderNormalizedGetter;
        private final BiConsumer<Draft, Double> sliderNormalizedSetter;
        private final Predicate<Draft> activeWhen;
        @Nullable
        private final Function<Draft, String> dynamicInactiveReasonKey;

        private OptionEntry(
            String key,
            CategoryId category,
            ControlKind kind,
            EffectScope effectScope,
            double min,
            double max,
            double step,
            Function<Draft, Object> getter,
            BiConsumer<Draft, Object> setter,
            Function<Draft, String> valueLabeler,
            BiConsumer<Draft, Integer> enumCycler,
            Function<Draft, Double> sliderNormalizedGetter,
            BiConsumer<Draft, Double> sliderNormalizedSetter,
            Predicate<Draft> activeWhen,
            @Nullable Function<Draft, String> dynamicInactiveReasonKey
        ) {
            this.key = key;
            this.category = category;
            this.kind = kind;
            this.effectScope = effectScope;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.valueLabeler = valueLabeler;
            this.enumCycler = enumCycler;
            this.sliderNormalizedGetter = sliderNormalizedGetter;
            this.sliderNormalizedSetter = sliderNormalizedSetter;
            this.activeWhen = activeWhen;
            this.dynamicInactiveReasonKey = dynamicInactiveReasonKey;
        }

        public String key() {
            return key;
        }

        public CategoryId category() {
            return category;
        }

        public ControlKind kind() {
            return kind;
        }

        public EffectScope effectScope() {
            return effectScope;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        public double step() {
            return step;
        }

        public String nameKey() {
            return "config.chiseled_enchanting_table.opt." + key;
        }

        public String descKey() {
            return "config.chiseled_enchanting_table.opt." + key + ".desc";
        }

        public Object get(Draft d) {
            return getter.apply(d);
        }

        /** Resolver-only assignment; normalize all related fields once after the complete overlay. */
        public void setResolvedValue(Draft d, Object v) { setter.accept(d, v); }

        public void set(Draft d, Object v) {
            setter.accept(d, v);
            d.normalizeAfterEdit(key);
        }

        public void toggleOrCycle(Draft d, int direction) {
            if (kind == ControlKind.BOOLEAN) {
                boolean cur = Boolean.TRUE.equals(getter.apply(d));
                set(d, !cur);
            } else if (kind == ControlKind.ENUM_CYCLE) {
                enumCycler.accept(d, direction);
                d.normalizeAfterEdit(key);
            }
        }

        public double normalizedSliderValue(Draft d) {
            return Math.max(0.0, Math.min(1.0, sliderNormalizedGetter.apply(d)));
        }

        public void setFromNormalizedSlider(Draft d, double t) {
            double clamped = Math.max(0.0, Math.min(1.0, t));
            sliderNormalizedSetter.accept(d, clamped);
            d.normalizeAfterEdit(key);
        }

        public void stepValue(Draft d, int steps) {
            if (kind == ControlKind.INT_SLIDER) {
                int cur = ((Number) getter.apply(d)).intValue();
                int next = (int) Math.max(min, Math.min(max, cur + steps * Math.max(1, (int) Math.round(step))));
                set(d, next);
            } else if (kind == ControlKind.DOUBLE_SLIDER) {
                double cur = ((Number) getter.apply(d)).doubleValue();
                double next = Math.max(min, Math.min(max, Math.round((cur + steps * step) * 100.0) / 100.0));
                set(d, next);
            } else {
                toggleOrCycle(d, steps >= 0 ? 1 : -1);
            }
        }

        /**
         * Returns either raw display text or a translation key prefixed with {@code "@"} so pure-Java
         * callers and I18n UI callers share the exact same formatting rules.
         */
        public String rawOrKeyDisplay(Draft d) {
            return valueLabeler.apply(d);
        }

        public boolean isModifiedFromDefault(Draft d) {
            Draft def = new Draft();
            return !Objects.equals(getter.apply(d), getter.apply(def));
        }

        public void resetToDefault(Draft d) {
            Draft def = new Draft();
            setter.accept(d, getter.apply(def));
            d.customizedKeys.remove(key);
            d.normalizeAfterEdit(null);
        }

        public OptionAvailability evaluate(Draft draft, SessionMode sessionMode) {
            ModRuntimeConfig.PackPolicy policy = ModRuntimeConfig.effectivePackPolicy();
            boolean parentOk = activeWhen.test(draft);
            if (draft.classic103Mode && CLASSIC_LOCKED_KEYS.contains(key)) {
                // 1.0.3 Classic linkage: rule-owned values (CLASSIC_OVERRIDDEN_KEYS)
                // are ignored by the classic frame, so they stay locked even on
                // the Classic merged page; the rest are live preferences that the
                // merged page unlocks (reason.classic_1_0_3).
                boolean ruleOwned = CLASSIC_OVERRIDDEN_KEYS.contains(key);
                return new OptionAvailability(
                    false,
                    false,
                    ruleOwned
                        ? "config.chiseled_enchanting_table.reason.classic_1_0_3_overridden"
                        : "config.chiseled_enchanting_table.reason.classic_1_0_3",
                    effectScope,
                    isModifiedFromDefault(draft)
                );
            }
            if (!sessionMode.writable()) {
                String roReason = sessionMode == SessionMode.MODPACK_LOCKED
                    ? "config.chiseled_enchanting_table.reason.pack_locked"
                    : "config.chiseled_enchanting_table.reason.server_readonly";
                return new OptionAvailability(
                    false,
                    parentOk,
                    roReason,
                    effectScope,
                    isModifiedFromDefault(draft)
                );
            }
            if (policy.isKeyLocked(key)) {
                return new OptionAvailability(
                    false,
                    parentOk,
                    "config.chiseled_enchanting_table.reason.pack_locked",
                    effectScope,
                    isModifiedFromDefault(draft)
                );
            }
            if (!"override_datapacks".equals(key)
                && policy.datapackOverriddenKeys().contains(key)
                && (!draft.overrideDatapacks || policy.enforceDatapackPriority())) {
                return new OptionAvailability(
                    false,
                    parentOk,
                    "config.chiseled_enchanting_table.reason.datapack_priority",
                    effectScope,
                    isModifiedFromDefault(draft)
                );
            }
            String reason = !parentOk && dynamicInactiveReasonKey != null
                ? dynamicInactiveReasonKey.apply(draft)
                : null;
            return new OptionAvailability(
                parentOk,
                parentOk,
                reason,
                effectScope,
                isModifiedFromDefault(draft)
            );
        }
    }

    /**
     * Option keys whose effective values are owned by the 1.0.3 Classic master
     * switch (forced in {@code ModRuntimeConfig} compact constructor / Draft#build)
     * or which have no meaning while it is on (Epiphany family). Editing them in
     * classic would be a no-op, so {@link OptionEntry#evaluate} locks them with
     * an explicit reason; leaving classic restores editability and the stored
     * preference values are still in the draft.
     */
    public static final java.util.Set<String> CLASSIC_LOCKED_KEYS = java.util.Set.of(
        "enchanted_book_stack_size", "vanilla_table_mode", "anvil_flat_repair_cost",
        "anvil_block_same_book_merge", "restrict_villager_infinity_books",
        "grindstone_depleted_tome", "direct_gates_enabled", "blank_max_tier_mode",
        "horizontal_scan_radius", "vertical_scan_radius", "require_breathing_room",
        "require_line_of_sight", "tome_consumption_mode", "tome_regen_enabled",
        "locked_shelf_breakable", "locked_book_extraction_blocked",
        "max_wooden_discount_percent", "max_total_xp_discount_percent",
        "book_combine_enabled", "book_combine_xp_multiplier",
        "book_research_enabled", "book_research_xp_multiplier",
        "epiphany_min_bookshelves", "epiphany_base_chance", "epiphany_max_chance",
        "epiphany_max_slots_cap", "epiphany_cycle_seconds", "epiphany_freeze_xp_levels",
        "epiphany_freeze_duration_seconds", "epiphany_max_freeze_seconds",
        "epiphany_reroll_xp_levels",
        "disenchant_enabled", "allow_curse_purge", "require_prior_discovery",
        "transformation_max_batch_size", "bookshelf_enchanted_prob",
        "tier1_bookshelves", "tier2_bookshelves", "tier3_qualifying_enchantments",
        "early_unlock_xp_levels", "early_unlock_grit_count", "sacrifice_required",
        "tome_page_required_shelves", "max_tome_uses", "book_copy_wear_cost",
        "tome_deplete_behavior", "tome_regen_base_seconds", "tome_regen_tier1_shelves",
        "tome_regen_tier1_seconds", "tome_regen_tier2_shelves", "tome_regen_tier2_seconds",
        "xp_refund_scale", "external_xp_refund_scale", "strip_xp_min", "strip_xp_max",
        "curse_purge_xp_cost"
    );

    /**
     * Subset of {@link #CLASSIC_LOCKED_KEYS} whose stored value has NO effect
     * while classic is on: the runtime frame zeroes them (Epiphany chances,
     * resonance discount caps) or takes the price from the classic tables
     * (combine/research XP multipliers), and the disenchant/cleanse and
     * tome-page-sacrifice mechanisms are hard-off. The Classic merged page
     * shows these greyed out with {@code reason.classic_1_0_3_overridden}
     * instead of pretending they are live preferences (audit round 4, #16).
     */
    public static final java.util.Set<String> CLASSIC_OVERRIDDEN_KEYS = java.util.Set.of(
        "max_wooden_discount_percent", "max_total_xp_discount_percent",
        "book_combine_xp_multiplier", "book_research_xp_multiplier",
        "epiphany_min_bookshelves", "epiphany_base_chance", "epiphany_max_chance",
        "epiphany_max_slots_cap", "epiphany_cycle_seconds", "epiphany_freeze_xp_levels",
        "epiphany_freeze_duration_seconds", "epiphany_max_freeze_seconds",
        "epiphany_reroll_xp_levels",
        "disenchant_enabled", "allow_curse_purge",
        "xp_refund_scale", "external_xp_refund_scale", "strip_xp_min", "strip_xp_max",
        "curse_purge_xp_cost", "sacrifice_required", "tome_page_required_shelves"
    );

    // ---- Concise builders ----

    private static final Predicate<Draft> ALWAYS = d -> true;

    private static OptionEntry boolOpt(
        String key,
        CategoryId cat,
        EffectScope scope,
        Function<Draft, Boolean> get,
        BiConsumer<Draft, Boolean> set,
        Predicate<Draft> activeWhen,
        @Nullable String reasonKey
    ) {
        return new OptionEntry(
            key, cat, ControlKind.BOOLEAN, scope, 0, 1, 1,
            get::apply,
            (d, v) -> set.accept(d, (Boolean) v),
            d -> Boolean.TRUE.equals(get.apply(d))
                ? "@config.chiseled_enchanting_table.val.on"
                : "@config.chiseled_enchanting_table.val.off",
            (d, dir) -> set.accept(d, !Boolean.TRUE.equals(get.apply(d))),
            d -> Boolean.TRUE.equals(get.apply(d)) ? 1.0 : 0.0,
            (d, t) -> set.accept(d, t >= 0.5),
            activeWhen,
            reasonKey == null ? null : d -> reasonKey
        );
    }

    private static OptionEntry intOpt(
        String key,
        CategoryId cat,
        EffectScope scope,
        int min,
        int max,
        int step,
        Function<Draft, Integer> get,
        BiConsumer<Draft, Integer> set,
        Function<Draft, String> fmt,
        Predicate<Draft> activeWhen,
        @Nullable Function<Draft, String> reasonFn
    ) {
        return new OptionEntry(
            key, cat, ControlKind.INT_SLIDER, scope, min, max, step,
            get::apply,
            (d, v) -> set.accept(d, ((Number) v).intValue()),
            fmt,
            (d, dir) -> {},
            d -> max <= min ? 0.0 : (get.apply(d) - min) / (double) (max - min),
            (d, t) -> {
                int raw = (int) Math.round(min + t * (max - min));
                if (step > 1) {
                    raw = min + Math.round((raw - min) / (float) step) * step;
                }
                set.accept(d, Math.max(min, Math.min(max, raw)));
            },
            activeWhen,
            reasonFn
        );
    }

    private static OptionEntry doubleOpt(
        String key,
        CategoryId cat,
        EffectScope scope,
        double min,
        double max,
        double step,
        Function<Draft, Double> get,
        BiConsumer<Draft, Double> set,
        Function<Draft, String> fmt,
        Predicate<Draft> activeWhen,
        @Nullable Function<Draft, String> reasonFn
    ) {
        return new OptionEntry(
            key, cat, ControlKind.DOUBLE_SLIDER, scope, min, max, step,
            get::apply,
            (d, v) -> set.accept(d, ((Number) v).doubleValue()),
            fmt,
            (d, dir) -> {},
            d -> max <= min ? 0.0 : (get.apply(d) - min) / (max - min),
            (d, t) -> {
                double raw = min + t * (max - min);
                double snapped = Math.round(raw / step) * step;
                set.accept(d, Math.round(Math.max(min, Math.min(max, snapped)) * 100.0) / 100.0);
            },
            activeWhen,
            reasonFn
        );
    }

    private static <E extends Enum<E>> OptionEntry enumOpt(
        String key,
        CategoryId cat,
        EffectScope scope,
        E[] values,
        Function<Draft, E> get,
        BiConsumer<Draft, E> set,
        Function<E, String> tokenFn,
        Predicate<Draft> activeWhen,
        @Nullable String reasonKey
    ) {
        return new OptionEntry(
            key, cat, ControlKind.ENUM_CYCLE, scope, 0, values.length - 1, 1,
            get::apply,
            (d, v) -> {
                @SuppressWarnings("unchecked")
                E cast = (E) v;
                set.accept(d, cast);
            },
            d -> "@config.chiseled_enchanting_table.enum." + key + "." + tokenFn.apply(get.apply(d)),
            (d, dir) -> {
                E cur = get.apply(d);
                int idx = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == cur) {
                        idx = i;
                        break;
                    }
                }
                int next = Math.floorMod(idx + dir, values.length);
                set.accept(d, values[next]);
            },
            d -> 0.0,
            (d, t) -> {},
            activeWhen,
            reasonKey == null ? null : d -> reasonKey
        );
    }

    public static final List<OptionEntry> ALL_OPTIONS = List.of(
        // ==================== 1. GENERAL ====================
        boolOpt(
            "override_datapacks", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.overrideDatapacks, (d, v) -> d.overrideDatapacks = v,
            ALWAYS, null
        ),
        intOpt(
            "enchanted_book_stack_size", CategoryId.GENERAL, EffectScope.RESTART_REQUIRED, 1, 64, 1,
            d -> d.enchantedBookStackSize, (d, v) -> d.enchantedBookStackSize = v,
            d -> d.enchantedBookStackSize + "",
            ALWAYS, null
        ),
        enumOpt(
            "vanilla_table_mode", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            VanillaTableMode.values(),
            d -> d.vanillaTableMode, (d, v) -> d.vanillaTableMode = v, VanillaTableMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "anvil_flat_repair_cost", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.anvilFlatRepairCost, (d, v) -> d.anvilFlatRepairCost = v,
            ALWAYS, null
        ),
        boolOpt(
            "anvil_block_same_book_merge", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.anvilBlockSameBookMerge, (d, v) -> d.anvilBlockSameBookMerge = v,
            ALWAYS, null
        ),
        boolOpt(
            "restrict_villager_infinity_books", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.restrictVillagerInfinityBooks, (d, v) -> d.restrictVillagerInfinityBooks = v,
            ALWAYS, null
        ),
        boolOpt(
            "grindstone_depleted_tome", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.grindstoneDepletedTome, (d, v) -> d.grindstoneDepletedTome = v,
            ALWAYS, null
        ),
        boolOpt(
            "classic_1_0_3_mode", CategoryId.GENERAL, EffectScope.IMMEDIATE,
            d -> d.classic103Mode, (d, v) -> d.classic103Mode = v,
            ALWAYS, null
        ),

        // ==================== 2. BOOKSHELVES ====================
        boolOpt(
            "direct_gates_enabled", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE,
            d -> d.directGatesEnabled, (d, v) -> d.directGatesEnabled = v,
            ALWAYS, null
        ),
        enumOpt(
            "blank_max_tier_mode", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE,
            BlankMaxTierMode.values(),
            d -> d.blankMaxTierMode, (d, v) -> d.blankMaxTierMode = v, BlankMaxTierMode::token,
            d -> d.directGatesEnabled, "config.chiseled_enchanting_table.reason.need_direct_gates"
        ),
        intOpt(
            "max_direct_bookshelves", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE, 4, 64, 1,
            d -> d.maxDirectBookshelves, (d, v) -> d.maxDirectBookshelves = v,
            d -> d.maxDirectBookshelves + "",
            d -> d.directGatesEnabled, d -> "config.chiseled_enchanting_table.reason.need_direct_gates"
        ),
        intOpt(
            "horizontal_scan_radius", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE, 1, 8, 1,
            d -> d.horizontalScanRadius, (d, v) -> d.horizontalScanRadius = v,
            d -> {
                int w = 2 * d.horizontalScanRadius + 1;
                int h = 2 * d.verticalScanRadius + 1;
                return d.horizontalScanRadius + " (" + w + "\u00d7" + h + "\u00d7" + w + ")";
            },
            ALWAYS, null
        ),
        intOpt(
            "vertical_scan_radius", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE, 0, 6, 1,
            d -> d.verticalScanRadius, (d, v) -> d.verticalScanRadius = v,
            d -> d.verticalScanRadius + " (" + BookshelfProgression.scanVolumeBlocks(d.horizontalScanRadius, d.verticalScanRadius) + " blk)",
            ALWAYS, null
        ),
        boolOpt(
            "require_breathing_room", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE,
            d -> d.requireBreathingRoom, (d, v) -> d.requireBreathingRoom = v,
            ALWAYS, null
        ),
        intOpt(
            "min_open_adjacent_blocks", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE, 1, 17, 1,
            d -> d.minOpenAdjacentBlocks, (d, v) -> d.minOpenAdjacentBlocks = v,
            d -> d.minOpenAdjacentBlocks + " / 17",
            d -> d.requireBreathingRoom, d -> "config.chiseled_enchanting_table.reason.need_breathing_room"
        ),
        boolOpt(
            "require_line_of_sight", CategoryId.BOOKSHELVES, EffectScope.IMMEDIATE,
            d -> d.requireLineOfSight, (d, v) -> d.requireLineOfSight = v,
            ALWAYS, null
        ),

        // ==================== 3. TOMES & ECONOMY ====================
        enumOpt(
            "tome_consumption_mode", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            MasterTomeWear.ConsumptionMode.values(),
            d -> d.tomeConsumptionMode, (d, v) -> d.tomeConsumptionMode = v, MasterTomeWear.ConsumptionMode::token,
            ALWAYS, null
        ),
        intOpt(
            "max_tome_uses", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 1, 256, 1,
            d -> d.maxTomeUses, (d, v) -> d.maxTomeUses = v,
            d -> d.maxTomeUses + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        intOpt(
            "book_copy_wear_cost", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 1, 64, 1,
            d -> d.bookCopyWearCost, (d, v) -> d.bookCopyWearCost = v,
            d -> d.bookCopyWearCost + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        enumOpt(
            "tome_deplete_behavior", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            MasterTomeWear.DepleteBehavior.values(),
            d -> d.tomeDepleteBehavior, (d, v) -> d.tomeDepleteBehavior = v, MasterTomeWear.DepleteBehavior::token,
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        boolOpt(
            "tome_regen_enabled", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            d -> d.tomeRegenEnabled, (d, v) -> d.tomeRegenEnabled = v,
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        intOpt(
            "tome_regen_base_seconds", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 60, 72_000, 600,
            d -> d.tomeRegenBaseSeconds, (d, v) -> d.tomeRegenBaseSeconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenBaseSeconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        boolOpt(
            "locked_shelf_breakable", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            d -> d.lockedShelfBreakable, (d, v) -> d.lockedShelfBreakable = v,
            ALWAYS, null
        ),
        boolOpt(
            "locked_book_extraction_blocked", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            d -> d.lockedBookExtractionBlocked, (d, v) -> d.lockedBookExtractionBlocked = v,
            ALWAYS, null
        ),
        intOpt(
            "early_unlock_xp_levels", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0, 512, 1,
            d -> d.earlyUnlockXpLevels, (d, v) -> d.earlyUnlockXpLevels = v,
            d -> d.earlyUnlockXpLevels + "",
            ALWAYS, null
        ),
        intOpt(
            "early_unlock_grit_count", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0, 512, 1,
            d -> d.earlyUnlockGritCount, (d, v) -> d.earlyUnlockGritCount = v,
            d -> d.earlyUnlockGritCount + "",
            ALWAYS, null
        ),
        intOpt(
            "sacrifice_required", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 1, 1024, 1,
            d -> d.sacrificeRequired, (d, v) -> d.sacrificeRequired = v,
            d -> d.sacrificeRequired + "",
            ALWAYS, null
        ),
        intOpt(
            "tome_page_required_shelves", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 1, 512, 1,
            d -> d.tomePageRequiredShelves, (d, v) -> d.tomePageRequiredShelves = v,
            d -> d.tomePageRequiredShelves + "",
            ALWAYS, null
        ),
        intOpt(
            "tome_regen_tier1_shelves", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 4, 96, 1,
            d -> d.tomeRegenTier1Shelves, (d, v) -> d.tomeRegenTier1Shelves = v,
            d -> d.tomeRegenTier1Shelves + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier1_seconds", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 60, 48_000, 600,
            d -> d.tomeRegenTier1Seconds, (d, v) -> d.tomeRegenTier1Seconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenTier1Seconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier2_shelves", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 4, 96, 1,
            d -> d.tomeRegenTier2Shelves, (d, v) -> d.tomeRegenTier2Shelves = v,
            d -> d.tomeRegenTier2Shelves + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier2_seconds", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 60, 24_000, 300,
            d -> d.tomeRegenTier2Seconds, (d, v) -> d.tomeRegenTier2Seconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenTier2Seconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "max_wooden_discount_percent", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0, 90, 1,
            d -> d.maxWoodenDiscountPercent, (d, v) -> d.maxWoodenDiscountPercent = v,
            d -> "-" + d.maxWoodenDiscountPercent + "%",
            ALWAYS, null
        ),
        intOpt(
            "max_total_xp_discount_percent", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0, 90, 1,
            d -> d.maxTotalXpDiscountPercent, (d, v) -> d.maxTotalXpDiscountPercent = v,
            d -> "-" + d.maxTotalXpDiscountPercent + "%",
            ALWAYS, null
        ),
        boolOpt(
            "book_combine_enabled", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            d -> d.bookCombineEnabled, (d, v) -> d.bookCombineEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "book_combine_xp_multiplier", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0.10, 3.00, 0.05,
            d -> d.bookCombineXpMultiplier, (d, v) -> d.bookCombineXpMultiplier = v,
            d -> String.format(java.util.Locale.ROOT, "%.2f\u00d7", d.bookCombineXpMultiplier),
            d -> d.bookCombineEnabled, d -> "config.chiseled_enchanting_table.reason.need_book_combine"
        ),
        boolOpt(
            "book_research_enabled", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE,
            d -> d.bookResearchEnabled, (d, v) -> d.bookResearchEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "book_research_xp_multiplier", CategoryId.TOMES_ECONOMY, EffectScope.IMMEDIATE, 0.10, 3.00, 0.05,
            d -> d.bookResearchXpMultiplier, (d, v) -> d.bookResearchXpMultiplier = v,
            d -> String.format(java.util.Locale.ROOT, "%.2f\u00d7", d.bookResearchXpMultiplier),
            d -> d.bookResearchEnabled, d -> "config.chiseled_enchanting_table.reason.need_book_research"
        ),

        // ==================== 4. TREASURE EPIPHANY ====================
        intOpt(
            "epiphany_min_bookshelves", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.epiphanyMinBookshelves, (d, v) -> d.epiphanyMinBookshelves = v,
            d -> d.epiphanyMinBookshelves + "",
            ALWAYS, null
        ),
        doubleOpt(
            "epiphany_base_chance", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 0.00, 1.00, 0.02,
            d -> d.epiphanyBaseChance, (d, v) -> d.epiphanyBaseChance = v,
            d -> Math.round(d.epiphanyBaseChance * 100) + "%",
            ALWAYS, null
        ),
        doubleOpt(
            "epiphany_max_chance", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 0.00, 1.00, 0.02,
            d -> d.epiphanyMaxChance, (d, v) -> d.epiphanyMaxChance = v,
            d -> Math.round(d.epiphanyMaxChance * 100) + "%",
            ALWAYS, null
        ),
        intOpt(
            "epiphany_max_slots_cap", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 1, 8, 1,
            d -> d.epiphanyMaxSlotsCap, (d, v) -> d.epiphanyMaxSlotsCap = v,
            d -> d.epiphanyMaxSlotsCap + "",
            ALWAYS, null
        ),
        intOpt(
            "epiphany_cycle_seconds", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 30, 3600, 30,
            d -> d.epiphanyCycleSeconds, (d, v) -> d.epiphanyCycleSeconds = v,
            d -> formatSeconds(d.epiphanyCycleSeconds),
            ALWAYS, null
        ),
        intOpt(
            "epiphany_freeze_xp_levels", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 0, 30, 1,
            d -> d.epiphanyFreezeXpLevels, (d, v) -> d.epiphanyFreezeXpLevels = v,
            d -> d.epiphanyFreezeXpLevels + "L",
            ALWAYS, null
        ),
        intOpt(
            "epiphany_freeze_duration_seconds", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 30, 3600, 30,
            d -> d.epiphanyFreezeDurationSeconds, (d, v) -> d.epiphanyFreezeDurationSeconds = v,
            d -> formatSeconds(d.epiphanyFreezeDurationSeconds),
            ALWAYS, null
        ),
        intOpt(
            "epiphany_max_freeze_seconds", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 30, 7200, 60,
            d -> d.epiphanyMaxFreezeSeconds, (d, v) -> d.epiphanyMaxFreezeSeconds = v,
            d -> formatSeconds(d.epiphanyMaxFreezeSeconds),
            ALWAYS, null
        ),
        intOpt(
            "epiphany_reroll_xp_levels", CategoryId.EPIPHANY, EffectScope.IMMEDIATE, 0, 30, 1,
            d -> d.epiphanyRerollXpLevels, (d, v) -> d.epiphanyRerollXpLevels = v,
            d -> d.epiphanyRerollXpLevels + "L",
            ALWAYS, null
        ),

        // ==================== 5. CLEANSE & TRANSFORMATIONS ====================
        boolOpt(
            "disenchant_enabled", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE,
            d -> d.disenchantEnabled, (d, v) -> d.disenchantEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "xp_refund_scale", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 0.00, 1.00, 0.05,
            d -> d.xpRefundScale, (d, v) -> d.xpRefundScale = v,
            d -> Math.round(d.xpRefundScale * 100) + "%",
            d -> d.disenchantEnabled, d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        doubleOpt(
            "external_xp_refund_scale", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 0.00, 1.00, 0.05,
            d -> d.externalXpRefundScale, (d, v) -> d.externalXpRefundScale = v,
            d -> Math.round(d.externalXpRefundScale * 100) + "%",
            d -> d.disenchantEnabled, d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "strip_xp_min", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 1, 30, 1,
            d -> d.stripXpMin, (d, v) -> d.stripXpMin = v,
            d -> "Lv." + d.stripXpMin,
            d -> d.disenchantEnabled, d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "strip_xp_max", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 1, 50, 1,
            d -> d.stripXpMax, (d, v) -> d.stripXpMax = v,
            d -> "Lv." + d.stripXpMax,
            d -> d.disenchantEnabled, d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        boolOpt(
            "allow_curse_purge", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE,
            d -> d.allowCursePurge, (d, v) -> d.allowCursePurge = v,
            d -> d.disenchantEnabled, "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "curse_purge_xp_cost", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 1, 50, 1,
            d -> d.cursePurgeXpCost, (d, v) -> d.cursePurgeXpCost = v,
            d -> "Lv." + d.cursePurgeXpCost,
            d -> d.disenchantEnabled && d.allowCursePurge,
            d -> !d.disenchantEnabled
                ? "config.chiseled_enchanting_table.reason.need_disenchant"
                : "config.chiseled_enchanting_table.reason.need_curse_purge"
        ),
        boolOpt(
            "require_prior_discovery", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE,
            d -> d.requirePriorDiscovery, (d, v) -> d.requirePriorDiscovery = v,
            ALWAYS, null
        ),
        intOpt(
            "tier1_bookshelves", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.tier1Bookshelves, (d, v) -> d.tier1Bookshelves = v,
            d -> d.tier1Bookshelves + "",
            ALWAYS, null
        ),
        intOpt(
            "tier2_bookshelves", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.tier2Bookshelves, (d, v) -> d.tier2Bookshelves = v,
            d -> d.tier2Bookshelves + "",
            ALWAYS, null
        ),
        intOpt(
            "tier3_qualifying_enchantments", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 1, 30, 1,
            d -> d.tier3QualifyingEnchantments, (d, v) -> d.tier3QualifyingEnchantments = v,
            d -> d.tier3QualifyingEnchantments + "",
            ALWAYS, null
        ),
        intOpt(
            "transformation_max_batch_size", CategoryId.CLEANSE_TRANSMUTE, EffectScope.IMMEDIATE, 1, 64, 1,
            d -> d.transformationMaxBatchSize, (d, v) -> d.transformationMaxBatchSize = v,
            d -> "\u00d7" + d.transformationMaxBatchSize,
            ALWAYS, null
        ),

        // ==================== 6. WORLD & LOOT ====================
        enumOpt(
            "vanilla_chest_books_mode", CategoryId.WORLD_LOOT, EffectScope.NEW_GENERATION,
            VanillaChestBooksMode.values(),
            d -> d.vanillaChestBooksMode, (d, v) -> d.vanillaChestBooksMode = v, VanillaChestBooksMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "bookshelf_pool_enabled", CategoryId.WORLD_LOOT, EffectScope.NEW_GENERATION,
            d -> d.bookshelfPoolEnabled, (d, v) -> d.bookshelfPoolEnabled = v,
            ALWAYS, null
        ),
        intOpt(
            "bookshelf_min_books", CategoryId.WORLD_LOOT, EffectScope.NEW_GENERATION, 0, 6, 1,
            d -> d.bookshelfMinBooks, (d, v) -> d.bookshelfMinBooks = v,
            d -> d.bookshelfMinBooks + " / 6",
            d -> d.bookshelfPoolEnabled, d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ),
        intOpt(
            "bookshelf_max_books", CategoryId.WORLD_LOOT, EffectScope.NEW_GENERATION, 0, 6, 1,
            d -> d.bookshelfMaxBooks, (d, v) -> d.bookshelfMaxBooks = v,
            d -> d.bookshelfMaxBooks + " / 6",
            d -> d.bookshelfPoolEnabled, d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ),
        doubleOpt(
            "bookshelf_enchanted_prob", CategoryId.WORLD_LOOT, EffectScope.NEW_GENERATION, 0.00, 1.00, 0.01,
            d -> d.bookshelfEnchantedProb, (d, v) -> d.bookshelfEnchantedProb = v,
            d -> Math.round(d.bookshelfEnchantedProb * 100) + "%",
            d -> d.bookshelfPoolEnabled, d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        )
    );

    public static List<OptionEntry> optionsInCategory(CategoryId category) {
        return ALL_OPTIONS.stream().filter(o -> o.category() == category).toList();
    }

    public static java.util.Optional<OptionEntry> byKey(String key) {
        return ALL_OPTIONS.stream().filter(o -> o.key().equals(key)).findFirst();
    }

    public static long countModified(Draft draft) {
        return ALL_OPTIONS.stream().filter(o -> o.isModifiedFromDefault(draft)).count();
    }

    public static void resetAll(Draft draft) {
        for (OptionEntry entry : ALL_OPTIONS) {
            entry.resetToDefault(draft);
        }
        draft.customizedKeys.clear();
        draft.normalizeAfterEdit(null);
    }

    private static String formatSeconds(int sec) {
        if (sec % 60 == 0) {
            return (sec / 60) + "m (" + sec + "s)";
        }
        return String.format(java.util.Locale.ROOT, "%dm%02ds", sec / 60, sec % 60);
    }
}
