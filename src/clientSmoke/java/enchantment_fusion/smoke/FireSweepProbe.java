package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FireSweepSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Prepared attack/sweep fixture. The delayed chain's actual later ticks remain a separate acceptance gap. */
final class FireSweepProbe {
    private static final FusedPair LINE = FusedPair.clean("minecraft:fire_aspect", 2, "minecraft:sweeping_edge", 3);
    private FireSweepProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = reverse ? LINE.flipped() : LINE;
        checkStation(level, pair, reverse); FireSweepSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] oldGear = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) oldGear[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(), known = player.getKnownMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(), sprint = player.isSprinting();
        double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Zombie primary = zombie(level), first = zombie(level), second = zombie(level), axeTarget = zombie(level);
        Villager villager = new Villager(EntityType.VILLAGER, level); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Fixture needs open disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setKnownMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0); player.setOnGround(true); player.setSprinting(false); player.fallDistance = 0;
            speed.setBaseValue(1024.0D); player.resetAttackStrengthTicker();
            primary.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            first.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 1.2D);
            second.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() - 0.2D);
            axeTarget.setPos(origin.getX() + 6D, origin.getY(), origin.getZ() + 2D);
            villager.setNoAi(true); villager.setPos(origin.getX() + 2D, origin.getY(), origin.getZ() + 1.7D);
            for (var entity : List.of(primary, first, second, axeTarget, villager)) require(level.addFreshEntity(entity), "Fixture target must enter the world");
            player.setItemSlot(EquipmentSlot.MAINHAND, fused(level, Items.DIAMOND_SWORD, pair));
            if (!reverse) {
                player.setOnGround(false); // avoid vanilla sword sweep igniting the observers before they cross
                first.setPos(origin.getX() + 2D, origin.getY(), origin.getZ() + 1.2D);
                deny.add(primary.getUUID()); primary.invulnerableTime = 0; player.attack(primary); deny.clear();
                FireSweepSkills.tickServer(level.getServer()); first.setPos(origin.getX() + 3D, origin.getY(), origin.getZ() + 1.2D);
                FireSweepSkills.tickServer(level.getServer()); require(!first.isOnFire(), "Cancelled attack must not create a boundary");
                first.setPos(origin.getX() + 2D, origin.getY(), origin.getZ() + 1.2D);
                speed.setBaseValue(1.0D); player.resetAttackStrengthTicker(); primary.invulnerableTime = 0; player.attack(primary);
                FireSweepSkills.tickServer(level.getServer()); first.setPos(origin.getX() + 3D, origin.getY(), origin.getZ() + 1.2D);
                FireSweepSkills.tickServer(level.getServer()); require(!first.isOnFire(), "Uncharged attack must not create a boundary");
                first.setPos(origin.getX() + 2D, origin.getY(), origin.getZ() + 1.2D);
                villager.setPos(origin.getX() + 2D, origin.getY(), origin.getZ() + 1.7D);
                speed.setBaseValue(1024.0D); player.resetAttackStrengthTicker(); primary.invulnerableTime = 0; player.attack(primary);
                require(primary.isOnFire(), "The native Fire Aspect primary must still apply");
                FireSweepSkills.tickServer(level.getServer());
                first.setPos(origin.getX() + 3D, origin.getY(), origin.getZ() + 1.2D);
                villager.setPos(origin.getX() + 3D, origin.getY(), origin.getZ() + 1.7D);
                FireSweepSkills.tickServer(level.getServer());
                require(first.isOnFire() && !villager.isOnFire(), "A crossing enemy ignites but a villager is protected");
                require(level.getBlockState(origin).isAir(), "The boundary must not place a fire block");
                player.setItemSlot(EquipmentSlot.MAINHAND, fused(level, Items.DIAMOND_AXE, pair));
                axeTarget.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(axeTarget);
                require(axeTarget.isOnFire(), "A non-native axe must replay the real Fire Aspect primary");
            } else {
                deny.add(first.getUUID()); primary.invulnerableTime = 0; second.invulnerableTime = 0;
                player.attack(primary); deny.clear();
                require(size("FUSES") == 1, "A cancelled swept target must not receive a fuse");
                FireSweepSkills.onDisconnect(player.getUUID()); primary.invulnerableTime = 0; first.invulnerableTime = 0; second.invulnerableTime = 0;
                primary.setHealth(primary.getMaxHealth()); first.setHealth(first.getMaxHealth()); second.setHealth(second.getMaxHealth());
                player.resetAttackStrengthTicker(); player.attack(primary);
                require(size("FUSES") == 2, "A real sweep marks the two enemies, not the protected villager");
                first.invulnerableTime = 0; second.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(first);
                require(size("FUSES") == 0 && size("BURSTS") == 2,
                    "A subsequent real hit must consume connected fuses and schedule one bounded chain");
                require(!first.isOnFire() && !second.isOnFire(), "The chain must not detonate before its configured delay");
            }
        } finally {
            deny.clear(); FireSweepSkills.onDisconnect(player.getUUID());
            primary.discard(); first.discard(); second.discard(); axeTarget.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], oldGear[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setKnownMovement(known);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setSprinting(sprint); player.fallDistance = fall;
        }
    }
    private static Zombie zombie(ServerLevel level) {
        Zombie result = new Zombie(EntityType.ZOMBIE, level); result.setNoAi(true);
        result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D); result.setHealth(200.0F); return result;
    }
    private static int size(String fieldName) throws Exception {
        Field field = FireSweepSkills.class.getDeclaredField(fieldName); field.setAccessible(true); Object state = field.get(null);
        return state instanceof Map<?, ?> map ? map.size() : ((Collection<?>) state).size();
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
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
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = item == Items.DIAMOND_SWORD || !reverse && item == Items.DIAMOND_AXE;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must enforce this direction's action and host");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
