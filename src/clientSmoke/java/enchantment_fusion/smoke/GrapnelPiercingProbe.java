package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real crossbow piercing, including a slow multi-step pass, fake arrows, cancellation and a wall. */
final class GrapnelPiercingProbe {
    private static final FusedPair PAIR = FusedPair.clean(GrapnelSkills.PIERCING, 2, GrapnelSkills.LURE, 2);
    private GrapnelPiercingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        ServerLevel level = player.level(); GrapnelProjectileProbe.checkStation(level, PAIR, false, true);
        GrapnelSkills.clearPlayer(player.getUUID());
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer(); ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20); Set<UUID> oldArrows = GrapnelProjectileProbe.arrowIds(level, area);
        BlockPos wall = origin.offset(2, 1, 0); BlockState oldWall = level.getBlockState(wall); boolean placedWall = false;
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && oldWall.isAir(), "Fixture needs loaded empty disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            for (LivingEntity entity : List.of(enemy, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            enemy.setPos(player.getX() + 4, player.getY(), player.getZ());
            villager.setPos(player.getX() + 4, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(enemy) && level.addFreshEntity(villager), "Fixture targets must enter the world");
            ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, PAIR); crossbow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);

            Arrow nonPiercing = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            require(nonPiercing.getPierceLevel() > 0, "Missing native Piercing must project the actual data-pack effect at construction");
            ((AbstractArrowAccessor) nonPiercing).ef$setPierceLevel((byte) 0);
            GrapnelProjectileProbe.hit(nonPiercing, enemy, false);
            require(GrapnelProjectileProbe.marked(player) == null, "Real non-piercing damage cannot claim a penetration mark");

            Arrow fake = new Arrow(level, player, new ItemStack(Items.ARROW), crossbow);
            ((AbstractArrowAccessor) fake).ef$setPierceLevel((byte) 2);
            require(level.addFreshEntity(fake), "Fake control arrow must enter the world");
            GrapnelProjectileProbe.hit(fake, enemy, false);
            require(GrapnelProjectileProbe.marked(player) == null, "A forged arrow outside a real shot cannot mark");

            Arrow denied = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area); deny.add(enemy.getUUID());
            GrapnelProjectileProbe.hit(denied, enemy, true); deny.clear();
            require(GrapnelProjectileProbe.marked(player) == null, "Cancelled piercing damage cannot mark");

            Arrow protectedShot = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            GrapnelProjectileProbe.hit(protectedShot, villager, false);
            require(GrapnelProjectileProbe.marked(player) == null, "A passed villager is still protected");

            Arrow blocked = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            require(level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3), "Fixture wall must be placed"); placedWall = true;
            enemy.invulnerableTime = 0; float health = enemy.getHealth();
            blocked.setPos(enemy.getX() - 3, enemy.getY() + 0.8D, enemy.getZ()); blocked.setNoGravity(true); blocked.shoot(1, 0, 0, 3, 0);
            for (int i = 0; i < 3 && blocked.isAlive() && !((AbstractArrowAccessor) blocked).ef$isInGround(); i++) blocked.tick();
            require(enemy.getHealth() == health && GrapnelProjectileProbe.marked(player) == null, "A wall before the target prevents damage and marking");
            level.setBlock(wall, oldWall, 3); placedWall = false;

            Arrow slow = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            require(slow.getPierceLevel() > 0, "Slow control must be a real piercing arrow");
            enemy.invulnerableTime = 0; health = enemy.getHealth(); enemy.setDeltaMovement(Vec3.ZERO);
            slow.setPos(enemy.getX() - 0.55D, enemy.getY() + 0.8D, enemy.getZ()); slow.setNoGravity(true); slow.shoot(1, 0, 0, 0.15F, 0);
            for (int i = 0; i < 3 && slow.isAlive() && enemy.getHealth() == health; i++) slow.tick();
            require(enemy.getHealth() < health, "Slow real arrow must damage its target");
            require(GrapnelProjectileProbe.marked(player) == null, "Impact alone, before passing the target, cannot mark");
            for (int i = 0; i < 20 && slow.isAlive() && GrapnelProjectileProbe.marked(player) == null; i++) slow.tick();
            require(enemy.getUUID().equals(GrapnelProjectileProbe.marked(player)), "Continuous real movement through the target must mark");
            enemy.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && GrapnelProjectileProbe.marked(player) == null,
                "The penetrated target must be actively reeled once");
        } finally {
            deny.clear(); if (placedWall) level.setBlock(wall, oldWall, 3);
            player.stopUsingItem(); GrapnelSkills.clearPlayer(player.getUUID()); enemy.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
