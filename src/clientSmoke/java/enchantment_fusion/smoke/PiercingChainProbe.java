package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Limited server fixture: real vanilla shots and loads, with manually advanced projectile ticks. */
final class PiercingChainProbe {
    private static final FusedPair FORWARD = FusedPair.clean("minecraft:quick_charge", 3, "minecraft:piercing", 4);
    private PiercingChainProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? FORWARD.flipped() : FORWARD;
        checkStation(level, pair, reverse);
        Vec3 oldPos = player.position(), oldMovement = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(16.0D);
        Set<UUID> existing = arrowIds(level, area);
        Zombie front = null, behind = null, decoy = null;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture requires loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 8));
            front = zombie(level, origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            if (!reverse) {
                ItemStack bow = fused(level, Items.BOW, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, bow);
                Arrow first = fireBow(level, player, bow, area);
                aim(first, origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                hit(front, first);
                require(!first.isAlive(), "The first ordinary bow arrow must not gain penetration for free");
                front.setHealth(front.getMaxHealth()); front.invulnerableTime = 0;
                // The second shot approaches diagonally. One decoy is on its old path and one target
                // on the first arrow's direction; the old same-tick collision batch must be stopped.
                behind = zombie(level, origin.getX() + 6.4D, origin.getY() + 1.0D, origin.getZ() + 0.2D);
                decoy = zombie(level, origin.getX() + 6.4D, origin.getY() + 1.0D, origin.getZ() + 1.9D);
                Arrow second = fireBow(level, player, bow, area);
                aim(second, origin.getX() + 2.9D, origin.getY() + 2.0D, origin.getZ() - 0.7D, 0.8D, 0.6D, 5.0F);
                float decoyHealth = decoy.getHealth(), behindHealth = behind.getHealth();
                second.tick();
                require(second.isAlive() && second.getPierceLevel() >= 1, "The repeated real shot must retain a real penetration");
                require(front.getHealth() < front.getMaxHealth(), "The repeated shot must actually hurt the original target");
                require(decoy.getHealth() == decoyHealth && behind.getHealth() == behindHealth,
                    "The already-collected old trajectory cannot damage either later target during the redirected tick");
                require(second.getDeltaMovement().x > 0 && Math.abs(second.getDeltaMovement().z) < 0.001D,
                    "The continued arrow must use the previous arrow's direction");
                for (int i = 0; i < 4 && second.isAlive() && behind.getHealth() == behindHealth; i++) second.tick();
                require(behind.getHealth() < behindHealth && decoy.getHealth() == decoyHealth,
                    "Later collision ticks must hit the target behind, not the old-path decoy");
                require(player.getOffhandItem().getCount() == 6, "Both real arrows must consume real ammunition");
            } else {
                ItemStack crossbow = fused(level, Items.CROSSBOW, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
                CrossbowItem item = (CrossbowItem) crossbow.getItem();
                int base = CrossbowItem.getChargeDuration(crossbow, player);
                load(player, item, crossbow);
                Arrow arrow = fireCrossbow(level, player, item, crossbow, area);
                require(arrow.getPierceLevel() > 0, "The primary Piercing must remain on the actual arrow");
                aim(arrow, origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                hit(front, arrow);
                require(CrossbowItem.getChargeDuration(crossbow, player) == base - 3,
                    "One successful real enemy penetration must shorten the next reload by Quick Charge level");
                require(CrossbowItem.getChargeDuration(crossbow.copy(), player) == base,
                    "A copied item must not inherit another item's charge credit");
                player.startUsingItem(InteractionHand.MAIN_HAND); player.stopUsingItem();
                require(CrossbowItem.getChargeDuration(crossbow, player) == base - 3,
                    "A cancelled reload must not spend the credit");
                load(player, item, crossbow);
                require(CrossbowItem.getChargeDuration(crossbow, player) == base,
                    "A successful real load must consume the credit exactly once");
                require(player.getOffhandItem().getCount() == 6, "Two successful loads must consume two real arrows");
            }
        } finally {
            player.stopUsingItem(); PiercingChainSkills.onDisconnect(player);
            if (front != null) front.discard(); if (behind != null) behind.discard(); if (decoy != null) decoy.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area))
                if (!existing.contains(arrow.getUUID()) && arrow.getOwner() == player) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain); player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setGameMode(oldMode); player.setPos(oldPos); player.setDeltaMovement(oldMovement);
            player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static Zombie zombie(ServerLevel level, double x, double y, double z) {
        Zombie result = new Zombie(EntityType.ZOMBIE, level); result.setNoAi(true); result.setPos(x, y, z);
        require(level.addFreshEntity(result), "Zombie must enter the disposable world"); return result;
    }
    private static void aim(Arrow arrow, double x, double y, double z, double dx, double dz, float speed) {
        arrow.setPos(x, y, z); arrow.setNoGravity(true); arrow.shoot(dx, 0, dz, speed, 0);
    }
    private static void hit(Zombie target, Arrow arrow) {
        float health = target.getHealth();
        for (int i = 0; i < 4 && arrow.isAlive() && target.getHealth() == health; i++) arrow.tick();
        require(target.getHealth() < health, "The genuine arrow must actually hurt the fixture target");
    }
    private static Arrow fireBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area) {
        Set<UUID> previous = arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
        player.stopUsingItem(); return oneArrow(level, area, previous, player);
    }
    private static void load(ServerPlayer player, CrossbowItem item, ItemStack crossbow) {
        player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = CrossbowItem.getChargeDuration(crossbow, player);
        item.onUseTick(player.level(), player, crossbow, crossbow.getUseDuration(player) - duration);
        player.stopUsingItem(); require(CrossbowItem.isCharged(crossbow), "A real crossbow projectile must load");
    }
    private static Arrow fireCrossbow(ServerLevel level, ServerPlayer player, CrossbowItem item, ItemStack crossbow, AABB area) {
        Set<UUID> previous = arrowIds(level, area);
        item.performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
        require(!CrossbowItem.isCharged(crossbow), "Firing must empty the real ammunition");
        return oneArrow(level, area, previous, player);
    }
    private static Set<UUID> arrowIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>();
        for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) result.add(arrow.getUUID());
        return result;
    }
    private static Arrow oneArrow(ServerLevel level, AABB area, Set<UUID> existing, ServerPlayer player) {
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !existing.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real central arrow must enter the world"); return found.getFirst();
    }
    private static ItemStack fused(ServerLevel level, net.minecraft.world.item.Item item, FusedPair pair) {
        ItemStack result = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(result, pair, level.registryAccess()); return result;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean reverse) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            for (var item : new net.minecraft.world.item.Item[]{Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD}) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = item == Items.CROSSBOW || (!reverse && item == Items.BOW);
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station routes must accept only this direction's implemented carriers");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
