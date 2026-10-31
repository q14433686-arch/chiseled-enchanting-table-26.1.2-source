package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

/** Prepared real server damage paths; calls tickServer manually and is not natural multiplayer play. */
final class FireThornsProbe {
    private static final FusedPair AMBUSH = FusedPair.clean("minecraft:thorns", 3, "minecraft:fire_protection", 4);
    private FireThornsProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level(); FusedPair pair = reverse ? AMBUSH.flipped() : AMBUSH;
        checkStation(level, pair); FireThornsSkills.onDisconnect(player.getUUID());
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] oldGear = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) oldGear[i] = player.getItemBySlot(slots[i]);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot(), oldHealth = player.getHealth(), oldAbsorption = player.getAbsorptionAmount();
        int oldFireTicks = player.getRemainingFireTicks(), oldInvuln = player.invulnerableTime;
        GameType oldMode = player.gameMode.getGameModeForPlayer(); MobEffectInstance oldResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin) && level.getBlockState(origin).isAir(), "Thorns fixture needs open disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.removeEffect(MobEffects.FIRE_RESISTANCE);
            for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F); player.setAbsorptionAmount(0);
            enemy.setNoAi(true); enemy.setPos(origin.getX() + 1.6D, origin.getY(), origin.getZ() + 0.5D);
            enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D); enemy.setHealth(100.0F);
            villager.setNoAi(true); villager.setPos(origin.getX() + 1.6D, origin.getY(), origin.getZ() + 1.0D);
            require(level.addFreshEntity(enemy) && level.addFreshEntity(villager), "Attacker and protected observer must enter the disposable world");
            if (!reverse) {
                ItemStack chest = fused(level, Items.DIAMOND_CHESTPLATE, pair); player.setItemSlot(EquipmentSlot.CHEST, chest);
                float health = enemy.getHealth();
                deny.add(player.getUUID()); player.invulnerableTime = 0;
                require(!enemy.doHurtTarget(level, player), "Cancelled melee damage must fail"); deny.clear();
                player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
                require(enemy.doHurtTarget(level, player), "First real melee hit must be accepted");
                FireThornsSkills.tickServer(level.getServer());
                require(enemy.getHealth() == health && !enemy.isOnFire(), "First hit must only mark, not trigger instant thorns or ignition");
                player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0; enemy.invulnerableTime = 0;
                require(enemy.doHurtTarget(level, player), "Second real melee hit must be accepted");
                require(enemy.getHealth() == health && !enemy.isOnFire(), "Retaliation must wait until the current attack completes");
                int beforeWear = chest.getDamageValue(); FireThornsSkills.tickServer(level.getServer());
                require(enemy.getHealth() < health && enemy.isOnFire() && chest.getDamageValue() == beforeWear + 2,
                    "Second hit must trigger one real delayed thorn strike, ignition and ordinary equipment wear");
                float after = enemy.getHealth(); FireThornsSkills.tickServer(level.getServer());
                require(enemy.getHealth() == after, "A consumed mark must not retaliate twice");
            } else {
                player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                float baseline = fireHit(player, 8.0F);
                ItemStack shield = fused(level, Items.SHIELD, pair); player.setItemSlot(EquipmentSlot.OFFHAND, shield);
                float protectedDamage = fireHit(player, 8.0F);
                require(protectedDamage < baseline, "A non-native shield must receive real Fire Protection once");
                float enemyBefore = enemy.getHealth(), villagerBefore = villager.getHealth(); enemy.invulnerableTime = 0;
                FireThornsSkills.tickServer(level.getServer());
                require(enemy.getHealth() < enemyBefore && enemy.isOnFire() && villager.getHealth() == villagerBefore && !villager.isOnFire(),
                    "The fire-resisted contact curtain must prick the enemy and protect the villager");
                require(level.getBlockState(origin).isAir(), "The curtain must not place fire or other blocks");
            }
        } finally {
            deny.clear(); FireThornsSkills.onDisconnect(player.getUUID()); enemy.discard(); villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], oldGear[i]);
            player.setGameMode(oldMode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity); player.setYRot(oldYaw); player.setXRot(oldPitch);
            player.setHealth(oldHealth); player.setAbsorptionAmount(oldAbsorption); player.setRemainingFireTicks(oldFireTicks); player.invulnerableTime = oldInvuln;
            if (oldResistance != null) player.addEffect(oldResistance);
        }
    }
    private static float fireHit(ServerPlayer player, float amount) {
        player.setHealth(player.getMaxHealth()); player.setAbsorptionAmount(0); player.invulnerableTime = 0;
        float before = player.getHealth();
        require(player.hurtServer(player.level(), player.level().damageSources().inFire(), amount), "Real fire damage must be accepted");
        return before - player.getHealth();
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
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
            for (Item item : List.of(Items.DIAMOND_CHESTPLATE, Items.SHIELD, Items.DIAMOND_SWORD)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                require(item == Items.DIAMOND_SWORD ? preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST : preview.executable(),
                    "Both station paths must accept armor/shields and reject swords");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
