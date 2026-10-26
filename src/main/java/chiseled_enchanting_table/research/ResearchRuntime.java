package chiseled_enchanting_table.research;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread gateway. No client completion packet, debug grant, material payment or timer. */
public final class ResearchRuntime implements SimpleSynchronousResourceReloadListener {
    private static volatile ResearchCatalog catalog = new ResearchCatalog(List.of());
    private static volatile ResearchCatalog pendingCatalog;
    private static final Map<MinecraftServer,ResearchStore> STORES = new IdentityHashMap<>();
    private static final Map<String,Predicate<ServerPlayer>> VERIFIERS = new HashMap<>();
    private static Function<ServerPlayer,Optional<ResearchCatalog.Library>> libraryAccess = ResearchWorkspace::resolve;
    private ResearchRuntime() {}
    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ResearchRuntime());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success) -> {
            if (success && pendingCatalog != null) catalog = pendingCatalog;
            pendingCatalog = null;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (pendingCatalog != null) { catalog = pendingCatalog; pendingCatalog = null; }
            try { STORES.put(server,ResearchStore.load(path(server))); }
            catch (Exception e) { ChiseledEnchantingTable.LOGGER.error("Research disabled: cannot safely read journal; original save preserved",e); }
        });
        ServerLifecycleEvents.AFTER_SAVE.register((server,flush,force) -> save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(ResearchRuntime::save);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STORES.remove(server); catalog = new ResearchCatalog(List.of()); pendingCatalog = null;
        });
    }
    public static ResearchCatalog catalog() { return catalog; }
    /** Installed by the table adapter, which must verify menu, range, dimension and permissions. */
    public static void setLibraryAccess(Function<ServerPlayer,Optional<ResearchCatalog.Library>> access) {
        libraryAccess = Objects.requireNonNull(access);
    }
    /** Installed by server event adapters, never by datapacks or packets; unknown evidence cannot award progress. */
    public static void registerEvidence(String id, Predicate<ServerPlayer> verifier) {
        ResearchCatalog.key(id);
        if (VERIFIERS.putIfAbsent(id,Objects.requireNonNull(verifier)) != null) throw new IllegalArgumentException("Duplicate evidence verifier " + id);
    }
    public static ResearchJournal journal(ServerPlayer player) { return store(player).get(player.getUUID()); }
    public static Optional<ResearchEngine.Preview> preview(ServerPlayer player, String topic) {
        var journal = journal(player);
        return libraryAccess.apply(player).map(library -> ResearchEngine.preview(catalog.topic(topic),journal,library));
    }
    public static ResearchEngine.Transition begin(ServerPlayer player, String topic, String hypothesis) {
        var store = store(player); var library = libraryAccess.apply(player).orElseThrow(() -> new IllegalStateException("No authorized research workspace"));
        var result = ResearchEngine.begin(catalog.topic(topic),store.get(player.getUUID()),library,hypothesis);
        store.put(player.getUUID(),result.journal()); return result;
    }
    /** Must be invoked from the relevant server event; verifiers must check genuine event evidence, not client claims. */
    public static ResearchEngine.Transition observe(ServerPlayer player, String topic, String evidence) {
        var store = store(player); var journal = store.get(player.getUUID());
        var verifier = VERIFIERS.get(evidence);
        if (verifier == null) return new ResearchEngine.Transition(ResearchEngine.Status.IRRELEVANT,journal);
        if (!verifier.test(player)) return new ResearchEngine.Transition(ResearchEngine.Status.IRRELEVANT,journal);
        var result = ResearchEngine.observe(catalog.topic(topic),journal,evidence);
        store.put(player.getUUID(),result.journal()); return result;
    }
    public static ResearchEngine.Transition conclude(ServerPlayer player, String topic) {
        var store = store(player);
        var library = libraryAccess.apply(player).orElseThrow(() -> new IllegalStateException("No authorized research workspace"));
        var result = ResearchEngine.conclude(catalog.topic(topic),store.get(player.getUUID()),library);
        store.put(player.getUUID(),result.journal()); return result;
    }
    public static void cancel(ServerPlayer player, String topic) {
        var store = store(player); store.put(player.getUUID(),ResearchEngine.cancel(store.get(player.getUUID()),topic));
    }
    private static ResearchStore store(ServerPlayer player) {
        var server = Objects.requireNonNull(player.level().getServer());
        if (!server.isSameThread()) throw new IllegalStateException("Research requires server thread");
        var store = STORES.get(server);
        if (store == null) throw new IllegalStateException("Research store unavailable; no progress accepted");
        return store;
    }
    private static Path path(MinecraftServer server) { return server.getWorldPath(LevelResource.DATA).resolve("chiseled_enchanting_table_research.json"); }
    private static void save(MinecraftServer server) {
        var store = STORES.get(server); if (store == null) return;
        try { store.save(path(server)); }
        catch (Exception e) { ChiseledEnchantingTable.LOGGER.error("Research save failed; unsaved state retained for retry",e); }
    }
    @Override public Identifier getFabricId() { return ChiseledEnchantingTable.identifier("research"); }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        var id = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/research.json");
        var resource = resources.getResource(id);
        ResearchCatalog next;
        if (resource.isEmpty()) next = new ResearchCatalog(List.of());
        else try (Reader reader = resource.get().openAsReader()) {
            next = ResearchJson.catalog(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) { throw new IllegalArgumentException("Invalid research catalog; previous snapshot retained",e); }
        pendingCatalog = next; // commit only on successful overall reload or initial server startup
    }
}
