package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FrostFireSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Prepared but not yet run; direct tick invocation is not a physical movement/PvP test. */
final class FrostFireProbe {
    private static final FusedPair BASALT = FusedPair.clean("minecraft:frost_walker", 2, "minecraft:fire_protection", 4);
    private static final FusedPair HEARTH = BASALT.flipped();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private FrostFireProbe() {}

    static void exercise(ServerPlayer player, BlockPos lavaPos) {
        ServerLevel level = player.level();
        exerciseCarriers(level);
        ItemStack[] savedSlots = new ItemStack[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) savedSlots[i] = player.getItemBySlot(SLOTS[i]);
        Vec3 oldPosition = player.position();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        boolean oldShift = player.isShiftKeyDown(), oldGround = player.onGround();
        int oldFrozen = player.getTicksFrozen();
        Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -1; y <= 1; y++) {
            BlockPos pos = lavaPos.offset(x, y, z);
            savedBlocks.put(pos, level.getBlockState(pos));
        }
        Cow cow = new Cow(EntityType.COW, level);
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        ItemStack selectedBoots = new ItemStack(Items.DIAMOND_BOOTS);
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setShiftKeyDown(false);
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack heldBoots = withPair(new ItemStack(Items.DIAMOND_BOOTS), BASALT);
            ItemStack oldShield = withPair(new ItemStack(Items.SHIELD), BASALT);
            player.setItemSlot(EquipmentSlot.MAINHAND, heldBoots);
            require(FrostFireSkills.select(player, true) == null, "Held selected boots must not activate");
            player.setItemSlot(EquipmentSlot.OFFHAND, oldShield);
            require(FrostFireSkills.select(player, true).stack() == oldShield, "Unsupported old carrier retains old selection");
            FusionStationOperations.applyFusedPairToGear(selectedBoots, BASALT, level.registryAccess());
            player.setItemSlot(EquipmentSlot.FEET, selectedBoots);
            var selected = FrostFireSkills.select(player, true);
            require(selected != null && selected.stack() == selectedBoots && selected.slot() == EquipmentSlot.FEET,
                "Worn boots take precedence over an unsupported old carrier");

            BlockPos water = lavaPos.east(2);
            level.setBlockAndUpdate(lavaPos, Blocks.LAVA.defaultBlockState());
            level.setBlockAndUpdate(lavaPos.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(water.above(), Blocks.AIR.defaultBlockState());
            player.teleportTo(lavaPos.getX() + 0.5D, lavaPos.getY() + 1.0D, lavaPos.getZ() + 0.5D);
            player.setShiftKeyDown(true);
            FrostFireSkills.tickBasalt(level, player, selected, true);
            require(level.getBlockState(lavaPos).is(Blocks.LAVA), "Sneaking must pause permanent basalt conversion");
            player.setShiftKeyDown(false);
            player.setGameMode(GameType.ADVENTURE);
            FrostFireSkills.tickBasalt(level, player, selected, true);
            require(level.getBlockState(lavaPos).is(Blocks.LAVA), "A player without build permission cannot convert lava");
            player.setGameMode(GameType.SURVIVAL);
            FrostFireSkills.tickBasalt(level, player, selected, true);
            require(level.getBlockState(lavaPos).is(Blocks.BASALT), "Source lava must permanently become basalt");
            require(level.getBlockState(water).is(Blocks.WATER), "Water must remain unchanged");
            require(level.getBlockState(water.above()).is(FusionBlocks.TEMPORARY_FROST_PATH),
                "The selected frost boots should replace native water freezing with a temporary path above it");

            level.setBlockAndUpdate(water.above(), Blocks.AIR.defaultBlockState());
            player.setOnGround(true);
            EnchantmentHelper.runLocationChangedEffects(level, selectedBoots, player, EquipmentSlot.FEET);
            require(level.getBlockState(water).is(Blocks.WATER), "Native Frost Walker must not replace water for selected boots");
            EnchantmentHelper.stopLocationBasedEffects(selectedBoots, player, EquipmentSlot.FEET);
            ItemStack ordinary = new ItemStack(Items.DIAMOND_BOOTS);
            ordinary.enchant(enchant(level, "frost_walker"), 2);
            player.setItemSlot(EquipmentSlot.FEET, ordinary);
            EnchantmentHelper.runLocationChangedEffects(level, ordinary, player, EquipmentSlot.FEET);
            require(level.getBlockState(water).is(Blocks.FROSTED_ICE), "An ordinary Frost Walker must keep vanilla behavior");
            EnchantmentHelper.stopLocationBasedEffects(ordinary, player, EquipmentSlot.FEET);

            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack heldHelmet = withPair(new ItemStack(Items.DIAMOND_HELMET), HEARTH);
            ItemStack oldHearth = withPair(new ItemStack(Items.SHIELD), HEARTH);
            ItemStack leggings = withPair(new ItemStack(Items.DIAMOND_LEGGINGS), HEARTH);
            player.setItemSlot(EquipmentSlot.MAINHAND, heldHelmet);
            require(FrostFireSkills.select(player, false) == null, "Held selected armor must not activate hearth");
            player.setItemSlot(EquipmentSlot.OFFHAND, oldHearth);
            require(FrostFireSkills.select(player, false).stack() == oldHearth, "Old unsupported hearth carrier remains active");
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
            var selectedHearth = FrostFireSkills.select(player, false);
            require(selectedHearth != null && selectedHearth.stack() == leggings, "Worn armor takes priority for hearth");
            cow.setPos(player.getX() + 1.5D, player.getY(), player.getZ());
            zombie.setPos(player.getX() - 1.5D, player.getY(), player.getZ());
            level.addFreshEntity(cow);
            level.addFreshEntity(zombie);
            player.setTicksFrozen(90);
            FrostFireSkills.tickHearth(level, player, true);
            require(player.getTicksFrozen() == 0 && zombie.getTicksFrozen() == 25, "Hearth clears wearer and chills a hostile");
            require(cow.getTicksFrozen() == 25, "Retained broad non-allied behavior also chills neutral animals");
        } finally {
            cow.discard();
            zombie.discard();
            EnchantmentHelper.stopLocationBasedEffects(selectedBoots, player, EquipmentSlot.FEET);
            for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], savedSlots[i]);
            player.setGameMode(oldMode);
            player.setTicksFrozen(oldFrozen);
            player.setShiftKeyDown(oldShift);
            player.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
            player.setOnGround(oldGround);
        }
    }

    private static void exerciseCarriers(ServerLevel level) {
        Holder<Enchantment> frost = enchant(level, "frost_walker"), fire = enchant(level, "fire_protection");
        for (FusedPair pair : List.of(BASALT, HEARTH)) {
            ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS), helmet = new ItemStack(Items.DIAMOND_HELMET);
            ItemStack shield = new ItemStack(Items.SHIELD);
            require(DirectionalHostRules.validateNewOrChanged(boots, pair) == RejectReason.NONE, "Both directions accept boots");
            require(DirectionalHostRules.validateNewOrChanged(helmet, pair) ==
                (FrostFireSkills.isBasaltPair(pair) ? RejectReason.UNSUPPORTED_DIRECTION_HOST : RejectReason.NONE),
                "The hearth direction accepts helmets while basalt requires boots");
            require(DirectionalHostRules.validateNewOrChanged(shield, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "New shield applications must be rejected");
            for (boolean direct : List.of(false, true)) {
                var station = new SimpleContainer(4);
                station.setItem(FusionStationOperations.SLOT_TARGET, shield.copy());
                if (direct) {
                    station.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(
                        FrostFireSkills.isBasaltPair(pair) ? frost : fire, pair.primaryLevel()));
                    station.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(
                        FrostFireSkills.isBasaltPair(pair) ? fire : frost, pair.secondaryLevel()));
                } else station.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
                int first = direct ? 0 : -1, second = direct ? 1 : -1;
                require(FusionStationOperations.inspect(station, level.registryAccess(), FusionStationOperations.TAB_SOCKET, first, second)
                    .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Station must reject unsupported shield");
                station.setItem(FusionStationOperations.SLOT_TARGET, boots.copy());
                require(FusionStationOperations.inspect(station, level.registryAccess(), FusionStationOperations.TAB_SOCKET, first, second)
                    .executable(), "Station must accept boots");
                station.setItem(FusionStationOperations.SLOT_TARGET, helmet.copy());
                var inspection = FusionStationOperations.inspect(station, level.registryAccess(), FusionStationOperations.TAB_SOCKET, first, second);
                require(FrostFireSkills.isBasaltPair(pair) ? inspection.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST : inspection.executable(),
                    "Both station paths must apply the direction-specific helmet rule");
            }
        }
    }

    private static ItemStack withPair(ItemStack stack, FusedPair pair) {
        stack.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(pair));
        return stack;
    }
    private static Holder<Enchantment> enchant(ServerLevel level, String path) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
