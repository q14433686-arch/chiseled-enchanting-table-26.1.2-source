package chiseled_enchanting_table.config;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import static chiseled_enchanting_table.config.ModRuntimeConfig.*;

/** Mutable editor values and cross-field normalization, independent of disk/network state. */
    public class ConfigDraft {
        public void markCustomized(String key) {
            if (key != null && !key.isBlank()) {
                this.customizedKeys.add(key);
            }
        }

        public boolean overrideDatapacks = false;
        public int enchantedBookStackSize = 64;
        public VanillaTableMode vanillaTableMode = VanillaTableMode.BOOKS;
        public boolean anvilFlatRepairCost = false;
        public boolean anvilBlockSameBookMerge = true;
        public boolean restrictVillagerInfinityBooks = false;
        public boolean grindstoneDepletedTome = true;

        public ShelfBoostModel shelfBoostModel = ShelfBoostModel.MODERN_CURVE;
        public DefaultUnlockFilter defaultUnlockFilter = DefaultUnlockFilter.EXCLUDE_SPECIAL;
        public boolean directTierUpgrade = true;
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
        public boolean tomeLocksEnabled = true;
        public boolean lockedShelfBreakable = true;
        public boolean lockedBookExtractionBlocked = true;
        public boolean provenanceEnabled = true;
        public boolean ritualQueueEnabled = true;
        public int earlyUnlockXpLevels = 1;
        public int earlyUnlockGritCount = 1;
        public int sacrificeRequired = 12;
        public int tomePageRequiredShelves = 4;
        public boolean incrementalCredit = true;
        public XpSettlementMode xpMode = XpSettlementMode.DUAL_STORED_DISCOUNT;
        public CostTableDataset costTableDataset = CostTableDataset.MODERN;
        public MaterialSelectionMode materialSelectionMode = MaterialSelectionMode.ALL_OPTIONS_MIDPOINT;
        public ResearchRoundingMode researchRoundingMode = ResearchRoundingMode.ROUND_NEAREST;
        public boolean libraryDiscountEnabled = true;
        public int maxWoodenDiscountPercent = 15;
        public int maxTotalXpDiscountPercent = 40;
        public boolean bookCombineEnabled = true;
        public double bookCombineXpMultiplier = 0.60;
        public boolean bookResearchEnabled = true;
        public double bookResearchXpMultiplier = 1.40;

        public boolean epiphanyEnabled = true;
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
        public TransformationSetMode transformationSetMode = TransformationSetMode.FULL_SIX;
        public boolean requirePriorDiscovery = true;
        public DrainedBookResult drainedBookResult = DrainedBookResult.DEPLETED_TOME;
        public boolean tomePageEnabled = true;
        public int tier1Bookshelves = 12;
        public int tier2Bookshelves = 32;
        public int tier3QualifyingEnchantments = 6;
        public int transformationMaxBatchSize = 64;

        public VanillaChestBooksMode vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
        public boolean bookshelfPoolEnabled = true;
        public int bookshelfMinBooks = 3;
        public int bookshelfMaxBooks = 5;
        public double bookshelfEnchantedProb = 0.12;
        public boolean librarianTomeOffers = true;

        public final Set<String> customizedKeys = new LinkedHashSet<>();

        public ConfigDraft() {}

        public ConfigDraft(ModRuntimeConfig src) {
            this.overrideDatapacks = src.overrideDatapacks();
            this.enchantedBookStackSize = src.enchantedBookStackSize();
            this.vanillaTableMode = src.vanillaTableMode();
            this.anvilFlatRepairCost = src.anvilFlatRepairCost();
            this.anvilBlockSameBookMerge = src.anvilBlockSameBookMerge();
            this.restrictVillagerInfinityBooks = src.restrictVillagerInfinityBooks();
            this.grindstoneDepletedTome = src.grindstoneDepletedTome();

            this.shelfBoostModel = src.shelfBoostModel();
            this.defaultUnlockFilter = src.defaultUnlockFilter();
            this.directTierUpgrade = src.directTierUpgrade();
            this.directGatesEnabled = src.directGatesEnabled();
            this.blankMaxTierMode = src.blankMaxTierMode();
            this.maxDirectBookshelves = src.maxDirectBookshelves();
            this.horizontalScanRadius = src.horizontalScanRadius();
            this.verticalScanRadius = src.verticalScanRadius();
            this.requireBreathingRoom = src.requireBreathingRoom();
            this.minOpenAdjacentBlocks = src.minOpenAdjacentBlocks();
            this.requireLineOfSight = src.requireLineOfSight();

            this.tomeConsumptionMode = src.tomeConsumptionMode();
            this.maxTomeUses = src.maxTomeUses();
            this.bookCopyWearCost = src.bookCopyWearCost();
            this.tomeDepleteBehavior = src.tomeDepleteBehavior();
            this.tomeRegenEnabled = src.tomeRegenEnabled();
            this.tomeRegenBaseSeconds = src.tomeRegenBaseSeconds();
            this.tomeRegenTier1Shelves = src.tomeRegenTier1Shelves();
            this.tomeRegenTier1Seconds = src.tomeRegenTier1Seconds();
            this.tomeRegenTier2Shelves = src.tomeRegenTier2Shelves();
            this.tomeRegenTier2Seconds = src.tomeRegenTier2Seconds();
            this.tomeLocksEnabled = src.tomeLocksEnabled();
            this.lockedShelfBreakable = src.lockedShelfBreakable();
            this.lockedBookExtractionBlocked = src.lockedBookExtractionBlocked();
            this.provenanceEnabled = src.provenanceEnabled();
            this.ritualQueueEnabled = src.ritualQueueEnabled();
            this.earlyUnlockXpLevels = src.earlyUnlockXpLevels();
            this.earlyUnlockGritCount = src.earlyUnlockGritCount();
            this.sacrificeRequired = src.sacrificeRequired();
            this.tomePageRequiredShelves = src.tomePageRequiredShelves();
            this.incrementalCredit = src.incrementalCredit();
            this.xpMode = src.xpMode();
            this.costTableDataset = src.costTableDataset();
            this.materialSelectionMode = src.materialSelectionMode();
            this.researchRoundingMode = src.researchRoundingMode();
            this.libraryDiscountEnabled = src.libraryDiscountEnabled();
            this.maxWoodenDiscountPercent = src.maxWoodenDiscountPercent();
            this.maxTotalXpDiscountPercent = src.maxTotalXpDiscountPercent();
            this.bookCombineEnabled = src.bookCombineEnabled();
            this.bookCombineXpMultiplier = src.bookCombineXpMultiplier();
            this.bookResearchEnabled = src.bookResearchEnabled();
            this.bookResearchXpMultiplier = src.bookResearchXpMultiplier();

            this.epiphanyEnabled = src.epiphanyEnabled();
            this.epiphanyMinBookshelves = src.epiphanyMinBookshelves();
            this.epiphanyBaseChance = src.epiphanyBaseChance();
            this.epiphanyMaxChance = src.epiphanyMaxChance();
            this.epiphanyMaxSlotsCap = src.epiphanyMaxSlotsCap();
            this.epiphanyCycleSeconds = src.epiphanyCycleSeconds();
            this.epiphanyFreezeXpLevels = src.epiphanyFreezeXpLevels();
            this.epiphanyFreezeDurationSeconds = src.epiphanyFreezeDurationSeconds();
            this.epiphanyMaxFreezeSeconds = src.epiphanyMaxFreezeSeconds();
            this.epiphanyRerollXpLevels = src.epiphanyRerollXpLevels();

            this.disenchantEnabled = src.disenchantEnabled();
            this.xpRefundScale = src.xpRefundScale();
            this.externalXpRefundScale = src.externalXpRefundScale();
            this.stripXpMin = src.stripXpMin();
            this.stripXpMax = src.stripXpMax();
            this.allowCursePurge = src.allowCursePurge();
            this.cursePurgeXpCost = src.cursePurgeXpCost();
            this.transformationSetMode = src.transformationSetMode();
            this.requirePriorDiscovery = src.requirePriorDiscovery();
            this.drainedBookResult = src.drainedBookResult();
            this.tomePageEnabled = src.tomePageEnabled();
            this.tier1Bookshelves = src.tier1Bookshelves();
            this.tier2Bookshelves = src.tier2Bookshelves();
            this.tier3QualifyingEnchantments = src.tier3QualifyingEnchantments();
            this.transformationMaxBatchSize = src.transformationMaxBatchSize();

            this.vanillaChestBooksMode = src.vanillaChestBooksMode();
            this.bookshelfPoolEnabled = src.bookshelfPoolEnabled();
            this.bookshelfMinBooks = src.bookshelfMinBooks();
            this.bookshelfMaxBooks = src.bookshelfMaxBooks();
            this.bookshelfEnchantedProb = src.bookshelfEnchantedProb();
            this.librarianTomeOffers = src.librarianTomeOffers();
            this.customizedKeys.addAll(src.customizedKeys);
        }

        /** Enforces live inter-option ordering and volume bounds after editing {@code editedKey}. */
        public void normalizeAfterEdit(@Nullable String editedKey) {
            if (editedKey != null && !editedKey.isBlank()) {
                this.customizedKeys.add(editedKey);
            }
            if ("cost_table".equals(editedKey)) {
                // Align default combine/research XP multipliers with the selected table unless
                // the player has explicitly customized those sliders.
                if (!this.customizedKeys.contains("book_combine_xp_multiplier")) {
                    this.bookCombineXpMultiplier = this.costTableDataset == CostTableDataset.CLASSIC ? 1.00 : 0.60;
                }
                if (!this.customizedKeys.contains("book_research_xp_multiplier")) {
                    this.bookResearchXpMultiplier = this.costTableDataset == CostTableDataset.CLASSIC ? 0.50 : 1.40;
                }
            }

            this.horizontalScanRadius = RuntimeConfigJson.clampInt(this.horizontalScanRadius, 1, BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS);
            this.verticalScanRadius = RuntimeConfigJson.clampInt(this.verticalScanRadius, 0, BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS);
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
                if (this.tomeRegenTier1Seconds < this.tomeRegenTier2Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier2Seconds;
                if (this.tomeRegenBaseSeconds < this.tomeRegenTier1Seconds) this.tomeRegenBaseSeconds = this.tomeRegenTier1Seconds;
            }

            this.bookCombineXpMultiplier = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.bookCombineXpMultiplier, 0.10, 3.00));
            this.bookResearchXpMultiplier = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.bookResearchXpMultiplier, 0.10, 3.00));
            this.epiphanyBaseChance = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.epiphanyBaseChance, 0.0, 1.0));
            this.epiphanyMaxChance = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.epiphanyMaxChance, this.epiphanyBaseChance, 1.0));
            this.xpRefundScale = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.xpRefundScale, 0.0, 1.0));
            this.externalXpRefundScale = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.externalXpRefundScale, 0.0, this.xpRefundScale));
            this.bookshelfEnchantedProb = RuntimeConfigJson.round2(RuntimeConfigJson.clampDouble(this.bookshelfEnchantedProb, 0.0, 1.0));
        }

        public void copyFrom(ConfigDraft other) {
            if (other == null || other == this) return;
            ModRuntimeConfig snap = other.build();
            this.overrideDatapacks = snap.overrideDatapacks();
            this.enchantedBookStackSize = snap.enchantedBookStackSize();
            this.vanillaTableMode = snap.vanillaTableMode();
            this.anvilFlatRepairCost = snap.anvilFlatRepairCost();
            this.anvilBlockSameBookMerge = snap.anvilBlockSameBookMerge();
            this.restrictVillagerInfinityBooks = snap.restrictVillagerInfinityBooks();
            this.grindstoneDepletedTome = snap.grindstoneDepletedTome();

            this.shelfBoostModel = snap.shelfBoostModel();
            this.defaultUnlockFilter = snap.defaultUnlockFilter();
            this.directTierUpgrade = snap.directTierUpgrade();
            this.directGatesEnabled = snap.directGatesEnabled();
            this.blankMaxTierMode = snap.blankMaxTierMode();
            this.maxDirectBookshelves = snap.maxDirectBookshelves();
            this.horizontalScanRadius = snap.horizontalScanRadius();
            this.verticalScanRadius = snap.verticalScanRadius();
            this.requireBreathingRoom = snap.requireBreathingRoom();
            this.minOpenAdjacentBlocks = snap.minOpenAdjacentBlocks();
            this.requireLineOfSight = snap.requireLineOfSight();

            this.tomeConsumptionMode = snap.tomeConsumptionMode();
            this.maxTomeUses = snap.maxTomeUses();
            this.bookCopyWearCost = snap.bookCopyWearCost();
            this.tomeDepleteBehavior = snap.tomeDepleteBehavior();
            this.tomeRegenEnabled = snap.tomeRegenEnabled();
            this.tomeRegenBaseSeconds = snap.tomeRegenBaseSeconds();
            this.tomeRegenTier1Shelves = snap.tomeRegenTier1Shelves();
            this.tomeRegenTier1Seconds = snap.tomeRegenTier1Seconds();
            this.tomeRegenTier2Shelves = snap.tomeRegenTier2Shelves();
            this.tomeRegenTier2Seconds = snap.tomeRegenTier2Seconds();
            this.tomeLocksEnabled = snap.tomeLocksEnabled();
            this.lockedShelfBreakable = snap.lockedShelfBreakable();
            this.lockedBookExtractionBlocked = snap.lockedBookExtractionBlocked();
            this.provenanceEnabled = snap.provenanceEnabled();
            this.ritualQueueEnabled = snap.ritualQueueEnabled();
            this.earlyUnlockXpLevels = snap.earlyUnlockXpLevels();
            this.earlyUnlockGritCount = snap.earlyUnlockGritCount();
            this.sacrificeRequired = snap.sacrificeRequired();
            this.tomePageRequiredShelves = snap.tomePageRequiredShelves();
            this.incrementalCredit = snap.incrementalCredit();
            this.xpMode = snap.xpMode();
            this.costTableDataset = snap.costTableDataset();
            this.materialSelectionMode = snap.materialSelectionMode();
            this.researchRoundingMode = snap.researchRoundingMode();
            this.libraryDiscountEnabled = snap.libraryDiscountEnabled();
            this.maxWoodenDiscountPercent = snap.maxWoodenDiscountPercent();
            this.maxTotalXpDiscountPercent = snap.maxTotalXpDiscountPercent();
            this.bookCombineEnabled = snap.bookCombineEnabled();
            this.bookCombineXpMultiplier = snap.bookCombineXpMultiplier();
            this.bookResearchEnabled = snap.bookResearchEnabled();
            this.bookResearchXpMultiplier = snap.bookResearchXpMultiplier();

            this.epiphanyEnabled = snap.epiphanyEnabled();
            this.epiphanyMinBookshelves = snap.epiphanyMinBookshelves();
            this.epiphanyBaseChance = snap.epiphanyBaseChance();
            this.epiphanyMaxChance = snap.epiphanyMaxChance();
            this.epiphanyMaxSlotsCap = snap.epiphanyMaxSlotsCap();
            this.epiphanyCycleSeconds = snap.epiphanyCycleSeconds();
            this.epiphanyFreezeXpLevels = snap.epiphanyFreezeXpLevels();
            this.epiphanyFreezeDurationSeconds = snap.epiphanyFreezeDurationSeconds();
            this.epiphanyMaxFreezeSeconds = snap.epiphanyMaxFreezeSeconds();
            this.epiphanyRerollXpLevels = snap.epiphanyRerollXpLevels();

            this.disenchantEnabled = snap.disenchantEnabled();
            this.xpRefundScale = snap.xpRefundScale();
            this.externalXpRefundScale = snap.externalXpRefundScale();
            this.stripXpMin = snap.stripXpMin();
            this.stripXpMax = snap.stripXpMax();
            this.allowCursePurge = snap.allowCursePurge();
            this.cursePurgeXpCost = snap.cursePurgeXpCost();
            this.transformationSetMode = snap.transformationSetMode();
            this.requirePriorDiscovery = snap.requirePriorDiscovery();
            this.drainedBookResult = snap.drainedBookResult();
            this.tomePageEnabled = snap.tomePageEnabled();
            this.tier1Bookshelves = snap.tier1Bookshelves();
            this.tier2Bookshelves = snap.tier2Bookshelves();
            this.tier3QualifyingEnchantments = snap.tier3QualifyingEnchantments();
            this.transformationMaxBatchSize = snap.transformationMaxBatchSize();

            this.vanillaChestBooksMode = snap.vanillaChestBooksMode();
            this.bookshelfPoolEnabled = snap.bookshelfPoolEnabled();
            this.bookshelfMinBooks = snap.bookshelfMinBooks();
            this.bookshelfMaxBooks = snap.bookshelfMaxBooks();
            this.bookshelfEnchantedProb = snap.bookshelfEnchantedProb();
            this.librarianTomeOffers = snap.librarianTomeOffers();
            this.customizedKeys.clear();
            this.customizedKeys.addAll(snap.customizedKeys);
        }

        /** Sets all 19 core mechanism rule atoms to their classic {@code 1.0.3} values. */
        public void applyClassic103Atoms() {
            shelfBoostModel = ShelfBoostModel.CLASSIC_FLOOR_THIRD;
            defaultUnlockFilter = DefaultUnlockFilter.CLASSIC_UNFILTERED;
            directTierUpgrade = false;
            incrementalCredit = false;
            xpMode = XpSettlementMode.CLASSIC_FULL_POINTS;
            costTableDataset = CostTableDataset.CLASSIC;
            materialSelectionMode = MaterialSelectionMode.PLAYER_SALTED_SINGLE;
            researchRoundingMode = ResearchRoundingMode.FLOOR_DOWN;
            libraryDiscountEnabled = false;
            tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
            tomeLocksEnabled = false;
            provenanceEnabled = false;
            ritualQueueEnabled = false;
            epiphanyEnabled = false;
            disenchantEnabled = false;
            transformationSetMode = TransformationSetMode.CLASSIC_TWO;
            drainedBookResult = DrainedBookResult.PLAIN_BOOK;
            tomePageEnabled = false;
            librarianTomeOffers = false;
        }

        /** Sets all 19 core mechanism rule atoms to their modern default values. */
        public void applyModern110Atoms() {
            shelfBoostModel = ShelfBoostModel.MODERN_CURVE;
            defaultUnlockFilter = DefaultUnlockFilter.EXCLUDE_SPECIAL;
            directTierUpgrade = true;
            incrementalCredit = true;
            xpMode = XpSettlementMode.DUAL_STORED_DISCOUNT;
            costTableDataset = CostTableDataset.MODERN;
            materialSelectionMode = MaterialSelectionMode.ALL_OPTIONS_MIDPOINT;
            researchRoundingMode = ResearchRoundingMode.ROUND_NEAREST;
            libraryDiscountEnabled = true;
            tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
            tomeLocksEnabled = true;
            provenanceEnabled = true;
            ritualQueueEnabled = true;
            epiphanyEnabled = true;
            disenchantEnabled = true;
            transformationSetMode = TransformationSetMode.FULL_SIX;
            drainedBookResult = DrainedBookResult.DEPLETED_TOME;
            tomePageEnabled = true;
            librarianTomeOffers = true;
        }

        /**
         * Writes the complete {@code 1.0.3} preset profile into this draft (all 19 core atoms plus
         * {@code 1.0.3} scan geometry, multiplier, and loot defaults). Every field remains freely
         * editable afterwards.
         */
        public void applyClassic103Overrides() {
            applyClassic103Atoms();
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
            allowCursePurge = false;
            requirePriorDiscovery = false;
            transformationMaxBatchSize = 1;
            bookshelfEnchantedProb = 0.03;
            vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
            bookshelfPoolEnabled = true;
        }

        public ModRuntimeConfig build() {
            return new ModRuntimeConfig(
                overrideDatapacks,
                enchantedBookStackSize,
                vanillaTableMode,
                anvilFlatRepairCost,
                anvilBlockSameBookMerge,
                shelfBoostModel,
                defaultUnlockFilter,
                directTierUpgrade,
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
                tomeLocksEnabled,
                lockedShelfBreakable,
                lockedBookExtractionBlocked,
                provenanceEnabled,
                ritualQueueEnabled,
                earlyUnlockXpLevels,
                earlyUnlockGritCount,
                sacrificeRequired,
                tomePageRequiredShelves,
                incrementalCredit,
                xpMode,
                costTableDataset,
                materialSelectionMode,
                researchRoundingMode,
                libraryDiscountEnabled,
                maxWoodenDiscountPercent,
                maxTotalXpDiscountPercent,
                bookCombineEnabled,
                bookCombineXpMultiplier,
                bookResearchEnabled,
                bookResearchXpMultiplier,
                epiphanyEnabled,
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
                transformationSetMode,
                requirePriorDiscovery,
                drainedBookResult,
                tomePageEnabled,
                tier1Bookshelves,
                tier2Bookshelves,
                tier3QualifyingEnchantments,
                transformationMaxBatchSize,
                vanillaChestBooksMode,
                bookshelfPoolEnabled,
                bookshelfMinBooks,
                bookshelfMaxBooks,
                bookshelfEnchantedProb,
                librarianTomeOffers,
                restrictVillagerInfinityBooks,
                grindstoneDepletedTome,
                customizedKeys
            );
        }
    }
