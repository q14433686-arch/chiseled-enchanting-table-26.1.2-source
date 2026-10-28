package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.mechanics.ritual.RitualActionService;
import chiseled_enchanting_table.menu.MenuTomePageActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Read-only tooltip family for the Tome 4x2 control grid and the 4-button Inscribe ritual console.
 * Consumes {@link TableTooltipContext} with no Screen, Widget, or networking dependency.
 */
final class TableActionTooltips {
    private TableActionTooltips() {}

    static boolean render(TableTooltipContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int tomeBtn = state.tomeButton();
        if (tomeBtn >= 0) {
            renderTomeButtonTooltip(state, ctx, tomeBtn, mouseX, mouseY);
            return true;
        }
        int ritualBtn = state.ritualButton();
        if (ritualBtn >= 0) {
            renderRitualButtonTooltip(state, ctx, ritualBtn, mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static void renderTomeButtonTooltip(
        TableTooltipContext state,
        GuiGraphicsExtractor ctx,
        int tomeBtn,
        int mouseX,
        int mouseY
    ) {
        var lrCfg = state.menu().libraryResonanceConfig();
        List<Component> lines = new ArrayList<>();
        switch (tomeBtn) {
            case 0 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_next").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_next").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            case 1 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_full").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_full").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            case 2 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_permanent").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_permanent").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            case 3 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_unlock").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.tip_unlock",
                    lrCfg.earlyUnlockXpLevels(),
                    lrCfg.earlyUnlockGritCount()
                ).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            case 4 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_full").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GRAY));
            }
            case 5 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_mend").withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.tip_mend",
                    MenuTomePageActions.MEND_DEPLETED_COST,
                    MenuTomePageActions.MEND_GRIT_COST,
                    MenuTomePageActions.MEND_XP_LEVELS
                ).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            case 6 -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_sanctify").withStyle(ChatFormatting.LIGHT_PURPLE));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_sanctify").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(ChatFormatting.GOLD));
            }
            default -> {
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.btn_locate").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_locate").withStyle(ChatFormatting.GRAY));
            }
        }
        TomeBatchPreview.appendActionBatchTooltip(
            lines, state.menu(), state.list().selectedTomes(), state.list().selectedTomeEntry(), tomeBtn
        );
        TooltipPresentation.show(ctx, state.font(), lines, Optional.empty(), mouseX, mouseY, (Identifier) null);
    }

    private static void renderRitualButtonTooltip(
        TableTooltipContext state,
        GuiGraphicsExtractor ctx,
        int ritualBtn,
        int mouseX,
        int mouseY
    ) {
        List<Component> tip = new ArrayList<>();
        switch (ritualBtn) {
            case 0 -> {
                int lvls = state.menu().currentSacrificeSkipLevels();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_title")
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_sacrifice_cost",
                    lvls,
                    state.menu().player.experienceLevel
                ).withStyle(state.menu().canSacrificeSkipNow() ? ChatFormatting.GREEN : ChatFormatting.RED));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_safe")
                    .withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_currency")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            case 1 -> {
                boolean canFuel = state.menu().canFuelBoostNow();
                boolean canCat = state.menu().canCatalystInstantNow();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_fuel_title")
                    .withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_fuel_desc",
                    RitualActionService.FUEL_BOOST_SECONDS
                ).withStyle(canFuel ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_catalyst_desc")
                    .withStyle(canCat ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_fuel_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            case 2 -> {
                double riskPct = state.menu().currentRushSkipDegradeChance() * 100.0;
                var rCfg = state.menu().ritualConfig();
                int capPct = (int) Math.round(100.0 * (state.menu().ritualUsedInfinityTome()
                    ? rCfg.maxRushDegradeInfinity()
                    : rCfg.maxRushDegradeNormal()));
                int infCapPct = (int) Math.round(100.0 * rCfg.maxRushDegradeInfinity());
                boolean hasTome = state.menu().hasAnyMasterTomeInRange();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_title")
                    .withStyle(ChatFormatting.RED));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_rush_risk",
                    String.format(Locale.ROOT, "%.1f", riskPct),
                    capPct
                ).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_priority")
                    .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_currency", infCapPct)
                    .withStyle(ChatFormatting.DARK_GRAY));
                if (!hasTome) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                        .withStyle(ChatFormatting.RED));
                }
            }
            default -> {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_abort_title")
                    .withStyle(ChatFormatting.YELLOW));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_abort_desc")
                    .withStyle(ChatFormatting.GREEN));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_abort_tome")
                    .withStyle(ChatFormatting.AQUA));
            }
        }
        TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
    }
}
