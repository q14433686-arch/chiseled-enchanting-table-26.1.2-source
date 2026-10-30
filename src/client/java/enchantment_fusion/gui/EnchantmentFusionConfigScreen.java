package enchantment_fusion.gui;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.config.ConfigCatalog;
import enchantment_fusion.config.ConfigCatalog.Category;
import enchantment_fusion.config.ConfigCatalog.FieldSpec;
import enchantment_fusion.config.ConfigCatalog.ValueKind;
import enchantment_fusion.config.ModConfigNetworking;
import enchantment_fusion.config.ModConfigNetworking.UpdateRuntimeConfigPayload;
import enchantment_fusion.config.ModRuntimeConfig;
import enchantment_fusion.gui.FusionGuiSkin.HitBox;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Pixel-art In-Game Configuration Screen modeled after sister mod {@code Chiseled Enchanting Table (纂刻附魔台)}.
 * Supports both standalone ModMenu / {@code /ef config} launch and lossless embedded overlay inside
 * {@link EnchantmentFusionScreen} without closing the container menu or interrupting active rituals.
 */
public final class EnchantmentFusionConfigScreen extends Screen {
    public static final int PANEL_W = 344;
    public static final int PANEL_H = 216;
    private static final int TAB_W = 76;
    private static final int TAB_H = 18;
    private static final int LIST_X = 12;
    private static final int LIST_Y = 42;
    private static final int LIST_W = PANEL_W - 24;
    private static final int ROW_H = 22;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_H = VISIBLE_ROWS * ROW_H;
    private static final int MAX_SEARCH_LEN = 40;
    private static final AtomicLong REQUEST_SEQ = new AtomicLong(1L);

    private record RowModel(
        @Nullable FieldSpec field,
        @Nullable String entryId,
        boolean isSignatureEntry,
        String label,
        String subtitle,
        String searchBlob
    ) {}

    private final @Nullable Screen parentScreen;
    private final @Nullable Runnable closeOverlayCallback;
    private final ModRuntimeConfig.Draft draft;

    private Category activeCategory = Category.SYSTEM;
    private int panelLeft;
    private int panelTop;
    private int scrollOffset = 0;
    private boolean onlyDisabledEntries = false;

    private String searchQuery = "";
    private int searchCaret = 0;
    private boolean searchFocused = false;
    private boolean searchSelectAll = false;

    private @Nullable Category cachedCategory = null;
    private String cachedFilterKey = null;
    private List<RowModel> cachedRows = List.of();
    private @Nullable FieldSpec draggingField = null;
    private int dragSliderX = 0;
    private int dragSliderW = 1;

    public EnchantmentFusionConfigScreen(@Nullable Screen parentScreen) {
        this(parentScreen, null);
    }

    public EnchantmentFusionConfigScreen(@Nullable Screen parentScreen, @Nullable Runnable closeOverlayCallback) {
        super(Component.translatable("gui.enchantment_fusion.config.title"));
        this.parentScreen = parentScreen;
        this.closeOverlayCallback = closeOverlayCallback;
        this.draft = new ModRuntimeConfig.Draft(ModRuntimeConfig.clientView());
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_W) / 2;
        this.panelTop = (this.height - PANEL_H) / 2 + 6;
    }

    @Override
    public boolean isPauseScreen() {
        return this.closeOverlayCallback == null;
    }

    @Override
    public void onClose() {
        if (this.closeOverlayCallback != null) {
            this.closeOverlayCallback.run();
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private boolean isEditable() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return true;
        }
        return ModConfigNetworking.clientCanEdit();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        int px = this.panelLeft;
        int py = this.panelTop;
        boolean editable = isEditable();

        // Top 4 Category Tabs
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            boolean active = cats[i] == this.activeCategory;
            HitBox tb = new HitBox(px + 10 + i * (TAB_W + 4), py - 15, TAB_W, TAB_H);
            boolean hov = tb.contains(mouseX, mouseY);
            var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hov ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
            FusionGuiSkin.drawSprite(ctx, sprite, tb.x(), tb.y(), tb.w(), tb.h() + (active ? 2 : 0));
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, Component.translatable(cats[i].titleKey()).getString(),
                tb.x() + 4, tb.w() - 8, tb.y() + 9.5F,
                FusionGuiSkin.SCALE_SMALL, active ? FusionGuiSkin.COLOR_GOLD : (hov ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
            );
        }

        // Close [X] button
        HitBox closeBtn = new HitBox(px + PANEL_W - 24, py - 14, 18, 15);
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, closeBtn, true, closeBtn.contains(mouseX, mouseY),
            "\u2715", FusionGuiSkin.SCALE_LABEL, FusionGuiSkin.COLOR_CRIMSON
        );

        // Main Obsidian Frame + Inset Viewport
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, px, py, PANEL_W, PANEL_H);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_DRAWER_PANEL, px + LIST_X - 2, py + LIST_Y - 2, LIST_W + 4, LIST_H + 4);

        // Header Title + Search Box + Optional Entries Filter Pills
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.config.title").getString(),
            px + 12, py + 9, 132, FusionGuiSkin.SCALE_LABEL, FusionGuiSkin.COLOR_GOLD
        );

        int searchW = this.activeCategory == Category.ENTRIES ? 114 : 182;
        HitBox searchBox = new HitBox(px + 150, py + 7, searchW, 14);
        FusionGuiSkin.drawNineSlice(
            ctx,
            this.searchFocused ? FusionGuiSkin.SLICE_ROW_SELECTED_B : FusionGuiSkin.SLICE_ROW_UNSELECTED,
            searchBox.x(), searchBox.y(), searchBox.w(), searchBox.h()
        );
        String searchDisplay = this.searchQuery.isEmpty()
            ? (this.searchFocused ? "|" : Component.translatable("gui.enchantment_fusion.grimoire.search_hint").getString())
            : (this.searchFocused
                ? this.searchQuery.substring(0, Math.min(this.searchCaret, this.searchQuery.length())) + "|"
                    + this.searchQuery.substring(Math.min(this.searchCaret, this.searchQuery.length()))
                : this.searchQuery);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, "\u2315 " + searchDisplay,
            searchBox.x() + 5, searchBox.y() + 4, searchBox.w() - 10, FusionGuiSkin.SCALE_SMALL,
            this.searchQuery.isEmpty() && !this.searchFocused ? FusionGuiSkin.COLOR_MUTED : FusionGuiSkin.COLOR_CREAM
        );

        if (this.activeCategory == Category.ENTRIES) {
            HitBox filterBtn = new HitBox(px + 268, py + 7, 64, 14);
            String fText = Component.translatable(
                this.onlyDisabledEntries
                    ? "gui.enchantment_fusion.config.filter_disabled"
                    : "gui.enchantment_fusion.config.filter_all"
            ).getString();
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, 0, 0, filterBtn, true, filterBtn.contains(mouseX, mouseY),
                fText, FusionGuiSkin.SCALE_MICRO,
                this.onlyDisabledEntries ? FusionGuiSkin.COLOR_WARN : FusionGuiSkin.COLOR_CYAN
            );
        }

        // Subtitle / Sync status strip
        String syncNote = !editable
            ? Component.translatable("gui.enchantment_fusion.config.status.readonly").getString()
            : Component.translatable(
                "gui.enchantment_fusion.config.status.summary",
                this.draft.disabledSignatures.size(),
                this.draft.disabledTransmutations.size()
            ).getString();
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, syncNote, px + 12, py + 26, PANEL_W - 24, FusionGuiSkin.SCALE_MICRO,
            editable ? FusionGuiSkin.COLOR_MUTED : FusionGuiSkin.COLOR_WARN
        );

        // Scissored Row List
        List<RowModel> rows = getVisibleRows();
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, maxScroll);
        String hoveredTooltip = null;

        ctx.enableScissor(px + LIST_X, py + LIST_Y, px + LIST_X + LIST_W, py + LIST_Y + LIST_H);
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = this.scrollOffset + r;
            if (idx >= rows.size()) {
                break;
            }
            RowModel row = rows.get(idx);
            int rx = px + LIST_X;
            int ry = py + LIST_Y + r * ROW_H;
            HitBox rowBox = new HitBox(rx, ry, LIST_W, ROW_H - 1);
            boolean hov = rowBox.contains(mouseX, mouseY);
            FusionGuiSkin.drawNineSlice(
                ctx, hov ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED,
                rowBox.x(), rowBox.y(), rowBox.w(), rowBox.h()
            );
            if (hov && !row.subtitle().isEmpty()) {
                hoveredTooltip = row.subtitle();
            }
            renderRowContent(ctx, row, rx, ry, LIST_W, editable, mouseX, mouseY);
        }
        ctx.disableScissor();

        // Bottom Controls: Scroll [◀ X/Y ▶], [Reset Defaults], [Cancel], [Save & Apply]
        int footerY = py + PANEL_H - 28;
        if (maxScroll > 0) {
            HitBox prevB = new HitBox(px + 12, footerY, 22, 16);
            HitBox nextB = new HitBox(px + 74, footerY, 22, 16);
            FusionGuiSkin.drawButtonBox(ctx, this.font, 0, 0, prevB, this.scrollOffset > 0, prevB.contains(mouseX, mouseY), "\u25C0", FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, (this.scrollOffset + 1) + "/" + (maxScroll + 1),
                px + 35, 38, footerY + 8.0F, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
            );
            FusionGuiSkin.drawButtonBox(ctx, this.font, 0, 0, nextB, this.scrollOffset < maxScroll, nextB.contains(mouseX, mouseY), "\u25B6", FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD);
        }

        HitBox resetBtn = new HitBox(px + 104, footerY, 72, 16);
        HitBox cancelBtn = new HitBox(px + 180, footerY, 64, 16);
        HitBox saveBtn = new HitBox(px + 248, footerY, 84, 16);

        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, resetBtn, editable, resetBtn.contains(mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.config.btn.reset").getString(),
            FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_WARN
        );
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, cancelBtn, true, cancelBtn.contains(mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.config.btn.cancel").getString(),
            FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CREAM
        );
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, saveBtn, editable, saveBtn.contains(mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.config.btn.save").getString(),
            FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GREEN
        );

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (hoveredTooltip != null && !hoveredTooltip.isBlank()) {
            ctx.setTooltipForNextFrame(this.font, List.of(Component.literal(hoveredTooltip).getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void renderRowContent(
        GuiGraphicsExtractor ctx,
        RowModel row,
        int rx,
        int ry,
        int rw,
        boolean editable,
        int mouseX,
        int mouseY
    ) {
        FusionGuiSkin.drawLeftBoundedText(ctx, this.font, row.label(), rx + 6, ry + 4, rw - 156, FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_CREAM);
        FusionGuiSkin.drawLeftBoundedText(ctx, this.font, row.subtitle(), rx + 6, ry + 13, rw - 156, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED);

        FieldSpec f = row.field();
        if (f == null && row.entryId() != null) {
            boolean enabled = row.isSignatureEntry()
                ? !this.draft.disabledSignatures.contains(row.entryId())
                : !this.draft.disabledTransmutations.contains(row.entryId());
            renderToggleControl(ctx, rx + rw - 74, ry + 3, 68, 15, enabled, editable, mouseX, mouseY);
            return;
        }
        if (f == null) {
            return;
        }
        if (f.kind() == ValueKind.BOOLEAN) {
            boolean on = f.boolGetter().apply(this.draft);
            renderToggleControl(ctx, rx + rw - 74, ry + 3, 68, 15, on, editable, mouseX, mouseY);
            return;
        }

        // Numeric / Percent Conduit Slider + [-][+][↺]
        int minusX = rx + rw - 146;
        int sliderX = minusX + 16;
        int sliderW = 74;
        int plusX = sliderX + sliderW + 2;
        int resetX = plusX + 16;

        HitBox minusBtn = new HitBox(minusX, ry + 3, 14, 15);
        HitBox plusBtn = new HitBox(plusX, ry + 3, 14, 15);
        HitBox rstBtn = new HitBox(resetX, ry + 3, 14, 15);

        FusionGuiSkin.drawButtonBox(ctx, this.font, 0, 0, minusBtn, editable, minusBtn.contains(mouseX, mouseY), "-", FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_GOLD);
        double ratio = f.normalized(this.draft);
        FusionGuiSkin.drawConduitH(
            ctx,
            f.kind() == ValueKind.PERCENT ? FusionGuiSkin.CONDUIT_AMETHYST : FusionGuiSkin.CONDUIT_CYAN,
            sliderX, ry + 6, sliderW, 9, (float) ratio, false
        );
        int knobX = sliderX + Math.clamp((int) Math.round(ratio * (sliderW - 8)), 0, sliderW - 8);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.VESSEL_FILLED, knobX, ry + 6, 8, 8);
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, f.formatValue(this.draft),
            sliderX + 2, sliderW - 4, ry + 10.5F, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GOLD
        );

        FusionGuiSkin.drawButtonBox(ctx, this.font, 0, 0, plusBtn, editable, plusBtn.contains(mouseX, mouseY), "+", FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_GOLD);
        boolean modified = !f.isDefault(this.draft);
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, rstBtn, editable && modified, rstBtn.contains(mouseX, mouseY),
            "\u21BA", FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_WARN
        );
    }

    private void renderToggleControl(
        GuiGraphicsExtractor ctx,
        int x,
        int y,
        int w,
        int h,
        boolean on,
        boolean editable,
        int mouseX,
        int mouseY
    ) {
        HitBox box = new HitBox(x, y, w, h);
        String text = Component.translatable(
            on ? "gui.enchantment_fusion.config.state_on" : "gui.enchantment_fusion.config.state_off"
        ).getString();
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, 0, 0, box, editable, box.contains(mouseX, mouseY),
            text, FusionGuiSkin.SCALE_SMALL, on ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_CRIMSON
        );
        FusionGuiSkin.drawSprite(ctx, on ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY, x + 5, y + 4, 7, 7);
    }

    private List<RowModel> getVisibleRows() {
        String filterKey = this.activeCategory.name() + "|" + this.onlyDisabledEntries + "|"
            + this.searchQuery.trim().toLowerCase(Locale.ROOT)
            + (this.onlyDisabledEntries ? ("|" + this.draft.disabledSignatures.size() + ":" + this.draft.disabledTransmutations.size()) : "");
        if (this.cachedCategory == this.activeCategory && filterKey.equals(this.cachedFilterKey)) {
            return this.cachedRows;
        }
        String q = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<RowModel> built = new ArrayList<>();
        if (this.activeCategory != Category.ENTRIES) {
            for (FieldSpec f : ConfigCatalog.fieldsFor(this.activeCategory)) {
                String label = Component.translatable(f.labelKey()).getString();
                String sub = Component.translatable(f.descKey()).getString();
                String blob = (f.id() + " " + label + " " + sub).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || blob.contains(q)) {
                    built.add(new RowModel(f, null, false, label, sub, blob));
                }
            }
        } else {
            for (SignatureEntry sig : SignatureCatalog.allUniqueSignatures()) {
                boolean disabled = this.draft.disabledSignatures.contains(sig.id());
                if (this.onlyDisabledEntries && !disabled) {
                    continue;
                }
                String name = Component.translatable(sig.titleKey()).getString();
                String desc = Component.translatable(sig.descKey()).getString();
                String sub = "[" + shortId(sig.primaryId()) + " \u25B7 " + shortId(sig.secondaryId()) + "] " + desc;
                String blob = (sig.id() + " " + name + " " + sub).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || blob.contains(q)) {
                    built.add(new RowModel(null, sig.id(), true, "\u2726 " + name, sub, blob));
                }
            }
            for (TransmuteVariant tv : TransmuteCatalog.allVariants()) {
                boolean disabled = this.draft.disabledTransmutations.contains(tv.id());
                if (this.onlyDisabledEntries && !disabled) {
                    continue;
                }
                String name = Component.translatable(tv.titleKey()).getString();
                String desc = Component.translatable(tv.descKey()).getString();
                String sub = "[" + shortId(tv.sourceEnchantId()) + "] " + desc;
                String blob = (tv.id() + " " + name + " " + sub).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || blob.contains(q)) {
                    built.add(new RowModel(null, tv.id(), false, "\u25C8 " + name, sub, blob));
                }
            }
        }
        this.cachedCategory = this.activeCategory;
        this.cachedFilterKey = filterKey;
        this.cachedRows = built;
        return built;
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private void invalidateCache() {
        this.cachedFilterKey = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, isDoubleClick);
        }
        double mx = event.x();
        double my = event.y();
        int px = this.panelLeft;
        int py = this.panelTop;
        boolean editable = isEditable();

        int searchW = this.activeCategory == Category.ENTRIES ? 114 : 182;
        if (new HitBox(px + 150, py + 7, searchW, 14).contains(mx, my)) {
            this.searchFocused = true;
            this.searchCaret = this.searchQuery.length();
            this.searchSelectAll = false;
            return true;
        }
        this.searchFocused = false;
        this.searchSelectAll = false;

        if (this.activeCategory == Category.ENTRIES && new HitBox(px + 268, py + 7, 64, 14).contains(mx, my)) {
            this.onlyDisabledEntries = !this.onlyDisabledEntries;
            this.scrollOffset = 0;
            invalidateCache();
            playClick(1.05F);
            return true;
        }

        if (new HitBox(px + PANEL_W - 24, py - 14, 18, 15).contains(mx, my)) {
            playClick(0.95F);
            this.onClose();
            return true;
        }

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (new HitBox(px + 10 + i * (TAB_W + 4), py - 15, TAB_W, TAB_H).contains(mx, my)) {
                this.activeCategory = cats[i];
                this.scrollOffset = 0;
                invalidateCache();
                playClick(1.06F);
                return true;
            }
        }

        List<RowModel> rows = getVisibleRows();
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        int footerY = py + PANEL_H - 28;
        if (maxScroll > 0) {
            if (this.scrollOffset > 0 && new HitBox(px + 12, footerY, 22, 16).contains(mx, my)) {
                this.scrollOffset = Math.max(0, this.scrollOffset - VISIBLE_ROWS);
                playClick(0.98F);
                return true;
            }
            if (this.scrollOffset < maxScroll && new HitBox(px + 74, footerY, 22, 16).contains(mx, my)) {
                this.scrollOffset = Math.min(maxScroll, this.scrollOffset + VISIBLE_ROWS);
                playClick(1.02F);
                return true;
            }
        }

        if (editable && new HitBox(px + 104, footerY, 72, 16).contains(mx, my)) {
            this.draft.copyFrom(ModRuntimeConfig.Snapshot.DEFAULT);
            invalidateCache();
            playClick(0.92F);
            return true;
        }
        if (new HitBox(px + 180, footerY, 64, 16).contains(mx, my)) {
            playClick(0.95F);
            this.onClose();
            return true;
        }
        if (editable && new HitBox(px + 248, footerY, 84, 16).contains(mx, my)) {
            commitSaveAndClose();
            return true;
        }

        if (editable && new HitBox(px + LIST_X, py + LIST_Y, LIST_W, LIST_H).contains(mx, my)) {
            int rowIdx = this.scrollOffset + (int) ((my - (py + LIST_Y)) / ROW_H);
            if (rowIdx >= 0 && rowIdx < rows.size()) {
                int ry = py + LIST_Y + (rowIdx - this.scrollOffset) * ROW_H;
                if (handleRowClick(rows.get(rowIdx), px + LIST_X, ry, LIST_W, mx, my)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private boolean handleRowClick(RowModel row, int rx, int ry, int rw, double mx, double my) {
        FieldSpec f = row.field();
        if (f == null && row.entryId() != null) {
            if (new HitBox(rx + rw - 74, ry + 3, 68, 15).contains(mx, my)) {
                var targetSet = row.isSignatureEntry() ? this.draft.disabledSignatures : this.draft.disabledTransmutations;
                if (!targetSet.remove(row.entryId())) {
                    targetSet.add(row.entryId());
                }
                invalidateCache();
                playClick(1.08F);
                return true;
            }
            return false;
        }
        if (f == null) {
            return false;
        }
        if (f.kind() == ValueKind.BOOLEAN) {
            if (new HitBox(rx + rw - 74, ry + 3, 68, 15).contains(mx, my)) {
                f.boolSetter().accept(this.draft, !f.boolGetter().apply(this.draft));
                playClick(1.08F);
                return true;
            }
            return false;
        }
        int minusX = rx + rw - 146;
        int sliderX = minusX + 16;
        int sliderW = 74;
        int plusX = sliderX + sliderW + 2;
        int resetX = plusX + 16;

        if (new HitBox(minusX, ry + 3, 14, 15).contains(mx, my)) {
            stepNumericField(f, -1);
            playClick(0.96F);
            return true;
        }
        if (new HitBox(plusX, ry + 3, 14, 15).contains(mx, my)) {
            stepNumericField(f, 1);
            playClick(1.06F);
            return true;
        }
        if (!f.isDefault(this.draft) && new HitBox(resetX, ry + 3, 14, 15).contains(mx, my)) {
            f.resetField(this.draft);
            playClick(0.92F);
            return true;
        }
        if (new HitBox(sliderX, ry + 3, sliderW, 15).contains(mx, my)) {
            this.draggingField = f;
            this.dragSliderX = sliderX;
            this.dragSliderW = sliderW;
            f.applyNormalized(this.draft, (mx - sliderX) / (double) sliderW);
            playClick(1.02F);
            return true;
        }
        return false;
    }

    private void stepNumericField(FieldSpec f, int dir) {
        if (f.kind() == ValueKind.INT) {
            int step = Math.max(1, (int) Math.round(f.step()));
            int next = Math.clamp(f.intGetter().applyAsInt(this.draft) + dir * step, (int) f.min(), (int) f.max());
            f.intSetter().accept(this.draft, next);
        } else if (f.kind() == ValueKind.PERCENT) {
            double next = Math.clamp(f.doubleGetter().applyAsDouble(this.draft) + dir * f.step(), f.min(), f.max());
            f.doubleSetter().accept(this.draft, Math.round(next * 100.0) / 100.0);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingField != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT && isEditable()) {
            this.draggingField.applyNormalized(this.draft, (event.x() - this.dragSliderX) / (double) this.dragSliderW);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingField = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (delta != 0) {
            int maxScroll = Math.max(0, getVisibleRows().size() - VISIBLE_ROWS);
            this.scrollOffset = Math.clamp(this.scrollOffset + delta, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (this.searchFocused && !this.searchQuery.isEmpty()) {
                this.searchQuery = "";
                this.searchCaret = 0;
                invalidateCache();
                return true;
            }
            this.onClose();
            return true;
        }
        boolean ctrl = FusionGuiSkin.isCtrlOrCmdHeld();
        if (ctrl && key == InputConstants.KEY_F) {
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
            if (key == InputConstants.KEY_BACKSPACE) {
                if (this.searchSelectAll) {
                    this.searchQuery = "";
                    this.searchCaret = 0;
                    this.searchSelectAll = false;
                } else if (this.searchCaret > 0 && !this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchCaret - 1) + this.searchQuery.substring(this.searchCaret);
                    this.searchCaret--;
                }
                this.scrollOffset = 0;
                invalidateCache();
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                this.searchFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchFocused) {
            int cp = event.codepoint();
            if (cp >= 32 && cp <= 0xFFFF && cp != 127) {
                String base = this.searchSelectAll ? "" : this.searchQuery;
                if (base.length() < MAX_SEARCH_LEN) {
                    int pos = this.searchSelectAll ? 0 : Math.clamp(this.searchCaret, 0, base.length());
                    this.searchQuery = base.substring(0, pos) + Character.toString(cp) + base.substring(pos);
                    this.searchCaret = pos + 1;
                    this.searchSelectAll = false;
                    this.scrollOffset = 0;
                    invalidateCache();
                }
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void commitSaveAndClose() {
        ModRuntimeConfig.Snapshot updated = this.draft.toSnapshot();
        if (this.minecraft != null && this.minecraft.level != null && ClientPlayNetworking.canSend(UpdateRuntimeConfigPayload.TYPE)) {
            ModRuntimeConfig.setClientOverride(updated);
            ClientPlayNetworking.send(new UpdateRuntimeConfigPayload(
                ModConfigNetworking.snapshotToJsonString(updated),
                REQUEST_SEQ.getAndIncrement()
            ));
        } else {
            ModRuntimeConfig.applyAndSave(updated);
        }
        playClick(1.15F);
        this.onClose();
    }

    private static void playClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }
}
