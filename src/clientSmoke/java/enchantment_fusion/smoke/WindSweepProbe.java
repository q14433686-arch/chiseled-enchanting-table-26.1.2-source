package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.WindSweepSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
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
import net.minecraft.world.InteractionHand;
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

/** Prepared real attack/sweep fixture; swing dispatch and age are simulated, not actual mouse/network input. */
final class WindSweepProbe {
    private static final FusedPair EYE = FusedPair.clean("minecraft:knockback", 2, "minecraft:sweeping_edge", 3);
    private WindSweepProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = reverse ? EYE.flipped() : EYE;
        checkStation(level, pair, reverse); WindSweepSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(), known = player.getKnownMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(), sprint = player.isSprinting();
        double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        Zombie primary = zombie(level), neighbor = zombie(level), axeTarget = zombie(level);
        Villager villager = new Villager(EntityType.VILLAGER, level); Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Fixture needs open disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setKnownMovement(Vec3.ZERO); player.setYRot(-90); player.setXRot(0); player.setOnGround(reverse); player.setSprinting(false); player.fallDistance = 0;
            speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            primary.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            neighbor.setPos(origin.getX() + 4D, origin.getY(), origin.getZ() + 0.5D);
            axeTarget.setPos(origin.getX() + 6D, origin.getY(), origin.getZ() + 2D);
            villager.setNoAi(true); villager.setPos(origin.getX() + 4D, origin.getY(), origin.getZ() + 1.1D);
            for (var entity : List.of(primary, neighbor, axeTarget, villager)) require(level.addFreshEntity(entity), "Fixture target must enter world");
            ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            deny.add(primary.getUUID()); primary.invulnerableTime = 0; player.attack(primary); deny.clear();
            require(size(reverse ? "CORES" : "EYES") == 0, "Cancelled damage must not arm the wind skill");
            primary.invulnerableTime = 0; primary.setDeltaMovement(Vec3.ZERO); player.resetAttackStrengthTicker(); player.attack(primary);
            if (!reverse) {
                require(size("EYES") == 1, "A real damaging knockback must attach the eye");
                neighbor.setDeltaMovement(Vec3.ZERO); villager.setDeltaMovement(Vec3.ZERO); WindSweepSkills.tickServer(level.getServer());
                require(neighbor.getDeltaMovement().x < 0 && villager.getDeltaMovement().lengthSqr() == 0,
                    "The eye must pull an enemy toward its anchor but protect the villager");
                player.setItemSlot(EquipmentSlot.MAINHAND, fused(level, Items.DIAMOND_AXE, pair));
                axeTarget.setDeltaMovement(Vec3.ZERO); axeTarget.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(axeTarget);
                require(axeTarget.getDeltaMovement().horizontalDistanceSqr() > 0.0001D,
                    "A non-native axe must replay the Knockback primary");
            } else {
                require(size("CORES") == 1, "A real sword sweep must create a core");
                neighbor.setDeltaMovement(Vec3.ZERO); villager.setDeltaMovement(Vec3.ZERO); WindSweepSkills.tickServer(level.getServer());
                require(neighbor.getDeltaMovement().x < 0 && villager.getDeltaMovement().lengthSqr() == 0,
                    "The core must pull an enemy but not a villager");
                WindSweepSkills.onSwing(player, InteractionHand.MAIN_HAND); // stand-in for first attack animation
                WindSweepSkills.onSwing(player, InteractionHand.MAIN_HAND); // too soon
                require(size("CORES") == 1, "The original and too-early swing must not detonate");
                ageCore(player, 4); // test-only forced age; natural server time is not advanced here
                player.setYRot(90); WindSweepSkills.onSwing(player, InteractionHand.MAIN_HAND);
                require(size("CORES") == 1, "Swinging away from the core must not detonate");
                player.setYRot(-90); neighbor.setDeltaMovement(Vec3.ZERO); WindSweepSkills.onSwing(player, InteractionHand.MAIN_HAND);
                require(size("CORES") == 0 && neighbor.getDeltaMovement().x > 0 && villager.getDeltaMovement().lengthSqr() == 0,
                    "Aimed release must push the enemy out and protect the villager");
            }
            require(level.getBlockState(origin).isAir(), "Wind must not place blocks");
        } finally {
            deny.clear(); WindSweepSkills.onDisconnect(player.getUUID()); primary.discard(); neighbor.discard(); axeTarget.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setKnownMovement(known);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.setSprinting(sprint); player.fallDistance = fall;
        }
    }
    private static Zombie zombie(ServerLevel level) {
        Zombie result = new Zombie(EntityType.ZOMBIE, level); result.setNoAi(true);
        result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); result.setHealth(200); return result;
    }
    private static Object state(String name) throws Exception { Field field = WindSweepSkills.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static int size(String name) throws Exception { Object state = state(name); return state instanceof Map<?, ?> map ? map.size() : ((Collection<?>) state).size(); }
    @SuppressWarnings("unchecked")
    private static void ageCore(ServerPlayer owner, int ticks) throws Exception {
        Map<UUID, Object> cores = (Map<UUID, Object>) state("CORES"); Object core = cores.get(owner.getUUID()); require(core != null, "Core must exist");
        RecordComponent[] components = core.getClass().getRecordComponents(); Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            var accessor = components[i].getAccessor(); accessor.setAccessible(true); args[i] = accessor.invoke(core);
            if (components[i].getName().equals("created")) args[i] = (long) args[i] - ticks;
        }
        Constructor<?> constructor = core.getClass().getDeclaredConstructors()[0]; constructor.setAccessible(true); cores.put(owner.getUUID(), constructor.newInstance(args));
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
                    "Both station paths must enforce the action and carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
