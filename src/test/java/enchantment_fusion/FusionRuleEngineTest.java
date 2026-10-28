package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.FusionRuleEngine;
import enchantment_fusion.mechanics.FusionRuleEngine.EnchantInput;
import enchantment_fusion.mechanics.FusionRuleEngine.PairEvaluation;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FusionRuleEngineTest {
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
    }

    @Test
    void primaryMustFitHostWhileSecondaryAllowsFullCrossDomainGrafting() {
        EnchantInput pickPrimaryOnSword = new EnchantInput("minecraft:efficiency", 5, 5, false);
        EnchantInput tridentSecondary = new EnchantInput("minecraft:loyalty", 3, 3, false);
        PairEvaluation invalid = FusionRuleEngine.evaluateFusion(
            pickPrimaryOnSword, tridentSecondary, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertFalse(invalid.valid());
        assertEquals(RejectReason.PRIMARY_INCOMPATIBLE_HOST, invalid.rejectReason());

        EnchantInput swordPrimary = new EnchantInput("minecraft:sharpness", 3, 5, true);
        EnchantInput crossbowSecondary = new EnchantInput("minecraft:multishot", 1, 1, false);
        PairEvaluation crossDomain = FusionRuleEngine.evaluateFusion(
            swordPrimary, crossbowSecondary, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(crossDomain.valid(), "Secondary B must allow full cross-domain grafting even at non-max Primary level");
        assertEquals(0.0, crossDomain.instabilityChance());
    }

    @Test
    void repulsiveExclusiveClashRequiresBothAtMaxLevelAndStabilizerZerosInstability() {
        EnchantInput sharp4 = new EnchantInput("minecraft:sharpness", 4, 5, true);
        EnchantInput smite5 = new EnchantInput("minecraft:smite", 5, 5, true);
        PairEvaluation underMax = FusionRuleEngine.evaluateFusion(
            sharp4, smite5, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertFalse(underMax.valid());
        assertEquals(RejectReason.REPULSIVE_REQUIRES_MAX_LEVEL, underMax.rejectReason());

        EnchantInput sharp5 = new EnchantInput("minecraft:sharpness", 5, 5, true);
        PairEvaluation unstabilized = FusionRuleEngine.evaluateFusion(
            sharp5, smite5, true, FusedEnchantmentList.EMPTY, Set.of(), 1, false, false
        );
        assertTrue(unstabilized.valid());
        assertTrue(unstabilized.repulsiveExclusive());
        assertEquals(0.35, unstabilized.instabilityChance(), 1e-6);

        PairEvaluation stabilized = FusionRuleEngine.evaluateFusion(
            sharp5, smite5, true, FusedEnchantmentList.EMPTY, Set.of(), 1, true, false
        );
        assertTrue(stabilized.valid());
        assertTrue(stabilized.repulsiveExclusive());
        assertEquals(0.0, stabilized.instabilityChance(), 1e-6);
    }

    @Test
    void vesselCapacityFullAndAberrantLockAreEnforced() {
        FusedEnchantmentList oneFilled = FusedEnchantmentList.EMPTY.append(
            new FusedPair("minecraft:sharpness", 5, "minecraft:fire_aspect", 2, "")
        );
        EnchantInput knockback = new EnchantInput("minecraft:knockback", 2, 2, true);
        EnchantInput windBurst = new EnchantInput("minecraft:wind_burst", 2, 3, false);

        PairEvaluation blockedOnOneSlotGear = FusionRuleEngine.evaluateFusion(
            knockback, windBurst, true, oneFilled, Set.of("minecraft:sharpness"), 1, false, false
        );
        assertFalse(blockedOnOneSlotGear.valid());
        assertEquals(RejectReason.VESSEL_CAPACITY_FULL, blockedOnOneSlotGear.rejectReason());

        PairEvaluation allowedOnTwoSlotGoldGear = FusionRuleEngine.evaluateFusion(
            knockback, windBurst, true, oneFilled, Set.of("minecraft:sharpness"), 2, false, false
        );
        assertTrue(allowedOnTwoSlotGoldGear.valid());

        FusedPair aberrant = new FusedPair("minecraft:infinity", 1, "minecraft:mending", 1, "perpetual_overdraw");
        assertEquals(RejectReason.ABERRANT_LOCKED, FusionRuleEngine.canSplitPair(aberrant, true));
        assertEquals(RejectReason.ABERRANT_LOCKED, FusionRuleEngine.canFlipPair(aberrant, false, true, true));
        assertEquals(
            RejectReason.NONE,
            FusionRuleEngine.canSplitPair(new FusedPair("minecraft:sharpness", 5, "minecraft:loyalty", 3, ""), true)
        );
    }
}
