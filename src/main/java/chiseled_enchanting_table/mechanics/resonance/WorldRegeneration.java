package chiseled_enchanting_table.mechanics.resonance;

import java.util.*;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Aggregates all ticking tables before one world-window settlement; server thread only. */
public final class WorldRegeneration {
    private record Slot(BlockPos position, int slot) {}
    private record Context(ChiseledBookShelfBlockEntity shelf, ItemStack identity, int wear) {}
    private static final class Window {
        final RegenerationScheduler<Slot> scheduler = new RegenerationScheduler<>();
        final Map<Slot, RegenerationScheduler.Observation<Slot>> observations = new HashMap<>();
        final Map<Slot, Context> contexts = new HashMap<>();
    }
    private static final Map<ServerLevel, Window> WORLDS = new IdentityHashMap<>();
    private WorldRegeneration() {}
    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
        ServerWorldEvents.UNLOAD.register((server, world) -> WORLDS.remove(world));
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (Math.floorMod(world.getGameTime(), 20L) != 19L) return;
            Window window = WORLDS.get(world);
            if (window == null) return;
            var valid = new ArrayList<RegenerationScheduler.Observation<Slot>>();
            window.observations.forEach((slot, observation) -> {
                Context context = window.contexts.get(slot);
                if (world.hasChunkAt(slot.position()) && world.getBlockEntity(slot.position()) == context.shelf()
                    && context.shelf().getItem(slot.slot()) == context.identity()
                    && MasterTomeWear.getWearUsed(context.identity()) == context.wear()) valid.add(observation);
            });
            window.scheduler.settle(world.getGameTime() / 20L, valid).forEach((slot, recovery) -> {
                Context context = window.contexts.get(slot);
                // Never apply an old observation to another block entity or another book.
                if (!world.hasChunkAt(slot.position()) || world.getBlockEntity(slot.position()) != context.shelf()
                    || context.shelf().getItem(slot.slot()) != recovery.expectedIdentity()) return;
                ItemStack updated = context.identity().copy();
                MasterTomeWear.setWearUsed(updated, recovery.remainingWear());
                context.shelf().setItem(slot.slot(), updated);
                context.shelf().setChanged();
                if (recovery.remainingWear() > 0) window.scheduler.acknowledgeReplacement(
                    slot, recovery.expectedIdentity(), updated, recovery.remainingWear());
                var state = world.getBlockState(slot.position());
                world.sendBlockUpdated(slot.position(), state, state, Block.UPDATE_ALL);
            });
            window.observations.clear(); window.contexts.clear();
        });
    }

    public static void observe(Level level, BlockPos table, BookshelfProgression.Config shelves,
                               LibraryResonance.Config rules, int count) {
        if (!(level instanceof ServerLevel world) || !rules.tomeRegenEnabled()
            || rules.tomeConsumptionMode() != MasterTomeWear.ConsumptionMode.WEAR) return;
        Window window = WORLDS.computeIfAbsent(world, ignored -> new Window());
        int interval = MasterTomeWear.computeRegenIntervalTicks(rules, count);
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(shelves)) {
            BlockPos pos = table.offset(offset);
            if (!world.hasChunkAt(pos) || (shelves.requireLineOfSight()
                && !ChiseledEnchantingTableBlock.hasClearLineOfSight(world, table, pos))) continue;
            if (!(world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int i = 0; i < 6; i++) {
                ItemStack book = shelf.getItem(i);
                if (!book.is(Items.ENCHANTED_BOOK) || MasterTomeWear.hasInfinityImmunity(book)) continue;
                int wear = MasterTomeWear.getWearUsed(book);
                if (wear <= 0) continue;
                Slot slot = new Slot(pos.immutable(), i);
                if (!window.observations.containsKey(slot) && window.observations.size() >= RegenerationScheduler.MAX_OBSERVATIONS) continue;
                var previous = window.observations.get(slot);
                // A changed reference resets residency in settle; this is the latest observation.
                int fastest = previous != null && previous.bookIdentity() == book && previous.wear() == wear
                    ? Math.min(interval, previous.intervalTicks()) : interval;
                window.observations.put(slot, new RegenerationScheduler.Observation<>(slot, book, wear, fastest));
                window.contexts.put(slot, new Context(shelf, book, wear));
            }
        }
    }
}
