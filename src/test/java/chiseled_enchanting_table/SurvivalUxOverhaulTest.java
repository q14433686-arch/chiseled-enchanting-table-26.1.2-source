package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.ritual.RitualActionService;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import org.junit.jupiter.api.Test;

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
}
