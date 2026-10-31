package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.ProjectilePathTracker;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exercises the retained implementation without changing its mechanics; not a natural-input multiplayer test. */
final class RetainedVolleyProbe {
    private static final FusedPair RANGED = FusedPair.clean("minecraft:efficiency", 4, "minecraft:multishot", 1);
    private RetainedVolleyProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean mining) {
        ServerLevel level = player.level(); FusedPair pair = mining ? RANGED.flipped() : RANGED;
        checkStation(level, pair, mining);
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean shift = player.isShiftKeyDown();
        GameType mode = player.gameMode.getGameModeForPlayer(); AABB area = new AABB(origin).inflate(16);
        Set<UUID> existing = new HashSet<>();
        for (Entity e : level.getEntitiesOfClass(Entity.class, area)) existing.add(e.getUUID());
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); List<Entity> created = new ArrayList<>();
        Set<BlockPos> denied = new HashSet<>();
        PlayerBlockBreakEvents.BEFORE.register((world, breaker, pos, state, blockEntity) -> breaker != player || !denied.contains(pos));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture needs loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0); player.setShiftKeyDown(false);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (mining) {
                Direction side = player.getDirection().getClockWise();
                for (int i = -2; i <= 2; i++) {
                    BlockPos pos = origin.relative(side, i);
                    require(level.hasChunkAt(pos) && level.getBlockState(pos).isAir(), "Do not overwrite an existing block");
                    saved.put(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState());
                }
                ItemStack pick = fused(level, Items.DIAMOND_PICKAXE, pair);
                var silk = FusionStationOperations.lookupEnchantment(level.registryAccess(), "minecraft:silk_touch").orElseThrow();
                pick.enchant(silk, 1); player.setItemSlot(EquipmentSlot.MAINHAND, pick);
                require(player.gameMode.destroyBlock(origin), "Central ore must break through player game mode");
                require(level.getBlockState(origin.relative(side, -1)).isAir() && level.getBlockState(origin.relative(side, 1)).isAir(),
                    "The retained fusion alone must break both adjacent blocks");
                require(level.getBlockState(origin.relative(side, -2)).is(Blocks.DIAMOND_ORE)
                    && level.getBlockState(origin.relative(side, 2)).is(Blocks.DIAMOND_ORE), "Extra mining must not recurse");
                require(pick.getDamageValue() == 3, "All three blocks must consume ordinary tool durability");
                int ore = level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(e -> !existing.contains(e.getUUID()))
                    .filter(e -> e.getItem().is(Items.DIAMOND_ORE)).mapToInt(e -> e.getItem().getCount()).sum();
                require(ore >= 3, "Each block must use the real Silk Touch loot path");
                for (int i = -1; i <= 1; i++) level.setBlockAndUpdate(origin.relative(side, i), Blocks.DIAMOND_ORE.defaultBlockState());
                player.setShiftKeyDown(true); ItemStack sneakingPick = fused(level, Items.DIAMOND_PICKAXE, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, sneakingPick);
                require(player.gameMode.destroyBlock(origin), "Center still breaks while sneaking");
                require(level.getBlockState(origin.relative(side, -1)).is(Blocks.DIAMOND_ORE)
                    && level.getBlockState(origin.relative(side, 1)).is(Blocks.DIAMOND_ORE), "Sneaking pauses both extra breaks");
                player.setShiftKeyDown(false);
                level.setBlockAndUpdate(origin, Blocks.DIAMOND_ORE.defaultBlockState()); denied.add(origin.relative(side, -1));
                require(player.gameMode.destroyBlock(origin), "Center breaks when one neighbor is denied");
                require(level.getBlockState(origin.relative(side, -1)).is(Blocks.DIAMOND_ORE)
                    && level.getBlockState(origin.relative(side, 1)).isAir(), "Fabric cancellation protects only the denied extra block");
            } else {
                MatrixEffectExecutor.onPlayerDisconnect(player.getUUID()); ProjectilePathTracker.onPlayerDisconnect(player.getUUID());
                Zombie primary = new Zombie(EntityType.ZOMBIE, level), nearby = new Zombie(EntityType.ZOMBIE, level);
                created.add(primary); created.add(nearby); primary.setNoAi(true); nearby.setNoAi(true);
                primary.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                nearby.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 2.5D);
                require(level.addFreshEntity(primary) && level.addFreshEntity(nearby), "Targets must enter the disposable world");
                ItemStack bow = fused(level, Items.BOW, pair); player.setItemSlot(EquipmentSlot.MAINHAND, bow);
                player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 3));
                player.startUsingItem(InteractionHand.MAIN_HAND);
                require(((BowItem) bow.getItem()).releaseUsing(bow, level, player, bow.getUseDuration(player) - 20), "Bow must fire a real arrow");
                player.stopUsingItem();
                List<Arrow> arrows = level.getEntitiesOfClass(Arrow.class, area, a -> !existing.contains(a.getUUID()) && a.getOwner() == player);
                created.addAll(arrows); require(arrows.size() == 1, "Retained fusion must not create free projectile ammunition");
                Arrow arrow = arrows.get(0); arrow.setPos(origin.getX() + 1.0D, origin.getY() + 2.0D, origin.getZ() + 0.5D);
                arrow.setNoGravity(true); arrow.shoot(1, 0, 0, 3.0F, 0); float before = nearby.getHealth();
                for (int i = 0; i < 4 && arrow.isAlive() && primary.getHealth() == primary.getMaxHealth(); i++) arrow.tick();
                require(primary.getHealth() < primary.getMaxHealth() && nearby.getHealth() < before,
                    "A real impact must retain the existing scatter reaction against a nearby target");
                require(player.getOffhandItem().getCount() == 2, "The real shot must consume its ordinary arrow");
            }
        } finally {
            denied.clear(); player.stopUsingItem();
            for (Entity entity : created) entity.discard();
            for (Entity entity : level.getEntitiesOfClass(Entity.class, area))
                if (!existing.contains(entity.getUUID()) && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) entity.discard();
            saved.forEach(level::setBlockAndUpdate); ProjectilePathTracker.onPlayerDisconnect(player.getUUID()); MatrixEffectExecutor.onPlayerDisconnect(player.getUUID());
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(yaw); player.setXRot(pitch); player.setShiftKeyDown(shift);
        }
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean mining) {
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
            for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean allowed = mining ? item == Items.DIAMOND_PICKAXE : item == Items.BOW || item == Items.CROSSBOW;
                require(allowed ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must enforce the selected direction's capability");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
