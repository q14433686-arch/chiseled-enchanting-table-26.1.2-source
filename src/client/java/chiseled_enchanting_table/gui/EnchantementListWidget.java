package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.gui.InscribeOptionModel.DisplayOptionSlot;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.row.CategoryRowRenderer;
import chiseled_enchanting_table.gui.row.EnchantmentRowRenderer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.gui.row.TransformationRowRenderer;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy spelling preserved as a binary/source compatibility bridge; prefer
 * {@link EnchantmentListWidget} in new code.
 *
 * @deprecated Prefer {@link EnchantmentListWidget}.
 */
@Deprecated(forRemoval = false)
public class EnchantementListWidget extends AbstractSelectionList<EnchantementListWidget.EnchantUiEntry> {
    public enum UiTabMode {
        INSCRIBE,
        CLEANSE,
        TOMES
    }

    public Set<EnchantmentWithLevel> unlocked_enchantements = null;
    public ItemStack enchantable_item;
    public ItemStack cost_item_stack;
    public Player player;
    public ChiseledEnchantingTableScreenHandler handler;
    private UiTabMode tabMode = UiTabMode.INSCRIBE;
    final chiseled_enchanting_table.catalog.TomeCatalog catalogue = new chiseled_enchanting_table.catalog.TomeCatalog();
    public final InscribeFilterBar inscribeFilter = new InscribeFilterBar();
    public final CleanseFilterBar cleanseFilter = new CleanseFilterBar();
    private final Set<Identifier> expandedInscribeCategories = new HashSet<>();
    private final Set<Identifier> visibleInscribeGroupIds = new HashSet<>();
    private final Set<Identifier> expandedCleanseCategories = new HashSet<>();
    private final Set<Identifier> visibleCleanseGroupIds = new HashSet<>();
    private ItemStack lastTargetSnapshot = ItemStack.EMPTY;

    public EnchantementListWidget(
        Minecraft client,
        ChiseledEnchantingTableScreenHandler handler,
        int width,
        int height,
        int y,
        int itemHeight
    ) {
        super(client, width, height, y, itemHeight);
        this.handler = handler;
        this.enchantable_item = handler.getEffectiveEnchantableItem();
        this.cost_item_stack = handler.get_cost_item();
        this.unlocked_enchantements = handler.unlocked_enchantements;
        this.player = handler.player;
        this.setFocused(null);
    }

    public UiTabMode tabMode() {
        return this.tabMode;
    }

    private static chiseled_enchanting_table.catalog.TomeCatalog.Key tomeKey(ChiseledEnchantingMenuPayloads.TomeBookEntry book) {
        return new chiseled_enchanting_table.catalog.TomeCatalog.Key(book.shelf_pos().asLong(), book.slot());
    }
    public boolean isTomeSelected(ChiseledEnchantingMenuPayloads.TomeBookEntry entry) {
        return entry != null && catalogue.isSelected(tomeKey(entry));
    }
    public void selectTome(@Nullable ChiseledEnchantingMenuPayloads.TomeBookEntry entry) {
        if (entry == null) catalogue.clearSelection(); else catalogue.select(tomeKey(entry), false);
    }
    public java.util.List<ChiseledEnchantingMenuPayloads.TomeBookEntry> selectedTomes() {
        return this.handler.tomeBooks.stream().filter(this::isTomeSelected).toList();
    }
    @Nullable
    public ChiseledEnchantingMenuPayloads.TomeBookEntry selectedTomeEntry() {
        var selected = selectedTomes();
        if (!selected.isEmpty()) return selected.getFirst();
        // 26.1.2 adaptation (deliberate deviation, documented in the backport report):
        // panel-detail fallback only — shows the first currently visible tome without
        // mutating the explicit multi-selection, preserving the 1.1.0 single-book workflow.
        if (this.handler == null || this.handler.tomeBooks == null || this.handler.tomeBooks.isEmpty()) return null;
        for (var row : catalogue.visible()) {
            for (var book : this.handler.tomeBooks) {
                if (tomeKey(book).equals(row.key())) return book;
            }
        }
        return this.handler.tomeBooks.getFirst();
    }
    void refreshDirectory() {
        updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        setScrollAmount(0);
    }

    public void setTabMode(UiTabMode nextMode) {
        if (nextMode != null && this.tabMode != nextMode) {
            this.inscribeFilter.unfocus();
            this.cleanseFilter.unfocus();
            this.tabMode = nextMode;
            this.setFocused(null);
            this.setScrollAmount(0);
            this.updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        }
    }

    @Nullable
    public Identifier hoveredMasterBackedInscribeId(double mouseX, double mouseY) {
        if (this.tabMode != UiTabMode.INSCRIBE || !this.isMouseOver(mouseX, mouseY)) return null;
        for (EnchantUiEntry entry : this.children()) {
            if (!entry.isMouseOver(mouseX, mouseY) || entry.is_transformation || entry.enchantment_id == null) continue;
            if (entry.is_category_header) return (!entry.cleanse_theme && this.handler.storedMasterLevel(entry.enchantment_id) > 0) ? entry.enchantment_id : null;
            if (entry.option_type == OptionType.ENCHANTMENT && !entry.cost_item_stack.is(Items.ENCHANTED_BOOK)
                && this.handler.storedMasterLevel(entry.enchantment_id) >= entry.enchant_level) return entry.enchantment_id;
        }
        return null;
    }

    public void jumpToTomesForEnchantment(Identifier enchantId) {
        this.inscribeFilter.unfocus(); this.cleanseFilter.unfocus();
        this.catalogue.reset(); this.catalogue.enchantment = enchantId.toString();
        this.tabMode = UiTabMode.TOMES; this.setFocused(null); this.setScrollAmount(0);
        this.updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        this.catalogue.selectVisible();
    }

    public boolean areAllCleanseCategoriesExpanded() {
        return !this.visibleCleanseGroupIds.isEmpty() && this.expandedCleanseCategories.containsAll(this.visibleCleanseGroupIds);
    }

    public void toggleExpandAllCleanseCategories() {
        if (areAllCleanseCategoriesExpanded()) this.expandedCleanseCategories.clear();
        else this.expandedCleanseCategories.addAll(this.visibleCleanseGroupIds);
        refreshInscribeFilter();
    }

    public boolean areAllInscribeCategoriesExpanded() {
        return !this.visibleInscribeGroupIds.isEmpty() && this.expandedInscribeCategories.containsAll(this.visibleInscribeGroupIds);
    }

    public void toggleExpandAllInscribeCategories() {
        if (areAllInscribeCategoriesExpanded()) this.expandedInscribeCategories.clear();
        else this.expandedInscribeCategories.addAll(this.visibleInscribeGroupIds);
        refreshInscribeFilter();
    }

    public void refreshInscribeFilter() {
        double savedScroll = this.scrollAmount();
        this.updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        this.setScrollAmount(Math.min(savedScroll, this.maxScrollAmount()));
    }

    public boolean isCategoryExpanded(Identifier categoryId) {
        return (this.tabMode == UiTabMode.CLEANSE ? this.expandedCleanseCategories : this.expandedInscribeCategories).contains(categoryId);
    }

    public void toggleCategory(Identifier categoryId) {
        Set<Identifier> set = (this.tabMode == UiTabMode.CLEANSE) ? this.expandedCleanseCategories : this.expandedInscribeCategories;
        if (!set.remove(categoryId)) set.add(categoryId);
        refreshInscribeFilter();
    }

    @Override
    protected int addEntry(EnchantUiEntry entry) {
        entry.cachedRowIndex = this.children().size();
        return super.addEntry(entry);
    }

    public static boolean isWearImmuneEnchantmentId(Identifier id, @Nullable Player player) { return EnchantmentDisplayNames.isWearImmuneEnchantmentId(id, player); }

    public static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Player player) { return EnchantmentDisplayNames.findEnchantment(id, player); }

    public static Holder<Enchantment> identifierToEnchantment(Identifier id, Player player) { return EnchantmentDisplayNames.identifierToEnchantment(id, player); }

    public static Identifier enchantmentToIdentifier(Holder<Enchantment> enchant) { return EnchantmentDisplayNames.enchantmentToIdentifier(enchant); }

    /** @deprecated Prefer {@link #identifierToEnchantment(Identifier, Player)}. */
    @Deprecated(forRemoval = false)
    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Player player) {
        return identifierToEnchantment(id, player);
    }

    /** @deprecated Prefer {@link #enchantmentToIdentifier(Holder)}. */
    @Deprecated(forRemoval = false)
    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant, Player player) {
        return enchantmentToIdentifier(enchant);
    }

    public static String baseEnchantmentName(Holder<Enchantment> ench) { return EnchantmentDisplayNames.baseEnchantmentName(ench); }

    private static String romanNumeral(int level) { return EnchantmentDisplayNames.romanNumeral(level); }

    public String displayName(AvailableOption option) { return EnchantmentDisplayNames.displayName(this.handler, this.player, option); }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.tabMode == UiTabMode.TOMES
            && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_A
            && event.hasControlDown()
            && !catalogue.visible().isEmpty()) {
            catalogue.selectVisible();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
            || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            var focusedEntry = this.getFocused();
            if (focusedEntry != null && focusedEntry.serverSendApplyEnchant()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    public int saveNavigationIfSameItem(ItemStack enchantable_item, ItemStack cost_item_stack) {
        var previousIsBook = this.cost_item_stack.is(Items.ENCHANTED_BOOK) || this.cost_item_stack.is(Items.BOOK);
        var nextIsBook = cost_item_stack.is(Items.ENCHANTED_BOOK) || cost_item_stack.is(Items.BOOK);
        if (previousIsBook || nextIsBook) return -1;
        var focusedIndex = -1;
        if (ItemStack.isSameItem(enchantable_item, this.enchantable_item)) {
            var focused = this.getFocused();
            if (focused != null) {
                focusedIndex = this.children().indexOf(focused);
            }
        }
        return focusedIndex;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.getX() - 4 && mouseX <= this.getX() + this.width + 14 && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
            this.setScrollAmount(this.scrollAmount() - scrollY * this.defaultEntryHeight * 1.15);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Jumps proportionally along the scrollbar track when the player clicks outside the current
     * scroller thumb, while preserving smooth thumb-anchored dragging when clicking inside the thumb.
     */
    public void jumpScrollbarToMouseY(double mouseY) {
        int maxScroll = this.maxScrollAmount();
        if (maxScroll <= 0) {
            return;
        }
        int totalContentH = this.height + maxScroll;
        int scrollerH = Math.clamp(
            (int) ((float) (this.height * this.height) / (float) Math.max(1, totalContentH)),
            32,
            Math.max(32, this.height - 8)
        );
        int travel = Math.max(1, this.height - scrollerH);
        double thumbTop = this.getY() + (this.scrollAmount() / (double) maxScroll) * travel;
        double thumbBottom = thumbTop + scrollerH;
        if (mouseY < thumbTop || mouseY > thumbBottom) {
            double centeredRatio = Math.clamp((mouseY - this.getY() - scrollerH * 0.5) / (double) travel, 0.0, 1.0);
            this.setScrollAmount(centeredRatio * maxScroll);
        }
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.isDragging()) {
            int maxScroll = this.maxScrollAmount();
            if (maxScroll > 0) {
                int totalContentH = this.height + maxScroll;
                int scrollerH = Math.clamp((int) ((float) (this.height * this.height) / (float) Math.max(1, totalContentH)), 32, this.height - 8);
                int travel = Math.max(1, this.height - scrollerH);
                double pixelsPerMousePixel = (double) maxScroll / (double) travel;
                this.setScrollAmount(this.scrollAmount() + dragY * pixelsPerMousePixel);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor ctx) {
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    @Override
    public int getRowLeft() {
        return this.getX();
    }

    @Override
    public int getRowRight() {
        return this.getRowLeft() + this.getRowWidth();
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width;
    }

    public void resumeNavigation(int index) {
        if (index >= 0 && index < this.children().size()) {
            var focusedEntry = this.children().get(index);
            this.setFocused(focusedEntry);
        }
    }

    @Nullable
    public ItemStack hoveredCostItem(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            ItemStack hovered = entry.hoveredCostItem(mouseX, mouseY);
            if (hovered != null && !hovered.isEmpty()) {
                return hovered;
            }
        }
        return null;
    }

    @Nullable
    public Identifier hoveredEnchantmentId(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            ItemStack hovered = entry.hoveredCostItem(mouseX, mouseY);
            if (hovered != null && !hovered.isEmpty()) {
                return entry.enchantment_id;
            }
        }
        return null;
    }

    @Nullable
    public OptionType hoveredOptionType(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            ItemStack hovered = entry.hoveredCostItem(mouseX, mouseY);
            if (hovered != null && !hovered.isEmpty()) {
                return entry.option_type;
            }
        }
        return null;
    }

    @Nullable
    public EnchantUiEntry hoveredXpBadgeEntry(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            if (!entry.is_category_header && entry.isOverXpBadge(mouseX, mouseY)) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public ChiseledEnchantingMenuPayloads.TomeBookEntry hoveredTomeEntry(double mouseX, double mouseY) {
        if (this.tabMode != UiTabMode.TOMES) return null;
        for (EnchantUiEntry entry : this.children()) {
            if (entry.is_tome_entry && entry.tome_book != null && entry.isMouseOver(mouseX, mouseY)) return entry.tome_book;
        }
        return null;
    }

    @Nullable
    public EnchantUiEntry hoveredActionRowEntry(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            if (entry.is_category_header || entry.is_tome_entry) continue;
            int dx = entry.effectiveDx();
            int dy = entry.getY();
            int dw = entry.effectiveDw();
            int dh = entry.getHeight();
            if (mouseX >= dx && mouseX <= dx + dw && mouseY >= dy && mouseY <= dy + dh) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public EnchantUiEntry hoveredSpecialRowEntry(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            int dx = entry.is_category_header ? (entry.getX() + 1) : entry.effectiveDx();
            int dy = entry.getY();
            int dw = entry.is_category_header ? (entry.getWidth() - 2) : entry.effectiveDw();
            int dh = entry.getHeight();
            if (mouseX >= dx && mouseX <= dx + dw && mouseY >= dy && mouseY <= dy + dh) {
                if (entry.shelf_locked || entry.discovery_locked) {
                    return entry;
                }
                if (entry.is_category_header && !entry.cleanse_theme) {
                    return entry;
                }
                if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(entry.enchantment_id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    @Nullable
    public List<List<ItemStack>> hoveredCostOptions(double mouseX, double mouseY) {
        if (mouseX < this.getX() || mouseX > this.getX() + this.width || mouseY < this.getY() || mouseY > this.getY() + this.height) {
            return null;
        }
        for (EnchantUiEntry entry : this.children()) {
            List<List<ItemStack>> options = entry.hoveredCostOptions(mouseX, mouseY);
            if (options != null && !options.isEmpty()) {
                return options;
            }
        }
        return null;
    }

    public void updateEntry(ItemStack enchantable_item, ItemStack cost_item_stack) {
        boolean targetChanged = !ItemStack.isSameItem(enchantable_item, this.lastTargetSnapshot);
        double previousScroll = this.scrollAmount();
        var navigationIndex = saveNavigationIfSameItem(enchantable_item, cost_item_stack);

        this.clearEntries();
        if (targetChanged) {
            this.setScrollAmount(0);
            this.lastTargetSnapshot = enchantable_item.copy();
        }
        this.enchantable_item = enchantable_item;
        this.cost_item_stack = cost_item_stack;

        if (this.tabMode == UiTabMode.TOMES) {
            populateTomeEntries(previousScroll);
            return;
        }
        if (this.tabMode == UiTabMode.CLEANSE) {
            populateCleanseEntries(navigationIndex, targetChanged, previousScroll);
            return;
        }
        populateInscribeEntries(navigationIndex, targetChanged, previousScroll);
    }

    private void populateTomeEntries(double previousScroll) {
        if (!this.handler.isTomePageUnlocked()) {
            selectTome(null);
            return;
        }
        catalogue.update(this.handler.tomeBooks.stream().map(book -> new chiseled_enchanting_table.catalog.TomeCatalog.Row(
            tomeKey(book), book.enchantments().stream().map(e -> e.enchantment_id().toString()).toList(),
            book.enchantments().stream().map(e -> EnchantmentDisplayNames.findEnchantment(e.enchantment_id(), this.player)
                .map(h -> h.value().description().getString()).orElse(e.enchantment_id().toString()))
                .sorted().collect(java.util.stream.Collectors.joining(" / ")),
            book.remaining_charges(), book.effective_max_uses(), book.locked(), book.locked_by_viewer())).toList());
        var books = new java.util.HashMap<chiseled_enchanting_table.catalog.TomeCatalog.Key, ChiseledEnchantingMenuPayloads.TomeBookEntry>();
        for (var book : this.handler.tomeBooks) books.put(tomeKey(book), book);
        for (var row : catalogue.visible()) this.addEntry(EnchantUiEntry.tomeEntry(minecraft, this.handler, books.get(row.key()), this));
        this.setScrollAmount(Math.min(previousScroll, this.maxScrollAmount()));
    }

    static String tomeTitle(ChiseledEnchantingMenuPayloads.TomeBookEntry book, Player player) { return TomeDisplayNames.tomeTitle(book, player); }

    static String formatTomeEta(int ticks) { return TomeDisplayNames.formatTomeEta(ticks); }

    private void addActionEntry(AvailableOption opt, boolean nested, boolean lastChild) {
        addActionEntry(opt, nested, lastChild, false, 0);
    }

    private EnchantUiEntry buildActionEntry(
        AvailableOption opt, boolean nested, boolean lastChild, boolean shelfLocked, int requiredBookshelves
    ) {
        boolean enchantFromBook = this.cost_item_stack.is(Items.ENCHANTED_BOOK);
        var ewl = opt.asEnchantmentWithLevel();
        List<List<ItemStack>> costOpts = opt.isBookCombineUpgrade() || (enchantFromBook && !opt.isBookResearchUpgrade() && !opt.isCleanseOperation())
            ? List.of() : this.handler.getEffectiveItemCostOptions(ewl, opt.type());
        var xpReq = (enchantFromBook && !opt.isBookUpgrade() && !opt.isCleanseOperation())
            ? XpCostConversion.XpRequirement.FREE : this.handler.resolveEffectiveXpRequirement(ewl, opt.type());
        return EnchantUiEntry.actionEntry(minecraft, this.handler, opt, costOpts, xpReq, this, nested, lastChild, shelfLocked, requiredBookshelves);
    }

    private void addActionEntry(AvailableOption opt, boolean nested, boolean lastChild, boolean shelfLocked, int requiredBookshelves) {
        this.addEntry(buildActionEntry(opt, nested, lastChild, shelfLocked, requiredBookshelves));
    }

    private void populateCleanseEntries(int navigationIndex, boolean targetChanged, double previousScroll) {
        CleanseListSupport.populate(
            this, minecraft, this.expandedCleanseCategories, this.visibleCleanseGroupIds,
            opt -> buildActionEntry(opt, false, false, false, 0),
            opt -> buildActionEntry(opt, true, false, false, 0),
            this::addEntry, navigationIndex, targetChanged, previousScroll
        );
    }





    private void populateInscribeEntries(int navigationIndex, boolean targetChanged, double previousScroll) {
        this.visibleInscribeGroupIds.clear();
        ItemEnchantments targetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
        var model = InscribeOptionModel.build(this.optionCatalog, this.player, enchantable_item, cost_item_stack,
            unlocked_enchantements, this::displayName);
        var pinnedTop = model.pinned();
        var groupedEnchants = model.groups();
        String query = this.inscribeFilter.query();
        boolean onlyAffordable = this.inscribeFilter.affordableOnly();

        for (DisplayOptionSlot pinned : pinnedTop) {
            EnchantUiEntry entry = buildActionEntry(pinned.option(), false, false, pinned.shelfLocked(), pinned.requiredBookshelves());
            if ((!onlyAffordable || entry.canEnchant())
                && InscribeFilterBar.matchesQuery(query, displayName(pinned.option()), pinned.option().id(), entry.cost_options)) {
                this.addEntry(entry);
            }
        }

        // Auto-expand when there is only 1 enchantment group available (e.g. single-book transfer/combine/upgrade)
        if (groupedEnchants.size() == 1) {
            this.expandedInscribeCategories.add(groupedEnchants.keySet().iterator().next());
        }

        // 2. Per-attribute collapsible horizontal tabs with crisp ▶ / ▼ triangles & indented tier children
        for (var groupEntry : groupedEnchants.entrySet()) {
            Identifier enchId = groupEntry.getKey();
            List<DisplayOptionSlot> children = groupEntry.getValue();
            Holder<Enchantment> holder = identifierToEnchantment(enchId, this.player);
            String categoryName = baseEnchantmentName(holder);
            List<EnchantUiEntry> matchedChildren = new ArrayList<>(children.size());
            for (DisplayOptionSlot child : children) {
                EnchantUiEntry built = buildActionEntry(child.option(), true, false, child.shelfLocked(), child.requiredBookshelves());
                if ((!onlyAffordable || built.canEnchant())
                    && (InscribeFilterBar.matchesQuery(query, categoryName, enchId, built.cost_options)
                        || InscribeFilterBar.matchesQuery(query, displayName(child.option()), child.option().id(), built.cost_options))) {
                    matchedChildren.add(built);
                }
            }
            if (matchedChildren.isEmpty()) continue;
            this.visibleInscribeGroupIds.add(enchId);

            int curLvl = targetEnchantments.getLevel(holder);
            int minTier = children.getFirst().option().level();
            int maxTier = children.getLast().option().level();
            int maxUnlockedTier = children.stream().filter(s -> !s.shelfLocked()).mapToInt(s -> s.option().level()).max().orElse(0);

            String badgeText;
            int badgeColor;
            if (curLvl > 0 && maxUnlockedTier > curLvl) {
                badgeText = maxUnlockedTier < maxTier
                    ? ("\u2B06 " + romanNumeral(curLvl) + "\u2192" + romanNumeral(maxUnlockedTier) + "/" + romanNumeral(maxTier))
                    : ("\u2B06 " + romanNumeral(curLvl) + "\u2192" + romanNumeral(maxUnlockedTier));
                badgeColor = RowRenderSupport.LIGHT_GREEN;
            } else if (curLvl > 0 && maxTier > curLvl && maxUnlockedTier <= curLvl) {
                badgeText = "🔒 " + romanNumeral(curLvl + 1);
                badgeColor = RowRenderSupport.LIGHT_RED;
            } else if (curLvl > 0 && maxUnlockedTier == curLvl) {
                badgeText = "\u2714 " + romanNumeral(curLvl);
                badgeColor = RowRenderSupport.CYAN;
            } else if (maxUnlockedTier > 0 && maxUnlockedTier < maxTier) {
                badgeText = (minTier == maxUnlockedTier ? romanNumeral(minTier) : (romanNumeral(minTier) + "\u2013" + romanNumeral(maxUnlockedTier)))
                    + "/" + romanNumeral(maxTier);
                badgeColor = RowRenderSupport.EPIPHANY_GOLD;
            } else if (minTier == maxTier) {
                badgeText = romanNumeral(minTier);
                badgeColor = RowRenderSupport.ARCANE_PURPLE;
            } else {
                badgeText = romanNumeral(minTier) + "\u2013" + romanNumeral(maxTier);
                badgeColor = RowRenderSupport.EPIPHANY_GOLD;
            }

            boolean open = this.inscribeFilter.hasActiveFilter() || isCategoryExpanded(enchId);
            this.addEntry(EnchantUiEntry.categoryEntry(minecraft, this.handler, enchId, categoryName, badgeText, badgeColor, 0, false, this));
            if (open) {
                for (int idx = 0; idx < matchedChildren.size(); idx++) {
                    EnchantUiEntry childEntry = matchedChildren.get(idx);
                    childEntry.last_child = (idx == matchedChildren.size() - 1);
                    this.addEntry(childEntry);
                }
            }
        }

        resumeNavigation(navigationIndex);
        if (!targetChanged) {
            this.setScrollAmount(Math.min(previousScroll, this.maxScrollAmount()));
        }
    }

    public static class EnchantUiEntry extends AbstractSelectionList.Entry<EnchantUiEntry> {
        public ItemStack enchantable_item;
        public ItemStack cost_item_stack;
        public int enchant_level;
        public boolean is_transformation;
        public boolean is_category_header;
        public boolean nested_child;
        public boolean last_child;
        public boolean shelf_locked;
        public boolean discovery_locked;
        public int required_bookshelves;
        public int conflict_refund_points;
        public boolean cleanse_theme;
        public String category_title;
        public String category_badge;
        public int category_badge_color;
        public int category_refund_points;
        public OptionType option_type;
        public Holder<Enchantment> enchantment;
        public Identifier enchantment_id;
        public List<List<ItemStack>> cost_options;
        public XpCostConversion.XpRequirement xp_requirement;
        public int xp_level_cost;
        public Minecraft client;
        public Player player;
        public ArrayList<EnchantmentWithLevel> overridenEnchant;
        public OptionalInt existingSameLevel = OptionalInt.empty();
        public OptionalInt sameEnchantLevelDiff = OptionalInt.empty();
        public ChiseledEnchantingTableScreenHandler handler;
        public EnchantementListWidget parent;
        public boolean is_tome_entry;
        public int cachedRowIndex = -1;
        @Nullable
        public ChiseledEnchantingMenuPayloads.TomeBookEntry tome_book;

        public static EnchantUiEntry categoryEntry(
            Minecraft client, ChiseledEnchantingTableScreenHandler handler, Identifier categoryId,
            String categoryTitle, String badgeText, int badgeColor, int refundPreviewPoints,
            boolean cleanseTheme, EnchantementListWidget parent
        ) {
            EnchantUiEntry entry = new EnchantUiEntry();
            entry.client = client;
            entry.handler = handler;
            entry.parent = parent;
            entry.enchantable_item = handler.getEffectiveEnchantableItem();
            entry.cost_item_stack = handler.get_cost_item();
            entry.player = handler.player;
            entry.is_category_header = true;
            entry.cleanse_theme = cleanseTheme;
            entry.enchantment_id = categoryId;
            entry.category_title = categoryTitle;
            entry.category_badge = badgeText;
            entry.category_badge_color = badgeColor;
            entry.category_refund_points = refundPreviewPoints;
            entry.option_type = OptionType.ENCHANTMENT;
            entry.cost_options = List.of();
            entry.xp_requirement = XpCostConversion.XpRequirement.FREE;
            entry.overridenEnchant = new ArrayList<>();
            return entry;
        }

        public static EnchantUiEntry tomeEntry(
            Minecraft client,
            ChiseledEnchantingTableScreenHandler handler,
            ChiseledEnchantingMenuPayloads.TomeBookEntry book,
            EnchantementListWidget parent
        ) {
            EnchantUiEntry entry = new EnchantUiEntry();
            entry.client = client;
            entry.handler = handler;
            entry.parent = parent;
            entry.enchantable_item = handler.getEffectiveEnchantableItem();
            entry.cost_item_stack = handler.get_cost_item();
            entry.player = handler.player;
            entry.is_tome_entry = true;
            entry.tome_book = book;
            entry.option_type = OptionType.ENCHANTMENT;
            entry.cost_options = List.of();
            entry.xp_requirement = XpCostConversion.XpRequirement.FREE;
            entry.overridenEnchant = new ArrayList<>();
            return entry;
        }

        public static EnchantUiEntry actionEntry(
            Minecraft client,
            ChiseledEnchantingTableScreenHandler handler,
            AvailableOption option,
            List<List<ItemStack>> costOptions,
            XpCostConversion.XpRequirement xpRequirement,
            EnchantementListWidget parent,
            boolean nestedChild,
            boolean lastChild,
            boolean shelfLocked,
            int requiredBookshelves
        ) {
            EnchantUiEntry entry = new EnchantUiEntry(client, handler, option, costOptions, xpRequirement, parent);
            entry.nested_child = nestedChild;
            entry.last_child = lastChild;
            entry.shelf_locked = shelfLocked;
            entry.required_bookshelves = Math.max(0, requiredBookshelves);
            return entry;
        }

        private EnchantUiEntry() {
            super();
        }

        public EnchantUiEntry(
            Minecraft client,
            ChiseledEnchantingTableScreenHandler handler,
            AvailableOption option,
            List<List<ItemStack>> cost_options,
            XpCostConversion.XpRequirement xp_requirement,
            EnchantementListWidget parent
        ) {
            super();
            this.client = client;
            this.handler = handler;
            this.parent = parent;
            this.enchantable_item = handler.getEffectiveEnchantableItem();
            this.cost_item_stack = handler.get_cost_item();
            this.player = handler.player;
            this.option_type = option.type();
            this.is_transformation = option.isTransformation();
            this.enchantment_id = option.id();
            this.enchant_level = option.level();
            this.cost_options = cost_options == null ? List.of() : List.copyOf(cost_options);
            this.xp_requirement = xp_requirement;
            this.xp_level_cost = xp_requirement.requiredLevel();
            this.enchantment = (this.is_transformation || option.isDisenchantAll())
                ? null
                : identifierToEnchantment(option.id(), this.player);
            this.discovery_locked = this.is_transformation && !handler.isTransformationUnlocked(this.enchantment_id);

            this.overridenEnchant = new ArrayList<>();
            this.conflict_refund_points = 0;
            if (this.enchantment != null && !option.isCleanseOperation()) {
                ItemEnchantments currentEnchants = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
                int currentSameLevel = currentEnchants.getLevel(this.enchantment);
                if (currentSameLevel > 0) {
                    this.existingSameLevel = OptionalInt.of(currentSameLevel);
                    this.sameEnchantLevelDiff = OptionalInt.of(this.enchant_level - currentSameLevel);
                }
                boolean targetIsBook = enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK);
                if (!targetIsBook) {
                    for (var entry : currentEnchants.entrySet()) {
                        Identifier existingId = enchantmentToIdentifier(entry.getKey());
                        if (!existingId.equals(this.enchantment_id) && !Enchantment.areCompatible(entry.getKey(), this.enchantment)) {
                            this.overridenEnchant.add(new EnchantmentWithLevel(existingId, entry.getIntValue()));
                        }
                    }
                }
                this.conflict_refund_points = chiseled_enchanting_table.mechanics.enchanting.EnchantmentApplication
                    .computeConflictOverwriteRefund(handler, enchantable_item, currentEnchants, this.enchantment);
            }
        }

        private int effectiveDx() {
            int base = this.getX() + 1;
            return nested_child ? (base + RowRenderSupport.NESTED_CHILD_INDENT) : base;
        }

        private int effectiveDw() {
            int base = this.getWidth() - 2;
            return nested_child ? (base - RowRenderSupport.NESTED_CHILD_INDENT) : base;
        }

        public boolean canDecipherWithCatalyst() {
            return discovery_locked && !cost_item_stack.isEmpty()
                && cost_item_stack.is(chiseled_enchanting_table.registry.BlockRegistry.ARCANE_CATALYST);
        }

        public int selectedCostOptionIndex() {
            if (cost_options.isEmpty()) return ApplyEnchantmentPayload.AUTO_COST_OPTION;
            AvailableOption opt = new AvailableOption(option_type, enchantment_id, enchant_level);
            if (AvailableOption.isBagPayableCleanse(option_type)) {
                return parent.cleanseFilter.resolveCostOptionIndex(opt, cost_options, cost_item_stack, handler);
            }
            return parent.inscribeFilter.resolveCostOptionIndex(opt, cost_options, cost_item_stack);
        }

        public XpCostConversion.XpRequirement effectiveXpRequirement() {
            if (canDecipherWithCatalyst() || (option_type == OptionType.EXTRACT_ENCHANTMENT_BOOK
                && chiseled_enchanting_table.menu.MenuCleanseQuotes.bundleUsesCatalyst(activeCostBundle()))) {
                return XpCostConversion.XpRequirement.FREE;
            }
            return xp_requirement;
        }

        public List<ItemStack> activeCostBundle() {
            if (canDecipherWithCatalyst()) {
                return List.of(new ItemStack(chiseled_enchanting_table.registry.BlockRegistry.ARCANE_CATALYST, 1));
            }
            if (cost_options.isEmpty()) return List.of();
            int idx = selectedCostOptionIndex();
            return idx >= 0 && idx < cost_options.size() ? cost_options.get(idx) : cost_options.getFirst();
        }

        public ItemStack activePrimaryCost() {
            List<ItemStack> bundle = activeCostBundle();
            return bundle.isEmpty() ? ItemStack.EMPTY : bundle.getFirst();
        }

        public List<ItemStack> activeAdditionalCosts() {
            List<ItemStack> bundle = activeCostBundle();
            return bundle.size() <= 1 ? List.of() : bundle.subList(1, bundle.size());
        }

        private boolean isZeroMaterialRow() {
            return option_type == OptionType.DISENCHANT_ALL
                || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                || (option_type == OptionType.STRIP_SINGLE_ENCHANTMENT && cost_options.isEmpty())
                || (is_transformation && cost_options.isEmpty());
        }

        private boolean isBundleFullyAffordable(List<ItemStack> bundle) {
            if (player.getAbilities().instabuild) return true;
            if (bundle.isEmpty()) return isZeroMaterialRow() || cost_item_stack.is(Items.ENCHANTED_BOOK);
            if (AvailableOption.isBagPayableCleanse(option_type)) {
                return handler.isCleanseBundleAffordableFromBagOrSlot(bundle);
            }
            return handler.isBundleAffordableWithOverflow(bundle);
        }

        public boolean enchantAlreadyExist() {
            return sameEnchantLevelDiff.isPresent() && sameEnchantLevelDiff.getAsInt() == 0;
        }

        public boolean canEnchant() {
            if (is_tome_entry) return false;
            if (is_category_header) return true;
            if (canDecipherWithCatalyst()) return !handler.isRitualActive() && !handler.isRitualBlocked();
            if (shelf_locked || discovery_locked || handler.isRitualBlocked()) return false;
            if (handler.isRitualActive()) {
                boolean nonQueueable = is_transformation
                    || option_type == OptionType.BOOK_COMBINE_UPGRADE
                    || option_type == OptionType.BOOK_RESEARCH_UPGRADE
                    || option_type == OptionType.DISENCHANT_ALL
                    || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                    || AvailableOption.isBagPayableCleanse(option_type)
                    || cost_item_stack.is(Items.ENCHANTED_BOOK);
                if (nonQueueable) {
                    return false;
                }
            }
            return xpConditionMet() && itemCostConditionMet() && !enchantAlreadyExist();
        }

        public boolean xpConditionMet() {
            var req = effectiveXpRequirement();
            if (req.isRefund() || (req.requiredLevel() <= 0 && req.deductedLevels() <= 0)) {
                return true;
            }
            return player.getAbilities().instabuild || player.experienceLevel >= req.requiredLevel();
        }

        public boolean primaryCostMet() {
            if (player.getAbilities().instabuild || isZeroMaterialRow()) return true;
            ItemStack cost = activePrimaryCost();
            if (cost.isEmpty() && cost_item_stack.is(Items.ENCHANTED_BOOK)) return true;
            if (AvailableOption.isBagPayableCleanse(option_type)) {
                return !cost.isEmpty() && handler.countAvailableIncludingCostSlot(cost) >= cost.getCount();
            }
            return !cost.isEmpty()
                && !cost_item_stack.isEmpty()
                && ItemStack.isSameItemSameComponents(cost, cost_item_stack)
                && handler.countAvailableIncludingCostSlot(cost) >= cost.getCount();
        }

        public boolean hasInventoryCost(ItemStack additionalCost) {
            if (player.getAbilities().instabuild) return true;
            if (AvailableOption.isBagPayableCleanse(option_type)) {
                return handler.countAvailableIncludingCostSlot(additionalCost) >= additionalCost.getCount();
            }
            int found = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, additionalCost)) {
                    found += candidate.getCount();
                    if (found >= additionalCost.getCount()) return true;
                }
            }
            return false;
        }

        public List<Boolean> additionalCostsAffordability() {
            List<ItemStack> additional_costs = activeAdditionalCosts();
            List<Boolean> out = new ArrayList<>(additional_costs.size());
            for (ItemStack additionalCost : additional_costs) {
                out.add(hasInventoryCost(additionalCost));
            }
            return out;
        }

        public boolean itemCostConditionMet() {
            if (player.getAbilities().instabuild || isZeroMaterialRow() || canDecipherWithCatalyst()) return true;
            if (cost_options.isEmpty()) return cost_item_stack.is(Items.ENCHANTED_BOOK);
            return isBundleFullyAffordable(activeCostBundle());
        }

        @Nullable
        public ItemStack hoveredCostItem(double mouseX, double mouseY) {
            if (is_category_header || is_tome_entry) return null;
            int dx = effectiveDx();
            int dy = this.getY();
            int dw = effectiveDw();
            int dh = this.getHeight();
            if (mouseX < dx || mouseX > dx + dw || mouseY < dy || mouseY > dy + dh) {
                return null;
            }
            int itemY = RowRenderSupport.costIconY(dy, dh);
            int iconSize = RowRenderSupport.COST_ICON_SIZE;
            if (mouseY < itemY - 1 || mouseY > itemY + iconSize + 1) {
                return null;
            }
            ItemStack cost = activePrimaryCost();
            List<ItemStack> additional_costs = activeAdditionalCosts();
            int primaryX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            if (!cost.isEmpty() && mouseX >= primaryX - 1 && mouseX <= primaryX + iconSize + 2) {
                return cost;
            }
            int shown = Math.min(2, additional_costs.size());
            if (shown > 0) {
                int startX = RowRenderSupport.additionalCostStartX(dx, dw, shown);
                for (int i = 0; i < shown; i++) {
                    int x = startX + i * RowRenderSupport.ADDITIONAL_COST_STRIDE;
                    if (mouseX >= x - 1 && mouseX <= x + iconSize + 2) {
                        return additional_costs.get(i);
                    }
                }
            }
            return null;
        }

        @Nullable
        public List<List<ItemStack>> hoveredCostOptions(double mouseX, double mouseY) {
            if (is_category_header || is_tome_entry) return null;
            int dx = effectiveDx();
            int dy = this.getY();
            int dw = effectiveDw();
            int dh = this.getHeight();
            if (mouseX < dx || mouseX > dx + dw || mouseY < dy || mouseY > dy + dh) {
                return null;
            }
            int itemY = RowRenderSupport.costIconY(dy, dh);
            int iconSize = RowRenderSupport.COST_ICON_SIZE;
            if (mouseY < itemY - 1 || mouseY > itemY + iconSize + 1) {
                return null;
            }
            ItemStack cost = activePrimaryCost();
            int primaryX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            if (!cost.isEmpty() && mouseX >= primaryX - 1 && mouseX <= primaryX + iconSize + 2 && cost_options.size() > 1) {
                return cost_options;
            }
            return null;
        }

        public boolean isOverXpBadge(double mouseX, double mouseY) {
            if (is_category_header || is_tome_entry) return false;
            int dx = effectiveDx();
            int dy = this.getY();
            int dw = effectiveDw();
            int dh = this.getHeight();
            return mouseX >= dx + dw - RowRenderSupport.XP_BADGE_WIDTH
                && mouseX <= dx + dw
                && mouseY >= dy
                && mouseY <= dy + dh;
        }

        public boolean serverSendApplyEnchant() {
            return serverSendApplyEnchant(false);
        }

        public boolean serverSendApplyEnchant(boolean shiftBypass) {
            if (is_tome_entry) {
                parent.catalogue.select(tomeKey(this.tome_book), shiftBypass);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (is_category_header) {
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, parent.isCategoryExpanded(enchantment_id) ? 0.92F : 1.08F)
                );
                parent.toggleCategory(enchantment_id);
                return true;
            }
            if (!this.canEnchant()) return false;
            if (option_type == OptionType.DISENCHANT_ALL
                && Minecraft.getInstance().screen instanceof ChiseledEnchantingTableScreen tableScreen) {
                tableScreen.requestDisenchantAll(shiftBypass);
                return true;
            }
            if (ClientPlayNetworking.canSend(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID)) {
                ClientPlayNetworking.send(new ApplyEnchantmentPayload(
                    handler.containerId, option_type, enchantment_id, enchant_level, selectedCostOptionIndex()));
            } else {
                ChiseledEnchantingTableScreen.notifyChannelUnavailableClient();
            }
            return true;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDouble) {
            if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            if (is_tome_entry && this.tome_book != null) {
                parent.catalogue.selectInteractive(tomeKey(this.tome_book), event.hasShiftDown(), event.hasControlDown());
                if (Minecraft.getInstance().screen instanceof ChiseledEnchantingTableScreen tableScreen) {
                    tableScreen.cancelPendingTomeActions();
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (!canDecipherWithCatalyst() && hoveredCostOptions(event.x(), event.y()) != null) {
                AvailableOption opt = new AvailableOption(option_type, enchantment_id, enchant_level);
                if (AvailableOption.isBagPayableCleanse(option_type)) {
                    parent.cleanseFilter.cycleCostOption(opt, cost_options.size(), selectedCostOptionIndex());
                } else {
                    parent.inscribeFilter.cycleCostOption(opt, cost_options.size(), selectedCostOptionIndex());
                }
                return true;
            }
            return serverSendApplyEnchant(event.hasShiftDown());
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int index = this.cachedRowIndex;
            var rows = parent.children();
            if (index < 0 || index >= rows.size() || rows.get(index) != this) {
                index = rows.indexOf(this);
                this.cachedRowIndex = index;
            }
            if (index < 0) {
                return false;
            }
            int top = parent.getRowTop(index);
            int bottom = parent.getRowBottom(index);
            int left = parent.getRowLeft();
            int right = parent.getRowRight();
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }

        private EnchantmentRowRenderer.OverrideAnnotation computeOverrideAnnotation(String enchantmentName) {
            if (shelf_locked) {
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get(
                        "gui.chiseled_enchanting_table.shelf_locked.row_sub",
                        required_bookshelves,
                        handler.regularBookshelves
                    ),
                    0xFFD4AD6A
                );
            }
            EnchantmentRowRenderer.OverrideAnnotation cleanseSub = CleanseListSupport.cleanseOverrideAnnotation(this);
            if (cleanseSub != null) {
                return cleanseSub;
            }
            if (enchantAlreadyExist()) {
                return new EnchantmentRowRenderer.OverrideAnnotation("\u2714 " + enchantmentName, RowRenderSupport.CYAN);
            }
            // 1.0.3 Classic charges the FULL target-level cost (no incremental
            // credit — see handler#getEffectiveItemCostOptions / resolveXpRequirement
            // classic branches), so the "pay difference only" annotation must not
            // be shown there. It was previously displayed unconditionally while the
            // mechanism behind it was already disabled in classic.
            if (this.handler.usesIncrementalCredit()
                && sameEnchantLevelDiff.isPresent() && existingSameLevel.isPresent() && sameEnchantLevelDiff.getAsInt() > 0) {
                String existingName = Enchantment.getFullname(this.enchantment, existingSameLevel.getAsInt()).getString();
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.upgrade_from", existingName),
                    RowRenderSupport.LIGHT_GREEN
                );
            }
            var firstOverridenEnchant = this.overridenEnchant.stream().findFirst();
            if (firstOverridenEnchant.isPresent() && !enchantable_item.is(Items.ENCHANTED_BOOK)) {
                var enchant = firstOverridenEnchant.get();
                var name = Enchantment.getFullname(identifierToEnchantment(enchant.enchantment_id(), this.player), enchant.enchantment_level()).getString();
                String conflictText = this.conflict_refund_points > 0
                    ? ("\u2716 " + name + " (+" + this.conflict_refund_points + " XP)")
                    : ("\u2716 " + name);
                return new EnchantmentRowRenderer.OverrideAnnotation(conflictText, RowRenderSupport.LIGHT_RED);
            }
            return null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean hovered, float delta) {
            int outerDx = this.getX() + 1;
            int dy = this.getY();
            int dh = this.getHeight();
            boolean isFocused = parent.getFocused() == this;

            if (is_tome_entry && this.tome_book != null) {
                int dx = effectiveDx();
                int dw = effectiveDw();
                boolean selected = parent.isTomeSelected(this.tome_book);
                boolean inRitual = handler.isTomeActiveInRitual(this.tome_book.shelf_pos(), this.tome_book.slot());
                RowRenderSupport.drawPlaque(ctx, dx, dy, dw, dh,
                    RowRenderSupport.RowVisualStyle.STANDARD, true, hovered, isFocused || selected);
                if (inRitual) {
                    ctx.fill(dx + 2, dy + 2, dx + 4, dy + dh - 2, 0xFFD99BFF);
                }
                RowRenderSupport.beginRowContentClip(ctx, dx, dy, dw, dh);
                try {
                    String title = tomeTitle(this.tome_book, this.player);
                    RowRenderSupport.renderBoundedText(ctx, client, title, dx + 7, dy + 5, 0.62f, dw - 14,
                        this.tome_book.locked() ? 0xFFFFC46B : 0xFFF7F2EA);
                    boolean hasWearImmuneTag = this.tome_book.enchantments().stream()
                        .anyMatch(e -> isWearImmuneEnchantmentId(e.enchantment_id(), this.player));
                    boolean purelyImmune = this.tome_book.wear_immune()
                        && this.tome_book.wear_used() <= 0
                        && this.tome_book.remaining_charges() >= this.tome_book.effective_max_uses();
                    String charge = (hasWearImmuneTag && purelyImmune)
                        ? I18n.get("item.chiseled_enchanting_table.master_tome_infinite")
                        : (this.tome_book.wear_immune() && purelyImmune)
                            ? I18n.get("gui.chiseled_enchanting_table.tomes.immune")
                            : I18n.get("gui.chiseled_enchanting_table.tomes.charges",
                                this.tome_book.remaining_charges(), this.tome_book.effective_max_uses());
                    int chargeColor = this.tome_book.wear_immune() ? 0xFFFF55FF : 0xFF9ED8FF;
                    if (this.tome_book.generation() > 0) {
                        charge += "  " + I18n.get("gui.chiseled_enchanting_table.tomes.generation", this.tome_book.generation());
                    }
                    RowRenderSupport.renderBoundedText(ctx, client, charge, dx + 7, dy + 11, 0.52f, dw - 14, chargeColor);
                    String status;
                    int statusColor;
                    if (this.tome_book.locked()) {
                        String modeShort = I18n.get(
                            "gui.chiseled_enchanting_table.tomes.lock_mode_short." + this.tome_book.lock_mode());
                        StringBuilder lockedLine = new StringBuilder(
                            I18n.get("gui.chiseled_enchanting_table.tomes.locked_short", modeShort));
                        int lockEta = this.tome_book.lock_mode() == 0 ? this.tome_book.eta_next_ticks()
                            : this.tome_book.lock_mode() == 1 ? this.tome_book.eta_full_ticks() : -1;
                        if (lockEta >= 0) {
                            lockedLine.append(" · ").append(formatTomeEta(lockEta));
                        }
                        if (!this.tome_book.lock_owner_name().isBlank()) {
                            lockedLine.append(" · ").append(this.tome_book.lock_owner_name());
                        }
                        status = lockedLine.toString();
                        statusColor = 0xFFFF9E7A;
                    } else if (this.tome_book.charging()) {
                        status = I18n.get("gui.chiseled_enchanting_table.tomes.eta",
                            formatTomeEta(this.tome_book.eta_next_ticks()), formatTomeEta(this.tome_book.eta_full_ticks()));
                        statusColor = 0xFFA8FFC4;
                    } else if (this.tome_book.wear_used() <= 0) {
                        status = I18n.get("gui.chiseled_enchanting_table.tomes.full");
                        statusColor = 0xFFA8FFC4;
                    } else {
                        status = I18n.get("gui.chiseled_enchanting_table.tomes.not_charging");
                        statusColor = 0xFFC9BFD8;
                    }
                    if (inRitual) {
                        status = I18n.get("gui.chiseled_enchanting_table.tomes.active_ritual_badge") + " · " + status;
                        statusColor = 0xFFE6A8FF;
                    }
                    RowRenderSupport.renderBoundedText(ctx, client, status, dx + 7, dy + 16, 0.5f, dw - 14, statusColor);
                } finally {
                    RowRenderSupport.endRowContentClip(ctx);
                }
                return;
            }

            if (is_category_header) {
                CategoryRowRenderer.render(
                    ctx,
                    client,
                    outerDx,
                    dy,
                    this.getWidth() - 2,
                    dh,
                    parent.isCategoryExpanded(enchantment_id),
                    hovered,
                    isFocused,
                    cleanse_theme,
                    category_title,
                    category_badge,
                    category_badge_color,
                    category_refund_points
                );
                return;
            }

            if (nested_child) {
                RowRenderSupport.drawTreeConnector(
                    ctx,
                    outerDx,
                    dy,
                    dh,
                    last_child,
                    parent.tabMode() == UiTabMode.CLEANSE
                );
            }

            int dx = effectiveDx();
            int dw = effectiveDw();
            boolean primaryMet = primaryCostMet();
            List<Boolean> additionalMet = additionalCostsAffordability();
            boolean xpMet = xpConditionMet();

            ItemStack cost = activePrimaryCost();
            List<ItemStack> additional_costs = activeAdditionalCosts();

            if (is_transformation) {
                String baseOutputName = handler.syncedTransformationOutputName(this.enchantment_id);
                boolean isBlankOverwrite = this.enchantment_id.equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID);
                int batchCount = handler.resolveTransformationBatchCount(this.enchantment_id);
                String outputName = batchCount > 1
                    ? (batchCount + "\u00d7 " + baseOutputName)
                    : baseOutputName;
                String rowTitle = isBlankOverwrite
                    ? I18n.get("gui.chiseled_enchanting_table.blank_overwrite", outputName)
                    : outputName;
                String subText = null;
                int subColor = RowRenderSupport.LIGHT_GREEN;
                if (canDecipherWithCatalyst()) {
                    subText = I18n.get("gui.chiseled_enchanting_table.discovery_locked.catalyst_ready");
                    subColor = RowRenderSupport.ARCANE_PURPLE;
                } else if (discovery_locked) {
                    subText = I18n.get("gui.chiseled_enchanting_table.discovery_locked.sub", baseOutputName);
                    subColor = 0xFFD4AD6A;
                } else if (isBlankOverwrite) {
                    var dtTier = handler.depletedTomeTier();
                    var sg = handler.shelfGatesConfig();
                    subText = switch (dtTier) {
                        case BASELINE -> I18n.get(
                            "gui.chiseled_enchanting_table.depleted_tome.sub_t0",
                            handler.regularBookshelves,
                            sg.tier1Bookshelves()
                        );
                        case TIER_1_NO_XP -> I18n.get(
                            "gui.chiseled_enchanting_table.depleted_tome.sub_t1",
                            handler.regularBookshelves,
                            sg.tier2Bookshelves()
                        );
                        case TIER_2_DISCOUNT_MAT -> I18n.get(
                            "gui.chiseled_enchanting_table.depleted_tome.sub_t2",
                            handler.qualifyingChiseledEnchantments(),
                            sg.tier3QualifyingEnchantments()
                        );
                        case TIER_3_FREE -> I18n.get("gui.chiseled_enchanting_table.depleted_tome.sub_t3");
                    };
                    subColor = switch (dtTier) {
                        case BASELINE -> RowRenderSupport.CYAN;
                        case TIER_1_NO_XP -> RowRenderSupport.CYAN;
                        case TIER_2_DISCOUNT_MAT -> RowRenderSupport.EPIPHANY_GOLD;
                        case TIER_3_FREE -> RowRenderSupport.LIGHT_GREEN;
                    };
                } else if (enchantable_item.getCount() > 1) {
                    subText = I18n.get("gui.chiseled_enchanting_table.transformation.batch_sub", batchCount);
                    subColor = batchCount > 1 ? RowRenderSupport.LIGHT_GREEN : RowRenderSupport.CYAN;
                }
                XpCostConversion.XpRequirement shownXp = canDecipherWithCatalyst()
                    ? XpCostConversion.XpRequirement.FREE : xp_requirement;
                TransformationRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, rowTitle, subText, subColor, cost, additional_costs,
                    shownXp, !canDecipherWithCatalyst() && cost_options.size() > 1
                );
                if (discovery_locked && !canDecipherWithCatalyst()) {
                    ctx.fill(dx + 3, dy + 3, dx + dw - 3, dy + dh - 3, 0x380A0614);
                }
                return;
            }

            if (option_type == OptionType.DISENCHANT_ALL
                || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                || AvailableOption.isBagPayableCleanse(option_type)) {
                AvailableOption opt = new AvailableOption(option_type, enchantment_id, enchant_level);
                String title = parent.displayName(opt);
                RowRenderSupport.RowVisualStyle style = (option_type == OptionType.PURGE_CURSE)
                    ? RowRenderSupport.RowVisualStyle.CURSE_PURGE
                    : (option_type == OptionType.EXTRACT_ENCHANTMENT_BOOK || cost.isEmpty()
                        ? RowRenderSupport.RowVisualStyle.CLEANSE : RowRenderSupport.RowVisualStyle.STANDARD);
                EnchantmentRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, style,
                    isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, title, cost, additional_costs, effectiveXpRequirement(),
                    computeOverrideAnnotation(title), cost_options.size() > 1
                );
                return;
            }

            String rawName = Enchantment.getFullname(this.enchantment, enchant_level).getString();
            String enchantmentName = rawName;
            if (option_type == OptionType.TREASURE_EPIPHANY) enchantmentName = I18n.get("gui.chiseled_enchanting_table.treasure_epiphany", rawName);
            if (option_type == OptionType.BOOK_COMBINE_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.combine", rawName);
            if (option_type == OptionType.BOOK_RESEARCH_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.research", rawName);
            EnchantmentRowRenderer.render(
                ctx, client, dx, dy, dw, dh,
                option_type == OptionType.TREASURE_EPIPHANY
                    ? RowRenderSupport.RowVisualStyle.EPIPHANY : RowRenderSupport.RowVisualStyle.STANDARD,
                isFocused, hovered, canEnchant(),
                primaryMet, additionalMet, xpMet, enchantmentName, cost, additional_costs, xp_requirement,
                computeOverrideAnnotation(rawName), cost_options.size() > 1
            );
            if (shelf_locked) {
                ctx.fill(dx + 3, dy + 3, dx + dw - 3, dy + dh - 3, 0x380A0614);
            }
        }
    }

    private final InscribeOptionModel.Catalog optionCatalog = new InscribeOptionModel.Catalog() {
        public boolean allowsDirectTierUpgrade() { return handler.allowsDirectTierUpgrade(); }
        public boolean usesClassicShelfBoost() { return handler.usesClassicShelfBoost(); }
        public boolean canDirectUpgradeExistingEnchantment(Holder<Enchantment> holder, Identifier id, int existing, int target) { return handler.canDirectUpgradeExistingEnchantment(holder, id, existing, target); }
        public boolean canDirectResearchUpgradeTo(Identifier id, int target) { return handler.canDirectResearchUpgradeTo(id, target); }
        public List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(ItemStack target) { return handler.computeActiveTreasureEpiphanies(target); }
        public int requiredBookshelvesForDirectTier(Identifier id, int target, int max) { return handler.requiredBookshelvesForDirectTier(id, target, max); }
        public Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack target) { return handler.syncedTransformationsFor(target); }
        public boolean syncedTransformationMatches(Identifier id, ItemStack target) { return handler.syncedTransformationMatches(id, target); }
    };

    private static TableRowView rowView(EnchantUiEntry row) {
        return row == null ? null : new TableRowView(row.discovery_locked, row.enchantment_id, row.shelf_locked,
            row.enchantment, row.enchant_level, row.required_bookshelves, row.is_category_header, row.cleanse_theme,
            row.category_title, row.category_badge, row.option_type, row.is_transformation, row.enchantable_item,
            row.existingSameLevel, row.conflict_refund_points, row.effectiveXpRequirement(), row.xpConditionMet());
    }

    private final TableListRead tableRead = new TableListRead() {
        public boolean hasEntries() { return !children().isEmpty(); }
        public List<ChiseledEnchantingMenuPayloads.TomeBookEntry> selectedTomes() { return EnchantementListWidget.this.selectedTomes(); }
        public ChiseledEnchantingMenuPayloads.TomeBookEntry selectedTomeEntry() { return EnchantementListWidget.this.selectedTomeEntry(); }
        public ChiseledEnchantingMenuPayloads.TomeBookEntry hoveredTomeEntry(double x, double y) { return EnchantementListWidget.this.hoveredTomeEntry(x, y); }
        public Identifier hoveredEnchantmentId(double x, double y) { return EnchantementListWidget.this.hoveredEnchantmentId(x, y); }
        public OptionType hoveredOptionType(double x, double y) { return EnchantementListWidget.this.hoveredOptionType(x, y); }
        public TableRowView hoveredXpBadgeEntry(double x, double y) { return rowView(EnchantementListWidget.this.hoveredXpBadgeEntry(x, y)); }
        public TableRowView hoveredSpecialRowEntry(double x, double y) { return rowView(EnchantementListWidget.this.hoveredSpecialRowEntry(x, y)); }
        public TableRowView hoveredActionRowEntry(double x, double y) { return rowView(EnchantementListWidget.this.hoveredActionRowEntry(x, y)); }
        public ItemStack hoveredCostItem(double x, double y) {
            var item = EnchantementListWidget.this.hoveredCostItem(x, y);
            return item == null ? null : item.copy();
        }
        public List<List<ItemStack>> hoveredCostOptions(double x, double y) {
            var options = EnchantementListWidget.this.hoveredCostOptions(x, y);
            return options == null ? null : options.stream().map(bundle -> bundle.stream().map(ItemStack::copy).toList()).toList();
        }
        public String displayName(AvailableOption option) { return EnchantementListWidget.this.displayName(option); }
    };
    TableListRead tableRead() { return this.tableRead; }
}
