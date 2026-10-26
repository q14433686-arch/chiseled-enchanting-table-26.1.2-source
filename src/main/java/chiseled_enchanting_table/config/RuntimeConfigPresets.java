package chiseled_enchanting_table.config;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import java.util.Objects;

import chiseled_enchanting_table.config.ModRuntimeConfig.*;

/** Preset definitions write a draft; they never alter published runtime state. */
final class RuntimeConfigPresets {
    private RuntimeConfigPresets() {}
        static void apply(Preset preset, ConfigDraft draft) {
            if (draft == null) {
                return;
            }
            boolean preservedOverrideDp = draft.overrideDatapacks();

            ConfigDraft fresh = new ConfigDraft();
            fresh.enchantedBookStackSize() = 64;
            fresh.overrideDatapacks() = preservedOverrideDp;

            switch (preset) {
                case STANDARD -> {
                    // Pure shipped defaults (including 64-stackable Enchanted Books).
                }
                case CLASSIC -> {
                    fresh.applyClassic103Overrides();
                    markModifiedRuntimeKeys(fresh);
                }
                case CASUAL -> {
                    fresh.vanillaTableMode() = VanillaTableMode.BOOKS;
                    fresh.anvilFlatRepairCost() = true;
                    fresh.anvilBlockSameBookMerge() = false;
                    fresh.restrictVillagerInfinityBooks() = false;
                    fresh.grindstoneDepletedTome() = false;

                    fresh.directGatesEnabled() = false;
                    fresh.blankMaxTierMode() = BlankMaxTierMode.FULL_CURVE;
                    fresh.horizontalScanRadius() = 4;
                    fresh.verticalScanRadius() = 2;
                    fresh.requireBreathingRoom() = false;
                    fresh.requireLineOfSight() = false;

                    fresh.tomeConsumptionMode() = MasterTomeWear.ConsumptionMode.KEEP;
                    fresh.tomeRegenEnabled() = true;
                    fresh.tomeRegenBaseSeconds() = 3_600;
                    fresh.tomeRegenTier1Seconds() = 1_800;
                    fresh.tomeRegenTier2Seconds() = 600;
                    fresh.lockedShelfBreakable() = true;
                    fresh.lockedBookExtractionBlocked() = false;
                    fresh.maxWoodenDiscountPercent() = 20;
                    fresh.maxTotalXpDiscountPercent() = 50;
                    fresh.bookCombineEnabled() = true;
                    fresh.bookCombineXpMultiplier() = 0.50;
                    fresh.bookResearchEnabled() = true;
                    fresh.bookResearchXpMultiplier() = 1.00;

                    fresh.epiphanyEnabled() = true;
                    fresh.epiphanyMinBookshelves() = 16;
                    fresh.epiphanyBaseChance() = 0.45;
                    fresh.epiphanyMaxChance() = 0.95;

                    fresh.disenchantEnabled() = true;
                    fresh.xpRefundScale() = 1.00;
                    fresh.externalXpRefundScale() = 0.50;
                    fresh.allowCursePurge() = true;
                    fresh.cursePurgeXpCost() = 6;
                    fresh.requirePriorDiscovery() = false;
                    markModifiedRuntimeKeys(fresh);
                }
                case EXPERT -> {
                    fresh.vanillaTableMode() = VanillaTableMode.ALL;
                    fresh.anvilFlatRepairCost() = false;
                    fresh.anvilBlockSameBookMerge() = true;
                    fresh.restrictVillagerInfinityBooks() = true;
                    fresh.grindstoneDepletedTome() = true;

                    fresh.defaultUnlockFilter() = DefaultUnlockFilter.EXCLUDE_SPECIAL;
                    fresh.directTierUpgrade() = false;
                    fresh.directGatesEnabled() = true;
                    fresh.blankMaxTierMode() = BlankMaxTierMode.LEVEL_ONE_ONLY;
                    fresh.maxDirectBookshelves() = 48;
                    fresh.horizontalScanRadius() = 3;
                    fresh.verticalScanRadius() = 2;
                    fresh.requireBreathingRoom() = true;
                    fresh.minOpenAdjacentBlocks() = 12;
                    fresh.requireLineOfSight() = true;

                    fresh.tomeConsumptionMode() = MasterTomeWear.ConsumptionMode.WEAR;
                    fresh.maxTomeUses() = 2;
                    fresh.bookCopyWearCost() = 2;
                    fresh.tomeDepleteBehavior() = MasterTomeWear.DepleteBehavior.DEPLETED_TOME;
                    fresh.tomeRegenEnabled() = true;
                    fresh.tomeRegenBaseSeconds() = 14_400;
                    fresh.tomeRegenTier1Shelves() = 42;
                    fresh.tomeRegenTier1Seconds() = 7_200;
                    fresh.tomeRegenTier2Shelves() = 52;
                    fresh.tomeRegenTier2Seconds() = 3_600;
                    fresh.tomeLocksEnabled() = true;
                    fresh.lockedShelfBreakable() = false;
                    fresh.lockedBookExtractionBlocked() = true;
                    fresh.provenanceEnabled() = true;
                    fresh.ritualQueueEnabled() = true;

                    fresh.incrementalCredit() = false;
                    fresh.materialSelectionMode() = MaterialSelectionMode.PLAYER_SALTED_SINGLE;
                    fresh.researchRoundingMode() = ResearchRoundingMode.ROUND_NEAREST;
                    fresh.libraryDiscountEnabled() = true;
                    fresh.maxWoodenDiscountPercent() = 8;
                    fresh.maxTotalXpDiscountPercent() = 20;
                    fresh.bookCombineEnabled() = true;
                    fresh.bookCombineXpMultiplier() = 1.35;
                    fresh.bookResearchEnabled() = true;
                    fresh.bookResearchXpMultiplier() = 2.25;

                    fresh.epiphanyEnabled() = true;
                    fresh.epiphanyMinBookshelves() = 42;
                    fresh.epiphanyBaseChance() = 0.12;
                    fresh.epiphanyMaxChance() = 0.60;
                    fresh.epiphanyMaxSlotsCap() = 2;
                    fresh.epiphanyCycleSeconds() = 900;
                    fresh.epiphanyFreezeXpLevels() = 8;
                    fresh.epiphanyFreezeDurationSeconds() = 600;
                    fresh.epiphanyMaxFreezeSeconds() = 1_800;
                    fresh.epiphanyRerollXpLevels() = 10;

                    fresh.disenchantEnabled() = true;
                    fresh.xpRefundScale() = 0.50;
                    fresh.externalXpRefundScale() = 0.05;
                    fresh.stripXpMin() = 6;
                    fresh.stripXpMax() = 18;
                    fresh.allowCursePurge() = true;
                    fresh.cursePurgeXpCost() = 28;
                    fresh.requirePriorDiscovery() = true;
                    fresh.drainedBookResult() = DrainedBookResult.DEPLETED_TOME;
                    fresh.tier1Bookshelves() = 20;
                    fresh.tier2Bookshelves() = 36;
                    fresh.tier3QualifyingEnchantments() = 10;
                    fresh.transformationMaxBatchSize() = 8;
                    fresh.tomePageEnabled() = true;
                    fresh.tomePageRequiredShelves() = 12;
                    fresh.sacrificeRequired() = 24;
                    fresh.earlyUnlockXpLevels() = 25;
                    fresh.earlyUnlockGritCount() = 12;

                    fresh.vanillaChestBooksMode() = VanillaChestBooksMode.STRIP_VANILLA;
                    fresh.bookshelfPoolEnabled() = true;
                    fresh.bookshelfMinBooks() = 1;
                    fresh.bookshelfMaxBooks() = 2;
                    fresh.bookshelfEnchantedProb() = 0.03;
                    fresh.librarianTomeOffers() = false;
                    markModifiedRuntimeKeys(fresh);
                }
            }

            fresh.normalizeAfterEdit(null);
            draft.copyFrom(fresh);
        }

        private static void markModifiedRuntimeKeys(ConfigDraft target) {
            ConfigDraft def = new ConfigDraft();
            for (var opt : ConfigCatalog.ALL_OPTIONS) {
                if ("override_datapacks".equals(opt.key()) || "enchanted_book_stack_size".equals(opt.key())) {
                    continue;
                }
                if (!Objects.equals(opt.get(target), opt.get(def))) {
                    target.customizedKeys.add(opt.key());
                }
            }
        }
}
