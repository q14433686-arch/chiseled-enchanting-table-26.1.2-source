package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelDurability;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual orb repair or durability requests, with a bounded random wait and simulated aim. */
final class GrapnelDurabilityProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private GrapnelDurabilityProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String primary) throws Exception {
        ServerLevel level = player.level(); boolean mending = GrapnelDurability.MENDING.equals(primary);
        FusedPair pair = FusedPair.clean(primary, mending ? 1 : 3, GrapnelSkills.LURE, 2); checkStation(level, pair);
        GrapnelSkills.clearPlayer(player.getUUID()); ItemStack[] previous = new ItemStack[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) previous[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 position = player.position(), velocity = player.getDeltaMovement(); float yaw = player.getYRot(), pitch = player.getXRot();
        boolean ground = player.onGround(); double fall = player.fallDistance; GameType mode = player.gameMode.getGameModeForPlayer();
        int xpLevel = player.experienceLevel, xpTotal = player.totalExperience, xpDelay = player.takeXpDelay; float xpProgress = player.experienceProgress;
        Zombie enemy = new Zombie(EntityType.ZOMBIE, level); Villager villager = new Villager(EntityType.VILLAGER, level);
        BlockPos wall = origin.east(2).above(); boolean changedWall = false;
        try {
            require(level.hasChunkAt(origin) && level.isInWorldBounds(wall) && level.getBlockState(wall).isAir(), "Fixture needs loaded disposable air");
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(0); player.setOnGround(false); player.fallDistance = 0;
            enemy.setNoAi(true); villager.setNoAi(true); enemy.setPos(player.getX() + 4, player.getY(), player.getZ());
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            require(level.addFreshEntity(enemy) && level.addFreshEntity(villager), "Fixture entities must enter the world");
            ItemStack tool = fused(level, Items.DIAMOND_PICKAXE, pair); tool.remove(DataComponents.ENCHANTMENTS);
            tool.setDamageValue(40); player.setItemSlot(EquipmentSlot.OFFHAND, tool);
            require(GrapnelSkills.hasEquipped(player) && !GrapnelSkills.activate(player), "Held non-main-hand gear exposes activation without inventing a mark");
            tool.setDamageValue(39); require(!marked(player), "Direct durability edits alone cannot mark");
            trigger(player, tool, mending); require(marked(player), "The real resource event must mark this location");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ()); enemy.setDeltaMovement(Vec3.ZERO);
            require(!GrapnelSkills.activate(player) && marked(player) && enemy.getDeltaMovement().lengthSqr() == 0,
                "A protected body cannot be pulled or bypassed by the aim ray");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            require(level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3), "Fixture wall must be placed"); changedWall = true;
            require(!GrapnelSkills.activate(player) && marked(player), "Blocked sight cannot spend a mark");
            level.removeBlock(wall, false); changedWall = false;
            player.setItemSlot(EquipmentSlot.OFFHAND, tool.copy());
            require(!GrapnelSkills.activate(player), "A copied item cannot spend the original position mark");
            player.setItemSlot(EquipmentSlot.OFFHAND, tool); trigger(player, tool, mending);
            require(marked(player), "The original item can establish a new mark"); enemy.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && enemy.getDeltaMovement().x < 0 && !marked(player),
                "Active recall must pull the aimed eligible enemy once");
            require(!GrapnelSkills.activate(player), "The spent mark cannot pull again");

            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); GrapnelSkills.clearPlayer(player.getUUID());
            if (mending) { tool.setDamageValue(40); touchOrb(player, 4); }
            else tool.hurtAndBreak(1, level, player, ignored -> {});
            require(!marked(player), "A resource event on unequipped gear cannot mark");

            ItemStack nativeTool = fused(level, Items.DIAMOND_PICKAXE, pair);
            var nativeEnchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(primary)));
            nativeTool.enchant(nativeEnchantment, pair.primaryLevel()); nativeTool.setDamageValue(40);
            player.setItemSlot(EquipmentSlot.OFFHAND, nativeTool); GrapnelSkills.clearPlayer(player.getUUID());
            trigger(player, nativeTool, mending); require(marked(player), "The native primary's real event must also mark");
            if (!mending) {
                GrapnelSkills.clearPlayer(player.getUUID()); nativeTool.setDamageValue(nativeTool.getMaxDamage() - 1);
                for (int i = 0; i < 64; i++) nativeTool.hurtWithoutBreaking(1, player);
                require(!marked(player), "The non-breaking damage cap cannot be mistaken for Unbreaking savings");
            }
            if (mending) {
                GrapnelSkills.clearPlayer(player.getUUID()); tool.setDamageValue(50); player.setItemSlot(EquipmentSlot.OFFHAND, tool);
                ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(nativeEnchantment, 1); helmet.setDamageValue(1);
                player.setItemSlot(EquipmentSlot.HEAD, helmet); int beforeXp = player.totalExperience; touchOrb(player, 2);
                require(helmet.getDamageValue() == 0 && tool.getDamageValue() == 46 && player.totalExperience == beforeXp && marked(player),
                    "Native recursive repair and the non-native pass must preserve the same orb XP");
            }
        } finally {
            if (changedWall) level.removeBlock(wall, false);
            GrapnelSkills.clearPlayer(player.getUUID()); enemy.discard(); villager.discard();
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], previous[i]);
            player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
            player.setOnGround(ground); player.fallDistance = fall;
            player.experienceLevel = xpLevel; player.totalExperience = xpTotal; player.experienceProgress = xpProgress; player.takeXpDelay = xpDelay;
        }
    }
    private static void trigger(ServerPlayer player, ItemStack stack, boolean mending) throws Exception {
        if (mending) {
            stack.setDamageValue(40); int oldXp = player.totalExperience; touchOrb(player, 4);
            require(stack.getDamageValue() < 40 && player.totalExperience == oldXp,
                "A real orb must spend its XP on actual repair without duplicating it");
        } else {
            boolean saved = false;
            for (int i = 0; i < 64 && !marked(player); i++) {
                stack.setDamageValue(0); stack.hurtAndBreak(1, player.level(), player, ignored -> {});
                if (marked(player)) saved = stack.getDamageValue() == 0;
            }
            require(saved, "A real Unbreaking roll must completely prevent this one-point wear request");
        }
    }
    private static void touchOrb(ServerPlayer player, int amount) {
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), amount);
        try { require(player.level().addFreshEntity(orb), "XP orb must enter the world"); player.takeXpDelay = 0; orb.playerTouch(player); }
        finally { orb.discard(); }
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.DIAMOND_HELMET, Items.SHIELD, Items.ELYTRA, Items.STONE, Items.APPLE)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = item != Items.STONE && item != Items.APPLE;
                require(supported ? result.executable() : !result.executable(),
                    "Both station paths must require actual durable gear for this direction");
            }
            ItemStack noDurability = new ItemStack(Items.DIAMOND_SWORD); noDurability.remove(DataComponents.MAX_DAMAGE);
            require(DirectionalHostRules.validateNewOrChanged(noDurability, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "A non-durable former equipment host must receive the directional rejection");
            inv.setItem(FusionStationOperations.SLOT_TARGET, noDurability);
            var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
            require(result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Both station paths must surface the non-durable equipment rejection");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
