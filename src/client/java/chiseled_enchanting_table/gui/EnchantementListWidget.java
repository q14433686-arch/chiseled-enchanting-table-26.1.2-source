package chiseled_enchanting_table.gui;

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
    @Nullable
    private net.minecraft.core.BlockPos selectedTomeShelf;
    private int selectedTomeSlot = -1;
    private final Set<Identifier> expandedInscribeCategories = new HashSet<>();
    private final Set<Identifier> expandedCleanseCategories = new HashSet<>();
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

    public boolean isTomeSelected(ChiseledEnchantingMenuPayloads.TomeBookEntry entry) {
        return entry != null && this.selectedTomeShelf != null
            && this.selectedTomeShelf.equals(entry.shelf_pos()) && this.selectedTomeSlot == entry.slot();
    }

    public void selectTome(@Nullable ChiseledEnchantingMenuPayloads.TomeBookEntry entry) {
        if (entry == null) {
            this.selectedTomeShelf = null;
            this.selectedTomeSlot = -1;
        } else {
            this.selectedTomeShelf = entry.shelf_pos().immutable();
            this.selectedTomeSlot = entry.slot();
        }
    }

    @Nullable
    public ChiseledEnchantingMenuPayloads.TomeBookEntry selectedTomeEntry() {
        if (this.handler == null || this.handler.tomeBooks == null || this.handler.tomeBooks.isEmpty()) return null;
        for (var entry : this.handler.tomeBooks) {
            if (isTomeSelected(entry)) return entry;
        }
        return this.handler.tomeBooks.getFirst();
    }

    public void setTabMode(UiTabMode nextMode) {
        if (nextMode != null && this.tabMode != nextMode) {
            this.tabMode = nextMode;
            this.setFocused(null);
            this.setScrollAmount(0);
            this.updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        }
    }

    public boolean isCategoryExpanded(Identifier categoryId) {
        return (this.tabMode == UiTabMode.CLEANSE ? this.expandedCleanseCategories : this.expandedInscribeCategories)
            .contains(categoryId);
    }

    public void toggleCategory(Identifier categoryId) {
        Set<Identifier> set = (this.tabMode == UiTabMode.CLEANSE)
            ? this.expandedCleanseCategories
            : this.expandedInscribeCategories;
        if (!set.remove(categoryId)) {
            set.add(categoryId);
        }
        double savedScroll = this.scrollAmount();
        this.updateEntry(this.handler.getEffectiveEnchantableItem(), this.handler.get_cost_item());
        this.setScrollAmount(Math.min(savedScroll, this.maxScrollAmount()));
    }

    @Override
    protected int addEntry(EnchantUiEntry entry) {
        entry.cachedRowIndex = this.children().size();
        return super.addEntry(entry);
    }

    public static boolean isWearImmuneEnchantmentId(Identifier id, @Nullable Player player) {
        if (id == null) {
            return false;
        }
        return findEnchantment(id, player)
            .map(chiseled_enchanting_table.mechanics.resonance.TomeWearData::isWearImmuneEnchantment)
            .orElseGet(() -> chiseled_enchanting_table.mechanics.resonance.TomeWearData.INFINITY_ENCHANTMENT_ID.equals(id));
    }

    public static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Player player) {
        if (id == null || player == null) {
            return java.util.Optional.empty();
        }
        return player.level().registryAccess()
            .lookup(Registries.ENCHANTMENT)
            .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
    }

    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Player player) {
        return findEnchantment(id, player)
            .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + id));
    }

    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant, Player player) {
        return enchant.unwrapKey().get().identifier();
    }

    public static String baseEnchantmentName(Holder<Enchantment> ench) {
        return ench.value().description().getString();
    }

    private static String romanNumeral(int level) {
        if (level >= 1 && level <= 10) {
            return Component.translatable("enchantment.level." + level).getString();
        }
        return String.valueOf(level);
    }

    public String displayName(AvailableOption option) {
        if (option.isDisenchantAll()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.all_row");
        }
        if (option.isTransformation()) {
            String out = this.handler.syncedTransformationOutputName(option.id());
            if (option.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID)) {
                return I18n.get("gui.chiseled_enchanting_table.blank_overwrite", out);
            }
            return out;
        }
        Holder<Enchantment> ench = IdentifierToEnchantment(option.id(), this.player);
        String name = Enchantment.getFullname(ench, option.level()).getString();
        if (option.isDowngrade()) {
            int curLvl = this.handler.currentTargetEnchantLevel(option.id());
            String curName = Enchantment.getFullname(ench, Math.max(option.level() + 1, curLvl)).getString();
            return I18n.get("gui.chiseled_enchanting_table.cleanse.downgrade_row", curName, name);
        }
        if (option.isStripSingle()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.strip_row", name);
        }
        if (option.isPurgeCurse()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.curse_row", name);
        }
        if (option.isTreasureEpiphany()) return I18n.get("gui.chiseled_enchanting_table.treasure_epiphany", name);
        if (option.isBookCombineUpgrade()) return I18n.get("gui.chiseled_enchanting_table.book_upgrade.combine", name);
        if (option.isBookResearchUpgrade()) return I18n.get("gui.chiseled_enchanting_table.book_upgrade.research", name);
        return name;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
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
        var selected = selectedTomeEntry();
        selectTome(selected);
        for (var book : this.handler.tomeBooks) {
            this.addEntry(EnchantUiEntry.tomeEntry(minecraft, this.handler, book, this));
        }
        this.setScrollAmount(Math.min(previousScroll, this.maxScrollAmount()));
    }

    static String tomeTitle(ChiseledEnchantingMenuPayloads.TomeBookEntry book, Player player) {
        String custom = book.custom_name() == null ? "" : book.custom_name().trim();
        String defaultName = new ItemStack(Items.ENCHANTED_BOOK).getHoverName().getString();
        if (!custom.isEmpty() && !custom.equals(defaultName)) return custom;
        if (book.enchantments().isEmpty()) return custom.isEmpty() ? defaultName : custom;
        var first = book.enchantments().getFirst();
        var holder = findEnchantment(first.enchantment_id(), player);
        String title = holder.map(h -> Enchantment.getFullname(h, first.enchantment_level()).getString())
            .orElse(first.enchantment_id().toString());
        return book.enchantments().size() > 1 ? title + " +" + (book.enchantments().size() - 1) : title;
    }

    static String formatTomeEta(int ticks) {
        if (ticks < 0) return "--:--";
        long seconds = (ticks + 19L) / 20L;
        long hours = seconds / 3600L;
        long mins = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0
            ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, mins, secs)
            : String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
    }

    private void addActionEntry(AvailableOption opt, boolean nested, boolean lastChild) {
        addActionEntry(opt, nested, lastChild, false, 0);
    }

    private void addActionEntry(
        AvailableOption opt,
        boolean nested,
        boolean lastChild,
        boolean shelfLocked,
        int requiredBookshelves
    ) {
        boolean enchantFromBook = this.cost_item_stack.is(Items.ENCHANTED_BOOK);
        var ewl = opt.asEnchantmentWithLevel();
        List<List<ItemStack>> costOpts = opt.isBookCombineUpgrade() || (enchantFromBook && !opt.isBookResearchUpgrade() && !opt.isCleanseOperation())
            ? List.of()
            : this.handler.getEffectiveItemCostOptions(ewl, opt.type());
        var xpReq = (enchantFromBook && !opt.isBookUpgrade() && !opt.isCleanseOperation())
            ? XpCostConversion.XpRequirement.FREE
            : this.handler.resolveEffectiveXpRequirement(ewl, opt.type());
        this.addEntry(EnchantUiEntry.actionEntry(
            minecraft, this.handler, opt, costOpts, xpReq, this, nested, lastChild, shelfLocked, requiredBookshelves
        ));
    }

    private void populateCleanseEntries(int navigationIndex, boolean targetChanged, double previousScroll) {
        if (!this.handler.disenchantEnabled() || this.enchantable_item.isEmpty()) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(this.enchantable_item);
        if (current.isEmpty()) {
            return;
        }

        // 1. Standalone top-pinned Cleanse rows: Full Disenchant + any Curse Purge items
        int nonCurseCount = DisenchantmentApplication.countNonCurseEnchantments(this.enchantable_item);
        if (nonCurseCount >= 1) {
            addActionEntry(AvailableOption.disenchantAll(DisenchantmentApplication.DISENCHANT_ALL_ID), false, false);
        }

        List<Holder<Enchantment>> sortedKeys = new ArrayList<>(current.keySet());
        sortedKeys.sort((a, b) -> baseEnchantmentName(a).compareTo(baseEnchantmentName(b)));

        int totalTraits = current.size();
        Map<Identifier, List<AvailableOption>> groupedCleanse = new LinkedHashMap<>();
        Map<Identifier, Holder<Enchantment>> holdersById = new LinkedHashMap<>();

        for (Holder<Enchantment> holder : sortedKeys) {
            int curLvl = current.getLevel(holder);
            if (curLvl <= 0) continue;
            Identifier id = EnchantmentToIdentifier(holder, this.player);
            if (DisenchantmentApplication.isCurse(holder)) {
                if (this.handler.disenchantAllowCursePurge()) {
                    addActionEntry(AvailableOption.purgeCurse(id, curLvl), false, false);
                }
            } else {
                List<AvailableOption> subRows = new ArrayList<>();
                for (int nextLvl = curLvl - 1; nextLvl >= 1; nextLvl--) {
                    subRows.add(AvailableOption.downgradeEnchantment(id, nextLvl));
                }
                if (totalTraits >= 2) {
                    subRows.add(AvailableOption.stripSingleEnchantment(id, curLvl));
                }
                if (!subRows.isEmpty()) {
                    groupedCleanse.put(id, subRows);
                    holdersById.put(id, holder);
                }
            }
        }

        // Auto-expand when there is only 1 collapsible trait on a newly placed item
        if (targetChanged && groupedCleanse.size() == 1) {
            this.expandedCleanseCategories.add(groupedCleanse.keySet().iterator().next());
        }

        for (var groupEntry : groupedCleanse.entrySet()) {
            Identifier id = groupEntry.getKey();
            List<AvailableOption> children = groupEntry.getValue();
            Holder<Enchantment> holder = holdersById.get(id);
            int curLvl = current.getLevel(holder);
            String headerTitle = Enchantment.getFullname(holder, curLvl).getString();
            int traitEssence = DisenchantmentApplication.provenanceEssencePotential(
                this.handler.libraryResonanceConfig(),
                this.enchantable_item,
                holder,
                curLvl,
                this.handler.disenchantRefundScale(),
                this.handler.disenchantExternalRefundScale()
            );
            boolean open = isCategoryExpanded(id);

            this.addEntry(EnchantUiEntry.categoryEntry(
                minecraft,
                this.handler,
                id,
                headerTitle,
                I18n.get("gui.chiseled_enchanting_table.category.count", children.size()),
                RowRenderSupport.JADE_MINT,
                traitEssence,
                true,
                this
            ));
            if (open) {
                for (int idx = 0; idx < children.size(); idx++) {
                    addActionEntry(children.get(idx), true, idx == children.size() - 1);
                }
            }
        }

        resumeNavigation(navigationIndex);
        if (!targetChanged) {
            this.setScrollAmount(Math.min(previousScroll, this.maxScrollAmount()));
        }
    }

    private boolean isDirectWoodenShelfEligible(Holder<Enchantment> holder, Identifier id) {
        if (DisenchantmentApplication.isCurse(holder) || DisenchantmentApplication.isTreasure(holder)) {
            return false;
        }
        return !SyncedEnchantmentCosts.explicitTreasureIds().contains(id);
    }

    private record DisplayOptionSlot(AvailableOption option, boolean shelfLocked, int requiredBookshelves) {}

    private void populateInscribeEntries(int navigationIndex, boolean targetChanged, double previousScroll) {
        var enchantFromBook = cost_item_stack.is(Items.ENCHANTED_BOOK);
        ItemEnchantments targetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);

        List<DisplayOptionSlot> optionSlots = new ArrayList<>();
        Set<EnchantmentWithLevel> unlockedDirectSet = new HashSet<>();
        Map<Identifier, Integer> maxAddedTierById = new LinkedHashMap<>();

        if (enchantFromBook) {
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            for (var entry : enchantments.entrySet()) {
                int existingLvl = targetEnchantments.getLevel(entry.getKey());
                if (entry.getIntValue() > existingLvl) {
                    Identifier id = EnchantmentToIdentifier(entry.getKey(), this.player);
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(id, entry.getIntValue()), false, 0));
                }
            }
        } else if (!cost_item_stack.is(Items.BOOK)) {
            if (unlocked_enchantements != null) {
                for (EnchantmentWithLevel e : unlocked_enchantements) {
                    var optH = findEnchantment(e.enchantment_id(), this.player);
                    if (optH.isEmpty()) continue;
                    Holder<Enchantment> h = optH.get();
                    int existingLvl = targetEnchantments.getLevel(h);
                    // Audit M-04: strictly-greater only — the server rejects same-level and
                    // downgrade applies, so listing them as candidates was a dead click.
                    if (e.enchantment_level() > existingLvl) {
                        optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(e), false, 0));
                        unlockedDirectSet.add(e);
                        maxAddedTierById.merge(e.enchantment_id(), e.enchantment_level(), Math::max);
                    }
                }
            }
            // Track 2 (Existing-category upgrade on gear): once an item already carries level >= 1,
            // regular bookshelves unlock upgrades up to maxUnlockedDirectTier (1..MAX at 38 shelves).
            if (!enchantable_item.is(Items.ENCHANTED_BOOK) && !enchantable_item.is(Items.BOOK)) {
                for (var existingEntry : targetEnchantments.entrySet()) {
                    int existingLvl = existingEntry.getIntValue();
                    int maxLvl = Math.max(1, existingEntry.getKey().value().getMaxLevel());
                    if (existingLvl < 1 || existingLvl >= maxLvl) continue;
                    Identifier id = EnchantmentToIdentifier(existingEntry.getKey(), this.player);
                    for (int lvl = existingLvl + 1; lvl <= maxLvl; lvl++) {
                        EnchantmentWithLevel candidate = new EnchantmentWithLevel(id, lvl);
                        if (!unlockedDirectSet.contains(candidate)
                            && this.handler.canDirectUpgradeExistingEnchantment(existingEntry.getKey(), id, existingLvl, lvl)) {
                            optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(candidate), false, 0));
                            unlockedDirectSet.add(candidate);
                            maxAddedTierById.merge(id, lvl, Math::max);
                        }
                    }
                }
            }
            for (EnchantmentWithLevel epiphany : this.handler.computeActiveTreasureEpiphanies(enchantable_item)) {
                var optH = findEnchantment(epiphany.enchantment_id(), this.player);
                if (optH.isEmpty()) continue;
                Holder<Enchantment> h = optH.get();
                if (epiphany.enchantment_level() > targetEnchantments.getLevel(h)) {
                    optionSlots.add(new DisplayOptionSlot(
                        AvailableOption.treasureEpiphany(epiphany.enchantment_id(), epiphany.enchantment_level()),
                        false,
                        0
                    ));
                }
            }
        }
        if (enchantable_item.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments costEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            boolean combineEnabled = SyncedEnchantmentCosts.combineEnabled();
            boolean researchEnabled = SyncedEnchantmentCosts.researchEnabled();
            for (var entry : targetEnchantments.entrySet()) {
                int level = entry.getIntValue();
                int maxLevel = entry.getKey().value().getMaxLevel();
                if (level >= maxLevel) continue;
                Identifier id = EnchantmentToIdentifier(entry.getKey(), this.player);
                int targetLevel = level + 1;
                if (combineEnabled && cost_item_stack.is(Items.ENCHANTED_BOOK) && costEnchantments.getLevel(entry.getKey()) == level) {
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.bookCombineUpgrade(id, targetLevel), false, 0));
                    maxAddedTierById.merge(id, targetLevel, Math::max);
                } else if (researchEnabled
                    && !cost_item_stack.is(Items.ENCHANTED_BOOK)
                    && !cost_item_stack.is(Items.BOOK)
                    && !unlockedDirectSet.contains(new EnchantmentWithLevel(id, targetLevel))) {
                    if (this.handler.canDirectResearchUpgradeTo(id, targetLevel)) {
                        optionSlots.add(new DisplayOptionSlot(AvailableOption.bookResearchUpgrade(id, targetLevel), false, 0));
                        maxAddedTierById.merge(id, targetLevel, Math::max);
                    } else if (isDirectWoodenShelfEligible(entry.getKey(), id)) {
                        int reqShelves = this.handler.requiredBookshelvesForDirectTier(id, targetLevel, maxLevel);
                        optionSlots.add(new DisplayOptionSlot(
                            AvailableOption.bookResearchUpgrade(id, targetLevel),
                            true,
                            reqShelves
                        ));
                        maxAddedTierById.merge(id, targetLevel, Math::max);
                    }
                }
            }
        }

        // Surface greyed-out shelf-locked direct tiers (with exact bookshelf goal) rather than hiding goals!
        if (!enchantFromBook && !cost_item_stack.is(Items.BOOK) && !ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            Set<Identifier> candidateDirectIds = new LinkedHashSet<>(maxAddedTierById.keySet());
            for (var entry : targetEnchantments.entrySet()) {
                Identifier id = EnchantmentToIdentifier(entry.getKey(), this.player);
                if (isDirectWoodenShelfEligible(entry.getKey(), id)) {
                    candidateDirectIds.add(id);
                }
            }
            for (Identifier enchId : candidateDirectIds) {
                Holder<Enchantment> holder = IdentifierToEnchantment(enchId, this.player);
                if (!isDirectWoodenShelfEligible(holder, enchId)) continue;
                int maxLevel = Math.max(1, holder.value().getMaxLevel());
                int existingLvl = targetEnchantments.getLevel(holder);
                int unlockedTop = maxAddedTierById.getOrDefault(enchId, 0);
                int startLocked = Math.max(unlockedTop + 1, Math.max(2, existingLvl + 1));
                for (int lvl = startLocked; lvl <= maxLevel; lvl++) {
                    int reqShelves = this.handler.requiredBookshelvesForDirectTier(enchId, lvl, maxLevel);
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(enchId, lvl), true, reqShelves));
                }
            }
        }

        for (EnchantmentWithLevel t : this.handler.syncedTransformationsFor(enchantable_item)) {
            optionSlots.add(new DisplayOptionSlot(AvailableOption.transformation(t.enchantment_id(), t.enchantment_level()), false, 0));
        }

        List<DisplayOptionSlot> filtered = optionSlots.stream().filter(slot -> {
            AvailableOption option = slot.option();
            if (option.isTransformation()) {
                return this.handler.syncedTransformationMatches(option.id(), enchantable_item);
            }
            if (ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
                return false;
            }
            var optEnchant = findEnchantment(option.id(), this.player);
            if (optEnchant.isEmpty()) {
                return false;
            }
            Holder<Enchantment> enchant = optEnchant.get();
            return enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchant.value().canEnchant(enchantable_item);
        }).toList();

        // 1. Standalone top-pinned rows: ALL Treasure Epiphanies + Item Transformations (never collapsed!)
        List<DisplayOptionSlot> pinnedTop = new ArrayList<>();
        Map<Identifier, List<DisplayOptionSlot>> groupedEnchants = new LinkedHashMap<>();

        filtered.stream()
            .filter(s -> s.option().isTreasureEpiphany() || s.option().isTransformation())
            .sorted((s1, s2) -> {
                AvailableOption o1 = s1.option();
                AvailableOption o2 = s2.option();
                int p1 = o1.isTreasureEpiphany() ? 0 : 1;
                int p2 = o2.isTreasureEpiphany() ? 0 : 1;
                if (p1 != p2) return Integer.compare(p1, p2);
                int c = displayName(o1).compareTo(displayName(o2));
                return c != 0 ? c : Integer.compare(o1.level(), o2.level());
            })
            .forEach(pinnedTop::add);

        filtered.stream()
            .filter(s -> !s.option().isTreasureEpiphany() && !s.option().isTransformation())
            .sorted((s1, s2) -> {
                AvailableOption o1 = s1.option();
                AvailableOption o2 = s2.option();
                String n1 = baseEnchantmentName(IdentifierToEnchantment(o1.id(), this.player));
                String n2 = baseEnchantmentName(IdentifierToEnchantment(o2.id(), this.player));
                int c = n1.compareTo(n2);
                if (c != 0) return c;
                int lvlCmp = Integer.compare(o1.level(), o2.level());
                if (lvlCmp != 0) return lvlCmp;
                return Integer.compare(o1.type().ordinal(), o2.type().ordinal());
            })
            .forEach(slot -> groupedEnchants.computeIfAbsent(slot.option().id(), ignored -> new ArrayList<>()).add(slot));

        for (DisplayOptionSlot pinned : pinnedTop) {
            addActionEntry(pinned.option(), false, false, pinned.shelfLocked(), pinned.requiredBookshelves());
        }

        // Auto-expand when there is only 1 enchantment group available (e.g. single-book transfer/combine/upgrade)
        if (groupedEnchants.size() == 1) {
            this.expandedInscribeCategories.add(groupedEnchants.keySet().iterator().next());
        }

        // 2. Per-attribute collapsible horizontal tabs with crisp ▶ / ▼ triangles & indented tier children
        for (var groupEntry : groupedEnchants.entrySet()) {
            Identifier enchId = groupEntry.getKey();
            List<DisplayOptionSlot> children = groupEntry.getValue();
            Holder<Enchantment> holder = IdentifierToEnchantment(enchId, this.player);
            String categoryName = baseEnchantmentName(holder);
            int curLvl = targetEnchantments.getLevel(holder);
            int minTier = children.getFirst().option().level();
            int maxTier = children.getLast().option().level();
            int maxUnlockedTier = children.stream()
                .filter(s -> !s.shelfLocked())
                .mapToInt(s -> s.option().level())
                .max()
                .orElse(0);

            String badgeText;
            int badgeColor;
            if (curLvl > 0 && maxUnlockedTier > curLvl) {
                badgeText = maxUnlockedTier < maxTier
                    ? ("\u2B06 " + romanNumeral(curLvl) + "\u2192" + romanNumeral(maxUnlockedTier) + "/" + romanNumeral(maxTier))
                    : ("\u2B06 " + romanNumeral(curLvl) + "\u2192" + romanNumeral(maxUnlockedTier));
                badgeColor = RowRenderSupport.LIGHT_GREEN;
            } else if (curLvl > 0 && maxTier > curLvl && maxUnlockedTier <= curLvl) {
                int nextReq = children.stream()
                    .filter(s -> s.shelfLocked() && s.option().level() > curLvl)
                    .mapToInt(DisplayOptionSlot::requiredBookshelves)
                    .findFirst()
                    .orElse(0);
                badgeText = "\uD83D\uDD12 " + romanNumeral(curLvl + 1) + " (" + nextReq + ")";
                badgeColor = RowRenderSupport.EPIPHANY_GOLD;
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

            boolean open = isCategoryExpanded(enchId);
            this.addEntry(EnchantUiEntry.categoryEntry(
                minecraft,
                this.handler,
                enchId,
                categoryName,
                badgeText,
                badgeColor,
                0,
                false,
                this
            ));
            if (open) {
                for (int idx = 0; idx < children.size(); idx++) {
                    DisplayOptionSlot child = children.get(idx);
                    addActionEntry(child.option(), true, idx == children.size() - 1, child.shelfLocked(), child.requiredBookshelves());
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
            Minecraft client,
            ChiseledEnchantingTableScreenHandler handler,
            Identifier categoryId,
            String categoryTitle,
            String badgeText,
            int badgeColor,
            int refundPreviewPoints,
            boolean cleanseTheme,
            EnchantementListWidget parent
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
                : IdentifierToEnchantment(option.id(), this.player);
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
                        Identifier existingId = EnchantmentToIdentifier(entry.getKey(), this.player);
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

        public List<ItemStack> activeCostBundle() {
            if (cost_options.isEmpty()) {
                return List.of();
            }
            if (!cost_item_stack.isEmpty()) {
                for (List<ItemStack> bundle : cost_options) {
                    if (!bundle.isEmpty() && isBundleFullyAffordable(bundle)) {
                        return bundle;
                    }
                }
                for (List<ItemStack> bundle : cost_options) {
                    if (!bundle.isEmpty() && ItemStack.isSameItemSameComponents(bundle.getFirst(), cost_item_stack)) {
                        return bundle;
                    }
                }
            }
            int idx = (int) ((net.minecraft.util.Util.getMillis() / 1500L) % cost_options.size());
            return cost_options.get(idx);
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
            return handler.isBundleAffordableWithOverflow(bundle);
        }

        public boolean enchantAlreadyExist() {
            return sameEnchantLevelDiff.isPresent() && sameEnchantLevelDiff.getAsInt() == 0;
        }

        public boolean canEnchant() {
            if (is_tome_entry) return false;
            if (is_category_header) return true;
            if (shelf_locked || discovery_locked || handler.isRitualBlocked()) return false;
            if (handler.isRitualActive()) {
                boolean nonQueueable = is_transformation
                    || option_type == OptionType.BOOK_COMBINE_UPGRADE
                    || option_type == OptionType.BOOK_RESEARCH_UPGRADE
                    || option_type == OptionType.DISENCHANT_ALL
                    || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                    || option_type == OptionType.STRIP_SINGLE_ENCHANTMENT
                    || option_type == OptionType.PURGE_CURSE
                    || cost_item_stack.is(Items.ENCHANTED_BOOK);
                if (nonQueueable) {
                    return false;
                }
            }
            return xpConditionMet() && itemCostConditionMet() && !enchantAlreadyExist();
        }

        public boolean xpConditionMet() {
            if (xp_requirement.isRefund() || (xp_requirement.requiredLevel() <= 0 && xp_requirement.deductedLevels() <= 0)) {
                return true;
            }
            return player.getAbilities().instabuild || player.experienceLevel >= xp_level_cost;
        }

        public boolean primaryCostMet() {
            if (player.getAbilities().instabuild || isZeroMaterialRow()) return true;
            ItemStack cost = activePrimaryCost();
            if (cost.isEmpty() && cost_item_stack.is(Items.ENCHANTED_BOOK)) return true;
            return !cost.isEmpty()
                && !cost_item_stack.isEmpty()
                && ItemStack.isSameItemSameComponents(cost, cost_item_stack)
                && handler.countAvailableIncludingCostSlot(cost) >= cost.getCount();
        }

        public boolean hasInventoryCost(ItemStack additionalCost) {
            if (player.getAbilities().instabuild) return true;
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
            if (player.getAbilities().instabuild || isZeroMaterialRow()) return true;
            if (cost_options.isEmpty()) {
                return cost_item_stack.is(Items.ENCHANTED_BOOK);
            }
            for (List<ItemStack> bundle : cost_options) {
                if (isBundleFullyAffordable(bundle)) {
                    return true;
                }
            }
            return false;
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
                parent.selectTome(this.tome_book);
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
                ClientPlayNetworking.send(new ApplyEnchantmentPayload(handler.containerId, option_type, enchantment_id, enchant_level));
            } else {
                ChiseledEnchantingTableScreen.notifyChannelUnavailableClient();
            }
            return true;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDouble) {
            // Audit M-01: only the left mouse button may apply an enchantment (or toggle a
            // category). Right/middle clicks used to enchant as well, which made inspecting
            // a row with other buttons spend XP and materials.
            if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
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
            if (option_type == OptionType.DISENCHANT_ALL) {
                if (Minecraft.getInstance().screen instanceof ChiseledEnchantingTableScreen tableScreen
                    && tableScreen.isDisenchantAllConfirmPending()) {
                    return new EnchantmentRowRenderer.OverrideAnnotation(
                        I18n.get("gui.chiseled_enchanting_table.cleanse.confirm_row_sub"),
                        RowRenderSupport.EPIPHANY_GOLD
                    );
                }
                String sub = enchantable_item.is(Items.ENCHANTED_BOOK)
                    ? I18n.get("gui.chiseled_enchanting_table.cleanse.all_sub_book")
                    : I18n.get("gui.chiseled_enchanting_table.cleanse.all_sub_item");
                return new EnchantmentRowRenderer.OverrideAnnotation(sub, RowRenderSupport.LIGHT_GREEN);
            }
            if (option_type == OptionType.DOWNGRADE_ENCHANTMENT) {
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.cleanse.downgrade_sub"),
                    RowRenderSupport.LIGHT_GREEN
                );
            }
            if (option_type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
                int kept = Math.max(1, EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item).size() - 1);
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.cleanse.strip_sub", kept),
                    RowRenderSupport.CYAN
                );
            }
            if (option_type == OptionType.PURGE_CURSE) {
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.cleanse.curse_sub"),
                    RowRenderSupport.EPIPHANY_GOLD
                );
            }
            if (enchantAlreadyExist()) {
                return new EnchantmentRowRenderer.OverrideAnnotation("\u2714 " + enchantmentName, RowRenderSupport.CYAN);
            }
            if (sameEnchantLevelDiff.isPresent() && existingSameLevel.isPresent() && sameEnchantLevelDiff.getAsInt() > 0) {
                String existingName = Enchantment.getFullname(this.enchantment, existingSameLevel.getAsInt()).getString();
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.upgrade_from", existingName),
                    RowRenderSupport.LIGHT_GREEN
                );
            }
            var firstOverridenEnchant = this.overridenEnchant.stream().findFirst();
            if (firstOverridenEnchant.isPresent() && !enchantable_item.is(Items.ENCHANTED_BOOK)) {
                var enchant = firstOverridenEnchant.get();
                var name = Enchantment.getFullname(IdentifierToEnchantment(enchant.enchantment_id(), this.player), enchant.enchantment_level()).getString();
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
                RowRenderSupport.drawPlaque(ctx, dx, dy, dw, dh,
                    RowRenderSupport.RowVisualStyle.STANDARD, true, hovered, isFocused || selected);
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
                if (discovery_locked) {
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
                TransformationRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, rowTitle, subText, subColor, cost, additional_costs, xp_requirement
                );
                if (discovery_locked) {
                    ctx.fill(dx + 3, dy + 3, dx + dw - 3, dy + dh - 3, 0x380A0614);
                }
                return;
            }

            if (option_type == OptionType.DISENCHANT_ALL
                || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                || option_type == OptionType.STRIP_SINGLE_ENCHANTMENT
                || option_type == OptionType.PURGE_CURSE) {
                AvailableOption opt = new AvailableOption(option_type, enchantment_id, enchant_level);
                String title = parent.displayName(opt);
                RowRenderSupport.RowVisualStyle style = (option_type == OptionType.PURGE_CURSE)
                    ? RowRenderSupport.RowVisualStyle.CURSE_PURGE
                    : (cost.isEmpty() ? RowRenderSupport.RowVisualStyle.CLEANSE : RowRenderSupport.RowVisualStyle.STANDARD);
                EnchantmentRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, style,
                    isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, title, cost, additional_costs, xp_requirement,
                    computeOverrideAnnotation(title)
                );
                return;
            }

            String rawName = Enchantment.getFullname(this.enchantment, enchant_level).getString();
            String enchantmentName = rawName;
            if (option_type == OptionType.TREASURE_EPIPHANY) enchantmentName = I18n.get("gui.chiseled_enchanting_table.treasure_epiphany", rawName);
            if (option_type == OptionType.BOOK_COMBINE_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.combine", rawName);
            if (option_type == OptionType.BOOK_RESEARCH_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.research", rawName);
            EnchantmentRowRenderer.render(
                ctx, client, dx, dy, dw, dh, option_type == OptionType.TREASURE_EPIPHANY,
                isFocused, hovered, canEnchant(),
                primaryMet, additionalMet, xpMet, enchantmentName, cost, additional_costs, xp_requirement,
                computeOverrideAnnotation(rawName)
            );
            if (shelf_locked) {
                ctx.fill(dx + 3, dy + 3, dx + dw - 3, dy + dh - 3, 0x380A0614);
            }
        }
    }
}
