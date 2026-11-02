package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelAttribute;
import enchantment_fusion.mechanics.signatures.GrapnelFrost;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Prepared source water and explicit native location events; not a natural movement or multiplayer test. */
final class GrapnelFrostProbe {
    private static final Identifier SOUL = EnchantmentFusion.id("primary_location/soul_speed/minecraft/enchantment.soul_speed/feet");
    private GrapnelFrostProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair frost = FusedPair.clean(GrapnelFrost.FROST, 2, GrapnelSkills.LURE, 2);
        FusedPair soul = FusedPair.clean(GrapnelAttribute.SOUL, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), frost, List.of(Items.DIAMOND_BOOTS),
            List.of(Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_SWORD, Items.FISHING_ROD));
        ServerLevel level = player.level(); BlockPos ground = origin.below(), water = origin.east().below(), top = water.above();
        Map<BlockPos, BlockState> prior = new LinkedHashMap<>();
        for (BlockPos pos : List.of(ground, water, top, origin)) {
            require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && level.getBlockState(pos).isAir(),
                "Frost fixture needs loaded disposable air");
            prior.put(pos, level.getBlockState(pos));
        }
        boolean oldShift = player.isShiftKeyDown();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        require(speed.getModifier(SOUL) == null, "Fixture must not replace an existing Soul projection");
        try (SharedMeleeFixture fixture = new SharedMeleeFixture(player, origin)) {
            level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
            player.setOnGround(true); player.setShiftKeyDown(false); player.setYRot(-90); player.setXRot(0); refreshFluid(player);
            LivingEntity target = fixture.spawn(false, 4, 0);
            Villager villager = fixture.spawn(new Villager(EntityType.VILLAGER, level), 2, 5);
            for (boolean nativePrimary : new boolean[]{true, false}) {
                PrimaryLocationProjection.clearPlayer(player); GrapnelSkills.clearPlayer(player.getUUID());
                dry(level, water, top);
                ItemStack boots = fixture.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, frost);
                if (!nativePrimary) boots.remove(DataComponents.ENCHANTMENTS);
                detect(player); GrapnelSkills.clearPlayer(player.getUUID());
                location(level, player);
                require(!marked(player), "Ordinary dry ground cannot create a Frost Walker mark");
                require(EnchantmentHelper.isImmuneToDamage(level, player, level.damageSources().hotFloor())
                    && !EnchantmentHelper.isImmuneToDamage(level, player, level.damageSources().inFire()),
                    "Native or missing primary must retain the current default stepping-immunity predicate");

                source(level, water, top); player.setOnGround(false); location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).isAir() && !marked(player),
                    "Airborne Frost Walker cannot make a path or mark");
                player.setOnGround(true); player.setShiftKeyDown(true); location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).isAir() && !marked(player),
                    "Sneaking must preserve the source without placing a path or mark");
                player.setShiftKeyDown(false);
                level.setBlockAndUpdate(origin, Blocks.WATER.defaultBlockState()); refreshFluid(player);
                require(player.isInWater(), "Prepared water must actually cover the player");
                location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).isAir() && !marked(player),
                    "Submerged players cannot create a path or mark");
                level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); refreshFluid(player);
                Vec3 beforeRide = player.position();
                require(player.startRiding(target, true, false), "The prepared player must become a real passenger");
                location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).isAir() && !marked(player),
                    "A passenger cannot create a path or mark");
                player.stopRiding(); player.setPos(beforeRide); player.setOnGround(true); refreshFluid(player);
                player.setGameMode(GameType.ADVENTURE); location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).isAir() && !marked(player),
                    "A player without building permission cannot place or mark");
                player.setGameMode(GameType.SURVIVAL); location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getFluidState(water).isSource()
                    && level.getBlockState(top).is(FusionBlocks.TEMPORARY_FROST_PATH) && marked(player),
                    "The actual eligible Frost write places new thin ice above unchanged source water and marks");
                require(GrapnelSkills.hasEquipped(player), "The original worn boots expose the active ability");
                villager.setPos(player.getX() + 2, player.getY(), player.getZ());
                require(!GrapnelSkills.activate(player) && marked(player), "A protected villager blocks the ray without consuming the mark");
                villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 5);
                target.setDeltaMovement(Vec3.ZERO);
                require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && !marked(player),
                    "The original boots recall an eligible enemy exactly once");
                require(!GrapnelSkills.activate(player), "A consumed Frost mark cannot be reused");
                location(level, player);
                require(!marked(player), "Existing thin ice alone cannot earn another mark");
                require(player.gameMode.destroyBlock(top) && level.getBlockState(top).isAir()
                    && level.getBlockState(water).is(Blocks.WATER), "Normally broken thin ice cannot generate water or replace the source");
                location(level, player); require(marked(player), "A newly recreated path can mark again");
                player.setItemSlot(EquipmentSlot.FEET, boots.copy());
                require(!GrapnelSkills.activate(player), "A copied replacement cannot spend the original boots' mark");
                player.setItemSlot(EquipmentSlot.FEET, boots); detect(player); GrapnelSkills.clearPlayer(player.getUUID());
                level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(water, Blocks.FROSTED_ICE.defaultBlockState()); location(level, player);
                require(level.getBlockState(water).is(Blocks.FROSTED_ICE) && !marked(player),
                    "Old vanilla ice cannot be credited as a new source-water write");
            }

            // Ambiguous same-primary sources preserve the path but cannot claim a specific mark.
            for (boolean retained : new boolean[]{false, true}) {
                PrimaryLocationProjection.clearPlayer(player); GrapnelSkills.clearPlayer(player.getUUID()); dry(level, water, top);
                ItemStack ambiguous = fixture.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, frost);
                if (retained) ambiguous.set(FusionDataComponents.RETAINED_ENCHANTS, List.of(new RetainedEnchant(GrapnelFrost.FROST, 3)));
                else FusionStationOperations.applyFusedPairToGear(ambiguous,
                    FusedPair.clean(GrapnelFrost.FROST, 2, "minecraft:sharpness", 1), level.registryAccess());
                ambiguous.remove(DataComponents.ENCHANTMENTS); detect(player); GrapnelSkills.clearPlayer(player.getUUID());
                source(level, water, top); location(level, player);
                require(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(top).is(FusionBlocks.TEMPORARY_FROST_PATH)
                    && !marked(player), "Two possible Frost sources cannot claim a specific mark");
            }

            // Two different missing location primaries must coexist; removing Frost cannot clean Soul's state.
            PrimaryLocationProjection.clearPlayer(player); GrapnelSkills.clearPlayer(player.getUUID()); dry(level, water, top);
            level.setBlockAndUpdate(ground, Blocks.SOUL_SOIL.defaultBlockState());
            ItemStack shared = fixture.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, frost);
            FusionStationOperations.applyFusedPairToGear(shared, soul, level.registryAccess()); shared.remove(DataComponents.ENCHANTMENTS);
            detect(player); GrapnelSkills.clearPlayer(player.getUUID()); source(level, water, top); location(level, player);
            require(speed.getModifier(SOUL) != null && level.getBlockState(water).is(Blocks.WATER)
                && level.getBlockState(top).is(FusionBlocks.TEMPORARY_FROST_PATH) && marked(player),
                "Soul Speed and Frost Walker projections coexist on the same boots");
            shared.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(soul));
            PrimaryLocationProjection.reconcile(player);
            require(speed.getModifier(SOUL) != null, "Removing the Frost pair cannot remove Soul Speed's active modifier");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); PrimaryLocationProjection.reconcile(player);
            require(speed.getModifier(SOUL) == null, "Removing the boots cleans the owned Soul Speed projection");

            // Ordinary unfused native Frost Walker continues to replace the same source water.
            dry(level, water, top); ItemStack ordinary = new ItemStack(Items.DIAMOND_BOOTS);
            ordinary.enchant(FusionStationOperations.lookupEnchantment(level.registryAccess(), GrapnelFrost.FROST).orElseThrow(), 2);
            player.setItemSlot(EquipmentSlot.FEET, ordinary); detect(player); source(level, water, top); location(level, player);
            require(level.getBlockState(water).is(Blocks.FROSTED_ICE), "Unfused native Frost Walker remains unchanged");
        } finally {
            if (player.isPassenger()) player.stopRiding();
            GrapnelSkills.clearPlayer(player.getUUID()); PrimaryLocationProjection.clearPlayer(player);
            for (var entry : prior.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setShiftKeyDown(oldShift); refreshFluid(player); detect(player);
        }
    }
    private static void dry(ServerLevel level, BlockPos water, BlockPos top) {
        level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(water, Blocks.AIR.defaultBlockState());
    }
    private static void source(ServerLevel level, BlockPos water, BlockPos top) {
        level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
    }
    private static void location(ServerLevel level, ServerPlayer player) { EnchantmentHelper.runLocationChangedEffects(level, player); }
    private static void refreshFluid(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
}
