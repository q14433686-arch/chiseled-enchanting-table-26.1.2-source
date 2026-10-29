package enchantment_fusion.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.gui.EnchantmentFusionScreen;
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
    private volatile Throwable serverFailure = null;
    private CompletableFuture<Void> resourceReloadFuture;

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
                // Open top-right embedded Fusion Grimoire button — container MUST remain open!
                click(screen, b.getX() + 266, b.getY() + 9);
                require(client.screen instanceof EnchantmentFusionScreen, "Embedded Grimoire must not replace EnchantmentFusionScreen");
                boolean escHandled = screen.keyPressed(new KeyEvent(256, 0, 0));
                require(escHandled, "Escape should close embedded Grimoire overlay cleanly");

                // Click [⇅] Swap-Order button (57..75, 51..69)
                click(screen, b.getX() + 66, b.getY() + 60);
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
                click(screen, b.getX() + 66, b.getY() + 60);
                next(3, 10);
            }
            case 3 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                var preview = screen.getMenu().computePreview();
                if (preview.previewPair() == null || !"minecraft:sharpness".equals(preview.previewPair().primaryId())) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                // Click primary execute button (237..298, 97..121)
                click(screen, b.getX() + 267, b.getY() + 108);
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
                Rect2i b = screen.guiBounds();
                // Switch to Tab 1 (② 器魂铭刻, 120..224, 0..20)
                click(screen, b.getX() + 172, b.getY() + 10);
                next(6, 10);
            }
            case 6 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                if (screen.getMenu().activeTab() != FusionStationOperations.TAB_SOCKET) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 267, b.getY() + 108);
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
                // Click inline [⇄] Flip button on Socket Card 0 (246..268, 37..54)
                click(screen, b.getX() + 257, b.getY() + 45);
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
                click(screen, b.getX() + 267, b.getY() + 108);
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
                            sp.level().getBlockState(icePos).is(Blocks.FROSTED_ICE),
                            "Freezing effects must place vanilla Blocks.FROSTED_ICE"
                        );

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

                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(11, 28);
            }
            case 11 -> {
                if (!serverReadyFlag) {
                    return;
                }
                resourceReloadFuture = client.reloadResourcePacks();
                next(12, 8);
            }
            case 12 -> {
                if (!resourceReloadFuture.isDone() || client.getOverlay() != null) {
                    return;
                }
                resourceReloadFuture.join();
                finish(
                    client,
                    "CLIENT SMOKE PASS: 0.6.0 auto-smelt + anvil guard + Spear multi-trigger + trajectory path cache + 174 signatures + CET cleanse"
                );
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
