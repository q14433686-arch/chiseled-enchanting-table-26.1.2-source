package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.FireSweepMath;
import enchantment_fusion.mechanics.signatures.FireSweepSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class FireSweepMathTest {
    private static boolean crosses(double x1, double z1, double x2, double z2) {
        return FireSweepMath.crosses(x1, z1, x2, z2, 0, 0, 1, 0, 2.5, 4);
    }
    @Test void crossingRequiresARealSideChangeInsideTheFiniteLine() {
        assertTrue(crosses(-1, 0, 1, 0));
        assertTrue(crosses(1, 2.5, -1, 2.5));
        assertFalse(crosses(-1, 2.51, 1, 2.51));
        assertFalse(crosses(-1, 0, -0.1, 0));
        assertFalse(crosses(0, 0, 0, 0));
        assertFalse(crosses(-3, 0, 3, 0));
        assertFalse(crosses(Double.NaN, 0, 1, 0));
    }
    @Test void neutralStripRetainsLastSideUntilTheEntityLeavesIt() {
        assertFalse(FireSweepMath.beyondCenter(0.01, 0, 0, 0, 1, 0));
        assertTrue(FireSweepMath.beyondCenter(-0.03, 0, 0, 0, 1, 0));
    }
    @Test void onlyTheReverseRequiresAnActualSweepingSword() {
        FusedPair pair = FusedPair.clean("minecraft:fire_aspect", 2, "minecraft:sweeping_edge", 3);
        assertTrue(FireSweepSkills.matches(pair, FireSweepSkills.LINE));
        assertTrue(FireSweepSkills.matches(pair.flipped(), FireSweepSkills.FUSE));
        assertTrue(FireSweepSkills.supportsCarrier(Set.of(FusionCarrier.AXE), FireSweepSkills.LINE));
        assertTrue(FireSweepSkills.supportsCarrier(Set.of(FusionCarrier.SWORD), FireSweepSkills.FUSE));
        assertFalse(FireSweepSkills.supportsCarrier(Set.of(FusionCarrier.AXE), FireSweepSkills.FUSE));
        assertFalse(FireSweepSkills.supportsCarrier(Set.of(FusionCarrier.BOW), FireSweepSkills.LINE));
    }
    @Test void sweepMixinPointsAtTheActualLivingDamageCall() throws Exception {
        try (InputStream input = Player.class.getResourceAsStream("/net/minecraft/world/entity/player/Player.class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            assertTrue(node.methods.stream().filter(m -> m.name.equals("doSweepAttack"))
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .anyMatch(i -> i instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/LivingEntity")
                    && call.name.equals("hurtServer") && call.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")));
        }
    }
}
