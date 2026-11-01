package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeFrostSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Six scoped direction probes share real damage, station, entity and temporary-ice setup. */
final class MeleeFrostProbe {
    private MeleeFrostProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean reverse) {
        FusedPair pair = FusedPair.clean(attack, 4, MeleeFrostSkills.FROST, 2); if (reverse) pair = pair.flipped();
        if (reverse) SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_BOOTS),
            List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET));
        else SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE),
            List.of(Items.DIAMOND_BOOTS, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.BOW));
        MeleeFrostSkills.clearPlayer(player.getUUID()); Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            boolean bane = MeleeAttackThemes.BANE.equals(attack), sharp = MeleeAttackThemes.SHARP.equals(attack);
            LivingEntity target = f.spawn(bane, 2, 0), other = f.spawn(bane, 4, 0);
            LivingEntity wrong = f.spawn(!bane, 2, 3); Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, -3);
            if (reverse) reverse(f, pair, target, other, sharp ? villager : wrong, denied, saved, origin);
            else forward(f, pair, target, sharp ? villager : wrong, denied);
        } finally {
            denied.clear(); MeleeFrostSkills.clearPlayer(player.getUUID());
            for (var entry : saved.entrySet()) player.level().setBlockAndUpdate(entry.getKey(), entry.getValue());
        }
    }
    private static void forward(SharedMeleeFixture f, FusedPair pair, LivingEntity target, LivingEntity invalid, Set<UUID> denied) {
        f.equip(Items.DIAMOND_AXE, pair);
        target.setHealth(200); denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        f.hit(invalid, true); require(target.getHealth() == 200, "Cancelled and invalid hits cannot damage or mark the intended target");
        f.hit(target, true); float baseline = 200 - target.getHealth(); require(baseline > 0, "First matching hit creates a real mark");
        target.setHealth(200); denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        require(target.getHealth() == 200, "A cancelled second hit cannot spend the mark");
        f.hit(invalid, true); target.setHealth(200); f.hit(target, true);
        require(200 - target.getHealth() > baseline * 1.10F, "Second matching hit must apply real frost damage");
        target.setHealth(200); f.hit(target, true);
        require(Math.abs((200 - target.getHealth()) - baseline) < 0.01F, "Consumed mark cannot grant a second bonus");
    }
    private static void reverse(SharedMeleeFixture f, FusedPair pair, LivingEntity target, LivingEntity other, LivingEntity invalid,
                                Set<UUID> denied, Map<BlockPos, BlockState> saved, BlockPos origin) {
        ItemStack boots = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, pair);
        f.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        require(!MeleeFrostSkills.activate(f.player), "The frost blade cannot prepare while airborne or off ice");
        for (int x = 0; x <= 6; x++) put(f.level, saved, origin.offset(x, -1, 0), Blocks.ICE.defaultBlockState());
        f.player.setOnGround(true); require(MeleeFrostSkills.activate(f.player), "Worn boots on continuous ice can prepare the blade");
        denied.add(target.getUUID()); f.hit(target, true); denied.clear();
        require(!MeleeFrostSkills.activate(f.player), "Cancelled damage does not spend the armed blade");
        f.hit(invalid, true); require(!MeleeFrostSkills.activate(f.player), "Wrong-category or protected targets do not spend the blade");
        float baseline = other.getHealth(); f.hit(target, true);
        require(other.getHealth() < baseline, "A real matching hit releases the blade along continuous ice");
        require(MeleeFrostSkills.activate(f.player), "A spent blade may be prepared again");
        put(f.level, saved, origin.offset(3, -1, 0), Blocks.AIR.defaultBlockState());
        other.setHealth(200); other.invulnerableTime = 0; f.hit(target, true);
        require(other.getHealth() == 200, "A missing ice block stops the blade before the next target");
        BlockPos water = origin.offset(0, -1, -2); put(f.level, saved, water, Blocks.WATER.defaultBlockState());
        put(f.level, saved, water.above(), Blocks.AIR.defaultBlockState());
        f.player.setPos(water.getX() + 0.5D, water.getY() + 1.0D, water.getZ() + 0.5D); f.player.setOnGround(true);
        try {
            EnchantmentHelper.runLocationChangedEffects(f.level, boots, f.player, EquipmentSlot.FEET);
            require(f.level.getBlockState(water).is(Blocks.WATER), "Selected frost primary must not replace original water");
        } finally { EnchantmentHelper.stopLocationBasedEffects(boots, f.player, EquipmentSlot.FEET); }
    }
    private static void put(ServerLevel level, Map<BlockPos, BlockState> saved, BlockPos pos, BlockState replacement) {
        require(level.hasChunkAt(pos) && level.isInWorldBounds(pos), "Fixture block must be loaded and in bounds");
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, replacement);
    }
}
