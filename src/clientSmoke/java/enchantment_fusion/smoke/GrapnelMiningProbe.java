package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Three real break/pickup probes. Activation, waiting and inventory setup are simulated. */
final class GrapnelMiningProbe {
    private GrapnelMiningProbe() {}
    static void exercise(ServerPlayer player, BlockPos pos, String primary) throws Exception {
        ServerLevel level = player.level(); FusedPair pair = FusedPair.clean(primary, primary.equals(GrapnelSkills.SILK) ? 1 : 3, GrapnelSkills.LURE, 2);
        checkStation(level, pair); GrapnelSkills.clearPlayer(player.getUUID());
        int size = player.getInventory().getContainerSize(); ItemStack[] old = new ItemStack[size];
        for (int i = 0; i < size; i++) old[i] = player.getInventory().getItem(i);
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement(); GameType oldMode = player.gameMode.getGameModeForPlayer();
        var oldBlock = level.getBlockState(pos); boolean[] deny = {false};
        PlayerBlockBreakEvents.BEFORE.register((world, actor, where, state, blockEntity) -> actor != player || !where.equals(pos) || !deny[0]);
        List<ItemEntity> oldItems = nearby(level, pos); List<ExperienceOrb> oldOrbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(4));
        ItemEntity unrelated = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 1.0D, new ItemStack(Items.DIAMOND));
        try {
            require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && oldBlock.isAir(), "Fixture needs loaded disposable air");
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() - 2.5D, pos.getY(), pos.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); FusionStationOperations.applyFusedPairToGear(pick, pair, level.registryAccess());
            if (!primary.equals(GrapnelSkills.EFFICIENCY)) {
                pick.remove(DataComponents.ENCHANTMENTS); // exercise non-native loot projection
                var nativePrimary = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(primary)));
                require(GrapnelSkills.lootTool(level, pick).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(nativePrimary) == pair.primaryLevel(),
                    "Missing loot primary must be projected onto a temporary copy");
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, pick); require(level.addFreshEntity(unrelated), "Unrelated item must enter the world");
            require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()), "Ore placement failed");
            deny[0] = true; require(!player.gameMode.destroyBlock(pos) && markedDrop(player) == null, "Cancelled break cannot mark drops"); deny[0] = false;
            require(player.gameMode.destroyBlock(pos), "Real break must succeed");
            ItemEntity drop = markedDrop(player); Item expected = primary.equals(GrapnelSkills.SILK) ? Items.DIAMOND_ORE : Items.DIAMOND;
            require(drop != null && drop.getItem().is(expected) && drop != unrelated, "Only the real loot entity may be marked");
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "Pickup delay must not be bypassed");
            drop.setNoPickUpDelay(); Vec3 near = player.position(); player.setPos(near.x - 20, near.y, near.z);
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "Out-of-range pickup must fail"); player.setPos(near);
            int count = drop.getItem().getCount(); require(GrapnelSkills.activate(player) && !drop.isAlive(), "Normal pickup must collect this actual drop once");
            int inInventory = 0;
            for (int i = 0; i < size; i++) if (player.getInventory().getItem(i).is(expected)) inInventory += player.getInventory().getItem(i).getCount();
            require(inInventory == count && unrelated.isAlive() && unrelated.getItem().is(Items.DIAMOND), "Unrelated nearby items cannot be collected or copied");
            require(!GrapnelSkills.activate(player), "The mark cannot be spent twice");

            drop = mine(player, level, pos); drop.setNoPickUpDelay(); drop.setTarget(UUID.randomUUID());
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "Another pickup owner must be respected");
            drop.setTarget(null); drop.setItem(drop.getItem().copy());
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "An identity-replaced or merged stack cannot be claimed");

            drop = mine(player, level, pos); drop.setNoPickUpDelay();
            for (int i = 0; i < size; i++) if (player.getInventory().getItem(i) != pick) player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            int before = drop.getItem().getCount();
            require(!GrapnelSkills.activate(player) && drop.isAlive() && drop.getItem().getCount() == before,
                "A full inventory must leave the actual item in the world");
        } finally {
            deny[0] = false; GrapnelSkills.clearPlayer(player.getUUID()); unrelated.discard();
            for (ItemEntity entity : nearby(level, pos)) if (!oldItems.contains(entity)) entity.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(4))) if (!oldOrbs.contains(orb)) orb.discard();
            level.setBlockAndUpdate(pos, oldBlock); for (int i = 0; i < size; i++) player.getInventory().setItem(i, old[i]);
            player.setGameMode(oldMode); player.setPos(oldPos); player.setDeltaMovement(oldVelocity);
        }
    }
    private static ItemEntity mine(ServerPlayer player, ServerLevel level, BlockPos pos) throws Exception {
        require(level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()) && player.gameMode.destroyBlock(pos), "Repeated break failed");
        ItemEntity result = markedDrop(player); require(result != null, "Real drop must be marked"); return result;
    }
    private static ItemEntity markedDrop(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((java.util.Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method drops = mark.getClass().getDeclaredMethod("drops"); drops.setAccessible(true); Object raw = drops.invoke(mark);
        if (!(raw instanceof List<?> list) || list.isEmpty()) return null;
        Method entity = list.getFirst().getClass().getDeclaredMethod("entity"); entity.setAccessible(true); return (ItemEntity) entity.invoke(list.getFirst());
    }
    private static List<ItemEntity> nearby(ServerLevel level, BlockPos pos) { return new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))); }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_SWORD, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item != Items.DIAMOND_SWORD && item != Items.BOW;
                require(supported ? preview.executable() : preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must use the actual mining carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
