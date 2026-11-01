package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.model.FusedPair;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real multi-arrow shots, including a one-arrow loaded control and normal ammunition accounting. */
final class GrapnelVolleyProbe {
    private static final FusedPair PAIR = FusedPair.clean(GrapnelSkills.MULTISHOT, 1, GrapnelSkills.LURE, 2);
    private GrapnelVolleyProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        ServerLevel level = player.level(); GrapnelProjectileProbe.checkStation(level, PAIR, false);
        GrapnelSkills.clearPlayer(player.getUUID());
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer(); ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20); Set<UUID> oldArrows = GrapnelProjectileProbe.arrowIds(level, area);
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level);
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            enemy.setNoAi(true); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200);
            enemy.setPos(player.getX() + 4, player.getY(), player.getZ());
            require(level.addFreshEntity(enemy), "Fixture target must enter the world");
            ItemStack bow = GrapnelProjectileProbe.fused(level, Items.BOW, PAIR); bow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
            int beforeAmmo = player.getOffhandItem().getCount();
            Set<UUID> before = GrapnelProjectileProbe.arrowIds(level, area);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
            player.stopUsingItem(); List<Arrow> bowArrows = newArrows(level, area, before, player);
            require(player.getOffhandItem().getCount() == beforeAmmo - 1, "A real multishot bow consumes one normal arrow");
            assertVolley(bowArrows);
            Arrow side = bowArrows.stream().filter(a -> a.pickup != AbstractArrow.Pickup.ALLOWED).findFirst().orElseThrow();
            GrapnelProjectileProbe.hit(side, enemy, false); recall(player, enemy);
            Arrow center = bowArrows.stream().filter(a -> a.pickup == AbstractArrow.Pickup.ALLOWED).findFirst().orElseThrow();
            GrapnelProjectileProbe.hit(center, enemy, false); recall(player, enemy);

            ItemStack crossbow = GrapnelProjectileProbe.fused(level, Items.CROSSBOW, PAIR); crossbow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, crossbow); CrossbowItem item = (CrossbowItem) crossbow.getItem();
            beforeAmmo = player.getOffhandItem().getCount(); player.startUsingItem(InteractionHand.MAIN_HAND);
            int duration = CrossbowItem.getChargeDuration(crossbow, player);
            item.onUseTick(level, player, crossbow, crossbow.getUseDuration(player) - duration); player.stopUsingItem();
            require(CrossbowItem.isCharged(crossbow) && player.getOffhandItem().getCount() == beforeAmmo - 1,
                "Crossbow must really load its multi-arrow batch using one normal arrow");
            before = GrapnelProjectileProbe.arrowIds(level, area);
            item.performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
            List<Arrow> crossbowArrows = newArrows(level, area, before, player); assertVolley(crossbowArrows);
            GrapnelProjectileProbe.hit(crossbowArrows.getFirst(), enemy, false); recall(player, enemy);
            require(player.getOffhandItem().getCount() == beforeAmmo - 1, "Firing an already loaded crossbow must not consume more arrows");

            // Manually supplied one-arrow load is a negative control: a single real shot is not a real volley.
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            before = GrapnelProjectileProbe.arrowIds(level, area);
            item.performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
            List<Arrow> single = newArrows(level, area, before, player); require(single.size() == 1, "Control must fire one real arrow");
            GrapnelProjectileProbe.hit(single.getFirst(), enemy, false);
            require(GrapnelProjectileProbe.marked(player) == null, "A one-arrow shot cannot claim a multishot mark");
        } finally {
            player.stopUsingItem(); GrapnelSkills.clearPlayer(player.getUUID()); enemy.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static void recall(ServerPlayer owner, Zombie target) throws Exception {
        require(target.getUUID().equals(GrapnelProjectileProbe.marked(owner)), "Real volley arrow must mark its actual target");
        target.setDeltaMovement(Vec3.ZERO);
        require(GrapnelSkills.activate(owner) && target.getDeltaMovement().x < 0 && GrapnelProjectileProbe.marked(owner) == null,
            "Volley's marked target must be actively reeled once");
    }
    private static void assertVolley(List<Arrow> arrows) {
        require(arrows.size() == 3 && arrows.stream().noneMatch(PhantomVolley::isGhost), "Three real non-ghost arrows must enter the world");
        require(arrows.stream().filter(arrow -> arrow.pickup == AbstractArrow.Pickup.ALLOWED).count() == 1,
            "Extra vanilla multi-shot arrows must not be survival-pickable copies");
    }
    private static List<Arrow> newArrows(ServerLevel level, AABB area, Set<UUID> before, ServerPlayer owner) {
        return level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == owner);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
