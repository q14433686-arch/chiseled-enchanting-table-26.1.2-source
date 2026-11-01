package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.signatures.GrapnelAttribute;
import enchantment_fusion.mechanics.signatures.GrapnelMotion;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Disposable engine fixture: prepared inputs and ground, not real client movement or multiplayer. */
final class GrapnelSoulProbe {
    private static final Identifier PROJECTED = EnchantmentFusion.id("primary_location/soul_speed/minecraft/enchantment.soul_speed/feet");
    private static final Identifier CAP = EnchantmentFusion.id("fixture_soul_speed_cap");
    private GrapnelSoulProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelAttribute.SOUL, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_BOOTS),
            List.of(Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_SWORD, Items.FISHING_ROD));
        Input oldInput = player.getLastClientInput(); Vec3 oldKnown = player.getKnownMovement();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance efficiency = player.getAttribute(Attributes.MOVEMENT_EFFICIENCY);
        require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null,
            "Fixture may not replace a pre-existing projection ID");
        Map<BlockPos, BlockState> prior = new LinkedHashMap<>();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            detect(player); GrapnelSkills.clearPlayer(player.getUUID()); PrimaryLocationProjection.clearPlayer(player);
            for (int x = 0; x < 6; x++) {
                BlockPos p = origin.below().east(x); BlockState state = f.level.getBlockState(p);
                require(f.level.hasChunkAt(p) && f.level.isInWorldBounds(p) && state.isAir(), "Soul fixture needs disposable empty ground");
                prior.put(p, state); f.level.setBlockAndUpdate(p, Blocks.SOUL_SOIL.defaultBlockState());
            }
            player.setOnGround(true); player.setYRot(-90); player.setXRot(0); player.setKnownMovement(Vec3.ZERO);
            Vec3 start = player.position(); double baseSpeed = speed.getValue(), baseEfficiency = efficiency.getValue();
            LivingEntity victim = f.spawn(false, 4, 0);
            ItemStack gear = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, pair); detect(player);
            EnchantmentHelper.runLocationChangedEffects(f.level, player);
            double nativeSpeed = speed.getValue(), nativeEfficiency = efficiency.getValue();
            require(nativeSpeed > baseSpeed && nativeEfficiency > baseEfficiency, "Native Soul Speed must activate both conditional attributes on soul soil");
            require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null, "Native Soul Speed must never get an extra projection");
            int nativeWear = gear.getDamageValue();
            for (int i = 0; i < 800 && !gear.isEmpty(); i++) {
                int priorDamage = gear.getDamageValue(); EnchantmentHelper.runLocationChangedEffects(f.level, player);
                require(gear.isEmpty() || gear.getDamageValue() - priorDamage <= 1, "One native location event cannot wear boots twice");
            }
            require(!gear.isEmpty() && gear.getDamageValue() > nativeWear, "Default native Soul Speed must still incur real random wear");

            player.setLastClientInput(Input.EMPTY); groundedMove(player); settle(player);
            require(!marked(player), "Soul-ground movement without input cannot mark");
            reset(player, start); input(player);
            speed.addTransientModifier(new AttributeModifier(CAP, 2048, AttributeModifier.Operation.ADD_VALUE));
            groundedMove(player); settle(player); require(!marked(player), "A saturated speed attribute cannot credit Soul Speed");
            speed.removeModifier(CAP); reset(player, start);
            groundedMove(player); player.setPos(player.position().add(4, 0, 0)); settle(player);
            require(!marked(player), "A correction after movement cancels the pending mark");
            reset(player, start); groundedMove(player); settle(player);
            require(marked(player), "Input-directed real movement with active native Soul Speed must mark");
            player.setItemSlot(EquipmentSlot.FEET, gear.copy());
            require(!GrapnelSkills.activate(player), "A copy cannot spend a mark from the original boots");
            player.setItemSlot(EquipmentSlot.FEET, gear); GrapnelSkills.clearPlayer(player.getUUID());
            reset(player, start); groundedMove(player); settle(player); victim.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && victim.getDeltaMovement().x < 0 && !marked(player), "Original boots recall one eligible aimed target");

            GrapnelSkills.clearPlayer(player.getUUID()); reset(player, start);
            ItemEnchantments nativeEnchants = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            gear.remove(DataComponents.ENCHANTMENTS); detect(player);
            EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(Math.abs(speed.getValue() - nativeSpeed) < 1.0E-6D && Math.abs(efficiency.getValue() - nativeEfficiency) < 1.0E-6D,
                "Missing native Soul Speed replays both current conditional attributes exactly once");
            require(speed.getModifier(PROJECTED) != null && efficiency.getModifier(PROJECTED) != null, "Projected IDs must be owned and separate from vanilla");
            groundedMove(player); settle(player); require(marked(player), "The conditional projected primary may mark a real input-directed step");
            GrapnelSkills.clearPlayer(player.getUUID()); reset(player, start);
            f.level.setBlockAndUpdate(origin.below(), Blocks.DIRT.defaultBlockState());
            EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null, "Leaving soul ground deactivates both projections");
            int dryWear = gear.getDamageValue();
            for (int i = 0; i < 100; i++) EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(gear.getDamageValue() == dryWear, "Default location wear must not apply on ordinary ground");
            groundedMove(player); settle(player); require(!marked(player), "Ordinary ground never creates a Soul Speed mark");
            reset(player, start); f.level.setBlockAndUpdate(origin.below(), Blocks.SOUL_SOIL.defaultBlockState());
            EnchantmentHelper.runLocationChangedEffects(f.level, player);
            gear.set(DataComponents.ENCHANTMENTS, nativeEnchants); detect(player);
            EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null
                && Math.abs(speed.getValue() - nativeSpeed) < 1.0E-6D, "Restoring native primary clears projection without doubling");

            FusedPair step = FusedPair.clean(SoulStepSkills.SOUL, 3, SoulStepSkills.SWIFT, 2);
            ItemStack shared = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, step); shared.remove(DataComponents.ENCHANTMENTS);
            detect(player); EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(speed.getModifier(PROJECTED) != null && Math.abs(speed.getValue() - nativeSpeed) < 1.0E-6D,
                "The selected Soul Step direction shares one missing primary without changing native boots");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); detect(player);
            require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null, "Unequipping cleans both owned modifiers");

            ItemStack missing = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, pair); missing.remove(DataComponents.ENCHANTMENTS);
            detect(player); EnchantmentHelper.runLocationChangedEffects(f.level, player);
            AttributeModifier foreign = new AttributeModifier(PROJECTED, 0.314D, AttributeModifier.Operation.ADD_VALUE);
            speed.removeModifier(PROJECTED); speed.addTransientModifier(foreign);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); detect(player);
            require(foreign.equals(speed.getModifier(PROJECTED)), "Unequipping must not remove a different modifier with our ID");
            speed.removeModifier(PROJECTED);

            // Default datapack wear is a 4% constant per location invocation, not 4% per level.
            // This is intentionally a statistical check: the chance of no wear in 800 rolls is below 7e-15.
            missing = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, pair); missing.remove(DataComponents.ENCHANTMENTS);
            detect(player); EnchantmentHelper.runLocationChangedEffects(f.level, player);
            int before = missing.getDamageValue();
            for (int i = 0; i < 800 && !missing.isEmpty(); i++) {
                int priorDamage = missing.getDamageValue(); EnchantmentHelper.runLocationChangedEffects(f.level, player);
                require(missing.isEmpty() || missing.getDamageValue() - priorDamage <= 1, "One location event cannot damage the projected boots twice");
            }
            require(!missing.isEmpty() && missing.getDamageValue() > before, "Default conditional damage must actually wear the projected boots");
            missing.setDamageValue(missing.getMaxDamage() - 1);
            for (int i = 0; i < 800 && !missing.isEmpty(); i++) EnchantmentHelper.runLocationChangedEffects(f.level, player);
            require(missing.isEmpty(), "Actual conditional wear must break nearly broken boots");
            require(speed.getModifier(PROJECTED) == null && efficiency.getModifier(PROJECTED) == null, "Breaking boots cleans owned location attributes immediately");
        } finally {
            speed.removeModifier(CAP); speed.removeModifier(PROJECTED); efficiency.removeModifier(PROJECTED);
            GrapnelSkills.clearPlayer(player.getUUID()); PrimaryLocationProjection.clearPlayer(player);
            for (var entry : prior.entrySet()) player.level().setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setLastClientInput(oldInput); player.setKnownMovement(oldKnown); detect(player);
        }
    }
    private static void input(ServerPlayer player) { player.setLastClientInput(new Input(true, false, false, false, false, false, false)); }
    private static void reset(ServerPlayer player, Vec3 start) { player.setPos(start); player.setOnGround(true); }
    private static void groundedMove(ServerPlayer player) { player.move(MoverType.SELF, new Vec3(0.12D, -0.05D, 0)); }
    private static void settle(ServerPlayer player) { GrapnelMotion.tickServer(player.level().getServer()); }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static boolean marked(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
}
