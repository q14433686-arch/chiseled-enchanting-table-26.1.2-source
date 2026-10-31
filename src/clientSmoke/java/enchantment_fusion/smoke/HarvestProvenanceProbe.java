package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.transmute.MiningFishingTransmutes;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Prepared real game-mode break fixture; client input and a second player are still untested. */
final class HarvestProvenanceProbe {
    private HarvestProvenanceProbe() {}

    static void exercise(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState saved = level.getBlockState(pos);
        ItemStack savedHand = player.getMainHandItem();
        GameType savedMode = player.gameMode.getGameModeForPlayer();
        boolean sneaking = player.isShiftKeyDown();
        List<ItemEntity> before = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4), e -> e.isAlive()));
        List<ExperienceOrb> beforeXp = new ArrayList<>(level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(4), e -> e.isAlive()));
        ItemEntity foreign = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
            new ItemStack(Items.RAW_IRON));
        boolean[] deny = {false};
        PlayerBlockBreakEvents.BEFORE.register((world, actor, block, state, be) -> actor != player || !block.equals(pos) || !deny[0]);
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setShiftKeyDown(false);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            pick.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:fire_aspect", 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            level.addFreshEntity(foreign);
            level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
            require(player.gameMode.destroyBlock(pos), "An ordinary permitted ore break must succeed");
            require(foreign.isAlive() && foreign.getItem().is(Items.RAW_IRON),
                "Real mining must leave a pre-existing nearby raw-iron entity unchanged");
            List<ItemEntity> after = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4), e -> e.isAlive());
            require(after.stream().anyMatch(e -> e != foreign && !before.contains(e) && e.getItem().is(Items.IRON_INGOT)),
                "The real ore drop must be captured after playerDestroy and smelted");
            level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
            deny[0] = true;
            require(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos).is(Blocks.IRON_ORE),
                "A cancelled break must not produce or transform loot");
            require(foreign.isAlive() && foreign.getItem().is(Items.RAW_IRON),
                "A cancelled break must leave nearby drops untouched");
            deny[0] = false;
            exerciseMagnet(level, player, pos);
            exerciseScopesAndLimit(level, player, pos);
        } finally {
            deny[0] = false;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4), e -> e.isAlive())) {
                if (!before.contains(item)) item.discard();
            }
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(4), e -> e.isAlive())) {
                if (!beforeXp.contains(orb)) orb.discard();
            }
            level.setBlockAndUpdate(pos, saved);
            player.setItemInHand(InteractionHand.MAIN_HAND, savedHand);
            player.setShiftKeyDown(sneaking);
            player.setGameMode(savedMode);
        }
    }

    private static void exerciseMagnet(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ItemStack magnet = new ItemStack(Items.DIAMOND_PICKAXE);
        magnet.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:loyalty", 1));
        ItemEntity oldItem = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
        ExperienceOrb oldOrb = new ExperienceOrb(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3);
        ItemEntity ownItem = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
        ExperienceOrb ownOrb = new ExperienceOrb(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3);
        try {
            level.addFreshEntity(oldItem); level.addFreshEntity(oldOrb);
            try (var scope = HarvestDropCapture.begin(level, player, pos)) {
                HarvestDropCapture.enterPlayerDestroy(level, player, pos);
                try { level.addFreshEntity(ownItem); level.addFreshEntity(ownOrb); }
                finally { HarvestDropCapture.leavePlayerDestroy(level, player, pos); }
                MiningFishingTransmutes.onPostDestroyBlockDrops(level, player, pos, Blocks.STONE.defaultBlockState(), magnet);
                require(ownItem.distanceToSqr(player) < 2.0D && ownOrb.distanceToSqr(player) < 2.0D,
                    "A captured item and XP orb must be pulled to the owner");
                require(oldItem.distanceToSqr(player) > 4.0D && oldOrb.distanceToSqr(player) > 4.0D,
                    "Pre-existing nearby item and XP must not be pulled");
                ItemStack smelter = new ItemStack(Items.DIAMOND_PICKAXE);
                smelter.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:fire_aspect", 2));
                MiningFishingTransmutes.onPostDestroyBlockDrops(level, player, pos, Blocks.IRON_ORE.defaultBlockState(), smelter);
                require(ownItem.getItem().is(Items.IRON_INGOT) && oldItem.getItem().is(Items.RAW_IRON),
                    "A captured drop may be smelted once even after it was pulled; an old drop stays untouched");
            }
        } finally {
            oldItem.discard(); oldOrb.discard(); ownItem.discard(); ownOrb.discard();
        }
    }

    private static void exerciseScopesAndLimit(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockPos innerPos = pos.offset(8, 0, 0);
        AABB outerBox = new AABB(pos).inflate(2);
        ItemEntity outerFirst = fixture(level, pos);
        ItemEntity outerLast = fixture(level, pos);
        ItemEntity inner = fixture(level, innerPos);
        try (var outerScope = HarvestDropCapture.begin(level, player, pos)) {
            HarvestDropCapture.enterPlayerDestroy(level, player, pos);
            try {
                HarvestDropCapture.record(level, outerFirst);
                try (var innerScope = HarvestDropCapture.begin(level, player, innerPos)) {
                    HarvestDropCapture.enterPlayerDestroy(level, player, innerPos);
                    try {
                        HarvestDropCapture.record(level, inner);
                        require(HarvestDropCapture.items(level, innerPos, new AABB(innerPos).inflate(2)).contains(inner),
                            "A nested break must capture its own drop");
                        require(HarvestDropCapture.items(level, pos, outerBox).isEmpty(),
                            "A nested frame must not expose its parent's drops");
                        throw new IllegalStateException("deliberate nested probe exception");
                    } finally { HarvestDropCapture.leavePlayerDestroy(level, player, innerPos); }
                } catch (IllegalStateException expected) {
                    require("deliberate nested probe exception".equals(expected.getMessage()), "Unexpected capture-scope exception");
                }
                HarvestDropCapture.record(level, outerLast);
                List<ItemEntity> outerDrops = HarvestDropCapture.items(level, pos, outerBox);
                require(outerDrops.contains(outerFirst) && outerDrops.contains(outerLast) && !outerDrops.contains(inner),
                    "After an exceptional nested scope, the parent must resume without adopting inner drops");
            } finally { HarvestDropCapture.leavePlayerDestroy(level, player, pos); }
        }
        require(HarvestDropCapture.items(level, pos, outerBox).isEmpty(), "Closed capture scopes must not leak");

        // Direct record calls test the limit without adding hundreds of real entities to the world.
        List<ItemEntity> generated = new ArrayList<>();
        try (var scope = HarvestDropCapture.begin(level, player, pos)) {
            HarvestDropCapture.enterPlayerDestroy(level, player, pos);
            try {
                for (int i = 0; i < 257; i++) {
                    ItemEntity item = fixture(level, pos);
                    generated.add(item);
                    HarvestDropCapture.record(level, item);
                }
            } finally { HarvestDropCapture.leavePlayerDestroy(level, player, pos); }
            require(HarvestDropCapture.items(level, pos, outerBox).size() == 256,
                "Capture must stop at the bounded entity count");
            ItemEntity excess = generated.get(256);
            require(excess.isAlive() && excess.getItem().is(Items.RAW_IRON)
                && !HarvestDropCapture.claimTransform(level, pos, excess), "Excess drops must remain unchanged");
        } finally {
            outerFirst.discard(); outerLast.discard(); inner.discard();
            generated.forEach(ItemEntity::discard);
        }
    }

    private static ItemEntity fixture(ServerLevel level, BlockPos pos) {
        return new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
