package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Aggregates all ticking tables before one world-window settlement; server thread only.
 * Persists loaded-chunk residency across chunk unloads and world restarts while evicting removed/moved books.
 */
public final class WorldRegeneration {
    private static final String SAVE_FILENAME = "chiseled_enchanting_table_regen.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Identity of one bookshelf slot; public so UI sync can key regen progress by it. */
    public record Slot(BlockPos position, int slot) {}

    /** Live regeneration progress of one tracked shelf book (for the «纂刻的书» page). */
    public record RegenProgress(int intervalTicks, long progressTicks) {}

    record BookSignature(Map<String, Integer> storedEnchantments, String customName) {
        static BookSignature of(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) {
                return null;
            }
            ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);
            TreeMap<String, Integer> sorted = new TreeMap<>();
            for (var entry : ench.entrySet()) {
                int lvl = entry.getIntValue();
                if (lvl > 0) {
                    String key = entry.getKey().unwrapKey()
                        .map(rk -> rk.identifier().toString())
                        .orElseGet(entry.getKey()::getRegisteredName);
                    sorted.put(key, lvl);
                }
            }
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            return new BookSignature(Collections.unmodifiableMap(sorted), name != null ? name.getString() : "");
        }
    }

    private record Context(ChiseledBookShelfBlockEntity shelf, ItemStack identity, int wear, BookSignature signature) {}

    private static final class Window {
        long lastSettled = -1;
        final RegenerationScheduler<Slot> scheduler = new RegenerationScheduler<>();
        final Map<Slot, RegenerationScheduler.Observation<Slot>> observations = new HashMap<>();
        final Map<Slot, Context> contexts = new HashMap<>();
        final Map<Slot, BookSignature> signatures = new HashMap<>();
    }

    private static final Map<ServerLevel, Window> WORLDS = new IdentityHashMap<>();

    private WorldRegeneration() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(WorldRegeneration::loadPersistedState);
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> savePersistedState(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(WorldRegeneration::savePersistedState);
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> onChunkUnloaded(world, chunk.getPos()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WORLDS.clear();
            chiseled_enchanting_table.config.ModRuntimeConfig.setServerPackPolicy(
                chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy.NONE);
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                chiseled_enchanting_table.config.ModRuntimeConfig.get(),
                chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy.NONE, () -> {});
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Set<ServerLevel> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ServerLevel world : server.getAllLevels()) {
                loaded.add(world);
                settleWorld(world);
            }
            WORLDS.keySet().retainAll(loaded);
        });
    }

    private static void onChunkUnloaded(ServerLevel world, ChunkPos chunkPos) {
        Window window = WORLDS.get(world);
        if (window == null) return;
        for (var entry : window.scheduler.exportSavedEntries()) {
            Slot slot = entry.slot();
            if ((slot.position().getX() >> 4) == chunkPos.x() && (slot.position().getZ() >> 4) == chunkPos.z()) {
                BookSignature sig = window.signatures.get(slot);
                if (sig == null && entry.bookIdentity() instanceof ItemStack stack) {
                    sig = BookSignature.of(stack);
                } else if (sig == null && entry.bookIdentity() instanceof BookSignature savedSig) {
                    sig = savedSig;
                }
                if (sig != null) {
                    window.signatures.put(slot, sig);
                    window.scheduler.pauseWithIdentity(slot, sig);
                }
            }
        }
    }

    private static void settleWorld(ServerLevel world) {
        if (Math.floorMod(world.getGameTime(), 20L) != 19L) return;
        Window window = WORLDS.get(world);
        if (window == null || window.lastSettled == world.getGameTime() / 20L) return;
        long nowWindow = world.getGameTime() / 20L;
        if (window.lastSettled > nowWindow) {
            // Game time ran backwards (e.g. /time set): preserve progress via snapshot restore.
            var snap = window.scheduler.exportSavedEntries();
            window.scheduler.restoreSavedEntries(snap);
        }
        window.lastSettled = nowWindow;

        var valid = new ArrayList<RegenerationScheduler.Observation<Slot>>();
        window.observations.forEach((slot, observation) -> {
            Context context = window.contexts.get(slot);
            if (context != null
                && world.hasChunkAt(slot.position())
                && world.getBlockEntity(slot.position()) == context.shelf()
                && context.shelf().getItem(slot.slot()) == context.identity()
                && MasterTomeWear.getWearUsed(context.identity()) == context.wear()) {
                window.signatures.put(slot, context.signature());
                if (window.scheduler.isSlotPaused(slot)) {
                    Object trackedId = window.scheduler.trackedIdentity(slot);
                    if (trackedId instanceof BookSignature pausedSig && pausedSig.equals(context.signature())) {
                        window.scheduler.rebindPausedIdentity(slot, pausedSig, context.identity());
                    }
                }
                valid.add(observation);
            }
        });

        var sgCfg = ChiseledEnchantingTableData.shelfGatesConfig();
        var lrCfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        boolean regenActive = lrCfg.tomeRegenEnabled() && lrCfg.tomeConsumptionMode() == MasterTomeWear.ConsumptionMode.WEAR;

        window.scheduler.settle(nowWindow, valid, slot -> shouldRetainPaused(world, window, slot, sgCfg, regenActive))
            .forEach((slot, recovery) -> {
                Context context = window.contexts.get(slot);
                if (context == null
                    || !world.hasChunkAt(slot.position())
                    || world.getBlockEntity(slot.position()) != context.shelf()
                    || context.shelf().getItem(slot.slot()) != recovery.expectedIdentity()) {
                    return;
                }
                ItemStack updated = context.identity().copy();
                MasterTomeWear.setWearUsed(updated, recovery.remainingWear());
                // Timed tome locks («锁至+1 / 充满») expire exactly when their charge
                // target lands; permanent locks survive regeneration untouched.
                if (TomeLock.shouldExpire(TomeLock.getMode(updated), context.wear(), recovery.remainingWear())) {
                    TomeLock.clearLock(updated);
                }
                context.shelf().setItem(slot.slot(), updated);
                context.shelf().setChanged();
                if (recovery.remainingWear() > 0) {
                    window.scheduler.acknowledgeReplacement(slot, recovery.expectedIdentity(), updated, recovery.remainingWear());
                    window.signatures.put(slot, BookSignature.of(updated));
                } else {
                    window.signatures.remove(slot);
                }
                var state = world.getBlockState(slot.position());
                world.sendBlockUpdated(slot.position(), state, state, Block.UPDATE_ALL);
            });

        // Clean up signature cache for evicted slots.
        Set<Slot> retained = new java.util.HashSet<>();
        for (var e : window.scheduler.exportSavedEntries()) {
            retained.add(e.slot());
        }
        window.signatures.keySet().retainAll(retained);
        window.observations.clear();
        window.contexts.clear();
    }

    private static boolean shouldRetainPaused(
        ServerLevel world,
        Window window,
        Slot slot,
        BookshelfProgression.Config sgCfg,
        boolean regenActive
    ) {
        if (!regenActive) {
            return false;
        }
        BookSignature sig = window.signatures.get(slot);
        Object tracked = window.scheduler.trackedIdentity(slot);
        if (sig == null && tracked instanceof BookSignature bs) {
            sig = bs;
            window.signatures.put(slot, sig);
        } else if (sig == null && tracked instanceof ItemStack st) {
            sig = BookSignature.of(st);
            if (sig != null) window.signatures.put(slot, sig);
        }
        if (sig == null) {
            return false;
        }

        if (!world.hasChunkAt(slot.position())) {
            window.scheduler.pauseWithIdentity(slot, sig);
            return true;
        }

        // Shelf chunk IS loaded: verify the exact book is still sitting untouched in this slot.
        if (!(world.getBlockEntity(slot.position()) instanceof ChiseledBookShelfBlockEntity shelf)) {
            return false;
        }
        ItemStack current = shelf.getItem(slot.slot());
        if (!current.is(Items.ENCHANTED_BOOK) || MasterTomeWear.hasInfinityImmunity(current)) {
            return false;
        }
        int currentWear = MasterTomeWear.getWearUsed(current);
        if (currentWear <= 0) {
            return false;
        }
        if (tracked instanceof ItemStack liveExpected && current != liveExpected) {
            return false;
        }
        if (!Objects.equals(sig, BookSignature.of(current))) {
            return false;
        }

        // Only stay paused if a neighboring chunk within scan radius is unloaded (so a covering table may be unloaded).
        if (hasAnyUnloadedCoveringChunk(world, slot.position(), sgCfg)) {
            window.scheduler.pauseWithIdentity(slot, sig);
            return true;
        }
        return false;
    }

    private static boolean hasAnyUnloadedCoveringChunk(
        ServerLevel world,
        BlockPos shelfPos,
        BookshelfProgression.Config sgCfg
    ) {
        int rx = sgCfg == null ? 3 : Math.max(1, sgCfg.horizontalScanRadius());
        int minCx = (shelfPos.getX() - rx) >> 4;
        int maxCx = (shelfPos.getX() + rx) >> 4;
        int minCz = (shelfPos.getZ() - rx) >> 4;
        int maxCz = (shelfPos.getZ() + rx) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.hasChunk(cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void acknowledgeInPlaceWear(
        Level level,
        BlockPos shelfPos,
        int slotIndex,
        ItemStack previousBook,
        ItemStack updatedBook,
        int updatedWear
    ) {
        if (!(level instanceof ServerLevel world) || updatedWear <= 0) return;
        Window window = WORLDS.get(world);
        if (window == null) return;
        Slot slot = new Slot(shelfPos.immutable(), slotIndex);
        window.scheduler.acknowledgeInPlaceWear(slot, previousBook, updatedBook, updatedWear);
        BookSignature sig = BookSignature.of(updatedBook);
        if (sig != null) {
            window.signatures.put(slot, sig);
        }
    }

    /**
     * One-shot snapshot of every tracked book's regen progress in {@code world},
     * keyed by shelf slot. Built from a single {@code exportSavedEntries()} pass so
     * UI sync stays O(tracked) instead of O(books x tracked).
     */
    public static java.util.Map<Slot, RegenProgress> regenProgressSnapshot(ServerLevel world) {
        Window window = world == null ? null : WORLDS.get(world);
        if (window == null) {
            return java.util.Map.of();
        }
        java.util.Map<Slot, RegenProgress> out = new java.util.HashMap<>();
        for (var entry : window.scheduler.exportSavedEntries()) {
            out.put(entry.slot(), new RegenProgress(entry.intervalTicks(), entry.progressTicks()));
        }
        return out;
    }

    public static void observe(
        Level level,
        BlockPos table,
        BookshelfProgression.Config shelves,
        LibraryResonance.Config rules,
        int count
    ) {
        if (!(level instanceof ServerLevel world) || !rules.tomeRegenEnabled()
            || rules.tomeConsumptionMode() != MasterTomeWear.ConsumptionMode.WEAR) return;
        if (shelves != null && shelves.requireBreathingRoom()) {
            boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(world, table);
            int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(world, table);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, shelves) <= 0) {
                return;
            }
        }
        Window window = WORLDS.computeIfAbsent(world, ignored -> new Window());
        int interval = MasterTomeWear.computeRegenIntervalTicks(rules, count);
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(shelves)) {
            BlockPos pos = table.offset(offset);
            if (!world.hasChunkAt(pos) || (shelves != null && shelves.requireLineOfSight()
                && !ChiseledEnchantingTableBlock.hasClearLineOfSight(world, table, pos))) continue;
            if (!(world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int i = 0; i < 6; i++) {
                ItemStack book = shelf.getItem(i);
                if (!book.is(Items.ENCHANTED_BOOK) || MasterTomeWear.hasInfinityImmunity(book)) continue;
                int wear = MasterTomeWear.getWearUsed(book);
                if (wear <= 0) continue;
                BookSignature sig = BookSignature.of(book);
                if (sig == null) continue;
                Slot slot = new Slot(pos.immutable(), i);
                if (!window.observations.containsKey(slot) && window.observations.size() >= RegenerationScheduler.MAX_OBSERVATIONS) continue;
                var previous = window.observations.get(slot);
                int fastest = previous != null && previous.bookIdentity() == book && previous.wear() == wear
                    ? Math.min(interval, previous.intervalTicks()) : interval;
                window.observations.put(slot, new RegenerationScheduler.Observation<>(slot, book, wear, fastest));
                window.contexts.put(slot, new Context(shelf, book, wear, sig));
            }
        }
    }

    private static Path resolveSavePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve(SAVE_FILENAME);
    }

    private static void savePersistedState(MinecraftServer server) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 1);
            JsonObject dims = new JsonObject();
            for (ServerLevel world : server.getAllLevels()) {
                Window window = WORLDS.get(world);
                if (window == null) continue;
                List<RegenerationScheduler.SavedEntry<Slot>> saved = window.scheduler.exportSavedEntries();
                if (saved.isEmpty()) continue;
                JsonArray arr = new JsonArray();
                for (var entry : saved) {
                    Slot slot = entry.slot();
                    BookSignature sig = window.signatures.get(slot);
                    if (sig == null && entry.bookIdentity() instanceof BookSignature bs) {
                        sig = bs;
                    } else if (sig == null && entry.bookIdentity() instanceof ItemStack st) {
                        sig = BookSignature.of(st);
                    }
                    if (sig == null || sig.storedEnchantments().isEmpty()) continue;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("x", slot.position().getX());
                    obj.addProperty("y", slot.position().getY());
                    obj.addProperty("z", slot.position().getZ());
                    obj.addProperty("slot", slot.slot());
                    obj.addProperty("wear", entry.expectedWear());
                    obj.addProperty("interval_ticks", entry.intervalTicks());
                    obj.addProperty("progress_ticks", entry.progressTicks());
                    obj.addProperty("custom_name", sig.customName());
                    JsonObject enchObj = new JsonObject();
                    sig.storedEnchantments().forEach(enchObj::addProperty);
                    obj.add("enchantments", enchObj);
                    arr.add(obj);
                }
                if (!arr.isEmpty()) {
                    dims.add(world.dimension().identifier().toString(), arr);
                }
            }
            root.add("dimensions", dims);
            Path target = resolveSavePath(server);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(SAVE_FILENAME + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to persist Master Tome regeneration state: {}", e.getMessage());
        }
    }

    private static void loadPersistedState(MinecraftServer server) {
        Path source = resolveSavePath(server);
        if (!Files.isRegularFile(source)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("dimensions") || !root.get("dimensions").isJsonObject()) return;
            JsonObject dims = root.getAsJsonObject("dimensions");
            Map<Identifier, ServerLevel> byId = new HashMap<>();
            for (ServerLevel world : server.getAllLevels()) {
                byId.put(world.dimension().identifier(), world);
            }
            for (Map.Entry<String, JsonElement> dimEntry : dims.entrySet()) {
                Identifier dimId = Identifier.tryParse(dimEntry.getKey());
                ServerLevel world = dimId != null ? byId.get(dimId) : null;
                if (world == null || !dimEntry.getValue().isJsonArray()) continue;
                Window window = WORLDS.computeIfAbsent(world, ignored -> new Window());
                List<RegenerationScheduler.SavedEntry<Slot>> entries = new ArrayList<>();
                for (JsonElement elem : dimEntry.getValue().getAsJsonArray()) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    int slotIdx = obj.has("slot") ? obj.get("slot").getAsInt() : -1;
                    int wear = obj.has("wear") ? obj.get("wear").getAsInt() : 0;
                    int interval = obj.has("interval_ticks") ? obj.get("interval_ticks").getAsInt() : 0;
                    long progress = obj.has("progress_ticks") ? obj.get("progress_ticks").getAsLong() : 0L;
                    if (slotIdx < 0 || slotIdx >= 6 || wear <= 0 || interval < RegenerationScheduler.WINDOW_TICKS || progress < 0) {
                        continue;
                    }
                    TreeMap<String, Integer> enchMap = new TreeMap<>();
                    if (obj.has("enchantments") && obj.get("enchantments").isJsonObject()) {
                        for (var ee : obj.getAsJsonObject("enchantments").entrySet()) {
                            if (ee.getValue().isJsonPrimitive() && ee.getValue().getAsJsonPrimitive().isNumber()) {
                                int lvl = ee.getValue().getAsInt();
                                if (lvl > 0) enchMap.put(ee.getKey(), lvl);
                            }
                        }
                    }
                    if (enchMap.isEmpty()) continue;
                    String customName = obj.has("custom_name") ? obj.get("custom_name").getAsString() : "";
                    BookSignature sig = new BookSignature(Collections.unmodifiableMap(enchMap), customName);
                    Slot slot = new Slot(new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()), slotIdx);
                    entries.add(new RegenerationScheduler.SavedEntry<>(slot, sig, wear, interval, progress));
                    window.signatures.put(slot, sig);
                    if (entries.size() >= RegenerationScheduler.MAX_OBSERVATIONS) break;
                }
                if (!entries.isEmpty()) {
                    window.scheduler.restoreSavedEntries(entries);
                }
            }
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to load Master Tome regeneration state: {}", e.getMessage());
        }
    }
}
