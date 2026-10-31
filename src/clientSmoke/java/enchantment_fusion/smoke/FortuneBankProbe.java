package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FortuneBankSkills;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real game-mode mining with direct server activation; not a real keyboard or restart test. */
final class FortuneBankProbe {
    private static final FusedPair PAIR = FusedPair.clean("minecraft:fortune", 3, "minecraft:silk_touch", 1);
    private FortuneBankProbe() {}
    static void exercise(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level(); checkStation(level);
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean sneak = player.isShiftKeyDown();
        GameType mode = player.gameMode.getGameModeForPlayer(); var oldBlock = level.getBlockState(pos);
        Set<BlockPos> denied = new HashSet<>();
        PlayerBlockBreakEvents.BEFORE.register((world, who, where, state, blockEntity) -> !denied.contains(where));
        Set<UUID> oldItems = nearby(level, pos).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        Set<UUID> oldOrbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(3)).stream()
            .map(ExperienceOrb::getUUID).collect(java.util.stream.Collectors.toSet());
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        try {
            require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && oldBlock.isAir(), "Fixture needs loaded disposable air");
            require(FortuneBankSkills.storedAmount(player, diamond) == 0, "Fixture needs an empty diamond reserve");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 3D); player.setDeltaMovement(Vec3.ZERO);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            FusionStationOperations.applyFusedPairToGear(pick, PAIR, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, pick); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true); FortuneBankSkills.activate(player);
            require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()), "Ore must be placed");
            denied.add(pos); require(!player.gameMode.destroyBlock(pos), "Cancelled break must fail"); denied.clear();
            require(FortuneBankSkills.storedAmount(player, diamond) == 0 && level.getBlockState(pos).is(Blocks.DIAMOND_ORE),
                "Cancelled break cannot create a reserve");
            int saved = depositUntilPositive(player, level, pos, diamond);
            require(saved > 0, "A real positive Fortune bonus must be saved");
            player.setShiftKeyDown(false);
            require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()), "Target ore must be placed");
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos)); FortuneBankSkills.activate(player);
            Set<UUID> before = nearby(level, pos).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            require(player.gameMode.destroyBlock(pos), "Target mining must succeed");
            List<ItemEntity> output = nearby(level, pos).stream().filter(e -> !before.contains(e.getUUID()) && e.getItem().is(Items.DIAMOND)).toList();
            int remaining = FortuneBankSkills.storedAmount(player, diamond), spent = saved - remaining;
            require(output.size() == 1 && spent > 0 && spent <= 3, "Target must spend bounded stored units into one real drop");
            int baseYield = output.getFirst().getItem().getCount() - spent;
            require(baseYield >= 1 && baseYield <= 4, "Target payout must add stored units to a valid native Fortune yield");
            require(remaining == 0, "This small fixture balance should be fully spent by the target");
            player.setShiftKeyDown(true);
            int refund = depositUntilPositive(player, level, pos, diamond);
            require(refund > 0, "A second real bonus is needed for the fallback fixture");
            player.setShiftKeyDown(false); player.setYRot(0); player.setXRot(-90); player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            FortuneBankSkills.activate(player);
            require(player.getOffhandItem().is(Items.DIAMOND) && player.getOffhandItem().getCount() == refund
                && FortuneBankSkills.storedAmount(player, diamond) == 0, "Fallback withdrawal must consume the same stored units");
        } finally {
            denied.clear();
            for (ItemEntity item : nearby(level, pos)) if (!oldItems.contains(item.getUUID())) item.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(3)))
                if (!oldOrbs.contains(orb.getUUID())) orb.discard();
            level.setBlockAndUpdate(pos, oldBlock); player.setItemSlot(EquipmentSlot.MAINHAND, oldMain);
            player.setItemSlot(EquipmentSlot.OFFHAND, oldOff); player.setGameMode(mode); player.setPos(oldPos);
            player.setDeltaMovement(oldVelocity); player.setYRot(yaw); player.setXRot(pitch); player.setShiftKeyDown(sneak);
            FortuneBankSkills.onDisconnect(player.getUUID());
        }
    }
    private static int depositUntilPositive(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack diamond) {
        for (int tries = 0; tries < 64; tries++) {
            if (!level.getBlockState(pos).is(Blocks.DIAMOND_ORE))
                require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()), "Ore must be placed");
            Set<UUID> before = nearby(level, pos).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            int old = FortuneBankSkills.storedAmount(player, diamond);
            require(player.gameMode.destroyBlock(pos), "Actual ore mining must succeed");
            List<ItemEntity> output = nearby(level, pos).stream().filter(e -> !before.contains(e.getUUID()) && e.getItem().is(Items.DIAMOND)).toList();
            require(output.size() == 1, "One actual diamond drop expected");
            int added = FortuneBankSkills.storedAmount(player, diamond) - old;
            int originalYield = output.getFirst().getItem().getCount() + added;
            require(added >= 0 && originalYield >= 1 && originalYield <= 4 && added <= originalYield - 1,
                "Only real Fortune-increment units may be held back");
            if (added > 0) return added;
        }
        return 0; // Finite random test can very rarely see no bonus in all attempts.
    }
    private static List<ItemEntity> nearby(ServerLevel level, BlockPos pos) { return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)); }
    private static void checkStation(ServerLevel level) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(PAIR.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(PAIR.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, PAIR.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, PAIR.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(PAIR));
            for (Item item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                require(item == Items.DIAMOND_PICKAXE ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths require a real supported ore-harvesting carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
