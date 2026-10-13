package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.EnchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.BookUpgradeApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.transformation.ItemTransformationApplication;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TransformationDisplay;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.util.Prediction;

/**
 * The chiseled enchanting menu container.
 *
 * <p>Architecture note (P15): this class now only owns the menu/container responsibilities — slot
 * layout, cost resolution, condition/consumption, and dispatch. The actual gameplay mechanics live
 * in {@code mechanics.enchanting.EnchantmentApplication} and
 * {@code mechanics.transformation.ItemTransformationApplication}; wire payloads live in
 * {@code menu.ChiseledEnchantingMenuPayloads}.</p>
 */
public class ChiseledEnchantingTableScreenHandler extends AbstractContainerMenu {

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID,
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler chiseledEnchantingTableScreenHandler) {
                    chiseledEnchantingTableScreenHandler.applyEnchantement(payload);
                }
            });
        });
    }

    public Runnable onContentChangedCallback = () -> {};
    public Player player;
    public final Set<EnchantmentWithLevel> unlocked_enchantements;
    /** Server-synced transformation display table, keyed by transformation id. Used by client UI. */
    public final java.util.Map<Identifier, TransformationDisplay> transformationDisplays;
    private final Level world;

    private final Container inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ChiseledEnchantingTableScreenHandler.this.slotsChanged(this);
        }
    };

    public static final int ENCHANTABLE_SLOT = 0;
    public static final int COST_SLOT = 1;

    public ChiseledEnchantingTableScreenHandler(int syncId, Inventory playerInventory, AvailableEnchantmentPayload payload) {
        super(ScreenHandlerRegistry.CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER, syncId);
        this.unlocked_enchantements = payload.unlocked_enchantements();
        java.util.Map<Identifier, TransformationDisplay> displays = new java.util.HashMap<>();
        for (TransformationDisplay display : payload.transformations()) {
            displays.put(display.id(), display);
        }
        this.transformationDisplays = java.util.Map.copyOf(displays);
        this.player = playerInventory.player;
        this.world = playerInventory.player.level();

        int inventorySlotStartX = 172;
        int inventorySlotStartY = 84;
        int slotSize = 16;
        int slotPadding = 1;
        int slotFullSize = slotSize + slotPadding*2;
        int rowLength = 9;
        int colmumnLength = 3;
        int hotbarMargin = 4;

        int cost_slot_gap = 4;
        int enchantable_slot_gap = 12;

        this.addSlot(new Slot(
            this.inventory,
            ENCHANTABLE_SLOT,
            inventorySlotStartX + slotSize + slotPadding*2,
            inventorySlotStartY - slotSize * 2 - slotPadding*3 - cost_slot_gap - enchantable_slot_gap
        ) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        this.addSlot(new Slot(
            this.inventory,
            COST_SLOT,
            inventorySlotStartX + slotSize + slotPadding*2,
            inventorySlotStartY - slotSize - slotPadding*2 - cost_slot_gap
        ) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                if (stack.is(Items.ENCHANTED_BOOK)) {
                    return 1;
                }
                return super.getMaxStackSize(stack);
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });

        for (int row = 0; row < rowLength; ++row) {
            this.addSlot(new Slot(
                playerInventory,
                row,
                inventorySlotStartX + row * slotFullSize,
                inventorySlotStartY + colmumnLength * slotFullSize + hotbarMargin
            ));
        }
        for (int row = 0; row < colmumnLength; ++row) {
            for (int column = 0; column < rowLength; ++column) {
                this.addSlot(new Slot(
                    playerInventory,
                    column + (row + 1) * rowLength,
                    inventorySlotStartX + column * slotFullSize,
                    inventorySlotStartY + row * slotFullSize
                ));
            }
        }
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    // ---- Slot/state accessors used by mechanics.* ----

    public Level world() {
        return this.world;
    }

    public ItemStack get_enchantable_item() {
        return this.getSlot(ENCHANTABLE_SLOT).getItem();
    }

    public ItemStack get_cost_item() {
        return this.getSlot(COST_SLOT).getItem();
    }

    public void setEnchantableSlot(ItemStack stack) {
        this.inventory.setItem(ENCHANTABLE_SLOT, stack);
    }

    public void setCostSlot(ItemStack stack) {
        this.inventory.setItem(COST_SLOT, stack);
    }

    public void markInventoryChanged() {
        this.inventory.setChanged();
    }

    // ---- Transformation display helpers (synced data, with local fallback) ----

    /** Whether the given id is a transformation, preferring server-synced data over local data. */
    public boolean isSyncedTransformation(Identifier id) {
        return this.transformationDisplays.containsKey(id) || ChiseledEnchantingTableData.isTransformation(id);
    }

    /** Transformation options whose input matches the given stack, from server-synced data. */
    public Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        if (this.transformationDisplays.isEmpty()) {
            return ChiseledEnchantingTableData.transformationEnchantmentsFor(input);
        }
        for (TransformationDisplay display : this.transformationDisplays.values()) {
            Item inputItem = BuiltInRegistries.ITEM.getValue(display.input_item());
            if (!input.isEmpty() && inputItem != null && input.is(inputItem)) {
                result.add(new EnchantmentWithLevel(display.id(), display.level()));
            }
        }
        return result;
    }

    /** Whether a synced transformation's input matches the stack. */
    public boolean syncedTransformationMatches(Identifier id, ItemStack input) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display == null) {
            return ChiseledEnchantingTableData.itemTransformation(id).map(rule -> rule.matchesInput(input)).orElse(false);
        }
        Item inputItem = BuiltInRegistries.ITEM.getValue(display.input_item());
        return !input.isEmpty() && inputItem != null && input.is(inputItem);
    }

    /** Display (hover) name of a synced transformation's output item. */
    public String syncedTransformationOutputName(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display == null) {
            return ChiseledEnchantingTableData.itemTransformation(id)
                .map(rule -> rule.outputStack(1).getHoverName().getString())
                .orElse(id.toString());
        }
        Item outputItem = BuiltInRegistries.ITEM.getValue(display.output_item());
        if (outputItem == null) {
            return id.toString();
        }
        return new ItemStack(outputItem).getHoverName().getString();
    }

    // ---- Cost resolution ----

    public ItemStack getEnchantmentItemCost(EnchantmentWithLevel e) {
        return getEnchantmentItemCost(e, OptionType.ENCHANTMENT);
    }

    public ItemStack getEnchantmentItemCost(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return ItemStack.EMPTY;
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchPrimaryCost(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
        }
        if (ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationPrimaryCost(e.enchantment_id(), this.player.getStringUUID());
        }
        return ChiseledEnchantingTableData.itemCost(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
    }

    public List<ItemStack> getEnchantmentItemCosts(EnchantmentWithLevel e) {
        return getEnchantmentItemCosts(e, OptionType.ENCHANTMENT);
    }

    public List<ItemStack> getEnchantmentItemCosts(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return java.util.List.of();
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchCosts(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
        }
        if (ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            java.util.ArrayList<ItemStack> costs = new java.util.ArrayList<>();
            costs.add(ChiseledEnchantingTableData.transformationPrimaryCost(e.enchantment_id(), this.player.getStringUUID()));
            costs.addAll(ChiseledEnchantingTableData.transformationAdditionalCosts(e.enchantment_id(), this.player.getStringUUID()));
            return java.util.List.copyOf(costs);
        }
        return ChiseledEnchantingTableData.itemCosts(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
    }

    public List<ItemStack> getAdditionalEnchantmentItemCosts(EnchantmentWithLevel e) {
        return getAdditionalEnchantmentItemCosts(e, OptionType.ENCHANTMENT);
    }

    public List<ItemStack> getAdditionalEnchantmentItemCosts(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return java.util.List.of();
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchAdditionalCosts(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
        }
        if (ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationAdditionalCosts(e.enchantment_id(), this.player.getStringUUID());
        }
        return ChiseledEnchantingTableData.additionalItemCosts(e, this.player.getStringUUID(), getEnchantmentMaxLevel(e));
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e) {
        return getEnchantmentXpLevelCost(e, OptionType.ENCHANTMENT);
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return ChiseledEnchantingTableData.bookCombineXpCost(e, getEnchantmentMaxLevel(e));
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchXpCost(e, getEnchantmentMaxLevel(e));
        }
        if (ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationXpCost(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.xpCost(e, getEnchantmentMaxLevel(e));
    }

    private int getEnchantmentMaxLevel(EnchantmentWithLevel e) {
        try {
            return Math.max(1, IdentifierToEnchantment(e.enchantment_id(), this.world).value().getMaxLevel());
        } catch (Exception ignored) {
            return Math.max(1, e.enchantment_level());
        }
    }

    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Level world) {
        return world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant) {
        return enchant.unwrapKey().get().identifier();
    }

    public int calculateXpCostToLevel(int X) {
        int A = Math.min(X, 16);
        int B = Math.min(X, 31);
        int C = X;
        int Am = 2;
        int Ac = 7;
        int Bm = 5;
        int Bc = -38;
        int Cm = 9;
        int Cc = -158;

        return (Am * ((A - 1) * A) / 2 + Ac * A)
             + (Bm * (((B - 1) * B) / 2 - ((A - 1) * A) / 2) + Bc * (B - A))
             + (Cm * (((C - 1) * C) / 2 - ((B - 1) * B) / 2) + Cc * (C - B));
    }

    // ---- Condition / consumption (shared by both mechanics) ----

    private boolean hasAdditionalCosts(List<ItemStack> costs) {
        for (int i = 1; i < costs.size(); i++) {
            ItemStack required = costs.get(i);
            int found = 0;
            for (int slot = 0; slot < this.player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = this.player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, required)) {
                    found += candidate.getCount();
                    if (found >= required.getCount()) break;
                }
            }
            if (found < required.getCount()) return false;
        }
        return true;
    }

    private void consumeAdditionalCosts(List<ItemStack> costs) {
        for (int i = 1; i < costs.size(); i++) {
            ItemStack required = costs.get(i).copy();
            for (int slot = 0; slot < this.player.getInventory().getContainerSize() && !required.isEmpty(); slot++) {
                ItemStack candidate = this.player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, required)) {
                    int consumed = Math.min(candidate.getCount(), required.getCount());
                    candidate.shrink(consumed);
                    required.shrink(consumed);
                    this.player.getInventory().setChanged();
                }
            }
        }
    }

    public boolean applyEnchantementCondition(ApplyEnchantmentPayload payload) {
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), payload.enchantment_level());
        int xp_level_cost = getEnchantmentXpLevelCost(ewl);
        if (this.player.experienceLevel < xp_level_cost && !player.getAbilities().instabuild) return false;

        List<ItemStack> costs = getEnchantmentItemCosts(ewl);
        ItemStack item_cost_stack = this.get_cost_item();
        if (!player.getAbilities().instabuild) {
            if (costs.isEmpty()) return false;
            ItemStack primaryCost = costs.getFirst();
            if (primaryCost.isEmpty() || !ItemStack.isSameItemSameComponents(item_cost_stack, primaryCost) || item_cost_stack.getCount() < primaryCost.getCount()) return false;
            if (!hasAdditionalCosts(costs)) return false;
        }

        if (!player.getAbilities().instabuild) {
            // Deduct actual XP points corresponding to `xp_level_cost` player levels, matching
            // vanilla anvil / enchanting-table behaviour, rather than a raw
            // `giveExperienceLevels(-xp_level_cost)` which drops the player exactly N levels
            // regardless of how much XP those levels were worth. That old behaviour under-
            // charged low-level players and over-charged high-level ones by conflating
            // "N levels" with "N units of some flat currency"; see the user report on this
            // in CHANGELOG.md.
            int xpPointsToDeduct = XpCostConversion.pointsForLevelCost(this.player, xp_level_cost);
            this.player.giveExperiencePoints(-xpPointsToDeduct);
            item_cost_stack.shrink(costs.getFirst().getCount());
            consumeAdditionalCosts(costs);
        }
        return true;
    }

    // ---- Dispatch ----

    public void applyEnchantement(ApplyEnchantmentPayload payload) {
        // Prefer the explicit option type from the payload; fall back to a data lookup so older
        // clients (which always send ENCHANTMENT) still route transformations correctly.
        if (payload.option_type() == OptionType.BOOK_COMBINE_UPGRADE || payload.option_type() == OptionType.BOOK_RESEARCH_UPGRADE) {
            BookUpgradeApplication.apply(this, payload);
            return;
        }
        boolean isTransformation = payload.option_type() == OptionType.TRANSFORMATION
            || (payload.option_type() == OptionType.ENCHANTMENT && ChiseledEnchantingTableData.isTransformation(payload.enchantment_id()));
        if (isTransformation) {
            ItemTransformationApplication.apply(this, payload);
            return;
        }
        if (this.get_cost_item().is(Items.ENCHANTED_BOOK)) {
            EnchantmentApplication.applyFromBook(this, payload);
            return;
        }
        EnchantmentApplication.apply(this, payload);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inventory);
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (actionType == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE) {
            if (slotIndex < 0 || slotIndex >= this.slots.size()) return;
            ItemStack clickedStack = this.getSlot(slotIndex).getItem();
            if (clickedStack.isEmpty()) return;
            if ((slotIndex == COST_SLOT) || (slotIndex == ENCHANTABLE_SLOT) ) {
                chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, clickedStack, java.util.Optional.empty());
                clickedStack.setCount(0);
                this.inventory.setChanged();
                this.player.getInventory().setChanged();
                return;
            }
            ItemStack clickedStackNoEnchant = clickedStack.copy();
            EnchantmentHelper.updateEnchantments(clickedStackNoEnchant, m -> {
                var keys = new java.util.HashSet<>(m.keySet());
                for(var k: keys) m.set(k, 0);
            });
            boolean isBook = clickedStackNoEnchant.is(Items.BOOK) || clickedStackNoEnchant.is(Items.ENCHANTED_BOOK);

            if (clickedStackNoEnchant.isEnchantable() || (isBook && !this.getSlot(ENCHANTABLE_SLOT).hasItem())) {
                ItemStack tempStack = clickedStack.copy();
                this.getSlot(slotIndex).set(this.getSlot(ENCHANTABLE_SLOT).getItem());
                this.getSlot(ENCHANTABLE_SLOT).set(tempStack);

                if (this.getSlot(ENCHANTABLE_SLOT).getItem().getCount() > 1) {
                    ItemStack overflow = this.getSlot(ENCHANTABLE_SLOT).getItem().copy();
                    overflow.setCount(overflow.getCount() - 1);
                    this.getSlot(ENCHANTABLE_SLOT).getItem().setCount(1);
                    if (!this.getSlot(slotIndex).hasItem()) {
                        this.getSlot(slotIndex).set(overflow);
                    } else {
                        if (!this.moveItemStackTo(overflow, 2, 38, true)) {
                            // 26.3: drop(ItemStack, boolean, Prediction); container-click drops are
                            // client-predicted, matching vanilla AbstractContainerMenu.
                            this.player.drop(overflow, false, Prediction.PREDICTED);
                        }
                    }
                }
            } else {
                if (!this.getSlot(COST_SLOT).hasItem()) {
                    this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT+1, false);
                } else {
                    ItemStack costSlotStack = this.getSlot(COST_SLOT).getItem();
                    if (ItemStack.isSameItemSameComponents(clickedStack, costSlotStack)) {
                        this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT+1, false);
                    } else {
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, costSlotStack, java.util.Optional.of(slotIndex));
                        costSlotStack.setCount(0);
                    }
                }
            }
            this.inventory.setChanged();
            this.player.getInventory().setChanged();
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
