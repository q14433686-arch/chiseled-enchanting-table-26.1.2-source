package chiseled_enchanting_table.data;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Inspects active datapack JSON tables to identify which runtime configuration keys are
 * explicitly governed by an external datapack when {@code overrideDatapacks == false}.
 */
final class DatapackKeyExtractor {
    private DatapackKeyExtractor() {}

    static void collectExternalDatapackKeys(
        ResourceManager resourceManager,
        Identifier tableId,
        String tableName,
        Set<String> outKeys
    ) {
        // The table loader uses getResource (whole-file replacement), so only the selected layer
        // may govern fields. Never lock keys from a shadowed lower layer or assume stack index 0.
        var selected = resourceManager.getResource(tableId);
        if (selected.isEmpty()) return;
        var resource = selected.get();
        try (Reader reader = resource.openAsReader()) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            boolean own = ChiseledEnchantingTable.FABRIC_MOD_ID.equals(resource.sourcePackId());
            // Aggregate sources such as "fabric"/"vanilla" cannot prove ownership by ID alone, so
            // only there do we spend a content comparison against the bundled table. A user pack
            // byte-identical to the bundled default is external governance, not our own layer.
            if (!own && ("fabric".equals(resource.sourcePackId()) || "vanilla".equals(resource.sourcePackId()))) {
                try (var stream = ChiseledEnchantingTableData.class.getResourceAsStream(
                    "/data/" + tableId.getNamespace() + "/" + tableId.getPath())) {
                    if (stream != null) own = object.equals(JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
                }
            }
            if (!own) outKeys.addAll(extractConfigKeysFromDatapackJson(tableName, object));
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to inspect effective table " + tableId, error);
        }
    }

    static Set<String> extractConfigKeysFromDatapackJson(String tableName, JsonObject root) {
        if (root == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        switch (tableName) {
            case "shelf_gates" -> {
                String[] directKeys = {
                    "direct_gates_enabled", "blank_max_tier_mode", "max_direct_bookshelves",
                    "horizontal_scan_radius", "vertical_scan_radius", "require_breathing_room",
                    "min_open_adjacent_blocks", "require_line_of_sight", "vanilla_table_mode",
                    "tier1_bookshelves", "tier2_bookshelves", "tier3_qualifying_enchantments"
                };
                for (String k : directKeys) {
                    if (root.has(k)) keys.add(k);
                }
                if (root.has("base_batch_size") || root.has("tier3_batch_size")) {
                    keys.add("transformation_max_batch_size");
                }
                JsonObject sv = root.has("scan_volume") && root.get("scan_volume").isJsonObject()
                    ? root.getAsJsonObject("scan_volume") : null;
                if (sv != null) {
                    if (sv.has("horizontal_radius")) keys.add("horizontal_scan_radius");
                    if (sv.has("vertical_radius")) keys.add("vertical_scan_radius");
                    if (sv.has("require_breathing_room")) keys.add("require_breathing_room");
                    if (sv.has("min_open_adjacent_blocks")) keys.add("min_open_adjacent_blocks");
                    if (sv.has("require_line_of_sight")) keys.add("require_line_of_sight");
                }
                JsonObject dg = root.has("direct_upgrade_gates") && root.get("direct_upgrade_gates").isJsonObject()
                    ? root.getAsJsonObject("direct_upgrade_gates") : null;
                if (dg != null) {
                    if (dg.has("enabled")) keys.add("direct_gates_enabled");
                    if (dg.has("max_bookshelves")) keys.add("max_direct_bookshelves");
                    if (dg.has("blank_max_tier_mode")) keys.add("blank_max_tier_mode");
                }
                JsonObject dep = root.has("depleted_tome_gradients") && root.get("depleted_tome_gradients").isJsonObject()
                    ? root.getAsJsonObject("depleted_tome_gradients") : null;
                if (dep != null) {
                    if (dep.has("tier1_bookshelves")) keys.add("tier1_bookshelves");
                    if (dep.has("tier2_bookshelves")) keys.add("tier2_bookshelves");
                    if (dep.has("tier3_qualifying_enchantments")) keys.add("tier3_qualifying_enchantments");
                    if (dep.has("base_batch_size") || dep.has("tier3_batch_size")) {
                        keys.add("transformation_max_batch_size");
                    }
                }
            }
            case "library_resonance" -> {
                JsonObject wooden = root.has("wooden_bookshelves") && root.get("wooden_bookshelves").isJsonObject()
                    ? root.getAsJsonObject("wooden_bookshelves") : null;
                JsonObject mastery = root.has("chiseled_mastery") && root.get("chiseled_mastery").isJsonObject()
                    ? root.getAsJsonObject("chiseled_mastery") : null;
                if (hasAny(root, wooden, "max_wooden_discount_percent")) keys.add("max_wooden_discount_percent");
                if (hasAny(root, mastery, "max_total_xp_discount_percent")) keys.add("max_total_xp_discount_percent");
                String[] masteryKeys = {
                    "tome_consumption_mode", "max_tome_uses", "book_copy_wear_cost", "tome_deplete_behavior",
                    "tome_regen_enabled", "tome_regen_base_seconds", "tome_regen_tier1_shelves",
                    "tome_regen_tier1_seconds", "tome_regen_tier2_shelves", "tome_regen_tier2_seconds"
                };
                for (String k : masteryKeys) {
                    if (hasAny(root, mastery, k)) keys.add(k);
                }
                JsonObject tomePages = root.has("tome_pages") && root.get("tome_pages").isJsonObject()
                    ? root.getAsJsonObject("tome_pages") : null;
                String[] tomePageKeys = {
                    "locked_shelf_breakable", "locked_book_extraction_blocked", "early_unlock_xp_levels",
                    "early_unlock_grit_count", "sacrifice_required", "tome_page_required_shelves"
                };
                for (String k : tomePageKeys) {
                    if (hasAny(root, tomePages, k)) keys.add(k);
                }
            }
            case "book_upgrades" -> {
                JsonObject comb = root.has("combine_route") && root.get("combine_route").isJsonObject()
                    ? root.getAsJsonObject("combine_route") : null;
                JsonObject res = root.has("research_route") && root.get("research_route").isJsonObject()
                    ? root.getAsJsonObject("research_route") : null;
                if (comb != null && comb.has("enabled")) keys.add("book_combine_enabled");
                if (comb != null && comb.has("xp_cost_multiplier_of_vanilla_like")) keys.add("book_combine_xp_multiplier");
                if (res != null && res.has("enabled")) keys.add("book_research_enabled");
                if (res != null && res.has("xp_cost_multiplier_of_direct_enchant")) keys.add("book_research_xp_multiplier");
            }
            case "treasure_epiphany" -> {
                if (root.has("min_regular_bookshelves")) keys.add("epiphany_min_bookshelves");
                if (root.has("base_chance")) keys.add("epiphany_base_chance");
                if (root.has("max_chance")) keys.add("epiphany_max_chance");
                if (root.has("max_slots_cap")) keys.add("epiphany_max_slots_cap");
                if (root.has("cycle_ticks")) keys.add("epiphany_cycle_seconds");
                if (root.has("freeze_xp_levels")) keys.add("epiphany_freeze_xp_levels");
                if (root.has("freeze_duration_ticks")) keys.add("epiphany_freeze_duration_seconds");
                if (root.has("max_freeze_ticks")) keys.add("epiphany_max_freeze_seconds");
                if (root.has("reroll_xp_levels")) keys.add("epiphany_reroll_xp_levels");
            }
            case "disenchanting" -> {
                if (root.has("enabled")) keys.add("disenchant_enabled");
                if (root.has("xp_refund_scale")) keys.add("xp_refund_scale");
                if (root.has("external_xp_refund_scale")) keys.add("external_xp_refund_scale");
                if (root.has("allow_curse_purge")) keys.add("allow_curse_purge");
                JsonObject strip = root.has("targeted_strip") && root.get("targeted_strip").isJsonObject()
                    ? root.getAsJsonObject("targeted_strip") : null;
                if (strip != null && (strip.has("xp_cost_min") || strip.has("xp_levels"))) keys.add("strip_xp_min");
                if (strip != null && strip.has("xp_cost_max")) keys.add("strip_xp_max");
                JsonObject curse = root.has("curse_purge") && root.get("curse_purge").isJsonObject()
                    ? root.getAsJsonObject("curse_purge") : null;
                if (curse != null && (curse.has("xp_cost") || curse.has("xp_levels"))) keys.add("curse_purge_xp_cost");
            }
            case "item_transformations" -> {
                if (root.has("require_prior_discovery")) {
                    keys.add("require_prior_discovery");
                } else if (root.has("values") && root.get("values").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("values")) {
                        if (el.isJsonObject() && el.getAsJsonObject().has("require_prior_discovery")) {
                            keys.add("require_prior_discovery");
                            break;
                        }
                    }
                }
            }
            case "loot_injections" -> {
                if (root.has("vanilla_chest_books")) keys.add("vanilla_chest_books_mode");
            }
            case "bookshelf_book_pool" -> {
                if (root.has("enabled")) keys.add("bookshelf_pool_enabled");
                if (root.has("min_books")) keys.add("bookshelf_min_books");
                if (root.has("max_books")) keys.add("bookshelf_max_books");
                JsonObject bc = root.has("book_count") && root.get("book_count").isJsonObject()
                    ? root.getAsJsonObject("book_count") : null;
                if (bc != null) {
                    if (bc.has("min")) keys.add("bookshelf_min_books");
                    if (bc.has("max")) keys.add("bookshelf_max_books");
                }
                if (root.has("enchanted_book_probability")) keys.add("bookshelf_enchanted_prob");
            }
            default -> {}
        }
        return Set.copyOf(keys);
    }

    private static boolean hasAny(JsonObject root, JsonObject sub, String key) {
        return root.has(key) || (sub != null && sub.has(key));
    }
}
