package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Candidate fixture uses a high test-only Thorns level to remove randomness, not natural gameplay. */
final class GrapnelThornsProbe {
    private GrapnelThornsProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair stationPair = FusedPair.clean(FireThornsSkills.THORNS, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), stationPair,
            List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_BOOTS, Items.SHIELD),
            List.of(Items.MACE, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.BOW));
        FusedPair pair = FusedPair.clean(FireThornsSkills.THORNS, 7, GrapnelSkills.LURE, 2);
        Set<UUID> deny = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !deny.contains(entity.getUUID()));
        double maxHealth = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        float oldHealth = player.getHealth(), oldAbsorption = player.getAbsorptionAmount(); int oldInvulnerable = player.invulnerableTime;
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); player.setHealth(200); player.setAbsorptionAmount(0);
            Zombie enemy = (Zombie) f.spawn(false, 3, 0); enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            int nativeWear = -1;
            for (boolean nativePrimary : new boolean[]{true, false}) {
                GrapnelSkills.clearPlayer(player.getUUID()); FireThornsSkills.onDisconnect(player.getUUID());
                ItemStack chest = f.equip(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, pair);
                if (!nativePrimary) chest.remove(DataComponents.ENCHANTMENTS);
                deny.add(player.getUUID()); attack(player, enemy); deny.clear();
                require(marked(player) == null, "A cancelled owner hit cannot mark");
                deny.add(enemy.getUUID()); float before = enemy.getHealth(); attack(player, enemy); deny.clear();
                require(enemy.getHealth() == before && marked(player) == null, "Cancelled reflected damage cannot mark");
                int priorWear = chest.getDamageValue(); enemy.setHealth(200); attack(player, enemy);
                int wear = chest.getDamageValue() - priorWear;
                require(enemy.getHealth() < 200 && enemy.getUUID().equals(marked(player)), "Only actual attributed Thorns loss marks the attacker");
                require(wear >= 2, "Real Thorns must retain its original durability cost");
                if (nativePrimary) nativeWear = wear; else require(wear == nativeWear, "A missing primary is replayed once, not twice");
                player.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                require(!GrapnelSkills.activate(player), "An armor copy cannot spend the original mark");
                player.setItemSlot(EquipmentSlot.CHEST, chest); GrapnelSkills.clearPlayer(player.getUUID()); enemy.setHealth(200);
                attack(player, enemy); enemy.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && marked(player) == null,
                    "Wearing the original armor can pull the marked attacker once");
                require(!GrapnelSkills.activate(player), "A spent attacker mark cannot pull twice");
            }
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); GrapnelSkills.clearPlayer(player.getUUID());
            ItemStack shield = f.equip(EquipmentSlot.OFFHAND, Items.SHIELD, pair); shield.remove(DataComponents.ENCHANTMENTS);
            enemy.setHealth(200); attack(player, enemy);
            require(enemy.getUUID().equals(marked(player)) && shield.getDamageValue() >= 2,
                "A held shield can project missing Thorns through its real effect and wear");
            GrapnelSkills.clearPlayer(player.getUUID()); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            FireThornsSkills.onDisconnect(player.getUUID());
            ItemStack mixed = f.equip(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, pair);
            FusionStationOperations.applyFusedPairToGear(mixed,
                FusedPair.clean(FireThornsSkills.THORNS, 7, FireThornsSkills.FIRE, 4), f.level.registryAccess());
            enemy.setHealth(200); attack(player, enemy);
            require(enemy.getHealth() == 200 && marked(player) == null,
                "The existing fire/thorns delayed-melee replacement must not acquire a second instant Thorns effect");
        } finally {
            deny.clear(); GrapnelSkills.clearPlayer(player.getUUID()); FireThornsSkills.onDisconnect(player.getUUID());
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth); player.setHealth(oldHealth);
            player.setAbsorptionAmount(oldAbsorption); player.invulnerableTime = oldInvulnerable;
        }
    }
    private static void attack(ServerPlayer player, Zombie enemy) {
        player.setHealth(200); player.setAbsorptionAmount(0); player.invulnerableTime = 0; enemy.invulnerableTime = 0;
        enemy.doHurtTarget(player.level(), player);
    }
    private static UUID marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method getter = mark.getClass().getDeclaredMethod("target"); getter.setAccessible(true); return (UUID) getter.invoke(mark);
    }
}
