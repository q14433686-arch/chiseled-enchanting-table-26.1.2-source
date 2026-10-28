package enchantment_fusion.gui;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.registry.FusionItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Responsive double-page {@code 《融合秘典》 (Fusion Grimoire)} UI inspired by
 * Chiseled Enchanting Table's {@code ArcaneGrimoireScreen} and Herbcraft's 3-stage
 * {@code CodexScreen} ({@code 未知 -> 初识 -> 通晓}).
 * Uses exclusively pixel-art PNG textures (zero solid rectangle draw calls).
 */
public final class FusionGrimoireScreen extends Screen {
    public static final int BOOK_W = 360;
    public static final int BOOK_H = 224;
    public static final int CHAPTER_TAB_H = 18;
    public static final int CHAPTER_TAB_W = 74;

    public static final int LEFT_PAGE_X = 25;
    public static final int LEFT_PAGE_W = 141;
    public static final int RIGHT_PAGE_X = 194;
    public static final int RIGHT_PAGE_W = 141;
    public static final int PAGE_TOP_Y = 22;

    private static final int ROWS_PER_PAGE = 7;

    public enum Chapter {
        OVERVIEW("gui.enchantment_fusion.grimoire.chapter.overview"),
        SIGNATURES("gui.enchantment_fusion.grimoire.chapter.signatures"),
        TRANSMUTES("gui.enchantment_fusion.grimoire.chapter.transmutes"),
        ABERRANTS("gui.enchantment_fusion.grimoire.chapter.aberrants");

        private final String langKey;

        Chapter(String langKey) {
            this.langKey = langKey;
        }

        public String langKey() {
            return langKey;
        }
    }

    private record EntryItem(
        String stateKey,
        int stage,
        String rowLabel,
        String detailTitle,
        String formulaLine,
        String bodyText,
        String searchBlob
    ) {}

    private final @Nullable Runnable closeOverlayCallback;
    private Chapter activeChapter;
    private int bookLeft;
    private int bookTop;
    private int listScroll = 0;
    private int selectedIndex = 0;
    private String searchQuery = "";
    private @Nullable EditBox searchBox;

    public FusionGrimoireScreen(@Nullable Runnable closeOverlayCallback, int initialChapter) {
        super(Component.translatable("item.enchantment_fusion.fusion_grimoire"));
        this.closeOverlayCallback = closeOverlayCallback;
        Chapter[] chapters = Chapter.values();
        int clamped = Math.max(0, Math.min(chapters.length - 1, initialChapter));
        this.activeChapter = chapters[clamped];
    }

    @Override
    protected void init() {
        super.init();
        this.bookLeft = (this.width - BOOK_W) / 2;
        this.bookTop = (this.height - BOOK_H) / 2 + 6;

        EditBox box = new EditBox(
            this.font,
            this.bookLeft + LEFT_PAGE_X + 2,
            this.bookTop + PAGE_TOP_Y + 14,
            LEFT_PAGE_W - 4,
            13,
            Component.translatable("gui.enchantment_fusion.grimoire.search_hint")
        );
        box.setMaxLength(40);
        box.setBordered(true);
        box.setValue(this.searchQuery);
        box.setHint(Component.translatable("gui.enchantment_fusion.grimoire.search_hint"));
        box.setResponder(val -> {
            this.searchQuery = val == null ? "" : val;
            this.listScroll = 0;
            this.selectedIndex = 0;
        });
        this.searchBox = box;
        this.addRenderableWidget(box);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.closeOverlayCallback != null) {
            this.closeOverlayCallback.run();
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        if (this.closeOverlayCallback == null) {
            super.extractBackground(ctx, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(ctx, mouseX, mouseY, partialTick);

        int bx = this.bookLeft;
        int by = this.bookTop;

        // 1. Top Chapter Tabs behind book top edge, then double-page Grimoire spread, then active tab
        Chapter[] chapters = Chapter.values();
        for (int i = 0; i < chapters.length; i++) {
            if (chapters[i] != this.activeChapter) {
                renderChapterTab(ctx, i, chapters[i], false, mouseX, mouseY);
            }
        }

        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.GRIMOIRE_SPREAD, bx, by, BOOK_W, BOOK_H);
        renderChapterTab(ctx, this.activeChapter.ordinal(), this.activeChapter, true, mouseX, mouseY);

        // Top-right Close [X] button on the upper leather corner
        int closeX = bx + BOOK_W - 24;
        int closeY = by - 14;
        boolean overClose = inside(mouseX, mouseY, closeX, closeY, 20, 16);
        FusionGuiSkin.drawNineSlice(
            ctx,
            overClose ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL,
            closeX, closeY, 20, 16, 32, 32, 4
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, "\u2715", closeX + 2, 16, closeY + 8.0F,
            0.65F, overClose ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );

        // Grimoire header icon + chapter title on Left Page
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), bx + LEFT_PAGE_X, by + PAGE_TOP_Y - 4);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable(this.activeChapter.langKey()).getString(),
            bx + LEFT_PAGE_X + 18, by + PAGE_TOP_Y + 1, LEFT_PAGE_W - 20,
            0.66F, FusionGuiSkin.COLOR_INK_TITLE
        );

        HolderLookup.Provider registries = currentRegistries();
        List<EntryItem> allEntries = buildEntriesForChapter(this.activeChapter, registries);
        List<EntryItem> filtered = filterEntries(allEntries);

        // Progress badge at bottom of Left Page
        renderProgressFooter(ctx, allEntries, bx + LEFT_PAGE_X, by + 190, LEFT_PAGE_W);

        // Left Page Entry List (7 rows at y = PAGE_TOP_Y + 31 .. + 149)
        if (this.searchBox != null) {
            this.searchBox.visible = (this.activeChapter != Chapter.OVERVIEW);
        }

        int listStartY = (this.activeChapter == Chapter.OVERVIEW) ? (by + PAGE_TOP_Y + 16) : (by + PAGE_TOP_Y + 31);
        int visibleRows = (this.activeChapter == Chapter.OVERVIEW) ? 8 : ROWS_PER_PAGE;
        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        int scroll = Math.max(0, Math.min(this.listScroll, maxScroll));

        if (filtered.isEmpty()) {
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable("gui.enchantment_fusion.grimoire.no_results").getString(),
                bx + LEFT_PAGE_X + 4, LEFT_PAGE_W - 8, listStartY + 45.0F,
                0.60F, FusionGuiSkin.COLOR_INK_MUTED
            );
        } else {
            int safeSel = Math.max(0, Math.min(this.selectedIndex, filtered.size() - 1));
            for (int r = 0; r < visibleRows; r++) {
                int idx = scroll + r;
                if (idx >= filtered.size()) break;
                EntryItem entry = filtered.get(idx);
                int ry = listStartY + r * 18;
                boolean sel = (idx == safeSel);
                boolean hov = inside(mouseX, mouseY, bx + LEFT_PAGE_X, ry, LEFT_PAGE_W, 17);

                var rowSprite = sel ? FusionGuiSkin.ROW_SELECTED_A
                    : (hov ? FusionGuiSkin.ROW_HOVER : FusionGuiSkin.ROW_UNSELECTED);
                FusionGuiSkin.drawNineSlice(ctx, rowSprite, bx + LEFT_PAGE_X, ry, LEFT_PAGE_W, 17, 64, 18, 4);

                int textCol = switch (entry.stage()) {
                    case CompendiumDiscoveryTracker.STATE_MASTERED -> sel ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM;
                    case CompendiumDiscoveryTracker.STATE_HEARD -> FusionGuiSkin.COLOR_CYAN;
                    default -> FusionGuiSkin.COLOR_MUTED;
                };
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, entry.rowLabel(),
                    bx + LEFT_PAGE_X + 6, ry + 5, LEFT_PAGE_W - 12,
                    0.56F, textCol
                );
            }

            // Prev / Next scroll arrows if list exceeds visibleRows
            if (maxScroll > 0) {
                renderScrollButtons(ctx, scroll, maxScroll, bx + LEFT_PAGE_X, by + 173, mouseX, mouseY);
            }

            // Right Page Illuminated Detail View
            renderRightDetailPage(ctx, filtered.get(safeSel), bx + RIGHT_PAGE_X, by + PAGE_TOP_Y, RIGHT_PAGE_W);
        }

        // Render child widgets (EditBox search bar)
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
    }

    private void renderChapterTab(GuiGraphicsExtractor ctx, int idx, Chapter ch, boolean active, int mouseX, int mouseY) {
        int tx = this.bookLeft + 24 + idx * (CHAPTER_TAB_W + 4);
        int ty = this.bookTop - 15;
        boolean hov = inside(mouseX, mouseY, tx, ty, CHAPTER_TAB_W, CHAPTER_TAB_H);
        var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hov ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
        FusionGuiSkin.drawSprite(ctx, sprite, tx, ty, CHAPTER_TAB_W, CHAPTER_TAB_H + (active ? 2 : 0));
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable(ch.langKey()).getString(),
            tx + 4, CHAPTER_TAB_W - 8, ty + 9.5F,
            0.58F, active ? FusionGuiSkin.COLOR_GOLD : (hov ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
        );
    }

    private void renderScrollButtons(GuiGraphicsExtractor ctx, int scroll, int maxScroll, int lx, int by, int mx, int my) {
        boolean canPrev = scroll > 0;
        boolean canNext = scroll < maxScroll;
        boolean hovPrev = inside(mx, my, lx, by, 26, 14);
        boolean hovNext = inside(mx, my, lx + LEFT_PAGE_W - 26, by, 26, 14);

        FusionGuiSkin.drawNineSlice(
            ctx, !canPrev ? FusionGuiSkin.BTN_DISABLED : (hovPrev ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
            lx, by, 26, 14, 32, 32, 4
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, "\u25C0", lx + 2, 22, by + 7.0F, 0.56F,
            canPrev ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );

        String pageIndicator = (scroll + 1) + "/" + (maxScroll + 1);
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, pageIndicator, lx + 28, LEFT_PAGE_W - 56, by + 7.0F, 0.54F, FusionGuiSkin.COLOR_INK_MUTED
        );

        FusionGuiSkin.drawNineSlice(
            ctx, !canNext ? FusionGuiSkin.BTN_DISABLED : (hovNext ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
            lx + LEFT_PAGE_W - 26, by, 26, 14, 32, 32, 4
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, "\u25B6", lx + LEFT_PAGE_W - 24, 22, by + 7.0F, 0.56F,
            canNext ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );
    }

    private void renderProgressFooter(GuiGraphicsExtractor ctx, List<EntryItem> entries, int x, int y, int w) {
        if (this.activeChapter == Chapter.OVERVIEW || entries.isEmpty()) {
            return;
        }
        int mastered = 0;
        int heard = 0;
        for (EntryItem e : entries) {
            if (e.stage() >= CompendiumDiscoveryTracker.STATE_MASTERED) mastered++;
            else if (e.stage() == CompendiumDiscoveryTracker.STATE_HEARD) heard++;
        }
        String stat = Component.translatable(
            "gui.enchantment_fusion.grimoire.progress", mastered, heard, entries.size()
        ).getString();
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, stat, x + 2, w - 4, y + 4.0F, 0.54F, FusionGuiSkin.COLOR_INK_GOLD);
    }

    private void renderRightDetailPage(GuiGraphicsExtractor ctx, EntryItem item, int rx, int ry, int rw) {
        ctx.enableScissor(rx - 2, ry - 2, rx + rw + 2, ry + 178);
        try {
            // Stage pill badge
            String stageKey = switch (item.stage()) {
                case CompendiumDiscoveryTracker.STATE_MASTERED -> "gui.enchantment_fusion.grimoire.stage.mastered";
                case CompendiumDiscoveryTracker.STATE_HEARD -> "gui.enchantment_fusion.grimoire.stage.heard";
                default -> "gui.enchantment_fusion.grimoire.stage.unknown";
            };
            int stageCol = switch (item.stage()) {
                case CompendiumDiscoveryTracker.STATE_MASTERED -> FusionGuiSkin.COLOR_INK_GOLD;
                case CompendiumDiscoveryTracker.STATE_HEARD -> FusionGuiSkin.COLOR_INK_TEAL;
                default -> FusionGuiSkin.COLOR_INK_MUTED;
            };
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, Component.translatable(stageKey).getString(),
                rx, ry, rw, 0.56F, stageCol
            );

            // Detail Title
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, item.detailTitle(),
                rx, ry + 13, rw, 0.70F, FusionGuiSkin.COLOR_INK_TITLE
            );

            // Formula / Source Banner inside ornate relic socket frame
            FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SOCKET_EMPTY, rx, ry + 27, rw, 22, 96, 40, 7);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, item.formulaLine(),
                rx + 5, rw - 10, ry + 38.0F, 0.56F, FusionGuiSkin.COLOR_CREAM
            );

            // Wrapped multi-line parchment body text
            float scale = 0.60F;
            int wrapWidth = Math.max(80, Math.round(rw / scale));
            List<FormattedCharSequence> lines = this.font.split(Component.literal(item.bodyText()), wrapWidth);

            ctx.pose().pushMatrix();
            ctx.pose().translate(rx, ry + 56.0F);
            ctx.pose().scale(scale, scale);
            int curY = 0;
            for (FormattedCharSequence seq : lines) {
                if (curY * scale > 116.0F) break;
                ctx.text(this.font, seq, 0, curY, FusionGuiSkin.COLOR_INK_BODY, false);
                curY += 11;
            }
            ctx.pose().popMatrix();
        } finally {
            ctx.disableScissor();
        }
    }

    private List<EntryItem> buildEntriesForChapter(Chapter ch, @Nullable HolderLookup.Provider registries) {
        List<EntryItem> out = new ArrayList<>();
        switch (ch) {
            case OVERVIEW -> {
                String[][] secs = {
                    {
                        "grimoire.enchantment_fusion.rules_vessel.title",
                        "grimoire.enchantment_fusion.rules_vessel.sub",
                        "grimoire.enchantment_fusion.rules_vessel.body"
                    },
                    {
                        "grimoire.enchantment_fusion.rules_fees.title",
                        "grimoire.enchantment_fusion.rules_fees.sub",
                        "grimoire.enchantment_fusion.rules_fees.body"
                    },
                    {
                        "grimoire.enchantment_fusion.rules_discovery.title",
                        "grimoire.enchantment_fusion.rules_discovery.sub",
                        "grimoire.enchantment_fusion.rules_discovery.body"
                    },
                    {
                        "grimoire.enchantment_fusion.rules_lunge_frost.title",
                        "grimoire.enchantment_fusion.rules_lunge_frost.sub",
                        "grimoire.enchantment_fusion.rules_lunge_frost.body"
                    }
                };
                for (String[] sec : secs) {
                    String title = Component.translatable(sec[0]).getString();
                    String formula = Component.translatable(sec[1]).getString();
                    String body = Component.translatable(sec[2]).getString();
                    out.add(new EntryItem(
                        sec[0], CompendiumDiscoveryTracker.STATE_MASTERED,
                        "\u2726 " + title, title, formula, body,
                        (title + " " + formula + " " + body).toLowerCase(Locale.ROOT)
                    ));
                }
            }
            case SIGNATURES -> {
                for (SignatureEntry sig : SignaturePairs.allOrderedEntries()) {
                    int st = "*".equals(sig.primaryId())
                        ? Math.max(CompendiumDiscoveryTracker.STATE_HEARD, CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.sigKey(sig.id())))
                        : Math.max(
                            CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.sigKey(sig.id())),
                            CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.pairKey(sig.primaryId(), sig.secondaryId()))
                        );

                    String pName = "*".equals(sig.primaryId())
                        ? "*"
                        : FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.primaryId()).getString();
                    String sName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.secondaryId()).getString();
                    String formula = pName + " \u25B7 " + sName;
                    String fullTitle = Component.translatable(sig.titleKey()).getString();
                    String fullDesc = Component.translatable(sig.descKey()).getString();

                    if (st >= CompendiumDiscoveryTracker.STATE_MASTERED) {
                        out.add(new EntryItem(
                            sig.id(), st,
                            "\u2605 " + fullTitle,
                            fullTitle,
                            formula,
                            fullDesc,
                            (fullTitle + " " + formula + " " + sig.id()).toLowerCase(Locale.ROOT)
                        ));
                    } else if (st == CompendiumDiscoveryTracker.STATE_HEARD) {
                        out.add(new EntryItem(
                            sig.id(), st,
                            "\u25C7 " + formula,
                            Component.translatable("gui.enchantment_fusion.grimoire.heard_title").getString(),
                            formula,
                            Component.translatable("gui.enchantment_fusion.grimoire.heard_sig_clue", pName, sName).getString(),
                            formula.toLowerCase(Locale.ROOT)
                        ));
                    } else {
                        String unk = Component.translatable("gui.enchantment_fusion.grimoire.unknown_row").getString();
                        out.add(new EntryItem(
                            sig.id(), st,
                            "? " + unk,
                            unk,
                            "??? \u25B7 ???",
                            Component.translatable("gui.enchantment_fusion.grimoire.unknown_hint").getString(),
                            ""
                        ));
                    }
                }
            }
            case TRANSMUTES -> {
                for (TransmuteVariant tv : CrossDomainTransmutations.allVariants()) {
                    int st = CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.transmuteKey(tv.id()));
                    String srcName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, tv.sourceEnchantId()).getString();
                    String fullTitle = Component.translatable(tv.titleKey()).getString();
                    String fullDesc = Component.translatable(tv.descKey()).getString();

                    if (st >= CompendiumDiscoveryTracker.STATE_MASTERED) {
                        out.add(new EntryItem(
                            tv.id(), st,
                            "\u2605 " + fullTitle,
                            fullTitle,
                            srcName + " \u2192 " + fullTitle,
                            fullDesc,
                            (fullTitle + " " + srcName + " " + tv.id()).toLowerCase(Locale.ROOT)
                        ));
                    } else if (st == CompendiumDiscoveryTracker.STATE_HEARD) {
                        String clueFormula = Component.translatable("gui.enchantment_fusion.grimoire.transmute_formula_clue", srcName).getString();
                        out.add(new EntryItem(
                            tv.id(), st,
                            "\u25C7 " + clueFormula,
                            Component.translatable("gui.enchantment_fusion.grimoire.heard_title").getString(),
                            clueFormula,
                            Component.translatable("gui.enchantment_fusion.grimoire.heard_transmute_clue", srcName).getString(),
                            srcName.toLowerCase(Locale.ROOT)
                        ));
                    } else {
                        String unk = Component.translatable("gui.enchantment_fusion.grimoire.unknown_row").getString();
                        out.add(new EntryItem(
                            tv.id(), st,
                            "? " + unk,
                            unk,
                            "??? \u2192 ???",
                            Component.translatable("gui.enchantment_fusion.grimoire.unknown_hint").getString(),
                            ""
                        ));
                    }
                }
            }
            case ABERRANTS -> {
                for (String abId : AberrantRegistry.allIds()) {
                    int st = CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.aberrantKey(abId));
                    String fullTitle = Component.translatable(AberrantRegistry.titleKey(abId)).getString();
                    String fullDesc = Component.translatable(AberrantRegistry.descKey(abId)).getString();
                    if (st >= CompendiumDiscoveryTracker.STATE_MASTERED) {
                        out.add(new EntryItem(
                            abId, st,
                            "\u2620 " + fullTitle,
                            fullTitle,
                            Component.translatable("gui.enchantment_fusion.grimoire.aberrant_tag").getString(),
                            fullDesc,
                            (fullTitle + " " + abId).toLowerCase(Locale.ROOT)
                        ));
                    } else {
                        String unk = Component.translatable("gui.enchantment_fusion.grimoire.unknown_aberrant").getString();
                        out.add(new EntryItem(
                            abId, st,
                            "? " + unk,
                            unk,
                            "??? \u26A1 ???",
                            Component.translatable("gui.enchantment_fusion.grimoire.unknown_aberrant_hint").getString(),
                            ""
                        ));
                    }
                }
            }
        }
        return out;
    }

    private List<EntryItem> filterEntries(List<EntryItem> raw) {
        String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || this.activeChapter == Chapter.OVERVIEW) {
            return raw;
        }
        List<EntryItem> matched = new ArrayList<>();
        for (EntryItem item : raw) {
            if (!item.searchBlob().isEmpty() && item.searchBlob().contains(q)) {
                matched.add(item);
            }
        }
        return matched;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (delta != 0) {
            List<EntryItem> filtered = filterEntries(buildEntriesForChapter(this.activeChapter, currentRegistries()));
            int visibleRows = (this.activeChapter == Chapter.OVERVIEW) ? 8 : ROWS_PER_PAGE;
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            this.listScroll = Math.max(0, Math.min(maxScroll, this.listScroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
        int bx = this.bookLeft;
        int by = this.bookTop;

        if (event.button() == 0) {
            // Close [X] button
            if (inside(mx, my, bx + BOOK_W - 24, by - 14, 20, 16)) {
                playPageTurn(0.95F);
                this.onClose();
                return true;
            }

            // Chapter Tabs
            Chapter[] chapters = Chapter.values();
            for (int i = 0; i < chapters.length; i++) {
                int tx = bx + 24 + i * (CHAPTER_TAB_W + 4);
                int ty = by - 15;
                if (inside(mx, my, tx, ty, CHAPTER_TAB_W, CHAPTER_TAB_H)) {
                    this.activeChapter = chapters[i];
                    this.listScroll = 0;
                    this.selectedIndex = 0;
                    playPageTurn(1.05F);
                    return true;
                }
            }

            // Entry Rows & Scroll Buttons
            List<EntryItem> filtered = filterEntries(buildEntriesForChapter(this.activeChapter, currentRegistries()));
            int listStartY = (this.activeChapter == Chapter.OVERVIEW) ? (by + PAGE_TOP_Y + 16) : (by + PAGE_TOP_Y + 31);
            int visibleRows = (this.activeChapter == Chapter.OVERVIEW) ? 8 : ROWS_PER_PAGE;
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            int scroll = Math.max(0, Math.min(this.listScroll, maxScroll));

            for (int r = 0; r < visibleRows; r++) {
                int idx = scroll + r;
                if (idx >= filtered.size()) break;
                int ry = listStartY + r * 18;
                if (inside(mx, my, bx + LEFT_PAGE_X, ry, LEFT_PAGE_W, 17)) {
                    this.selectedIndex = idx;
                    playPageTurn(1.12F);
                    return true;
                }
            }

            if (maxScroll > 0) {
                int sby = by + 173;
                if (scroll > 0 && inside(mx, my, bx + LEFT_PAGE_X, sby, 26, 14)) {
                    this.listScroll = Math.max(0, scroll - visibleRows);
                    playPageTurn(0.98F);
                    return true;
                }
                if (scroll < maxScroll && inside(mx, my, bx + LEFT_PAGE_X + LEFT_PAGE_W - 26, sby, 26, 14)) {
                    this.listScroll = Math.min(maxScroll, scroll + visibleRows);
                    playPageTurn(1.04F);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            this.onClose();
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event)) {
                return true;
            }
            if (event.key() == 69) { // Swallow inventory 'E' key while typing in search box!
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox != null && this.searchBox.isFocused() && this.searchBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    private @Nullable HolderLookup.Provider currentRegistries() {
        return this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.registryAccess()
            : null;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void playPageTurn(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch));
    }
}
