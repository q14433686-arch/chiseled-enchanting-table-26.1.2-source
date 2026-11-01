package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.GrapnelRiptide;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Calls vanilla use/release with simulated charge and collision-clipped movement in temporary water. */
final class GrapnelRiptideProbe {
    private GrapnelRiptideProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelRiptide.RIPTIDE, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.TRIDENT),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR, Items.BOW, Items.FISHING_ROD, Items.DIAMOND_BOOTS));
        ServerLevel level = player.level(); Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        Field ticks = field("autoSpinAttackTicks"), damage = field("autoSpinAttackDmg"), item = field("autoSpinAttackItemStack");
        int oldTicks = ticks.getInt(player); float oldDamage = damage.getFloat(player); Object oldItem = item.get(player);
        boolean oldSpin = player.isAutoSpinAttack();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            GrapnelSkills.clearPlayer(player.getUUID()); clearSpin(player); player.setXRot(0);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) rememberAir(level, saved, origin.offset(x, y, z));
                rememberAir(level, saved, origin.offset(x, 4, z));
            }
            for (BlockPos pos : saved.keySet()) if (pos.getY() == origin.getY() + 4) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            refreshWater(player);
            require(!player.isInWaterOrRain(), "The dry fixture must be sheltered from rain");
            LivingEntity target = f.spawn(false, 4, 0);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, level), 2, 5);
            ItemStack dry = f.equip(Items.TRIDENT, pair);
            require(((TridentItem) dry.getItem()).use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                "Riptide cannot start in dry weather");
            require(!marked(player), "Dry use cannot mark");
            for (BlockPos pos : saved.keySet()) if (pos.getY() <= origin.getY() + 2) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            refreshWater(player); require(player.isInWaterOrRain(), "Temporary water must cover the player");
            Vec3 start = player.position();
            for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                InteractionHand hand = slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                for (boolean nativePrimary : new boolean[]{true, false}) {
                    reset(player, start); player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    ItemStack trident = f.equip(slot, Items.TRIDENT, pair);
                    float nativeStrength = EnchantmentHelper.getTridentSpinAttackStrength(trident, player);
                    var nativeSound = EnchantmentHelper.pickHighestLevel(trident, EnchantmentEffectComponents.TRIDENT_SOUND);
                    require(nativeStrength > 0 && nativeSound.isPresent(), "Native Riptide must supply a strength and sound");
                    if (!nativePrimary) {
                        trident.remove(DataComponents.ENCHANTMENTS);
                        require(EnchantmentHelper.getTridentSpinAttackStrength(trident, player) == 0
                            && Math.abs(GrapnelRiptide.project(trident, player, 0) - nativeStrength) < 1.0E-5F,
                            "Missing Riptide uses the same data-pack strength exactly once");
                        require(GrapnelRiptide.projectSound(trident, level, EnchantmentEffectComponents.TRIDENT_SOUND, Optional.empty()).equals(nativeSound),
                            "Missing Riptide preserves its original data-pack sound");
                    }
                    int before = trident.getDamageValue();
                    require(((TridentItem) trident.getItem()).releaseUsing(trident, level, player, trident.getUseDuration(player) - 10),
                        "A direct vanilla release still has its normal Riptide effect");
                    player.move(MoverType.SELF, new Vec3(0.4, 0, 0)); GrapnelSkills.tickServer(level.getServer());
                    require(!marked(player), "An uncharged direct release cannot impersonate a real held use");
                    reset(player, start); chargeAndRelease(player, trident, hand, 9);
                    require(!player.isAutoSpinAttack() && !marked(player), "Nine ticks cannot release or mark");
                    reset(player, start); before = trident.getDamageValue(); chargeAndRelease(player, trident, hand, 10);
                    require(player.isAutoSpinAttack() && player.getDeltaMovement().x > 0 && trident.getDamageValue() > before,
                        "Ten ticks in water retain the real impulse, spin and ordinary wear");
                    GrapnelSkills.tickServer(level.getServer()); require(!marked(player), "Impulse without actual movement cannot mark");
                    player.move(MoverType.SELF, new Vec3(-0.4, 0, 0)); GrapnelSkills.tickServer(level.getServer());
                    require(!marked(player), "Movement against the recorded impulse cannot mark");
                    reset(player, start); chargeAndRelease(player, trident, hand, 10);
                    player.setPos(player.position().add(0.4, 0, 0)); GrapnelSkills.tickServer(level.getServer());
                    require(!marked(player), "A teleport without observed movement cannot mark");
                    reset(player, start);
                    BlockPos wall = origin.east(); level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
                    player.move(MoverType.SELF, new Vec3(0.2, 0, 0));
                    chargeAndRelease(player, trident, hand, 10); player.move(MoverType.SELF, new Vec3(1, 0, 0));
                    GrapnelSkills.tickServer(level.getServer());
                    require(!marked(player), "A solid wall preventing displacement cannot create a mark");
                    level.setBlockAndUpdate(wall, Blocks.WATER.defaultBlockState());
                    level.setBlockAndUpdate(wall.above(), Blocks.WATER.defaultBlockState());
                    reset(player, start); chargeAndRelease(player, trident, hand, 10);
                    player.move(MoverType.SELF, new Vec3(0.6, 0, 0)); GrapnelSkills.tickServer(level.getServer());
                    require(marked(player), "Actual continuous same-direction movement must mark its new position");
                    player.setItemSlot(slot, trident.copy()); require(!GrapnelSkills.activate(player), "A copied trident cannot spend the mark");
                    player.setItemSlot(slot, trident);
                    reset(player, start); chargeAndRelease(player, trident, hand, 10);
                    player.move(MoverType.SELF, new Vec3(0.6, 0, 0)); GrapnelSkills.tickServer(level.getServer());
                    require(marked(player), "The original trident must be able to make a new mark");
                    villager.setPos(player.getX() + 1.5, player.getY(), player.getZ());
                    require(!GrapnelSkills.activate(player) && marked(player), "A protected villager blocks recall without consuming the mark");
                    villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 5);
                    target.setDeltaMovement(Vec3.ZERO);
                    require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && !marked(player),
                        "The original trident recalls one eligible enemy");
                    if (!nativePrimary) {
                        reset(player, start);
                        FusedEnchantmentList pairs = trident.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        trident.set(FusionDataComponents.FUSED_PAIRS, pairs.append(FusedPair.clean(GrapnelRiptide.RIPTIDE, 1, GrapnelSkills.LURE, 1)));
                        chargeAndRelease(player, trident, hand, 10); player.move(MoverType.SELF, new Vec3(0.6, 0, 0));
                        GrapnelSkills.tickServer(level.getServer());
                        require(player.getDeltaMovement().x >= nativeStrength - 0.01 && !marked(player),
                            "Ambiguous old sources retain the highest missing primary but cannot create a new mark");
                    }
                }
            }
            reset(player, start); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ItemStack creative = f.equip(Items.TRIDENT, pair); player.setGameMode(GameType.CREATIVE);
            chargeAndRelease(player, creative, InteractionHand.MAIN_HAND, 10); player.move(MoverType.SELF, new Vec3(0.5, 0, 0));
            GrapnelSkills.tickServer(level.getServer()); require(!marked(player), "Creative flight/use cannot earn a mark");
            player.setGameMode(GameType.SURVIVAL);
        } finally {
            GrapnelSkills.clearPlayer(player.getUUID());
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.stopUsingItem(); ticks.setInt(player, oldTicks); damage.setFloat(player, oldDamage); item.set(player, oldItem);
            flag().invoke(player, 4, oldSpin); refreshWater(player);
        }
    }
    private static void rememberAir(ServerLevel level, Map<BlockPos, BlockState> saved, BlockPos pos) {
        BlockState block = level.getBlockState(pos);
        require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && block.isAir(), "Riptide fixture needs disposable loaded air"); saved.put(pos, block);
    }
    private static void reset(ServerPlayer player, Vec3 pos) throws Exception {
        GrapnelSkills.clearPlayer(player.getUUID()); player.stopUsingItem(); clearSpin(player);
        player.setPos(pos); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(false); player.setYRot(-90); player.setXRot(0); refreshWater(player);
    }
    private static void chargeAndRelease(ServerPlayer player, ItemStack trident, InteractionHand hand, int charge) throws Exception {
        require(((TridentItem) trident.getItem()).use(player.level(), player, hand) == InteractionResult.CONSUME,
            "A valid wet trident must begin the normal use action");
        Field remaining = field("useItemRemaining"); remaining.setInt(player, trident.getUseDuration(player) - charge);
        player.releaseUsingItem();
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field f = GrapnelSkills.class.getDeclaredField("MARKS"); f.setAccessible(true);
        return ((Map<?, ?>) f.get(null)).containsKey(player.getUUID());
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
