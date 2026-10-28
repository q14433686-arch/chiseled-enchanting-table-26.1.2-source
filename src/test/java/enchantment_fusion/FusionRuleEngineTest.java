package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.FusionRuleEngine;
import enchantment_fusion.mechanics.FusionRuleEngine.EnchantInput;
import enchantment_fusion.mechanics.FusionRuleEngine.PairEvaluation;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        JsonObject matrixJson = JsonParser.parseString(Files.readString(matrixPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject repulsiveJson = JsonParser.parseString(Files.readString(repulsivePath, StandardCharsets.UTF_8)).getAsJsonObject();
        FusionMatrixRegistry.publish(FusionMatrixRegistry.parseFromJson(matrixJson, repulsiveJson));
    }

    @Test
    void vesselSpiritCapacityRespectsGoldInnateAndHardTwoSlotCeiling() {
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
    void universalAnyGearAllowsEveryCrossDomainFusedPairEvenWhenVanillaIncompatible() {
        // primaryFitsHost = false (e.g., Loyalty III on a Diamond Helmet or Multishot I on a Pickaxe)
        EnchantInput tridentPrimary = new EnchantInput("minecraft:loyalty", 3, 3, false);
        EnchantInput swordSecondary = new EnchantInput("minecraft:sharpness", 5, 5, false);

        PairEvaluation universal = FusionRuleEngine.evaluateFusion(
            tridentPrimary, swordSecondary, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(universal.valid(), "universal_any_gear must allow [Loyalty ▷ Sharpness] on any equipment host");
        assertEquals("minecraft:loyalty", universal.resultingPair().primaryId());
        assertEquals("minecraft:sharpness", universal.resultingPair().secondaryId());

        EnchantInput multishotPrimary = new EnchantInput("minecraft:multishot", 1, 1, false);
        EnchantInput effSecondary = new EnchantInput("minecraft:efficiency", 5, 5, false);
        PairEvaluation pickEval = FusionRuleEngine.evaluateFusion(
            multishotPrimary, effSecondary, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(pickEval.valid());
        assertEquals("triple_excavate", SignaturePairs.resolve(pickEval.resultingPair()).orElseThrow().id());
    }

    @Test
    void directionalAsymmetryAndUserMandatedSignaturePairs() {
        EnchantInput fireAspect = new EnchantInput("minecraft:fire_aspect", 2, 2, true);
        EnchantInput frostWalker = new EnchantInput("minecraft:frost_walker", 2, 2, false);
        EnchantInput fortune = new EnchantInput("minecraft:fortune", 3, 3, false);
        EnchantInput protection = new EnchantInput("minecraft:protection", 4, 4, true);

        PairEvaluation steam = FusionRuleEngine.evaluateFusion(
            fireAspect, frostWalker, false, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        PairEvaluation frostfire = FusionRuleEngine.evaluateFusion(
            frostWalker, fireAspect, false, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(steam.valid());
        assertTrue(frostfire.valid());
        assertNotEquals(steam.resultingPair(), frostfire.resultingPair());

        // User-mandated exact signatures:
        assertEquals("steam_scald", SignaturePairs.resolve(steam.resultingPair()).orElseThrow().id());
        assertEquals("frostfire_cage", SignaturePairs.resolve(frostfire.resultingPair()).orElseThrow().id());

        PairEvaluation gilded = FusionRuleEngine.evaluateFusion(
            fortune, protection, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        PairEvaluation grace = FusionRuleEngine.evaluateFusion(
            protection, fortune, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertEquals("lucky_golden_body", SignaturePairs.resolve(gilded.resultingPair()).orElseThrow().id());
        assertEquals("fortune_grace", SignaturePairs.resolve(grace.resultingPair()).orElseThrow().id());
        assertTrue(SignaturePairs.allUniqueSignatures().size() >= 100);
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
        assertTrue(clampedMax.instabilityChance() > 0.30);

        PairEvaluation stabilized = FusionRuleEngine.evaluateFusion(
            sharpOver, smite5, false, FusedEnchantmentList.EMPTY, Set.of(), 1, true, false
        );
        assertEquals(0.0, stabilized.instabilityChance(), 1e-6);
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

        // Verify FusedEnchantmentList append / replaceAt / removeAt socket index management
        FusedEnchantmentList list = FusedEnchantmentList.EMPTY.append(clean);
        assertEquals(1, list.size());
        list = list.replaceAt(0, clean.flipped());
        assertEquals("minecraft:loyalty", list.pairs().get(0).primaryId());
        list = list.removeAt(0);
        assertTrue(list.isEmpty());
    }

    @Test
    void allFortyThreeVanilla2612EnchantmentsHaveExplicitMatrixProfiles() {
        var snap = FusionMatrixRegistry.current();
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
    void allElevenLocalesShareIdenticalTranslationKeySetIncludingSignaturesAndAberrations() throws Exception {
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
                    assertTrue(keys.size() >= 350, "Expected comprehensive Phase 2 translation key set");
                    assertTrue(keys.contains("block.enchantment_fusion.enchantment_fusion_table"));
                    assertTrue(keys.contains("item.enchantment_fusion.enchantment_fusion_table"));
                    for (var sig : SignaturePairs.allUniqueSignatures()) {
                        assertTrue(keys.contains(sig.titleKey()), "Missing signature title key: " + sig.titleKey());
                        assertTrue(keys.contains(sig.descKey()), "Missing signature desc key: " + sig.descKey());
                    }
                    for (String abId : AberrantRegistry.ALL_IDS) {
                        assertTrue(keys.contains(AberrantRegistry.titleKey(abId)), "Missing aberrant title key: " + abId);
                        assertTrue(keys.contains(AberrantRegistry.descKey(abId)), "Missing aberrant desc key: " + abId);
                    }
                    assertEquals(115, CrossDomainTransmutations.allVariants().size(), "Expected all 115 Phase 4 TransmuteVariants (incl. 4 minecraft:lunge variants)");
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
    void phase3CrossDomainSingleTransmutationsRespectNativeExclusionsAndSpecialRelics() {
        // 0a: Fire Aspect / Flame -> Auto-Smelt Pick & Axe Charcoal/Ash, while native Sword/Bow stay vanilla
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.SWORD, true).isEmpty());
        assertEquals("auto_smelt_pick", CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.DIG_TOOL, true).orElseThrow().id());
        assertEquals("auto_smelt_axe", CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.AXE, true).orElseThrow().id());
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:flame", TargetRole.BOW, true).isEmpty());
        assertEquals("auto_smelt_pick", CrossDomainTransmutations.resolveForRole("minecraft:flame", TargetRole.DIG_TOOL, true).orElseThrow().id());
        assertEquals("auto_smelt_axe", CrossDomainTransmutations.resolveForRole("minecraft:flame", TargetRole.AXE, true).orElseThrow().id());

        // 0b: Aqua Affinity -> Yishui on Sword, Bow, Trident, Mace; native on Helmet
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.HEAD_ARMOR, true).isEmpty());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.TRIDENT, true).orElseThrow().id());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.MACE, true).orElseThrow().id());

        // 5, 7, 14: Soul Speed combo, Thorns Serrated Hook, Fortune $3 Bill
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:soul_speed", TargetRole.FOOT_ARMOR, true).isEmpty());
        assertEquals("soul_chase", CrossDomainTransmutations.resolveForRole("minecraft:soul_speed", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("soul_escape", CrossDomainTransmutations.resolveForRole("minecraft:soul_speed", TargetRole.CHEST_ARMOR, true).orElseThrow().id());
        assertEquals("soul_seeker_arrow", CrossDomainTransmutations.resolveForRole("minecraft:soul_speed", TargetRole.BOW, true).orElseThrow().id());

        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:thorns", TargetRole.CHEST_ARMOR, true).isEmpty());
        assertEquals("serrated_hook", CrossDomainTransmutations.resolveForRole("minecraft:thorns", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("barbed_arrow", CrossDomainTransmutations.resolveForRole("minecraft:thorns", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("spiked_shield", CrossDomainTransmutations.resolveForRole("minecraft:thorns", TargetRole.SHIELD, true).orElseThrow().id());

        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:fortune", TargetRole.DIG_TOOL, true).isEmpty());
        assertEquals("three_dollar_luck", CrossDomainTransmutations.resolveForRole("minecraft:fortune", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("three_dollar_luck", CrossDomainTransmutations.resolveForRole("minecraft:fortune", TargetRole.BOW, true).orElseThrow().id());

        // 36, 37a, 37b: Universal Vanishing stays vanilla; Unbreaking & Mending transmute only on non-damageable relics
        for (TargetRole role : TargetRole.values()) {
            assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:vanishing_curse", role, true).isEmpty());
            assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:vanishing_curse", role, false).isEmpty());
        }
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:unbreaking", TargetRole.SWORD, true).isEmpty());
        assertEquals("unbreaking_totem", CrossDomainTransmutations.resolveForRole("minecraft:unbreaking", TargetRole.TOTEM, false).orElseThrow().id());
        assertEquals("unbreaking_ration", CrossDomainTransmutations.resolveForRole("minecraft:unbreaking", TargetRole.CONSUMABLE, false).orElseThrow().id());

        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:mending", TargetRole.SWORD, true).isEmpty());
        assertEquals("mending_xp_bottle", CrossDomainTransmutations.resolveForRole("minecraft:mending", TargetRole.GLASS_BOTTLE, false).orElseThrow().id());
        assertEquals("mending_replenish", CrossDomainTransmutations.resolveForRole("minecraft:mending", TargetRole.CONSUMABLE, false).orElseThrow().id());
        assertEquals("mending_vitality", CrossDomainTransmutations.resolveForRole("minecraft:mending", TargetRole.TOTEM, false).orElseThrow().id());

        // Phase 4 (26.1.2): Spear native vs cross-domain & minecraft:lunge transmutations
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.SPEAR, true).isEmpty());
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:sharpness", TargetRole.SPEAR, true).isEmpty());
        assertTrue(CrossDomainTransmutations.resolveForRole("minecraft:fire_aspect", TargetRole.SPEAR, true).isEmpty());
        assertEquals("yishui", CrossDomainTransmutations.resolveForRole("minecraft:aqua_affinity", TargetRole.SPEAR, true).orElseThrow().id());
        assertEquals("heavy_cleave", CrossDomainTransmutations.resolveForRole("minecraft:sweeping_edge", TargetRole.SPEAR, true).orElseThrow().id());
        assertEquals("lunge_dash_strike", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.SWORD, true).orElseThrow().id());
        assertEquals("lunge_vault_shot", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.BOW, true).orElseThrow().id());
        assertEquals("lunge_wind_stride", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.FOOT_ARMOR, true).orElseThrow().id());
        assertEquals("lunge_tunnel_rush", CrossDomainTransmutations.resolveForRole("minecraft:lunge", TargetRole.DIG_TOOL, true).orElseThrow().id());
    }

    @Test
    void phase4TieredRiskEconomyThreeStageDiscoveryAndFrostedIceGuards() throws Exception {
        // 1. Tiered reagent quotes + Stabilizer reduction + 50% normal failure refund
        var lowQuote = enchantment_fusion.mechanics.FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 1, 5, "minecraft:knockback", 1, 2, false, false, 0, false
        );
        assertEquals(enchantment_fusion.mechanics.FusionEconomyRules.ReagentKind.LAPIS, lowQuote.reagent());
        assertFalse(lowQuote.highRiskTier());
        assertTrue(lowQuote.failureChance() > 0.05);
        assertTrue(lowQuote.halfChargedXp() <= lowQuote.xpLevels() / 2);

        var lowStabilized = enchantment_fusion.mechanics.FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 1, 5, "minecraft:knockback", 1, 2, false, false, 1, false
        );
        assertEquals(0.0, lowStabilized.failureChance(), 1e-6);

        var repulsiveQuote = enchantment_fusion.mechanics.FusionEconomyRules.computeSynthesisQuote(
            "minecraft:sharpness", 5, 5, "minecraft:smite", 5, 5, true, true, 0, false
        );
        assertEquals(enchantment_fusion.mechanics.FusionEconomyRules.ReagentKind.EMERALD, repulsiveQuote.reagent());
        assertTrue(repulsiveQuote.highRiskTier());
        assertTrue(repulsiveQuote.failureChance() >= 0.40);

        var lungeSig = SignaturePairs.resolve("minecraft:lunge", "minecraft:wind_burst").orElseThrow();
        assertEquals("gale_vault_thrust", lungeSig.id());

        // 2. Three-stage Compendium discovery state roundtrip + legacy compatibility
        enchantment_fusion.mechanics.CompendiumDiscoveryTracker.applyClientSync(List.of(
            "sig/gale_vault_thrust=1",
            "transmute/yishui=2",
            "minecraft:sharpness>minecraft:loyalty"
        ));
        assertEquals(1, enchantment_fusion.mechanics.CompendiumDiscoveryTracker.clientState("sig/gale_vault_thrust"));
        assertEquals(2, enchantment_fusion.mechanics.CompendiumDiscoveryTracker.clientState("transmute/yishui"));
        assertTrue(enchantment_fusion.mechanics.CompendiumDiscoveryTracker.isClientDiscovered("minecraft:sharpness", "minecraft:loyalty"));

        // 3. Freezing effects must use Blocks.FROSTED_ICE (zero permanent Blocks.ICE / PACKED_ICE cages)
        String abSrc = Files.readString(Path.of("src/main/java/enchantment_fusion/mechanics/AberrantRegistry.java"), StandardCharsets.UTF_8);
        String sigSrc = Files.readString(Path.of("src/main/java/enchantment_fusion/mechanics/SignaturePairs.java"), StandardCharsets.UTF_8);
        assertTrue(abSrc.contains("Blocks.FROSTED_ICE"), "AberrantRegistry must place Blocks.FROSTED_ICE");
        assertFalse(abSrc.contains("Blocks.PACKED_ICE"), "Permanent PACKED_ICE cages forbidden");
        assertFalse(sigSrc.contains("Blocks.ICE."), "Permanent Blocks.ICE cages forbidden in SignaturePairs");
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
