package chiseled_enchanting_table.mechanics.ritual;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.TomeWearUndo;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.PaidItemEntry;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import chiseled_enchanting_table.utils.InventoryUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/**
 * Smart ritual gating and active-ritual console actions:
 * <ul>
 *   <li>Single-step regular-bookshelf non-capstone inscriptions complete instantly ({@code 0s}),
 *       while Master Tome copies, Treasure Epiphanies, {@code maxLevel >= 3} capstones, and queued
 *       multi-step chains enter the deep-resonance ritual.</li>
 *   <li>{@code FUEL_BOOST}: {@code 1 Depleted Tome + 1 Cleansing Grit} reduces remaining ritual
 *       wait by {@code 60s} ({@code 1,200} ticks).</li>
 *   <li>{@code CATALYST_INSTANT}: {@code 1 Arcane Catalyst} completes the entire ritual queue
 *       instantly with {@code 0%} Master Tome degrade risk and {@code 0%} slip/crossline risk.</li>
 *   <li>{@code SAFE_ABORT}: {@code 100%} refunds paid XP points, paid material stacks, and any
 *       unfinalized Master Tome shelf wear still matching its post-wear shelf snapshot.</li>
 * </ul>
 */
public final class RitualActionService {
    public static final int FUEL_BOOST_SECONDS = 60;
    public static final long FUEL_BOOST_TICKS = FUEL_BOOST_SECONDS * 20L;

    private RitualActionService() {}

    /**
     * Determines whether a single inscription bypasses ritual waiting ({@code 0s} instant):
     * single-step regular-bookshelf direct inscriptions that are neither Master-Tome-backed, nor
     * Treasure Epiphanies, nor {@code maxLevel >= 3} final-tier capstones complete immediately.
     */
    public static boolean shouldBypassRitualWait(
        boolean ritualQueueEnabled,
        boolean tableHasActiveRitual,
        boolean masterTomeUsed,
        OptionType optionType,
        int targetLevel,
        int maxLevel
    ) {
        if (!ritualQueueEnabled) {
            return true;
        }
        if (tableHasActiveRitual || masterTomeUsed || optionType == OptionType.TREASURE_EPIPHANY) {
            return false;
        }
        boolean isCapstone = maxLevel >= 3 && targetLevel >= maxLevel;
        return !isCapstone;
    }

    public static int countItemInSlotAndBag(ChiseledEnchantingTableScreenHandler h, Item item) {
        if (h == null || h.player == null || item == null) return 0;
        int total = 0;
        ItemStack cost = h.getCostItem();
        if (!cost.isEmpty() && cost.is(item)) {
            total += cost.getCount();
        }
        for (int slot = 0; slot < h.player.getInventory().getContainerSize(); slot++) {
            ItemStack st = h.player.getInventory().getItem(slot);
            if (!st.isEmpty() && st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
    }

    public static int countDepletedTomesAvailable(ChiseledEnchantingTableScreenHandler h, boolean includeShelves) {
        if (h == null || h.player == null) return 0;
        int total = countItemInSlotAndBag(h, BlockRegistry.DEPLETED_TOME);
        ItemStack mainSlot = h.getEnchantableItem();
        if (!h.isRitualActive() && !mainSlot.isEmpty() && mainSlot.is(BlockRegistry.DEPLETED_TOME)) {
            total += mainSlot.getCount();
        }
        if (includeShelves && !h.world().isClientSide() && h.tableBlockPos() != null) {
            var cfg = h.shelfGatesConfig();
            BlockPos tablePos = h.tableBlockPos();
            for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
                BlockPos pos = tablePos.offset(offset);
                if (!h.world().hasChunkAt(pos)) continue;
                if (!(h.world().getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
                for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                    ItemStack st = shelf.getItem(slot);
                    if (st.is(BlockRegistry.DEPLETED_TOME)) {
                        total += st.getCount();
                    }
                }
            }
        }
        return total;
    }

    public static boolean consumeFromCostSlotThenBag(ChiseledEnchantingTableScreenHandler h, Item item, int count) {
        if (count <= 0) return true;
        if (countItemInSlotAndBag(h, item) < count) return false;
        int rem = count;
        ItemStack cost = h.getCostItem();
        if (!cost.isEmpty() && cost.is(item)) {
            int take = Math.min(rem, cost.getCount());
            cost.shrink(take);
            rem -= take;
            h.markInventoryChanged();
        }
        for (int slot = 0; slot < h.player.getInventory().getContainerSize() && rem > 0; slot++) {
            ItemStack st = h.player.getInventory().getItem(slot);
            if (!st.isEmpty() && st.is(item)) {
                int take = Math.min(rem, st.getCount());
                st.shrink(take);
                rem -= take;
            }
        }
        h.player.getInventory().setChanged();
        return rem == 0;
    }

    /** Consumes {@code count} Depleted Tomes in order: Table Slots -> Player Inventory -> Nearby Chiseled Bookshelves. */
    public static boolean consumeDepletedTomesBagFirst(
        ChiseledEnchantingTableScreenHandler h,
        int count,
        boolean allowMainSlot
    ) {
        if (count <= 0) return true;
        int available = countItemInSlotAndBag(h, BlockRegistry.DEPLETED_TOME)
            + ((allowMainSlot && !h.isRitualActive() && h.getEnchantableItem().is(BlockRegistry.DEPLETED_TOME))
                ? h.getEnchantableItem().getCount()
                : 0);
        BlockPos tablePos = h.tableBlockPos();
        if (available < count && tablePos != null && !h.world().isClientSide()) {
            available = countDepletedTomesAvailable(h, true);
        }
        if (available < count) return false;

        int rem = count;
        if (allowMainSlot && !h.isRitualActive()) {
            ItemStack main = h.getEnchantableItem();
            if (!main.isEmpty() && main.is(BlockRegistry.DEPLETED_TOME)) {
                int take = Math.min(rem, main.getCount());
                main.shrink(take);
                rem -= take;
                h.markInventoryChanged();
            }
        }
        ItemStack cost = h.getCostItem();
        if (rem > 0 && !cost.isEmpty() && cost.is(BlockRegistry.DEPLETED_TOME)) {
            int take = Math.min(rem, cost.getCount());
            cost.shrink(take);
            rem -= take;
            h.markInventoryChanged();
        }
        for (int slot = 0; slot < h.player.getInventory().getContainerSize() && rem > 0; slot++) {
            ItemStack st = h.player.getInventory().getItem(slot);
            if (!st.isEmpty() && st.is(BlockRegistry.DEPLETED_TOME)) {
                int take = Math.min(rem, st.getCount());
                st.shrink(take);
                rem -= take;
            }
        }
        if (rem > 0 && tablePos != null && !h.world().isClientSide()) {
            var cfg = h.shelfGatesConfig();
            for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
                if (rem <= 0) break;
                BlockPos pos = tablePos.offset(offset);
                if (!h.world().hasChunkAt(pos)) continue;
                if (!(h.world().getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
                for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE && rem > 0; slot++) {
                    ItemStack st = shelf.getItem(slot);
                    if (!st.is(BlockRegistry.DEPLETED_TOME)) continue;
                    int take = Math.min(rem, st.getCount());
                    ItemStack updated = st.copy();
                    updated.shrink(take);
                    shelf.setItem(slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
                    shelf.setChanged();
                    var state = h.world().getBlockState(pos);
                    h.world().sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                    rem -= take;
                }
            }
        }
        h.player.getInventory().setChanged();
        return rem == 0;
    }

    public static boolean canFuelBoostNow(ChiseledEnchantingTableScreenHandler h) {
        if (!h.isRitualActive() || h.isRitualBlocked()) return false;
        if (h.player.getAbilities().instabuild) return true;
        return countDepletedTomesAvailable(h, !h.world().isClientSide()) >= 1
            && countItemInSlotAndBag(h, BlockRegistry.CLEANSING_GRIT) >= 1;
    }

    public static boolean canCatalystInstantNow(ChiseledEnchantingTableScreenHandler h) {
        if (!h.isRitualActive() || h.isRitualBlocked()) return false;
        if (h.player.getAbilities().instabuild) return true;
        return countItemInSlotAndBag(h, BlockRegistry.ARCANE_CATALYST) >= 1;
    }

    public static boolean canSafeAbortNow(ChiseledEnchantingTableScreenHandler h) {
        return h.isRitualActive() && !h.isRitualBlocked();
    }

    public static boolean handleAction(ChiseledEnchantingTableScreenHandler h, SkipRitualPayload.Action action) {
        if (h.world().isClientSide() || action == null) {
            return false;
        }
        h.refreshLiveShelfState();
        BlockPos pos = h.tableBlockPos();
        if (pos == null
            || !(h.world().getBlockEntity(pos) instanceof ChiseledEnchantingTableBlockEntity tableBe)
            || !tableBe.hasActiveRitual()
            || tableBe.isRitualBlocked()
            || !tableBe.ownsRitual(h.player.getUUID())) {
            h.pushRitualStateToViewer();
            return false;
        }
        ServerPlayer sp = h.player instanceof ServerPlayer s ? s : null;
        return switch (action) {
            case SACRIFICE_XP -> handleSacrificeXp(h, pos, tableBe, sp);
            case FUEL_BOOST -> handleFuelBoost(h, pos, tableBe, sp);
            case CATALYST_INSTANT -> handleCatalystInstant(h, pos, tableBe, sp);
            case FORCE_RUSH -> handleForceRush(h, pos, tableBe, sp);
            case SAFE_ABORT -> handleSafeAbort(h, pos, tableBe, sp);
        };
    }

    private static boolean handleSacrificeXp(
        ChiseledEnchantingTableScreenHandler h,
        BlockPos pos,
        ChiseledEnchantingTableBlockEntity tableBe,
        ServerPlayer sp
    ) {
        int levelsCost = InscriptionRitualMechanics.computeSacrificeXpLevels(
            h.ritualConfig(),
            tableBe.ritualRemainingTicks(),
            h.queuedRitualSteps().size()
        );
        if (!h.player.getAbilities().instabuild && h.player.experienceLevel < levelsCost) {
            h.world().playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
            return false;
        }
        if (!tableBe.completeActiveRitual(h.world(), pos, sp, RitualCompletionKind.SACRIFICED_XP)) {
            return false;
        }
        if (!h.player.getAbilities().instabuild) {
            h.player.giveExperienceLevels(-levelsCost);
        }
        Advancement.give(h.player, "soul_fed_ink");
        h.world().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.85F);
        chiseled_enchanting_table.menu.TableVisualEvents.onSacrificePages(h.world(), pos);
        return true;
    }

    private static boolean handleFuelBoost(
        ChiseledEnchantingTableScreenHandler h,
        BlockPos pos,
        ChiseledEnchantingTableBlockEntity tableBe,
        ServerPlayer sp
    ) {
        if (!h.player.getAbilities().instabuild) {
            if (countDepletedTomesAvailable(h, true) < 1
                || countItemInSlotAndBag(h, BlockRegistry.CLEANSING_GRIT) < 1) {
                if (sp != null) {
                    sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.fuel_missing")
                        .withStyle(ChatFormatting.RED));
                }
                h.world().playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
                return false;
            }
            if (!consumeDepletedTomesBagFirst(h, 1, false)
                || !consumeFromCostSlotThenBag(h, BlockRegistry.CLEANSING_GRIT, 1)) {
                return false;
            }
        }
        long remAfter = tableBe.reduceRitualRemainingTicks(FUEL_BOOST_TICKS);
        if (remAfter <= 0L) {
            if (!tableBe.completeActiveRitual(h.world(), pos, sp, RitualCompletionKind.NATURAL)) {
                return false;
            }
            if (sp != null) {
                sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.fuel_completed")
                    .withStyle(ChatFormatting.GREEN));
            }
        } else {
            h.world().playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.9F, 1.25F);
            h.world().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
            if (sp != null) {
                sendActionBar(sp, Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.fuel_boosted",
                    FUEL_BOOST_SECONDS
                ).withStyle(ChatFormatting.AQUA));
            }
        }
        chiseled_enchanting_table.menu.TableVisualEvents.onSacrificePages(h.world(), pos);
        chiseled_enchanting_table.menu.TableVisualEvents.onRitualPulse(
            h.world(), pos, chiseled_enchanting_table.menu.TableVisualEvents.COLOR_JADE_MINT
        );
        h.refreshLiveShelfState(true);
        h.pushRitualStateToViewer();
        h.broadcastChanges();
        return true;
    }

    private static boolean handleCatalystInstant(
        ChiseledEnchantingTableScreenHandler h,
        BlockPos pos,
        ChiseledEnchantingTableBlockEntity tableBe,
        ServerPlayer sp
    ) {
        if (!h.player.getAbilities().instabuild) {
            if (!consumeFromCostSlotThenBag(h, BlockRegistry.ARCANE_CATALYST, 1)) {
                if (sp != null) {
                    sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.catalyst_missing")
                        .withStyle(ChatFormatting.RED));
                }
                h.world().playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
                return false;
            }
        }
        if (!tableBe.completeActiveRitual(h.world(), pos, sp, RitualCompletionKind.CATALYST_PERFECT)) {
            return false;
        }
        h.world().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9F, 1.35F);
        chiseled_enchanting_table.menu.TableVisualEvents.onRitualPulse(
            h.world(), pos, chiseled_enchanting_table.menu.TableVisualEvents.COLOR_ARCANE_VIOLET
        );
        if (sp != null) {
            sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.catalyst_completed")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        h.refreshLiveShelfState(true);
        h.pushRitualStateToViewer();
        h.broadcastChanges();
        return true;
    }

    private static boolean handleForceRush(
        ChiseledEnchantingTableScreenHandler h,
        BlockPos pos,
        ChiseledEnchantingTableBlockEntity tableBe,
        ServerPlayer sp
    ) {
        var scan = InscriptionRitualMechanics.scanLibraryRitualContext(h.world(), pos, h.shelfGatesConfig());
        if (!scan.hasAnyMasterTome()) {
            if (sp != null) {
                sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                    .withStyle(ChatFormatting.RED));
            }
            h.world().playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
            h.pushRitualStateToViewer();
            return false;
        }
        long remBeforeSkip = tableBe.ritualRemainingTicks();
        boolean usedInfTome = tableBe.anyQueuedStepUsedInfinityTome();
        Set<Identifier> activeRitualEnchantIds = new HashSet<>();
        for (EnchantmentWithLevel step : h.queuedRitualSteps()) {
            if (step != null && step.enchantment_id() != null) {
                activeRitualEnchantIds.add(step.enchantment_id());
            }
        }
        double degradeProb = InscriptionRitualMechanics.computeRushDegradeChance(
            h.ritualConfig(),
            remBeforeSkip,
            usedInfTome
        );
        if (!tableBe.completeActiveRitual(h.world(), pos, sp, RitualCompletionKind.RUSHED_NO_SACRIFICE)) {
            return false;
        }
        if (h.world().getRandom().nextDouble() < degradeProb) {
            ItemStack degradedTome = InscriptionRitualMechanics.degradeLowestPriorityMasterTomeInWorld(
                h.world(),
                pos,
                h.shelfGatesConfig(),
                activeRitualEnchantIds
            );
            if (degradedTome != null) {
                Advancement.give(h.player, "haste_makes_waste");
                if (sp != null) {
                    sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.degraded_to_remnant")
                        .withStyle(ChatFormatting.RED));
                }
                h.refreshLiveShelfState(true);
                h.pushRitualStateToViewer();
                h.broadcastChanges();
            }
        } else if (sp != null) {
            sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.rush_lucky")
                .withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    private static boolean handleSafeAbort(
        ChiseledEnchantingTableScreenHandler h,
        BlockPos pos,
        ChiseledEnchantingTableBlockEntity tableBe,
        ServerPlayer sp
    ) {
        List<QueuedInscription> abortedSteps = tableBe.queuedInscriptions();
        List<TomeWearUndo> tomeUndos = tableBe.queuedTomeWearUndos();
        if (!tableBe.cancelRitual(h.player.getUUID())) {
            return false;
        }
        long xpRefund = 0L;
        if (!h.player.getAbilities().instabuild) {
            for (QueuedInscription step : abortedSteps) {
                xpRefund += Math.max(0, step.paidXpPoints());
                for (PaidItemEntry entry : step.paidItems()) {
                    Item item = BuiltInRegistries.ITEM.getOptional(entry.itemId()).orElse(Items.AIR);
                    if (item == Items.AIR || entry.count() <= 0) continue;
                    int rem = entry.count();
                    int maxStack = Math.max(1, item.getDefaultMaxStackSize());
                    while (rem > 0) {
                        int chunk = Math.min(rem, maxStack);
                        InventoryUtils.insertItem(h.player, new ItemStack(item, chunk), Optional.empty());
                        rem -= chunk;
                    }
                }
            }
            if (xpRefund > 0L) {
                h.player.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, xpRefund));
            }
            h.player.getInventory().setChanged();
        }
        // Roll back unfinalized Master Tome wear in LIFO order ONLY when the shelf slot still
        // holds the exact post-wear stack (prevents dupes if the player removed the book mid-ritual).
        for (int i = tomeUndos.size() - 1; i >= 0; i--) {
            TomeWearUndo undo = tomeUndos.get(i);
            if (undo == null || undo.shelfPos() == null || !h.world().hasChunkAt(undo.shelfPos())) continue;
            if (!(h.world().getBlockEntity(undo.shelfPos()) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            if (undo.slot() < 0 || undo.slot() >= ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE) continue;
            ItemStack live = shelf.getItem(undo.slot());
            if (!live.isEmpty() && ItemStack.isSameItemSameComponents(live, undo.postWear())) {
                shelf.setItem(undo.slot(), undo.preWear().copy());
                shelf.setChanged();
                var st = h.world().getBlockState(undo.shelfPos());
                h.world().sendBlockUpdated(undo.shelfPos(), st, st, Block.UPDATE_ALL);
                chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects.endRodAt(
                    h.world(), undo.shelfPos(), undo.slot()
                );
                chiseled_enchanting_table.menu.TableVisualEvents.onTomeRestore(
                    h.world(), pos, undo.shelfPos(), undo.slot(),
                    chiseled_enchanting_table.menu.TableVisualEvents.COLOR_SACRED_GOLD
                );
            }
        }
        h.world().playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.9F, 1.15F);
        if (sp != null) {
            sendActionBar(sp, Component.translatable("gui.chiseled_enchanting_table.ritual.safe_aborted")
                .withStyle(ChatFormatting.YELLOW));
        }
        h.markInventoryChanged();
        h.refreshLiveShelfState(true);
        h.pushRitualStateToViewer();
        h.broadcastChanges();
        ChiseledEnchantingTableBlockEntity.syncOpenMenusWatchingTable(h.world(), pos);
        return true;
    }

    private static void sendActionBar(ServerPlayer sp, Component message) {
        sp.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}
