package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeFireSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

/** Six direction-specific real-hit probes using shared fixture setup, not natural client input. */
final class MeleeFireProbe {
    private MeleeFireProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean reverse) {
        FusedPair pair = FusedPair.clean(attack, 4, MeleeFireSkills.FIRE, 2); if (reverse) pair = pair.flipped();
        SharedMeleeFixture.checkMeleeStation(player.level(), pair); MeleeFireSkills.clearPlayer(player.getUUID());
        Set<UUID> denied = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        try (SharedMeleeFixture fixture = new SharedMeleeFixture(player, origin)) {
            boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
            LivingEntity target = fixture.spawn(bane, 2, 0), other = fixture.spawn(bane, 3.6D, 0);
            LivingEntity wrong = fixture.spawn(!bane, 2, 3); Villager villager = fixture.spawn(new Villager(EntityType.VILLAGER, fixture.level), 2, -3);
            fixture.equip(Items.DIAMOND_SWORD, pair);
            if (reverse) reverse(fixture, target, other, sharp ? villager : wrong, denied, pair);
            else forward(fixture, target, other, sharp ? villager : wrong, denied);
        } finally { denied.clear(); MeleeFireSkills.clearPlayer(player.getUUID()); }
    }
    private static void forward(SharedMeleeFixture f, LivingEntity target, LivingEntity other, LivingEntity invalid, Set<UUID> denied) {
        require(!MeleeFireSkills.activate(f.player), "No confirmed burning hit cannot extract fire");
        f.hit(target, true); require(!MeleeFireSkills.activate(f.player), "An unlit target cannot be marked");
        target.igniteForSeconds(8); denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        require(!MeleeFireSkills.activate(f.player) && target.isOnFire(), "A cancelled hit cannot extract or extinguish fire");
        invalid.igniteForSeconds(8); f.hit(invalid, true);
        require(!MeleeFireSkills.activate(f.player), "Wrong-category or protected targets cannot be marked");
        f.hit(target, false); require(!MeleeFireSkills.activate(f.player), "A partial hit cannot mark a burning target");
        f.hit(target, true); require(target.isOnFire(), "Marking alone does not extinguish the target");
        float before = other.getHealth();
        require(MeleeFireSkills.activate(f.player) && !target.isOnFire(), "The active input extracts the original fire");
        require(other.getHealth() < before, "A real qualified creature on the directed blade line is hit");
        require(!MeleeFireSkills.activate(f.player), "Extraction cannot be repeated without another eligible mark");
    }
    private static void reverse(SharedMeleeFixture f, LivingEntity target, LivingEntity other, LivingEntity invalid,
                                Set<UUID> denied, FusedPair pair) {
        denied.add(target.getUUID()); f.hit(target, true); denied.clear(); target.clearFire();
        float baseline = other.getHealth(); f.hit(invalid, true);
        require(other.getHealth() == baseline, "Wrong or protected target cannot release a virtual blade");
        f.hit(target, true); require(target.isOnFire() && other.getHealth() == baseline, "A real first hit ignites and marks, without immediately releasing a blade");
        other.invulnerableTime = 0; f.hit(target, true);
        require(other.getHealth() < baseline, "A second real hit on the burning marked target releases the blade");
        // Axe lacks native Fire Aspect support in the default data: it must replay the real datapack effect once.
        MeleeFireSkills.clearPlayer(f.player.getUUID()); target.clearFire(); other.setHealth(200); other.invulnerableTime = 0;
        f.equip(Items.DIAMOND_AXE, pair); f.hit(target, true); require(target.isOnFire(), "The non-native carrier must use the real primary fire effect");
        baseline = other.getHealth(); other.invulnerableTime = 0; f.hit(target, true);
        require(other.getHealth() < baseline, "The non-native primary can establish and consume the same mark");
    }
}
