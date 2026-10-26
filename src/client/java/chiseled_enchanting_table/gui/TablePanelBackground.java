package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import static chiseled_enchanting_table.gui.TableSkin.*;

/** TablePanelBackground owns panel presentation, with no Screen reference or action capability. */
final class TablePanelBackground {
    private TablePanelBackground() {}
    static void extractBackground(TablePanelContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        int i = state.left();
        int j = state.top();

        // Draw inactive tab under main frame, main frame, then active tab flush on top so its open bottom blends seamlessly!
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);
        renderModeTabs(state, ctx, i, j, mouseX, mouseY);

        int px = i + PANEL_X;
        int py = j + PANEL_Y;
        if (!state.showSanctum()) {
            return;
        }

        if (state.tooltips().menu().isRitualActive()) {
            ctx.blit(EPIPHANY_PANEL_FROZEN, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                int barX = px + 10;
                int barY = py + 29;
                int barW = 99;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE181229);
                long rem = state.tooltips().menu().ritualRemainingTicks();
                long tot = Math.max(1L, Math.max(rem, state.tooltips().menu().ritualTotalTicks()));
                float doneRatio = Mth.clamp(1.0f - (float) rem / (float) tot, 0.04f, 1.0f);
                int filledW = Math.round(barW * doneRatio);
                ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);

                // Left button: Sacrifice XP Skip
                int sx = i + SKIP_SACRIFICE_BTN_X;
                int sy = j + SKIP_SACRIFICE_BTN_Y;
                boolean canSac = state.tooltips().menu().canSacrificeSkipNow();
                ctx.fill(sx, sy, sx + SKIP_BTN_W, sy + SKIP_BTN_H, canSac ? 0xFFE0B85A : 0xFF584E68);
                ctx.fill(
                    sx + 1,
                    sy + 1,
                    sx + SKIP_BTN_W - 1,
                    sy + SKIP_BTN_H - 1,
                    canSac
                        ? (state.tooltips().hovered(TableTooltipContext.Region.isOverSacrificeSkipButton) ? 0xFF2E5B48 : 0xFF1E3E33)
                        : 0xFF191522
                );

                // Right button: Non-Sacrifice Force Rush Skip
                int rx = i + SKIP_RUSH_BTN_X;
                int ry = j + SKIP_RUSH_BTN_Y;
                boolean canRush = state.tooltips().menu().canRushSkipNow();
                ctx.fill(rx, ry, rx + SKIP_BTN_W, ry + SKIP_BTN_H, canRush ? 0xFFD9725A : 0xFF584E68);
                ctx.fill(
                    rx + 1,
                    ry + 1,
                    rx + SKIP_BTN_W - 1,
                    ry + SKIP_BTN_H - 1,
                    canRush
                        ? (state.tooltips().hovered(TableTooltipContext.Region.isOverRushSkipButton) ? 0xFF662932 : 0xFF491D26)
                        : 0xFF191522
                );
            } finally {
                ctx.disableScissor();
            }
            return;
        }

        if (state.tooltips().isTomeTab()) {
            ctx.blit(CLEANSE_PANEL, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            var selected = state.tooltips().list().selectedTomeEntry();
            String[] labels = {
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_next").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_full").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_permanent").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_unlock").getString(),
                Component.translatable("gui.chiseled_enchanting_table.catalog.batch_full").getString()
            };
            for (int b = 0; b < labels.length; b++) {
                int[] r = state.tomeRect().apply(b);
                int bx = i + r[0];
                int by = j + r[1];
                boolean enabled = false;
                for (var book : state.tooltips().list().selectedTomes()) {
                    if (b == 3 ? book.locked() : !book.locked() && (b == 2 || book.wear_used() > 0)) enabled = true;
                }
                if (b != 3 && !state.tooltips().menu().isTomeLocksEnabled()) enabled = false;
                boolean hovered = state.tooltips().tomeButton() == b;
                ctx.blit(TOME_BUTTONS[b], bx, by, bx + r[2], by + r[3], 0.0f, 1.0f, 0.0f, 1.0f);
                if (!enabled) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0xB0191522);
                } else if (hovered) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0x332AD4EE);
                }
                RowRenderSupport.renderCenteredBoxText(ctx, state.tooltips().font(), labels[b], bx + 1, r[2] - 2, by + r[3] * 0.5f, 0.58f,
                    enabled ? 0xFFDFF7FF : 0xFF7D748D);
            }
            if (!state.tooltips().list().hasEntries()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint = Component.translatable(state.tooltips().menu().isTomePageUnlocked()
                    ? (state.tooltips().menu().tomeBooks.isEmpty() ? "gui.chiseled_enchanting_table.tomes.empty" : "gui.chiseled_enchanting_table.catalog.empty")
                    : "gui.chiseled_enchanting_table.tab.locked_tooltip").getString();
                RowRenderSupport.renderBoundedText(ctx, state.tooltips().font(), hint, lx, ly, 0.68f, 140, 0xFFAEEADF);
            }
            return;
        }

        if (state.tooltips().isCleanseTab()) {
            ctx.blit(CLEANSE_PANEL, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                ItemStack target = state.tooltips().menu().get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int barX = px + 10;
                int barY = py + 45;
                int barW = 80;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC071210);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE102420);
                float ratio = totalTraits <= 0 ? 0.0f : Mth.clamp((float) nonCurse / (float) Math.max(1, totalTraits), 0.15f, 1.0f);
                int filledW = Math.round(barW * ratio);
                if (filledW > 0) {
                    ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF39D99A);
                    ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFA8FFE0);
                }

                int bx = i + CLEANSE_ALL_BTN_X;
                int by = j + CLEANSE_ALL_BTN_Y;
                boolean canAll = state.tooltips().canCleanseAllNow();
                ctx.blit(BTN_CLEANSE_ALL, bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
                if (!canAll) {
                    ctx.fill(bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0x990A1010);
                } else if (state.tooltips().isDisenchantAllConfirmPending()) {
                    ctx.fill(bx - 1, by - 1, bx + BTN_SIZE + 1, by + BTN_SIZE + 1, 0xCCFFAA2A);
                    ctx.fill(bx + 1, by + 1, bx + BTN_SIZE - 1, by + BTN_SIZE - 1, 0x44FFD066);
                } else if (state.tooltips().hovered(TableTooltipContext.Region.isOverCleanseAllButton)) {
                    ctx.fill(bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0x2E8CFFD2);
                }
            } finally {
                ctx.disableScissor();
            }

            if (!state.tooltips().list().hasEntries()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint1 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line1").getString();
                String hint2 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line2").getString();
                RowRenderSupport.renderBoundedText(ctx, state.tooltips().font(), hint1, lx, ly, 0.72f, 140, 0xFFAEEADF);
                RowRenderSupport.renderBoundedText(ctx, state.tooltips().font(), hint2, lx, ly + 12, 0.64f, 140, 0xFF82B8AD);
            }
            return;
        }

        boolean frozen = state.tooltips().menu().isEpiphanyFrozen();
        Identifier panelSprite = frozen ? EPIPHANY_PANEL_FROZEN : EPIPHANY_PANEL;
        ctx.blit(panelSprite, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);

        ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
        try {
            if (!state.tooltips().menu().isEpiphanyEnabled()) {
                int barX = px + 10;
                int barY = py + 45;
                int barW = 98;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE1A122B);
                int maxDisc = Math.max(1, state.tooltips().menu().libraryResonanceConfig().maxTotalXpDiscountPercent());
                float ratio = Mth.clamp((float) state.tooltips().menu().xpDiscountPercent / (float) maxDisc, 0.0f, 1.0f);
                int filledW = Math.round(barW * ratio);
                if (filledW > 0) {
                    ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                    ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);
                }
                return;
            }
            int minShelves = Math.max(1, state.tooltips().menu().epiphanyMinBookshelves());
            boolean unlocked = state.tooltips().menu().regularBookshelves >= minShelves;
            int barX = px + 10;
            int barY = py + 45;
            int barW = 65;
            int barH = 4;
            ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE1A122B);

            float ratio;
            int fillMain;
            int fillHighlight;
            if (!unlocked) {
                ratio = Mth.clamp((float) state.tooltips().menu().regularBookshelves / (float) minShelves, 0.0f, 1.0f);
                fillMain = 0xFF8C52E8;
                fillHighlight = 0xFFD2B0FF;
            } else if (frozen) {
                int maxSec = Math.max(1, state.tooltips().menu().epiphanyMaxFreezeTicks() / 20);
                ratio = Mth.clamp((float) state.tooltips().menu().countdownSeconds() / (float) maxSec, 0.0f, 1.0f);
                fillMain = 0xFF3FD8FF;
                fillHighlight = 0xFFC2FBFF;
            } else {
                int cycleSec = Math.max(1, state.tooltips().menu().epiphanyCycleTicks() / 20);
                ratio = Mth.clamp((float) state.tooltips().menu().countdownSeconds() / (float) cycleSec, 0.0f, 1.0f);
                fillMain = 0xFFC86BFF;
                fillHighlight = 0xFFFFDF8E;
            }
            int filledW = Math.round(barW * ratio);
            if (filledW > 0) {
                ctx.fill(barX, barY, barX + filledW, barY + barH, fillMain);
                ctx.fill(barX, barY, barX + filledW, barY + 1, fillHighlight);
            }

            int fx = i + FREEZE_BTN_X;
            int fy = j + FREEZE_BTN_Y;
            boolean canFreeze = state.tooltips().menu().canAffordFreezeEpiphany();
            ctx.blit(BTN_FREEZE, fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canFreeze) {
                ctx.fill(fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (state.tooltips().hovered(TableTooltipContext.Region.isOverFreezeButton)) {
                ctx.fill(fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0x2C90F5FF);
            }

            int rx = i + REROLL_BTN_X;
            int ry = j + REROLL_BTN_Y;
            boolean canReroll = state.tooltips().menu().canAffordRerollEpiphany();
            ctx.blit(BTN_REROLL, rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canReroll) {
                ctx.fill(rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (state.tooltips().hovered(TableTooltipContext.Region.isOverRerollButton)) {
                ctx.fill(rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0x2CFFE082);
            }
        } finally {
            ctx.disableScissor();
        }
    }

    static void renderModeTabs(TablePanelContext state, GuiGraphicsExtractor ctx, int i, int j, int mouseX, int mouseY) {
        if (!state.tooltips().menu().hasSecondaryTabs()) {
            int cx = i + CONFIG_BTN_X;
            int cy = j + CONFIG_BTN_Y_OFFSET;
            ctx.blit(BTN_CONFIG, cx, cy, cx + CONFIG_BTN_SIZE, cy + CONFIG_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (state.tooltips().hovered(TableTooltipContext.Region.isOverConfigButton)) {
                ctx.fill(cx + 1, cy + 1, cx + CONFIG_BTN_SIZE - 1, cy + CONFIG_BTN_SIZE - 1, 0x2EFFDF82);
            }
            int gx = i + GRIMOIRE_BTN_X;
            int gy = j + GRIMOIRE_BTN_Y_OFFSET;
            ctx.blit(BTN_GRIMOIRE, gx, gy, gx + GRIMOIRE_BTN_SIZE, gy + GRIMOIRE_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (state.tooltips().hovered(TableTooltipContext.Region.isOverGrimoireButton)) {
                ctx.fill(gx + 1, gy + 1, gx + GRIMOIRE_BTN_SIZE - 1, gy + GRIMOIRE_BTN_SIZE - 1, 0x2EFFDF82);
            }
            return;
        }
        boolean cleanse = state.tooltips().isCleanseTab();
        boolean tome = state.tooltips().isTomeTab();
        boolean inscribe = state.inscribeTab();
        int tabY = j + TAB_Y_OFFSET;

        int inscribeX = i + TAB_INSCRIBE_X;
        Identifier inscribeSprite = inscribe ? TAB_INSCRIBE_ACTIVE : TAB_INSCRIBE_INACTIVE;
        ctx.blit(inscribeSprite, inscribeX, tabY, inscribeX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!inscribe && state.tooltips().hovered(TableTooltipContext.Region.isOverInscribeTab)) {
            ctx.fill(inscribeX + 2, tabY + 2, inscribeX + TAB_W - 2, tabY + TAB_H - 3, 0x24FFE082);
        }
        float tabVisualMidY = tabY + 8.0f;
        String tab1Text = Component.translatable("gui.chiseled_enchanting_table.tab.inscribe").getString();
        RowRenderSupport.renderCenteredBoxText(
            ctx,
            state.tooltips().font(),
            tab1Text,
            inscribeX + 10,
            TAB_W - 20,
            tabVisualMidY,
            0.76f,
            inscribe ? 0xFFFFF0C8 : 0xFFC2B2DC
        );

        if (state.tooltips().menu().disenchantEnabled()) {
            int cleanseX = i + TAB_CLEANSE_X;
            Identifier cleanseSprite = cleanse ? TAB_CLEANSE_ACTIVE : TAB_CLEANSE_INACTIVE;
            ctx.blit(cleanseSprite, cleanseX, tabY, cleanseX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!state.tooltips().menu().isCleansePageUnlocked()) {
                ctx.fill(cleanseX + 2, tabY + 2, cleanseX + TAB_W - 2, tabY + TAB_H - 3, 0xA0181024);
            } else if (!cleanse && state.tooltips().hovered(TableTooltipContext.Region.isOverCleanseTab)) {
                ctx.fill(cleanseX + 2, tabY + 2, cleanseX + TAB_W - 2, tabY + TAB_H - 3, 0x247CFFA6);
            }
            String baseLabel = Component.translatable("gui.chiseled_enchanting_table.tab.cleanse").getString();
            String tab2Text = baseLabel;
            RowRenderSupport.renderCenteredBoxText(
                ctx,
                state.tooltips().font(),
                tab2Text,
                cleanseX + 10,
                TAB_W - 20,
                tabVisualMidY,
                0.76f,
                !state.tooltips().menu().isCleansePageUnlocked() ? 0xFF8E829E : (cleanse ? 0xFF9EFFDB : 0xFFA2C4BC)
            );
        }

        if (state.tooltips().menu().isTomePageEnabled()) {
            int tomeX = i + TAB_TOMES_X;
            Identifier tomeSprite = tome ? TAB_TOME_ACTIVE : TAB_TOME_INACTIVE;
            ctx.blit(tomeSprite, tomeX, tabY, tomeX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!state.tooltips().menu().isTomePageUnlocked()) {
                ctx.fill(tomeX + 2, tabY + 2, tomeX + TAB_W - 2, tabY + TAB_H - 3, 0xA0181024);
            } else if (!tome && state.tooltips().hovered(TableTooltipContext.Region.isOverTomeTab)) {
                ctx.fill(tomeX + 2, tabY + 2, tomeX + TAB_W - 2, tabY + TAB_H - 3, 0x24B9F6FF);
            }
            String tomeText = Component.translatable("gui.chiseled_enchanting_table.tab.tomes").getString();
            RowRenderSupport.renderCenteredBoxText(
                ctx,
                state.tooltips().font(),
                tomeText,
                tomeX + 10,
                TAB_W - 20,
                tabVisualMidY,
                0.76f,
                !state.tooltips().menu().isTomePageUnlocked() ? 0xFF8E829E : (tome ? 0xFFB9F6FF : 0xFFA8CBD8)
            );
        }

        if (state.tooltips().menu().disenchantEnabled() && !state.tooltips().menu().isCleansePageUnlocked()) {
            int sx = i + SACRIFICE_BTN_X;
            ctx.fill(sx, tabY + 1, sx + SACRIFICE_BTN_W, tabY + TAB_H - 2, state.tooltips().hovered(TableTooltipContext.Region.isOverSacrificeButton) ? 0xFF6B4E2E : 0xFF3A2A20);
            ctx.fill(sx + 1, tabY + 2, sx + SACRIFICE_BTN_W - 1, tabY + TAB_H - 3, 0xFF241722);
            String sacText = Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_short", state.tooltips().menu().sacrificeProgress, state.tooltips().menu().sacrificeRequired).getString();
            RowRenderSupport.renderCenteredBoxText(ctx, state.tooltips().font(), sacText, sx + 2, SACRIFICE_BTN_W - 4, tabVisualMidY, 0.62f, 0xFFFFC46B);
        }

        int cx = i + CONFIG_BTN_X;
        int cy = j + CONFIG_BTN_Y_OFFSET;
        ctx.blit(BTN_CONFIG, cx, cy, cx + CONFIG_BTN_SIZE, cy + CONFIG_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
        if (state.tooltips().hovered(TableTooltipContext.Region.isOverConfigButton)) {
            ctx.fill(cx + 1, cy + 1, cx + CONFIG_BTN_SIZE - 1, cy + CONFIG_BTN_SIZE - 1, 0x2EFFDF82);
        }

        int gx = i + GRIMOIRE_BTN_X;
        int gy = j + GRIMOIRE_BTN_Y_OFFSET;
        ctx.blit(BTN_GRIMOIRE, gx, gy, gx + GRIMOIRE_BTN_SIZE, gy + GRIMOIRE_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
        if (state.tooltips().hovered(TableTooltipContext.Region.isOverGrimoireButton)) {
            ctx.fill(gx + 1, gy + 1, gx + GRIMOIRE_BTN_SIZE - 1, gy + GRIMOIRE_BTN_SIZE - 1, 0x2EFFDF82);
        }
    }

}
