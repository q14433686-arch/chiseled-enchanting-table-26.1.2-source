package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.LootingFireSeedSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real kills and loot, with player position and server skill ticks prepared in one disposable world. */
final class LootingFireSeedProbe {
    private LootingFireSeedProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(LootingFireSeedSkills.LOOTING, 3, LootingFireSeedSkills.FIRE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair,
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR, Items.MACE, Items.TRIDENT),
            List.of(Items.BOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        ServerLevel level = player.level(); AABB area = new AABB(origin).inflate(12);
        Set<UUID> oldItems = itemIds(level, area), denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !denied.contains(entity.getUUID()));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            LootingFireSeedSkills.clearPlayer(player.getUUID());
            Vec3 start = player.position(); ItemStack weapon = f.equip(Items.DIAMOND_SWORD, pair);
            LivingEntity target = f.spawn(false, 2.5D, 0); target.setHealth(100);
            f.hit(target, true); require(seeds(player) == 0, "A surviving hit cannot create a fire seed");
            target.setHealth(1); denied.add(target.getUUID()); f.hit(target, true); denied.clear();
            require(target.isAlive() && seeds(player) == 0, "Cancelled lethal damage cannot create a seed");
            Villager protectedVictim = f.spawn(new Villager(EntityType.VILLAGER, level), 2.5D, -3);
            protectedVictim.setHealth(1); f.hit(protectedVictim, true);
            require(seeds(player) == 0, "A protected villager cannot be used to create a seed");

            Vec3 death = target.position(); f.hit(target, true);
            require(!target.isAlive() && seeds(player) == 1, "A real eligible direct kill must create one fire seed");
            LivingEntity enemy = f.spawn(false, 4.4D, 0);
            LivingEntity deniedEnemy = f.spawn(false, 2.5D, -1.9D); denied.add(deniedEnemy.getUUID());
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, level), 2.5D, 1.9D);
            ItemStack unrelated = new ItemStack(Items.DIAMOND); ItemEntity loose = new ItemEntity(level, death.x, death.y, death.z + 0.2D, unrelated);
            require(level.addFreshEntity(loose), "The unrelated test drop must enter the level");
            BlockPos center = BlockPos.containing(death); List<BlockState> blocks = snapshot(level, center);
            float enemyHealth = enemy.getHealth(), villagerHealth = villager.getHealth();
            player.setPos(death); LootingFireSeedSkills.tickServer(level.getServer());
            require(active(player) == 1 && enemy.getHealth() < enemyHealth && enemy.isOnFire(),
                "Approaching the seed with the same weapon raises the virtual ring and burns an eligible enemy at its edge");
            require(villager.getHealth() == villagerHealth && !villager.isOnFire() && !player.isOnFire(),
                "The owner and protected villagers are not harmed by the ring");
            require(deniedEnemy.getHealth() == 200 && !deniedEnemy.isOnFire(), "Cancelled ring damage cannot ignite an enemy");
            denied.clear();
            require(loose.isAlive() && loose.getItem() == unrelated && unrelated.getCount() == 1,
                "The virtual ring neither consumes nor copies unrelated item entities");
            require(blocks.equals(snapshot(level, center)), "The virtual ring cannot place fire or change blocks");
            player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy()); LootingFireSeedSkills.tickServer(level.getServer());
            require(seeds(player) == 0, "An equal copy of another weapon cannot inherit the seed or ring");

            player.setPos(start); player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            LivingEntity nested = f.spawn(false, 2.5D, 0); nested.setHealth(1);
            var outer = MeleeImpulseSkills.beginAttack(player, weapon, nested, EquipmentSlot.MAINHAND);
            try { f.hit(nested, true); } finally { MeleeImpulseSkills.endAttack(outer, true); }
            require(seeds(player) == 0, "Ambiguous nested attacks cannot create seeds");

            // Deterministic loot table verifies both the native and the cross-carrier Looting primary.
            for (boolean nativePrimary : new boolean[]{true, false}) {
                LootingFireSeedSkills.clearPlayer(player.getUUID()); player.setPos(start);
                ItemStack lootWeapon = f.equip(nativePrimary ? Items.DIAMOND_SWORD : Items.TRIDENT, pair);
                if (!nativePrimary) lootWeapon.remove(DataComponents.ENCHANTMENTS);
                LootZombie victim = f.spawn(new LootZombie(level), 2.5D, 0); victim.setHealth(1);
                Field table = Mob.class.getDeclaredField("lootTable"); table.setAccessible(true);
                table.set(victim, Optional.of(ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("ef_client_smoke", "looting_count"))));
                Set<UUID> before = itemIds(level, area); f.hit(victim, true);
                List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
                require(!victim.isAlive() && seeds(player) == 1 && loot.size() == 1 && loot.getFirst().getItem().is(Items.IRON_INGOT)
                    && loot.getFirst().getItem().getCount() == 4 && Math.abs(victim.sample - 0.28F) < 0.0001F,
                    "Native and projected Looting each affect real loot once, while leaving a seed");
                loot.forEach(ItemEntity::discard);
            }
        } finally {
            denied.clear(); LootingFireSeedSkills.clearPlayer(player.getUUID());
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!oldItems.contains(item.getUUID())) item.discard();
        }
    }
    private static List<BlockState> snapshot(ServerLevel level, BlockPos center) {
        java.util.ArrayList<BlockState> states = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(5, 1, 5))) states.add(level.getBlockState(pos));
        return states;
    }
    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>(); for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) ids.add(item.getUUID()); return ids;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> states() throws Exception {
        Field field = LootingFireSeedSkills.class.getDeclaredField("SEEDS"); field.setAccessible(true); return (List<Object>) field.get(null);
    }
    private static long seeds(ServerPlayer player) throws Exception {
        long result = 0; for (Object seed : states()) {
            Field owner = seed.getClass().getDeclaredField("owner"); owner.setAccessible(true);
            if (player.getUUID().equals(owner.get(seed))) result++;
        } return result;
    }
    private static long active(ServerPlayer player) throws Exception {
        long result = 0; for (Object seed : states()) {
            Field owner = seed.getClass().getDeclaredField("owner"), active = seed.getClass().getDeclaredField("active");
            owner.setAccessible(true); active.setAccessible(true);
            if (player.getUUID().equals(owner.get(seed)) && active.getBoolean(seed)) result++;
        } return result;
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
