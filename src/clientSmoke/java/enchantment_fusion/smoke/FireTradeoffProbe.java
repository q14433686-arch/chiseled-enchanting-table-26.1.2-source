package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FireTradeoffSkills;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Prepared single-player fixture uses real fire damage; input and timing are simulated. */
final class FireTradeoffProbe {
    private static final FusedPair COMPENSATE = FusedPair.clean("minecraft:protection", 4, "minecraft:fire_protection", 4);
    private FireTradeoffProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? COMPENSATE.flipped() : COMPENSATE;
        checkStation(level, pair);
        Vec3 oldPos = player.position(), oldMovement = player.getDeltaMovement();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot(), oldHealth = player.getHealth(), oldAbsorb = player.getAbsorptionAmount();
        int oldFireTicks = player.getRemainingFireTicks(), oldInvuln = player.invulnerableTime;
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] oldGear = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) oldGear[i] = player.getItemBySlot(slots[i]);
        MobEffectInstance oldResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
        Zombie enemy = null; Villager villager = null;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(origin), "Fixture requires loaded disposable space");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); player.removeEffect(MobEffects.FIRE_RESISTANCE);
            for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setYRot(-90.0F); player.setXRot(0.0F); player.setAbsorptionAmount(0);
            if (!reverse) {
                ItemStack chest = fused(level, Items.DIAMOND_CHESTPLATE, pair); player.setItemSlot(EquipmentSlot.CHEST, chest);
                int damage0 = chest.getDamageValue(); float normal = fireHit(player, 8.0F);
                int ordinaryWear = chest.getDamageValue() - damage0;
                require(FireTradeoffSkills.toggle(player), "Real armor must allow enabling compensation");
                int damage1 = chest.getDamageValue(); float compensated = fireHit(player, 8.0F);
                require(compensated < normal, "Enabled compensation must reduce real post-protection fire damage");
                require(chest.getDamageValue() - damage1 > ordinaryWear, "Compensation must pass through real item wear");
                chest.setDamageValue(chest.getMaxDamage() - 1);
                float before = 5.0F;
                float after = FireTradeoffSkills.afterMagic(player, level.damageSources().inFire(), before, before);
                require(after == before && chest.getDamageValue() == chest.getMaxDamage() - 1,
                    "The extra compensation must never consume the final point of durability");
                require(FireTradeoffSkills.toggle(player), "Compensation must be switchable off");
            } else {
                ItemStack helmet = fused(level, Items.DIAMOND_HELMET, pair); player.setItemSlot(EquipmentSlot.HEAD, helmet);
                for (int i = 0; i < 4; i++) fireHit(player, 8.0F);
                require(!FireTradeoffSkills.release(player), "With no legal target, stored heat must not be spent");
                enemy = new Zombie(EntityType.ZOMBIE, level); enemy.setNoAi(true);
                enemy.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                require(level.addFreshEntity(enemy), "Hostile target must enter the disposable world");
                villager = new Villager(EntityType.VILLAGER, level); villager.setNoAi(true);
                villager.setPos(origin.getX() + 3.5D, origin.getY() + 1.0D, origin.getZ() + 1.0D);
                require(level.addFreshEntity(villager), "Protected villager must enter the disposable world");
                Vec3 protectedBefore = villager.getDeltaMovement();
                require(FireTradeoffSkills.release(player), "Accumulated real fire mitigation must release toward a legal target");
                require(enemy.getDeltaMovement().x > 0.1D && villager.getDeltaMovement().equals(protectedBefore),
                    "The directed wave must push the enemy and leave the villager alone");
                require(!FireTradeoffSkills.release(player), "Released heat must be consumed once");
            }
        } finally {
            FireTradeoffSkills.onDisconnect(player.getUUID());
            if (enemy != null) enemy.discard(); if (villager != null) villager.discard();
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], oldGear[i]);
            player.setGameMode(oldMode); player.setPos(oldPos); player.setDeltaMovement(oldMovement); player.setYRot(oldYaw); player.setXRot(oldPitch);
            player.setHealth(oldHealth); player.setAbsorptionAmount(oldAbsorb); player.setRemainingFireTicks(oldFireTicks); player.invulnerableTime = oldInvuln;
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
            for (Item item : new Item[]{Items.DIAMOND_HELMET, Items.SHIELD, Items.DIAMOND_SWORD}) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                require(item != Items.DIAMOND_SWORD ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station routes must accept armor and shields while rejecting a sword");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
