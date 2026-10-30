package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Real integrated-server fixture: old equipment, actual damage events, collision and wake movement. */
final class FlameFusionProbe {
    private static final FusedPair PRESSURE = FusedPair.clean("minecraft:fire_aspect", 2, "minecraft:knockback", 2);
    private static final FusedPair WAKE = PRESSURE.flipped();
    private final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Set<UUID> denied = new HashSet<>();
    private ServerLevel level;
    private ServerPlayer player;
    private Vec3 oldPos;
    private ItemStack oldHand;
    private BlockPos base;
    private Zombie target;
    private Zombie witness;
    private Zombie ally;
    private Cow bystander;
    private net.minecraft.world.scores.PlayerTeam oldTeam;
    private net.minecraft.world.scores.PlayerTeam smokeTeam;
    private double witnessStartX;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private void set(BlockPos pos, BlockState state) {
        saved.putIfAbsent(pos, level.getBlockState(pos));
        level.setBlockAndUpdate(pos, state);
    }

    private Zombie zombie(double x, double z) {
        Zombie zombie = new Zombie(level);
        // NoAI also disables vanilla velocity integration; remove goals instead so
        // the test sees real knockback, collisions and travel without random wandering.
        zombie.removeAllGoals(goal -> true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setPos(base.getX() + x, base.getY(), base.getZ() + z);
        level.addFreshEntity(zombie);
        spawned.add(zombie);
        return zombie;
    }

    private Cow cow(double x, double z) {
        Cow cow = new Cow(EntityType.COW, level);
        cow.setNoAi(true);
        cow.setPos(base.getX() + x, base.getY(), base.getZ() + z);
        level.addFreshEntity(cow);
        spawned.add(cow);
        return cow;
    }

    void start(ServerPlayer sp, BlockPos table) {
        this.level = sp.level();
        this.player = sp;
        this.oldPos = sp.position();
        this.oldHand = sp.getMainHandItem();
        this.base = table.above(45).east(4);
        var scoreboard = level.getScoreboard();
        oldTeam = scoreboard.getPlayersTeam(sp.getScoreboardName());
        smokeTeam = scoreboard.addPlayerTeam("ef_smoke");
        scoreboard.addPlayerToTeam(sp.getScoreboardName(), smokeTeam);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));

        for (int x = -1; x <= 10; x++) for (int z = -3; z <= 3; z++) {
            set(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) set(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
            set(base.offset(4, y, z), Blocks.STONE.defaultBlockState());
        }
        sp.teleportTo(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        sp.setDeltaMovement(Vec3.ZERO);

        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var fire = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
        var knock = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("knockback")));
        var sharp = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness")));
        ItemStack fresh = new ItemStack(Items.DIAMOND_SWORD);
        FusionStationOperations.applyFusedPairToGear(fresh, PRESSURE, level.registryAccess());
        require(fresh.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fire) == 0,
            "New complete fusion must not retain the vanilla primary");
        require(fresh.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(PRESSURE),
            "New complete fusion must retain the pair and both levels");
        ItemStack legacy = new ItemStack(Items.DIAMOND_SWORD);
        legacy.enchant(fire, 2);
        legacy.enchant(sharp, 1);
        legacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PRESSURE));
        require(FusionStationOperations.normalizeRedesignedPrimary(legacy, level.registryAccess()), "Legacy primary must migrate");
        var migrated = legacy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        require(migrated.getLevel(fire) == 0 && migrated.getLevel(sharp) == 1, "Migration must preserve unrelated enchantments");
        require(legacy.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(PRESSURE),
            "Migration must preserve the ordered pair");
        try {
            enchantment_fusion.mechanics.signatures.SignatureCatalog.publish(
                new enchantment_fusion.mechanics.signatures.SignatureCatalog.Snapshot(Map.of(), Map.of(), List.of(), Map.of()));
            require(FusionStationOperations.normalizeRedesignedPrimary(legacy, level.registryAccess()),
                "Removing the redesign mapping must restore the old primary representation");
            require(legacy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fire) == 2,
                "Restoration must preserve the original primary level");
        } finally {
            enchantment_fusion.mechanics.signatures.SignatureCatalog.reloadFromJson(null);
        }
        require(FusionStationOperations.normalizeRedesignedPrimary(legacy, level.registryAccess()),
            "Reloading the redesign must re-normalize existing equipment");
        var inv = new net.minecraft.world.SimpleContainer(4);
        inv.setItem(FusionStationOperations.SLOT_TARGET, legacy);
        inv.setItem(FusionStationOperations.SLOT_CATALYST, new ItemStack(Items.LAPIS_LAZULI));
        require(FusionStationOperations.executeFlipSocket(inv, sp, level.registryAccess(), 0), "Redesigned pair must flip normally");
        require(legacy.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(WAKE),
            "Flip must preserve levels while reversing order");
        require(legacy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(knock) == 0
            && legacy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharp) == 1,
            "Flip must not restore the vanilla primary or remove unrelated enchants");

        target = zombie(1.5D, 0.5D);
        witness = zombie(3.3D, 2.1D);
        ally = zombie(3.0D, 2.9D);
        level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(), smokeTeam);
        bystander = cow(3.3D, -1.2D);
        sp.setItemInHand(InteractionHand.MAIN_HAND, fresh);
        MatrixEffectExecutor.clearCooldowns();
        MatrixEffectExecutor.dispatch(level, sp, fresh, TriggerType.ON_MELEE_STRIKE, target, target.position(), false);
        require(target.hurtServer(level, level.damageSources().playerAttack(sp), 1.0F), "Partial-hit fixture must damage normally");
        require(!target.isOnFire(), "Partial attacks must not activate pressure");
        target.invulnerableTime = 0;
        denied.add(target.getUUID());
        MatrixEffectExecutor.dispatch(level, sp, fresh, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
        require(!target.hurtServer(level, level.damageSources().playerAttack(sp), 1.0F), "Fabric cancellation fixture must reject damage");
        denied.clear();
        target.invulnerableTime = 0;
        MatrixEffectExecutor.dispatch(level, sp, fresh, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
        require(target.hurtServer(level, level.damageSources().playerAttack(sp), 1.0F),
            "A cancelled or partial hit must not consume the next full-hit cooldown");
        require(!target.isOnFire(), "Pressure must wait until the real damage event and server tick");
    }

    void checkPressureAndStartWake() {
        require(target.isOnFire(), "The confirmed full hit must ignite its primary target");
        require(witness.getHealth() < witness.getMaxHealth() && witness.isOnFire(),
            "A genuine wall collision must burst onto a hostile: target=" + target.position()
                + " velocity=" + target.getDeltaMovement() + " collision=" + target.horizontalCollision
                + " witness=" + witness.position() + " health=" + witness.getHealth() + " burning=" + witness.isOnFire());
        require(!ally.isOnFire() && ally.getHealth() == ally.getMaxHealth(), "The burst must not harm allied hostiles");
        require(bystander.getHealth() == bystander.getMaxHealth() && !bystander.isOnFire(), "The burst must not harm passive bystanders");
        require(level.getBlockState(base.offset(4, 1, 0)).is(Blocks.STONE), "Heat pressure must not destroy the wall");
        for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) set(base.offset(4, y, z), Blocks.AIR.defaultBlockState());
        target.discard(); witness.discard(); ally.discard(); bystander.discard();
        target = zombie(1.5D, 0.5D);
        witness = zombie(2.9D, 1.25D);
        ally = zombie(2.4D, -0.25D);
        level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(), smokeTeam);
        bystander = cow(3.5D, -1.0D);
        witnessStartX = witness.getX();
        ItemStack reverse = new ItemStack(Items.DIAMOND_SWORD);
        FusionStationOperations.applyFusedPairToGear(reverse, WAKE, level.registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, reverse);
        MatrixEffectExecutor.clearCooldowns();
        MatrixEffectExecutor.dispatch(level, player, reverse, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
        require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Wake fixture must damage normally");
    }

    void checkWakeAndClose() {
        try {
            require(witness.isOnFire(), "A hostile crossing the real wake must ignite");
            require(witness.getX() > witnessStartX + 0.05D || witness.getDeltaMovement().x > 0.05D,
                "Wake must push the hostile along the target's path");
            require(!ally.isOnFire(), "The wake must not ignite allied hostiles");
            require(!bystander.isOnFire(), "The wake must not ignite passive bystanders");
            for (BlockPos pos : saved.keySet()) {
                require(!level.getBlockState(pos).is(Blocks.FIRE), "Neither signature may place a fire block");
            }
            System.out.println("FIRE FUSION BOUNDARY PASS: migration, cancelled/partial hits, wall collision and directional wake");
        } finally {
            denied.clear();
            spawned.forEach(Entity::discard);
            level.getScoreboard().removePlayerTeam(smokeTeam);
            if (oldTeam != null) level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), oldTeam);
            saved.forEach(level::setBlockAndUpdate);
            player.teleportTo(oldPos.x, oldPos.y, oldPos.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            MatrixEffectExecutor.clearCooldowns();
        }
    }
}
