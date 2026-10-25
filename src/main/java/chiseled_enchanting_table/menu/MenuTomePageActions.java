package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects;
import chiseled_enchanting_table.mechanics.resonance.TomeWearData;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.Advancement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/**
 * Server-authoritative Tome-page &amp; Depleted-Tome sacrifice collaborator extracted from
 * {@link ChiseledEnchantingTableScreenHandler} (Audit COUP-06).
 */
public final class MenuTomePageActions {
    private MenuTomePageActions() {}

    static boolean shelfScanBlockedByBreathingRoom(ChiseledEnchantingTableScreenHandler h, BlockPos tablePos) {
        var cfg = h.shelfGatesConfig();
        if (cfg == null || !cfg.requireBreathingRoom()) return false;
        boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(h.world(), tablePos);
        int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(h.world(), tablePos);
        return BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, cfg) <= 0;
    }

    static boolean isShelfInScope(ChiseledEnchantingTableScreenHandler h, BlockPos tablePos, BlockPos shelfPos) {
        if (tablePos == null || shelfPos == null || shelfScanBlockedByBreathingRoom(h, tablePos)) return false;
        var cfg = h.shelfGatesConfig();
        BlockPos delta = shelfPos.subtract(tablePos);
        if (!ChiseledEnchantingTableBlock.getScanOffsets(cfg).contains(delta)) {
            return false;
        }
        if (!h.world().hasChunkAt(shelfPos)) return false;
        return cfg == null || !cfg.requireLineOfSight()
            || ChiseledEnchantingTableBlock.hasClearLineOfSight(h.world(), tablePos, shelfPos);
    }

    static List<BlockPos> scopedShelfPositions(ChiseledEnchantingTableScreenHandler h, BlockPos tablePos) {
        List<BlockPos> out = new ArrayList<>();
        if (tablePos == null || shelfScanBlockedByBreathingRoom(h, tablePos)) return out;
        var cfg = h.shelfGatesConfig();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
            BlockPos pos = tablePos.offset(offset);
            if (isShelfInScope(h, tablePos, pos)
                && h.world().getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity) {
                out.add(pos.immutable());
            }
        }
        return out;
    }

    static List<BlockPos> sacrificeShelfPositions(ChiseledEnchantingTableScreenHandler h, BlockPos tablePos) {
        List<BlockPos> out = new ArrayList<>();
        if (tablePos == null) return out;
        var cfg = h.shelfGatesConfig();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
            BlockPos pos = tablePos.offset(offset);
            if (h.world().hasChunkAt(pos)
                && h.world().getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity) {
                out.add(pos.immutable());
            }
        }
        return out;
    }

    private static void writeShelfStack(
        ChiseledEnchantingTableScreenHandler h,
        ChiseledBookShelfBlockEntity shelf,
        BlockPos shelfPos,
        int slot,
        ItemStack stack
    ) {
        shelf.setItem(slot, stack);
        shelf.setChanged();
        var state = h.world().getBlockState(shelfPos);
        h.world().sendBlockUpdated(shelfPos, state, state, Block.UPDATE_ALL);
    }

    private static boolean consumeOneCleansingGrit(ChiseledEnchantingTableScreenHandler h) {
        for (int slot = 0; slot < h.player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = h.player.getInventory().getItem(slot);
            if (candidate.is(BlockRegistry.CLEANSING_GRIT)) {
                candidate.shrink(1);
                h.player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static void handleTomePageAction(
        ChiseledEnchantingTableScreenHandler h,
        ChiseledEnchantingMenuPayloads.TomePageActionPayload payload
    ) {
        if (h.world().isClientSide() || !(h.player instanceof ServerPlayer sp)) return;
        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.SACRIFICE_DEPLETED) {
            if (!h.disenchantEnabled() && !h.isTomePageEnabled()) {
                return;
            }
        } else if (!h.isTomePageEnabled()) {
            return;
        }
        h.refreshLiveShelfState();
        BlockPos tablePos = h.tableBlockPos();
        if (tablePos == null) return;

        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.SACRIFICE_DEPLETED) {
            sacrificeDepletedTomes(h, sp, tablePos);
            h.refreshLiveShelfState();
            return;
        }
        if (!h.isTomePageUnlocked()) {
            h.tomeActionMessage("gui.chiseled_enchanting_table.tab.locked_tooltip");
            return;
        }
        if (payload.action() != ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK
            && !h.isTomeLocksEnabled()) {
            return;
        }
        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL) {
            int locked = lockAllChargingTomes(h, sp, tablePos);
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.batch", locked);
            h.refreshLiveShelfState();
            return;
        }
        if (!isShelfInScope(h, tablePos, payload.shelf_pos())
            || !(h.world().getBlockEntity(payload.shelf_pos()) instanceof ChiseledBookShelfBlockEntity shelf)) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        int slot = payload.slot();
        ItemStack current = shelf.getItem(slot);
        if (!current.is(Items.ENCHANTED_BOOK)) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        ItemStack updated = current;
        var expired = TomeLockData.clearExpired(updated);
        if (expired != null
            && payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK) {
            writeShelfStack(h, shelf, payload.shelf_pos(), slot, updated);
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.expired");
            h.refreshLiveShelfState();
            return;
        }

        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK) {
            unlockTome(h, sp, shelf, payload.shelf_pos(), slot, updated);
        } else {
            var mode = switch (payload.action()) {
                case LOCK_NEXT -> TomeLockData.Mode.NEXT_CHARGE;
                case LOCK_FULL -> TomeLockData.Mode.FULL_CHARGE;
                case LOCK_PERMANENT -> TomeLockData.Mode.PERMANENT;
                default -> null;
            };
            if (mode == null) return;
            if (TomeLockData.isEffectivelyLocked(updated)) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.already_locked");
                return;
            }
            if (!TomeLockData.lock(updated, sp.getUUID(), sp.getName().getString(), mode)) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.full");
                return;
            }
            writeShelfStack(h, shelf, payload.shelf_pos(), slot, updated);
            TomeParticleEffects.portalAt(h.world(), payload.shelf_pos(), slot);
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.locked");
        }
        h.refreshLiveShelfState();
    }

    private static void unlockTome(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        ChiseledBookShelfBlockEntity shelf,
        BlockPos shelfPos,
        int slot,
        ItemStack updated
    ) {
        var lock = TomeLockData.getLock(updated);
        if (lock == null || !TomeLockData.isEffectivelyLocked(updated)) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_locked");
            return;
        }
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && !lock.ownedBy(sp.getUUID())) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_owner", lock.ownerName());
            return;
        }
        if (!creative) {
            ItemStack grit = new ItemStack(BlockRegistry.CLEANSING_GRIT);
            var lrCfg = h.libraryResonanceConfig();
            int xpCost = Math.max(0, lrCfg.earlyUnlockXpLevels());
            int gritCost = Math.max(0, lrCfg.earlyUnlockGritCount());
            if (sp.experienceLevel < xpCost || h.countAvailableInInventory(grit) < gritCost) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.cost", xpCost, gritCost);
                return;
            }
            for (int i = 0; i < gritCost; i++) {
                if (!consumeOneCleansingGrit(h)) {
                    h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.cost", xpCost, gritCost);
                    return;
                }
            }
            sp.giveExperienceLevels(-xpCost);
        }
        TomeLockData.unlock(updated);
        writeShelfStack(h, shelf, shelfPos, slot, updated);
        TomeParticleEffects.endRodAt(h.world(), shelfPos, slot);
        h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.unlocked");
    }

    private static int lockAllChargingTomes(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        BlockPos tablePos
    ) {
        var cfg = h.libraryResonanceConfig();
        if (!cfg.tomeRegenEnabled() || cfg.tomeConsumptionMode() != MasterTomeWear.ConsumptionMode.WEAR) {
            return 0;
        }
        int locked = 0;
        for (BlockPos shelfPos : scopedShelfPositions(h, tablePos)) {
            if (!(h.world().getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                ItemStack current = shelf.getItem(slot);
                if (!current.is(Items.ENCHANTED_BOOK)
                    || TomeWearData.getWearUsed(current) <= 0
                    || TomeLockData.isEffectivelyLocked(current)) {
                    continue;
                }
                ItemStack updated = current;
                TomeLockData.clearExpired(updated);
                if (TomeLockData.lock(
                    updated,
                    sp.getUUID(),
                    sp.getName().getString(),
                    TomeLockData.Mode.FULL_CHARGE
                )) {
                    writeShelfStack(h, shelf, shelfPos, slot, updated);
                    locked++;
                }
            }
        }
        return locked;
    }

    private static void sacrificeDepletedTomes(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        BlockPos tablePos
    ) {
        if (h.isCleansePageUnlocked()) {
            h.tomeActionMessage("message.chiseled_enchanting_table.sacrifice.done");
            return;
        }
        int needed = h.effectiveSacrificeRequired(sp)
            - Advancement.completedCriteria(sp, Advancement.DISENCHANT_SACRIFICE);
        int consumed = 0;
        for (BlockPos shelfPos : sacrificeShelfPositions(h, tablePos)) {
            if (needed <= 0) break;
            if (!(h.world().getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE && needed > 0; slot++) {
                ItemStack current = shelf.getItem(slot);
                if (!current.is(BlockRegistry.DEPLETED_TOME)) continue;
                int here = 0;
                while (here < current.getCount() && needed > 0
                    && Advancement.awardNextCriterion(sp, Advancement.DISENCHANT_SACRIFICE)) {
                    here++;
                    needed--;
                    consumed++;
                }
                if (here > 0) {
                    ItemStack updated = current.copy();
                    updated.shrink(here);
                    writeShelfStack(h, shelf, shelfPos, slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
                }
            }
        }
        if (h.isCleansePageUnlocked()) {
            h.tomeActionMessage("message.chiseled_enchanting_table.sacrifice.done");
        } else if (consumed > 0) {
            h.tomeActionMessage(
                "message.chiseled_enchanting_table.sacrifice.progress",
                Advancement.completedCriteria(sp, Advancement.DISENCHANT_SACRIFICE),
                h.effectiveSacrificeRequired(sp)
            );
        } else {
            h.tomeActionMessage("message.chiseled_enchanting_table.sacrifice.none");
        }
    }
}
