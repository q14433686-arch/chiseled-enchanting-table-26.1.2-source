package chiseled_enchanting_table.gui.grimoire;

import static chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.*;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.grimoire.GrimoireDiagramRenderer.HitRegion;
import chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.ColumnRenderResult;
import chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout.PageBlock;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Full double-page illuminated manual ({@code 纂刻秘典 / Arcane Grimoire}) covering every mechanic,
 * condition, recipe, step, and anti-trap rule of {@code Chiseled Enchanting Table}.
 *
 * <p>Navigation &amp; layout architecture:</p>
 * <ul>
 *   <li><b>Spread {@code 0} — Home / Navigation Directory ({@code 主页 / 导航目录})</b>:
 *       Left page shows the illuminated emblem, 3-step quick-start overview, and live world parameters;
 *       Right page displays an interactive 8-chapter card directory where clicking any chapter card
 *       jumps straight to that chapter.</li>
 *   <li><b>Spreads {@code 1..8} — Illustrated Chapters</b>:
 *       Left page renders structured, numbered, auto-scaled prose inside hardware GPU scissor clipping;
 *       Right page renders live interactive schematics, crafting grids, progression curves, and hoverable
 *       {@link ItemStack} tooltips.</li>
 *   <li><b>4 Independent Return-to-Home &amp; Navigation Surfaces</b>:
 *       (1) Top floating bar {@code [⌂ 主页 / 目录]} button (always visible on every chapter spread) +
 *       {@code [↩ 返回附魔台]} when opened from the table UI;
 *       (2) Right-edge colored bookmark ribbons ({@code ⌂} Home + {@code 01..08} chapters);
 *       (3) Bottom page-footer {@code [◀ 上一章]} / {@code [⌂ 返回目录]} / {@code [下一章 ▶]} controls;
 *       (4) Full keyboard shortcuts: {@code Left/Right} or {@code A/D} turn spreads, {@code Home/Backspace}
 *       returns to the Home Directory, {@code 1..8} jump directly to chapters, and {@code ESC} closes
 *       (or returns cleanly to the open {@code ChiseledEnchantingTableScreen}).</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ArcaneGrimoireScreen extends Screen {

    private static final Identifier SPREAD_TEX = ChiseledEnchantingTable.identifier("gui/grimoire_spread.png");
    private static final Identifier CREST_TEX = ChiseledEnchantingTable.identifier("gui/grimoire_crest.png");

    // Logical book dimensions (backed by 2x high-DPI 672x424 texture)
    private static final int BOOK_W = 336;
    private static final int BOOK_H = 212;

    // Pixel-measured safe inner parchment margins (strictly inside [15..162] and [173..316] x [8..194])
    private static final int LEFT_PAGE_X = 22;
    private static final int RIGHT_PAGE_X = 179;
    private static final int PAGE_TOP_Y = 15;
    private static final int PAGE_W = 132;
    private static final int PAGE_H = 159;
    private static final int FOOTER_Y = 177;
    private static final int FOOTER_H = 14;

    public static final int CHAPTER_COUNT = 8;

    private static final int[] TAB_COLORS = {
        0xFF7A4E10, // 0: Home (Gilded Gold)
        0xFF5A2D82, // 1: Recipes
        0xFF285E6B, // 2: UI Anatomy
        0xFF7D5320, // 3: 3-Track Shelves
        0xFF1E6E46, // 4: XP & Resonance
        0xFF4C236E, // 5: Book Loop
        0xFF8B5E14, // 6: Epiphany & Stasis
        0xFF2B6B58, // 7: Transmutation & Depleted Tome
        0xFF7D2430  // 8: Cleanse & Curse Purge
    };

    @Nullable
    private final Screen parentScreen;

    /** {@code 0} = Home Directory spread, {@code 1..8} = Chapter spreads. */
    private int spreadIndex = 0;
    private float leftScroll = 0.0f;
    private float leftMaxScroll = 0.0f;

    // Interactive diagram states
    private int selectedRecipeIdx = 0;
    private int demoShelves = 16;
    private int selectedTransmuteIdx = 0;

    private int bookX;
    private int bookY;

    private final List<HitRegion> interactiveRegions = new ArrayList<>();
    private List<ItemStack> chapterIcons;

    private static final int NAV_SPREAD = 100;
    private static final int NAV_CLOSE = 101;

    public ArcaneGrimoireScreen(@Nullable Screen parentScreen) {
        super(Component.translatable("item.chiseled_enchanting_table.arcane_grimoire"));
        this.parentScreen = parentScreen;
    }

    public ArcaneGrimoireScreen(@Nullable Screen parentScreen, int initialSpread) {
        this(parentScreen);
        this.spreadIndex = Mth.clamp(initialSpread, 0, CHAPTER_COUNT);
    }

    @Override
    protected void init() {
        super.init();
        this.bookX = (this.width - BOOK_W) / 2 - 6;
        this.bookY = (this.height - BOOK_H) / 2 + 7;
        if (this.chapterIcons == null) {
            this.chapterIcons = List.of(
                new ItemStack(BlockRegistry.ARCANE_GRIMOIRE),
                new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE),
                new ItemStack(Items.CHISELED_BOOKSHELF),
                new ItemStack(Items.EXPERIENCE_BOTTLE),
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(BlockRegistry.ARCANE_CATALYST),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(BlockRegistry.DEPLETED_TOME)
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        GrimoireSoundHelper.playClose();
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    private void jumpToSpread(int target, boolean bookmarkSound) {
        int clamped = Mth.clamp(target, 0, CHAPTER_COUNT);
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

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        // Rendered seamlessly inside extractRenderState so parent container screen isn't double-blurred.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        this.interactiveRegions.clear();
        List<ItemStack> hoveredItem = new ArrayList<>(1);
        List<Component> hoveredTabTip = new ArrayList<>();

        // Subtle dark arcane backdrop tint
        ctx.fill(0, 0, this.width, this.height, 0xB4090610);

        // 1. Right-edge chapter bookmark ribbons (drawn slightly tucked behind right cover edge, active tab pops out!)
        renderBookmarkRibbons(ctx, mouseX, mouseY, hoveredTabTip);

        // 2. Crisp 2x double-page illuminated book spread
        ctx.blit(SPREAD_TEX, this.bookX, this.bookY, this.bookX + BOOK_W, this.bookY + BOOK_H, 0.0f, 1.0f, 0.0f, 1.0f);

        // 3. Top floating navigation bar (Home button + Breadcrumb + Return/Close button)
        renderTopNavigationBar(ctx, mouseX, mouseY);

        // 4. Main left & right pages content strictly inside safe parchment bounding boxes
        int lx = this.bookX + LEFT_PAGE_X;
        int rx = this.bookX + RIGHT_PAGE_X;
        int py = this.bookY + PAGE_TOP_Y;

        if (this.spreadIndex == 0) {
            renderHomeLeftPage(ctx, lx, py, PAGE_W, PAGE_H);
            renderHomeRightDirectory(ctx, rx, py, PAGE_W, PAGE_H, mouseX, mouseY);
        } else {
            ColumnRenderResult res = renderChapterLeftProse(ctx, this.spreadIndex, lx, py, PAGE_W, PAGE_H);
            this.leftMaxScroll = res.maxScroll();
            this.leftScroll = Mth.clamp(this.leftScroll, 0.0f, this.leftMaxScroll);
            renderChapterRightDiagram(ctx, this.spreadIndex, rx, py, PAGE_W, PAGE_H, mouseX, mouseY, hoveredItem);
        }

        // 5. In-page bottom navigation footers (Prev / Return to Home Index / Next)
        renderBottomFooters(ctx, mouseX, mouseY);

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);

        // 6. Rich interactive tooltips on top layer
        if (!hoveredItem.isEmpty()) {
            ItemStack st = hoveredItem.get(0);
            List<Component> itemLines = Screen.getTooltipFromItem(this.minecraft, st);
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

        // Left pill: [⌂ 主页 / 目录] (highlighted active on spread 0, clickable button on 1..8)
        int homeW = 74;
        int homeX = this.bookX + 8;
        boolean onHome = this.spreadIndex == 0;
        boolean overHome = mouseX >= homeX && mouseX < homeX + homeW && mouseY >= barY && mouseY < barY + barH;
        int homeBg = onHome ? 0xFF5A3912 : (overHome ? 0xFF4B2B6E : 0xFF2B1B3D);
        int homeBorder = (onHome || overHome) ? LIGHT_GOLD : 0xFF9E7B46;
        ctx.fill(homeX, barY, homeX + homeW, barY + barH, homeBorder);
        ctx.fill(homeX + 1, barY + 1, homeX + homeW - 1, barY + barH - 1, homeBg);
        String homeLabel = "\u2302 " + Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.home").getString();
        drawCenteredBox(
            ctx,
            this.font,
            homeLabel,
            homeX + 3,
            homeW - 6,
            barY + barH * 0.5f,
            0.62f,
            onHome ? LIGHT_GOLD : LIGHT_CREAM,
            true
        );
        if (!onHome) {
            this.interactiveRegions.add(new HitRegion(homeX, barY, homeW, barH, NAV_SPREAD, "0"));
        }

        // Right pill: [↩ 返回附魔台] when parentScreen != null, else [✕ 关闭]
        int closeW = 76;
        int closeX = this.bookX + BOOK_W - closeW - 8;
        boolean overClose = mouseX >= closeX && mouseX < closeX + closeW && mouseY >= barY && mouseY < barY + barH;
        ctx.fill(closeX, barY, closeX + closeW, barY + barH, overClose ? LIGHT_GOLD : 0xFF9E7B46);
        ctx.fill(closeX + 1, barY + 1, closeX + closeW - 1, barY + barH - 1, overClose ? 0xFF592638 : 0xFF2B1B3D);
        String closeKey = this.parentScreen != null
            ? "gui.chiseled_enchanting_table.grimoire.nav.back_table"
            : "gui.chiseled_enchanting_table.grimoire.nav.close";
        drawCenteredBox(
            ctx,
            this.font,
            Component.translatable(closeKey).getString(),
            closeX + 3,
            closeW - 6,
            barY + barH * 0.5f,
            0.60f,
            overClose ? LIGHT_GOLD : LIGHT_CREAM,
            true
        );
        this.interactiveRegions.add(new HitRegion(closeX, barY, closeW, barH, NAV_CLOSE, ""));

        // Center breadcrumb plaque strictly between [homeX + homeW + 4 .. closeX - 4]
        int midX = homeX + homeW + 4;
        int midW = Math.max(40, closeX - 4 - midX);
        ctx.fill(midX, barY + 1, midX + midW, barY + barH - 1, 0xCC1E132B);
        String crumb = this.spreadIndex == 0
            ? Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.crumb_home").getString()
            : (String.format("%02d / %02d · ", this.spreadIndex, CHAPTER_COUNT)
                + Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + this.spreadIndex + ".short").getString());
        drawCenteredBox(ctx, this.font, crumb, midX + 4, midW - 8, barY + barH * 0.5f, 0.58f, LIGHT_GOLD, true);
    }

    private void renderBookmarkRibbons(
        GuiGraphicsExtractor ctx,
        int mouseX,
        int mouseY,
        List<Component> hoveredTabTip
    ) {
        int startY = this.bookY + 24;
        int stepY = 18;
        int tabH = 16;

        for (int i = 0; i <= CHAPTER_COUNT; i++) {
            boolean active = (i == this.spreadIndex);
            int tx = this.bookX + BOOK_W - 3;
            int tw = active ? 23 : 18;
            int ty = startY + i * stepY;
            boolean over = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + tabH;
            if (over && !active) {
                tw = 21;
            }

            int baseCol = TAB_COLORS[i];
            ctx.fill(tx, ty, tx + tw, ty + tabH, active ? LIGHT_GOLD : 0xFF241612);
            ctx.fill(tx, ty + 1, tx + tw - 1, ty + tabH - 1, baseCol);
            if (active || over) {
                ctx.fill(tx, ty + 1, tx + tw - 1, ty + 2, 0x55FFF2CC);
            }

            String label = i == 0 ? "\u2302" : String.format("%02d", i);
            drawCenteredBox(
                ctx,
                this.font,
                label,
                tx + 2,
                tw - 4,
                ty + tabH * 0.5f,
                0.56f,
                active ? LIGHT_GOLD : LIGHT_CREAM,
                true
            );
            this.interactiveRegions.add(new HitRegion(tx, ty, tw, tabH, NAV_SPREAD, String.valueOf(i)));

            if (over) {
                if (i == 0) {
                    hoveredTabTip.add(Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.home")
                        .withStyle(ChatFormatting.GOLD));
                } else {
                    hoveredTabTip.add(Component.literal(String.format("%02d · ", i))
                        .append(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + i + ".title"))
                        .withStyle(ChatFormatting.GOLD));
                    hoveredTabTip.add(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + i + ".sub")
                        .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private void renderBottomFooters(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int fy = this.bookY + FOOTER_Y;
        int lx = this.bookX + LEFT_PAGE_X;
        int rx = this.bookX + RIGHT_PAGE_X;

        ctx.fill(lx, fy - 2, lx + PAGE_W, fy - 1, RULE_LINE);
        ctx.fill(rx, fy - 2, rx + PAGE_W, fy - 1, RULE_LINE);

        if (this.spreadIndex == 0) {
            // Left footer on Home: keyboard shortcut hint
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.keys").getString(),
                lx,
                PAGE_W,
                fy + FOOTER_H * 0.5f,
                0.54f,
                INK_MUTED,
                false
            );

            // Right footer on Home: [开始阅读第 01 章 ▶]
            int btnW = PAGE_W - 16;
            int btnX = rx + 8;
            boolean over = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= fy && mouseY < fy + FOOTER_H;
            ctx.fill(btnX, fy, btnX + btnW, fy + FOOTER_H, over ? CARD_HOVER_BORDER : CARD_BORDER);
            ctx.fill(btnX + 1, fy + 1, btnX + btnW - 1, fy + FOOTER_H - 1, over ? CARD_HOVER_BG : CALLOUT_BG);
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.start_btn").getString() + " \u25b6",
                btnX + 4,
                btnW - 8,
                fy + FOOTER_H * 0.5f,
                0.58f,
                INK_PURPLE,
                false
            );
            this.interactiveRegions.add(new HitRegion(btnX, fy, btnW, FOOTER_H, NAV_SPREAD, "1"));
            return;
        }

        // Left page footer on Chapters 1..8: [◀ 上一页] + [⌂ 返回目录]
        int prevW = 60;
        int homeBtnW = 68;
        drawFooterPill(
            ctx,
            lx,
            fy,
            prevW,
            FOOTER_H,
            "\u25c0 " + Component.translatable(
                this.spreadIndex == 1
                    ? "gui.chiseled_enchanting_table.grimoire.nav.home_short"
                    : "gui.chiseled_enchanting_table.grimoire.nav.prev"
            ).getString(),
            String.valueOf(this.spreadIndex - 1),
            mouseX,
            mouseY,
            false
        );
        drawFooterPill(
            ctx,
            lx + PAGE_W - homeBtnW,
            fy,
            homeBtnW,
            FOOTER_H,
            "\u2302 " + Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.return_home").getString(),
            "0",
            mouseX,
            mouseY,
            true
        );

        // Right page footer on Chapters 1..8: Chapter indicator + [下一章 ▶ / 回到目录 ⌂]
        String pageCounter = String.format("%d / %d", this.spreadIndex, CHAPTER_COUNT);
        drawLeftCenteredY(ctx, this.font, pageCounter, rx + 2, fy + FOOTER_H * 0.5f, 0.58f, 44, INK_MUTED, false);

        int nextW = 78;
        boolean hasNext = this.spreadIndex < CHAPTER_COUNT;
        drawFooterPill(
            ctx,
            rx + PAGE_W - nextW,
            fy,
            nextW,
            FOOTER_H,
            hasNext
                ? (Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.next").getString() + " \u25b6")
                : ("\u2302 " + Component.translatable("gui.chiseled_enchanting_table.grimoire.nav.return_home").getString()),
            hasNext ? String.valueOf(this.spreadIndex + 1) : "0",
            mouseX,
            mouseY,
            !hasNext
        );
    }

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
        boolean accentGold
    ) {
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int border = over ? INK_PURPLE : (accentGold ? INK_GOLD_DARK : CARD_BORDER);
        int bg = over ? CARD_HOVER_BG : (accentGold ? 0xFFF5E6C4 : CARD_BG);
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        drawCenteredBox(
            ctx,
            this.font,
            text,
            x + 3,
            w - 6,
            y + h * 0.5f,
            0.55f,
            accentGold ? INK_GOLD_DARK : INK_PURPLE,
            false
        );
        this.interactiveRegions.add(new HitRegion(x, y, w, h, NAV_SPREAD, targetSpread));
    }

    // =========================================================================
    // SPREAD 0 — HOME / NAVIGATION DIRECTORY SPREAD
    // =========================================================================

    private void renderHomeLeftPage(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            // Crest medallion at top center
            int crestSize = 30;
            int cx = x + (w - crestSize) / 2;
            ctx.blit(CREST_TEX, cx, y, cx + crestSize, y + crestSize, 0.0f, 1.0f, 0.0f, 1.0f);

            // Title & subtitle
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("item.chiseled_enchanting_table.arcane_grimoire").getString(),
                x + 2,
                w - 4,
                y + 37.0f,
                0.86f,
                INK_PRIMARY,
                false
            );
            drawCenteredBox(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.edition").getString(),
                x + 2,
                w - 4,
                y + 46.5f,
                0.56f,
                INK_GOLD_DARK,
                false
            );

            // Ornamental divider
            int divY = y + 53;
            ctx.fill(x + 10, divY, x + w - 10, divY + 1, CARD_BORDER);
            ctx.fill(x + w / 2 - 14, divY, x + w / 2 + 14, divY + 1, INK_PURPLE);

            // Intro summary box
            int introY = divY + 5;
            ctx.fill(x, introY, x + w, introY + 34, CALLOUT_BG);
            ctx.fill(x, introY, x + 3, introY + 34, INK_PURPLE);
            drawWrappedParagraph(
                ctx,
                this.font,
                Component.translatable("gui.chiseled_enchanting_table.grimoire.home.intro"),
                x + 6,
                introY + 3.5f,
                w - 10,
                0.60f,
                6.8f,
                INK_PRIMARY,
                false
            );

            // 3 Core Pillars quick cards
            String[] pillars = {
                "gui.chiseled_enchanting_table.grimoire.home.p1",
                "gui.chiseled_enchanting_table.grimoire.home.p2",
                "gui.chiseled_enchanting_table.grimoire.home.p3"
            };
            int[] cols = {INK_GOLD_DARK, INK_PURPLE, INK_EMERALD};
            int pStartY = introY + 39;
            int pH = 19;
            for (int i = 0; i < 3; i++) {
                int py0 = pStartY + i * (pH + 3);
                ctx.fill(x, py0, x + w, py0 + pH, CARD_BORDER);
                ctx.fill(x + 1, py0 + 1, x + w - 1, py0 + pH - 1, CARD_BG);
                ctx.fill(x, py0, x + 13, py0 + pH, cols[i]);
                drawCenteredBox(ctx, this.font, String.valueOf(i + 1), x, 13, py0 + pH * 0.5f, 0.60f, LIGHT_GOLD, false);
                drawLeftCenteredY(
                    ctx,
                    this.font,
                    Component.translatable(pillars[i]).getString(),
                    x + 17,
                    py0 + pH * 0.5f,
                    0.56f,
                    w - 21,
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

        int listTop = y + 16;
        int availH = h - 16;
        int gap = 2;
        int rowH = (availH - gap * (CHAPTER_COUNT - 1)) / CHAPTER_COUNT; // 16px clean rows

        for (int i = 0; i < CHAPTER_COUNT; i++) {
            int ch = i + 1;
            int ry = listTop + i * (rowH + gap);
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;

            int border = over ? CARD_HOVER_BORDER : CARD_BORDER;
            int bg = over ? CARD_HOVER_BG : CARD_BG;
            ctx.fill(x, ry, x + w, ry + rowH, border);
            ctx.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, bg);
            ctx.fill(x, ry, x + 3, ry + rowH, TAB_COLORS[ch]);

            // Mini 11x11 item icon
            ItemStack icon = this.chapterIcons.get(i);
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(x + 5.0f, ry + (rowH - 11.0f) * 0.5f);
            pose.scale(11.0f / 16.0f, 11.0f / 16.0f);
            ctx.item(icon, 0, 0);
            pose.popMatrix();

            // Chapter number + title
            String title = String.format("%02d ", ch)
                + Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + ch + ".short").getString();
            drawLeftCenteredY(
                ctx,
                this.font,
                title,
                x + 19,
                ry + rowH * 0.5f,
                0.60f,
                w - 30,
                over ? INK_PURPLE : INK_PRIMARY,
                false
            );

            drawCenteredBox(
                ctx,
                this.font,
                "\u25b8",
                x + w - 10,
                8,
                ry + rowH * 0.5f,
                0.58f,
                over ? INK_GOLD_DARK : INK_MUTED,
                false
            );

            this.interactiveRegions.add(new HitRegion(x, ry, w, rowH, NAV_SPREAD, String.valueOf(ch)));
        }
    }

    // =========================================================================
    // SPREADS 1..8 — LEFT PAGE PROSE + RIGHT PAGE INTERACTIVE DIAGRAMS
    // =========================================================================

    private ColumnRenderResult renderChapterLeftProse(
        GuiGraphicsExtractor ctx,
        int ch,
        int x,
        int y,
        int w,
        int h
    ) {
        var sg = SyncedEnchantmentCosts.shelfGatesConfig();
        var lr = SyncedEnchantmentCosts.libraryResonanceConfig();
        String badge = String.format("%02d", ch);
        Component title = Component.translatable("gui.chiseled_enchanting_table.grimoire.ch" + ch + ".title");

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
            case 2 -> List.of(
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
            );
            case 3 -> List.of(
                PageBlock.lead(Component.translatable(
                    "gui.chiseled_enchanting_table.grimoire.ch3.lead",
                    sg.horizontalScanRadius(),
                    sg.verticalScanRadius()
                )),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.tracks"), INK_GOLD_DARK),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.t1")),
                PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.t2")),
                PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.t3")),
                PageBlock.callout(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.note_t"),
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch3.note_b"),
                    INK_PURPLE
                )
            );
            case 4 -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.rules"), INK_EMERALD),
                PageBlock.bullet("1", INK_EMERALD, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m1")),
                PageBlock.bullet("2", INK_PURPLE, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m2")),
                PageBlock.bullet("3", INK_GOLD_DARK, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.m3")),
                PageBlock.callout(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch4.res_t", lr.maxTotalXpDiscountPercent()),
                    Component.translatable(
                        "gui.chiseled_enchanting_table.grimoire.ch4.res_b",
                        lr.maxDiscountBookshelves(),
                        lr.maxWoodenDiscountPercent(),
                        lr.maxGlobalMasteryDiscountPercent(),
                        lr.maxSpecificEnchantBonusPercent()
                    ),
                    INK_EMERALD
                )
            );
            case 5 -> List.of(
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
            );
            case 6 -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.conditions"), INK_GOLD_DARK),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.s1")),
                PageBlock.step(2, Component.translatable(
                    "gui.chiseled_enchanting_table.grimoire.ch6.s2",
                    SyncedEnchantmentCosts.epiphanyMinBookshelves()
                )),
                PageBlock.step(3, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.s3")),
                PageBlock.callout(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.note_t"),
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch6.note_b"),
                    0xFF285E6B
                )
            );
            case 7 -> List.of(
                PageBlock.lead(Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.lead")),
                PageBlock.header(Component.translatable("gui.chiseled_enchanting_table.grimoire.sec.steps"), INK_EMERALD),
                PageBlock.step(1, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.s1")),
                PageBlock.step(2, Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.s2")),
                PageBlock.step(3, Component.translatable(
                    "gui.chiseled_enchanting_table.grimoire.ch7.s3",
                    sg.tier2Bookshelves(),
                    sg.tier3QualifyingEnchantments()
                )),
                PageBlock.callout(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.note_t"),
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.ch7.note_b"),
                    INK_EMERALD
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
        int ch,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        List<ItemStack> hoveredItem
    ) {
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            switch (ch) {
                case 1 -> GrimoireDiagramRenderer.renderChapter1Recipes(
                    ctx, this.minecraft, x, y, w, h, this.selectedRecipeIdx, mouseX, mouseY, this.interactiveRegions, hoveredItem
                );
                case 2 -> GrimoireDiagramRenderer.renderChapter2Anatomy(
                    ctx, this.minecraft, x, y, w, h, mouseX, mouseY, hoveredItem
                );
                case 3 -> GrimoireDiagramRenderer.renderChapter3Shelves(
                    ctx, this.minecraft, x, y, w, h, this.demoShelves, mouseX, mouseY, this.interactiveRegions
                );
                case 4 -> GrimoireDiagramRenderer.renderChapter4Economy(ctx, this.minecraft, x, y, w, h);
                case 5 -> GrimoireDiagramRenderer.renderChapter5BookLoop(
                    ctx, this.minecraft, x, y, w, h, mouseX, mouseY, hoveredItem
                );
                case 6 -> GrimoireDiagramRenderer.renderChapter6Epiphany(
                    ctx, this.minecraft, x, y, w, h, mouseX, mouseY, hoveredItem
                );
                case 7 -> GrimoireDiagramRenderer.renderChapter7Transmute(
                    ctx, this.minecraft, x, y, w, h, this.selectedTransmuteIdx, mouseX, mouseY, this.interactiveRegions, hoveredItem
                );
                default -> GrimoireDiagramRenderer.renderChapter8Cleanse(ctx, this.minecraft, x, y, w, h);
            }
        } finally {
            ctx.disableScissor();
        }
    }

    // =========================================================================
    // INPUT HANDLING (MOUSE CLICKS, SCROLL WHEEL, FULL KEYBOARD SHORTCUTS)
    // =========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            for (HitRegion region : this.interactiveRegions) {
                if (region.contains(mouseX, mouseY)) {
                    switch (region.actionId()) {
                        case NAV_SPREAD -> jumpToSpread(Integer.parseInt(region.payload()), true);
                        case NAV_CLOSE -> onClose();
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
                            int updated = Mth.clamp(this.demoShelves + delta, 0, 38);
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
            int lx = this.bookX + LEFT_PAGE_X;
            int py = this.bookY + PAGE_TOP_Y;
            if (mouseX >= lx && mouseX < lx + PAGE_W && mouseY >= py && mouseY < py + PAGE_H) {
                this.leftScroll = Mth.clamp(this.leftScroll - (float) (scrollY * 14.0), 0.0f, this.leftMaxScroll);
                return true;
            }
        }
        if (scrollY < -0.2 && this.spreadIndex < CHAPTER_COUNT) {
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
        if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_D || keyCode == InputConstants.KEY_PAGEDOWN) {
            if (this.spreadIndex < CHAPTER_COUNT) {
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
            jumpToSpread(ch, true);
            return true;
        }
        return super.keyPressed(event);
    }
}
