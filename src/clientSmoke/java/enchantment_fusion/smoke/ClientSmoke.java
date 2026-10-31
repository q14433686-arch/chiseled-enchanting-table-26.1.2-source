package enchantment_fusion.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.gui.FusionGrimoireScreen;
import enchantment_fusion.gui.FusionTooltipRenderer;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * End-to-end real client + integrated server probe for Enchantment Fusion 0.4.1 on MC 26.1.2.
 * Exercises:
 * 1. Localized BlockItem + Fusion Grimoire + Tattered Fusion Page item names.
 * 2. 2-tab 308x214 pixel-art GUI + top-right embedded Fusion Grimoire modal overlay (without closing container).
 * 3. Needed-only Resonance Stabilizer consumption (BAL-03) & zero-stabilizer-burn Flip (CPL-05).
 * 4. API-01 TRANSMUTED_ENCHANTS non-native isolation on Diamond Pickaxe + Shift-expandable transmuted tooltip.
 * 5. 26.1.2 minecraft:lunge + Golden Spear 2-vessel-slot tag rules + Frosted Ice temporary cages + resource reload.
 */
public final class ClientSmoke implements ClientModInitializer {
    private int phase = 0;
    private int ticks = 0;
    private int delay = 0;
    private boolean finished = false;
    private BlockPos tablePos;
    private volatile boolean serverReadyFlag = false;
    private volatile boolean closedRitualFinished = false;
    private volatile Throwable serverFailure = null;
    private CompletableFuture<Void> resourceReloadFuture;
    private final FlameFusionProbe flameProbe = new FlameFusionProbe();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) {
                return;
            }
            try {
                step(client);
            } catch (Throwable err) {
                err.printStackTrace();
                finish(client, "CLIENT SMOKE FAIL: " + err);
            }
        });
    }

    private static void require(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    private static void exerciseRiskFormatting() throws Exception {
        var formatter = EnchantmentFusionScreen.class.getDeclaredMethod("formatPercent", double.class);
        formatter.setAccessible(true);
        require("<0.1".equals(formatter.invoke(null, 0.0006D)), "Tiny nonzero risks must not display as zero");
        require("18".equals(formatter.invoke(null, 0.18D)), "Ordinary risk must remain readable");
        require(">99.9".equals(formatter.invoke(null, 0.9996D)), "Almost-certain risk must not display as 100%");
        require("100".equals(formatter.invoke(null, 1.0D)), "Certain risk must display as 100%");
    }

    private static void exerciseLiveConfig(ServerPlayer sp) throws Exception {
        var server = sp.level().getServer();
        require(enchantment_fusion.config.FusionConfigNetworking.canEdit(server, sp), "Singleplayer owner must be able to configure");
        long initialRevision = enchantment_fusion.config.FusionConfigStore.revision();
        String fingerprint = enchantment_fusion.config.FusionConfigStore.rulesFingerprint();
        var saved = enchantment_fusion.config.FusionConfigStore.update("economy", List.of("low_fail_chance"),
            new com.google.gson.JsonPrimitive(0.12), false, initialRevision);
        require(saved.success(), "Economy config must save");
        require(Math.abs(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().economy().lowFailChance() - 0.12) < 0.0001,
            "Saved economy value must affect the live server snapshot");
        require(!fingerprint.equals(enchantment_fusion.config.FusionConfigStore.rulesFingerprint()), "Rule fingerprint must change");
        Path overridesPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("enchantment_fusion/overrides.json");
        require(Files.isRegularFile(overridesPath) && Files.readString(overridesPath).contains("low_fail_chance"),
            "Config override must persist on disk");
        new enchantment_fusion.data.FusionDataLoader().onResourceManagerReload(server.getResourceManager());
        require(Math.abs(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().economy().lowFailChance() - 0.12) < 0.0001,
            "Persisted overrides must survive a datapack reload");
        require(!enchantment_fusion.config.FusionConfigStore.update("economy", List.of("low_fail_chance"),
            new com.google.gson.JsonPrimitive(0.2), false, initialRevision).success(), "Stale changes must be rejected");
        require(!enchantment_fusion.config.FusionConfigStore.update("economy", List.of("low_fail_chance"),
            new com.google.gson.JsonPrimitive(2.0), false, enchantment_fusion.config.FusionConfigStore.revision()).success(),
            "Out-of-range probability must be rejected");
        require(enchantment_fusion.config.FusionConfigStore.update("economy", List.of("low_fail_chance"),
            null, true, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Economy reset must succeed");

        require(enchantment_fusion.config.FusionConfigStore.update("matrix_profiles", List.of("profiles", "minecraft:loyalty", "trigger"),
            new com.google.gson.JsonPrimitive("ON_MELEE_STRIKE"), false, enchantment_fusion.config.FusionConfigStore.revision()).success(),
            "Trigger override must save");
        require(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().resolve("minecraft:loyalty").trigger()
            == enchantment_fusion.model.TriggerType.ON_MELEE_STRIKE, "Trigger override must affect server snapshot");
        require(enchantment_fusion.config.FusionConfigStore.update("matrix_profiles", List.of("profiles", "minecraft:loyalty", "trigger"),
            null, true, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Trigger reset must succeed");

        var groups = com.google.gson.JsonParser.parseString("[[\"minecraft:sharpness\",\"minecraft:loyalty\"]]");
        require(enchantment_fusion.config.FusionConfigStore.update("repulsive_pairs", List.of("exclusive_groups"), groups,
            false, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Exclusive array override must save");
        require(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().areRepulsiveExclusives("minecraft:sharpness", "minecraft:loyalty"),
            "Exclusive override must affect the live rules");
        require(!enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().areRepulsiveExclusives("minecraft:loyalty", "minecraft:riptide"),
            "Exclusive array override must replace built-ins, not append them");
        require(enchantment_fusion.config.FusionConfigStore.update("repulsive_pairs", List.of("exclusive_groups"),
            null, true, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Exclusive reset must succeed");

        require(enchantment_fusion.config.FusionConfigStore.update("signatures", List.of("signatures", "sanctified_execute", "domain"),
            new com.google.gson.JsonPrimitive("smoke_domain"), false, enchantment_fusion.config.FusionConfigStore.revision()).success(),
            "Signature override must save");
        require("smoke_domain".equals(enchantment_fusion.mechanics.signatures.SignatureCatalog.domainOf("sanctified_execute")),
            "Signature catalog must update immediately");
        require(enchantment_fusion.config.FusionConfigStore.update("signatures", List.of("signatures", "sanctified_execute", "domain"),
            null, true, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Signature reset must succeed");

        List<String> sourcePath = List.of("variants", "auto_smelt_pick", "source");
        require(enchantment_fusion.config.FusionConfigStore.update("transmutations", sourcePath,
            new com.google.gson.JsonPrimitive("breach"), false, enchantment_fusion.config.FusionConfigStore.revision()).success(),
            "Transmutation override must save");
        require("auto_smelt_pick".equals(CrossDomainTransmutations.resolve(new ItemStack(Items.DIAMOND_PICKAXE), "minecraft:breach").orElseThrow().id()),
            "Transmutation catalog must update immediately");
        require(enchantment_fusion.config.FusionConfigStore.update("transmutations", sourcePath,
            null, true, enchantment_fusion.config.FusionConfigStore.revision()).success(), "Transmutation reset must succeed");
        require(fingerprint.equals(enchantment_fusion.config.FusionConfigStore.rulesFingerprint()), "Reset must restore datapack rules");
        String originalFile = Files.readString(overridesPath);
        try {
            Files.writeString(overridesPath, "{\"economy\":{\"melee_min_charge\":0.95,\"melee_full_charge\":0.9}}");
            new enchantment_fusion.data.FusionDataLoader().onResourceManagerReload(server.getResourceManager());
            var eco = enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().economy();
            require(eco.meleeMinCharge() <= eco.meleeFullCharge(), "Conflicting on-disk overrides must not load");
            require(Files.readString(overridesPath).contains("0.95"), "Invalid overrides must be preserved for repair");
        } finally {
            Files.writeString(overridesPath, originalFile);
            new enchantment_fusion.data.FusionDataLoader().onResourceManagerReload(server.getResourceManager());
        }
    }

    private void exerciseFrostAndSneakSmelt(ServerPlayer sp) {
        var level = sp.level();
        var oldHand = sp.getMainHandItem();
        boolean wasSneaking = sp.isShiftKeyDown();
        var originalPos = sp.position();
        var saved = new java.util.LinkedHashMap<BlockPos, BlockState>();
        java.util.function.BiConsumer<BlockPos, BlockState> set = (pos, state) -> {
            saved.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state);
        };
        var spawned = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        try {
            BlockPos seal = tablePos.above(10);
            set.accept(seal, Blocks.AIR.defaultBlockState());
            set.accept(seal.east(), Blocks.WATER.defaultBlockState());
            set.accept(seal.west(), Blocks.LAVA.defaultBlockState());
            ItemStack frostPick = new ItemStack(Items.DIAMOND_PICKAXE);
            frostPick.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:frost_walker", 1));
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, frostPick);
            CrossDomainTransmutations.onAfterBlockBreak(level, sp, seal, Blocks.STONE.defaultBlockState());
            require(level.getBlockState(seal).is(FusionBlocks.TEMPORARY_FROST), "Water must be sealed in the mined air cell");
            require(level.getBlockState(seal.east()).is(Blocks.WATER), "Frost seal must preserve the original water");
            require(level.getBlockState(seal.west()).is(Blocks.COBBLESTONE), "Frost seal must still turn lava into cobblestone");
            level.destroyBlock(seal, false);
            require(level.getBlockState(seal).isAir(), "Broken frost seal must not produce water");
            require(level.getBlockState(seal.east()).is(Blocks.WATER), "Breaking the seal must not remove the original water");

            BlockPos testDrops = tablePos.above(13);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            pick.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:fire_aspect", 2));
            var unrelated = new net.minecraft.world.entity.item.ItemEntity(level, testDrops.getX() + 0.5, testDrops.getY(),
                testDrops.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
            level.addFreshEntity(unrelated); spawned.add(unrelated);
            var raw = new net.minecraft.world.entity.item.ItemEntity(level, testDrops.getX() + 0.5, testDrops.getY(),
                testDrops.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
            var reserved = new net.minecraft.world.entity.item.ItemEntity(level, testDrops.getX() + 0.5, testDrops.getY(),
                testDrops.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
            reserved.setTarget(java.util.UUID.randomUUID());
            try (var capture = enchantment_fusion.mechanics.HarvestDropCapture.begin(level, sp, testDrops)) {
                enchantment_fusion.mechanics.HarvestDropCapture.enterPlayerDestroy(level, sp, testDrops);
                try { level.addFreshEntity(raw); spawned.add(raw); level.addFreshEntity(reserved); spawned.add(reserved); }
                finally { enchantment_fusion.mechanics.HarvestDropCapture.leavePlayerDestroy(level, sp, testDrops); }
                sp.setShiftKeyDown(true);
                enchantment_fusion.mechanics.transmute.MiningFishingTransmutes.onPostDestroyBlockDrops(
                    level, sp, testDrops, Blocks.IRON_ORE.defaultBlockState(), pick);
                require(raw.getItem().is(Items.RAW_IRON), "Sneaking must pause pickaxe auto-smelting");
                sp.setShiftKeyDown(false);
                enchantment_fusion.mechanics.transmute.MiningFishingTransmutes.onPostDestroyBlockDrops(
                    level, sp, testDrops, Blocks.IRON_ORE.defaultBlockState(), pick);
                require(raw.getItem().is(Items.IRON_INGOT), "Pickaxe auto-smelting must resume when not sneaking");
                require(unrelated.getItem().is(Items.RAW_IRON), "Previously existing nearby drops must never be smelted");
                require(reserved.getItem().is(Items.RAW_IRON), "Another player's reserved drop must never be smelted");
                require(!enchantment_fusion.mechanics.HarvestDropCapture.claimTransform(level, testDrops, raw),
                    "The same actual drop must not be transformed twice");
                raw.discard();
            }
            unrelated.discard(); reserved.discard();

            ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
            axe.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:fire_aspect", 2));
            var log = new net.minecraft.world.entity.item.ItemEntity(level, testDrops.getX() + 0.5, testDrops.getY(),
                testDrops.getZ() + 0.5, new ItemStack(Items.OAK_LOG));
            try (var capture = enchantment_fusion.mechanics.HarvestDropCapture.begin(level, sp, testDrops)) {
                enchantment_fusion.mechanics.HarvestDropCapture.enterPlayerDestroy(level, sp, testDrops);
                try { level.addFreshEntity(log); spawned.add(log); }
                finally { enchantment_fusion.mechanics.HarvestDropCapture.leavePlayerDestroy(level, sp, testDrops); }
                sp.setShiftKeyDown(true);
                enchantment_fusion.mechanics.transmute.MiningFishingTransmutes.onPostDestroyBlockDrops(
                    level, sp, testDrops, Blocks.OAK_LOG.defaultBlockState(), axe);
                require(log.getItem().is(Items.OAK_LOG), "Sneaking must pause axe charcoal production");
                sp.setShiftKeyDown(false);
                enchantment_fusion.mechanics.transmute.MiningFishingTransmutes.onPostDestroyBlockDrops(
                    level, sp, testDrops, Blocks.OAK_LOG.defaultBlockState(), axe);
                require(log.getItem().is(Items.CHARCOAL), "Axe charcoal production must resume when not sneaking");
                log.discard();
            }

            BlockPos feet = tablePos.above(16);
            sp.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            BlockPos first = feet.east(), second = feet.east(2), occupied = feet.west(2), lower = feet.south(2).below();
            for (BlockPos top : List.of(first, second, occupied, lower, feet)) {
                set.accept(top, Blocks.AIR.defaultBlockState());
                set.accept(top.below(), Blocks.WATER.defaultBlockState());
            }
            var swimmer = new net.minecraft.world.entity.monster.zombie.Zombie(level);
            swimmer.setPos(occupied.getX() + 0.5, occupied.getY(), occupied.getZ() + 0.5);
            level.addFreshEntity(swimmer); spawned.add(swimmer);
            sp.setShiftKeyDown(true);
            enchantment_fusion.mechanics.TemporaryFrostEffects.placeWake(level, sp, 2);
            require(level.getBlockState(first).isAir(), "Sneaking must pause new ice paths");
            sp.setShiftKeyDown(false);
            enchantment_fusion.mechanics.TemporaryFrostEffects.placeWake(level, sp, 2);
            require(level.getBlockState(first).is(FusionBlocks.TEMPORARY_FROST_PATH), "Wake must place a path above water");
            require(level.getBlockState(second).is(FusionBlocks.TEMPORARY_FROST_PATH), "Wake must extend across adjacent water");
            require(level.getBlockState(lower).is(FusionBlocks.TEMPORARY_FROST_PATH), "Wake must reach water one block below shore");
            require(level.getBlockState(occupied).isAir() && level.getBlockState(feet).isAir(), "Wake must not intersect living entities");
            require(level.getBlockState(first.below()).is(Blocks.WATER), "Wake must preserve water sources");
            double height = level.getBlockState(first).getCollisionShape(level, first).max(net.minecraft.core.Direction.Axis.Y);
            require(Math.abs(height - 0.125) < 1e-6, "Ice path collision must match the two-pixel model");

            // The dive is only allowed to remove our thin path, never the water under it or
            // vanilla ice. These assertions are prepared for the eventual in-game run.
            sp.teleportTo(first.getX() + 0.5, first.getY() + 0.125, first.getZ() + 0.5);
            require(enchantment_fusion.mechanics.TemporaryFrostEffects.removeDivePath(level, sp),
                "Dive must find the thin path in the player's feet cell");
            require(level.getBlockState(first).isAir() && level.getBlockState(first.below()).is(Blocks.WATER),
                "Dive must remove only the thin path, leaving the original water");
            set.accept(first, FusionBlocks.TEMPORARY_FROST_PATH.defaultBlockState());
            sp.teleportTo(first.getX() + 0.5, first.getY() + 1.0, first.getZ() + 0.5);
            require(enchantment_fusion.mechanics.TemporaryFrostEffects.removeDivePath(level, sp),
                "Dive must also find a thin path directly below the player");
            require(level.getBlockState(first).isAir() && level.getBlockState(first.below()).is(Blocks.WATER),
                "Dive below the feet must leave the original water intact");

            BlockPos protectedCell = tablePos.above(20).east(4);
            set.accept(protectedCell.above(), Blocks.AIR.defaultBlockState());
            set.accept(protectedCell.below(), Blocks.AIR.defaultBlockState());
            sp.teleportTo(protectedCell.getX() + 0.5, protectedCell.getY() + 0.125, protectedCell.getZ() + 0.5);
            for (BlockState original : List.of(Blocks.ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(),
                Blocks.BLUE_ICE.defaultBlockState(), Blocks.FROSTED_ICE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), FusionBlocks.TEMPORARY_FROST.defaultBlockState())) {
                set.accept(protectedCell, original);
                require(!enchantment_fusion.mechanics.TemporaryFrostEffects.removeDivePath(level, sp),
                    "Dive must not remove vanilla ice, water, or full-sized temporary frost");
                require(level.getBlockState(protectedCell).equals(original),
                    "Dive must not change any non-path block");
            }
            set.accept(protectedCell, FusionBlocks.TEMPORARY_FROST_PATH.defaultBlockState());
            boolean couldBuild = sp.getAbilities().mayBuild;
            try {
                sp.getAbilities().mayBuild = false;
                require(!enchantment_fusion.mechanics.TemporaryFrostEffects.removeDivePath(level, sp),
                    "Dive must respect a player without build permission");
                require(level.getBlockState(protectedCell).is(FusionBlocks.TEMPORARY_FROST_PATH),
                    "A permission-denied dive must not change the path");
            } finally {
                sp.getAbilities().mayBuild = couldBuild;
            }
            sp.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            set.accept(first, FusionBlocks.TEMPORARY_FROST_PATH.defaultBlockState());
            var walker = new net.minecraft.world.entity.monster.zombie.Zombie(level);
            walker.setPos(first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5);
            level.addFreshEntity(walker); spawned.add(walker);
            walker.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -0.7, 0));
            require(Math.abs(walker.getY() - (first.getY() + 0.125)) < 1e-4, "Entity must land on thin ice collision");
            double beforeX = walker.getX();
            walker.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(1, 0, 0));
            require(walker.getX() > beforeX + 0.9, "Entity must move across adjacent thin-ice tiles");
            level.destroyBlock(first, false);
            require(level.getBlockState(first).isAir() && level.getBlockState(first.below()).is(Blocks.WATER),
                "Breaking thin ice must leave air above the unchanged water");
            ItemStack matrixFrost = new ItemStack(Items.DIAMOND_CHESTPLATE);
            matrixFrost.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
                FusedPair.clean("minecraft:unbreaking", 1, "minecraft:frost_walker", 1)));
            enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
            enchantment_fusion.mechanics.MatrixEffectExecutor.dispatch(level, sp, matrixFrost,
                enchantment_fusion.model.TriggerType.ON_MOVEMENT_OR_LAND, null,
                net.minecraft.world.phys.Vec3.atLowerCornerOf(first));
            require(level.getBlockState(first).is(FusionBlocks.TEMPORARY_FROST_PATH)
                && level.getBlockState(first.below()).is(Blocks.WATER), "Generic CRYO payload must make a water-preserving ice patch");
        } finally {
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
            saved.forEach(level::setBlockAndUpdate);
            sp.teleportTo(originalPos.x, originalPos.y, originalPos.z);
            sp.setShiftKeyDown(wasSneaking);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, oldHand);
        }
    }

    private void exerciseExtraMining(ServerPlayer sp) {
        var level = sp.level();
        var oldMode = sp.gameMode.getGameModeForPlayer();
        ItemStack oldHand = sp.getMainHandItem();
        boolean wasSneaking = sp.isShiftKeyDown();
        var saved = new java.util.LinkedHashMap<BlockPos, BlockState>();
        java.util.function.BiConsumer<BlockPos, BlockState> set = (pos, state) -> {
            saved.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state);
        };
        BlockPos center = tablePos.above(32);
        var side = sp.getDirection().getClockWise();
        var denied = new java.util.HashSet<BlockPos>();
        var observedEvents = new java.util.concurrent.atomic.AtomicInteger();
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player == sp && pos.distManhattan(center) <= 2) observedEvents.incrementAndGet();
            return player != sp || !denied.contains(pos);
        });
        java.util.function.Supplier<ItemStack> pickFactory = () -> {
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            pick.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:multishot", 1));
            pick.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
                FusedPair.clean("minecraft:multishot", 1, "minecraft:efficiency", 2)));
            return pick;
        };
        java.util.function.IntSupplier diamondDrops = () -> level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(center).inflate(4),
            e -> e.isAlive() && e.getItem().is(Items.DIAMOND)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        try {
            sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp.setShiftKeyDown(false);
            ItemStack pick = pickFactory.get();
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);
            for (int offset = -2; offset <= 2; offset++) set.accept(center.relative(side, offset), Blocks.DIAMOND_ORE.defaultBlockState());
            int dropsBefore = diamondDrops.getAsInt();
            require(sp.gameMode.destroyBlock(center), "Center ore must be mined normally");
            require(level.getBlockState(center.relative(side, -1)).isAir() && level.getBlockState(center.relative(side, 1)).isAir(),
                "Signature and transmutation must mine both adjacent blocks");
            require(level.getBlockState(center.relative(side, -2)).is(Blocks.DIAMOND_ORE)
                && level.getBlockState(center.relative(side, 2)).is(Blocks.DIAMOND_ORE), "Area mining must not recurse");
            require(pick.getDamageValue() == 3, "Center and two additional ores must each wear the real tool");
            require(diamondDrops.getAsInt() - dropsBefore >= 3, "Extra ore must use the real tool's loot path");
            require(observedEvents.get() >= 3, "Extra breaks must fire Fabric player-break events");

            for (int offset = -1; offset <= 1; offset++) set.accept(center.relative(side, offset), Blocks.DIAMOND_ORE.defaultBlockState());
            ItemStack protectedPick = pickFactory.get();
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, protectedPick);
            BlockPos protectedPos = center.relative(side, -1);
            denied.add(protectedPos);
            require(sp.gameMode.destroyBlock(center), "Center break must succeed when an adjacent block is denied");
            require(level.getBlockState(protectedPos).is(Blocks.DIAMOND_ORE), "Fabric break cancellation must protect an adjacent block");
            require(level.getBlockState(center.relative(side, 1)).isAir(), "Another permitted adjacent block must still break");
            require(protectedPick.getDamageValue() == 2, "A cancelled extra break must not wear the tool");
            denied.clear();
            require(!enchantment_fusion.mechanics.ExtraBlockHarvest.isActive(), "Cancelled break must release the recursion guard");

            for (int offset = -1; offset <= 1; offset++) set.accept(center.relative(side, offset), Blocks.DIAMOND_ORE.defaultBlockState());
            ItemStack finalUse = pickFactory.get();
            finalUse.setDamageValue(finalUse.getMaxDamage() - 1);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, finalUse);
            dropsBefore = diamondDrops.getAsInt();
            require(sp.gameMode.destroyBlock(center), "Almost-broken tool must still mine the center");
            require(diamondDrops.getAsInt() > dropsBefore, "The center ore must drop before the tool breaks");
            require(level.getBlockState(center.relative(side, -1)).is(Blocks.DIAMOND_ORE)
                && level.getBlockState(center.relative(side, 1)).is(Blocks.DIAMOND_ORE),
                "Additional breaks must stop when the original tool breaks");

            ItemStack chestPick = pickFactory.get();
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, chestPick);
            for (int offset = -1; offset <= 1; offset++) set.accept(center.relative(side, offset), Blocks.CHEST.defaultBlockState());
            require(sp.gameMode.destroyBlock(center), "Center container must be mineable");
            require(level.getBlockState(center.relative(side, -1)).is(Blocks.CHEST)
                && level.getBlockState(center.relative(side, 1)).is(Blocks.CHEST), "Extra mining must skip block entities");

            BlockPos farm = center.above(5);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                set.accept(farm.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState());
                set.accept(farm.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
            }
            var mature = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
            var immature = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2);
            set.accept(farm, mature); set.accept(farm.east(), mature); set.accept(farm.west(), immature);
            ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
            hoe.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:sweeping_edge", 1));
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hoe);
            require(sp.gameMode.destroyBlock(farm), "Center crop must be mineable");
            require(level.getBlockState(farm.east()).isAir(), "Wide reap must harvest adjacent mature crops");
            require(level.getBlockState(farm.west()).is(Blocks.WHEAT), "Wide reap must preserve immature adjacent crops");
        } finally {
            denied.clear();
            saved.forEach(level::setBlockAndUpdate);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, oldHand);
            sp.setShiftKeyDown(wasSneaking);
            sp.setGameMode(oldMode);
        }
    }

    private void exerciseLifelineAnchors(ServerPlayer sp) {
        var level = sp.level();
        var originalPos = sp.position();
        ItemStack oldBoots = sp.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        var state = enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.LAST_SAFE_GROUND;
        var cooldowns = enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.LOYAL_LIFELINE_COOLDOWN;
        var oldAnchor = state.get(sp.getUUID());
        var oldCooldown = cooldowns.get(sp.getUUID());
        BlockPos support = tablePos.above(20), feet = support.above(), head = feet.above(2);
        BlockState oldSupport = level.getBlockState(support), oldFeet = level.getBlockState(feet), oldHead = level.getBlockState(head);
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
            FusedPair.clean("minecraft:protection", 1, "minecraft:loyalty", 1)));
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        var safe = new net.minecraft.world.phys.Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        try {
            cooldowns.remove(sp.getUUID());
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
            state.put(sp.getUUID(), new enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.SafeGround(
                net.minecraft.world.level.Level.NETHER, safe));
            require(enchantment_fusion.mechanics.signatures.ArmorCurseSignatures.allowPlayerDeath(sp, level.damageSources().fellOutOfWorld()),
                "Lifeline must not use an anchor from a different dimension");
            state.put(sp.getUUID(), new enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.SafeGround(level.dimension(), safe));
            level.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
            require(enchantment_fusion.mechanics.signatures.ArmorCurseSignatures.allowPlayerDeath(sp, level.damageSources().fellOutOfWorld()),
                "Lifeline must not use an anchor whose floor was removed");
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(head, Blocks.STONE.defaultBlockState());
            require(enchantment_fusion.mechanics.signatures.ArmorCurseSignatures.allowPlayerDeath(sp, level.damageSources().fellOutOfWorld()),
                "Lifeline must not teleport into a blocked destination");
            level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
            require(!enchantment_fusion.mechanics.signatures.ArmorCurseSignatures.allowPlayerDeath(sp, level.damageSources().fellOutOfWorld()),
                "A safe same-dimension anchor must still save the player");
            require(sp.position().distanceToSqr(safe.add(0, 0.5, 0)) < 1e-6, "Lifeline must return to the anchor");
            require(boots.getDamageValue() == 20, "Successful lifeline must consume 20 durability");
            require(cooldowns.containsKey(sp.getUUID()), "Successful lifeline must start its cooldown");
        } finally {
            level.setBlockAndUpdate(support, oldSupport); level.setBlockAndUpdate(feet, oldFeet); level.setBlockAndUpdate(head, oldHead);
            sp.teleportTo(originalPos.x, originalPos.y, originalPos.z);
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, oldBoots);
            if (oldAnchor == null) state.remove(sp.getUUID()); else state.put(sp.getUUID(), oldAnchor);
            if (oldCooldown == null) cooldowns.remove(sp.getUUID()); else cooldowns.put(sp.getUUID(), oldCooldown);
        }
    }

    private static void exerciseLoyaltyRiptide(ServerPlayer sp) {
        ItemStack trident = new ItemStack(Items.TRIDENT);
        trident.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
            FusedPair.clean("minecraft:loyalty", 3, "minecraft:riptide", 3)));
        enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
        enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();
        var triggers = java.util.EnumSet.of(enchantment_fusion.model.TriggerType.ON_TRIDENT_CHANNEL);
        float previousYaw = sp.getYRot(), previousPitch = sp.getXRot();
        sp.setYRot(0); sp.setXRot(0);
        BlockPos obstacle = BlockPos.containing(sp.getEyePosition().add(0, 0, 2));
        BlockState oldObstacle = sp.level().getBlockState(obstacle);
        sp.level().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
        try {
            // A partial hit must not spend the flying sword's cooldown; its generated
            // hit must not recursively fire another sword, and must recover nearby drops.
            ItemStack qiSword = new ItemStack(Items.DIAMOND_SWORD);
            qiSword.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
                FusedPair.clean("minecraft:sharpness", 3, "minecraft:loyalty", 3)));
            var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(sp.level());
            zombie.setPos(sp.getX(), sp.getY(), sp.getZ() + 1.5D);
            sp.level().addFreshEntity(zombie);
            var drop = new net.minecraft.world.entity.item.ItemEntity(sp.level(), sp.getX() + 0.5D,
                sp.getY() + 1.0D, sp.getZ() + 1.5D, new ItemStack(Items.DIAMOND));
            sp.level().addFreshEntity(drop);
            try {
                enchantment_fusion.mechanics.MatrixEffectExecutor.dispatch(
                    sp.level(), sp, qiSword, enchantment_fusion.model.TriggerType.ON_MELEE_STRIKE, null, sp.position(), false);
                require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 0,
                    "Partial hit must not fire a full-charge-only flying sword");
                enchantment_fusion.mechanics.MatrixEffectExecutor.dispatch(
                    sp.level(), sp, qiSword, enchantment_fusion.model.TriggerType.ON_MELEE_STRIKE, null, sp.position(), true);
                require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 1,
                    "Partial hit must not consume the cooldown for a subsequent charged flying sword");
                float healthBefore = zombie.getHealth();
                enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
                enchantment_fusion.mechanics.FlyingBladeManager.tickServer(sp.level().getServer());
                require(zombie.getHealth() < healthBefore, "The test flying sword must actually hit the zombie");
                require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 1,
                    "A generated flying sword must not recursively fire another sword on impact");
                require(drop.position().distanceToSqr(sp.position().add(0, 0.4D, 0)) < 0.01D,
                    "Flying Sword Qi must return drops collected along its path");
            } finally {
                zombie.discard();
                drop.discard();
                enchantment_fusion.mechanics.FlyingBladeManager.flushAll();
                enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();
                enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
            }
            enchantment_fusion.mechanics.MatrixEffectExecutor.dispatch(sp.level(), sp, trident, triggers, null, sp.position(), false, 0.6F);
            require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 0,
                "A weak swing must not launch a blade or consume the full-charge cooldown");
            enchantment_fusion.mechanics.MatrixEffectExecutor.dispatch(sp.level(), sp, trident, triggers, null, sp.position(), true, 1.0F);
            require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 1,
                "A fully charged Loyalty/Riptide trigger must launch a real visible blade");
            // Simulate an especially short configured cooldown before a block impact. The
            // generated blade must not recursively launch itself on its own impact.
            enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
            enchantment_fusion.mechanics.FlyingBladeManager.tickServer(sp.level().getServer());
            var impactPath = enchantment_fusion.mechanics.ProjectilePathTracker.findLatestPathForOwner(sp.getUUID(), sp.level().dimension()).orElseThrow();
            require(impactPath.isCompleted(), "The test blade must hit the test block");
            require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 1,
                "A water blade must not recursively create another water blade on impact");

            enchantment_fusion.mechanics.FlyingBladeManager.flushAll();
            enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();
            enchantment_fusion.mechanics.MatrixEffectExecutor.clearCooldowns();
            // The actual thrown trident, rather than whatever the player currently holds,
            // carries the fusion. Exercise the real projectile mixin and block impact path.
            ItemStack priorHand = sp.getMainHandItem().copy();
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            try {
                var thrown = new net.minecraft.world.entity.projectile.arrow.ThrownTrident(sp.level(), sp, trident);
                var weaponAccess = (enchantment_fusion.mechanics.ProjectileLaunchWeaponAccess) (Object) thrown;
                require(!weaponAccess.ef$getLaunchWeapon().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty(),
                    "Thrown trident must capture its own fused item even with empty hands");
                thrown.shoot(0, 0, 1, 3.0F, 0.0F);
                sp.level().addFreshEntity(thrown);
                int pathsBeforeImpact = enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount();
                thrown.tick();
                require(enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() > pathsBeforeImpact,
                    "A real fused trident hitting a block must launch the water blade");
                thrown.discard();
            } finally {
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, priorHand);
            }
        } finally {
            sp.level().setBlockAndUpdate(obstacle, oldObstacle);
            enchantment_fusion.mechanics.FlyingBladeManager.flushAll();
            enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();
            sp.setYRot(previousYaw); sp.setXRot(previousPitch);
        }

        ItemStack reverse = new ItemStack(Items.TRIDENT);
        reverse.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(
            FusedPair.clean("minecraft:riptide", 3, "minecraft:loyalty", 3)));
        sp.setShiftKeyDown(false);
        require(!enchantment_fusion.mechanics.signatures.MirageSnapbackSkill.canUse(sp, reverse),
            "Normal use must remain vanilla");
        sp.setShiftKeyDown(true);
        net.minecraft.world.phys.Vec3 anchor = sp.position();
        require(enchantment_fusion.mechanics.signatures.MirageSnapbackSkill.use(sp.level(), sp, reverse),
            "Sneak-use must set the anchor and dash");
        sp.teleportTo(anchor.x + 3.0, anchor.y, anchor.z);
        sp.getCooldowns().removeCooldown(sp.getCooldowns().getCooldownGroup(reverse));
        require(enchantment_fusion.mechanics.signatures.MirageSnapbackSkill.use(sp.level(), sp, reverse),
            "Second sneak-use must return to a safe anchor");
        require(sp.position().distanceToSqr(anchor) < 0.01, "Return must reach the anchor");
        sp.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        sp.setShiftKeyDown(false);
        sp.getCooldowns().removeCooldown(sp.getCooldowns().getCooldownGroup(reverse));
    }

    private static Object configField(enchantment_fusion.gui.FusionConfigScreen screen, String name) throws Exception {
        var field = enchantment_fusion.gui.FusionConfigScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void clickWidget(Screen screen, net.minecraft.client.gui.components.AbstractWidget widget) {
        click(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void click(Screen screen, double x, double y) {
        boolean handled = screen.mouseClicked(
            new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
            false
        );
        require(handled, "Click was not handled at (" + x + ", " + y + ") on " + screen.getClass().getSimpleName());
    }

    private void next(int nextPhase, int waitTicks) {
        this.phase = nextPhase;
        this.delay = waitTicks;
    }

    private void step(Minecraft client) throws Exception {
        if (++ticks > 4000) {
            throw new AssertionError("Client smoke timed out at phase " + phase);
        }
        if (serverFailure != null) {
            throw new AssertionError("Integrated server assertion failed", serverFailure);
        }
        if (delay-- > 0) {
            return;
        }

        switch (phase) {
            case 0 -> {
                if (client.player == null || client.getSingleplayerServer() == null || client.getOverlay() != null) {
                    return;
                }
                for (ItemStack checkStack : List.of(
                    new ItemStack(FusionItems.ENCHANTMENT_FUSION_TABLE_ITEM),
                    new ItemStack(FusionItems.FUSION_GRIMOIRE),
                    new ItemStack(FusionItems.FUSION_TATTERED_PAGE)
                )) {
                    String hover = checkStack.getHoverName().getString();
                    require(!hover.startsWith("item.") && !hover.startsWith("block."), "Unlocalized hover name: " + hover);
                }

                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        sp.giveExperienceLevels(45);
                        sp.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 32));
                        sp.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 32));
                        sp.getInventory().add(new ItemStack(Items.EMERALD, 32));

                        tablePos = sp.blockPosition().offset(1, 0, 0);
                        BlockState state = FusionBlocks.ENCHANTMENT_FUSION_TABLE.defaultBlockState();
                        sp.level().setBlockAndUpdate(tablePos, state);

                        var enchReg = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        Holder<Enchantment> sharp = enchReg.getOrThrow(
                            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness"))
                        );
                        Holder<Enchantment> loyalty = enchReg.getOrThrow(
                            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("loyalty"))
                        );
                        Holder<Enchantment> lunge = enchReg.getOrThrow(
                            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("lunge"))
                        );
                        require(lunge.value().getMaxLevel() == 3, "Expected 26.1.2 minecraft:lunge maxLevel == 3");
                        require(
                            VesselCapacityRules.getMaxVesselSlots(new ItemStack(Items.GOLDEN_SPEAR)) == 2,
                            "Expected Golden Spear to hold 2 innate Vessel Slots via tag"
                        );
                        require(
                            "yishui".equals(CrossDomainTransmutations.resolve(new ItemStack(Items.GOLDEN_SPEAR), "minecraft:aqua_affinity").orElseThrow().id()),
                            "Expected Aqua Affinity on Spear -> yishui"
                        );
                        require(
                            CrossDomainTransmutations.resolve(new ItemStack(Items.GOLDEN_SPEAR), "minecraft:lunge").isEmpty(),
                            "Expected Lunge on Spear to stay native"
                        );

                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        require(be != null, "Missing EnchantmentFusionTableBlockEntity");
                        be.setItem(0, FusionStationOperations.createSingleEnchantedBook(sharp, 5));
                        be.setItem(1, FusionStationOperations.createSingleEnchantedBook(loyalty, 3));
                        be.setItem(2, new ItemStack(FusionItems.RESONANCE_STABILIZER, 8));
                        be.setItem(3, ItemStack.EMPTY);

                        sp.openMenu(state.getMenuProvider(sp.level(), tablePos));
                        sp.containerMenu.broadcastChanges();
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(1, 14);
            }
            case 1 -> {
                if (!serverReadyFlag || !(client.screen instanceof EnchantmentFusionScreen screen)) {
                    return;
                }
                if (screen.getMenu().getSlot(0).getItem().isEmpty() || screen.getMenu().getSlot(1).getItem().isEmpty()) {
                    return;
                }
                Screenshot.grab(
                    client.gameDirectory,
                    "fusion-ui-zh_cn.png",
                    client.getMainRenderTarget(),
                    1,
                    msg -> System.out.println("Saved fusion UI screenshot: " + msg.getString())
                );
                Rect2i b = screen.guiBounds();
                require(b.getWidth() == 304 && b.getHeight() == 218, "Expected compact 304x218 Split-Wing Live-Stage bounds");
                // Open top-right embedded Fusion Grimoire button (279..297, 8..24) — container MUST remain open!
                click(screen, b.getX() + 288, b.getY() + 16);
                require(client.screen instanceof EnchantmentFusionScreen, "Embedded Grimoire must not replace EnchantmentFusionScreen");
                // Leave the overlay open for real rendered frames, rather than closing it in the opening tick.
                next(30, 10);
            }
            case 30 -> {
                require(client.screen instanceof EnchantmentFusionScreen, "Embedded Grimoire must keep the station open");
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                Rect2i b = screen.guiBounds();
                Rect2i book = b;
                click(screen, book.getX() + 32, book.getY() + 40);
                var overlayField = EnchantmentFusionScreen.class.getDeclaredField("grimoireOverlay");
                overlayField.setAccessible(true);
                Object overlay = overlayField.get(screen);
                var queryField = overlay.getClass().getDeclaredField("searchQuery");
                queryField.setAccessible(true);
                screen.charTyped(new net.minecraft.client.input.CharacterEvent(0x1F3AE));
                screen.charTyped(new net.minecraft.client.input.CharacterEvent('A'));
                screen.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0));
                screen.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, 0, 0));
                require("A".equals(queryField.get(overlay)), "Search must delete a whole supplementary Unicode character");
                require(screen.keyPressed(new KeyEvent(256, 0, 0)), "Escape should clear search");
                require(overlayField.get(screen) != null && "".equals(queryField.get(overlay)),
                    "First Escape must clear search without closing the embedded Grimoire");
                require(screen.keyPressed(new KeyEvent(256, 0, 0)), "Second Escape should close embedded Grimoire");
                require(overlayField.get(screen) == null, "Escape must close the overlay, not the station");
                b = screen.guiBounds();

                // Toggle in-panel Synergy Popover badge (253..277, 8..24) open & closed (never expands into JEI/REI!)
                click(screen, b.getX() + 265, b.getY() + 16);
                require(screen.guiBounds().getWidth() == 304, "Synergy popover must stay inside 304px bounds");
                click(screen, b.getX() + 265, b.getY() + 16);

                // Left-Click Central [⇄] Swap Medallion (38..58, 29..49) -> swaps [A ▷ B] order
                click(screen, b.getX() + 48, b.getY() + 39);
                next(2, 10);
            }
            case 2 -> {
                require(client.screen instanceof EnchantmentFusionScreen, "Screen closed unexpectedly");
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                var previewAfterSwap = screen.getMenu().computePreview();
                if (previewAfterSwap.previewPair() == null || !"minecraft:loyalty".equals(previewAfterSwap.previewPair().primaryId())) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 48, b.getY() + 39);
                next(3, 10);
            }
            case 3 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                var preview = screen.getMenu().computePreview();
                if (preview.previewPair() == null || !"minecraft:sharpness".equals(preview.previewPair().primaryId())) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                // Start, close the actual menu, and reopen while the server-owned ritual runs.
                click(screen, b.getX() + 244, b.getY() + 105);
                screen.onClose();
                next(13, 8);
            }
            case 13 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        require(be.hasActiveRitual(), "Closing the UI must not cancel the server ritual");
                        require(!be.canPlaceItem(0, new ItemStack(Items.STONE)), "Inputs must be locked during a ritual");
                        require(be.removeItem(0, 1).isEmpty(), "Items cannot be removed during a ritual");
                        var state = sp.level().getBlockState(tablePos);
                        sp.openMenu(state.getMenuProvider(sp.level(), tablePos));
                        sp.containerMenu.broadcastChanges();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(14, 8);
            }
            case 14 -> {
                if (!serverReadyFlag || !(client.screen instanceof EnchantmentFusionScreen screen)) return;
                if (screen.getMenu().getSlot(3).getItem().is(FusionItems.FUSED_ENCHANTED_BOOK)) {
                    next(4, 2); // The timer already completed while the UI was closed.
                    return;
                }
                if (!screen.getMenu().isRitualRunning()) return;
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 244, b.getY() + 105); // owner can still skip after reopening
                next(4, 14);
            }
            case 4 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        ItemStack fusedBook = be.getItem(3);
                        require(fusedBook.is(FusionItems.FUSED_ENCHANTED_BOOK), "Expected Fused Enchanted Book directly in Slot 3");
                        FusedEnchantmentList list = fusedBook.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        require(list.size() == 1, "Expected 1 fused pair on Fused Enchanted Book");
                        require("minecraft:sharpness".equals(list.pairs().get(0).primaryId()), "Expected Sharpness as Primary A");
                        require("minecraft:loyalty".equals(list.pairs().get(0).secondaryId()), "Expected Loyalty as Secondary B");

                        // Prepare universal-host inscription onto a DIAMOND HELMET!
                        be.setItem(0, fusedBook.copy());
                        be.setItem(3, new ItemStack(Items.DIAMOND_HELMET, 1));
                        sp.containerMenu.broadcastChanges();
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(5, 10);
            }
            case 5 -> {
                if (!serverReadyFlag) {
                    return;
                }
                if (!CompendiumDiscoveryTracker.isClientDiscovered("minecraft:sharpness", "minecraft:loyalty")) {
                    return;
                }
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                if (!screen.getMenu().getSlot(3).getItem().is(Items.DIAMOND_HELMET)) {
                    return;
                }
                // Auto-Morphs automatically to Gear Inscription Mode when Diamond Helmet is in Slot 3!
                next(6, 10);
            }
            case 6 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                if (screen.getMenu().activeTab() != FusionStationOperations.TAB_SOCKET) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 244, b.getY() + 105);
                click(screen, b.getX() + 244, b.getY() + 105);
                next(7, 14);
            }
            case 7 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                ItemStack helmetClient = screen.getMenu().getSlot(3).getItem();
                FusedEnchantmentList socketed = helmetClient.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                if (socketed.isEmpty()) {
                    return;
                }
                int stabilizersBeforeFlip = screen.getMenu().getSlot(2).getItem().getCount();
                require(stabilizersBeforeFlip > 0, "Expected leftover Resonance Stabilizers in Slot 2 before Flip");
                Rect2i b = screen.guiBounds();
                // Click inline [⇄] Flip button on Socket Card 0 (261..276, 28..42)
                click(screen, b.getX() + 268, b.getY() + 35);
                next(8, 14);
            }
            case 8 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        ItemStack helmet = be.getItem(3);
                        require(helmet.is(Items.DIAMOND_HELMET), "Expected Diamond Helmet in Slot 3");
                        FusedEnchantmentList list = helmet.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        require(list.size() == 1, "Expected universal-gear fused pair on Diamond Helmet");
                        require(
                            "minecraft:loyalty".equals(list.pairs().get(0).primaryId()),
                            "Expected inline socket flip to invert pair to [Loyalty ▷ Sharpness]"
                        );
                        // CPL-05: Flip must NEVER consume Resonance Stabilizers sitting in Slot 2!
                        require(be.getItem(2).getCount() == 6, "CPL-05: Flip must not burn Resonance Stabilizers in Slot 2");
                        // API-01: Non-native Loyalty must NOT be placed into vanilla DataComponents.ENCHANTMENTS on Helmet!
                        require(
                            helmet.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                            "API-01: Non-native primary must not pollute vanilla DataComponents.ENCHANTMENTS"
                        );

                        // Prepare Single Cross-Domain Inscription (Fire Aspect II Book -> Diamond Pickaxe = Auto-Smelt II at 0◇!)
                        var enchReg = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        Holder<Enchantment> fireAspect = enchReg.getOrThrow(
                            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect"))
                        );
                        be.setItem(0, FusionStationOperations.createSingleEnchantedBook(fireAspect, 2));
                        be.setItem(1, ItemStack.EMPTY);
                        be.setItem(3, new ItemStack(Items.DIAMOND_PICKAXE, 1));
                        sp.containerMenu.broadcastChanges();
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(9, 12);
            }
            case 9 -> {
                if (!serverReadyFlag) {
                    return;
                }
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                var preview = screen.getMenu().computePreview();
                if (preview.kind() != OperationKind.INSCRIBE_SINGLE_CROSS || !preview.executable()) {
                    return;
                }
                require(
                    preview.previewTransmute() != null && "auto_smelt_pick".equals(preview.previewTransmute().id()),
                    "Expected Auto-Smelt Pickaxe preview for Fire Aspect II on Diamond Pickaxe"
                );
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 244, b.getY() + 105);
                click(screen, b.getX() + 244, b.getY() + 105);
                next(10, 14);
            }
            case 10 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        ItemStack pick = be.getItem(3);
                        require(pick.is(Items.DIAMOND_PICKAXE), "Expected Diamond Pickaxe in Slot 3");
                        FusedEnchantmentList fused = pick.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        require(fused.isEmpty(), "Single Cross-Domain Transmutation must consume 0 Vessel Slots!");

                        // API-01: Fire Aspect II on Pickaxe lives in TRANSMUTED_ENCHANTS, leaving vanilla ENCHANTMENTS empty!
                        ItemEnchantments vanillaEnch = pick.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                        require(vanillaEnch.isEmpty(), "API-01: Cross-domain Fire Aspect must not enter vanilla ENCHANTMENTS on Pickaxe");
                        Map<String, Integer> transMap = pick.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                        require(transMap.getOrDefault("minecraft:fire_aspect", 0) == 2, "Expected Fire Aspect II in TRANSMUTED_ENCHANTS");
                        require(Boolean.TRUE.equals(pick.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "Expected glint override on transmuted Pickaxe");

                        List<Component> lines = new ArrayList<>();
                        lines.add(pick.getHoverName());
                        FusionTooltipRenderer.appendFusionTooltips(pick, sp.level().registryAccess(), lines);
                        require(lines.size() >= 2, "Expected transmuted tooltip line appended");
                        String rendered = lines.get(1).getString();
                        String expectedTitle = Component.translatable("transmute.enchantment_fusion.auto_smelt_pick.title").getString();
                        require(rendered.contains(expectedTitle), "Expected transmuted tooltip title '" + expectedTitle + "', got: " + rendered);

                        BlockPos icePos = sp.blockPosition().above(4);
                        sp.level().setBlockAndUpdate(icePos, Blocks.AIR.defaultBlockState());
                        AberrantRegistry.placeSingleFrostedIce(sp.level(), icePos);
                        require(
                            sp.level().getBlockState(icePos).is(FusionBlocks.TEMPORARY_FROST),
                            "Freezing effects must use the non-water-producing temporary frost block"
                        );
                        sp.level().destroyBlock(icePos, false);
                        require(sp.level().getBlockState(icePos).isAir(), "Broken temporary frost must not become water");
                        BlockPos existingWater = tablePos.above(7).east(4);
                        sp.level().setBlockAndUpdate(existingWater, Blocks.WATER.defaultBlockState());
                        AberrantRegistry.placeSingleFrostedIce(sp.level(), existingWater);
                        require(sp.level().getBlockState(existingWater).is(Blocks.WATER), "Existing water must not be deleted");
                        sp.level().setBlockAndUpdate(existingWater, Blocks.AIR.defaultBlockState());
                        BlockPos meltingIce = tablePos.above(5);
                        sp.level().setBlockAndUpdate(meltingIce, Blocks.AIR.defaultBlockState());
                        AberrantRegistry.placeSingleFrostedIce(sp.level(), meltingIce);
                        sp.level().setBlockAndUpdate(meltingIce, sp.level().getBlockState(meltingIce)
                            .setValue(enchantment_fusion.block.TemporaryFrostBlock.AGE, 3));
                        BlockPos meltingPath = tablePos.above(5).east(3);
                        sp.level().setBlockAndUpdate(meltingPath, FusionBlocks.TEMPORARY_FROST_PATH.defaultBlockState()
                            .setValue(enchantment_fusion.block.TemporaryFrostBlock.AGE, 3));

                        sp.closeContainer();
                        require(be.getItem(3).is(Items.DIAMOND_PICKAXE), "Closing menu must persist pickaxe inside table BlockEntity");

                        ItemStack testSword = new ItemStack(Items.NETHERITE_SWORD, 1);
                        SignaturePairs.executeSignatureCombat(
                            sp.level(), sp, testSword,
                            FusedPair.clean("minecraft:fire_aspect", 2, "minecraft:channeling", 1),
                            "skyfire_lightning", sp, sp.position(), true
                        );
                        SignaturePairs.executeSignatureCombat(
                            sp.level(), sp, testSword,
                            FusedPair.clean("minecraft:lunge", 3, "minecraft:wind_burst", 3),
                            "gale_vault_thrust", sp, sp.position(), true
                        );

                        // 0.5.0: Verify real 3D Display.ItemDisplay Boomerang Flying Blade launch + tick + cleanup
                        ItemStack flyingSword = new ItemStack(Items.DIAMOND_SWORD, 1);
                        flyingSword.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:loyalty", 3));
                        boolean launched = enchantment_fusion.mechanics.FlyingBladeManager.launchReturningBlade(
                            sp.level(), sp, net.minecraft.world.InteractionHand.MAIN_HAND, flyingSword, 3
                        );
                        require(launched, "Expected FlyingBladeManager.launchReturningBlade to succeed");
                        enchantment_fusion.mechanics.FlyingBladeManager.tickServer(server);
                        enchantment_fusion.mechanics.FlyingBladeManager.flushAll();
                        require(
                            enchantment_fusion.mechanics.FlyingBladeManager.canLaunchFor(sp.getUUID()),
                            "Expected FlyingBladeManager.flushAll() to clear active flights"
                        );

                        // 0.5.0: Verify CET Cleanse & Aberrant Purification operations end-to-end on real ItemStacks
                        ItemStack abGear = new ItemStack(Items.DIAMOND_AXE, 1);
                        abGear.set(
                            FusionDataComponents.FUSED_PAIRS,
                            FusedEnchantmentList.EMPTY.append(
                                new FusedPair("minecraft:silk_touch", 1, "minecraft:fortune", 3, "ab_greedy_abyss")
                            )
                        );
                        require(enchantment_fusion.compat.CetCleanseOperations.countAberrantFusions(abGear) == 1, "Expected 1 aberrant fusion");

                        // Verify native Enchantment Fusion Table aberrant purification recipe (Slot 3 + Stabilizers in Slot 2)
                        var bePurify = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        bePurify.setItem(0, ItemStack.EMPTY);
                        bePurify.setItem(1, ItemStack.EMPTY);
                        bePurify.setItem(2, new ItemStack(FusionItems.RESONANCE_STABILIZER, 4));
                        bePurify.setItem(3, abGear.copy());
                        sp.giveExperienceLevels(40);
                        var purifyPreview = FusionStationOperations.inspect(
                            bePurify, sp.level().registryAccess(), FusionStationOperations.TAB_FUSE, -1, -1
                        );
                        require(
                            purifyPreview.kind() == FusionStationOperations.OperationKind.PURIFY_ABERRANT && purifyPreview.executable(),
                            "Expected native PURIFY_ABERRANT preview when aberrant gear is in Slot 3 with Resonance Stabilizers"
                        );
                        var purifyRes = FusionStationOperations.executePrimaryWithReason(
                            bePurify, sp, sp.level().registryAccess(), FusionStationOperations.TAB_FUSE, -1, -1, false, ""
                        );
                        require(purifyRes.success(), "Expected native PURIFY_ABERRANT execution to succeed");
                        ItemStack nativelyPurified = bePurify.getItem(3);
                        require(
                            enchantment_fusion.compat.CetCleanseOperations.countAberrantFusions(nativelyPurified) == 0
                                && enchantment_fusion.compat.CetCleanseOperations.countCleanFusions(nativelyPurified) == 1,
                            "Native Fusion Table purification must strip aberration while preserving pure [A ▷ B]"
                        );
                        require(bePurify.getItem(2).getCount() == 1, "Expected 3 Resonance Stabilizers consumed from Slot 2");

                        ItemStack purifiedGear = enchantment_fusion.compat.CetCleanseOperations.purifySingleAberrant(abGear, 0);
                        require(purifiedGear != null && enchantment_fusion.compat.CetCleanseOperations.countAberrantFusions(purifiedGear) == 0,
                            "Expected aberrant curse purified");
                        require(enchantment_fusion.compat.CetCleanseOperations.countCleanFusions(purifiedGear) == 1,
                            "Purifying aberrant must preserve pure [A ▷ B] fusion");

                        var extractedPair = enchantment_fusion.compat.CetCleanseOperations.extractFusedPairToBook(purifiedGear, 0);
                        require(extractedPair != null && extractedPair.extractedBook().is(enchantment_fusion.registry.FusionItems.FUSED_ENCHANTED_BOOK),
                            "Expected clean fusion extracted onto FUSED_ENCHANTED_BOOK");
                        require(enchantment_fusion.compat.CetCleanseOperations.countCleanFusions(extractedPair.updatedTarget()) == 0,
                            "Expected Vessel Slot freed after extracting fused book");

                        var extractedTrans = enchantment_fusion.compat.CetCleanseOperations.extractTransmutedToBook(
                            pick.copy(), "minecraft:fire_aspect", sp.level().registryAccess()
                        );
                        require(extractedTrans != null && extractedTrans.extractedBook().is(Items.ENCHANTED_BOOK),
                            "Expected transmuted single enchant restored onto vanilla ENCHANTED_BOOK");
                        require(enchantment_fusion.compat.CetCleanseOperations.countTransmutedSingles(extractedTrans.updatedTarget()) == 0,
                            "Expected TRANSMUTED_ENCHANTS emptied after extraction");

                        // 0.6.0: Verify real RecipeType.SMELTING auto-smelt on Fortune-multiplied Raw Iron drops
                        var smeltedIron = CrossDomainTransmutations.resolveSmeltedStack(
                            sp.level(), new ItemStack(Items.RAW_IRON, 3), Blocks.IRON_ORE
                        );
                        require(
                            smeltedIron != null && smeltedIron.result().is(Items.IRON_INGOT) && smeltedIron.result().getCount() == 3,
                            "Expected 3x RAW_IRON to smelt into 3x IRON_INGOT via live RecipeType.SMELTING"
                        );
                        require(
                            CrossDomainTransmutations.resolveSmeltedStack(sp.level(), new ItemStack(Items.IRON_INGOT, 2), Blocks.IRON_ORE) == null,
                            "Already-smelted ingots must not re-smelt"
                        );
                        require(
                            CrossDomainTransmutations.resolveSmeltedStack(null, new ItemStack(Items.DIAMOND), Blocks.IRON_ORE) == null,
                            "An unrelated byproduct must not turn into an ingot merely because iron ore was broken"
                        );

                        // 0.6.0: Verify Vanilla Anvil strictly blocks Cross-Domain Inscription even when Creative instabuild == true
                        boolean savedInstabuild = sp.getAbilities().instabuild;
                        try {
                            sp.getAbilities().instabuild = true;
                            var fireAspectHolder = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
                            net.minecraft.world.inventory.AnvilMenu anvil = new net.minecraft.world.inventory.AnvilMenu(
                                77, sp.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(sp.level(), sp.blockPosition())
                            );
                            anvil.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
                            anvil.getSlot(1).set(FusionStationOperations.createSingleEnchantedBook(fireAspectHolder, 2));
                            anvil.createResult();
                            require(
                                anvil.getSlot(2).getItem().isEmpty(),
                                "Vanilla Anvil must NEVER perform Cross-Domain Inscription even in Creative Mode"
                            );
                        } finally {
                            sp.getAbilities().instabuild = savedInstabuild;
                        }

                        // 0.6.0: Verify Spear preserves bare Right-Click for vanilla Hold-Charge and uses Sneak+Right-Click for active throw
                        ItemStack loyalSpear = new ItemStack(Items.GOLDEN_SPEAR, 1);
                        loyalSpear.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:loyalty", 3));
                        sp.setShiftKeyDown(false);
                        require(
                            !CrossDomainTransmutations.hasReadyRightClickAbility(sp, loyalSpear),
                            "Bare Right-Click on Spear must NOT be intercepted so vanilla Hold-Right-Click charging stays intact!"
                        );
                        sp.setShiftKeyDown(true);
                        require(
                            CrossDomainTransmutations.hasReadyRightClickAbility(sp, loyalSpear),
                            "Sneak + Right-Click on Spear with Loyalty transmutation must trigger returning javelin throw!"
                        );
                        sp.setShiftKeyDown(false);

                        // 0.6.0: Verify SPEAR_CHARGE_LANCE + ProjectilePathTracker trajectory cache & cleanup
                        boolean lanceLaunched = enchantment_fusion.mechanics.FlyingBladeManager.launchLinearPhantomWeapon(
                            sp.level(),
                            sp,
                            net.minecraft.world.InteractionHand.MAIN_HAND,
                            loyalSpear,
                            3,
                            enchantment_fusion.mechanics.FlyingBladeManager.FlightStyle.SPEAR_CHARGE_LANCE
                        );
                        require(lanceLaunched, "Expected SPEAR_CHARGE_LANCE launch to succeed");
                        enchantment_fusion.mechanics.FlyingBladeManager.tickServer(server);
                        require(
                            enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() >= 1,
                            "Expected ProjectilePathTracker to record active flight trajectory"
                        );
                        enchantment_fusion.mechanics.FlyingBladeManager.flushAll();
                        enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();
                        require(
                            enchantment_fusion.mechanics.ProjectilePathTracker.cachedPathCount() == 0,
                            "Expected ProjectilePathTracker.flushAll() to clear cached paths"
                        );

                        // 0.6.0 Overhaul: Verify Bow fan_volley spawns 3 real arrows (2 flanking CREATIVE_ONLY),
                        // sun_piercer sets real Arrow pierceLevel, and swift_draw accelerates draw speed!
                        ItemStack fanBow = new ItemStack(Items.BOW, 1);
                        fanBow.set(
                            FusionDataComponents.TRANSMUTED_ENCHANTS,
                            Map.of("minecraft:multishot", 1, "minecraft:piercing", 2, "minecraft:quick_charge", 3)
                        );
                        require(
                            CrossDomainTransmutations.getEffectiveUseSpeedBoost(fanBow) == 3,
                            "Expected Bow swift_draw III to report 3x draw acceleration boost"
                        );
                        net.minecraft.world.entity.projectile.arrow.Arrow centerArrow = new net.minecraft.world.entity.projectile.arrow.Arrow(
                            sp.level(), sp, new ItemStack(Items.ARROW), fanBow.copyWithCount(1)
                        );
                        centerArrow.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F, 3.0F, 0.0F);
                        sp.level().addFreshEntity(centerArrow);
                        var spawnedArrows = sp.level().getEntitiesOfClass(
                            net.minecraft.world.entity.projectile.arrow.Arrow.class,
                            sp.getBoundingBox().inflate(8.0D),
                            a -> a.isAlive() && a.getOwner() == sp
                        );
                        require(
                            spawnedArrows.size() == 3,
                            "Expected Bow fan_volley to spawn 3 real Arrow entities (1 center + 2 flanking), got " + spawnedArrows.size()
                        );
                        require(
                            centerArrow.getPierceLevel() >= 2,
                            "Expected Bow sun_piercer II to grant real AbstractArrow pierceLevel >= 2, got " + centerArrow.getPierceLevel()
                        );
                        for (var a : spawnedArrows) {
                            a.discard();
                        }
                        enchantment_fusion.mechanics.ProjectilePathTracker.flushAll();

                        exerciseRiskFormatting();
                        exerciseLiveConfig(sp);
                        exerciseFrostAndSneakSmelt(sp);
                        GlazedStrikeProbe.exercise(sp);
                        WaterFireCarrierProbe.exercise(sp);
                        UnderwaterMiningProbe.exercise(sp);
                        FrostFallProbe.exercise(sp, tablePos.above(45).east(8));
                        StationSelectionProbe.exercise(sp);
                        LoyaltyGuardProbe.exercise(sp);
                        ThirdPartyCarrierProbe.exercise(sp);
                        ReplenishProbe.exercise(sp);
                        DroppedItemSafetyProbe.exercise(sp);
                        HarvestProvenanceProbe.exercise(sp, tablePos.above(39));
                        exerciseExtraMining(sp);
                        exerciseLifelineAnchors(sp);
                        exerciseLoyaltyRiptide(sp);
                        // Start another no-risk ritual with no menu open. It must finish on server ticks.
                        var enchantments = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        var sharpness = enchantments.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness")));
                        var knockback = enchantments.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("knockback")));
                        be.setItem(0, FusionStationOperations.createSingleEnchantedBook(sharpness, 1));
                        be.setItem(1, FusionStationOperations.createSingleEnchantedBook(knockback, 1));
                        be.setItem(2, ItemStack.EMPTY);
                        be.setItem(3, ItemStack.EMPTY);
                        var closedPreview = FusionStationOperations.inspect(be, sp.level().registryAccess(), FusionStationOperations.TAB_FUSE, -1, -1);
                        require(closedPreview.executable(), "Closed-menu ritual preview must be executable");
                        require(closedPreview.economyQuote() != null && closedPreview.economyQuote().failureChance() <= 0.0005D,
                            "Closed-menu ritual fixture must be deterministic and have no failure roll");
                        require(be.startRitual(sp, FusionStationOperations.TAB_FUSE, -1, -1, false, closedPreview.expectedExecuteToken()).success(),
                            "Closed-menu ritual must start");
                        require(be.cancelRitual(sp.getUUID()) && !be.hasActiveRitual(), "Owner must be able to cancel without consuming inputs");
                        require(be.startRitual(sp, FusionStationOperations.TAB_FUSE, -1, -1, false, closedPreview.expectedExecuteToken()).success(),
                            "Ritual must restart after cancellation");
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(11, 60);
            }
            case 11 -> {
                if (!serverReadyFlag) return;
                serverReadyFlag = false;
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        if (!be.hasActiveRitual() && sp.level().getBlockState(tablePos.above(5)).isAir()
                            && sp.level().getBlockState(tablePos.above(5).east(3)).isAir()) {
                            require(be.getItem(3).is(FusionItems.FUSED_ENCHANTED_BOOK), "Ritual must complete after closing the UI");
                            require(sp.level().getBlockState(tablePos.above(5)).isAir(), "Melted frost must not produce water");
                            require(sp.level().getBlockState(tablePos.above(5).east(3)).isAir(), "Melted thin ice must not produce water");
                            closedRitualFinished = true;
                        }
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(15, 5);
            }
            case 15 -> {
                if (!serverReadyFlag) return;
                if (!closedRitualFinished) { next(11, 10); return; }
                resourceReloadFuture = client.reloadResourcePacks();
                next(12, 8);
            }
            case 12 -> {
                if (!resourceReloadFuture.isDone() || client.getOverlay() != null) {
                    return;
                }
                resourceReloadFuture.join();
                client.setScreen(new enchantment_fusion.gui.FusionConfigScreen(null));
                next(20, 8);
            }
            case 20 -> {
                if (!(client.screen instanceof enchantment_fusion.gui.FusionConfigScreen screen)
                    || !enchantment_fusion.config.FusionConfigClient.synced()
                    || enchantment_fusion.config.FusionConfigClient.revision() != enchantment_fusion.config.FusionConfigStore.revision()
                    || !enchantment_fusion.config.FusionConfigClient.canEdit()) return;
                @SuppressWarnings("unchecked")
                var rows = (List<net.minecraft.client.gui.components.Button>) configField(screen, "rows");
                require(!rows.isEmpty() && rows.getFirst().visible, "Config UI must show editable fields");
                clickWidget(screen, rows.getFirst());
                next(21, 4);
            }
            case 21 -> {
                var screen = (enchantment_fusion.gui.FusionConfigScreen) client.screen;
                var input = (net.minecraft.client.gui.components.EditBox) configField(screen, "valueBox");
                input.setValue("0.12");
                var save = (net.minecraft.client.gui.components.Button) configField(screen, "saveButton");
                require(save.active, "Singleplayer owner must be able to save in the config UI");
                clickWidget(screen, save);
                next(22, 8);
            }
            case 22 -> {
                if (Math.abs(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().economy().lowFailChance() - 0.12) > 0.0001
                    || enchantment_fusion.config.FusionConfigClient.revision() != enchantment_fusion.config.FusionConfigStore.revision()) return;
                next(23, 4);
            }
            case 23 -> {
                var screen = (enchantment_fusion.gui.FusionConfigScreen) client.screen;
                var reset = (net.minecraft.client.gui.components.Button) configField(screen, "resetButton");
                require(reset.active, "Saved override must be resettable in the UI");
                clickWidget(screen, reset);
                next(24, 8);
            }
            case 24 -> {
                if (Math.abs(enchantment_fusion.data.FusionMatrixRegistry.serverSnapshot().economy().lowFailChance()) > 0.0001
                    || enchantment_fusion.config.FusionConfigClient.revision() != enchantment_fusion.config.FusionConfigStore.revision()) return;
                client.screen.onClose();
                require(client.options.getMenuBackgroundBlurriness() >= 1,
                    "Standalone Grimoire regression requires menu background blur to be enabled");
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        require(FusionItems.FUSION_GRIMOIRE.use(sp.level(), sp, net.minecraft.world.InteractionHand.MAIN_HAND)
                            == net.minecraft.world.InteractionResult.SUCCESS, "Using the Grimoire must succeed");
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(25, 1);
            }
            case 25 -> {
                // The normal item-use packet must open the standalone screen on the real client.
                if (!serverReadyFlag || !(client.screen instanceof FusionGrimoireScreen)) return;
                next(26, 12);
            }
            case 26 -> {
                require(client.screen instanceof FusionGrimoireScreen, "Standalone Grimoire must remain open after rendering");
                var screen = (FusionGrimoireScreen) client.screen;
                var leftField = FusionGrimoireScreen.class.getDeclaredField("bookLeft");
                var topField = FusionGrimoireScreen.class.getDeclaredField("bookTop");
                var chapterField = FusionGrimoireScreen.class.getDeclaredField("activeChapter");
                leftField.setAccessible(true);
                topField.setAccessible(true);
                chapterField.setAccessible(true);
                int left = leftField.getInt(screen);
                int top = topField.getInt(screen);
                click(screen, left + 24 + FusionGrimoireScreen.CHAPTER_TAB_W + 4 + 6, top - 15 + 8);
                require(chapterField.get(screen) == FusionGrimoireScreen.Chapter.SIGNATURES,
                    "Standalone Grimoire chapter tabs must remain clickable");
                next(27, 10);
            }
            case 27 -> {
                require(client.screen instanceof FusionGrimoireScreen, "Standalone Grimoire must render after changing chapters");
                require(client.screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)),
                    "Escape must close the standalone Grimoire");
                require(client.screen == null, "Standalone Grimoire must return to the world");
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.start(server.getPlayerList().getPlayers().getFirst(), tablePos);
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(40, 1);
            }
            case 40 -> {
                if (!serverReadyFlag) return;
                // Wait for actual entity movement/collisions, rather than simulating ticks.
                next(41, 35);
            }
            case 41 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkPressureAndStartWake();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(42, 1);
            }
            case 42 -> {
                if (!serverReadyFlag) return;
                next(43, 35);
            }
            case 43 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkWakeBeforeExpiry();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(44, 1);
            }
            case 44 -> {
                if (!serverReadyFlag) return;
                next(45, 65);
            }
            case 45 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.spawnAfterWakeExpiry();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(46, 1);
            }
            case 46 -> {
                if (!serverReadyFlag) return;
                next(47, 8);
            }
            case 47 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkExpiryAndStartOpenPressure();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(48, 1);
            }
            case 48 -> {
                if (!serverReadyFlag) return;
                next(49, 50);
            }
            case 49 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkOpenAndStartTeleportWake();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(50, 1);
            }
            case 50 -> {
                if (!serverReadyFlag) return;
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.teleportWakeTarget();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(51, 1);
            }
            case 51 -> {
                if (!serverReadyFlag) return;
                next(52, 12);
            }
            case 52 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkTeleportAndStartShield();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(53, 1);
            }
            case 53 -> {
                if (!serverReadyFlag) return;
                next(54, 10);
            }
            case 54 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkShieldAndStrike();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(55, 1);
            }
            case 55 -> {
                if (!serverReadyFlag) return;
                next(56, 5);
            }
            case 56 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        flameProbe.checkShieldAndClose();
                        serverReadyFlag = true;
                    } catch (Throwable t) { serverFailure = t; }
                });
                next(57, 1);
            }
            case 57 -> {
                if (!serverReadyFlag) return;
                finish(client, "CLIENT SMOKE PASS: rituals, frost, live configuration, Grimoire and redesigned fire/knockback");
            }
            default -> throw new AssertionError("Unknown phase " + phase);
        }
    }

    private void finish(Minecraft client, String message) {
        finished = true;
        System.out.println(message);
        try {
            Files.writeString(Path.of("client-smoke-result.txt"), message + "\n");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        client.stop();
    }
}
