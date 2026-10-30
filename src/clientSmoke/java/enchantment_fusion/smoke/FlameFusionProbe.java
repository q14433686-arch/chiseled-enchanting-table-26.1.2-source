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
    private ItemStack oldInventorySlot;
    private ItemStack queuedLegacy;
    private net.minecraft.core.Holder<Enchantment> fireHolder;
    private net.minecraft.core.Holder<Enchantment> sharpHolder;
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

        for (int x = -1; x <= 13; x++) for (int z = -3; z <= 3; z++) {
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
        fireHolder = fire;
        sharpHolder = sharp;
        oldInventorySlot = sp.getInventory().getItem(9);
        queuedLegacy = new ItemStack(Items.DIAMOND_SWORD);
        queuedLegacy.enchant(fire, 2);
        queuedLegacy.enchant(sharp, 1);
        queuedLegacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PRESSURE));
        sp.getInventory().setItem(9, queuedLegacy);
        exercisePvpFilter();
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

        exerciseStationMaintenance(sp, fire, knock, sharp);
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
        float oldHealth = target.getHealth();
        require(sp.getAttackStrengthScale(0.5F) >= 0.9F, "The real attack fixture must be fully charged");
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(
            sp, level, InteractionHand.MAIN_HAND, target, null);
        sp.attack(target);
        require(target.getHealth() < oldHealth, "The real player attack must deal damage");
        require(!target.isOnFire(), "Pressure must wait until the real damage event and server tick");
    }

    private void exerciseStationMaintenance(ServerPlayer sp, net.minecraft.core.Holder<Enchantment> fire,
                                            net.minecraft.core.Holder<Enchantment> knock,
                                            net.minecraft.core.Holder<Enchantment> sharp) {
        sp.giveExperienceLevels(40);
        FusedPair low = FusedPair.clean("minecraft:fire_aspect", 1, "minecraft:knockback", 1);
        for (FusedPair pair : List.of(low, low.flipped())) {
            var primary = pair.primaryId().equals("minecraft:fire_aspect") ? fire : knock;
            ItemStack gear = new ItemStack(Items.DIAMOND_SWORD);
            gear.enchant(primary, 1); // Legacy mirror must also be removed by the operation.
            gear.enchant(sharp, 1);
            gear.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(pair));
            var inv = new net.minecraft.world.SimpleContainer(4);
            inv.setItem(FusionStationOperations.SLOT_TARGET, gear);
            inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(primary, 2));
            var preview = FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1);
            require(preview.kind() == FusionStationOperations.OperationKind.TOP_UP_LEVEL, "Expected real fusion level top-up");
            require(FusionStationOperations.executePrimaryOperation(inv, sp, level.registryAccess(),
                FusionStationOperations.TAB_SOCKET, -1, -1), "Primary top-up must succeed");
            require(gear.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().primaryLevel() == 2,
                "Top-up must retain the upgraded fused level");
            require(gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) == 0
                && gear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharp) == 1,
                "Top-up must not restore a redesigned vanilla primary or remove other enchantments");
        }
        ItemStack unreviewed = new ItemStack(Items.DIAMOND_SWORD);
        FusionStationOperations.applyFusedPairToGear(unreviewed,
            FusedPair.clean("minecraft:sharpness", 1, "minecraft:knockback", 1), level.registryAccess());
        var otherInv = new net.minecraft.world.SimpleContainer(4);
        otherInv.setItem(FusionStationOperations.SLOT_TARGET, unreviewed);
        otherInv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(sharp, 2));
        require(FusionStationOperations.executePrimaryOperation(otherInv, sp, level.registryAccess(),
            FusionStationOperations.TAB_SOCKET, -1, -1), "Unreviewed fusion top-up must succeed");
        require(unreviewed.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharp) == 2,
            "Unreviewed pairs must keep their native primary after top-up");
        ItemStack cursed = new ItemStack(Items.DIAMOND_SWORD);
        cursed.enchant(fire, 2);
        cursed.enchant(sharp, 1);
        cursed.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
            new FusedPair("minecraft:fire_aspect", 2, "minecraft:knockback", 2, "ab_greedy_abyss")));
        var purify = new net.minecraft.world.SimpleContainer(4);
        purify.setItem(FusionStationOperations.SLOT_TARGET, cursed);
        purify.setItem(FusionStationOperations.SLOT_CATALYST,
            new ItemStack(enchantment_fusion.registry.FusionItems.RESONANCE_STABILIZER, 4));
        require(FusionStationOperations.inspect(purify, level.registryAccess(), FusionStationOperations.TAB_FUSE, -1, -1).kind()
            == FusionStationOperations.OperationKind.PURIFY_ABERRANT, "Expected real aberrant purification");
        require(FusionStationOperations.executePrimaryOperation(purify, sp, level.registryAccess(),
            FusionStationOperations.TAB_FUSE, -1, -1), "Purification must succeed");
        require(!cursed.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().isAberrant(),
            "Purification must clear the aberration");
        require(cursed.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(fire) == 0
            && cursed.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharp) == 1,
            "Purification must not restore the vanilla primary or remove unrelated enchantments");
    }

    private void exercisePvpFilter() {
        // A second server-side Player instance exercises the actual PvP/game-rule and
        // scoreboard branches without claiming to simulate a second network client.
        var server = level.getServer();
        var other = new ServerPlayer(server, level,
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "EfProbe"),
            net.minecraft.server.level.ClientInformation.createDefault());
        other.getAbilities().invulnerable = false;
        var rules = level.getGameRules();
        boolean oldPvp = level.isPvpAllowed();
        try {
            var canAffect = enchantment_fusion.mechanics.signatures.FlameKineticEffects.class.getDeclaredMethod(
                "canAffect", ServerPlayer.class, net.minecraft.world.entity.LivingEntity.class, boolean.class);
            canAffect.setAccessible(true);
            rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, false, server);
            require(!(boolean) canAffect.invoke(null, player, other, false), "PvP-disabled players must be excluded");
            rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, true, server);
            require((boolean) canAffect.invoke(null, player, other, false), "A permitted hostile player must be eligible");
            level.getScoreboard().addPlayerToTeam(other.getScoreboardName(), smokeTeam);
            require(!(boolean) canAffect.invoke(null, player, other, false), "Allied players must remain excluded");
            level.getScoreboard().removePlayerFromTeam(other.getScoreboardName());
            other.getAbilities().invulnerable = true;
            require(!(boolean) canAffect.invoke(null, player, other, false), "Creative/invulnerable players must be excluded");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("PvP filter fixture failed", ex);
        } finally {
            rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, oldPvp, server);
        }
    }

    void checkPressureAndStartWake() {
        var auto = queuedLegacy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        require(auto.getLevel(fireHolder) == 0 && auto.getLevel(sharpHolder) == 1,
            "Legacy equipment in the player inventory must migrate automatically, preserving other enchantments");
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

    void checkWakeBeforeExpiry() {
        require(witness.isOnFire(), "A hostile crossing the real wake must ignite");
        require(witness.getX() > witnessStartX + 0.05D || witness.getDeltaMovement().x > 0.05D,
            "Wake must push the hostile along the target's path");
        require(!ally.isOnFire(), "The wake must not ignite allied hostiles");
        require(!bystander.isOnFire(), "The wake must not ignite passive bystanders");
        witness.discard(); ally.discard(); bystander.discard();
    }

    void spawnAfterWakeExpiry() {
        // The client has waited long enough for even the last segment to expire.
        witness = zombie(2.9D, 1.25D);
    }

    void checkExpiryAndStartOpenPressure() {
        require(!witness.isOnFire(), "A wake must expire rather than ignite a late arrival forever");
        target.discard(); witness.discard();
        MatrixEffectExecutor.clearCooldowns();
        target = zombie(1.5D, 0.5D);
        witness = zombie(3.3D, 2.1D);
        ItemStack pressure = new ItemStack(Items.DIAMOND_SWORD);
        FusionStationOperations.applyFusedPairToGear(pressure, PRESSURE, level.registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, pressure);
        MatrixEffectExecutor.dispatch(level, player, pressure, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
        require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Open-space pressure fixture must damage normally");
    }

    void checkOpenAndStartTeleportWake() {
        require(target.isOnFire(), "Open-space pressure should still ignite the primary target");
        require(!witness.isOnFire() && witness.getHealth() == witness.getMaxHealth(),
            "Without a wall collision, pressure must not produce an area burst");
        target.discard(); witness.discard();
        MatrixEffectExecutor.clearCooldowns();
        target = zombie(1.5D, 0.5D);
        witness = zombie(8.5D, 0.5D);
        ItemStack reverse = new ItemStack(Items.DIAMOND_SWORD);
        FusionStationOperations.applyFusedPairToGear(reverse, WAKE, level.registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, reverse);
        MatrixEffectExecutor.dispatch(level, player, reverse, TriggerType.ON_MELEE_STRIKE, target, target.position(), true);
        require(target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Teleport-wake fixture must damage normally");
    }

    void teleportWakeTarget() {
        require(target.getX() < base.getX() + 7.0D, "Teleport fixture must jump before the target reaches the gap");
        target.teleportTo(base.getX() + 12.5D, base.getY(), base.getZ() + 0.5D);
        target.setDeltaMovement(Vec3.ZERO);
    }

    void checkTeleportAndClose() {
        try {
            require(!witness.isOnFire(), "A teleport must not create a burning corridor across the gap");
            for (BlockPos pos : saved.keySet()) {
                require(!level.getBlockState(pos).is(Blocks.FIRE), "Neither signature may place a fire block");
            }
            System.out.println("FIRE FUSION BOUNDARY PASS: migration, real/cancelled/partial hits, PvP, allies, wall/no-wall, expiry and teleport");
        } finally {
            denied.clear();
            spawned.forEach(Entity::discard);
            level.getScoreboard().removePlayerTeam(smokeTeam);
            if (oldTeam != null) level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), oldTeam);
            saved.forEach(level::setBlockAndUpdate);
            player.teleportTo(oldPos.x, oldPos.y, oldPos.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            player.getInventory().setItem(9, oldInventorySlot);
            MatrixEffectExecutor.clearCooldowns();
        }
    }

}
