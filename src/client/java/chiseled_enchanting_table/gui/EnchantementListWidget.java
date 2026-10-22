package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.row.EnchantmentRowRenderer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.gui.row.TransformationRowRenderer;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
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
    public Set<EnchantmentWithLevel> unlocked_enchantements = null;
    public ItemStack enchantable_item;
    public ItemStack cost_item_stack;
    public Player player;
    public ChiseledEnchantingTableScreenHandler handler;

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

    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Player player) {
        RegistryAccess registryAccess = player.level().registryAccess();
        Registry<Enchantment> registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        return registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant, Player player) {
        return enchant.unwrapKey().get().identifier();
    }

    public String displayName(AvailableOption option) {
        if (option.isTransformation()) {
            return this.handler.syncedTransformationOutputName(option.id());
        }
        String name = Enchantment.getFullname(IdentifierToEnchantment(option.id(), this.player), option.level()).getString();
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
        if (mouseX >= this.getX() - 4 && mouseX <= this.getX() + this.width + 12 && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
            this.setScrollAmount(this.scrollAmount() - scrollY * this.defaultEntryHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.isDragging() || this.updateScrolling(event)) {
            this.setScrollAmount(this.scrollAmount() + dragY);
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

    public void updateEntry(ItemStack enchantable_item, ItemStack cost_item_stack) {
        var navigationIndex = saveNavigationIfSameItem(enchantable_item, cost_item_stack);

        this.clearEntries();
        if (navigationIndex == -1) {
            this.setScrollAmount(0);
        }
        this.enchantable_item = enchantable_item;
        this.cost_item_stack = cost_item_stack;
        var enchantFromBook = cost_item_stack.is(Items.ENCHANTED_BOOK);

        List<AvailableOption> options = new ArrayList<>();
        if (enchantFromBook) {
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            for (var entry : enchantments.entrySet()) {
                options.add(AvailableOption.enchantment(EnchantmentToIdentifier(entry.getKey(), this.player), entry.getIntValue()));
            }
        } else if (!cost_item_stack.is(Items.BOOK) && unlocked_enchantements != null) {
            for (EnchantmentWithLevel e : unlocked_enchantements) {
                options.add(AvailableOption.enchantment(e));
            }
        }
        if (enchantable_item.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments targetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
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
                } else if (researchEnabled && !cost_item_stack.is(Items.ENCHANTED_BOOK) && !cost_item_stack.is(Items.BOOK)) {
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
            Holder<Enchantment> enchant = IdentifierToEnchantment(option.id(), this.player);
            return enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchant.value().canEnchant(enchantable_item);
        }).sorted((o1, o2) -> {
            String name1 = displayName(o1);
            String name2 = displayName(o2);
            int nameComparison = name1.compareTo(name2);
            if (nameComparison != 0) return nameComparison;
            return Integer.compare(o1.level(), o2.level());
        }).forEach(option -> {
            var ewl = option.asEnchantmentWithLevel();
            var cost = option.isBookCombineUpgrade() || (enchantFromBook && !option.isBookResearchUpgrade())
                ? ItemStack.EMPTY
                : this.handler.getEnchantmentItemCost(ewl, option.type());
            var xpCost = this.handler.getEnchantmentXpLevelCost(ewl, option.type());
            if (enchantFromBook && !option.isBookUpgrade()) xpCost = 0;
            this.addEntry(new EnchantUiEntry(minecraft, this.handler, option, cost, xpCost, this));
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
        public ItemStack cost;
        public List<ItemStack> additional_costs;
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
            ItemStack cost,
            int xp_level_cost,
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
            this.cost = cost;
            boolean fromBook = this.cost_item_stack.is(Items.ENCHANTED_BOOK);
            this.additional_costs = (fromBook && !option.isBookResearchUpgrade())
                ? java.util.List.of()
                : handler.getAdditionalEnchantmentItemCosts(option.asEnchantmentWithLevel(), option.type());
            this.xp_level_cost = xp_level_cost;
            this.enchantment = this.is_transformation ? null : IdentifierToEnchantment(option.id(), this.player);

            this.overridenEnchant = new ArrayList<>();
            if (this.enchantment != null) {
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

        public boolean enchantAlreadyExist() {
            return sameEnchantLevelDiff.isPresent() && sameEnchantLevelDiff.getAsInt() == 0;
        }

        public boolean canEnchant() {
            return xpConditionMet() && itemCostConditionMet() && !enchantAlreadyExist();
        }

        public boolean xpConditionMet() {
            return player.getAbilities().instabuild || player.experienceLevel >= xp_level_cost;
        }

        public boolean primaryCostMet() {
            if (player.getAbilities().instabuild) return true;
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
            List<Boolean> out = new ArrayList<>(additional_costs.size());
            for (ItemStack additionalCost : additional_costs) {
                out.add(hasInventoryCost(additionalCost));
            }
            return out;
        }

        public boolean itemCostConditionMet() {
            if (!primaryCostMet()) return false;
            for (ItemStack additionalCost : additional_costs) {
                if (!hasInventoryCost(additionalCost)) return false;
            }
            return true;
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
            int itemY = dy + RowRenderSupport.COST_OFFSET_Y;
            if (mouseY < itemY || mouseY > itemY + 16) {
                return null;
            }
            int primaryX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            if (!cost.isEmpty() && mouseX >= primaryX && mouseX <= primaryX + 16) {
                return cost;
            }
            int startX = dx + dw - RowRenderSupport.ADDITIONAL_COST_RIGHT_MARGIN;
            int shown = Math.min(2, additional_costs.size());
            for (int i = 0; i < shown; i++) {
                int x = startX + i * RowRenderSupport.ADDITIONAL_COST_STRIDE;
                if (mouseX >= x && mouseX <= x + 16) {
                    return additional_costs.get(i);
                }
            }
            return null;
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
            if (enchantAlreadyExist()) {
                return new EnchantmentRowRenderer.OverrideAnnotation("\u2714 " + enchantmentName, RowRenderSupport.CYAN);
            }
            if (sameEnchantLevelDiff.isPresent() && existingSameLevel.isPresent()) {
                String existingName = Enchantment.getFullname(this.enchantment, existingSameLevel.getAsInt()).getString();
                if (sameEnchantLevelDiff.getAsInt() > 0) {
                    return new EnchantmentRowRenderer.OverrideAnnotation("\u2B06 " + existingName, RowRenderSupport.LIGHT_GREEN);
                }
                if (sameEnchantLevelDiff.getAsInt() < 0) {
                    return new EnchantmentRowRenderer.OverrideAnnotation("\u2B07 " + existingName, RowRenderSupport.LIGHT_RED);
                }
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

            if (is_transformation) {
                String outputName = handler.syncedTransformationOutputName(this.enchantment_id);
                TransformationRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, outputName, cost, additional_costs, xp_level_cost
                );
            } else {
                String enchantmentName = Enchantment.getFullname(this.enchantment, enchant_level).getString();
                if (option_type == OptionType.BOOK_COMBINE_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.combine", enchantmentName);
                if (option_type == OptionType.BOOK_RESEARCH_UPGRADE) enchantmentName = I18n.get("gui.chiseled_enchanting_table.book_upgrade.research", enchantmentName);
                EnchantmentRowRenderer.render(
                    ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant(),
                    primaryMet, additionalMet, xpMet, enchantmentName, cost, additional_costs, xp_level_cost,
                    computeOverrideAnnotation(enchantmentName)
                );
            }
        }
    }
}
