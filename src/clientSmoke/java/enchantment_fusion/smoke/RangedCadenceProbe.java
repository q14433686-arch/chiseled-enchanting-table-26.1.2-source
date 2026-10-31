package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mixin.AbstractArrowAccessor;
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

/** Prepared fixture uses real vanilla release/load methods, with manually advanced item-use times. */
final class RangedCadenceProbe {
    private static final FusedPair BOW = FusedPair.clean("minecraft:power", 5, "minecraft:quick_charge", 3);
    private static final FusedPair CROSSBOW = BOW.flipped();
    private RangedCadenceProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? CROSSBOW : BOW;
        checkStation(level, pair, reverse);
        Vec3 pos = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer();
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(16.0D);
        Set<UUID> before = arrowIds(level, area);
        Zombie target = null;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Cadence fixture needs loaded disposable space");
            player.stopUsingItem();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 8));
            if (!reverse) {
                ItemStack bow = fused(level, new ItemStack(Items.BOW), pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, bow);
                Arrow partial = releaseBow(level, player, bow, area, 5);
                target = target(level, origin);
                hit(partial, target, origin);
                player.startUsingItem(InteractionHand.MAIN_HAND);
                require(player.getUseItemRemainingTicks() == bow.getUseDuration(player),
                    "A partial-draw arrow must not grant virtual draw progress");
                player.stopUsingItem(); target.discard(); target = null; partial.discard();

                Arrow full = releaseBow(level, player, bow, area, 20);
                require(full.isCritArrow(), "Vanilla must have launched a full-draw real arrow");
                target = target(level, origin);
                hit(full, target, origin);
                player.startUsingItem(InteractionHand.MAIN_HAND);
                require(player.getUseItemRemainingTicks() == bow.getUseDuration(player) - 6,
                    "A successful full real arrow hit must advance the next draw by Quick Charge level");
                player.stopUsingItem();
                player.startUsingItem(InteractionHand.MAIN_HAND);
                require(player.getUseItemRemainingTicks() == bow.getUseDuration(player),
                    "The stored draw progress can be used only once");
                player.stopUsingItem();
                require(player.getOffhandItem().getCount() == 6, "The two real bow shots must consume two real arrows");
            } else {
                ItemStack crossbow = fused(level, new ItemStack(Items.CROSSBOW), pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
                CrossbowItem item = (CrossbowItem) crossbow.getItem();
                load(item, player, crossbow);
                Arrow baseline = fireCrossbow(item, level, player, crossbow, area);
                double ordinaryDamage = ((AbstractArrowAccessor) baseline).ef$getBaseDamage();
                load(item, player, crossbow);
                require(RangedCadenceSkills.activateCrossbow(player), "An actual arrow load must allow arming stored momentum");
                int beforeFireAmmo = player.getOffhandItem().getCount();
                Arrow boosted = fireCrossbow(item, level, player, crossbow, area);
                require(((AbstractArrowAccessor) boosted).ef$getBaseDamage() > ordinaryDamage * 1.3D,
                    "Armed consecutive loads must enhance the actual central arrow");
                require(player.getOffhandItem().getCount() == beforeFireAmmo,
                    "Firing a loaded arrow must not debit or create extra ammunition");
                load(item, player, crossbow);
                Arrow ordinaryAgain = fireCrossbow(item, level, player, crossbow, area);
                require(Math.abs(((AbstractArrowAccessor) ordinaryAgain).ef$getBaseDamage() - ordinaryDamage) < 0.0001D,
                    "Stored momentum is consumed once and later shots stay ordinary unless armed again");
                require(player.getOffhandItem().getCount() == 5, "Three real loads must consume three arrows");
                require(!RangedCadenceSkills.activateCrossbow(player), "An unloaded crossbow cannot arm a phantom shot");
            }
        } finally {
            player.stopUsingItem(); RangedCadenceSkills.onDisconnect(player);
            if (target != null) target.discard();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area))
                if (!before.contains(arrow.getUUID()) && arrow.getOwner() == player) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(pos); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
        }
    }
    private static Zombie target(ServerLevel level, BlockPos origin) {
        Zombie target = new Zombie(EntityType.ZOMBIE, level);
        target.setNoAi(true);
        target.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
        require(level.addFreshEntity(target), "Cadence target must enter the disposable world");
        return target;
    }
    private static void hit(Arrow arrow, Zombie target, BlockPos origin) {
        float health = target.getHealth();
        arrow.setPos(origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D);
        arrow.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
        for (int i = 0; i < 3 && arrow.isAlive() && target.getHealth() == health; i++) arrow.tick();
        require(target.getHealth() < health || !target.isAlive(), "The real arrow must hit the fixture target");
    }
    private static Arrow releaseBow(ServerLevel level, ServerPlayer player, ItemStack bow, AABB area, int heldTicks) {
        Set<UUID> before = arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        boolean released = ((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - heldTicks);
        player.stopUsingItem();
        require(released, "Vanilla bow release must succeed");
        return oneNewArrow(level, area, before, player);
    }
    private static void load(CrossbowItem item, ServerPlayer player, ItemStack crossbow) {
        player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = CrossbowItem.getChargeDuration(crossbow, player);
        item.onUseTick(player.level(), player, crossbow, crossbow.getUseDuration(player) - duration);
        player.stopUsingItem();
        require(CrossbowItem.isCharged(crossbow), "Vanilla crossbow must really load an arrow");
    }
    private static Arrow fireCrossbow(CrossbowItem item, ServerLevel level, ServerPlayer player, ItemStack crossbow, AABB area) {
        Set<UUID> before = arrowIds(level, area);
        item.performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
        require(!CrossbowItem.isCharged(crossbow), "Vanilla firing must empty the real loaded projectile");
        return oneNewArrow(level, area, before, player);
    }
    private static Set<UUID> arrowIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>();
        for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) ids.add(arrow.getUUID());
        return ids;
    }
    private static Arrow oneNewArrow(ServerLevel level, AABB area, Set<UUID> before, ServerPlayer owner) {
        var arrows = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == owner);
        require(arrows.size() == 1, "One normal arrow must enter the world without a duplicate");
        return arrows.getFirst();
    }
    private static ItemStack fused(ServerLevel level, ItemStack stack, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
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
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(reverse ? Items.CROSSBOW : Items.BOW));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).executable(),
                "Both station routes must accept the direction's real weapon");
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(reverse ? Items.BOW : Items.CROSSBOW));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station routes must reject the other direction's weapon");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
