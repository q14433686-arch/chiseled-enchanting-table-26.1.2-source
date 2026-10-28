package enchantment_fusion.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;

/**
 * End-to-end real client + integrated server probe for Enchantment Fusion on MC 26.1.2.
 * Exercises table placement, pixel-art GUI rendering, [⇄] Swap-Order button click,
 * Book+Book fusion, Compendium discovery sync, Gear inscription, and container persistence.
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
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        sp.giveExperienceLevels(30);
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

                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        require(be != null, "Missing EnchantmentFusionTableBlockEntity");
                        be.setItem(0, FusionStationOperations.createSingleEnchantedBook(sharp, 5));
                        be.setItem(1, FusionStationOperations.createSingleEnchantedBook(loyalty, 3));
                        be.setItem(3, new ItemStack(Items.BOOK, 1));

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
                if (
                    screen.getMenu().getSlot(0).getItem().isEmpty()
                    || screen.getMenu().getSlot(1).getItem().isEmpty()
                    || screen.getMenu().getSlot(3).getItem().isEmpty()
                ) {
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
                // Click [⇄] Swap-Order button (47..71, 30..54)
                click(screen, b.getX() + 58, b.getY() + 41);
                next(2, 8);
            }
            case 2 -> {
                require(client.screen instanceof EnchantmentFusionScreen, "Screen closed unexpectedly");
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                if (screen.getMenu().getSlot(0).getItem().isEmpty() || screen.getMenu().getSlot(1).getItem().isEmpty()) {
                    return;
                }
                var previewAfterSwap = screen.getMenu().computePreview();
                if (previewAfterSwap.previewPair() == null || !"minecraft:loyalty".equals(previewAfterSwap.previewPair().primaryId())) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 58, b.getY() + 41);
                next(3, 8);
            }
            case 3 -> {
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                var preview = screen.getMenu().computePreview();
                if (preview.previewPair() == null || !"minecraft:sharpness".equals(preview.previewPair().primaryId())) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 158, b.getY() + 98);
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
                        require(fusedBook.is(FusionItems.FUSED_ENCHANTED_BOOK), "Expected Fused Enchanted Book in target slot");
                        FusedEnchantmentList list = fusedBook.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        require(list.size() == 1, "Expected 1 fused pair on Fused Enchanted Book");
                        require("minecraft:sharpness".equals(list.pairs().get(0).primaryId()), "Expected Sharpness as Primary A");
                        require("minecraft:loyalty".equals(list.pairs().get(0).secondaryId()), "Expected Loyalty as Secondary B");

                        be.setItem(0, fusedBook.copy());
                        be.setItem(3, new ItemStack(Items.DIAMOND_SWORD, 1));
                        sp.containerMenu.broadcastChanges();
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(5, 8);
            }
            case 5 -> {
                if (!serverReadyFlag) {
                    return;
                }
                if (!CompendiumDiscoveryTracker.isClientDiscovered("minecraft:sharpness", "minecraft:loyalty")) {
                    return;
                }
                EnchantmentFusionScreen screen = (EnchantmentFusionScreen) client.screen;
                if (!screen.getMenu().getSlot(3).getItem().is(Items.DIAMOND_SWORD)) {
                    return;
                }
                Rect2i b = screen.guiBounds();
                click(screen, b.getX() + 158, b.getY() + 98);
                next(6, 14);
            }
            case 6 -> {
                var server = client.getSingleplayerServer();
                serverReadyFlag = false;
                server.execute(() -> {
                    try {
                        ServerPlayer sp = server.getPlayerList().getPlayers().getFirst();
                        var be = (EnchantmentFusionTableBlockEntity) sp.level().getBlockEntity(tablePos);
                        ItemStack sword = be.getItem(3);
                        require(sword.is(Items.DIAMOND_SWORD), "Expected Diamond Sword in Slot 3");
                        FusedEnchantmentList list = sword.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        require(list.size() == 1, "Expected cross-domain [Sharpness ▷ Loyalty] inscribed on Diamond Sword");
                        sp.closeContainer();
                        require(be.getItem(3).is(Items.DIAMOND_SWORD), "Closing menu must persist sword inside table BlockEntity");
                        serverReadyFlag = true;
                    } catch (Throwable t) {
                        serverFailure = t;
                    }
                });
                next(7, 10);
            }
            case 7 -> {
                if (!serverReadyFlag) {
                    return;
                }
                resourceReloadFuture = client.reloadResourcePacks();
                next(8, 8);
            }
            case 8 -> {
                if (!resourceReloadFuture.isDone() || client.getOverlay() != null) {
                    return;
                }
                resourceReloadFuture.join();
                finish(
                    client,
                    "CLIENT SMOKE PASS: pixel-art fusion GUI + [⇄] swap order + Book+Book fusion + compendium sync + cross-domain Diamond Sword inscription + container persistence + resource reload"
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
