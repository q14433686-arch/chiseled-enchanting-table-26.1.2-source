package chiseled_enchanting_table.config;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chiseled_enchanting_table.config.ModRuntimeConfig.*;
import static chiseled_enchanting_table.config.ModRuntimeConfig.*;

/** Pure JSON schema and legacy migration. No disk, remote state or publication. */
final class RuntimeConfigJson {
    private RuntimeConfigJson() {}
    static JsonObject toJson(ModRuntimeConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty(
            "_comment",
            "Chiseled Enchanting Table unified granular configuration. Editable directly in-game via the Config button, Arcane Grimoire, /cet, or Mod Menu."
        );
        root.addProperty("enchanted_book_stack_size", config.enchantedBookStackSize());

        JsonObject general = new JsonObject();
        general.addProperty("override_datapacks", config.overrideDatapacks());
        general.addProperty("enchanted_book_stack_size", config.enchantedBookStackSize());
        general.addProperty("vanilla_table_mode", config.vanillaTableMode().token());
        general.addProperty("anvil_flat_repair_cost", config.anvilFlatRepairCost());
        general.addProperty("anvil_block_same_book_merge", config.anvilBlockSameBookMerge());
        general.addProperty("restrict_villager_infinity_books", config.restrictVillagerInfinityBooks());
        general.addProperty("grindstone_depleted_tome", config.grindstoneDepletedTome());
        // Derived legacy mirror kept so external tools inspecting JSON can see full-1.0.3 state.
        general.addProperty("classic_1_0_3_mode", config.isAllClassicAtoms());
        root.add("general", general);

        JsonObject bookshelves = new JsonObject();
        bookshelves.addProperty("shelf_boost_model", config.shelfBoostModel().token());
        bookshelves.addProperty("default_unlock_filter", config.defaultUnlockFilter().token());
        bookshelves.addProperty("direct_tier_upgrade", config.directTierUpgrade());
        bookshelves.addProperty("direct_gates_enabled", config.directGatesEnabled());
        bookshelves.addProperty("blank_max_tier_mode", config.blankMaxTierMode().token());
        bookshelves.addProperty("max_direct_bookshelves", config.maxDirectBookshelves());
        bookshelves.addProperty("horizontal_scan_radius", config.horizontalScanRadius());
        bookshelves.addProperty("vertical_scan_radius", config.verticalScanRadius());
        bookshelves.addProperty("require_breathing_room", config.requireBreathingRoom());
        bookshelves.addProperty("min_open_adjacent_blocks", config.minOpenAdjacentBlocks());
        bookshelves.addProperty("require_line_of_sight", config.requireLineOfSight());
        root.add("bookshelves", bookshelves);

        JsonObject tomes = new JsonObject();
        tomes.addProperty("tome_consumption_mode", config.tomeConsumptionMode().token());
        tomes.addProperty("max_tome_uses", config.maxTomeUses());
        tomes.addProperty("book_copy_wear_cost", config.bookCopyWearCost());
        tomes.addProperty("tome_deplete_behavior", config.tomeDepleteBehavior().token());
        tomes.addProperty("tome_regen_enabled", config.tomeRegenEnabled());
        tomes.addProperty("tome_regen_base_seconds", config.tomeRegenBaseSeconds());
        tomes.addProperty("tome_regen_tier1_shelves", config.tomeRegenTier1Shelves());
        tomes.addProperty("tome_regen_tier1_seconds", config.tomeRegenTier1Seconds());
        tomes.addProperty("tome_regen_tier2_shelves", config.tomeRegenTier2Shelves());
        tomes.addProperty("tome_regen_tier2_seconds", config.tomeRegenTier2Seconds());
        tomes.addProperty("tome_locks", config.tomeLocksEnabled());
        tomes.addProperty("locked_shelf_breakable", config.lockedShelfBreakable());
        tomes.addProperty("locked_book_extraction_blocked", config.lockedBookExtractionBlocked());
        tomes.addProperty("provenance", config.provenanceEnabled());
        tomes.addProperty("ritual_queue", config.ritualQueueEnabled());
        tomes.addProperty("early_unlock_xp_levels", config.earlyUnlockXpLevels());
        tomes.addProperty("early_unlock_grit_count", config.earlyUnlockGritCount());
        tomes.addProperty("sacrifice_required", config.sacrificeRequired());
        tomes.addProperty("tome_page_required_shelves", config.tomePageRequiredShelves());
        tomes.addProperty("incremental_credit", config.incrementalCredit());
        tomes.addProperty("xp_mode", config.xpMode().token());
        tomes.addProperty("cost_table", config.costTableDataset().token());
        tomes.addProperty("material_selection", config.materialSelectionMode().token());
        tomes.addProperty("research_rounding", config.researchRoundingMode().token());
        tomes.addProperty("library_discount", config.libraryDiscountEnabled());
        tomes.addProperty("max_wooden_discount_percent", config.maxWoodenDiscountPercent());
        tomes.addProperty("max_total_xp_discount_percent", config.maxTotalXpDiscountPercent());
        tomes.addProperty("book_combine_enabled", config.bookCombineEnabled());
        tomes.addProperty("book_combine_xp_multiplier", config.bookCombineXpMultiplier());
        tomes.addProperty("book_research_enabled", config.bookResearchEnabled());
        tomes.addProperty("book_research_xp_multiplier", config.bookResearchXpMultiplier());
        root.add("tomes_and_economy", tomes);

        JsonObject epiphany = new JsonObject();
        epiphany.addProperty("epiphany_enabled", config.epiphanyEnabled());
        epiphany.addProperty("epiphany_min_bookshelves", config.epiphanyMinBookshelves());
        epiphany.addProperty("epiphany_base_chance", config.epiphanyBaseChance());
        epiphany.addProperty("epiphany_max_chance", config.epiphanyMaxChance());
        epiphany.addProperty("epiphany_max_slots_cap", config.epiphanyMaxSlotsCap());
        epiphany.addProperty("epiphany_cycle_seconds", config.epiphanyCycleSeconds());
        epiphany.addProperty("epiphany_freeze_xp_levels", config.epiphanyFreezeXpLevels());
        epiphany.addProperty("epiphany_freeze_duration_seconds", config.epiphanyFreezeDurationSeconds());
        epiphany.addProperty("epiphany_max_freeze_seconds", config.epiphanyMaxFreezeSeconds());
        epiphany.addProperty("epiphany_reroll_xp_levels", config.epiphanyRerollXpLevels());
        root.add("treasure_epiphany", epiphany);

        JsonObject cleanse = new JsonObject();
        cleanse.addProperty("disenchant_enabled", config.disenchantEnabled());
        cleanse.addProperty("xp_refund_scale", config.xpRefundScale());
        cleanse.addProperty("external_xp_refund_scale", config.externalXpRefundScale());
        cleanse.addProperty("strip_xp_min", config.stripXpMin());
        cleanse.addProperty("strip_xp_max", config.stripXpMax());
        cleanse.addProperty("allow_curse_purge", config.allowCursePurge());
        cleanse.addProperty("curse_purge_xp_cost", config.cursePurgeXpCost());
        cleanse.addProperty("transformation_set", config.transformationSetMode().token());
        cleanse.addProperty("require_prior_discovery", config.requirePriorDiscovery());
        cleanse.addProperty("drained_book_result", config.drainedBookResult().token());
        cleanse.addProperty("tome_page_enabled", config.tomePageEnabled());
        cleanse.addProperty("tier1_bookshelves", config.tier1Bookshelves());
        cleanse.addProperty("tier2_bookshelves", config.tier2Bookshelves());
        cleanse.addProperty("tier3_qualifying_enchantments", config.tier3QualifyingEnchantments());
        cleanse.addProperty("transformation_max_batch_size", config.transformationMaxBatchSize());
        root.add("cleanse_and_transformations", cleanse);

        JsonObject world = new JsonObject();
        world.addProperty("vanilla_chest_books_mode", config.vanillaChestBooksMode().token());
        world.addProperty("bookshelf_pool_enabled", config.bookshelfPoolEnabled());
        world.addProperty("bookshelf_min_books", config.bookshelfMinBooks());
        world.addProperty("bookshelf_max_books", config.bookshelfMaxBooks());
        world.addProperty("bookshelf_enchanted_prob", config.bookshelfEnchantedProb());
        world.addProperty("librarian_tome_offers", config.librarianTomeOffers());
        root.add("world_and_loot", world);

        com.google.gson.JsonArray customized = new com.google.gson.JsonArray();
        for (String key : config.customizedKeys()) {
            customized.add(key);
        }
        root.add("customized_keys", customized);
        return root;
    }

    static ModRuntimeConfig fromJson(JsonObject root) {
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

        // One-time migration for pre-atom configs carrying `classic_1_0_3_mode: true`:
        // expand all 19 classic rule atoms first, then overlay any explicit fields present in JSON.
        boolean legacyClassicFlag = boolOr(
            general,
            "classic_1_0_3_mode",
            boolOr(root, "classic_1_0_3_mode", false)
        );
        boolean hasGranularSchema = shelves.has("shelf_boost_model")
            || tomes.has("cost_table")
            || epiphany.has("epiphany_enabled")
            || cleanse.has("transformation_set");
        if (legacyClassicFlag && !hasGranularSchema) {
            d.applyClassic103Atoms();
            d.customizedKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
        }

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

        if (shelves.has("shelf_boost_model")) {
            d.shelfBoostModel = ShelfBoostModel.fromToken(shelves.get("shelf_boost_model").getAsString());
        }
        if (shelves.has("default_unlock_filter")) {
            d.defaultUnlockFilter = DefaultUnlockFilter.fromToken(shelves.get("default_unlock_filter").getAsString());
        }
        d.directTierUpgrade = boolOr(shelves, "direct_tier_upgrade", d.directTierUpgrade);
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
        d.tomeLocksEnabled = boolOr(tomes, "tome_locks", d.tomeLocksEnabled);
        d.lockedShelfBreakable = boolOr(tomes, "locked_shelf_breakable", d.lockedShelfBreakable);
        d.lockedBookExtractionBlocked = boolOr(tomes, "locked_book_extraction_blocked", d.lockedBookExtractionBlocked);
        d.provenanceEnabled = boolOr(tomes, "provenance", d.provenanceEnabled);
        d.ritualQueueEnabled = boolOr(tomes, "ritual_queue", d.ritualQueueEnabled);
        d.earlyUnlockXpLevels = intOr(tomes, "early_unlock_xp_levels", d.earlyUnlockXpLevels);
        d.earlyUnlockGritCount = intOr(tomes, "early_unlock_grit_count", d.earlyUnlockGritCount);
        d.sacrificeRequired = intOr(tomes, "sacrifice_required", d.sacrificeRequired);
        d.tomePageRequiredShelves = intOr(tomes, "tome_page_required_shelves", d.tomePageRequiredShelves);
        d.incrementalCredit = boolOr(tomes, "incremental_credit", d.incrementalCredit);
        if (tomes.has("xp_mode")) {
            d.xpMode = XpSettlementMode.fromToken(tomes.get("xp_mode").getAsString());
        }
        if (tomes.has("cost_table")) {
            d.costTableDataset = CostTableDataset.fromToken(tomes.get("cost_table").getAsString());
        }
        if (tomes.has("material_selection")) {
            d.materialSelectionMode = MaterialSelectionMode.fromToken(tomes.get("material_selection").getAsString());
        }
        if (tomes.has("research_rounding")) {
            d.researchRoundingMode = ResearchRoundingMode.fromToken(tomes.get("research_rounding").getAsString());
        }
        d.libraryDiscountEnabled = boolOr(tomes, "library_discount", d.libraryDiscountEnabled);
        d.maxWoodenDiscountPercent = intOr(tomes, "max_wooden_discount_percent", d.maxWoodenDiscountPercent);
        d.maxTotalXpDiscountPercent = intOr(tomes, "max_total_xp_discount_percent", d.maxTotalXpDiscountPercent);
        d.bookCombineEnabled = boolOr(tomes, "book_combine_enabled", d.bookCombineEnabled);
        d.bookCombineXpMultiplier = doubleOr(tomes, "book_combine_xp_multiplier", d.bookCombineXpMultiplier);
        d.bookResearchEnabled = boolOr(tomes, "book_research_enabled", d.bookResearchEnabled);
        d.bookResearchXpMultiplier = doubleOr(tomes, "book_research_xp_multiplier", d.bookResearchXpMultiplier);

        d.epiphanyEnabled = boolOr(epiphany, "epiphany_enabled", d.epiphanyEnabled);
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
        if (cleanse.has("transformation_set")) {
            d.transformationSetMode = TransformationSetMode.fromToken(cleanse.get("transformation_set").getAsString());
        }
        d.requirePriorDiscovery = boolOr(cleanse, "require_prior_discovery", d.requirePriorDiscovery);
        if (cleanse.has("drained_book_result")) {
            d.drainedBookResult = DrainedBookResult.fromToken(cleanse.get("drained_book_result").getAsString());
        }
        d.tomePageEnabled = boolOr(cleanse, "tome_page_enabled", d.tomePageEnabled);
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
        d.librarianTomeOffers = boolOr(world, "librarian_tome_offers", d.librarianTomeOffers);

        if (root.has("customized_keys") && root.get("customized_keys").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("customized_keys")) {
                if (el.isJsonPrimitive()) {
                    String k = el.getAsString();
                    if ("classic_1_0_3_mode".equals(k)) {
                        d.customizedKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
                    } else {
                        d.customizedKeys.add(k);
                    }
                }
            }
        }
        return d.build();
    }

    static JsonObject objOrEmpty(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
            ? parent.getAsJsonObject(key)
            : new JsonObject();
    }

    static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
    }

    static int intOr(JsonObject obj, String key, int fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
    }

    static double doubleOr(JsonObject obj, String key, double fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsDouble() : fallback;
    }
}
