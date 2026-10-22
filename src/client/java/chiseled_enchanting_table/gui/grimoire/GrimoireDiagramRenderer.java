package chiseled_enchanting_table.gui.grimoire;

import static chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.*;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Programmatic schematic &amp; interactive demonstration renderer for the right-hand page of every
 * chapter in {@link ArcaneGrimoireScreen}.
 *
 * <p>Every diagram here is rendered dynamically in Java from real {@link ItemStack} icons, live
 * mathematical progression curves, structured flow connectors, and hardware-scissored plates —
 * NEVER static AI-generated screenshots — with interactive hover tooltips on every slot!</p>
 */
@Environment(EnvType.CLIENT)
public final class GrimoireDiagramRenderer {

    private static final Identifier XP_ORB_1 = RowRenderSupport.XP_ORB_1;
    private static final Identifier XP_ORB_3 = RowRenderSupport.XP_ORB_3;
    private static final Identifier XP_FLASK = RowRenderSupport.XP_RITUAL_FLASK;
    private static final Identifier XP_REFUND = RowRenderSupport.XP_REFUND_ORB;
    private static final Identifier BTN_FREEZE = ChiseledEnchantingTable.identifier("gui/btn_freeze.png");
    private static final Identifier BTN_REROLL = ChiseledEnchantingTable.identifier("gui/btn_reroll.png");
    private static final Identifier BTN_CLEANSE_ALL = ChiseledEnchantingTable.identifier("gui/btn_cleanse_all.png");

    public record HitRegion(int x, int y, int w, int h, int actionId, String payload) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public static final int ACTION_RECIPE_TAB = 1;
    public static final int ACTION_SHELF_PRESET = 2;
    public static final int ACTION_SHELF_STEP = 3;
    public static final int ACTION_TRANSMUTE_SELECT = 4;

    public record RecipeShowcase(
        String labelKey,
        boolean shapeless,
        ItemStack[] grid9,
        ItemStack result
    ) {}

    private static List<RecipeShowcase> cachedRecipes;

    private GrimoireDiagramRenderer() {}

    public static List<RecipeShowcase> showcases() {
        if (cachedRecipes != null) {
            return cachedRecipes;
        }
        ItemStack air = ItemStack.EMPTY;
        ItemStack grimoire = new ItemStack(BlockRegistry.ARCANE_GRIMOIRE);
        ItemStack table = new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE);
        ItemStack catalyst = new ItemStack(BlockRegistry.ARCANE_CATALYST);
        ItemStack cry8 = new ItemStack(Items.CRYING_OBSIDIAN, 8);

        cachedRecipes = List.of(
            // 0: Manual Recipe A — Book & Quill + Chiseled Bookshelf (Shapeless)
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.manual_shelf",
                true,
                new ItemStack[]{
                    air, air, air,
                    new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.CHISELED_BOOKSHELF), air,
                    air, air, air
                },
                grimoire.copy()
            ),
            // 1: Manual Recipe B — Depleted Tome + Ink Sac (Shapeless)
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.manual_tome",
                true,
                new ItemStack[]{
                    air, air, air,
                    new ItemStack(BlockRegistry.DEPLETED_TOME), new ItemStack(Items.INK_SAC), air,
                    air, air, air
                },
                grimoire.copy()
            ),
            // 2: Chiseled Enchanting Table — Direct Craft
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.table_direct",
                false,
                new ItemStack[]{
                    air, new ItemStack(Items.BOOK), air,
                    new ItemStack(Items.DIAMOND), new ItemStack(Items.CHISELED_BOOKSHELF), new ItemStack(Items.DIAMOND),
                    new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN)
                },
                table.copy()
            ),
            // 3: Vanilla Enchanting Table Upgrade (Variant 1)
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.table_upgrade_a",
                false,
                new ItemStack[]{
                    new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.ENCHANTING_TABLE), new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.CHISELED_BOOKSHELF), new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN)
                },
                table.copy()
            ),
            // 4: Vanilla Enchanting Table Upgrade (Variant 2)
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.table_upgrade_b",
                false,
                new ItemStack[]{
                    air, new ItemStack(Items.AMETHYST_SHARD), air,
                    new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.ENCHANTING_TABLE), new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.CHISELED_BOOKSHELF), new ItemStack(Items.CRYING_OBSIDIAN)
                },
                table.copy()
            ),
            // 5: 8x Crying Obsidian Mass Craft
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.crying_obsidian",
                false,
                new ItemStack[]{
                    new ItemStack(Items.OBSIDIAN), new ItemStack(Items.OBSIDIAN), new ItemStack(Items.OBSIDIAN),
                    new ItemStack(Items.OBSIDIAN), new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.OBSIDIAN),
                    new ItemStack(Items.OBSIDIAN), new ItemStack(Items.OBSIDIAN), new ItemStack(Items.OBSIDIAN)
                },
                cry8
            ),
            // 6: Arcane Crystal (Catalyst)
            new RecipeShowcase(
                "gui.chiseled_enchanting_table.grimoire.rec.arcane_catalyst",
                false,
                new ItemStack[]{
                    new ItemStack(Items.LAPIS_BLOCK), new ItemStack(Items.EMERALD_BLOCK), new ItemStack(Items.LAPIS_BLOCK),
                    new ItemStack(Items.AMETHYST_BLOCK), new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.AMETHYST_BLOCK),
                    new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.CRYING_OBSIDIAN)
                },
                catalyst
            )
        );
        return cachedRecipes;
    }

    public static void drawPanelHeader(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, String titleKey) {
        ctx.fill(x, y, x + w, y + 13, 0xFFEAD9BA);
        ctx.fill(x, y, x + w, y + 1, CARD_BORDER);
        ctx.fill(x, y + 12, x + w, y + 13, CARD_BORDER);
        ctx.fill(x, y, x + 3, y + 13, INK_GOLD_DARK);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(titleKey).getString(),
            x + 6,
            y + 6.5f,
            0.68f,
            w - 10,
            INK_PRIMARY,
            false
        );
    }

    public static void drawSlotItem(
        GuiGraphicsExtractor ctx,
        Font font,
        ItemStack stack,
        int sx,
        int sy,
        int size,
        int mouseX,
        int mouseY,
        List<ItemStack> hoveredOut,
        boolean highlightResult
    ) {
        int border = highlightResult ? INK_GOLD_DARK : 0xFFA98B5F;
        int fill = highlightResult ? 0xFFFBEEC9 : 0xFFE6D5B8;
        ctx.fill(sx, sy, sx + size, sy + size, border);
        ctx.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, fill);
        ctx.fill(sx + 1, sy + 1, sx + size - 1, sy + 2, 0x559B7B4E);

        boolean over = mouseX >= sx && mouseX < sx + size && mouseY >= sy && mouseY < sy + size;
        if (over) {
            ctx.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, 0x44FFE082);
        }

        if (!stack.isEmpty()) {
            float scale = (size - 2) / 16.0f;
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(sx + 1.0f, sy + 1.0f);
            pose.scale(scale, scale);
            ctx.item(stack, 0, 0);
            pose.popMatrix();

            if (stack.getCount() > 1) {
                String c = String.valueOf(stack.getCount());
                drawRightCenteredY(ctx, font, c, sx + 2, sx + size - 1, sy + size - 3.5f, 0.56f, INK_PRIMARY, false);
            }
            if (over && hoveredOut.isEmpty()) {
                hoveredOut.add(stack);
            }
        }
    }

    public static void drawRightArrow(GuiGraphicsExtractor ctx, int x, int midY, int length, int argbColor) {
        int shaftEnd = x + Math.max(4, length - 4);
        ctx.fill(x, midY - 1, shaftEnd, midY + 1, argbColor);
        for (int i = 0; i < 4; i++) {
            int half = 4 - i;
            ctx.fill(shaftEnd + i, midY - half, shaftEnd + i + 1, midY + half, argbColor);
        }
    }

    public static void drawDownArrow(GuiGraphicsExtractor ctx, int midX, int y, int length, int argbColor) {
        int shaftEnd = y + Math.max(3, length - 3);
        ctx.fill(midX - 1, y, midX + 1, shaftEnd, argbColor);
        for (int i = 0; i < 3; i++) {
            int half = 3 - i;
            ctx.fill(midX - half, shaftEnd + i, midX + half, shaftEnd + i + 1, argbColor);
        }
    }

    // =========================================================================
    // CHAPTER 1 — INTERACTIVE 7-RECIPE CRAFTING GALLERY
    // =========================================================================
    public static void renderChapter1Recipes(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int selectedIdx,
        int mouseX,
        int mouseY,
        List<HitRegion> hits,
        List<ItemStack> hoveredItem
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch1_title");

        List<RecipeShowcase> list = showcases();
        int idx = Mth.clamp(selectedIdx, 0, list.size() - 1);
        RecipeShowcase cur = list.get(idx);

        // Selector tabs row (7 compact pills: 1..7)
        int tabY = y + 17;
        int gap = 2;
        int tabW = (w - gap * (list.size() - 1)) / list.size();
        for (int i = 0; i < list.size(); i++) {
            int tx = x + i * (tabW + gap);
            boolean active = i == idx;
            boolean over = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + 13;
            int bg = active ? INK_PURPLE : (over ? CARD_HOVER_BG : CARD_BG);
            int border = active ? INK_GOLD_DARK : CARD_BORDER;
            ctx.fill(tx, tabY, tx + tabW, tabY + 13, border);
            ctx.fill(tx + 1, tabY + 1, tx + tabW - 1, tabY + 12, bg);
            drawCenteredBox(
                ctx,
                font,
                "#" + (i + 1),
                tx + 1,
                tabW - 2,
                tabY + 6.5f,
                0.58f,
                active ? LIGHT_GOLD : INK_PRIMARY,
                false
            );
            hits.add(new HitRegion(tx, tabY, tabW, 13, ACTION_RECIPE_TAB, String.valueOf(i)));
        }

        // Recipe title card + Shapeless/Shaped tag
        int cardY = tabY + 17;
        int cardH = 84;
        ctx.fill(x, cardY, x + w, cardY + cardH, CARD_BORDER);
        ctx.fill(x + 1, cardY + 1, x + w - 1, cardY + cardH - 1, CARD_BG);

        drawCenteredBox(
            ctx,
            font,
            Component.translatable(cur.labelKey()).getString(),
            x + 4,
            w - 8,
            cardY + 7.5f,
            0.64f,
            INK_PURPLE,
            false
        );
        ctx.fill(x + 6, cardY + 13, x + w - 6, cardY + 14, RULE_LINE);

        int slot = 18;
        int gridX = x + 10;
        int gridY = cardY + 18;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                drawSlotItem(
                    ctx,
                    font,
                    cur.grid9()[r * 3 + c],
                    gridX + c * (slot + 1),
                    gridY + r * (slot + 1),
                    slot,
                    mouseX,
                    mouseY,
                    hoveredItem,
                    false
                );
            }
        }

        int arrowX = gridX + 3 * (slot + 1) + 5;
        int midY = gridY + (3 * slot + 2) / 2;
        drawRightArrow(ctx, arrowX, midY, 16, INK_GOLD_DARK);

        String modeTag = Component.translatable(
            cur.shapeless()
                ? "gui.chiseled_enchanting_table.grimoire.rec.tag_shapeless"
                : "gui.chiseled_enchanting_table.grimoire.rec.tag_shaped"
        ).getString();
        drawCenteredBox(ctx, font, modeTag, arrowX - 4, 24, midY - 9.0f, 0.50f, INK_EMERALD, false);

        int outX = arrowX + 20;
        drawSlotItem(ctx, font, cur.result(), outX, midY - 11, 22, mouseX, mouseY, hoveredItem, true);

        // Bottom dual quick-summary strip for the Manual's 2 shapeless recipes
        int sumY = cardY + cardH + 5;
        int sumH = Math.max(24, (y + h) - sumY);
        ctx.fill(x, sumY, x + w, sumY + sumH, CALLOUT_BORDER);
        ctx.fill(x + 1, sumY + 1, x + w - 1, sumY + sumH - 1, CALLOUT_BG);
        ctx.fill(x, sumY, x + 3, sumY + sumH, INK_EMERALD);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch1_note1").getString(),
            x + 6,
            sumY + sumH * 0.30f,
            0.58f,
            w - 10,
            INK_EMERALD,
            false
        );
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch1_note2").getString(),
            x + 6,
            sumY + sumH * 0.70f,
            0.55f,
            w - 10,
            INK_SECONDARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 2 — ANNOTATED 5-ZONE UI SCHEMATIC MAP
    // =========================================================================
    public static void renderChapter2Anatomy(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<ItemStack> hoveredItem
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch2_title");

        int mockY = y + 17;
        int mockH = 82;

        // Top mode tabs above window frame
        ctx.fill(x + 3, mockY, x + 36, mockY + 9, 0xFF4C236E);
        drawCenteredBox(ctx, font, "①", x + 3, 12, mockY + 4.5f, 0.54f, LIGHT_GOLD, false);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.tab.inscribe").getString(),
            x + 14,
            mockY + 4.5f,
            0.48f,
            20,
            LIGHT_CREAM,
            false
        );
        ctx.fill(x + 38, mockY + 1, x + 71, mockY + 9, 0xFF1F4E46);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.tab.cleanse").getString(),
            x + 41,
            mockY + 5.0f,
            0.48f,
            28,
            LIGHT_MINT,
            false
        );

        // Quick-manual button on top-right
        ctx.fill(x + w - 15, mockY, x + w - 4, mockY + 9, INK_GOLD_DARK);
        drawCenteredBox(ctx, font, "?", x + w - 15, 11, mockY + 4.5f, 0.54f, LIGHT_GOLD, false);

        // Main miniature console window
        int winY = mockY + 9;
        int winH = mockH - 9;
        ctx.fill(x, winY, x + w, winY + winH, 0xFF23162E);
        ctx.fill(x + 1, winY + 1, x + w - 1, winY + winH - 1, 0xFF342244);

        // Left scrollable list zone [②]
        int listW = 62;
        ctx.fill(x + 4, winY + 5, x + 4 + listW, winY + winH - 5, 0xFF1B1126);
        String[] sampleRows = {"Sharpness III", "Unbreaking II", "Mending ★"};
        int[] rowCols = {0xFF563A78, 0xFF483166, 0xFF6B4F1D};
        for (int r = 0; r < 3; r++) {
            int ry = winY + 8 + r * 19;
            ctx.fill(x + 6, ry, x + 2 + listW, ry + 16, rowCols[r]);
            drawLeftCenteredY(ctx, font, sampleRows[r], x + 9, ry + 8.0f, 0.48f, 40, LIGHT_CREAM, false);
            ctx.blit(r == 2 ? XP_ORB_1 : XP_FLASK, x + listW - 8, ry + 4, x + listW, ry + 12, 0.0f, 1.0f, 0.0f, 1.0f);
        }
        drawBadgeCircle(ctx, font, x + 6, winY + 5, "2", 0xFF7A4E10);

        // Center slots column [③ Target, ④ Reagent]
        int midX = x + 70;
        drawSlotItem(ctx, font, new ItemStack(Items.DIAMOND_SWORD), midX, winY + 11, 18, mouseX, mouseY, hoveredItem, true);
        drawBadgeCircle(ctx, font, midX - 2, winY + 9, "3", INK_PURPLE);

        drawSlotItem(ctx, font, new ItemStack(Items.LAPIS_LAZULI, 4), midX, winY + 42, 18, mouseX, mouseY, hoveredItem, false);
        drawBadgeCircle(ctx, font, midX - 2, winY + 40, "4", INK_EMERALD);

        // Right Sanctum / Stasis Console [⑤]
        int sanX = x + 92;
        int sanW = w - 96;
        ctx.fill(sanX, winY + 8, sanX + sanW, winY + winH - 8, 0xFF241538);
        ctx.fill(sanX + 1, winY + 9, sanX + sanW - 1, winY + 10, 0xFF8B56BA);
        drawBadgeCircle(ctx, font, sanX + 2, winY + 10, "5", 0xFF285E6B);
        drawLeftCenteredY(ctx, font, "-40% XP", sanX + 4, winY + 25.0f, 0.50f, sanW - 8, LIGHT_MINT, false);
        drawLeftCenteredY(ctx, font, "09:42", sanX + 4, winY + 35.0f, 0.50f, sanW - 8, LIGHT_GOLD, false);
        ctx.blit(BTN_FREEZE, sanX + 4, winY + 44, sanX + 15, winY + 55, 0.0f, 1.0f, 0.0f, 1.0f);
        ctx.blit(BTN_REROLL, sanX + 18, winY + 44, sanX + 29, winY + 55, 0.0f, 1.0f, 0.0f, 0.0f + 1.0f);

        // Numbered legend cards below schematic
        int legY = mockY + mockH + 4;
        int legH = Math.max(24, (y + h) - legY);
        ctx.fill(x, legY, x + w, legY + legH, CALLOUT_BORDER);
        ctx.fill(x + 1, legY + 1, x + w - 1, legY + legH - 1, CARD_BG);

        String[] keys = {
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_k1",
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_k2",
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_k3"
        };
        float step = legH / 3.0f;
        for (int i = 0; i < 3; i++) {
            drawLeftCenteredY(
                ctx,
                font,
                Component.translatable(keys[i]).getString(),
                x + 5,
                legY + step * (i + 0.5f),
                0.55f,
                w - 10,
                INK_PRIMARY,
                false
            );
        }
    }

    private static void drawBadgeCircle(GuiGraphicsExtractor ctx, Font font, int x, int y, String num, int bg) {
        ctx.fill(x, y, x + 8, y + 8, LIGHT_GOLD);
        ctx.fill(x + 1, y + 1, x + 7, y + 7, bg);
        drawCenteredBox(ctx, font, num, x, 8, y + 4.0f, 0.48f, LIGHT_GOLD, false);
    }

    // =========================================================================
    // CHAPTER 3 — LIVE INTERACTIVE 7×5×7 SHELF SIMULATOR + 3-TRACK TABLE
    // =========================================================================
    public static void renderChapter3Shelves(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int demoShelves,
        int mouseX,
        int mouseY,
        List<HitRegion> hits
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch3_title");

        int shelves = Mth.clamp(demoShelves, 0, 38);

        // Top-down 7x7 scan ring slice + interactive shelf stepper
        int topY = y + 16;
        int cell = 7;
        int gridSize = 7 * cell; // 49px
        int gx = x + 3;
        int gy = topY + 2;
        ctx.fill(gx - 1, gy - 1, gx + gridSize + 1, gy + gridSize + 1, CARD_BORDER);
        int ringPlaced = 0;
        for (int rz = 0; rz < 7; rz++) {
            for (int rx = 0; rx < 7; rx++) {
                int cx = gx + rx * cell;
                int cz = gy + rz * cell;
                boolean center = (rx == 3 && rz == 3);
                boolean outerRing = rx == 0 || rx == 6 || rz == 0 || rz == 6 || rx == 1 || rx == 5 || rz == 1 || rz == 5;
                int color = 0xFFF3E8D2;
                if (center) {
                    color = 0xFF7B3FB8;
                } else if (outerRing && ringPlaced < shelves) {
                    color = (ringPlaced % 6 == 0) ? 0xFF287D65 : 0xFF9C6B28;
                    ringPlaced++;
                }
                ctx.fill(cx, cz, cx + cell - 1, cz + cell - 1, color);
            }
        }

        // Right of 7x7 grid: interactive shelf counter & presets [0] [7] [16] [27] [38]
        int ctrlX = gx + gridSize + 6;
        int ctrlW = (x + w) - ctrlX;
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_scan").getString(),
            ctrlX,
            topY + 6.0f,
            0.55f,
            ctrlW,
            INK_PURPLE,
            false
        );

        // [- ]  Shelves: XX / 38  [ +]
        int spinY = topY + 13;
        drawMiniButton(ctx, font, ctrlX, spinY, 13, 12, "-", mouseX, mouseY);
        hits.add(new HitRegion(ctrlX, spinY, 13, 12, ACTION_SHELF_STEP, "-1"));

        ctx.fill(ctrlX + 15, spinY, ctrlX + ctrlW - 15, spinY + 12, CARD_BG);
        drawCenteredBox(
            ctx,
            font,
            shelves + " / 38",
            ctrlX + 15,
            ctrlW - 30,
            spinY + 6.0f,
            0.60f,
            INK_PRIMARY,
            false
        );

        drawMiniButton(ctx, font, ctrlX + ctrlW - 13, spinY, 13, 12, "+", mouseX, mouseY);
        hits.add(new HitRegion(ctrlX + ctrlW - 13, spinY, 13, 12, ACTION_SHELF_STEP, "+1"));

        int[] presets = {0, 7, 16, 27, 38};
        int pw = (ctrlW - 8) / presets.length;
        int py = topY + 29;
        for (int i = 0; i < presets.length; i++) {
            int px = ctrlX + i * (pw + 2);
            boolean active = shelves == presets[i];
            ctx.fill(px, py, px + pw, py + 11, active ? INK_GOLD_DARK : CARD_BORDER);
            ctx.fill(px + 1, py + 1, px + pw - 1, py + 10, active ? INK_PURPLE : CARD_BG);
            drawCenteredBox(
                ctx,
                font,
                String.valueOf(presets[i]),
                px,
                pw,
                py + 5.5f,
                0.52f,
                active ? LIGHT_GOLD : INK_PRIMARY,
                false
            );
            hits.add(new HitRegion(px, py, pw, 11, ACTION_SHELF_PRESET, String.valueOf(presets[i])));
        }

        boolean epiphanyReady = shelves >= 30;
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(
                epiphanyReady
                    ? "gui.chiseled_enchanting_table.grimoire.diag.ch3_epi_on"
                    : "gui.chiseled_enchanting_table.grimoire.diag.ch3_epi_off"
            ).getString(),
            ctrlX,
            topY + 47.0f,
            0.52f,
            ctrlW,
            epiphanyReady ? INK_EMERALD : INK_MUTED,
            false
        );

        // Live Max-Tier Unlock Table for Max II / III / IV / V at `shelves`
        int tblY = topY + 56;
        int tblH = Math.max(40, (y + h) - tblY);
        ctx.fill(x, tblY, x + w, tblY + tblH, CARD_BORDER);
        ctx.fill(x + 1, tblY + 1, x + w - 1, tblY + tblH - 1, CARD_BG);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_curve_hdr").getString(),
            x + 5,
            tblY + 7.0f,
            0.55f,
            w - 10,
            INK_GOLD_DARK,
            false
        );
        ctx.fill(x + 4, tblY + 13, x + w - 4, tblY + 14, RULE_LINE);

        int[] maxClasses = {5, 4, 3, 2};
        String[] romanMax = {"MAX V", "MAX IV", "MAX III", "MAX II"};
        float rowStep = (tblH - 16.0f) / maxClasses.length;
        for (int r = 0; r < maxClasses.length; r++) {
            int maxLvl = maxClasses[r];
            float cy = tblY + 16.0f + rowStep * (r + 0.5f);
            drawLeftCenteredY(ctx, font, romanMax[r], x + 5, cy, 0.54f, 34, INK_PURPLE, false);

            int gx0 = x + 42;
            int cellW = (w - 47) / maxLvl;
            for (int lvl = 1; lvl <= maxLvl; lvl++) {
                int req = BookshelfProgression.requiredBookshelvesForDirectTier(lvl, maxLvl, 38, 1.25);
                boolean unlocked = shelves >= req;
                int bx = gx0 + (lvl - 1) * cellW;
                int bw = cellW - 2;
                ctx.fill(bx, Math.round(cy - 5.5f), bx + bw, Math.round(cy + 5.5f), unlocked ? 0xFF1F6E4A : 0xFFD9CBB2);
                String tag = toRoman(lvl) + "(" + req + ")";
                drawCenteredBox(
                    ctx,
                    font,
                    tag,
                    bx + 1,
                    bw - 2,
                    cy,
                    0.47f,
                    unlocked ? LIGHT_CREAM : INK_SECONDARY,
                    false
                );
            }
        }
    }

    private static void drawMiniButton(
        GuiGraphicsExtractor ctx,
        Font font,
        int x,
        int y,
        int w,
        int h,
        String label,
        int mouseX,
        int mouseY
    ) {
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        ctx.fill(x, y, x + w, y + h, over ? CARD_HOVER_BORDER : CARD_BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, over ? CARD_HOVER_BG : CALLOUT_BG);
        drawCenteredBox(ctx, font, label, x, w, y + h * 0.5f, 0.65f, INK_PURPLE, false);
    }

    // =========================================================================
    // CHAPTER 4 — DUAL XP MODES + LIBRARY RESONANCE -40% CAP BAR
    // =========================================================================
    public static void renderChapter4Economy(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch4_title");

        int c1Y = y + 16;
        drawXpModeCard(
            ctx,
            font,
            x,
            c1Y,
            w,
            34,
            XP_ORB_3,
            "Lv.18 (-3L)",
            0xFF1E6E46,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m1_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m1_b"
        );

        int c2Y = c1Y + 38;
        drawXpModeCard(
            ctx,
            font,
            x,
            c2Y,
            w,
            34,
            XP_FLASK,
            "-12L XP",
            0xFF5A287D,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m2_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m2_b"
        );

        int c3Y = c2Y + 38;
        drawXpModeCard(
            ctx,
            font,
            x,
            c3Y,
            w,
            28,
            XP_REFUND,
            "+XP",
            0xFF1F665E,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m3_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m3_b"
        );

        // Stacked Resonance Discount Bar (-15% + -15% + -10% = -40% Max)
        int barCardY = c3Y + 32;
        int barCardH = Math.max(24, (y + h) - barCardY);
        ctx.fill(x, barCardY, x + w, barCardY + barCardH, CARD_BORDER);
        ctx.fill(x + 1, barCardY + 1, x + w - 1, barCardY + barCardH - 1, CARD_BG);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch4_res").getString(),
            x + 5,
            barCardY + 6.5f,
            0.54f,
            w - 10,
            INK_PRIMARY,
            false
        );

        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        int pWood = Math.max(1, lr.maxWoodenDiscountPercent());
        int pGlobal = Math.max(1, lr.maxGlobalMasteryDiscountPercent());
        int pSpec = Math.max(1, lr.maxSpecificEnchantBonusPercent());
        float sumParts = pWood + pGlobal + pSpec;

        int bx = x + 5;
        int bw = w - 10;
        int by = barCardY + 13;
        int bh = Math.max(8, barCardH - 17);
        int w1 = Math.round(bw * (pWood / sumParts));
        int w2 = Math.round(bw * (pGlobal / sumParts));
        int w3 = bw - w1 - w2;

        ctx.fill(bx, by, bx + w1, by + bh, 0xFF8C5B1F);
        ctx.fill(bx + w1, by, bx + w1 + w2, by + bh, 0xFF5A2D82);
        ctx.fill(bx + w1 + w2, by, bx + bw, by + bh, 0xFF1C7555);
        drawCenteredBox(ctx, font, "-" + pWood + "%", bx, w1, by + bh * 0.5f, 0.48f, LIGHT_CREAM, false);
        drawCenteredBox(ctx, font, "-" + pGlobal + "%", bx + w1, w2, by + bh * 0.5f, 0.48f, LIGHT_GOLD, false);
        drawCenteredBox(ctx, font, "-" + pSpec + "%", bx + w1 + w2, w3, by + bh * 0.5f, 0.48f, LIGHT_MINT, false);
    }

    private static void drawXpModeCard(
        GuiGraphicsExtractor ctx,
        Font font,
        int x,
        int y,
        int w,
        int h,
        Identifier icon,
        String pillText,
        int accent,
        String titleKey,
        String bodyKey
    ) {
        ctx.fill(x, y, x + w, y + h, CARD_BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD_BG);
        ctx.fill(x, y, x + 3, y + h, accent);

        ctx.blit(icon, x + 6, y + 4, x + 15, y + 13, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(titleKey).getString(),
            x + 18,
            y + 8.5f,
            0.58f,
            w - 62,
            accent,
            false
        );

        int pw = 40;
        int px = x + w - pw - 4;
        ctx.fill(px, y + 3, px + pw, y + 13, accent);
        drawCenteredBox(ctx, font, pillText, px + 1, pw - 2, y + 8.0f, 0.50f, LIGHT_CREAM, false);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(bodyKey).getString(),
            x + 6,
            y + h * 0.68f,
            0.54f,
            w - 11,
            INK_SECONDARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 5 — TOME UPGRADE & DELTA CONSERVATION FLOWCHART
    // =========================================================================
    public static void renderChapter5BookLoop(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<ItemStack> hoveredItem
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch5_title");

        // Route A: Twin Combine (0 items, 0.6x XP, +1 Depleted Tome)
        int r1Y = y + 17;
        ctx.fill(x, r1Y, x + w, r1Y + 44, CARD_BORDER);
        ctx.fill(x + 1, r1Y + 1, x + w - 1, r1Y + 43, CARD_BG);
        ctx.fill(x, r1Y, x + 3, r1Y + 44, INK_EMERALD);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r1").getString(),
            x + 6,
            r1Y + 7.5f,
            0.56f,
            w - 10,
            INK_EMERALD,
            false
        );
        int sy1 = r1Y + 15;
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 6, sy1, 18, mouseX, mouseY, hoveredItem, false);
        drawCenteredBox(ctx, font, "+", x + 25, 9, sy1 + 9.0f, 0.65f, INK_PRIMARY, false);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 35, sy1, 18, mouseX, mouseY, hoveredItem, false);
        drawRightArrow(ctx, x + 57, sy1 + 9, 15, INK_EMERALD);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 76, sy1, 18, mouseX, mouseY, hoveredItem, true);
        drawCenteredBox(ctx, font, "+", x + 95, 8, sy1 + 9.0f, 0.60f, INK_GOLD_DARK, false);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME), x + 104, sy1, 18, mouseX, mouseY, hoveredItem, false);

        // Route B: Single-Book Research (1.4x XP + Reagent OR 1x Arcane Catalyst)
        int r2Y = r1Y + 48;
        ctx.fill(x, r2Y, x + w, r2Y + 44, CARD_BORDER);
        ctx.fill(x + 1, r2Y + 1, x + w - 1, r2Y + 43, CARD_BG);
        ctx.fill(x, r2Y, x + 3, r2Y + 44, INK_PURPLE);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r2").getString(),
            x + 6,
            r2Y + 7.5f,
            0.56f,
            w - 10,
            INK_PURPLE,
            false
        );
        int sy2 = r2Y + 15;
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 8, sy2, 18, mouseX, mouseY, hoveredItem, false);
        drawCenteredBox(ctx, font, "+", x + 28, 9, sy2 + 9.0f, 0.65f, INK_PRIMARY, false);
        drawSlotItem(ctx, font, new ItemStack(Items.DIAMOND, 4), x + 39, sy2, 18, mouseX, mouseY, hoveredItem, false);
        drawCenteredBox(ctx, font, "/", x + 58, 7, sy2 + 9.0f, 0.60f, INK_GOLD_DARK, false);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.ARCANE_CATALYST), x + 66, sy2, 18, mouseX, mouseY, hoveredItem, false);
        drawRightArrow(ctx, x + 88, sy2 + 9, 14, INK_PURPLE);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 105, sy2, 18, mouseX, mouseY, hoveredItem, true);

        // Route C: Delta Deduction & Master Tome Mass-Craft Payoff
        int r3Y = r2Y + 48;
        int r3H = Math.max(24, (y + h) - r3Y);
        ctx.fill(x, r3Y, x + w, r3Y + r3H, CALLOUT_BORDER);
        ctx.fill(x + 1, r3Y + 1, x + w - 1, r3Y + r3H - 1, CALLOUT_BG);
        ctx.fill(x, r3Y, x + 3, r3Y + r3H, INK_GOLD_DARK);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r3a").getString(),
            x + 6,
            r3Y + r3H * 0.32f,
            0.55f,
            w - 10,
            INK_GOLD_DARK,
            false
        );
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r3b").getString(),
            x + 6,
            r3Y + r3H * 0.70f,
            0.53f,
            w - 10,
            INK_PRIMARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 6 — TREASURE EPIPHANIES, STASIS FREEZE & MANUAL REROLL
    // =========================================================================
    public static void renderChapter6Epiphany(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<ItemStack> hoveredItem
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch6_title");

        // Top condition banner: 30+ Wooden Shelves -> 10:00 Roll Window
        int bY = y + 17;
        ctx.fill(x, bY, x + w, bY + 34, CARD_BORDER);
        ctx.fill(x + 1, bY + 1, x + w - 1, bY + 33, CARD_BG);
        drawSlotItem(ctx, font, new ItemStack(Items.BOOKSHELF, 30), x + 5, bY + 8, 18, mouseX, mouseY, hoveredItem, false);
        drawRightArrow(ctx, x + 27, bY + 17, 14, INK_GOLD_DARK);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_gate").getString(),
            x + 44,
            bY + 11.0f,
            0.55f,
            w - 48,
            INK_PURPLE,
            false
        );
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_slots").getString(),
            x + 44,
            bY + 23.0f,
            0.53f,
            w - 48,
            INK_EMERALD,
            false
        );

        drawDownArrow(ctx, x + w / 2, bY + 35, 9, INK_GOLD_DARK);

        // Dual Action Controls Card: Freeze vs Reroll
        int midY = bY + 45;
        int halfW = (w - 4) / 2;

        // Left sub-card: Stasis Freeze
        ctx.fill(x, midY, x + halfW, midY + 52, 0xFF3E8FA8);
        ctx.fill(x + 1, midY + 1, x + halfW - 1, midY + 51, 0xFFEBF6F9);
        ctx.blit(BTN_FREEZE, x + 5, midY + 5, x + 17, midY + 17, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_freeze_t").getString(),
            x + 20,
            midY + 11.0f,
            0.55f,
            halfW - 23,
            0xFF15576B,
            false
        );
        drawSlotItem(ctx, font, new ItemStack(Items.BLUE_ICE, 4), x + 6, midY + 24, 18, mouseX, mouseY, hoveredItem, false);
        drawSlotItem(ctx, font, new ItemStack(Items.QUARTZ, 4), x + 26, midY + 24, 18, mouseX, mouseY, hoveredItem, false);
        drawSlotItem(ctx, font, new ItemStack(Items.AMETHYST_SHARD, 4), x + 46, midY + 24, 16, mouseX, mouseY, hoveredItem, false);

        // Right sub-card: Instant Reroll
        int rx = x + halfW + 4;
        ctx.fill(rx, midY, rx + halfW, midY + 52, INK_PURPLE);
        ctx.fill(rx + 1, midY + 1, rx + halfW - 1, midY + 51, CARD_BG);
        ctx.blit(BTN_REROLL, rx + 5, midY + 5, rx + 17, midY + 17, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_reroll_t").getString(),
            rx + 20,
            midY + 11.0f,
            0.55f,
            halfW - 23,
            INK_PURPLE,
            false
        );
        drawSlotItem(ctx, font, new ItemStack(Items.AMETHYST_SHARD, 4), rx + 8, midY + 24, 18, mouseX, mouseY, hoveredItem, false);
        drawSlotItem(ctx, font, new ItemStack(Items.LAPIS_LAZULI, 4), rx + 30, midY + 24, 18, mouseX, mouseY, hoveredItem, false);

        // Bottom payoff: Inscribe onto Book -> Permanent Master Tome!
        int botY = midY + 56;
        int botH = Math.max(24, (y + h) - botY);
        ctx.fill(x, botY, x + w, botY + botH, CALLOUT_BORDER);
        ctx.fill(x + 1, botY + 1, x + w - 1, botY + botH - 1, CALLOUT_BG);
        ctx.fill(x, botY, x + 3, botY + botH, INK_EMERALD);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_payoff").getString(),
            x + 6,
            botY + botH * 0.5f,
            0.55f,
            w - 10,
            INK_PRIMARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 7 — TRANSMUTATION WHEEL + DEPLETED TOME 0→12→24→30 GATE LADDER
    // =========================================================================
    public static void renderChapter7Transmute(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int selectedTransmute,
        int mouseX,
        int mouseY,
        List<HitRegion> hits,
        List<ItemStack> hoveredItem
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch7_title");

        ItemStack[][] pairs = {
            {new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)},
            {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.TRIDENT)},
            {new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.TOTEM_OF_UNDYING, 2)},
            {new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.ECHO_SHARD)},
            {new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.EXPERIENCE_BOTTLE)},
            {new ItemStack(Items.BONE), new ItemStack(Items.SKELETON_SKULL)}
        };
        String[] lvBadges = {"Lv.30 · -3L", "Lv.20 · -3L", "Lv.30 · -3L", "Lv.8 · -1L", "Lv.3 · -1L", "Lv.15 · -2L"};
        int sel = Mth.clamp(selectedTransmute, 0, pairs.length - 1);

        // 6 clickable output icons strip
        int stripY = y + 17;
        int cellW = (w - 10) / pairs.length;
        for (int i = 0; i < pairs.length; i++) {
            int cx = x + i * (cellW + 2);
            boolean active = i == sel;
            ctx.fill(cx, stripY, cx + cellW, stripY + 20, active ? INK_GOLD_DARK : CARD_BORDER);
            ctx.fill(cx + 1, stripY + 1, cx + cellW - 1, stripY + 19, active ? 0xFFFFF4D4 : CARD_BG);
            drawSlotItem(ctx, font, pairs[i][1], cx + (cellW - 18) / 2, stripY + 1, 18, mouseX, mouseY, hoveredItem, active);
            hits.add(new HitRegion(cx, stripY, cellW, 20, ACTION_TRANSMUTE_SELECT, String.valueOf(i)));
        }

        // Selected transformation detail box
        int detY = stripY + 24;
        ctx.fill(x, detY, x + w, detY + 38, CARD_BORDER);
        ctx.fill(x + 1, detY + 1, x + w - 1, detY + 37, CARD_BG);
        drawSlotItem(ctx, font, pairs[sel][0], x + 12, detY + 10, 20, mouseX, mouseY, hoveredItem, false);
        drawRightArrow(ctx, x + 38, detY + 20, 22, INK_GOLD_DARK);
        drawCenteredBox(ctx, font, lvBadges[sel], x + 34, 30, detY + 9.0f, 0.48f, INK_EMERALD, false);
        drawSlotItem(ctx, font, pairs[sel][1], x + 66, detY + 9, 22, mouseX, mouseY, hoveredItem, true);
        drawLeftCenteredY(
            ctx,
            font,
            "1-3L!",
            x + 94,
            detY + 20.0f,
            0.58f,
            w - 98,
            INK_EMERALD,
            false
        );

        // Depleted Tome 4-Gate Restoration Ladder (0 -> 12 -> 24 -> 30+6)
        int ladY = detY + 42;
        int ladH = Math.max(36, (y + h) - ladY);
        ctx.fill(x, ladY, x + w, ladY + ladH, CALLOUT_BORDER);
        ctx.fill(x + 1, ladY + 1, x + w - 1, ladY + ladH - 1, CALLOUT_BG);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch7_tome_hdr").getString(),
            x + 5,
            ladY + 7.0f,
            0.54f,
            w - 10,
            INK_PURPLE,
            false
        );

        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        String[] gates = {
            "0+: 2×",
            sg.tier1Bookshelves() + "+: 0L",
            sg.tier2Bookshelves() + "+: -50%",
            sg.tier2Bookshelves() + "+ & " + sg.tier3QualifyingEnchantments() + "★: FREE!"
        };
        int[] gCols = {0xFF7D634C, 0xFF285E6B, 0xFF4C236E, 0xFF175E3E};
        int gw = (w - 10) / 2;
        int gh = Math.max(11, (ladH - 17) / 2);
        for (int i = 0; i < 4; i++) {
            int gx0 = x + 4 + (i % 2) * (gw + 2);
            int gy0 = ladY + 14 + (i / 2) * (gh + 2);
            ctx.fill(gx0, gy0, gx0 + gw, gy0 + gh, gCols[i]);
            drawCenteredBox(ctx, font, gates[i], gx0 + 2, gw - 4, gy0 + gh * 0.5f, 0.50f, LIGHT_CREAM, false);
        }
    }

    // =========================================================================
    // CHAPTER 8 — CLEANSE & CURSE PURGE 4-WAY ACTION DIAGRAM
    // =========================================================================
    public static void renderChapter8Cleanse(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch8_title");

        String[][] rows = {
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c1_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c1_b", "+XP"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c2_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c2_b", "+XP"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c3_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c3_b", "-1L"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c4_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c4_b", "-5L"}
        };
        int[] accents = {0xFF175E3E, 0xFF285E6B, 0xFF4C236E, INK_CRIMSON};

        int areaY = y + 16;
        int availH = (y + h) - areaY;
        int cardH = (availH - 9) / 4;

        for (int i = 0; i < 4; i++) {
            int cy = areaY + i * (cardH + 3);
            ctx.fill(x, cy, x + w, cy + cardH, CARD_BORDER);
            ctx.fill(x + 1, cy + 1, x + w - 1, cy + cardH - 1, CARD_BG);
            ctx.fill(x, cy, x + 3, cy + cardH, accents[i]);

            if (i == 0) {
                ctx.blit(BTN_CLEANSE_ALL, x + 5, cy + 3, x + 15, cy + 13, 0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                drawBadgeCircle(ctx, font, x + 6, cy + 4, String.valueOf(i + 1), accents[i]);
            }

            drawLeftCenteredY(
                ctx,
                font,
                Component.translatable(rows[i][0]).getString(),
                x + 18,
                cy + cardH * 0.30f,
                0.56f,
                w - 50,
                accents[i],
                false
            );

            int pw = 26;
            ctx.fill(x + w - pw - 4, cy + 3, x + w - 4, cy + 12, accents[i]);
            drawCenteredBox(ctx, font, rows[i][2], x + w - pw - 3, pw - 2, cy + 7.5f, 0.48f, LIGHT_CREAM, false);

            drawLeftCenteredY(
                ctx,
                font,
                Component.translatable(rows[i][1]).getString(),
                x + 6,
                cy + cardH * 0.72f,
                0.52f,
                w - 11,
                INK_SECONDARY,
                false
            );
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
