package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.ThermalProjectileSkills;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Limited prepared fixture: real central arrows, manually advanced projectiles and settle observations. */
final class ThermalProjectileProbe {
    private static final FusedPair BARRIER = FusedPair.clean("minecraft:flame", 1, "minecraft:punch", 2);
    private ThermalProjectileProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? BARRIER.flipped() : BARRIER;
        checkStation(level, pair);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20.0D);
        Set<UUID> existing = arrowIds(level, area);
        BlockPos wall = origin.offset(2, 2, 0);
        BlockState oldWall = level.getBlockState(wall);
        Zombie enemy = null;
        Villager villager = null;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture requires loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 8));
            enemy = new Zombie(EntityType.ZOMBIE, level); enemy.setNoAi(true);
            enemy.setPos(origin.getX() + 10.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            require(level.addFreshEntity(enemy), "Hostile shooter must enter the disposable world");
            villager = new Villager(EntityType.VILLAGER, level); villager.setNoAi(true);
            villager.setPos(origin.getX() + 10.5D, origin.getY() + 1.0D, origin.getZ() + 2.5D);
            require(level.addFreshEntity(villager), "Protected villager must enter the disposable world");
            if (!reverse) {
                ItemStack bow = fused(level, Items.BOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
                Arrow shield = fireBow(level, player, bow, area);
                require(shield.isOnFire(), "Native Flame primary must still ignite the real bow arrow");
                aim(shield, origin.getX() + 0.5D, origin.getY() + 2.2D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                Arrow incoming = arrow(level, enemy, origin, 4.5D, 0.5D);
                Arrow protectedArrow = arrow(level, villager, origin, 4.5D, 1.0D);
                Vec3 original = incoming.getDeltaMovement(), protectedVelocity = protectedArrow.getDeltaMovement();
                shield.tick();
                require(Math.abs(incoming.getDeltaMovement().z - original.z) > 0.2D && incoming.getOwner() == enemy,
                    "The moving heat barrier must nudge a hostile arrow without taking ownership");
                require(protectedArrow.getDeltaMovement().equals(protectedVelocity) && protectedArrow.getOwner() == villager,
                    "A protected owner's projectile must not be changed");
                incoming.discard(); protectedArrow.discard(); shield.discard();

                require(oldWall.isAir(), "The temporary wall location must be empty");
                level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                Arrow blockedShield = fireBow(level, player, bow, area);
                aim(blockedShield, origin.getX() + 0.5D, origin.getY() + 2.2D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                Arrow behindWall = arrow(level, enemy, origin, 4.5D, 0.5D);
                Vec3 blockedVelocity = behindWall.getDeltaMovement();
                blockedShield.tick();
                require(behindWall.getDeltaMovement().equals(blockedVelocity), "A wall must block the heat barrier's deflection");
                level.setBlockAndUpdate(wall, oldWall); blockedShield.discard(); behindWall.discard();
                ItemStack crossbow = fused(level, Items.CROSSBOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
                Arrow crossbowArrow = fireCrossbow(level, player, crossbow, area);
                require(crossbowArrow.isOnFire(), "A non-native crossbow must execute the real Flame spawn effect once");
            } else {
                ItemStack bow = fused(level, Items.BOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
                enemy.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                villager.setPos(origin.getX() + 5.0D, origin.getY() + 1.0D, origin.getZ() + 1.0D);
                Arrow shot = fireBow(level, player, bow, area);
                aim(shot, origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                hit(enemy, shot);
                require(enemy.getDeltaMovement().x > 0.1D, "Native Punch must actually push the successfully hit target");
                ThermalProjectileSkills.tickServer(level.getServer()); // establish the post-hit observation
                enemy.setOnGround(true); // explicit test stand-in, not a natural landing
                ThermalProjectileSkills.tickServer(level.getServer());
                float afterArrow = enemy.getHealth(), protectedHealth = villager.getHealth();
                enemy.invulnerableTime = 0;
                for (int i = 0; i < 8 && enemy.getHealth() == afterArrow; i++) ThermalProjectileSkills.tickServer(level.getServer());
                require(enemy.getHealth() < afterArrow && enemy.isOnFire(), "The virtual vortex must burn the landed hostile");
                require(villager.getHealth() == protectedHealth && !villager.isOnFire(), "The virtual vortex must protect villagers");
                ThermalProjectileSkills.onDisconnect(player.getUUID());
                enemy.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                enemy.setHealth(enemy.getMaxHealth()); enemy.invulnerableTime = 0; enemy.setDeltaMovement(Vec3.ZERO); enemy.clearFire();
                ItemStack ordinary = new ItemStack(Items.CROSSBOW); player.setItemSlot(EquipmentSlot.MAINHAND, ordinary);
                Arrow baselineArrow = fireCrossbow(level, player, ordinary, area);
                aim(baselineArrow, origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                hit(enemy, baselineArrow);
                double baselinePush = enemy.getDeltaMovement().x;
                enemy.setHealth(enemy.getMaxHealth()); enemy.invulnerableTime = 0; enemy.setDeltaMovement(Vec3.ZERO);
                ItemStack crossbow = fused(level, Items.CROSSBOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
                Arrow crossbowArrow = fireCrossbow(level, player, crossbow, area);
                aim(crossbowArrow, origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D, 1, 0, 3.0F);
                hit(enemy, crossbowArrow);
                require(enemy.getDeltaMovement().x > baselinePush + 0.5D,
                    "A non-native crossbow must apply real Punch beyond the ordinary arrow's push");
            }
        } finally {
            player.stopUsingItem(); ThermalProjectileSkills.onDisconnect(player.getUUID());
            if (!level.getBlockState(wall).equals(oldWall)) level.setBlockAndUpdate(wall, oldWall);
            if (enemy != null) enemy.discard(); if (villager != null) villager.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area))
                if (!existing.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain); player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setGameMode(oldMode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity);
            player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    private static Arrow arrow(ServerLevel level, LivingEntity owner, BlockPos origin, double x, double z) {
        Arrow arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
        aim(arrow, origin.getX() + x, origin.getY() + 2.2D, origin.getZ() + z, -1, 0, 3.0F);
        require(level.addFreshEntity(arrow), "Approaching arrow must enter the disposable world"); return arrow;
    }
    private static void aim(Arrow arrow, double x, double y, double z, double dx, double dz, float speed) {
        arrow.setPos(x, y, z); arrow.setNoGravity(true); arrow.shoot(dx, 0, dz, speed, 0);
    }
    private static void hit(Zombie target, Arrow arrow) {
        float health = target.getHealth();
        for (int i = 0; i < 4 && arrow.isAlive() && target.getHealth() == health; i++) arrow.tick();
        require(target.getHealth() < health, "A real arrow must actually damage the target");
    }
    private static Arrow fireBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area) {
        Set<UUID> before = arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must release");
        player.stopUsingItem(); return oneArrow(level, area, before, player);
    }
    private static Arrow fireCrossbow(ServerLevel level, ServerPlayer player, ItemStack weapon, AABB area) {
        CrossbowItem item = (CrossbowItem) weapon.getItem(); player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = CrossbowItem.getChargeDuration(weapon, player);
        item.onUseTick(level, player, weapon, weapon.getUseDuration(player) - duration); player.stopUsingItem();
        require(CrossbowItem.isCharged(weapon), "Crossbow must load real ammunition");
        Set<UUID> before = arrowIds(level, area);
        item.performShooting(level, player, InteractionHand.MAIN_HAND, weapon, 3.15F, 1.0F, null);
        return oneArrow(level, area, before, player);
    }
    private static Set<UUID> arrowIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) ids.add(arrow.getUUID()); return ids;
    }
    private static Arrow oneArrow(ServerLevel level, AABB area, Set<UUID> before, ServerPlayer player) {
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(found.size() == 1, "Exactly one real central arrow must enter the world"); return found.getFirst();
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack result = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(result, pair, level.registryAccess()); return result;
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
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
            for (Item item : new Item[]{Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD}) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = item == Items.BOW || item == Items.CROSSBOW;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station routes must accept real arrow weapons and reject a sword");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
