package enchantment_fusion.gui;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.gui.FusionGuiSkin.HitBox;
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
 * Responsive double-page {@code 《融合秘典》 (Fusion Grimoire)} UI with 3-stage
 * {@code 未知 -> 初识 -> 通晓} discovery, cached chapter lists (GUI-03),
 * scrollable right-page detail body with overflow indicators (TXT-4),
 * keyboard navigation (TXT-9), and full cursor/clipboard search input (GUI-02).
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
    private static final int MAX_SEARCH_LEN = 40;

    // Derived right-page detail layout constants (TXT-4)
    private static final int DETAIL_SCISSOR_HEIGHT = 178;
    private static final int DETAIL_BODY_OFFSET_Y = 56;
    private static final int DETAIL_BODY_HEIGHT = DETAIL_SCISSOR_HEIGHT - DETAIL_BODY_OFFSET_Y;
    private static final int DETAIL_SCROLL_BAR_H = 12;
    private static final float DETAIL_BODY_SCALE = 0.60F;
    private static final int DETAIL_LINE_STEP = 11;

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
    private int detailScroll = 0;
    private int cachedDetailMaxScroll = 0;

    // GUI-02: Full cursor & clipboard state for custom pixel-art search bar
    private String searchQuery = "";
    private int searchCaret = 0;
    private boolean searchSelectAll = false;
    private boolean searchFocused = false;

    // GUI-03: Cached chapter entries invalidated only when chapter, query, or discovery count changes
    private @Nullable Chapter cachedChapterKey = null;
    private int cachedDiscoveryHash = Integer.MIN_VALUE;
    private List<EntryItem> cachedAllEntries = List.of();
    private String cachedFilterQuery = null;
    private List<EntryItem> cachedFilteredEntries = List.of();

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
        invalidateEntryCache();
    }

    private void invalidateEntryCache() {
        this.cachedChapterKey = null;
        this.cachedFilterQuery = null;
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

        Chapter[] chapters = Chapter.values();
        for (int i = 0; i < chapters.length; i++) {
            if (chapters[i] != this.activeChapter) {
                renderChapterTab(ctx, i, chapters[i], false, mouseX, mouseY);
            }
        }

        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.GRIMOIRE_SPREAD, bx, by, BOOK_W, BOOK_H);
        for (int clasp = 0; clasp < 5; clasp++) {
            FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.VESSEL_FILLED, bx + BOOK_W / 2 - 6, by + 28 + clasp * 38, 12, 12);
        }
        renderChapterTab(ctx, this.activeChapter.ordinal(), this.activeChapter, true, mouseX, mouseY);

        // Top-right Close [X] button on the upper leather corner
        HitBox closeBox = new HitBox(bx + BOOK_W - 24, by - 14, 20, 16);
        boolean overClose = closeBox.contains(mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx,
            overClose ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL,
            closeBox.x(), closeBox.y(), closeBox.w(), closeBox.h()
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, "\u2715", closeBox.x() + 2, 16, closeBox.y() + 8.0F,
            FusionGuiSkin.SCALE_BODY, overClose ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );

        // Grimoire header icon + chapter title on Left Page
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), bx + LEFT_PAGE_X, by + PAGE_TOP_Y - 4);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable(this.activeChapter.langKey()).getString(),
            bx + LEFT_PAGE_X + 18, by + PAGE_TOP_Y + 1, LEFT_PAGE_W - 20,
            FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_INK_TITLE
        );

        List<EntryItem> allEntries = getOrBuildChapterEntries();
        List<EntryItem> filtered = getOrFilterEntries(allEntries);

        renderProgressFooter(ctx, allEntries, bx + LEFT_PAGE_X, by + 190, LEFT_PAGE_W);

        // Pixel-art Search Bar on Left Page (chapters with searchable entries)
        if (this.activeChapter != Chapter.OVERVIEW) {
            HitBox searchBox = new HitBox(bx + LEFT_PAGE_X + 2, by + PAGE_TOP_Y + 14, LEFT_PAGE_W - 4, 14);
            boolean hovSearch = searchBox.contains(mouseX, mouseY);
            var searchSlice = this.searchFocused ? FusionGuiSkin.SLICE_ROW_SELECTED_B
                : (hovSearch ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED);
            FusionGuiSkin.drawNineSlice(ctx, searchSlice, searchBox.x(), searchBox.y(), searchBox.w(), searchBox.h());
            String shown = formatSearchTextWithCaret();
            int col = this.searchQuery.isEmpty() && !this.searchFocused
                ? FusionGuiSkin.COLOR_MUTED
                : (this.searchSelectAll ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM);
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, shown,
                searchBox.x() + 5, searchBox.y() + 4, searchBox.w() - 10, FusionGuiSkin.SCALE_SMALL, col
            );
        }

        int listStartY = (this.activeChapter == Chapter.OVERVIEW) ? (by + PAGE_TOP_Y + 16) : (by + PAGE_TOP_Y + 31);
        int visibleRows = visibleRowCount();
        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        int scroll = Math.max(0, Math.min(this.listScroll, maxScroll));

        if (filtered.isEmpty()) {
            this.cachedDetailMaxScroll = 0;
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable("gui.enchantment_fusion.grimoire.no_results").getString(),
                bx + LEFT_PAGE_X + 4, LEFT_PAGE_W - 8, listStartY + 45.0F,
                FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_INK_MUTED
            );
        } else {
            int safeSel = Math.max(0, Math.min(this.selectedIndex, filtered.size() - 1));
            for (int r = 0; r < visibleRows; r++) {
                int idx = scroll + r;
                if (idx >= filtered.size()) break;
                EntryItem entry = filtered.get(idx);
                HitBox rowBox = new HitBox(bx + LEFT_PAGE_X, listStartY + r * 18, LEFT_PAGE_W, 17);
                boolean sel = (idx == safeSel);
                boolean hov = rowBox.contains(mouseX, mouseY);

                var rowSlice = sel ? FusionGuiSkin.SLICE_ROW_SELECTED_A
                    : (hov ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED);
                FusionGuiSkin.drawNineSlice(ctx, rowSlice, rowBox.x(), rowBox.y(), rowBox.w(), rowBox.h());

                int textCol = switch (entry.stage()) {
                    case CompendiumDiscoveryTracker.STATE_MASTERED -> sel ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM;
                    case CompendiumDiscoveryTracker.STATE_HEARD -> FusionGuiSkin.COLOR_CYAN;
                    default -> FusionGuiSkin.COLOR_MUTED;
                };
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, entry.rowLabel(),
                    rowBox.x() + 6, rowBox.y() + 5, rowBox.w() - 12,
                    FusionGuiSkin.SCALE_SMALL, textCol
                );
            }

            if (maxScroll > 0) {
                renderScrollButtons(ctx, scroll, maxScroll, bx + LEFT_PAGE_X, by + 173, mouseX, mouseY);
            }

            renderRightDetailPage(ctx, filtered.get(safeSel), bx + RIGHT_PAGE_X, by + PAGE_TOP_Y, RIGHT_PAGE_W, mouseX, mouseY);
        }

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        FusionGuiMicroEffects.stepAndRenderOverlayParticles(ctx);
    }

    private int visibleRowCount() {
        return (this.activeChapter == Chapter.OVERVIEW) ? 8 : ROWS_PER_PAGE;
    }

    private String formatSearchTextWithCaret() {
        if (this.searchQuery.isEmpty()) {
            return this.searchFocused
                ? "|"
                : Component.translatable("gui.enchantment_fusion.grimoire.search_hint").getString();
        }
        if (!this.searchFocused) {
            return this.searchQuery;
        }
        int clampedCaret = Math.max(0, Math.min(this.searchCaret, this.searchQuery.length()));
        return this.searchQuery.substring(0, clampedCaret) + "|" + this.searchQuery.substring(clampedCaret);
    }

    private List<EntryItem> getOrBuildChapterEntries() {
        int discHash = CompendiumDiscoveryTracker.clientStateVersionHash();
        if (this.cachedChapterKey != this.activeChapter || this.cachedDiscoveryHash != discHash) {
            this.cachedAllEntries = buildEntriesForChapter(this.activeChapter, currentRegistries());
            this.cachedChapterKey = this.activeChapter;
            this.cachedDiscoveryHash = discHash;
            this.cachedFilterQuery = null;
        }
        return this.cachedAllEntries;
    }

    private List<EntryItem> getOrFilterEntries(List<EntryItem> allEntries) {
        String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (!q.equals(this.cachedFilterQuery)) {
            this.cachedFilteredEntries = filterEntries(allEntries, q);
            this.cachedFilterQuery = q;
        }
        return this.cachedFilteredEntries;
    }

    private void renderChapterTab(GuiGraphicsExtractor ctx, int idx, Chapter ch, boolean active, int mouseX, int mouseY) {
        HitBox tabBox = new HitBox(this.bookLeft + 24 + idx * (CHAPTER_TAB_W + 4), this.bookTop - 15, CHAPTER_TAB_W, CHAPTER_TAB_H);
        boolean hov = tabBox.contains(mouseX, mouseY);
        var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hov ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
        FusionGuiSkin.drawSprite(ctx, sprite, tabBox.x(), tabBox.y(), tabBox.w(), tabBox.h() + (active ? 2 : 0));
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable(ch.langKey()).getString(),
            tabBox.x() + 4, tabBox.w() - 8, tabBox.y() + 9.5F,
            FusionGuiSkin.SCALE_SMALL, active ? FusionGuiSkin.COLOR_GOLD : (hov ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
        );
    }

    private void renderScrollButtons(GuiGraphicsExtractor ctx, int scroll, int maxScroll, int lx, int by, int mx, int my) {
        boolean canPrev = scroll > 0;
        boolean canNext = scroll < maxScroll;
        HitBox prevBox = new HitBox(lx, by, 26, 14);
        HitBox nextBox = new HitBox(lx + LEFT_PAGE_W - 26, by, 26, 14);

        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, prevBox,
            canPrev, prevBox.contains(mx, my), "\u25C0", FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
        );

        String pageIndicator = (scroll + 1) + "/" + (maxScroll + 1);
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, pageIndicator, lx + 28, LEFT_PAGE_W - 56, by + 7.0F, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_INK_MUTED
        );

        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, nextBox,
            canNext, nextBox.contains(mx, my), "\u25B6", FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
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
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, stat, x + 2, w - 4, y + 4.0F, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_INK_GOLD);
    }

    private void renderRightDetailPage(
        GuiGraphicsExtractor ctx,
        EntryItem item,
        int rx,
        int ry,
        int rw,
        int mouseX,
        int mouseY
    ) {
        ctx.enableScissor(rx - 2, ry - 2, rx + rw + 2, ry + DETAIL_SCISSOR_HEIGHT);
        try {
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
            if (item.stage() >= CompendiumDiscoveryTracker.STATE_MASTERED) {
                FusionGuiMicroEffects.renderGrimoireMasteredSeal(ctx, rx + rw - 13.0F, ry + 11.0F);
            }
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, Component.translatable(stageKey).getString(),
                rx, ry, rw, FusionGuiSkin.SCALE_SMALL, stageCol
            );

            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, item.detailTitle(),
                rx, ry + 13, rw, FusionGuiSkin.SCALE_LABEL, FusionGuiSkin.COLOR_INK_TITLE
            );

            FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_SOCKET_EMPTY, rx, ry + 27, rw, 22);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, item.formulaLine(),
                rx + 5, rw - 10, ry + 38.0F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CREAM
            );

            int wrapWidth = Math.max(80, Math.round(rw / DETAIL_BODY_SCALE));
            List<FormattedCharSequence> lines = this.font.split(Component.literal(item.bodyText()), wrapWidth);

            int fullCapacity = Math.max(1, (int) Math.floor((DETAIL_BODY_HEIGHT - 2.0F) / (DETAIL_LINE_STEP * DETAIL_BODY_SCALE)));
            boolean overflows = lines.size() > fullCapacity;
            int visibleLines = overflows
                ? Math.max(1, (int) Math.floor((DETAIL_BODY_HEIGHT - DETAIL_SCROLL_BAR_H - 2.0F) / (DETAIL_LINE_STEP * DETAIL_BODY_SCALE)))
                : fullCapacity;
            int maxDetailScroll = overflows ? Math.max(0, lines.size() - visibleLines) : 0;
            this.cachedDetailMaxScroll = maxDetailScroll;
            this.detailScroll = Math.max(0, Math.min(this.detailScroll, maxDetailScroll));

            ctx.pose().pushMatrix();
            ctx.pose().translate(rx, ry + (float) DETAIL_BODY_OFFSET_Y);
            ctx.pose().scale(DETAIL_BODY_SCALE, DETAIL_BODY_SCALE);
            int curY = 0;
            for (int i = this.detailScroll; i < lines.size() && i < this.detailScroll + visibleLines; i++) {
                ctx.text(this.font, lines.get(i), 0, curY, FusionGuiSkin.COLOR_INK_BODY, false);
                curY += DETAIL_LINE_STEP;
            }
            ctx.pose().popMatrix();

            if (overflows) {
                int barY = ry + DETAIL_SCISSOR_HEIGHT - DETAIL_SCROLL_BAR_H;
                HitBox upBox = new HitBox(rx, barY, 18, DETAIL_SCROLL_BAR_H);
                HitBox downBox = new HitBox(rx + rw - 18, barY, 18, DETAIL_SCROLL_BAR_H);
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, 0, 0, upBox,
                    this.detailScroll > 0, upBox.contains(mouseX, mouseY),
                    "\u25B2", FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GOLD
                );
                String indicator = Component.translatable(
                    "gui.enchantment_fusion.grimoire.detail_scroll",
                    this.detailScroll + 1,
                    Math.min(lines.size(), this.detailScroll + visibleLines),
                    lines.size()
                ).getString();
                FusionGuiSkin.drawCenteredBoundedText(
                    ctx, this.font, indicator,
                    rx + 20, rw - 40, barY + DETAIL_SCROLL_BAR_H * 0.5F,
                    FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_INK_GOLD
                );
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, 0, 0, downBox,
                    this.detailScroll < maxDetailScroll, downBox.contains(mouseX, mouseY),
                    "\u25BC", FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GOLD
                );
            }
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

    private List<EntryItem> filterEntries(List<EntryItem> raw, String q) {
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
            // TXT-4: Mouse-wheel over the right detail page scrolls long descriptions
            if (mouseX >= this.bookLeft + RIGHT_PAGE_X - 6 && this.cachedDetailMaxScroll > 0) {
                this.detailScroll = Math.max(0, Math.min(this.cachedDetailMaxScroll, this.detailScroll + delta));
                return true;
            }
            List<EntryItem> filtered = getOrFilterEntries(getOrBuildChapterEntries());
            int visibleRows = visibleRowCount();
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
            HitBox searchBox = new HitBox(bx + LEFT_PAGE_X + 2, by + PAGE_TOP_Y + 14, LEFT_PAGE_W - 4, 14);
            if (this.activeChapter != Chapter.OVERVIEW && searchBox.contains(mx, my)) {
                this.searchFocused = true;
                this.searchCaret = this.searchQuery.length();
                this.searchSelectAll = false;
                return true;
            }
            this.searchFocused = false;
            this.searchSelectAll = false;

            if (new HitBox(bx + BOOK_W - 24, by - 14, 20, 16).contains(mx, my)) {
                playPageTurn(0.95F);
                this.onClose();
                return true;
            }

            Chapter[] chapters = Chapter.values();
            for (int i = 0; i < chapters.length; i++) {
                HitBox tabBox = new HitBox(bx + 24 + i * (CHAPTER_TAB_W + 4), by - 15, CHAPTER_TAB_W, CHAPTER_TAB_H);
                if (tabBox.contains(mx, my)) {
                    this.activeChapter = chapters[i];
                    this.listScroll = 0;
                    this.selectedIndex = 0;
                    this.detailScroll = 0;
                    invalidateEntryCache();
                    playPageTurn(1.05F);
                    FusionGuiMicroEffects.triggerGrimoireSparkle((int) mx, (int) my);
                    return true;
                }
            }

            List<EntryItem> filtered = getOrFilterEntries(getOrBuildChapterEntries());
            int listStartY = (this.activeChapter == Chapter.OVERVIEW) ? (by + PAGE_TOP_Y + 16) : (by + PAGE_TOP_Y + 31);
            int visibleRows = visibleRowCount();
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            int scroll = Math.max(0, Math.min(this.listScroll, maxScroll));

            for (int r = 0; r < visibleRows; r++) {
                int idx = scroll + r;
                if (idx >= filtered.size()) break;
                HitBox rowBox = new HitBox(bx + LEFT_PAGE_X, listStartY + r * 18, LEFT_PAGE_W, 17);
                if (rowBox.contains(mx, my)) {
                    this.selectedIndex = idx;
                    this.detailScroll = 0;
                    playPageTurn(1.12F);
                    if (filtered.get(idx).stage() >= CompendiumDiscoveryTracker.STATE_MASTERED) {
                        FusionGuiMicroEffects.triggerGrimoireSparkle(bx + RIGHT_PAGE_X + RIGHT_PAGE_W - 14, by + PAGE_TOP_Y + 12);
                    }
                    return true;
                }
            }

            if (maxScroll > 0) {
                int sby = by + 173;
                if (scroll > 0 && new HitBox(bx + LEFT_PAGE_X, sby, 26, 14).contains(mx, my)) {
                    this.listScroll = Math.max(0, scroll - visibleRows);
                    playPageTurn(0.98F);
                    return true;
                }
                if (scroll < maxScroll && new HitBox(bx + LEFT_PAGE_X + LEFT_PAGE_W - 26, sby, 26, 14).contains(mx, my)) {
                    this.listScroll = Math.min(maxScroll, scroll + visibleRows);
                    playPageTurn(1.04F);
                    return true;
                }
            }

            if (this.cachedDetailMaxScroll > 0) {
                int rx = bx + RIGHT_PAGE_X;
                int barY = by + PAGE_TOP_Y + DETAIL_SCISSOR_HEIGHT - DETAIL_SCROLL_BAR_H;
                if (this.detailScroll > 0 && new HitBox(rx, barY, 18, DETAIL_SCROLL_BAR_H).contains(mx, my)) {
                    this.detailScroll = Math.max(0, this.detailScroll - 1);
                    playPageTurn(0.98F);
                    return true;
                }
                if (this.detailScroll < this.cachedDetailMaxScroll
                    && new HitBox(rx + RIGHT_PAGE_W - 18, barY, 18, DETAIL_SCROLL_BAR_H).contains(mx, my)) {
                    this.detailScroll = Math.min(this.cachedDetailMaxScroll, this.detailScroll + 1);
                    playPageTurn(1.04F);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (this.searchFocused && !this.searchQuery.isEmpty()) {
                updateSearchText("", 0);
                return true;
            }
            this.onClose();
            return true;
        }
        boolean ctrl = FusionGuiSkin.isCtrlOrCmdHeld();
        if (ctrl && key == InputConstants.KEY_F && this.activeChapter != Chapter.OVERVIEW) {
            this.searchFocused = true;
            this.searchSelectAll = !this.searchQuery.isEmpty();
            this.searchCaret = this.searchQuery.length();
            return true;
        }
        if (this.searchFocused) {
            if (ctrl && key == InputConstants.KEY_A) {
                this.searchSelectAll = !this.searchQuery.isEmpty();
                this.searchCaret = this.searchQuery.length();
                return true;
            }
            if (ctrl && key == InputConstants.KEY_C) {
                if (this.minecraft != null && !this.searchQuery.isEmpty()) {
                    this.minecraft.keyboardHandler.setClipboard(this.searchQuery);
                }
                return true;
            }
            if (ctrl && key == InputConstants.KEY_V) {
                if (this.minecraft != null) {
                    String clip = sanitizeSearchPaste(this.minecraft.keyboardHandler.getClipboard());
                    if (!clip.isEmpty()) {
                        insertAtSearchCaret(clip);
                    }
                }
                return true;
            }
            if (key == InputConstants.KEY_LEFT) {
                this.searchSelectAll = false;
                if (this.searchCaret > 0) this.searchCaret = this.searchQuery.offsetByCodePoints(this.searchCaret, -1);
                return true;
            }
            if (key == InputConstants.KEY_RIGHT) {
                this.searchSelectAll = false;
                if (this.searchCaret < this.searchQuery.length()) this.searchCaret = this.searchQuery.offsetByCodePoints(this.searchCaret, 1);
                return true;
            }
            if (key == InputConstants.KEY_HOME) {
                this.searchSelectAll = false;
                this.searchCaret = 0;
                return true;
            }
            if (key == InputConstants.KEY_END) {
                this.searchSelectAll = false;
                this.searchCaret = this.searchQuery.length();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (this.searchSelectAll) {
                    updateSearchText("", 0);
                } else if (this.searchCaret > 0 && !this.searchQuery.isEmpty()) {
                    int start = this.searchQuery.offsetByCodePoints(this.searchCaret, -1);
                    String next = this.searchQuery.substring(0, start) + this.searchQuery.substring(this.searchCaret);
                    updateSearchText(next, start);
                }
                return true;
            }
            if (key == InputConstants.KEY_DELETE) {
                if (this.searchSelectAll) {
                    updateSearchText("", 0);
                } else if (this.searchCaret < this.searchQuery.length()) {
                    int end = this.searchQuery.offsetByCodePoints(this.searchCaret, 1);
                    String next = this.searchQuery.substring(0, this.searchCaret) + this.searchQuery.substring(end);
                    updateSearchText(next, this.searchCaret);
                }
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                this.searchFocused = false;
                this.searchSelectAll = false;
                return true;
            }
            return true;
        }

        // TXT-9: Keyboard navigation when search input is not focused
        if (key == InputConstants.KEY_TAB) {
            Chapter[] chapters = Chapter.values();
            int step = FusionGuiSkin.isShiftHeld() ? -1 : 1;
            int nextIdx = (this.activeChapter.ordinal() + step + chapters.length) % chapters.length;
            this.activeChapter = chapters[nextIdx];
            this.listScroll = 0;
            this.selectedIndex = 0;
            this.detailScroll = 0;
            invalidateEntryCache();
            playPageTurn(1.05F);
            return true;
        }
        List<EntryItem> filtered = getOrFilterEntries(getOrBuildChapterEntries());
        int visibleRows = visibleRowCount();
        if ((key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) && !filtered.isEmpty()) {
            int delta = key == InputConstants.KEY_UP ? -1 : 1;
            this.selectedIndex = Math.max(0, Math.min(filtered.size() - 1, this.selectedIndex + delta));
            this.detailScroll = 0;
            if (this.selectedIndex < this.listScroll) {
                this.listScroll = this.selectedIndex;
            } else if (this.selectedIndex >= this.listScroll + visibleRows) {
                this.listScroll = this.selectedIndex - visibleRows + 1;
            }
            playPageTurn(1.12F);
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            int delta = key == InputConstants.KEY_PAGEUP ? -1 : 1;
            if (this.cachedDetailMaxScroll > 0) {
                this.detailScroll = Math.max(0, Math.min(this.cachedDetailMaxScroll, this.detailScroll + delta * 3));
                return true;
            }
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            if (maxScroll > 0) {
                this.listScroll = Math.max(0, Math.min(maxScroll, this.listScroll + delta * visibleRows));
                playPageTurn(1.0F);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchFocused) {
            int cp = event.codepoint();
            if (Character.isValidCodePoint(cp) && cp >= 32 && cp != 127
                && !(cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)) {
                insertAtSearchCaret(Character.toString(cp));
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void insertAtSearchCaret(String text) {
        String base = this.searchSelectAll ? "" : this.searchQuery;
        int pos = this.searchSelectAll ? 0 : Math.max(0, Math.min(this.searchCaret, base.length()));
        int room = MAX_SEARCH_LEN - base.length();
        if (room <= 0) {
            return;
        }
        int end = Math.min(text.length(), room);
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        String chunk = text.substring(0, end);
        String next = base.substring(0, pos) + chunk + base.substring(pos);
        updateSearchText(next, pos + chunk.length());
    }

    private void updateSearchText(String next, int nextCaret) {
        this.searchQuery = next;
        this.searchCaret = Math.max(0, Math.min(nextCaret, next.length()));
        this.searchSelectAll = false;
        this.listScroll = 0;
        this.selectedIndex = 0;
        this.detailScroll = 0;
        this.cachedFilterQuery = null;
    }

    private static String sanitizeSearchPaste(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 32 || cp == 127 || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)) continue;
            if (sb.length() + Character.charCount(cp) > MAX_SEARCH_LEN) break;
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    private @Nullable HolderLookup.Provider currentRegistries() {
        return this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.registryAccess()
            : null;
    }

    private static void playPageTurn(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch));
    }
}
