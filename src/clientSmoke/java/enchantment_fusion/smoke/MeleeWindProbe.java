package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWindSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/** Candidate fixtures: real engine damage/movement, but simulated charge, position and fall. */
final class MeleeWindProbe {
    private MeleeWindProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean reverse) {
        FusedPair pair = FusedPair.clean(attack, 4, MeleeWindSkills.WIND, 1); if (reverse) pair = pair.flipped();
        if (reverse) SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.MACE),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        else SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE),
            List.of(Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW));
        MeleeWindSkills.clearPlayer(player.getUUID()); Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
            LivingEntity victim = f.spawn(bane, 2, 0), bystander = f.spawn(bane, 2, 1), wrong = f.spawn(!bane, 2, 2);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, 1.4D);
            if (reverse) reverse(f, pair, victim, bystander, sharp ? villager : wrong, villager, denied);
            else forward(f, pair, victim, bystander, sharp ? villager : wrong, villager, denied);
        } finally { denied.clear(); MeleeWindSkills.clearPlayer(player.getUUID()); }
    }
    private static void moveAndSettle(SharedMeleeFixture f, Vec3 delta) {
        f.player.move(MoverType.SELF, delta);
        MeleeWindSkills.tickServer(f.level.getServer());
    }
    private static void forward(SharedMeleeFixture f, FusedPair pair, LivingEntity victim, LivingEntity bystander,
                                LivingEntity invalid, Villager villager, Set<UUID> denied) {
        f.equip(Items.DIAMOND_AXE, pair); Vec3 initial = f.player.position();
        denied.add(victim.getUUID()); f.hit(victim, true); denied.clear();
        moveAndSettle(f, new Vec3(0, 0, 1)); require(bystander.getHealth() == 200, "Cancelled first hit cannot create a blade");
        f.player.setPos(initial); f.hit(victim, false); moveAndSettle(f, new Vec3(0, 0, 1));
        require(bystander.getHealth() == 200, "A weak hit cannot create the full-charge blade");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); f.player.setPos(initial);
        f.hit(invalid, true); moveAndSettle(f, new Vec3(0, 0, 1));
        require(bystander.getHealth() == 200, "Protected or wrong-category first hits cannot create the blade");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); f.player.setPos(initial); f.hit(victim, true);
        float villagerHealth = villager.getHealth(); moveAndSettle(f, new Vec3(0, 0, 1));
        require(bystander.getHealth() < 200 && villager.getHealth() == villagerHealth, "Observed movement cuts a matching enemy but not a villager");
        bystander.setHealth(200); bystander.invulnerableTime = 0; moveAndSettle(f, new Vec3(0, 0, 1));
        require(bystander.getHealth() == 200, "A blade cannot repeatedly damage a previously hit enemy");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); f.player.setPos(initial); victim.setHealth(200); f.hit(victim, true);
        f.player.setPos(initial.add(0, 0, 1)); MeleeWindSkills.tickServer(f.level.getServer());
        moveAndSettle(f, new Vec3(0, 0, 1));
        require(bystander.getHealth() == 200, "Unobserved teleport invalidates the blade rather than sweeping the gap");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); f.player.setPos(initial); victim.setHealth(200); f.hit(victim, true);
        bystander.setPos(victim.getX(), victim.getY(), victim.getZ() + 3); f.player.move(MoverType.SELF, new Vec3(0, 0, 1));
        bystander.setPos(victim.getX(), victim.getY(), victim.getZ() + 0.8D); MeleeWindSkills.tickServer(f.level.getServer());
        require(bystander.getHealth() == 200, "A target that appears after the move cannot retroactively enter the swept path");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); f.player.setPos(initial); victim.setHealth(200); f.hit(victim, true);
        bystander.setPos(victim.position()); MeleeWindSkills.tickServer(f.level.getServer());
        require(bystander.getHealth() < 200, "A matching enemy currently overlapping a stationary blade is contacted once");
    }
    private static void reverse(SharedMeleeFixture f, FusedPair pair, LivingEntity victim, LivingEntity bystander,
                                LivingEntity invalid, Villager villager, Set<UUID> denied) {
        ItemStack mace = f.equip(Items.MACE, pair);
        f.player.fallDistance = 0; f.hit(victim, true); f.hit(victim, true);
        require(bystander.getHealth() == 200, "Ordinary mace hits are not wind bursts and cannot create an updraft");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); victim.setHealth(200);
        f.player.fallDistance = 2; denied.add(victim.getUUID()); f.hit(victim, true); denied.clear();
        f.player.fallDistance = 0; f.hit(victim, true);
        require(bystander.getHealth() == 200, "Cancelled damage cannot create an updraft");
        MeleeWindSkills.clearPlayer(f.player.getUUID()); victim.setHealth(200);
        double nativeLaunch = smash(f, victim);
        require(nativeLaunch > 0.01D, "The real native Wind Burst must actually launch the player");
        f.player.fallDistance = 0; denied.add(victim.getUUID()); f.hit(victim, true); denied.clear();
        f.hit(invalid, true); require(bystander.getHealth() == 200, "Cancelled or invalid follow-ups cannot release the wave");
        float protectedHealth = villager.getHealth(); f.hit(victim, true);
        require(bystander.getHealth() < 200 && villager.getHealth() == protectedHealth, "A matching follow-up releases one protected virtual wave");
        bystander.setHealth(200); bystander.invulnerableTime = 0; f.hit(victim, true);
        require(bystander.getHealth() == 200, "A consumed updraft cannot release a second wave");
        MeleeWindSkills.clearPlayer(f.player.getUUID());
        var holder = FusionStationOperations.lookupEnchantment(f.level.registryAccess(), MeleeWindSkills.WIND).orElseThrow();
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(mace.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchants.set(holder, 0); mace.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
        victim.setHealth(200); double replayedLaunch = smash(f, victim);
        require(Math.abs(nativeLaunch - replayedLaunch) < 0.001D, "A missing primary is replayed once, not zero or twice");
        f.player.fallDistance = 0; f.hit(victim, true);
        require(bystander.getHealth() < 200, "The once-only replay is observed as a real updraft");
    }
    private static double smash(SharedMeleeFixture f, LivingEntity victim) {
        f.player.setOnGround(false); f.player.fallDistance = 2; f.player.setDeltaMovement(Vec3.ZERO); f.hit(victim, true);
        return f.player.getDeltaMovement().y;
    }
}
