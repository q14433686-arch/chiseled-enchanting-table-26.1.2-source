package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.ThermalProjectileMath;
import enchantment_fusion.mechanics.signatures.ThermalProjectileSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class ThermalProjectileMathTest {
    @Test void bothDirectionsUseRealRangedCarriers() {
        FusedPair pair = FusedPair.clean("minecraft:flame", 1, "minecraft:punch", 2);
        assertTrue(ThermalProjectileSkills.matches(pair, ThermalProjectileSkills.BARRIER));
        assertTrue(ThermalProjectileSkills.matches(pair.flipped(), ThermalProjectileSkills.VORTEX));
        assertFalse(ThermalProjectileSkills.matches(pair, ThermalProjectileSkills.VORTEX));
        assertTrue(ThermalProjectileSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertTrue(ThermalProjectileSkills.supportsCarrier(Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(ThermalProjectileSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void sweptApproachIsBoundedAndRejectsMovingApart() {
        assertEquals(2.0D / 3.0D, ThermalProjectileMath.approachTime(new Vec3(4, 0, 0), new Vec3(-6, 0, 0)), 1e-9);
        assertEquals(1.0D, ThermalProjectileMath.approachTime(new Vec3(4, 0, 0), new Vec3(-1, 0, 0)));
        assertEquals(-1.0D, ThermalProjectileMath.approachTime(new Vec3(4, 0, 0), new Vec3(1, 0, 0)));
        assertEquals(-1.0D, ThermalProjectileMath.approachTime(new Vec3(4, 0, 0), Vec3.ZERO));
        assertEquals(-1.0D, ThermalProjectileMath.approachTime(new Vec3(Double.NaN, 0, 0), Vec3.ZERO));
    }
    @Test void arrowKnockbackMixinInvocationOwnersMatchTargetBytecode() throws Exception {
        try (InputStream input = AbstractArrow.class.getResourceAsStream("/net/minecraft/world/entity/projectile/arrow/AbstractArrow.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            Set<String> pushOwners = new HashSet<>(), valueOwners = new HashSet<>();
            node.methods.stream().filter(method -> method.name.equals("doKnockback")).forEach(method -> {
                for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) {
                    if (call.name.equals("push")) pushOwners.add(call.owner);
                    if (call.name.equals("modifyKnockback")) valueOwners.add(call.owner);
                }
            });
            assertEquals(Set.of("net/minecraft/world/entity/LivingEntity"), pushOwners);
            assertEquals(Set.of("net/minecraft/world/item/enchantment/EnchantmentHelper"), valueOwners);
        }
    }
}
