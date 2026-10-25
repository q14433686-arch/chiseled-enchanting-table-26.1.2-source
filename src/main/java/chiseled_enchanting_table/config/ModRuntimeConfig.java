package chiseled_enchanting_table.config;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Unified persistent & live-synchronized configuration backing both bootstrap settings
 * ({@code config/chiseled_enchanting_table.json}) and runtime gameplay rules across all mod
 * subsystems, with built-in cross-field coherence invariants and client/server synchronization.
 */
public record ModRuntimeConfig(
    // 1. General & Vanilla Compatibility
    boolean overrideDatapacks,
    int enchantedBookStackSize,
    VanillaTableMode vanillaTableMode,
    boolean anvilFlatRepairCost,
    boolean anvilBlockSameBookMerge,

    // 2. Bookshelf Progression & Breathing Room
    boolean directGatesEnabled,
    BlankMaxTierMode blankMaxTierMode,
    int maxDirectBookshelves,
    int horizontalScanRadius,
    int verticalScanRadius,
    boolean requireBreathingRoom,
    int minOpenAdjacentBlocks,
    boolean requireLineOfSight,

    // 3. Master Tomes & Enchanting Economy
    MasterTomeWear.ConsumptionMode tomeConsumptionMode,
    int maxTomeUses,
    int bookCopyWearCost,
    MasterTomeWear.DepleteBehavior tomeDepleteBehavior,
    boolean tomeRegenEnabled,
    int tomeRegenBaseSeconds,
    int tomeRegenTier1Shelves,
    int tomeRegenTier1Seconds,
    int tomeRegenTier2Shelves,
    int tomeRegenTier2Seconds,
    boolean lockedShelfBreakable,
    boolean lockedBookExtractionBlocked,
    int earlyUnlockXpLevels,
    int earlyUnlockGritCount,
    int sacrificeRequired,
    int tomePageRequiredShelves,
    int maxWoodenDiscountPercent,
    int maxTotalXpDiscountPercent,
    boolean bookCombineEnabled,
    double bookCombineXpMultiplier,
    boolean bookResearchEnabled,
    double bookResearchXpMultiplier,

    // 4. Treasure Epiphany & Cryo-Freeze
    int epiphanyMinBookshelves,
    double epiphanyBaseChance,
    double epiphanyMaxChance,
    int epiphanyMaxSlotsCap,
    int epiphanyCycleSeconds,
    int epiphanyFreezeXpLevels,
    int epiphanyFreezeDurationSeconds,
    int epiphanyMaxFreezeSeconds,
    int epiphanyRerollXpLevels,

    // 5. Cleanse, Provenance & Item Transformations
    boolean disenchantEnabled,
    double xpRefundScale,
    double externalXpRefundScale,
    int stripXpMin,
    int stripXpMax,
    boolean allowCursePurge,
    int cursePurgeXpCost,
    boolean requirePriorDiscovery,
    int tier1Bookshelves,
    int tier2Bookshelves,
    int tier3QualifyingEnchantments,
    int transformationMaxBatchSize,

    // 6. World Generation & Loot Tables
    VanillaChestBooksMode vanillaChestBooksMode,
    boolean bookshelfPoolEnabled,
    int bookshelfMinBooks,
    int bookshelfMaxBooks,
    double bookshelfEnchantedProb,

    // Global trade sanitisation master switch (audit: rewriting other mods' villager
    // Infinity book offers must be opt-outable by the server owner)
    boolean restrictVillagerInfinityBooks,

    // Grindstone returns a Depleted Tome instead of a plain book (core economy hook;
    // default ON per the 2026-10-24 audit disposition, opt-out for pack authors)
    boolean grindstoneDepletedTome,

    // Faithful 1.0.3 Classic Purist mode: restores 1.0.3 +W/3 shelf boost, instant
    // full-point enchanting, classic 2 transformations, plain Book drain, and hides
    // post-1.0.3 UI tabs & feature consoles while keeping new registry items save-safe.
    boolean classic103Mode,

    // Explicit per-field customization tracking (used when overrideDatapacks == false)
    Set<String> customizedKeys
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "chiseled_enchanting_table.json";

    public static final ModRuntimeConfig DEFAULT = new Draft().build();

    /**
     * Built-in one-click gameplay presets exposed in {@code ChiseledEnchantingConfigScreen}
     * and {@code /cet preset <id>}:
     * <ul>
     *   <li>{@link #CLASSIC_1_0_3}: 1:1 faithful restoration of release {@code 1.0.3};</li>
     *   <li>{@link #CASUAL}: relaxed progression with infinite Master Tomes and generous discounts;</li>
     *   <li>{@link #STANDARD}: shipped default balance ({@link #DEFAULT});</li>
     *   <li>{@link #EXPERT}: high-stakes archivist rules with LOS, discovery gates, and strict wear.</li>
     * </ul>
     */
    public enum Preset {
        CLASSIC_1_0_3("classic_1_0_3"),
        CASUAL("casual"),
        STANDARD("standard"),
        EXPERT("expert");

        private final String id;

        Preset(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public String buttonKey() {
            return "config.chiseled_enchanting_table.preset." + this.id;
        }

        public String tooltipKey() {
            return "config.chiseled_enchanting_table.preset." + this.id + ".desc";
        }

        @Nullable
        public static Preset fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "1.0.3", "classic", "classic_1_0_3", "103", "purist" -> CLASSIC_1_0_3;
                case "casual", "easy", "relaxed" -> CASUAL;
                case "standard", "default", "normal" -> STANDARD;
                case "expert", "hard", "archivist" -> EXPERT;
                default -> null;
            };
        }

        /**
         * Applies this preset onto {@code draft} with the canonical 64 Enchanted Book stack limit
         * (all 4 presets — including 1.0.3 Classic — natively use 64-stackable Enchanted Books,
         * which never shrinks container stacks) while preserving the user's {@code overrideDatapacks}
         * governance flag.
         */
        public void applyTo(Draft draft) {
            if (draft == null) {
                return;
            }
            boolean preservedOverrideDp = draft.overrideDatapacks;

            Draft fresh = new Draft();
            fresh.enchantedBookStackSize = 64;
            fresh.overrideDatapacks = preservedOverrideDp;

            switch (this) {
                case STANDARD -> {
                    // Pure shipped defaults (including 64-stackable Enchanted Books).
                }
                case CLASSIC_1_0_3 -> {
                    fresh.classic103Mode = true;
                    fresh.vanillaTableMode = VanillaTableMode.BOOKS;
                    fresh.anvilFlatRepairCost = false;
                    fresh.anvilBlockSameBookMerge = true;
                    fresh.restrictVillagerInfinityBooks = false;
                    fresh.grindstoneDepletedTome = false;

                    fresh.directGatesEnabled = false;
                    fresh.blankMaxTierMode = BlankMaxTierMode.FULL_CURVE;
                    fresh.horizontalScanRadius = 2;
                    fresh.verticalScanRadius = 2;
                    fresh.requireBreathingRoom = false;
                    fresh.requireLineOfSight = false;

                    fresh.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
                    fresh.tomeRegenEnabled = false;
                    fresh.lockedShelfBreakable = true;
                    fresh.lockedBookExtractionBlocked = false;
                    fresh.maxWoodenDiscountPercent = 0;
                    fresh.maxTotalXpDiscountPercent = 0;
                    fresh.bookCombineEnabled = true;
                    fresh.bookCombineXpMultiplier = 1.00;
                    fresh.bookResearchEnabled = true;
                    fresh.bookResearchXpMultiplier = 0.50;

                    fresh.epiphanyBaseChance = 0.0;
                    fresh.epiphanyMaxChance = 0.0;

                    fresh.disenchantEnabled = false;
                    fresh.allowCursePurge = false;
                    fresh.requirePriorDiscovery = false;
                    fresh.transformationMaxBatchSize = 1;

                    fresh.vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
                    fresh.bookshelfPoolEnabled = true;
                    fresh.bookshelfEnchantedProb = 0.03;
                    markModifiedRuntimeKeys(fresh);
                }
                case CASUAL -> {
                    fresh.classic103Mode = false;
                    fresh.vanillaTableMode = VanillaTableMode.BOOKS;
                    fresh.anvilFlatRepairCost = true;
                    fresh.anvilBlockSameBookMerge = false;
                    fresh.restrictVillagerInfinityBooks = false;
                    fresh.grindstoneDepletedTome = false;

                    fresh.directGatesEnabled = false;
                    fresh.blankMaxTierMode = BlankMaxTierMode.FULL_CURVE;
                    fresh.horizontalScanRadius = 4;
                    fresh.verticalScanRadius = 2;
                    fresh.requireBreathingRoom = false;
                    fresh.requireLineOfSight = false;

                    fresh.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
                    fresh.tomeRegenEnabled = true;
                    fresh.tomeRegenBaseSeconds = 3_600;
                    fresh.tomeRegenTier1Seconds = 1_800;
                    fresh.tomeRegenTier2Seconds = 600;
                    fresh.lockedShelfBreakable = true;
                    fresh.lockedBookExtractionBlocked = false;
                    fresh.maxWoodenDiscountPercent = 20;
                    fresh.maxTotalXpDiscountPercent = 50;
                    fresh.bookCombineEnabled = true;
                    fresh.bookCombineXpMultiplier = 0.50;
                    fresh.bookResearchEnabled = true;
                    fresh.bookResearchXpMultiplier = 1.00;

                    fresh.epiphanyMinBookshelves = 16;
                    fresh.epiphanyBaseChance = 0.45;
                    fresh.epiphanyMaxChance = 0.95;

                    fresh.disenchantEnabled = true;
                    fresh.xpRefundScale = 1.00;
                    fresh.externalXpRefundScale = 0.50;
                    fresh.allowCursePurge = true;
                    fresh.cursePurgeXpCost = 6;
                    fresh.requirePriorDiscovery = false;
                    markModifiedRuntimeKeys(fresh);
                }
                case EXPERT -> {
                    fresh.classic103Mode = false;
                    fresh.vanillaTableMode = VanillaTableMode.ALIGN_CAPS;
                    fresh.anvilFlatRepairCost = false;
                    fresh.anvilBlockSameBookMerge = true;
                    fresh.restrictVillagerInfinityBooks = true;
                    fresh.grindstoneDepletedTome = true;

                    fresh.directGatesEnabled = true;
                    fresh.blankMaxTierMode = BlankMaxTierMode.LEVEL_ONE_ONLY;
                    fresh.maxDirectBookshelves = 42;
                    fresh.horizontalScanRadius = 3;
                    fresh.verticalScanRadius = 2;
                    fresh.requireBreathingRoom = true;
                    fresh.minOpenAdjacentBlocks = 8;
                    fresh.requireLineOfSight = true;

                    fresh.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
                    fresh.maxTomeUses = 3;
                    fresh.bookCopyWearCost = 2;
                    fresh.tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.DEPLETED_TOME;
                    fresh.tomeRegenEnabled = true;
                    fresh.lockedShelfBreakable = false;
                    fresh.lockedBookExtractionBlocked = true;
                    fresh.maxWoodenDiscountPercent = 12;
                    fresh.maxTotalXpDiscountPercent = 30;
                    fresh.bookCombineEnabled = true;
                    fresh.bookCombineXpMultiplier = 0.85;
                    fresh.bookResearchEnabled = true;
                    fresh.bookResearchXpMultiplier = 1.75;

                    fresh.epiphanyMinBookshelves = 36;
                    fresh.epiphanyBaseChance = 0.22;
                    fresh.epiphanyMaxChance = 0.80;

                    fresh.disenchantEnabled = true;
                    fresh.xpRefundScale = 0.75;
                    fresh.externalXpRefundScale = 0.15;
                    fresh.allowCursePurge = true;
                    fresh.cursePurgeXpCost = 18;
                    fresh.requirePriorDiscovery = true;
                    markModifiedRuntimeKeys(fresh);
                }
            }

            fresh.normalizeAfterEdit(null);
            draft.copyFrom(fresh);
        }

        public ModRuntimeConfig createConfig(boolean overrideDp) {
            Draft probe = new Draft();
            probe.overrideDatapacks = overrideDp;
            applyTo(probe);
            return probe.build();
        }

        public boolean matches(Draft draft) {
            if (draft == null) {
                return false;
            }
            return draft.build().equals(createConfig(draft.overrideDatapacks));
        }

        private static void markModifiedRuntimeKeys(Draft target) {
            Draft def = new Draft();
            for (var opt : ConfigCatalog.ALL_OPTIONS) {
                if ("override_datapacks".equals(opt.key()) || "enchanted_book_stack_size".equals(opt.key())) {
                    continue;
                }
                if (!java.util.Objects.equals(opt.get(target), opt.get(def))) {
                    target.customizedKeys.add(opt.key());
                }
            }
        }
    }

    private static volatile ModRuntimeConfig localInstance;
    @Nullable
    private static volatile ModRuntimeConfig remoteServerInstance;
    private static volatile ModRuntimeConfig remotePreferences;
    public static ModRuntimeConfig preferences() { return remotePreferences != null ? remotePreferences : get(); }
    public static void setRemotePreferences(ModRuntimeConfig value) { remotePreferences = value; }
    private static volatile boolean clientCanEditServerConfig = false;
    private static volatile PackPolicy serverPackPolicy = PackPolicy.NONE;
    @Nullable
    private static volatile PackPolicy remotePackPolicy;

    /**
     * Authoritative governance policy loaded from datapacks ({@code pack_policy.json} plus any
     * keys explicitly specified by external datapacks).
     */
    public record PackPolicy(
        boolean enforceDatapackPriority,
        boolean lockInGameConfig,
        Set<String> datapackOverriddenKeys,
        Set<String> lockedKeys
    ) {
        public static final PackPolicy NONE = new PackPolicy(false, false, Set.of(), Set.of());

        public PackPolicy {
            datapackOverriddenKeys = datapackOverriddenKeys == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(datapackOverriddenKeys));
            lockedKeys = lockedKeys == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(lockedKeys));
        }

        public boolean isKeyLocked(String key) {
            if (lockInGameConfig) {
                return true;
            }
            if ("override_datapacks".equals(key) && enforceDatapackPriority) {
                return true;
            }
            return lockedKeys.contains(key);
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("enforce_datapack_priority", enforceDatapackPriority);
            obj.addProperty("lock_in_game_config", lockInGameConfig);
            com.google.gson.JsonArray dpArr = new com.google.gson.JsonArray();
            for (String k : datapackOverriddenKeys) dpArr.add(k);
            obj.add("datapack_overridden_keys", dpArr);
            com.google.gson.JsonArray lockArr = new com.google.gson.JsonArray();
            for (String k : lockedKeys) lockArr.add(k);
            obj.add("locked_keys", lockArr);
            return obj;
        }

        public String toJsonString() {
            return GSON.toJson(toJson());
        }

        public static PackPolicy fromJson(JsonObject root, Set<String> externalDatapackKeys) {
            if (root != null) {
                for (String key : Set.of("enforce_datapack_priority", "lock_in_game_config")) {
                    if (root.has(key) && (!root.get(key).isJsonPrimitive()
                        || !root.getAsJsonPrimitive(key).isBoolean()))
                        throw new IllegalArgumentException("Policy flag must be boolean: " + key);
                }
                for (String key : Set.of("locked_keys", "datapack_overridden_keys")) {
                    if (!root.has(key)) continue;
                    if (!root.get(key).isJsonArray()) throw new IllegalArgumentException("Policy keys must be array: " + key);
                    for (var element : root.getAsJsonArray(key)) {
                        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Policy key must be a string");
                        if (ConfigCatalog.ALL_OPTIONS.stream().noneMatch(option -> option.key().equals(element.getAsString())))
                            throw new IllegalArgumentException("Unknown policy key: " + element.getAsString());
                    }
                }
            }
            boolean enforceDp = root != null && boolOr(root, "enforce_datapack_priority", false);
            boolean lockAll = root != null && boolOr(root, "lock_in_game_config", false);
            Set<String> dpKeys = new LinkedHashSet<>();
            if (externalDatapackKeys != null) {
                dpKeys.addAll(externalDatapackKeys);
            }
            if (root != null && root.has("datapack_overridden_keys") && root.get("datapack_overridden_keys").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("datapack_overridden_keys")) {
                    if (el.isJsonPrimitive()) dpKeys.add(el.getAsString());
                }
            }
            Set<String> locked = new LinkedHashSet<>();
            if (root != null && root.has("locked_keys") && root.get("locked_keys").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("locked_keys")) {
                    if (el.isJsonPrimitive()) locked.add(el.getAsString());
                }
            }
            return new PackPolicy(enforceDp, lockAll, dpKeys, locked);
        }

        public static PackPolicy fromJsonString(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            return fromJson(JsonParser.parseString(raw).getAsJsonObject(), Set.of());
        }
    }

    public ModRuntimeConfig {
        // 1.0.3 Classic semantics (third-round audit decision): classic_1_0_3_mode
        // is a MODE flag plus an editable value profile, not a construction-time
        // rewrite. The preset button and the config-screen toggle apply the 1.0.3
        // profile once via Draft#applyClassic103Overrides; afterwards every value
        // stays editable on the config screen's Classic merged page and divergences
        // simply make it a "custom ruleset based on 1.0.3". Hard mechanism gates
        // (no Epiphany rolls, no ritual slip, no cleanse, salted 1.0.3 pricing)
        // key on classic103Mode itself in the handler / runtime frame, so they
        // cannot be resurrected by editing a subordinate value. In particular
        // enchanted_book_stack_size is a startup-only item component: forcing it
        // here broke RuleBook's bootstrap validation (R2-01) — the preset only
        // SUGGESTS 64 in the draft, effective after restart.
        enchantedBookStackSize = clampInt(enchantedBookStackSize, 1, 64);
        vanillaTableMode = vanillaTableMode != null ? vanillaTableMode : VanillaTableMode.BOOKS;
        blankMaxTierMode = blankMaxTierMode != null ? blankMaxTierMode : BlankMaxTierMode.HALF_CEIL;
        maxDirectBookshelves = clampInt(maxDirectBookshelves, 4, 64);
        horizontalScanRadius = clampInt(horizontalScanRadius, 1, BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS);
        verticalScanRadius = clampInt(verticalScanRadius, 0, BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS);
        while (verticalScanRadius > 0
            && BookshelfProgression.scanVolumeBlocks(horizontalScanRadius, verticalScanRadius)
                > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
            verticalScanRadius--;
        }
        while (horizontalScanRadius > 1
            && BookshelfProgression.scanVolumeBlocks(horizontalScanRadius, verticalScanRadius)
                > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
            horizontalScanRadius--;
        }
        minOpenAdjacentBlocks = clampInt(minOpenAdjacentBlocks, 1, 17);

        tomeConsumptionMode = tomeConsumptionMode != null ? tomeConsumptionMode : MasterTomeWear.ConsumptionMode.WEAR;
        maxTomeUses = clampInt(maxTomeUses, 1, 256);
        bookCopyWearCost = clampInt(bookCopyWearCost, 1, maxTomeUses);
        tomeDepleteBehavior = tomeDepleteBehavior != null ? tomeDepleteBehavior : MasterTomeWear.DepleteBehavior.STEP_DOWN;
        tomeRegenBaseSeconds = clampInt(tomeRegenBaseSeconds, 1, 86_400);
        tomeRegenTier1Shelves = clampInt(tomeRegenTier1Shelves, 1, 128);
        tomeRegenTier1Seconds = clampInt(tomeRegenTier1Seconds, 1, tomeRegenBaseSeconds);
        tomeRegenTier2Shelves = clampInt(tomeRegenTier2Shelves, tomeRegenTier1Shelves, 128);
        tomeRegenTier2Seconds = clampInt(tomeRegenTier2Seconds, 1, tomeRegenTier1Seconds);
        maxWoodenDiscountPercent = clampInt(maxWoodenDiscountPercent, 0, 90);
        maxTotalXpDiscountPercent = clampInt(maxTotalXpDiscountPercent, maxWoodenDiscountPercent, 90);
        bookCombineXpMultiplier = round2(clampDouble(bookCombineXpMultiplier, 0.10, 3.00));
        bookResearchXpMultiplier = round2(clampDouble(bookResearchXpMultiplier, 0.10, 3.00));

        epiphanyMinBookshelves = clampInt(epiphanyMinBookshelves, 0, 64);
        epiphanyBaseChance = round2(clampDouble(epiphanyBaseChance, 0.0, 1.0));
        epiphanyMaxChance = round2(clampDouble(epiphanyMaxChance, epiphanyBaseChance, 1.0));
        epiphanyMaxSlotsCap = clampInt(epiphanyMaxSlotsCap, 1, 8);
        epiphanyCycleSeconds = clampInt(epiphanyCycleSeconds, 10, 3600);
        epiphanyFreezeXpLevels = clampInt(epiphanyFreezeXpLevels, 0, 30);
        epiphanyFreezeDurationSeconds = clampInt(epiphanyFreezeDurationSeconds, 10, 3600);
        epiphanyMaxFreezeSeconds = clampInt(epiphanyMaxFreezeSeconds, epiphanyFreezeDurationSeconds, 7200);
        epiphanyRerollXpLevels = clampInt(epiphanyRerollXpLevels, 0, 30);

        xpRefundScale = round2(clampDouble(xpRefundScale, 0.0, 1.0));
        externalXpRefundScale = round2(clampDouble(externalXpRefundScale, 0.0, xpRefundScale));
        stripXpMin = clampInt(stripXpMin, 1, 30);
        stripXpMax = clampInt(stripXpMax, stripXpMin, 50);
        cursePurgeXpCost = clampInt(cursePurgeXpCost, 1, 50);
        tier2Bookshelves = clampInt(tier2Bookshelves, 0, maxDirectBookshelves);
        tier1Bookshelves = clampInt(tier1Bookshelves, 0, tier2Bookshelves);
        tier3QualifyingEnchantments = clampInt(tier3QualifyingEnchantments, 1, 30);
        transformationMaxBatchSize = clampInt(transformationMaxBatchSize, 1, 64);

        vanillaChestBooksMode = vanillaChestBooksMode != null ? vanillaChestBooksMode : VanillaChestBooksMode.KEEP_BOTH;
        bookshelfMinBooks = clampInt(bookshelfMinBooks, 0, 6);
        bookshelfMaxBooks = clampInt(bookshelfMaxBooks, bookshelfMinBooks, 6);
        bookshelfEnchantedProb = round2(clampDouble(bookshelfEnchantedProb, 0.0, 1.0));

        customizedKeys = customizedKeys == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(customizedKeys));
    }

    public ModRuntimeConfig withEnforcedPolicy(PackPolicy policy) {
        if (policy == null || !policy.enforceDatapackPriority() || !this.overrideDatapacks) {
            return this;
        }
        Draft d = this.toDraft();
        d.overrideDatapacks = false;
        d.customizedKeys.remove("override_datapacks");
        return d.build();
    }

    public boolean shouldApplyKey(String key) {
        PackPolicy policy = effectivePackPolicy();
        if (policy.isKeyLocked(key)) {
            return false;
        }
        if (policy.datapackOverriddenKeys().contains(key)) {
            return !policy.enforceDatapackPriority() && overrideDatapacks;
        }
        return true;
    }

    public static PackPolicy serverPackPolicy() {
        return serverPackPolicy;
    }

    public static void setServerPackPolicy(PackPolicy policy) {
        serverPackPolicy = policy != null ? policy : PackPolicy.NONE;
    }

    public static PackPolicy effectivePackPolicy() {
        PackPolicy remote = remotePackPolicy;
        return remote != null ? remote : serverPackPolicy;
    }

    public Draft toDraft() {
        return new Draft(this);
    }

    // ---- Static lifecycle & active-session accessors ----

    public static ModRuntimeConfig get() {
        ModRuntimeConfig cur = localInstance;
        if (cur == null) {
            synchronized (ModRuntimeConfig.class) {
                cur = localInstance;
                if (cur == null) {
                    cur = loadFromDiskOrWriteDefault();
                    localInstance = cur;
                }
            }
        }
        return cur;
    }

    /** Returns server-synced config when connected to a multiplayer server, otherwise local config. */
    public static ModRuntimeConfig effective() {
        ModRuntimeConfig remote = remoteServerInstance;
        return remote != null ? remote : ChiseledEnchantingTableData.runtimeConfiguration();
    }

    /**
     * Side-aware authoritative config accessor: server threads (Dedicated Server and LAN/Singleplayer
     * IntegratedServer) always read {@link ChiseledEnchantingTableData#runtimeConfiguration()}, while
     * client threads read the server-synced {@link #effective()} snapshot.
     */
    public static ModRuntimeConfig forSide(boolean isClientSide) {
        return isClientSide ? effective() : ChiseledEnchantingTableData.runtimeConfiguration();
    }

    /**
     * Updates {@link #remoteServerInstance} immediately on the same JVM when an IntegratedServer
     * (Singleplayer / Open-to-LAN host) commits a config change, eliminating any 1-tick client/server
     * snapshot lag before the loopback sync packet executes on the render thread.
     */
    public static void refreshSameJvmRemoteIfPresent(ModRuntimeConfig effectiveSynced, PackPolicy policy) {
        if (remoteServerInstance != null) {
            remoteServerInstance = effectiveSynced;
            remotePackPolicy = policy;
        }
    }

    /**
     * Side-isolated config accessor for dual-side menu mixins (such as {@code AnvilMenu}):
     * server threads always read {@link ChiseledEnchantingTableData#runtimeConfiguration()}, while
     * client threads read the server-synced snapshot (returning {@code null} during the brief
     * pre-sync join window so client prediction never diverges from an unsynced local disk file).
     */
    @Nullable
    public static ModRuntimeConfig forAnvilSide(boolean isClientSide) {
        if (!isClientSide) {
            return ChiseledEnchantingTableData.runtimeConfiguration();
        }
        return remoteServerInstance;
    }

    public static void setLocalAndSave(ModRuntimeConfig updated) {
        ModRuntimeConfig normalized = updated != null ? updated : DEFAULT;
        saveToDisk(normalized); // Commit persisted state before publishing the new in-memory value.
        localInstance = normalized;
    }

    /** Explicit reload must reject malformed input, not silently replace it with defaults. */
    public static ModRuntimeConfig reloadFromDisk() {
        try (Reader reader = Files.newBufferedReader(configFilePath(), StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Cannot reload " + FILE_NAME, error);
        }
    }

    public static void applyRemoteSync(ModRuntimeConfig remote, PackPolicy policy, boolean canEdit) {
        remoteServerInstance = remote;
        remotePackPolicy = policy;
        clientCanEditServerConfig = canEdit;
    }

    public static void applyRemoteSync(ModRuntimeConfig remote, boolean canEdit) {
        applyRemoteSync(remote, null, canEdit);
    }

    public static void clearRemoteSync() {
        remoteServerInstance = null;
        remotePreferences = null;
        remotePackPolicy = null;
        clientCanEditServerConfig = false;
        acknowledgedSaveId = 0;
    }

    public static boolean clientCanEditServerConfig() {
        return clientCanEditServerConfig;
    }

    private static Path configFilePath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve(FILE_NAME);
    }

    private static ModRuntimeConfig loadFromDiskOrWriteDefault() {
        try {
            Path path = configFilePath();
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    ModRuntimeConfig parsed = fromJson(obj);
                    saveToDisk(parsed);
                    return parsed;
                }
            }
            saveToDisk(DEFAULT);
            return DEFAULT;
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to read {}; using built-in defaults", FILE_NAME, e);
            return DEFAULT;
        }
    }

    /** Exposed path overload allows failure/atomic-replacement tests without Fabric bootstrap. */
    public static void writeAtomically(Path path, ModRuntimeConfig cfg) throws java.io.IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        String json = GSON.toJson(cfg.toJson()) + "\n";
        Path temporary = Files.createTempFile(parent, ".cet-config-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (java.io.IOException atomicFailure) {
                // Windows rejects ATOMIC_MOVE over an existing target with AccessDenied when
                // the file is read-only or briefly held by an antivirus/launcher sync. Fall
                // back to progressively weaker replacements instead of failing the save.
                clearWindowsReadOnly(path);
                java.io.IOException lastFailure = atomicFailure;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return;
                    } catch (java.io.IOException moveFailure) {
                        lastFailure = moveFailure;
                        pauseBeforeRetry(attempt);
                    }
                }
                try {
                    Files.copy(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (java.io.IOException copyFailure) {
                    lastFailure = copyFailure;
                }
                // Last resort: write the target directly (throws if truly impossible,
                // e.g. the target is a directory - which tests rely on staying intact).
                try {
                    Files.writeString(path, json, StandardCharsets.UTF_8);
                } catch (java.io.IOException directFailure) {
                    directFailure.addSuppressed(lastFailure);
                    throw directFailure;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void clearWindowsReadOnly(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                Files.setAttribute(path, "dos:readonly", false);
            }
        } catch (Exception ignored) {
            // Non-Windows filesystems do not support the DOS view; nothing to clear there.
        }
    }

    private static void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(25L * (attempt + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void saveToDisk(ModRuntimeConfig cfg) {
        try {
            writeAtomically(configFilePath(), cfg);
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Cannot save " + FILE_NAME, error);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong SAVE_REQUEST_IDS = new java.util.concurrent.atomic.AtomicLong();
    public static long nextSaveRequestId() { return SAVE_REQUEST_IDS.incrementAndGet(); }
    private static volatile long acknowledgedSaveId;
    private static volatile boolean acknowledgedSaveAccepted;
    public static void acknowledgeSave(long id, boolean accepted) {
        acknowledgedSaveAccepted = accepted;
        acknowledgedSaveId = id;
    }
    public static long acknowledgedSaveId() { return acknowledgedSaveId; }
    public static boolean acknowledgedSaveAccepted() { return acknowledgedSaveAccepted; }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    public static ModRuntimeConfig fromJsonString(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return fromJson(JsonParser.parseString(raw).getAsJsonObject());
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(
            "_comment",
            "Chiseled Enchanting Table unified configuration. Editable directly in-game via the Config button, /cet config, or Mod Menu."
        );
        // Keep root-level enchanted_book_stack_size for seamless backwards compatibility.
        root.addProperty("enchanted_book_stack_size", enchantedBookStackSize);

        JsonObject general = new JsonObject();
        general.addProperty("override_datapacks", overrideDatapacks);
        general.addProperty("enchanted_book_stack_size", enchantedBookStackSize);
        general.addProperty("vanilla_table_mode", vanillaTableMode.token());
        general.addProperty("anvil_flat_repair_cost", anvilFlatRepairCost);
        general.addProperty("anvil_block_same_book_merge", anvilBlockSameBookMerge);
        general.addProperty("restrict_villager_infinity_books", restrictVillagerInfinityBooks);
        general.addProperty("grindstone_depleted_tome", grindstoneDepletedTome);
        general.addProperty("classic_1_0_3_mode", classic103Mode);
        root.add("general", general);

        JsonObject bookshelves = new JsonObject();
        bookshelves.addProperty("direct_gates_enabled", directGatesEnabled);
        bookshelves.addProperty("blank_max_tier_mode", blankMaxTierMode.token());
        bookshelves.addProperty("max_direct_bookshelves", maxDirectBookshelves);
        bookshelves.addProperty("horizontal_scan_radius", horizontalScanRadius);
        bookshelves.addProperty("vertical_scan_radius", verticalScanRadius);
        bookshelves.addProperty("require_breathing_room", requireBreathingRoom);
        bookshelves.addProperty("min_open_adjacent_blocks", minOpenAdjacentBlocks);
        bookshelves.addProperty("require_line_of_sight", requireLineOfSight);
        root.add("bookshelves", bookshelves);

        JsonObject tomes = new JsonObject();
        tomes.addProperty("tome_consumption_mode", tomeConsumptionMode.token());
        tomes.addProperty("max_tome_uses", maxTomeUses);
        tomes.addProperty("book_copy_wear_cost", bookCopyWearCost);
        tomes.addProperty("tome_deplete_behavior", tomeDepleteBehavior.token());
        tomes.addProperty("tome_regen_enabled", tomeRegenEnabled);
        tomes.addProperty("tome_regen_base_seconds", tomeRegenBaseSeconds);
        tomes.addProperty("tome_regen_tier1_shelves", tomeRegenTier1Shelves);
        tomes.addProperty("tome_regen_tier1_seconds", tomeRegenTier1Seconds);
        tomes.addProperty("tome_regen_tier2_shelves", tomeRegenTier2Shelves);
        tomes.addProperty("tome_regen_tier2_seconds", tomeRegenTier2Seconds);
        tomes.addProperty("locked_shelf_breakable", lockedShelfBreakable);
        tomes.addProperty("locked_book_extraction_blocked", lockedBookExtractionBlocked);
        tomes.addProperty("early_unlock_xp_levels", earlyUnlockXpLevels);
        tomes.addProperty("early_unlock_grit_count", earlyUnlockGritCount);
        tomes.addProperty("sacrifice_required", sacrificeRequired);
        tomes.addProperty("tome_page_required_shelves", tomePageRequiredShelves);
        tomes.addProperty("max_wooden_discount_percent", maxWoodenDiscountPercent);
        tomes.addProperty("max_total_xp_discount_percent", maxTotalXpDiscountPercent);
        tomes.addProperty("book_combine_enabled", bookCombineEnabled);
        tomes.addProperty("book_combine_xp_multiplier", bookCombineXpMultiplier);
        tomes.addProperty("book_research_enabled", bookResearchEnabled);
        tomes.addProperty("book_research_xp_multiplier", bookResearchXpMultiplier);
        root.add("tomes_and_economy", tomes);

        JsonObject epiphany = new JsonObject();
        epiphany.addProperty("epiphany_min_bookshelves", epiphanyMinBookshelves);
        epiphany.addProperty("epiphany_base_chance", epiphanyBaseChance);
        epiphany.addProperty("epiphany_max_chance", epiphanyMaxChance);
        epiphany.addProperty("epiphany_max_slots_cap", epiphanyMaxSlotsCap);
        epiphany.addProperty("epiphany_cycle_seconds", epiphanyCycleSeconds);
        epiphany.addProperty("epiphany_freeze_xp_levels", epiphanyFreezeXpLevels);
        epiphany.addProperty("epiphany_freeze_duration_seconds", epiphanyFreezeDurationSeconds);
        epiphany.addProperty("epiphany_max_freeze_seconds", epiphanyMaxFreezeSeconds);
        epiphany.addProperty("epiphany_reroll_xp_levels", epiphanyRerollXpLevels);
        root.add("treasure_epiphany", epiphany);

        JsonObject cleanse = new JsonObject();
        cleanse.addProperty("disenchant_enabled", disenchantEnabled);
        cleanse.addProperty("xp_refund_scale", xpRefundScale);
        cleanse.addProperty("external_xp_refund_scale", externalXpRefundScale);
        cleanse.addProperty("strip_xp_min", stripXpMin);
        cleanse.addProperty("strip_xp_max", stripXpMax);
        cleanse.addProperty("allow_curse_purge", allowCursePurge);
        cleanse.addProperty("curse_purge_xp_cost", cursePurgeXpCost);
        cleanse.addProperty("require_prior_discovery", requirePriorDiscovery);
        cleanse.addProperty("tier1_bookshelves", tier1Bookshelves);
        cleanse.addProperty("tier2_bookshelves", tier2Bookshelves);
        cleanse.addProperty("tier3_qualifying_enchantments", tier3QualifyingEnchantments);
        cleanse.addProperty("transformation_max_batch_size", transformationMaxBatchSize);
        root.add("cleanse_and_transformations", cleanse);

        JsonObject world = new JsonObject();
        world.addProperty("vanilla_chest_books_mode", vanillaChestBooksMode.token());
        world.addProperty("bookshelf_pool_enabled", bookshelfPoolEnabled);
        world.addProperty("bookshelf_min_books", bookshelfMinBooks);
        world.addProperty("bookshelf_max_books", bookshelfMaxBooks);
        world.addProperty("bookshelf_enchanted_prob", bookshelfEnchantedProb);
        root.add("world_and_loot", world);

        com.google.gson.JsonArray customized = new com.google.gson.JsonArray();
        for (String key : customizedKeys) {
            customized.add(key);
        }
        root.add("customized_keys", customized);
        return root;
    }

    public static ModRuntimeConfig fromJson(JsonObject root) {
        if (root == null) {
            return DEFAULT;
        }
        Draft d = new Draft();
        JsonObject general = objOrEmpty(root, "general");
        JsonObject shelves = objOrEmpty(root, "bookshelves");
        JsonObject tomes = objOrEmpty(root, "tomes_and_economy");
        JsonObject epiphany = objOrEmpty(root, "treasure_epiphany");
        JsonObject cleanse = objOrEmpty(root, "cleanse_and_transformations");
        JsonObject world = objOrEmpty(root, "world_and_loot");

        d.overrideDatapacks = boolOr(general, "override_datapacks", d.overrideDatapacks);
        d.enchantedBookStackSize = intOr(
            general,
            "enchanted_book_stack_size",
            intOr(root, "enchanted_book_stack_size", d.enchantedBookStackSize)
        );
        if (general.has("vanilla_table_mode")) {
            d.vanillaTableMode = VanillaTableMode.fromToken(general.get("vanilla_table_mode").getAsString());
        }
        d.anvilFlatRepairCost = boolOr(general, "anvil_flat_repair_cost", d.anvilFlatRepairCost);
        d.anvilBlockSameBookMerge = boolOr(general, "anvil_block_same_book_merge", d.anvilBlockSameBookMerge);
        d.restrictVillagerInfinityBooks = boolOr(general, "restrict_villager_infinity_books", d.restrictVillagerInfinityBooks);
        d.grindstoneDepletedTome = boolOr(general, "grindstone_depleted_tome", d.grindstoneDepletedTome);
        d.classic103Mode = boolOr(general, "classic_1_0_3_mode", d.classic103Mode);

        d.directGatesEnabled = boolOr(shelves, "direct_gates_enabled", d.directGatesEnabled);
        if (shelves.has("blank_max_tier_mode")) {
            d.blankMaxTierMode = BlankMaxTierMode.fromToken(shelves.get("blank_max_tier_mode").getAsString());
        }
        d.maxDirectBookshelves = intOr(shelves, "max_direct_bookshelves", d.maxDirectBookshelves);
        d.horizontalScanRadius = intOr(shelves, "horizontal_scan_radius", d.horizontalScanRadius);
        d.verticalScanRadius = intOr(shelves, "vertical_scan_radius", d.verticalScanRadius);
        d.requireBreathingRoom = boolOr(shelves, "require_breathing_room", d.requireBreathingRoom);
        d.minOpenAdjacentBlocks = intOr(shelves, "min_open_adjacent_blocks", d.minOpenAdjacentBlocks);
        d.requireLineOfSight = boolOr(shelves, "require_line_of_sight", d.requireLineOfSight);

        if (tomes.has("tome_consumption_mode")) {
            d.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.fromToken(
                tomes.get("tome_consumption_mode").getAsString()
            );
        }
        d.maxTomeUses = intOr(tomes, "max_tome_uses", d.maxTomeUses);
        d.bookCopyWearCost = intOr(tomes, "book_copy_wear_cost", d.bookCopyWearCost);
        if (tomes.has("tome_deplete_behavior")) {
            d.tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.fromToken(
                tomes.get("tome_deplete_behavior").getAsString()
            );
        }
        d.tomeRegenEnabled = boolOr(tomes, "tome_regen_enabled", d.tomeRegenEnabled);
        d.tomeRegenBaseSeconds = intOr(tomes, "tome_regen_base_seconds", d.tomeRegenBaseSeconds);
        d.tomeRegenTier1Shelves = intOr(tomes, "tome_regen_tier1_shelves", d.tomeRegenTier1Shelves);
        d.tomeRegenTier1Seconds = intOr(tomes, "tome_regen_tier1_seconds", d.tomeRegenTier1Seconds);
        d.tomeRegenTier2Shelves = intOr(tomes, "tome_regen_tier2_shelves", d.tomeRegenTier2Shelves);
        d.tomeRegenTier2Seconds = intOr(tomes, "tome_regen_tier2_seconds", d.tomeRegenTier2Seconds);
        d.lockedShelfBreakable = boolOr(tomes, "locked_shelf_breakable", d.lockedShelfBreakable);
        d.lockedBookExtractionBlocked = boolOr(tomes, "locked_book_extraction_blocked", d.lockedBookExtractionBlocked);
        d.earlyUnlockXpLevels = intOr(tomes, "early_unlock_xp_levels", d.earlyUnlockXpLevels);
        d.earlyUnlockGritCount = intOr(tomes, "early_unlock_grit_count", d.earlyUnlockGritCount);
        d.sacrificeRequired = intOr(tomes, "sacrifice_required", d.sacrificeRequired);
        d.tomePageRequiredShelves = intOr(tomes, "tome_page_required_shelves", d.tomePageRequiredShelves);
        d.maxWoodenDiscountPercent = intOr(tomes, "max_wooden_discount_percent", d.maxWoodenDiscountPercent);
        d.maxTotalXpDiscountPercent = intOr(tomes, "max_total_xp_discount_percent", d.maxTotalXpDiscountPercent);
        d.bookCombineEnabled = boolOr(tomes, "book_combine_enabled", d.bookCombineEnabled);
        d.bookCombineXpMultiplier = doubleOr(tomes, "book_combine_xp_multiplier", d.bookCombineXpMultiplier);
        d.bookResearchEnabled = boolOr(tomes, "book_research_enabled", d.bookResearchEnabled);
        d.bookResearchXpMultiplier = doubleOr(tomes, "book_research_xp_multiplier", d.bookResearchXpMultiplier);

        d.epiphanyMinBookshelves = intOr(epiphany, "epiphany_min_bookshelves", d.epiphanyMinBookshelves);
        d.epiphanyBaseChance = doubleOr(epiphany, "epiphany_base_chance", d.epiphanyBaseChance);
        d.epiphanyMaxChance = doubleOr(epiphany, "epiphany_max_chance", d.epiphanyMaxChance);
        d.epiphanyMaxSlotsCap = intOr(epiphany, "epiphany_max_slots_cap", d.epiphanyMaxSlotsCap);
        d.epiphanyCycleSeconds = intOr(epiphany, "epiphany_cycle_seconds", d.epiphanyCycleSeconds);
        d.epiphanyFreezeXpLevels = intOr(epiphany, "epiphany_freeze_xp_levels", d.epiphanyFreezeXpLevels);
        d.epiphanyFreezeDurationSeconds = intOr(epiphany, "epiphany_freeze_duration_seconds", d.epiphanyFreezeDurationSeconds);
        d.epiphanyMaxFreezeSeconds = intOr(epiphany, "epiphany_max_freeze_seconds", d.epiphanyMaxFreezeSeconds);
        d.epiphanyRerollXpLevels = intOr(epiphany, "epiphany_reroll_xp_levels", d.epiphanyRerollXpLevels);

        d.disenchantEnabled = boolOr(cleanse, "disenchant_enabled", d.disenchantEnabled);
        d.xpRefundScale = doubleOr(cleanse, "xp_refund_scale", d.xpRefundScale);
        d.externalXpRefundScale = doubleOr(cleanse, "external_xp_refund_scale", d.externalXpRefundScale);
        d.stripXpMin = intOr(cleanse, "strip_xp_min", d.stripXpMin);
        d.stripXpMax = intOr(cleanse, "strip_xp_max", d.stripXpMax);
        d.allowCursePurge = boolOr(cleanse, "allow_curse_purge", d.allowCursePurge);
        d.cursePurgeXpCost = intOr(cleanse, "curse_purge_xp_cost", d.cursePurgeXpCost);
        d.requirePriorDiscovery = boolOr(cleanse, "require_prior_discovery", d.requirePriorDiscovery);
        d.tier1Bookshelves = intOr(cleanse, "tier1_bookshelves", d.tier1Bookshelves);
        d.tier2Bookshelves = intOr(cleanse, "tier2_bookshelves", d.tier2Bookshelves);
        d.tier3QualifyingEnchantments = intOr(cleanse, "tier3_qualifying_enchantments", d.tier3QualifyingEnchantments);
        d.transformationMaxBatchSize = intOr(cleanse, "transformation_max_batch_size", d.transformationMaxBatchSize);

        if (world.has("vanilla_chest_books_mode")) {
            d.vanillaChestBooksMode = VanillaChestBooksMode.fromToken(
                world.get("vanilla_chest_books_mode").getAsString()
            );
        }
        d.bookshelfPoolEnabled = boolOr(world, "bookshelf_pool_enabled", d.bookshelfPoolEnabled);
        d.bookshelfMinBooks = intOr(world, "bookshelf_min_books", d.bookshelfMinBooks);
        d.bookshelfMaxBooks = intOr(world, "bookshelf_max_books", d.bookshelfMaxBooks);
        d.bookshelfEnchantedProb = doubleOr(world, "bookshelf_enchanted_prob", d.bookshelfEnchantedProb);

        if (root.has("customized_keys") && root.get("customized_keys").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("customized_keys")) {
                if (el.isJsonPrimitive()) {
                    d.customizedKeys.add(el.getAsString());
                }
            }
        }
        return d.build();
    }

    /** Mutable editor buffer enforcing live cross-field coherence as users drag sliders or click toggles. */
    public static final class Draft {
        public boolean overrideDatapacks = false;
        public int enchantedBookStackSize = 64;
        public VanillaTableMode vanillaTableMode = VanillaTableMode.BOOKS;
        // Fresh-install defaults: peripheral vanilla-rewrites stay OFF until the
        // owner opts in (2026-10-24 audit disposition); existing config files keep
        // their persisted values because loadFromDiskOrWriteDefault reads them first.
        public boolean anvilFlatRepairCost = false;
        public boolean anvilBlockSameBookMerge = true;
        public boolean restrictVillagerInfinityBooks = false;
        public boolean grindstoneDepletedTome = true;
        public boolean classic103Mode = false;

        public boolean directGatesEnabled = true;
        public BlankMaxTierMode blankMaxTierMode = BlankMaxTierMode.HALF_CEIL;
        public int maxDirectBookshelves = 38;
        public int horizontalScanRadius = 3;
        public int verticalScanRadius = 2;
        public boolean requireBreathingRoom = true;
        public int minOpenAdjacentBlocks = 6;
        public boolean requireLineOfSight = false;

        public MasterTomeWear.ConsumptionMode tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
        public int maxTomeUses = MasterTomeWear.DEFAULT_MAX_TOME_USES;
        public int bookCopyWearCost = MasterTomeWear.DEFAULT_BOOK_COPY_WEAR_COST;
        public MasterTomeWear.DepleteBehavior tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.STEP_DOWN;
        public boolean tomeRegenEnabled = true;
        public int tomeRegenBaseSeconds = 7_200;
        public int tomeRegenTier1Shelves = 38;
        public int tomeRegenTier1Seconds = 3_600;
        public int tomeRegenTier2Shelves = 42;
        public int tomeRegenTier2Seconds = 1_200;
        public boolean lockedShelfBreakable = true;
        public boolean lockedBookExtractionBlocked = true;
        public int earlyUnlockXpLevels = 1;
        public int earlyUnlockGritCount = 1;
        public int sacrificeRequired = 12;
        public int tomePageRequiredShelves = 4;
        public int maxWoodenDiscountPercent = 15;
        public int maxTotalXpDiscountPercent = 40;
        public boolean bookCombineEnabled = true;
        public double bookCombineXpMultiplier = 0.60;
        public boolean bookResearchEnabled = true;
        public double bookResearchXpMultiplier = 1.40;

        public int epiphanyMinBookshelves = 32;
        public double epiphanyBaseChance = 0.28;
        public double epiphanyMaxChance = 0.90;
        public int epiphanyMaxSlotsCap = 4;
        public int epiphanyCycleSeconds = 600;
        public int epiphanyFreezeXpLevels = 1;
        public int epiphanyFreezeDurationSeconds = 1200;
        public int epiphanyMaxFreezeSeconds = 3600;
        public int epiphanyRerollXpLevels = 2;

        public boolean disenchantEnabled = true;
        public double xpRefundScale = 1.00;
        public double externalXpRefundScale = 0.25;
        public int stripXpMin = 3;
        public int stripXpMax = 10;
        public boolean allowCursePurge = true;
        public int cursePurgeXpCost = 12;
        public boolean requirePriorDiscovery = true;
        public int tier1Bookshelves = 12;
        public int tier2Bookshelves = 32;
        public int tier3QualifyingEnchantments = 6;
        public int transformationMaxBatchSize = 64;

        public VanillaChestBooksMode vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
        public boolean bookshelfPoolEnabled = true;
        public int bookshelfMinBooks = 3;
        public int bookshelfMaxBooks = 5;
        public double bookshelfEnchantedProb = 0.12;

        public final Set<String> customizedKeys = new LinkedHashSet<>();

        public Draft() {}

        public Draft(ModRuntimeConfig src) {
            this.overrideDatapacks = src.overrideDatapacks;
            this.enchantedBookStackSize = src.enchantedBookStackSize;
            this.vanillaTableMode = src.vanillaTableMode;
            this.anvilFlatRepairCost = src.anvilFlatRepairCost;
            this.anvilBlockSameBookMerge = src.anvilBlockSameBookMerge;
            this.restrictVillagerInfinityBooks = src.restrictVillagerInfinityBooks;
            this.grindstoneDepletedTome = src.grindstoneDepletedTome;
            this.classic103Mode = src.classic103Mode;

            this.directGatesEnabled = src.directGatesEnabled;
            this.blankMaxTierMode = src.blankMaxTierMode;
            this.maxDirectBookshelves = src.maxDirectBookshelves;
            this.horizontalScanRadius = src.horizontalScanRadius;
            this.verticalScanRadius = src.verticalScanRadius;
            this.requireBreathingRoom = src.requireBreathingRoom;
            this.minOpenAdjacentBlocks = src.minOpenAdjacentBlocks;
            this.requireLineOfSight = src.requireLineOfSight;

            this.tomeConsumptionMode = src.tomeConsumptionMode;
            this.maxTomeUses = src.maxTomeUses;
            this.bookCopyWearCost = src.bookCopyWearCost;
            this.tomeDepleteBehavior = src.tomeDepleteBehavior;
            this.tomeRegenEnabled = src.tomeRegenEnabled;
            this.tomeRegenBaseSeconds = src.tomeRegenBaseSeconds;
            this.tomeRegenTier1Shelves = src.tomeRegenTier1Shelves;
            this.tomeRegenTier1Seconds = src.tomeRegenTier1Seconds;
            this.tomeRegenTier2Shelves = src.tomeRegenTier2Shelves;
            this.tomeRegenTier2Seconds = src.tomeRegenTier2Seconds;
            this.lockedShelfBreakable = src.lockedShelfBreakable;
            this.lockedBookExtractionBlocked = src.lockedBookExtractionBlocked;
            this.earlyUnlockXpLevels = src.earlyUnlockXpLevels;
            this.earlyUnlockGritCount = src.earlyUnlockGritCount;
            this.sacrificeRequired = src.sacrificeRequired;
            this.tomePageRequiredShelves = src.tomePageRequiredShelves;
            this.maxWoodenDiscountPercent = src.maxWoodenDiscountPercent;
            this.maxTotalXpDiscountPercent = src.maxTotalXpDiscountPercent;
            this.bookCombineEnabled = src.bookCombineEnabled;
            this.bookCombineXpMultiplier = src.bookCombineXpMultiplier;
            this.bookResearchEnabled = src.bookResearchEnabled;
            this.bookResearchXpMultiplier = src.bookResearchXpMultiplier;

            this.epiphanyMinBookshelves = src.epiphanyMinBookshelves;
            this.epiphanyBaseChance = src.epiphanyBaseChance;
            this.epiphanyMaxChance = src.epiphanyMaxChance;
            this.epiphanyMaxSlotsCap = src.epiphanyMaxSlotsCap;
            this.epiphanyCycleSeconds = src.epiphanyCycleSeconds;
            this.epiphanyFreezeXpLevels = src.epiphanyFreezeXpLevels;
            this.epiphanyFreezeDurationSeconds = src.epiphanyFreezeDurationSeconds;
            this.epiphanyMaxFreezeSeconds = src.epiphanyMaxFreezeSeconds;
            this.epiphanyRerollXpLevels = src.epiphanyRerollXpLevels;

            this.disenchantEnabled = src.disenchantEnabled;
            this.xpRefundScale = src.xpRefundScale;
            this.externalXpRefundScale = src.externalXpRefundScale;
            this.stripXpMin = src.stripXpMin;
            this.stripXpMax = src.stripXpMax;
            this.allowCursePurge = src.allowCursePurge;
            this.cursePurgeXpCost = src.cursePurgeXpCost;
            this.requirePriorDiscovery = src.requirePriorDiscovery;
            this.tier1Bookshelves = src.tier1Bookshelves;
            this.tier2Bookshelves = src.tier2Bookshelves;
            this.tier3QualifyingEnchantments = src.tier3QualifyingEnchantments;
            this.transformationMaxBatchSize = src.transformationMaxBatchSize;

            this.vanillaChestBooksMode = src.vanillaChestBooksMode;
            this.bookshelfPoolEnabled = src.bookshelfPoolEnabled;
            this.bookshelfMinBooks = src.bookshelfMinBooks;
            this.bookshelfMaxBooks = src.bookshelfMaxBooks;
            this.bookshelfEnchantedProb = src.bookshelfEnchantedProb;
            this.customizedKeys.addAll(src.customizedKeys);
        }

        /** Enforces live inter-option ordering and volume bounds after editing {@code editedKey}. */
        public void normalizeAfterEdit(String editedKey) {
            if (editedKey != null && !editedKey.isBlank()) {
                this.customizedKeys.add(editedKey);
            }
            // Flipping the master switch on applies the 1.0.3 profile once;
            // later edits (incl. on the Classic merged page) stay as made.
            if ("classic_1_0_3_mode".equals(editedKey) && this.classic103Mode) {
                applyClassic103Overrides();
            }
            this.horizontalScanRadius = clampInt(this.horizontalScanRadius, 1, BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS);
            this.verticalScanRadius = clampInt(this.verticalScanRadius, 0, BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS);
            if ("vertical_scan_radius".equals(editedKey)) {
                while (this.horizontalScanRadius > 1
                    && BookshelfProgression.scanVolumeBlocks(this.horizontalScanRadius, this.verticalScanRadius)
                        > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
                    this.horizontalScanRadius--;
                }
            }
            while (this.verticalScanRadius > 0
                && BookshelfProgression.scanVolumeBlocks(this.horizontalScanRadius, this.verticalScanRadius)
                    > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
                this.verticalScanRadius--;
            }

            if ("max_tome_uses".equals(editedKey) && this.bookCopyWearCost > this.maxTomeUses) {
                this.bookCopyWearCost = this.maxTomeUses;
            } else if ("book_copy_wear_cost".equals(editedKey) && this.bookCopyWearCost > this.maxTomeUses) {
                this.maxTomeUses = this.bookCopyWearCost;
            }

            if ("max_wooden_discount_percent".equals(editedKey) && this.maxWoodenDiscountPercent > this.maxTotalXpDiscountPercent) {
                this.maxTotalXpDiscountPercent = this.maxWoodenDiscountPercent;
            } else if ("max_total_xp_discount_percent".equals(editedKey) && this.maxTotalXpDiscountPercent < this.maxWoodenDiscountPercent) {
                this.maxWoodenDiscountPercent = this.maxTotalXpDiscountPercent;
            }

            if ("epiphany_base_chance".equals(editedKey) && this.epiphanyBaseChance > this.epiphanyMaxChance) {
                this.epiphanyMaxChance = this.epiphanyBaseChance;
            } else if ("epiphany_max_chance".equals(editedKey) && this.epiphanyMaxChance < this.epiphanyBaseChance) {
                this.epiphanyBaseChance = this.epiphanyMaxChance;
            }

            if ("epiphany_freeze_duration_seconds".equals(editedKey)
                && this.epiphanyFreezeDurationSeconds > this.epiphanyMaxFreezeSeconds) {
                this.epiphanyMaxFreezeSeconds = this.epiphanyFreezeDurationSeconds;
            } else if ("epiphany_max_freeze_seconds".equals(editedKey)
                && this.epiphanyMaxFreezeSeconds < this.epiphanyFreezeDurationSeconds) {
                this.epiphanyFreezeDurationSeconds = this.epiphanyMaxFreezeSeconds;
            }

            if ("xp_refund_scale".equals(editedKey) && this.externalXpRefundScale > this.xpRefundScale) {
                this.externalXpRefundScale = this.xpRefundScale;
            } else if ("external_xp_refund_scale".equals(editedKey) && this.externalXpRefundScale > this.xpRefundScale) {
                this.xpRefundScale = this.externalXpRefundScale;
            }

            if ("strip_xp_min".equals(editedKey) && this.stripXpMin > this.stripXpMax) {
                this.stripXpMax = this.stripXpMin;
            } else if ("strip_xp_max".equals(editedKey) && this.stripXpMax < this.stripXpMin) {
                this.stripXpMin = this.stripXpMax;
            }

            if ("tier1_bookshelves".equals(editedKey)) {
                if (this.tier2Bookshelves < this.tier1Bookshelves) this.tier2Bookshelves = this.tier1Bookshelves;
                if (this.maxDirectBookshelves < this.tier2Bookshelves) this.maxDirectBookshelves = Math.max(4, this.tier2Bookshelves);
            } else if ("tier2_bookshelves".equals(editedKey)) {
                if (this.tier1Bookshelves > this.tier2Bookshelves) this.tier1Bookshelves = this.tier2Bookshelves;
                if (this.maxDirectBookshelves < this.tier2Bookshelves) this.maxDirectBookshelves = Math.max(4, this.tier2Bookshelves);
            } else if ("max_direct_bookshelves".equals(editedKey)) {
                if (this.tier2Bookshelves > this.maxDirectBookshelves) this.tier2Bookshelves = this.maxDirectBookshelves;
                if (this.tier1Bookshelves > this.tier2Bookshelves) this.tier1Bookshelves = this.tier2Bookshelves;
            }

            if ("bookshelf_min_books".equals(editedKey) && this.bookshelfMinBooks > this.bookshelfMaxBooks) {
                this.bookshelfMaxBooks = this.bookshelfMinBooks;
            } else if ("bookshelf_max_books".equals(editedKey) && this.bookshelfMaxBooks < this.bookshelfMinBooks) {
                this.bookshelfMinBooks = this.bookshelfMaxBooks;
            }

            if ("tome_regen_tier1_shelves".equals(editedKey) && this.tomeRegenTier1Shelves > this.tomeRegenTier2Shelves) {
                this.tomeRegenTier2Shelves = this.tomeRegenTier1Shelves;
            } else if ("tome_regen_tier2_shelves".equals(editedKey) && this.tomeRegenTier2Shelves < this.tomeRegenTier1Shelves) {
                this.tomeRegenTier1Shelves = this.tomeRegenTier2Shelves;
            }

            if ("tome_regen_base_seconds".equals(editedKey)) {
                if (this.tomeRegenTier1Seconds > this.tomeRegenBaseSeconds) this.tomeRegenTier1Seconds = this.tomeRegenBaseSeconds;
                if (this.tomeRegenTier2Seconds > this.tomeRegenTier1Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier1Seconds;
            } else if ("tome_regen_tier1_seconds".equals(editedKey)) {
                if (this.tomeRegenBaseSeconds < this.tomeRegenTier1Seconds) this.tomeRegenBaseSeconds = this.tomeRegenTier1Seconds;
                if (this.tomeRegenTier2Seconds > this.tomeRegenTier1Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier1Seconds;
            } else if ("tome_regen_tier2_seconds".equals(editedKey)) {
                if (this.tomeRegenTier1Seconds < this.tomeRegenTier2Seconds) this.tomeRegenTier1Seconds = this.tomeRegenTier2Seconds;
                if (this.tomeRegenBaseSeconds < this.tomeRegenTier1Seconds) this.tomeRegenBaseSeconds = this.tomeRegenTier1Seconds;
            }

            this.bookCombineXpMultiplier = round2(clampDouble(this.bookCombineXpMultiplier, 0.10, 3.00));
            this.bookResearchXpMultiplier = round2(clampDouble(this.bookResearchXpMultiplier, 0.10, 3.00));
            this.epiphanyBaseChance = round2(clampDouble(this.epiphanyBaseChance, 0.0, 1.0));
            this.epiphanyMaxChance = round2(clampDouble(this.epiphanyMaxChance, this.epiphanyBaseChance, 1.0));
            this.xpRefundScale = round2(clampDouble(this.xpRefundScale, 0.0, 1.0));
            this.externalXpRefundScale = round2(clampDouble(this.externalXpRefundScale, 0.0, this.xpRefundScale));
            this.bookshelfEnchantedProb = round2(clampDouble(this.bookshelfEnchantedProb, 0.0, 1.0));
        }

        public void copyFrom(Draft other) {
            if (other == null || other == this) return;
            ModRuntimeConfig snap = other.build();
            this.overrideDatapacks = snap.overrideDatapacks;
            this.enchantedBookStackSize = snap.enchantedBookStackSize;
            this.vanillaTableMode = snap.vanillaTableMode;
            this.anvilFlatRepairCost = snap.anvilFlatRepairCost;
            this.anvilBlockSameBookMerge = snap.anvilBlockSameBookMerge;
            this.restrictVillagerInfinityBooks = snap.restrictVillagerInfinityBooks;
            this.grindstoneDepletedTome = snap.grindstoneDepletedTome;
            this.classic103Mode = snap.classic103Mode;

            this.directGatesEnabled = snap.directGatesEnabled;
            this.blankMaxTierMode = snap.blankMaxTierMode;
            this.maxDirectBookshelves = snap.maxDirectBookshelves;
            this.horizontalScanRadius = snap.horizontalScanRadius;
            this.verticalScanRadius = snap.verticalScanRadius;
            this.requireBreathingRoom = snap.requireBreathingRoom;
            this.minOpenAdjacentBlocks = snap.minOpenAdjacentBlocks;
            this.requireLineOfSight = snap.requireLineOfSight;

            this.tomeConsumptionMode = snap.tomeConsumptionMode;
            this.maxTomeUses = snap.maxTomeUses;
            this.bookCopyWearCost = snap.bookCopyWearCost;
            this.tomeDepleteBehavior = snap.tomeDepleteBehavior;
            this.tomeRegenEnabled = snap.tomeRegenEnabled;
            this.tomeRegenBaseSeconds = snap.tomeRegenBaseSeconds;
            this.tomeRegenTier1Shelves = snap.tomeRegenTier1Shelves;
            this.tomeRegenTier1Seconds = snap.tomeRegenTier1Seconds;
            this.tomeRegenTier2Shelves = snap.tomeRegenTier2Shelves;
            this.tomeRegenTier2Seconds = snap.tomeRegenTier2Seconds;
            this.lockedShelfBreakable = snap.lockedShelfBreakable;
            this.lockedBookExtractionBlocked = snap.lockedBookExtractionBlocked;
            this.earlyUnlockXpLevels = snap.earlyUnlockXpLevels;
            this.earlyUnlockGritCount = snap.earlyUnlockGritCount;
            this.sacrificeRequired = snap.sacrificeRequired;
            this.tomePageRequiredShelves = snap.tomePageRequiredShelves;
            this.maxWoodenDiscountPercent = snap.maxWoodenDiscountPercent;
            this.maxTotalXpDiscountPercent = snap.maxTotalXpDiscountPercent;
            this.bookCombineEnabled = snap.bookCombineEnabled;
            this.bookCombineXpMultiplier = snap.bookCombineXpMultiplier;
            this.bookResearchEnabled = snap.bookResearchEnabled;
            this.bookResearchXpMultiplier = snap.bookResearchXpMultiplier;

            this.epiphanyMinBookshelves = snap.epiphanyMinBookshelves;
            this.epiphanyBaseChance = snap.epiphanyBaseChance;
            this.epiphanyMaxChance = snap.epiphanyMaxChance;
            this.epiphanyMaxSlotsCap = snap.epiphanyMaxSlotsCap;
            this.epiphanyCycleSeconds = snap.epiphanyCycleSeconds;
            this.epiphanyFreezeXpLevels = snap.epiphanyFreezeXpLevels;
            this.epiphanyFreezeDurationSeconds = snap.epiphanyFreezeDurationSeconds;
            this.epiphanyMaxFreezeSeconds = snap.epiphanyMaxFreezeSeconds;
            this.epiphanyRerollXpLevels = snap.epiphanyRerollXpLevels;

            this.disenchantEnabled = snap.disenchantEnabled;
            this.xpRefundScale = snap.xpRefundScale;
            this.externalXpRefundScale = snap.externalXpRefundScale;
            this.stripXpMin = snap.stripXpMin;
            this.stripXpMax = snap.stripXpMax;
            this.allowCursePurge = snap.allowCursePurge;
            this.cursePurgeXpCost = snap.cursePurgeXpCost;
            this.requirePriorDiscovery = snap.requirePriorDiscovery;
            this.tier1Bookshelves = snap.tier1Bookshelves;
            this.tier2Bookshelves = snap.tier2Bookshelves;
            this.tier3QualifyingEnchantments = snap.tier3QualifyingEnchantments;
            this.transformationMaxBatchSize = snap.transformationMaxBatchSize;

            this.vanillaChestBooksMode = snap.vanillaChestBooksMode;
            this.bookshelfPoolEnabled = snap.bookshelfPoolEnabled;
            this.bookshelfMinBooks = snap.bookshelfMinBooks;
            this.bookshelfMaxBooks = snap.bookshelfMaxBooks;
            this.bookshelfEnchantedProb = snap.bookshelfEnchantedProb;
            this.customizedKeys.clear();
            this.customizedKeys.addAll(snap.customizedKeys);
        }

        /**
         * Writes the 1.0.3 value profile into this draft (preset button / classic
         * toggle). Values remain editable afterwards. enchanted_book_stack_size=64
         * is only a suggestion here: it is a startup-only component and takes
         * effect after restart (RESTART_REQUIRED), never by runtime force.
         */
        public void applyClassic103Overrides() {
            enchantedBookStackSize = 64;
            vanillaTableMode = VanillaTableMode.BOOKS;
            anvilFlatRepairCost = false;
            anvilBlockSameBookMerge = true;
            restrictVillagerInfinityBooks = false;
            grindstoneDepletedTome = false;
            directGatesEnabled = false;
            blankMaxTierMode = BlankMaxTierMode.FULL_CURVE;
            horizontalScanRadius = 2;
            verticalScanRadius = 2;
            requireBreathingRoom = false;
            requireLineOfSight = false;
            tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
            tomeRegenEnabled = false;
            lockedShelfBreakable = true;
            lockedBookExtractionBlocked = false;
            maxWoodenDiscountPercent = 0;
            maxTotalXpDiscountPercent = 0;
            bookCombineEnabled = true;
            bookCombineXpMultiplier = 1.00;
            bookResearchEnabled = true;
            bookResearchXpMultiplier = 0.50;
            epiphanyBaseChance = 0.0;
            epiphanyMaxChance = 0.0;
            disenchantEnabled = false;
            allowCursePurge = false;
            requirePriorDiscovery = false;
            transformationMaxBatchSize = 1;
            bookshelfEnchantedProb = 0.03;
            vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
            bookshelfPoolEnabled = true;
        }

        public ModRuntimeConfig build() {
            // No classic forcing here: profile application is explicit
            // (Preset#applyTo / config-screen toggle / Classic merged page edits).
            return new ModRuntimeConfig(
                overrideDatapacks,
                enchantedBookStackSize,
                vanillaTableMode,
                anvilFlatRepairCost,
                anvilBlockSameBookMerge,
                directGatesEnabled,
                blankMaxTierMode,
                maxDirectBookshelves,
                horizontalScanRadius,
                verticalScanRadius,
                requireBreathingRoom,
                minOpenAdjacentBlocks,
                requireLineOfSight,
                tomeConsumptionMode,
                maxTomeUses,
                bookCopyWearCost,
                tomeDepleteBehavior,
                tomeRegenEnabled,
                tomeRegenBaseSeconds,
                tomeRegenTier1Shelves,
                tomeRegenTier1Seconds,
                tomeRegenTier2Shelves,
                tomeRegenTier2Seconds,
                lockedShelfBreakable,
                lockedBookExtractionBlocked,
                earlyUnlockXpLevels,
                earlyUnlockGritCount,
                sacrificeRequired,
                tomePageRequiredShelves,
                maxWoodenDiscountPercent,
                maxTotalXpDiscountPercent,
                bookCombineEnabled,
                bookCombineXpMultiplier,
                bookResearchEnabled,
                bookResearchXpMultiplier,
                epiphanyMinBookshelves,
                epiphanyBaseChance,
                epiphanyMaxChance,
                epiphanyMaxSlotsCap,
                epiphanyCycleSeconds,
                epiphanyFreezeXpLevels,
                epiphanyFreezeDurationSeconds,
                epiphanyMaxFreezeSeconds,
                epiphanyRerollXpLevels,
                disenchantEnabled,
                xpRefundScale,
                externalXpRefundScale,
                stripXpMin,
                stripXpMax,
                allowCursePurge,
                cursePurgeXpCost,
                requirePriorDiscovery,
                tier1Bookshelves,
                tier2Bookshelves,
                tier3QualifyingEnchantments,
                transformationMaxBatchSize,
                vanillaChestBooksMode,
                bookshelfPoolEnabled,
                bookshelfMinBooks,
                bookshelfMaxBooks,
                bookshelfEnchantedProb,
                restrictVillagerInfinityBooks,
                grindstoneDepletedTome,
                classic103Mode,
                customizedKeys
            );
        }
    }

    private static JsonObject objOrEmpty(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
            ? parent.getAsJsonObject(key)
            : new JsonObject();
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
    }

    private static double doubleOr(JsonObject obj, String key, double fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsDouble() : fallback;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampDouble(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
