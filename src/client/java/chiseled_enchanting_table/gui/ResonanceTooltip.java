package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import java.util.ArrayList;
import java.util.List;
import chiseled_enchanting_table.compat.CetFormatting;
import net.minecraft.network.chat.Style;
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

    static List<Component> build(ChiseledEnchantingTableScreenHandler menu, InscriptionRitualMechanics.LibraryRitualScan ignored) {
        var tip = new ArrayList<Component>();
        tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.title", menu.xpDiscountPercent,
            menu.libraryResonanceConfig().maxTotalXpDiscountPercent()).withStyle(CetFormatting.GOLD));
        var sg = menu.shelfGatesConfig();
        var lr = menu.libraryResonanceConfig();
        int ordinary = menu.regularBookshelves;
        int next = java.util.stream.IntStream.of(menu.isLibraryDiscountEnabled() ? lr.minDiscountBookshelves() : 0,
            menu.isLibraryDiscountEnabled() ? lr.maxDiscountBookshelves() : 0,
            menu.allowsDirectTierUpgrade() ? sg.tier1Bookshelves() : 0,
            menu.isEpiphanyEnabled() ? menu.epiphanyMinBookshelves() : 0, menu.allowsDirectTierUpgrade() ? sg.tier2Bookshelves() : 0, menu.allowsDirectTierUpgrade() ? sg.maxDirectBookshelves() : 0)
            .filter(n -> n > ordinary).min().orElse(ordinary);
        tip.add(Component.translatable("gui.chiseled_enchanting_table.ui.ordinary_progress", ordinary, next,
            Math.max(0, next - ordinary)).withStyle(next > ordinary ? CetFormatting.YELLOW : CetFormatting.GREEN));
        tip.add(Component.translatable("gui.chiseled_enchanting_table.ui.total_shelves", menu.totalLibraryBookshelves())
            .withStyle(CetFormatting.AQUA));
        if (lr.tomeRegenEnabled() && lr.tomeConsumptionMode() != chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP) {
            int total = menu.totalLibraryBookshelves();
            int regenNext = java.util.stream.IntStream.of(lr.tomeRegenTier1Shelves(), lr.tomeRegenTier2Shelves())
                .filter(n -> n > total).min().orElse(total);
            tip.add((regenNext > total
                ? Component.translatable("gui.chiseled_enchanting_table.ui.regen_gap", regenNext - total)
                : Component.translatable("gui.chiseled_enchanting_table.ui.regen_complete")).withStyle(CetFormatting.GREEN));
        }
        if (menu.allowsDirectTierUpgrade()) tip.add(Component.translatable("gui.chiseled_enchanting_table.ui.shelf_hint")
            .withStyle(CetFormatting.GRAY));
        if (menu.crampedLibrary) tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.cramped").withStyle(CetFormatting.RED));
        if (TooltipPresentation.detailsRequested()) tip.addAll(ResonanceDetailsTooltip.build(menu, ignored));
        else tip.add(Component.translatable("gui.chiseled_enchanting_table.ui.details").withStyle(CetFormatting.DARK_GRAY));
        return tip;
    }
}
