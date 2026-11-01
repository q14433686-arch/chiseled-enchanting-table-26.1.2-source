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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Actual game-mode breaks and pickup in prepared water; timing and client input are simulated. */
final class GrapnelAquaProbe {
    private static final Identifier PROJECTED = EnchantmentFusion.id("primary_attribute/minecraft/aqua_affinity/head/0");
    private static final Identifier CAP = EnchantmentFusion.id("fixture_aqua_mining_cap");
    private GrapnelAquaProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin) throws Exception {
        FusedPair pair = FusedPair.clean(GrapnelAttribute.AQUA, 1, GrapnelSkills.LURE, 2);
        SharedMeleeFixture.checkStation(player.level(), pair, List.of(Items.DIAMOND_HELMET),
            List.of(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.FISHING_ROD));
        ServerLevel level = player.level(); BlockPos target = origin.east(3);
        int size = player.getInventory().getContainerSize(); ItemStack[] inventory = new ItemStack[size];
        for (int i = 0; i < size; i++) inventory[i] = player.getInventory().getItem(i);
        AttributeInstance speed = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        require(speed.getModifier(PROJECTED) == null, "Fixture must not replace a pre-existing projection ID");
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); boolean[] denied = {false}, nested = {false};
        BlockPos inner = origin.east(4);
        PlayerBlockBreakEvents.BEFORE.register((world, actor, pos, state, be) -> {
            if (actor == player && target.equals(pos) && nested[0]) { nested[0] = false; player.gameMode.destroyBlock(inner); }
            return actor != player || !target.equals(pos) || !denied[0];
        });
        AABB box = AABB.encapsulatingFullBlocks(origin.offset(-2, -2, -3), origin.offset(6, 4, 4));
        List<ItemEntity> oldItems = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, box));
        List<ExperienceOrb> oldOrbs = new ArrayList<>(level.getEntitiesOfClass(ExperienceOrb.class, box));
        try (SharedMeleeFixture f = new SharedMeleeFixture(player, origin)) {
            for (int x = -1; x <= 4; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                BlockPos p = origin.offset(x, y, z); BlockState block = level.getBlockState(p);
                require(level.hasChunkAt(p) && level.isInWorldBounds(p) && block.isAir(), "Aqua fixture needs loaded disposable air");
                saved.put(p, block);
            }
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
            detect(player); GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            ItemStack helmet = f.equip(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, pair);
            ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE); player.setItemSlot(EquipmentSlot.MAINHAND, tool);
            detect(player); MeleeWorkSkills.tickServer(level.getServer()); refreshWater(player);
            double nativeSpeed = speed.getValue();
            level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
            require(player.gameMode.destroyBlock(target) && markedDrop(player) == null, "Mining out of water cannot produce an Aqua Affinity mark");
            for (BlockPos p : saved.keySet()) level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
            refreshWater(player); require(player.isEyeInFluid(FluidTags.WATER), "The prepared water must cover the player's eyes");
            speed.addTransientModifier(new AttributeModifier(CAP, 100, AttributeModifier.Operation.ADD_VALUE));
            level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
            require(player.gameMode.destroyBlock(target) && markedDrop(player) == null, "A saturated underwater attribute cannot be credited");
            speed.removeModifier(CAP);
            level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
            level.setBlockAndUpdate(inner, Blocks.DIAMOND_ORE.defaultBlockState()); nested[0] = true;
            require(player.gameMode.destroyBlock(target) && !nested[0] && markedDrop(player) == null,
                "Nested ambiguous breaks cannot produce a helmet-owned mark");
            ItemEntity unrelated = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 1.5, new ItemStack(Items.DIAMOND));
            require(level.addFreshEntity(unrelated), "The unrelated nearby item must spawn");
            level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState()); denied[0] = true;
            require(!player.gameMode.destroyBlock(target) && markedDrop(player) == null, "A cancelled underwater break cannot mark"); denied[0] = false;
            int priorDamage = tool.getDamageValue(); require(player.gameMode.destroyBlock(target), "Real underwater mining must succeed");
            ItemEntity drop = markedDrop(player);
            require(drop != null && drop != unrelated && drop.getItem().is(Items.DIAMOND) && tool.getDamageValue() == priorDamage + 1,
                "Only actual block loot is marked, and the real tool still pays durability");
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "Normal pickup delay cannot be bypassed");
            drop.setNoPickUpDelay(); player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
            require(!GrapnelSkills.activate(player), "A copied helmet cannot spend the original mark");
            player.setItemSlot(EquipmentSlot.HEAD, helmet); detect(player);
            drop = mine(player, level, target); drop.setNoPickUpDelay(); drop.setTarget(UUID.randomUUID());
            require(!GrapnelSkills.activate(player) && drop.isAlive(), "A drop reserved for another player is not collected");
            drop.setTarget(null); require(GrapnelSkills.activate(player) && !drop.isAlive() && unrelated.isAlive(),
                "The same helmet collects only the verified loot through ordinary pickup");
            require(!GrapnelSkills.activate(player), "A drop mark is spent once");

            GrapnelSkills.clearPlayer(player.getUUID());
            ItemEnchantments enchantments = helmet.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            helmet.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
            require(speed.getModifier(PROJECTED) != null && Math.abs(speed.getValue() - nativeSpeed) < 1.0E-6D,
                "A missing Aqua Affinity projects the same attribute only once");
            drop = mine(player, level, target); drop.setNoPickUpDelay();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); detect(player); MeleeWorkSkills.tickServer(level.getServer());
            require(speed.getModifier(PROJECTED) == null && !GrapnelSkills.activate(player) && drop.isAlive(), "Removing the helmet cleans its attribute and invalidates its mark");
            helmet.set(DataComponents.ENCHANTMENTS, enchantments); player.setItemSlot(EquipmentSlot.HEAD, helmet); detect(player);
            MeleeWorkSkills.tickServer(level.getServer());
            require(speed.getModifier(PROJECTED) == null && Math.abs(speed.getValue() - nativeSpeed) < 1.0E-6D, "Native restoration cannot double the attribute");

            // Multiple families on one item share the highest missing primary, in either provider order.
            for (boolean lureHigh : new boolean[]{false, true}) {
                GrapnelSkills.clearPlayer(player.getUUID());
                FusedPair lure = FusedPair.clean(GrapnelAttribute.AQUA, lureHigh ? 5 : 1, GrapnelSkills.LURE, 2);
                FusedPair work = FusedPair.clean(GrapnelAttribute.AQUA, lureHigh ? 1 : 5, "minecraft:smite", 3);
                ItemStack shared = f.equip(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, lure);
                FusionStationOperations.applyFusedPairToGear(shared, work, level.registryAccess());
                shared.remove(DataComponents.ENCHANTMENTS); detect(player); MeleeWorkSkills.tickServer(level.getServer());
                var enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), GrapnelAttribute.AQUA).orElseThrow();
                var base = enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).getFirst().getModifier(5, EquipmentSlot.HEAD);
                var projected = speed.getModifier(PROJECTED);
                require(projected != null && projected.amount() == base.amount() && projected.operation() == base.operation(),
                    "One shared projection must use the highest primary level, independent of provider order");
                level.setBlockAndUpdate(target, Blocks.DIAMOND_ORE.defaultBlockState());
                require(player.gameMode.destroyBlock(target) && markedDrop(player) == null,
                    "Two same-primary fusions have ambiguous mark attribution");
            }
            player.setItemSlot(EquipmentSlot.HEAD, helmet); detect(player); MeleeWorkSkills.tickServer(level.getServer());
            // Existing held-tool drop marks retain priority even when the helmet also qualifies.
            GrapnelSkills.clearPlayer(player.getUUID());
            FusedPair held = FusedPair.clean(GrapnelSkills.EFFICIENCY, 3, GrapnelSkills.LURE, 2);
            FusionStationOperations.applyFusedPairToGear(tool, held, level.registryAccess());
            drop = mine(player, level, target); drop.setNoPickUpDelay();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            require(GrapnelSkills.activate(player) && !drop.isAlive(), "The old held-tool mark does not become bound to the new helmet");
        } finally {
            denied[0] = false; nested[0] = false; speed.removeModifier(CAP);
            GrapnelSkills.clearPlayer(player.getUUID()); MeleeWorkSkills.clearPlayer(player);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) if (!oldItems.contains(entity)) entity.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) if (!oldOrbs.contains(orb)) orb.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            for (int i = 0; i < size; i++) player.getInventory().setItem(i, inventory[i]);
            detect(player); refreshWater(player);
        }
    }
    private static ItemEntity mine(ServerPlayer player, ServerLevel level, BlockPos pos) throws Exception {
        level.setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState());
        require(player.gameMode.destroyBlock(pos), "Prepared ore break must succeed");
        ItemEntity drop = markedDrop(player); require(drop != null, "The real drop must be marked"); return drop;
    }
    private static @org.jetbrains.annotations.Nullable ItemEntity markedDrop(ServerPlayer player) throws Exception {
        Field field = GrapnelSkills.class.getDeclaredField("MARKS"); field.setAccessible(true);
        Object mark = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (mark == null) return null;
        Method drops = mark.getClass().getDeclaredMethod("drops"); drops.setAccessible(true);
        List<?> list = (List<?>) drops.invoke(mark); if (list.isEmpty()) return null;
        Method entity = list.getFirst().getClass().getDeclaredMethod("entity"); entity.setAccessible(true); return (ItemEntity) entity.invoke(list.getFirst());
    }
    private static void detect(ServerPlayer player) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); method.setAccessible(true); method.invoke(player);
    }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
}
