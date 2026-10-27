package chiseled_enchanting_table.gui;
import static chiseled_enchanting_table.gui.ResonanceTooltip.formatClock;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import chiseled_enchanting_table.gui.TableTooltipContext.Region;

/** Read-only tooltip family; action dispatch remains at the screen boundary. */
final class TablePanelTooltips {
    private TablePanelTooltips() {}
    static boolean render(TableTooltipContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (state.hovered(Region.isOverPurgeCursesButton)) {
            int curses = chiseled_enchanting_table.mechanics.enchanting.CleanseMechanics.countCurses(state.menu().get_enchantable_item());
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_purge_curses_title").withStyle(ChatFormatting.GOLD));
            if (curses <= 0) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_purge_curses_none").withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_purge_curses_count", curses)
                    .withStyle(state.canPurgeCursesNow() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_purge_curses_hint").withStyle(ChatFormatting.DARK_GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverCleanseAllButton)) {
            int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                state.menu().libraryResonanceConfig(),
                state.menu().get_enchantable_item(),
                state.menu().disenchantRefundScale(),
                state.menu().disenchantExternalRefundScale()
            );
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_all_title")
                .withStyle(ChatFormatting.GREEN));
            if (state.canCleanseAllNow()) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.amount", refund)
                    .withStyle(ChatFormatting.AQUA));
                if (state.isDisenchantAllConfirmPending()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.confirm_prompt")
                        .withStyle(ChatFormatting.GOLD));
                } else if (TableCleansePresentation.requiresDisenchantAllConfirm(state.menu().get_enchantable_item())) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.confirm_hint_shift")
                        .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line1")
                    .withStyle(ChatFormatting.RED));
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_all_hint")
                .withStyle(ChatFormatting.GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverFreezeButton)) {
            int minShelves = state.menu().epiphanyMinBookshelves();
            int addMins = Math.max(1, state.menu().epiphanyFreezeDurationTicks() / 1200);
            int maxMins = Math.max(1, state.menu().epiphanyMaxFreezeTicks() / 1200);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.freeze_title", addMins)
                .withStyle(ChatFormatting.AQUA));
            if (state.menu().regularBookshelves < minShelves) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.progress",
                    state.menu().regularBookshelves,
                    minShelves
                ).withStyle(ChatFormatting.RED));
            } else {
                if (state.menu().isEpiphanyFrozen()) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.frozen_remaining",
                        formatClock(state.menu().countdownSeconds()),
                        maxMins
                    ).withStyle(ChatFormatting.AQUA));
                }
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.freeze_cost",
                    state.menu().epiphanyFreezeXpLevels()
                ).withStyle(state.menu().canAffordFreezeEpiphany() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            for (ItemStack req : state.menu().epiphanyFreezeCosts()) {
                tip.add(Component.literal(" \u2022 " + req.getCount() + "\u00d7 ")
                    .append(req.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.freeze_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverRerollButton)) {
            int minShelves = state.menu().epiphanyMinBookshelves();
            int pct = (int) Math.round(state.menu().epiphanyChance() * 100.0);
            int maxSlots = Math.max(1, state.menu().epiphanyMaxSlots());
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.reroll_title", pct)
                .withStyle(ChatFormatting.GOLD));
            if (state.menu().regularBookshelves < minShelves) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.progress",
                    state.menu().regularBookshelves,
                    minShelves
                ).withStyle(ChatFormatting.RED));
            } else {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.active", pct)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.slots_line", maxSlots)
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.reroll_cost",
                    state.menu().epiphanyRerollXpLevels()
                ).withStyle(state.menu().canAffordRerollEpiphany() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            for (ItemStack req : state.menu().epiphanyRerollCosts()) {
                tip.add(Component.literal(" \u2022 " + req.getCount() + "\u00d7 ")
                    .append(req.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.epiphany.auto_hint",
                Math.max(1, Math.round(state.menu().epiphanyCycleTicks() / 1200.0f))
            ).withStyle(ChatFormatting.DARK_GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverSanctumPanel)) {
            if (state.isTomeTab()) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.panel_title").withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.panel_shelves", state.menu().qualifyingEnchantedBookshelves, state.menu().liveTotalLibraryBookshelves).withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.unlock_progress", state.menu().qualifyingEnchantedBookshelves, state.menu().tomeRequiredShelves).withStyle(state.menu().isTomePageUnlocked() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                var sel = state.list().selectedTomeEntry();
                if (sel != null) tip.add(Component.literal(TomeDisplayNames.tomeTitle(sel, state.menu().player)).withStyle(ChatFormatting.GOLD));
                chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
            if (state.isCleanseTab()) {
                ItemStack target = state.menu().get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                    state.menu().libraryResonanceConfig(),
                    target,
                    state.menu().disenchantRefundScale(),
                    state.menu().disenchantExternalRefundScale()
                );
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_title")
                    .withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_free")
                    .withStyle(ChatFormatting.GREEN));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_strip")
                    .withStyle(ChatFormatting.GRAY));
                if (state.menu().disenchantAllowCursePurge()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_curse")
                        .withStyle(ChatFormatting.GOLD));
                }
                if (totalTraits > 0) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_stats", nonCurse, refund)
                        .withStyle(ChatFormatting.AQUA));
                }
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.cleanse.panel_resonance",
                    state.menu().libraryResonanceConfig().maxSpecificEnchantBonusPercent()
                ).withStyle(ChatFormatting.DARK_GRAY));
                chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx,
                state.font(),
                state.resonance().get(),
                Optional.empty(),
                mouseX,
                mouseY,
                (Identifier) null
            );
            return true;
        }
        return false;
    }
}
