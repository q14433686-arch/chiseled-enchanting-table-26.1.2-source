package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelLunge;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mixin.LivingEntityAttackChargeAccessor;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Real PiercingWeapon attacks, with a prepared player, target and attack charge. */
final class GrapnelLungeProbe {
    private GrapnelLungeProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelLunge.LUNGE, 2, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_SPEAR, Items.GOLDEN_SPEAR),
            List.of(Items.DIAMOND_SWORD, Items.TRIDENT, Items.MACE, Items.DIAMOND_PICKAXE, Items.FISHING_ROD));
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> !denied.contains(target.getUUID()));
        FoodData food = player.getFoodData(); int oldFood = food.getFoodLevel(); float oldSaturation = food.getSaturationLevel();
        Field exhaustion = FoodData.class.getDeclaredField("exhaustionLevel"); exhaustion.setAccessible(true);
        float oldExhaustion = exhaustion.getFloat(food), oldHead = player.getYHeadRot(); Vec3 oldKnown = player.getKnownMovement();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            food.setFoodLevel(20); food.setSaturation(5); player.setYRot(-90); player.setYHeadRot(-90); player.setXRot(0);
            player.setKnownMovement(Vec3.ZERO); GrapnelSkills.clearPlayer(player.getUUID());
            LivingEntity target = f.spawn(false, 2.8, 0);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2.8, 5);
            for (boolean nativePrimary : new boolean[]{true, false}) {
                ItemStack spear = f.equip(Items.DIAMOND_SPEAR, pair);
                if (!nativePrimary) spear.remove(DataComponents.ENCHANTMENTS);
                detect(player); GrapnelSkills.clearPlayer(player.getUUID());
                target.setPos(player.getX() + 2.8, player.getY(), player.getZ() + 5);
                thrust(player, spear, target, villager); require(marked(player) == null, "A forward lunge without a hit cannot mark");
                target.setPos(player.getX() + 2.8, player.getY(), player.getZ());
                denied.add(target.getUUID()); thrust(player, spear, target, villager); denied.clear();
                require(marked(player) == null, "Cancelled damage cannot mark, even if the spear still lunges");
                food.setFoodLevel(6); thrust(player, spear, target, villager);
                require(marked(player) == null, "A hit without the actual Lunge impulse cannot mark"); food.setFoodLevel(20);
                target.setPos(player.getX() + 2.8, player.getY(), player.getZ() + 5);
                villager.setPos(player.getX() + 2.8, player.getY(), player.getZ());
                thrust(player, spear, target, villager); require(marked(player) == null, "A protected villager cannot be marked");
                villager.setPos(player.getX() + 2.8, player.getY(), player.getZ() + 5);
                target.setPos(player.getX() + 2.8, player.getY(), player.getZ());
                int previousDamage = spear.getDamageValue(); thrust(player, spear, target, villager);
                require(target.getUUID().equals(marked(player)) && player.getDeltaMovement().x > 0 && spear.getDamageValue() > previousDamage,
                    "Same real thrust, accepted hit, forward impulse and ordinary wear must mark the target");
                double singleRankImpulse = player.getDeltaMovement().x;
                player.setItemSlot(EquipmentSlot.OFFHAND, spear); player.setItemSlot(EquipmentSlot.MAINHAND, spear.copy());
                require(!GrapnelSkills.activate(player), "A copied spear cannot spend the original mark");
                player.setItemSlot(EquipmentSlot.MAINHAND, spear); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && marked(player) == null,
                    "The original spear recalls its marked target exactly once");
                GrapnelSkills.clearPlayer(player.getUUID());
                target.invulnerableTime = 0; player.setDeltaMovement(Vec3.ZERO);
                ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(200);
                player.attack(target);
                require(marked(player) == null && player.getDeltaMovement().x > 0,
                    "An ordinary swing preserves the primary impulse but cannot mark as a spear thrust");
                if (!nativePrimary) {
                    FusedEnchantmentList pairs = spear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                    spear.set(FusionDataComponents.FUSED_PAIRS, pairs.append(FusedPair.clean(GrapnelLunge.LUNGE, 3, GrapnelSkills.LURE, 2)));
                    GrapnelSkills.clearPlayer(player.getUUID()); target.setPos(player.getX() + 2.8, player.getY(), player.getZ());
                    thrust(player, spear, target, villager);
                    require(marked(player) == null && player.getDeltaMovement().x > singleRankImpulse + 0.1,
                        "Ambiguous old primary sources must not mark, but their highest missing primary still runs");
                }
            }
        } finally {
            denied.clear(); GrapnelSkills.clearPlayer(player.getUUID());
            food.setFoodLevel(oldFood); food.setSaturation(oldSaturation); exhaustion.setFloat(food, oldExhaustion);
            player.setYHeadRot(oldHead); player.setKnownMovement(oldKnown); detect(player);
        }
    }
    private static void thrust(ServerPlayer player, ItemStack spear, LivingEntity target, LivingEntity villager) {
        player.setDeltaMovement(Vec3.ZERO); target.invulnerableTime = 0; villager.invulnerableTime = 0;
        ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(200);
        spear.get(DataComponents.PIERCING_WEAPON).attack(player, EquipmentSlot.MAINHAND);
    }
    private static UUID marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method target = mark.getClass().getDeclaredMethod("target"); target.setAccessible(true); return (UUID) target.invoke(mark);
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
}
