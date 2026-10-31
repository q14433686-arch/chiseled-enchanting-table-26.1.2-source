package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Prepared server-side fixture; not a client input or third-party permission test. */
final class PrecisionConstructionProbe {
    private static final FusedPair SLICE = FusedPair.clean("minecraft:efficiency", 2, "minecraft:silk_touch", 1);
    private static final FusedPair LINE = SLICE.flipped();
    private PrecisionConstructionProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.level();
        exerciseCarriers(level);
        Vec3 oldPos = player.position();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        Set<BlockPos> denied = new HashSet<>();
        AABB area = new AABB(origin).inflate(16);
        Set<java.util.UUID> existingItems = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) existingItems.add(item.getUUID());
        Set<java.util.UUID> existingOrbs = new HashSet<>();
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) existingOrbs.add(orb.getUUID());
        PlayerBlockBreakEvents.BEFORE.register((world, breaker, pos, state, blockEntity) ->
            breaker != player || world != level || !denied.contains(pos));
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(origin.getX() + 0.5D, origin.getY() + 2.0D, origin.getZ() + 0.5D);
            for (int x = -9; x <= 5; x++) for (int y = -1; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                BlockPos pos = origin.offset(x, y, z);
                BlockState previous = level.getBlockState(pos);
                require(previous.isAir(), "Fixture volume must be empty; do not overwrite existing world data");
                saved.put(pos, previous);
            }
            ItemStack tool = fused(level, SLICE);
            player.setItemSlot(EquipmentSlot.MAINHAND, tool);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            BlockPos center = origin.offset(3, 0, 0);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
            }
            BlockPos chest = center.east(), water = center.offset(1, 0, -1), granite = center.offset(1, 0, 1);
            BlockPos protectedSide = center.west(), above = center.above();
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(granite, Blocks.GRANITE.defaultBlockState());
            level.setBlockAndUpdate(above, Blocks.STONE.defaultBlockState());
            PrecisionConstruction.cycleSlice(player); // horizontal
            denied.add(center);
            require(!player.gameMode.destroyBlock(center) && level.getBlockState(center).is(Blocks.STONE),
                "A cancelled center break cannot start a slice");
            denied.clear(); denied.add(protectedSide);
            require(player.gameMode.destroyBlock(center), "Center break should succeed");
            require(level.getBlockState(protectedSide).is(Blocks.STONE) && level.getBlockState(chest).is(Blocks.CHEST)
                && level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(granite).is(Blocks.GRANITE)
                && level.getBlockState(above).is(Blocks.STONE),
                "Slice must respect event cancellation, block entities, fluids, other state, and the selected plane");
            require(level.getBlockState(center.north()).isAir() && level.getBlockState(center.south()).isAir()
                && level.getBlockState(center.offset(-1, 0, -1)).isAir() && level.getBlockState(center.offset(-1, 0, 1)).isAir(),
                "Connected identical cubes in the selected plane should use real extra breaks");
            require(tool.getDamageValue() == 5, "Center and four successful additional breaks must each damage the real tool");
            denied.clear();

            ItemStack lineTool = fused(level, LINE);
            player.setItemSlot(EquipmentSlot.MAINHAND, lineTool);
            ItemStack material = new ItemStack(Items.STONE, 5);
            player.setItemSlot(EquipmentSlot.OFFHAND, material);
            BlockPos anchor = origin.offset(-2, 0, 0);
            level.setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
            require(PrecisionConstruction.sampleAt(player, anchor), "A plain reachable cube may be sampled");
            int count = PrecisionConstruction.placeLine(player, hit(anchor, Direction.WEST));
            require(count == 5 && material.isEmpty(), "Every placed block must consume one real offhand material");
            for (int i = 1; i <= 5; i++) require(level.getBlockState(anchor.west(i)).is(Blocks.STONE), "Missing placed cube " + i);
            require(level.getBlockState(anchor.west(6)).isAir(), "No block may be generated after materials run out");

            BlockPos log = origin.offset(-2, 0, 1);
            level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.OAK_LOG, 3));
            require(PrecisionConstruction.sampleAt(player, log), "A full cube with a state can be sampled");
            require(PrecisionConstruction.placeLine(player, hit(log, Direction.WEST)) == 0
                && player.getOffhandItem().getCount() == 3 && level.getBlockState(log.west()).isAir(),
                "A mismatched placement axis must not consume material or change the world");
            require(PrecisionConstruction.sampleAt(player, anchor), "Stone may be resampled");
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE, 2));
            level.setBlockAndUpdate(anchor.west(), Blocks.WATER.defaultBlockState());
            require(PrecisionConstruction.placeLine(player, hit(anchor, Direction.WEST)) == 0
                && level.getBlockState(anchor.west()).is(Blocks.WATER) && player.getOffhandItem().getCount() == 2,
                "Line construction must not replace original water");
            player.setGameMode(GameType.CREATIVE);
            require(PrecisionConstruction.placeLine(player, hit(anchor, Direction.WEST)) == 0,
                "Infinite-material mode may not bypass the per-block cost");
        } finally {
            denied.clear();
            PrecisionConstruction.onDisconnect(player.getUUID());
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!existingItems.contains(item.getUUID())) item.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!existingOrbs.contains(orb.getUUID())) orb.discard();
            for (Map.Entry<BlockPos, BlockState> entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain);
            player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setGameMode(oldMode);
            player.teleportTo(oldPos.x, oldPos.y, oldPos.z);
        }
    }

    private static ItemStack fused(ServerLevel level, FusedPair pair) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
    }
    private static BlockHitResult hit(BlockPos pos, Direction direction) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5D,
            direction.getStepY() * 0.5D, direction.getStepZ() * 0.5D), direction, pos, false);
    }
    private static void exerciseCarriers(ServerLevel level) {
        var registries = level.registryAccess();
        for (FusedPair pair : new FusedPair[]{SLICE, LINE}) {
            for (ItemStack tool : new ItemStack[]{new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE),
                new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE), new ItemStack(Items.SHEARS)}) {
                require(DirectionalHostRules.validateNewOrChanged(tool, pair) == RejectReason.NONE, "Suitable tool rejected");
            }
            ItemStack shield = new ItemStack(Items.SHIELD);
            require(DirectionalHostRules.validateNewOrChanged(shield, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "A shield cannot perform these actions");
            SimpleContainer socket = new SimpleContainer(4);
            socket.setItem(FusionStationOperations.SLOT_TARGET, shield);
            socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            require(FusionStationOperations.inspect(socket, registries, FusionStationOperations.TAB_SOCKET, -1, -1)
                .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Book socketing must reject unsupported gear");
            socket.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.IRON_PICKAXE));
            require(FusionStationOperations.inspect(socket, registries, FusionStationOperations.TAB_SOCKET, -1, -1).executable(),
                "Book socketing must allow a usable tool");
            var primary = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
            var secondary = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
            SimpleContainer direct = new SimpleContainer(4);
            direct.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.SHIELD));
            direct.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
            direct.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            require(FusionStationOperations.inspect(direct, registries, FusionStationOperations.TAB_SOCKET, 0, 1).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Direct fusion must also reject unsupported gear");
            direct.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.IRON_PICKAXE));
            require(FusionStationOperations.inspect(direct, registries, FusionStationOperations.TAB_SOCKET, 0, 1).executable(),
                "Direct fusion must allow the supported tool");
            shield.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(pair));
            require(!PrecisionConstruction.applies(pair, shield, PrecisionConstruction.isSlice(pair) ? PrecisionConstruction.SLICE : PrecisionConstruction.LINE)
                && shield.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair),
                "Existing unsupported gear must keep its data and legacy path");
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
