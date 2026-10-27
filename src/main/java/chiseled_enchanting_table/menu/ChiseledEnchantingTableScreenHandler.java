package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;

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
    public int sacrificeRequired = chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE_REQUIRED;
    public int tomeRequiredShelves = chiseled_enchanting_table.utils.Advancement.TOME_PAGE_REQUIRED_SHELVES;
    public double syncedEffectiveMasteryUnits = -1.0;

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
    @org.jetbrains.annotations.Nullable
    private final net.minecraft.core.BlockPos syncedTablePos;
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
        this.syncedTablePos = payload.table_pos().orElse(null);
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
        // Persistent per-player page state from the opening payload: correct at frame one.
        this.syncedCleanseUnlocked = payload.cleanse_unlocked();
        this.syncedTomePageUnlocked = payload.tome_page_unlocked();
        this.sacrificeProgress = Math.max(0, payload.sacrifice_progress());
        this.sacrificeRequired = Math.max(1, payload.sacrifice_required());
        this.tomeRequiredShelves = Math.max(1, payload.tome_page_required_shelves());
        this.syncedEffectiveMasteryUnits = payload.effective_mastery_units();
        this.qualifyingEnchantedBookshelves = this.chiseledBookshelves;
        this.liveTotalLibraryBookshelves = totalLibraryBookshelves();
        var initLrCfg = this.libraryResonanceConfig();
        this.liveRegenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.regenTierIndex(
            initLrCfg,
            this.liveTotalLibraryBookshelves
        );
        this.nextRegenTierGap = switch (this.liveRegenTier) {
            case 0 -> Math.max(0, initLrCfg.tomeRegenTier1Shelves() - this.liveTotalLibraryBookshelves);
            case 1 -> Math.max(0, initLrCfg.tomeRegenTier2Shelves() - this.liveTotalLibraryBookshelves);
            default -> 0;
        };
        boolean initRegenActive = initLrCfg.tomeRegenEnabled()
            && initLrCfg.tomeConsumptionMode() == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR;
        int initInterval = initRegenActive
            ? chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.computeRegenIntervalTicks(
                initLrCfg,
                this.liveTotalLibraryBookshelves
            )
            : -1;
        this.regenIntervalTicks = initInterval == Integer.MAX_VALUE ? -1 : initInterval;
        this.syncedHasAnyMasterTome = this.chiseledBookCount > 0 || !this.storedMasterLevels.isEmpty();

        this.epiphanySeed = payload.epiphany_seed();
        int cycleTicksInt = Math.max(200, this.epiphanyCycleTicks());
        long cycleTicks = cycleTicksInt;
        long nowGameTime = this.world.getGameTime();
        this.syncedCycleIndex = (int) ((nowGameTime / cycleTicks) & 0xFFFFL);
        this.syncedCountdownSeconds = (int) (((cycleTicks - Math.floorMod(nowGameTime, cycleTicks)) + 19L) / 20L);
        this.syncedFrozen = false;
        chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity initialTableBe = backingHolder[0];
        if (initialTableBe == null
            && this.syncedTablePos != null
            && this.world.hasChunkAt(this.syncedTablePos)
            && this.world.getBlockEntity(this.syncedTablePos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity clientBe) {
            initialTableBe = clientBe;
        }
        if (initialTableBe != null) {
            this.syncedFrozen = initialTableBe.isEpiphanyFrozen(nowGameTime);
            this.syncedCycleIndex = initialTableBe.activeCycleIndex(nowGameTime, cycleTicksInt);
            if (this.syncedFrozen) {
                this.syncedCountdownSeconds = (int) ((initialTableBe.remainingFreezeTicks(nowGameTime) + 19L) / 20L);
            }
            this.syncedRitualRemainingTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, initialTableBe.ritualRemainingTicks()));
            this.syncedRitualTotalTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, initialTableBe.ritualTotalTicks()));
            this.syncedRitualSession = initialTableBe.ritualSession();
            this.syncedQueuedSteps = List.copyOf(initialTableBe.queuedEnchantmentSummary());
            this.syncedUsedInfinityTome = initialTableBe.anyQueuedStepUsedInfinityTome();
            this.syncedRitualBlocked = initialTableBe.isRitualBlocked();
        }
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
        if (!isEpiphanyEnabled()) {
            return;
        }
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
        this.sacrificeRequired = Math.max(1, live.sacrifice_required());
        this.tomeRequiredShelves = Math.max(1, live.tome_page_required_shelves());
        this.qualifyingEnchantedBookshelves = Math.max(0, live.qualifying_enchanted_bookshelves());
        this.liveTotalLibraryBookshelves = Math.max(0, live.total_library_bookshelves());
        this.liveRegenTier = Math.max(0, live.regen_tier());
        this.nextRegenTierGap = Math.max(0, live.next_regen_tier_gap());
        this.regenIntervalTicks = live.regen_interval_ticks();
        this.tomeBooks = live.tome_books() == null ? List.of() : List.copyOf(live.tome_books());
        this.syncedEffectiveMasteryUnits = live.effective_mastery_units();
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

    /** Configured sacrifice requirement, capped by the criteria the advancement declares. */
    public int effectiveSacrificeRequired(net.minecraft.server.level.ServerPlayer sp) {
        int configured = Math.max(1, libraryResonanceConfig().sacrificeRequired());
        int total = chiseled_enchanting_table.utils.Advancement.totalCriteria(
            sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE);
        return total > 0 ? Math.min(configured, total) : configured;
    }

    public chiseled_enchanting_table.config.ModRuntimeConfig activeRuntimeConfig() {
        return chiseled_enchanting_table.config.ModRuntimeConfig.forSide(this.world.isClientSide());
    }

    public boolean isClassic103Mode() {
        return activeRuntimeConfig().isAllClassicAtoms();
    }

    public boolean usesClassicShelfBoost() {
        return activeRuntimeConfig().shelfBoostModel()
            == chiseled_enchanting_table.config.ModRuntimeConfig.ShelfBoostModel.CLASSIC_FLOOR_THIRD;
    }

    public boolean allowsDirectTierUpgrade() {
        return !usesClassicShelfBoost() && activeRuntimeConfig().directTierUpgrade();
    }

    public boolean usesIncrementalCredit() {
        return activeRuntimeConfig().incrementalCredit();
    }

    public boolean usesClassicFullPointXp() {
        return activeRuntimeConfig().xpMode()
            == chiseled_enchanting_table.config.ModRuntimeConfig.XpSettlementMode.CLASSIC_FULL_POINTS;
    }

    public boolean usesSaltedSingleMaterial() {
        return activeRuntimeConfig().materialSelectionMode()
            == chiseled_enchanting_table.config.ModRuntimeConfig.MaterialSelectionMode.PLAYER_SALTED_SINGLE;
    }

    public boolean usesClassicCostTable() {
        return activeRuntimeConfig().costTableDataset()
            == chiseled_enchanting_table.config.ModRuntimeConfig.CostTableDataset.CLASSIC;
    }

    public boolean isLibraryDiscountEnabled() {
        return activeRuntimeConfig().libraryDiscountEnabled();
    }

    public boolean isTomeLocksEnabled() {
        return activeRuntimeConfig().tomeLocksEnabled();
    }

    public boolean isProvenanceEnabled() {
        return activeRuntimeConfig().provenanceEnabled();
    }

    public boolean isRitualQueueEnabled() {
        return activeRuntimeConfig().ritualQueueEnabled();
    }

    public boolean isEpiphanyEnabled() {
        return activeRuntimeConfig().epiphanyEnabled();
    }

    public boolean usesClassicTransformations() {
        return activeRuntimeConfig().transformationSetMode()
            == chiseled_enchanting_table.config.ModRuntimeConfig.TransformationSetMode.CLASSIC_TWO;
    }

    public boolean returnsPlainDrainedBook() {
        return activeRuntimeConfig().drainedBookResult()
            == chiseled_enchanting_table.config.ModRuntimeConfig.DrainedBookResult.PLAIN_BOOK;
    }

    public boolean isTomePageEnabled() {
        return activeRuntimeConfig().tomePageEnabled();
    }

    public boolean hasSecondaryTabs() {
        return disenchantEnabled() || isTomePageEnabled();
    }

    public boolean isCleansePageUnlocked() {
        if (!disenchantEnabled()) {
            return false;
        }
        if (!this.world.isClientSide() && this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return chiseled_enchanting_table.utils.Advancement.completedCriteria(
                    sp, chiseled_enchanting_table.utils.Advancement.DISENCHANT_SACRIFICE)
                >= effectiveSacrificeRequired(sp);
        }
        return this.syncedCleanseUnlocked;
    }

    public boolean isTomePageUnlocked() {
        if (!isTomePageEnabled()) {
            return false;
        }
        if (!this.world.isClientSide() && this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return chiseled_enchanting_table.utils.Advancement.hasCompleted(
                sp, chiseled_enchanting_table.utils.Advancement.HOW_MUCH_LONGER);
        }
        return this.syncedTomePageUnlocked;
    }

    void tomeActionMessage(String key, Object... args) {
        if (this.player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, args), true);
        }
    }

    /** Server-authoritative handler for every Tome-page button (delegated to MenuTomePageActions, Audit COUP-06). */
    public void handleTomePageAction(ChiseledEnchantingMenuPayloads.TomePageActionPayload payload) {
        MenuTomePageActions.handleTomePageAction(this, payload);
    }

    public void refreshLiveShelfState() {
        refreshLiveShelfState(false);
    }

    public void refreshLiveShelfState(boolean forceSend) {
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
        boolean changed = forceSend
            || !live.unlocked_enchantements().equals(this.unlocked_enchantements)
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
            || Math.abs(live.effective_mastery_units() - this.syncedEffectiveMasteryUnits) > 1e-6
            || !live.tome_books().equals(this.tomeBooks);
        if (changed) {
            applyLiveShelfState(live);
            if (this.player instanceof net.minecraft.server.level.ServerPlayer sp
                && ServerPlayNetworking.canSend(sp, ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE)) {
                ServerPlayNetworking.send(sp, live);
            }
        }
    }

    private boolean initialLiveSyncSent = false;

    /**
     * Immediately pushes full live shelf state ({@code tomeBooks}, regen tier/ETAs, sacrifice
     * progress) and ritual queue state at menu-open time so the client never waits up to 20 ticks
     * for {@code world.getGameTime() % 20L == 0L}.
     */
    public void sendInitialLiveSync() {
        if (this.world.isClientSide()) {
            return;
        }
        this.initialLiveSyncSent = true;
        refreshLiveShelfState(true);
        pushRitualStateToViewer();
    }

    @Override
    public void broadcastChanges() {
        if (!this.world.isClientSide()) {
            if (!this.initialLiveSyncSent) {
                sendInitialLiveSync();
            } else if (this.world.getGameTime() % 20L == 0L) {
                refreshLiveShelfState();
                pushRitualStateToViewer();
            }
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
        refreshLiveShelfState(true);
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
            ritualRemainingTicks(),
            queuedRitualSteps().size()
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

    public boolean canFuelBoostNow() {
        return chiseled_enchanting_table.mechanics.ritual.RitualActionService.canFuelBoostNow(this);
    }

    public boolean canCatalystInstantNow() {
        return chiseled_enchanting_table.mechanics.ritual.RitualActionService.canCatalystInstantNow(this);
    }

    public boolean canSafeAbortNow() {
        return chiseled_enchanting_table.mechanics.ritual.RitualActionService.canSafeAbortNow(this);
    }

    public boolean handleRitualAction(ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action action) {
        return chiseled_enchanting_table.mechanics.ritual.RitualActionService.handleAction(this, action);
    }

    public boolean skipActiveRitual(boolean sacrificeXp) {
        return handleRitualAction(sacrificeXp
            ? ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.SACRIFICE_XP
            : ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.FORCE_RUSH);
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (!this.world.isClientSide()) {
            ItemStack enchSlot = get_enchantable_item();
            if (!enchSlot.isEmpty()
                && chiseled_enchanting_table.mechanics.resonance.TomeLockData.hasLock(enchSlot)) {
                chiseled_enchanting_table.mechanics.resonance.TomeLockData.unlock(enchSlot);
            }
            ItemStack costSlot = get_cost_item();
            if (!costSlot.isEmpty()
                && chiseled_enchanting_table.mechanics.resonance.TomeLockData.hasLock(costSlot)) {
                chiseled_enchanting_table.mechanics.resonance.TomeLockData.unlock(costSlot);
            }
            if (computeActiveTreasureEpiphanies(enchSlot).size() >= 2) {
                chiseled_enchanting_table.utils.Advancement.give(this.player, "multi_epiphany");
            }
        }
        if (this.onContentChangedCallback != null) {
            this.onContentChangedCallback.run();
        }
    }

    // ---- Slot/state accessors used by mechanics.* ----

    public Level world() {
        return this.world;
    }

    public Set<EnchantmentWithLevel> unlockedEnchantments() {
        return this.unlocked_enchantements;
    }

    public ItemStack getEnchantableItem() {
        return this.getSlot(ENCHANTABLE_SLOT).getItem();
    }

    /** @deprecated Use {@link #getEnchantableItem()} (Audit API-02). */
    @Deprecated
    public ItemStack get_enchantable_item() {
        return getEnchantableItem();
    }

    public ItemStack getCostItem() {
        return this.getSlot(COST_SLOT).getItem();
    }

    /** @deprecated Use {@link #getCostItem()} (Audit API-02). */
    @Deprecated
    public ItemStack get_cost_item() {
        return getCostItem();
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

    // ---- Cost resolution & Epiphany (delegated to MenuPricing & MenuEpiphanyController, Audit COUP-06) ----

    private static final net.minecraft.tags.TagKey<Enchantment> CURSE_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.CURSE;

    int resolvedEpiphanySeed() {
        return this.world.isClientSide() ? this.epiphanySeed : currentServerEpiphanySeed();
    }

    int resolvedEpiphanyCycleIndex() {
        return this.world.isClientSide() ? this.syncedCycleIndex : serverActiveCycleIndex();
    }

    void executeAtTable(java.util.function.BiConsumer<Level, net.minecraft.core.BlockPos> action) {
        this.access.execute(action);
    }

    int resolveEnchantmentMaxLevel(EnchantmentWithLevel e) {
        return findEnchantment(e.enchantment_id(), this.world)
            .map(h -> Math.max(1, h.value().getMaxLevel()))
            .orElseGet(() -> Math.max(1, e.enchantment_level()));
    }

    public List<List<ItemStack>> getEnchantmentItemCostOptions(EnchantmentWithLevel e, OptionType type) {
        return MenuPricing.getEnchantmentItemCostOptions(this, e, type);
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
        return MenuPricing.getBaseEnchantmentXpLevelCost(this, e, type);
    }

    public boolean disenchantEnabled() {
        if (!activeRuntimeConfig().disenchantEnabled()) {
            return false;
        }
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

    public List<List<ItemStack>> getEffectiveItemCostOptions(EnchantmentWithLevel e, OptionType type) {
        return MenuPricing.getEffectiveItemCostOptions(this, e, type);
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
        return holder[0] != null ? holder[0] : this.syncedTablePos;
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

    public int countAvailableInInventory(ItemStack required) { return MenuPaymentService.countAvailableInInventory(this.paymentAccess, required); }

    public int countAvailableIncludingCostSlot(ItemStack required) { return MenuPaymentService.countAvailableIncludingCostSlot(this.paymentAccess, required); }

    public int resolveTransformationBatchCount(Identifier transformationId) {
        return MenuPricing.resolveTransformationBatchCount(this, transformationId);
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
        if (!allowsDirectTierUpgrade()
            || existingLevel < 1
            || targetLevel <= existingLevel
            || holder == null
            || holder.is(CURSE_TAG)) {
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
        if (usesClassicShelfBoost()) {
            return targetLevel >= 1 && targetLevel <= maxLvl;
        }
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
        double rawDeduplicated = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.totalMasteryUnits(
            libraryResonanceConfig(),
            this.world,
            this.storedMasterLevels
        );
        if (this.syncedEffectiveMasteryUnits >= 0.0) {
            return Math.min(rawDeduplicated, this.syncedEffectiveMasteryUnits);
        }
        return rawDeduplicated;
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
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return bookCombineXpRequirement();
        }
        return resolveEffectiveXpRequirement(e, type);
    }

    public XpCostConversion.XpRequirement bookCombineXpRequirement() {
        return XpCostConversion.combineSequential(MenuPricing.collectBookCombineEntryRequirements(this));
    }

    public int syncedTransformationDeductedLevels(Identifier id) {
        TransformationDisplay display = this.transformationDisplays.get(id);
        return display != null
            ? display.deducted_levels()
            : ChiseledEnchantingTableData.transformationDeductedLevels(id);
    }

    public XpCostConversion.XpRequirement resolveEffectiveXpRequirement(EnchantmentWithLevel e, OptionType type) {
        boolean applyUpgradeRouteRawCap =
            (type == OptionType.BOOK_RESEARCH_UPGRADE || type == OptionType.BOOK_COMBINE_UPGRADE);
        return MenuPricing.resolveEffectiveXpRequirement(this, e, type, applyUpgradeRouteRawCap);
    }

    public int getEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) {
        return resolveXpRequirement(e, type).requiredLevel();
    }

    public int epiphanyMinBookshelves() {
        return MenuEpiphanyController.epiphanyMinBookshelves(this);
    }

    public double epiphanyChance() {
        return epiphanyChance(getEnchantableItem());
    }

    public double epiphanyChance(ItemStack targetStack) {
        return MenuEpiphanyController.epiphanyChance(this, targetStack);
    }

    public int epiphanyMaxSlots() {
        return epiphanyMaxSlots(getEnchantableItem());
    }

    public int epiphanyMaxSlots(ItemStack targetStack) {
        return MenuEpiphanyController.epiphanyMaxSlots(this, targetStack);
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
        return MenuEpiphanyController.isTreasureEnchantment(this, holder);
    }

    public List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(ItemStack targetStack) {
        return MenuEpiphanyController.computeActiveTreasureEpiphanies(this, targetStack);
    }

    public boolean matchesActiveTreasureEpiphany(ApplyEnchantmentPayload payload) {
        return MenuEpiphanyController.matchesActiveTreasureEpiphany(this, payload);
    }

    public boolean canAffordRerollEpiphany() {
        return MenuEpiphanyController.canAffordRerollEpiphany(this);
    }

    public boolean canAffordFreezeEpiphany() {
        return MenuEpiphanyController.canAffordFreezeEpiphany(this);
    }

    public boolean freezeEpiphany() {
        return MenuEpiphanyController.freezeEpiphany(this);
    }

    public boolean rerollEpiphany() {
        return MenuEpiphanyController.rerollEpiphany(this);
    }

    public static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Level world) { return MenuEnchantmentLookup.findEnchantment(id, world); }

    public static Holder<Enchantment> identifierToEnchantment(Identifier id, Level world) {
        return findEnchantment(id, world)
            .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + id));
    }

    /** @deprecated Use {@link #identifierToEnchantment(Identifier, Level)} (Audit API-02). */
    @Deprecated
    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Level world) {
        return identifierToEnchantment(id, world);
    }

    public static Identifier enchantmentToIdentifier(Holder<Enchantment> enchant) {
        return enchant.unwrapKey()
            .orElseThrow(() -> new IllegalArgumentException("Unbound enchantment holder: " + enchant))
            .identifier();
    }

    /** @deprecated Use {@link #enchantmentToIdentifier(Holder)} (Audit API-02). */
    @Deprecated
    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant) {
        return enchantmentToIdentifier(enchant);
    }

    /** @deprecated Use {@link XpCostConversion#totalPointsForLevel(int)}. */
    @Deprecated
    public int calculateXpCostToLevel(int X) {
        return XpCostConversion.totalXpForLevel(X);
    }

    // ---- Condition / consumption (shared by both mechanics) ----



    public boolean isBundleAffordableWithOverflow(List<ItemStack> bundle) { return MenuPaymentService.isBundleAffordableWithOverflow(this.paymentAccess, bundle); }

    public boolean isCleanseBundleAffordableFromBagOrSlot(List<ItemStack> bundle) {
        return MenuCleanseQuotes.isCleanseBundleAffordableFromBagOrSlot(this.paymentAccess, bundle);
    }

    @org.jetbrains.annotations.Nullable
    public List<ItemStack> resolveCleanseCostBundle(EnchantmentWithLevel ewl, OptionType type, int costOptionIndex) {
        return MenuCleanseQuotes.resolveCleanseCostBundle(this.paymentAccess, ewl, type, costOptionIndex);
    }

    public void deductCleanseBundle(List<ItemStack> matchedBundle) {
        MenuCleanseQuotes.deductCleanseBundle(this.paymentAccess, matchedBundle);
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

    public List<ItemStack> findMatchingAffordableCostBundle(EnchantmentWithLevel ewl, OptionType type) { return MenuPaymentService.findMatchingAffordableCostBundle(this.paymentAccess, ewl, type); }

    public void deductMatchedBundle(List<ItemStack> matchedBundle) { MenuPaymentService.deductMatchedBundle(this.paymentAccess, matchedBundle); }

    /** Exact survival payment receipt used for 50% cross-line pollution compensation. */
    public record PaymentReceipt(boolean accepted, int paidXpPoints, List<ItemStack> paidMaterials) {}

    public boolean applyEnchantmentCondition(ApplyEnchantmentPayload payload) {
        return applyEnchantmentConditionWithReceipt(payload).accepted();
    }

    /** @deprecated Use {@link #applyEnchantmentCondition(ApplyEnchantmentPayload)} (Audit API-01). */
    @Deprecated
    public boolean applyEnchantementCondition(ApplyEnchantmentPayload payload) {
        return applyEnchantmentCondition(payload);
    }

    /** @deprecated Use {@link #applyEnchantmentConditionWithReceipt(ApplyEnchantmentPayload)} (Audit API-01). */
    @Deprecated
    public PaymentReceipt applyEnchantementConditionWithReceipt(ApplyEnchantmentPayload payload) {
        return applyEnchantmentConditionWithReceipt(payload);
    }

    public PaymentReceipt applyEnchantmentConditionWithReceipt(ApplyEnchantmentPayload payload) { return MenuPaymentService.applyEnchantmentConditionWithReceipt(this.paymentAccess, payload); }

    // ---- Dispatch ----

    /** @deprecated Use {@link #applyEnchantment(ApplyEnchantmentPayload)} (Audit API-01). */
    @Deprecated
    public void applyEnchantement(ApplyEnchantmentPayload payload) {
        applyEnchantment(payload);
    }

    public void applyEnchantment(ApplyEnchantmentPayload payload) {
        refreshLiveShelfState();
        if (isTomeLocksEnabled()
            && (chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(get_enchantable_item())
                || chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(get_cost_item()))) {
            tomeActionMessage("message.chiseled_enchanting_table.tome_lock.locked_input");
            return;
        }
        boolean cleanseAction = payload.option_type() == OptionType.DISENCHANT_ALL
            || payload.option_type() == OptionType.DOWNGRADE_ENCHANTMENT
            || AvailableOption.isBagPayableCleanse(payload.option_type());
        if (cleanseAction && !isCleansePageUnlocked()) {
            tomeActionMessage("gui.chiseled_enchanting_table.tab.locked_tooltip");
            return;
        }
        if (cleanseAction) {
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
        // Never run clearContainer on the client (where persistentBlockInventory == false),
        // because external viewer screens (JEI / REI) invoke Screen#removed() on setScreen().
        if (!this.persistentBlockInventory && !player.level().isClientSide()) {
            this.clearContainer(player, this.inventory);
        }
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (!MenuQuickMove.handle(this.quickMoveAccess, this.player, slotIndex, actionType, player)) {
            super.clicked(slotIndex, button, actionType, player);
        }
    }

    private final MenuQuickMove.Access quickMoveAccess = new MenuQuickMove.Access() {
        public int slotCount() { return slots.size(); }
        public Slot slot(int index) { return getSlot(index); }
        public boolean ritualActive() { return isRitualActive(); }
        public boolean hasTransformation(ItemStack stack) { return !syncedTransformationsFor(stack).isEmpty(); }
        public void insertCost(ItemStack stack) { moveItemStackTo(stack, COST_SLOT, COST_SLOT + 1, false); }
        public void changed() {
            inventory.setChanged();
            ChiseledEnchantingTableScreenHandler.this.player.getInventory().setChanged();
        }
    };

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, BlockRegistry.CHISELED_ENCHANTING_TABLE);
    }

    private final MenuPaymentService.Access paymentAccess = new MenuPaymentService.Access() {
        public Player player() { return ChiseledEnchantingTableScreenHandler.this.player; }
        public ItemStack costSlot() { return getCostItem(); }
        public void tableChanged() { markInventoryChanged(); }
        public int availableUnits(List<ItemStack> bundle) { return MenuPricing.maxBundleUnitsAvailable(ChiseledEnchantingTableScreenHandler.this, bundle); }
        public List<List<ItemStack>> costOptions(EnchantmentWithLevel enchantment, OptionType type) { return getEffectiveItemCostOptions(enchantment, type); }
        public boolean materialFree(EnchantmentWithLevel enchantment, OptionType type) { return isMaterialFreeOperation(enchantment, type); }
        public XpCostConversion.XpRequirement xpRequirement(EnchantmentWithLevel enchantment, OptionType type) { return resolveXpRequirement(enchantment, type); }
    };

    private final MenuPricingAccess pricingAccess = new MenuPricingAccess() {
        public net.minecraft.world.level.Level world() { return ChiseledEnchantingTableScreenHandler.this.world(); }
        public net.minecraft.world.item.ItemStack getEnchantableItem() { return ChiseledEnchantingTableScreenHandler.this.getEnchantableItem(); }
        public net.minecraft.world.item.ItemStack getCostItem() { return ChiseledEnchantingTableScreenHandler.this.getCostItem(); }
        public int regularBookshelves() { return ChiseledEnchantingTableScreenHandler.this.regularBookshelves; }
        public int xpDiscountPercent() { return ChiseledEnchantingTableScreenHandler.this.xpDiscountPercent; }
        public String playerUuid() { return ChiseledEnchantingTableScreenHandler.this.player.getStringUUID(); }
        public boolean creative() { return ChiseledEnchantingTableScreenHandler.this.player != null && ChiseledEnchantingTableScreenHandler.this.player.getAbilities().instabuild; }
        public int playerLevel() { return ChiseledEnchantingTableScreenHandler.this.player != null ? ChiseledEnchantingTableScreenHandler.this.player.experienceLevel : 0; }
        public int resolveEnchantmentMaxLevel(EnchantmentWithLevel e) { return ChiseledEnchantingTableScreenHandler.this.resolveEnchantmentMaxLevel(e); }
        public boolean usesSaltedSingleMaterial() { return ChiseledEnchantingTableScreenHandler.this.usesSaltedSingleMaterial(); }
        public boolean usesClassicTransformations() { return ChiseledEnchantingTableScreenHandler.this.usesClassicTransformations(); }
        public boolean usesIncrementalCredit() { return ChiseledEnchantingTableScreenHandler.this.usesIncrementalCredit(); }
        public boolean usesClassicFullPointXp() { return ChiseledEnchantingTableScreenHandler.this.usesClassicFullPointXp(); }
        public BookshelfProgression.Config shelfGatesConfig() { return ChiseledEnchantingTableScreenHandler.this.shelfGatesConfig(); }
        public LibraryResonance.Config libraryResonanceConfig() { return ChiseledEnchantingTableScreenHandler.this.libraryResonanceConfig(); }
        public BookshelfProgression.DepletedTomeTier depletedTomeTier() { return ChiseledEnchantingTableScreenHandler.this.depletedTomeTier(); }
        public int depletedTomeMaxBatchSize() { return ChiseledEnchantingTableScreenHandler.this.depletedTomeMaxBatchSize(); }
        public int transformationMaxBatchSize() { return ChiseledEnchantingTableScreenHandler.this.transformationMaxBatchSize(); }
        public int currentTargetEnchantLevel(Identifier id) { return ChiseledEnchantingTableScreenHandler.this.currentTargetEnchantLevel(id); }
        public int storedMasterLevel(Identifier id) { return ChiseledEnchantingTableScreenHandler.this.storedMasterLevel(id); }
        public int countAvailableIncludingCostSlot(ItemStack stack) { return ChiseledEnchantingTableScreenHandler.this.countAvailableIncludingCostSlot(stack); }
        public int countAvailableInInventory(ItemStack stack) { return ChiseledEnchantingTableScreenHandler.this.countAvailableInInventory(stack); }
        public int syncedTransformationLevel(Identifier id) { return ChiseledEnchantingTableScreenHandler.this.syncedTransformationLevel(id); }
        public int syncedTransformationMinPlayerLevel(Identifier id) { return ChiseledEnchantingTableScreenHandler.this.syncedTransformationMinPlayerLevel(id); }
        public int syncedTransformationDeductedLevels(Identifier id) { return ChiseledEnchantingTableScreenHandler.this.syncedTransformationDeductedLevels(id); }
        public int effectiveXpDiscountPercent(Identifier id, OptionType type) { return ChiseledEnchantingTableScreenHandler.this.effectiveXpDiscountPercent(id, type); }
        public double disenchantRefundScale() { return ChiseledEnchantingTableScreenHandler.this.disenchantRefundScale(); }
        public double disenchantExternalRefundScale() { return ChiseledEnchantingTableScreenHandler.this.disenchantExternalRefundScale(); }
        public boolean isMasterTomeBacked(EnchantmentWithLevel e, OptionType type) { return ChiseledEnchantingTableScreenHandler.this.isMasterTomeBacked(e, type); }
    };
    MenuPricingAccess pricingAccess() { return this.pricingAccess; }
}
