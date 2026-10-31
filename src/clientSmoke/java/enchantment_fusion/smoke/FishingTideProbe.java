package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real casts and catches with manually advanced bite counters in a disposable world. */
final class FishingTideProbe {
    private static final FusedPair CHOICE = FusedPair.clean("minecraft:luck_of_the_sea", 3, "minecraft:lure", 2);
    private static final FusedPair SCHOOL = CHOICE.flipped();
    private FishingTideProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? SCHOOL : CHOICE;
        checkStation(level, pair);
        require(player.fishing == null, "The fixture cannot replace an existing fishing hook");
        Vec3 oldPos = player.position(), oldVelocity = player.getDeltaMovement();
        ItemStack oldMain = player.getMainHandItem(), oldOff = player.getOffhandItem();
        Map<BlockPos, BlockState> prior = new LinkedHashMap<>();
        AABB area = new AABB(origin).inflate(12.0D);
        Set<UUID> itemIds = new HashSet<>(), orbIds = new HashSet<>(), hookIds = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) itemIds.add(item.getUUID());
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) orbIds.add(orb.getUUID());
        for (FishingHook hook : level.getEntitiesOfClass(FishingHook.class, area)) hookIds.add(hook.getUUID());
        try {
            for (int x = -4; x <= 4; x++) for (int y = -1; y <= 2; y++) for (int z = -4; z <= 4; z++) {
                BlockPos pos = origin.offset(x, y, z);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && level.getBlockState(pos).isAir(),
                    "Fishing fixture needs a loaded, empty disposable volume");
                prior.put(pos, level.getBlockState(pos));
            }
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.WATER.defaultBlockState());
            }
            player.setPos(origin.getX() - 3.0D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO);
            ItemStack rod = new ItemStack(Items.FISHING_ROD);
            FusionStationOperations.applyFusedPairToGear(rod, pair, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, rod);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            FishingRodItem fishing = (FishingRodItem) rod.getItem();
            fishing.use(level, player, InteractionHand.MAIN_HAND);
            FishingHook hook = player.fishing;
            require(hook != null && hook.getOwner() == player, "Real rod use must create the owner's hook");
            hook.setPos(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
            hook.setDeltaMovement(Vec3.ZERO);
            Field nibble = field("nibble"), hooked = field("timeUntilHooked"), waiting = field("timeUntilLured");
            Method fish = FishingHook.class.getDeclaredMethod("catchingFish", BlockPos.class);
            fish.setAccessible(true);
            hooked.setInt(hook, 1);
            fish.invoke(hook, origin);
            require(nibble.getInt(hook) > 0, "The native fishing state must enter a real bite");
            if (!reverse) {
                require(FishingTideSkills.lootLuck(hook, rod, 3.0F, nibble.getInt(hook)) == 3.0F,
                    "Immediate reeling must retain original loot luck");
                for (int i = 0; i < 25; i++) fish.invoke(hook, origin);
                require(nibble.getInt(hook) > 0 && FishingTideSkills.lootLuck(hook, rod, 3.0F, nibble.getInt(hook)) > 3.0F,
                    "Waiting through a real bite must increase this catch's loot luck without losing the bite");
            }
            fishing.use(level, player, InteractionHand.MAIN_HAND);
            require(player.fishing == null, "Reeling must retire the hook normally");
            long freshItems = level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(item -> !itemIds.contains(item.getUUID())).count();
            require(freshItems == 1, "The default fishing loot table must produce one real item, not a copied bonus");
            if (reverse) {
                fishing.use(level, player, InteractionHand.MAIN_HAND);
                FishingHook next = player.fishing;
                require(next != null && next != hook, "A second real cast must create a fresh hook");
                next.setPos(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
                next.setDeltaMovement(Vec3.ZERO);
                waiting.setInt(next, 100); hooked.setInt(next, 0); nibble.setInt(next, 0);
                fish.invoke(next, origin);
                require(waiting.getInt(next) < 90, "Following the actual catch's school must shorten the next real wait");
                waiting.setInt(next, 100);
                fish.invoke(next, origin);
                require(waiting.getInt(next) >= 98, "A school can shorten at most one wait");
            }
        } finally {
            if (player.fishing != null && !hookIds.contains(player.fishing.getUUID())) player.fishing.discard();
            FishingTideSkills.onDisconnect(player.getUUID());
            for (FishingHook hook : level.getEntitiesOfClass(FishingHook.class, area))
                if (!hookIds.contains(hook.getUUID()) && hook.getOwner() == player) hook.discard();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!itemIds.contains(item.getUUID())) item.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!orbIds.contains(orb.getUUID())) orb.discard();
            for (var entry : prior.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setItemSlot(EquipmentSlot.MAINHAND, oldMain); player.setItemSlot(EquipmentSlot.OFFHAND, oldOff);
            player.setPos(oldPos); player.setDeltaMovement(oldVelocity);
        }
    }
    private static Field field(String name) throws Exception {
        Field field = FishingHook.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var a = reg.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var b = reg.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(a, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(b, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int ai = direct ? 0 : -1, bi = direct ? 1 : -1;
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.FISHING_ROD));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, ai, bi).executable(),
                "Both station routes must accept the actual fishing rod");
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.IRON_SWORD));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, ai, bi).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both station routes must reject a sword for fishing");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
