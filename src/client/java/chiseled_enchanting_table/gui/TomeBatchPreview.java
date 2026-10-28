package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.catalog.TomeActionQueue;
import chiseled_enchanting_table.mechanics.ritual.RitualActionService;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.menu.MenuTomePageActions;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.List;
import chiseled_enchanting_table.compat.CetFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Dynamic multi-selection eligibility, total material/XP cost, and affordability summary
 * for the Tome Catalogue 4x2 console and right-hand Sanctum panel.
 */
final class TomeBatchPreview {
    private TomeBatchPreview() {}

    static List<TomeBookEntry> effectiveTargets(
        List<TomeBookEntry> explicitSelected,
        @Nullable TomeBookEntry fallbackSingle
    ) {
        if (explicitSelected != null && !explicitSelected.isEmpty()) {
            return explicitSelected;
        }
        return fallbackSingle != null ? List.of(fallbackSingle) : List.of();
    }

    static int countEligible(Action action, List<TomeBookEntry> targets) {
        int n = 0;
        for (TomeBookEntry b : targets) {
            if (TomeActionQueue.isEligibleFor(action, b)) {
                n++;
            }
        }
        return n;
    }

    private static net.minecraft.network.chat.Style affordabilityStyle(int eligible, int affordable) {
        if (eligible <= 0) {
            return CetFormatting.DARK_GRAY;
        }
        if (affordable >= eligible) {
            return CetFormatting.GREEN;
        }
        return affordable > 0 ? CetFormatting.YELLOW : CetFormatting.RED;
    }

    static void appendActionBatchTooltip(
        List<Component> lines,
        ChiseledEnchantingTableScreenHandler menu,
        List<TomeBookEntry> explicitSelected,
        @Nullable TomeBookEntry fallbackSingle,
        int tomeBtn
    ) {
        List<TomeBookEntry> targets = effectiveTargets(explicitSelected, fallbackSingle);
        if (targets.isEmpty() && tomeBtn != 4) {
            lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_select_shortcuts")
                .withStyle(CetFormatting.DARK_GRAY));
            return;
        }
        boolean creative = menu.player != null && menu.player.getAbilities().instabuild;
        var lrCfg = menu.libraryResonanceConfig();

        switch (tomeBtn) {
            case 0, 1, 2 -> {
                Action act = switch (tomeBtn) {
                    case 0 -> Action.LOCK_NEXT;
                    case 1 -> Action.LOCK_FULL;
                    default -> Action.LOCK_PERMANENT;
                };
                int eligible = countEligible(act, targets);
                if (targets.size() >= 2) {
                    lines.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.batch_eligible",
                        eligible,
                        targets.size()
                    ).withStyle(eligible > 0 ? CetFormatting.GREEN : CetFormatting.DARK_GRAY));
                }
            }
            case 3 -> {
                int eligible = 0;
                for (TomeBookEntry b : targets) {
                    if (b.locked() && (creative || b.locked_by_viewer())) {
                        eligible++;
                    }
                }
                int xpPer = Math.max(0, lrCfg.earlyUnlockXpLevels());
                int gritPer = Math.max(0, lrCfg.earlyUnlockGritCount());
                int availXp = menu.player != null ? menu.player.experienceLevel : 0;
                int availGrit = menu.countAvailableInInventory(new ItemStack(BlockRegistry.CLEANSING_GRIT));
                int byXp = xpPer <= 0 ? eligible : (availXp / xpPer);
                int byGrit = gritPer <= 0 ? eligible : (availGrit / gritPer);
                int affordable = creative ? eligible : Math.min(eligible, Math.min(byXp, byGrit));
                if (targets.size() >= 2 || eligible > 0) {
                    lines.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.batch_unlock_total",
                        eligible,
                        targets.size(),
                        eligible * xpPer,
                        eligible * gritPer,
                        affordable,
                        eligible
                    ).withStyle(affordabilityStyle(eligible, affordable)));
                }
            }
            case 5 -> {
                int eligible = countEligible(Action.MEND_ONE, targets);
                int availDep = RitualActionService.countDepletedTomesAvailable(menu, true);
                int availGrit = RitualActionService.countItemInSlotAndBag(menu, BlockRegistry.CLEANSING_GRIT);
                int availXp = menu.player != null ? menu.player.experienceLevel : 0;
                int byDep = availDep / MenuTomePageActions.MEND_DEPLETED_COST;
                int byGrit = availGrit / MenuTomePageActions.MEND_GRIT_COST;
                int byXp = availXp / MenuTomePageActions.MEND_XP_LEVELS;
                int affordable = creative ? eligible : Math.min(eligible, Math.min(byDep, Math.min(byGrit, byXp)));
                if (targets.size() >= 2 || eligible > 0) {
                    lines.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.batch_mend_total",
                        eligible,
                        targets.size(),
                        eligible * MenuTomePageActions.MEND_DEPLETED_COST,
                        eligible * MenuTomePageActions.MEND_GRIT_COST,
                        eligible * MenuTomePageActions.MEND_XP_LEVELS,
                        affordable,
                        eligible
                    ).withStyle(affordabilityStyle(eligible, affordable)));
                }
            }
            case 6 -> {
                int eligible = countEligible(Action.SANCTIFY_CATALYST, targets);
                int availCat = RitualActionService.countItemInSlotAndBag(menu, BlockRegistry.ARCANE_CATALYST);
                int affordable = creative ? eligible : Math.min(eligible, availCat);
                if (targets.size() >= 2 || eligible > 0) {
                    lines.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.batch_sanctify_total",
                        eligible,
                        targets.size(),
                        eligible,
                        affordable,
                        eligible
                    ).withStyle(affordabilityStyle(eligible, affordable)));
                }
            }
            case 7 -> lines.add(Component.translatable(
                "gui.chiseled_enchanting_table.tomes.batch_locate_total",
                targets.size()
            ).withStyle(CetFormatting.AQUA));
            default -> {}
        }
        lines.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_select_shortcuts")
            .withStyle(CetFormatting.DARK_GRAY));
    }

    static void appendPanelSelectionSummary(
        List<Component> tip,
        ChiseledEnchantingTableScreenHandler menu,
        List<TomeBookEntry> explicitSelected,
        @Nullable TomeBookEntry fallbackSingle
    ) {
        if (explicitSelected != null && explicitSelected.size() >= 2) {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.tomes.panel_multi_header",
                explicitSelected.size()
            ).withStyle(CetFormatting.GOLD));
            int limit = Math.min(5, explicitSelected.size());
            for (int i = 0; i < limit; i++) {
                TomeBookEntry b = explicitSelected.get(i);
                boolean inRitual = menu.isTomeActiveInRitual(b.shelf_pos(), b.slot());
                String prefix = inRitual ? " \u2726 " : " \u2022 ";
                tip.add(Component.literal(
                    prefix + TomeDisplayNames.tomeTitle(b, menu.player) + " [#" + (b.slot() + 1) + "]"
                ).withStyle(inRitual ? CetFormatting.LIGHT_PURPLE : CetFormatting.GRAY));
            }
            if (explicitSelected.size() > limit) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.panel_multi_more",
                    explicitSelected.size() - limit
                ).withStyle(CetFormatting.DARK_GRAY));
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_select_shortcuts")
                .withStyle(CetFormatting.DARK_GRAY));
            return;
        }
        if (fallbackSingle != null) {
            tip.add(Component.literal(TomeDisplayNames.tomeTitle(fallbackSingle, menu.player))
                .withStyle(CetFormatting.GOLD));
            if (menu.isTomeActiveInRitual(fallbackSingle.shelf_pos(), fallbackSingle.slot())) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.active_ritual_tip")
                    .withStyle(CetFormatting.LIGHT_PURPLE));
            }
        }
        tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_select_shortcuts")
            .withStyle(CetFormatting.DARK_GRAY));
    }
}
