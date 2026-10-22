package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.row.EnchantmentRowRenderer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.gui.row.TransformationRowRenderer;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
        CLEANSE
    }

    public Set<EnchantmentWithLevel> unlocked_enchantements = null;
    public ItemStack enchantable_item;
    public ItemStack cost_item_stack;
    public Player player;
    public ChiseledEnchantingTableScreenHandler handler;
    private UiTabMode tabMode = UiTabMode.INSCRIBE;

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
        this.enchantable_item = handler.get_enchantable_item();
        this.cost_item_stack = handler.get_cost_item();
        this.unlocked_enchantements = handler.unlocked_enchantements;
        this.player = handler.player;
        this.setFocused(null);
    }

    public UiTabMode tabMode() {
        return this.tabMode;
    }

    public void setTabMode(UiTabMode nextMode) {
        if (nextMode != null && this.tabMode != nextMode) {
            this.tabMode = nextMode;
            this.setFocused(null);
            this.setScrollAmount(0);
            this.updateEntry(this.handler.get_enchantable_item(), this.handler.get_cost_item());
        }
    }

    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Player player) {
        RegistryAccess registryAccess = player.level().registryAccess();
        Registry<Enchantment> registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        return registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant, Player player) {
        return enchant.unwrapKey().get().identifier();
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
        if (keyCode == 257 || keyCode == 335) {
            var focusedEntry = this.getFocused();
            if (focusedEntry != null) {
                focusedEntry.serverSendApplyEnchant();
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
            if (entry.isOverXpBadge(mouseX, mouseY)) {
                return entry;
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
        var navigationIndex = saveNavigationIfSameItem(enchantable_item, cost_item_stack);

        this.clearEntries();
        if (navigationIndex == -1) {
            this.setScrollAmount(0);
        }
        this.enchantable_item = enchantable_item;
        this.cost_item_stack = cost_item_stack;

        if (this.tabMode == UiTabMode.CLEANSE) {
            populateCleanseEntries(navigationIndex);
            return;
        }
        populateInscribeEntries(navigationIndex);
    }

    private void populateCleanseEntries(int navigationIndex) {
        if (!this.handler.disenchantEnabled() || this.enchantable_item.isEmpty()) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(this.enchantable_item);
        if (current.isEmpty()) {
            return;
        }

        List<AvailableOption> cleanseOptions = new ArrayList<>();
        int nonCurseCount = DisenchantmentApplication.countNonCurseEnchantments(this.enchantable_item);
        if (nonCurseCount >= 1) {
            cleanseOptions.add(AvailableOption.disenchantAll(DisenchantmentApplication.DISENCHANT_ALL_ID));
        }

        List<Holder<Enchantment>> sortedKeys = new ArrayList<>(current.keySet());
        sortedKeys.sort((a, b) -> {
            String na = Enchantment.getFullname(a, 1).getString();
            String nb = Enchantment.getFullname(b, 1).getString();
            return na.compareTo(nb);
        });

        int totalTraits = current.size();
        for (Holder<Enchantment> holder : sortedKeys) {
            int curLvl = current.getLevel(holder);
            if (curLvl <= 0) continue;
            Identifier id = EnchantmentToIdentifier(holder, this.player);
            if (DisenchantmentApplication.isCurse(holder)) {
                if (this.handler.disenchantAllowCursePurge()) {
                    cleanseOptions.add(AvailableOption.purgeCurse(id, curLvl));
                }
            } else {
                // Free XP-refunding step-down options (e.g. V -> IV, V -> III, ..., V -> I)
                for (int nextLvl = curLvl - 1; nextLvl >= 1; nextLvl--) {
                    cleanseOptions.add(AvailableOption.downgradeEnchantment(id, nextLvl));
                }
                // Paid surgical single-trait removal when preserving >= 1 other enchantment on the item
                if (totalTraits >= 2) {
                    cleanseOptions.add(AvailableOption.stripSingleEnchantment(id, curLvl));
                }
            }
        }

        for (AvailableOption opt : cleanseOptions) {
            var ewl = opt.asEnchantmentWithLevel();
            List<List<ItemStack>> costOpts = this.handler.getEffectiveItemCostOptions(ewl, opt.type());
            var xpReq = this.handler.resolveEffectiveXpRequirement(ewl, opt.type());
            this.addEntry(new EnchantUiEntry(minecraft, this.handler, opt, costOpts, xpReq, this));
        }
        resumeNavigation(navigationIndex);
    }

    private void populateInscribeEntries(int navigationIndex) {
        var enchantFromBook = cost_item_stack.is(Items.ENCHANTED_BOOK);
        ItemEnchantments targetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);

        List<AvailableOption> options = new ArrayList<>();
        Set<EnchantmentWithLevel> unlockedDirectSet = new HashSet<>();

        if (enchantFromBook) {
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            for (var entry : enchantments.entrySet()) {
                int existingLvl = targetEnchantments.getLevel(entry.getKey());
                if (entry.getIntValue() > existingLvl) {
                    options.add(AvailableOption.enchantment(EnchantmentToIdentifier(entry.getKey(), this.player), entry.getIntValue()));
                }
            }
        } else if (!cost_item_stack.is(Items.BOOK)) {
            if (unlocked_enchantements != null) {
                for (EnchantmentWithLevel e : unlocked_enchantements) {
                    Holder<Enchantment> h = IdentifierToEnchantment(e.enchantment_id(), this.player);
                    int existingLvl = targetEnchantments.getLevel(h);
                    // Hide downward rows from Inscribe mode — downgrading lives in Cleanse mode and refunds XP!
                    if (e.enchantment_level() >= existingLvl) {
                        options.add(AvailableOption.enchantment(e));
                        unlockedDirectSet.add(e);
                    }
                }
            }
            for (EnchantmentWithLevel epiphany : this.handler.computeActiveTreasureEpiphanies(enchantable_item)) {
                Holder<Enchantment> h = IdentifierToEnchantment(epiphany.enchantment_id(), this.player);
                if (epiphany.enchantment_level() > targetEnchantments.getLevel(h)) {
                    options.add(AvailableOption.treasureEpiphany(epiphany.enchantment_id(), epiphany.enchantment_level()));
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
                    options.add(AvailableOption.bookCombineUpgrade(id, targetLevel));
                } else if (researchEnabled
                    && !cost_item_stack.is(Items.ENCHANTED_BOOK)
                    && !cost_item_stack.is(Items.BOOK)
                    && !unlockedDirectSet.contains(new EnchantmentWithLevel(id, targetLevel))) {
                    // Only emit a separate BOOK_RESEARCH_UPGRADE row when shelves/Master Tome do not already unlock targetLevel;
                    // when unlocked directly, getEffectiveItemCostOptions already gives the strictly cheaper of both!
                    options.add(AvailableOption.bookResearchUpgrade(id, targetLevel));
                }
            }
        }
        for (EnchantmentWithLevel t : this.handler.syncedTransformationsFor(enchantable_item)) {
            options.add(AvailableOption.transformation(t.enchantment_id(), t.enchantment_level()));
        }

        options.stream().filter(option -> {
            if (option.isTransformation()) {
                return this.handler.syncedTransformationMatches(option.id(), enchantable_item);
            }
            if (ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
                return false;
            }
            Holder<Enchantment> enchant = IdentifierToEnchantment(option.id(), this.player);
            return enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchant.value().canEnchant(enchantable_item);
        }).sorted((o1, o2) -> {
            int p1 = o1.isTreasureEpiphany() ? 0 : (o1.isTransformation() ? 1 : (o1.isBookUpgrade() ? 2 : 3));
            int p2 = o2.isTreasureEpiphany() ? 0 : (o2.isTransformation() ? 1 : (o2.isBookUpgrade() ? 2 : 3));
            if (p1 != p2) return Integer.compare(p1, p2);
            String name1 = displayName(o1);
            String name2 = displayName(o2);
            int nameComparison = name1.compareTo(name2);
            if (nameComparison != 0) return nameComparison;
            return Integer.compare(o1.level(), o2.level());
        }).forEach(option -> {
            var ewl = option.asEnchantmentWithLevel();
            List<List<ItemStack>> costOptions = option.isBookCombineUpgrade() || (enchantFromBook && !option.isBookResearchUpgrade())
                ? List.of()
                : this.handler.getEffectiveItemCostOptions(ewl, option.type());
            var xpReq = (enchantFromBook && !option.isBookUpgrade())
                ? XpCostConversion.XpRequirement.FREE
                : this.handler.resolveEffectiveXpRequirement(ewl, option.type());
            this.addEntry(new EnchantUiEntry(minecraft, this.handler, option, costOptions, xpReq, this));
        });
        resumeNavigation(navigationIndex);
    }

    public static class EnchantUiEntry extends AbstractSelectionList.Entry<EnchantUiEntry> {
        public ItemStack enchantable_item;
        public ItemStack cost_item_stack;
        public int enchant_level;
        public boolean is_transformation;
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
            this.enchantable_item = handler.get_enchantable_item();
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

            this.overridenEnchant = new ArrayList<>();
            if (this.enchantment != null && !option.isCleanseOperation()) {
                ItemEnchantments currentEnchants = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
                int currentSameLevel = currentEnchants.getLevel(this.enchantment);
                if (currentSameLevel > 0) {
                    this.existingSameLevel = OptionalInt.of(currentSameLevel);
                    this.sameEnchantLevelDiff = OptionalInt.of(this.enchant_level - currentSameLevel);
                }
                for (var entry : currentEnchants.entrySet()) {
                    Identifier existingId = EnchantmentToIdentifier(entry.getKey(), this.player);
                    if (!existingId.equals(this.enchantment_id) && !Enchantment.areCompatible(entry.getKey(), this.enchantment)) {
                        this.overridenEnchant.add(new EnchantmentWithLevel(existingId, entry.getIntValue()));
                    }
                }
            }
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

        private boolean isFreeCleanseRow() {
            return option_type == OptionType.DISENCHANT_ALL
                || option_type == OptionType.DOWNGRADE_ENCHANTMENT
                || (option_type == OptionType.STRIP_SINGLE_ENCHANTMENT && cost_options.isEmpty());
        }

        private boolean isBundleFullyAffordable(List<ItemStack> bundle) {
            if (player.getAbilities().instabuild) return true;
            if (bundle.isEmpty()) return isFreeCleanseRow() || cost_item_stack.is(Items.ENCHANTED_BOOK);
            ItemStack primary = bundle.getFirst();
            if (primary.isEmpty()
                || !ItemStack.isSameItemSameComponents(primary, cost_item_stack)
                || cost_item_stack.getCount() < primary.getCount()) {
                return false;
            }
            for (int i = 1; i < bundle.size(); i++) {
                if (!hasInventoryCost(bundle.get(i))) return false;
            }
            return true;
        }

        public boolean enchantAlreadyExist() {
            return sameEnchantLevelDiff.isPresent() && sameEnchantLevelDiff.getAsInt() == 0;
        }

        public boolean canEnchant() {
            return xpConditionMet() && itemCostConditionMet() && !enchantAlreadyExist();
        }

        public boolean xpConditionMet() {
            if (xp_requirement.isRefund() || (xp_requirement.requiredLevel() <= 0 && xp_requirement.deductedLevels() <= 0)) {
                return true;
            }
            return player.getAbilities().instabuild || player.experienceLevel >= xp_level_cost;
        }

        public boolean primaryCostMet() {
            if (player.getAbilities().instabuild || isFreeCleanseRow()) return true;
            ItemStack cost = activePrimaryCost();
            if (cost.isEmpty() && cost_item_stack.is(Items.ENCHANTED_BOOK)) return true;
            return !cost.isEmpty()
                && ItemStack.isSameItemSameComponents(cost, cost_item_stack)
                && cost_item_stack.getCount() >= cost.getCount();
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
            if (player.getAbilities().instabuild || isFreeCleanseRow()) return true;
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
            int dx = this.getX() + 1;
            int dy = this.getY();
            int dw = this.getWidth() - 2;
            int dh = this.getHeight();
            if (mouseX < dx || mouseX > dx + dw || mouseY < dy || mouseY > dy + dh) {
                return null;
            }
            int itemY = RowRenderSupport.costIconY(dy, dh);
            int iconSize = RowRenderSupport.COST_ICON_SIZE;
            if (mouseY < itemY || mouseY > itemY + iconSize) {
                return null;
            }
            ItemStack cost = activePrimaryCost();
            List<ItemStack> additional_costs = activeAdditionalCosts();
            int primaryX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            if (!cost.isEmpty() && mouseX >= primaryX && mouseX <= primaryX + iconSize) {
                return cost;
            }
            int shown = Math.min(2, additional_costs.size());
            if (shown > 0) {
                int startX = RowRenderSupport.additionalCostStartX(dx, dw, shown);
                for (int i = 0; i < shown; i++) {
                    int x = startX + i * RowRenderSupport.ADDITIONAL_COST_STRIDE;
                    if (mouseX >= x && mouseX <= x + iconSize) {
                        return additional_costs.get(i);
                    }
                }
            }
            return null;
        }

        @Nullable
        public List<List<ItemStack>> hoveredCostOptions(double mouseX, double mouseY) {
            int dx = this.getX() + 1;
            int dy = this.getY();
            int dw = this.getWidth() - 2;
            int dh = this.getHeight();
            if (mouseX < dx || mouseX > dx + dw || mouseY < dy || mouseY > dy + dh) {
                return null;
            }
            int itemY = RowRenderSupport.costIconY(dy, dh);
            int iconSize = RowRenderSupport.COST_ICON_SIZE;
            if (mouseY < itemY || mouseY > itemY + iconSize) {
                return null;
            }
            ItemStack cost = activePrimaryCost();
            int primaryX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            if (!cost.isEmpty() && mouseX >= primaryX && mouseX <= primaryX + iconSize && cost_options.size() > 1) {
                return cost_options;
            }
            return null;
        }

        public boolean isOverXpBadge(double mouseX, double mouseY) {
            int dx = this.getX() + 1;
            int dy = this.getY();
            int dw = this.getWidth() - 2;
            int dh = this.getHeight();
            return mouseX >= dx + dw - RowRenderSupport.XP_BADGE_WIDTH
                && mouseX <= dx + dw
                && mouseY >= dy
                && mouseY <= dy + dh;
        }

        public boolean serverSendApplyEnchant() {
            if (!this.canEnchant()) return false;
            ClientPlayNetworking.send(new ApplyEnchantmentPayload(option_type, enchantment_id, enchant_level));
            return true;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDouble) {
            return serverSendApplyEnchant();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int index = parent.children().indexOf(this);
            int top = parent.getRowTop(index);
            int bottom = parent.getRowBottom(index);
            int left = parent.getRowLeft();
            int right = parent.getRowRight();
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }

        private EnchantmentRowRenderer.OverrideAnnotation computeOverrideAnnotation(String enchantmentName) {
            if (option_type == OptionType.DISENCHANT_ALL) {
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
                return new EnchantmentRowRenderer.OverrideAnnotation("\u2716 " + name, RowRenderSupport.LIGHT_RED);
            }
            return null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean hovered, float delta) {
            var dx = this.getX() + 1;
            var dw = this.getWidth() - 2;
            var dh = this.getHeight();
            var dy = this.getY();
            var isFocused = parent.getFocused() == this;
            boolean primaryMet = primaryCostMet();
            List<Boolean> additionalMet = additionalCostsAffordability();
            boolean xpMet = xpConditionMet();

            ItemStack cost = activePrimaryCost();
            List<ItemStack> additional_costs = activeAdditionalCosts();

            if (is_transformation) {
                String outputName = handler.syncedTransformationOutputName(this.enchantment_id);
                String rowTitle = this.enchantment_id.equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID)
                    ? I18n.get("gui.chiseled_enchanting_table.blank_overwrite", outputName)
                    : outputName;
                TransformationRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, rowTitle, cost, additional_costs, xp_requirement
                );
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
        }
    }
}
