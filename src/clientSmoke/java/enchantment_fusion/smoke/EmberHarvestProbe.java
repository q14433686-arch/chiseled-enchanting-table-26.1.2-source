package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.BurningTargetHistory;
import enchantment_fusion.mechanics.signatures.EmberHarvestSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Real attacks and same-death loot with prepared burn history, charge and input. */
final class EmberHarvestProbe {
    private EmberHarvestProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(EmberHarvestSkills.FIRE, 2, EmberHarvestSkills.LOOTING, 3);
        SharedMeleeFixture.checkStation(player.level(), pair,
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR, Items.MACE, Items.TRIDENT),
            List.of(Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(12);
        Set<UUID> oldItems = itemIds(level, area), denied = new HashSet<>(); List<LivingEntity> observed = new ArrayList<>();
        boolean oldShift = player.isShiftKeyDown();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !denied.contains(entity.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            EmberHarvestSkills.clearPlayer(player.getUUID()); ItemStack weapon = f.equip(Items.DIAMOND_SWORD, pair);
            LivingEntity victim = f.spawn(false, 2.5D, 0); observed.add(victim); victim.igniteForSeconds(15);
            BurningTargetHistory.observe(victim); player.setShiftKeyDown(true); f.hit(victim, true);
            require(mark(player) == null && victim.isOnFire(), "An incompletely observed fire cannot be harvested");
            ageBurn(victim); player.setShiftKeyDown(false); f.hit(victim, true);
            require(mark(player) == null && victim.isOnFire(), "Standing hits cannot harvest fire");
            player.setShiftKeyDown(true); f.hit(victim, false);
            require(mark(player) == null && victim.isOnFire(), "Partial hits cannot harvest fire");
            denied.add(victim.getUUID()); f.hit(victim, true); denied.clear();
            require(mark(player) == null && victim.isOnFire(), "Cancelled damage cannot harvest fire");
            var outer = MeleeImpulseSkills.beginAttack(player, weapon, victim, EquipmentSlot.MAINHAND);
            try { f.hit(victim, true); } finally { MeleeImpulseSkills.endAttack(outer, true); }
            require(mark(player) == null && victim.isOnFire(), "Ambiguous nested attacks cannot harvest fire");
            f.hit(victim, true);
            require(mark(player) != null && !victim.isOnFire(), "A confirmed sneaking full hit extracts the observed fire once");

            setLoot((Mob) victim, "ember_harvest_ordinary"); victim.setHealth(1);
            ItemStack unrelated = new ItemStack(Items.DIAMOND); ItemEntity loose = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ() + 0.2D, unrelated);
            require(level.addFreshEntity(loose), "The unrelated test drop must enter the level");
            Set<UUID> before = itemIds(level, area); f.hit(victim, true);
            List<ItemEntity> produced = level.getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
            require(!victim.isAlive() && mark(player) == null && produced.size() == 2
                && produced.stream().allMatch(item -> item.getItem().is(Items.ROTTEN_FLESH))
                && produced.stream().mapToInt(item -> item.getItem().getCount()).sum() == 5,
                "Exactly two extra plain items must come from the same actual three-item death loot");
            require(loose.isAlive() && loose.getItem() == unrelated && unrelated.getCount() == 1,
                "The reward cannot consume or copy an unrelated nearby drop");
            produced.forEach(ItemEntity::discard);

            LivingEntity rare = f.spawn(false, 2.5D, 0); observed.add(rare); rare.igniteForSeconds(15); ageBurn(rare);
            f.hit(rare, true); require(mark(player) != null && !rare.isOnFire(), "Rare-loot test needs a real extraction");
            setLoot((Mob) rare, "looting_no_bonus"); rare.setHealth(1); Set<UUID> prior = itemIds(level, area); f.hit(rare, true);
            produced = level.getEntitiesOfClass(ItemEntity.class, area, item -> !prior.contains(item.getUUID()));
            require(produced.size() == 1 && produced.getFirst().getItem().is(Items.IRON_INGOT)
                && produced.getFirst().getItem().getCount() == 1, "Unlisted rare or equipment loot must not be duplicated");
            EmberHarvestSkills.tickServer(level.getServer()); produced.forEach(ItemEntity::discard);

            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, level), 2.5D, 2); observed.add(villager);
            villager.igniteForSeconds(15); ageBurn(villager); f.hit(villager, true);
            require(mark(player) == null && villager.isOnFire(), "Protected villagers cannot receive harvest marks");
            ItemStack trident = f.equip(Items.TRIDENT, pair); trident.remove(DataComponents.ENCHANTMENTS);
            LivingEntity cross = f.spawn(false, 2.5D, 0); observed.add(cross); player.setShiftKeyDown(false); f.hit(cross, true);
            require(cross.isOnFire(), "An actual non-native melee hit must retain its Fire Aspect primary once");
        } finally {
            player.setShiftKeyDown(oldShift); denied.clear(); EmberHarvestSkills.clearPlayer(player.getUUID());
            observed.forEach(BurningTargetHistory::forget);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!oldItems.contains(item.getUUID())) item.discard();
        }
    }
    private static void setLoot(Mob victim, String tableName) throws Exception {
        Field field = Mob.class.getDeclaredField("lootTable"); field.setAccessible(true);
        field.set(victim, Optional.of(ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath("ef_client_smoke", tableName))));
    }
    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) ids.add(item.getUUID()); return ids;
    }
    private static Object mark(ServerPlayer player) throws Exception {
        Field field = EmberHarvestSkills.class.getDeclaredField("MARKS"); field.setAccessible(true); Object store = field.get(null);
        var method = store.getClass().getDeclaredMethod("get", ServerPlayer.class, String.class); method.setAccessible(true);
        return method.invoke(store, player, "ember-harvest");
    }
    /** Artificially age an existing real fire observation; this is not a natural tick-duration test. */
    @SuppressWarnings("unchecked")
    private static void ageBurn(LivingEntity victim) throws Exception {
        BurningTargetHistory.observe(victim);
        Field field = BurningTargetHistory.class.getDeclaredField("ENTRIES"); field.setAccessible(true);
        Map<UUID, Object> entries = (Map<UUID, Object>) field.get(null); Object entry = entries.get(victim.getUUID());
        require(entry != null && victim.level().getGameTime() >= 19, "The disposable world must have enough ticks for the prepared burn");
        var ctor = entry.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
        entries.put(victim.getUUID(), ctor.newInstance(victim, victim.level().dimension(), victim.level().getGameTime() - 19, victim.level().getGameTime()));
    }
}
