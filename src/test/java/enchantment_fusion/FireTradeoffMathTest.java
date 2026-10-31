package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.FireTradeoffMath;
import enchantment_fusion.mechanics.signatures.FireTradeoffSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class FireTradeoffMathTest {
    @Test void paidWearKeepsOnePointAndPartialBudgetOnlyBlocksProportionally() {
        var full = FireTradeoffMath.plan(8.0F, 50, 2, 100);
        assertEquals(4.0F, full.prevented()); assertEquals(8, full.requestedWear());
        var partial = FireTradeoffMath.plan(8.0F, 50, 2, 4);
        assertEquals(1.5F, partial.prevented()); assertEquals(3, partial.requestedWear());
        assertEquals(0, FireTradeoffMath.plan(100, 100, 2, 1).requestedWear());
        assertEquals(0.0F, FireTradeoffMath.plan(Float.NaN, 100, 2, 99).prevented());
        assertEquals(40.0F, FireTradeoffMath.addHeat(38, 9, 40));
        assertEquals(0.0F, FireTradeoffMath.addHeat(Float.NaN, Float.NaN, 40));
    }
    @Test void bothDirectionsAreDistinctAndNeedProtectiveGear() {
        var pair = FusedPair.clean("minecraft:protection", 4, "minecraft:fire_protection", 4);
        assertTrue(FireTradeoffSkills.matches(pair, FireTradeoffSkills.COMPENSATE));
        assertTrue(FireTradeoffSkills.matches(pair.flipped(), FireTradeoffSkills.WAVE));
        assertFalse(FireTradeoffSkills.matches(pair.flipped(), FireTradeoffSkills.COMPENSATE));
        assertTrue(FireTradeoffSkills.supportsCarrier(Set.of(FusionCarrier.HEAD_ARMOR)));
        assertTrue(FireTradeoffSkills.supportsCarrier(Set.of(FusionCarrier.SHIELD)));
        assertFalse(FireTradeoffSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void playerMitigationCallOwnerMatchesMixinTarget() throws Exception {
        try (InputStream input = Player.class.getResourceAsStream("/net/minecraft/world/entity/player/Player.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            Set<String> owners = new HashSet<>();
            node.methods.stream().filter(method -> method.name.equals("actuallyHurt")).forEach(method -> {
                for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                    && call.name.equals("getDamageAfterMagicAbsorb")) owners.add(call.owner);
            });
            assertEquals(Set.of("net/minecraft/world/entity/player/Player"), owners);
        }
    }
}
