package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Actual crossbow loading, arrow spawning and collision, with simulated use timing and activation. */
final class GrapnelQuickChargeProbe {
    private GrapnelQuickChargeProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(GrapnelSkills.QUICK, 2, GrapnelSkills.LURE, 2);
        GrapnelProjectileProbe.checkStation(level, pair, false, true); GrapnelSkills.clearPlayer(player.getUUID());
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer(); ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20); Set<UUID> oldArrows = GrapnelProjectileProbe.arrowIds(level, area);
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.setPos(origin.getX() + 0.5D, origin.getY() + 1, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 24));
            for (LivingEntity entity : List.of(enemy, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            enemy.setPos(player.getX() + 4, player.getY(), player.getZ()); villager.setPos(player.getX() + 4, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(enemy) && level.addFreshEntity(villager), "Fixture targets must enter world");
            ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
            int duration = CrossbowItem.getChargeDuration(crossbow, player);
            require(duration == 15, "Non-native Quick Charge II must project its real datapack time once");
            var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(GrapnelSkills.QUICK)));
            ItemStack nativeBow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); nativeBow.enchant(holder, 2);
            player.setItemSlot(EquipmentSlot.MAINHAND, nativeBow);
            require(CrossbowItem.getChargeDuration(nativeBow, player) == duration, "A native primary cannot receive a second time reduction");
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);

            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            Arrow fabricated = shoot(level, player, crossbow, area); GrapnelProjectileProbe.hit(fabricated, enemy, false);
            require(GrapnelProjectileProbe.marked(player) == null, "Artificially charged ammunition without a real quickened load cannot mark");

            load(level, player, crossbow); ItemStack copy = crossbow.copy(); player.setItemSlot(EquipmentSlot.MAINHAND, copy);
            Arrow copied = shoot(level, player, copy, area); GrapnelProjectileProbe.hit(copied, enemy, false);
            require(GrapnelProjectileProbe.marked(player) == null, "A copied charged crossbow cannot inherit the original load's proof");
            crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);

            Arrow cancelled = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            deny.add(enemy.getUUID()); GrapnelProjectileProbe.hit(cancelled, enemy, true); deny.clear();
            require(GrapnelProjectileProbe.marked(player) == null, "Cancelled damage cannot mark");
            Arrow real = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            GrapnelProjectileProbe.hit(real, enemy, false);
            require(enemy.getUUID().equals(GrapnelProjectileProbe.marked(player)), "The arrow of a real quickened load must mark a damaged enemy");
            enemy.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && GrapnelProjectileProbe.marked(player) == null,
                "The original crossbow must actively reel the mark once");
            Arrow safe = GrapnelProjectileProbe.fireCrossbow(level, player, crossbow, area);
            GrapnelProjectileProbe.hit(safe, villager, false);
            require(GrapnelProjectileProbe.marked(player) == null, "A protected villager cannot be marked");
        } finally {
            deny.clear(); player.stopUsingItem(); GrapnelSkills.clearPlayer(player.getUUID()); enemy.discard(); villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
        }
    }
    private static void load(ServerLevel level, ServerPlayer player, ItemStack crossbow) {
        CrossbowItem item = (CrossbowItem) crossbow.getItem(); player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = CrossbowItem.getChargeDuration(crossbow, player);
        item.onUseTick(level, player, crossbow, crossbow.getUseDuration(player) - duration); player.stopUsingItem();
        require(CrossbowItem.isCharged(crossbow), "The real load must produce charged projectiles");
    }
    private static Arrow shoot(ServerLevel level, ServerPlayer player, ItemStack crossbow, AABB area) {
        Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
        ((CrossbowItem) crossbow.getItem()).performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
        var arrows = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(arrows.size() == 1, "Exactly one arrow must be fired"); return arrows.getFirst();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
