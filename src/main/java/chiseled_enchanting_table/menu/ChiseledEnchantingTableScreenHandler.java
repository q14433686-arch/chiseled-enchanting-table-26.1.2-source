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
import chiseled_enchanting_table.registry.BlockRegistry;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
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
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.TYPE,
            ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler chiseledEnchantingTableScreenHandler) {
                    chiseledEnchantingTableScreenHandler.rerollEpiphany();
                }
            });
        });
    }

    public Runnable onContentChangedCallback = () -> {};
    public Player player;
    public final Set<EnchantmentWithLevel> unlocked_enchantements;
    public final int xpDiscountPercent;
    public final int regularBookshelves;
    public final int chiseledBookCount;
    private int epiphanySeed;
    private int syncedWorldDay;
    /** Server-synced transformation table, keyed by transformation id. Used by client UI. */
    public final java.util.Map<Identifier, TransformationDisplay> transformationDisplays;
    private final Level world;
    private final ContainerLevelAccess access;

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
        this(syncId, playerInventory, payload, ContainerLevelAccess.NULL);
    }

    public ChiseledEnchantingTableScreenHandler(
        int syncId,
        Inventory playerInventory,
        AvailableEnchantmentPayload payload,
        ContainerLevelAccess access
    ) {
        super(ScreenHandlerRegistry.CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER, syncId);
        this.access = access;
        this.unlocked_enchantements = payload.unlocked_enchantements();
        this.xpDiscountPercent = Math.max(0, Math.min(35, payload.xp_discount_percent()));
        this.regularBookshelves = Math.max(0, payload.regular_bookshelves());
        this.chiseledBookCount = Math.max(0, payload.chiseled_book_count());
        this.epiphanySeed = payload.epiphany_seed();
        this.syncedWorldDay = (int) (playerInventory.player.level().getGameTime() / 24000L);
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

        // Sync 32-bit epiphanySeed + 16-bit worldDay from server -> client automatically.
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return currentServerEpiphanySeed() & 0xFFFF;
            }

            @Override
            public void set(int value) {
                epiphanySeed = (epiphanySeed & 0xFFFF0000) | (value & 0xFFFF);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (currentServerEpiphanySeed() >>> 16) & 0xFFFF;
            }

            @Override
            public void set(int value) {
                epiphanySeed = (epiphanySeed & 0x0000FFFF) | ((value & 0xFFFF) << 16);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (int) ((world.getGameTime() / 24000L) & 0xFFFFL);
            }

            @Override
            public void set(int value) {
                syncedWorldDay = value & 0xFFFF;
            }
        });
    }

    private int currentServerEpiphanySeed() {
        int[] holder = new int[] { this.epiphanySeed };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                holder[0] = be.getOrInitEpiphanySeed();
            }
        });
        this.epiphanySeed = holder[0];
        return holder[0];
    }

    public void advanceTableEpiphanySeed() {
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                this.epiphanySeed = be.advanceEpiphanySeed();
            } else {
                this.epiphanySeed = this.epiphanySeed * 1664525 + 1013904223;
            }
        });
        this.broadcastChanges();
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
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

    public List<List<ItemStack>> getEnchantmentItemCostOptions(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return List.of();
        }
        var row = syncedRow(e, type);
        if (row != null) {
            return row.costOptions();
        }
        if (type == OptionType.TREASURE_EPIPHANY) {
            return ChiseledEnchantingTableData.treasureEpiphanyCostOptions(e.enchantment_id());
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchCostOptions(e, getEnchantmentMaxLevel(e));
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationCostOptions(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.itemCostOptions(e, getEnchantmentMaxLevel(e));
    }

    public ItemStack getEnchantmentItemCost(EnchantmentWithLevel e) {
        return getEnchantmentItemCost(e, OptionType.ENCHANTMENT);
    }

    public ItemStack getEnchantmentItemCost(EnchantmentWithLevel e, OptionType type) {
        List<ItemStack> first = getEnchantmentItemCosts(e, type);
        return first.isEmpty() ? ItemStack.EMPTY : first.getFirst();
    }

    public List<ItemStack> getEnchantmentItemCosts(EnchantmentWithLevel e) {
        return getEnchantmentItemCosts(e, OptionType.ENCHANTMENT);
    }

    public List<ItemStack> getEnchantmentItemCosts(EnchantmentWithLevel e, OptionType type) {
        List<List<ItemStack>> options = getEnchantmentItemCostOptions(e, type);
        return options.isEmpty() ? List.of() : options.getFirst();
    }

    public List<ItemStack> getAdditionalEnchantmentItemCosts(EnchantmentWithLevel e) {
        return getAdditionalEnchantmentItemCosts(e, OptionType.ENCHANTMENT);
    }

    public List<ItemStack> getAdditionalEnchantmentItemCosts(EnchantmentWithLevel e, OptionType type) {
        List<ItemStack> first = getEnchantmentItemCosts(e, type);
        return first.size() <= 1 ? List.of() : List.copyOf(first.subList(1, first.size()));
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e) {
        return getEnchantmentXpLevelCost(e, OptionType.ENCHANTMENT);
    }

    public int getBaseEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) {
        var serverRow = syncedRow(e, type);
        if (serverRow != null) {
            return serverRow.xpLevelCost();
        }
        if (type == OptionType.TREASURE_EPIPHANY) {
            return ChiseledEnchantingTableData.treasureEpiphanyXpCost(e.enchantment_id());
        }
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return ChiseledEnchantingTableData.bookCombineXpCost(e, getEnchantmentMaxLevel(e));
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchXpCost(e, getEnchantmentMaxLevel(e));
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationXpCost(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.xpCost(e, getEnchantmentMaxLevel(e));
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) {
        int base = getBaseEnchantmentXpLevelCost(e, type);
        if (type == OptionType.TREASURE_EPIPHANY || base <= 0 || this.xpDiscountPercent <= 0) {
            return base;
        }
        return Math.max(1, Math.round(base * (100 - this.xpDiscountPercent) / 100.0f));
    }

    private static final net.minecraft.tags.TagKey<Enchantment> TREASURE_TAG =
        net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("treasure"));
    private static final net.minecraft.tags.TagKey<Enchantment> CURSE_TAG =
        net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    public int epiphanyMinBookshelves() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyMinBookshelves()
            : ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves();
    }

    public double epiphanyChance() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyChance(this.regularBookshelves)
            : ChiseledEnchantingTableData.treasureEpiphanyChance(this.regularBookshelves);
    }

    public int epiphanyRerollXpLevels() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyRerollXpLevels()
            : ChiseledEnchantingTableData.treasureEpiphanyRerollXpLevels();
    }

    public List<ItemStack> epiphanyRerollCosts() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyRerollCosts()
            : ChiseledEnchantingTableData.treasureEpiphanyRerollCostOptions();
    }

    /**
     * Deterministically resolves the active Grand Library Treasure Epiphany (at most ONE matching
     * positive treasure enchantment at Level 1) for {@code targetStack}, identically on server & client.
     */
    @org.jetbrains.annotations.Nullable
    public EnchantmentWithLevel computeActiveTreasureEpiphany(ItemStack targetStack) {
        int minShelves = epiphanyMinBookshelves();
        if (this.regularBookshelves < minShelves || targetStack.isEmpty() || targetStack.is(Items.ENCHANTED_BOOK)) {
            return null;
        }
        boolean isBlankBook = targetStack.is(Items.BOOK);
        var registry = this.world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        java.util.List<Identifier> eligible = new java.util.ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            if (!holder.is(TREASURE_TAG) || holder.is(CURSE_TAG)) continue;
            Identifier id = holder.key().identifier();
            if (this.unlocked_enchantements.contains(new EnchantmentWithLevel(id, 1))) continue;
            if (isBlankBook || holder.value().canEnchant(targetStack)) {
                eligible.add(id);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        eligible.sort(java.util.Comparator.comparing(Identifier::toString));

        int seed = this.world.isClientSide() ? this.epiphanySeed : currentServerEpiphanySeed();
        int day = this.world.isClientSide() ? this.syncedWorldDay : (int) ((this.world.getGameTime() / 24000L) & 0xFFFFL);
        Identifier itemId = BuiltInRegistries.ITEM.getKey(targetStack.getItem());
        long h = 0x9E3779B97F4A7C15L;
        h = splitMix64(h ^ Integer.toUnsignedLong(seed));
        h = splitMix64(h ^ Integer.toUnsignedLong(day));
        h = splitMix64(h ^ this.player.getUUID().getMostSignificantBits());
        h = splitMix64(h ^ this.player.getUUID().getLeastSignificantBits());
        h = splitMix64(h ^ itemId.toString().hashCode());

        double roll = ((h >>> 11) * (1.0 / (1L << 53)));
        if (roll >= epiphanyChance()) {
            return null;
        }
        long pickHash = splitMix64(h ^ 0xBF58476D1CE4E5B9L);
        int index = (int) (Long.remainderUnsigned(pickHash, eligible.size()));
        return new EnchantmentWithLevel(eligible.get(index), 1);
    }

    public boolean matchesActiveTreasureEpiphany(ApplyEnchantmentPayload payload) {
        if (payload.option_type() != OptionType.TREASURE_EPIPHANY || payload.enchantment_level() != 1) {
            return false;
        }
        EnchantmentWithLevel active = computeActiveTreasureEpiphany(get_enchantable_item());
        return active != null && active.enchantment_id().equals(payload.enchantment_id()) && active.enchantment_level() == payload.enchantment_level();
    }

    public boolean canAffordRerollEpiphany() {
        if (this.regularBookshelves < epiphanyMinBookshelves()) return false;
        if (this.player.getAbilities().instabuild) return true;
        if (this.player.experienceLevel < epiphanyRerollXpLevels()) return false;
        return findAffordableRerollStack() != null;
    }

    @org.jetbrains.annotations.Nullable
    private ItemStack findAffordableRerollStack() {
        ItemStack costSlot = get_cost_item();
        for (ItemStack opt : epiphanyRerollCosts()) {
            if (opt.isEmpty()) continue;
            int total = 0;
            if (ItemStack.isSameItemSameComponents(costSlot, opt)) {
                total += costSlot.getCount();
            }
            for (int slot = 0; slot < this.player.getInventory().getContainerSize(); slot++) {
                ItemStack inv = this.player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(inv, opt)) {
                    total += inv.getCount();
                }
            }
            if (total >= opt.getCount()) {
                return opt;
            }
        }
        return null;
    }

    public boolean rerollEpiphany() {
        if (!canAffordRerollEpiphany()) {
            return false;
        }
        if (!this.player.getAbilities().instabuild) {
            int xpLevels = epiphanyRerollXpLevels();
            if (xpLevels > 0) {
                this.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(this.player, xpLevels));
            }
            ItemStack req = findAffordableRerollStack();
            if (req != null) {
                int remaining = req.getCount();
                ItemStack costSlot = get_cost_item();
                if (ItemStack.isSameItemSameComponents(costSlot, req)) {
                    int take = Math.min(remaining, costSlot.getCount());
                    costSlot.shrink(take);
                    remaining -= take;
                }
                for (int slot = 0; slot < this.player.getInventory().getContainerSize() && remaining > 0; slot++) {
                    ItemStack inv = this.player.getInventory().getItem(slot);
                    if (ItemStack.isSameItemSameComponents(inv, req)) {
                        int take = Math.min(remaining, inv.getCount());
                        inv.shrink(take);
                        remaining -= take;
                    }
                }
                this.inventory.setChanged();
                this.player.getInventory().setChanged();
            }
        }
        advanceTableEpiphanySeed();
        this.world.playSound(
            null,
            this.player.blockPosition(),
            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.2F,
            1.15F
        );
        return true;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private chiseled_enchanting_table.compat.SyncedEnchantmentCosts.Cost syncedRow(EnchantmentWithLevel e, OptionType type) {
        if (!this.world.isClientSide()) {
            return null;
        }
        return chiseled_enchanting_table.compat.SyncedEnchantmentCosts
            .find(type, e.enchantment_id(), e.enchantment_level())
            .orElse(null);
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

    @org.jetbrains.annotations.Nullable
    public List<ItemStack> findMatchingAffordableCostBundle(EnchantmentWithLevel ewl, OptionType type) {
        if (this.player.getAbilities().instabuild) {
            List<ItemStack> first = getEnchantmentItemCosts(ewl, type);
            return first == null ? List.of() : first;
        }
        List<List<ItemStack>> options = getEnchantmentItemCostOptions(ewl, type);
        ItemStack itemCostStack = this.get_cost_item();
        for (List<ItemStack> bundle : options) {
            if (bundle.isEmpty()) continue;
            ItemStack primaryCost = bundle.getFirst();
            if (!primaryCost.isEmpty()
                && ItemStack.isSameItemSameComponents(itemCostStack, primaryCost)
                && itemCostStack.getCount() >= primaryCost.getCount()
                && hasAdditionalCosts(bundle)) {
                return bundle;
            }
        }
        return null;
    }

    public void deductMatchedBundle(List<ItemStack> matchedBundle) {
        if (this.player.getAbilities().instabuild || matchedBundle == null || matchedBundle.isEmpty()) {
            return;
        }
        this.get_cost_item().shrink(matchedBundle.getFirst().getCount());
        consumeAdditionalCosts(matchedBundle);
    }

    public boolean applyEnchantementCondition(ApplyEnchantmentPayload payload) {
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), payload.enchantment_level());
        OptionType effectiveType = payload.option_type();
        if (effectiveType == OptionType.ENCHANTMENT && ChiseledEnchantingTableData.isTransformation(payload.enchantment_id())) {
            effectiveType = OptionType.TRANSFORMATION;
        }
        int xp_level_cost = getEnchantmentXpLevelCost(ewl, effectiveType);
        if (this.player.experienceLevel < xp_level_cost && !player.getAbilities().instabuild) return false;

        List<ItemStack> matchedBundle = findMatchingAffordableCostBundle(ewl, effectiveType);
        if (!player.getAbilities().instabuild && matchedBundle == null) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            int xpPointsToDeduct = XpCostConversion.pointsForLevelCost(this.player, xp_level_cost);
            this.player.giveExperiencePoints(-xpPointsToDeduct);
            deductMatchedBundle(matchedBundle);
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
                            this.player.drop(overflow, false);
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
                        int containerSlot = this.getSlot(slotIndex).getContainerSlot();
                        java.util.Optional<Integer> preferredSlot = (containerSlot >= 0 && containerSlot < 36)
                            ? java.util.Optional.of(containerSlot)
                            : java.util.Optional.empty();
                        ItemStack returning = costSlotStack.copy();
                        this.getSlot(COST_SLOT).set(ItemStack.EMPTY);
                        this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT + 1, false);
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, returning, preferredSlot);
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
        return stillValid(this.access, player, BlockRegistry.CHISELED_ENCHANTING_TABLE);
    }
}
