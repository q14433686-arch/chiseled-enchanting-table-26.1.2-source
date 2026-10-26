package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Read-only resonance/ritual fact presentation; world scanning stays at the screen boundary. */
final class ResonanceTooltip {
    private ResonanceTooltip() {}
    static String formatClock(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int mins = clamped / 60;
        int secs = clamped % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
    }

    static List<Component> build(ChiseledEnchantingTableScreenHandler menu, InscriptionRitualMechanics.LibraryRitualScan ritScan) {
        int woodenBonus = menu.woodenXpDiscountPercent();
        int chiseledBonus = menu.globalMasteryDiscountPercent();
        double totalUnits = menu.totalMasteryUnits();
        double affinityUnits = menu.affinityMasteryUnits(menu.get_enchantable_item());
        int minShelves = menu.epiphanyMinBookshelves();
        var sg = menu.shelfGatesConfig();
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.title",
            menu.xpDiscountPercent,
            menu.libraryResonanceConfig().maxTotalXpDiscountPercent()
        ).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.broad_breakdown",
            menu.totalLibraryBookshelves(),
            menu.regularBookshelves,
            menu.emptyChiseledBookshelves,
            menu.chiseledBookshelves
        ).withStyle(ChatFormatting.YELLOW));
        if (menu.crampedLibrary) {
            tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.cramped")
                .withStyle(ChatFormatting.RED));
        }
        int minDiscShelves = menu.libraryResonanceConfig().minDiscountBookshelves();
        if (menu.regularBookshelves < minDiscShelves) {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.wooden_locked",
                menu.regularBookshelves,
                minDiscShelves,
                minDiscShelves
            ).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", menu.regularBookshelves, woodenBonus)
                .withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.chiseled_mastery",
            String.format(java.util.Locale.ROOT, "%.1f", totalUnits),
            chiseledBonus
        ).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.slot_cap_hint",
            String.format(java.util.Locale.ROOT, "%.1f", menu.libraryResonanceConfig().maxMasteryUnitsPerSlot())
        ).withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.high_tier_books",
            menu.qualifyingChiseledEnchantments(),
            sg.tier3QualifyingEnchantments()
        ).withStyle(menu.qualifyingChiseledEnchantments() >= sg.tier3QualifyingEnchantments()
            ? ChatFormatting.GREEN
            : ChatFormatting.GRAY));
        var lrPanelCfg = menu.libraryResonanceConfig();
        if (lrPanelCfg.tomeRegenEnabled()
            && lrPanelCfg.tomeConsumptionMode() != chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP) {
            int nearShelves = menu.totalLibraryBookshelves();
            double regenSec = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                .computeRegenSeconds(lrPanelCfg, nearShelves);
            int regenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                .regenTierIndex(lrPanelCfg, nearShelves);
            Component tierBadge = switch (regenTier) {
                case 2 -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier2",
                    lrPanelCfg.tomeRegenTier2Shelves()
                );
                case 1 -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier1",
                    lrPanelCfg.tomeRegenTier1Shelves()
                );
                default -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier0",
                    lrPanelCfg.tomeRegenTier1Shelves()
                );
            };
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.pricing.tome_regen_rate",
                String.format(java.util.Locale.ROOT, "%.1f", regenSec / 1200.0),
                nearShelves,
                tierBadge
            ).withStyle(regenTier >= 2
                ? ChatFormatting.GOLD
                : (regenTier == 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
        if (affinityUnits > 0.005) {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.affinity_line",
                String.format(java.util.Locale.ROOT, "%.1f", affinityUnits)
            ).withStyle(ChatFormatting.GREEN));
        }
        if (menu.regularBookshelves >= minShelves) {
            int pct = (int) Math.round(menu.epiphanyChance() * 100.0);
            int maxSlots = Math.max(1, menu.epiphanyMaxSlots());
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.active", pct)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.slots_line", maxSlots)
                .withStyle(ChatFormatting.GOLD));
            if (menu.isEpiphanyFrozen()) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.frozen_remaining",
                    formatClock(menu.countdownSeconds()),
                    Math.max(1, menu.epiphanyMaxFreezeTicks() / 1200)
                ).withStyle(ChatFormatting.AQUA));
            } else {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.cycle_remaining",
                    formatClock(menu.countdownSeconds())
                ).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.epiphany.progress",
                menu.regularBookshelves,
                minShelves
            ).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.treasure_rule")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (ritScan != null) {
            var rCfg = menu.ritualConfig();
            double sigmaL = ritScan.totalWearImmuneLoad();
            double slipPct = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
                .computeLoadSlipProbability(rCfg, sigmaL, ritScan.totalDetectedBookshelves()) * 100.0;
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.overload_sigma_line",
                String.format(java.util.Locale.ROOT, "%.1f", sigmaL),
                String.format(java.util.Locale.ROOT, "%.1f", slipPct),
                String.format(java.util.Locale.ROOT, "%.1f", rCfg.crosslineSlip().minLoadThreshold())
            ).withStyle(slipPct > 0.005 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.scapegoat_order_line")
            .withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }
}
