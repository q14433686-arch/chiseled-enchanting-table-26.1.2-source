package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.WaterPressureMath;
import enchantment_fusion.mechanics.signatures.WaterPressureSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Six prepared server fixtures with temporary water and explicitly refreshed fluid state. */
final class WaterPressureProbe {
    private WaterPressureProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean breathFirst) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(attack, 4, WaterPressureSkills.BREATH, 3);
        if (breathFirst) pair = pair.flipped();
        checkStation(level, pair, breathFirst); WaterPressureSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(), sneak = player.isShiftKeyDown(); double fall = player.fallDistance; int oldAir = player.getAirSupply();
        GameType mode = player.gameMode.getGameModeForPlayer(); var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity inLine = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        try {
            for (int x = -1; x <= 6; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = origin.offset(x, y, z); BlockState state = level.getBlockState(pos);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && state.isAir(), "Fixture needs loaded open disposable space");
                saved.put(pos, state);
            }
            for (BlockPos pos : saved.keySet()) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(bane ? 15 : 0); player.setOnGround(false); player.fallDistance = 0;
            player.setShiftKeyDown(false); speed.setBaseValue(1024); player.resetAttackStrengthTicker();
            for (LivingEntity entity : List.of(target, inLine, wrong, villager)) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); entity.setHealth(200);
            }
            target.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D);
            inLine.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D);
            wrong.setPos(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D);
            villager.setPos(origin.getX() + 3.5D, origin.getY(), origin.getZ() + 0.5D);
            require(level.addFreshEntity(target) && level.addFreshEntity(inLine) && level.addFreshEntity(wrong) && level.addFreshEntity(villager),
                "Fixture targets must enter the world");
            refreshWater(player); for (LivingEntity entity : List.of(target, inLine, wrong, villager)) refreshWater(entity);
            require(player.isEyeInFluid(FluidTags.WATER) && target.isInWater() && inLine.isInWater(), "Temporary water must be observed");
            if (breathFirst) {
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                player.setItemSlot(EquipmentSlot.HEAD, fused(level, Items.DIAMOND_HELMET, pair));
                int max = player.getMaxAirSupply(), minimum = WaterPressureMath.cost(max) + WaterPressureMath.reserve(max);
                player.setAirSupply(minimum - 1); require(!WaterPressureSkills.activate(player) && player.getAirSupply() == minimum - 1,
                    "Insufficient air must not be paid");
                player.setAirSupply(max); require(WaterPressureSkills.activate(player), "Underwater activation must prepare a blade");
                int paid = player.getAirSupply(); require(paid == max - WaterPressureMath.cost(max), "Exactly one real air cost must be paid");
                require(!WaterPressureSkills.activate(player) && player.getAirSupply() == paid, "Already prepared cannot charge twice");
            } else {
                ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair), trident = fused(level, Items.TRIDENT, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, target, level.damageSources().playerAttack(player), 1);
                player.setItemSlot(EquipmentSlot.MAINHAND, trident);
                float replayDamage = EnchantmentHelper.modifyDamage(level, trident, target, level.damageSources().playerAttack(player), 1);
                require(nativeDamage > 1 && Math.abs(nativeDamage - replayDamage) < 0.001F, "A cross-carrier must preserve the main damage once");
                player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                hit(player, villager); villager.setHealth(200); require(size("BUBBLES") == 0, "A protected target must not create a bubble");
                deny.add(target.getUUID()); hit(player, target); deny.clear(); require(size("BUBBLES") == 0, "Cancelled damage cannot create a bubble");
                hit(player, target); require(size("BUBBLES") == 1, "A real underwater hit must create a bubble");
            }
            String state = breathFirst ? "PREPARED" : "BUBBLES";
            deny.add(target.getUUID()); hit(player, target); deny.clear(); require(size(state) == 1, "Cancelled damage cannot consume readiness");
            float before = inLine.getHealth(), wrongBefore = wrong.getHealth(), villagerBefore = villager.getHealth();
            hit(player, target); require(size(state) == 0 && inLine.getHealth() < before, "A valid hit must release pressure into an eligible enemy");
            require(villager.getHealth() == villagerBefore, "Water pressure must not harm villagers");
            if (!MeleeAttackThemes.SHARP.equals(attack)) require(wrong.getHealth() == wrongBefore, "Attack theme must restrict pressure targets");
            if (breathFirst) { player.setAirSupply(player.getMaxAirSupply()); require(WaterPressureSkills.activate(player), "A second blade can be prepared"); }
            else { hit(player, target); require(size("BUBBLES") == 1, "A second bubble can be created"); }
            level.setBlockAndUpdate(origin.east(3), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(3).above(), Blocks.STONE.defaultBlockState());
            inLine.invulnerableTime = 0; before = inLine.getHealth(); hit(player, target);
            require(inLine.getHealth() == before, "A wall must stop the pressure effect");
        } finally {
            deny.clear(); WaterPressureSkills.clearPlayer(player.getUUID());
            target.discard(); inLine.discard(); wrong.discard(); villager.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setShiftKeyDown(sneak);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall; player.setAirSupply(oldAir);
            refreshWater(player);
        }
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
    private static void hit(ServerPlayer player, LivingEntity target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static int size(String name) throws Exception {
        Field field = WaterPressureSkills.class.getDeclaredField(name); field.setAccessible(true); Object state = field.get(null);
        return state instanceof Map<?, ?> map ? map.size() : ((List<?>) state).size();
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean breathFirst) {
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
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.TRIDENT, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = breathFirst ? item == Items.DIAMOND_HELMET : item == Items.DIAMOND_SWORD || item == Items.TRIDENT;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must respect direction-specific carriers");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
