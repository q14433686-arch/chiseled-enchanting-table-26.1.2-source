package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Real server hits and knockback, with movement and mark age deliberately prepared. */
final class PursuitPlunderProbe {
    private PursuitPlunderProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(MeleeImpulseSkills.KNOCKBACK, 2, MeleeImpulseSkills.LOOTING, 2);
        SharedMeleeFixture.checkStation(player.level(), pair,
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR, Items.MACE, Items.TRIDENT),
            List.of(Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !denied.contains(entity.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            MeleeImpulseSkills.clearPlayer(player.getUUID());
            LivingEntity target = f.spawn(false, 2, 0);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, 3);
            ItemStack sword = f.equip(Items.DIAMOND_SWORD, pair);
            denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(marks(player) == 0, "Denied damage cannot create a pursuit mark");
            var resistance = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE); double oldResistance = resistance.getBaseValue();
            resistance.setBaseValue(1); target.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            require(marks(player) == 0, "Fully resisted knockback cannot create a pursuit mark");
            resistance.setBaseValue(oldResistance);
            f.hit(villager, true); require(marks(player) == 0, "A protected villager cannot become a pursuit target");

            target.setHealth(200); target.setDeltaMovement(Vec3.ZERO); player.setDeltaMovement(Vec3.ZERO);
            f.hit(target, true); require(marks(player) == 1 && target.getDeltaMovement().x > 0, "A real resisted knockback is needed");
            age(player, target); target.move(MoverType.SELF, new Vec3(0.3D, 0, 0));
            MeleeImpulseSkills.tickServer(f.level.getServer());
            denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(marks(player) == 1 && player.getDeltaMovement().horizontalDistanceSqr() == 0,
                "Denied follow-up cannot consume the mark or dash");
            double beforeVelocity = target.getDeltaMovement().x; f.hit(target, true);
            require(marks(player) == 0 && player.getDeltaMovement().x > 0 && target.hasEffect(MobEffects.SLOWNESS),
                "The real follow-up consumes the trace and grants one dash and brief imbalance");
            require(target.getDeltaMovement().x < beforeVelocity + 1.0D,
                "Only bounded horizontal momentum can be taken");
            player.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            require(player.getDeltaMovement().horizontalDistanceSqr() == 0, "A later hit cannot repeat a consumed dash");

            // A previous observed movement is insufficient if the enemy is already stationary before damage.
            MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(player.getX() + 2, player.getY(), player.getZ());
            target.setDeltaMovement(Vec3.ZERO); player.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            require(marks(player) == 1, "Stationary test needs a real first knockback");
            age(player, target); target.move(MoverType.SELF, new Vec3(0.25D, 0, 0)); MeleeImpulseSkills.tickServer(f.level.getServer());
            target.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            require(player.getDeltaMovement().horizontalDistanceSqr() == 0, "The follow-up's own new knockback is not old momentum");

            MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setPos(player.getX() + 2, player.getY(), player.getZ());
            target.setDeltaMovement(Vec3.ZERO); f.hit(target, true); require(marks(player) == 1, "Teleport test needs a mark");
            age(player, target); target.setPos(target.position().add(0.3D, 0, 0)); MeleeImpulseSkills.tickServer(f.level.getServer());
            require(marks(player) == 0, "Teleporting cannot pass off as collision-clipped movement");

            target.setPos(player.getX() + 2, player.getY(), player.getZ()); target.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            require(marks(player) == 1, "Identity test needs a mark");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword.copy()); MeleeImpulseSkills.tickServer(f.level.getServer());
            require(marks(player) == 0, "An equal copy of a different weapon cannot retain a trace");

            // Test the real non-damaging stab path, without any native enchantment projected on the trident.
            MeleeImpulseSkills.clearPlayer(player.getUUID()); target.setDeltaMovement(Vec3.ZERO);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            player.stabAttack(EquipmentSlot.MAINHAND, target, 0, false, true, false);
            double nativeMotion = target.getDeltaMovement().horizontalDistanceSqr(); target.setDeltaMovement(Vec3.ZERO);
            ItemStack cross = f.equip(Items.TRIDENT, pair); cross.remove(DataComponents.ENCHANTMENTS);
            player.stabAttack(EquipmentSlot.MAINHAND, target, 0, false, true, false);
            require(target.getDeltaMovement().horizontalDistanceSqr() > nativeMotion && marks(player) == 0,
                "The real non-native Knockback primary works once without fabricating a successful-hit mark");
        } finally { denied.clear(); MeleeImpulseSkills.clearPlayer(player.getUUID()); }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> states() throws Exception {
        Field field = MeleeImpulseSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return (List<Object>) field.get(null);
    }
    private static long marks(ServerPlayer player) throws Exception {
        long count = 0;
        for (Object mark : states()) {
            Field owner = mark.getClass().getDeclaredField("owner"); owner.setAccessible(true);
            if (player.getUUID().equals(owner.get(mark))) count++;
        }
        return count;
    }
    /** Rebuild one state with an earlier creation time; the world clock is unchanged. */
    private static void age(ServerPlayer player, LivingEntity target) throws Exception {
        List<Object> marks = states();
        for (int i = 0; i < marks.size(); i++) {
            Object mark = marks.get(i); Class<?> type = mark.getClass();
            Field owner = type.getDeclaredField("owner"), victim = type.getDeclaredField("target"), gear = type.getDeclaredField("gear");
            Field direction = type.getDeclaredField("direction");
            for (Field field : List.of(owner, victim, gear, direction)) field.setAccessible(true);
            if (!player.getUUID().equals(owner.get(mark)) || !target.getUUID().equals(victim.get(mark))) continue;
            var ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            marks.set(i, ctor.newInstance(player, target, gear.get(mark), direction.get(mark), player.level().getGameTime() - 1));
            return;
        }
        throw new AssertionError("Expected a real mark to age");
    }
}
