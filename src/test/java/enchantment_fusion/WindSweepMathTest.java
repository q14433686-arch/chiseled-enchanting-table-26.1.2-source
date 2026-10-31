package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.WindSweepMath;
import enchantment_fusion.mechanics.signatures.WindSweepSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.Set;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class WindSweepMathTest {
    @Test void resistanceAndExistingSpeedBoundThePull() {
        assertEquals(0.12, WindSweepMath.force(0.12, 0, 0, false), 0.00001);
        assertEquals(0.03, WindSweepMath.force(0.12, 0, 0.57, false), 0.00001);
        assertEquals(0, WindSweepMath.force(0.12, 0, 0.6, false));
        assertEquals(0, WindSweepMath.force(0.12, 1, 0, false));
        assertEquals(0.425, WindSweepMath.force(0.85, 0.5, 0.6, true), 0.00001);
        assertEquals(0, WindSweepMath.force(0.12, Double.NaN, 0, false));
    }
    @Test void releasingRequiresFiniteCloseAim() {
        assertTrue(WindSweepMath.aimed(25, 1, 0.5));
        assertFalse(WindSweepMath.aimed(25.01, 1, 1));
        assertFalse(WindSweepMath.aimed(1, 1, 0.49));
        assertFalse(WindSweepMath.aimed(1, 1, Double.NaN));
    }
    @Test void onlyTheSweepDirectionRequiresASword() {
        FusedPair pair = FusedPair.clean("minecraft:knockback", 2, "minecraft:sweeping_edge", 3);
        assertTrue(WindSweepSkills.matches(pair, WindSweepSkills.EYE));
        assertTrue(WindSweepSkills.matches(pair.flipped(), WindSweepSkills.CORE));
        assertTrue(WindSweepSkills.supportsCarrier(Set.of(FusionCarrier.AXE), WindSweepSkills.EYE));
        assertTrue(WindSweepSkills.supportsCarrier(Set.of(FusionCarrier.SWORD), WindSweepSkills.CORE));
        assertFalse(WindSweepSkills.supportsCarrier(Set.of(FusionCarrier.AXE), WindSweepSkills.CORE));
        assertFalse(WindSweepSkills.supportsCarrier(Set.of(FusionCarrier.BOW), WindSweepSkills.EYE));
    }
    @Test void bothPlayerAttackPathsCallTheKnockbackHook() throws Exception {
        try (InputStream input = Player.class.getResourceAsStream("/net/minecraft/world/entity/player/Player.class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            for (String name : Set.of("attack", "stabAttack")) {
                var methods = node.methods.stream().filter(m -> m.name.equals(name)).toList();
                assertEquals(1, methods.size());
                int calls = 0;
                for (var insn : methods.getFirst().instructions) if (insn instanceof MethodInsnNode call
                    && call.name.equals("getKnockback") && call.owner.equals("net/minecraft/world/entity/player/Player")
                    && call.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F")) calls++;
                assertEquals(1, calls, name + " must expose the selected mixin call");
            }
        }
    }
    @Test void targetVersionHasTheRealSwingHandler() throws Exception {
        try (InputStream input = ServerGamePacketListenerImpl.class.getResourceAsStream("/net/minecraft/server/network/ServerGamePacketListenerImpl.class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            var handlers = node.methods.stream().filter(m -> m.name.equals("handleAnimate")
                && m.desc.equals("(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V")).toList();
            assertEquals(1, handlers.size());
            boolean callsSwing = false;
            for (var insn : handlers.getFirst().instructions) if (insn instanceof MethodInsnNode call && call.name.equals("swing")) callsSwing = true;
            assertTrue(callsSwing);
        }
    }
}
