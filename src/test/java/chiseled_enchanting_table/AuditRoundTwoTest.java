package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.config.SearchTextBuffer;
import chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.menu.EnchantingProtocol;
import chiseled_enchanting_table.menu.PayloadLimits;
import chiseled_enchanting_table.registry.CetTags;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless JUnit verification suite for the repository technical, code, localization,
 * and data-driven boundary audit fixes (revision_17).
 */
class AuditRoundTwoTest {

    private static JsonObject readBundledJson(String fileName) throws Exception {
        String path = "/data/chiseled_enchanting_table/chiseled_enchanting_table/" + fileName;
        try (var in = AuditRoundTwoTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Missing bundled resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void searchTextBufferHandlesSurrogatePairsWithoutCorruptingUtf16Boundaries() {
        SearchTextBuffer buf = new SearchTextBuffer(6);
        String rocket = "\uD83D\uDE80"; // U+1F680 (2 UTF-16 chars, 1 code point)
        String rareHan = "\uD842\uDFB7"; // U+20BB7 𠮷 (2 UTF-16 chars, 1 code point)

        assertTrue(buf.insertCodePoint(rocket.codePointAt(0)));
        assertTrue(buf.insertText("A纂" + rareHan));
        assertEquals(rocket + "A纂" + rareHan, buf.text());
        assertEquals(4, buf.codePointLength());
        assertEquals(6, buf.text().length(), "UTF-16 char length includes 2 surrogate pairs");

        // Stepping left once from end must step across both UTF-16 surrogates of U+20BB7
        buf.moveByCodePoints(-1, false);
        assertEquals(4, buf.cursor(), "Cursor must land cleanly before high surrogate, never mid-pair");
        assertTrue(buf.insertCodePoint('!'));
        assertEquals(rocket + "A纂!" + rareHan, buf.text());

        // Forward delete removes the entire trailing supplementary code point U+20BB7
        assertTrue(buf.deleteForward());
        assertEquals(rocket + "A纂!", buf.text());

        // Move to index after rocket and backspace: removes entire U+1F680 surrogate pair
        buf.moveHome(false);
        buf.moveByCodePoints(1, false);
        assertEquals(2, buf.cursor());
        assertTrue(buf.deleteBackward());
        assertEquals("A纂!", buf.text());
        assertEquals(0, buf.cursor());

        // Code-point cap counts Unicode code points rather than UTF-16 char units
        buf.clear();
        for (int i = 0; i < 6; i++) {
            assertTrue(buf.insertCodePoint(rocket.codePointAt(0)));
        }
        assertEquals(6, buf.codePointLength());
        assertEquals(12, buf.text().length());
        assertFalse(buf.insertCodePoint('X'), "7th code point must be rejected at maxCodePoints=6");
    }

    @Test
    void deVersionedConfigEnumsPreserveLegacyAliasesAndFailClosedOnTypos() {
        assertEquals("modern", ModRuntimeConfig.CostTableDataset.MODERN.token());
        assertEquals("classic", ModRuntimeConfig.CostTableDataset.CLASSIC.token());
        assertEquals("classic", ModRuntimeConfig.Preset.CLASSIC.id());

        // Legacy versioned tokens resolve cleanly to canonical constants
        assertEquals(ModRuntimeConfig.CostTableDataset.MODERN,
            ModRuntimeConfig.CostTableDataset.tryFromToken("modern_1_1_0").orElseThrow());
        assertEquals(ModRuntimeConfig.CostTableDataset.CLASSIC,
            ModRuntimeConfig.CostTableDataset.tryFromToken("classic_1_0_3").orElseThrow());
        assertEquals(ModRuntimeConfig.Preset.CLASSIC,
            ModRuntimeConfig.Preset.tryFromToken("classic_1_0_3").orElseThrow());

        // Typos fail closed (Optional.empty() on tryFromToken, deterministic fallback on fromToken)
        assertTrue(ModRuntimeConfig.CostTableDataset.tryFromToken("modern_typo").isEmpty());
        assertTrue(ModRuntimeConfig.CostTableDataset.tryFromToken("").isEmpty());
        assertTrue(ModRuntimeConfig.CostTableDataset.tryFromToken(null).isEmpty());
        assertEquals(ModRuntimeConfig.CostTableDataset.MODERN,
            ModRuntimeConfig.CostTableDataset.fromToken("modern_typo"));

        assertTrue(ModRuntimeConfig.MaterialSelectionMode.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.XpSettlementMode.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.ShelfBoostModel.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.DefaultUnlockFilter.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.TransformationSetMode.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.DrainedBookResult.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.ResearchRoundingMode.tryFromToken("bogus").isEmpty());
        assertTrue(ModRuntimeConfig.Preset.tryFromToken("bogus").isEmpty());
        assertNull(ModRuntimeConfig.Preset.fromToken("bogus"));
    }

    @Test
    void nestedLibraryResonanceCodecRoundTripsAndClampsWoodenDiscountAgainstTotalCeiling() throws Exception {
        JsonObject shipped = readBundledJson("library_resonance.json");
        LibraryResonance.Config parsed = LibraryResonance.Config.fromJson(shipped);
        assertEquals(LibraryResonance.Config.DEFAULT, parsed);

        var encoded = LibraryResonance.Config.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow();
        var decoded = LibraryResonance.Config.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(parsed, decoded, "Codec round-trip must preserve all 6 sub-records losslessly");

        // COUP-03: wooden sub-cap exceeding total cap clamps cleanly in both LibraryResonance and ModRuntimeConfig
        var overWood = new LibraryResonance.Config(
            new LibraryResonance.WoodenBookshelves(3, 45, 65, 0.9),
            new LibraryResonance.ChiseledMastery(
                new LibraryResonance.MasteryScaling(1.25, 1.25, 20, 35, 15, 1.0, 30, 0.8, 0.85, 3.5, 2, 4, 0.06),
                LibraryResonance.TomeLifecycle.DEFAULT
            ),
            LibraryResonance.EpiphanyAffinity.DEFAULT,
            LibraryResonance.WildBookWear.DEFAULT,
            LibraryResonance.TomePages.DEFAULT,
            LibraryResonance.EssenceValuation.DEFAULT
        );
        assertEquals(30, overWood.maxTotalXpDiscountPercent());
        assertEquals(30, overWood.maxWoodenDiscountPercent(),
            "maxWoodenDiscountPercent must never exceed maxTotalXpDiscountPercent");

        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.maxTotalXpDiscountPercent = 25;
        draft.maxWoodenDiscountPercent = 50;
        ModRuntimeConfig built = draft.build();
        assertEquals(25, built.maxTotalXpDiscountPercent());
        assertEquals(25, built.maxWoodenDiscountPercent(),
            "ModRuntimeConfig must clamp maxWoodenDiscountPercent <= maxTotalXpDiscountPercent");
    }

    @Test
    void bookshelfOverrideLadderValidatesMonotonicityAndDirectTierNeverRegresses() {
        Identifier enchId = Identifier.withDefaultNamespace("sharpness");
        assertNull(BookshelfProgression.validateOverrideLadder(enchId, List.of(0, 24, 12, 30, 38), 38),
            "Non-monotonic datapack override ladder must fail validation and fall back to default curve");
        assertEquals(List.of(0, 8, 16, 28, 38),
            BookshelfProgression.validateOverrideLadder(enchId, List.of(0, 8, 16, 28, 38), 38));

        var cfg = new BookshelfProgression.Config(
            true, 38, 1.25, BookshelfProgression.BlankMaxTierMode.HALF_CEIL,
            3, 2, Map.of(enchId, List.of(0, 24, 12, 30, 18)),
            12, 32, 0.5, 6, 64, 64,
            BookshelfProgression.VanillaTableMode.BOOKS, true, 6, false
        );
        for (int maxLv = 1; maxLv <= 8; maxLv++) {
            int prevBlank = 0;
            int prevUpgrade = 1;
            for (int shelves = 0; shelves <= 64; shelves++) {
                int blankTier = BookshelfProgression.maxBlankWoodenTier(cfg, enchId, maxLv, shelves);
                int upgradeTier = BookshelfProgression.maxUnlockedDirectTier(cfg, enchId, maxLv, shelves);
                assertTrue(blankTier >= prevBlank, "Blank tier regressed at shelves=" + shelves + ", maxLv=" + maxLv);
                assertTrue(upgradeTier >= prevUpgrade, "Upgrade tier regressed at shelves=" + shelves + ", maxLv=" + maxLv);
                prevBlank = blankTier;
                prevUpgrade = upgradeTier;
            }
        }
    }

    @Test
    void datapackDrivenDepletedTomeChestSpawnsAndTagFilesStayAligned() throws Exception {
        JsonObject lootJson = readBundledJson("loot_injections.json");
        assertTrue(lootJson.has("depleted_tome_chest_spawns"),
            "loot_injections.json must expose data-driven depleted_tome_chest_spawns");
        var parsedSpawns = DepletedTomeWorldMechanics.DepletedTomeChestSpawnConfig.fromLootInjectionsJson(lootJson);
        assertEquals(24, parsedSpawns.exactTables().size(), "Expected all 24 canonical chest tables in loot_injections.json");
        assertFalse(parsedSpawns.prefixRules().isEmpty(), "Expected village chest prefix rule in loot_injections.json");

        var stronghold = DepletedTomeWorldMechanics.chestSpawnSpecForTableId("minecraft", "chests/stronghold_library");
        assertNotNull(stronghold);
        assertEquals(0.55F, stronghold.probability(), 1e-6F);
        assertEquals(1, stronghold.minCount());
        assertEquals(3, stronghold.maxCount());

        var village = DepletedTomeWorldMechanics.chestSpawnSpecForTableId("minecraft", "chests/village/village_plains_house");
        assertNotNull(village);
        assertEquals(0.10F, village.probability(), 1e-6F);

        // Verify shipped tag JSON files match CetTags constants and have zero duplicate entries
        for (var tagKey : List.of(
            CetTags.Enchantments.KEYSTONE_MAJOR,
            CetTags.Enchantments.KEYSTONE_MINOR,
            CetTags.Enchantments.BROAD_APPLICABILITY,
            CetTags.Enchantments.WEAR_IMMUNE
        )) {
            Path p = Path.of("src/main/resources/data", tagKey.location().getNamespace(),
                "tags/enchantment", tagKey.location().getPath() + ".json");
            assertTrue(Files.isRegularFile(p), "Missing enchantment tag file: " + p);
            JsonObject obj = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            var arr = obj.getAsJsonArray("values");
            List<String> vals = new java.util.ArrayList<>();
            arr.forEach(e -> vals.add(e.getAsString()));
            assertEquals(vals.size(), new java.util.HashSet<>(vals).size(),
                "Duplicate entries in tag file: " + p);
        }

        // Verify wire protocol & byte budget constants
        assertEquals(18, EnchantingProtocol.CURRENT_REVISION_NUMBER);
        assertEquals("26_1_2_revision_18", EnchantingProtocol.REVISION);
        assertEquals(980_000, PayloadLimits.MAX_SAFE_ENCODED_PAYLOAD_BYTES);

        JsonObject lrJson = readBundledJson("library_resonance.json");
        var ritualCfg = InscriptionRitualMechanics.Config.fromJson(lrJson);
        assertEquals(InscriptionRitualMechanics.ApplicabilityConfig.DEFAULT, ritualCfg.applicability());
    }
}
