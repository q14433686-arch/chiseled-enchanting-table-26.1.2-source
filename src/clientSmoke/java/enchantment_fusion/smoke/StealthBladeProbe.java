package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.StealthBladeSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
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

/** Six individual prepared server fixtures; movement is simulated, not real client input. */
final class StealthBladeProbe {
    private StealthBladeProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean sneakFirst) throws Exception {
        ServerLevel level = player.level();
        FusedPair pair = FusedPair.clean(attack, 4, StealthBladeSkills.SNEAK, 3);
        if (sneakFirst) pair = pair.flipped();
        checkStation(level, pair, sneakFirst); StealthBladeSkills.clearPlayer(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] old = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) old[i] = player.getItemBySlot(slots[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(), sneak = player.isShiftKeyDown();
        double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = StealthBladeSkills.BANE.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity inLine = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>(); ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        BlockPos wall = origin.east(3).above(); BlockState oldWall = level.getBlockState(wall);
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(wall) && oldWall.isAir(), "Fixture needs open disposable space");
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
            if (sneakFirst) {
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                player.setItemSlot(EquipmentSlot.LEGS, fused(level, Items.DIAMOND_LEGGINGS, pair));
            } else {
                ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair), trident = fused(level, Items.TRIDENT, pair);
                player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                float nativeDamage = EnchantmentHelper.modifyDamage(level, sword, target, level.damageSources().playerAttack(player), 1);
                player.setItemSlot(EquipmentSlot.MAINHAND, trident);
                float replayDamage = EnchantmentHelper.modifyDamage(level, trident, target, level.damageSources().playerAttack(player), 1);
                require(nativeDamage > 1 && Math.abs(nativeDamage - replayDamage) < 0.001F, "A cross-carrier must preserve the main damage once");
                player.setItemSlot(EquipmentSlot.MAINHAND, sword);
                deny.add(target.getUUID()); hit(player, target); deny.clear();
                require(size("MARKS") == 0, "A cancelled hit must not create an imprint");
                hit(player, target); require(size("MARKS") == 1, "A full damaging hit must create an imprint");
            }
            prepare(player, origin, sneakFirst);
            String state = sneakFirst ? "CHARGES" : "MARKS";
            require(size(state) == 1 && isReady(state), "Simulated crouch movement must prepare this direction");
            deny.add(target.getUUID()); hit(player, target); deny.clear();
            require(size(state) == 1 && isReady(state), "A cancelled follow-up must not consume readiness");
            float before = inLine.getHealth(), wrongBefore = wrong.getHealth(), villagerBefore = villager.getHealth();
            hit(player, target);
            require(size(state) == 0 && inLine.getHealth() < before, "A real damaging hit must consume readiness and cut a qualifying enemy");
            require(villager.getHealth() == villagerBefore, "The cutting line must not harm villagers");
            if (!StealthBladeSkills.SHARP.equals(attack)) require(wrong.getHealth() == wrongBefore, "The attack theme must restrict the line's targets");

            player.setShiftKeyDown(false); player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            StealthBladeSkills.tickServer(level.getServer());
            if (!sneakFirst) { hit(player, target); require(size("MARKS") == 1, "A new full hit can create another imprint"); }
            prepare(player, origin, sneakFirst);
            require(size(state) == 1 && isReady(state), "The second state must become ready");
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            inLine.invulnerableTime = 0; before = inLine.getHealth(); hit(player, target);
            require(inLine.getHealth() == before, "A wall must stop the cutting line");
        } finally {
            deny.clear(); StealthBladeSkills.clearPlayer(player.getUUID()); level.setBlockAndUpdate(wall, oldWall);
            target.discard(); inLine.discard(); wrong.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], old[i]); speed.setBaseValue(oldSpeed);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setShiftKeyDown(sneak);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall;
        }
    }
    private static void prepare(ServerPlayer player, BlockPos origin, boolean sneakFirst) {
        player.setShiftKeyDown(true); StealthBladeSkills.tickServer(player.level().getServer());
        player.setPos(origin.getX() + 1.5D, origin.getY(), origin.getZ() + 0.5D); StealthBladeSkills.tickServer(player.level().getServer());
        if (sneakFirst) {
            player.setPos(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D); StealthBladeSkills.tickServer(player.level().getServer());
        }
    }
    private static void hit(ServerPlayer player, LivingEntity target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static List<?> state(String name) throws Exception {
        Field field = StealthBladeSkills.class.getDeclaredField(name); field.setAccessible(true); return (List<?>) field.get(null);
    }
    private static int size(String name) throws Exception { return state(name).size(); }
    private static boolean isReady(String name) throws Exception {
        Object value = state(name).getFirst(); Field field = value.getClass().getDeclaredField("ready"); field.setAccessible(true); return field.getBoolean(value);
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean sneakFirst) {
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
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.TRIDENT, Items.DIAMOND_LEGGINGS, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                boolean supported = sneakFirst ? item == Items.DIAMOND_LEGGINGS : item == Items.DIAMOND_SWORD || item == Items.TRIDENT;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must respect direction-specific carriers");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
