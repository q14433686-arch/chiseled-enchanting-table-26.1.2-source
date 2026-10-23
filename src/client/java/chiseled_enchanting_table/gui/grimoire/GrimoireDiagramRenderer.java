package chiseled_enchanting_table.gui.grimoire;

import static chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.*;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge.HoveredSlot;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.registry.BlockRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Live data-driven schematic &amp; interactive demonstration renderer for the right-hand page of
 * every chapter in {@link ArcaneGrimoireScreen}.
 *
 * <p>Zero-duplication guarantees:</p>
 * <ul>
 *   <li>Chapter 1 reads all 7 bundled {@code data/chiseled_enchanting_table/recipe/*.json} files
 *       directly and clears its cache whenever {@link SyncedEnchantmentCosts} updates.</li>
 *   <li>Chapters 3, 4, 6, and 7 read live synced values directly from {@link SyncedEnchantmentCosts}
 *       and {@link TransformationQueryIndex#build()}.</li>
 *   <li>Multi-phrase notes use {@link GrimoireTextLayout#drawWrappedBox} so text wraps cleanly at
 *       readable scales instead of shrinking into illegible sub-pixel lines.</li>
 * </ul>
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
        String recipeId,
        String labelKey,
        boolean shapeless,
        ItemStack[] grid9,
        ItemStack result
    ) {}

    private static volatile List<RecipeShowcase> cachedRecipes;

    static {
        SyncedEnchantmentCosts.addListener(GrimoireDiagramRenderer::invalidateCache);
    }

    private GrimoireDiagramRenderer() {}

    public static void invalidateCache() {
        cachedRecipes = null;
    }

    private static ItemStack parseStackId(String rawId, int count) {
        if (rawId == null || rawId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = rawId.indexOf(':') >= 0
            ? Identifier.parse(rawId)
            : Identifier.withDefaultNamespace(rawId);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, count));
    }

    private static RecipeShowcase loadBundledRecipe(String recipeName, String labelKey) {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            grid[i] = ItemStack.EMPTY;
        }
        String path = "/data/chiseled_enchanting_table/recipe/" + recipeName + ".json";
        try (var in = GrimoireDiagramRenderer.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
                String type = root.has("type") ? root.get("type").getAsString() : "";
                boolean shapeless = type.endsWith("crafting_shapeless");
                JsonObject resObj = root.getAsJsonObject("result");
                String resId = resObj.get("id").getAsString();
                int resCount = resObj.has("count") ? resObj.get("count").getAsInt() : 1;
                ItemStack result = parseStackId(resId, resCount);

                if (shapeless) {
                    JsonArray ings = root.getAsJsonArray("ingredients");
                    int[] slots = ings.size() <= 2 ? new int[]{3, 4} : new int[]{0, 1, 3, 4, 2, 5, 6, 7, 8};
                    for (int i = 0; i < Math.min(ings.size(), slots.length); i++) {
                        JsonElement el = ings.get(i);
                        String itemId = el.isJsonPrimitive()
                            ? el.getAsString()
                            : el.getAsJsonObject().get("item").getAsString();
                        grid[slots[i]] = parseStackId(itemId, 1);
                    }
                } else {
                    JsonArray pattern = root.getAsJsonArray("pattern");
                    JsonObject keys = root.getAsJsonObject("key");
                    for (int r = 0; r < Math.min(3, pattern.size()); r++) {
                        String row = pattern.get(r).getAsString();
                        for (int c = 0; c < Math.min(3, row.length()); c++) {
                            String ch = String.valueOf(row.charAt(c));
                            if (!" ".equals(ch) && keys.has(ch)) {
                                JsonElement kEl = keys.get(ch);
                                String itemId = kEl.isJsonPrimitive()
                                    ? kEl.getAsString()
                                    : kEl.getAsJsonObject().get("item").getAsString();
                                grid[r * 3 + c] = parseStackId(itemId, 1);
                            }
                        }
                    }
                }
                return new RecipeShowcase(recipeName, labelKey, shapeless, grid, result);
            }
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to parse bundled recipe {}", path, e);
        }
        return new RecipeShowcase(recipeName, labelKey, true, grid, new ItemStack(BlockRegistry.ARCANE_GRIMOIRE));
    }

    public static List<RecipeShowcase> showcases() {
        List<RecipeShowcase> local = cachedRecipes;
        if (local != null) {
            return local;
        }
        List<RecipeShowcase> built = List.of(
            loadBundledRecipe("arcane_grimoire_from_shelf", "gui.chiseled_enchanting_table.grimoire.rec.manual_shelf"),
            loadBundledRecipe("arcane_grimoire_from_tome", "gui.chiseled_enchanting_table.grimoire.rec.manual_tome"),
            loadBundledRecipe("chiseled_enchanting_table", "gui.chiseled_enchanting_table.grimoire.rec.table_direct"),
            loadBundledRecipe("chiseled_enchanting_table_upgrade", "gui.chiseled_enchanting_table.grimoire.rec.table_upgrade"),
            loadBundledRecipe("crying_obsidian", "gui.chiseled_enchanting_table.grimoire.rec.crying_obsidian"),
            loadBundledRecipe("cleansing_grit", "gui.chiseled_enchanting_table.grimoire.rec.cleansing_grit"),
            loadBundledRecipe("arcane_catalyst", "gui.chiseled_enchanting_table.grimoire.rec.arcane_catalyst")
        );
        cachedRecipes = built;
        return built;
    }

    public static void drawPanelHeader(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, String titleKey) {
        ctx.fill(x, y, x + w, y + 14, 0xFFEAD9BA);
        ctx.fill(x, y, x + w, y + 1, CARD_BORDER);
        ctx.fill(x, y + 13, x + w, y + 14, CARD_BORDER);
        ctx.fill(x, y, x + 3, y + 14, INK_GOLD_DARK);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(titleKey).getString(),
            x + 6,
            y + 7.0f,
            0.74f,
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
        List<HoveredSlot> slotsOut,
        boolean highlightResult,
        boolean leftClickOpensRecipes
    ) {
        int border = highlightResult ? INK_GOLD_DARK : 0xFFA98B5F;
        int fill = highlightResult ? 0xFFFBEEC9 : 0xFFE6D5B8;
        ctx.fill(sx, sy, sx + size, sy + size, border);
        ctx.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, fill);
        ctx.fill(sx + 1, sy + 1, sx + size - 1, sy + 2, 0x559B7B4E);

        boolean over = mouseX >= sx && mouseX < sx + size && mouseY >= sy && mouseY < sy + size;
        if (over && !stack.isEmpty()) {
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
                ctx.fill(sx + size - 9, sy + size - 7, sx + size - 1, sy + size - 1, 0xB8F7EEDB);
                drawRightCenteredY(ctx, font, c, sx + 2, sx + size - 1, sy + size - 4.0f, 0.60f, INK_PRIMARY, false);
            }
            slotsOut.add(new HoveredSlot(stack, new Rect2i(sx, sy, size, size), leftClickOpensRecipes));
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

    private static void drawBadgeCircle(GuiGraphicsExtractor ctx, Font font, int x, int y, String num, int bg) {
        ctx.fill(x, y, x + 9, y + 9, LIGHT_GOLD);
        ctx.fill(x + 1, y + 1, x + 8, y + 8, bg);
        drawCenteredBox(ctx, font, num, x, 9, y + 4.5f, 0.56f, LIGHT_GOLD, false);
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
        drawCenteredBox(ctx, font, label, x, w, y + h * 0.5f, 0.68f, INK_PURPLE, false);
    }

    // =========================================================================
    // CHAPTER 1 — LIVE 7-RECIPE CRAFTING WORKBENCH (PARSED DIRECTLY FROM JSON)
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
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch1_title");

        List<RecipeShowcase> list = showcases();
        int idx = Mth.clamp(selectedIdx, 0, list.size() - 1);
        RecipeShowcase cur = list.get(idx);

        int tabY = y + 18;
        int gap = 2;
        int tabW = (w - gap * (list.size() - 1)) / list.size();
        for (int i = 0; i < list.size(); i++) {
            int tx = x + i * (tabW + gap);
            boolean active = i == idx;
            boolean over = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + 14;
            int bg = active ? INK_PURPLE : (over ? CARD_HOVER_BG : CARD_BG);
            int border = active ? INK_GOLD_DARK : CARD_BORDER;
            ctx.fill(tx, tabY, tx + tabW, tabY + 14, border);
            ctx.fill(tx + 1, tabY + 1, tx + tabW - 1, tabY + 13, bg);
            drawCenteredBox(
                ctx,
                font,
                "#" + (i + 1),
                tx + 1,
                tabW - 2,
                tabY + 7.0f,
                0.66f,
                active ? LIGHT_GOLD : INK_PRIMARY,
                false
            );
            hits.add(new HitRegion(tx, tabY, tabW, 14, ACTION_RECIPE_TAB, String.valueOf(i)));
        }

        int cardY = tabY + 18;
        int cardH = 92;
        ctx.fill(x, cardY, x + w, cardY + cardH, CARD_BORDER);
        ctx.fill(x + 1, cardY + 1, x + w - 1, cardY + cardH - 1, CARD_BG);

        drawCenteredBox(
            ctx,
            font,
            Component.translatable(cur.labelKey()).getString(),
            x + 5,
            w - 10,
            cardY + 8.0f,
            0.74f,
            INK_PURPLE,
            false
        );
        ctx.fill(x + 6, cardY + 15, x + w - 6, cardY + 16, RULE_LINE);

        int slot = 20;
        int totalBenchW = 3 * (slot + 1) + 34 + 26;
        int gridX = x + Math.max(8, (w - totalBenchW) / 2);
        int gridY = cardY + 21;
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
                    slotsOut,
                    false,
                    true
                );
            }
        }

        int arrowX = gridX + 3 * (slot + 1) + 7;
        int midY = gridY + (3 * slot + 2) / 2;
        drawRightArrow(ctx, arrowX, midY, 20, INK_GOLD_DARK);

        String modeTag = Component.translatable(
            cur.shapeless()
                ? "gui.chiseled_enchanting_table.grimoire.rec.tag_shapeless"
                : "gui.chiseled_enchanting_table.grimoire.rec.tag_shaped"
        ).getString();
        drawCenteredBox(ctx, font, modeTag, arrowX - 5, 30, midY - 10.0f, 0.62f, INK_EMERALD, false);

        int outX = arrowX + 26;
        drawSlotItem(ctx, font, cur.result(), outX, midY - 13, 26, mouseX, mouseY, slotsOut, true, true);

        int sumY = cardY + cardH + 5;
        int sumH = Math.max(28, (y + h) - sumY);
        ctx.fill(x, sumY, x + w, sumY + sumH, CALLOUT_BORDER);
        ctx.fill(x + 1, sumY + 1, x + w - 1, sumY + sumH - 1, CALLOUT_BG);
        ctx.fill(x, sumY, x + 3, sumY + sumH, INK_EMERALD);

        drawWrappedBox(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch1_note1"),
            x + 6,
            sumY + 2,
            w - 10,
            sumH - 4,
            0.70f,
            0.64f,
            INK_PRIMARY,
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
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch2_title");

        int mockY = y + 18;
        int mockH = 86;

        ctx.fill(x + 3, mockY, x + 52, mockY + 11, 0xFF4C236E);
        drawBadgeCircle(ctx, font, x + 4, mockY + 1, "1", 0xFF6B3A9E);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.tab.inscribe").getString(),
            x + 15,
            mockY + 5.5f,
            0.60f,
            34,
            LIGHT_CREAM,
            false
        );
        ctx.fill(x + 54, mockY + 1, x + 98, mockY + 11, 0xFF1F4E46);
        drawCenteredBox(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.tab.cleanse").getString(),
            x + 56,
            40,
            mockY + 6.0f,
            0.60f,
            LIGHT_MINT,
            false
        );

        ctx.fill(x + w - 16, mockY, x + w - 4, mockY + 10, INK_GOLD_DARK);
        drawCenteredBox(ctx, font, "?", x + w - 16, 12, mockY + 5.0f, 0.62f, LIGHT_GOLD, false);

        int winY = mockY + 11;
        int winH = mockH - 11;
        ctx.fill(x, winY, x + w, winY + winH, 0xFF23162E);
        ctx.fill(x + 1, winY + 1, x + w - 1, winY + winH - 1, 0xFF342244);

        int listW = Math.max(68, w - 92);
        ctx.fill(x + 4, winY + 5, x + 4 + listW, winY + winH - 5, 0xFF1B1126);
        String[] rowKeys = {
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_r1",
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_r2",
            "gui.chiseled_enchanting_table.grimoire.diag.ch2_r3"
        };
        int[] rowCols = {0xFF563A78, 0xFF483166, 0xFF6B4F1D};
        for (int r = 0; r < 3; r++) {
            int ry = winY + 8 + r * 20;
            ctx.fill(x + 6, ry, x + 2 + listW, ry + 17, rowCols[r]);
            drawLeftCenteredY(
                ctx,
                font,
                Component.translatable(rowKeys[r]).getString(),
                x + 9,
                ry + 8.5f,
                0.60f,
                listW - 18,
                LIGHT_CREAM,
                false
            );
            ctx.blit(r == 2 ? XP_ORB_3 : XP_FLASK, x + listW - 8, ry + 4, x + listW, ry + 12, 0.0f, 1.0f, 0.0f, 1.0f);
        }
        drawBadgeCircle(ctx, font, x + 5, winY + 5, "2", 0xFF7A4E10);

        int midX = x + listW + 10;
        drawSlotItem(ctx, font, new ItemStack(Items.DIAMOND_SWORD), midX, winY + 10, 20, mouseX, mouseY, slotsOut, true, true);
        drawBadgeCircle(ctx, font, midX - 3, winY + 8, "3", INK_PURPLE);

        drawSlotItem(ctx, font, new ItemStack(Items.LAPIS_LAZULI, 8), midX, winY + 42, 20, mouseX, mouseY, slotsOut, false, true);
        drawBadgeCircle(ctx, font, midX - 3, winY + 40, "4", INK_EMERALD);

        int sanX = midX + 25;
        int sanW = Math.max(34, (x + w - 4) - sanX);
        ctx.fill(sanX, winY + 8, sanX + sanW, winY + winH - 8, 0xFF241538);
        ctx.fill(sanX + 1, winY + 9, sanX + sanW - 1, winY + 10, 0xFF8B56BA);
        drawBadgeCircle(ctx, font, sanX + 2, winY + 11, "5", 0xFF285E6B);
        drawLeftCenteredY(ctx, font, "-40%", sanX + 4, winY + 27.0f, 0.62f, sanW - 8, LIGHT_MINT, false);
        drawLeftCenteredY(ctx, font, "09:42", sanX + 4, winY + 38.0f, 0.58f, sanW - 8, LIGHT_GOLD, false);
        ctx.blit(BTN_FREEZE, sanX + 4, winY + 46, sanX + 15, winY + 57, 0.0f, 1.0f, 0.0f, 1.0f);
        ctx.blit(BTN_REROLL, sanX + 18, winY + 46, sanX + 29, winY + 57, 0.0f, 1.0f, 0.0f, 1.0f);

        int legY = mockY + mockH + 4;
        int legH = Math.max(30, (y + h) - legY);
        ctx.fill(x, legY, x + w, legY + legH, CALLOUT_BORDER);
        ctx.fill(x + 1, legY + 1, x + w - 1, legY + legH - 1, CARD_BG);

        drawWrappedBox(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch2_legend"),
            x + 5,
            legY + 2,
            w - 10,
            legH - 4,
            0.68f,
            0.62f,
            INK_PRIMARY,
            false
        );
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

        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        int maxCap = Math.max(1, sg.maxDirectBookshelves());
        int minEpi = SyncedEnchantmentCosts.epiphanyMinBookshelves();
        int shelves = Mth.clamp(demoShelves, 0, maxCap);

        int topY = y + 18;
        int cell = 8;
        int gridSize = 7 * cell;
        int gx = x + 2;
        int gy = topY + 1;
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

        int ctrlX = gx + gridSize + 6;
        int ctrlW = Math.max(40, (x + w) - ctrlX);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_scan").getString(),
            ctrlX,
            topY + 6.0f,
            0.66f,
            ctrlW,
            INK_PURPLE,
            false
        );

        int spinY = topY + 13;
        drawMiniButton(ctx, font, ctrlX, spinY, 15, 13, "-", mouseX, mouseY);
        hits.add(new HitRegion(ctrlX, spinY, 15, 13, ACTION_SHELF_STEP, "-1"));

        ctx.fill(ctrlX + 17, spinY, ctrlX + ctrlW - 17, spinY + 13, CARD_BG);
        drawCenteredBox(
            ctx,
            font,
            shelves + " / " + maxCap,
            ctrlX + 17,
            ctrlW - 34,
            spinY + 6.5f,
            0.68f,
            INK_PRIMARY,
            false
        );

        drawMiniButton(ctx, font, ctrlX + ctrlW - 15, spinY, 15, 13, "+", mouseX, mouseY);
        hits.add(new HitRegion(ctrlX + ctrlW - 15, spinY, 15, 13, ACTION_SHELF_STEP, "+1"));

        int[] presets = java.util.stream.IntStream.of(0, sg.tier1Bookshelves(), sg.tier2Bookshelves(), minEpi, maxCap)
            .filter(v -> v >= 0 && v <= maxCap)
            .distinct()
            .sorted()
            .toArray();
        int pw = (ctrlW - 8) / presets.length;
        int py = topY + 29;
        for (int i = 0; i < presets.length; i++) {
            int px = ctrlX + i * (pw + 2);
            boolean active = shelves == presets[i];
            ctx.fill(px, py, px + pw, py + 12, active ? INK_GOLD_DARK : CARD_BORDER);
            ctx.fill(px + 1, py + 1, px + pw - 1, py + 11, active ? INK_PURPLE : CARD_BG);
            drawCenteredBox(
                ctx,
                font,
                String.valueOf(presets[i]),
                px,
                pw,
                py + 6.0f,
                0.60f,
                active ? LIGHT_GOLD : INK_PRIMARY,
                false
            );
            hits.add(new HitRegion(px, py, pw, 12, ACTION_SHELF_PRESET, String.valueOf(presets[i])));
        }

        boolean epiphanyReady = shelves >= minEpi;
        Component epiStatus = epiphanyReady
            ? Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_epi_on", minEpi)
            : Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_epi_off", minEpi);
        drawWrappedBox(
            ctx,
            font,
            epiStatus,
            ctrlX,
            topY + 43,
            ctrlW,
            16,
            0.64f,
            0.58f,
            epiphanyReady ? INK_EMERALD : INK_MUTED,
            false
        );

        int tblY = topY + 62;
        int tblH = Math.max(46, (y + h) - tblY);
        ctx.fill(x, tblY, x + w, tblY + tblH, CARD_BORDER);
        ctx.fill(x + 1, tblY + 1, x + w - 1, tblY + tblH - 1, CARD_BG);

        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3_curve_hdr").getString(),
            x + 5,
            tblY + 7.5f,
            0.66f,
            w - 10,
            INK_GOLD_DARK,
            false
        );
        ctx.fill(x + 4, tblY + 14, x + w - 4, tblY + 15, RULE_LINE);

        int[] maxClasses = {5, 4, 3, 2};
        String[] romanMax = {"MAX V", "MAX IV", "MAX III", "MAX II"};
        float rowStep = (tblH - 17.0f) / maxClasses.length;
        for (int r = 0; r < maxClasses.length; r++) {
            int maxLvl = maxClasses[r];
            float cy = tblY + 17.0f + rowStep * (r + 0.5f);
            drawLeftCenteredY(ctx, font, romanMax[r], x + 5, cy, 0.62f, 38, INK_PURPLE, false);

            int gx0 = x + 46;
            int cellW = (w - 50) / maxLvl;
            for (int lvl = 1; lvl <= maxLvl; lvl++) {
                int req = BookshelfProgression.requiredBookshelvesForDirectTier(lvl, maxLvl, maxCap, sg.gamma());
                boolean unlocked = shelves >= req;
                int bx = gx0 + (lvl - 1) * cellW;
                int bw = cellW - 2;
                ctx.fill(bx, Math.round(cy - 6.5f), bx + bw, Math.round(cy + 6.5f), unlocked ? 0xFF1F6E4A : 0xFFD9CBB2);
                String tag = toRoman(lvl) + ":" + req;
                drawCenteredBox(
                    ctx,
                    font,
                    tag,
                    bx + 1,
                    bw - 2,
                    cy,
                    0.58f,
                    unlocked ? LIGHT_CREAM : INK_SECONDARY,
                    false
                );
            }
        }
    }

    // =========================================================================
    // CHAPTER 4 — DUAL XP MODES + TWO-STAGE -35% / -40% RESONANCE BAR
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

        int c1Y = y + 17;
        drawXpModeCard(
            ctx, font, x, c1Y, w, 37,
            XP_ORB_3, "Lv.18 (-3L)", 0xFF1E6E46,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m1_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m1_b"
        );

        int c2Y = c1Y + 40;
        drawXpModeCard(
            ctx, font, x, c2Y, w, 37,
            XP_FLASK, "-12L XP", 0xFF5A287D,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m2_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m2_b"
        );

        int c3Y = c2Y + 40;
        drawXpModeCard(
            ctx, font, x, c3Y, w, 35,
            XP_REFUND, "+XP", 0xFF1F665E,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m3_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4_m3_b"
        );

        int barCardY = c3Y + 38;
        int barCardH = Math.max(30, (y + h) - barCardY);
        ctx.fill(x, barCardY, x + w, barCardY + barCardH, CARD_BORDER);
        ctx.fill(x + 1, barCardY + 1, x + w - 1, barCardY + barCardH - 1, CARD_BG);

        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        int pWood = Math.max(1, lr.maxWoodenDiscountPercent());
        int pGlobal = Math.max(1, lr.maxGlobalMasteryDiscountPercent());
        int pBaseCap = Math.max(1, lr.maxBaseXpDiscountPercent());
        int pSpec = Math.max(1, lr.maxSpecificEnchantBonusPercent());
        int pTotalCap = Math.max(1, lr.maxTotalXpDiscountPercent());

        drawWrappedBox(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch4_res", pBaseCap, pTotalCap),
            x + 5,
            barCardY + 2,
            w - 10,
            Math.max(12, barCardH - 16),
            0.66f,
            0.60f,
            INK_PRIMARY,
            false
        );

        int bx = x + 5;
        int bw = w - 10;
        int bh = 11;
        int by = barCardY + barCardH - bh - 3;
        int w1 = Math.round(bw * (pWood / (float) pTotalCap));
        int wBase = Math.round(bw * (pBaseCap / (float) pTotalCap));
        int w2 = Math.max(12, wBase - w1);
        int w3 = Math.max(12, bw - w1 - w2);

        ctx.fill(bx, by, bx + w1, by + bh, 0xFF8C5B1F);
        ctx.fill(bx + w1, by, bx + w1 + w2, by + bh, 0xFF5A2D82);
        ctx.fill(bx + w1 + w2, by, bx + bw, by + bh, 0xFF1C7555);
        ctx.fill(bx + w1 + w2 - 1, by - 1, bx + w1 + w2 + 1, by + bh + 1, LIGHT_GOLD);
        drawCenteredBox(ctx, font, "-" + pWood + "%", bx, w1, by + bh * 0.5f, 0.58f, LIGHT_CREAM, false);
        drawCenteredBox(ctx, font, "-" + pGlobal + "%", bx + w1, w2, by + bh * 0.5f, 0.58f, LIGHT_GOLD, false);
        drawCenteredBox(ctx, font, "+" + pSpec + "%→-" + pTotalCap + "%", bx + w1 + w2, w3, by + bh * 0.5f, 0.56f, LIGHT_MINT, false);
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

        ctx.blit(icon, x + 6, y + 3, x + 15, y + 12, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx,
            font,
            Component.translatable(titleKey).getString(),
            x + 18,
            y + 8.0f,
            0.70f,
            w - 68,
            accent,
            false
        );

        int pw = 44;
        int px = x + w - pw - 4;
        ctx.fill(px, y + 2, px + pw, y + 13, accent);
        drawCenteredBox(ctx, font, pillText, px + 1, pw - 2, y + 7.5f, 0.60f, LIGHT_CREAM, false);

        drawWrappedBox(
            ctx,
            font,
            Component.translatable(bodyKey),
            x + 6,
            y + 14,
            w - 11,
            h - 16,
            0.66f,
            0.60f,
            INK_SECONDARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 5 — THREE-ROUTE BOOK CARE & MASTER TOME LOOP
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
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch5_title");

        int areaY = y + 17;
        int availH = (y + h) - areaY;
        int cardH = (availH - 8) / 3;

        // Route 1: Combine (Top slot book + Bottom slot book -> next tier + Depleted Tome, 0 materials)
        int r1Y = areaY;
        ctx.fill(x, r1Y, x + w, r1Y + cardH, CARD_BORDER);
        ctx.fill(x + 1, r1Y + 1, x + w - 1, r1Y + cardH - 1, CARD_BG);
        ctx.fill(x, r1Y, x + 3, r1Y + cardH, INK_EMERALD);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r1").getString(),
            x + 6, r1Y + 8.0f, 0.68f, w - 10, INK_EMERALD, false
        );
        int sy1 = r1Y + Math.max(15, (cardH - 18) / 2 + 5);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 8, sy1, 18, mouseX, mouseY, slotsOut, false, true);
        drawCenteredBox(ctx, font, "+", x + 28, 9, sy1 + 9.0f, 0.70f, INK_PRIMARY, false);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 39, sy1, 18, mouseX, mouseY, slotsOut, false, true);
        drawRightArrow(ctx, x + 62, sy1 + 9, 18, INK_EMERALD);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 85, sy1, 18, mouseX, mouseY, slotsOut, true, true);
        drawCenteredBox(ctx, font, "+", x + 106, 9, sy1 + 9.0f, 0.68f, INK_GOLD_DARK, false);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME), x + 117, sy1, 18, mouseX, mouseY, slotsOut, false, true);

        // Route 2: Material Research (Top slot book + Lapis / Emerald / Amethyst / Catalyst -> next tier)
        int r2Y = r1Y + cardH + 4;
        ctx.fill(x, r2Y, x + w, r2Y + cardH, CARD_BORDER);
        ctx.fill(x + 1, r2Y + 1, x + w - 1, r2Y + cardH - 1, CARD_BG);
        ctx.fill(x, r2Y, x + 3, r2Y + cardH, INK_PURPLE);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r2").getString(),
            x + 6, r2Y + 8.0f, 0.68f, w - 10, INK_PURPLE, false
        );
        int sy2 = r2Y + Math.max(15, (cardH - 18) / 2 + 5);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 8, sy2, 18, mouseX, mouseY, slotsOut, false, true);
        drawCenteredBox(ctx, font, "+", x + 27, 8, sy2 + 9.0f, 0.70f, INK_PRIMARY, false);
        drawSlotItem(ctx, font, new ItemStack(Items.LAPIS_LAZULI, 6), x + 36, sy2, 18, mouseX, mouseY, slotsOut, false, true);
        drawSlotItem(ctx, font, new ItemStack(Items.EMERALD, 5), x + 56, sy2, 18, mouseX, mouseY, slotsOut, false, true);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.ARCANE_CATALYST), x + 76, sy2, 18, mouseX, mouseY, slotsOut, false, true);
        drawRightArrow(ctx, x + 98, sy2 + 9, 16, INK_PURPLE);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK), x + 118, sy2, 18, mouseX, mouseY, slotsOut, true, true);

        // Route 3: Direct Transfer & Chiseled Shelf Graduation
        int r3Y = r2Y + cardH + 4;
        int r3H = Math.max(28, (y + h) - r3Y);
        ctx.fill(x, r3Y, x + w, r3Y + r3H, CALLOUT_BORDER);
        ctx.fill(x + 1, r3Y + 1, x + w - 1, r3Y + r3H - 1, CALLOUT_BG);
        ctx.fill(x, r3Y, x + 3, r3Y + r3H, INK_GOLD_DARK);
        drawSlotItem(ctx, font, new ItemStack(Items.CHISELED_BOOKSHELF), x + 6, r3Y + (r3H - 20) / 2, 20, mouseX, mouseY, slotsOut, true, true);
        drawWrappedBox(
            ctx,
            font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5_r3"),
            x + 30,
            r3Y + 3,
            w - 35,
            r3H - 6,
            0.68f,
            0.62f,
            INK_PRIMARY,
            false
        );
    }

    // =========================================================================
    // CHAPTER 6 — LIVE TREASURE EPIPHANIES, 4-STAR SLOTS, STASIS & REROLL
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
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch6_title");

        int minShelves = SyncedEnchantmentCosts.epiphanyMinBookshelves();
        int maxSlots = Math.max(1, SyncedEnchantmentCosts.epiphanyMaxSlotsCap());
        int frzLv = SyncedEnchantmentCosts.epiphanyFreezeXpLevels();
        int rerLv = SyncedEnchantmentCosts.epiphanyRerollXpLevels();
        List<ItemStack> frzOpts = SyncedEnchantmentCosts.epiphanyFreezeCosts();
        List<ItemStack> rerOpts = SyncedEnchantmentCosts.epiphanyRerollCosts();

        int bY = y + 17;
        ctx.fill(x, bY, x + w, bY + 38, CARD_BORDER);
        ctx.fill(x + 1, bY + 1, x + w - 1, bY + 37, CARD_BG);
        drawSlotItem(
            ctx, font,
            new ItemStack(Items.BOOKSHELF, minShelves),
            x + 5, bY + 9, 20, mouseX, mouseY, slotsOut, false, true
        );
        drawRightArrow(ctx, x + 28, bY + 19, 13, INK_GOLD_DARK);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_gate", minShelves).getString(),
            x + 44, bY + 11.5f, 0.66f, w - 48, INK_PURPLE, false
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_slots", maxSlots).getString(),
            x + 44, bY + 26.5f, 0.64f, w - 48, INK_EMERALD, false
        );

        int midY = bY + 43;
        int halfW = (w - 4) / 2;

        // Left card: Stasis Freeze (live costs from SyncedEnchantmentCosts!)
        ctx.fill(x, midY, x + halfW, midY + 56, 0xFF3E8FA8);
        ctx.fill(x + 1, midY + 1, x + halfW - 1, midY + 55, 0xFFEBF6F9);
        ctx.blit(BTN_FREEZE, x + 5, midY + 5, x + 17, midY + 17, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_freeze_t", frzLv).getString(),
            x + 20, midY + 11.0f, 0.66f, halfW - 23, 0xFF15576B, false
        );
        for (int i = 0; i < Math.min(3, frzOpts.size()); i++) {
            drawSlotItem(ctx, font, frzOpts.get(i), x + 5 + i * 21, midY + 27, 19, mouseX, mouseY, slotsOut, false, true);
        }

        // Right card: Manual Reroll (live costs from SyncedEnchantmentCosts!)
        int rx = x + halfW + 4;
        ctx.fill(rx, midY, rx + halfW, midY + 56, INK_PURPLE);
        ctx.fill(rx + 1, midY + 1, rx + halfW - 1, midY + 55, CARD_BG);
        ctx.blit(BTN_REROLL, rx + 5, midY + 5, rx + 17, midY + 17, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_reroll_t", rerLv).getString(),
            rx + 20, midY + 11.0f, 0.66f, halfW - 23, INK_PURPLE, false
        );
        for (int i = 0; i < Math.min(3, rerOpts.size()); i++) {
            drawSlotItem(ctx, font, rerOpts.get(i), rx + 5 + i * 21, midY + 27, 19, mouseX, mouseY, slotsOut, false, true);
        }

        int botY = midY + 60;
        int botH = Math.max(28, (y + h) - botY);
        ctx.fill(x, botY, x + w, botY + botH, CALLOUT_BORDER);
        ctx.fill(x + 1, botY + 1, x + w - 1, botY + botH - 1, CALLOUT_BG);
        ctx.fill(x, botY, x + 3, botY + botH, INK_EMERALD);
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch6_payoff"),
            x + 6, botY + 2, w - 10, botH - 4, 0.68f, 0.62f, INK_PRIMARY, false
        );
    }

    // =========================================================================
    // CHAPTER 7 — LIVE TRANSMUTATION SHOWCASE + 4-GATE DEPLETED TOME LADDER
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
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch7_title");

        List<TransformationQueryIndex.Entry> entries = TransformationQueryIndex.build();
        if (entries.isEmpty()) {
            return;
        }
        int count = Math.min(6, entries.size());
        int sel = Mth.clamp(selectedTransmute, 0, count - 1);
        TransformationQueryIndex.Entry cur = entries.get(sel);

        int stripY = y + 17;
        int gap = 2;
        int cellW = (w - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int cx = x + i * (cellW + gap);
            boolean active = i == sel;
            ctx.fill(cx, stripY, cx + cellW, stripY + 22, active ? INK_GOLD_DARK : CARD_BORDER);
            ctx.fill(cx + 1, stripY + 1, cx + cellW - 1, stripY + 21, active ? 0xFFFFF4D4 : CARD_BG);
            ItemStack outIcon = entries.get(i).outputStack();
            drawSlotItem(ctx, font, outIcon, cx + (cellW - 18) / 2, stripY + 2, 18, mouseX, mouseY, slotsOut, active, false);
            hits.add(new HitRegion(cx, stripY, cellW, 22, ACTION_TRANSMUTE_SELECT, String.valueOf(i)));
        }

        // Exact live input + primary cost + bag extras -> output + real Lv.X (-dL)
        int detY = stripY + 26;
        int detH = 44;
        ctx.fill(x, detY, x + w, detY + detH, CARD_BORDER);
        ctx.fill(x + 1, detY + 1, x + w - 1, detY + detH - 1, CARD_BG);

        ItemStack inStack = cur.inputStack();
        List<ItemStack> firstBundle = cur.costOptions().isEmpty() ? List.of() : cur.costOptions().getFirst();

        int sx = x + 6;
        int sy = detY + 12;
        drawSlotItem(ctx, font, inStack, sx, sy, 20, mouseX, mouseY, slotsOut, false, true);
        sx += 22;
        for (int bi = 0; bi < Math.min(3, firstBundle.size()); bi++) {
            drawCenteredBox(ctx, font, "+", sx, 7, sy + 10.0f, 0.62f, INK_MUTED, false);
            sx += 7;
            drawSlotItem(ctx, font, firstBundle.get(bi), sx, sy + 1, 18, mouseX, mouseY, slotsOut, false, true);
            sx += 19;
        }
        int outX = x + w - 28;
        int arrowStart = Math.min(sx + 3, outX - 18);
        drawRightArrow(ctx, arrowStart, sy + 10, Math.max(12, outX - 4 - arrowStart), INK_GOLD_DARK);

        String lvTag = "Lv." + Math.max(1, cur.xpRequirement().requiredLevel())
            + " (-" + Math.max(1, cur.xpRequirement().deductedLevels()) + "L)";
        drawRightCenteredY(ctx, font, lvTag, x + 6, outX - 2, detY + 6.5f, 0.60f, INK_EMERALD, false);
        drawSlotItem(ctx, font, cur.outputStack(), outX, sy - 1, 22, mouseX, mouseY, slotsOut, true, true);

        int ladY = detY + detH + 4;
        int ladH = Math.max(42, (y + h) - ladY);
        ctx.fill(x, ladY, x + w, ladY + ladH, CALLOUT_BORDER);
        ctx.fill(x + 1, ladY + 1, x + w - 1, ladY + ladH - 1, CALLOUT_BG);

        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch7_tome_hdr").getString(),
            x + 5, ladY + 7.5f, 0.64f, w - 10, INK_PURPLE, false
        );

        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        String[] gates = {
            "0+: 2\u00d7 (\u00d7" + sg.baseBatchSize() + ")",
            sg.tier1Bookshelves() + "+: 0 XP",
            sg.tier2Bookshelves() + "+: 1\u00d7 (-50%)",
            sg.tier2Bookshelves() + "+ & " + sg.tier3QualifyingEnchantments() + "*: 0 (\u00d7" + sg.tier3BatchSize() + ")"
        };
        int[] gCols = {0xFF7D634C, 0xFF285E6B, 0xFF4C236E, 0xFF175E3E};
        int gw = (w - 10) / 2;
        int gh = Math.max(12, (ladH - 18) / 2);
        for (int i = 0; i < 4; i++) {
            int gx0 = x + 4 + (i % 2) * (gw + 2);
            int gy0 = ladY + 15 + (i / 2) * (gh + 2);
            ctx.fill(gx0, gy0, gx0 + gw, gy0 + gh, gCols[i]);
            drawCenteredBox(ctx, font, gates[i], gx0 + 2, gw - 4, gy0 + gh * 0.5f, 0.58f, LIGHT_CREAM, false);
        }
    }

    // =========================================================================
    // CHAPTER 8 — 4-WAY CLEANSE & CURSE PURGE DIAGRAM (LIVE SERVER REAGENTS)
    // =========================================================================
    public static void renderChapter8Cleanse(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch8_title");

        Identifier sampleId = Identifier.withDefaultNamespace("sharpness");
        var syncedStrip = SyncedEnchantmentCosts.find(OptionType.STRIP_SINGLE_ENCHANTMENT, sampleId, 3);
        List<List<ItemStack>> stripBundles = syncedStrip.map(SyncedEnchantmentCosts.Cost::costOptions)
            .orElseGet(() -> ChiseledEnchantingTableData.targetedStripCostOptions(sampleId, 3, 5));

        Identifier curseSampleId = Identifier.withDefaultNamespace("binding_curse");
        var syncedCurse = SyncedEnchantmentCosts.find(OptionType.PURGE_CURSE, curseSampleId, 1);
        int curseXp = syncedCurse.map(SyncedEnchantmentCosts.Cost::xpLevelCost)
            .orElseGet(ChiseledEnchantingTableData::cursePurgeXpCost);
        List<List<ItemStack>> curseBundles = syncedCurse.map(SyncedEnchantmentCosts.Cost::costOptions)
            .orElseGet(() -> ChiseledEnchantingTableData.cursePurgeCostOptions(curseSampleId));

        String[][] rows = {
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c1_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c1_b", "+XP"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c2_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c2_b", "+XP"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c3_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c3_b", "-1~3L"},
            {"gui.chiseled_enchanting_table.grimoire.diag.ch8_c4_t", "gui.chiseled_enchanting_table.grimoire.diag.ch8_c4_b", "-" + curseXp + "L"}
        };
        ItemStack[] icons = {
            new ItemStack(Items.EXPERIENCE_BOTTLE),
            new ItemStack(BlockRegistry.DEPLETED_TOME),
            cycledReagentIcon(stripBundles, new ItemStack(BlockRegistry.CLEANSING_GRIT)),
            cycledReagentIcon(curseBundles, new ItemStack(Items.GHAST_TEAR))
        };
        int[] accents = {0xFF175E3E, 0xFF285E6B, 0xFF4C236E, INK_CRIMSON};

        int areaY = y + 17;
        int availH = (y + h) - areaY;
        int cardH = (availH - 9) / 4;

        for (int i = 0; i < 4; i++) {
            int cy = areaY + i * (cardH + 3);
            ctx.fill(x, cy, x + w, cy + cardH, CARD_BORDER);
            ctx.fill(x + 1, cy + 1, x + w - 1, cy + cardH - 1, CARD_BG);
            ctx.fill(x, cy, x + 3, cy + cardH, accents[i]);

            drawSlotItem(ctx, font, icons[i], x + 5, cy + (cardH - 18) / 2, 18, mouseX, mouseY, slotsOut, false, true);

            drawLeftCenteredY(
                ctx, font,
                Component.translatable(rows[i][0]).getString(),
                x + 26, cy + 7.5f, 0.68f, w - 64, accents[i], false
            );

            int pw = 32;
            ctx.fill(x + w - pw - 4, cy + 2, x + w - 4, cy + 12, accents[i]);
            drawCenteredBox(ctx, font, rows[i][2], x + w - pw - 3, pw - 2, cy + 7.0f, 0.56f, LIGHT_CREAM, false);

            drawWrappedBox(
                ctx, font,
                Component.translatable(rows[i][1]),
                x + 26, cy + 13, w - 30, Math.max(12, cardH - 15), 0.65f, 0.58f, INK_SECONDARY, false
            );
        }
    }

    private static ItemStack cycledReagentIcon(List<List<ItemStack>> bundles, ItemStack fallback) {
        if (bundles == null || bundles.isEmpty()) {
            return fallback;
        }
        List<ItemStack> distinct = new ArrayList<>();
        for (List<ItemStack> bundle : bundles) {
            for (ItemStack st : bundle) {
                if (st.isEmpty()) {
                    continue;
                }
                boolean seen = false;
                for (ItemStack existing : distinct) {
                    if (ItemStack.isSameItem(existing, st)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    distinct.add(st);
                }
            }
        }
        if (distinct.isEmpty()) {
            return fallback;
        }
        int idx = (int) (Math.floorMod(System.currentTimeMillis() / 1600L, distinct.size()));
        return distinct.get(idx);
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

    // =========================================================================
    // CHAPTER 2 PAGE 2 — CHEST PERSISTENCE & LIVE RITUAL QUEUE CONSOLE DIAGRAM
    // =========================================================================
    public static void renderChapter2Persistence(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch2p2_title");

        int c1Y = y + 17;
        int c1H = 56;
        ctx.fill(x, c1Y, x + w, c1Y + c1H, CARD_BORDER);
        ctx.fill(x + 1, c1Y + 1, x + w - 1, c1Y + c1H - 1, CARD_BG);
        ctx.fill(x, c1Y, x + 3, c1Y + c1H, INK_EMERALD);

        drawSlotItem(ctx, font, new ItemStack(Items.CHEST), x + 6, c1Y + 7, 20, mouseX, mouseY, slotsOut, false, true);
        drawCenteredBox(ctx, font, "=", x + 27, 9, c1Y + 17.0f, 0.72f, INK_GOLD_DARK, false);
        drawSlotItem(
            ctx, font, new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE),
            x + 37, c1Y + 7, 20, mouseX, mouseY, slotsOut, true, true
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch2p2_chest_t").getString(),
            x + 62, c1Y + 11.5f, 0.68f, w - 66, INK_EMERALD, false
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch2p2_chest_sub").getString(),
            x + 62, c1Y + 23.0f, 0.60f, w - 66, INK_SECONDARY, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch2p2_chest_b"),
            x + 6, c1Y + 30, w - 11, 24, 0.65f, 0.58f, INK_PRIMARY, false
        );

        int c2Y = c1Y + c1H + 5;
        int c2H = Math.max(52, (y + h) - c2Y);
        ctx.fill(x, c2Y, x + w, c2Y + c2H, 0xFF8B56BA);
        ctx.fill(x + 1, c2Y + 1, x + w - 1, c2Y + c2H - 1, 0xFF221433);

        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.ritual.panel_title", 3).getString(),
            x + 6, c2Y + 8.5f, 0.66f, w - 12, LIGHT_GOLD, true
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.ritual.panel_timer", "06:40").getString(),
            x + 6, c2Y + 19.5f, 0.62f, w - 12, LIGHT_CREAM, true
        );

        int barX = x + 6;
        int barW = w - 12;
        int barY = c2Y + 26;
        ctx.fill(barX, barY, barX + barW, barY + 6, 0xFF12091E);
        ctx.fill(barX + 1, barY + 1, barX + Math.round(barW * 0.62f), barY + 5, 0xFFB86BFF);

        int btnY = barY + 9;
        int halfW = (barW - 4) / 2;
        ctx.fill(barX, btnY, barX + halfW, btnY + 14, 0xFF43A07A);
        ctx.fill(barX + 1, btnY + 1, barX + halfW - 1, btnY + 13, 0xFF163D33);
        drawCenteredBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.ritual.btn_sacrifice", 9).getString(),
            barX + 2, halfW - 4, btnY + 7.0f, 0.62f, LIGHT_MINT, true
        );

        int rx = barX + halfW + 4;
        ctx.fill(rx, btnY, rx + halfW, btnY + 14, 0xFFD98B38);
        ctx.fill(rx + 1, btnY + 1, rx + halfW - 1, btnY + 13, 0xFF42211C);
        drawCenteredBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.ritual.btn_rush", 32).getString(),
            rx + 2, halfW - 4, btnY + 7.0f, 0.62f, LIGHT_GOLD, true
        );

        if (c2H > 58) {
            drawWrappedBox(
                ctx, font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch2p2_note"),
                barX, btnY + 16, barW, c2H - (btnY - c2Y) - 18, 0.62f, 0.56f, LIGHT_CREAM, true
            );
        }
    }

    // =========================================================================
    // CHAPTER 3 PAGE 2 — DEEP RITUAL GATE & DUAL SKIP RISK COMPARISON
    // =========================================================================
    public static void renderChapter3Ritual(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch3p2_title");

        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        int midShelf = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.medianShelfGate(sg);

        int gY = y + 17;
        int gH = 34;
        ctx.fill(x, gY, x + w, gY + gH, CARD_BORDER);
        ctx.fill(x + 1, gY + 1, x + w - 1, gY + gH - 1, CALLOUT_BG);
        ctx.fill(x, gY, x + 3, gY + gH, INK_GOLD_DARK);
        drawWrappedBox(
            ctx, font,
            Component.translatable(
                "gui.chiseled_enchanting_table.grimoire.diag.ch3p2_gate",
                midShelf,
                SyncedEnchantmentCosts.ritualConfig().optimalTargetTomes()
            ),
            x + 6, gY + 3, w - 10, gH - 6, 0.66f, 0.58f, INK_PRIMARY, false
        );

        int s1Y = gY + gH + 4;
        int cardH = Math.max(40, ((y + h) - s1Y - 4) / 2);
        ctx.fill(x, s1Y, x + w, s1Y + cardH, CARD_BORDER);
        ctx.fill(x + 1, s1Y + 1, x + w - 1, s1Y + cardH - 1, CARD_BG);
        ctx.fill(x, s1Y, x + 3, s1Y + cardH, INK_EMERALD);
        ctx.blit(XP_FLASK, x + 6, s1Y + 4, x + 15, s1Y + 13, 0.0f, 1.0f, 0.0f, 1.0f);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p2_sac_t").getString(),
            x + 18, s1Y + 8.5f, 0.68f, w - 22, INK_EMERALD, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p2_sac_b"),
            x + 6, s1Y + 15, w - 11, cardH - 17, 0.64f, 0.58f, INK_SECONDARY, false
        );

        int s2Y = s1Y + cardH + 4;
        int s2H = Math.max(40, (y + h) - s2Y);
        ctx.fill(x, s2Y, x + w, s2Y + s2H, CARD_BORDER);
        ctx.fill(x + 1, s2Y + 1, x + w - 1, s2Y + s2H - 1, CARD_BG);
        ctx.fill(x, s2Y, x + 3, s2Y + s2H, INK_CRIMSON);
        drawSlotItem(
            ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME),
            x + 5, s2Y + 4, 18, mouseX, mouseY, slotsOut, false, true
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p2_rush_t").getString(),
            x + 26, s2Y + 11.0f, 0.68f, w - 30, INK_CRIMSON, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p2_rush_b"),
            x + 6, s2Y + 23, w - 11, Math.max(14, s2H - 25), 0.63f, 0.56f, INK_SECONDARY, false
        );
    }

    // =========================================================================
    // CHAPTER 4 PAGE 2 — WORKED EXAMPLE OF "原为 XX 级" IN GREEN vs PURPLE MODE
    // =========================================================================
    public static void renderChapter4ThresholdGuide(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch4p2_title");

        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        int maxDisc = lr.maxTotalXpDiscountPercent();
        int discountedLv = Math.max(3, (int) Math.round(30 * (1.0 - maxDisc / 100.0)));

        int fY = y + 17;
        int fH = 34;
        ctx.fill(x, fY, x + w, fY + fH, CARD_BORDER);
        ctx.fill(x + 1, fY + 1, x + w - 1, fY + fH - 1, CALLOUT_BG);
        ctx.fill(x, fY, x + 3, fY + fH, INK_GOLD_DARK);
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch4p2_step", maxDisc, discountedLv),
            x + 6, fY + 3, w - 10, fH - 6, 0.65f, 0.58f, INK_PRIMARY, false
        );

        int gY = fY + fH + 4;
        int cardH = Math.max(40, ((y + h) - gY - 4) / 2);
        drawXpModeCard(
            ctx, font, x, gY, w, cardH,
            XP_ORB_3, "Lv." + discountedLv + "→-3L", 0xFF1E6E46,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4p2_green_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4p2_green_b"
        );

        int pY = gY + cardH + 4;
        int pH = Math.max(40, (y + h) - pY);
        drawXpModeCard(
            ctx, font, x, pY, w, pH,
            XP_FLASK, "-" + discountedLv + "L XP", 0xFF5A287D,
            "gui.chiseled_enchanting_table.grimoire.diag.ch4p2_purple_t",
            "gui.chiseled_enchanting_table.grimoire.diag.ch4p2_purple_b"
        );
    }

    // =========================================================================
    // CHAPTER 5 PAGE 2 — 10D/3D GAME-DAY REGEN & INFINITY TOME SLIP DIAGRAM
    // =========================================================================
    public static void renderChapter5InfinityAndRegen(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch5p2_title");

        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        String d1 = formatCleanGameDays(lr.tomeRegenTier1Seconds());
        String d2 = formatCleanGameDays(lr.tomeRegenTier2Seconds());

        int rY = y + 17;
        int rH = 52;
        ctx.fill(x, rY, x + w, rY + rH, CARD_BORDER);
        ctx.fill(x + 1, rY + 1, x + w - 1, rY + rH - 1, CARD_BG);
        ctx.fill(x, rY, x + 3, rY + rH, INK_EMERALD);
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5p2_regen_t").getString(),
            x + 6, rY + 8.0f, 0.67f, w - 10, INK_EMERALD, false
        );

        int pillY = rY + 16;
        int halfW = (w - 14) / 2;
        ctx.fill(x + 6, pillY, x + 6 + halfW, pillY + 15, 0xFF285E6B);
        drawCenteredBox(
            ctx, font,
            lr.tomeRegenTier1Shelves() + "+ → " + d1 + "d/+1",
            x + 7, halfW - 2, pillY + 7.5f, 0.58f, LIGHT_CREAM, false
        );
        ctx.fill(x + 8 + halfW, pillY, x + 8 + halfW * 2, pillY + 15, 0xFF175E3E);
        drawCenteredBox(
            ctx, font,
            lr.tomeRegenTier2Shelves() + "+ → " + d2 + "d/+1",
            x + 9 + halfW, halfW - 2, pillY + 7.5f, 0.58f, LIGHT_GOLD, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5p2_regen_b",
                lr.tomeRegenTier1Shelves(), d1, lr.tomeRegenTier2Shelves(), d2),
            x + 6, pillY + 17, w - 11, 17, 0.62f, 0.56f, INK_SECONDARY, false
        );

        int iY = rY + rH + 4;
        int iH = Math.max(54, (y + h) - iY);
        ctx.fill(x, iY, x + w, iY + iH, CARD_BORDER);
        ctx.fill(x + 1, iY + 1, x + w - 1, iY + iH - 1, CALLOUT_BG);
        ctx.fill(x, iY, x + 3, iY + iH, INK_PURPLE);
        drawSlotItem(
            ctx, font, new ItemStack(Items.BOW),
            x + 5, iY + 5, 18, mouseX, mouseY, slotsOut, false, true
        );
        drawLeftCenteredY(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5p2_inf_t").getString(),
            x + 26, iY + 11.5f, 0.67f, w - 30, INK_PURPLE, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch5p2_inf_b"),
            x + 6, iY + 24, w - 11, Math.max(24, iH - 26), 0.63f, 0.56f, INK_PRIMARY, false
        );
    }

    public static String formatCleanGameDays(int seconds) {
        double days = seconds / 1200.0;
        if (Math.abs(days - Math.round(days)) < 0.05) {
            return String.valueOf(Math.round(days));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", days);
    }

    // =========================================================================
    // CHAPTER 3 (1/3) — UNIFIED BOOKSHELF TAXONOMY SET DIAGRAM (图示法)
    // =========================================================================
    public static void renderChapter3ShelfTaxonomy(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch3p0_title");

        // Outer set box representing Broad Bookshelves (广义书架 = ① + ② + ③)
        int setY = y + 17;
        int setH = 98;
        ctx.fill(x, setY, x + w, setY + setH, 0xFF8C6218);
        ctx.fill(x + 1, setY + 1, x + w - 1, setY + setH - 1, CALLOUT_BG);
        ctx.fill(x + 1, setY + 1, x + w - 1, setY + 13, 0xFF5A3E12);
        drawCenteredBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p0_broad_hdr").getString(),
            x + 4, w - 8, setY + 7.0f, 0.62f, LIGHT_GOLD, false
        );

        // Top row inside set: [① 普通书架] | [② 空雕纹书架]
        int topRowY = setY + 16;
        int topRowH = 34;
        int colGap = 4;
        int colW = (w - 8 - colGap) / 2;
        int c1X = x + 4;
        int c2X = c1X + colW + colGap;

        ctx.fill(c1X, topRowY, c1X + colW, topRowY + topRowH, CARD_BORDER);
        ctx.fill(c1X + 1, topRowY + 1, c1X + colW - 1, topRowY + topRowH - 1, CARD_BG);
        ctx.fill(c1X, topRowY, c1X + 3, topRowY + topRowH, INK_GOLD_DARK);
        drawSlotItem(
            ctx, font, new ItemStack(Items.BOOKSHELF),
            c1X + 4, topRowY + 8, 17, mouseX, mouseY, slotsOut, false, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p0_c1"),
            c1X + 23, topRowY + 3, colW - 25, topRowH - 6, 0.58f, 0.52f, INK_PRIMARY, false
        );

        ctx.fill(c2X, topRowY, c2X + colW, topRowY + topRowH, CARD_BORDER);
        ctx.fill(c2X + 1, topRowY + 1, c2X + colW - 1, topRowY + topRowH - 1, CARD_BG);
        ctx.fill(c2X, topRowY, c2X + 3, topRowY + topRowH, 0xFF6B5B45);
        drawSlotItem(
            ctx, font, new ItemStack(Items.CHISELED_BOOKSHELF),
            c2X + 4, topRowY + 8, 17, mouseX, mouseY, slotsOut, false, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p0_c2"),
            c2X + 23, topRowY + 3, colW - 25, topRowH - 6, 0.58f, 0.52f, INK_SECONDARY, false
        );

        // Bottom wide card inside set: [③ 有书雕纹书架：普通书/褪魔残卷 => 广义书架 | 附魔书 => 广义书架 + 母本]
        int c3Y = topRowY + topRowH + 3;
        int c3H = setH - (c3Y - setY) - 4;
        ctx.fill(x + 4, c3Y, x + w - 4, c3Y + c3H, CARD_BORDER);
        ctx.fill(x + 5, c3Y + 1, x + w - 5, c3Y + c3H - 1, CARD_BG);
        ctx.fill(x + 4, c3Y, x + 7, c3Y + c3H, INK_EMERALD);

        drawSlotItem(
            ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME),
            x + 9, c3Y + 4, 16, mouseX, mouseY, slotsOut, false, false
        );
        drawSlotItem(
            ctx, font, new ItemStack(Items.ENCHANTED_BOOK),
            x + 9, c3Y + 22, 16, mouseX, mouseY, slotsOut, false, true
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p0_c3_remnant"),
            x + 28, c3Y + 3, w - 35, 18, 0.58f, 0.52f, INK_EMERALD, false
        );
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch3p0_c3_master"),
            x + 28, c3Y + 22, w - 35, Math.max(16, c3H - 24), 0.58f, 0.52f, INK_PURPLE, false
        );

        // Bottom scope matrix card showing which mechanics use which shelf definition
        int mapY = setY + setH + 3;
        int mapH = Math.max(46, (y + h) - mapY);
        ctx.fill(x, mapY, x + w, mapY + mapH, CARD_BORDER);
        ctx.fill(x + 1, mapY + 1, x + w - 1, mapY + mapH - 1, CARD_BG);

        String[] keys = {
            "gui.chiseled_enchanting_table.grimoire.diag.ch3p0_map_reg",
            "gui.chiseled_enchanting_table.grimoire.diag.ch3p0_map_broad",
            "gui.chiseled_enchanting_table.grimoire.diag.ch3p0_map_tome"
        };
        int[] accents = { INK_GOLD_DARK, INK_EMERALD, INK_PURPLE };
        int rowH = Math.max(14, (mapH - 4) / 3);
        for (int i = 0; i < 3; i++) {
            int ry = mapY + 2 + i * rowH;
            ctx.fill(x + 3, ry + 1, x + 6, ry + rowH - 1, accents[i]);
            drawWrappedBox(
                ctx, font,
                Component.translatable(keys[i]),
                x + 9, ry + 1, w - 13, rowH - 1, 0.57f, 0.50f, INK_PRIMARY, false
            );
        }
    }

    // =========================================================================
    // CHAPTER 7 (2/2) — DEPLETED TOME WORLD LOOT (<=30%) & LIBRARIAN TRADES
    // =========================================================================
    public static void renderChapter7RemnantEconomy(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<HoveredSlot> slotsOut
    ) {
        Font font = mc.font;
        drawPanelHeader(ctx, font, x, y, w, "gui.chiseled_enchanting_table.grimoire.diag.ch7p2_title");

        int bodyTop = y + 17;
        int totalH = Math.max(120, (y + h) - bodyTop);
        int gap = 3;
        int c1H = (totalH - gap * 2) * 32 / 100;
        int c2H = (totalH - gap * 2) * 31 / 100;
        int c3H = totalH - gap * 2 - c1H - c2H;

        // Card 1: World Chests + Wild Chiseled Bookshelves (<= 30% book decay)
        int c1Y = bodyTop;
        ctx.fill(x, c1Y, x + w, c1Y + c1H, CARD_BORDER);
        ctx.fill(x + 1, c1Y + 1, x + w - 1, c1Y + c1H - 1, CARD_BG);
        ctx.fill(x, c1Y, x + 3, c1Y + c1H, INK_GOLD_DARK);
        drawSlotItem(ctx, font, new ItemStack(Items.CHEST), x + 5, c1Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawSlotItem(ctx, font, new ItemStack(Items.CHISELED_BOOKSHELF), x + 23, c1Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawRightArrow(ctx, x + 41, c1Y + 13, 8, INK_GOLD_DARK);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME), x + 51, c1Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch7p2_world"),
            x + 70, c1Y + 3, w - 74, c1H - 6, 0.58f, 0.51f, INK_PRIMARY, false
        );

        // Card 2: Librarian Buyback: 2× Depleted Tome -> 1× Emerald OR 2× Amethyst Shard
        int c2Y = c1Y + c1H + gap;
        ctx.fill(x, c2Y, x + w, c2Y + c2H, CARD_BORDER);
        ctx.fill(x + 1, c2Y + 1, x + w - 1, c2Y + c2H - 1, CARD_BG);
        ctx.fill(x, c2Y, x + 3, c2Y + c2H, INK_EMERALD);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME, 2), x + 5, c2Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawRightArrow(ctx, x + 23, c2Y + 13, 8, INK_EMERALD);
        drawSlotItem(ctx, font, new ItemStack(Items.EMERALD, 1), x + 33, c2Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawSlotItem(ctx, font, new ItemStack(Items.AMETHYST_SHARD, 2), x + 51, c2Y + 5, 16, mouseX, mouseY, slotsOut, false, false);
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch7p2_recycle"),
            x + 70, c2Y + 3, w - 74, c2H - 6, 0.58f, 0.51f, INK_EMERALD, false
        );

        // Card 3: Librarian Commission: 4 Lapis / 2 Ink / 1 Emerald base -> Book / Dynamic-Weighted Enchanted Book (< Mending)
        int c3Y = c2Y + c2H + gap;
        ctx.fill(x, c3Y, x + w, c3Y + c3H, CARD_BORDER);
        ctx.fill(x + 1, c3Y + 1, x + w - 1, c3Y + c3H - 1, CALLOUT_BG);
        ctx.fill(x, c3Y, x + 3, c3Y + c3H, INK_PURPLE);
        drawSlotItem(ctx, font, new ItemStack(BlockRegistry.DEPLETED_TOME, 1), x + 5, c3Y + 4, 15, mouseX, mouseY, slotsOut, false, false);
        drawSlotItem(ctx, font, new ItemStack(Items.LAPIS_LAZULI, 4), x + 21, c3Y + 4, 15, mouseX, mouseY, slotsOut, false, false);
        drawSlotItem(ctx, font, new ItemStack(Items.INK_SAC, 2), x + 37, c3Y + 4, 15, mouseX, mouseY, slotsOut, false, false);
        drawRightArrow(ctx, x + 54, c3Y + 11, 7, INK_PURPLE);
        drawSlotItem(ctx, font, new ItemStack(Items.ENCHANTED_BOOK, 1), x + 63, c3Y + 4, 15, mouseX, mouseY, slotsOut, false, true);
        drawWrappedBox(
            ctx, font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.diag.ch7p2_rewrite"),
            x + 6, c3Y + 21, w - 10, Math.max(18, c3H - 23), 0.58f, 0.51f, INK_PRIMARY, false
        );
    }
}
