package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.GrapnelRiptide;
import enchantment_fusion.mechanics.signatures.ThunderRailSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real held releases and collision-clipped moves, with prepared water, charge and spin ending. */
final class ThunderRailProbe {
    private ThunderRailProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(ThunderRailSkills.RIPTIDE, 3, ThunderRailSkills.CHANNELING, 1);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR, Items.BOW, Items.FISHING_ROD, Items.DIAMOND_BOOTS));
        ServerLevel level = player.level(); Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        Field ticks = field("autoSpinAttackTicks"), damage = field("autoSpinAttackDmg"), item = field("autoSpinAttackItemStack");
        int oldTicks = ticks.getInt(player); float oldDamage = damage.getFloat(player); Object oldItem = item.get(player);
        boolean oldSpin = player.isAutoSpinAttack();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            for (int x = -1; x <= 7; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 2; y++) {
                BlockPos pos = origin.offset(x, y, z); BlockState prior = level.getBlockState(pos);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && prior.isAir(), "Thunder rail fixture needs disposable loaded air");
                saved.put(pos, prior); level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            }
            Vec3 start = player.position(); LivingEntity enemy = f.spawn(false, 2, 5);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, level), 2, 5);
            for (boolean nativePrimary : new boolean[]{true, false}) {
                reset(player, start); ItemStack trident = f.equip(Items.TRIDENT, pair);
                float strength = EnchantmentHelper.getTridentSpinAttackStrength(trident, player);
                require(strength > 0, "Native Riptide must contribute real spin strength");
                if (!nativePrimary) {
                    trident.remove(DataComponents.ENCHANTMENTS);
                    require(Math.abs(GrapnelRiptide.project(trident, player, 0) - strength) < 1.0E-5F,
                        "The missing primary must reproduce the same data-pack Riptide value once");
                }
                require(((TridentItem) trident.getItem()).releaseUsing(trident, level, player, trident.getUseDuration(player) - 10),
                    "An unheld direct call may retain vanilla Riptide");
                player.move(MoverType.SELF, new Vec3(1.5, 0, 0)); clearSpin(player); ThunderRailSkills.tickServer(level.getServer());
                require(!marked(player), "An unheld release cannot create anchors");
                reset(player, start); charge(player, trident, 9); require(!player.isAutoSpinAttack() && !marked(player), "Nine ticks cannot create anchors");
                reset(player, start); charge(player, trident, 10); player.setPos(player.position().add(2, 0, 0));
                clearSpin(player); ThunderRailSkills.tickServer(level.getServer()); require(!marked(player), "An unobserved teleport cannot create anchors");
                reset(player, start); charge(player, trident, 10); player.move(MoverType.SELF, new Vec3(-1.5, 0, 0));
                clearSpin(player); ThunderRailSkills.tickServer(level.getServer()); require(!marked(player), "Opposite movement cannot create anchors");
                reset(player, start); createRail(player, trident); require(marked(player), "A confirmed connected spin creates two anchors");
                player.setItemSlot(EquipmentSlot.MAINHAND, trident.copy()); ThunderRailSkills.tickServer(level.getServer());
                require(!marked(player), "An equal copied trident cannot retain the anchors");
                player.setItemSlot(EquipmentSlot.MAINHAND, trident); reset(player, start); createRail(player, trident);
                require(marked(player), "Original trident can make a fresh rail");
                enemy.setPos(start.x + 1.5D, start.y, start.z); villager.setPos(start.x + 1.5D, start.y, start.z + 0.1D);
                enemy.invulnerableTime = 0; float hp = enemy.getHealth(), protectedHp = villager.getHealth();
                Map<BlockPos, BlockState> beforeBlocks = new LinkedHashMap<>();
                for (BlockPos pos : saved.keySet()) beforeBlocks.put(pos, level.getBlockState(pos));
                int bolts = level.getEntitiesOfClass(LightningBolt.class, new AABB(origin).inflate(12)).size();
                refreshWater(player); charge(player, trident, 10);
                require(enemy.getHealth() < hp && villager.getHealth() == protectedHp && !marked(player),
                    "The second real use discharges once into an eligible enemy, not a villager");
                require(level.getEntitiesOfClass(LightningBolt.class, new AABB(origin).inflate(12)).size() == bolts,
                    "The virtual rail must not summon a real lightning bolt");
                for (var entry : beforeBlocks.entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                    "The rail cannot replace water or ignite a block");
                clearSpin(player); ThunderRailSkills.tickServer(level.getServer());
                enemy.setPos(start.x + 1.5D, start.y, start.z + 5); villager.setPos(start.x + 1.5D, start.y, start.z + 5);
                reset(player, start); createRail(player, trident); require(marked(player), "Wall test needs two anchors");
                BlockPos wall = origin.east(); level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                enemy.setPos(start.x + 1.5D, start.y, start.z); hp = enemy.getHealth(); enemy.invulnerableTime = 0;
                refreshWater(player); charge(player, trident, 10);
                require(enemy.getHealth() == hp && !marked(player), "A later wall blocks and consumes the obstructed rail");
                level.setBlockAndUpdate(wall, Blocks.WATER.defaultBlockState());
            }
        } finally {
            ThunderRailSkills.clearPlayer(player.getUUID());
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.stopUsingItem(); ticks.setInt(player, oldTicks); damage.setFloat(player, oldDamage); item.set(player, oldItem);
            flag().invoke(player, 4, oldSpin); refreshWater(player);
        }
    }
    private static void createRail(ServerPlayer player, ItemStack trident) throws Exception {
        charge(player, trident, 10);
        require(player.isAutoSpinAttack(), "A wet charged held release must start the real spin");
        player.move(MoverType.SELF, new Vec3(1.5, 0, 0)); player.move(MoverType.SELF, new Vec3(1.5, 0, 0));
        clearSpin(player); ThunderRailSkills.tickServer(player.level().getServer());
    }
    private static void reset(ServerPlayer player, Vec3 position) throws Exception {
        ThunderRailSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); clearSpin(player);
        player.setPos(position); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(false); player.setYRot(-90); player.setXRot(0); refreshWater(player);
        require(player.isInWaterOrRain(), "The player must be in the temporary water");
    }
    private static void charge(ServerPlayer player, ItemStack trident, int duration) throws Exception {
        require(((TridentItem) trident.getItem()).use(player.level(), player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
            "Wet trident use must start normally");
        field("useItemRemaining").setInt(player, trident.getUseDuration(player) - duration); player.releaseUsingItem();
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = ThunderRailSkills.class.getDeclaredField("RAILS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static Field field(String name) throws Exception { Field field = LivingEntity.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Method flag() throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("setLivingEntityFlag", int.class, boolean.class); method.setAccessible(true); return method;
    }
    private static void clearSpin(ServerPlayer player) throws Exception {
        field("autoSpinAttackTicks").setInt(player, 0); field("autoSpinAttackDmg").setFloat(player, 0);
        field("autoSpinAttackItemStack").set(player, null); flag().invoke(player, 4, false);
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
}
