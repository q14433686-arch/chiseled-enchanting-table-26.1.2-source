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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

    /** Delegates to {@link EnchantingNetworking#init()} (god-class split, 2026-10-24 audit). */
    public static void init() {
        EnchantingNetworking.init();
    }

    public Runnable onContentChangedCallback = () -> {};
    public Player player;
    public final Set<EnchantmentWithLevel> unlocked_enchantements;
    public int xpDiscountPercent;
    public int regularBookshelves;
    public int chiseledBookshelves;
    public int emptyChiseledBookshelves;
    public int chiseledBookCount;
    public int highTierChiseledBooks;
    public java.util.Map<Identifier, Integer> storedMasterLevels;
    public java.util.Map<EnchantmentWithLevel, Integer> masterTomeCharges;
    public boolean crampedLibrary;
    public boolean syncedCleanseUnlocked;
    public boolean syncedTomePageUnlocked;
    public int sacrificeProgress;
    public int qualifyingEnchantedBookshelves;
    public int liveTotalLibraryBookshelves;
    public int liveRegenTier;
    public int nextRegenTierGap;
    public int regenIntervalTicks = -1;
    public java.util.List<ChiseledEnchantingMenuPayloads.TomeBookEntry> tomeBooks = List.of();
    private int epiphanySeed;
    private int syncedCycleIndex;
    private int syncedCountdownSeconds;
    private boolean syncedFrozen;
    private int syncedRitualRemainingTicks = 0;
    private int syncedRitualTotalTicks = 0;
    private List<EnchantmentWithLevel> syncedQueuedSteps = List.of();
    private boolean syncedUsedInfinityTome = false;
    private boolean syncedHasAnyMasterTome = false;
    private boolean syncedRitualBlocked = false;
    /** Server-synced transformation table, keyed by transformation id. Used by client UI. */
    public java.util.Map<Identifier, TransformationDisplay> transformationDisplays;
    private final Level world;
    private final ContainerLevelAccess access;
    private final boolean persistentBlockInventory;
    private final Container inventory;

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
        this.player = playerInventory.player;
        this.world = playerInventory.player.level();
        chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity[] backingHolder =
            new chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity[] { null };
        if (!this.world.isClientSide()) {
            this.access.execute((lvl, pos) -> {
                if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                    backingHolder[0] = be;
                }
            });
        }
        if (backingHolder[0] != null) {
            final var backingBe = backingHolder[0];
            this.persistentBlockInventory = true;
            this.inventory = new Container() {
                @Override public int getContainerSize() { return backingBe.getContainerSize(); }
                @Override public boolean isEmpty() { return backingBe.isEmpty(); }
                @Override public ItemStack getItem(int slot) { return backingBe.getItem(slot); }
                @Override public ItemStack removeItem(int slot, int amount) {
                    ItemStack r = backingBe.removeItem(slot, amount);
                    ChiseledEnchantingTableScreenHandler.this.slotsChanged(this);
                    return r;
                }
                @Override public ItemStack removeItemNoUpdate(int slot) { return backingBe.removeItemNoUpdate(slot); }
                @Override public void setItem(int slot, ItemStack stack) {
                    backingBe.setItem(slot, stack);
                    ChiseledEnchantingTableScreenHandler.this.slotsChanged(this);
                }
                @Override public void setChanged() {
                    backingBe.setChanged();
                    ChiseledEnchantingTableScreenHandler.this.slotsChanged(this);
                }
                @Override public boolean stillValid(Player p) { return backingBe.stillValid(p); }
                @Override public void clearContent() { backingBe.clearContent(); }
            };
        } else {
            this.persistentBlockInventory = false;
            this.inventory = new SimpleContainer(2) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    ChiseledEnchantingTableScreenHandler.this.slotsChanged(this);
                }
            };
        }
        this.unlocked_enchantements = new HashSet<>(payload.unlocked_enchantements());
        this.xpDiscountPercent = Math.max(
            0,
            Math.min(
                this.libraryResonanceConfig().maxTotalXpDiscountPercent(),
                payload.xp_discount_percent()
            )
        );
        this.regularBookshelves = Math.max(0, payload.regular_bookshelves());
        this.chiseledBookshelves = Math.max(0, payload.chiseled_bookshelves());
        this.emptyChiseledBookshelves = Math.max(0, payload.empty_chiseled_bookshelves());
        this.chiseledBookCount = Math.max(0, payload.chiseled_book_count());
        this.highTierChiseledBooks = Math.max(0, payload.high_tier_chiseled_books());
        this.storedMasterLevels = payload.stored_master_levels() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(payload.stored_master_levels());
        this.masterTomeCharges = payload.master_tome_charges() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(payload.master_tome_charges());
        this.crampedLibrary = payload.cramped_library();
        this.epiphanySeed = payload.epiphany_seed();
        long cycleTicks = Math.max(1L, this.epiphanyCycleTicks());
        this.syncedCycleIndex = (int) ((this.world.getGameTime() / cycleTicks) & 0xFFFFL);
        this.syncedCountdownSeconds = (int) Math.max(1L, cycleTicks / 20L);
        this.syncedFrozen = false;
        java.util.Map<Identifier, TransformationDisplay> displays = new java.util.HashMap<>();
        for (TransformationDisplay display : payload.transformations()) {
            displays.put(display.id(), display);
        }
        this.transformationDisplays = java.util.Map.copyOf(displays);

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
                // Audit H-04: batch caps are additionally bounded by the item's own stack
                // limit so Slot / BE admission / hoppers agree (glass bottles stop at 16).
                if (stack.is(BlockRegistry.DEPLETED_TOME)) {
                    return Math.max(1, Math.min(depletedTomeMaxBatchSize(), stack.getMaxStackSize()));
                }
                if (!stack.is(Items.BOOK)
                    && !stack.is(Items.ENCHANTED_BOOK)
                    && !stack.isEnchantable()
                    && !syncedTransformationsFor(stack).isEmpty()) {
                    return Math.max(1, Math.min(transformationMaxBatchSize(), stack.getMaxStackSize()));
                }
                return 1;
            }

            @Override
            public boolean mayPickup(Player playerEntity) {
                return !isRitualActive();
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return !isRitualActive();
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
        // DataSlots 5..8 sync full 31-bit active ritual remaining ticks & total ticks every tick.
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (int) (serverRitualRemainingTicks() & 0xFFFFL);
            }

            @Override
            public void set(int value) {
                syncedRitualRemainingTicks = (syncedRitualRemainingTicks & 0xFFFF0000) | (value & 0xFFFF);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (int) ((Math.min(Integer.MAX_VALUE, serverRitualRemainingTicks()) >>> 16) & 0x7FFFL);
            }

            @Override
            public void set(int value) {
                syncedRitualRemainingTicks = (syncedRitualRemainingTicks & 0x0000FFFF) | ((value & 0x7FFF) << 16);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (int) (serverRitualTotalTicks() & 0xFFFFL);
            }

            @Override
            public void set(int value) {
                syncedRitualTotalTicks = (syncedRitualTotalTicks & 0xFFFF0000) | (value & 0xFFFF);
            }
        });
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return (int) ((Math.min(Integer.MAX_VALUE, serverRitualTotalTicks()) >>> 16) & 0x7FFFL);
            }

            @Override
            public void set(int value) {
                syncedRitualTotalTicks = (syncedRitualTotalTicks & 0x0000FFFF) | ((value & 0x7FFF) << 16);
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

    public void applyLiveShelfState(ChiseledEnchantingMenuPayloads.LiveShelfStatePayload live) {
        if (live == null || !live.targets(this.containerId)) {
            return;
        }
        this.unlocked_enchantements.clear();
        this.unlocked_enchantements.addAll(live.unlocked_enchantements());
        this.xpDiscountPercent = Math.max(
            0,
            Math.min(
                libraryResonanceConfig().maxTotalXpDiscountPercent(),
                live.xp_discount_percent()
            )
        );
        this.regularBookshelves = Math.max(0, live.regular_bookshelves());
        this.chiseledBookshelves = Math.max(0, live.chiseled_bookshelves());
        this.emptyChiseledBookshelves = Math.max(0, live.empty_chiseled_bookshelves());
        this.chiseledBookCount = Math.max(0, live.chiseled_book_count());
        this.highTierChiseledBooks = Math.max(0, live.high_tier_chiseled_books());
        this.storedMasterLevels = live.stored_master_levels() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(live.stored_master_levels());
        this.masterTomeCharges = live.master_tome_charges() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(live.master_tome_charges());
        this.crampedLibrary = live.cramped_library();
        this.syncedCleanseUnlocked = live.cleanse_unlocked();
        this.syncedTomePageUnlocked = live.tome_page_unlocked();
        this.sacrificeProgress = Math.max(0, live.sacrifice_progress());
        this.qualifyingEnchantedBookshelves = Math.max(0, live.qualifying_enchanted_bookshelves());
        this.liveTotalLibraryBookshelves = Math.max(0, live.total_library_bookshelves());
        this.liveRegenTier = Math.max(0, live.regen_tier());
        this.nextRegenTierGap = Math.max(0, live.next_regen_tier_gap());
        this.regenIntervalTicks = live.regen_interval_ticks();
        this.tomeBooks = live.tome_books() == null ? List.of() : List.copyOf(live.tome_books());
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    public void applyTransformationSnapshot(java.util.List<TransformationDisplay> displays) {
        var next = new java.util.HashMap<Identifier, TransformationDisplay>();
        for (var display : displays) next.put(display.id(), display);
        this.transformationDisplays = java.util.Map.copyOf(next);
        if (this.onContentChangedCallback != null) this.onContentChangedCallback.run();
    }

    public int totalLibraryBookshelves() {
        return chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.computeBroadBookshelfCount(
            this.regularBookshelves,
            this.emptyChiseledBookshelves,
            this.chiseledBookshelves
        );
    }

    public int masterTomeRemainingCharges(Identifier id, int level) {
        if (id == null) {
            return this.libraryResonanceConfig().maxTomeUses();
        }
        Integer live = this.masterTomeCharges.get(new EnchantmentWithLevel(id, Math.max(1, level)));
        return live != null ? live : this.libraryResonanceConfig().maxTomeUses();
    }

    public boolean isCleansePageUnlocked() {
        if (!this.world.isClientSide() && this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return chiseled_enchanting_table.utils.Advancement.hasCompleted(
                sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE);
        }
        return this.syncedCleanseUnlocked;
    }

    public boolean isTomePageUnlocked() {
        if (!this.world.isClientSide() && this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return chiseled_enchanting_table.utils.Advancement.hasCompleted(
                sp, chiseled_enchanting_table.utils.Advancement.HOW_MUCH_LONGER);
        }
        return this.syncedTomePageUnlocked;
    }

    private void tomeActionMessage(String key, Object... args) {
        if (this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, args), true);
        }
    }

    private boolean shelfScanBlockedByBreathingRoom(net.minecraft.core.BlockPos tablePos) {
        var cfg = shelfGatesConfig();
        if (cfg == null || !cfg.requireBreathingRoom()) return false;
        boolean ceilingBlocked = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock
            .isTableCeilingBlocked(this.world, tablePos);
        int openAdj = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock
            .countOpenAdjacentBlocks(this.world, tablePos);
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
            .effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, cfg) <= 0;
    }

    private boolean isShelfInScope(net.minecraft.core.BlockPos tablePos, net.minecraft.core.BlockPos shelfPos) {
        if (tablePos == null || shelfPos == null || shelfScanBlockedByBreathingRoom(tablePos)) return false;
        var cfg = shelfGatesConfig();
        net.minecraft.core.BlockPos delta = shelfPos.subtract(tablePos);
        if (!chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.getScanOffsets(cfg).contains(delta)) {
            return false;
        }
        if (!this.world.hasChunkAt(shelfPos)) return false;
        return cfg == null || !cfg.requireLineOfSight()
            || chiseled_enchanting_table.block.ChiseledEnchantingTableBlock
                .hasClearLineOfSight(this.world, tablePos, shelfPos);
    }

    private java.util.List<net.minecraft.core.BlockPos> scopedShelfPositions(net.minecraft.core.BlockPos tablePos) {
        java.util.List<net.minecraft.core.BlockPos> out = new java.util.ArrayList<>();
        if (tablePos == null || shelfScanBlockedByBreathingRoom(tablePos)) return out;
        var cfg = shelfGatesConfig();
        for (net.minecraft.core.BlockPos offset
            : chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
            net.minecraft.core.BlockPos pos = tablePos.offset(offset);
            if (isShelfInScope(tablePos, pos)
                && this.world.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity) {
                out.add(pos.immutable());
            }
        }
        return out;
    }

    private void writeShelfStack(
        net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity shelf,
        net.minecraft.core.BlockPos shelfPos,
        int slot,
        ItemStack stack
    ) {
        shelf.setItem(slot, stack);
        shelf.setChanged();
        var state = this.world.getBlockState(shelfPos);
        this.world.sendBlockUpdated(
            shelfPos, state, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    private boolean consumeOneCleansingGrit() {
        for (int slot = 0; slot < this.player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = this.player.getInventory().getItem(slot);
            if (candidate.is(BlockRegistry.CLEANSING_GRIT)) {
                candidate.shrink(1);
                this.player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    /** Server-authoritative handler for every Tome-page button. */
    public void handleTomePageAction(ChiseledEnchantingMenuPayloads.TomePageActionPayload payload) {
        if (this.world.isClientSide() || !(this.player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        refreshLiveShelfState();
        net.minecraft.core.BlockPos tablePos = tableBlockPos();
        if (tablePos == null) return;

        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.SACRIFICE_DEPLETED) {
            sacrificeDepletedTomes(sp, tablePos);
            refreshLiveShelfState();
            return;
        }
        if (!isTomePageUnlocked()) {
            tomeActionMessage("gui.chiseled_enchanting_table.tab.locked_tooltip");
            return;
        }
        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL) {
            int locked = lockAllChargingTomes(sp, tablePos);
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.batch", locked);
            refreshLiveShelfState();
            return;
        }
        if (!isShelfInScope(tablePos, payload.shelf_pos())
            || !(this.world.getBlockEntity(payload.shelf_pos())
                instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity shelf)) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        int slot = payload.slot();
        ItemStack current = shelf.getItem(slot);
        if (!current.is(Items.ENCHANTED_BOOK)) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        ItemStack updated = current.copy();
        var expired = chiseled_enchanting_table.mechanics.resonance.TomeLockData.clearExpired(updated);
        if (expired != null
            && payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK) {
            writeShelfStack(shelf, payload.shelf_pos(), slot, updated);
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.expired");
            refreshLiveShelfState();
            return;
        }

        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK) {
            unlockTome(sp, shelf, payload.shelf_pos(), slot, updated);
        } else {
            var mode = switch (payload.action()) {
                case LOCK_NEXT -> chiseled_enchanting_table.mechanics.resonance.TomeLockData.Mode.NEXT_CHARGE;
                case LOCK_FULL -> chiseled_enchanting_table.mechanics.resonance.TomeLockData.Mode.FULL_CHARGE;
                case LOCK_PERMANENT -> chiseled_enchanting_table.mechanics.resonance.TomeLockData.Mode.PERMANENT;
                default -> null;
            };
            if (mode == null) return;
            if (chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(updated)) {
                tomeActionMessage("message.chiseled_enchanting_table.tome_lock.already_locked");
                return;
            }
            if (!chiseled_enchanting_table.mechanics.resonance.TomeLockData.lock(
                updated, sp.getUUID(), sp.getName().getString(), mode)) {
                tomeActionMessage("message.chiseled_enchanting_table.tome_lock.full");
                return;
            }
            writeShelfStack(shelf, payload.shelf_pos(), slot, updated);
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.locked");
        }
        refreshLiveShelfState();
    }

    private void unlockTome(
        net.minecraft.server.level.ServerPlayer sp,
        net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity shelf,
        net.minecraft.core.BlockPos shelfPos,
        int slot,
        ItemStack updated
    ) {
        var lock = chiseled_enchanting_table.mechanics.resonance.TomeLockData.getLock(updated);
        if (lock == null || !chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(updated)) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_locked");
            return;
        }
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && !lock.ownedBy(sp.getUUID())) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_owner", lock.ownerName());
            return;
        }
        if (!creative) {
            ItemStack grit = new ItemStack(BlockRegistry.CLEANSING_GRIT);
            if (sp.experienceLevel < 1 || countAvailableInInventory(grit) < 1) {
                tomeActionMessage("message.chiseled_enchanting_table.tome_lock.cost");
                return;
            }
            if (!consumeOneCleansingGrit()) {
                tomeActionMessage("message.chiseled_enchanting_table.tome_lock.cost");
                return;
            }
            sp.giveExperienceLevels(-1);
        }
        chiseled_enchanting_table.mechanics.resonance.TomeLockData.unlock(updated);
        writeShelfStack(shelf, shelfPos, slot, updated);
        tomeActionMessage("message.chiseled_enchanting_table.tome_lock.unlocked");
    }

    private int lockAllChargingTomes(
        net.minecraft.server.level.ServerPlayer sp,
        net.minecraft.core.BlockPos tablePos
    ) {
        var cfg = libraryResonanceConfig();
        if (!cfg.tomeRegenEnabled()
            || cfg.tomeConsumptionMode() != chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR) {
            return 0;
        }
        int locked = 0;
        for (net.minecraft.core.BlockPos shelfPos : scopedShelfPositions(tablePos)) {
            if (!(this.world.getBlockEntity(shelfPos)
                instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity shelf)) continue;
            for (int slot = 0; slot < net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                ItemStack current = shelf.getItem(slot);
                if (!current.is(Items.ENCHANTED_BOOK)
                    || chiseled_enchanting_table.mechanics.resonance.TomeWearData.hasWearImmunity(current)
                    || chiseled_enchanting_table.mechanics.resonance.TomeWearData.getWearUsed(current) <= 0
                    || chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(current)) {
                    continue;
                }
                ItemStack updated = current.copy();
                chiseled_enchanting_table.mechanics.resonance.TomeLockData.clearExpired(updated);
                if (chiseled_enchanting_table.mechanics.resonance.TomeLockData.lock(
                    updated,
                    sp.getUUID(),
                    sp.getName().getString(),
                    chiseled_enchanting_table.mechanics.resonance.TomeLockData.Mode.FULL_CHARGE
                )) {
                    writeShelfStack(shelf, shelfPos, slot, updated);
                    locked++;
                }
            }
        }
        return locked;
    }

    private void sacrificeDepletedTomes(
        net.minecraft.server.level.ServerPlayer sp,
        net.minecraft.core.BlockPos tablePos
    ) {
        if (isCleansePageUnlocked()) {
            tomeActionMessage("message.chiseled_enchanting_table.sacrifice.done");
            return;
        }
        int needed = chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE_REQUIRED
            - chiseled_enchanting_table.utils.Advancement.completedCriteria(
                sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE);
        int consumed = 0;
        for (net.minecraft.core.BlockPos shelfPos : scopedShelfPositions(tablePos)) {
            if (needed <= 0) break;
            if (!(this.world.getBlockEntity(shelfPos)
                instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity shelf)) continue;
            for (int slot = 0; slot < net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE && needed > 0; slot++) {
                ItemStack current = shelf.getItem(slot);
                if (!current.is(BlockRegistry.DEPLETED_TOME)) continue;
                int here = 0;
                while (here < current.getCount() && needed > 0
                    && chiseled_enchanting_table.utils.Advancement.awardNextCriterion(
                        sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE)) {
                    here++;
                    needed--;
                    consumed++;
                }
                if (here > 0) {
                    ItemStack updated = current.copy();
                    updated.shrink(here);
                    writeShelfStack(shelf, shelfPos, slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
                }
            }
        }
        if (isCleansePageUnlocked()) {
            tomeActionMessage("message.chiseled_enchanting_table.sacrifice.done");
        } else if (consumed > 0) {
            tomeActionMessage(
                "message.chiseled_enchanting_table.sacrifice.progress",
                chiseled_enchanting_table.utils.Advancement.completedCriteria(
                    sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE),
                chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE_REQUIRED
            );
        } else {
            tomeActionMessage("message.chiseled_enchanting_table.sacrifice.none");
        }
    }

    public void refreshLiveShelfState() {
        if (this.world.isClientSide()) {
            return;
        }
        ChiseledEnchantingMenuPayloads.LiveShelfStatePayload[] holder =
            new ChiseledEnchantingMenuPayloads.LiveShelfStatePayload[] { null };
        this.access.execute((lvl, pos) ->
            holder[0] = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.computeLiveShelfState(lvl, pos, this.containerId, this.player)
        );
        var live = holder[0];
        if (live == null) {
            return;
        }
        boolean changed = !live.unlocked_enchantements().equals(this.unlocked_enchantements)
            || live.xp_discount_percent() != this.xpDiscountPercent
            || live.regular_bookshelves() != this.regularBookshelves
            || live.chiseled_bookshelves() != this.chiseledBookshelves
            || live.empty_chiseled_bookshelves() != this.emptyChiseledBookshelves
            || live.chiseled_book_count() != this.chiseledBookCount
            || live.high_tier_chiseled_books() != this.highTierChiseledBooks
            || live.cramped_library() != this.crampedLibrary
            || !live.stored_master_levels().equals(this.storedMasterLevels)
            || !java.util.Objects.equals(live.master_tome_charges(), this.masterTomeCharges)
            || live.cleanse_unlocked() != this.syncedCleanseUnlocked
            || live.tome_page_unlocked() != this.syncedTomePageUnlocked
            || live.sacrifice_progress() != this.sacrificeProgress
            || live.qualifying_enchanted_bookshelves() != this.qualifyingEnchantedBookshelves
            || live.total_library_bookshelves() != this.liveTotalLibraryBookshelves
            || live.regen_tier() != this.liveRegenTier
            || live.next_regen_tier_gap() != this.nextRegenTierGap
            || live.regen_interval_ticks() != this.regenIntervalTicks
            || !live.tome_books().equals(this.tomeBooks);
        if (changed) {
            applyLiveShelfState(live);
            if (this.player instanceof net.minecraft.server.level.ServerPlayer sp
                && ServerPlayNetworking.canSend(sp, ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE)) {
                ServerPlayNetworking.send(sp, live);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (!this.world.isClientSide() && (this.world.getGameTime() % 20L == 0L)) {
            refreshLiveShelfState();
            pushRitualStateToViewer();
        }
        super.broadcastChanges();
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        // Only rebuild scroll list when seed (0/1), cycle (2), or frozen state (4) changes — NOT every tick on timers (3/5/6/7/8).
        if (id != 3 && id < 5 && this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    public void refreshAfterRuntimeConfigChange() {
        if (this.world.isClientSide()) {
            return;
        }
        applyTransformationSnapshot(AvailableEnchantmentPayload.buildTransformationDisplays(this.player));
        refreshLiveShelfState();
        pushRitualStateToViewer();
        slotsChanged(this.inventory);
        broadcastChanges();
    }

    public long serverRitualRemainingTicks() {
        long[] out = new long[] { 0L };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.ritualRemainingTicks();
            }
        });
        return out[0];
    }

    public long serverRitualTotalTicks() {
        long[] out = new long[] { 0L };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.ritualTotalTicks();
            }
        });
        return out[0];
    }

    public long ritualRemainingTicks() {
        return this.world.isClientSide() ? Math.max(0L, this.syncedRitualRemainingTicks) : serverRitualRemainingTicks();
    }

    public long ritualTotalTicks() {
        return this.world.isClientSide() ? Math.max(0L, this.syncedRitualTotalTicks) : serverRitualTotalTicks();
    }

    public boolean isRitualBlocked() {
        if (this.world.isClientSide()) {
            return this.syncedRitualBlocked;
        }
        boolean[] out = new boolean[] { false };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.isRitualBlocked();
            }
        });
        return out[0];
    }

    public boolean isRitualActive() {
        if (this.world.isClientSide()) {
            return ritualRemainingTicks() > 0L || !this.syncedQueuedSteps.isEmpty() || this.syncedRitualBlocked;
        }
        boolean[] out = new boolean[] { ritualRemainingTicks() > 0L };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.hasActiveRitual();
            }
        });
        return out[0];
    }

    public List<EnchantmentWithLevel> queuedRitualSteps() {
        if (this.world.isClientSide()) {
            return this.syncedQueuedSteps;
        }
        List<EnchantmentWithLevel>[] out = new List[] { List.of() };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.queuedEnchantmentSummary();
            }
        });
        return out[0];
    }

    public boolean ritualUsedInfinityTome() {
        if (this.world.isClientSide()) {
            return this.syncedUsedInfinityTome;
        }
        boolean[] out = new boolean[] { false };
        this.access.execute((lvl, pos) -> {
            if (lvl.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be) {
                out[0] = be.anyQueuedStepUsedInfinityTome();
            }
        });
        return out[0];
    }

    public boolean hasAnyMasterTomeInRange() {
        if (this.world.isClientSide()) {
            return this.syncedHasAnyMasterTome || !this.storedMasterLevels.isEmpty();
        }
        var scan = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.scanLibraryRitualContext(
            this.world,
            tableBlockPos(),
            shelfGatesConfig()
        );
        return scan.hasAnyMasterTome();
    }

    public chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritualConfig() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.ritualConfig()
            : ChiseledEnchantingTableData.ritualConfig();
    }

    public int currentSacrificeSkipLevels() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSacrificeXpLevels(
            ritualConfig(),
            ritualRemainingTicks()
        );
    }

    public double currentRushSkipDegradeChance() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeRushDegradeChance(
            ritualConfig(),
            ritualRemainingTicks(),
            ritualUsedInfinityTome()
        );
    }

    public boolean canSacrificeSkipNow() {
        if (!isRitualActive() || isRitualBlocked()) {
            return false;
        }
        if (this.player.getAbilities().instabuild) {
            return true;
        }
        return this.player.experienceLevel >= currentSacrificeSkipLevels();
    }

    public boolean canRushSkipNow() {
        return isRitualActive() && !isRitualBlocked() && hasAnyMasterTomeInRange();
    }

    private long syncedRitualSession;
    public long ritualSession() {
        if (this.world.isClientSide()) return this.syncedRitualSession;
        var pos = tableBlockPos();
        return pos != null && this.world.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity be
            ? be.ritualSession() : 0L;
    }

    public void pushRitualStateToViewer() {
        if (this.world.isClientSide() || !(this.player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return;
        }
        if (!ServerPlayNetworking.canSend(sp, ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.TYPE)) {
            return;
        }
        var payload = new ChiseledEnchantingMenuPayloads.SyncRitualStatePayload(
            this.containerId,
            ritualSession(),
            queuedRitualSteps(),
            ritualUsedInfinityTome(),
            hasAnyMasterTomeInRange(),
            isRitualBlocked()
        );
        ServerPlayNetworking.send(sp, payload);
    }

    public void applySyncedRitualState(ChiseledEnchantingMenuPayloads.SyncRitualStatePayload payload) {
        if (payload.containerId() != this.containerId) return;
        this.syncedRitualSession = payload.session();
        this.syncedQueuedSteps = payload.queuedSteps() != null ? List.copyOf(payload.queuedSteps()) : List.of();
        this.syncedUsedInfinityTome = payload.usedInfinityTome();
        this.syncedHasAnyMasterTome = payload.hasAnyMasterTome();
        this.syncedRitualBlocked = payload.ritualBlocked();
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    /**
     * Returns a virtual copy of {@link #get_enchantable_item()} reflecting all currently queued
     * inscriptions (including virtual provenance slices) so players can queue sequential
     * enchantments cleanly without dupes or lost refund previews.
     */
    public ItemStack getEffectiveEnchantableItem() {
        ItemStack base = get_enchantable_item();
        List<EnchantmentWithLevel> queued = queuedRitualSteps();
        if (base.isEmpty() || queued.isEmpty()) {
            return base;
        }
        boolean wasBook = base.is(Items.BOOK) || base.is(Items.ENCHANTED_BOOK);
        ItemStack copy;
        if (base.is(Items.BOOK)) {
            // Preview and completion share one conversion (audit M-05) so the planned
            // item can never diverge from what the ritual actually produces.
            copy = chiseled_enchanting_table.mechanics.enchanting.BookConversion.toEnchantedBook(base.copyWithCount(1));
        } else {
            copy = base.copy();
        }
        var existing = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
            EnchantmentHelper.getEnchantmentsForCrafting(copy)
        );
        for (EnchantmentWithLevel q : queued) {
            var opt = findEnchantment(q.enchantment_id(), this.world);
            if (opt.isEmpty()) {
                continue;
            }
            Holder<Enchantment> h = opt.get();
            int prevLvl = existing.getLevel(h);
            if (!wasBook) {
                var keys = new HashSet<>(existing.keySet());
                for (var k : keys) {
                    if (!Enchantment.areCompatible(k, h)) {
                        existing.set(k, 0);
                    }
                }
            }
            existing.set(h, q.enchantment_level());
            chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.recordTablePayment(
                copy,
                q.enchantment_id(),
                prevLvl,
                q.enchantment_level()
            );
        }
        EnchantmentHelper.updateEnchantments(copy, m -> {
            var mKeys = new HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var k : existing.keySet()) {
                m.set(k, existing.getLevel(k));
            }
        });
        chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.pruneToCurrentEnchantments(
            copy,
            EnchantmentHelper.getEnchantmentsForCrafting(copy)
        );
        return copy;
    }

    public boolean skipActiveRitual(boolean sacrificeXp) {
        if (this.world.isClientSide()) {
            return false;
        }
        refreshLiveShelfState();
        var pos = tableBlockPos();
        if (pos == null
            || !(this.world.getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity tableBe)
            || !tableBe.hasActiveRitual()
            || tableBe.isRitualBlocked()
            || !tableBe.ownsRitual(this.player.getUUID())) {
            pushRitualStateToViewer();
            return false;
        }

        if (sacrificeXp) {
            int levelsCost = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSacrificeXpLevels(
                ritualConfig(),
                tableBe.ritualRemainingTicks()
            );
            if (!this.player.getAbilities().instabuild && this.player.experienceLevel < levelsCost) {
                this.world.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                return false;
            }
            if (!tableBe.completeActiveRitual(
                this.world,
                pos,
                this.player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind.SACRIFICED_XP
            )) return false;
            if (!this.player.getAbilities().instabuild) this.player.giveExperienceLevels(-levelsCost);
            chiseled_enchanting_table.utils.Advancement.give(this.player, "soul_fed_ink");
            this.world.playSound(
                null,
                pos,
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                0.85F
            );
            return true;
        }

        var scan = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.scanLibraryRitualContext(
            this.world,
            pos,
            shelfGatesConfig()
        );
        if (!scan.hasAnyMasterTome()) {
            if (this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                    net.minecraft.network.chat.Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                        .withStyle(net.minecraft.ChatFormatting.RED)
                ));
            }
            this.world.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            pushRitualStateToViewer();
            return false;
        }

        // Non-sacrifice forced rush skip: dynamic probability of degrading lowest-priority Master Tome to 褪魔残卷!
        long remBeforeSkip = tableBe.ritualRemainingTicks();
        boolean usedInfTome = tableBe.anyQueuedStepUsedInfinityTome();
        double degradeProb = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeRushDegradeChance(
            ritualConfig(),
            remBeforeSkip,
            usedInfTome
        );
        if (!tableBe.completeActiveRitual(
            this.world,
            pos,
            this.player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
            chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind.RUSHED_NO_SACRIFICE
        )) return false;
        if (this.world.getRandom().nextDouble() < degradeProb) {
            ItemStack degradedTome = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.degradeLowestPriorityMasterTomeInWorld(
                this.world,
                pos,
                shelfGatesConfig()
            );
            if (degradedTome != null) {
                chiseled_enchanting_table.utils.Advancement.give(this.player, "haste_makes_waste");
                if (this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        net.minecraft.network.chat.Component.translatable("gui.chiseled_enchanting_table.ritual.degraded_to_remnant")
                            .withStyle(net.minecraft.ChatFormatting.RED)
                    ));
                }
                refreshLiveShelfState();
                pushRitualStateToViewer();
                broadcastChanges();
            }
        } else if (this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("gui.chiseled_enchanting_table.ritual.rush_lucky")
                    .withStyle(net.minecraft.ChatFormatting.GREEN)
            ));
        }
        return true;
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
        return this.transformationDisplays.containsKey(id);
    }

    /** Transformation options whose input matches the given stack, from server-synced data. */
    public Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (TransformationDisplay display : this.transformationDisplays.values()) {
            if (syncedTransformationMatches(display.id(), input)) {
                result.add(new EnchantmentWithLevel(display.id(), display.level()));
            }
        }
        return result;
    }

    /** Whether a synced transformation's input matches the stack. */
    public boolean syncedTransformationMatches(Identifier id, ItemStack input) {
        if (input.isEmpty()) {
            return false;
        }
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display == null) {
            return false; // the opening snapshot, including an empty rule set, is authoritative
        }
        if (id.equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID) && ChiseledEnchantingTableData.isResidualBook(input)) {
            return true;
        }
        if (display.input_is_tag()) {
            return input.is(net.minecraft.tags.TagKey.create(Registries.ITEM, display.input_item()));
        }
        Item inputItem = BuiltInRegistries.ITEM.getValue(display.input_item());
        return inputItem != null && input.is(inputItem);
    }

    public int syncedTransformationMinPlayerLevel(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display != null) {
            return Math.max(1, display.min_player_level());
        }
        return ChiseledEnchantingTableData.transformationMinPlayerLevel(id);
    }

    public boolean isTransformationUnlocked(Identifier id) {
        if (this.player != null && this.player.getAbilities().instabuild) {
            return true;
        }
        var activeCfg = chiseled_enchanting_table.config.ModRuntimeConfig.effective();
        if (activeCfg.shouldApplyKey("require_prior_discovery") && !activeCfg.requirePriorDiscovery()) {
            return true;
        }
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display != null) {
            if (display.unlocked()) {
                return true;
            }
            Item outItem = BuiltInRegistries.ITEM.getValue(display.output_item());
            if (outItem != null && outItem != Items.AIR && this.player != null) {
                for (int slot = 0; slot < this.player.getInventory().getContainerSize(); slot++) {
                    ItemStack st = this.player.getInventory().getItem(slot);
                    if (!st.isEmpty() && st.is(outItem)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return ChiseledEnchantingTableData.isTransformationUnlocked(this.player, id);
    }

    public int syncedTransformationLevel(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        if (display != null) {
            return Math.max(1, display.level());
        }
        return ChiseledEnchantingTableData.itemTransformation(id)
            .map(rule -> Math.max(1, rule.level()))
            .orElse(1);
    }

    public int transformationBatchSignature() {
        int sig = this.player != null ? this.player.experienceLevel : 0;
        ItemStack target = get_enchantable_item();
        if (target.isEmpty()) {
            return sig;
        }
        for (EnchantmentWithLevel tr : syncedTransformationsFor(target)) {
            sig = 31 * sig + resolveTransformationBatchCount(tr.enchantment_id());
        }
        return sig;
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
        if (this.world.isClientSide()
            && chiseled_enchanting_table.compat.SyncedEnchantmentCosts.hasServerTable()
            && !chiseled_enchanting_table.compat.SyncedEnchantmentCosts.costTableComplete()) {
            // Audit R-01: the server truncated its cost table; a missing row is "unknown",
            // not "price it from the client's datapacks". Empty options fail closed in
            // findMatchingAffordableCostBundle for every non-whitelisted operation.
            return List.of();
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
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            return ChiseledEnchantingTableData.targetedStripCostOptions(
                e.enchantment_id(),
                e.enchantment_level(),
                getEnchantmentMaxLevel(e)
            );
        }
        if (type == OptionType.PURGE_CURSE) {
            return ChiseledEnchantingTableData.cursePurgeCostOptions(e.enchantment_id());
        }
        if (type == OptionType.DISENCHANT_ALL || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return List.of();
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
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            return ChiseledEnchantingTableData.targetedStripXpCost(
                e.enchantment_id(),
                e.enchantment_level(),
                getEnchantmentMaxLevel(e)
            );
        }
        if (type == OptionType.PURGE_CURSE) {
            return ChiseledEnchantingTableData.cursePurgeXpCost();
        }
        if (type == OptionType.DISENCHANT_ALL || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return 0;
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationXpCost(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.xpCost(e, getEnchantmentMaxLevel(e));
    }

    public boolean disenchantEnabled() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.disenchantEnabled()
            : ChiseledEnchantingTableData.disenchantEnabled();
    }

    public boolean disenchantAllowCursePurge() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.disenchantAllowCursePurge()
            : ChiseledEnchantingTableData.disenchantAllowCursePurge();
    }

    public double disenchantRefundScale() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.disenchantRefundScale()
            : ChiseledEnchantingTableData.disenchantRefundScale();
    }

    public double disenchantExternalRefundScale() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.disenchantExternalRefundScale()
            : ChiseledEnchantingTableData.disenchantExternalRefundScale();
    }

    public int currentTargetEnchantLevel(Identifier enchantmentId) {
        if (enchantmentId == null || ChiseledEnchantingTableData.isTransformation(enchantmentId)) {
            return 0;
        }
        ItemStack target = getEffectiveEnchantableItem();
        if (target.isEmpty()) {
            return 0;
        }
        return findEnchantment(enchantmentId, this.world)
            .map(holder -> EnchantmentHelper.getEnchantmentsForCrafting(target).getLevel(holder))
            .orElse(0);
    }

    private static int findMatchingStackCount(List<ItemStack> bundle, ItemStack probe) {
        if (bundle == null || probe.isEmpty()) return 0;
        for (ItemStack s : bundle) {
            if (ItemStack.isSameItemSameComponents(s, probe)) {
                return s.getCount();
            }
        }
        return 0;
    }

    /**
     * Context-aware effective material options taking the target item's existing enchantment level
     * {@code existingLevel} and nearby shelf/Master-Tome guidance into account identically on
     * server & client.
     */
    public List<List<ItemStack>> getEffectiveItemCostOptions(EnchantmentWithLevel e, OptionType type) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE
            || type == OptionType.DISENCHANT_ALL
            || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return List.of();
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            ItemStack target = get_enchantable_item();
            if (EnchantmentHelper.getEnchantmentsForCrafting(target).size() <= 1) {
                return List.of();
            }
            return getEnchantmentItemCostOptions(e, OptionType.STRIP_SINGLE_ENCHANTMENT);
        }
        if (type == OptionType.PURGE_CURSE) {
            return getEnchantmentItemCostOptions(e, OptionType.PURGE_CURSE);
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            int batchCount = resolveTransformationBatchCount(e.enchantment_id());
            if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(e.enchantment_id())) {
                return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
                    .applyDepletedTomeMaterialTierWithBatch(
                        shelfGatesConfig(),
                        getEnchantmentItemCostOptions(e, type),
                        depletedTomeTier(),
                        batchCount
                    );
            }
            return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.scaleBundlesWithBatch(
                getEnchantmentItemCostOptions(e, OptionType.TRANSFORMATION),
                batchCount
            );
        }

        int existingLevel = currentTargetEnchantLevel(e.enchantment_id());
        if (existingLevel <= 0 || e.enchantment_level() <= existingLevel) {
            return getEnchantmentItemCostOptions(e, type);
        }

        OptionType baselineType = (type == OptionType.BOOK_RESEARCH_UPGRADE) ? OptionType.ENCHANTMENT : type;
        List<List<ItemStack>> targetBundles = getEnchantmentItemCostOptions(e, baselineType);
        List<List<ItemStack>> prevBundles = getEnchantmentItemCostOptions(
            new EnchantmentWithLevel(e.enchantment_id(), existingLevel),
            baselineType
        );

        var lr = libraryResonanceConfig();
        boolean masterGuided = storedMasterLevel(e.enchantment_id()) >= e.enchantment_level();
        double guidanceMultiplier = masterGuided
            ? lr.masterTomeUpgradeMaterialMultiplier()
            : (this.regularBookshelves >= lr.minDiscountBookshelves() ? lr.shelfUpgradeMaterialMultiplier() : 1.0);

        List<List<ItemStack>> researchBundles = (type == OptionType.BOOK_RESEARCH_UPGRADE
            || (get_enchantable_item().is(Items.ENCHANTED_BOOK) && e.enchantment_level() == existingLevel + 1))
            ? getEnchantmentItemCostOptions(e, OptionType.BOOK_RESEARCH_UPGRADE)
            : List.of();

        List<List<ItemStack>> out = new ArrayList<>(targetBundles.size() + researchBundles.size());
        for (int bIdx = 0; bIdx < targetBundles.size(); bIdx++) {
            List<ItemStack> tgtBundle = targetBundles.get(bIdx);
            if (tgtBundle.isEmpty()) {
                // Explicitly material-free datapack rule stays free on upgrades (audit H-02).
                out.add(List.of());
                continue;
            }
            List<ItemStack> prvBundle = bIdx < prevBundles.size() ? prevBundles.get(bIdx) : List.of();
            List<ItemStack> resMatch = findResearchBundleByPrimary(researchBundles, tgtBundle.isEmpty() ? ItemStack.EMPTY : tgtBundle.getFirst());

            List<ItemStack> reduced = new ArrayList<>(tgtBundle.size());
            for (ItemStack st : tgtBundle) {
                ItemStack copy = st.copy();
                if (!copy.is(BlockRegistry.ARCANE_CATALYST)) {
                    int prevCount = findMatchingStackCount(prvBundle, st);
                    int delta = Math.max(1, st.getCount() - prevCount);
                    int guided = Math.max(1, (int) Math.floor(delta * guidanceMultiplier));
                    int resCap = findMatchingStackCount(resMatch, st);
                    if (resCap > 0) {
                        guided = Math.min(guided, resCap);
                    }
                    copy.setCount(guided);
                }
                reduced.add(copy);
            }
            if (!reduced.isEmpty()) {
                out.add(List.copyOf(reduced));
            }
        }
        // Append any distinct primary option from book_upgrades.json (e.g. Arcane Catalyst) not yet present.
        for (List<ItemStack> rb : researchBundles) {
            if (!rb.isEmpty() && findResearchBundleByPrimary(out, rb.getFirst()).isEmpty()) {
                out.add(rb);
            }
        }
        return List.copyOf(out);
    }

    private static List<ItemStack> findResearchBundleByPrimary(List<List<ItemStack>> bundles, ItemStack primary) {
        if (bundles == null || primary.isEmpty()) return List.of();
        for (List<ItemStack> b : bundles) {
            if (!b.isEmpty() && ItemStack.isSameItemSameComponents(b.getFirst(), primary)) {
                return b;
            }
        }
        return List.of();
    }

    public chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.shelfGatesConfig()
            : ChiseledEnchantingTableData.shelfGatesConfig();
    }

    public chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig() {
        return this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.libraryResonanceConfig()
            : ChiseledEnchantingTableData.libraryResonanceConfig();
    }

    public int qualifyingChiseledEnchantments() {
        return this.highTierChiseledBooks;
    }

    @org.jetbrains.annotations.Nullable
    public net.minecraft.core.BlockPos tableBlockPos() {
        net.minecraft.core.BlockPos[] holder = new net.minecraft.core.BlockPos[] { null };
        this.access.execute((lvl, pos) -> holder[0] = pos);
        return holder[0];
    }

    public chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DepletedTomeTier depletedTomeTier() {
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.resolveDepletedTomeTier(
            shelfGatesConfig(),
            this.regularBookshelves,
            this.highTierChiseledBooks
        );
    }

    public int transformationMaxBatchSize() {
        return Math.max(1, chiseled_enchanting_table.config.ModRuntimeConfig.effective().transformationMaxBatchSize());
    }

    public int depletedTomeMaxBatchSize() {
        var sg = shelfGatesConfig();
        return Math.max(1, Math.max(sg.baseBatchSize(), sg.tier3BatchSize()));
    }

    public int countAvailableInInventory(ItemStack required) {
        if (required == null || required.isEmpty() || this.player == null) {
            return 0;
        }
        int found = 0;
        for (int slot = 0; slot < this.player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = this.player.getInventory().getItem(slot);
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, required)) {
                found += candidate.getCount();
            }
        }
        return found;
    }

    public int countAvailableIncludingCostSlot(ItemStack required) {
        if (required == null || required.isEmpty()) {
            return 0;
        }
        ItemStack costSlot = get_cost_item();
        int fromSlot = (!costSlot.isEmpty() && ItemStack.isSameItemSameComponents(costSlot, required))
            ? costSlot.getCount()
            : 0;
        return fromSlot + countAvailableInInventory(required);
    }

    private int maxBundleUnitsAvailable(List<ItemStack> unitBundle) {
        if (unitBundle == null || unitBundle.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(unitBundle);
        if (merged.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        ItemStack primary = merged.getFirst();
        ItemStack costSlot = get_cost_item();
        if (primary.isEmpty() || costSlot.isEmpty() || !ItemStack.isSameItemSameComponents(primary, costSlot)) {
            return 0;
        }
        int units = countAvailableIncludingCostSlot(primary) / Math.max(1, primary.getCount());
        for (int i = 1; i < merged.size(); i++) {
            ItemStack add = merged.get(i);
            if (add.isEmpty()) continue;
            int totalPool = countAvailableInInventory(add);
            int unitNeed = Math.max(1, add.getCount());
            units = Math.min(units, totalPool / unitNeed);
        }
        return Math.max(0, units);
    }

    public int resolveTransformationBatchCount(Identifier transformationId) {
        if (transformationId == null) {
            return 1;
        }
        boolean isBlank = ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(transformationId);
        int inputCount = Math.max(1, get_enchantable_item().getCount());
        int maxCap = isBlank ? Math.max(1, depletedTomeMaxBatchSize()) : transformationMaxBatchSize();
        int desired = Math.max(1, Math.min(maxCap, inputCount));
        if (this.player != null && this.player.getAbilities().instabuild) {
            return desired;
        }
        if (desired <= 1) {
            return 1;
        }

        int level = isBlank ? 1 : Math.max(1, syncedTransformationLevel(transformationId));
        EnchantmentWithLevel unitRef = new EnchantmentWithLevel(transformationId, level);
        List<List<ItemStack>> unitBundles = isBlank
            ? chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.applyDepletedTomeMaterialTier(
                shelfGatesConfig(),
                getEnchantmentItemCostOptions(unitRef, OptionType.TRANSFORMATION),
                depletedTomeTier()
            )
            : getEnchantmentItemCostOptions(unitRef, OptionType.TRANSFORMATION);

        int rawUnitXp = getBaseEnchantmentXpLevelCost(unitRef, OptionType.TRANSFORMATION);
        var baseUnitXp = XpCostConversion.forTransformation(
            libraryResonanceConfig(),
            transformationId,
            rawUnitXp,
            effectiveXpDiscountPercent(transformationId, OptionType.TRANSFORMATION),
            syncedTransformationMinPlayerLevel(transformationId),
            syncedTransformationDeductedLevels(transformationId)
        );
        var effectiveUnitXp = isBlank
            ? chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.applyDepletedTomeXpTierWithBatch(
                baseUnitXp,
                depletedTomeTier(),
                1
            )
            : baseUnitXp;

        int maxXpUnits = XpCostConversion.maxTransformationBatchForLevel(
            effectiveUnitXp,
            this.player != null ? this.player.experienceLevel : 0
        );
        if (unitBundles.isEmpty()) {
            return maxXpUnits >= 1 ? Math.min(desired, maxXpUnits) : 1;
        }

        int maxMatUnits = 0;
        for (List<ItemStack> bundle : unitBundles) {
            maxMatUnits = Math.max(maxMatUnits, maxBundleUnitsAvailable(bundle));
        }
        int affordable = Math.min(maxMatUnits, maxXpUnits);
        return affordable >= 1 ? Math.min(desired, affordable) : 1;
    }

    public int resolveDepletedTomeBatchCount() {
        return resolveTransformationBatchCount(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID);
    }

    public int requiredBookshelvesForDirectTier(Identifier enchantmentId, int targetLevel, int maxLevel) {
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.requiredBookshelvesForDirectTier(
            shelfGatesConfig(),
            enchantmentId,
            targetLevel,
            maxLevel
        );
    }

    public int maxUnlockedDirectTier(Identifier enchantmentId, int maxLevel) {
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.maxUnlockedDirectTier(
            shelfGatesConfig(),
            enchantmentId,
            maxLevel,
            this.regularBookshelves
        );
    }

    public int maxBlankWoodenTier(Identifier enchantmentId, int maxLevel) {
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.maxBlankWoodenTier(
            shelfGatesConfig(),
            enchantmentId,
            maxLevel,
            this.regularBookshelves
        );
    }

    public boolean canDirectUpgradeExistingEnchantment(
        Holder<Enchantment> holder,
        Identifier enchantmentId,
        int existingLevel,
        int targetLevel
    ) {
        if (existingLevel < 1 || targetLevel <= existingLevel || holder == null || holder.is(CURSE_TAG)) {
            return false;
        }
        int maxLvl = Math.max(1, holder.value().getMaxLevel());
        int cap = chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.effectiveUnlockedTierCap(
            shelfGatesConfig(),
            enchantmentId,
            maxLvl,
            this.regularBookshelves,
            storedMasterLevel(enchantmentId)
        );
        return targetLevel <= cap;
    }

    public boolean canDirectResearchUpgradeTo(Identifier enchantmentId, int targetLevel) {
        var optHolder = findEnchantment(enchantmentId, this.world);
        if (optHolder.isEmpty() || optHolder.get().is(CURSE_TAG)) {
            return false;
        }
        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, enchantmentId);
        int cap = chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.effectiveUnlockedTierCap(
            shelfGatesConfig(),
            enchantmentId,
            maxLvl,
            this.regularBookshelves,
            storedMasterLevel(enchantmentId)
        );
        return targetLevel >= 1 && targetLevel <= cap;
    }

    public int storedMasterLevel(Identifier enchantmentId) {
        return enchantmentId == null ? 0 : this.storedMasterLevels.getOrDefault(enchantmentId, 0);
    }

    public double totalMasteryUnits() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.totalMasteryUnits(
            libraryResonanceConfig(),
            this.world,
            this.storedMasterLevels
        );
    }

    public double affinityMasteryUnits(ItemStack targetStack) {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.affinityMasteryUnits(
            libraryResonanceConfig(),
            this.world,
            this.storedMasterLevels,
            targetStack
        );
    }

    public int woodenXpDiscountPercent() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.woodenBookshelfDiscountPercent(
            libraryResonanceConfig(),
            this.regularBookshelves
        );
    }

    public int globalMasteryDiscountPercent() {
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.globalMasteryDiscountPercent(
            libraryResonanceConfig(),
            totalMasteryUnits()
        );
    }

    public int specificEnchantDiscountBonus(Identifier enchantmentId) {
        int stored = storedMasterLevel(enchantmentId);
        if (stored <= 0) return 0;
        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, enchantmentId);
        return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.specificEnchantmentDiscountBonus(
            libraryResonanceConfig(),
            stored,
            maxLvl
        );
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
            libraryResonanceConfig(),
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
        // Audit H-01: every preview/affordability query for a combine row must quote the
        // whole-book price the server will actually deduct for that click.
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return bookCombineXpRequirement();
        }
        return resolveEffectiveXpRequirement(e, type);
    }

    /**
     * Audit H-01: whole-book price for BOOK_COMBINE. One combine click merges <b>every</b>
     * enchantment of the cost book (anvil-style), so it must charge for every entry whose
     * merged level actually rises above the target book's current level - not only the
     * clicked row. Each rising entry is priced at its merged final level through the same
     * BOOK_COMBINE discount path, then combined with sequential semantics. Shared by the
     * server deduction and the client preview so both always agree.
     */
    public XpCostConversion.XpRequirement bookCombineXpRequirement() {
        ItemStack targetBook = get_enchantable_item();
        ItemStack costBook = get_cost_item();
        if (!targetBook.is(Items.ENCHANTED_BOOK) || !costBook.is(Items.ENCHANTED_BOOK)) {
            return XpCostConversion.XpRequirement.FREE;
        }
        ItemEnchantments targetEnchants = EnchantmentHelper.getEnchantmentsForCrafting(targetBook);
        ItemEnchantments costEnchants = EnchantmentHelper.getEnchantmentsForCrafting(costBook);
        java.util.List<XpCostConversion.XpRequirement> parts = new java.util.ArrayList<>();
        for (var entry : costEnchants.entrySet()) {
            var incoming = entry.getKey();
            int current = targetEnchants.getLevel(incoming);
            int incomingLevel = entry.getIntValue();
            int mergedLevel = current == incomingLevel
                ? Math.min(Math.max(1, incoming.value().getMaxLevel()), incomingLevel + 1)
                : Math.max(current, incomingLevel);
            if (mergedLevel > current) {
                parts.add(resolveEffectiveXpRequirement(
                    new EnchantmentWithLevel(EnchantmentToIdentifier(incoming), mergedLevel),
                    OptionType.BOOK_COMBINE_UPGRADE
                ));
            }
        }
        return XpCostConversion.combineSequential(parts);
    }

    public int syncedTransformationDeductedLevels(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        return display != null
            ? display.deducted_levels()
            : ChiseledEnchantingTableData.transformationDeductedLevels(id);
    }

    public XpCostConversion.XpRequirement resolveEffectiveXpRequirement(EnchantmentWithLevel e, OptionType type) {
        var resonanceCfg = libraryResonanceConfig();
        if (type == OptionType.DISENCHANT_ALL) {
            int totalRefund = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication
                .totalFullDisenchantRefund(
                    resonanceCfg,
                    get_enchantable_item(),
                    disenchantRefundScale(),
                    disenchantExternalRefundScale()
                );
            return XpCostConversion.XpRequirement.refund(totalRefund);
        }
        if (type == OptionType.DOWNGRADE_ENCHANTMENT) {
            var optHolder = findEnchantment(e.enchantment_id(), this.world);
            if (optHolder.isEmpty()) {
                return XpCostConversion.XpRequirement.FREE;
            }
            int cur = currentTargetEnchantLevel(e.enchantment_id());
            int refund = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication
                .provenanceRefundForStepDown(
                    resonanceCfg,
                    get_enchantable_item(),
                    optHolder.get(),
                    cur,
                    e.enchantment_level(),
                    disenchantRefundScale(),
                    disenchantExternalRefundScale()
                );
            return XpCostConversion.XpRequirement.refund(refund);
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            ItemStack target = get_enchantable_item();
            var curMap = EnchantmentHelper.getEnchantmentsForCrafting(target);
            if (curMap.size() <= 1) {
                var optHolder = findEnchantment(e.enchantment_id(), this.world);
                if (optHolder.isEmpty()) {
                    return XpCostConversion.XpRequirement.FREE;
                }
                int refund = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication
                    .provenanceEssencePotential(
                        resonanceCfg,
                        target,
                        optHolder.get(),
                        curMap.getLevel(optHolder.get()),
                        disenchantRefundScale(),
                        disenchantExternalRefundScale()
                    );
                return XpCostConversion.XpRequirement.refund(refund);
            }
            int rawStrip = getBaseEnchantmentXpLevelCost(e, OptionType.STRIP_SINGLE_ENCHANTMENT);
            int maxLvl = getEnchantmentMaxLevel(e);
            int stripDiscount = effectiveXpDiscountPercent(e.enchantment_id(), OptionType.STRIP_SINGLE_ENCHANTMENT);
            return XpCostConversion.forTargetedCleanse(resonanceCfg, e.enchantment_level(), maxLvl, rawStrip, stripDiscount);
        }
        if (type == OptionType.PURGE_CURSE) {
            int rawPurge = getBaseEnchantmentXpLevelCost(e, OptionType.PURGE_CURSE);
            return XpCostConversion.forFullArcaneRitual(resonanceCfg, rawPurge, this.xpDiscountPercent);
        }

        int rawCost = getBaseEnchantmentXpLevelCost(e, type);
        int discount = effectiveXpDiscountPercent(e.enchantment_id(), type);
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            var baseTransformXp = XpCostConversion.forTransformation(
                resonanceCfg,
                e.enchantment_id(),
                rawCost,
                discount,
                syncedTransformationMinPlayerLevel(e.enchantment_id()),
                syncedTransformationDeductedLevels(e.enchantment_id())
            );
            int batchCount = resolveTransformationBatchCount(e.enchantment_id());
            if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(e.enchantment_id())) {
                return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
                    .applyDepletedTomeXpTierWithBatch(
                        baseTransformXp,
                        depletedTomeTier(),
                        batchCount
                    );
            }
            return XpCostConversion.scaleTransformationXpForBatch(baseTransformXp, batchCount);
        }

        int existingLevel = currentTargetEnchantLevel(e.enchantment_id());
        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
            this.world,
            e.enchantment_id()
        );
        if (existingLevel > 0 && e.enchantment_level() > existingLevel) {
            if (storedMasterLevel(e.enchantment_id()) >= e.enchantment_level()
                && (type == OptionType.ENCHANTMENT
                    || type == OptionType.BOOK_RESEARCH_UPGRADE
                    || type == OptionType.BOOK_COMBINE_UPGRADE)) {
                int fullRaw = getBaseEnchantmentXpLevelCost(e, OptionType.ENCHANTMENT);
                int effectiveMasterRaw = type == OptionType.BOOK_COMBINE_UPGRADE
                    ? Math.min(rawCost, fullRaw)
                    : fullRaw;
                return XpCostConversion.forIncrementalMasterTomeEnchant(
                    resonanceCfg,
                    existingLevel,
                    e.enchantment_level(),
                    maxLvl,
                    effectiveMasterRaw,
                    discount
                );
            }
            int targetRaw = getBaseEnchantmentXpLevelCost(
                new EnchantmentWithLevel(e.enchantment_id(), e.enchantment_level()),
                OptionType.ENCHANTMENT
            );
            int prevRaw = getBaseEnchantmentXpLevelCost(
                new EnchantmentWithLevel(e.enchantment_id(), existingLevel),
                OptionType.ENCHANTMENT
            );
            int deltaRaw = Math.max(1, targetRaw - prevRaw);
            int effectiveRaw = (type == OptionType.BOOK_RESEARCH_UPGRADE || type == OptionType.BOOK_COMBINE_UPGRADE)
                ? Math.min(rawCost, deltaRaw)
                : deltaRaw;
            return XpCostConversion.forFullArcaneEnchantOrUpgrade(
                resonanceCfg,
                existingLevel,
                e.enchantment_level(),
                maxLvl,
                effectiveRaw,
                discount
            );
        }

        if (isMasterTomeBacked(e, type)) {
            return XpCostConversion.forMasterTomeEnchant(resonanceCfg, e.enchantment_level(), maxLvl, rawCost, discount);
        }
        return XpCostConversion.forFullArcaneEnchantOrUpgrade(
            resonanceCfg,
            0,
            e.enchantment_level(),
            maxLvl,
            rawCost,
            discount
        );
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
            libraryResonanceConfig(),
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
            libraryResonanceConfig(),
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
        if (chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(id)) {
            return false;
        }
        Set<Identifier> explicit = this.world.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.explicitTreasureIds()
            : ChiseledEnchantingTableData.treasureEpiphanyExplicitIds(this.world.registryAccess());
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
        java.util.List<Holder.Reference<Enchantment>> poolHolders = new java.util.ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            if (!isTreasureEnchantment(holder)) continue;
            Identifier id = holder.key().identifier();
            int maxLvl = Math.max(1, holder.value().getMaxLevel());
            if (storedMasterLevel(id) >= maxLvl) continue;
            if (isBlankBook || holder.value().canEnchant(targetStack)) {
                poolHolders.add(holder);
            }
        }
        if (poolHolders.isEmpty()) {
            return List.of();
        }
        poolHolders.sort(java.util.Comparator.comparing(h -> h.key().identifier().toString()));
        java.util.List<Identifier> pool = new java.util.ArrayList<>(poolHolders.size());
        java.util.List<Double> poolWeights = new java.util.ArrayList<>(poolHolders.size());
        for (Holder.Reference<Enchantment> h : poolHolders) {
            pool.add(h.key().identifier());
            poolWeights.add(chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeEpiphanySelectionWeight(h));
        }

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
        var resonanceCfg = libraryResonanceConfig();
        double resScore = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.epiphanyResonanceScore(
            resonanceCfg,
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
                int index = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.pickWeightedIndex(
                    poolWeights,
                    pickHash
                );
                Identifier chosen = pool.remove(index);
                poolWeights.remove(index);
                int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(this.world, chosen);
                int storedLvl = storedMasterLevel(chosen);
                int minTier = Math.min(maxLvl, storedLvl + 1);
                double ownR = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.relativeMastery(storedLvl, maxLvl);
                int tier = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.rollEpiphanyTier(
                    resonanceCfg,
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
        refreshLiveShelfState();
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
        refreshLiveShelfState();
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
        return findEnchantment(e.enchantment_id(), this.world)
            .map(h -> Math.max(1, h.value().getMaxLevel()))
            .orElseGet(() -> Math.max(1, e.enchantment_level()));
    }

    public static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Level world) {
        if (id == null || world == null) {
            return java.util.Optional.empty();
        }
        return world.registryAccess()
            .lookup(Registries.ENCHANTMENT)
            .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
    }

    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Level world) {
        return findEnchantment(id, world)
            .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + id));
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
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(costs);
        for (int i = 1; i < merged.size(); i++) {
            ItemStack required = merged.get(i);
            if (countAvailableInInventory(required) < required.getCount()) {
                return false;
            }
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

    public boolean isBundleAffordableWithOverflow(List<ItemStack> bundle) {
        if (bundle == null || bundle.isEmpty()) {
            return true;
        }
        if (this.player.getAbilities().instabuild) {
            return true;
        }
        return maxBundleUnitsAvailable(bundle) >= 1;
    }

    @org.jetbrains.annotations.Nullable
    /**
     * Audit H-02: the exhaustive whitelist of operations that are <b>by design</b> allowed to
     * run without material cost even when no cost option exists. Everything else with an
     * empty/unresolvable option list is a datapack data error and must fail closed instead
     * of silently becoming free.
     */
    public boolean isMaterialFreeOperation(EnchantmentWithLevel ewl, OptionType type) {
        return switch (type) {
            case BOOK_COMBINE_UPGRADE, DISENCHANT_ALL, DOWNGRADE_ENCHANTMENT -> true;
            case STRIP_SINGLE_ENCHANTMENT ->
                EnchantmentHelper.getEnchantmentsForCrafting(get_enchantable_item()).size() <= 1;
            case TRANSFORMATION ->
                ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(ewl.enchantment_id())
                    && depletedTomeTier()
                        == chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DepletedTomeTier.TIER_3_FREE;
            default -> false;
        };
    }

    public List<ItemStack> findMatchingAffordableCostBundle(EnchantmentWithLevel ewl, OptionType type) {
        List<List<ItemStack>> options = getEffectiveItemCostOptions(ewl, type);
        if (options.isEmpty()) {
            // Fail closed (audit H-02): "no pricing data" is only free for whitelisted
            // material-free operations; datapacks declare XP-only costs explicitly via
            // "material_free": true, which produces one empty bundle instead.
            return isMaterialFreeOperation(ewl, type) ? List.of() : null;
        }
        for (List<ItemStack> bundle : options) {
            if (bundle.isEmpty()) {
                return List.of();
            }
        }
        if (this.player.getAbilities().instabuild) {
            return ChiseledEnchantingTableData.mergeEquivalentCostBundle(options.getFirst());
        }
        for (List<ItemStack> bundle : options) {
            List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(bundle);
            if (!merged.isEmpty() && isBundleAffordableWithOverflow(merged)) {
                return merged;
            }
        }
        return null;
    }

    public void deductMatchedBundle(List<ItemStack> matchedBundle) {
        if (this.player.getAbilities().instabuild || matchedBundle == null || matchedBundle.isEmpty()) {
            return;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(matchedBundle);
        if (merged.isEmpty()) {
            return;
        }
        ItemStack primary = merged.getFirst();
        if (!primary.isEmpty()) {
            int remainingPrimary = primary.getCount();
            ItemStack costSlot = this.get_cost_item();
            if (!costSlot.isEmpty() && ItemStack.isSameItemSameComponents(costSlot, primary)) {
                int takeSlot = Math.min(remainingPrimary, costSlot.getCount());
                costSlot.shrink(takeSlot);
                remainingPrimary -= takeSlot;
            }
            for (int slot = 0; slot < this.player.getInventory().getContainerSize() && remainingPrimary > 0; slot++) {
                ItemStack candidate = this.player.getInventory().getItem(slot);
                if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, primary)) {
                    int takeInv = Math.min(remainingPrimary, candidate.getCount());
                    candidate.shrink(takeInv);
                    remainingPrimary -= takeInv;
                }
            }
            this.inventory.setChanged();
            this.player.getInventory().setChanged();
        }
        consumeAdditionalCosts(merged);
    }

    /** Exact survival payment receipt used for 50% cross-line pollution compensation. */
    public record PaymentReceipt(boolean accepted, int paidXpPoints, List<ItemStack> paidMaterials) {}

    public boolean applyEnchantementCondition(ApplyEnchantmentPayload payload) {
        return applyEnchantementConditionWithReceipt(payload).accepted();
    }

    public PaymentReceipt applyEnchantementConditionWithReceipt(ApplyEnchantmentPayload payload) {
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), payload.enchantment_level());
        OptionType effectiveType = payload.option_type();
        if (effectiveType == OptionType.ENCHANTMENT && ChiseledEnchantingTableData.isTransformation(payload.enchantment_id())) {
            effectiveType = OptionType.TRANSFORMATION;
        }
        var xpReq = resolveXpRequirement(ewl, effectiveType);
        if (!xpReq.isMetBy(this.player)) {
            return new PaymentReceipt(false, 0, List.of());
        }

        List<ItemStack> matchedBundle = findMatchingAffordableCostBundle(ewl, effectiveType);
        if (!player.getAbilities().instabuild && matchedBundle == null) {
            return new PaymentReceipt(false, 0, List.of());
        }

        if (!player.getAbilities().instabuild) {
            int xpBefore = XpCostConversion.totalPlayerXpPoints(this.player);
            XpCostConversion.deductXp(this.player, xpReq);
            int xpAfter = XpCostConversion.totalPlayerXpPoints(this.player);
            int paidXpPoints = Math.max(0, xpBefore - xpAfter);
            List<ItemStack> paidCopies = matchedBundle == null
                ? List.of()
                : matchedBundle.stream().map(ItemStack::copy).toList();
            deductMatchedBundle(matchedBundle);
            return new PaymentReceipt(true, paidXpPoints, paidCopies);
        }
        return new PaymentReceipt(true, 0, List.of());
    }

    // ---- Dispatch ----

    public void applyEnchantement(ApplyEnchantmentPayload payload) {
        refreshLiveShelfState();
        if (chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(get_enchantable_item())
            || chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(get_cost_item())) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.locked_input");
            return;
        }
        boolean cleanseAction = payload.option_type() == OptionType.DISENCHANT_ALL
            || payload.option_type() == OptionType.DOWNGRADE_ENCHANTMENT
            || payload.option_type() == OptionType.STRIP_SINGLE_ENCHANTMENT
            || payload.option_type() == OptionType.PURGE_CURSE;
        if (cleanseAction && !isCleansePageUnlocked()) {
            tomeActionMessage("gui.chiseled_enchanting_table.tab.locked_tooltip");
            return;
        }
        if (payload.option_type() == OptionType.DISENCHANT_ALL
            || payload.option_type() == OptionType.DOWNGRADE_ENCHANTMENT
            || payload.option_type() == OptionType.STRIP_SINGLE_ENCHANTMENT
            || payload.option_type() == OptionType.PURGE_CURSE) {
            if (isRitualActive()) {
                return;
            }
            chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication.apply(this, payload);
            return;
        }
        // Prefer the explicit option type from the payload; fall back to a data lookup so older
        // clients (which always send ENCHANTMENT) still route transformations correctly.
        if (payload.option_type() == OptionType.BOOK_COMBINE_UPGRADE || payload.option_type() == OptionType.BOOK_RESEARCH_UPGRADE) {
            if (isRitualActive()) {
                return;
            }
            BookUpgradeApplication.apply(this, payload);
            return;
        }
        boolean isTransformation = payload.option_type() == OptionType.TRANSFORMATION
            || (payload.option_type() == OptionType.ENCHANTMENT && ChiseledEnchantingTableData.isTransformation(payload.enchantment_id()));
        if (isTransformation) {
            if (isRitualActive()) {
                return;
            }
            ItemTransformationApplication.apply(this, payload);
            return;
        }
        if (this.get_cost_item().is(Items.ENCHANTED_BOOK)) {
            if (isRitualActive()) {
                return;
            }
            EnchantmentApplication.applyFromBook(this, payload);
            return;
        }
        EnchantmentApplication.apply(this, payload);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Chest-style persistence: items remain stored inside ChiseledEnchantingTableBlockEntity!
        if (!this.persistentBlockInventory) {
            this.clearContainer(player, this.inventory);
        }
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (actionType == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE) {
            if (slotIndex < 0 || slotIndex >= this.slots.size()) return;
            Slot sourceSlot = this.getSlot(slotIndex);
            if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) return;
            ItemStack clickedStack = sourceSlot.getItem();
            if (clickedStack.isEmpty()) return;

            if (slotIndex == COST_SLOT || slotIndex == ENCHANTABLE_SLOT) {
                if (slotIndex == ENCHANTABLE_SLOT && isRitualActive()) return;
                ItemStack moving = clickedStack.copy();
                sourceSlot.set(ItemStack.EMPTY);
                chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, moving, java.util.Optional.empty());
                this.inventory.setChanged();
                this.player.getInventory().setChanged();
                return;
            }

            Slot targetSlot = this.getSlot(ENCHANTABLE_SLOT);
            boolean ritualLocked = isRitualActive();

            if (!ritualLocked && targetSlot.mayPlace(clickedStack)) {
                ItemStack existingTarget = targetSlot.getItem();
                int maxTargetAllowed = Math.max(1, targetSlot.getMaxStackSize(clickedStack));

                if (!existingTarget.isEmpty()
                    && maxTargetAllowed > 1
                    && ItemStack.isSameItemSameComponents(clickedStack, existingTarget)
                    && existingTarget.getCount() < maxTargetAllowed) {
                    int move = Math.min(maxTargetAllowed - existingTarget.getCount(), clickedStack.getCount());
                    ItemStack updatedTarget = existingTarget.copyWithCount(existingTarget.getCount() + move);
                    ItemStack remainingSource = clickedStack.getCount() > move
                        ? clickedStack.copyWithCount(clickedStack.getCount() - move)
                        : ItemStack.EMPTY;
                    targetSlot.set(updatedTarget);
                    sourceSlot.set(remainingSource);
                    this.inventory.setChanged();
                    this.player.getInventory().setChanged();
                    return;

                }

                ItemStack clickedStackNoEnchant = clickedStack.copyWithCount(1);
                EnchantmentHelper.updateEnchantments(clickedStackNoEnchant, m -> {
                    var keys = new java.util.HashSet<>(m.keySet());
                    for (var k : keys) m.set(k, 0);
                });
                boolean isBook = clickedStackNoEnchant.is(Items.BOOK) || clickedStackNoEnchant.is(Items.ENCHANTED_BOOK);
                boolean hasExistingEnchants = !EnchantmentHelper.getEnchantmentsForCrafting(clickedStack).isEmpty();
                boolean isDedicatedTransformInput = clickedStack.is(BlockRegistry.DEPLETED_TOME)
                    || ChiseledEnchantingTableData.isResidualBook(clickedStack)
                    || (!targetSlot.hasItem() && !this.syncedTransformationsFor(clickedStack).isEmpty());

                boolean wantsTargetSlot = clickedStackNoEnchant.isEnchantable()
                    || isDedicatedTransformInput
                    || (hasExistingEnchants && !isBook && !targetSlot.hasItem())
                    || (isBook && !targetSlot.hasItem());

                if (wantsTargetSlot && (!targetSlot.hasItem() || targetSlot.mayPickup(player))) {
                    ItemStack displaced = existingTarget.isEmpty() ? ItemStack.EMPTY : existingTarget.copy();
                    int moveCount = Math.min(maxTargetAllowed, clickedStack.getCount());
                    ItemStack placed = clickedStack.copyWithCount(moveCount);
                    ItemStack remainingSource = clickedStack.getCount() > moveCount
                        ? clickedStack.copyWithCount(clickedStack.getCount() - moveCount)
                        : ItemStack.EMPTY;

                    targetSlot.set(placed);
                    if (displaced.isEmpty()) {
                        sourceSlot.set(remainingSource);
                    } else if (remainingSource.isEmpty()) {
                        sourceSlot.set(displaced);
                    } else {
                        sourceSlot.set(remainingSource);
                        int containerSlot = sourceSlot.getContainerSlot();
                        java.util.Optional<Integer> preferredSlot = (containerSlot >= 0 && containerSlot < 36)
                            ? java.util.Optional.of(containerSlot)
                            : java.util.Optional.empty();
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, displaced, preferredSlot);
                    }
                    this.inventory.setChanged();
                    this.player.getInventory().setChanged();
                    return;
                }
            } else {
                // While a ritual is active, never route enchantable gear or blank books into COST_SLOT by accident unless valid cost!
                ItemStack probe = clickedStack.copyWithCount(1);
                EnchantmentHelper.updateEnchantments(probe, m -> {
                    var keys = new java.util.HashSet<>(m.keySet());
                    for (var k : keys) m.set(k, 0);
                });
                if (probe.isEnchantable() || clickedStack.is(BlockRegistry.DEPLETED_TOME)) {
                    return;
                }
            }

            Slot costSlotObj = this.getSlot(COST_SLOT);
            if (!costSlotObj.mayPlace(clickedStack)) {
                return;
            }
            if (!costSlotObj.hasItem()) {
                this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT + 1, false);
            } else {
                ItemStack costSlotStack = costSlotObj.getItem();
                if (ItemStack.isSameItemSameComponents(clickedStack, costSlotStack)) {
                    this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT + 1, false);
                } else if (costSlotObj.mayPickup(player)) {
                    int containerSlot = sourceSlot.getContainerSlot();
                    java.util.Optional<Integer> preferredSlot = (containerSlot >= 0 && containerSlot < 36)
                        ? java.util.Optional.of(containerSlot)
                        : java.util.Optional.empty();
                    ItemStack returning = costSlotStack.copy();
                    int maxCostAllowed = Math.max(1, costSlotObj.getMaxStackSize(clickedStack));
                    int moveCount = Math.min(maxCostAllowed, clickedStack.getCount());
                    ItemStack placedCost = clickedStack.copyWithCount(moveCount);
                    ItemStack remainingSource = clickedStack.getCount() > moveCount
                        ? clickedStack.copyWithCount(clickedStack.getCount() - moveCount)
                        : ItemStack.EMPTY;

                    costSlotObj.set(placedCost);
                    if (remainingSource.isEmpty()) {
                        sourceSlot.set(returning);
                    } else {
                        sourceSlot.set(remainingSource);
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
