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
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.TYPE,
            ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler chiseledEnchantingTableScreenHandler) {
                    chiseledEnchantingTableScreenHandler.freezeEpiphany();
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
    public final java.util.Map<Identifier, Integer> storedMasterLevels;
    private int epiphanySeed;
    private int syncedCycleIndex;
    private int syncedCountdownSeconds;
    private boolean syncedFrozen;
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
        this.xpDiscountPercent = Math.max(
            0,
            Math.min(
                chiseled_enchanting_table.mechanics.resonance.LibraryResonance.MAX_TOTAL_XP_DISCOUNT_PERCENT,
                payload.xp_discount_percent()
            )
        );
        this.regularBookshelves = Math.max(0, payload.regular_bookshelves());
        this.chiseledBookCount = Math.max(0, payload.chiseled_book_count());
        this.storedMasterLevels = payload.stored_master_levels() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(payload.stored_master_levels());
        this.epiphanySeed = payload.epiphany_seed();
        this.syncedCycleIndex = (int) ((playerInventory.player.level().getGameTime() / 12000L) & 0xFFFFL);
        this.syncedCountdownSeconds = 600;
        this.syncedFrozen = false;
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

        int enchantableSlotX = 183;
        int enchantableSlotY = 28;
        int costSlotX = 183;
        int costSlotY = 57;

        this.addSlot(new Slot(
            this.inventory,
            ENCHANTABLE_SLOT,
            enchantableSlotX,
            enchantableSlotY
        ) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                if (stack.is(BlockRegistry.DEPLETED_TOME)) {
                    return 8;
                }
                return 1;
            }
        });

        this.addSlot(new Slot(
            this.inventory,
            COST_SLOT,
            costSlotX,
            costSlotY
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

        // DataSlots 0..4 sync 32-bit epiphanySeed, active cycle index, live countdown (seconds), and Stasis Freeze state.
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
                return serverActiveCycleIndex() & 0xFFFF;
            }

            @Override
            public void set(int value) {
                syncedCycleIndex = value & 0xFFFF;
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return Math.min(32767, Math.max(0, serverCountdownSeconds()));
            }

            @Override
            public void set(int value) {
                syncedCountdownSeconds = Math.max(0, value);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return serverIsFrozen() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                syncedFrozen = (value != 0);
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

    private boolean serverIsFrozen() {
        boolean[] holder = new boolean[] { false };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                holder[0] = be.isEpiphanyFrozen(lvl.getGameTime());
            }
        });
        return holder[0];
    }

    private int serverActiveCycleIndex() {
        int cycleTicks = epiphanyCycleTicks();
        int[] holder = new int[] { (int) ((this.world.getGameTime() / Math.max(200L, cycleTicks)) & 0xFFFFL) };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                holder[0] = be.activeCycleIndex(lvl.getGameTime(), cycleTicks);
            }
        });
        return holder[0];
    }

    private int serverCountdownSeconds() {
        int cycleTicks = Math.max(200, epiphanyCycleTicks());
        int[] holder = new int[] { (int) (((cycleTicks - (this.world.getGameTime() % cycleTicks)) + 19L) / 20L) };
        this.access.execute((lvl, pos) -> {
            long gt = lvl.getGameTime();
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be
                && be.isEpiphanyFrozen(gt)) {
                holder[0] = (int) ((be.remainingFreezeTicks(gt) + 19L) / 20L);
            }
        });
        return holder[0];
    }

    public boolean isEpiphanyFrozen() {
        return this.world.isClientSide() ? this.syncedFrozen : serverIsFrozen();
    }

    public int countdownSeconds() {
        return this.world.isClientSide() ? this.syncedCountdownSeconds : serverCountdownSeconds();
    }

    public void advanceTableEpiphanySeed(boolean consumedEpiphanyOrForceClearFreeze) {
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                if (consumedEpiphanyOrForceClearFreeze) {
                    this.epiphanySeed = be.clearFreezeAndAdvanceSeed();
                } else if (!be.isEpiphanyFrozen(lvl.getGameTime())) {
                    this.epiphanySeed = be.advanceEpiphanySeed();
                }
            } else {
                this.epiphanySeed = this.epiphanySeed * 1664525 + 1013904223;
            }
        });
        this.broadcastChanges();
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        // Only rebuild scroll list when seed (0/1), cycle (2), or frozen state (4) changes — NOT every second on timer tick (3).
        if (id != 3 && this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (!this.world.isClientSide() && computeActiveTreasureEpiphanies(get_enchantable_item()).size() >= 2) {
            chiseled_enchanting_table.utils.Advancement.give(this.player, "multi_epiphany");
        }
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
            boolean matched = (!input.isEmpty() && inputItem != null && input.is(inputItem))
                || (display.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID) && ChiseledEnchantingTableData.isResidualBook(input));
            if (matched) {
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
        if (id.equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID) && ChiseledEnchantingTableData.isResidualBook(input)) {
            return true;
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
            return ChiseledEnchantingTableData.treasureEpiphanyCostOptions(
                e.enchantment_id(),
                e.enchantment_level(),
                getEnchantmentMaxLevel(e)
            );
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
            return ChiseledEnchantingTableData.treasureEpiphanyXpCost(
                e.enchantment_id(),
                e.enchantment_level(),
                getEnchantmentMaxLevel(e)
            );
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

    public int storedMasterLevel(Identifier enchantmentId) {
        return enchantmentId == null ? 0 : this.storedMasterLevels.getOrDefault(enchantmentId, 0);
    }

    public double totalMasteryUnits() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.totalMasteryUnits(
            this.world,
            this.storedMasterLevels
        );
    }

    public double affinityMasteryUnits(ItemStack targetStack) {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.affinityMasteryUnits(
            this.world,
            this.storedMasterLevels,
            targetStack
        );
    }

    public int woodenXpDiscountPercent() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.woodenBookshelfDiscountPercent(this.regularBookshelves);
    }

    public int globalMasteryDiscountPercent() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.globalMasteryDiscountPercent(totalMasteryUnits());
    }

    public int specificEnchantDiscountBonus(Identifier enchantmentId) {
        int stored = storedMasterLevel(enchantmentId);
        if (stored <= 0) return 0;
        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, enchantmentId);
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.specificEnchantmentDiscountBonus(stored, maxLvl);
    }

    public int effectiveXpDiscountPercent(Identifier enchantmentId, OptionType type) {
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(enchantmentId)) {
            return this.xpDiscountPercent;
        }
        if (type == OptionType.TREASURE_EPIPHANY) {
            return specificEnchantDiscountBonus(enchantmentId);
        }
        int stored = storedMasterLevel(enchantmentId);
        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, enchantmentId);
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.effectiveXpDiscountPercent(
            this.regularBookshelves,
            totalMasteryUnits(),
            stored,
            maxLvl
        );
    }

    public boolean isMasterTomeBacked(EnchantmentWithLevel e, OptionType type) {
        return type == OptionType.ENCHANTMENT
            && !ChiseledEnchantingTableData.isTransformation(e.enchantment_id())
            && storedMasterLevel(e.enchantment_id()) >= e.enchantment_level();
    }

    public XpCostConversion.XpRequirement resolveXpRequirement(EnchantmentWithLevel e, OptionType type) {
        int rawCost = getBaseEnchantmentXpLevelCost(e, type);
        int discount = effectiveXpDiscountPercent(e.enchantment_id(), type);
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return XpCostConversion.forTransformation(e.enchantment_id(), rawCost, discount);
        }
        if (isMasterTomeBacked(e, type)) {
            int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
                this.world,
                e.enchantment_id()
            );
            return XpCostConversion.forMasterTomeEnchant(e.enchantment_level(), maxLvl, rawCost, discount);
        }
        return XpCostConversion.forFullArcaneRitual(rawCost, discount);
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) {
        return resolveXpRequirement(e, type).requiredLevel();
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
        return epiphanyChance(get_enchantable_item());
    }

    public double epiphanyChance(ItemStack targetStack) {
        double base = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyBaseChance()
            : ChiseledEnchantingTableData.treasureEpiphanyBaseChance();
        double perShelf = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyPerShelfChance()
            : ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance();
        double perTome = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyPerTomeChance()
            : ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance();
        double max = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyMaxChance()
            : ChiseledEnchantingTableData.treasureEpiphanyMaxChance();
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.epiphanyProbability(
            this.regularBookshelves,
            epiphanyMinBookshelves(),
            base,
            perShelf,
            perTome,
            max,
            totalMasteryUnits(),
            affinityMasteryUnits(targetStack)
        );
    }

    public int epiphanyMaxSlots() {
        return epiphanyMaxSlots(get_enchantable_item());
    }

    public int epiphanyMaxSlots(ItemStack targetStack) {
        int cap = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyMaxSlotsCap()
            : ChiseledEnchantingTableData.treasureEpiphanyMaxSlotsCap();
        int step = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyScorePerSlot()
            : ChiseledEnchantingTableData.treasureEpiphanyScorePerSlot();
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.epiphanyMaxSlots(
            this.regularBookshelves,
            epiphanyMinBookshelves(),
            cap,
            step,
            totalMasteryUnits(),
            affinityMasteryUnits(targetStack)
        );
    }

    public int epiphanyCycleTicks() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyCycleTicks()
            : ChiseledEnchantingTableData.treasureEpiphanyCycleTicks();
    }

    public int epiphanyFreezeDurationTicks() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyFreezeDurationTicks()
            : ChiseledEnchantingTableData.treasureEpiphanyFreezeDurationTicks();
    }

    public int epiphanyMaxFreezeTicks() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyMaxFreezeTicks()
            : ChiseledEnchantingTableData.treasureEpiphanyMaxFreezeTicks();
    }

    public int epiphanyFreezeXpLevels() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyFreezeXpLevels()
            : ChiseledEnchantingTableData.treasureEpiphanyFreezeXpLevels();
    }

    public List<ItemStack> epiphanyFreezeCosts() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.epiphanyFreezeCosts()
            : ChiseledEnchantingTableData.treasureEpiphanyFreezeCostOptions();
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

    public boolean isTreasureEnchantment(Holder.Reference<Enchantment> holder) {
        if (holder.is(CURSE_TAG)) return false;
        Identifier id = holder.key().identifier();
        Set<Identifier> explicit = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.explicitTreasureIds()
            : ChiseledEnchantingTableData.treasureEpiphanyExplicitIds();
        return holder.is(TREASURE_TAG) || explicit.contains(id);
    }

    /**
     * Deterministically resolves all active Grand Library Treasure Epiphanies (1..epiphanyMaxSlots()
     * distinct matching Treasure Enchantments at Level 1) identically on server & client.
     */
    public List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(ItemStack targetStack) {
        int minShelves = epiphanyMinBookshelves();
        int maxSlots = epiphanyMaxSlots(targetStack);
        if (this.regularBookshelves < minShelves
            || maxSlots <= 0
            || targetStack.isEmpty()
            || targetStack.is(Items.ENCHANTED_BOOK)
            || ChiseledEnchantingTableData.isResidualBook(targetStack)) {
            return List.of();
        }
        boolean isBlankBook = targetStack.is(Items.BOOK);
        var registry = this.world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        java.util.List<Identifier> pool = new java.util.ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            if (!isTreasureEnchantment(holder)) continue;
            Identifier id = holder.key().identifier();
            int maxLvl = Math.max(1, holder.value().getMaxLevel());
            if (storedMasterLevel(id) >= maxLvl) continue;
            if (isBlankBook || holder.value().canEnchant(targetStack)) {
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        pool.sort(java.util.Comparator.comparing(Identifier::toString));

        int seed = this.world.isClientSide() ? this.epiphanySeed : currentServerEpiphanySeed();
        int cycle = this.world.isClientSide() ? this.syncedCycleIndex : serverActiveCycleIndex();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(targetStack.getItem());
        long baseHash = 0x9E3779B97F4A7C15L;
        baseHash = splitMix64(baseHash ^ Integer.toUnsignedLong(seed));
        baseHash = splitMix64(baseHash ^ Integer.toUnsignedLong(cycle));
        baseHash = splitMix64(baseHash ^ this.player.getUUID().getMostSignificantBits());
        baseHash = splitMix64(baseHash ^ this.player.getUUID().getLeastSignificantBits());
        baseHash = splitMix64(baseHash ^ itemId.toString().hashCode());

        double totalUnits = totalMasteryUnits();
        double affinityUnits = affinityMasteryUnits(targetStack);
        double resScore = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.epiphanyResonanceScore(
            this.regularBookshelves,
            minShelves,
            totalUnits,
            affinityUnits
        );
        double baseProb = epiphanyChance(targetStack);
        java.util.List<EnchantmentWithLevel> out = new java.util.ArrayList<>(maxSlots);
        double slotFactor = 1.0;
        for (int slotIdx = 0; slotIdx < maxSlots && !pool.isEmpty(); slotIdx++) {
            long h = splitMix64(baseHash ^ (0x517CC1B727220A95L * (slotIdx + 1L)));
            double roll = ((h >>> 11) * (1.0 / (1L << 53)));
            if (roll < baseProb * slotFactor) {
                long pickHash = splitMix64(h ^ 0xBF58476D1CE4E5B9L);
                int index = (int) Long.remainderUnsigned(pickHash, pool.size());
                Identifier chosen = pool.remove(index);
                int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, chosen);
                int storedLvl = storedMasterLevel(chosen);
                int minTier = Math.min(maxLvl, storedLvl + 1);
                double ownR = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.relativeMastery(storedLvl, maxLvl);
                int tier = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.rollEpiphanyTier(
                    splitMix64(pickHash ^ 0x94D049BB133111EBL),
                    minTier,
                    maxLvl,
                    resScore,
                    ownR
                );
                out.add(new EnchantmentWithLevel(chosen, tier));
            }
            slotFactor *= 0.78;
        }
        return List.copyOf(out);
    }

    public boolean matchesActiveTreasureEpiphany(ApplyEnchantmentPayload payload) {
        if (payload.option_type() != OptionType.TREASURE_EPIPHANY || payload.enchantment_level() < 1) {
            return false;
        }
        for (EnchantmentWithLevel active : computeActiveTreasureEpiphanies(get_enchantable_item())) {
            if (active.enchantment_id().equals(payload.enchantment_id()) && active.enchantment_level() == payload.enchantment_level()) {
                return true;
            }
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private ItemStack findAffordableSingleOption(List<ItemStack> options) {
        ItemStack costSlot = get_cost_item();
        for (ItemStack opt : options) {
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

    private void consumeSingleStackOption(ItemStack req) {
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

    public boolean canAffordRerollEpiphany() {
        if (this.regularBookshelves < epiphanyMinBookshelves()) return false;
        if (this.player.getAbilities().instabuild) return true;
        if (this.player.experienceLevel < epiphanyRerollXpLevels()) return false;
        return findAffordableSingleOption(epiphanyRerollCosts()) != null;
    }

    public boolean canAffordFreezeEpiphany() {
        if (this.regularBookshelves < epiphanyMinBookshelves()) return false;
        int maxSec = Math.max(10, epiphanyMaxFreezeTicks() / 20);
        if (isEpiphanyFrozen() && countdownSeconds() >= maxSec - 2) return false;
        if (this.player.getAbilities().instabuild) return true;
        if (this.player.experienceLevel < epiphanyFreezeXpLevels()) return false;
        return findAffordableSingleOption(epiphanyFreezeCosts()) != null;
    }

    public boolean freezeEpiphany() {
        if (!canAffordFreezeEpiphany()) {
            return false;
        }
        if (!this.player.getAbilities().instabuild) {
            int xpLevels = epiphanyFreezeXpLevels();
            if (xpLevels > 0) {
                this.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(this.player, xpLevels));
            }
            ItemStack req = findAffordableSingleOption(epiphanyFreezeCosts());
            if (req != null) {
                consumeSingleStackOption(req);
            }
        }
        int cycleTicks = epiphanyCycleTicks();
        int addTicks = epiphanyFreezeDurationTicks();
        int maxTicks = epiphanyMaxFreezeTicks();
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                be.addFreezeTicks(lvl.getGameTime(), cycleTicks, addTicks, maxTicks);
            }
        });
        this.broadcastChanges();
        chiseled_enchanting_table.utils.Advancement.give(this.player, "freeze_epiphany");
        this.world.playSound(
            null,
            this.player.blockPosition(),
            net.minecraft.sounds.SoundEvents.GLASS_PLACE,
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.0F,
            1.35F
        );
        this.world.playSound(
            null,
            this.player.blockPosition(),
            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.2F,
            0.85F
        );
        return true;
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
            ItemStack req = findAffordableSingleOption(epiphanyRerollCosts());
            if (req != null) {
                consumeSingleStackOption(req);
            }
        }
        advanceTableEpiphanySeed(true);
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
        var xpReq = resolveXpRequirement(ewl, effectiveType);
        if (!xpReq.isMetBy(this.player)) return false;

        List<ItemStack> matchedBundle = findMatchingAffordableCostBundle(ewl, effectiveType);
        if (!player.getAbilities().instabuild && matchedBundle == null) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            XpCostConversion.deductXp(this.player, xpReq);
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
            ItemStack existingTarget = this.getSlot(ENCHANTABLE_SLOT).getItem();
            if (clickedStack.is(BlockRegistry.DEPLETED_TOME)
                && existingTarget.is(BlockRegistry.DEPLETED_TOME)
                && existingTarget.getCount() < 8) {
                int space = 8 - existingTarget.getCount();
                int move = Math.min(space, clickedStack.getCount());
                existingTarget.grow(move);
                clickedStack.shrink(move);
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
            boolean isDedicatedTransformInput = clickedStack.is(BlockRegistry.DEPLETED_TOME)
                || ChiseledEnchantingTableData.isResidualBook(clickedStack)
                || (!this.getSlot(ENCHANTABLE_SLOT).hasItem() && !this.syncedTransformationsFor(clickedStack).isEmpty());

            if (clickedStackNoEnchant.isEnchantable() || isDedicatedTransformInput || (isBook && !this.getSlot(ENCHANTABLE_SLOT).hasItem())) {
                ItemStack tempStack = clickedStack.copy();
                int maxAllowed = this.getSlot(ENCHANTABLE_SLOT).getMaxStackSize(tempStack);
                this.getSlot(slotIndex).set(this.getSlot(ENCHANTABLE_SLOT).getItem());
                this.getSlot(ENCHANTABLE_SLOT).set(tempStack);

                if (this.getSlot(ENCHANTABLE_SLOT).getItem().getCount() > maxAllowed) {
                    ItemStack overflow = this.getSlot(ENCHANTABLE_SLOT).getItem().copy();
                    overflow.setCount(overflow.getCount() - maxAllowed);
                    this.getSlot(ENCHANTABLE_SLOT).getItem().setCount(maxAllowed);
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
