package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeImpulseMath;
import enchantment_fusion.mechanics.signatures.MeleeImpulseMath.Box;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.mechanics.signatures.MeleeDisarmSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeImpulseMathTest {
    private static final Box MOVING = new Box(0, 0, 0, 1, 1, 1);
    @Test void sweptContactRequiresAnActualCrossingRatherThanProximity() {
        assertEquals(0.5D, MeleeImpulseMath.contact(MOVING, 2, 0, 0, new Box(2, 0, 0, 3, 1, 1)), 1.0E-9D);
        assertEquals(1.0D, MeleeImpulseMath.contact(MOVING, 1, 0, 0, new Box(2, 0, 0, 3, 1, 1)), 1.0E-9D);
        assertEquals(0.0D, MeleeImpulseMath.contact(MOVING, 1, 0, 0, new Box(1, 0, 0, 2, 1, 1)), 1.0E-9D);
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, -1, 0, 0, new Box(1, 0, 0, 2, 1, 1))));
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, 1, 0, 0, new Box(0.5, 0, 0, 1.5, 1, 1))));
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, 2, 0, 0, new Box(2, 2, 0, 3, 3, 1))));
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, 2, 0, 0, new Box(2, 0, 2, 3, 1, 3))));
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, Double.NaN, 0, 0, new Box(2, 0, 0, 3, 1, 1))));
        assertFalse(Double.isFinite(MeleeImpulseMath.contact(MOVING, 1, 0, 0, new Box(1, 0, 0, 1, 1, 1))));
        assertEquals(0.5D, MeleeImpulseMath.contact(new Box(2, 0, 0, 3, 1, 1), -2, 0, 0, MOVING), 1.0E-9D);
    }
    @Test void motionMustBeFiniteForwardAndNotTeleportSized() {
        assertTrue(MeleeImpulseMath.trackedMotion(0.1, 0, 1, 0));
        assertFalse(MeleeImpulseMath.trackedMotion(0.01, 0, 1, 0));
        assertFalse(MeleeImpulseMath.trackedMotion(-0.1, 0, 1, 0));
        assertFalse(MeleeImpulseMath.trackedMotion(3, 0, 1, 0));
        assertFalse(MeleeImpulseMath.trackedMotion(Double.POSITIVE_INFINITY, 0, 1, 0));
        assertEquals(0, MeleeImpulseMath.transferStrength(Double.NaN, 2));
        assertTrue(MeleeImpulseMath.transferStrength(0.4, 3) > 0);
        assertTrue(MeleeImpulseMath.transferStrength(8, 255) <= 1.25F);
        assertTrue(MeleeImpulseMath.bladeDamage(5, 3) > MeleeImpulseMath.bladeDamage(1, 1));
        assertEquals(MeleeImpulseMath.bladeDamage(10, 10), MeleeImpulseMath.bladeDamage(255, 255));
    }
    @Test void sixSelectedDirectionsAndCarrierFamilies() {
        for (String id : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            FusedPair attack = FusedPair.clean(id, 4, MeleeImpulseSkills.KNOCKBACK, 2), reverse = attack.flipped();
            assertTrue(MeleeImpulseSkills.matches(attack)); assertTrue(MeleeImpulseSkills.matches(reverse));
            assertTrue(MeleeImpulseSkills.isAttackFirst(attack)); assertTrue(MeleeImpulseSkills.isKnockbackFirst(reverse));
        }
        assertFalse(MeleeImpulseSkills.matches(FusedPair.clean("minecraft:fire_aspect", 1, MeleeImpulseSkills.KNOCKBACK, 1)));
        assertTrue(MeleeImpulseSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
        assertTrue(MeleeImpulseSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(MeleeImpulseSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
    }
    @Test void pursuitOnlyClaimsItsSelectedOrderedSignature() {
        SignatureCatalog.reloadFromJson(null);
        FusedPair pair = FusedPair.clean(MeleeImpulseSkills.KNOCKBACK, 2, MeleeImpulseSkills.LOOTING, 3);
        try {
            assertTrue(MeleeImpulseSkills.isPursuit(pair));
            assertTrue(MeleeImpulseSkills.matches(pair));
            assertFalse(MeleeImpulseSkills.isPursuit(pair.flipped()));
            assertTrue(MeleeDisarmSkills.isPair(pair.flipped()));
            assertTrue(MeleeImpulseSkills.pursuitSignature(SignatureCatalog.resolve(pair).orElseThrow()));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:knockback","secondary":"minecraft:looting"}}}
                """).getAsJsonObject());
            assertFalse(MeleeImpulseSkills.isPursuit(pair));
            assertFalse(MeleeImpulseSkills.matches(pair));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"concussive_plunder":{"primary":"minecraft:knockback","secondary":"minecraft:smite"}}}
                """).getAsJsonObject());
            assertFalse(MeleeImpulseSkills.pursuitSignature(SignatureCatalog.resolve(
                FusedPair.clean(MeleeImpulseSkills.KNOCKBACK, 1, MeleeAttackThemes.SMITE, 1)).orElseThrow()));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void disarmUsesOnlyTheSelectedReverseAndPreservesDataPackReplacements() {
        SignatureCatalog.reloadFromJson(null);
        FusedPair pair = FusedPair.clean(MeleeDisarmSkills.LOOTING, 3, MeleeDisarmSkills.KNOCKBACK, 2);
        try {
            assertTrue(MeleeDisarmSkills.isPair(pair));
            assertTrue(MeleeImpulseSkills.matches(pair));
            assertFalse(MeleeDisarmSkills.isPair(pair.flipped()));
            assertTrue(MeleeDisarmSkills.selectedSignature(SignatureCatalog.resolve(pair).orElseThrow()));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:looting","secondary":"minecraft:knockback"}}}
                """).getAsJsonObject());
            assertFalse(MeleeDisarmSkills.isPair(pair));
            assertFalse(MeleeImpulseSkills.matches(pair));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void disarmDoesNotTakePreservedOrInvalidEquipmentChances() {
        assertTrue(MeleeDisarmSkills.ordinaryDropChance(0.085F));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(0.0F));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(-0.1F));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(1.0F));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(2.0F));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(Float.NaN));
        assertFalse(MeleeDisarmSkills.ordinaryDropChance(Float.POSITIVE_INFINITY));
    }
    @Test void pursuitRequiresFiniteRealRemainingMomentumAndCapsItsImpulse() {
        assertEquals(0.0F, MeleeImpulseMath.pursuitStrength(Double.NaN, 2, 3));
        assertEquals(0.0F, MeleeImpulseMath.pursuitStrength(Double.POSITIVE_INFINITY, 2, 3));
        assertEquals(0.0F, MeleeImpulseMath.pursuitStrength(-0.5D, 2, 3));
        assertEquals(0.0F, MeleeImpulseMath.pursuitStrength(0.024D, 2, 3));
        assertEquals(0.0F, MeleeImpulseMath.pursuitStrength(2.01D, 2, 3));
        assertTrue(MeleeImpulseMath.pursuitStrength(0.3D, 3, 3) > MeleeImpulseMath.pursuitStrength(0.3D, 1, 1));
        assertEquals(1.25F, MeleeImpulseMath.pursuitStrength(2.0D, 255, 255));
    }
    @Test void exactAndWildcardSignaturesRemainAuthoritative() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:knockback"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(MeleeImpulseSkills.matches(FusedPair.clean(MeleeAttackThemes.SHARP, 1, MeleeImpulseSkills.KNOCKBACK, 1)));
            assertFalse(MeleeImpulseSkills.matches(FusedPair.clean(MeleeImpulseSkills.KNOCKBACK, 1, MeleeAttackThemes.SMITE, 1)));
            assertTrue(MeleeImpulseSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, MeleeImpulseSkills.KNOCKBACK, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
