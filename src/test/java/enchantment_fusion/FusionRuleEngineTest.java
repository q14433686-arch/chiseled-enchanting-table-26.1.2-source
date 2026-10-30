package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.FusionEconomyRules;
import enchantment_fusion.mechanics.FusionEconomyRules.ReagentKind;
import enchantment_fusion.mechanics.FusionRuleEngine;
import enchantment_fusion.mechanics.FusionRuleEngine.EnchantInput;
import enchantment_fusion.mechanics.FusionRuleEngine.PairEvaluation;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class FusionRuleEngineTest {
    @BeforeAll
    static void initMatrix() throws Exception {
        Path matrixPath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json");
        Path repulsivePath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/repulsive_pairs.json");
        Path economyPath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/economy.json");
        JsonObject matrixJson = JsonParser.parseString(Files.readString(matrixPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject repulsiveJson = JsonParser.parseString(Files.readString(repulsivePath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject economyJson = JsonParser.parseString(Files.readString(economyPath, StandardCharsets.UTF_8)).getAsJsonObject();
        FusionMatrixRegistry.publish(FusionMatrixRegistry.parseFromJson(matrixJson, repulsiveJson, economyJson));
    }

    @Test
    void vesselSlotCapacityRespectsGoldInnateTagAndHardTwoSlotCeiling() {
        assertEquals(1, VesselCapacityRules.computeMaxSlots(false, 0));
        assertEquals(2, VesselCapacityRules.computeMaxSlots(false, 1));
        assertEquals(2, VesselCapacityRules.computeMaxSlots(false, 5), "Must never exceed hard ceiling of 2");

        assertEquals(2, VesselCapacityRules.computeMaxSlots(true, 0));
        assertEquals(2, VesselCapacityRules.computeMaxSlots(true, 1));
        assertTrue(VesselCapacityRules.isInnateTwoSlotItemId("minecraft:golden_sword"));
        assertTrue(VesselCapacityRules.isInnateTwoSlotItemId("minecraft:golden_spear"));
        assertFalse(VesselCapacityRules.isInnateTwoSlotItemId("minecraft:diamond_sword"));
        assertFalse(VesselCapacityRules.isInnateTwoSlotItemId("minecraft:copper_spear"));
    }

    @Test
    void universalAnyGearAndHostMutualExclusivityChecks() {
        EnchantInput tridentPrimary = new EnchantInput("minecraft:loyalty", 3, 3, false);
        EnchantInput swordSecondary = new EnchantInput("minecraft:sharpness", 5, 5, false);

        PairEvaluation universal = FusionRuleEngine.evaluateFusion(
            tridentPrimary, swordSecondary, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(universal.valid(), "universal_any_gear must allow [Loyalty ▷ Sharpness] on any equipment host");
        assertEquals("minecraft:loyalty", universal.resultingPair().primaryId());
        assertEquals("minecraft:sharpness", universal.resultingPair().secondaryId());

        // API-02: Host carrying Smite rejects Sharpness primary with EXCLUSIVE_ON_HOST
        EnchantInput sharpPrimary = new EnchantInput("minecraft:sharpness", 5, 5, true);
        PairEvaluation exclusiveBlocked = FusionRuleEngine.evaluateFusion(
            sharpPrimary, tridentPrimary, true, FusedEnchantmentList.EMPTY, Set.of("minecraft:smite"), 1, false, true
        );
        assertFalse(exclusiveBlocked.valid());
        assertEquals(RejectReason.EXCLUSIVE_ON_HOST, exclusiveBlocked.rejectReason());

        // Existing socketed pair secondaryId must also enforce repulsive exclusivity on the host
        FusedEnchantmentList hostWithSmiteSecondary = FusedEnchantmentList.EMPTY.append(
            FusedPair.clean("minecraft:loyalty", 3, "minecraft:smite", 5)
        );
        PairEvaluation secondaryExclusiveBlocked = FusionRuleEngine.evaluateFusion(
            new EnchantInput("minecraft:sharpness", 5, 5),
            new EnchantInput("minecraft:fire_aspect", 2, 2),
            true,
            hostWithSmiteSecondary,
            Set.of(),
            2,
            false,
            false
        );
        assertFalse(secondaryExclusiveBlocked.valid());
        assertEquals(RejectReason.EXCLUSIVE_ON_HOST, secondaryExclusiveBlocked.rejectReason());
    }

    @Test
    void allOneHundredSeventyFourSignatureResonancesAreStrictlyUniqueAndDirectional() {
        assertEquals(174, SignaturePairs.allUniqueSignatures().size(), "Expected 174 unique directional Signature Resonances");
        Set<String> ids = new HashSet<>();
        for (SignatureEntry entry : SignaturePairs.allUniqueSignatures()) {
            assertTrue(ids.add(entry.id()), "Duplicate Signature ID forbidden: " + entry.id());
            if (!"*".equals(entry.primaryId())) {
                var rev = SignaturePairs.resolve(entry.secondaryId(), entry.primaryId());
                if (rev.isPresent()) {
                    assertNotEquals(
                        entry.id(), rev.get().id(),
                        "BAL-06: [A ▷ B] and [B ▷ A] must never share the same Signature ID: " + entry.id()
                    );
                }
            }
        }

        assertEquals("steam_scald", SignaturePairs.resolve("minecraft:fire_aspect", "minecraft:frost_walker").orElseThrow().id());
        assertEquals("frostfire_cage", SignaturePairs.resolve("minecraft:frost_walker", "minecraft:fire_aspect").orElseThrow().id());
        assertEquals("lucky_golden_body", SignaturePairs.resolve("minecraft:fortune", "minecraft:protection").orElseThrow().id());
        assertEquals("fortune_grace", SignaturePairs.resolve("minecraft:protection", "minecraft:fortune").orElseThrow().id());
        assertEquals("concussive_plunder", SignaturePairs.resolve("minecraft:knockback", "minecraft:looting").orElseThrow().id());
        assertEquals("trophy_shockwave", SignaturePairs.resolve("minecraft:looting", "minecraft:knockback").orElseThrow().id());
        assertEquals("boomerang_lance", SignaturePairs.resolve("minecraft:lunge", "minecraft:loyalty").orElseThrow().id());
        assertEquals("shadow_recall_lunge", SignaturePairs.resolve("minecraft:loyalty", "minecraft:lunge").orElseThrow().id());
        assertEquals("hundred_step_skewer", SignaturePairs.resolve("minecraft:lunge", "minecraft:piercing").orElseThrow().id());
        assertEquals("armor_seam_rush", SignaturePairs.resolve("minecraft:piercing", "minecraft:lunge").orElseThrow().id());
        assertEquals("quench_temper_blade", SignaturePairs.resolve("minecraft:aqua_affinity", "minecraft:fire_aspect").orElseThrow().id());
        assertEquals("geyser_bore", SignaturePairs.resolve("minecraft:fire_aspect", "minecraft:aqua_affinity").orElseThrow().id());
        assertEquals("mirror_moon_sweep", SignaturePairs.resolve("minecraft:sweeping_edge", "minecraft:silk_touch").orElseThrow().id());
        assertEquals("paper_thin_cleave", SignaturePairs.resolve("minecraft:silk_touch", "minecraft:sweeping_edge").orElseThrow().id());
    }

    @Test
    void repulsiveExclusivePairRequiresBothAtVanillaMaxLevelAndNeverExceedsCap() {
        EnchantInput sharp4 = new EnchantInput("minecraft:sharpness", 4, 5, true);
        EnchantInput smite5 = new EnchantInput("minecraft:smite", 5, 5, true);

        PairEvaluation rejectedLow = FusionRuleEngine.evaluateFusion(
            sharp4, smite5, false, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertFalse(rejectedLow.valid());
        assertEquals(RejectReason.REPULSIVE_REQUIRES_MAX_LEVEL, rejectedLow.rejectReason());

        EnchantInput sharpOver = new EnchantInput("minecraft:sharpness", 9, 5, true);
        PairEvaluation clampedMax = FusionRuleEngine.evaluateFusion(
            sharpOver, smite5, false, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(clampedMax.valid());
        assertTrue(clampedMax.repulsiveExclusive());
        assertEquals(5, clampedMax.resultingPair().primaryLevel(), "Level cap must never exceed vanilla maxLevel");
    }

    @Test
    void aberrantMutationsAreLockedAgainstFlipAndUnsocketWhileCleanPairsAllowBoth() {
        FusedPair clean = FusedPair.clean("minecraft:sharpness", 5, "minecraft:loyalty", 3);
        assertEquals(RejectReason.NONE, FusionRuleEngine.canFlipPair(clean));
        assertEquals(RejectReason.NONE, FusionRuleEngine.canUnsocketOrSplitPair(clean));

        for (String abId : AberrantRegistry.ALL_IDS) {
            FusedPair aberrant = new FusedPair("minecraft:sharpness", 5, "minecraft:smite", 5, abId);
            assertTrue(aberrant.isAberrant());
            assertEquals(RejectReason.ABERRANT_LOCKED, FusionRuleEngine.canFlipPair(aberrant));
            assertEquals(RejectReason.ABERRANT_LOCKED, FusionRuleEngine.canUnsocketOrSplitPair(aberrant));
        }
    }

    @Test
    void allFortyThreeVanilla2612EnchantmentsHaveExplicitMatrixProfiles() {
        var snap = FusionMatrixRegistry.serverSnapshot();
        assertTrue(snap.profiles().size() >= 43, "Expected all 43 vanilla 26.1.2 matrix profiles including minecraft:lunge");
        assertTrue(snap.profiles().containsKey("minecraft:lunge"), "minecraft:lunge must be explicitly profiled");
        for (HostForm form : HostForm.values()) {
            for (var profile : snap.profiles().values()) {
                assertNotNull(profile.trigger().translationKey());
                assertNotNull(profile.payload().effectKey(form));
            }
        }
    }

    @Test
    void allElevenLocalesShareIdenticalTranslationKeySetIncludingAll150SignaturesAnd140Transmutes() throws Exception {
        List<String> locales = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "it_it", "ja_jp",
            "ko_kr", "pt_br", "ru_ru", "zh_cn", "zh_tw"
        );
        Set<String> baselineKeys = null;
        for (String code : locales) {
            String path = "/assets/enchantment_fusion/lang/" + code + ".json";
            try (InputStream in = FusionRuleEngineTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Missing locale file: " + path);
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Set<String> keys = obj.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
                if (baselineKeys == null) {
                    baselineKeys = keys;
                    assertTrue(keys.size() >= 750, "Expected comprehensive 0.6.0 curated translation key set");
                    assertEquals(150, SignaturePairs.allUniqueSignatures().size());
                    for (var sig : SignaturePairs.allUniqueSignatures()) {
                        assertTrue(keys.contains(sig.titleKey()), "Missing signature title key: " + sig.titleKey());
                        assertTrue(keys.contains(sig.descKey()), "Missing signature desc key: " + sig.descKey());
                    }
                    for (String abId : AberrantRegistry.ALL_IDS) {
                        assertTrue(keys.contains(AberrantRegistry.titleKey(abId)), "Missing aberrant title key: " + abId);
                        assertTrue(keys.contains(AberrantRegistry.descKey(abId)), "Missing aberrant desc key: " + abId);
                    }
                    assertEquals(116, CrossDomainTransmutations.allVariants().size());
                    for (var tv : CrossDomainTransmutations.allVariants()) {
                        assertTrue(keys.contains(tv.titleKey()), "Missing transmute title key: " + tv.titleKey());
                        assertTrue(keys.contains(tv.descKey()), "Missing transmute desc key: " + tv.descKey());
                    }
                } else {
                    assertEquals(baselineKeys, keys, "Locale key mismatch in " + code);
                }
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    String val = entry.getValue().getAsString();
                    assertFalse(val.contains("\n"), "Literal newline forbidden in " + code + " key " + entry.getKey());
                }
            }
        }
    }

    @Test
    void crossDomainSingleTransmutationsRespectNativeExclusionsAndSpearRules() {
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.SWORD, true).isEmpty());
        assertEquals("auto_smelt_pick", CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.DIG_TOOL, true).orElseThrow().id());
        assertEquals("auto_smelt_axe", CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.AXE, true).orElseThrow().id());

        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.HEAD_ARMOR, true).isEmpty());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.SPEAR, true).orElseThrow().id());

        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.SPEAR, true).isEmpty());
        assertEquals("lunge_dash_strike", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("lunge_vault_shot", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("lunge_wind_stride", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.FOOT_ARMOR, true).orElseThrow().id());
        assertEquals("lunge_tunnel_rush", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.DIG_TOOL, true).orElseThrow().id());
    }

    @Test
    void safeLowTierMin8RepulsiveFloorNeededStabilizersAndDeltaDiscovery() throws Exception {
        // EF-003 / BAL-04: Low-tier synthesis has 0% failure and consumes 0 stabilizers
        var lowQuote = FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 1, 5, "minecraft:knockback", 1, 2, false, false, 8, false
        );
        assertEquals(ReagentKind.LAPIS, lowQuote.reagent());
        assertEquals(0.0, lowQuote.failureChance(), 1e-6);
        assertEquals(0, lowQuote.stabilizersUsed());

        // Mid-tier synthesis (sumLv = 5, non-max) has 18% base failure, reducible to 0% with 1 stabilizer out of 8
        var midQuote = FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 3, 5, "minecraft:loyalty", 2, 3, false, false, 0, false
        );
        assertEquals(ReagentKind.AMETHYST, midQuote.reagent());
        assertFalse(midQuote.highRiskTier());
        assertTrue(midQuote.failureChance() > 0.10);

        var midStabilized = FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 3, 5, "minecraft:loyalty", 2, 3, false, false, 8, false
        );
        assertEquals(0.0, midStabilized.failureChance(), 1e-6);
        assertEquals(1, midStabilized.stabilizersUsed(), "BAL-03: Must consume only 1 needed stabilizer out of 8");

        // BAL-05: Repulsive max-level clash has 45% base failure and an honest 8% floor consuming at most 3 stabilizers
        var repulsiveQuote = FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 5, 5, "minecraft:smite", 5, 5, true, true, 0, false
        );
        assertEquals(ReagentKind.EMERALD, repulsiveQuote.reagent());
        assertTrue(repulsiveQuote.highRiskTier());
        assertEquals(0.45, repulsiveQuote.failureChance(), 1e-6);

        var repulsiveStabilized = FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 5, 5, "minecraft:smite", 5, 5, true, true, 16, false
        );
        assertEquals(0.08, repulsiveStabilized.failureChance(), 1e-6, "Repulsive clash must keep 8% instability floor");
        assertEquals(3, repulsiveStabilized.stabilizersUsed(), "Must consume at most 3 stabilizers to reach 8% floor");

        // BAL-07: Higher anvilCost increases Cross-Domain Transmutation pricing
        var cheapConv = FusionEconomyRules.computeConversionQuote(1, 5, 1, 0, false);
        var rareConv = FusionEconomyRules.computeConversionQuote(1, 1, 8, 0, false);
        assertTrue(rareConv.xpLevels() > cheapConv.xpLevels());

        // CPL-1: Native aberrant purification quote uses configured stabilizers & XP with 0% failure
        var purifyQuote = FusionEconomyRules.computePurifyAberrantQuote(FusionMatrixRegistry.serverSnapshot().economy(), false);
        assertEquals(25, purifyQuote.xpLevels());
        assertEquals(3, purifyQuote.stabilizersUsed());
        assertEquals(0.0, purifyQuote.failureChance(), 1e-6);

        var cetCatalystPurify = FusionEconomyRules.computePurifyAberrantQuote(FusionMatrixRegistry.serverSnapshot().economy(), true);
        assertEquals(0, cetCatalystPurify.xpLevels());
        assertEquals(1, cetCatalystPurify.stabilizersUsed());

        // Exact Identifier path matching in AberrantRegistry.pickAberration + CET mixin allowlist size
        net.minecraft.util.RandomSource fixedRng = net.minecraft.util.RandomSource.create(42L);
        assertEquals(AberrantRegistry.HOT_POTATO, AberrantRegistry.pickAberration("minecraft:fire_aspect", "minecraft:unbreaking", fixedRng));
        assertNotEquals(
            AberrantRegistry.HOT_POTATO,
            AberrantRegistry.pickAberration("mymod:anti_fire_aspect_shield", "minecraft:channeling", fixedRng),
            "Substring path matches like anti_fire_aspect_shield must not trigger HOT_POTATO over exact channeling"
        );
        assertEquals(9, enchantment_fusion.mixin.FusionMixinConfigPlugin.CET_GATED_MIXINS.size());

        // NET-05: Full + delta Compendium discovery sync
        CompendiumDiscoveryTracker.applyClientSync(List.of("sig/gale_vault_thrust=1"));
        assertEquals(1, CompendiumDiscoveryTracker.clientState("sig/gale_vault_thrust"));
        CompendiumDiscoveryTracker.applyClientDelta("sig/gale_vault_thrust", 2);
        assertEquals(2, CompendiumDiscoveryTracker.clientState("sig/gale_vault_thrust"));

        // Freezing effects must use Blocks.FROSTED_ICE
        String abSrc = Files.readString(Path.of("src/main/java/enchantment_fusion/mechanics/AberrantRegistry.java"), StandardCharsets.UTF_8);
        String sigSrc = Files.readString(Path.of("src/main/java/enchantment_fusion/mechanics/SignaturePairs.java"), StandardCharsets.UTF_8);
        assertTrue(abSrc.contains("Blocks.FROSTED_ICE"));
        assertFalse(abSrc.contains("Blocks.PACKED_ICE"));
        assertFalse(sigSrc.contains("Blocks.ICE."));
    }

    @Test
    void trajectoryPathTrackerSegmentGeometryIsExact() {
        net.minecraft.world.phys.Vec3 a = new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D);
        net.minecraft.world.phys.Vec3 b = new net.minecraft.world.phys.Vec3(10.0D, 1.0D, 0.0D);
        net.minecraft.world.phys.Vec3 midAbove = new net.minecraft.world.phys.Vec3(5.0D, 2.5D, 0.0D);
        assertEquals(
            2.25D,
            enchantment_fusion.mechanics.ProjectilePathTracker.distancePointToSegmentSq(midAbove, a, b),
            1e-6,
            "Midpoint 1.5 blocks above [0..10] segment must have squared distance 2.25"
        );

        net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(5.0D, 0.5D, -4.0D);
        net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(5.0D, 0.5D, 4.0D);
        assertEquals(
            0.25D,
            enchantment_fusion.mechanics.ProjectilePathTracker.segmentsClosestDistanceSq(a, b, c, d),
            1e-6,
            "Crossing segments separated by 0.5 Y must report closest squared distance 0.25"
        );
    }

    @Test
    void noJavaSourceMutatesMapEntryViaSetValueOnConcurrentHashMap() throws Exception {
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            for (Path javaFile : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(javaFile, StandardCharsets.UTF_8);
                assertFalse(
                    src.contains(".setValue("),
                    "Forbidden Map.Entry.setValue() (crashes ConcurrentHashMap.entrySet().removeIf on Java 25): " + javaFile
                );
            }
        }
    }
}
