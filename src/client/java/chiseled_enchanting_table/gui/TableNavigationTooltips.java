package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Tab and ritual-button hover text composition. */
final class TableNavigationTooltips {
    private TableNavigationTooltips() {}


    static List<Component> sacrificeSkipTip(ChiseledEnchantingTableScreenHandler menu) {
                int lvls = menu.currentSacrificeSkipLevels();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_title")
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_sacrifice_cost",
                    lvls,
                    menu.player.experienceLevel
                ).withStyle(menu.player.experienceLevel >= lvls ? ChatFormatting.GREEN : ChatFormatting.RED));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_safe")
                    .withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_currency")
                    .withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    static List<Component> rushSkipTip(ChiseledEnchantingTableScreenHandler menu) {
                double riskPct = menu.currentRushSkipDegradeChance() * 100.0;
                var rCfg = menu.ritualConfig();
                int capPct = (int) Math.round(100.0 * (menu.ritualUsedInfinityTome()
                    ? rCfg.maxRushDegradeInfinity()
                    : rCfg.maxRushDegradeNormal()));
                int infCapPct = (int) Math.round(100.0 * rCfg.maxRushDegradeInfinity());
                boolean hasTome = menu.hasAnyMasterTomeInRange();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_title")
                    .withStyle(ChatFormatting.RED));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_rush_risk",
                    String.format(java.util.Locale.ROOT, "%.1f", riskPct),
                    capPct
                ).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_priority")
                    .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_rush_currency",
                    infCapPct
                ).withStyle(ChatFormatting.DARK_GRAY));
                if (!hasTome) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                        .withStyle(ChatFormatting.RED));
                }
        return tip;
    }

    static List<Component> cleanseTabTip(ChiseledEnchantingTableScreenHandler menu) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.title").withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.desc").withStyle(ChatFormatting.GRAY)
                );
        return tip;
    }
}
