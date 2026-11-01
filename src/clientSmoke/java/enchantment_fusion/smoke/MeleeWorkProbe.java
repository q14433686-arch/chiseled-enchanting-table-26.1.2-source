package enchantment_fusion.smoke;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.model.FusedPair;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Twelve server fixtures with real attacks and block breaks; water/input/timing are simulated. */
final class MeleeWorkProbe {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private MeleeWorkProbe() {}
    static void exercise(ServerPlayer player, BlockPos origin, String attack, boolean water, boolean workFirst) throws Exception {
        ServerLevel level = player.level(); String work = water ? MeleeWorkSkills.AQUA : MeleeWorkSkills.EFFICIENCY;
        FusedPair pair = FusedPair.clean(attack, 4, work, 3); if (workFirst) pair = pair.flipped();
        checkStation(level, pair, water, workFirst); MeleeWorkSkills.clearPlayer(player);
        ItemStack[] old = new ItemStack[SLOTS.length]; for (int i = 0; i < SLOTS.length; i++) old[i] = player.getItemBySlot(SLOTS[i]);
        ItemStack oldExtra = player.getInventory().getItem(20);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot(); boolean ground = player.onGround(); double fall = player.fallDistance;
        GameType mode = player.gameMode.getGameModeForPlayer(); var speed = player.getAttribute(Attributes.ATTACK_SPEED); double oldSpeed = speed.getBaseValue();
        boolean bane = MeleeAttackThemes.BANE.equals(attack);
        LivingEntity target = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity neighbor = bane ? new Spider(EntityType.SPIDER, level) : new Zombie(EntityType.ZOMBIE, level);
        LivingEntity wrong = bane ? new Zombie(EntityType.ZOMBIE, level) : new Spider(EntityType.SPIDER, level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        Set<UUID> denyDamage = new HashSet<>(); Set<BlockPos> denyBlocks = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denyDamage.contains(entity.getUUID()));
        PlayerBlockBreakEvents.BEFORE.register((world, owner, pos, state, blockEntity) -> !denyBlocks.contains(pos));
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>(); BlockPos mined = origin.offset(-1, 0, -2);
        try {
            for (int x = -2; x <= 5; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 1; z++) {
                BlockPos pos = origin.offset(x, y, z); BlockState state = level.getBlockState(pos);
                require(level.hasChunkAt(pos) && level.isInWorldBounds(pos) && state.isAir(), "Fixture needs loaded disposable open space");
                saved.put(pos, state);
            }
            if (water) for (BlockPos pos : saved.keySet()) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            player.stopUsingItem(); player.setGameMode(GameType.SURVIVAL); for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(-90); player.setXRot(bane ? 15 : 0); player.setOnGround(false); player.fallDistance = 0; speed.setBaseValue(1024);
            for (LivingEntity mob : List.of(target, neighbor, wrong, villager)) {
                if (mob instanceof net.minecraft.world.entity.Mob actual) actual.setNoAi(true);
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); mob.setHealth(200);
            }
            target.setPos(player.getX() + 2, player.getY(), player.getZ()); neighbor.setPos(player.getX() + 4.2, player.getY(), player.getZ());
            wrong.setPos(player.getX() + 2, player.getY(), player.getZ() + 3); villager.setPos(player.getX() + 2, player.getY(), player.getZ() - 3);
            require(level.addFreshEntity(target) && level.addFreshEntity(neighbor) && level.addFreshEntity(wrong) && level.addFreshEntity(villager), "Targets must enter the world");
            refreshWater(player); if (water) require(player.isEyeInFluid(FluidTags.WATER), "Temporary water must reach the player's eyes");
            if (workFirst) reverse(player, pair, water, attack, mined, target, neighbor, wrong, villager, denyDamage, denyBlocks);
            else forward(player, pair, water, attack, mined, target, wrong, villager, denyDamage, denyBlocks);
        } finally {
            denyDamage.clear(); denyBlocks.clear(); MeleeWorkSkills.clearPlayer(player);
            target.discard(); neighbor.discard(); wrong.discard(); villager.discard();
            for (var entry : saved.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.getInventory().setItem(20, oldExtra);
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], old[i]);
            speed.setBaseValue(oldSpeed); player.setGameMode(mode); player.setPos(position); player.setDeltaMovement(velocity);
            player.setYRot(yaw); player.setXRot(pitch); player.setOnGround(ground); player.fallDistance = fall; refreshWater(player);
        }
    }
    private static void forward(ServerPlayer player, FusedPair pair, boolean water, String attack, BlockPos pos,
                                LivingEntity target, LivingEntity wrong, Villager villager, Set<UUID> denyDamage,
                                Set<BlockPos> denyBlocks) throws Exception {
        ServerLevel level = player.level(); ItemStack sword = fused(level, Items.DIAMOND_SWORD, pair); player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        require(!MeleeWorkSkills.activate(player, water), "No hit cannot prepare a mining boost");
        denyDamage.add(target.getUUID()); hit(player, target); denyDamage.clear(); require(value(player, sword, "streak") == 0, "Cancelled hits cannot count");
        hit(player, target); hit(player, target); require(value(player, sword, "streak") == 2, "Two real hits must be recorded");
        hit(player, MeleeAttackThemes.SHARP.equals(attack) ? villager : wrong);
        require(value(player, sword, "streak") == 0, "A protected or wrong-category hit resets the combo");
        hit(player, target); hit(player, target); hit(player, target);
        require(MeleeWorkSkills.activate(player, water) && boosted(player), "Three qualified hits must prepare work once");
        player.getInventory().setItem(20, sword); ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); player.setItemSlot(EquipmentSlot.MAINHAND, pick);
        MeleeWorkSkills.tickServer(level.getServer());
        require(player.getAttribute(Attributes.BLOCK_BREAK_SPEED).hasModifier(EnchantmentFusion.id("melee_work_speed")), "Holding a real tool must enable synced speed");
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); denyBlocks.add(pos);
        require(!player.gameMode.destroyBlock(pos) && boosted(player), "Cancelled mining cannot spend the boost");
        denyBlocks.clear(); require(player.gameMode.destroyBlock(pos), "A real legal break must succeed");
        require(!boosted(player) && !player.getAttribute(Attributes.BLOCK_BREAK_SPEED).hasModifier(EnchantmentFusion.id("melee_work_speed")),
            "Exactly one successful block consumes the speed boost");
    }
    private static void reverse(ServerPlayer player, FusedPair pair, boolean water, String attack, BlockPos pos,
                                LivingEntity target, LivingEntity neighbor, LivingEntity wrong, Villager villager,
                                Set<UUID> denyDamage, Set<BlockPos> denyBlocks) throws Exception {
        ServerLevel level = player.level(); ItemStack gear;
        if (water) {
            gear = fused(level, Items.DIAMOND_HELMET, pair); player.setItemSlot(EquipmentSlot.HEAD, gear);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        } else { gear = fused(level, Items.DIAMOND_PICKAXE, pair); player.setItemSlot(EquipmentSlot.MAINHAND, gear); }
        checkMissingPrimaryAttributes(player, gear, pair, water);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); denyBlocks.add(pos);
        require(!player.gameMode.destroyBlock(pos) && value(player, gear, "cuts") == 0, "Cancelled mining cannot earn a blade");
        denyBlocks.clear(); require(player.gameMode.destroyBlock(pos) && value(player, gear, "cuts") == 1, "A real allowed break earns one edge");
        if (water) player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        hit(player, MeleeAttackThemes.SHARP.equals(attack) ? villager : wrong);
        require(value(player, gear, "cuts") == 1, "A protected or wrong-category hit cannot spend the edge");
        denyDamage.add(target.getUUID()); hit(player, target); denyDamage.clear();
        require(value(player, gear, "cuts") == 1, "Cancelled damage cannot spend the edge");
        float before = neighbor.getHealth(); hit(player, target);
        require(value(player, gear, "cuts") == 0 && neighbor.getHealth() < before, "A real qualified hit releases the short blade");
    }
    private static void checkMissingPrimaryAttributes(ServerPlayer player, ItemStack gear, FusedPair pair, boolean water) {
        var enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), pair.primaryId()).orElseThrow();
        var effects = enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES);
        var attribute = water ? Attributes.SUBMERGED_MINING_SPEED : Attributes.MINING_EFFICIENCY;
        int index = -1; for (int i = 0; i < effects.size(); i++) if (effects.get(i).attribute().equals(attribute)) { index = i; break; }
        require(index >= 0, "The default work primary must provide its native attribute");
        EquipmentSlot slot = water ? EquipmentSlot.HEAD : EquipmentSlot.MAINHAND;
        AttributeModifier base = effects.get(index).getModifier(pair.primaryLevel(), slot);
        Identifier id = EnchantmentFusion.id("primary_attribute/" + pair.primaryId().replace(':', '/') + "/" + slot.getName() + "/" + index);
        AttributeInstance instance = player.getAttribute(attribute);
        AttributeInstance saved = new AttributeInstance(instance.getAttribute(), ignored -> {}); saved.replaceFrom(instance);
        ItemEnchantments enchantments = gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        try {
            gear.remove(DataComponents.ENCHANTMENTS); instance.removeModifier(base.id());
            AttributeModifier conflict = new AttributeModifier(id, 7.0D, AttributeModifier.Operation.ADD_VALUE);
            instance.addTransientModifier(conflict); MeleeWorkSkills.tickServer(player.level().getServer());
            require(conflict.equals(instance.getModifier(id)), "A conflicting modifier must not be overwritten");
            instance.removeModifier(id); MeleeWorkSkills.tickServer(player.level().getServer());
            AttributeModifier projected = instance.getModifier(id);
            require(projected != null && projected.amount() == base.amount() && projected.operation() == base.operation(),
                "A missing native primary must project its actual datapack attribute");
            MeleeWorkSkills.tickServer(player.level().getServer());
            require(projected.equals(instance.getModifier(id)), "Repeated refresh must not stack the projected attribute");
            gear.set(DataComponents.ENCHANTMENTS, enchantments); MeleeWorkSkills.tickServer(player.level().getServer());
            require(instance.getModifier(id) == null, "A native primary must not be projected twice");
        } finally {
            gear.set(DataComponents.ENCHANTMENTS, enchantments); MeleeWorkSkills.clearPlayer(player); instance.replaceFrom(saved);
        }
    }
    private static void hit(ServerPlayer player, LivingEntity target) { target.invulnerableTime = 0; player.resetAttackStrengthTicker(); player.attack(target); }
    private static void refreshWater(Entity entity) throws Exception {
        Method method = Entity.class.getDeclaredMethod("updateFluidInteraction"); method.setAccessible(true); method.invoke(entity);
    }
    private static boolean boosted(ServerPlayer player) throws Exception {
        Field field = MeleeWorkSkills.class.getDeclaredField("BOOSTS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(player.getUUID());
    }
    private static int value(ServerPlayer player, ItemStack stack, String name) throws Exception {
        Field field = MeleeWorkSkills.class.getDeclaredField("STATES"); field.setAccessible(true);
        Object raw = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (!(raw instanceof List<?> states)) return 0;
        for (Object state : states) {
            Field gear = state.getClass().getDeclaredField("gear"); gear.setAccessible(true); Object object = gear.get(state);
            Method getter = object.getClass().getDeclaredMethod("stack"); getter.setAccessible(true);
            if (getter.invoke(object) == stack) { Field value = state.getClass().getDeclaredField(name); value.setAccessible(true); return value.getInt(state); }
        }
        return 0;
    }
    private static ItemStack fused(ServerLevel level, Item item, FusedPair pair) {
        ItemStack stack = new ItemStack(item); FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess()); return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean water, boolean workFirst) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var primary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var secondary = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(secondary, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            for (Item item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HELMET, Items.BOW)) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(item));
                var result = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, direct ? 0 : -1, direct ? 1 : -1);
                boolean supported = !workFirst ? item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.TRIDENT
                    : water ? item == Items.DIAMOND_HELMET : item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL;
                require(supported ? result.executable() : result.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                    "Both station paths must validate the ordered action carrier");
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
