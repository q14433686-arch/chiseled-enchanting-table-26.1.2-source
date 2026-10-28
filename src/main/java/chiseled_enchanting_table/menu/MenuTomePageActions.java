package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.compat.CetFormatting;
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

    public static final int MEND_DEPLETED_COST = 1;
    public static final int MEND_GRIT_COST = 2;
    public static final int MEND_XP_LEVELS = 2;

    private static boolean isLockCreationAction(ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action action) {
        return action == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_NEXT
            || action == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_FULL
            || action == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_PERMANENT
            || action == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL;
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
            h.refreshLiveShelfState(true);
            return;
        }
        if (!h.isTomePageUnlocked()) {
            h.tomeActionMessage("gui.chiseled_enchanting_table.tab.locked_tooltip");
            return;
        }
        if (isLockCreationAction(payload.action()) && !h.isTomeLocksEnabled()) {
            return;
        }
        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL) {
            int locked = lockAllChargingTomes(h, sp, tablePos);
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.batch", locked);
            h.refreshLiveShelfState(true);
            return;
        }
        if (payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCATE_PING) {
            pingTomeLocations(h, sp, tablePos, payload);
            h.refreshLiveShelfState(true);
            return;
        }
        if (!isShelfInScope(h, tablePos, payload.shelf_pos())
            || !(h.world().getBlockEntity(payload.shelf_pos()) instanceof ChiseledBookShelfBlockEntity shelf)) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        int slot = payload.slot();
        if (slot < 0 || slot >= ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        ItemStack current = shelf.getItem(slot);
        if (!current.is(Items.ENCHANTED_BOOK)) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }
        ItemStack updated = current.copy();
        var expired = TomeLockData.clearExpired(updated);
        if (expired != null
            && payload.action() == ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK) {
            writeShelfStack(h, shelf, payload.shelf_pos(), slot, updated);
            TableVisualEvents.onTomeRestore(h.world(), tablePos, payload.shelf_pos(), slot, TableVisualEvents.COLOR_FROST_BLUE);
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.expired");
            h.refreshLiveShelfState(true);
            return;
        }

        switch (payload.action()) {
            case UNLOCK -> unlockTome(h, sp, shelf, payload.shelf_pos(), slot, updated);
            case MEND_ONE -> mendTomeOneCharge(h, sp, shelf, payload.shelf_pos(), slot, updated);
            case SANCTIFY_CATALYST -> sanctifyTomeWithCatalyst(h, sp, shelf, payload.shelf_pos(), slot, updated);
            default -> {
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
                TableVisualEvents.onTomeRestore(h.world(), tablePos, payload.shelf_pos(), slot, TableVisualEvents.COLOR_ARCANE_VIOLET);
                h.world().playSound(null, payload.shelf_pos(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.55F, 0.92F);
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.locked");
            }
        }
        h.refreshLiveShelfState(true);
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
        TableVisualEvents.onTomeRestore(h.world(), h.tableBlockPos(), shelfPos, slot, TableVisualEvents.COLOR_FROST_BLUE);
        h.world().playSound(null, shelfPos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.65F, 1.42F);
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
                    if (locked < 12) {
                        TableVisualEvents.onTomeRestore(h.world(), tablePos, shelfPos, slot, TableVisualEvents.COLOR_ARCANE_VIOLET);
                    }
                    locked++;
                }
            }
        }
        if (locked > 0) {
            h.world().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.70F, 0.95F);
        }
        return locked;
    }

    private static void mendTomeOneCharge(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        ChiseledBookShelfBlockEntity shelf,
        BlockPos shelfPos,
        int slot,
        ItemStack updated
    ) {
        int wearUsed = TomeWearData.getWearUsed(updated);
        if (wearUsed <= 0) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_mend.already_full");
            return;
        }
        var lock = TomeLockData.getLock(updated);
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && lock != null && TomeLockData.isEffectivelyLocked(updated) && !lock.ownedBy(sp.getUUID())) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_owner", lock.ownerName());
            return;
        }
        if (!creative) {
            if (sp.experienceLevel < MEND_XP_LEVELS
                || chiseled_enchanting_table.mechanics.ritual.RitualActionService.countDepletedTomesAvailable(h, true) < MEND_DEPLETED_COST
                || chiseled_enchanting_table.mechanics.ritual.RitualActionService.countItemInSlotAndBag(h, BlockRegistry.CLEANSING_GRIT) < MEND_GRIT_COST) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_mend.cost", MEND_DEPLETED_COST, MEND_GRIT_COST, MEND_XP_LEVELS);
                return;
            }
            if (!chiseled_enchanting_table.mechanics.ritual.RitualActionService.consumeDepletedTomesBagFirst(h, MEND_DEPLETED_COST, true)
                || !chiseled_enchanting_table.mechanics.ritual.RitualActionService.consumeFromCostSlotThenBag(h, BlockRegistry.CLEANSING_GRIT, MEND_GRIT_COST)) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_mend.cost", MEND_DEPLETED_COST, MEND_GRIT_COST, MEND_XP_LEVELS);
                return;
            }
            sp.giveExperienceLevels(-MEND_XP_LEVELS);
        }
        int nextWear = Math.max(0, wearUsed - 1);
        TomeWearData.setWearUsed(updated, nextWear);
        TomeLockData.clearExpired(updated);
        writeShelfStack(h, shelf, shelfPos, slot, updated);
        TomeParticleEffects.endRodAt(h.world(), shelfPos, slot);
        TableVisualEvents.onTomeRestore(h.world(), h.tableBlockPos(), shelfPos, slot, TableVisualEvents.COLOR_JADE_MINT);
        h.world().playSound(null, shelfPos, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.15F);
        h.world().playSound(null, shelfPos, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
        int maxUses = TomeWearData.effectiveMaxUses(updated, h.libraryResonanceConfig().maxTomeUses(), h.libraryResonanceConfig().wearImmuneMaxGeneration());
        h.tomeActionMessage("message.chiseled_enchanting_table.tome_mend.restored", Math.max(0, maxUses - nextWear), maxUses);
        h.broadcastChanges();
    }

    private static void sanctifyTomeWithCatalyst(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        ChiseledBookShelfBlockEntity shelf,
        BlockPos shelfPos,
        int slot,
        ItemStack updated
    ) {
        int wearUsed = TomeWearData.getWearUsed(updated);
        if (wearUsed <= 0) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_mend.already_full");
            return;
        }
        var lock = TomeLockData.getLock(updated);
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && lock != null && TomeLockData.isEffectivelyLocked(updated) && !lock.ownedBy(sp.getUUID())) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_lock.not_owner", lock.ownerName());
            return;
        }
        if (!creative) {
            if (!chiseled_enchanting_table.mechanics.ritual.RitualActionService.consumeFromCostSlotThenBag(
                h, BlockRegistry.ARCANE_CATALYST, 1)) {
                h.tomeActionMessage("message.chiseled_enchanting_table.tome_sanctify.cost");
                return;
            }
        }
        // Restores full current-generation durability without altering cet_generation (no copy laundering).
        TomeWearData.setWearUsed(updated, 0);
        TomeLockData.clearExpired(updated);
        writeShelfStack(h, shelf, shelfPos, slot, updated);
        TomeParticleEffects.locateBeaconAt(h.world(), shelfPos, slot);
        TableVisualEvents.onTomeRestore(h.world(), h.tableBlockPos(), shelfPos, slot, TableVisualEvents.COLOR_SACRED_GOLD);
        h.world().playSound(null, shelfPos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.3F);
        int maxUses = TomeWearData.effectiveMaxUses(updated, h.libraryResonanceConfig().maxTomeUses(), h.libraryResonanceConfig().wearImmuneMaxGeneration());
        h.tomeActionMessage("message.chiseled_enchanting_table.tome_sanctify.restored", maxUses, maxUses);
        h.broadcastChanges();
    }

    private record LocatedTomeTarget(BlockPos shelfPos, int slot, net.minecraft.network.chat.Component title) {}

    private static net.minecraft.network.chat.Component describeTomeStack(ItemStack stack) {
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return stack.getHoverName();
        }
        var enchants = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (!enchants.isEmpty()) {
            var it = enchants.entrySet().iterator();
            var first = it.next();
            net.minecraft.network.chat.MutableComponent full = net.minecraft.world.item.enchantment.Enchantment
                .getFullname(first.getKey(), first.getIntValue()).copy();
            int extra = enchants.size() - 1;
            if (extra > 0) {
                full.append(net.minecraft.network.chat.Component.literal(" (+" + extra + ")"));
            }
            return full;
        }
        return stack.getHoverName();
    }

    private static String formatRelativeDelta(BlockPos tablePos, BlockPos shelfPos) {
        int dx = shelfPos.getX() - tablePos.getX();
        int dy = shelfPos.getY() - tablePos.getY();
        int dz = shelfPos.getZ() - tablePos.getZ();
        String relX = (dx >= 0 ? "+" : "") + dx;
        String relY = (dy >= 0 ? "+" : "") + dy;
        String relZ = (dz >= 0 ? "+" : "") + dz;
        return relX + "/" + relY + "/" + relZ;
    }

    private static void pingTomeLocations(
        ChiseledEnchantingTableScreenHandler h,
        ServerPlayer sp,
        BlockPos tablePos,
        ChiseledEnchantingMenuPayloads.TomePageActionPayload payload
    ) {
        java.util.LinkedHashSet<ChiseledEnchantingMenuPayloads.TomeSlotRef> candidates = new java.util.LinkedHashSet<>();
        if (payload.slot() >= 0 && payload.slot() < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE) {
            candidates.add(new ChiseledEnchantingMenuPayloads.TomeSlotRef(payload.shelf_pos(), payload.slot()));
        }
        for (var extra : payload.extra_targets()) {
            if (candidates.size() >= ChiseledEnchantingMenuPayloads.TomeSlotRef.MAX_BATCH_SLOTS) {
                break;
            }
            candidates.add(extra);
        }

        List<LocatedTomeTarget> valid = new ArrayList<>();
        for (var ref : candidates) {
            if (!isShelfInScope(h, tablePos, ref.shelf_pos())
                || !(h.world().getBlockEntity(ref.shelf_pos()) instanceof ChiseledBookShelfBlockEntity shelf)) {
                continue;
            }
            ItemStack current = shelf.getItem(ref.slot());
            if (!current.is(Items.ENCHANTED_BOOK)) {
                continue;
            }
            valid.add(new LocatedTomeTarget(ref.shelf_pos(), ref.slot(), describeTomeStack(current)));
        }

        if (valid.isEmpty()) {
            h.tomeActionMessage("message.chiseled_enchanting_table.tome_action.invalid");
            return;
        }

        BlockPos firstShelf = valid.getFirst().shelfPos();
        h.world().playSound(null, firstShelf, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.25F, 1.35F);
        if (valid.size() >= 2) {
            h.world().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.70F, 1.32F);
        }

        for (LocatedTomeTarget target : valid) {
            TomeParticleEffects.locateBeaconAt(h.world(), target.shelfPos(), target.slot());
            TableVisualEvents.onTomeLocateBeam(h.world(), tablePos, target.shelfPos(), target.slot());
        }

        if (valid.size() == 1) {
            LocatedTomeTarget single = valid.getFirst();
            h.tomeActionMessage(
                "message.chiseled_enchanting_table.tome_locate.ping",
                single.shelfPos().getX(),
                single.shelfPos().getY(),
                single.shelfPos().getZ(),
                formatRelativeDelta(tablePos, single.shelfPos()),
                single.slot() + 1
            );
            return;
        }

        h.tomeActionMessage("message.chiseled_enchanting_table.tome_locate.batch_summary", valid.size());
        sp.sendSystemMessage(
            net.minecraft.network.chat.Component.translatable(
                "message.chiseled_enchanting_table.tome_locate.batch_header",
                valid.size()
            ).withStyle(net.minecraft.CetFormatting.AQUA),
            false
        );
        for (int i = 0; i < valid.size(); i++) {
            LocatedTomeTarget t = valid.get(i);
            sp.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable(
                    "message.chiseled_enchanting_table.tome_locate.batch_line",
                    i + 1,
                    t.title(),
                    t.shelfPos().getX(),
                    t.shelfPos().getY(),
                    t.shelfPos().getZ(),
                    formatRelativeDelta(tablePos, t.shelfPos()),
                    t.slot() + 1
                ).withStyle(net.minecraft.CetFormatting.GRAY),
                false
            );
        }
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

        // 1. Table Slots (ENCHANTABLE_SLOT when idle, then COST_SLOT)
        if (!h.isRitualActive() && needed > 0) {
            ItemStack main = h.getEnchantableItem();
            if (main.is(BlockRegistry.DEPLETED_TOME)) {
                int here = 0;
                while (here < main.getCount() && needed > 0
                    && Advancement.awardNextCriterion(sp, Advancement.DISENCHANT_SACRIFICE)) {
                    here++;
                    needed--;
                    consumed++;
                }
                if (here > 0) {
                    main.shrink(here);
                    h.markInventoryChanged();
                }
            }
        }
        if (needed > 0) {
            ItemStack cost = h.getCostItem();
            if (cost.is(BlockRegistry.DEPLETED_TOME)) {
                int here = 0;
                while (here < cost.getCount() && needed > 0
                    && Advancement.awardNextCriterion(sp, Advancement.DISENCHANT_SACRIFICE)) {
                    here++;
                    needed--;
                    consumed++;
                }
                if (here > 0) {
                    cost.shrink(here);
                    h.markInventoryChanged();
                }
            }
        }

        // 2. Player Inventory
        for (int invSlot = 0; invSlot < sp.getInventory().getContainerSize() && needed > 0; invSlot++) {
            ItemStack current = sp.getInventory().getItem(invSlot);
            if (!current.is(BlockRegistry.DEPLETED_TOME)) continue;
            int here = 0;
            while (here < current.getCount() && needed > 0
                && Advancement.awardNextCriterion(sp, Advancement.DISENCHANT_SACRIFICE)) {
                here++;
                needed--;
                consumed++;
            }
            if (here > 0) {
                current.shrink(here);
                sp.getInventory().setChanged();
            }
        }

        // 3. Nearby Chiseled Bookshelves
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
        if (consumed > 0) {
            h.world().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.05F);
            h.world().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
            TableVisualEvents.onSacrificePages(h.world(), tablePos);
            h.broadcastChanges();
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
