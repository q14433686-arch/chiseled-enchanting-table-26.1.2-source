package chiseled_enchanting_table.gui;
import static chiseled_enchanting_table.gui.ResonanceTooltip.formatClock;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import static chiseled_enchanting_table.gui.TableSkin.*;

/** TablePanelLabels owns panel presentation, with no Screen reference or action capability. */
final class TablePanelLabels {
    private TablePanelLabels() {}
    static void extractLabels(TablePanelContext state, GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Purple stitched header cloth spans y = [1.0 .. 15.0] (center = 8.0); place visual ink center at 7.6f!
        RowRenderSupport.renderCenteredYText(context, state.tooltips().font(), state.title(), 18, 7.6f, 0.94f, 190, 0xFFFFF0C8);

        if (state.showShelfBadge()) {
            int broadShelves = state.tooltips().menu().totalLibraryBookshelves();
            int nextTarget = state.nextShelf();
            int gap = Math.max(0, nextTarget - broadShelves);
            String hudText = state.tooltips().menu().crampedLibrary
                ? Component.translatable(
                    "gui.chiseled_enchanting_table.header.shelf_hud_cramped",
                    state.tooltips().menu().regularBookshelves,
                    broadShelves
                ).getString()
                : (gap > 0
                    ? Component.translatable(
                        "gui.chiseled_enchanting_table.header.shelf_hud_gap",
                        state.tooltips().menu().regularBookshelves,
                        broadShelves,
                        gap
                    ).getString()
                    : Component.translatable(
                        "gui.chiseled_enchanting_table.header.shelf_hud_max",
                        state.tooltips().menu().regularBookshelves,
                        broadShelves
                    ).getString());
            int hudTextColor = state.tooltips().menu().crampedLibrary ? 0xFFFF8A96 : (gap == 0 ? 0xFFFFE58F : 0xFFC8F7EA);
            RowRenderSupport.renderCenteredBoxText(
                context,
                state.tooltips().font(),
                hudText,
                SHELF_HUD_X + 3,
                SHELF_HUD_W - 6,
                SHELF_HUD_Y + 4.8f,
                0.58f,
                hudTextColor
            );
        }

        if (!state.showSanctum()) {
            return;
        }

        int lx = PANEL_X + 10;
        int ly = PANEL_Y;

        context.enableScissor(PANEL_X + 9, PANEL_Y + 9, PANEL_X + 110, PANEL_Y + 52);
        try {
            if (state.tooltips().menu().isRitualActive()) {
                int queueCount = Math.max(1, state.tooltips().menu().queuedRitualSteps().size());
                int remSeconds = (int) ((state.tooltips().menu().ritualRemainingTicks() + 19L) / 20L);
                Component header = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.panel_title",
                    queueCount
                );
                drawAutoScaledText(state, context, header, lx, ly + 9, 0.78f, 99, 0xFFFFE082);

                Component timer = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.panel_timer",
                    formatClock(remSeconds)
                );
                drawAutoScaledText(state, context, timer, lx, ly + 19, 0.78f, 99, 0xFF8CFFF0);

                int sacLvls = state.tooltips().menu().currentSacrificeSkipLevels();
                int rushPct = (int) Math.round(state.tooltips().menu().currentRushSkipDegradeChance() * 100.0);
                boolean canSac = state.tooltips().menu().canSacrificeSkipNow();
                boolean canRush = state.tooltips().menu().canRushSkipNow();

                String sacLabel = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.btn_sacrifice",
                    sacLvls
                ).getString();
                String rushLabel = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.btn_rush",
                    rushPct
                ).getString();

                RowRenderSupport.renderCenteredBoxText(
                    context,
                    state.tooltips().font(),
                    sacLabel,
                    SKIP_SACRIFICE_BTN_X + 2,
                    SKIP_BTN_W - 4,
                    SKIP_SACRIFICE_BTN_Y + 7.0f,
                    0.68f,
                    canSac ? 0xFFA8FFD8 : 0xFF7D748D
                );
                RowRenderSupport.renderCenteredBoxText(
                    context,
                    state.tooltips().font(),
                    rushLabel,
                    SKIP_RUSH_BTN_X + 2,
                    SKIP_BTN_W - 4,
                    SKIP_RUSH_BTN_Y + 7.0f,
                    0.68f,
                    canRush ? 0xFFFFB2A6 : 0xFF7D748D
                );
                return;
            }

            if (state.tooltips().isTomeTab()) {
                Component header = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_title");
                drawAutoScaledText(state, context, header, lx, ly + 9, 0.8f, 99, 0xFFB9F6FF);
                Component shelves = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_shelves",
                    state.tooltips().menu().qualifyingEnchantedBookshelves, state.tooltips().menu().liveTotalLibraryBookshelves);
                drawAutoScaledText(state, context, shelves, lx, ly + 16, 0.7f, 99, 0xFFE6F7FF);
                Component tier = state.tooltips().menu().nextRegenTierGap > 0
                    ? Component.translatable("gui.chiseled_enchanting_table.tomes.panel_regen_gap", state.tooltips().menu().liveRegenTier + 1, state.tooltips().menu().nextRegenTierGap)
                    : Component.translatable("gui.chiseled_enchanting_table.tomes.panel_regen_max", state.tooltips().menu().liveRegenTier + 1);
                drawAutoScaledText(state, context, tier, lx, ly + 23, 0.64f, 99, 0xFFA8FFC4);
                String interval = state.tooltips().menu().regenIntervalTicks > 0 ? EnchantementListWidget.formatTomeEta(state.tooltips().menu().regenIntervalTicks) : "--:--";
                Component rate = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_rate", interval);
                drawAutoScaledText(state, context, rate, lx, ly + 30, 0.64f, 99, 0xFFA8FFC4);
                return;
            }

            if (state.tooltips().isCleanseTab()) {
                ItemStack target = state.tooltips().menu().get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                    state.tooltips().menu().libraryResonanceConfig(),
                    target,
                    state.tooltips().menu().disenchantRefundScale(),
                    state.tooltips().menu().disenchantExternalRefundScale()
                );

                Component header = Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_title");
                drawAutoScaledText(state, context, header, lx, ly + 10, 0.85f, 98, 0xFF9EFFDB);

                Component countLine = totalTraits > 0
                    ? Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_traits", totalTraits, nonCurse)
                    : Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_idle");
                drawAutoScaledText(state, context, countLine, lx, ly + 22, 0.82f, 98, totalTraits > 0 ? 0xFFE6FFF6 : 0xFFA2C4BC);

                Component refundLine = Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_refund", refund);
                drawAutoScaledText(state, context, refundLine, lx, ly + 34, 0.80f, 80, refund > 0 ? 0xFF7CFFA6 : 0xFF8FB8AD);
                return;
            }

            int minShelves = Math.max(1, state.tooltips().menu().epiphanyMinBookshelves());
            boolean unlocked = state.tooltips().menu().regularBookshelves >= minShelves;
            boolean frozen = state.tooltips().menu().isEpiphanyFrozen();

            Component resLine = Component.translatable("gui.chiseled_enchanting_table.resonance.badge", state.tooltips().menu().xpDiscountPercent);
            drawAutoScaledText(state, context, resLine, lx, ly + 10, 0.85f, 98, state.tooltips().menu().xpDiscountPercent > 0 ? 0xFF7FFFB2 : 0xFFE2D8F5);

            if (!state.tooltips().menu().isEpiphanyEnabled()) {
                Component shelvesLine = Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.panel_shelves",
                    state.tooltips().menu().chiseledBookshelves,
                    state.tooltips().menu().totalLibraryBookshelves()
                );
                drawAutoScaledText(state, context, shelvesLine, lx, ly + 22, 0.78f, 98, 0xFFD4BEFF);
                return;
            }

            if (unlocked) {
                int pct = (int) Math.round(state.tooltips().menu().epiphanyChance() * 100.0);
                int maxSlots = Math.max(1, state.tooltips().menu().epiphanyMaxSlots());
                Component statLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_stats", pct, maxSlots);
                drawAutoScaledText(state, context, statLine, lx, ly + 22, 0.85f, 98, 0xFFFFDF7E);

                String clock = formatClock(state.tooltips().menu().countdownSeconds());
                Component timerLine = frozen
                    ? Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_frozen", clock)
                    : Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_cycle", clock);
                drawAutoScaledText(state, context, timerLine, lx, ly + 34, 0.82f, 65, frozen ? 0xFF74F3FF : 0xFFFFF2FF);
            } else {
                Component lockedLine = Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.panel_locked",
                    state.tooltips().menu().regularBookshelves,
                    minShelves
                );
                drawAutoScaledText(state, context, lockedLine, lx, ly + 22, 0.85f, 98, 0xFFD4BEFF);

                int wakePct = Math.min(100, Math.round(state.tooltips().menu().regularBookshelves * 100.0f / minShelves));
                Component wakeLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_wake", wakePct);
                drawAutoScaledText(state, context, wakeLine, lx, ly + 34, 0.82f, 65, 0xFFB8A2E0);
            }
        } finally {
            context.disableScissor();
        }
    }

    static void drawAutoScaledText(TablePanelContext state, 
        GuiGraphicsExtractor ctx,
        Component text,
        int x,
        int y,
        float maxScale,
        int maxWidth,
        int argbColor
    ) {
        RowRenderSupport.renderBoundedText(ctx, state.tooltips().font(), text.getString(), x, y, maxScale, maxWidth, argbColor);
    }
}
