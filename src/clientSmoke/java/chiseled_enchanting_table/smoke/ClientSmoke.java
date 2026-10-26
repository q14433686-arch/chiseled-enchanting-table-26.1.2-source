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
    private void pushText(Minecraft client) {
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
                require(input.buffer().text().equals("洗练 abc"), "native text did not reach config search");
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
                        menu.broadcastChanges();
                        menuPrepared = true;
                    } catch (Throwable error) { serverFailure = error; }
                });
                next(6);
            }
            case 6 -> {
                if (!menuPrepared || !(client.gui.screen() instanceof ChiseledEnchantingTableScreen table)) return;
                if (!((ChiseledEnchantingTableScreenHandler) client.player.containerMenu).get_enchantable_item().is(Items.DIAMOND_SWORD)) return;
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
                require(((SearchInputSession) field(editor, "searchInput")).buffer().text().equals("洗练 abc"), "overlay swallowed native text");
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
