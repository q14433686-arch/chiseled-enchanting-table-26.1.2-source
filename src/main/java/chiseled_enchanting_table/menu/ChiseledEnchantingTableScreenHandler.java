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
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.ReopenTablePayload.TYPE,
            ChiseledEnchantingMenuPayloads.ReopenTablePayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE,
            ChiseledEnchantingMenuPayloads.SkipRitualPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler chiseledEnchantingTableScreenHandler) {
                    chiseledEnchantingTableScreenHandler.skipActiveRitual(payload.sacrificeXp());
                }
            });
        });
    }

    public Runnable onContentChangedCallback = () -> {};
    public Player player;
    public final Set<EnchantmentWithLevel> unlocked_enchantements;
    public int xpDiscountPercent;
    public int regularBookshelves;
    public int chiseledBookshelves;
    public int chiseledBookCount;
    public int highTierChiseledBooks;
    public java.util.Map<Identifier, Integer> storedMasterLevels;
    public java.util.Map<EnchantmentWithLevel, Integer> masterTomeCharges;
    public boolean crampedLibrary;
    private int epiphanySeed;
    private int syncedCycleIndex;
    private int syncedCountdownSeconds;
    private boolean syncedFrozen;
    private int syncedRitualRemainingTicks = 0;
    private int syncedRitualTotalTicks = 0;
    private List<EnchantmentWithLevel> syncedQueuedSteps = List.of();
    private boolean syncedUsedInfinityTome = false;
    private boolean syncedHasAnyMasterTome = false;
    /** Server-synced transformation table, keyed by transformation id. Used by client UI. */
    public final java.util.Map<Identifier, TransformationDisplay> transformationDisplays;
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
                if (stack.is(BlockRegistry.DEPLETED_TOME)) {
                    return depletedTomeMaxBatchSize();
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
        if (live == null) {
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
        this.chiseledBookCount = Math.max(0, live.chiseled_book_count());
        this.highTierChiseledBooks = Math.max(0, live.high_tier_chiseled_books());
        this.storedMasterLevels = live.stored_master_levels() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(live.stored_master_levels());
        this.masterTomeCharges = live.master_tome_charges() == null
            ? java.util.Map.of()
            : java.util.Map.copyOf(live.master_tome_charges());
        this.crampedLibrary = live.cramped_library();
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    public int totalLibraryBookshelves() {
        return Math.max(0, this.regularBookshelves) + Math.max(0, this.chiseledBookshelves);
    }

    public int masterTomeRemainingCharges(Identifier id, int level) {
        if (id == null) {
            return this.libraryResonanceConfig().maxTomeUses();
        }
        Integer live = this.masterTomeCharges.get(new EnchantmentWithLevel(id, Math.max(1, level)));
        return live != null ? live : this.libraryResonanceConfig().maxTomeUses();
    }

    public void refreshLiveShelfState() {
        if (this.world.isClientSide()) {
            return;
        }
        ChiseledEnchantingMenuPayloads.LiveShelfStatePayload[] holder =
            new ChiseledEnchantingMenuPayloads.LiveShelfStatePayload[] { null };
        this.access.execute((lvl, pos) ->
            holder[0] = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.computeLiveShelfState(lvl, pos)
        );
        var live = holder[0];
        if (live == null) {
            return;
        }
        boolean changed = !live.unlocked_enchantements().equals(this.unlocked_enchantements)
            || live.xp_discount_percent() != this.xpDiscountPercent
            || live.regular_bookshelves() != this.regularBookshelves
            || live.chiseled_bookshelves() != this.chiseledBookshelves
            || live.chiseled_book_count() != this.chiseledBookCount
            || live.high_tier_chiseled_books() != this.highTierChiseledBooks
            || live.cramped_library() != this.crampedLibrary
            || !live.stored_master_levels().equals(this.storedMasterLevels)
            || !java.util.Objects.equals(live.master_tome_charges(), this.masterTomeCharges);
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

    public boolean isRitualActive() {
        return ritualRemainingTicks() > 0L;
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

    public int currentSacrificeSkipLevels() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSacrificeXpLevels(
            ritualRemainingTicks()
        );
    }

    public double currentRushSkipDegradeChance() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeRushDegradeChance(
            ritualRemainingTicks(),
            ritualUsedInfinityTome()
        );
    }

    public boolean canSacrificeSkipNow() {
        if (!isRitualActive() || !hasAnyMasterTomeInRange()) {
            return false;
        }
        if (this.player.getAbilities().instabuild) {
            return true;
        }
        return this.player.experienceLevel >= currentSacrificeSkipLevels();
    }

    public boolean canRushSkipNow() {
        return isRitualActive() && hasAnyMasterTomeInRange();
    }

    public void pushRitualStateToViewer() {
        if (this.world.isClientSide() || !(this.player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return;
        }
        if (!ServerPlayNetworking.canSend(sp, ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.TYPE)) {
            return;
        }
        var payload = new ChiseledEnchantingMenuPayloads.SyncRitualStatePayload(
            queuedRitualSteps(),
            ritualUsedInfinityTome(),
            hasAnyMasterTomeInRange()
        );
        ServerPlayNetworking.send(sp, payload);
    }

    public void applySyncedRitualState(ChiseledEnchantingMenuPayloads.SyncRitualStatePayload payload) {
        this.syncedQueuedSteps = payload.queuedSteps() != null ? List.copyOf(payload.queuedSteps()) : List.of();
        this.syncedUsedInfinityTome = payload.usedInfinityTome();
        this.syncedHasAnyMasterTome = payload.hasAnyMasterTome();
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    /**
     * Returns a virtual copy of {@link #get_enchantable_item()} reflecting all currently queued
     * inscriptions so players can queue unlimited sequential enchantments cleanly without dupes.
     */
    public ItemStack getEffectiveEnchantableItem() {
        ItemStack base = get_enchantable_item();
        List<EnchantmentWithLevel> queued = queuedRitualSteps();
        if (base.isEmpty() || queued.isEmpty()) {
            return base;
        }
        boolean wasBook = base.is(Items.BOOK) || base.is(Items.ENCHANTED_BOOK);
        ItemStack copy = base.is(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK, 1) : base.copy();
        var existing = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
            EnchantmentHelper.getEnchantmentsForCrafting(copy)
        );
        for (EnchantmentWithLevel q : queued) {
            try {
                Holder<Enchantment> h = IdentifierToEnchantment(q.enchantment_id(), this.world);
                if (!wasBook) {
                    var keys = new HashSet<>(existing.keySet());
                    for (var k : keys) {
                        if (!Enchantment.areCompatible(k, h)) {
                            existing.set(k, 0);
                        }
                    }
                }
                existing.set(h, q.enchantment_level());
            } catch (RuntimeException ignored) {
            }
        }
        EnchantmentHelper.updateEnchantments(copy, m -> {
            var mKeys = new HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var k : existing.keySet()) {
                m.set(k, existing.getLevel(k));
            }
        });
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
            || !tableBe.hasActiveRitual()) {
            pushRitualStateToViewer();
            return false;
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

        if (sacrificeXp) {
            int levelsCost = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSacrificeXpLevels(
                tableBe.ritualRemainingTicks()
            );
            if (!this.player.getAbilities().instabuild && this.player.experienceLevel < levelsCost) {
                this.world.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                return false;
            }
            if (!this.player.getAbilities().instabuild) {
                this.player.giveExperienceLevels(-levelsCost);
            }
            tableBe.completeActiveRitual(
                this.world,
                pos,
                this.player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind.SACRIFICED_XP
            );
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

        // Non-sacrifice forced rush skip: dynamic probability of degrading lowest-priority Master Tome to 褪魔残卷!
        long remBeforeSkip = tableBe.ritualRemainingTicks();
        boolean usedInfTome = tableBe.anyQueuedStepUsedInfinityTome();
        double degradeProb = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeRushDegradeChance(
            remBeforeSkip,
            usedInfTome
        );
        tableBe.completeActiveRitual(
            this.world,
            pos,
            this.player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
            chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind.RUSHED_NO_SACRIFICE
        );
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
        return this.transformationDisplays.containsKey(id) || ChiseledEnchantingTableData.isTransformation(id);
    }

    /** Transformation options whose input matches the given stack, from server-synced data. */
    public Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        if (this.transformationDisplays.isEmpty()) {
            return ChiseledEnchantingTableData.transformationEnchantmentsFor(input);
        }
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
            return ChiseledEnchantingTableData.itemTransformation(id).map(rule -> rule.matchesInput(input)).orElse(false);
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
        try {
            Holder<Enchantment> holder = IdentifierToEnchantment(enchantmentId, this.world);
            return EnchantmentHelper.getEnchantmentsForCrafting(target).getLevel(holder);
        } catch (Exception ignored) {
            return 0;
        }
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
        ItemStack primary = unitBundle.getFirst();
        ItemStack costSlot = get_cost_item();
        if (primary.isEmpty() || costSlot.isEmpty() || !ItemStack.isSameItemSameComponents(primary, costSlot)) {
            return 0;
        }
        int units = countAvailableIncludingCostSlot(primary) / Math.max(1, primary.getCount());
        for (int i = 1; i < unitBundle.size(); i++) {
            ItemStack add = unitBundle.get(i);
            if (add.isEmpty()) continue;
            int totalPool = ItemStack.isSameItemSameComponents(add, primary)
                ? countAvailableIncludingCostSlot(add)
                : countAvailableInInventory(add);
            int unitNeed = ItemStack.isSameItemSameComponents(add, primary)
                ? (Math.max(1, primary.getCount()) + Math.max(1, add.getCount()))
                : Math.max(1, add.getCount());
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
        int maxCap = isBlank ? Math.max(1, depletedTomeMaxBatchSize()) : 64;
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
        try {
            Holder<Enchantment> holder = IdentifierToEnchantment(enchantmentId, this.world);
            if (holder.is(CURSE_TAG)) {
                return false;
            }
        } catch (Exception ignored) {
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
        return resolveEffectiveXpRequirement(e, type);
    }

    public int syncedTransformationDeductedLevels(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        return display != null && display.deducted_levels() > 0
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
            try {
                Holder<Enchantment> holder = IdentifierToEnchantment(e.enchantment_id(), this.world);
                int cur = currentTargetEnchantLevel(e.enchantment_id());
                int refund = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication
                    .provenanceRefundForStepDown(
                        resonanceCfg,
                        get_enchantable_item(),
                        holder,
                        cur,
                        e.enchantment_level(),
                        disenchantRefundScale(),
                        disenchantExternalRefundScale()
                    );
                return XpCostConversion.XpRequirement.refund(refund);
            } catch (Exception ignored) {
                return XpCostConversion.XpRequirement.FREE;
            }
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            ItemStack target = get_enchantable_item();
            var curMap = EnchantmentHelper.getEnchantmentsForCrafting(target);
            if (curMap.size() <= 1) {
                try {
                    Holder<Enchantment> holder = IdentifierToEnchantment(e.enchantment_id(), this.world);
                    int refund = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication
                        .provenanceEssencePotential(
                            resonanceCfg,
                            target,
                            holder,
                            curMap.getLevel(holder),
                            disenchantRefundScale(),
                            disenchantExternalRefundScale()
                        );
                    return XpCostConversion.XpRequirement.refund(refund);
                } catch (Exception ignored) {
                    return XpCostConversion.XpRequirement.FREE;
                }
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
    public List<ItemStack> findMatchingAffordableCostBundle(EnchantmentWithLevel ewl, OptionType type) {
        List<List<ItemStack>> options = getEffectiveItemCostOptions(ewl, type);
        if (options.isEmpty()) {
            return List.of();
        }
        if (this.player.getAbilities().instabuild) {
            return options.getFirst();
        }
        for (List<ItemStack> bundle : options) {
            if (!bundle.isEmpty() && isBundleAffordableWithOverflow(bundle)) {
                return bundle;
            }
        }
        return null;
    }

    public void deductMatchedBundle(List<ItemStack> matchedBundle) {
        if (this.player.getAbilities().instabuild || matchedBundle == null || matchedBundle.isEmpty()) {
            return;
        }
        ItemStack primary = matchedBundle.getFirst();
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
        refreshLiveShelfState();
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
            if (slotIndex == ENCHANTABLE_SLOT && isRitualActive()) return;
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
            int stackLimit = clickedStack.is(BlockRegistry.DEPLETED_TOME)
                ? depletedTomeMaxBatchSize()
                : clickedStack.getMaxStackSize();
            if (!existingTarget.isEmpty()
                && ItemStack.isSameItemSameComponents(clickedStack, existingTarget)
                && (clickedStack.is(BlockRegistry.DEPLETED_TOME) || !this.syncedTransformationsFor(clickedStack).isEmpty())
                && existingTarget.getCount() < stackLimit) {
                int space = stackLimit - existingTarget.getCount();
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
            boolean hasExistingEnchants = !EnchantmentHelper.getEnchantmentsForCrafting(clickedStack).isEmpty();
            boolean isDedicatedTransformInput = clickedStack.is(BlockRegistry.DEPLETED_TOME)
                || ChiseledEnchantingTableData.isResidualBook(clickedStack)
                || (!this.getSlot(ENCHANTABLE_SLOT).hasItem() && !this.syncedTransformationsFor(clickedStack).isEmpty());

            if (clickedStackNoEnchant.isEnchantable()
                || isDedicatedTransformInput
                || (hasExistingEnchants && !isBook && !this.getSlot(ENCHANTABLE_SLOT).hasItem())
                || (isBook && !this.getSlot(ENCHANTABLE_SLOT).hasItem())) {
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
