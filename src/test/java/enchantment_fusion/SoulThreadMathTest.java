package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.SoulThreadMath;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
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

final class SoulThreadMathTest {
    @Test void cocoonMustFirstBeMarkedAndThenChargedBeforeAFullHit() {
        var first = SoulThreadMath.advance(-1, 2, true);
        assertEquals(0, first.pressure()); assertFalse(first.release());
        var second = SoulThreadMath.advance(first.pressure(), 2, true);
        assertEquals(1, second.pressure()); assertFalse(second.release());
        var weak = SoulThreadMath.advance(second.pressure(), 2, false);
        assertEquals(2, weak.pressure()); assertFalse(weak.release());
        var ready = SoulThreadMath.advance(weak.pressure(), 2, true);
        assertEquals(2, ready.pressure()); assertTrue(ready.release());
    }
    @Test void directionsAndActualWeaponsAreDistinct() {
        var forward = FusedPair.clean("minecraft:smite", 5, "minecraft:bane_of_arthropods", 5);
        assertTrue(SoulThreadSkills.matches(forward, SoulThreadSkills.WEB));
        assertTrue(SoulThreadSkills.matches(forward.flipped(), SoulThreadSkills.COCOON));
        assertFalse(SoulThreadSkills.matches(forward.flipped(), SoulThreadSkills.WEB));
        assertTrue(SoulThreadSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
        assertTrue(SoulThreadSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(SoulThreadSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
    }
    @Test void playerPostAttackCallMatchesTheRegisteredMixinTarget() throws Exception {
        try (InputStream stream = Player.class.getResourceAsStream("/net/minecraft/world/entity/player/Player.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES);
            Set<String> calls = new HashSet<>();
            node.methods.stream().filter(method -> method.name.equals("itemAttackInteraction")).forEach(method -> {
                for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                    && call.name.equals("doPostAttackEffectsWithItemSource")) calls.add(call.owner + call.desc);
            });
            assertEquals(Set.of("net/minecraft/world/item/enchantment/EnchantmentHelper"
                + "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/item/ItemStack;)V"), calls);
        }
    }
}
