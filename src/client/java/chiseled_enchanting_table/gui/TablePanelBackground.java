package chiseled_enchanting_table.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Panel/background chrome. Input routing and lifecycle remain in the screen. */
final class TablePanelBackground {
    private TablePanelBackground() {}

    static void render(ChiseledEnchantingTableScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int i = screen.leftPos;
        int j = screen.topPos;

        // Draw inactive tab under main frame, main frame, then active tab flush on top so its open bottom blends seamlessly!
        ctx.blit(TableSkin.BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);
        screen.renderModeTabs(ctx, i, j, mouseX, mouseY);
        if (screen.shouldShowShelfHudBadge()) {
            screen.renderHeaderShelfHudBadge(ctx, i, j, mouseX, mouseY);
        }

        int px = i + TableSkin.PANEL_X;
        int py = j + TableSkin.PANEL_Y;
        if (!screen.shouldShowSanctumPanel()) {
            return;
        }

        if (screen.menu.isRitualActive()) {
            ctx.blit(TableSkin.EPIPHANY_PANEL_FROZEN, px, py, px + TableSkin.PANEL_W, py + TableSkin.PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                int barX = px + 10;
                int barY = py + 29;
                int barW = 99;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE181229);
                long rem = screen.menu.ritualRemainingTicks();
                long tot = Math.max(1L, Math.max(rem, screen.menu.ritualTotalTicks()));
                float doneRatio = Mth.clamp(1.0f - (float) rem / (float) tot, 0.04f, 1.0f);
                int filledW = Math.round(barW * doneRatio);
                ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);

                // Left button: Sacrifice XP Skip
                int sx = i + TableSkin.SKIP_SACRIFICE_BTN_X;
                int sy = j + TableSkin.SKIP_SACRIFICE_BTN_Y;
                boolean canSac = screen.menu.canSacrificeSkipNow();
                ctx.fill(sx, sy, sx + TableSkin.SKIP_BTN_W, sy + TableSkin.SKIP_BTN_H, canSac ? 0xFFE0B85A : 0xFF584E68);
                ctx.fill(
                    sx + 1,
                    sy + 1,
                    sx + TableSkin.SKIP_BTN_W - 1,
                    sy + TableSkin.SKIP_BTN_H - 1,
                    canSac
                        ? (screen.isOverSacrificeSkipButton(mouseX, mouseY) ? 0xFF2E5B48 : 0xFF1E3E33)
                        : 0xFF191522
                );

                // Right button: Non-Sacrifice Force Rush Skip
                int rx = i + TableSkin.SKIP_RUSH_BTN_X;
                int ry = j + TableSkin.SKIP_RUSH_BTN_Y;
                boolean canRush = screen.menu.canRushSkipNow();
                ctx.fill(rx, ry, rx + TableSkin.SKIP_BTN_W, ry + TableSkin.SKIP_BTN_H, canRush ? 0xFFD9725A : 0xFF584E68);
                ctx.fill(
                    rx + 1,
                    ry + 1,
                    rx + TableSkin.SKIP_BTN_W - 1,
                    ry + TableSkin.SKIP_BTN_H - 1,
                    canRush
                        ? (screen.isOverRushSkipButton(mouseX, mouseY) ? 0xFF662932 : 0xFF491D26)
                        : 0xFF191522
                );
            } finally {
                ctx.disableScissor();
            }
            return;
        }

        if (isTomeTab()) {
            ctx.blit(TableSkin.CLEANSE_PANEL, px, py, px + TableSkin.PANEL_W, py + TableSkin.PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            var selected = screen.scrollContainer.selectedTomeEntry();
            String[] labels = {
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_next").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_full").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_permanent").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_unlock").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_lock_all").getString()
            };
            for (int b = 0; b < labels.length; b++) {
                int[] r = tomeButtonRect(b);
                int bx = i + r[0];
                int by = j + r[1];
                boolean enabled = b == 4 || (selected != null && (b == 3 ? selected.locked() : !selected.locked()));
                if (b < 3 && selected != null && selected.wear_used() <= 0 && b != 2) enabled = false;
                if (b != 3 && !screen.menu.isTomeLocksEnabled()) enabled = false;
                boolean hovered = screen.hoveredTomeButton(mouseX, mouseY) == b;
                ctx.blit(TableSkin.TOME_BUTTONS[b], bx, by, bx + r[2], by + r[3], 0.0f, 1.0f, 0.0f, 1.0f);
                if (!enabled) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0xB0191522);
                } else if (hovered) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0x332AD4EE);
                }
                RowRenderSupport.renderCenteredBoxText(ctx, screen.minecraft, labels[b], bx + 1, r[2] - 2, by + r[3] * 0.5f, 0.58f,
                    enabled ? 0xFFDFF7FF : 0xFF7D748D);
            }
            if (screen.scrollContainer.children().isEmpty()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint = Component.translatable(screen.menu.isTomePageUnlocked()
                    ? "gui.chiseled_enchanting_table.tomes.empty"
                    : "gui.chiseled_enchanting_table.tab.locked_tooltip").getString();
                RowRenderSupport.renderBoundedText(ctx, screen.minecraft, hint, lx, ly, 0.68f, 140, 0xFFAEEADF);
            }
            return;
        }

        if (isCleanseTab()) {
            ctx.blit(TableSkin.CLEANSE_PANEL, px, py, px + TableSkin.PANEL_W, py + TableSkin.PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                ItemStack target = screen.menu.get_enchantable_item();
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

                int bx = i + TableSkin.CLEANSE_ALL_BTN_X;
                int by = j + TableSkin.CLEANSE_ALL_BTN_Y;
                boolean canAll = canCleanseAllNow();
                ctx.blit(TableSkin.BTN_CLEANSE_ALL, bx, by, bx + TableSkin.BTN_SIZE, by + TableSkin.BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
                if (!canAll) {
                    ctx.fill(bx, by, bx + TableSkin.BTN_SIZE, by + TableSkin.BTN_SIZE, 0x990A1010);
                } else if (isDisenchantAllConfirmPending()) {
                    ctx.fill(bx - 1, by - 1, bx + TableSkin.BTN_SIZE + 1, by + TableSkin.BTN_SIZE + 1, 0xCCFFAA2A);
                    ctx.fill(bx + 1, by + 1, bx + TableSkin.BTN_SIZE - 1, by + TableSkin.BTN_SIZE - 1, 0x44FFD066);
                } else if (isOverCleanseAllButton(mouseX, mouseY)) {
                    ctx.fill(bx, by, bx + TableSkin.BTN_SIZE, by + TableSkin.BTN_SIZE, 0x2E8CFFD2);
                }
            } finally {
                ctx.disableScissor();
            }

            if (screen.scrollContainer.children().isEmpty()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint1 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line1").getString();
                String hint2 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line2").getString();
                RowRenderSupport.renderBoundedText(ctx, screen.minecraft, hint1, lx, ly, 0.72f, 140, 0xFFAEEADF);
                RowRenderSupport.renderBoundedText(ctx, screen.minecraft, hint2, lx, ly + 12, 0.64f, 140, 0xFF82B8AD);
            }
            return;
        }

        boolean frozen = screen.menu.isEpiphanyFrozen();
        Identifier panelSprite = frozen ? TableSkin.EPIPHANY_PANEL_FROZEN : TableSkin.EPIPHANY_PANEL;
        ctx.blit(panelSprite, px, py, px + TableSkin.PANEL_W, py + TableSkin.PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);

        ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
        try {
            if (!screen.menu.isEpiphanyEnabled()) {
                int barX = px + 10;
                int barY = py + 45;
                int barW = 98;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE1A122B);
                int maxDisc = Math.max(1, screen.menu.libraryResonanceConfig().maxTotalXpDiscountPercent());
                float ratio = Mth.clamp((float) screen.menu.xpDiscountPercent / (float) maxDisc, 0.0f, 1.0f);
                int filledW = Math.round(barW * ratio);
                if (filledW > 0) {
                    ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                    ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);
                }
                return;
            }
            int minShelves = Math.max(1, screen.menu.epiphanyMinBookshelves());
            boolean unlocked = screen.menu.regularBookshelves >= minShelves;
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
                ratio = Mth.clamp((float) screen.menu.regularBookshelves / (float) minShelves, 0.0f, 1.0f);
                fillMain = 0xFF8C52E8;
                fillHighlight = 0xFFD2B0FF;
            } else if (frozen) {
                int maxSec = Math.max(1, screen.menu.epiphanyMaxFreezeTicks() / 20);
                ratio = Mth.clamp((float) screen.menu.countdownSeconds() / (float) maxSec, 0.0f, 1.0f);
                fillMain = 0xFF3FD8FF;
                fillHighlight = 0xFFC2FBFF;
            } else {
                int cycleSec = Math.max(1, screen.menu.epiphanyCycleTicks() / 20);
                ratio = Mth.clamp((float) screen.menu.countdownSeconds() / (float) cycleSec, 0.0f, 1.0f);
                fillMain = 0xFFC86BFF;
                fillHighlight = 0xFFFFDF8E;
            }
            int filledW = Math.round(barW * ratio);
            if (filledW > 0) {
                ctx.fill(barX, barY, barX + filledW, barY + barH, fillMain);
                ctx.fill(barX, barY, barX + filledW, barY + 1, fillHighlight);
            }

            int fx = i + TableSkin.FREEZE_BTN_X;
            int fy = j + TableSkin.FREEZE_BTN_Y;
            boolean canFreeze = screen.menu.canAffordFreezeEpiphany();
            ctx.blit(TableSkin.BTN_FREEZE, fx, fy, fx + TableSkin.BTN_SIZE, fy + TableSkin.BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canFreeze) {
                ctx.fill(fx, fy, fx + TableSkin.BTN_SIZE, fy + TableSkin.BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (isOverFreezeButton(mouseX, mouseY)) {
                ctx.fill(fx, fy, fx + TableSkin.BTN_SIZE, fy + TableSkin.BTN_SIZE, 0x2C90F5FF);
            }

            int rx = i + TableSkin.REROLL_BTN_X;
            int ry = j + TableSkin.REROLL_BTN_Y;
            boolean canReroll = screen.menu.canAffordRerollEpiphany();
            ctx.blit(TableSkin.BTN_REROLL, rx, ry, rx + TableSkin.BTN_SIZE, ry + TableSkin.BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canReroll) {
                ctx.fill(rx, ry, rx + TableSkin.BTN_SIZE, ry + TableSkin.BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (isOverRerollButton(mouseX, mouseY)) {
                ctx.fill(rx, ry, rx + TableSkin.BTN_SIZE, ry + TableSkin.BTN_SIZE, 0x2CFFE082);
            }
        } finally {
            ctx.disableScissor();
        }
    }
}
