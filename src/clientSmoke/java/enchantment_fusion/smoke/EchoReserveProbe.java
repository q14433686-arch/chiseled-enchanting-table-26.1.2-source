package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.DurabilityReserveSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Prepared server fixture; manually advances projectile/echo ticks and samples natural Unbreaking rolls. */
final class EchoReserveProbe {
    private static final FusedPair ECHO = FusedPair.clean("minecraft:infinity", 1, "minecraft:unbreaking", 3);
    private static final FusedPair RESERVE = ECHO.flipped();
    private EchoReserveProbe() {}

    static void exerciseEcho(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.level();
        checkStation(level, ECHO, false);
        EchoPathSkills.onDisconnect(player.getUUID());
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        GameType mode = player.gameMode.getGameModeForPlayer();
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        AABB area = new AABB(origin).inflate(16.0D);
        Set<UUID> existing = arrowIds(level, area);
        BlockPos wall = origin.offset(2, 1, 0);
        BlockState previousWall = level.getBlockState(wall);
        Zombie target = new Zombie(EntityType.ZOMBIE, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && previousWall.isAir(),
                "Echo fixture must use empty loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            target.setNoAi(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D); target.setHealth(200.0F);
            target.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            require(level.addFreshEntity(target), "Echo target must enter the disposable world");
            for (ItemStack weapon : List.of(new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW))) {
                FusionStationOperations.applyFusedPairToGear(weapon, ECHO, level.registryAccess());
                player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                require(!EchoPathSkills.activate(player), "No real hit means no selectable path");
                target.invulnerableTime = 0;
                fireAndHit(level, player, weapon, target, area, origin);
                int realAmmoAfterShot = player.getOffhandItem().getCount(), damageBefore = weapon.getDamageValue();
                Set<UUID> beforeEcho = arrowIds(level, area);
                target.invulnerableTime = 0;
                float health = target.getHealth();
                require(EchoPathSkills.activate(player), "A successful real central arrow must arm its actual path");
                for (int tick = 0; tick < 6; tick++) EchoPathSkills.tickServer(level.getServer());
                require(target.getHealth() < health, "A virtual echo must replay the real path and damage the target");
                require(weapon.getDamageValue() == damageBefore + 3, "The active echo must request real launcher wear");
                require(player.getOffhandItem().getCount() == realAmmoAfterShot && arrowIds(level, area).equals(beforeEcho),
                    "A virtual echo must neither consume extra ammunition nor spawn an arrow in the world");
                require(!EchoPathSkills.activate(player), "One recorded path can be released only once");
            }

            ItemStack bow = player.getMainHandItem();
            // Record another real hit, then place a wall: the replay must stop at current geometry.
            target.invulnerableTime = 0;
            fireAndHit(level, player, bow, target, area, origin);
            target.invulnerableTime = 0;
            float afterRealArrow = target.getHealth();
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            require(EchoPathSkills.activate(player), "Second real hit must make a new path available");
            for (int tick = 0; tick < 6; tick++) EchoPathSkills.tickServer(level.getServer());
            require(target.getHealth() == afterRealArrow && level.getBlockState(wall).is(Blocks.STONE),
                "A newly placed wall must stop the virtual echo without block damage");
            level.setBlockAndUpdate(wall, previousWall);

            target.invulnerableTime = 0;
            fireAndHit(level, player, bow, target, area, origin);
            target.invulnerableTime = 0;
            afterRealArrow = target.getHealth();
            villager.setNoAi(true);
            villager.setPos(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            require(level.addFreshEntity(villager), "Protected villager must enter the fixture");
            float villagerHealth = villager.getHealth();
            require(EchoPathSkills.activate(player), "Another real hit must make one more path available");
            for (int tick = 0; tick < 6; tick++) EchoPathSkills.tickServer(level.getServer());
            require(villager.getHealth() == villagerHealth && target.getHealth() == afterRealArrow,
                "A protected villager must stop the echo without harm to itself or a target behind it");
        } finally {
            EchoPathSkills.onDisconnect(player.getUUID());
            if (level.getBlockState(wall).is(Blocks.STONE)) level.setBlockAndUpdate(wall, previousWall);
            target.discard(); villager.discard(); player.stopUsingItem();
            for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area))
                if (!existing.contains(arrow.getUUID()) && arrow.getOwner() == player) arrow.discard();
            player.setItemSlot(EquipmentSlot.MAINHAND, main); player.setItemSlot(EquipmentSlot.OFFHAND, off);
            player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
        }
    }
    static void exerciseReserve(ServerPlayer player) throws Exception {
        ServerLevel level = player.level();
        checkStation(level, RESERVE, true);
        DurabilityReserveSkills.onDisconnect(player.getUUID());
        GameType mode = player.gameMode.getGameModeForPlayer();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        ItemStack oldHead = player.getItemBySlot(EquipmentSlot.HEAD);
        try {
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ItemStack nativePick = new ItemStack(Items.DIAMOND_PICKAXE);
            ItemStack nonNative = new ItemStack(Items.IRON_INGOT);
            nonNative.set(DataComponents.MAX_STACK_SIZE, 1); nonNative.set(DataComponents.MAX_DAMAGE, 1000);
            nonNative.set(DataComponents.DAMAGE, 0);
            for (ItemStack stack : List.of(nativePick, nonNative)) {
                FusionStationOperations.applyFusedPairToGear(stack, RESERVE, level.registryAccess());
                player.setItemSlot(EquipmentSlot.MAINHAND, stack);
                int maximum = stack.getMaxDamage();
                require(!DurabilityReserveSkills.activate(player), "No prevented wear means no reservation");
                int prevented = 0;
                for (int i = 0; i < 300 && prevented < 4; i++) {
                    int before = stack.getDamageValue();
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    if (stack.getDamageValue() == before) prevented++;
                }
                require(prevented == 4 && stack.getMaxDamage() == maximum, "Actual Unbreaking must prevent wear without raising maximum");
                require(DurabilityReserveSkills.activate(player) && armed(player, stack), "Observed real prevention must fund one armed reserve");
                boolean spent = false;
                for (int i = 0; i < 300 && !spent; i++) {
                    int before = stack.getDamageValue();
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    if (!armed(player, stack)) {
                        require(stack.getDamageValue() == before, "The reserved nonzero wear operation must be cancelled");
                        spent = true;
                    }
                }
                require(spent && stack.getMaxDamage() == maximum, "The reserve must be consumed once, not increase maximum durability");
                int afterSpend = stack.getDamageValue();
                for (int i = 0; i < 300 && stack.getDamageValue() == afterSpend; i++) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                require(stack.getDamageValue() > afterSpend, "Later unreserved normal wear must remain possible");
                DurabilityReserveSkills.onDisconnect(player.getUUID());
            }
            ItemStack hand = new ItemStack(Items.DIAMOND_PICKAXE), helmet = new ItemStack(Items.NETHERITE_HELMET);
            FusionStationOperations.applyFusedPairToGear(hand, RESERVE, level.registryAccess());
            FusionStationOperations.applyFusedPairToGear(helmet, RESERVE, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, hand); player.setItemSlot(EquipmentSlot.HEAD, helmet);
            fund(player, hand, EquipmentSlot.MAINHAND); fund(player, helmet, EquipmentSlot.HEAD);
            require(DurabilityReserveSkills.activate(player) && armed(player, hand) && !armed(player, helmet),
                "The first ready held item must be selectable");
            require(DurabilityReserveSkills.activate(player) && armed(player, hand) && armed(player, helmet),
                "A second activation must cycle to ready worn equipment");
            require(DurabilityReserveSkills.activate(player) && !armed(player, hand) && armed(player, helmet),
                "Cycling back must cancel only the selected item's reservation");
            ItemStack copy = helmet.copy(); player.setItemSlot(EquipmentSlot.HEAD, copy);
            DurabilityReserveSkills.tickServer(level.getServer());
            require(!armed(player, copy) && !armed(player, helmet), "A replacement copy must not inherit the original item's reserve");
        } finally {
            DurabilityReserveSkills.onDisconnect(player.getUUID());
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain); player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setItemSlot(EquipmentSlot.HEAD, oldHead); player.setGameMode(mode);
        }
    }
    private static void fund(ServerPlayer player, ItemStack stack, EquipmentSlot slot) {
        int prevented = 0;
        for (int i = 0; i < 300 && prevented < 4; i++) {
            int before = stack.getDamageValue();
            stack.hurtAndBreak(1, player, slot);
            if (stack.getDamageValue() == before) prevented++;
        }
        require(prevented == 4, "Fixture must observe real Unbreaking prevention to fund a reserve");
    }
    private static boolean armed(ServerPlayer player, ItemStack stack) throws Exception {
        Field mapField = DurabilityReserveSkills.class.getDeclaredField("RESERVES"); mapField.setAccessible(true);
        Object raw = ((Map<?, ?>) mapField.get(null)).get(player.getUUID());
        if (!(raw instanceof List<?> entries)) return false;
        for (Object entry : entries) {
            Field item = entry.getClass().getDeclaredField("stack"), flag = entry.getClass().getDeclaredField("armed");
            item.setAccessible(true); flag.setAccessible(true);
            if (item.get(entry) == stack) return flag.getBoolean(entry);
        }
        return false;
    }
    private static void fireAndHit(ServerLevel level, ServerPlayer player, ItemStack weapon, Zombie target, AABB area, BlockPos origin) {
        Set<UUID> before = arrowIds(level, area);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (weapon.getItem() instanceof BowItem bow) {
            require(bow.releaseUsing(weapon, level, player, weapon.getUseDuration(player) - 20), "Vanilla bow must release");
        } else {
            CrossbowItem crossbow = (CrossbowItem) weapon.getItem();
            crossbow.onUseTick(level, player, weapon, weapon.getUseDuration(player) - CrossbowItem.getChargeDuration(weapon, player));
            require(CrossbowItem.isCharged(weapon), "Vanilla crossbow must really load");
            crossbow.performShooting(level, player, InteractionHand.MAIN_HAND, weapon, 3.15F, 1.0F, null);
        }
        player.stopUsingItem();
        var created = level.getEntitiesOfClass(Arrow.class, area, arrow -> !before.contains(arrow.getUUID()) && arrow.getOwner() == player);
        require(created.size() == 1, "A real central arrow must be launched exactly once");
        Arrow arrow = created.getFirst();
        arrow.setPos(origin.getX() + 1.0D, origin.getY() + 1.5D, origin.getZ() + 0.5D);
        arrow.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
        float health = target.getHealth();
        for (int i = 0; i < 4 && arrow.isAlive() && target.getHealth() == health; i++) arrow.tick();
        require(target.getHealth() < health, "The normal arrow must actually hurt the fixture target");
    }
    private static Set<UUID> arrowIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>();
        for (Arrow arrow : level.getEntitiesOfClass(Arrow.class, area)) ids.add(arrow.getUUID());
        return ids;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean reserve) {
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
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(reserve ? Items.DIAMOND_PICKAXE : Items.BOW));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).executable(),
                "Both station routes must accept the direction's valid carrier");
            ItemStack wrong = new ItemStack(reserve ? Items.BOW : Items.DIAMOND_SWORD);
            if (reserve) wrong.remove(DataComponents.MAX_DAMAGE);
            inv.setItem(FusionStationOperations.SLOT_TARGET, wrong);
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station routes must reject a carrier lacking the action");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
