package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real bow/crossbow release and arrow damage, with simulated draw time, aim and activation. */
final class GrapnelProjectileProbe {
    private GrapnelProjectileProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String primary) throws Exception {
        ServerLevel level = player.level(); boolean power = GrapnelSkills.POWER.equals(primary);
        boolean flame = GrapnelSkills.FLAME.equals(primary), punch = GrapnelSkills.PUNCH.equals(primary);
        FusedPair pair = FusedPair.clean(primary, power ? 3 : punch ? 2 : 1, GrapnelSkills.LURE, 2); checkStation(level, pair, power);
        GrapnelSkills.clearPlayer(player.getUUID());
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer(); ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(20); Set<UUID> oldArrows = arrowIds(level, area);
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager protectedTarget = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            for (LivingEntity entity : List.of(enemy, protectedTarget)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            enemy.setPos(player.getX() + 4, player.getY(), player.getZ());
            protectedTarget.setPos(player.getX() + 4, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(enemy) && level.addFreshEntity(protectedTarget), "Fixture targets must enter the world");
            ItemStack bow = fused(level, Items.BOW, pair); bow.remove(DataComponents.ENCHANTMENTS);
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
            if (power) {
                Arrow partial = fireBow(level, player, bow, area, 5);
                float projected = EnchantmentHelper.modifyDamage(level, partial.getWeaponItem(), enemy, level.damageSources().arrow(partial, player), 2.0F);
                require(projected > 2.0F, "Missing native Power must replay its actual damage effect once");
                hit(partial, enemy, false); require(marked(player) == null, "Partial bow draw cannot mark");
            }
            Arrow synthetic = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
            require(level.addFreshEntity(synthetic), "Synthetic control arrow must enter the world");
            hit(synthetic, enemy, false); require(marked(player) == null, "An arrow outside a real shot cannot establish a mark");
            Arrow cancelled = fireBow(level, player, bow, area, 20); deny.add(enemy.getUUID());
            hit(cancelled, enemy, true); deny.clear(); require(marked(player) == null, "Cancelled damage cannot mark");
            if (punch) {
                enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                Arrow resisted = fireBow(level, player, bow, area, 20);
                hit(resisted, enemy, false); require(marked(player) == null, "Fully resisted push cannot mark");
                enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            }
            enemy.setDeltaMovement(Vec3.ZERO);
            Arrow arrow = fireBow(level, player, bow, area, 20);
            if (flame) require(arrow.isOnFire(), "Missing native Flame must ignite the actual arrow");
            hit(arrow, enemy, false); require(enemy.getUUID().equals(marked(player)), "Real qualifying arrow damage must mark the target");
            if (punch) require(enemy.getDeltaMovement().x > 0.05D, "Missing native Punch must actually push the target");
            enemy.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && marked(player) == null,
                "The marked target must be pulled toward the shooter once");
            require(!GrapnelSkills.activate(player), "A spent arrow mark cannot be reused");

            Arrow safe = fireBow(level, player, bow, area, 20);
            hit(safe, protectedTarget, false); require(marked(player) == null, "A villager cannot become a grapnel target");
            if (!power) {
                ItemStack crossbow = fused(level, Items.CROSSBOW, pair); crossbow.remove(DataComponents.ENCHANTMENTS);
                player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
                Arrow realCrossbowArrow = fireCrossbow(level, player, crossbow, area);
                if (flame) require(realCrossbowArrow.isOnFire(), "Non-native crossbow Flame must use the real spawn effect");
                enemy.setDeltaMovement(Vec3.ZERO); hit(realCrossbowArrow, enemy, false);
                require(enemy.getUUID().equals(marked(player)), "A qualifying real crossbow arrow must mark");
                if (punch) require(enemy.getDeltaMovement().x > 0.05D, "Non-native crossbow Punch must really push");
                enemy.setDeltaMovement(Vec3.ZERO); require(GrapnelSkills.activate(player), "Crossbow mark must be actively recalled");
            }
        } finally {
            deny.clear(); player.stopUsingItem(); GrapnelSkills.clearPlayer(player.getUUID()); enemy.discard(); protectedTarget.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) if (!oldArrows.contains(arrow.getUUID())) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    static void hit(Arrow arrow, LivingEntity target, boolean cancelled) {
        target.invulnerableTime = 0; float before = target.getHealth();
        arrow.setPos(target.getX() - 3.0D, target.getY() + 0.8D, target.getZ()); arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3, 0);
        for (int i = 0; i < 4 && arrow.isAlive() && target.getHealth() == before; i++) arrow.tick();
        require(cancelled ? target.getHealth() == before : target.getHealth() < before, "Fixture arrow damage outcome differs");
    }
    private static Arrow fireBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area, int ticks) {
        Set<UUID> before = arrowIds(level, area); player.startUsingItem(InteractionHand.MAIN_HAND);
        require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - ticks), "Bow must release");
        player.stopUsingItem(); return oneArrow(level, area, before, player);
    }
    private static Arrow fireCrossbow(ServerLevel level, ServerPlayer player, ItemStack crossbow, AABB area) {
        CrossbowItem item = (CrossbowItem) crossbow.getItem(); player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = CrossbowItem.getChargeDuration(crossbow, player);
        item.onUseTick(level, player, crossbow, crossbow.getUseDuration(player) - duration); player.stopUsingItem();
        require(CrossbowItem.isCharged(crossbow), "Crossbow must load real ammunition");
        Set<UUID> before = arrowIds(level, area);
        item.performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
        return oneArrow(level, area, before, player);
    }
    static Set<UUID> arrowIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>(); for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) result.add(arrow.getUUID()); return result;
    }
    private static Arrow oneArrow(ServerLevel level, AABB area, Set<UUID> before, ServerPlayer owner) {
        var found = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == owner);
        require(found.size() == 1, "Exactly one real arrow must be launched"); return found.getFirst();
    }
    static UUID marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method target = mark.getClass().getDeclaredMethod("target"); target.setAccessible(true); return (UUID) target.invoke(mark);
    }
    static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack result = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(result, pair, level.registryAccess()); return result;
    }
    static void checkStation(ServerLevel level, FusedPair pair, boolean power) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item == Items.BOW || (!power && item == Items.CROSSBOW);
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must require the direction's real launcher");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
