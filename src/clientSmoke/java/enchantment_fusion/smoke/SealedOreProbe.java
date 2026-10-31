package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.SealedOreSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real game-mode mining and one-slot exchange; activation is called directly, not via client key input. */
final class SealedOreProbe {
    private static final FusedPair PAIR = FusedPair.clean("minecraft:silk_touch", 1, "minecraft:fortune", 3);
    private SealedOreProbe() {}
    static void exercise(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level(); checkStation(level);
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); GameType mode = player.gameMode.getGameModeForPlayer();
        var oldBlock = level.getBlockState(pos); Set<BlockPos> denied = new HashSet<>();
        PlayerBlockBreakEvents.BEFORE.register((world, who, where, state, blockEntity) -> !denied.contains(where));
        ItemEntity unrelated = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 1.2D, new ItemStack(Items.DIAMOND_ORE));
        List<ItemEntity> created = List.of();
        try {
            require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && oldBlock.isAir(), "Fixture needs loaded disposable air");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 3D); player.setDeltaMovement(Vec3.ZERO);
            ItemStack pick = fused(level, Items.DIAMOND_PICKAXE); player.setItemSlot(EquipmentSlot.MAINHAND, pick);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            require(level.addFreshEntity(unrelated), "Unrelated ore must enter world");
            Set<UUID> previous = nearby(level, pos).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()), "Ore block must be placed");
            denied.add(pos); require(!player.gameMode.destroyBlock(pos), "Cancelled mining must fail"); denied.clear();
            require(level.getBlockState(pos).is(Blocks.DIAMOND_ORE), "Cancelled mining must leave ore intact");
            require(player.gameMode.destroyBlock(pos), "Real mining must succeed");
            created = nearby(level, pos).stream().filter(e -> !previous.contains(e.getUUID())).toList();
            List<ItemEntity> samples = created.stream().filter(e -> e.getItem().is(Items.DIAMOND_ORE)
                && e.getItem().getOrDefault(FusionDataComponents.SEALED_ORE_FORTUNE, 0) == 3).toList();
            require(samples.size() == 1 && created.stream().noneMatch(e -> e.getItem().is(Items.DIAMOND)),
                "One real Silk drop becomes a sample without also producing Fortune loot");
            require(!unrelated.getItem().has(FusionDataComponents.SEALED_ORE_FORTUNE), "An existing nearby item must not be changed");
            ItemStack sample = samples.getFirst().getItem().copy(); samples.getFirst().discard();
            player.setItemSlot(EquipmentSlot.OFFHAND, sample.copyWithCount(2));
            SealedOreSkills.openSample(player);
            require(player.getOffhandItem().getCount() == 2 && player.getOffhandItem().is(Items.DIAMOND_ORE),
                "A stack of samples cannot be exchanged all at once");
            player.setItemSlot(EquipmentSlot.OFFHAND, sample);
            require(SealedOreSkills.hasOpenableSample(player), "Matching tool and single sample must be eligible");
            SealedOreSkills.openSample(player);
            require(player.getOffhandItem().is(Items.DIAMOND) && !player.getOffhandItem().has(FusionDataComponents.SEALED_ORE_FORTUNE)
                && player.getOffhandItem().getCount() >= 1, "The ore is consumed and replaced by one Fortune loot result");
        } finally {
            denied.clear(); unrelated.discard(); for (ItemEntity item : created) if (item.isAlive()) item.discard();
            level.setBlockAndUpdate(pos, oldBlock); player.setItemSlot(EquipmentSlot.MAINHAND, oldMain);
            player.setItemSlot(EquipmentSlot.OFFHAND, oldOff); player.setGameMode(mode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity);
        }
    }
    private static List<ItemEntity> nearby(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    }
    private static ItemStack fused(ServerLevel level, Item item) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, PAIR, level.registryAccess()); return stack;
    }
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
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            for (Item item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b);
                require(item == Items.DIAMOND_PICKAXE ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must require a tool capable of the supported ore action");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
