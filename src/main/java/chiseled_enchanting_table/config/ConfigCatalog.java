package chiseled_enchanting_table.config;

import chiseled_enchanting_table.config.ModRuntimeConfig.CostTableDataset;
import chiseled_enchanting_table.config.ModRuntimeConfig.DefaultUnlockFilter;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import chiseled_enchanting_table.config.ModRuntimeConfig.DrainedBookResult;
import chiseled_enchanting_table.config.ModRuntimeConfig.MaterialSelectionMode;
import chiseled_enchanting_table.config.ModRuntimeConfig.ResearchRoundingMode;
import chiseled_enchanting_table.config.ModRuntimeConfig.ShelfBoostModel;
import chiseled_enchanting_table.config.ModRuntimeConfig.TransformationSetMode;
import chiseled_enchanting_table.config.ModRuntimeConfig.XpSettlementMode;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Declarative L1/L2/L3 schema, applicability engine, and 20-atom {@code 1.0.3 <-> 1.1.0} rule
 * provenance catalog for {@link ModRuntimeConfig}.
 */
public final class ConfigCatalog {
    private ConfigCatalog() {}

    public enum CategoryId {
        BOOKSHELVES("bookshelves", 0xFF3E9AB0, "icon_domain_unlock.png"),
        EXPERIENCE_ECONOMY("experience_economy", 0xFFD99B38, "icon_domain_pricing.png"),
        TOME_DURABILITY("tome_durability", 0xFFA46EE5, "icon_domain_tomes.png"),
        EPIPHANY("epiphany", 0xFFB86BFF, "icon_domain_epiphany.png"),
        CLEANSE_TRANSMUTE("cleanse_transmute", 0xFF43C59E, "icon_domain_cleanse.png"),
        WORLD_LOOT("world_loot", 0xFFD17A47, "icon_domain_world.png"),
        GENERAL("general", 0xFF8E7CC3, "icon_domain_system.png");

        private final String id;
        private final int accentColor;
        private final String iconFile;

        CategoryId(String id, int accentColor, String iconFile) {
            this.id = id;
            this.accentColor = accentColor;
            this.iconFile = iconFile;
        }

        public String id() {
            return id;
        }

        public int accentColor() {
            return accentColor;
        }

        public String iconFile() {
            return iconFile;
        }

        public String titleKey() {
            return "config.chiseled_enchanting_table.cat." + id;
        }

        public String subtitleKey() {
            return "config.chiseled_enchanting_table.cat." + id + ".sub";
        }
    }

    public enum EffectScope {
        IMMEDIATE("config.chiseled_enchanting_table.scope.immediate", 0xFF56E39F, "\u25CF"),
        NEW_GENERATION("config.chiseled_enchanting_table.scope.new_gen", 0xFF6CD4FF, "\u25C6"),
        RESTART_REQUIRED("config.chiseled_enchanting_table.scope.restart", 0xFFFFBA52, "\u25B2");

        private final String badgeKey;
        private final int color;
        private final String glyph;

        EffectScope(String badgeKey, int color, String glyph) {
            this.badgeKey = badgeKey;
            this.color = color;
            this.glyph = glyph;
        }

        public String badgeKey() {
            return badgeKey;
        }

        public int color() {
            return color;
        }

        /** Dual-coded geometric glyph (circle / diamond / triangle) so badges never rely on color alone. */
        public String glyph() {
            return glyph;
        }
    }

    public enum SessionMode {
        OFFLINE_CLIENT("config.chiseled_enchanting_table.session.offline", true),
        SINGLEPLAYER_LIVE("config.chiseled_enchanting_table.session.singleplayer", true),
        LAN_HOST_LIVE("config.chiseled_enchanting_table.session.lan_host", true),
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

        public static SessionMode resolve(
            boolean modpackLocked,
            boolean inWorld,
            boolean hasSingleplayerServer,
            boolean lanPublished,
            boolean canEditDedicatedServer
        ) {
            if (modpackLocked) {
                return MODPACK_LOCKED;
            }
            if (!inWorld) {
                return OFFLINE_CLIENT;
            }
            if (hasSingleplayerServer) {
                return lanPublished ? LAN_HOST_LIVE : SINGLEPLAYER_LIVE;
            }
            return canEditDedicatedServer ? DEDICATED_ADMIN : DEDICATED_READONLY;
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

    /**
     * Declarative entry in the 20-atom {@code 1.0.3 <-> 1.1.0} Rule Provenance Trace view.
     * Nineteen entries bind directly to an editable {@link OptionEntry}; the 20th
     * ({@code classic_presentation}) is derived automatically from the active rule atoms.
     */
    public record RuleTraceEntry(
        String traceId,
        CategoryId domain,
        @Nullable String optionKey,
        @Nullable Object classic103Value,
        @Nullable Object modern110Value,
        boolean derived
    ) {
        public String titleKey() {
            return optionKey != null
                ? "config.chiseled_enchanting_table.opt." + optionKey
                : "config.chiseled_enchanting_table.trace." + traceId + ".title";
        }

        public String impactKey() {
            return "config.chiseled_enchanting_table.trace." + traceId + ".impact";
        }

        public String classicSummaryKey() {
            return "config.chiseled_enchanting_table.trace." + traceId + ".v103";
        }

        public String modernSummaryKey() {
            return "config.chiseled_enchanting_table.trace." + traceId + ".v110";
        }

        public boolean isUsingClassic103(Draft draft) {
            if (derived) {
                return draft.build().isAllClassicAtoms();
            }
            Optional<OptionEntry> opt = byKey(optionKey);
            return opt.isPresent() && Objects.equals(opt.get().get(draft), classic103Value);
        }

        public boolean isUsingModern110(Draft draft) {
            if (derived) {
                return draft.build().countClassic103Atoms() == 0;
            }
            Optional<OptionEntry> opt = byKey(optionKey);
            return opt.isPresent() && Objects.equals(opt.get().get(draft), modern110Value);
        }

        public void applyClassic103(Draft draft) {
            if (derived || optionKey == null || classic103Value == null) return;
            byKey(optionKey).ifPresent(o -> o.set(draft, classic103Value));
        }

        public void applyModern110(Draft draft) {
            if (derived || optionKey == null || modern110Value == null) return;
            byKey(optionKey).ifPresent(o -> o.set(draft, modern110Value));
        }
    }

    public static final class OptionEntry {
        private final String key;
        private final CategoryId category;
        private final String sectionId;
        private final boolean coreRuleAtom;
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
            String sectionId,
            boolean coreRuleAtom,
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
            this.sectionId = sectionId;
            this.coreRuleAtom = coreRuleAtom;
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

        public String sectionId() {
            return sectionId;
        }

        public String sectionTitleKey() {
            return "config.chiseled_enchanting_table.sec." + sectionId;
        }

        public boolean isCoreRuleAtom() {
            return coreRuleAtom;
        }

        public boolean isParentApplicable(Draft d) {
            return activeWhen.test(d);
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
        public void setResolvedValue(Draft d, Object v) {
            setter.accept(d, v);
        }

        public void set(Draft d, Object v) {
            setter.accept(d, v);
            d.markCustomized(key);
            d.normalizeAfterEdit(key);
        }

        public void toggleOrCycle(Draft d, int direction) {
            if (kind == ControlKind.BOOLEAN) {
                boolean cur = Boolean.TRUE.equals(getter.apply(d));
                set(d, !cur);
            } else if (kind == ControlKind.ENUM_CYCLE) {
                enumCycler.accept(d, direction);
                d.markCustomized(key);
                d.normalizeAfterEdit(key);
            }
        }

        public double normalizedSliderValue(Draft d) {
            return Math.max(0.0, Math.min(1.0, sliderNormalizedGetter.apply(d)));
        }

        public void setFromNormalizedSlider(Draft d, double t) {
            double clamped = Math.max(0.0, Math.min(1.0, t));
            sliderNormalizedSetter.accept(d, clamped);
            d.markCustomized(key);
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

    // ---- Concise builders ----

    private static final Predicate<Draft> ALWAYS = d -> true;

    private static OptionEntry boolOpt(
        String key,
        CategoryId cat,
        String sectionId,
        boolean coreAtom,
        EffectScope scope,
        Function<Draft, Boolean> get,
        BiConsumer<Draft, Boolean> set,
        Predicate<Draft> activeWhen,
        @Nullable Function<Draft, String> reasonFn
    ) {
        return new OptionEntry(
            key, cat, sectionId, coreAtom, ControlKind.BOOLEAN, scope, 0, 1, 1,
            get::apply,
            (d, v) -> set.accept(d, (Boolean) v),
            d -> Boolean.TRUE.equals(get.apply(d))
                ? "@config.chiseled_enchanting_table.val.on"
                : "@config.chiseled_enchanting_table.val.off",
            (d, dir) -> set.accept(d, !Boolean.TRUE.equals(get.apply(d))),
            d -> Boolean.TRUE.equals(get.apply(d)) ? 1.0 : 0.0,
            (d, t) -> set.accept(d, t >= 0.5),
            activeWhen,
            reasonFn
        );
    }

    private static OptionEntry intOpt(
        String key,
        CategoryId cat,
        String sectionId,
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
            key, cat, sectionId, false, ControlKind.INT_SLIDER, scope, min, max, step,
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
        String sectionId,
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
            key, cat, sectionId, false, ControlKind.DOUBLE_SLIDER, scope, min, max, step,
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
        String sectionId,
        boolean coreAtom,
        EffectScope scope,
        E[] values,
        Function<Draft, E> get,
        BiConsumer<Draft, E> set,
        Function<E, String> tokenFn,
        Predicate<Draft> activeWhen,
        @Nullable Function<Draft, String> reasonFn
    ) {
        return new OptionEntry(
            key, cat, sectionId, coreAtom, ControlKind.ENUM_CYCLE, scope, 0, values.length - 1, 1,
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
            reasonFn
        );
    }

    public static final List<OptionEntry> ALL_OPTIONS = List.of(
        // ==================== 1. UNLOCK & BOOKSHELVES ====================
        enumOpt(
            "shelf_boost_model", CategoryId.BOOKSHELVES, "unlock_core", true, EffectScope.IMMEDIATE,
            ShelfBoostModel.values(),
            d -> d.shelfBoostModel, (d, v) -> d.shelfBoostModel = v, ShelfBoostModel::token,
            ALWAYS, null
        ),
        enumOpt(
            "default_unlock_filter", CategoryId.BOOKSHELVES, "unlock_core", true, EffectScope.IMMEDIATE,
            DefaultUnlockFilter.values(),
            d -> d.defaultUnlockFilter, (d, v) -> d.defaultUnlockFilter = v, DefaultUnlockFilter::token,
            ALWAYS, null
        ),
        boolOpt(
            "direct_tier_upgrade", CategoryId.BOOKSHELVES, "unlock_core", true, EffectScope.IMMEDIATE,
            d -> d.directTierUpgrade, (d, v) -> d.directTierUpgrade = v,
            d -> d.shelfBoostModel == ShelfBoostModel.MODERN_CURVE,
            d -> "config.chiseled_enchanting_table.reason.need_modern_shelf_curve"
        ),
        boolOpt(
            "direct_gates_enabled", CategoryId.BOOKSHELVES, "shelf_curve", false, EffectScope.IMMEDIATE,
            d -> d.directGatesEnabled, (d, v) -> d.directGatesEnabled = v,
            d -> d.shelfBoostModel == ShelfBoostModel.MODERN_CURVE,
            d -> "config.chiseled_enchanting_table.reason.need_modern_shelf_curve"
        ),
        enumOpt(
            "blank_max_tier_mode", CategoryId.BOOKSHELVES, "shelf_curve", false, EffectScope.IMMEDIATE,
            BlankMaxTierMode.values(),
            d -> d.blankMaxTierMode, (d, v) -> d.blankMaxTierMode = v, BlankMaxTierMode::token,
            d -> d.shelfBoostModel == ShelfBoostModel.MODERN_CURVE && d.directGatesEnabled,
            d -> d.shelfBoostModel != ShelfBoostModel.MODERN_CURVE
                ? "config.chiseled_enchanting_table.reason.need_modern_shelf_curve"
                : "config.chiseled_enchanting_table.reason.need_direct_gates"
        ),
        intOpt(
            "max_direct_bookshelves", CategoryId.BOOKSHELVES, "shelf_curve", EffectScope.IMMEDIATE, 4, 64, 1,
            d -> d.maxDirectBookshelves, (d, v) -> d.maxDirectBookshelves = v,
            d -> d.maxDirectBookshelves + "",
            d -> d.shelfBoostModel == ShelfBoostModel.MODERN_CURVE && d.directGatesEnabled,
            d -> d.shelfBoostModel != ShelfBoostModel.MODERN_CURVE
                ? "config.chiseled_enchanting_table.reason.need_modern_shelf_curve"
                : "config.chiseled_enchanting_table.reason.need_direct_gates"
        ),
        intOpt(
            "horizontal_scan_radius", CategoryId.BOOKSHELVES, "scan_geometry", EffectScope.IMMEDIATE, 1, 8, 1,
            d -> d.horizontalScanRadius, (d, v) -> d.horizontalScanRadius = v,
            d -> {
                int w = 2 * d.horizontalScanRadius + 1;
                int h = 2 * d.verticalScanRadius + 1;
                return d.horizontalScanRadius + " (" + w + "\u00d7" + h + "\u00d7" + w + ")";
            },
            ALWAYS, null
        ),
        intOpt(
            "vertical_scan_radius", CategoryId.BOOKSHELVES, "scan_geometry", EffectScope.IMMEDIATE, 0, 6, 1,
            d -> d.verticalScanRadius, (d, v) -> d.verticalScanRadius = v,
            d -> d.verticalScanRadius + " (" + BookshelfProgression.scanVolumeBlocks(d.horizontalScanRadius, d.verticalScanRadius) + " blk)",
            ALWAYS, null
        ),
        boolOpt(
            "require_breathing_room", CategoryId.BOOKSHELVES, "scan_geometry", false, EffectScope.IMMEDIATE,
            d -> d.requireBreathingRoom, (d, v) -> d.requireBreathingRoom = v,
            ALWAYS, null
        ),
        intOpt(
            "min_open_adjacent_blocks", CategoryId.BOOKSHELVES, "scan_geometry", EffectScope.IMMEDIATE, 1, 17, 1,
            d -> d.minOpenAdjacentBlocks, (d, v) -> d.minOpenAdjacentBlocks = v,
            d -> d.minOpenAdjacentBlocks + " / 17",
            d -> d.requireBreathingRoom,
            d -> "config.chiseled_enchanting_table.reason.need_breathing_room"
        ),
        boolOpt(
            "require_line_of_sight", CategoryId.BOOKSHELVES, "scan_geometry", false, EffectScope.IMMEDIATE,
            d -> d.requireLineOfSight, (d, v) -> d.requireLineOfSight = v,
            ALWAYS, null
        ),

        // ==================== 2. PRICING & EXPERIENCE ECONOMY ====================
        enumOpt(
            "cost_table", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            CostTableDataset.values(),
            d -> d.costTableDataset, (d, v) -> d.costTableDataset = v, CostTableDataset::token,
            ALWAYS, null
        ),
        enumOpt(
            "material_selection", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            MaterialSelectionMode.values(),
            d -> d.materialSelectionMode, (d, v) -> d.materialSelectionMode = v, MaterialSelectionMode::token,
            ALWAYS, null
        ),
        enumOpt(
            "xp_mode", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            XpSettlementMode.values(),
            d -> d.xpMode, (d, v) -> d.xpMode = v, XpSettlementMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "incremental_credit", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            d -> d.incrementalCredit, (d, v) -> d.incrementalCredit = v,
            ALWAYS, null
        ),
        enumOpt(
            "research_rounding", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            ResearchRoundingMode.values(),
            d -> d.researchRoundingMode, (d, v) -> d.researchRoundingMode = v, ResearchRoundingMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "library_discount", CategoryId.EXPERIENCE_ECONOMY, "pricing_core", true, EffectScope.IMMEDIATE,
            d -> d.libraryDiscountEnabled, (d, v) -> d.libraryDiscountEnabled = v,
            ALWAYS, null
        ),
        intOpt(
            "max_wooden_discount_percent", CategoryId.EXPERIENCE_ECONOMY, "resonance_caps", EffectScope.IMMEDIATE, 0, 90, 1,
            d -> d.maxWoodenDiscountPercent, (d, v) -> d.maxWoodenDiscountPercent = v,
            d -> "-" + d.maxWoodenDiscountPercent + "%",
            d -> d.libraryDiscountEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_library_discount"
        ),
        intOpt(
            "max_total_xp_discount_percent", CategoryId.EXPERIENCE_ECONOMY, "resonance_caps", EffectScope.IMMEDIATE, 0, 90, 1,
            d -> d.maxTotalXpDiscountPercent, (d, v) -> d.maxTotalXpDiscountPercent = v,
            d -> "-" + d.maxTotalXpDiscountPercent + "%",
            d -> d.libraryDiscountEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_library_discount"
        ),
        boolOpt(
            "book_combine_enabled", CategoryId.EXPERIENCE_ECONOMY, "book_upgrades", false, EffectScope.IMMEDIATE,
            d -> d.bookCombineEnabled, (d, v) -> d.bookCombineEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "book_combine_xp_multiplier", CategoryId.EXPERIENCE_ECONOMY, "book_upgrades", EffectScope.IMMEDIATE, 0.10, 3.00, 0.05,
            d -> d.bookCombineXpMultiplier, (d, v) -> d.bookCombineXpMultiplier = v,
            d -> String.format(Locale.ROOT, "%.2f\u00d7", d.bookCombineXpMultiplier),
            d -> d.bookCombineEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_book_combine"
        ),
        boolOpt(
            "book_research_enabled", CategoryId.EXPERIENCE_ECONOMY, "book_upgrades", false, EffectScope.IMMEDIATE,
            d -> d.bookResearchEnabled, (d, v) -> d.bookResearchEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "book_research_xp_multiplier", CategoryId.EXPERIENCE_ECONOMY, "book_upgrades", EffectScope.IMMEDIATE, 0.10, 3.00, 0.05,
            d -> d.bookResearchXpMultiplier, (d, v) -> d.bookResearchXpMultiplier = v,
            d -> String.format(Locale.ROOT, "%.2f\u00d7", d.bookResearchXpMultiplier),
            d -> d.bookResearchEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_book_research"
        ),

        // ==================== 3. MASTER TOMES & RITUAL ====================
        enumOpt(
            "tome_consumption_mode", CategoryId.TOME_DURABILITY, "tome_core", true, EffectScope.IMMEDIATE,
            MasterTomeWear.ConsumptionMode.values(),
            d -> d.tomeConsumptionMode, (d, v) -> d.tomeConsumptionMode = v, MasterTomeWear.ConsumptionMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "tome_locks", CategoryId.TOME_DURABILITY, "tome_core", true, EffectScope.IMMEDIATE,
            d -> d.tomeLocksEnabled, (d, v) -> d.tomeLocksEnabled = v,
            ALWAYS, null
        ),
        boolOpt(
            "provenance", CategoryId.TOME_DURABILITY, "tome_core", true, EffectScope.IMMEDIATE,
            d -> d.provenanceEnabled, (d, v) -> d.provenanceEnabled = v,
            ALWAYS, null
        ),
        boolOpt(
            "ritual_queue", CategoryId.TOME_DURABILITY, "tome_core", true, EffectScope.IMMEDIATE,
            d -> d.ritualQueueEnabled, (d, v) -> d.ritualQueueEnabled = v,
            ALWAYS, null
        ),
        intOpt(
            "max_tome_uses", CategoryId.TOME_DURABILITY, "wear_tuning", EffectScope.IMMEDIATE, 1, 256, 1,
            d -> d.maxTomeUses, (d, v) -> d.maxTomeUses = v,
            d -> d.maxTomeUses + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        intOpt(
            "book_copy_wear_cost", CategoryId.TOME_DURABILITY, "wear_tuning", EffectScope.IMMEDIATE, 1, 64, 1,
            d -> d.bookCopyWearCost, (d, v) -> d.bookCopyWearCost = v,
            d -> d.bookCopyWearCost + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        enumOpt(
            "tome_deplete_behavior", CategoryId.TOME_DURABILITY, "wear_tuning", false, EffectScope.IMMEDIATE,
            MasterTomeWear.DepleteBehavior.values(),
            d -> d.tomeDepleteBehavior, (d, v) -> d.tomeDepleteBehavior = v, MasterTomeWear.DepleteBehavior::token,
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        boolOpt(
            "locked_shelf_breakable", CategoryId.TOME_DURABILITY, "wear_tuning", false, EffectScope.IMMEDIATE,
            d -> d.lockedShelfBreakable, (d, v) -> d.lockedShelfBreakable = v,
            d -> d.tomeLocksEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_tome_locks"
        ),
        boolOpt(
            "locked_book_extraction_blocked", CategoryId.TOME_DURABILITY, "wear_tuning", false, EffectScope.IMMEDIATE,
            d -> d.lockedBookExtractionBlocked, (d, v) -> d.lockedBookExtractionBlocked = v,
            d -> d.tomeLocksEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_tome_locks"
        ),
        boolOpt(
            "tome_regen_enabled", CategoryId.TOME_DURABILITY, "regen_schedule", false, EffectScope.IMMEDIATE,
            d -> d.tomeRegenEnabled, (d, v) -> d.tomeRegenEnabled = v,
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR,
            d -> "config.chiseled_enchanting_table.reason.need_wear_mode"
        ),
        intOpt(
            "tome_regen_base_seconds", CategoryId.TOME_DURABILITY, "regen_schedule", EffectScope.IMMEDIATE, 60, 72_000, 600,
            d -> d.tomeRegenBaseSeconds, (d, v) -> d.tomeRegenBaseSeconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenBaseSeconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier1_shelves", CategoryId.TOME_DURABILITY, "regen_schedule", EffectScope.IMMEDIATE, 4, 96, 1,
            d -> d.tomeRegenTier1Shelves, (d, v) -> d.tomeRegenTier1Shelves = v,
            d -> d.tomeRegenTier1Shelves + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier1_seconds", CategoryId.TOME_DURABILITY, "regen_schedule", EffectScope.IMMEDIATE, 60, 48_000, 600,
            d -> d.tomeRegenTier1Seconds, (d, v) -> d.tomeRegenTier1Seconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenTier1Seconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier2_shelves", CategoryId.TOME_DURABILITY, "regen_schedule", EffectScope.IMMEDIATE, 4, 96, 1,
            d -> d.tomeRegenTier2Shelves, (d, v) -> d.tomeRegenTier2Shelves = v,
            d -> d.tomeRegenTier2Shelves + "",
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),
        intOpt(
            "tome_regen_tier2_seconds", CategoryId.TOME_DURABILITY, "regen_schedule", EffectScope.IMMEDIATE, 60, 24_000, 300,
            d -> d.tomeRegenTier2Seconds, (d, v) -> d.tomeRegenTier2Seconds = v,
            d -> chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.formatRegenGameDays(d.tomeRegenTier2Seconds),
            d -> d.tomeConsumptionMode == MasterTomeWear.ConsumptionMode.WEAR && d.tomeRegenEnabled,
            d -> d.tomeConsumptionMode != MasterTomeWear.ConsumptionMode.WEAR
                ? "config.chiseled_enchanting_table.reason.need_wear_mode"
                : "config.chiseled_enchanting_table.reason.need_tome_regen"
        ),

        // ==================== 4. TREASURE EPIPHANY ====================
        boolOpt(
            "epiphany_enabled", CategoryId.EPIPHANY, "epiphany_core", true, EffectScope.IMMEDIATE,
            d -> d.epiphanyEnabled, (d, v) -> d.epiphanyEnabled = v,
            ALWAYS, null
        ),
        intOpt(
            "epiphany_min_bookshelves", CategoryId.EPIPHANY, "epiphany_chances", EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.epiphanyMinBookshelves, (d, v) -> d.epiphanyMinBookshelves = v,
            d -> d.epiphanyMinBookshelves + "",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        doubleOpt(
            "epiphany_base_chance", CategoryId.EPIPHANY, "epiphany_chances", EffectScope.IMMEDIATE, 0.00, 1.00, 0.02,
            d -> d.epiphanyBaseChance, (d, v) -> d.epiphanyBaseChance = v,
            d -> Math.round(d.epiphanyBaseChance * 100) + "%",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        doubleOpt(
            "epiphany_max_chance", CategoryId.EPIPHANY, "epiphany_chances", EffectScope.IMMEDIATE, 0.00, 1.00, 0.02,
            d -> d.epiphanyMaxChance, (d, v) -> d.epiphanyMaxChance = v,
            d -> Math.round(d.epiphanyMaxChance * 100) + "%",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_max_slots_cap", CategoryId.EPIPHANY, "epiphany_chances", EffectScope.IMMEDIATE, 1, 8, 1,
            d -> d.epiphanyMaxSlotsCap, (d, v) -> d.epiphanyMaxSlotsCap = v,
            d -> d.epiphanyMaxSlotsCap + "",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_cycle_seconds", CategoryId.EPIPHANY, "epiphany_freeze", EffectScope.IMMEDIATE, 30, 3600, 30,
            d -> d.epiphanyCycleSeconds, (d, v) -> d.epiphanyCycleSeconds = v,
            d -> formatSeconds(d.epiphanyCycleSeconds),
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_freeze_xp_levels", CategoryId.EPIPHANY, "epiphany_freeze", EffectScope.IMMEDIATE, 0, 30, 1,
            d -> d.epiphanyFreezeXpLevels, (d, v) -> d.epiphanyFreezeXpLevels = v,
            d -> d.epiphanyFreezeXpLevels + "L",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_freeze_duration_seconds", CategoryId.EPIPHANY, "epiphany_freeze", EffectScope.IMMEDIATE, 30, 3600, 30,
            d -> d.epiphanyFreezeDurationSeconds, (d, v) -> d.epiphanyFreezeDurationSeconds = v,
            d -> formatSeconds(d.epiphanyFreezeDurationSeconds),
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_max_freeze_seconds", CategoryId.EPIPHANY, "epiphany_freeze", EffectScope.IMMEDIATE, 30, 7200, 60,
            d -> d.epiphanyMaxFreezeSeconds, (d, v) -> d.epiphanyMaxFreezeSeconds = v,
            d -> formatSeconds(d.epiphanyMaxFreezeSeconds),
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),
        intOpt(
            "epiphany_reroll_xp_levels", CategoryId.EPIPHANY, "epiphany_freeze", EffectScope.IMMEDIATE, 0, 30, 1,
            d -> d.epiphanyRerollXpLevels, (d, v) -> d.epiphanyRerollXpLevels = v,
            d -> d.epiphanyRerollXpLevels + "L",
            d -> d.epiphanyEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_epiphany"
        ),

        // ==================== 5. CLEANSE, TRANSFORMATIONS & TOME PAGE ====================
        boolOpt(
            "disenchant_enabled", CategoryId.CLEANSE_TRANSMUTE, "cleanse_core", true, EffectScope.IMMEDIATE,
            d -> d.disenchantEnabled, (d, v) -> d.disenchantEnabled = v,
            ALWAYS, null
        ),
        enumOpt(
            "transformation_set", CategoryId.CLEANSE_TRANSMUTE, "cleanse_core", true, EffectScope.IMMEDIATE,
            TransformationSetMode.values(),
            d -> d.transformationSetMode, (d, v) -> d.transformationSetMode = v, TransformationSetMode::token,
            ALWAYS, null
        ),
        enumOpt(
            "drained_book_result", CategoryId.CLEANSE_TRANSMUTE, "cleanse_core", true, EffectScope.IMMEDIATE,
            DrainedBookResult.values(),
            d -> d.drainedBookResult, (d, v) -> d.drainedBookResult = v, DrainedBookResult::token,
            ALWAYS, null
        ),
        boolOpt(
            "tome_page_enabled", CategoryId.CLEANSE_TRANSMUTE, "cleanse_core", true, EffectScope.IMMEDIATE,
            d -> d.tomePageEnabled, (d, v) -> d.tomePageEnabled = v,
            ALWAYS, null
        ),
        doubleOpt(
            "xp_refund_scale", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", EffectScope.IMMEDIATE, 0.00, 1.00, 0.05,
            d -> d.xpRefundScale, (d, v) -> d.xpRefundScale = v,
            d -> Math.round(d.xpRefundScale * 100) + "%",
            d -> d.disenchantEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        doubleOpt(
            "external_xp_refund_scale", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", EffectScope.IMMEDIATE, 0.00, 1.00, 0.05,
            d -> d.externalXpRefundScale, (d, v) -> d.externalXpRefundScale = v,
            d -> Math.round(d.externalXpRefundScale * 100) + "%",
            d -> d.disenchantEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "strip_xp_min", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", EffectScope.IMMEDIATE, 1, 30, 1,
            d -> d.stripXpMin, (d, v) -> d.stripXpMin = v,
            d -> "Lv." + d.stripXpMin,
            d -> d.disenchantEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "strip_xp_max", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", EffectScope.IMMEDIATE, 1, 50, 1,
            d -> d.stripXpMax, (d, v) -> d.stripXpMax = v,
            d -> "Lv." + d.stripXpMax,
            d -> d.disenchantEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        boolOpt(
            "allow_curse_purge", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", false, EffectScope.IMMEDIATE,
            d -> d.allowCursePurge, (d, v) -> d.allowCursePurge = v,
            d -> d.disenchantEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant"
        ),
        intOpt(
            "curse_purge_xp_cost", CategoryId.CLEANSE_TRANSMUTE, "cleanse_tuning", EffectScope.IMMEDIATE, 1, 50, 1,
            d -> d.cursePurgeXpCost, (d, v) -> d.cursePurgeXpCost = v,
            d -> "Lv." + d.cursePurgeXpCost,
            d -> d.disenchantEnabled && d.allowCursePurge,
            d -> !d.disenchantEnabled
                ? "config.chiseled_enchanting_table.reason.need_disenchant"
                : "config.chiseled_enchanting_table.reason.need_curse_purge"
        ),
        boolOpt(
            "require_prior_discovery", CategoryId.CLEANSE_TRANSMUTE, "transmute_tiers", false, EffectScope.IMMEDIATE,
            d -> d.requirePriorDiscovery, (d, v) -> d.requirePriorDiscovery = v,
            d -> d.transformationSetMode == TransformationSetMode.FULL_SIX,
            d -> "config.chiseled_enchanting_table.reason.need_full_transformations"
        ),
        intOpt(
            "transformation_max_batch_size", CategoryId.CLEANSE_TRANSMUTE, "transmute_tiers", EffectScope.IMMEDIATE, 1, 64, 1,
            d -> d.transformationMaxBatchSize, (d, v) -> d.transformationMaxBatchSize = v,
            d -> "\u00d7" + d.transformationMaxBatchSize,
            ALWAYS, null
        ),
        intOpt(
            "tier1_bookshelves", CategoryId.CLEANSE_TRANSMUTE, "transmute_tiers", EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.tier1Bookshelves, (d, v) -> d.tier1Bookshelves = v,
            d -> d.tier1Bookshelves + "",
            d -> d.drainedBookResult == DrainedBookResult.DEPLETED_TOME || d.ritualQueueEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_depleted_tome"
        ),
        intOpt(
            "tier2_bookshelves", CategoryId.CLEANSE_TRANSMUTE, "transmute_tiers", EffectScope.IMMEDIATE, 0, 64, 1,
            d -> d.tier2Bookshelves, (d, v) -> d.tier2Bookshelves = v,
            d -> d.tier2Bookshelves + "",
            d -> d.drainedBookResult == DrainedBookResult.DEPLETED_TOME || d.ritualQueueEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_depleted_tome"
        ),
        intOpt(
            "tier3_qualifying_enchantments", CategoryId.CLEANSE_TRANSMUTE, "transmute_tiers", EffectScope.IMMEDIATE, 1, 30, 1,
            d -> d.tier3QualifyingEnchantments, (d, v) -> d.tier3QualifyingEnchantments = v,
            d -> d.tier3QualifyingEnchantments + "",
            d -> d.drainedBookResult == DrainedBookResult.DEPLETED_TOME || d.ritualQueueEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_depleted_tome"
        ),
        intOpt(
            "tome_page_required_shelves", CategoryId.CLEANSE_TRANSMUTE, "tome_page_unlock", EffectScope.IMMEDIATE, 1, 512, 1,
            d -> d.tomePageRequiredShelves, (d, v) -> d.tomePageRequiredShelves = v,
            d -> d.tomePageRequiredShelves + "",
            d -> d.tomePageEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_tome_page"
        ),
        intOpt(
            "sacrifice_required", CategoryId.CLEANSE_TRANSMUTE, "tome_page_unlock", EffectScope.IMMEDIATE, 1, 1024, 1,
            d -> d.sacrificeRequired, (d, v) -> d.sacrificeRequired = v,
            d -> d.sacrificeRequired + "",
            d -> d.disenchantEnabled || d.tomePageEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_disenchant_or_tome_page"
        ),
        intOpt(
            "early_unlock_xp_levels", CategoryId.CLEANSE_TRANSMUTE, "tome_page_unlock", EffectScope.IMMEDIATE, 0, 512, 1,
            d -> d.earlyUnlockXpLevels, (d, v) -> d.earlyUnlockXpLevels = v,
            d -> d.earlyUnlockXpLevels + "",
            d -> d.tomePageEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_tome_page"
        ),
        intOpt(
            "early_unlock_grit_count", CategoryId.CLEANSE_TRANSMUTE, "tome_page_unlock", EffectScope.IMMEDIATE, 0, 512, 1,
            d -> d.earlyUnlockGritCount, (d, v) -> d.earlyUnlockGritCount = v,
            d -> d.earlyUnlockGritCount + "",
            d -> d.tomePageEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_tome_page"
        ),

        // ==================== 6. WORLD & VANILLA INTEGRATION ====================
        boolOpt(
            "librarian_tome_offers", CategoryId.WORLD_LOOT, "world_core", true, EffectScope.IMMEDIATE,
            d -> d.librarianTomeOffers, (d, v) -> d.librarianTomeOffers = v,
            ALWAYS, null
        ),
        enumOpt(
            "vanilla_table_mode", CategoryId.WORLD_LOOT, "vanilla_hooks", false, EffectScope.IMMEDIATE,
            VanillaTableMode.values(),
            d -> d.vanillaTableMode, (d, v) -> d.vanillaTableMode = v, VanillaTableMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "anvil_flat_repair_cost", CategoryId.WORLD_LOOT, "vanilla_hooks", false, EffectScope.IMMEDIATE,
            d -> d.anvilFlatRepairCost, (d, v) -> d.anvilFlatRepairCost = v,
            ALWAYS, null
        ),
        boolOpt(
            "anvil_block_same_book_merge", CategoryId.WORLD_LOOT, "vanilla_hooks", false, EffectScope.IMMEDIATE,
            d -> d.anvilBlockSameBookMerge, (d, v) -> d.anvilBlockSameBookMerge = v,
            ALWAYS, null
        ),
        boolOpt(
            "restrict_villager_infinity_books", CategoryId.WORLD_LOOT, "vanilla_hooks", false, EffectScope.IMMEDIATE,
            d -> d.restrictVillagerInfinityBooks, (d, v) -> d.restrictVillagerInfinityBooks = v,
            ALWAYS, null
        ),
        boolOpt(
            "grindstone_depleted_tome", CategoryId.WORLD_LOOT, "vanilla_hooks", false, EffectScope.IMMEDIATE,
            d -> d.grindstoneDepletedTome, (d, v) -> d.grindstoneDepletedTome = v,
            ALWAYS, null
        ),
        enumOpt(
            "vanilla_chest_books_mode", CategoryId.WORLD_LOOT, "loot_pools", false, EffectScope.NEW_GENERATION,
            VanillaChestBooksMode.values(),
            d -> d.vanillaChestBooksMode, (d, v) -> d.vanillaChestBooksMode = v, VanillaChestBooksMode::token,
            ALWAYS, null
        ),
        boolOpt(
            "bookshelf_pool_enabled", CategoryId.WORLD_LOOT, "loot_pools", false, EffectScope.NEW_GENERATION,
            d -> d.bookshelfPoolEnabled, (d, v) -> d.bookshelfPoolEnabled = v,
            ALWAYS, null
        ),
        intOpt(
            "bookshelf_min_books", CategoryId.WORLD_LOOT, "loot_pools", EffectScope.NEW_GENERATION, 0, 6, 1,
            d -> d.bookshelfMinBooks, (d, v) -> d.bookshelfMinBooks = v,
            d -> d.bookshelfMinBooks + " / 6",
            d -> d.bookshelfPoolEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ),
        intOpt(
            "bookshelf_max_books", CategoryId.WORLD_LOOT, "loot_pools", EffectScope.NEW_GENERATION, 0, 6, 1,
            d -> d.bookshelfMaxBooks, (d, v) -> d.bookshelfMaxBooks = v,
            d -> d.bookshelfMaxBooks + " / 6",
            d -> d.bookshelfPoolEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ),
        doubleOpt(
            "bookshelf_enchanted_prob", CategoryId.WORLD_LOOT, "loot_pools", EffectScope.NEW_GENERATION, 0.00, 1.00, 0.01,
            d -> d.bookshelfEnchantedProb, (d, v) -> d.bookshelfEnchantedProb = v,
            d -> Math.round(d.bookshelfEnchantedProb * 100) + "%",
            d -> d.bookshelfPoolEnabled,
            d -> "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ),

        // ==================== 7. SYSTEM & DATAPACK GOVERNANCE ====================
        boolOpt(
            "override_datapacks", CategoryId.GENERAL, "datapack_governance", true, EffectScope.IMMEDIATE,
            d -> d.overrideDatapacks, (d, v) -> d.overrideDatapacks = v,
            ALWAYS, null
        ),
        intOpt(
            "enchanted_book_stack_size", CategoryId.GENERAL, "bootstrap_items", EffectScope.RESTART_REQUIRED, 1, 64, 1,
            d -> d.enchantedBookStackSize, (d, v) -> d.enchantedBookStackSize = v,
            d -> d.enchantedBookStackSize + "",
            ALWAYS, null
        )
    );

    /**
     * Complete 20-atom {@code 1.0.3 <-> 1.1.0} Rule Provenance Trace table (19 independent
     * mechanism atoms + 1 derived UI/viewer presentation entry).
     */
    public static final List<RuleTraceEntry> RULE_TRACE_ENTRIES = List.of(
        new RuleTraceEntry("shelf_boost_model", CategoryId.BOOKSHELVES, "shelf_boost_model",
            ShelfBoostModel.CLASSIC_FLOOR_THIRD, ShelfBoostModel.MODERN_CURVE, false),
        new RuleTraceEntry("default_unlock_filter", CategoryId.BOOKSHELVES, "default_unlock_filter",
            DefaultUnlockFilter.CLASSIC_UNFILTERED, DefaultUnlockFilter.EXCLUDE_SPECIAL, false),
        new RuleTraceEntry("direct_tier_upgrade", CategoryId.BOOKSHELVES, "direct_tier_upgrade",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("cost_table", CategoryId.EXPERIENCE_ECONOMY, "cost_table",
            CostTableDataset.CLASSIC_1_0_3, CostTableDataset.MODERN_1_1_0, false),
        new RuleTraceEntry("material_selection", CategoryId.EXPERIENCE_ECONOMY, "material_selection",
            MaterialSelectionMode.PLAYER_SALTED_SINGLE, MaterialSelectionMode.ALL_OPTIONS_MIDPOINT, false),
        new RuleTraceEntry("xp_mode", CategoryId.EXPERIENCE_ECONOMY, "xp_mode",
            XpSettlementMode.CLASSIC_FULL_POINTS, XpSettlementMode.DUAL_STORED_DISCOUNT, false),
        new RuleTraceEntry("incremental_credit", CategoryId.EXPERIENCE_ECONOMY, "incremental_credit",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("research_rounding", CategoryId.EXPERIENCE_ECONOMY, "research_rounding",
            ResearchRoundingMode.FLOOR_DOWN, ResearchRoundingMode.ROUND_NEAREST, false),
        new RuleTraceEntry("library_discount", CategoryId.EXPERIENCE_ECONOMY, "library_discount",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("tome_wear_system", CategoryId.TOME_DURABILITY, "tome_consumption_mode",
            MasterTomeWear.ConsumptionMode.KEEP, MasterTomeWear.ConsumptionMode.WEAR, false),
        new RuleTraceEntry("tome_locks", CategoryId.TOME_DURABILITY, "tome_locks",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("provenance", CategoryId.TOME_DURABILITY, "provenance",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("ritual_queue", CategoryId.TOME_DURABILITY, "ritual_queue",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("epiphany_system", CategoryId.EPIPHANY, "epiphany_enabled",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("cleanse_system", CategoryId.CLEANSE_TRANSMUTE, "disenchant_enabled",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("transformation_set", CategoryId.CLEANSE_TRANSMUTE, "transformation_set",
            TransformationSetMode.CLASSIC_TWO, TransformationSetMode.FULL_SIX, false),
        new RuleTraceEntry("drained_book_result", CategoryId.CLEANSE_TRANSMUTE, "drained_book_result",
            DrainedBookResult.PLAIN_BOOK, DrainedBookResult.DEPLETED_TOME, false),
        new RuleTraceEntry("tome_page_sacrifice", CategoryId.CLEANSE_TRANSMUTE, "tome_page_enabled",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("librarian_offers", CategoryId.WORLD_LOOT, "librarian_tome_offers",
            Boolean.FALSE, Boolean.TRUE, false),
        new RuleTraceEntry("classic_presentation", CategoryId.GENERAL, null, null, null, true)
    );

    public static List<OptionEntry> optionsInCategory(CategoryId category) {
        return ALL_OPTIONS.stream().filter(o -> o.category() == category).toList();
    }

    public static Optional<OptionEntry> byKey(String key) {
        return ALL_OPTIONS.stream().filter(o -> o.key().equals(key)).findFirst();
    }

    public static Optional<RuleTraceEntry> traceByOptionKey(String optionKey) {
        if (optionKey == null) return Optional.empty();
        return RULE_TRACE_ENTRIES.stream()
            .filter(t -> optionKey.equals(t.optionKey()))
            .findFirst();
    }

    public static long countModified(Draft draft) {
        return ALL_OPTIONS.stream().filter(o -> o.isModifiedFromDefault(draft)).count();
    }

    public static long countModifiedInCategory(Draft draft, CategoryId category) {
        return ALL_OPTIONS.stream()
            .filter(o -> o.category() == category && o.isModifiedFromDefault(draft))
            .count();
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
        return String.format(Locale.ROOT, "%dm%02ds", sec / 60, sec % 60);
    }
}
