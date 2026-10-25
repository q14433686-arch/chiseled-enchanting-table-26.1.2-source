package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.BlockRegistry;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side world guards that keep locked tomes physically inside their shelves. */
public final class TomeLockGuards {
    private TomeLockGuards() {}

    /** Clears all tome locks across every slot of {@code shelf} before items leave the block. */
    public static void unlockAllShelfTomes(ChiseledBookShelfBlockEntity shelf) {
        if (shelf == null) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
            ItemStack slotStack = shelf.getItem(slot);
            if (!slotStack.isEmpty() && TomeLockData.unlock(slotStack)) {
                changed = true;
            }
        }
        if (changed) {
            shelf.setChanged();
        }
    }

    /** Clears all tome locks if {@code pos} currently holds a {@link ChiseledBookShelfBlockEntity}. */
    public static void unlockAllShelfTomesAt(Level level, BlockPos pos) {
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf) {
            unlockAllShelfTomes(shelf);
        }
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !state.is(Blocks.CHISELED_BOOKSHELF)) {
                return true;
            }
            if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)) {
                return true;
            }
            if (player.getAbilities().instabuild) {
                unlockAllShelfTomes(shelf);
                return true;
            }
            var lrCfg = LibraryResonance.configForSide(world.isClientSide());
            if (lrCfg.lockedShelfBreakable()) {
                // Clear tome locks automatically when books drop from a broken chiseled bookshelf
                // so dropped books never carry orphaned shelf-lock metadata into inventories.
                unlockAllShelfTomes(shelf);
                return true;
            }
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                if (TomeLockData.isEffectivelyLocked(shelf.getItem(slot))) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(
                            Component.translatable("message.chiseled_enchanting_table.tome_lock.shelf_break_blocked"),
                            true
                        );
                    }
                    return false;
                }
            }
            unlockAllShelfTomes(shelf);
            return true;
        });

        // Manual right-click guard on the shelf front: block taking an effectively locked book
        // out by hand (in non-Creative mode when configured), and strip any stale lock from a
        // hand-held book before inserting it into an empty shelf slot.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!state.is(Blocks.CHISELED_BOOKSHELF)) {
                return InteractionResult.PASS;
            }
            if (!(world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) {
                return InteractionResult.PASS;
            }
            int slot = hitSlot(state, hitResult, pos);
            if (slot < 0) {
                return InteractionResult.PASS;
            }
            ItemStack stack = shelf.getItem(slot);
            if (stack.isEmpty()) {
                ItemStack held = player.getItemInHand(hand);
                if (!world.isClientSide() && !held.isEmpty() && TomeLockData.hasLock(held)) {
                    TomeLockData.unlock(held);
                }
                return InteractionResult.PASS;
            }
            if (player.getAbilities().instabuild) {
                return InteractionResult.PASS;
            }
            var lrCfg = LibraryResonance.configForSide(world.isClientSide());
            if (!lrCfg.lockedBookExtractionBlocked()) {
                return InteractionResult.PASS;
            }
            if (!stack.is(Items.ENCHANTED_BOOK) || !TomeLockData.isEffectivelyLocked(stack)) {
                return InteractionResult.PASS;
            }
            if (!world.isClientSide()
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                    Component.translatable(
                        "message.chiseled_enchanting_table.tome_lock.shelf_locked_duration",
                        lockDurationText(world, pos, slot, stack)),
                    true);
            }
            return InteractionResult.FAIL;
        });
    }

    /**
     * Replicates the vanilla chiseled-bookshelf hit-to-slot mapping (front face only):
     * columns split at 0.375 / 0.6875 across the face, upper half is the first row.
     * Returns {@code -1} when the hit is not on the shelf front.
     */
    private static int hitSlot(BlockState state, BlockHitResult hit, BlockPos pos) {
        Direction facing = state.getValue(ChiseledBookShelfBlock.FACING);
        if (hit.getDirection() != facing) {
            return -1;
        }
        Vec3 loc = hit.getLocation();
        double lx = loc.x() - pos.getX();
        double ly = loc.y() - pos.getY();
        double lz = loc.z() - pos.getZ();
        double u = switch (facing) {
            case NORTH -> 1.0D - lx;
            case SOUTH -> lx;
            case WEST -> lz;
            case EAST -> 1.0D - lz;
            default -> -1.0D;
        };
        if (u < 0.0D) {
            return -1;
        }
        int column = u < 0.375D ? 0 : (u < 0.6875D ? 1 : 2);
        int row = ly >= 0.5D ? 0 : 1;
        return column + row * 3;
    }

    /** Human-readable remaining lock duration for the extraction-blocked message. */
    private static Component lockDurationText(Level level, BlockPos shelfPos, int slot, ItemStack stack) {
        TomeLockData.Lock lock = TomeLockData.getLock(stack);
        if (lock == null || lock.mode() == TomeLockData.Mode.PERMANENT) {
            return Component.translatable("message.chiseled_enchanting_table.tome_lock.duration_permanent");
        }
        int eta = -1;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            BlockPos tablePos = findNearestChiseledTable(serverLevel, shelfPos);
            if (tablePos != null) {
                var cfg = ChiseledEnchantingTableData.libraryResonanceConfig();
                int totalShelves = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock
                    .calculateShelfPower(serverLevel, tablePos).totalLibraryBookshelves();
                int interval = MasterTomeWear.computeRegenIntervalTicks(cfg, totalShelves);
                long progress = WorldRegeneration.regenProgressTicks(serverLevel, shelfPos, slot);
                int wear = TomeWearData.getWearUsed(stack);
                eta = lock.mode() == TomeLockData.Mode.NEXT_CHARGE
                    ? TomeLockData.ticksToNextCharge(wear, progress, interval)
                    : TomeLockData.ticksToFullCharge(wear, progress, interval);
            }
        }
        boolean next = lock.mode() == TomeLockData.Mode.NEXT_CHARGE;
        if (eta < 0) {
            return Component.translatable(next
                ? "message.chiseled_enchanting_table.tome_lock.duration_next_bare"
                : "message.chiseled_enchanting_table.tome_lock.duration_full_bare");
        }
        return Component.translatable(
            next
                ? "message.chiseled_enchanting_table.tome_lock.duration_next"
                : "message.chiseled_enchanting_table.tome_lock.duration_full",
            formatDurationTicks(eta));
    }

    @Nullable
    private static BlockPos findNearestChiseledTable(net.minecraft.server.level.ServerLevel level, BlockPos shelfPos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        var sgCfg = ChiseledEnchantingTableData.shelfGatesConfig();
        for (BlockPos offset : chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.getScanOffsets(sgCfg)) {
            BlockPos candidate = shelfPos.offset(offset);
            if (!level.hasChunkAt(candidate)) continue;
            if (level.getBlockState(candidate).is(BlockRegistry.CHISELED_ENCHANTING_TABLE)) {
                double d = offset.distSqr(BlockPos.ZERO);
                if (d < bestDist) {
                    bestDist = d;
                    best = candidate.immutable();
                }
            }
        }
        return best;
    }

    /** Mirrors the client-side ETA text shape (h:mm:ss or mm:ss), rounded up to whole seconds. */
    private static String formatDurationTicks(int ticks) {
        long seconds = (Math.max(0, ticks) + 19L) / 20L;
        long hours = seconds / 3600L;
        long mins = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0
            ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, mins, secs)
            : String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
    }

    /** True when any slot in {@code shelf} holds a non-expired locked enchanted book. */
    public static boolean shelfHasLockedBook(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) {
            return false;
        }
        for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
            var stack = shelf.getItem(slot);
            if (stack.is(Items.ENCHANTED_BOOK) && TomeLockData.isEffectivelyLocked(stack)) {
                return true;
            }
        }
        return false;
    }
}
