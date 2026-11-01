package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelAttribute;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.model.FusedPair;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Real breathing base ticks in prepared water; timing and player position are simulated. */
final class GrapnelBreathProbe {
    private static final Identifier PROJECTED = EnchantmentFusion.id("primary_attribute/minecraft/respiration/head/0");
    private static final Identifier CAP = EnchantmentFusion.id("fixture_breath_cap");
    private GrapnelBreathProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelAttribute.RESPIRATION, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_HELMET),
            List.of(Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.FISHING_ROD));
        ServerLevel level = player.level(); AttributeInstance oxygen = player.getAttribute(Attributes.OXYGEN_BONUS);
        require(oxygen.getModifier(PROJECTED) == null && oxygen.getModifier(CAP) == null, "Fixture cannot replace an existing modifier");
        int oldAir = player.getAirSupply(); MobEffectInstance oldEffect = player.getEffect(MobEffects.WATER_BREATHING);
        oldEffect = oldEffect == null ? null : new MobEffectInstance(oldEffect);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            player.removeEffect(MobEffects.WATER_BREATHING); detect(player); GrapnelSkills.clearPlayer(player.getUUID());
            MeleeWorkSkills.clearPlayer(player);
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                BlockPos p = origin.offset(x, y, z); BlockState block = level.getBlockState(p);
                require(level.hasChunkAt(p) && level.isInWorldBounds(p) && block.isAir(), "Breath fixture needs disposable loaded air");
                saved.put(p, block);
            }
            LivingEntity target = f.spawn(false, 4, 0); player.setYRot(-90); player.setXRot(0);
            ItemStack helmet = f.equip(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, pair); detect(player);
            double nativeValue = oxygen.getValue(); require(nativeValue > 0 && oxygen.getModifier(PROJECTED) == null, "Native Respiration is present once");
            player.setAirSupply(100); player.baseTick(); require(!marked(player), "Dry ticks cannot mark");
            for (BlockPos p : saved.keySet()) level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
            refreshWater(player); require(player.isEyeInFluid(FluidTags.WATER), "Water must cover the player's eyes");
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0));
            player.setAirSupply(100); player.baseTick(); require(!marked(player), "Water Breathing does not masquerade as a saved oxygen roll");
            player.removeEffect(MobEffects.WATER_BREATHING);
            oxygen.addTransientModifier(new AttributeModifier(CAP, 1024, AttributeModifier.Operation.ADD_VALUE));
            for (int i = 0; i < 16; i++) { player.setAirSupply(100); player.baseTick(); }
            require(!marked(player), "A saturated oxygen attribute cannot credit Respiration"); oxygen.removeModifier(CAP);
            waitForMark(player); player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
            require(!GrapnelSkills.activate(player), "A copied helmet cannot spend the original mark");
            player.setItemSlot(EquipmentSlot.HEAD, helmet); detect(player); GrapnelSkills.clearPlayer(player.getUUID());
            waitForMark(player); target.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && target.getDeltaMovement().x < 0 && !marked(player), "The original helmet recalls one eligible target");

            ItemEnchantments enchantments = helmet.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            helmet.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
            require(oxygen.getModifier(PROJECTED) != null && Math.abs(oxygen.getValue() - nativeValue) < 1.0E-6D,
                "The missing primary projects the same oxygen attribute once");
            GrapnelSkills.clearPlayer(player.getUUID()); waitForMark(player);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); detect(player); MeleeWorkSkills.tickServer(level.getServer());
            require(oxygen.getModifier(PROJECTED) == null && !GrapnelSkills.activate(player), "Unequipping cleans the projection and invalidates its mark");
            helmet.set(DataComponents.ENCHANTMENTS, enchantments); player.setItemSlot(EquipmentSlot.HEAD, helmet); detect(player);
            MeleeWorkSkills.tickServer(level.getServer());
            require(oxygen.getModifier(PROJECTED) == null && Math.abs(oxygen.getValue() - nativeValue) < 1.0E-6D,
                "Native restoration cannot double Respiration");

            for (boolean lureHigh : new boolean[]{false, true}) {
                GrapnelSkills.clearPlayer(player.getUUID());
                FusedPair lure = FusedPair.clean(GrapnelAttribute.RESPIRATION, lureHigh ? 3 : 1, GrapnelSkills.LURE, 2);
                FusedPair pressure = FusedPair.clean(GrapnelAttribute.RESPIRATION, lureHigh ? 1 : 3, "minecraft:smite", 3);
                ItemStack shared = f.equip(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, lure);
                FusionStationOperations.applyFusedPairToGear(shared, pressure, level.registryAccess());
                shared.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
                require(oxygen.getModifier(PROJECTED) != null && oxygen.getModifier(PROJECTED).amount() == 3.0D,
                    "Water-pressure and grapnel families share one highest-level oxygen projection");
                for (int i = 0; i < 16; i++) { player.setAirSupply(100); player.baseTick(); }
                require(!marked(player), "Ambiguous same-primary ownership cannot mark a breath");
            }
            FusedPair mining = FusedPair.clean(GrapnelAttribute.RESPIRATION, 3, GrapnelAttribute.AQUA, 1);
            ItemStack specialized = f.equip(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, mining); specialized.remove(DataComponents.ENCHANTMENTS);
            detect(player); MeleeWorkSkills.tickServer(level.getServer());
            require(oxygen.getModifier(PROJECTED) != null && oxygen.getModifier(PROJECTED).amount() == 3.0D,
                "The selected underwater mining family shares the same missing primary");
        } finally {
            oxygen.removeModifier(CAP); GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            player.removeEffect(MobEffects.WATER_BREATHING); if (oldEffect != null) player.addEffect(oldEffect);
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setAirSupply(oldAir); detect(player); refreshWater(player);
        }
    }
    private static void waitForMark(ServerPlayer player) throws Exception {
        for (int i = 0; i < 64 && !marked(player); i++) { player.setAirSupply(100); player.baseTick(); }
        require(marked(player) && player.getAirSupply() == 100, "A real saved underwater breath must mark without changing the air result");
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
}
