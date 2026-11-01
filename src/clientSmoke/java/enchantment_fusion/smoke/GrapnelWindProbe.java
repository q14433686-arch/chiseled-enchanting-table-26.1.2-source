package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.MeleeWindSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Candidate server fixture: real attacks/wind, manually simulated fall, aim and activation. */
final class GrapnelWindProbe {
    private GrapnelWindProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(MeleeWindSkills.WIND, 1, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.MACE),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.SHIELD, Items.DIAMOND_CHESTPLATE, Items.BOW));
        Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            LivingEntity victim = f.spawn(false, 4, 0), bystander = f.spawn(false, 2.5, 1.5);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, 3);
            player.setYRot(-90); player.setXRot(0);
            GrapnelSkills.clearPlayer(player.getUUID()); MeleeWindSkills.clearPlayer(player.getUUID());
            ItemStack mace = f.equip(Items.MACE, pair);
            player.fallDistance = 0; f.hit(victim, true);
            require(!marked(player), "An ordinary mace hit must not imply a Wind Burst");
            deny.add(victim.getUUID()); smash(f, victim); deny.clear();
            require(!marked(player), "Cancelled damage must not mark");
            double nativeLaunch = smash(f, victim);
            require(nativeLaunch > 0.01 && marked(player), "A real upward native Wind Burst after the hit must mark");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ()); victim.setDeltaMovement(Vec3.ZERO);
            require(!GrapnelSkills.activate(player) && marked(player), "A protected body blocks the aim without consuming the mark");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            player.setItemSlot(EquipmentSlot.MAINHAND, mace.copy());
            require(!GrapnelSkills.activate(player), "A copied mace cannot spend the original mark");
            player.setItemSlot(EquipmentSlot.MAINHAND, mace); GrapnelSkills.clearPlayer(player.getUUID()); victim.setHealth(200);
            smash(f, victim); victim.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && victim.getDeltaMovement().x < 0 && !marked(player),
                "The original mace may pull one eligible aimed target");
            require(!GrapnelSkills.activate(player), "A position mark is consumed once");

            GrapnelSkills.clearPlayer(player.getUUID()); MeleeWindSkills.clearPlayer(player.getUUID());
            mace.remove(DataComponents.ENCHANTMENTS); victim.setHealth(200);
            double replayLaunch = smash(f, victim);
            require(Math.abs(nativeLaunch - replayLaunch) < 0.001 && marked(player), "A missing primary is replayed exactly once");

            GrapnelSkills.clearPlayer(player.getUUID()); MeleeWindSkills.clearPlayer(player.getUUID());
            FusedPair windAttack = FusedPair.clean(MeleeWindSkills.WIND, 1, "minecraft:sharpness", 3);
            FusionStationOperations.applyFusedPairToGear(mace, windAttack, f.level.registryAccess());
            mace.remove(DataComponents.ENCHANTMENTS); victim.setHealth(200); bystander.setHealth(200);
            double sharedLaunch = smash(f, victim);
            require(Math.abs(nativeLaunch - sharedLaunch) < 0.001 && marked(player),
                "Wind/Lure and 43.4 share one missing-primary replay");
            player.fallDistance = 0; f.hit(victim, true);
            require(bystander.getHealth() < 200, "The existing 43.4 updraft still releases its wave");
        } finally {
            deny.clear(); GrapnelSkills.clearPlayer(player.getUUID()); MeleeWindSkills.clearPlayer(player.getUUID());
        }
    }
    private static double smash(SharedMeleeFixture f, LivingEntity victim) {
        f.player.setOnGround(false); f.player.fallDistance = 2; f.player.setDeltaMovement(Vec3.ZERO); f.hit(victim, true);
        return f.player.getDeltaMovement().y;
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
}
