package chiseled_enchanting_table.gui.config;

import chiseled_enchanting_table.config.ConfigCatalog;
import chiseled_enchanting_table.config.ConfigCatalog.CategoryId;
import chiseled_enchanting_table.config.ConfigCatalog.ControlKind;
import chiseled_enchanting_table.config.ConfigCatalog.EffectScope;
import chiseled_enchanting_table.config.ConfigCatalog.OptionAvailability;
import chiseled_enchanting_table.config.ConfigCatalog.OptionEntry;
import chiseled_enchanting_table.config.ConfigCatalog.SessionMode;
import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.grimoire.GrimoireTextLayout;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Built-in responsive in-game configuration screen supporting both standalone presentation
 * (ModMenu, {@code /cet config}, Arcane Grimoire) and lossless container-modal overlay rendering
 * directly inside {@link chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen}.
 */
@Environment(EnvType.CLIENT)
public class ChiseledEnchantingConfigScreen extends Screen {

    private static final int BG_DIM = 0xCA07050E;
    private static final int CHASSIS_OUTER = 0xFFD9B266;
    private static final int CHASSIS_INNER = 0xFF171124;
    private static final int HEADER_BG = 0xFF241838;
    private static final int RAIL_BG = 0xFF130E1F;
    private static final int CARD_BG = 0xFF211833;
    private static final int CARD_BG_HOVER = 0xFF2B2042;
    private static final int CARD_BG_DISABLED = 0xFF181325;
    private static final int GOLD_TEXT = 0xFFFFE599;
    private static final int CREAM_TEXT = 0xFFF3ECFF;
    private static final int MUTED_TEXT = 0xFF9C8FB5;

    private static final int ROW_H = 31;
    private static final int ROW_GAP = 4;

    private record HitBox(int x, int y, int w, int h, Runnable onClick, @Nullable Runnable onRightClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record SliderTrack(OptionEntry entry, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final Runnable closeOverride;

    private final SessionMode sessionMode;
    private ModRuntimeConfig baselineConfig;
    private Draft draft;

    private CategoryId activeCategory = CategoryId.GENERAL;
    private boolean filterModifiedOnly = false;
    private float scrollOffset = 0.0f;
    private float maxScroll = 0.0f;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private final List<HitBox> hitBoxes = new ArrayList<>();
    private final List<SliderTrack> sliderTracks = new ArrayList<>();
    @Nullable
    private SliderTrack activeDraggingSlider = null;
    private boolean draggingScrollbar = false;
    private long pendingSaveId;
    private boolean pendingClose;
    private boolean lastSaveFailed;
    private long statusToastUntilMs = 0L;

    public ChiseledEnchantingConfigScreen(@Nullable Screen parentScreen) {
        this(parentScreen, null);
    }

    public ChiseledEnchantingConfigScreen(@Nullable Screen parentScreen, @Nullable Runnable closeOverride) {
        super(Component.translatable("config.chiseled_enchanting_table.title"));
        this.parentScreen = parentScreen;
        this.closeOverride = closeOverride;
        this.sessionMode = detectSessionMode();
        this.baselineConfig = ModRuntimeConfig.preferences();
        this.draft = this.baselineConfig.toDraft();
    }

    public void initOverlay(Minecraft mc, int fullWidth, int fullHeight) {
        this.width = fullWidth;
        this.height = fullHeight;
        this.init();
    }

    public Rect2i fullGuiBounds() {
        return new Rect2i(this.panelX, this.panelY, this.panelW, this.panelH);
    }

    private static SessionMode detectSessionMode() {
        if (ModRuntimeConfig.effectivePackPolicy().lockInGameConfig()) {
            return SessionMode.MODPACK_LOCKED;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return SessionMode.OFFLINE_CLIENT;
        }
        if (mc.hasSingleplayerServer()) {
            return SessionMode.SINGLEPLAYER_LIVE;
        }
        return ModRuntimeConfig.clientCanEditServerConfig()
            ? SessionMode.DEDICATED_ADMIN
            : SessionMode.DEDICATED_READONLY;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
    }

    private void computeLayout() {
        this.panelW = Mth.clamp(this.width - 24, 296, 444);
        this.panelH = Mth.clamp(this.height - 20, 184, 266);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int railW = Math.min(118, Math.max(94, this.panelW * 28 / 100));
        this.listX = this.panelX + railW + 8;
        this.listY = this.panelY + 46;
        this.listW = this.panelW - railW - 16;
        this.listH = this.panelH - 76;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (this.closeOverride != null) {
            this.closeOverride.run();
        } else if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parentScreen);
        }
    }

    private List<OptionEntry> visibleOptions() {
        if (this.filterModifiedOnly) {
            return ConfigCatalog.ALL_OPTIONS.stream()
                .filter(o -> o.isModifiedFromDefault(this.draft))
                .toList();
        }
        return ConfigCatalog.optionsInCategory(this.activeCategory);
    }

    private Draft displayedDraft(OptionEntry entry) {
        if (this.sessionMode == SessionMode.OFFLINE_CLIENT) return this.draft;
        var policy = ModRuntimeConfig.effectivePackPolicy();
        boolean governed = policy.isKeyLocked(entry.key())
            || (policy.datapackOverriddenKeys().contains(entry.key())
                && (!this.draft.overrideDatapacks || policy.enforceDatapackPriority()));
        return governed ? ModRuntimeConfig.effective().toDraft() : this.draft;
    }

    private String formatOptionValue(OptionEntry entry) {
        String raw = entry.rawOrKeyDisplay(displayedDraft(entry));
        return raw.startsWith("@") ? I18n.get(raw.substring(1)) : raw;
    }

    private String formatDefaultValue(OptionEntry entry) {
        Draft def = new Draft();
        String raw = entry.rawOrKeyDisplay(def);
        return raw.startsWith("@") ? I18n.get(raw.substring(1)) : raw;
    }

    private void playClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void saveAndApply(boolean closeAfterSave) {
        if (!this.sessionMode.writable()) {
            return;
        }
        if (this.pendingSaveId != 0) return;
        ModRuntimeConfig built = this.draft.build();
        playClick(1.12f);
        if (this.sessionMode == SessionMode.OFFLINE_CLIENT) {
            try {
                ModRuntimeConfig.setLocalAndSave(built);
                ChiseledEnchantingTableData.applyRuntimeConfig(built);
                this.baselineConfig = built;
                this.lastSaveFailed = false;
                this.statusToastUntilMs = System.currentTimeMillis() + 2600L;
                if (closeAfterSave) onClose();
            } catch (RuntimeException error) {
                this.lastSaveFailed = true;
                this.statusToastUntilMs = System.currentTimeMillis() + 5000L;
            }
            return;
        }
        // Integrated and dedicated servers share the same authorized, acknowledged path.
        this.pendingSaveId = ModRuntimeConfig.nextSaveRequestId();
        this.pendingClose = closeAfterSave;
        ClientPlayNetworking.send(new ModConfigNetworking.UpdateRuntimeConfigPayload(built.toJsonString(), this.pendingSaveId));
    }

    private void reconcileSaveAcknowledgement() {
        if (this.pendingSaveId == 0 || ModRuntimeConfig.acknowledgedSaveId() != this.pendingSaveId) return;
        this.pendingSaveId = 0;
        this.lastSaveFailed = !ModRuntimeConfig.acknowledgedSaveAccepted();
        this.statusToastUntilMs = System.currentTimeMillis() + 5000L;
        if (!this.lastSaveFailed) {
            this.baselineConfig = ModRuntimeConfig.preferences();
            this.draft = this.baselineConfig.toDraft();
            if (this.pendingClose) onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        // Drawn inside extractRenderState for zero double-blur artifacts.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        reconcileSaveAcknowledgement();
        computeLayout();
        this.hitBoxes.clear();
        this.sliderTracks.clear();
        List<Component> pendingTooltip = null;

        // Backdrop & outer gilded chassis
        ctx.fill(0, 0, this.width, this.height, BG_DIM);
        ctx.fill(this.panelX - 1, this.panelY - 1, this.panelX + this.panelW + 1, this.panelY + this.panelH + 1, CHASSIS_OUTER);
        ctx.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, CHASSIS_INNER);

        // Top Header Bar
        int headerH = 24;
        ctx.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + headerH, HEADER_BG);
        ctx.fill(this.panelX, this.panelY + headerH - 1, this.panelX + this.panelW, this.panelY + headerH, 0xFF4B3570);

        String sessionBadge = I18n.get(this.sessionMode.bannerKey());
        int badgeW = Math.min(156, Math.round(this.font.width(sessionBadge) * 0.72f) + 12);
        int badgeX = this.panelX + this.panelW - badgeW - 6;
        int badgeY = this.panelY + 4;
        int badgeCol = switch (this.sessionMode) {
            case SINGLEPLAYER_LIVE -> 0xFF1B5E42;
            case DEDICATED_ADMIN -> 0xFF6B4C1B;
            case OFFLINE_CLIENT -> 0xFF1E4C66;
            case DEDICATED_READONLY -> 0xFF662432;
            case MODPACK_LOCKED -> 0xFF4C2466;
        };
        ctx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, 0xFF8C6F39);
        ctx.fill(badgeX + 1, badgeY + 1, badgeX + badgeW - 1, badgeY + 15, badgeCol);
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, sessionBadge, badgeX + 3, badgeW - 6, badgeY + 8.0f, 0.70f, CREAM_TEXT, true
        );

        int maxTitleW = Math.max(80, badgeX - (this.panelX + 10));
        RowRenderSupport.renderCenteredYText(
            ctx, Minecraft.getInstance(), I18n.get("config.chiseled_enchanting_table.title"),
            this.panelX + 8, this.panelY + 12.0f, 0.86f, maxTitleW, GOLD_TEXT
        );

        // Left Category Rail
        int railX = this.panelX + 4;
        int railY = this.panelY + headerH + 4;
        int railW = this.listX - 4 - railX;
        int railH = this.panelH - headerH - 34;
        ctx.fill(railX, railY, railX + railW, railY + railH, RAIL_BG);

        int totalModified = (int) ConfigCatalog.ALL_OPTIONS.stream()
            .filter(o -> o.isModifiedFromDefault(this.draft))
            .count();

        // Filter button: [All / Modified (N)]
        int filterY = railY + 3;
        int filterH = 17;
        boolean overFilter = mouseX >= railX + 3 && mouseX < railX + railW - 3 && mouseY >= filterY && mouseY < filterY + filterH;
        int filterFill = this.filterModifiedOnly ? 0xFF5D3F92 : (overFilter ? 0xFF2E2249 : 0xFF211834);
        ctx.fill(railX + 3, filterY, railX + railW - 3, filterY + filterH, this.filterModifiedOnly ? GOLD_TEXT : 0xFF523E78);
        ctx.fill(railX + 4, filterY + 1, railX + railW - 4, filterY + filterH - 1, filterFill);
        String filterLabel = this.filterModifiedOnly
            ? I18n.get("config.chiseled_enchanting_table.filter.modified", totalModified)
            : I18n.get("config.chiseled_enchanting_table.filter.all", ConfigCatalog.ALL_OPTIONS.size());
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, filterLabel, railX + 6, railW - 12, filterY + filterH * 0.5f, 0.68f,
            this.filterModifiedOnly ? GOLD_TEXT : CREAM_TEXT, true
        );
        this.hitBoxes.add(new HitBox(railX + 3, filterY, railW - 6, filterH, () -> {
            this.filterModifiedOnly = !this.filterModifiedOnly;
            this.scrollOffset = 0.0f;
            playClick(1.0f);
        }, null));

        // 6 Category buttons
        CategoryId[] cats = CategoryId.values();
        int catStartY = filterY + filterH + 5;
        int availCatH = Math.max(96, railH - (catStartY - railY) - 4);
        int catStepY = availCatH / cats.length;
        int catBtnH = Math.max(15, Math.min(25, catStepY - 2));

        for (int i = 0; i < cats.length; i++) {
            CategoryId cat = cats[i];
            int cy = catStartY + i * catStepY;
            boolean selected = !this.filterModifiedOnly && this.activeCategory == cat;
            boolean over = mouseX >= railX + 3 && mouseX < railX + railW - 3 && mouseY >= cy && mouseY < cy + catBtnH;
            int modInCat = (int) ConfigCatalog.optionsInCategory(cat).stream()
                .filter(o -> o.isModifiedFromDefault(this.draft))
                .count();

            ctx.fill(railX + 3, cy, railX + railW - 3, cy + catBtnH, selected ? GOLD_TEXT : 0xFF392B56);
            ctx.fill(railX + 4, cy + 1, railX + railW - 4, cy + catBtnH - 1, selected ? 0xFF352454 : (over ? 0xFF281D3F : 0xFF1B142B));
            ctx.fill(railX + 4, cy + 1, railX + 7, cy + catBtnH - 1, cat.accentColor());

            String catTitle = I18n.get(cat.titleKey()) + (modInCat > 0 ? (" \u2605" + modInCat) : "");
            RowRenderSupport.renderCenteredYText(
                ctx, Minecraft.getInstance(), catTitle,
                railX + 10, cy + catBtnH * 0.5f, 0.72f, railW - 16,
                selected ? GOLD_TEXT : CREAM_TEXT
            );
            final CategoryId chosen = cat;
            this.hitBoxes.add(new HitBox(railX + 3, cy, railW - 6, catBtnH, () -> {
                this.filterModifiedOnly = false;
                this.activeCategory = chosen;
                this.scrollOffset = 0.0f;
                playClick(1.0f);
            }, null));
            if (over) {
                pendingTooltip = List.of(
                    Component.translatable(cat.titleKey()).withStyle(style -> style.withColor(0xFFFFAA00)),
                    Component.translatable(cat.subtitleKey()).withStyle(style -> style.withColor(0xFFAAAAAA))
                );
            }
        }

        // Category Banner + Category Reset Button
        int subY = this.panelY + headerH + 4;
        int resetCatW = 68;
        int resetCatX = this.listX + this.listW - resetCatW;
        String subHeader = this.filterModifiedOnly
            ? I18n.get("config.chiseled_enchanting_table.filter.modified_desc")
            : I18n.get(this.activeCategory.subtitleKey());
        RowRenderSupport.renderCenteredYText(
            ctx, Minecraft.getInstance(), subHeader,
            this.listX, subY + 8.0f, 0.70f, Math.max(60, this.listW - resetCatW - 6), 0xFFD9CCFA
        );

        List<OptionEntry> visible = visibleOptions();
        boolean anyModInView = visible.stream().anyMatch(o -> o.isModifiedFromDefault(this.draft));
        boolean canResetPage = this.sessionMode.writable() && anyModInView;
        boolean overResetCat = canResetPage
            && mouseX >= resetCatX && mouseX < resetCatX + resetCatW
            && mouseY >= subY && mouseY < subY + 16;
        ctx.fill(resetCatX, subY, resetCatX + resetCatW, subY + 16, canResetPage ? (overResetCat ? GOLD_TEXT : 0xFF7B63A6) : 0xFF342A47);
        ctx.fill(resetCatX + 1, subY + 1, resetCatX + resetCatW - 1, subY + 15, overResetCat ? 0xFF422E68 : 0xFF211834);
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, I18n.get("config.chiseled_enchanting_table.btn.reset_page"),
            resetCatX + 3, resetCatW - 6, subY + 8.0f, 0.66f,
            canResetPage ? CREAM_TEXT : 0xFF6B607E, true
        );
        if (canResetPage) {
            this.hitBoxes.add(new HitBox(resetCatX, subY, resetCatW, 16, () -> {
                for (OptionEntry entry : new ArrayList<>(visible)) {
                    entry.resetToDefault(this.draft);
                }
                playClick(0.92f);
            }, null));
        }

        // Scrollable Option Cards Area
        int contentH = Math.max(0, visible.size() * (ROW_H + ROW_GAP) - ROW_GAP);
        this.maxScroll = Math.max(0.0f, contentH - this.listH);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0f, this.maxScroll);

        int innerCardW = this.maxScroll > 0.5f ? (this.listW - 7) : this.listW;
        ctx.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
        try {
            if (visible.isEmpty()) {
                GrimoireTextLayout.drawCenteredBox(
                    ctx, this.font, I18n.get("config.chiseled_enchanting_table.empty_modified"),
                    this.listX + 8, this.listW - 16, this.listY + this.listH * 0.5f, 0.78f, MUTED_TEXT, true
                );
            }
            for (int idx = 0; idx < visible.size(); idx++) {
                OptionEntry entry = visible.get(idx);
                int ry = Math.round(this.listY + idx * (ROW_H + ROW_GAP) - this.scrollOffset);
                if (ry + ROW_H < this.listY || ry > this.listY + this.listH) {
                    continue;
                }
                boolean inClipMouse = mouseY >= this.listY && mouseY < this.listY + this.listH;
                boolean rowHover = inClipMouse
                    && mouseX >= this.listX && mouseX < this.listX + innerCardW
                    && mouseY >= ry && mouseY < ry + ROW_H;

                OptionAvailability avail = entry.evaluate(this.draft, this.sessionMode);
                int cardFill = !avail.parentApplicable()
                    ? CARD_BG_DISABLED
                    : (rowHover ? CARD_BG_HOVER : CARD_BG);
                int borderCol = avail.modifiedFromDefault()
                    ? 0xFFBA9244
                    : (avail.parentApplicable() ? 0xFF3E2E5C : 0xFF281F3B);

                ctx.fill(this.listX, ry, this.listX + innerCardW, ry + ROW_H, borderCol);
                ctx.fill(this.listX + 1, ry + 1, this.listX + innerCardW - 1, ry + ROW_H - 1, cardFill);

                int resetBtnW = 14;
                int ctrlW = Math.min(120, Math.max(92, innerCardW * 38 / 100));
                int resetX = this.listX + innerCardW - resetBtnW - 4;
                int ctrlX = resetX - ctrlW - 4;
                int ctrlY = ry + 7;
                int ctrlH = 17;

                // Option Title + Scope / Applicability Subtitle
                int textMaxW = Math.max(52, ctrlX - (this.listX + 8));
                String titlePrefix = avail.modifiedFromDefault() ? "\u2605 " : "";
                int titleColor = avail.parentApplicable() ? (avail.modifiedFromDefault() ? GOLD_TEXT : CREAM_TEXT) : 0xFF817599;
                RowRenderSupport.renderCenteredYText(
                    ctx, Minecraft.getInstance(), titlePrefix + I18n.get(entry.nameKey()),
                    this.listX + 6, ry + 9.5f, 0.76f, textMaxW, titleColor
                );

                String subLine;
                int subColor;
                if (!avail.parentApplicable() && avail.inactiveReasonKey() != null) {
                    subLine = "\u26a0 " + I18n.get(avail.inactiveReasonKey());
                    subColor = 0xFFE6B566;
                } else {
                    subLine = "[" + I18n.get(avail.effectScope().badgeKey()) + "] " + I18n.get(entry.descKey());
                    subColor = avail.effectScope() == EffectScope.IMMEDIATE ? MUTED_TEXT : avail.effectScope().color();
                }
                RowRenderSupport.renderCenteredYText(
                    ctx, Minecraft.getInstance(), subLine,
                    this.listX + 6, ry + 22.0f, 0.64f, textMaxW, subColor
                );

                // Control Widget (Boolean / Enum / Slider with - & + step buttons)
                renderOptionControl(ctx, entry, avail, ctrlX, ctrlY, ctrlW, ctrlH, mouseX, mouseY, inClipMouse);

                // Per-option Reset button [↺]
                boolean canResetRow = this.sessionMode.writable() && avail.modifiedFromDefault();
                boolean overResetRow = inClipMouse && canResetRow
                    && mouseX >= resetX && mouseX < resetX + resetBtnW
                    && mouseY >= ctrlY && mouseY < ctrlY + ctrlH;
                ctx.fill(resetX, ctrlY, resetX + resetBtnW, ctrlY + ctrlH, canResetRow ? (overResetRow ? GOLD_TEXT : 0xFF7B63A6) : 0xFF2B223D);
                ctx.fill(resetX + 1, ctrlY + 1, resetX + resetBtnW - 1, ctrlY + ctrlH - 1, overResetRow ? 0xFF4B3378 : 0xFF1B1429);
                GrimoireTextLayout.drawCenteredBox(
                    ctx, this.font, "\u21ba", resetX + 1, resetBtnW - 2, ctrlY + ctrlH * 0.5f, 0.72f,
                    canResetRow ? GOLD_TEXT : 0xFF564B6B, false
                );
                if (canResetRow && inClipMouse) {
                    this.hitBoxes.add(new HitBox(resetX, ctrlY, resetBtnW, ctrlH, () -> {
                        entry.resetToDefault(this.draft);
                        playClick(0.92f);
                    }, null));
                }

                if (rowHover) {
                    pendingTooltip = buildOptionTooltip(entry, avail);
                }
            }
        } finally {
            ctx.disableScissor();
        }

        // Scrollbar
        if (this.maxScroll > 0.5f) {
            int sbX = this.listX + this.listW - 4;
            ctx.fill(sbX, this.listY, sbX + 4, this.listY + this.listH, 0xFF120D1C);
            int thumbH = Math.max(18, Math.round((float) this.listH * this.listH / (this.listH + this.maxScroll)));
            int thumbY = this.listY + Math.round((this.scrollOffset / this.maxScroll) * (this.listH - thumbH));
            ctx.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, this.draggingScrollbar ? GOLD_TEXT : 0xFF8C72BD);
        }

        // Bottom Footer Bar
        renderFooterBar(ctx, mouseX, mouseY);

        if (pendingTooltip != null) {
            ctx.setTooltipForNextFrame(this.font, pendingTooltip, Optional.empty(), mouseX, mouseY, (Identifier) null);
        }
    }

    private void renderOptionControl(
        GuiGraphicsExtractor ctx,
        OptionEntry entry,
        OptionAvailability avail,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY,
        boolean inClipMouse
    ) {
        String valText = formatOptionValue(entry);
        boolean editable = avail.editable();

        if (entry.kind() == ControlKind.BOOLEAN) {
            boolean on = Boolean.TRUE.equals(entry.get(displayedDraft(entry)));
            boolean over = inClipMouse && editable && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int border = !editable ? 0xFF3B3052 : (on ? 0xFF5CE1A6 : 0xFF75609E);
            int fill = !editable ? 0xFF1D172B : (on ? (over ? 0xFF236B4E : 0xFF1A523B) : (over ? 0xFF34264F : 0xFF241A38));
            ctx.fill(x, y, x + w, y + h, border);
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            GrimoireTextLayout.drawCenteredBox(
                ctx, this.font, (on ? "\u25cf " : "\u25cb ") + valText,
                x + 4, w - 8, y + h * 0.5f, 0.72f,
                editable ? (on ? 0xFF9EFFD2 : CREAM_TEXT) : 0xFF6E6285, true
            );
            if (editable && inClipMouse) {
                this.hitBoxes.add(new HitBox(x, y, w, h, () -> {
                    entry.toggleOrCycle(this.draft, 1);
                    playClick(1.04f);
                }, () -> {
                    entry.toggleOrCycle(this.draft, -1);
                    playClick(0.96f);
                }));
            }
            return;
        }

        if (entry.kind() == ControlKind.ENUM_CYCLE) {
            boolean over = inClipMouse && editable && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            ctx.fill(x, y, x + w, y + h, editable ? (over ? GOLD_TEXT : 0xFF876BB8) : 0xFF3B3052);
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, !editable ? 0xFF1D172B : (over ? 0xFF3B2A5E : 0xFF291D42));
            GrimoireTextLayout.drawCenteredBox(
                ctx, this.font, "\u25c2 " + valText + " \u25b8",
                x + 3, w - 6, y + h * 0.5f, 0.68f,
                editable ? GOLD_TEXT : 0xFF6E6285, true
            );
            if (editable && inClipMouse) {
                this.hitBoxes.add(new HitBox(x, y, w, h, () -> {
                    entry.toggleOrCycle(this.draft, 1);
                    playClick(1.05f);
                }, () -> {
                    entry.toggleOrCycle(this.draft, -1);
                    playClick(0.95f);
                }));
            }
            return;
        }

        // Numeric slider with [-] [==== track ====] [+]
        int stepW = 12;
        int trackX = x + stepW + 2;
        int trackW = Math.max(30, w - (stepW + 2) * 2);
        int plusX = trackX + trackW + 2;

        boolean overMinus = inClipMouse && editable && mouseX >= x && mouseX < x + stepW && mouseY >= y && mouseY < y + h;
        boolean overPlus = inClipMouse && editable && mouseX >= plusX && mouseX < plusX + stepW && mouseY >= y && mouseY < y + h;
        ctx.fill(x, y, x + stepW, y + h, editable ? (overMinus ? GOLD_TEXT : 0xFF6D5899) : 0xFF352B4A);
        ctx.fill(x + 1, y + 1, x + stepW - 1, y + h - 1, overMinus ? 0xFF422E66 : 0xFF211735);
        GrimoireTextLayout.drawCenteredBox(ctx, this.font, "-", x, stepW, y + h * 0.5f, 0.76f, editable ? CREAM_TEXT : 0xFF665A7D, true);

        ctx.fill(plusX, y, plusX + stepW, y + h, editable ? (overPlus ? GOLD_TEXT : 0xFF6D5899) : 0xFF352B4A);
        ctx.fill(plusX + 1, y + 1, plusX + stepW - 1, y + h - 1, overPlus ? 0xFF422E66 : 0xFF211735);
        GrimoireTextLayout.drawCenteredBox(ctx, this.font, "+", plusX, stepW, y + h * 0.5f, 0.76f, editable ? CREAM_TEXT : 0xFF665A7D, true);

        double norm = entry.normalizedSliderValue(displayedDraft(entry));
        ctx.fill(trackX, y, trackX + trackW, y + h, editable ? 0xFF6D5899 : 0xFF352B4A);
        ctx.fill(trackX + 1, y + 1, trackX + trackW - 1, y + h - 1, 0xFF181126);
        int fillW = Math.round((float) ((trackW - 2) * norm));
        if (fillW > 0) {
            ctx.fill(trackX + 1, y + 1, trackX + 1 + fillW, y + h - 1, editable ? 0xFF563B8C : 0xFF2E2444);
        }
        int knobX = trackX + 1 + Mth.clamp(fillW, 0, Math.max(0, trackW - 4));
        ctx.fill(knobX, y + 1, Math.min(trackX + trackW - 1, knobX + 2), y + h - 1, editable ? GOLD_TEXT : 0xFF6B6080);

        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, valText, trackX + 2, trackW - 4, y + h * 0.5f, 0.70f,
            editable ? CREAM_TEXT : 0xFF6E6285, true
        );

        if (editable && inClipMouse) {
            this.hitBoxes.add(new HitBox(x, y, stepW, h, () -> {
                entry.stepValue(this.draft, -1);
                playClick(0.96f);
            }, () -> {
                entry.stepValue(this.draft, -5);
                playClick(0.90f);
            }));
            this.hitBoxes.add(new HitBox(plusX, y, stepW, h, () -> {
                entry.stepValue(this.draft, 1);
                playClick(1.04f);
            }, () -> {
                entry.stepValue(this.draft, 5);
                playClick(1.10f);
            }));
            this.sliderTracks.add(new SliderTrack(entry, trackX, y, trackW, h));
        }
    }

    private List<Component> buildOptionTooltip(OptionEntry entry, OptionAvailability avail) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(entry.nameKey()).withStyle(style -> style.withColor(0xFFFFAA00)));
        lines.add(Component.translatable(entry.descKey()).withStyle(style -> style.withColor(0xFFAAAAAA)));
        lines.add(Component.translatable(
            "config.chiseled_enchanting_table.tip.current_default",
            formatOptionValue(entry),
            formatDefaultValue(entry)
        ).withStyle(style -> style.withColor(0xFF55FFFF)));
        if (entry.effectScope() == ConfigCatalog.EffectScope.RESTART_REQUIRED) {
            String actual = entry.rawOrKeyDisplay(ModRuntimeConfig.effective().toDraft());
            lines.add(Component.translatable("config.chiseled_enchanting_table.tip.active_value", actual).withStyle(style -> style.withColor(0xFFFFAA00)));
        }
        int scopeFmt = switch (avail.effectScope()) {
            case IMMEDIATE -> 0xFF55FF55;
            case NEW_GENERATION -> 0xFF55FFFF;
            case RESTART_REQUIRED -> 0xFFFFAA00;
        };
        lines.add(Component.translatable(avail.effectScope().badgeKey() + ".detail")
            .withStyle(style -> style.withColor(scopeFmt)));
        if (avail.inactiveReasonKey() != null) {
            lines.add(Component.translatable(avail.inactiveReasonKey()).withStyle(style -> style.withColor(0xFFFF5555)));
        }
        return lines;
    }

    private void renderFooterBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int footY = this.panelY + this.panelH - 25;
        ctx.fill(this.panelX, footY - 2, this.panelX + this.panelW, this.panelY + this.panelH, HEADER_BG);
        ctx.fill(this.panelX, footY - 2, this.panelX + this.panelW, footY - 1, 0xFF4B3570);

        ModRuntimeConfig currentPreview = this.draft.build();
        boolean hasUnsaved = !currentPreview.equals(this.baselineConfig);
        boolean restartChanged = currentPreview.enchantedBookStackSize() != ModRuntimeConfig.get().enchantedBookStackSize();

        int btnH = 18;
        int saveW = 86;
        int cancelW = 60;
        int resetAllW = 74;

        int saveX = this.panelX + this.panelW - saveW - 6;
        int cancelX = saveX - cancelW - 5;
        int resetAllX = this.panelX + 6;

        // Reset All button
        boolean anyCustom = ConfigCatalog.ALL_OPTIONS.stream().anyMatch(o -> o.isModifiedFromDefault(this.draft));
        boolean canResetAll = this.sessionMode.writable() && anyCustom;
        boolean overResetAll = canResetAll && mouseX >= resetAllX && mouseX < resetAllX + resetAllW && mouseY >= footY + 2 && mouseY < footY + 2 + btnH;
        ctx.fill(resetAllX, footY + 2, resetAllX + resetAllW, footY + 2 + btnH, canResetAll ? (overResetAll ? 0xFFFF9B85 : 0xFF8C4A52) : 0xFF352B47);
        ctx.fill(resetAllX + 1, footY + 3, resetAllX + resetAllW - 1, footY + 1 + btnH, overResetAll ? 0xFF572631 : 0xFF241834);
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, I18n.get("config.chiseled_enchanting_table.btn.reset_all"),
            resetAllX + 3, resetAllW - 6, footY + 2 + btnH * 0.5f, 0.68f,
            canResetAll ? CREAM_TEXT : 0xFF665A7D, true
        );
        if (canResetAll) {
            this.hitBoxes.add(new HitBox(resetAllX, footY + 2, resetAllW, btnH, () -> {
                for (OptionEntry entry : ConfigCatalog.ALL_OPTIONS) {
                    entry.resetToDefault(this.draft);
                }
                this.draft.customizedKeys.clear();
                playClick(0.88f);
            }, null));
        }

        // Center status line
        int statusX = resetAllX + resetAllW + 8;
        int statusMaxW = Math.max(40, cancelX - 8 - statusX);
        String statusText;
        int statusCol;
        if (this.pendingSaveId != 0) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.saving");
            statusCol = GOLD_TEXT;
        } else if (System.currentTimeMillis() < this.statusToastUntilMs) {
            statusText = I18n.get(this.lastSaveFailed ? "config.chiseled_enchanting_table.msg.save_failed" : "config.chiseled_enchanting_table.msg.saved");
            statusCol = 0xFF6BFFB3;
        } else if (restartChanged) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.restart_hint");
            statusCol = 0xFFFFC46B;
        } else if (hasUnsaved) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.unsaved");
            statusCol = GOLD_TEXT;
        } else {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.synced");
            statusCol = MUTED_TEXT;
        }
        RowRenderSupport.renderCenteredYText(
            ctx, Minecraft.getInstance(), statusText, statusX, footY + 2 + btnH * 0.5f, 0.68f, statusMaxW, statusCol
        );

        // Cancel / Close button
        boolean overCancel = mouseX >= cancelX && mouseX < cancelX + cancelW && mouseY >= footY + 2 && mouseY < footY + 2 + btnH;
        ctx.fill(cancelX, footY + 2, cancelX + cancelW, footY + 2 + btnH, overCancel ? GOLD_TEXT : 0xFF6F5D94);
        ctx.fill(cancelX + 1, footY + 3, cancelX + cancelW - 1, footY + 1 + btnH, overCancel ? 0xFF3A2959 : 0xFF231936);
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, I18n.get("config.chiseled_enchanting_table.btn.close"),
            cancelX + 3, cancelW - 6, footY + 2 + btnH * 0.5f, 0.70f, CREAM_TEXT, true
        );
        this.hitBoxes.add(new HitBox(cancelX, footY + 2, cancelW, btnH, () -> {
            playClick(0.95f);
            onClose();
        }, null));

        // Save & Apply button
        boolean canSave = this.sessionMode.writable();
        boolean overSave = canSave && mouseX >= saveX && mouseX < saveX + saveW && mouseY >= footY + 2 && mouseY < footY + 2 + btnH;
        ctx.fill(saveX, footY + 2, saveX + saveW, footY + 2 + btnH, canSave ? (overSave ? 0xFFFFF0A6 : 0xFF56E39F) : 0xFF3A304D);
        ctx.fill(saveX + 1, footY + 3, saveX + saveW - 1, footY + 1 + btnH, canSave ? (overSave ? 0xFF237552 : 0xFF19593E) : 0xFF211B2E);
        GrimoireTextLayout.drawCenteredBox(
            ctx, this.font, I18n.get("config.chiseled_enchanting_table.btn.save"),
            saveX + 3, saveW - 6, footY + 2 + btnH * 0.5f, 0.72f,
            canSave ? CREAM_TEXT : 0xFF695E7D, true
        );
        if (canSave) {
            this.hitBoxes.add(new HitBox(saveX, footY + 2, saveW, btnH, () -> saveAndApply(true), () -> saveAndApply(false)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == 0) {
            for (SliderTrack slider : this.sliderTracks) {
                if (slider.contains(mx, my)) {
                    this.activeDraggingSlider = slider;
                    double t = (mx - slider.x()) / (double) Math.max(1, slider.w());
                    slider.entry().setFromNormalizedSlider(this.draft, t);
                    playClick(1.02f);
                    return true;
                }
            }
            if (this.maxScroll > 0.5f
                && mx >= this.listX + this.listW - 6 && mx <= this.listX + this.listW
                && my >= this.listY && my < this.listY + this.listH) {
                this.draggingScrollbar = true;
                updateScrollFromMouse(my);
                return true;
            }
        }

        for (HitBox box : this.hitBoxes) {
            if (box.contains(mx, my)) {
                if (event.button() == 0) {
                    box.onClick().run();
                    return true;
                } else if (event.button() == 1 && box.onRightClick() != null) {
                    box.onRightClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.activeDraggingSlider != null && event.button() == 0) {
            double t = (event.x() - this.activeDraggingSlider.x()) / (double) Math.max(1, this.activeDraggingSlider.w());
            this.activeDraggingSlider.entry().setFromNormalizedSlider(this.draft, t);
            return true;
        }
        if (this.draggingScrollbar && event.button() == 0) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.activeDraggingSlider = null;
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void updateScrollFromMouse(double my) {
        float ratio = Mth.clamp((float) ((my - this.listY) / Math.max(1.0, this.listH)), 0.0f, 1.0f);
        this.scrollOffset = ratio * this.maxScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxScroll > 0.5f
            && mouseX >= this.listX && mouseX < this.listX + this.listW
            && mouseY >= this.listY && mouseY < this.listY + this.listH) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (float) (scrollY * 18.0), 0.0f, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_6) {
            this.filterModifiedOnly = false;
            this.activeCategory = CategoryId.values()[key - InputConstants.KEY_1];
            this.scrollOffset = 0.0f;
            playClick(1.0f);
            return true;
        }
        return super.keyPressed(event);
    }
}
