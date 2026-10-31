package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.io.InputStream;
import java.util.Set;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.Arrays;

final class FireThornsStructureTest {
    @Test void directionsRemainDistinctAndRequireProtectiveCarriers() {
        var pair = FusedPair.clean("minecraft:thorns", 3, "minecraft:fire_protection", 4);
        assertTrue(FireThornsSkills.matches(pair, FireThornsSkills.AMBUSH));
        assertTrue(FireThornsSkills.matches(pair.flipped(), FireThornsSkills.CURTAIN));
        assertFalse(FireThornsSkills.matches(pair.flipped(), FireThornsSkills.AMBUSH));
        assertTrue(FireThornsSkills.supportsCarrier(Set.of(FusionCarrier.HEAD_ARMOR)));
        assertTrue(FireThornsSkills.supportsCarrier(Set.of(FusionCarrier.SHIELD)));
        assertFalse(FireThornsSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void livingAndPlayerDamageEachHaveTheirOwnActualHurtMethod() throws Exception {
        String descriptor = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V";
        assertTrue(classNode(LivingEntity.class).methods.stream().anyMatch(m -> m.name.equals("actuallyHurt") && m.desc.equals(descriptor)));
        assertTrue(classNode(Player.class).methods.stream().anyMatch(m -> m.name.equals("actuallyHurt") && m.desc.equals(descriptor)));
        assertTrue(invokes(classNode(Mob.class), "doHurtTarget", "hurtServer"));
        assertTrue(invokes(classNode(LivingEntity.class), "stabAttack", "hurtServer"));
        assertTrue(invokes(classNode(Player.class), "attack", "hurtOrSimulate"));
        assertTrue(invokes(classNode(Player.class), "stabAttack", "hurtOrSimulate"));
    }
    private static boolean invokes(ClassNode owner, String method, String call) {
        return owner.methods.stream().filter(m -> m.name.equals(method))
            .flatMap(m -> Arrays.stream(m.instructions.toArray()))
            .anyMatch(i -> i instanceof MethodInsnNode mi && mi.owner.equals("net/minecraft/world/entity/Entity") && mi.name.equals(call));
    }
    private static ClassNode classNode(Class<?> type) throws Exception {
        try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream); ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
    @Test void thornsPostAttackWrapperSpecifiesTheNonStaticOverload() throws Exception {
        try (InputStream stream = Enchantment.class.getResourceAsStream("/net/minecraft/world/item/enchantment/Enchantment.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES);
            String descriptor = "(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;"
                + "Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/damagesource/DamageSource;)V";
            assertEquals(1, node.methods.stream().filter(m -> m.name.equals("doPostAttack") && m.desc.equals(descriptor)).count());
            assertTrue(node.methods.stream().filter(m -> m.name.equals("doPostAttack")).count() > 1,
                "The bare method name is overloaded and is not a safe Mixin target");
        }
    }
}
