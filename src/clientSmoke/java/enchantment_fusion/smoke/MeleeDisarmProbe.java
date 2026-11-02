package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.MeleeDisarmSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real server-side equipment transfer and death loot; action charge and event interleaving are prepared. */
final class MeleeDisarmProbe {
    private MeleeDisarmProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(MeleeDisarmSkills.LOOTING, 3, MeleeDisarmSkills.KNOCKBACK, 2);
        SharedMeleeFixture.checkStation(player.level(), pair,
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR, Items.MACE, Items.TRIDENT),
            List.of(Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(12);
        Set<UUID> oldItems = itemIds(level, area), denied = new HashSet<>(); ItemStack[] rejectedDrop = {null};
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !denied.contains(entity.getUUID()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world == level && entity instanceof ItemEntity item && item.getItem() == rejectedDrop[0]) {
                rejectedDrop[0] = null; item.discard();
            }
        });
        int size = player.getInventory().getContainerSize(); ItemStack[] saved = new ItemStack[size];
        for (int i = 0; i < size; i++) { saved[i] = player.getInventory().getItem(i); player.getInventory().setItem(i, ItemStack.EMPTY); }
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            ItemStack weapon = f.equip(Items.DIAMOND_SWORD, pair);
            Mob target = ordinary(f, 2); ItemStack real = new ItemStack(Items.IRON_SWORD);
            real.set(DataComponents.CUSTOM_NAME, Component.literal("fixture equipment")); ItemStack snapshot = real.copy();
            target.setItemSlot(EquipmentSlot.MAINHAND, real);
            f.hit(target, false); require(target.getMainHandItem() == real, "A partial hit cannot disarm");
            denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(target.getMainHandItem() == real, "Cancelled damage cannot disarm");
            target.setDeltaMovement(Vec3.ZERO); f.hit(target, true);
            List<ItemEntity> transferred = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem() == real);
            require(target.getMainHandItem().isEmpty() && transferred.size() == 1 && ItemStack.matches(transferred.getFirst().getItem(), snapshot),
                "One original component-preserving stack must leave the actual equipment slot");
            ItemEntity drop = transferred.getFirst();
            require(drop.hasPickUpDelay() && drop.getDeltaMovement().x < 0 && target.getDeltaMovement().x > 0,
                "The ordinary item goes toward the player while the enemy is pushed away");
            ItemStack replacement = new ItemStack(Items.IRON_AXE); target.setItemSlot(EquipmentSlot.MAINHAND, replacement);
            f.hit(target, true); require(target.getMainHandItem() == replacement, "The same enemy cannot be repeatedly stripped during cooldown");
            drop.setNoPickUpDelay(); drop.playerTouch(player);
            require(!drop.isAlive() && player.getInventory().contains(snapshot), "The original item uses normal pickup exactly once");

            Mob bound = ordinary(f, 2.5D); ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            var binding = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE);
            ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchants.set(binding, 1); EnchantmentHelper.setEnchantments(helmet, enchants.toImmutable());
            bound.setItemSlot(EquipmentSlot.HEAD, helmet); f.hit(bound, true);
            require(bound.getItemBySlot(EquipmentSlot.HEAD) == helmet, "Vanilla bound equipment cannot be removed");
            Mob fusedBound = ordinary(f, 2.6D); ItemStack fusedItem = new ItemStack(Items.IRON_SWORD);
            fusedItem.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
                FusedPair.clean("minecraft:sharpness", 1, "minecraft:binding_curse", 1)));
            fusedBound.setItemSlot(EquipmentSlot.MAINHAND, fusedItem); f.hit(fusedBound, true);
            require(fusedBound.getMainHandItem() == fusedItem, "Unprojected fused Binding also prevents removal");
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, level), 2.7D, 2);
            ItemStack protectedItem = new ItemStack(Items.IRON_SWORD); villager.setItemSlot(EquipmentSlot.MAINHAND, protectedItem);
            f.hit(villager, true); require(villager.getMainHandItem() == protectedItem, "Villagers cannot be disarmed");
            Warden boss = f.spawn(new Warden(EntityType.WARDEN, level), 2.5D, -2);
            ItemStack bossItem = new ItemStack(Items.IRON_SWORD); boss.setItemSlot(EquipmentSlot.MAINHAND, bossItem);
            f.hit(boss, true); require(boss.getMainHandItem() == bossItem, "Boss-class targets cannot be disarmed");

            Mob blocked = ordinary(f, 2.8D); ItemStack blockedItem = new ItemStack(Items.IRON_AXE);
            blocked.setItemSlot(EquipmentSlot.MAINHAND, blockedItem); rejectedDrop[0] = blockedItem;
            f.hit(blocked, true);
            require(rejectedDrop[0] == null && blocked.getMainHandItem() == blockedItem,
                "Rejected item registration must leave the original equipment in place");
            f.hit(blocked, true);
            require(blocked.getMainHandItem().isEmpty(), "A rejected transfer does not spend the cooldown");

            Mob nested = ordinary(f, 2.9D); ItemStack nestedItem = new ItemStack(Items.IRON_SWORD);
            nested.setItemSlot(EquipmentSlot.MAINHAND, nestedItem);
            var enclosing = MeleeImpulseSkills.beginAttack(player, weapon, nested, EquipmentSlot.MAINHAND);
            try { f.hit(nested, true); } finally { MeleeImpulseSkills.endAttack(enclosing, true); }
            require(nested.getMainHandItem() == nestedItem, "Ambiguous nested attacks cannot transfer equipment");

            // The existing shared Looting projection must work for both native and missing primaries.
            for (boolean nativePrimary : new boolean[]{true, false}) {
                ItemStack lootWeapon = f.equip(nativePrimary ? Items.DIAMOND_SWORD : Items.TRIDENT, pair);
                if (!nativePrimary) lootWeapon.remove(DataComponents.ENCHANTMENTS);
                LootZombie victim = f.spawn(new LootZombie(level), 2.5D, 0); victim.setHealth(1);
                Field table = Mob.class.getDeclaredField("lootTable"); table.setAccessible(true);
                table.set(victim, Optional.of(ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("ef_client_smoke", "looting_count"))));
                Set<UUID> before = itemIds(level, area); f.hit(victim, true);
                List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
                require(!victim.isAlive() && loot.size() == 1 && loot.getFirst().getItem().is(Items.IRON_INGOT)
                    && loot.getFirst().getItem().getCount() == 4 && Math.abs(victim.sample - 0.28F) < 0.0001F,
                    "Native and projected Looting must each modify real death loot once");
                loot.forEach(ItemEntity::discard);
            }
        } finally {
            denied.clear(); rejectedDrop[0] = null;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!oldItems.contains(item.getUUID())) item.discard();
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, saved[i]);
        }
    }
    private static Mob ordinary(SharedMeleeFixture f, double dx) {
        Mob mob = (Mob) f.spawn(false, dx, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100); return mob;
    }
    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) ids.add(item.getUUID()); return ids;
    }
    private static final class LootZombie extends Zombie {
        float sample = -1;
        LootZombie(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
            if (source.getEntity() instanceof LivingEntity attacker) sample = EnchantmentHelper.processEquipmentDropChance(level, attacker, source, 0.25F);
            super.dropCustomDeathLoot(level, source, killedByPlayer);
        }
    }
}
