package chiseled_enchanting_table.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeFrameSnapshotTest {
    @Test void callerOwnedCollectionsCannotMutatePublishedFrame() {
        var source = ChiseledEnchantingTableData.runtimeFrame();
        var transformations = new HashMap<>(source.itemTransformations());
        var values = new HashSet<>(source.defaultEnchantmentValues());
        var tags = new ArrayList<>(source.defaultEnchantmentTags());
        var excluded = new HashSet<>(source.defaultExcludedIds());
        var excludedTags = new ArrayList<>(source.defaultExcludedTags());
        var loot = new ArrayList<>(source.lootInjections());
        var frame = new RuntimeFrame(transformations, source.vanillaChestBooksMode(), source.bookshelfBookPool(),
            source.bookUpgradeRules(), source.treasureEpiphanyRules(), source.disenchantingRules(),
            source.shelfGatesConfig(), source.libraryResonanceConfig(), source.ritualConfig(), source.costTables(),
            values, tags, excluded, excludedTags, loot, source.configuration(), source.policy(), source.rules());
        transformations.clear(); values.clear(); tags.clear(); excluded.clear(); excludedTags.clear(); loot.clear();
        assertEquals(source.itemTransformations(), frame.itemTransformations());
        assertEquals(source.defaultEnchantmentValues(), frame.defaultEnchantmentValues());
        assertEquals(source.defaultEnchantmentTags(), frame.defaultEnchantmentTags());
        assertEquals(source.defaultExcludedIds(), frame.defaultExcludedIds());
        assertEquals(source.defaultExcludedTags(), frame.defaultExcludedTags());
        assertEquals(source.lootInjections(), frame.lootInjections());
        assertThrows(UnsupportedOperationException.class, () -> frame.lootInjections().add(null));
    }
}
