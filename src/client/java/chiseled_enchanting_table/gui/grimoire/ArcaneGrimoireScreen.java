package chiseled_enchanting_table.gui.grimoire;

import static chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.*;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.grimoire.GrimoireDiagramRenderer.HitRegion;
import chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.ColumnRenderResult;
import chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.PageBlock;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge.HoveredSlot;
import chiseled_enchanting_table.registry.BlockRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Responsive double-page illuminated manual ({@code 纂刻秘典 / Chiseled Grimoire}).
 *
 * <p>Supports two lossless presentation modes:</p>
 * <ul>
 *   <li><b>Standalone Screen:</b> opened from right-clicking the {@code Arcane Grimoire} item.</li>
 *   <li><b>In-Container Modal Overlay ({@code G1} safe):</b> hosted directly inside
 *       {@link chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen} so the server container
 *       menu is never closed, slot items are never ejected, and live 1s bookshelf updates keep running.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ArcaneGrimoireScreen extends Screen {

    private static final Identifier SPREAD_TEX = ChiseledEnchantingTable.identifier("gui/grimoire_spread.png");
    private static final Identifier CREST_TEX = ChiseledEnchantingTable.identifier("gui/grimoire_crest.png");

    /** Base UV coordinate system of {@code gui/grimoire_spread.png} (672x424 = 2x of 336x212). */
    public static final int BASE_BOOK_W = 336;
    public static final int BASE_BOOK_H = 212;
    public static final int CHAPTER_COUNT = 8;

    /** Maps every spread index (`0 = Home`, `1..N = multi-page chapter spreads`) to `(chapter, subPage, totalPages)`. */
    public record SpreadSpec(int chapter, int subPage, int totalPages) {}

    public static final List<SpreadSpec> SPREAD_SPECS = List.of(
        new SpreadSpec(0, 0, 0), // 0: Home Directory
        new SpreadSpec(1, 1, 1), // 1: Ch 1 — 入门与合成
        new SpreadSpec(2, 1, 2), // 2: Ch 2 (1/2) — 五大分区一眼认全
        new SpreadSpec(2, 2, 2), // 3: Ch 2 (2/2) — 箱子式存物与排队铭刻
        new SpreadSpec(3, 1, 3), // 4: Ch 3 (1/3) — 书架总区分与作用范围图示（普通 / 空雕纹 / 有书雕纹 / 广义书架）
        new SpreadSpec(3, 2, 3), // 5: Ch 3 (2/3) — 书库扫描与普通书架直附模拟
        new SpreadSpec(3, 3, 3), // 6: Ch 3 (3/3) — 深层铭刻等待与双跳过博弈
        new SpreadSpec(4, 1, 2), // 7: Ch 4 (1/2) — 三种经验图标与 -40% 减免
        new SpreadSpec(4, 2, 2), // 8: Ch 4 (2/2) — 「原为 XX 级」怎么算？门槛有什么用？
        new SpreadSpec(5, 1, 2), // 9: Ch 5 (1/2) — 64 本堆叠、升书与母本磨损
        new SpreadSpec(5, 2, 2), // 10: Ch 5 (2/2) — 游戏日温养、「无限」母本与「抄错」风险
        new SpreadSpec(6, 1, 1), // 11: Ch 6 — 宝藏顿悟（稀有×通用加权）、冰封与重随
        new SpreadSpec(7, 1, 2), // 12: Ch 7 (1/2) — 物品批量置换与台内残卷 4 档还原
        new SpreadSpec(7, 2, 2), // 13: Ch 7 (2/2) — 褪魔残卷：世界宝箱、野生书架(<=30%)与图书管理员交易
        new SpreadSpec(8, 1, 1)  // 14: Ch 8 — 退魔返经验、单洗词条与破除诅咒
    );

    public static final int MAX_SPREAD_INDEX = SPREAD_SPECS.size() - 1;

    public static int firstSpreadOfChapter(int chapter) {
        if (chapter <= 0) {
            return 0;
        }
        for (int i = 1; i < SPREAD_SPECS.size(); i++) {
            if (SPREAD_SPECS.get(i).chapter() == chapter) {
                return i;
            }
        }
        return 1;
    }

    public static int spreadOf(int chapter, int subPage) {
        for (int i = 1; i < SPREAD_SPECS.size(); i++) {
            SpreadSpec sp = SPREAD_SPECS.get(i);
            if (sp.chapter() == chapter && sp.subPage() == subPage) {
                return i;
            }
        }
        return firstSpreadOfChapter(chapter);
    }

    private static final int[] TAB_COLORS = {
        0xFF7A4E10,
        0xFF5A2D82,
        0xFF285E6B,
        0xFF7D5320,
        0xFF1E6E46,
        0xFF4C236E,
        0xFF8B5E14,
        0xFF2B6B58,
        0xFF7D2430
    };

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final Runnable closeOverride;
    private final boolean openedFromTable;

    /** {@code 0} = Home Directory spread, {@code 1..8} = Chapter spreads. */
    private int spreadIndex = 0;
    private float leftScroll = 0.0f;
    private float leftMaxScroll = 0.0f;

    // Interactive diagram states
    private int selectedRecipeIdx = 0;
    private int demoShelves = 12;
    private int selectedTransmuteIdx = 0;

    // Responsive UV-matched book geometry
    private int bookW = 404;
    private int bookH = 236;
    private int bookX;
    private int bookY;
    private int leftPageX;
    private int rightPageX;
    private int pageTopY;
    private int pageW;
    private int pageH;
    private int footerY;
    private int footerH;

    private final List<HitRegion> interactiveRegions = new ArrayList<>();
    private final List<HoveredSlot> itemSlots = new ArrayList<>();
    private List<ItemStack> chapterIcons;

    private static final int NAV_SPREAD = 100;
    private static final int NAV_CLOSE = 101;
    private static final int NAV_OPEN_CONFIG = 102;

    public ArcaneGrimoireScreen(@Nullable Screen parentScreen) {
        this(parentScreen, 0, null, parentScreen != null, 12);
    }

    public ArcaneGrimoireScreen(@Nullable Screen parentScreen, int initialSpread) {
        this(parentScreen, initialSpread, null, parentScreen != null, 12);
    }

    public ArcaneGrimoireScreen(
        @Nullable Screen parentScreen,
        int initialSpread,
        @Nullable Runnable closeOverride,
        boolean openedFromTable,
        int initialDemoShelves
    ) {
        super(Component.translatable("item.chiseled_enchanting_table.arcane_grimoire"));
        this.parentScreen = parentScreen;
        this.spreadIndex = initialSpread <= 0
            ? 0
            : Mth.clamp(firstSpreadOfChapter(Math.min(CHAPTER_COUNT, initialSpread)), 0, MAX_SPREAD_INDEX);
        this.closeOverride = closeOverride;
        this.openedFromTable = openedFromTable;
        this.demoShelves = Mth.clamp(initialDemoShelves, 0, 38);
    }

    public void initOverlay(Minecraft mc, int width, int height) {
        this.width = width;
        this.height = height;
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        this.bookW = Mth.clamp(this.width - 38, BASE_BOOK_W, 408);
        this.bookH = Mth.clamp(this.height - 26, BASE_BOOK_H, 238);
        this.bookX = (this.width - this.bookW) / 2 - 6;
        this.bookY = (this.height - this.bookH) / 2 + 8;

        float sx = this.bookW / (float) BASE_BOOK_W;
        float sy = this.bookH / (float) BASE_BOOK_H;
        this.leftPageX = Math.round(21.5f * sx);
        this.rightPageX = Math.round(179.5f * sx);
        this.pageW = Math.round(133.5f * sx);
        this.pageTopY = Math.round(14.5f * sy);
        this.footerY = Math.round(176.5f * sy);
        this.footerH = Math.max(14, Math.round(14.0f * sy));
        this.pageH = Math.max(120, (this.footerY - 3) - this.pageTopY);

        if (this.chapterIcons == null) {
            this.chapterIcons = List.of(
                new ItemStack(BlockRegistry.ARCANE_GRIMOIRE),
                new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE),
                new ItemStack(Items.CHISELED_BOOKSHELF),
                new ItemStack(Items.EXPERIENCE_BOTTLE),
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(BlockRegistry.ARCANE_CATALYST),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(BlockRegistry.CLEANSING_GRIT)
            );
        }
    }

    /** Full bounding box including top floating bar and right-edge chapter bookmark ribbons (for JEI / REI). */
    public Rect2i fullGuiBounds() {
        return new Rect2i(this.bookX, Math.max(0, this.bookY - 19), this.bookW + 25, this.bookH + 19);
    }

    /** Returns the item slot under {@code (mouseX, mouseY)}, enabling native JEI / REI R/U/Bookmark lookups. */
    @Nullable
    public HoveredSlot hoveredIngredient(double mouseX, double mouseY) {
        for (int i = this.itemSlots.size() - 1; i >= 0; i--) {
            HoveredSlot s = this.itemSlots.get(i);
            Rect2i r = s.area();
            if (mouseX >= r.getX() && mouseX < r.getX() + r.getWidth()
                && mouseY >= r.getY() && mouseY < r.getY() + r.getHeight()) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        GrimoireSoundHelper.playClose();
        if (this.closeOverride != null) {
            this.closeOverride.run();
        } else {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }
    }

    private SpreadSpec currentSpec() {
        return SPREAD_SPECS.get(Mth.clamp(this.spreadIndex, 0, MAX_SPREAD_INDEX));
    }

    private void jumpToSpread(int target, boolean bookmarkSound) {
        int clamped = Mth.clamp(target, 0, MAX_SPREAD_INDEX);
        if (clamped == this.spreadIndex) {
            return;
        }
        this.spreadIndex = clamped;
        this.leftScroll = 0.0f;
        this.leftMaxScroll = 0.0f;
        if (clamped == 0) {
            GrimoireSoundHelper.playHome();
        } else if (bookmarkSound) {
            GrimoireSoundHelper.playChapterSelect();
        } else {
            GrimoireSoundHelper.playPageTurn();
        }
    }

    /** Draws a crisp 7x7 pixel-art House glyph (never relying on tiny Unifont fallback symbols). */
    private static void drawHomeGlyph(GuiGraphicsExtractor ctx, int leftX, int centerY, int argbColor) {
        for (int r = 0; r < 4; r++) {
            ctx.fill(leftX + 3 - r, centerY - 3 + r, leftX + 4 + r, centerY - 2 + r, argbColor);
        }
        ctx.fill(leftX + 1, centerY + 1, leftX + 3, centerY + 4, argbColor);
        ctx.fill(leftX + 4, centerY + 1, leftX + 6, centerY + 4, argbColor);
        ctx.fill(leftX + 3, centerY + 1, leftX + 4, centerY + 2, argbColor);
    }

    /** Draws a crisp 4x7 pixel-art right or left triangle arrow centered on {@code centerY}. */
    private static void drawSmallTriangle(GuiGraphicsExtractor ctx, int leftX, int centerY, boolean right, int argbColor) {
        for (int col = 0; col < 4; col++) {
            int half = right ? (3 - col) : col;
            ctx.fill(leftX + col, centerY - half, leftX + col + 1, centerY + half + 1, argbColor);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        // Rendered explicitly inside extractRenderState to prevent double-blur artifacts.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        this.interactiveRegions.clear();
        this.itemSlots.clear();
        List<Component> hoveredTabTip = new ArrayList<>();

        ctx.fill(0, 0, this.width, this.height, 0xC4080510);

        // 1. Right-edge chapter bookmark ribbons
        renderBookmarkRibbons(ctx, mouseX, mouseY, hoveredTabTip);

        // 2. Crisp 2x double-page illuminated book spread scaled proportionately
        ctx.blit(SPREAD_TEX, this.bookX, this.bookY, this.bookX + this.bookW, this.bookY + this.bookH, 0.0f, 1.0f, 0.0f, 1.0f);

        // 3. Top floating navigation bar
        renderTopNavigationBar(ctx, mouseX, mouseY);

        // 4. Main left & right pages content strictly inside UV-matched parchment bounds
        int lx = this.bookX + this.leftPageX;
        int rx = this.bookX + this.rightPageX;
        int py = this.bookY + this.pageTopY;

        if (this.spreadIndex == 0) {
            renderHomeLeftPage(ctx, lx, py, this.pageW, this.pageH);
            renderHomeRightDirectory(ctx, rx, py, this.pageW, this.pageH, mouseX, mouseY);
        } else {
            SpreadSpec spec = currentSpec();
            ColumnRenderResult res = renderChapterLeftProse(ctx, spec, lx, py, this.pageW, this.pageH);
            this.leftMaxScroll = res.maxScroll();
            this.leftScroll = Mth.clamp(this.leftScroll, 0.0f, this.leftMaxScroll);
            renderChapterRightDiagram(ctx, spec, rx, py, this.pageW, this.pageH, mouseX, mouseY);
        }

        // 5. In-page bottom navigation footers
        renderBottomFooters(ctx, mouseX, mouseY);

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);

        // 6. Rich interactive item & bookmark tooltips on top layer
        HoveredSlot hoveredSlot = hoveredIngredient(mouseX, mouseY);
        if (hoveredSlot != null) {
            ItemStack st = hoveredSlot.stack();
            List<Component> itemLines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, st));
            if (GrimoireViewerBridge.isAvailable()) {
                itemLines.add(Component.translatable("gui.chiseled_enchanting_table.grimoire.viewer_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            ctx.setTooltipForNextFrame(
                this.font,
                itemLines,
                st.getTooltipImage(),
                mouseX,
                mouseY,
                st.get(DataComponents.TOOLTIP_STYLE)
            );
        } else if (!hoveredTabTip.isEmpty()) {
            ctx.setTooltipForNextFrame(this.font, hoveredTabTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
        }
    }

    // =========================================================================
    // TOP FLOATING BAR + RIGHT BOOKMARKS + IN-PAGE FOOTERS
    // =========================================================================

    private void renderTopNavigationBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int barY = this.bookY - 17;
        int barH = 14;

        // Left pill: [Home Icon + 主页目录]
        int homeW = 84;
        int homeX = this.bookX + 8;
        boolean onHome = this.spreadIndex == 0;
        boolean overHome = mouseX >= homeX && mouseX < homeX + homeW && mouseY >= barY && mouseY < barY + barH;
        int homeBg = onHome ? 0xFF5A3912 : (overHome ? 0xFF4B2B6E : 0xFF2B1B3D);
        int homeBorder = (onHome || overHome) ? LIGHT_GOLD : 0xFF9E7B46;
        ctx.fill(homeX, barY, homeX + homeW, barY + barH, homeBorder);
        ctx.fill(homeX + 1, barY + 1, homeX + homeW - 1, barY + barH - 1, homeBg);
        int homeFg = onHome ? LIGHT_GOLD : LIGHT_CREAM;
        drawHomeGlyph(ctx, homeX + 6, barY + barH / 2, homeFg);
        drawCenteredBox(
            ctx,
            this.font,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.home").getString(),
            homeX + 15,
            homeW - 19,
            barY + barH * 0.5f,
            0.70f,
            homeFg,
            true
        );
        if (!onHome) {
            this.interactiveRegions.add(new HitRegion(homeX, barY, homeW, barH, NAV_SPREAD, "0"));
        }

        // Right pill: [返回附魔 / 合上秘典] + [配置]
        int closeW = 82;
        int closeX = this.bookX + this.bookW - closeW - 8;
        boolean overClose = mouseX >= closeX && mouseX < closeX + closeW && mouseY >= barY && mouseY < barY + barH;
        ctx.fill(closeX, barY, closeX + closeW, barY + barH, overClose ? LIGHT_GOLD : 0xFF9E7B46);
        ctx.fill(closeX + 1, barY + 1, closeX + closeW - 1, barY + barH - 1, overClose ? 0xFF592638 : 0xFF2B1B3D);
        String closeKey = this.openedFromTable
            ? "gui.chiseled_enchanting_table.grimoire.nav.back_table"
            : "gui.chiseled_enchanting_table.grimoire.nav.close";
        drawCenteredBox(
            ctx,
            this.font,
            Component.translatable(closeKey).getString(),
            closeX + 4,
            closeW - 8,
            barY + barH * 0.5f,
            0.70f,
            overClose ? LIGHT_GOLD : LIGHT_CREAM,
            true
        );
        this.interactiveRegions.add(new HitRegion(closeX, barY, closeW, barH, NAV_CLOSE, ""));

        int cfgW = 54;
        int cfgX = closeX - cfgW - 4;
        boolean overCfg = mouseX >= cfgX && mouseX < cfgX + cfgW && mouseY >= barY && mouseY < barY + barH;
        ctx.fill(cfgX, barY, cfgX + cfgW, barY + barH, overCfg ? LIGHT_GOLD : 0xFF7B63A6);
        ctx.fill(cfgX + 1, barY + 1, cfgX + cfgW - 1, barY + barH - 1, overCfg ? 0xFF3E2966 : 0xFF23173A);
        drawCenteredBox(
            ctx,
            this.font,
            Component.translatable("config.chiseled_enchanting_table.grimoire_btn").getString(),
            cfgX + 3,
            cfgW - 6,
            barY + barH * 0.5f,
            0.68f,
            overCfg ? LIGHT_GOLD : LIGHT_CREAM,
            true
        );
        this.interactiveRegions.add(new HitRegion(cfgX, barY, cfgW, barH, NAV_OPEN_CONFIG, ""));

        // Center breadcrumb plaque (shows sub-page indicator e.g. [2/2] on multi-page chapters!)
        int midX = homeX + homeW + 4;
        int midW = Math.max(40, cfgX - 4 - midX);
        ctx.fill(midX, barY + 1, midX + midW, barY + barH - 1, 0xCC1E132B);
        SpreadSpec curSpec = currentSpec();
        String crumb;
        if (this.spreadIndex == 0) {
            crumb = Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.crumb_home").getString();
        } else {
            String base = String.format("%02d / %02d  ", curSpec.chapter(), CHAPTER_COUNT)
                + Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + curSpec.chapter() + ".short").getString();
            crumb = curSpec.totalPages() > 1
                ? base + " · " + curSpec.subPage() + "/" + curSpec.totalPages()
                : base;
        }
        drawCenteredBox(ctx, this.font, crumb, midX + 5, midW - 10, barY + barH * 0.5f, 0.68f, LIGHT_GOLD, true);
    }

    private void renderBookmarkRibbons(
        GuiGraphicsExtractor ctx,
        int mouseX,
        int mouseY,
        List<Component> hoveredTabTip
    ) {
        int startY = this.bookY + 20;
        int availSpan = Math.max(162, this.bookH - 40);
        int stepY = availSpan / (CHAPTER_COUNT + 1);
        int tabH = Math.max(15, stepY - 2);
        int curChapter = currentSpec().chapter();

        for (int i = 0; i <= CHAPTER_COUNT; i++) {
            boolean active = (i == curChapter);
            int tx = this.bookX + this.bookW - 3;
            int tw = active ? 24 : 19;
            int ty = startY + i * stepY;
            boolean over = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + tabH;
            if (over && !active) {
                tw = 22;
            }

            int baseCol = TAB_COLORS[i];
            ctx.fill(tx, ty, tx + tw, ty + tabH, active ? LIGHT_GOLD : 0xFF241612);
            ctx.fill(tx, ty + 1, tx + tw - 1, ty + tabH - 1, baseCol);
            if (active || over) {
                ctx.fill(tx, ty + 1, tx + tw - 1, ty + 2, 0x55FFF2CC);
            }

            int fg = active ? LIGHT_GOLD : LIGHT_CREAM;
            if (i == 0) {
                drawHomeGlyph(ctx, tx + (tw - 7) / 2, ty + tabH / 2, fg);
            } else {
                drawCenteredBox(
                    ctx,
                    this.font,
                    String.format("%02d", i),
                    tx + 2,
                    tw - 4,
                    ty + tabH * 0.5f,
                    0.64f,
                    fg,
                    true
                );
            }
            this.interactiveRegions.add(new HitRegion(tx, ty, tw, tabH, NAV_SPREAD, String.valueOf(firstSpreadOfChapter(i))));

            if (over) {
                if (i == 0) {
                    hoveredTabTip.add(Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.home")
                        .withStyle(ChatFormatting.GOLD));
                } else {
                    hoveredTabTip.add(Component.literal(String.format("%02d  ", i))
                        .append(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + i + ".title"))
                        .withStyle(ChatFormatting.GOLD));
                    hoveredTabTip.add(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + i + ".sub")
                        .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private void renderBottomFooters(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int fy = this.bookY + this.footerY;
        int lx = this.bookX + this.leftPageX;
        int rx = this.bookX + this.rightPageX;

        ctx.fill(lx, fy - 2, lx + this.pageW, fy - 1, RULE_LINE);
        ctx.fill(rx, fy - 2, rx + this.pageW, fy - 1, RULE_LINE);

        if (this.spreadIndex == 0) {
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.keys").getString(),
                lx,
                this.pageW,
                fy + this.footerH * 0.5f,
                0.62f,
                INK_MUTED,
                false
            );

            int btnW = this.pageW - 10;
            int btnX = rx + 5;
            boolean over = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= fy && mouseY < fy + this.footerH;
            ctx.fill(btnX, fy, btnX + btnW, fy + this.footerH, over ? CARD_HOVER_BORDER : CARD_BORDER);
            ctx.fill(btnX + 1, fy + 1, btnX + btnW - 1, fy + this.footerH - 1, over ? CARD_HOVER_BG : CALLOUT_BG);
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.start_btn").getString(),
                btnX + 6,
                btnW - 18,
                fy + this.footerH * 0.5f,
                0.70f,
                INK_PURPLE,
                false
            );
            drawSmallTriangle(ctx, btnX + btnW - 10, fy + this.footerH / 2, true, INK_PURPLE);
            this.interactiveRegions.add(new HitRegion(btnX, fy, btnW, this.footerH, NAV_SPREAD, "1"));
            return;
        }

        int prevW = Math.min(76, (this.pageW - 6) / 2);
        int homeBtnW = this.pageW - prevW - 6;
        drawFooterPill(
            ctx,
            lx,
            fy,
            prevW,
            this.footerH,
            Component.translatable(
                this.spreadIndex == 1
                    ? "gui.chiseled_enchanting_table.grimoire.nav.home_short"
                    : "gui.chiseled_enchanting_table.grimoire.nav.prev"
            ).getString(),
            String.valueOf(this.spreadIndex - 1),
            mouseX,
            mouseY,
            false,
            -1
        );
        drawFooterPill(
            ctx,
            lx + this.pageW - homeBtnW,
            fy,
            homeBtnW,
            this.footerH,
            Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.return_home").getString(),
            "0",
            mouseX,
            mouseY,
            true,
            0
        );

        SpreadSpec curSpec = currentSpec();
        int nextW = Math.min(82, this.pageW - 60);
        if (curSpec.totalPages() > 1) {
            int chipW = 24;
            for (int p = 1; p <= curSpec.totalPages(); p++) {
                int cx = rx + (p - 1) * (chipW + 3);
                boolean activeSub = (p == curSpec.subPage());
                boolean overSub = mouseX >= cx && mouseX < cx + chipW && mouseY >= fy && mouseY < fy + this.footerH;
                ctx.fill(cx, fy, cx + chipW, fy + this.footerH, activeSub ? INK_GOLD_DARK : CARD_BORDER);
                ctx.fill(cx + 1, fy + 1, cx + chipW - 1, fy + this.footerH - 1, activeSub ? INK_PURPLE : (overSub ? CARD_HOVER_BG : CARD_BG));
                drawCenteredBox(
                    ctx,
                    this.font,
                    p + "/" + curSpec.totalPages(),
                    cx + 1,
                    chipW - 2,
                    fy + this.footerH * 0.5f,
                    0.58f,
                    activeSub ? LIGHT_GOLD : INK_PRIMARY,
                    false
                );
                this.interactiveRegions.add(new HitRegion(cx, fy, chipW, this.footerH, NAV_SPREAD, String.valueOf(spreadOf(curSpec.chapter(), p))));
            }
        } else {
            String pageCounter = String.format("%d / %d", this.spreadIndex, MAX_SPREAD_INDEX);
            drawLeftCenteredY(ctx, this.font, pageCounter, rx + 2, fy + this.footerH * 0.5f, 0.68f, 52, INK_MUTED, false);
        }

        boolean hasNext = this.spreadIndex < MAX_SPREAD_INDEX;
        drawFooterPill(
            ctx,
            rx + this.pageW - nextW,
            fy,
            nextW,
            this.footerH,
            Component.translatable(
                hasNext
                    ? "gui.chiseled_enchanting_table.grimoire.nav.next"
                    : "gui.chiseled_enchanting_table.grimoire.nav.return_home"
            ).getString(),
            hasNext ? String.valueOf(this.spreadIndex + 1) : "0",
            mouseX,
            mouseY,
            !hasNext,
            hasNext ? 1 : 0
        );
    }

    /** {@code iconMode}: {@code -1} = left arrow, {@code 0} = home glyph, {@code +1} = right arrow. */
    private void drawFooterPill(
        GuiGraphicsExtractor ctx,
        int x,
        int y,
        int w,
        int h,
        String text,
        String targetSpread,
        int mouseX,
        int mouseY,
        boolean accentGold,
        int iconMode
    ) {
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int border = over ? INK_PURPLE : (accentGold ? INK_GOLD_DARK : CARD_BORDER);
        int bg = over ? CARD_HOVER_BG : (accentGold ? 0xFFF5E6C4 : CARD_BG);
        int fg = accentGold ? INK_GOLD_DARK : INK_PURPLE;
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        if (iconMode < 0) {
            drawSmallTriangle(ctx, x + 5, y + h / 2, false, fg);
            drawCenteredBox(ctx, this.font, text, x + 11, w - 14, y + h * 0.5f, 0.68f, fg, false);
        } else if (iconMode == 0) {
            drawHomeGlyph(ctx, x + 4, y + h / 2, fg);
            drawCenteredBox(ctx, this.font, text, x + 13, w - 16, y + h * 0.5f, 0.68f, fg, false);
        } else {
            drawCenteredBox(ctx, this.font, text, x + 4, w - 15, y + h * 0.5f, 0.68f, fg, false);
            drawSmallTriangle(ctx, x + w - 8, y + h / 2, true, fg);
        }
        this.interactiveRegions.add(new HitRegion(x, y, w, h, NAV_SPREAD, targetSpread));
    }

    // =========================================================================
    // SPREAD 0 — HOME / NAVIGATION DIRECTORY SPREAD
    // =========================================================================

    private void renderHomeLeftPage(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            int crestSize = 28;
            int cx = x + (w - crestSize) / 2;
            ctx.blit(CREST_TEX, cx, y, cx + crestSize, y + crestSize, 0.0f, 1.0f, 0.0f, 1.0f);
            this.itemSlots.add(new HoveredSlot(
                new ItemStack(BlockRegistry.ARCANE_GRIMOIRE),
                new Rect2i(cx, y, crestSize, crestSize),
                true
            ));

            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("item.chiseled_enchanting_table.arcane_grimoire").getString(),
                x + 2,
                w - 4,
                y + 35.5f,
                0.92f,
                INK_PRIMARY,
                false
            );
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.edition").getString(),
                x + 2,
                w - 4,
                y + 46.0f,
                0.68f,
                INK_GOLD_DARK,
                false
            );

            int divY = y + 53;
            ctx.fill(x + 10, divY, x + w - 10, divY + 1, CARD_BORDER);
            ctx.fill(x + w / 2 - 16, divY, x + w / 2 + 16, divY + 1, INK_PURPLE);

            int introY = divY + 4;
            int introH = 38;
            ctx.fill(x, introY, x + w, introY + introH, CALLOUT_BG);
            ctx.fill(x, introY, x + 3, introY + introH, INK_PURPLE);
            drawWrappedBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.intro"),
                x + 6,
                introY + 2,
                w - 10,
                introH - 4,
                0.70f,
                0.62f,
                INK_PRIMARY,
                false
            );

            String[] pillars = {
                "gui.chiseled_enchanting_table.grimoire.home.p1",
                "gui.chiseled_enchanting_table.grimoire.home.p2",
                "gui.chiseled_enchanting_table.grimoire.home.p3"
            };
            int[] cols = {INK_GOLD_DARK, INK_PURPLE, INK_EMERALD};
            int pStartY = introY + introH + 4;
            int remH = Math.max(60, (y + h) - pStartY);
            int pH = (remH - 6) / 3;
            for (int i = 0; i < 3; i++) {
                int py0 = pStartY + i * (pH + 3);
                ctx.fill(x, py0, x + w, py0 + pH, CARD_BORDER);
                ctx.fill(x + 1, py0 + 1, x + w - 1, py0 + pH - 1, CARD_BG);
                ctx.fill(x, py0, x + 14, py0 + pH, cols[i]);
                drawCenteredBox(ctx, this.font, String.valueOf(i + 1), x, 14, py0 + pH * 0.5f, 0.68f, LIGHT_GOLD, false);
                drawWrappedBox(
                    ctx,
                    this.font,
                    Component.translatable(pillars[i]),
                    x + 18,
                    py0 + 2,
                    w - 22,
                    pH - 4,
                    0.68f,
                    0.62f,
                    INK_PRIMARY,
                    false
                );
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private void renderHomeRightDirectory(
        GuiGraphicsExtractor ctx,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY
    ) {
        GrimoireDiagramRenderer.drawPanelHeader(
            ctx,
            this.font,
            x,
            y,
            w,
            "gui.chiseled_enchanting_table.grimoire.home.dir_title"
        );

        int listTop = y + 17;
        int availH = h - 17;
        int gap = 2;
        int rowH = (availH - gap * (CHAPTER_COUNT - 1)) / CHAPTER_COUNT;

        for (int i = 0; i < CHAPTER_COUNT; i++) {
            int ch = i + 1;
            int ry = listTop + i * (rowH + gap);
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;

            int border = over ? CARD_HOVER_BORDER : CARD_BORDER;
            int bg = over ? CARD_HOVER_BG : CARD_BG;
            ctx.fill(x, ry, x + w, ry + rowH, border);
            ctx.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, bg);
            ctx.fill(x, ry, x + 3, ry + rowH, TAB_COLORS[ch]);

            ItemStack icon = this.chapterIcons.get(i);
            int iconSize = Math.min(14, rowH - 3);
            int iconX = x + 5;
            int iconY = Math.round(ry + (rowH - iconSize) * 0.5f);
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(iconX, iconY);
            pose.scale(iconSize / 16.0f, iconSize / 16.0f);
            ctx.item(icon, 0, 0);
            pose.popMatrix();
            this.itemSlots.add(new HoveredSlot(icon, new Rect2i(iconX - 1, ry + 1, iconSize + 2, rowH - 2), false));

            String title = String.format("%02d  ", ch)
                + Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + ch + ".title").getString();
            drawLeftCenteredY(
                ctx,
                this.font,
                title,
                x + 22,
                ry + rowH * 0.5f,
                0.72f,
                w - 33,
                over ? INK_PURPLE : INK_PRIMARY,
                false
            );

            drawSmallTriangle(ctx, x + w - 8, ry + rowH / 2, true, over ? INK_GOLD_DARK : INK_MUTED);
            this.interactiveRegions.add(new HitRegion(x, ry, w, rowH, NAV_SPREAD, String.valueOf(firstSpreadOfChapter(ch))));
        }
    }

    // =========================================================================
    // SPREADS 1..12 — MULTI-PAGE CHAPTER PROSE + LIVE DIAGRAMS
    // =========================================================================

    private ColumnRenderResult renderChapterLeftProse(
        GuiGraphicsExtractor ctx,
        SpreadSpec spec,
        int x,
        int y,
        int w,
        int h
    ) {
        int ch = spec.chapter();
        int sub = spec.subPage();
        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        int midShelf = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.medianShelfGate(sg);
        String d1 = GrimoireDiagramRenderer.formatCleanGameDays(lr.tomeRegenTier1Seconds());
        String d2 = GrimoireDiagramRenderer.formatCleanGameDays(lr.tomeRegenTier2Seconds());

        String badge = spec.totalPages() > 1
            ? String.format("%02d·%d", ch, sub)
            : String.format("%02d", ch);
        Component title;
        if (ch == 3 && sub == 1) {
            title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.title");
        } else if (ch == 3 && sub == 2) {
            title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.title");
        } else if (ch == 3 && sub == 3) {
            title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.title");
        } else if (ch == 7 && sub == 2) {
            title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.title");
        } else if (sub == 2) {
            title = switch (ch) {
                case 2 -> Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.title");
                case 4 -> Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.title");
                default -> Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.title");
            };
        } else {
            title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + ch + ".title");
        }

        List<PageBlock> blocks = switch (ch) {
            case 1 -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.conditions"), INK_GOLD_DARK),
                PageBlock.bullet("A", INK_EMERALD, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.c1")),
                PageBlock.bullet("B", INK_PURPLE, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.c2")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_PURPLE),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.s1")),
                PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.s2")),
                PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch1.s3"))
            );
            case 2 -> (sub == 1)
                ? List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_PURPLE),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.tip"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2.tip"),
                        INK_EMERALD
                    )
                )
                : List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_EMERALD),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.tip_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch2p2.tip_b"),
                        INK_PURPLE
                    )
                );
            case 3 -> switch (sub) {
                case 1 -> List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_GOLD_DARK),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p0.note_b"),
                        INK_EMERALD
                    )
                );
                case 2 -> List.of(
                    PageBlock.lead(Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch3.lead",
                        sg.horizontalScanRadius(),
                        sg.verticalScanRadius()
                    )),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.tracks"), INK_GOLD_DARK),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.t1")),
                    PageBlock.step(2, Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch3.t2",
                        sg.maxDirectBookshelves()
                    )),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.t3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.note_b"),
                        INK_PURPLE
                    )
                );
                default -> List.of(
                    PageBlock.lead(Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch3p2.lead",
                        midShelf,
                        10
                    )),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_PURPLE),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3p2.note_b"),
                        INK_CRIMSON
                    )
                );
            };
            case 4 -> (sub == 1)
                ? List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_EMERALD),
                    PageBlock.bullet("1", INK_EMERALD, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m1")),
                    PageBlock.bullet("2", INK_PURPLE, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m2")),
                    PageBlock.bullet("3", INK_GOLD_DARK, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.res_t", lr.maxTotalXpDiscountPercent()),
                        Component.translatable(
                            "gui.chiseled_enchanting_table.grimoire.ch4.res_b",
                            lr.maxWoodenDiscountPercent(),
                            lr.maxGlobalMasteryDiscountPercent(),
                            lr.maxBaseXpDiscountPercent(),
                            lr.maxSpecificEnchantBonusPercent()
                        ),
                        INK_EMERALD
                    )
                )
                : List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_GOLD_DARK),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4p2.note_b"),
                        INK_EMERALD
                    )
                );
            case 5 -> (sub == 1)
                ? List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_PURPLE),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5.note_b"),
                        INK_GOLD_DARK
                    )
                )
                : List.of(
                    PageBlock.lead(Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch5p2.lead",
                        lr.tomeRegenTier1Shelves(),
                        d1,
                        lr.tomeRegenTier2Shelves(),
                        d2
                    )),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_EMERALD),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch5p2.note_b"),
                        INK_PURPLE
                    )
                );
            case 6 -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.conditions"), INK_GOLD_DARK),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.s1")),
                PageBlock.step(2, Component.translatable(
                    "gui.chiseled_enchanting_table.grimoire.ch6.s2",
                    SyncedEnchantmentCosts.epiphanyMinBookshelves(),
                    SyncedEnchantmentCosts.epiphanyMaxSlotsCap()
                )),
                PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.s3")),
                PageBlock.callout(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.note_t"),
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.note_b"),
                    0xFF285E6B
                )
            );
            case 7 -> (sub == 1)
                ? List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_EMERALD),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.s1")),
                    PageBlock.step(2, Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch7.s2",
                        sg.baseBatchSize(),
                        sg.tier3BatchSize()
                    )),
                    PageBlock.step(3, Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch7.s3",
                        sg.tier1Bookshelves(),
                        sg.tier2Bookshelves(),
                        sg.tier3QualifyingEnchantments()
                    )),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.note_b"),
                        INK_EMERALD
                    )
                )
                : List.of(
                    PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.lead")),
                    PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_GOLD_DARK),
                    PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.s1")),
                    PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.s2")),
                    PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.s3")),
                    PageBlock.callout(
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.note_t"),
                        Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7p2.note_b"),
                        INK_PURPLE
                    )
                );
            default -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch8.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_CRIMSON),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch8.s1")),
                PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch8.s2")),
                PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch8.s3")),
                PageBlock.step(4, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch8.s4"))
            );
        };

        return GrimoireTextLayout.renderStructuredColumn(
            ctx,
            this.minecraft,
            x,
            y,
            w,
            h,
            badge,
            title,
            blocks,
            this.leftScroll
        );
    }

    private void renderChapterRightDiagram(
        GuiGraphicsExtractor ctx,
        SpreadSpec spec,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY
    ) {
        int ch = spec.chapter();
        int sub = spec.subPage();
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            switch (ch) {
                case 1 -> GrimoireDiagramRenderer.renderChapter1Recipes(
                    ctx, this.minecraft, x, y, w, h, this.selectedRecipeIdx, mouseX, mouseY, this.interactiveRegions, this.itemSlots
                );
                case 2 -> {
                    if (sub == 1) {
                        GrimoireDiagramRenderer.renderChapter2Anatomy(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    } else {
                        GrimoireDiagramRenderer.renderChapter2Persistence(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    }
                }
                case 3 -> {
                    if (sub == 1) {
                        GrimoireDiagramRenderer.renderChapter3ShelfTaxonomy(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    } else if (sub == 2) {
                        GrimoireDiagramRenderer.renderChapter3Shelves(
                            ctx, this.minecraft, x, y, w, h, this.demoShelves, mouseX, mouseY, this.interactiveRegions
                        );
                    } else {
                        GrimoireDiagramRenderer.renderChapter3Ritual(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    }
                }
                case 4 -> {
                    if (sub == 1) {
                        GrimoireDiagramRenderer.renderChapter4Economy(ctx, this.minecraft, x, y, w, h);
                    } else {
                        GrimoireDiagramRenderer.renderChapter4ThresholdGuide(ctx, this.minecraft, x, y, w, h);
                    }
                }
                case 5 -> {
                    if (sub == 1) {
                        GrimoireDiagramRenderer.renderChapter5BookLoop(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    } else {
                        GrimoireDiagramRenderer.renderChapter5InfinityAndRegen(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    }
                }
                case 6 -> GrimoireDiagramRenderer.renderChapter6Epiphany(
                    ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                );
                case 7 -> {
                    if (sub == 1) {
                        GrimoireDiagramRenderer.renderChapter7Transmute(
                            ctx, this.minecraft, x, y, w, h, this.selectedTransmuteIdx, mouseX, mouseY, this.interactiveRegions, this.itemSlots
                        );
                    } else {
                        GrimoireDiagramRenderer.renderChapter7RemnantEconomy(
                            ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                        );
                    }
                }
                default -> GrimoireDiagramRenderer.renderChapter8Cleanse(
                    ctx, this.minecraft, x, y, w, h, mouseX, mouseY, this.itemSlots
                );
            }
        } finally {
            ctx.disableScissor();
        }
    }

    // =========================================================================
    // INPUT HANDLING (JEI/REI CLICK + HOTKEY LOOKUP, TABS, SCROLL, KEYBOARD)
    // =========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Use InputConstants button constants across Minecraft versions.
        HoveredSlot slot = hoveredIngredient(mouseX, mouseY);
        if (slot != null && GrimoireViewerBridge.isAvailable()) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (GrimoireViewerBridge.openUses(slot.stack())) {
                    return true;
                }
            } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (slot.leftClickOpensRecipes() || event.hasShiftDown())) {
                if (GrimoireViewerBridge.openRecipes(slot.stack())) {
                    return true;
                }
            }
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int maxCap = Math.max(1, SyncedEnchantmentCosts.shelfGatesConfig().maxDirectBookshelves());
            for (HitRegion region : this.interactiveRegions) {
                if (region.contains(mouseX, mouseY)) {
                    switch (region.actionId()) {
                        case NAV_SPREAD -> jumpToSpread(Integer.parseInt(region.payload()), true);
                        case NAV_CLOSE -> onClose();
                        case NAV_OPEN_CONFIG -> {
                            GrimoireSoundHelper.playTabSwitch();
                            if (this.closeOverride != null && this.parentScreen instanceof chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen table) {
                                table.openConfigFromGrimoire();
                            } else {
                                Minecraft.getInstance().setScreen(new chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen(this));
                            }
                        }
                        case GrimoireDiagramRenderer.ACTION_RECIPE_TAB -> {
                            int next = Integer.parseInt(region.payload());
                            if (next != this.selectedRecipeIdx) {
                                this.selectedRecipeIdx = next;
                                GrimoireSoundHelper.playTabSwitch();
                            }
                        }
                        case GrimoireDiagramRenderer.ACTION_SHELF_PRESET -> {
                            int preset = Integer.parseInt(region.payload());
                            if (preset != this.demoShelves) {
                                this.demoShelves = preset;
                                GrimoireSoundHelper.playTabSwitch();
                            }
                        }
                        case GrimoireDiagramRenderer.ACTION_SHELF_STEP -> {
                            int delta = Integer.parseInt(region.payload());
                            int updated = Mth.clamp(this.demoShelves + delta, 0, maxCap);
                            if (updated != this.demoShelves) {
                                this.demoShelves = updated;
                                GrimoireSoundHelper.playTabSwitch();
                            }
                        }
                        case GrimoireDiagramRenderer.ACTION_TRANSMUTE_SELECT -> {
                            int idx = Integer.parseInt(region.payload());
                            if (idx != this.selectedTransmuteIdx) {
                                this.selectedTransmuteIdx = idx;
                                GrimoireSoundHelper.playTabSwitch();
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.spreadIndex > 0 && this.leftMaxScroll > 0.5f) {
            int lx = this.bookX + this.leftPageX;
            int py = this.bookY + this.pageTopY;
            if (mouseX >= lx && mouseX < lx + this.pageW && mouseY >= py && mouseY < py + this.pageH) {
                this.leftScroll = Mth.clamp(this.leftScroll - (float) (scrollY * 14.0), 0.0f, this.leftMaxScroll);
                return true;
            }
        }
        if (scrollY < -0.2 && this.spreadIndex < MAX_SPREAD_INDEX) {
            jumpToSpread(this.spreadIndex + 1, false);
            return true;
        }
        if (scrollY > 0.2 && this.spreadIndex > 0) {
            jumpToSpread(this.spreadIndex - 1, false);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }

        // Direct R / U recipe-viewer lookup fallback when hovering an item slot
        if ((keyCode == InputConstants.KEY_R || keyCode == InputConstants.KEY_U) && GrimoireViewerBridge.isAvailable()) {
            double mx = this.minecraft.mouseHandler.xpos()
                * (double) this.minecraft.getWindow().getGuiScaledWidth()
                / (double) this.minecraft.getWindow().getScreenWidth();
            double my = this.minecraft.mouseHandler.ypos()
                * (double) this.minecraft.getWindow().getGuiScaledHeight()
                / (double) this.minecraft.getWindow().getScreenHeight();
            HoveredSlot slot = hoveredIngredient(mx, my);
            if (slot != null) {
                boolean opened = (keyCode == InputConstants.KEY_R)
                    ? GrimoireViewerBridge.openRecipes(slot.stack())
                    : GrimoireViewerBridge.openUses(slot.stack());
                if (opened) {
                    return true;
                }
            }
        }

        if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_D || keyCode == InputConstants.KEY_PAGEDOWN) {
            if (this.spreadIndex < MAX_SPREAD_INDEX) {
                jumpToSpread(this.spreadIndex + 1, false);
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_A || keyCode == InputConstants.KEY_PAGEUP) {
            if (this.spreadIndex > 0) {
                jumpToSpread(this.spreadIndex - 1, false);
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_HOME || keyCode == InputConstants.KEY_BACKSPACE || keyCode == InputConstants.KEY_0) {
            if (this.spreadIndex != 0) {
                jumpToSpread(0, true);
                return true;
            }
        }
        if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_8) {
            int ch = (keyCode - InputConstants.KEY_1) + 1;
            jumpToSpread(firstSpreadOfChapter(ch), true);
            return true;
        }
        return super.keyPressed(event);
    }
}
