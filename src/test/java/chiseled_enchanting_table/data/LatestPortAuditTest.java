package chiseled_enchanting_table.data;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.DataIds;
import chiseled_enchanting_table.data.DatapackKeyExtractor;
import chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real resource parsing against 26.3 Resource/ResourceManager, not textual source assertions. */
class LatestPortAuditTest {
    private static Resource resource(String source, String json) {
        var pack = (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
            new Class<?>[] { PackResources.class }, (proxy, method, args) -> switch (method.getName()) {
                case "packId", "toString" -> source;
                case "knownPackInfo" -> Optional.empty();
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ResourceManager manager(Map<Identifier, Resource> resources) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
            new Class<?>[] { ResourceManager.class }, (proxy, method, args) -> switch (method.getName()) {
                case "getResource" -> Optional.ofNullable(resources.get((Identifier) args[0]));
                case "toString" -> "latest-port-audit-resources";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static java.util.Set<String> extract(ResourceManager manager) {
        var keys = new java.util.LinkedHashSet<String>();
        DatapackKeyExtractor.collectExternalDatapackKeys(manager, DataIds.LIBRARY_RESONANCE_ID, "library_resonance", keys);
        return keys;
    }

    @Test void externalPackCanPinEvenByteIdenticalBundledDefaults() throws Exception {
        String bundled = Files.readString(Path.of("src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json"));
        var external = manager(Map.of(DataIds.LIBRARY_RESONANCE_ID, resource("file/pinned_defaults", bundled)));
        var keys = extract(external);
        assertTrue(keys.contains("locked_shelf_breakable"));
        assertTrue(keys.contains("tome_page_required_shelves"));
        for (String builtInSource : List.of("chiseled_enchanting_table", "fabric", "vanilla")) {
            assertTrue(extract(manager(Map.of(DataIds.LIBRARY_RESONANCE_ID,
                resource(builtInSource, bundled)))).isEmpty(), builtInSource);
        }
    }

    @Test void tomePageDatapackRulesReachTheResolverAndPublishedDomain() throws Exception {
        String json = "{\"tome_pages\":{\"locked_shelf_breakable\":false,\"locked_book_extraction_blocked\":false,"
            + "\"early_unlock_xp_levels\":23,\"early_unlock_grit_count\":7,\"sacrifice_required\":29,\"tome_page_required_shelves\":13}}";
        var raw = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.fromJson(
            com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        assertFalse(raw.lockedShelfBreakable());
        assertEquals(23, raw.earlyUnlockXpLevels());
        var field = ChiseledEnchantingTableData.class.getDeclaredField("rawLibraryResonanceConfig");
        field.setAccessible(true);
        var previousRaw = field.get(null);
        var previousFrame = ChiseledEnchantingTableData.runtimeFrame();
        try {
            field.set(null, raw);
            var keys = extract(manager(Map.of(DataIds.LIBRARY_RESONANCE_ID, resource("file/pages", json))));
            assertEquals(6, keys.size());
            var policy = new chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy(true, false, keys, keys);
            ChiseledEnchantingTableData.applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT, policy, () -> {});
            var frame = ChiseledEnchantingTableData.runtimeFrame();
            assertFalse(frame.configuration().lockedShelfBreakable());
            assertFalse(frame.configuration().lockedBookExtractionBlocked());
            assertEquals(23, frame.configuration().earlyUnlockXpLevels());
            assertEquals(7, frame.configuration().earlyUnlockGritCount());
            assertEquals(29, frame.configuration().sacrificeRequired());
            assertEquals(13, frame.configuration().tomePageRequiredShelves());
            assertEquals(raw.tomePages(), frame.libraryResonanceConfig().tomePages());
            assertEquals(23, frame.rules().rule("early_unlock_xp_levels").effective());
        } finally {
            field.set(null, previousRaw);
            ChiseledEnchantingTableData.applyRuntimeConfig(previousFrame.configuration(), previousFrame.policy(), () -> {});
        }
    }

    @Test void registryPreloadDoesNotPublishToGameplayOrChestSpawnCache() {
        var liveEntries = ChiseledEnchantingTableData.lootInjections();
        var liveMode = ChiseledEnchantingTableData.vanillaChestBooksMode();
        var liveChests = DepletedTomeWorldMechanics.activeChestSpawnConfig();
        var valid = manager(Map.of(DataIds.LOOT_INJECTIONS_ID, resource("file/test", "{\"entries\":[]}")));
        try {
            ChiseledEnchantingTableData.preloadForLootRegistries(valid);
            assertTrue(ChiseledEnchantingTableData.lootRegistryBuildRules().entries().isEmpty());
            assertSame(liveEntries, ChiseledEnchantingTableData.lootInjections());
            assertEquals(liveMode, ChiseledEnchantingTableData.vanillaChestBooksMode());
            assertSame(liveChests, DepletedTomeWorldMechanics.activeChestSpawnConfig());
            ChiseledEnchantingTableData.clearLootRegistryBuildRules();
            assertEquals(liveEntries, ChiseledEnchantingTableData.lootRegistryBuildRules().entries());
        } finally {
            ChiseledEnchantingTableData.clearLootRegistryBuildRules();
        }
    }

    @Test void invalidLootOrPolicyAbortsPreparationWithoutPublishingPartialInputs() {
        var good = manager(Map.of(DataIds.LOOT_INJECTIONS_ID, resource("file/test", "{\"entries\":[]}")));
        try {
            ChiseledEnchantingTableData.preloadForLootRegistries(good);
            var previous = ChiseledEnchantingTableData.lootRegistryBuildRules();
            assertThrows(IllegalArgumentException.class, () -> ChiseledEnchantingTableData.preloadForLootRegistries(
                manager(Map.of(DataIds.LOOT_INJECTIONS_ID, resource("file/broken", "{\"entries\":42}")))));
            assertSame(previous, ChiseledEnchantingTableData.lootRegistryBuildRules());
            assertThrows(IllegalArgumentException.class, () -> ChiseledEnchantingTableData.preloadForLootRegistries(
                manager(Map.of(DataIds.LOOT_INJECTIONS_ID, resource("file/test", "{\"entries\":[]}"),
                    DataIds.PACK_POLICY_ID, resource("file/broken", "{\"locked_keys\":[\"unknown_option\"]}")))));
            assertSame(previous, ChiseledEnchantingTableData.lootRegistryBuildRules());
        } finally {
            ChiseledEnchantingTableData.clearLootRegistryBuildRules();
        }
    }
}
