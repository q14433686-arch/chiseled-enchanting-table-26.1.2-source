package chiseled_enchanting_table.smoke;

import com.mojang.blaze3d.platform.InputConstants;

import chiseled_enchanting_table.config.*;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Runs against a real SDL window and integrated server, not mocks or source-text assertions. */
public final class ClientSmoke implements ClientModInitializer {
    private int phase, ticks, delay;
    private ChiseledEnchantingConfigScreen editor;
    private ByteBuffer text;
    private volatile boolean menuPrepared, menuClosed;
    private volatile Throwable serverFailure;
    private BlockPos tablePos;
    private boolean finished;
    private java.util.concurrent.CompletableFuture<Void> itemReload;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try { step(client); }
            catch (Throwable error) { error.printStackTrace(); finish(client, "CLIENT SMOKE FAIL: " + error); }
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private ConfigLayoutSpec.ModalLayout layout() throws Exception {
        return (ConfigLayoutSpec.ModalLayout) field(editor, "layout");
    }
    private static void click(Screen screen, double x, double y, int button) {
        require(screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)), false),
            "click not handled at " + x + "," + y + " on " + screen.getClass().getSimpleName());
    }
    private void clickBox(ConfigLayoutSpec.Box box, int button) {
        click(editor, box.x() + Math.min(4, box.w() / 2), box.y() + box.h() / 2.0, button);
    }
    private long textDeadlineNanos;
    private boolean awaitText(SearchInputSession input, String context) {
        String actual = input.buffer().text();
        if (actual.equals("洗练 abc")) return true;
        require(System.nanoTime() < textDeadlineNanos, context + ": expected committed CJK text, actual=" + actual);
        return false;
    }
    private void pushText(Minecraft client) {
        textDeadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        // Keep the UTF-8 allocation alive until the native queue has actually been consumed.
        text = MemoryUtil.memUTF8("洗练 abc");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Event event = SDL_Event.calloc(stack);
            event.text().type(SDLEvents.SDL_EVENT_TEXT_INPUT)
                .windowID(SDLVideo.SDL_GetWindowID(client.getWindow().handle())).text(text);
            require(SDLEvents.SDL_PushEvent(event), "SDL rejected text event");
        }
    }
    private void next(int phase) { this.phase = phase; delay = 6; }
    private void step(Minecraft client) throws Exception {
        if (++ticks > 6000) throw new AssertionError("client probe timed out at phase " + phase);
        if (serverFailure != null) throw new AssertionError("server probe failed", serverFailure);
        if (delay-- > 0) return;
        long handle = client.getWindow().handle();
        switch (phase) {
            case 0 -> {
                if (client.player == null || client.getSingleplayerServer() == null || client.gui.overlay() != null) return;
                // Wait for initial remote permission/config synchronization.
                if (!ModRuntimeConfig.clientCanEditServerConfig()) return;
                editor = new ChiseledEnchantingConfigScreen(null);
                client.gui.setScreen(editor);
                next(1);
            }
            case 1 -> {
                if (layout() == null || ((java.util.List<?>) field(editor, "hitBoxes")).isEmpty()) return;
                // Layout exists after init(), before the first render. Wait for real hit regions.
                clickBox(layout().searchBox(), InputConstants.MOUSE_BUTTON_LEFT);
                require(editor.capturesInput(), "search did not take focus");
                require(SDLKeyboard.SDL_TextInputActive(handle), "native SDL text input never started");
                pushText(client);
                next(2);
            }
            case 2 -> {
                var input = (SearchInputSession) field(editor, "searchInput");
                if (!awaitText(input, "native config search")) return;
                MemoryUtil.memFree(text); text = null;
                clickBox(layout().presetButtons().get(1), InputConstants.MOUSE_BUTTON_LEFT);
                require(!SDLKeyboard.SDL_TextInputActive(handle), "clicking preset did not release native focus");
                var edit = (ConfigEditSession) field(editor, "editSession");
                require(ModRuntimeConfig.Preset.CASUAL.matches(edit.draft()), "preset click did not update actual draft");
                next(3);
            }
            case 3 -> {
                clickBox(layout().saveBtn(), InputConstants.MOUSE_BUTTON_RIGHT); // Save without closing, through actual screen + networking.
                next(4);
            }
            case 4 -> {
                var saves = (ConfigSaveSession) field(editor, "saves");
                if (saves.pendingRequestId() != 0) return;
                require(!saves.failed(), "server rejected/failed config save");
                var edit = (ConfigEditSession) field(editor, "editSession");
                require(!edit.dirty() && ModRuntimeConfig.Preset.CASUAL.matches(edit.baseline().toDraft()), "accepted save did not reconcile baseline");
                editor.onClose();
                editor = new ChiseledEnchantingConfigScreen(null);
                client.gui.setScreen(editor);
                next(5);
            }
            case 5 -> {
                require(ModRuntimeConfig.Preset.CASUAL.matches(((ConfigEditSession) field(editor, "editSession")).draft()), "reopened preferences lost preset");
                editor.onClose();
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var level = player.level();
                        tablePos = player.blockPosition().offset(1, 0, 0);
                        var state = BlockRegistry.CHISELED_ENCHANTING_TABLE.defaultBlockState();
                        level.setBlockAndUpdate(tablePos, state);
                        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
                        player.openMenu(state.getMenuProvider(level, tablePos));
                        var menu = (ChiseledEnchantingTableScreenHandler) player.containerMenu;
                        menu.sendInitialLiveSync();
                        menu.clicked(-1, 0, ContainerInput.QUICK_MOVE, player); // Invalid slot must be harmless.
                        menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player); // Hotbar 0 -> target slot.
                        require(menu.get_enchantable_item().is(Items.DIAMOND_SWORD), "quick-move missed target slot");
                        require(player.getInventory().getItem(0).isEmpty(), "quick-move duplicated source stack");
                        MenuBoundaryProbe.verify(menu);
                        RainbowClothProbe.verify(player, tablePos);
                        ResearchFoundationProbe.verify(player, tablePos);
                        TomeWorldProbe.prepare(menu, player, tablePos);
                        menu.broadcastChanges();
                        menuPrepared = true;
                    } catch (Throwable error) { serverFailure = error; }
                });
                next(6);
            }
            case 6 -> {
                if (!menuPrepared || !(client.gui.screen() instanceof ChiseledEnchantingTableScreen table)) return;
                if (!((ChiseledEnchantingTableScreenHandler) client.player.containerMenu).get_enchantable_item().is(Items.DIAMOND_SWORD)) return;
                var syncedMenu=(ChiseledEnchantingTableScreenHandler)client.player.containerMenu;
                if (!syncedMenu.isTomePageUnlocked() || syncedMenu.tomeBooks.size()<2) return;
                if(!chiseled_enchanting_table.render.RainbowTableItemVisuals.isRainbow(client.player.getInventory().getItem(8))) return;
                chiseled_enchanting_table.render.RainbowItemProbe.begin(client);
                client.player.setYRot(-90); client.player.setXRot(40);
                require(chiseled_enchanting_table.render.ChiseledEnchantingTableBlockEntityRenderer.clothColor(0,0)
                    != chiseled_enchanting_table.render.ChiseledEnchantingTableBlockEntityRenderer.clothColor(12,.5f),"rainbow colour did not interpolate");
                chiseled_enchanting_table.gui.ListModelProbe.verify(client.player);
                chiseled_enchanting_table.gui.UiPresentationProbe.verify(client, (ChiseledEnchantingTableScreenHandler) client.player.containerMenu);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "table-ui-zh_cn.png",
                    client.gameRenderer.mainRenderTarget(), 1, message -> System.out.println("UI screenshot: " + message.getString()));
                click(table, (int) field(table, "leftPos") + 172, (int) field(table, "topPos") - 12, InputConstants.MOUSE_BUTTON_LEFT);
                next(20);
            }
            case 20 -> {
                var table=(ChiseledEnchantingTableScreen)client.gui.screen();
                var menu=(ChiseledEnchantingTableScreenHandler)client.player.containerMenu;
                if (!menu.isTomePageUnlocked() || menu.tomeBooks.size()<2) return;
                var list=(chiseled_enchanting_table.gui.EnchantementListWidget)field(table,"scrollContainer");
                require(list.tabMode()==chiseled_enchanting_table.gui.EnchantementListWidget.UiTabMode.TOMES,"tome tab click did not switch catalogue");
                int left=(int)field(table,"leftPos"), top=(int)field(table,"topPos");
                var select=chiseled_enchanting_table.gui.TomeDirectoryLayout.button(7);
                click(table,left+7+select.x()+select.width()/2.0,top+17+select.y()+select.height()/2.0,InputConstants.MOUSE_BUTTON_LEFT);
                require(list.selectedTomes().size()==menu.tomeBooks.size(),"visible-selection control did not select real shelf books");
                net.minecraft.client.Screenshot.grab(client.gameDirectory,"rainbow-items-a.png",
                    client.gameRenderer.mainRenderTarget(),1,message->System.out.println("Rainbow items screenshot A: "+message.getString()));
                var lock=chiseled_enchanting_table.gui.TomeDirectoryLayout.lockButton(2);
                click(table,left+lock.x()+lock.width()/2.0,top+lock.y()+lock.height()/2.0,InputConstants.MOUSE_BUTTON_LEFT);
                click(table,left+lock.x()+lock.width()/2.0,top+lock.y()+lock.height()/2.0,InputConstants.MOUSE_BUTTON_LEFT);
                next(21);
            }
            case 21 -> {
                var menu=(ChiseledEnchantingTableScreenHandler)client.player.containerMenu;
                if (menu.tomeBooks.stream().anyMatch(b->!b.locked())) return;
                require(menu.tomeBooks.size()>=2 && menu.tomeBooks.stream().allMatch(b->b.locked_by_viewer()),"batch lock owner/live sync mismatch");
                System.out.println("CATALOGUE WORLD PASS: real bookshelf multi-enchantment books + actual tome-tab/visible-selection/button clicks + confirmed paced permanent locks + server-authoritative owner sync");
                next(22);
            }
            case 22 -> {
                if(!chiseled_enchanting_table.render.RainbowItemProbe.laterFrame(client)) return;
                net.minecraft.client.Screenshot.grab(client.gameDirectory,"rainbow-items-b.png",
                    client.gameRenderer.mainRenderTarget(),1,message->System.out.println("Rainbow items screenshot B: "+message.getString()));
                var table=(ChiseledEnchantingTableScreen)client.gui.screen();
                net.minecraft.client.Screenshot.grab(client.gameDirectory,"tome-catalogue-zh_cn.png",
                    client.gameRenderer.mainRenderTarget(),1,message->System.out.println("Catalogue screenshot: "+message.getString()));
                click(table, (int) field(table, "leftPos") + 300, (int) field(table, "topPos") - 8, InputConstants.MOUSE_BUTTON_LEFT);
                require(table.hasActiveConfigOverlay(), "table config button did not open overlay");
                editor = (ChiseledEnchantingConfigScreen) field(table, "configOverlay");
                next(7);
            }
            case 7 -> {
                if (layout() == null || ((java.util.List<?>) field(editor, "hitBoxes")).isEmpty()) return;
                var box = layout().searchBox();
                // Dispatch through the parent menu screen, exercising overlay forwarding.
                click(client.gui.screen(), box.x() + 4, box.y() + box.h() / 2.0, InputConstants.MOUSE_BUTTON_LEFT);
                require(SDLKeyboard.SDL_TextInputActive(handle), "overlay did not start SDL input");
                pushText(client);
                next(8);
            }
            case 8 -> {
                if (!awaitText((SearchInputSession) field(editor, "searchInput"), "overlay native search")) return;
                MemoryUtil.memFree(text); text = null;
                editor.onClose();
                require(!SDLKeyboard.SDL_TextInputActive(handle), "closing overlay leaked native focus");
                require(client.gui.screen() instanceof ChiseledEnchantingTableScreen table && !table.hasActiveConfigOverlay(), "overlay close lost parent table");
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.closeContainer();
                        var entity = (chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity) player.level().getBlockEntity(tablePos);
                        require(entity.getItem(0).is(Items.DIAMOND_SWORD), "closing menu lost persisted target");
                        menuClosed = true;
                    } catch (Throwable error) { serverFailure = error; }
                });
                next(9);
            }
            case 9 -> {
                if (!menuClosed) return;
                itemReload=client.reloadResourcePacks();
                next(30);
            }
            case 30 -> {
                if(!itemReload.isDone() || client.gui.overlay()!=null) return;
                itemReload.join();
                chiseled_enchanting_table.render.RainbowItemProbe.afterReload(client);
                finish(client, "CLIENT SMOKE PASS: real SDL committed CJK text + preset click + server save/ack + reopen + real menu quick-move + overlay forwarding/focus release + persistent target after close");
            }
            default -> throw new AssertionError("bad phase " + phase);
        }
    }
    private void finish(Minecraft client, String message) {
        finished = true;
        System.out.println(message);
        try { Files.writeString(Path.of("client-smoke-result.txt"), message + "\n"); }
        catch (Exception error) { error.printStackTrace(); }
        if (text != null) { MemoryUtil.memFree(text); text = null; }
        client.stop();
    }
}
