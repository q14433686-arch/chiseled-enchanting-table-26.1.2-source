package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CrossDomainMatrixCompletenessTest {

    // Canonical native-equipment mapping in Minecraft 26.1.2 (where vanilla anvil applies natively)
    private static final Map<String, Set<TargetRole>> NATIVE_ROLES = Map.ofEntries(
        Map.entry("minecraft:protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:fire_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:blast_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:projectile_protection", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:feather_falling", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:respiration", Set.of(TargetRole.HEAD_ARMOR)),
        Map.entry("minecraft:aqua_affinity", Set.of(TargetRole.HEAD_ARMOR)),
        Map.entry("minecraft:thorns", Set.of(TargetRole.HEAD_ARMOR, TargetRole.CHEST_ARMOR, TargetRole.LEG_ARMOR, TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:depth_strider", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:frost_walker", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:soul_speed", Set.of(TargetRole.FOOT_ARMOR)),
        Map.entry("minecraft:swift_sneak", Set.of(TargetRole.LEG_ARMOR)),
        Map.entry("minecraft:sharpness", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE)),
        Map.entry("minecraft:smite", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE)),
        Map.entry("minecraft:bane_of_arthropods", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.AXE, TargetRole.MACE)),
        Map.entry("minecraft:knockback", Set.of(TargetRole.SWORD, TargetRole.SPEAR)),
        Map.entry("minecraft:fire_aspect", Set.of(TargetRole.SWORD, TargetRole.SPEAR, TargetRole.MACE)),
        Map.entry("minecraft:looting", Set.of(TargetRole.SWORD, TargetRole.SPEAR)),
        Map.entry("minecraft:sweeping_edge", Set.of(TargetRole.SWORD)),
        Map.entry("minecraft:lunge", Set.of(TargetRole.SPEAR)),
        Map.entry("minecraft:efficiency", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:silk_touch", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:fortune", Set.of(TargetRole.DIG_TOOL, TargetRole.AXE)),
        Map.entry("minecraft:power", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:punch", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:flame", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:infinity", Set.of(TargetRole.BOW)),
        Map.entry("minecraft:luck_of_the_sea", Set.of(TargetRole.FISHING_ROD)),
        Map.entry("minecraft:lure", Set.of(TargetRole.FISHING_ROD)),
        Map.entry("minecraft:loyalty", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:impaling", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:riptide", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:channeling", Set.of(TargetRole.TRIDENT)),
        Map.entry("minecraft:multishot", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:quick_charge", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:piercing", Set.of(TargetRole.CROSSBOW)),
        Map.entry("minecraft:density", Set.of(TargetRole.MACE)),
        Map.entry("minecraft:breach", Set.of(TargetRole.MACE)),
        Map.entry("minecraft:wind_burst", Set.of(TargetRole.MACE))
    );

    @Test
    void curatedJsonTablesResolveWithoutNativeOverlapAndStayUnderFiveHundredLinesPerHandler() throws Exception {
        JsonObject sigJson = JsonParser.parseString(
            Files.readString(
                Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/signatures.json"),
                StandardCharsets.UTF_8
            )
        ).getAsJsonObject().getAsJsonObject("signatures");

        JsonObject transJson = JsonParser.parseString(
            Files.readString(
                Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/transmutations.json"),
                StandardCharsets.UTF_8
            )
        ).getAsJsonObject().getAsJsonObject("variants");

        JsonObject enUs = JsonParser.parseString(
            Files.readString(
                Path.of("src/client/resources/assets/enchantment_fusion/lang/en_us.json"),
                StandardCharsets.UTF_8
            )
        ).getAsJsonObject();

        // 1. Curated Signatures: exact parity with signatures.json & asymmetric [A ▷ B] != [B ▷ A]
        List<SignatureEntry> allSigs = SignaturePairs.allUniqueSignatures();
        assertEquals(sigJson.size(), allSigs.size(), "SignatureCatalog must load all curated signatures.json entries");
        assertTrue(allSigs.size() >= 140, "Expected curated signature catalog >= 140");
        for (SignatureEntry sig : allSigs) {
            assertTrue(enUs.has(sig.titleKey()), "Missing signature title key " + sig.titleKey());
            assertTrue(enUs.has(sig.descKey()), "Missing signature desc key " + sig.descKey());
            if (!"*".equals(sig.primaryId())) {
                Optional<SignatureEntry> forward = SignaturePairs.resolve(sig.primaryId(), sig.secondaryId());
                assertTrue(forward.isPresent() && sig.id().equals(forward.get().id()));
                Optional<SignatureEntry> reverse = SignaturePairs.resolve(sig.secondaryId(), sig.primaryId());
                reverse.ifPresent(rev -> assertNotEquals(sig.id(), rev.id(), "Symmetric signature alias forbidden: " + sig.id()));
            }
        }

        // 2. Curated Transmutations: exact parity with transmutations.json & zero native-role leaks
        Set<String> registeredIds = CrossDomainTransmutations.allVariants().stream()
            .map(TransmuteVariant::id)
            .collect(Collectors.toSet());
        assertEquals(transJson.size(), registeredIds.size(), "TransmuteCatalog must load all curated transmutations.json entries");
        assertTrue(registeredIds.size() >= 110, "Expected curated cross-domain variant catalog >= 110");

        for (Map.Entry<String, Set<TargetRole>> nativeEntry : NATIVE_ROLES.entrySet()) {
            String enchId = nativeEntry.getKey();
            for (TargetRole nativeRole : nativeEntry.getValue()) {
                assertTrue(
                    CrossDomainTransmutations.resolveForRole(enchId, nativeRole, true).isEmpty(),
                    "Native role " + nativeRole + " must NEVER trigger cross-domain transmutation for " + enchId
                );
            }
        }

        // Key ranged & flagship transmutations must resolve cleanly
        assertEquals("fan_volley", CrossDomainTransmutations.resolveForRole("minecraft:multishot", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("fan_volley", CrossDomainTransmutations.resolveForRole("minecraft:multishot", TargetRole.TRIDENT, true).orElseThrow().id());
        assertEquals("sun_piercer", CrossDomainTransmutations.resolveForRole("minecraft:piercing", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("swift_draw", CrossDomainTransmutations.resolveForRole("minecraft:quick_charge", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("warding_shove", CrossDomainTransmutations.resolveForRole("minecraft:punch", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("recoil_jump", CrossDomainTransmutations.resolveForRole("minecraft:punch", TargetRole.CROSSBOW, true).orElseThrow().id());

        for (TransmuteVariant tv : CrossDomainTransmutations.allVariants()) {
            assertTrue(enUs.has(tv.titleKey()), "Missing title key " + tv.titleKey());
            assertTrue(enUs.has(tv.descKey()), "Missing desc key " + tv.descKey());
        }

        // 3. Enforce < 500-line modular domain handler architecture across facades & subpackages
        for (Path checkDir : List.of(
            Path.of("src/main/java/enchantment_fusion/mechanics/signatures"),
            Path.of("src/main/java/enchantment_fusion/mechanics/transmute")
        )) {
            try (var stream = Files.list(checkDir)) {
                List<Path> files = stream.filter(p -> p.toString().endsWith(".java")).toList();
                assertFalse(files.isEmpty());
                for (Path jf : files) {
                    long lineCount = Files.lines(jf, StandardCharsets.UTF_8).count();
                    assertTrue(lineCount < 500, "Domain handler exceeds 500 lines (" + lineCount + "): " + jf);
                }
            }
        }
        assertTrue(Files.lines(Path.of("src/main/java/enchantment_fusion/mechanics/SignaturePairs.java")).count() < 500);
        assertTrue(Files.lines(Path.of("src/main/java/enchantment_fusion/mechanics/CrossDomainTransmutations.java")).count() < 500);
    }
}
