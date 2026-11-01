package enchantment_fusion.smoke;

import static enchantment_fusion.smoke.SharedMeleeFixture.require;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.GrapnelAttribute;
import enchantment_fusion.mechanics.signatures.GrapnelMotion;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Prepared engine fixtures: equipment refresh, inputs, movement and water are simulated, not live client packets. */
final class GrapnelAttributeProbe {
    private static final Identifier CAP = EnchantmentFusion.id("fixture_grapnel_attribute_cap");
    private GrapnelAttributeProbe() {}
    static void movement(ServerPlayer player, BlockPos origin, String primary) throws Exception {
        boolean swift = GrapnelAttribute.SWIFT.equals(primary);
        FusedPair pair = FusedPair.clean(primary, 3, GrapnelSkills.LURE, 2);
        Item item = swift ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS;
        EquipmentSlot slot = swift ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
        Holder<Attribute> attribute = swift ? Attributes.SNEAKING_SPEED : Attributes.WATER_MOVEMENT_EFFICIENCY;
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(item),
            List.of(swift ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS, Items.DIAMOND_SWORD, Items.DIAMOND_HELMET, Items.FISHING_ROD));
        Pose oldPose = player.getPose(); boolean oldShift = player.isShiftKeyDown(); Input oldInput = player.getLastClientInput();
        BlockState original = player.level().getBlockState(origin); AttributeInstance instance = player.getAttribute(attribute);
        Vec3 known = player.getKnownMovement();
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            detect(player); GrapnelSkills.clearPlayer(player.getUUID());
            LivingEntity victim = f.spawn(false, 4, 0);
            Villager villager = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, 3);
            player.setYRot(-90); player.setXRot(0); player.setKnownMovement(Vec3.ZERO);
            player.setShiftKeyDown(swift); player.setPose(swift ? Pose.CROUCHING : Pose.STANDING);
            ItemStack gear = f.equip(slot, item, pair); detect(player);
            if (!swift) { f.level.setBlockAndUpdate(origin, Blocks.WATER.defaultBlockState()); refreshWater(player); require(player.isInWater(), "Water fixture must be wet"); }
            Vec3 start = player.position();
            player.setLastClientInput(Input.EMPTY); move(player); settle(player);
            require(!marked(player), "Displacement without movement input cannot mark");
            player.setPos(start); input(player, swift);
            var cap = new AttributeModifier(CAP, 2.0D, AttributeModifier.Operation.ADD_VALUE); instance.addTransientModifier(cap);
            move(player); settle(player); require(!marked(player), "A saturated primary cannot be credited");
            instance.removeModifier(CAP); player.setPos(start);
            move(player); player.setPos(player.position().add(4, 0, 0)); settle(player);
            require(!marked(player), "An unobserved correction after a movement cancels its pending mark");
            player.setPos(start); move(player); settle(player);
            require(marked(player), "Input-directed movement with the active native attribute must mark");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ());
            require(!GrapnelSkills.activate(player) && marked(player), "A protected body blocks the aim without spending the position mark");
            villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            player.setItemSlot(slot, gear.copy());
            require(!GrapnelSkills.activate(player), "A copied item cannot spend the original equipment mark");
            player.setItemSlot(slot, gear); GrapnelSkills.clearPlayer(player.getUUID()); player.setPos(start);
            move(player); settle(player); victim.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && victim.getDeltaMovement().x < 0 && !marked(player),
                "The same equipped item may pull one eligible aimed target and consume its mark");

            GrapnelSkills.clearPlayer(player.getUUID()); player.setPos(start);
            ItemEnchantments nativeEnchants = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            double nativeValue = instance.getValue(); gear.remove(DataComponents.ENCHANTMENTS); detect(player);
            MeleeWorkSkills.tickServer(f.level.getServer());
            require(Math.abs(instance.getValue() - nativeValue) < 1.0E-6D, "Missing primary projects the same datapack attribute exactly once");
            move(player); settle(player); require(marked(player), "The one projected primary also supports a real movement mark");
            gear.set(DataComponents.ENCHANTMENTS, nativeEnchants); detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(Math.abs(instance.getValue() - nativeValue) < 1.0E-6D, "Restoring the native primary removes the projection without doubling");
        } finally {
            instance.removeModifier(CAP); GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            player.level().setBlockAndUpdate(origin, original); player.setPose(oldPose); player.setShiftKeyDown(oldShift);
            player.setLastClientInput(oldInput); player.setKnownMovement(known); refreshWater(player); detect(player);
        }
    }
    static void sweep(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelAttribute.SWEEP, 3, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_SWORD),
            List.of(Items.DIAMOND_AXE, Items.MACE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.BOW));
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        Vec3 known = player.getKnownMovement(); boolean sprint = player.isSprinting();
        AttributeInstance attribute = player.getAttribute(Attributes.SWEEPING_DAMAGE_RATIO);
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            detect(player); GrapnelSkills.clearPlayer(player.getUUID());
            LivingEntity primary = f.spawn(false, 2, 0), secondary = f.spawn(false, 2, 0.6D);
            Villager protectedVictim = f.spawn(new Villager(EntityType.VILLAGER, f.level), 2, -0.6D);
            player.setKnownMovement(Vec3.ZERO); player.setSprinting(false); player.setOnGround(true); player.setYRot(-90);
            ItemStack sword = f.equip(Items.DIAMOND_SWORD, pair); detect(player);
            denied.add(primary.getUUID()); hit(f, primary, secondary, protectedVictim); denied.clear();
            require(!marked(player), "A cancelled direct hit cannot produce a sweep mark");
            denied.add(secondary.getUUID()); hit(f, primary, secondary, protectedVictim); denied.clear();
            require(!marked(player), "A cancelled secondary hit and a protected villager cannot produce a mark");
            attribute.addTransientModifier(new AttributeModifier(CAP, 2.0D, AttributeModifier.Operation.ADD_VALUE));
            hit(f, primary, secondary, protectedVictim);
            require(!marked(player), "A normal sweep with saturated primary damage ratio cannot be credited");
            attribute.removeModifier(CAP); hit(f, primary, secondary, protectedVictim);
            require(secondary.getHealth() < 200 && markedTarget(player).equals(secondary.getUUID()),
                "A real enhanced sword sweep marks its actual eligible secondary victim");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
            require(!GrapnelSkills.activate(player), "A copied sword cannot spend the original target mark");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword); GrapnelSkills.clearPlayer(player.getUUID());
            hit(f, primary, secondary, protectedVictim); secondary.setDeltaMovement(Vec3.ZERO);
            require(GrapnelSkills.activate(player) && secondary.getDeltaMovement().x < 0 && !marked(player),
                "The same sword pulls the marked enemy once");
            GrapnelSkills.clearPlayer(player.getUUID());
            double nativeValue = attribute.getValue(); ItemEnchantments saved = sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            sword.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(Math.abs(attribute.getValue() - nativeValue) < 1.0E-6D, "The missing sweep primary is projected exactly once");
            hit(f, primary, secondary, protectedVictim);
            require(markedTarget(player).equals(secondary.getUUID()), "The projected primary can mark a real swept victim");
            sword.set(DataComponents.ENCHANTMENTS, saved); detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(Math.abs(attribute.getValue() - nativeValue) < 1.0E-6D, "Native sweep restoration removes its projection");
        } finally {
            denied.clear(); attribute.removeModifier(CAP); GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            player.setKnownMovement(known); player.setSprinting(sprint); detect(player);
        }
    }
    /** Separate regression fixture: old work and new movement/sweep projections must coexist. */
    static void sharedProjection(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair swift = FusedPair.clean(GrapnelAttribute.SWIFT, 3, GrapnelSkills.LURE, 2);
        FusedPair depth = FusedPair.clean(GrapnelAttribute.DEPTH, 3, GrapnelSkills.LURE, 2);
        FusedPair sweep = FusedPair.clean(GrapnelAttribute.SWEEP, 3, GrapnelSkills.LURE, 2);
        FusedPair efficiency = FusedPair.clean(MeleeWorkSkills.EFFICIENCY, 3, GrapnelSkills.LURE, 2);
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            detect(player);
            ItemStack legs = f.equip(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, swift);
            ItemStack boots = f.equip(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, depth);
            ItemStack pick = f.equip(Items.DIAMOND_PICKAXE, efficiency);
            ItemEnchantments nativeSwift = legs.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            legs.remove(DataComponents.ENCHANTMENTS); boots.remove(DataComponents.ENCHANTMENTS); pick.remove(DataComponents.ENCHANTMENTS);
            detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(projected(player, swift, EquipmentSlot.LEGS, Attributes.SNEAKING_SPEED)
                && projected(player, depth, EquipmentSlot.FEET, Attributes.WATER_MOVEMENT_EFFICIENCY)
                && projected(player, efficiency, EquipmentSlot.MAINHAND, Attributes.MINING_EFFICIENCY),
                "Movement and the existing work primary projections coexist");
            MeleeWorkSkills.tickServer(f.level.getServer());
            require(projected(player, swift, EquipmentSlot.LEGS, Attributes.SNEAKING_SPEED)
                && projected(player, depth, EquipmentSlot.FEET, Attributes.WATER_MOVEMENT_EFFICIENCY), "Repeated refresh does not erase movement projections");
            ItemStack sword = f.equip(Items.DIAMOND_SWORD, sweep); sword.remove(DataComponents.ENCHANTMENTS);
            detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(projected(player, swift, EquipmentSlot.LEGS, Attributes.SNEAKING_SPEED)
                && projected(player, depth, EquipmentSlot.FEET, Attributes.WATER_MOVEMENT_EFFICIENCY)
                && projected(player, sweep, EquipmentSlot.MAINHAND, Attributes.SWEEPING_DAMAGE_RATIO)
                && !projected(player, efficiency, EquipmentSlot.MAINHAND, Attributes.MINING_EFFICIENCY),
                "Replacing the work item changes only its own projection");
            legs.set(DataComponents.ENCHANTMENTS, nativeSwift); detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(!projected(player, swift, EquipmentSlot.LEGS, Attributes.SNEAKING_SPEED)
                && projected(player, depth, EquipmentSlot.FEET, Attributes.WATER_MOVEMENT_EFFICIENCY)
                && projected(player, sweep, EquipmentSlot.MAINHAND, Attributes.SWEEPING_DAMAGE_RATIO),
                "Native restoration cannot clear other equipment projections");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); detect(player); MeleeWorkSkills.tickServer(f.level.getServer());
            require(!projected(player, depth, EquipmentSlot.FEET, Attributes.WATER_MOVEMENT_EFFICIENCY)
                && projected(player, sweep, EquipmentSlot.MAINHAND, Attributes.SWEEPING_DAMAGE_RATIO), "Unequipping clears only that source");
        } finally { GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player); detect(player); }
    }
    private static boolean projected(ServerPlayer player, FusedPair pair, EquipmentSlot slot, Holder<Attribute> target) {
        var enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), pair.primaryId()).orElseThrow();
        var effects = enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES);
        for (int i = 0; i < effects.size(); i++) if (target.equals(effects.get(i).attribute())) {
            Identifier id = EnchantmentFusion.id("primary_attribute/" + pair.primaryId().replace(':', '/') + "/" + slot.getName() + "/" + i);
            var base = effects.get(i).getModifier(pair.primaryLevel(), slot); var active = player.getAttribute(target).getModifier(id);
            return active != null && active.amount() == base.amount() && active.operation() == base.operation();
        }
        return false;
    }
    private static void hit(SharedMeleeFixture f, LivingEntity primary, LivingEntity secondary, LivingEntity villager) {
        for (LivingEntity victim : List.of(primary, secondary, villager)) { victim.setHealth(200); victim.invulnerableTime = 0; }
        f.player.setOnGround(true); f.player.fallDistance = 0; f.hit(primary, true);
    }
    private static void input(ServerPlayer player, boolean swift) {
        player.setLastClientInput(new Input(true, false, false, false, false, swift, false));
    }
    private static void move(ServerPlayer player) { player.move(MoverType.SELF, new Vec3(0.12D, 0, 0)); }
    private static void settle(ServerPlayer player) { GrapnelMotion.tickServer(player.level().getServer()); }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
    private static @org.jetbrains.annotations.Nullable Object mark(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static boolean marked(ServerPlayer player) throws Exception { return mark(player) != null; }
    private static UUID markedTarget(ServerPlayer player) throws Exception {
        Object mark = mark(player); require(mark != null, "A target mark must exist");
        Method method = mark.getClass().getDeclaredMethod("target"); method.setAccessible(true); return (UUID) method.invoke(mark);
    }
}
