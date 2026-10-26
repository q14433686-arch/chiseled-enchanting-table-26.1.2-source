package chiseled_enchanting_table.gui.config;

import static chiseled_enchanting_table.gui.config.ConfigSkin.*;
import static chiseled_enchanting_table.gui.config.ConfigInteractions.*;

import static chiseled_enchanting_table.gui.config.ConfigPanelPainter.*;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.config.ConfigCatalog;
import chiseled_enchanting_table.config.ConfigCatalog.CategoryId;
import chiseled_enchanting_table.config.ConfigCatalog.ControlKind;
import chiseled_enchanting_table.config.ConfigCatalog.EffectScope;
import chiseled_enchanting_table.config.ConfigCatalog.OptionAvailability;
import chiseled_enchanting_table.config.ConfigCatalog.OptionEntry;
import chiseled_enchanting_table.config.ConfigCatalog.RuleTraceEntry;
import chiseled_enchanting_table.config.ConfigCatalog.SessionMode;
import chiseled_enchanting_table.config.ConfigLayoutSpec;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import chiseled_enchanting_table.config.ConfigLayoutSpec.ModalLayout;
import chiseled_enchanting_table.config.ConfigLayoutSpec.OptionRowLayout;
import chiseled_enchanting_table.config.ConfigLayoutSpec.SliderControlLayout;
import chiseled_enchanting_table.config.ConfigLayoutSpec.TraceCardLayout;
import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
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
 * Layered pixel-art L0–L3 Configuration &amp; Rule Trace screen supporting both standalone
 * presentation (ModMenu, {@code /cet config}, Arcane Grimoire) and lossless centered modal overlay
 * rendering directly inside {@link chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen}.
 */
@Environment(EnvType.CLIENT)
public class ChiseledEnchantingConfigScreen extends Screen {

    // ---- Interactive Hit Regions ----




    private record DisplayItem(
        boolean isSectionHeader,
        @Nullable String sectionKey,
        @Nullable String sectionTitle,
        int sectionTotalCount,
        int sectionActiveCount,
        boolean sectionExpanded,
        @Nullable OptionEntry option,
        int flatOptionIndex
    ) {
        int height() {
            return isSectionHeader ? ConfigLayoutSpec.SECTION_HEADER_H : ConfigLayoutSpec.OPTION_ROW_H;
        }
    }

    // ---- State ----
    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final Runnable closeOverride;

    private final SessionMode sessionMode;
    private final chiseled_enchanting_table.config.ConfigEditSession editSession;
    private final Draft defaultReferenceDraft = new Draft();
    private final Draft classicReferenceDraft;

    private CategoryId activeCategory = CategoryId.BOOKSHELVES;
    private boolean ruleTracePage = false;
    public static final int MAX_SEARCH_CODE_POINTS = chiseled_enchanting_table.config.SearchTextBuffer.DEFAULT_MAX_CODE_POINTS;

    private static final java.util.Map<CategoryId, Integer> CATEGORY_HOTKEYS;
    private static final int RULE_TRACE_HOTKEY = InputConstants.KEY_8;

    static {
        java.util.EnumMap<CategoryId, Integer> map = new java.util.EnumMap<>(CategoryId.class);
        map.put(CategoryId.BOOKSHELVES, InputConstants.KEY_1);
        map.put(CategoryId.EXPERIENCE_ECONOMY, InputConstants.KEY_2);
        map.put(CategoryId.TOME_DURABILITY, InputConstants.KEY_3);
        map.put(CategoryId.EPIPHANY, InputConstants.KEY_4);
        map.put(CategoryId.CLEANSE_TRANSMUTE, InputConstants.KEY_5);
        map.put(CategoryId.WORLD_LOOT, InputConstants.KEY_6);
        map.put(CategoryId.GENERAL, InputConstants.KEY_7);
        if (map.size() != CategoryId.values().length
            || new java.util.HashSet<>(map.values()).size() != map.size()
            || map.containsValue(RULE_TRACE_HOTKEY)) {
            throw new IllegalStateException("Category hotkey table out of sync with CategoryId.values()");
        }
        CATEGORY_HOTKEYS = java.util.Collections.unmodifiableMap(map);
    }

    private boolean filterModifiedOnly = false;
    // 26.1.2 has no SDL text-input manager (onTextInputFocusChange is a 26.3 API):
    // native focus ownership is a no-op and the buffer receives CharacterEvent text directly.
    private final chiseled_enchanting_table.config.SearchInputSession searchInput =
        new chiseled_enchanting_table.config.SearchInputSession(focused -> {});
    private final chiseled_enchanting_table.config.SearchTextBuffer searchBuffer = searchInput.buffer();
    private String searchQuery = "";

    private final Set<String> collapsedSections = new HashSet<>();
    private final Set<String> userToggledSections = new HashSet<>();

    private float listScroll = 0.0f;
    private float maxListScroll = 0.0f;
    private float railScroll = 0.0f;
    private float maxRailScroll = 0.0f;
    private int focusedOptionIndex = -1;

    private ModalLayout layout;
    private int stickyBodyOffset = 0;

    private final List<HitBox> hitBoxes = new ArrayList<>();
    private final List<SliderTrack> sliderTracks = new ArrayList<>();
    @Nullable
    private final chiseled_enchanting_table.config.ConfigSliderDragSession sliderDrag = new chiseled_enchanting_table.config.ConfigSliderDragSession();
    private boolean draggingScrollbar = false;

    private final chiseled_enchanting_table.config.ConfigSaveSession saves = new chiseled_enchanting_table.config.ConfigSaveSession();

    public ChiseledEnchantingConfigScreen(@Nullable Screen parentScreen) {
        this(parentScreen, null);
    }

    public ChiseledEnchantingConfigScreen(@Nullable Screen parentScreen, @Nullable Runnable closeOverride) {
        super(Component.translatable("config.chiseled_enchanting_table.title"));
        this.parentScreen = parentScreen;
        this.closeOverride = closeOverride;
        this.sessionMode = detectSessionMode();
        this.editSession = new chiseled_enchanting_table.config.ConfigEditSession(ModRuntimeConfig.preferences());
        Draft classicRef = new Draft();
        ModRuntimeConfig.Preset.CLASSIC.applyTo(classicRef);
        this.classicReferenceDraft = classicRef;
        this.activeCategory = CategoryId.BOOKSHELVES;
        syncAutoCollapsedSections();
    }

    public void initOverlay(Minecraft mc, int fullWidth, int fullHeight) {
        this.width = fullWidth;
        this.height = fullHeight;
        this.init();
    }

    public Rect2i fullGuiBounds() {
        if (this.layout == null) {
            recomputeLayout();
        }
        Box m = this.layout.modal();
        return new Rect2i(m.x(), m.y(), m.w(), m.h());
    }

    private static boolean isPublishedLanHost() {
        var integratedServer = Minecraft.getInstance().getSingleplayerServer();
        return integratedServer != null && integratedServer.isPublished();
    }

    private static SessionMode detectSessionMode() {
        Minecraft mc = Minecraft.getInstance();
        return SessionMode.resolve(
            ModRuntimeConfig.effectivePackPolicy().lockInGameConfig(),
            mc.level != null,
            mc.hasSingleplayerServer(),
            isPublishedLanHost(),
            ModRuntimeConfig.clientCanEditServerConfig()
        );
    }

    private SessionMode effectiveSessionMode() {
        if (this.sessionMode == SessionMode.SINGLEPLAYER_LIVE && isPublishedLanHost()) {
            return SessionMode.LAN_HOST_LIVE;
        }
        return this.sessionMode;
    }

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
    }

    private void recomputeLayout() {
        int railCount = CategoryId.values().length + 1; // 7 domains + Rule Trace
        this.layout = ConfigLayoutSpec.compute(
            Math.max(320, this.width),
            Math.max(220, this.height),
            ModRuntimeConfig.Preset.values().length,
            railCount
        );
        this.maxRailScroll = Math.max(0.0f, this.layout.railContentHeight() - this.layout.railViewport().h());
        this.railScroll = Mth.clamp(this.railScroll, 0.0f, this.maxRailScroll);
    }

    /**
     * Auto-collapses fine-tuning subsections whose options are ALL currently gated off by a parent
     * rule atom (unless the user explicitly expanded/collapsed that subsection during this session).
     */
    private void syncAutoCollapsedSections() {
        for (CategoryId cat : CategoryId.values()) {
            Map<String, List<OptionEntry>> bySec = new LinkedHashMap<>();
            for (OptionEntry entry : ConfigCatalog.optionsInCategory(cat)) {
                if (entry.isCoreRuleAtom() || entry.sectionId().endsWith("_core")) continue;
                bySec.computeIfAbsent(cat.id() + ":" + entry.sectionId(), k -> new ArrayList<>()).add(entry);
            }
            for (var e : bySec.entrySet()) {
                String key = e.getKey();
                if (this.userToggledSections.contains(key)) continue;
                boolean anyApplicable = e.getValue().stream().anyMatch(opt -> opt.isParentApplicable(this.editSession.draft()));
                if (!anyApplicable) {
                    this.collapsedSections.add(key);
                } else {
                    this.collapsedSections.remove(key);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** 26.3 uses SDL text input; a private boolean alone never starts character events. */
    private void setSearchFocused(boolean focused) {
        this.searchInput.setFocused(focused);
    }

    // 26.1.2: Screen has no capturesInput() (an SDL text-capture hook in 26.3);
    // character events are delivered without native capture ownership.

    @Override
    public void removed() {
        setSearchFocused(false);
        super.removed();
    }

    @Override
    public void onClose() {
        setSearchFocused(false);
        if (this.closeOverride != null) {
            this.closeOverride.run();
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    // ---- Search, Filtering & Section Building ----

    private boolean isSearchMode() {
        return !this.searchQuery.isBlank();
    }

    private boolean matchesSearch(OptionEntry entry) {
        String q = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return entry.key().contains(q)
            || I18n.get(entry.nameKey()).toLowerCase(Locale.ROOT).contains(q)
            || I18n.get(entry.descKey()).toLowerCase(Locale.ROOT).contains(q)
            || I18n.get(entry.category().titleKey()).toLowerCase(Locale.ROOT).contains(q)
            || I18n.get(entry.sectionTitleKey()).toLowerCase(Locale.ROOT).contains(q);
    }

    private List<OptionEntry> flatFilteredOptions() {
        if (isSearchMode()) {
            return ConfigCatalog.ALL_OPTIONS.stream().filter(this::matchesSearch).toList();
        }
        if (this.filterModifiedOnly) {
            return ConfigCatalog.ALL_OPTIONS.stream()
                .filter(o -> o.isModifiedFromDefault(this.editSession.draft()))
                .toList();
        }
        return ConfigCatalog.optionsInCategory(this.activeCategory);
    }

    private List<DisplayItem> buildDisplayItems(List<OptionEntry> sourceOptions) {
        List<DisplayItem> items = new ArrayList<>();
        if (sourceOptions.isEmpty()) return items;

        if (isSearchMode() || this.filterModifiedOnly) {
            // Group across natural domains so search & modified views show exact domain context.
            int optIdx = 0;
            for (CategoryId cat : CategoryId.values()) {
                List<OptionEntry> inCat = sourceOptions.stream().filter(o -> o.category() == cat).toList();
                if (inCat.isEmpty()) continue;
                int activeCount = (int) inCat.stream().filter(o -> o.isParentApplicable(this.editSession.draft())).count();
                String secKey = "global:" + cat.id();
                boolean expanded = !this.collapsedSections.contains(secKey) || isSearchMode();
                items.add(new DisplayItem(
                    true, secKey, I18n.get(cat.titleKey()), inCat.size(), activeCount, expanded, null, -1
                ));
                if (expanded) {
                    for (OptionEntry entry : inCat) {
                        items.add(new DisplayItem(false, secKey, null, 0, 0, true, entry, optIdx++));
                    }
                }
            }
            return items;
        }

        // Standard L1 Domain View: Core Rule Atoms first (expanded at top), followed by L2 collapsible subsections!
        Map<String, List<OptionEntry>> bySection = new LinkedHashMap<>();
        for (OptionEntry entry : sourceOptions) {
            bySection.computeIfAbsent(entry.sectionId(), k -> new ArrayList<>()).add(entry);
        }

        int optIdx = 0;
        for (var secEntry : bySection.entrySet()) {
            String secId = secEntry.getKey();
            List<OptionEntry> group = secEntry.getValue();
            if (group.isEmpty()) continue;
            String collapseKey = this.activeCategory.id() + ":" + secId;
            boolean expanded = !this.collapsedSections.contains(collapseKey);
            int activeCount = (int) group.stream().filter(o -> o.isParentApplicable(this.editSession.draft())).count();
            String secTitle = I18n.get(group.getFirst().sectionTitleKey());

            items.add(new DisplayItem(
                true, collapseKey, secTitle, group.size(), activeCount, expanded, null, -1
            ));
            if (expanded) {
                for (OptionEntry entry : group) {
                    items.add(new DisplayItem(false, collapseKey, null, 0, 0, true, entry, optIdx++));
                }
            }
        }
        return items;
    }

    private static int computeDisplayHeight(List<DisplayItem> items) {
        if (items.isEmpty()) return 0;
        int total = -ConfigLayoutSpec.ROW_GAP;
        for (DisplayItem item : items) {
            total += item.height() + ConfigLayoutSpec.ROW_GAP;
        }
        return Math.max(0, total);
    }

    // ---- Value & Provenance Formatting ----

    private Draft displayedDraft(OptionEntry entry) {
        if (this.sessionMode == SessionMode.OFFLINE_CLIENT) return this.editSession.draft();
        var policy = ModRuntimeConfig.effectivePackPolicy();
        boolean governed = policy.isKeyLocked(entry.key())
            || (policy.datapackOverriddenKeys().contains(entry.key())
                && (!this.editSession.draft().overrideDatapacks || policy.enforceDatapackPriority()));
        return governed ? ModRuntimeConfig.effective().toDraft() : this.editSession.draft();
    }



    private void onDraftMutated(@Nullable String editedKey) {
        this.editSession.draft().normalizeAfterEdit(editedKey);
        syncAutoCollapsedSections();
    }

    private void playClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void setSearchQuery(String next) {
        this.searchBuffer.setAll(next);
        this.searchQuery = this.searchBuffer.text();
        this.focusedOptionIndex = -1;
        this.listScroll = 0.0f;
    }

    private void syncSearchQueryFromBuffer() {
        this.searchQuery = this.searchBuffer.text();
        this.focusedOptionIndex = -1;
        this.listScroll = 0.0f;
    }

    // ---- Save & Sync Lifecycle ----

    private void saveAndApply(boolean closeAfterSave) {
        if (!this.sessionMode.writable() || this.saves.pendingRequestId() != 0) return;
        ModRuntimeConfig built = this.editSession.draft().build();
        playClick(1.12f);
        long now = System.currentTimeMillis();
        if (this.sessionMode == SessionMode.OFFLINE_CLIENT) {
            try {
                ModRuntimeConfig.setLocalAndSave(built);
                ChiseledEnchantingTableData.applyRuntimeConfig(built);
                this.editSession.acceptSaved(built);
                this.saves.completed(true, now);
                if (closeAfterSave) onClose();
            } catch (RuntimeException error) {
                this.saves.completed(false, now);
            }
            return;
        }
        // Missing/closed transport must not leave an editor permanently stuck in pending.
        if (!ClientPlayNetworking.canSend(ModConfigNetworking.UpdateRuntimeConfigPayload.TYPE)) {
            this.saves.completed(false, now);
            return;
        }
        long request = ModRuntimeConfig.nextSaveRequestId();
        this.saves.begin(request, closeAfterSave, now);
        try {
            ClientPlayNetworking.send(new ModConfigNetworking.UpdateRuntimeConfigPayload(
                built.toJsonString(), request, closeAfterSave && this.closeOverride == null));
        } catch (RuntimeException failure) {
            this.saves.acknowledge(request, false, now);
        }
    }

    private void reconcileSaveAcknowledgement() {
        long now = System.currentTimeMillis();
        this.saves.expire(now);
        var result = this.saves.acknowledge(ModRuntimeConfig.acknowledgedSaveId(),
            ModRuntimeConfig.acknowledgedSaveAccepted(), now);
        if (result == chiseled_enchanting_table.config.ConfigSaveSession.Result.ACCEPTED
            || result == chiseled_enchanting_table.config.ConfigSaveSession.Result.ACCEPTED_CLOSE) {
            this.editSession.acceptSaved(ModRuntimeConfig.preferences());
            syncAutoCollapsedSections();
            if (result == chiseled_enchanting_table.config.ConfigSaveSession.Result.ACCEPTED_CLOSE) onClose();
        }
    }

    // ---- Main Layered Render Pass ----

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        // Rendered inside extractRenderState to prevent double-blur artifacts when opened over world/table.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        reconcileSaveAcknowledgement();
        recomputeLayout();
        this.hitBoxes.clear();
        this.sliderTracks.clear();
        this.stickyBodyOffset = 0;
        List<Component> pendingTooltip = null;

        Box modal = this.layout.modal();

        // Layer 0: Full-viewport scrim + tiled arcane backdrop + 9-slice sculpted obsidian/brass frame + symmetrical clasps
        ctx.fill(0, 0, this.width, this.height, BG_SCRIM);
        blitTiledBackdrop(ctx, this.layout.backdropArea());
        blitNineSliceRect(ctx, MODAL_FRAME, modal.x(), modal.y(), modal.w(), modal.h(), 28, 28, 96, 96);

        int claspW = 44;
        int claspH = 12;
        int claspX = Math.round(modal.centerX() - claspW * 0.5f);
        ctx.blit(FRAME_GEM_PIP, claspX, modal.y(), claspX + claspW, modal.y() + claspH, 0.0f, 1.0f, 0.0f, 1.0f);
        ctx.blit(FRAME_GEM_PIP, claspX, modal.bottom() - claspH, claspX + claspW, modal.bottom(), 0.0f, 1.0f, 1.0f, 0.0f);

        // Layer 1 (L0): Header & Preset/Search Toolbar
        List<Component> l0Tip = renderL0HeaderAndToolbar(ctx, mouseX, mouseY);
        if (l0Tip != null) pendingTooltip = l0Tip;
        blitOrnamentDivider(ctx, this.layout.topDivider());

        // Layer 2 (L1): Left Domain Rail + Rule Trace Entry
        List<Component> railTip = renderL1DomainRail(ctx, mouseX, mouseY);
        if (railTip != null && pendingTooltip == null) pendingTooltip = railTip;

        // Layer 3 (L2 / L3 / Rule Trace): Main Right Viewport
        List<Component> bodyTip = (this.ruleTracePage && !isSearchMode() && !this.filterModifiedOnly)
            ? renderRuleTraceViewport(ctx, mouseX, mouseY)
            : renderOptionListViewport(ctx, mouseX, mouseY);
        if (bodyTip != null && pendingTooltip == null) pendingTooltip = bodyTip;

        // Layer 4: Bottom Divider & Action Footer
        blitOrnamentDivider(ctx, this.layout.bottomDivider());
        List<Component> footTip = renderFooterBar(ctx, mouseX, mouseY);
        if (footTip != null && pendingTooltip == null) pendingTooltip = footTip;

        if (pendingTooltip != null && !pendingTooltip.isEmpty()) {
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, this.font, pendingTooltip, Optional.empty(), mouseX, mouseY, (Identifier) null);
        }
    }

    private List<Component> renderL0HeaderAndToolbar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> tip = null;

        // L0-A1: Title
        drawClampedLeftText(this.font,
            ctx,
            I18n.get("config.chiseled_enchanting_table.title"),
            this.layout.headerTitleBox(),
            ConfigLayoutSpec.PRIMARY_TEXT_SCALE + 0.04f,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            GOLD_TEXT
        );

        // L0-A2: Modified Filter Chip ("★ N")
        int modCount = (int) ConfigCatalog.ALL_OPTIONS.stream()
            .filter(o -> o.isModifiedFromDefault(this.editSession.draft()))
            .count();
        Box modBtn = this.layout.modifiedFilterBtn();
        boolean overMod = modBtn.contains(mouseX, mouseY);
        Identifier modSprite = this.filterModifiedOnly
            ? BTN_PLAQUE_GOLD
            : (overMod ? BTN_PLAQUE_HOVER : BTN_PLAQUE);
        blitNineSliceRect(ctx, modSprite, modBtn.x(), modBtn.y(), modBtn.w(), modBtn.h(), 6, 6, 64, 18);
        String modLabel = "\u2605 " + modCount;
        drawClampedCenteredText(this.font,
            ctx,
            modLabel,
            new Box(modBtn.x() + 4, modBtn.y(), Math.max(12, modBtn.w() - 8), modBtn.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            this.filterModifiedOnly ? GOLD_TEXT : (modCount > 0 ? EMERALD_TEXT : MUTED_TEXT)
        );
        this.hitBoxes.add(new HitBox(modBtn, () -> {
            this.filterModifiedOnly = !this.filterModifiedOnly;
            if (this.filterModifiedOnly) {
                this.ruleTracePage = false;
            }
            this.listScroll = 0.0f;
            playClick(1.0f);
        }, null));
        if (overMod) {
            tip = List.of(
                Component.translatable("config.chiseled_enchanting_table.filter.modified_only", modCount)
                    .withStyle(ChatFormatting.GOLD),
                Component.translatable("config.chiseled_enchanting_table.filter.modified_hint")
                    .withStyle(ChatFormatting.GRAY)
            );
        }

        // L0-A3: Session Mode Badge (sculpted obsidian/brass plaque with subtle status glow)
        SessionMode shownMode = effectiveSessionMode();
        Box modeBox = this.layout.sessionBadgeBox();
        boolean overMode = modeBox.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            shownMode.writable() ? (overMode ? BTN_PLAQUE_HOVER : BTN_PLAQUE) : BTN_PLAQUE_DISABLED,
            modeBox.x(), modeBox.y(), modeBox.w(), modeBox.h(), 6, 6, 64, 18
        );
        int modeTint = switch (shownMode) {
            case SINGLEPLAYER_LIVE -> 0x3029B87A;
            case LAN_HOST_LIVE -> 0x3036C2B4;
            case DEDICATED_ADMIN -> 0x30E5A93C;
            case OFFLINE_CLIENT -> 0x304CA8E0;
            case DEDICATED_READONLY -> 0x34C94862;
            case MODPACK_LOCKED -> 0x349B59E0;
        };
        ctx.fill(modeBox.x() + 4, modeBox.y() + 3, modeBox.right() - 4, modeBox.bottom() - 3, modeTint);
        int modeTextColor = switch (shownMode) {
            case SINGLEPLAYER_LIVE -> EMERALD_TEXT;
            case LAN_HOST_LIVE -> CYAN_TEXT;
            case DEDICATED_ADMIN -> GOLD_TEXT;
            case OFFLINE_CLIENT -> CYAN_TEXT;
            case DEDICATED_READONLY, MODPACK_LOCKED -> AMBER_TEXT;
        };
        String modeLabel = (shownMode.writable() ? "\u270e " : "🔒 ") + I18n.get(shownMode.bannerKey());
        drawClampedCenteredText(this.font,
            ctx,
            modeLabel,
            new Box(modeBox.x() + 6, modeBox.y(), Math.max(16, modeBox.w() - 12), modeBox.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            modeTextColor
        );
        if (overMode) {
            String descKey = shownMode == SessionMode.LAN_HOST_LIVE
                ? "config.chiseled_enchanting_table.mode.lan_host_desc"
                : (shownMode.writable()
                    ? "config.chiseled_enchanting_table.mode.writable_desc"
                    : "config.chiseled_enchanting_table.mode.readonly_desc");
            tip = List.of(
                Component.translatable(shownMode.bannerKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(descKey).withStyle(ChatFormatting.GRAY)
            );
        }

        // L0-B1: 4 One-Click Presets
        ModRuntimeConfig.Preset[] presets = ModRuntimeConfig.Preset.values();
        List<Box> presetBoxes = this.layout.presetButtons();
        for (int i = 0; i < presets.length && i < presetBoxes.size(); i++) {
            ModRuntimeConfig.Preset preset = presets[i];
            Box pb = presetBoxes.get(i);
            boolean active = preset.matches(this.editSession.draft());
            boolean writable = this.sessionMode.writable();
            boolean over = pb.contains(mouseX, mouseY);
            Identifier sprite = !writable
                ? BTN_PLAQUE_DISABLED
                : (active ? BTN_PLAQUE_GOLD : (over ? BTN_PLAQUE_HOVER : BTN_PLAQUE));
            blitNineSliceRect(ctx, sprite, pb.x(), pb.y(), pb.w(), pb.h(), 6, 6, 64, 18);

            String title = I18n.get("config.chiseled_enchanting_table.preset." + preset.id() + ".short");
            if (title.startsWith("config.")) {
                title = I18n.get("config.chiseled_enchanting_table.preset." + preset.id() + ".title");
            }
            String label = active ? ("\u25cf " + title) : title;
            drawClampedCenteredText(this.font,
                ctx,
                label,
                new Box(pb.x() + 4, pb.y(), Math.max(12, pb.w() - 8), pb.h()),
                ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
                ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
                !writable ? DIM_TEXT : (active ? GOLD_TEXT : CREAM_TEXT)
            );

            if (writable) {
                this.hitBoxes.add(new HitBox(pb, () -> {
                    preset.applyTo(this.editSession.draft());
                    syncAutoCollapsedSections();
                    playClick(1.06f);
                }, null));
            }
            if (over) {
                tip = buildPresetTooltip(preset, active);
            }
        }

        // L0-B2: Global Search Box
        Box sb = this.layout.searchBox();
        boolean overSearch = sb.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            (this.searchInput.focused() || !this.searchQuery.isEmpty()) ? SEARCH_BOX_FOCUS : SEARCH_BOX,
            sb.x(), sb.y(), sb.w(), sb.h(), 8, 6, 80, 18
        );

        boolean hasQuery = !this.searchQuery.isEmpty();
        int clearW = hasQuery ? 14 : 0;
        Box searchTextBox = new Box(sb.x() + 6, sb.y(), Math.max(20, sb.w() - 12 - clearW), sb.h());
        String displaySearch = hasQuery
            ? this.searchBuffer.renderWithCaret(this.searchInput.focused())
            : (this.searchInput.focused() ? "|" : "/ " + I18n.get("config.chiseled_enchanting_table.search.placeholder"));
        drawClampedLeftText(this.font,
            ctx,
            displaySearch,
            searchTextBox,
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            (hasQuery || this.searchInput.focused()) ? CREAM_TEXT : MUTED_TEXT
        );

        if (hasQuery) {
            Box clearBox = new Box(sb.right() - 16, sb.y() + 1, 15, sb.h() - 2);
            boolean overClear = clearBox.contains(mouseX, mouseY);
            drawClampedCenteredText(this.font,
                ctx,
                "\u00d7",
                clearBox,
                ConfigLayoutSpec.PRIMARY_TEXT_SCALE,
                ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
                overClear ? CORAL_TEXT : GOLD_TEXT
            );
            this.hitBoxes.add(new HitBox(clearBox, () -> {
                setSearchQuery("");
                playClick(0.95f);
            }, null));
        }

        this.hitBoxes.add(new HitBox(new Box(sb.x(), sb.y(), sb.w() - clearW, sb.h()), () -> {
            setSearchFocused(true);
            playClick(1.0f);
        }, () -> {
            setSearchQuery("");
            setSearchFocused(true);
            playClick(0.95f);
        }));
        if (overSearch && tip == null) {
            tip = List.of(
                Component.translatable("config.chiseled_enchanting_table.search.tip_title").withStyle(ChatFormatting.GOLD),
                Component.translatable("config.chiseled_enchanting_table.search.tip_desc").withStyle(ChatFormatting.GRAY)
            );
        }

        return tip;
    }

    private List<Component> buildPresetTooltip(ModRuntimeConfig.Preset preset, boolean active) {
        String key = preset.id();
        int diffs = preset.countDeviations(this.editSession.draft());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("config.chiseled_enchanting_table.preset." + key + ".title")
            .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("config.chiseled_enchanting_table.preset." + key + ".desc")
            .withStyle(ChatFormatting.GRAY));
        lines.add((active
            ? Component.translatable("config.chiseled_enchanting_table.preset.status_active")
            : Component.translatable("config.chiseled_enchanting_table.preset.status_diff", diffs))
            .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        lines.add(Component.translatable("config.chiseled_enchanting_table.preset.note_new_chunks")
            .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // ---- L1 Domain Rail + Rule Trace Entry ----

    private List<Component> renderL1DomainRail(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> tip = null;
        Box rail = this.layout.railViewport();
        ctx.fill(rail.x() - 1, rail.y() - 1, rail.right() + 1, rail.bottom() + 1, 0x66160F24);

        CategoryId[] categories = CategoryId.values();
        List<Box> tabBoxes = this.layout.railTabBoxes();
        int scrollY = Math.round(this.railScroll);

        ctx.enableScissor(rail.x(), rail.y(), rail.right(), rail.bottom());
        try {
            for (int i = 0; i < tabBoxes.size(); i++) {
                Box raw = tabBoxes.get(i);
                Box tb = new Box(raw.x(), raw.y() - scrollY, raw.w(), raw.h());
                if (tb.bottom() <= rail.y() || tb.y() >= rail.bottom()) continue;

                boolean isTraceTab = (i == categories.length);
                CategoryId cat = isTraceTab ? null : categories[i];
                boolean selected = !isSearchMode() && !this.filterModifiedOnly
                    && (isTraceTab ? this.ruleTracePage : (!this.ruleTracePage && this.activeCategory == cat));
                boolean hovered = rail.contains(mouseX, mouseY) && tb.contains(mouseX, mouseY);

                // Subtle separator line right above Rule Trace tab
                if (isTraceTab) {
                    ctx.fill(tb.x() + 6, tb.y() - 3, tb.right() - 6, tb.y() - 2, 0x887A5C38);
                }

                blitNineSliceRect(
                    ctx,
                    selected ? RAIL_TAB_ACTIVE : RAIL_TAB,
                    tb.x(), tb.y(), tb.w(), tb.h(), 12, 5, 80, 22
                );
                if (!selected && hovered) {
                    ctx.fill(tb.x() + 2, tb.y() + 2, tb.right() - 2, tb.bottom() - 2, 0x22FFE49E);
                }

                // 14x14 crisp pixel-art domain icon (7px inset clears 5.5px brass pillar)
                Identifier icon = isTraceTab ? ICON_TRACE : domainIcon(cat);
                int iconSize = 14;
                int iconX = tb.x() + 7;
                int iconY = Math.round(tb.centerY() - iconSize * 0.5f);
                ctx.blit(icon, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);

                // Right-side count badge (modified count for domains; classic atom ratio for Rule Trace)
                String badgeStr = "";
                int badgeColor = MUTED_TEXT;
                if (isTraceTab) {
                    int classicAtoms = this.editSession.draft().build().countClassic103Atoms();
                    badgeStr = classicAtoms + "/" + ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT;
                    badgeColor = classicAtoms > 0 ? AMBER_TEXT : MUTED_TEXT;
                } else {
                    int catMod = (int) ConfigCatalog.optionsInCategory(cat).stream()
                        .filter(o -> o.isModifiedFromDefault(this.editSession.draft()))
                        .count();
                    if (catMod > 0) {
                        badgeStr = Integer.toString(catMod);
                        badgeColor = EMERALD_TEXT;
                    }
                }

                int badgeW = badgeStr.isEmpty()
                    ? 0
                    : Math.max(14, Math.round(this.font.width(badgeStr) * ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE) + 4);
                int labelX = iconX + iconSize + 4;
                int labelRight = tb.right() - 7 - (badgeW > 0 ? badgeW + 3 : 0);
                Box labelBox = new Box(labelX, tb.y(), Math.max(24, labelRight - labelX), tb.h());
                String title = isTraceTab
                    ? I18n.get("config.chiseled_enchanting_table.trace.tab_title")
                    : I18n.get(cat.titleKey());

                drawClampedLeftText(this.font,
                    ctx,
                    title,
                    labelBox,
                    ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
                    ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
                    selected ? GOLD_TEXT : (isTraceTab ? AMBER_TEXT : CREAM_TEXT)
                );

                if (badgeW > 0) {
                    Box bBox = new Box(tb.right() - 7 - badgeW, tb.y(), badgeW, tb.h());
                    drawClampedCenteredText(this.font,
                        ctx,
                        badgeStr,
                        bBox,
                        ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
                        ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
                        badgeColor
                    );
                }

                int clippedTop = Math.max(rail.y(), tb.y());
                int clippedBot = Math.min(rail.bottom(), tb.bottom());
                if (clippedBot > clippedTop) {
                    Box clickBox = new Box(tb.x(), clippedTop, tb.w(), clippedBot - clippedTop);
                    if (isTraceTab) {
                        this.hitBoxes.add(new HitBox(clickBox, () -> {
                            this.ruleTracePage = true;
                            this.filterModifiedOnly = false;
                            setSearchQuery("");
                            setSearchFocused(false);
                            this.listScroll = 0.0f;
                            playClick(1.02f);
                        }, null));
                    } else {
                        CategoryId targetCat = cat;
                        this.hitBoxes.add(new HitBox(clickBox, () -> {
                            this.ruleTracePage = false;
                            this.filterModifiedOnly = false;
                            this.activeCategory = targetCat;
                            setSearchQuery("");
                            setSearchFocused(false);
                            this.focusedOptionIndex = -1;
                            this.listScroll = 0.0f;
                            playClick(1.0f);
                        }, null));
                    }
                }

                if (hovered) {
                    tip = isTraceTab
                        ? List.of(
                            Component.translatable("config.chiseled_enchanting_table.trace.tab_title").withStyle(ChatFormatting.GOLD),
                            Component.translatable("config.chiseled_enchanting_table.trace.tab_desc").withStyle(ChatFormatting.GRAY)
                        )
                        : List.of(
                            Component.translatable(cat.titleKey()).withStyle(ChatFormatting.GOLD),
                            Component.translatable(cat.subtitleKey()).withStyle(ChatFormatting.GRAY)
                        );
                }
            }
        } finally {
            ctx.disableScissor();
        }
        return tip;
    }

    // ---- L2 / L3 Option Rows & Sticky Collapsible Subsections ----

    private List<Component> renderOptionListViewport(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> tip = null;
        Box vp = this.layout.listViewport();
        Box col = this.layout.listContentColumn();

        List<OptionEntry> flatOptions = flatFilteredOptions();
        List<DisplayItem> items = buildDisplayItems(flatOptions);

        if (items.isEmpty()) {
            this.maxListScroll = 0.0f;
            this.listScroll = 0.0f;
            ctx.enableScissor(vp.x(), vp.y(), vp.right(), vp.bottom());
            try {
                int embSize = 40;
                int ex = Math.round(vp.centerX() - embSize * 0.5f);
                int ey = Math.max(vp.y() + 8, Math.round(vp.centerY() - 32.0f));
                ctx.blit(EMBLEM_TRACE_LARGE, ex, ey, ex + embSize, ey + embSize, 0.0f, 1.0f, 0.0f, 1.0f);
                String emptyMsg = I18n.get(this.filterModifiedOnly
                    ? "config.chiseled_enchanting_table.empty.modified"
                    : "config.chiseled_enchanting_table.empty.search");
                drawClampedCenteredText(this.font,
                    ctx,
                    emptyMsg,
                    new Box(vp.x() + 10, ey + embSize + 6, Math.max(40, vp.w() - 20), 16),
                    ConfigLayoutSpec.PRIMARY_TEXT_SCALE,
                    ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
                    MUTED_TEXT
                );
            } finally {
                ctx.disableScissor();
            }
            return null;
        }

        int totalContentH = computeDisplayHeight(items);
        this.maxListScroll = Math.max(0.0f, totalContentH - vp.h());
        this.listScroll = Mth.clamp(this.listScroll, 0.0f, this.maxListScroll);
        int scrollPx = Math.round(this.listScroll);

        // Determine sticky active section header when scrolled past an expanded section's header
        DisplayItem stickySection = null;
        int cursorCheck = 0;
        for (DisplayItem item : items) {
            if (item.isSectionHeader()) {
                if (cursorCheck < scrollPx && item.sectionExpanded()) {
                    stickySection = item;
                } else if (cursorCheck >= scrollPx) {
                    break;
                }
            }
            cursorCheck += item.height() + ConfigLayoutSpec.ROW_GAP;
        }

        int stickyH = stickySection != null ? ConfigLayoutSpec.SECTION_HEADER_H + 2 : 0;
        this.stickyBodyOffset = stickyH;
        int rowsClipTop = vp.y() + stickyH;

        ctx.enableScissor(vp.x(), rowsClipTop, vp.right(), vp.bottom());
        try {
            int curY = vp.y() - scrollPx;
            for (DisplayItem item : items) {
                int h = item.height();
                if (curY + h > rowsClipTop && curY < vp.bottom()) {
                    if (item.isSectionHeader()) {
                        List<Component> secTip = renderSectionHeaderRow(
                            ctx, item, col.x(), curY, col.w(), mouseX, mouseY, rowsClipTop, vp.bottom()
                        );
                        if (secTip != null && tip == null) tip = secTip;
                    } else if (item.option() != null) {
                        List<Component> rowTip = renderSingleOptionRow(
                            ctx, item.option(), item.flatOptionIndex(), col.x(), curY, col.w(),
                            mouseX, mouseY, rowsClipTop, vp.bottom()
                        );
                        if (rowTip != null && tip == null) tip = rowTip;
                    }
                }
                curY += h + ConfigLayoutSpec.ROW_GAP;
            }
        } finally {
            ctx.disableScissor();
        }

        // Render pinned sticky section header cleanly above scrolling rows
        if (stickySection != null) {
            ctx.enableScissor(vp.x(), vp.y(), vp.right(), vp.y() + stickyH);
            try {
                ctx.fill(col.x(), vp.y(), col.right(), vp.y() + stickyH, 0xEE0E091A);
                List<Component> stTip = renderSectionHeaderRow(
                    ctx, stickySection, col.x(), vp.y(), col.w(), mouseX, mouseY, vp.y(), vp.y() + stickyH
                );
                if (stTip != null) tip = stTip;
            } finally {
                ctx.disableScissor();
            }
        }

        renderListScrollbar(ctx, totalContentH, mouseX, mouseY);
        return tip;
    }

    private List<Component> renderSectionHeaderRow(
        GuiGraphicsExtractor ctx,
        DisplayItem sec,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) {
        Box box = new Box(x, y, w, ConfigLayoutSpec.SECTION_HEADER_H);
        boolean inClip = mouseY >= clipTop && mouseY < clipBottom;
        boolean hovered = inClip && box.contains(mouseX, mouseY);

        blitNineSliceRect(ctx, SECTION_PLAQUE, x, y, w, box.h(), 14, 5, 96, 18);
        if (hovered) {
            ctx.fill(x + 2, y + 2, x + w - 2, y + box.h() - 2, 0x22FFE599);
        }

        RowRenderSupport.drawDisclosureTriangle(
            ctx, x + 9, box.centerY(), sec.sectionExpanded(), GOLD_TEXT
        );

        boolean allGated = sec.sectionActiveCount() == 0 && sec.sectionTotalCount() > 0;
        String rightTag = allGated
            ? ("🔒 " + sec.sectionTotalCount())
            : Integer.toString(sec.sectionTotalCount());
        int rightW = Math.max(20, Math.round(this.font.width(rightTag) * ConfigLayoutSpec.SECONDARY_TEXT_SCALE) + 8);
        Box rightBox = new Box(x + w - 9 - rightW, y, rightW, box.h());
        Box titleBox = new Box(x + 20, y, Math.max(36, rightBox.x() - 4 - (x + 20)), box.h());

        drawClampedLeftText(this.font,
            ctx,
            sec.sectionTitle() == null ? "" : sec.sectionTitle(),
            titleBox,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            allGated ? MUTED_TEXT : GOLD_TEXT
        );
        drawClampedCenteredText(this.font,
            ctx,
            rightTag,
            rightBox,
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            allGated ? AMBER_TEXT : MUTED_TEXT
        );

        int cTop = Math.max(clipTop, y);
        int cBot = Math.min(clipBottom, y + box.h());
        if (cBot > cTop && sec.sectionKey() != null) {
            String key = sec.sectionKey();
            this.hitBoxes.add(new HitBox(new Box(x, cTop, w, cBot - cTop), () -> {
                this.userToggledSections.add(key);
                if (!this.collapsedSections.remove(key)) {
                    this.collapsedSections.add(key);
                }
                playClick(0.98f);
            }, null));
        }

        if (hovered && allGated) {
            return List.of(
                Component.literal(sec.sectionTitle() == null ? "" : sec.sectionTitle()).withStyle(ChatFormatting.GOLD),
                Component.translatable("config.chiseled_enchanting_table.sec.gated_hint").withStyle(ChatFormatting.YELLOW)
            );
        }
        return null;
    }

    private List<Component> renderSingleOptionRow(
        GuiGraphicsExtractor ctx,
        OptionEntry entry,
        int flatIndex,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) { return ConfigOptionRenderer.renderSingleOptionRow(this.viewContext, ctx, entry, flatIndex, x, y, w, mouseX, mouseY, clipTop, clipBottom); }

    private void renderOptionControl(
        GuiGraphicsExtractor ctx,
        OptionEntry entry,
        Box cb,
        boolean editable,
        boolean inClip,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) { ConfigOptionRenderer.renderOptionControl(this.viewContext, ctx, entry, cb, editable, inClip, mouseX, mouseY, clipTop, clipBottom); }

    /**
     * Builds the L3 3-Value Provenance Tooltip:
     * Pending Draft value vs Live Effective value vs 1.1.0 Default (+ 1.0.3 Classic reference),
     * dual-coded effect scope explanation, and parent-atom / governance lock reason.
     */
    private List<Component> buildL3ProvenanceTooltip(OptionEntry entry, OptionAvailability avail) { return ConfigOptionRenderer.buildL3ProvenanceTooltip(this.viewContext, entry, avail); }

    // ---- Rule Trace (1.0.3 <-> 1.1.0) Comparison & Interactive Atom Matrix ----

    private List<Component> renderRuleTraceViewport(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> tip = null;
        Box vp = this.layout.listViewport();
        Box col = this.layout.listContentColumn();

        List<RuleTraceEntry> entries = ConfigCatalog.RULE_TRACE_ENTRIES;
        int totalH = ConfigLayoutSpec.TRACE_BANNER_H + ConfigLayoutSpec.TRACE_CARD_GAP
            + entries.size() * (ConfigLayoutSpec.TRACE_CARD_H + ConfigLayoutSpec.TRACE_CARD_GAP)
            - ConfigLayoutSpec.TRACE_CARD_GAP;
        this.maxListScroll = Math.max(0.0f, totalH - vp.h());
        this.listScroll = Mth.clamp(this.listScroll, 0.0f, this.maxListScroll);
        int scrollPx = Math.round(this.listScroll);

        ctx.enableScissor(vp.x(), vp.y(), vp.right(), vp.bottom());
        try {
            int curY = vp.y() - scrollPx;

            // 1. Top Arcane Emblem & Parity Banner
            if (curY + ConfigLayoutSpec.TRACE_BANNER_H > vp.y() && curY < vp.bottom()) {
                List<Component> bTip = renderTraceBanner(ctx, col.x(), curY, col.w(), mouseX, mouseY, vp.y(), vp.bottom());
                if (bTip != null) tip = bTip;
            }
            curY += ConfigLayoutSpec.TRACE_BANNER_H + ConfigLayoutSpec.TRACE_CARD_GAP;

            // 2. 20 Rule Trace Cards
            for (int idx = 0; idx < entries.size(); idx++) {
                RuleTraceEntry trace = entries.get(idx);
                if (curY + ConfigLayoutSpec.TRACE_CARD_H > vp.y() && curY < vp.bottom()) {
                    List<Component> cTip = renderSingleTraceCard(
                        ctx, idx + 1, trace, col.x(), curY, col.w(), mouseX, mouseY, vp.y(), vp.bottom()
                    );
                    if (cTip != null && tip == null) tip = cTip;
                }
                curY += ConfigLayoutSpec.TRACE_CARD_H + ConfigLayoutSpec.TRACE_CARD_GAP;
            }
        } finally {
            ctx.disableScissor();
        }

        renderListScrollbar(ctx, totalH, mouseX, mouseY);
        return tip;
    }

    private List<Component> renderTraceBanner(
        GuiGraphicsExtractor ctx,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) { return ConfigTraceRenderer.renderTraceBanner(this.viewContext, ctx, x, y, w, mouseX, mouseY, clipTop, clipBottom); }

    private boolean isAtomInClassicState(RuleTraceEntry trace) { return ConfigTraceRenderer.isAtomInClassicState(this.viewContext, trace); }

    private void toggleTraceAtom(RuleTraceEntry trace) { ConfigTraceRenderer.toggleTraceAtom(this.viewContext, trace); }

    private List<Component> renderSingleTraceCard(
        GuiGraphicsExtractor ctx,
        int ordinal,
        RuleTraceEntry trace,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) { return ConfigTraceRenderer.renderSingleTraceCard(this.viewContext, ctx, ordinal, trace, x, y, w, mouseX, mouseY, clipTop, clipBottom); }

    private void renderListScrollbar(GuiGraphicsExtractor ctx, int totalContentH, int mouseX, int mouseY) {
        if (this.maxListScroll <= 0.5f) return;
        Box track = this.layout.scrollbarTrack();
        ctx.fill(track.x(), track.y(), track.right(), track.bottom(), 0xAA100B1C);
        ctx.fill(track.x() + 1, track.y() + 1, track.right() - 1, track.bottom() - 1, 0xFF1F1633);

        int thumbH = Math.max(18, Math.round((float) track.h() * (float) track.h() / (float) Math.max(1, totalContentH)));
        int travel = Math.max(1, track.h() - thumbH);
        int thumbY = track.y() + Math.round((this.listScroll / this.maxListScroll) * travel);
        boolean over = track.contains(mouseX, mouseY) || this.draggingScrollbar;
        ctx.fill(track.x(), thumbY, track.right(), thumbY + thumbH, over ? GOLD_TEXT : 0xFFA88948);
        ctx.fill(track.x() + 1, thumbY + 1, track.right() - 1, thumbY + thumbH - 1, over ? 0xFF7052A8 : 0xFF4C3578);
    }

    // ---- Footer Bar ----

    private List<Component> renderFooterBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> tip = null;
        ModRuntimeConfig preview = this.editSession.draft().build();
        boolean hasUnsaved = !preview.equals(this.editSession.baseline());
        boolean restartChanged = preview.enchantedBookStackSize() != ModRuntimeConfig.get().enchantedBookStackSize();
        boolean stackShrinkRisk = preview.enchantedBookStackSize()
            < ChiseledEnchantingTable.bootEnchantedBookStackSize();

        // 0. Reset Page
        Box resetPageBtn = this.layout.resetPageBtn();
        List<OptionEntry> pageOpts = (this.ruleTracePage && !isSearchMode() && !this.filterModifiedOnly)
            ? ConfigCatalog.ALL_OPTIONS.stream().filter(OptionEntry::isCoreRuleAtom).toList()
            : flatFilteredOptions();
        boolean canResetPage = this.sessionMode.writable()
            && pageOpts.stream().anyMatch(o -> o.isModifiedFromDefault(this.editSession.draft()));
        boolean overResetPage = canResetPage && resetPageBtn.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            canResetPage ? (overResetPage ? BTN_PLAQUE_HOVER : BTN_PLAQUE) : BTN_PLAQUE_DISABLED,
            resetPageBtn.x(), resetPageBtn.y(), resetPageBtn.w(), resetPageBtn.h(), 10, 5, 64, 18
        );
        drawClampedCenteredText(this.font,
            ctx,
            I18n.get("config.chiseled_enchanting_table.btn.reset_page"),
            new Box(resetPageBtn.x() + 5, resetPageBtn.y(), Math.max(16, resetPageBtn.w() - 10), resetPageBtn.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            canResetPage ? CREAM_TEXT : DIM_TEXT
        );
        if (canResetPage) {
            this.hitBoxes.add(new HitBox(resetPageBtn, () -> {
                for (OptionEntry entry : pageOpts) {
                    if (entry.evaluate(this.editSession.draft(), this.sessionMode).editable()) {
                        entry.resetToDefault(this.editSession.draft());
                    }
                }
                syncAutoCollapsedSections();
                playClick(0.92f);
            }, null));
        }

        // 1. Reset All
        Box resetBtn = this.layout.resetAllBtn();
        boolean anyCustom = ConfigCatalog.ALL_OPTIONS.stream().anyMatch(o -> o.isModifiedFromDefault(this.editSession.draft()));
        boolean canResetAll = this.sessionMode.writable() && anyCustom;
        boolean overReset = canResetAll && resetBtn.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            canResetAll ? (overReset ? BTN_PLAQUE_HOVER : BTN_PLAQUE) : BTN_PLAQUE_DISABLED,
            resetBtn.x(), resetBtn.y(), resetBtn.w(), resetBtn.h(), 10, 5, 64, 18
        );
        drawClampedCenteredText(this.font,
            ctx,
            I18n.get("config.chiseled_enchanting_table.btn.reset_all"),
            new Box(resetBtn.x() + 4, resetBtn.y(), Math.max(16, resetBtn.w() - 8), resetBtn.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            canResetAll ? CORAL_TEXT : DIM_TEXT
        );
        if (canResetAll) {
            this.hitBoxes.add(new HitBox(resetBtn, () -> {
                for (OptionEntry entry : ConfigCatalog.ALL_OPTIONS) {
                    entry.resetToDefault(this.editSession.draft());
                }
                this.editSession.draft().customizedKeys.clear();
                syncAutoCollapsedSections();
                playClick(0.88f);
            }, null));
        }

        // 2. Center Status Line
        String statusText;
        int statusColor;
        if (this.saves.pendingRequestId() != 0) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.saving");
            statusColor = GOLD_TEXT;
        } else if (System.currentTimeMillis() < this.saves.toastUntil()) {
            statusText = I18n.get(this.saves.failed()
                ? "config.chiseled_enchanting_table.msg.save_failed"
                : "config.chiseled_enchanting_table.msg.saved");
            statusColor = this.saves.failed() ? CORAL_TEXT : EMERALD_TEXT;
        } else if (stackShrinkRisk) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.stack_shrink_risk");
            statusColor = CORAL_TEXT;
        } else if (restartChanged) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.restart_hint");
            statusColor = AMBER_TEXT;
        } else if (hasUnsaved) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.unsaved");
            statusColor = GOLD_TEXT;
        } else if (preview.isAllClassicAtoms()) {
            statusText = I18n.get("config.chiseled_enchanting_table.msg.classic_1_0_3_active");
            statusColor = AMBER_TEXT;
        } else {
            int classicAtoms = preview.countClassic103Atoms();
            statusText = classicAtoms > 0
                ? I18n.get("config.chiseled_enchanting_table.msg.hybrid_atoms",
                    classicAtoms, ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT)
                : I18n.get("config.chiseled_enchanting_table.msg.synced");
            statusColor = classicAtoms > 0 ? CYAN_TEXT : MUTED_TEXT;
        }
        drawClampedLeftText(this.font,
            ctx,
            statusText,
            this.layout.footerStatusBox(),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            statusColor
        );
        if (this.layout.footerStatusBox().contains(mouseX, mouseY)) {
            tip = List.of(Component.literal(statusText).withStyle(ChatFormatting.GRAY));
        }

        // 3. Close Button
        Box closeBtn = this.layout.closeBtn();
        boolean overClose = closeBtn.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            overClose ? BTN_PLAQUE_HOVER : BTN_PLAQUE,
            closeBtn.x(), closeBtn.y(), closeBtn.w(), closeBtn.h(), 6, 6, 64, 18
        );
        drawClampedCenteredText(this.font,
            ctx,
            I18n.get("config.chiseled_enchanting_table.btn.close"),
            new Box(closeBtn.x() + 4, closeBtn.y(), Math.max(16, closeBtn.w() - 8), closeBtn.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            CREAM_TEXT
        );
        this.hitBoxes.add(new HitBox(closeBtn, () -> {
            playClick(0.95f);
            onClose();
        }, null));

        // 4. Save & Apply Button
        Box saveBtn = this.layout.saveBtn();
        boolean canSave = this.sessionMode.writable();
        boolean overSave = canSave && saveBtn.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            !canSave ? BTN_PLAQUE_DISABLED : (overSave ? BTN_PLAQUE_HOVER : BTN_PLAQUE_GOLD),
            saveBtn.x(), saveBtn.y(), saveBtn.w(), saveBtn.h(), 6, 6, 64, 18
        );
        drawClampedCenteredText(this.font,
            ctx,
            I18n.get("config.chiseled_enchanting_table.btn.save"),
            new Box(saveBtn.x() + 4, saveBtn.y(), Math.max(16, saveBtn.w() - 8), saveBtn.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            canSave ? GOLD_TEXT : DIM_TEXT
        );
        if (canSave) {
            this.hitBoxes.add(new HitBox(saveBtn, () -> saveAndApply(true), () -> saveAndApply(false)));
            if (overSave) {
                tip = List.of(
                    Component.translatable("config.chiseled_enchanting_table.btn.save").withStyle(ChatFormatting.GOLD),
                    Component.translatable("config.chiseled_enchanting_table.btn.save_tip").withStyle(ChatFormatting.GRAY)
                );
            }
        }

        return tip;
    }

    // ---- Mouse & Keyboard Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (this.layout == null) recomputeLayout();
        double mx = event.x();
        double my = event.y();

        if (!this.layout.searchBox().contains(mx, my)) {
            setSearchFocused(false);
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (SliderTrack slider : this.sliderTracks) {
                if (slider.contains(mx, my)) {
                    this.sliderDrag.begin(slider.entry(), slider.box(), this.editSession.draft(), mx);
                    syncAutoCollapsedSections();
                    playClick(1.02f);
                    return true;
                }
            }
            if (this.maxListScroll > 0.5f && this.layout.scrollbarTrack().contains(mx, my)) {
                this.draggingScrollbar = true;
                updateListScrollFromMouse(my);
                return true;
            }
        }

        // Iterate in reverse so topmost elements (e.g. sticky section header, clear 'x' inside search) win cleanly.
        for (int i = this.hitBoxes.size() - 1; i >= 0; i--) {
            HitBox hb = this.hitBoxes.get(i);
            if (hb.contains(mx, my)) {
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    hb.onLeftClick().run();
                    return true;
                } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hb.onRightClick() != null) {
                    hb.onRightClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.sliderDrag.active() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.sliderDrag.drag(this.editSession.draft(), event.x());
            syncAutoCollapsedSections();
            return true;
        }
        if (this.draggingScrollbar && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            updateListScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.sliderDrag.release();
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void updateListScrollFromMouse(double my) {
        Box track = this.layout.scrollbarTrack();
        float ratio = Mth.clamp((float) ((my - track.y()) / Math.max(1.0, track.h())), 0.0f, 1.0f);
        this.listScroll = ratio * this.maxListScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.layout == null) recomputeLayout();
        if (this.maxRailScroll > 0.5f && this.layout.railViewport().contains(mouseX, mouseY)) {
            this.railScroll = Mth.clamp(this.railScroll - (float) (scrollY * 18.0), 0.0f, this.maxRailScroll);
            return true;
        }
        if (this.maxListScroll > 0.5f && this.layout.listViewport().contains(mouseX, mouseY)) {
            this.listScroll = Mth.clamp(this.listScroll - (float) (scrollY * 22.0), 0.0f, this.maxListScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchInput.focused()) {
            if (this.searchBuffer.insertCodePoint(event.codepoint())) {
                syncSearchQueryFromBuffer();
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.searchInput.focused()) {
            if (key == InputConstants.KEY_ESCAPE) {
                if (this.searchBuffer.hasSelection()) {
                    this.searchBuffer.collapseSelection();
                } else if (!this.searchBuffer.isEmpty()) {
                    setSearchQuery("");
                } else {
                    setSearchFocused(false);
                }
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                setSearchFocused(false);
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (this.searchBuffer.deleteBackward()) {
                    syncSearchQueryFromBuffer();
                }
                return true;
            }
            if (key == InputConstants.KEY_DELETE) {
                if (this.searchBuffer.deleteForward()) {
                    syncSearchQueryFromBuffer();
                }
                return true;
            }
            if (key == InputConstants.KEY_LEFT) {
                this.searchBuffer.moveByCodePoints(-1, event.hasShiftDown());
                return true;
            }
            if (key == InputConstants.KEY_RIGHT) {
                this.searchBuffer.moveByCodePoints(1, event.hasShiftDown());
                return true;
            }
            if (key == InputConstants.KEY_HOME) {
                this.searchBuffer.moveHome(event.hasShiftDown());
                return true;
            }
            if (key == InputConstants.KEY_END) {
                this.searchBuffer.moveEnd(event.hasShiftDown());
                return true;
            }
            if (event.hasControlDown()) {
                if (key == InputConstants.KEY_A) {
                    this.searchBuffer.selectAll();
                    return true;
                }
                if (key == InputConstants.KEY_C && this.minecraft != null) {
                    if (this.searchBuffer.hasSelection()) {
                        this.minecraft.keyboardHandler.setClipboard(this.searchBuffer.selectedText());
                    }
                    return true;
                }
                if (key == InputConstants.KEY_X && this.minecraft != null) {
                    String cut = this.searchBuffer.cutSelection();
                    if (!cut.isEmpty()) {
                        this.minecraft.keyboardHandler.setClipboard(cut);
                        syncSearchQueryFromBuffer();
                    }
                    return true;
                }
                if (key == InputConstants.KEY_V && this.minecraft != null) {
                    String pasted = this.minecraft.keyboardHandler.getClipboard();
                    if (this.searchBuffer.insertText(pasted)) {
                        syncSearchQueryFromBuffer();
                    }
                    return true;
                }
            }
            if (this.minecraft != null && this.minecraft.options.keyInventory.matches(event)) {
                // Keep 'E' (inventory key) from closing the screen while typing in search.
                return true;
            }
            return super.keyPressed(event);
        }

        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_SLASH || (key == InputConstants.KEY_F && event.hasControlDown())) {
            setSearchFocused(true);
            playClick(1.0f);
            return true;
        }

        // Validated table-driven domain hotkeys + Rule Trace hotkey (GUI-07)
        if (key == RULE_TRACE_HOTKEY) {
            this.ruleTracePage = true;
            this.filterModifiedOnly = false;
            setSearchQuery("");
            this.listScroll = 0.0f;
            playClick(1.02f);
            return true;
        }
        for (var entry : CATEGORY_HOTKEYS.entrySet()) {
            if (entry.getValue() == key) {
                this.ruleTracePage = false;
                this.filterModifiedOnly = false;
                this.activeCategory = entry.getKey();
                setSearchQuery("");
                this.focusedOptionIndex = -1;
                this.listScroll = 0.0f;
                playClick(1.0f);
                return true;
            }
        }

        if (!this.ruleTracePage || isSearchMode() || this.filterModifiedOnly) {
            List<DisplayItem> displayItems = buildDisplayItems(flatFilteredOptions());
            List<DisplayItem> optionItems = displayItems.stream().filter(d -> !d.isSectionHeader()).toList();
            if (!optionItems.isEmpty()) {
                if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                    int delta = (key == InputConstants.KEY_UP || (key == InputConstants.KEY_TAB && event.hasShiftDown())) ? -1 : 1;
                    if (this.focusedOptionIndex < 0) {
                        this.focusedOptionIndex = delta > 0 ? 0 : optionItems.size() - 1;
                    } else {
                        this.focusedOptionIndex = Math.floorMod(this.focusedOptionIndex + delta, optionItems.size());
                    }
                    scrollOptionIntoView(displayItems, this.focusedOptionIndex);
                    playClick(1.0f);
                    return true;
                }
                if (this.focusedOptionIndex >= 0 && this.focusedOptionIndex < optionItems.size()) {
                    OptionEntry entry = optionItems.get(this.focusedOptionIndex).option();
                    if (entry != null) {
                        OptionAvailability avail = entry.evaluate(this.editSession.draft(), this.sessionMode);
                        if (avail.editable()) {
                            if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
                                int dir = key == InputConstants.KEY_RIGHT ? 1 : -1;
                                if (entry.kind() == ControlKind.BOOLEAN || entry.kind() == ControlKind.ENUM_CYCLE) {
                                    entry.toggleOrCycle(this.editSession.draft(), dir);
                                } else {
                                    entry.stepValue(this.editSession.draft(), event.hasShiftDown() ? dir * 5 : dir);
                                }
                                syncAutoCollapsedSections();
                                playClick(dir > 0 ? 1.04f : 0.96f);
                                return true;
                            }
                            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) {
                                if (entry.kind() == ControlKind.BOOLEAN || entry.kind() == ControlKind.ENUM_CYCLE) {
                                    entry.toggleOrCycle(this.editSession.draft(), 1);
                                    syncAutoCollapsedSections();
                                    playClick(1.04f);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.keyPressed(event);
    }

    private void scrollOptionIntoView(List<DisplayItem> items, int targetFlatIdx) {
        int top = 0;
        for (DisplayItem item : items) {
            if (!item.isSectionHeader() && item.flatOptionIndex() == targetFlatIdx) {
                int viewH = Math.max(40, this.layout.listViewport().h() - this.stickyBodyOffset);
                if (top < this.listScroll) {
                    this.listScroll = Mth.clamp(top, 0.0f, this.maxListScroll);
                } else if (top + item.height() > this.listScroll + viewH) {
                    this.listScroll = Mth.clamp(top + item.height() - viewH, 0.0f, this.maxListScroll);
                }
                return;
            }
            top += item.height() + ConfigLayoutSpec.ROW_GAP;
        }
    }

    private final ConfigViewContext viewContext = new ConfigViewContext() {
        public net.minecraft.client.gui.Font font() { return ChiseledEnchantingConfigScreen.this.font; }
        public Draft draft() { return editSession.draft(); }
        public Draft defaults() { return defaultReferenceDraft; }
        public Draft classicReference() { return classicReferenceDraft; }
        public Draft displayed(OptionEntry entry) { return displayedDraft(entry); }
        public SessionMode mode() { return sessionMode; }
        public int focusedIndex() { return focusedOptionIndex; }
        public void registerHit(HitBox hit) { hitBoxes.add(hit); }
        public void registerSlider(SliderTrack slider) { sliderTracks.add(slider); }
        public void changed() { syncAutoCollapsedSections(); }
        public void sound(float pitch) { playClick(pitch); }
        public void navigate(CategoryId category) {
            ruleTracePage = false;
            filterModifiedOnly = false;
            activeCategory = category;
            setSearchQuery("");
            listScroll = 0.0f;
        }
    };
}
