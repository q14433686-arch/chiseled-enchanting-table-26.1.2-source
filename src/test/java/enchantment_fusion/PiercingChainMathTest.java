package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.PiercingChainMath;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class PiercingChainMathTest {
    private static final FusedPair FORWARD = FusedPair.clean("minecraft:quick_charge", 3, "minecraft:piercing", 4);

    @Test void hostRulesAndOrderFollowTheActualActions() {
        assertTrue(PiercingChainSkills.matches(FORWARD, PiercingChainSkills.FORWARD));
        assertTrue(PiercingChainSkills.matches(FORWARD.flipped(), PiercingChainSkills.REVERSE));
        assertFalse(PiercingChainSkills.matches(FORWARD.flipped(), PiercingChainSkills.FORWARD));
        assertTrue(PiercingChainSkills.supportsCarrier(FORWARD, Set.of(FusionCarrier.BOW)));
        assertTrue(PiercingChainSkills.supportsCarrier(FORWARD, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(PiercingChainSkills.supportsCarrier(FORWARD.flipped(), Set.of(FusionCarrier.BOW)));
        assertTrue(PiercingChainSkills.supportsCarrier(FORWARD.flipped(), Set.of(FusionCarrier.CROSSBOW)));
    }
    @Test void onlyLaterAlignedRealShotsMayFollowAnUnexpiredHole() {
        assertTrue(PiercingChainMath.canFollow(3, 4, 19, 20, 0.7D));
        assertFalse(PiercingChainMath.canFollow(3, 3, 19, 20, 1.0D));
        assertFalse(PiercingChainMath.canFollow(3, 2, 19, 20, 1.0D));
        assertFalse(PiercingChainMath.canFollow(3, 4, 20, 20, 1.0D));
        assertFalse(PiercingChainMath.canFollow(3, 4, 19, 20, 0.69D));
        assertFalse(PiercingChainMath.canFollow(3, 4, 19, 20, Double.NaN));
    }
    @Test void reloadCreditIsBoundedAndDoesNotCreateZeroDuration() {
        assertEquals(3, PiercingChainMath.accumulate(0, 3, 1, 12));
        assertEquals(12, PiercingChainMath.accumulate(11, Integer.MAX_VALUE, Integer.MAX_VALUE, 12));
        assertEquals(0, PiercingChainMath.accumulate(-5, 3, 0, 12));
        assertEquals(13, PiercingChainMath.duration(25, 12));
        assertEquals(1, PiercingChainMath.duration(3, 12));
    }
    @Test void arrowAndPlayerMixinInvocationOwnersMatchTargetBytecode() throws Exception {
        assertEquals(Set.of("net/minecraft/world/entity/projectile/arrow/AbstractArrow"),
            callOwners(AbstractArrow.class, "hitTargetsOrDeflectSelf", "hitTargetOrDeflectSelf"));
        assertEquals(Set.of("net/minecraft/world/entity/player/Player"),
            callOwners(Player.class, "attack", "causeExtraKnockback"));
        assertEquals(Set.of("net/minecraft/world/entity/player/Player"),
            callOwners(Player.class, "stabAttack", "causeExtraKnockback"));
    }
    private static Set<String> callOwners(Class<?> type, String methodName, String callee) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            Set<String> owners = new HashSet<>();
            node.methods.stream().filter(method -> method.name.equals(methodName)).forEach(method -> {
                for (var insn : method.instructions)
                    if (insn instanceof MethodInsnNode call && call.name.equals(callee)) owners.add(call.owner);
            });
            return owners;
        }
    }
}
