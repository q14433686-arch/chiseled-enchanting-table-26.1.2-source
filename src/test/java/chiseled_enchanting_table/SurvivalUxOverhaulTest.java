package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.enchanting.CleanseMechanics;
import chiseled_enchanting_table.mechanics.ritual.RitualActionService;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.MenuCleanseQuotes;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalUxOverhaulTest {

    @Test
    void smartRitualGateBypassesLowAndMidTierDirectInscriptions() {
        // Low/mid-tier direct inscriptions (e.g. Sharpness I..IV/V, Unbreaking I..II/III, Aqua Affinity I/I)
        assertTrue(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 1, 5));
        assertTrue(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 4, 5));
        assertTrue(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 2, 3));
        assertTrue(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 1, 1));
        assertTrue(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 2, 2));
        // Disabled ritual queue config always bypasses ritual wait
        assertTrue(RitualActionService.shouldBypassRitualWait(false, true, true, OptionType.TREASURE_EPIPHANY, 5, 5));
    }

    @Test
    void smartRitualGateRequiresRitualForCapstonesTomeCopiesEpiphaniesAndQueues() {
        // Final tier of a multi-tier enchantment (maxLevel >= 3 && targetLevel >= maxLevel)
        assertFalse(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 5, 5));
        assertFalse(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.ENCHANTMENT, 3, 3));
        // Master Tome copies always enter ritual waiting
        assertFalse(RitualActionService.shouldBypassRitualWait(true, false, true, OptionType.ENCHANTMENT, 1, 5));
        assertFalse(RitualActionService.shouldBypassRitualWait(true, false, true, OptionType.ENCHANTMENT, 1, 1));
        // Treasure Epiphany always enters ritual waiting
        assertFalse(RitualActionService.shouldBypassRitualWait(true, false, false, OptionType.TREASURE_EPIPHANY, 1, 1));
        // Multi-step queue (table already has an active ritual) always queues into ritual waiting
        assertFalse(RitualActionService.shouldBypassRitualWait(true, true, false, OptionType.ENCHANTMENT, 1, 5));
    }

    @Test
    void cleanseExtractPricingUsesOnePointFiveMultiplierCappedAtFifteen() {
        assertEquals(0, MenuCleanseQuotes.computeExtractRawXpLevels(0));
        assertEquals(5, MenuCleanseQuotes.computeExtractRawXpLevels(3));
        assertEquals(9, MenuCleanseQuotes.computeExtractRawXpLevels(6));
        assertEquals(15, MenuCleanseQuotes.computeExtractRawXpLevels(10));
        assertEquals(15, MenuCleanseQuotes.computeExtractRawXpLevels(14));
    }

    @Test
    void extractedBookGenerationBlocksMobLootArbitrageWhilePreservingCatalystAndTableProvenance() {
        assertEquals(0, CleanseMechanics.computeExtractedBookGeneration(0, true, false));
        assertEquals(0, CleanseMechanics.computeExtractedBookGeneration(0, false, true));
        assertEquals(1, CleanseMechanics.computeExtractedBookGeneration(0, false, false));
        assertEquals(2, CleanseMechanics.computeExtractedBookGeneration(2, false, false));
        assertEquals(2, CleanseMechanics.computeExtractedBookGeneration(2, false, true));
    }

    @Test
    void mendingFirstXpRefundMathRepairsTwoDurabilityPerPointAndReturnsRemainder() {
        int[] out = new int[1];
        assertEquals(0, CleanseMechanics.repairMendingDamageStep(100, 30, out));
        assertEquals(40, out[0]);
        assertEquals(15, CleanseMechanics.repairMendingDamageStep(29, 30, out));
        assertEquals(0, out[0]);
        assertEquals(50, CleanseMechanics.repairMendingDamageStep(0, 50, out));
        assertEquals(0, out[0]);
        assertEquals(0, CleanseMechanics.repairMendingDamageStep(10, 0, out));
        assertEquals(10, out[0]);
    }

    @Test
    void bagPayableCleanseFlagCoversOnlyMaterialCleanseOperations() {
        assertTrue(AvailableOption.isBagPayableCleanse(OptionType.STRIP_SINGLE_ENCHANTMENT));
        assertTrue(AvailableOption.isBagPayableCleanse(OptionType.EXTRACT_ENCHANTMENT_BOOK));
        assertTrue(AvailableOption.isBagPayableCleanse(OptionType.PURGE_CURSE));
        assertTrue(AvailableOption.isBagPayableCleanse(OptionType.PURGE_ALL_CURSES));
        assertFalse(AvailableOption.isBagPayableCleanse(OptionType.DISENCHANT_ALL));
        assertFalse(AvailableOption.isBagPayableCleanse(OptionType.DOWNGRADE_ENCHANTMENT));
        assertFalse(AvailableOption.isBagPayableCleanse(OptionType.ENCHANTMENT));
        assertFalse(AvailableOption.isBagPayableCleanse(null));
    }

    @Test
    void searchBoxAndCleansePixelArtResourcesExistAtExactPaths() throws Exception {
        Path guiRoot = Path.of("src/client/resources/assets/chiseled_enchanting_table/gui");
        String tableSkin = Files.readString(Path.of("src/client/java/chiseled_enchanting_table/gui/TableSkin.java"));
        assertTrue(tableSkin.contains("gui/config/search_box_focus.png"));
        assertFalse(tableSkin.contains("search_box_focused.png"));
        for (String rel : new String[] {
            "config/search_box.png", "config/search_box_focus.png",
            "ritual_panel.png", "btn_sacrifice_plaque.png",
            "cleanse_search_box.png", "cleanse_search_box_focus.png",
            "btn_purge_all_curses.png", "btn_cleanse_mode.png",
            "btn_cleanse_affordable.png", "btn_cleanse_expand.png"
        }) {
            assertTrue(Files.isRegularFile(guiRoot.resolve(rel)), "Missing GUI texture: " + rel);
        }
    }
}
