package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.model.FusedPair;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Synchronous disposable-world probes, not a replacement for real input or multiplayer. */
final class HeavyCrushProbe {
    private static final FusedPair FORWARD = FusedPair.clean("minecraft:density", 3, "minecraft:breach", 2);
    private static final FusedPair REVERSE = FORWARD.flipped();
    private HeavyCrushProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) {
        FusedPair pair = reverse ? REVERSE : FORWARD;
        ServerLevel level = player.level();
        checkCarriers(level, pair);
        Vec3 savedPos = player.position();
        Vec3 savedVelocity = player.getDeltaMovement();
        double savedFall = player.fallDistance;
        GameType savedMode = player.gameMode.getGameModeForPlayer();
        ItemStack savedMain = player.getMainHandItem(), savedOff = player.getOffhandItem();
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double savedAttackSpeed = attackSpeed.getBaseValue(), savedAttackDamage = attackDamage.getBaseValue();
        double savedMoveSpeed = moveSpeed.getValue();
        Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        Set<BlockPos> denied = new HashSet<>();
        AABB area = new AABB(origin).inflate(12.0D);
        Set<UUID> beforeItems = new HashSet<>(), beforeOrbs = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) beforeItems.add(item.getUUID());
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) beforeOrbs.add(orb.getUUID());
        Zombie target = new Zombie(level);
        Arrow projectile = null, restored = null;
        PlayerBlockBreakEvents.BEFORE.register((world, breaker, pos, state, blockEntity) ->
            breaker != player || world != level || !denied.contains(pos));
        try {
            // Refuse to overwrite any existing block, including a block entity.
            for (int x = -4; x <= 7; x++) for (int y = -3; y <= 5; y++) for (int z = -4; z <= 4; z++) {
                BlockPos pos = origin.offset(x, y, z);
                require(level.isInWorldBounds(pos) && level.hasChunkAt(pos) && level.getBlockState(pos).isAir(),
                    "Heavy-crush fixture needs a loaded empty disposable volume");
                savedBlocks.put(pos, level.getBlockState(pos));
            }
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y >= -1; y--) {
                level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.DIRT.defaultBlockState());
            }
            BlockPos container = origin.north(), wet = origin.east(), water = origin.east(2), blocked = origin.west();
            level.setBlockAndUpdate(container, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            denied.add(blocked);

            player.setGameMode(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.teleportTo(origin.getX() - 1.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO);
            target.setNoAi(true);
            target.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            target.setHealth(200.0F);
            require(level.addFreshEntity(target), "Heavy-crush target must enter the disposable world");

            ItemStack forward = fused(level, new ItemStack(Items.MACE), FORWARD);
            ItemStack backward = fused(level, new ItemStack(Items.MACE), REVERSE);
            DamageSource source = level.damageSources().playerAttack(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, forward);
            float armorForward = CombatRules.getDamageAfterAbsorb(target, 10.0F, source, 20.0F, 0.0F);
            float fallForward = EnchantmentHelper.modifyFallBasedDamage(level, forward, target, source, 0.0F);
            player.setItemSlot(EquipmentSlot.MAINHAND, backward);
            float armorReverse = CombatRules.getDamageAfterAbsorb(target, 10.0F, source, 20.0F, 0.0F);
            float fallReverse = EnchantmentHelper.modifyFallBasedDamage(level, backward, target, source, 0.0F);
            require(Math.abs(armorForward - armorReverse) < 0.0001F && Math.abs(armorForward - 7.75F) < 0.01F,
                "Both orientations must produce the same Breach and Density armor reduction exactly once");
            require(Math.abs(fallForward - fallReverse) < 0.0001F && Math.abs(fallForward - 1.5F) < 0.01F,
                "Both orientations must preserve the datapack Density smash bonus once");

            ItemStack mace = reverse ? backward : forward;
            player.setItemSlot(EquipmentSlot.MAINHAND, mace);
            HeavyCrushSkill.tickPlayer(player);
            require(moveSpeed.getValue() < savedMoveSpeed, "Held melee weapon must slow actual movement speed");
            attackSpeed.setBaseValue(1024.0D);
            attackDamage.setBaseValue(14.0D);
            player.resetAttackStrengthTicker();
            player.fallDistance = 2.0D;
            float healthBefore = target.getHealth();
            player.attack(target);
            require(target.getHealth() < healthBefore - 6.0F && target.hasEffect(MobEffects.SLOWNESS),
                "A real hard melee hit must inflict damage and the slowing effect");
            HeavyCrushSkill.tickServer(level.getServer());
            require(level.getBlockState(origin).isAir() && level.getBlockState(origin.below()).isAir(),
                "Qualified damage and impact must break both center layers using the current tool");
            require(level.getBlockState(container).is(Blocks.CHEST) && level.getBlockState(blocked).is(Blocks.DIRT)
                && level.getBlockState(wet).is(Blocks.DIRT) && level.getBlockState(water).is(Blocks.WATER),
                "The crater must preserve block entities, denied blocks and original adjacent water");
            require(target.getY() < origin.getY(), "A surviving target must be pressed only into a collision-free pit");
            require(mace.getDamageValue() > 0, "Real per-block harvesting must consume the real main-hand tool");

            ItemStack bow = fused(level, new ItemStack(Items.BOW), pair);
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
            projectile = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
            projectile.shoot(1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
            require(Math.abs(projectile.getGravity() - 0.075D) < 0.0001D,
                "A real fired arrow must use increased gravity");
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            projectile.saveWithoutId(output);
            restored = new Arrow(EntityType.ARROW, level);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
            require(Math.abs(restored.getGravity() - 0.075D) < 0.0001D,
                "Projectile gravity must survive serialization");
            ItemStack crossbow = fused(level, new ItemStack(Items.CROSSBOW), pair);
            int normalCharge = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), player);
            int heavyCharge = CrossbowItem.getChargeDuration(crossbow, player);
            require(heavyCharge == (int) Math.ceil(normalCharge * 1.5D),
                "The fused crossbow must take longer to load");
        } finally {
            denied.clear();
            if (projectile != null) projectile.discard();
            if (restored != null) restored.discard();
            HeavyCrushSkill.onUnload(target);
            target.discard();
            HeavyCrushSkill.onDisconnect(player);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) if (!beforeItems.contains(item.getUUID())) item.discard();
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) if (!beforeOrbs.contains(orb.getUUID())) orb.discard();
            for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            attackSpeed.setBaseValue(savedAttackSpeed); attackDamage.setBaseValue(savedAttackDamage);
            player.setItemSlot(EquipmentSlot.MAINHAND, savedMain); player.setItemSlot(EquipmentSlot.OFFHAND, savedOff);
            player.setGameMode(savedMode); player.teleportTo(savedPos.x, savedPos.y, savedPos.z);
            player.setDeltaMovement(savedVelocity); player.fallDistance = savedFall;
        }
    }

    private static ItemStack fused(ServerLevel level, ItemStack item, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(item, pair, level.registryAccess());
        return item;
    }
    private static void checkCarriers(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : List.of(false, true)) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int firstIndex = direct ? 0 : -1, secondIndex = direct ? 1 : -1;
            for (ItemStack item : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE),
                new ItemStack(Items.DIAMOND_SPEAR), new ItemStack(Items.MACE), new ItemStack(Items.TRIDENT),
                new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW))) {
                inv.setItem(FusionStationOperations.SLOT_TARGET, item);
                require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET,
                    firstIndex, secondIndex).executable(), "Both station paths must accept each weapon action");
            }
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.IRON_CHESTPLATE));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET,
                firstIndex, secondIndex).rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Both station paths must reject armor for this weapon-only ability");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
