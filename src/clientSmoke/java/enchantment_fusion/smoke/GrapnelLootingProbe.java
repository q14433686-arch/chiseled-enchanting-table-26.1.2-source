package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.GrapnelLooting;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/** Prepared loot tables and targets, real damage/death/spawn, simulated delay and activation. */
final class GrapnelLootingProbe {
    private GrapnelLootingProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelLooting.LOOTING, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair,
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR, Items.MACE, Items.TRIDENT),
            List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW, Items.FISHING_ROD));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(12);
        Set<UUID> oldItems = itemIds(level, area), oldOrbs = orbIds(level, area), denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> !denied.contains(target.getUUID()));
        int size = player.getInventory().getContainerSize(); ItemStack[] inventory = new ItemStack[size];
        for (int i = 0; i < size; i++) { inventory[i] = player.getInventory().getItem(i); player.getInventory().setItem(i, ItemStack.EMPTY); }
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            for (boolean nativePrimary : new boolean[]{true, false}) {
                ItemStack weapon = f.equip(Items.DIAMOND_SWORD, pair);
                if (!nativePrimary) weapon.remove(DataComponents.ENCHANTMENTS);
                require(GrapnelSkills.hasEquipped(player), "The Looting direction must appear in the skill cycle");
                GrapnelSkills.clearPlayer(player.getUUID());
                EquipmentZombie cancelled = spawn(f, "looting_count"); denied.add(cancelled.getUUID()); f.hit(cancelled, true); denied.clear();
                require(cancelled.isAlive() && markedItem(player) == null, "Cancelled damage cannot produce a loot mark");
                EquipmentZombie saved = spawn(f, "looting_count"); saved.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                f.hit(saved, true); require(saved.isAlive() && markedItem(player) == null, "A totem rescue is not a death or a loot mark");
                EquipmentZombie real = spawn(f, "looting_count"); Set<UUID> before = itemIds(level, area); f.hit(real, true);
                ItemEntity original = onlyNew(level, area, before);
                require(!real.isAlive() && original.getItem().is(Items.IRON_INGOT) && original.getItem().getCount() == 4,
                    "One Looting III calculation must increase the actual count exactly once");
                require(Math.abs(real.sample - 0.28F) < 0.0001F, "Native or projected equipment-drop effect must apply exactly once");
                require(markedItem(player) == original, "Only the actual registered bonus stack may be marked");
                ItemEntity unrelated = new ItemEntity(level, original.getX(), original.getY(), original.getZ() + 0.5D, new ItemStack(Items.IRON_INGOT, 2));
                require(level.addFreshEntity(unrelated), "Unrelated control item must enter the world");
                require(!GrapnelSkills.activate(player) && original.isAlive(), "The ordinary pickup delay still applies");
                original.setNoPickUpDelay(); unrelated.setNoPickUpDelay();
                require(GrapnelSkills.activate(player) && !original.isAlive() && unrelated.isAlive()
                    && unrelated.getItem().getCount() == 2 && markedItem(player) == null,
                    "Recall must pick the one original item once, not scan or collect an unrelated neighbor");
                unrelated.discard();

                GrapnelSkills.clearPlayer(player.getUUID());
                EquipmentZombie chance = spawn(f, "looting_chance"); before = itemIds(level, area); f.hit(chance, true);
                ItemEntity chanceDrop = onlyNew(level, area, before);
                require(chanceDrop.getItem().getCount() == 1 && markedItem(player) == chanceDrop,
                    "A necessary Looting pool chance proven by the same roll may mark its actual output");
                chanceDrop.setNoPickUpDelay(); chanceDrop.setTarget(UUID.randomUUID());
                require(!GrapnelSkills.activate(player) && chanceDrop.isAlive(), "Another pickup owner must be respected");
                chanceDrop.setTarget(null); chanceDrop.setItem(chanceDrop.getItem().copy());
                require(!GrapnelSkills.activate(player) && chanceDrop.isAlive(), "A replaced or merged stack loses provenance"); chanceDrop.discard();
                checkNoMark(f, area, "looting_no_bonus", 1, 1);
                checkNoMark(f, area, "looting_erased", 1, 1);
                checkNoMark(f, area, "looting_split", 2, 67);
                checkNoMark(f, area, "looting_chance_baseline", 1, 1);

                GrapnelSkills.clearPlayer(player.getUUID());
                EquipmentZombie untracked = spawn(f, "looting_count"); before = itemIds(level, area);
                require(untracked.hurtServer(level, level.damageSources().playerAttack(player), 100), "Direct server damage control must be accepted");
                ItemEntity direct = onlyNew(level, area, before);
                require(direct.getItem().getCount() == 4 && markedItem(player) == null,
                    "A direct untracked caller keeps primary Looting but cannot claim an outer-action mark"); direct.discard();
            }
            ItemStack ambiguous = f.equip(Items.DIAMOND_SWORD, pair); ambiguous.remove(DataComponents.ENCHANTMENTS);
            ambiguous.set(FusionDataComponents.FUSED_PAIRS, ambiguous.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
                .append(FusedPair.clean(GrapnelLooting.LOOTING, 5, GrapnelSkills.LURE, 1)));
            checkNoMark(f, area, "looting_count", 1, 6);
            ItemStack retained = f.equip(Items.DIAMOND_SWORD, pair); retained.remove(DataComponents.ENCHANTMENTS);
            retained.set(FusionDataComponents.RETAINED_ENCHANTS, List.of(new RetainedEnchant(GrapnelLooting.LOOTING, 5)));
            checkNoMark(f, area, "looting_count", 1, 6);
        } finally {
            denied.clear(); GrapnelSkills.clearPlayer(player.getUUID());
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!oldItems.contains(item.getUUID())) item.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!oldOrbs.contains(orb.getUUID())) orb.discard();
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, inventory[i]);
        }
    }
    private static void checkNoMark(SharedMeleeFixture f, AABB area, String table, int expectedEntities, int expectedCount) throws Exception {
        GrapnelSkills.clearPlayer(f.player.getUUID()); EquipmentZombie zombie = spawn(f, table); Set<UUID> before = itemIds(f.level, area);
        f.hit(zombie, true);
        List<ItemEntity> drops = f.level.getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
        require(drops.size() == expectedEntities && drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == expectedCount
            && markedItem(f.player) == null, table + " must keep its real output without claiming an unproven item");
        drops.forEach(ItemEntity::discard);
    }
    private static EquipmentZombie spawn(SharedMeleeFixture f, String table) throws Exception {
        EquipmentZombie zombie = f.spawn(new EquipmentZombie(f.level), 2.8D, 0);
        Field field = Mob.class.getDeclaredField("lootTable"); field.setAccessible(true);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("ef_client_smoke", table));
        field.set(zombie, Optional.of(key)); zombie.setHealth(1); return zombie;
    }
    private static ItemEntity onlyNew(ServerLevel level, AABB area, Set<UUID> before) {
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
        require(found.size() == 1, "Exactly one prepared loot item must enter the world"); return found.getFirst();
    }
    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>(); for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) result.add(item.getUUID()); return result;
    }
    private static Set<UUID> orbIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>(); for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) result.add(orb.getUUID()); return result;
    }
    private static ItemEntity markedItem(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID());
        if (mark == null || !mark.getClass().getSimpleName().equals("DropsMark")) return null;
        Method drops = mark.getClass().getDeclaredMethod("drops"); drops.setAccessible(true);
        List<?> list = (List<?>) drops.invoke(mark); if (list.isEmpty()) return null;
        Method entity = list.getFirst().getClass().getDeclaredMethod("entity"); entity.setAccessible(true);
        return (ItemEntity) entity.invoke(list.getFirst());
    }
    /** The extra sampled value is a disposable fixture, not a second production roll. */
    private static final class EquipmentZombie extends Zombie {
        float sample = -1;
        EquipmentZombie(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
            if (source.getEntity() instanceof LivingEntity attacker)
                sample = EnchantmentHelper.processEquipmentDropChance(level, attacker, source, 0.25F);
            super.dropCustomDeathLoot(level, source, killedByPlayer);
        }
    }
}
