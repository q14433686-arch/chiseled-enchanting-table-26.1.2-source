package enchantment_fusion.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import enchantment_fusion.config.FusionConfigClient;
import enchantment_fusion.config.FusionConfigNetworking;
import enchantment_fusion.config.FusionConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Compact, searchable editor of the actual server datapack values and sparse overrides. */
public final class FusionConfigScreen extends Screen {
    private record Field(String file, List<String> path, JsonElement value, boolean overridden) {
        String pathText() { return String.join(" / ", path); }
    }

    private final @Nullable Screen parent;
    private final boolean local;
    private int category, page, x, panelWidth, listY, rowCount, navigationY;
    private String search = "", draft = "";
    private @Nullable Field selected;
    private long selectedRevision, nextRequestId = 1, pendingRequest;
    private String status = "";
    private EditBox searchBox, valueBox;
    private Button categoryButton, saveButton, resetButton;
    private final List<Button> rows = new ArrayList<>();

    public FusionConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("config.enchantment_fusion.title"));
        this.parent = parent;
        this.local = Minecraft.getInstance().level == null;
        if (local) FusionConfigClient.openLocal();
        else if (ClientPlayNetworking.canSend(FusionConfigNetworking.RequestPayload.TYPE)) FusionConfigClient.request();
        else status = "unavailable";
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, Math.max(220, this.width - 16));
        x = (this.width - panelWidth) / 2;
        int top = 30;
        listY = top + 27;
        rowCount = Math.max(2, Math.min(8, (this.height - 165) / 19));
        navigationY = listY + rowCount * 19 + 2;
        categoryButton = addRenderableWidget(Button.builder(categoryLabel(), button -> {
            category = (category + 1) % FusionConfigStore.FILES.size();
            page = 0; selected = null; draft = ""; status = "";
            valueBox.setValue("");
            button.setMessage(categoryLabel());
            refreshRows();
        }).bounds(x, top, Math.min(175, panelWidth / 2), 20).build());
        int searchX = x + Math.min(175, panelWidth / 2) + 6;
        searchBox = addRenderableWidget(new EditBox(font, searchX, top, x + panelWidth - searchX, 20,
            Component.translatable("config.enchantment_fusion.search")));
        searchBox.setMaxLength(120);
        searchBox.setHint(Component.translatable("config.enchantment_fusion.search"));
        searchBox.setValue(search);
        searchBox.setResponder(text -> { search = text; page = 0; refreshRows(); });
        rows.clear();
        for (int i = 0; i < rowCount; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> selectRow(row))
                .bounds(x, listY + i * 19, panelWidth, 18).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(0, page - 1); refreshRows();
        }).bounds(x, navigationY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page = Math.min(maxPage(), page + 1); refreshRows();
        }).bounds(x + panelWidth - 30, navigationY, 30, 20).build());
        int valueY = navigationY + 37;
        valueBox = addRenderableWidget(new EditBox(font, x, valueY, panelWidth, 20,
            Component.translatable("config.enchantment_fusion.value")));
        valueBox.setMaxLength(FusionConfigStore.MAX_VALUE_CHARS);
        valueBox.setValue(draft);
        valueBox.setResponder(text -> draft = text);
        int buttonY = valueY + 37;
        int gap = 4, buttonWidth = (panelWidth - gap * 2) / 3;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("config.enchantment_fusion.save"),
            button -> submit(false)).bounds(x, buttonY, buttonWidth, 20).build());
        resetButton = addRenderableWidget(Button.builder(Component.translatable("config.enchantment_fusion.reset"),
            button -> submit(true)).bounds(x + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.enchantment_fusion.done"), button -> onClose())
            .bounds(x + 2 * (buttonWidth + gap), buttonY, panelWidth - 2 * (buttonWidth + gap), 20).build());
        refreshRows();
    }

    private Component categoryLabel() {
        String key = "config.enchantment_fusion.category." + FusionConfigStore.FILES.get(category);
        return Component.translatable(key);
    }

    private List<Field> filtered() {
        List<Field> all = new ArrayList<>();
        String file = FusionConfigStore.FILES.get(category);
        JsonObject root = FusionConfigClient.effective();
        if (root.has(file) && root.get(file).isJsonObject()) {
            flatten(file, root.getAsJsonObject(file), new ArrayList<>(), all);
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        if (!needle.isEmpty()) all.removeIf(f -> !f.pathText().toLowerCase(Locale.ROOT).contains(needle)
            && !labelFor(f).toLowerCase(Locale.ROOT).contains(needle));
        return all;
    }

    private void flatten(String file, JsonObject object, List<String> prefix, List<Field> output) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (prefix.isEmpty() && (entry.getKey().equals("schema_version") || entry.getKey().equals("replace"))) continue;
            // This legacy field is superseded by economy.repulsive_fail_chance when that exists.
            if (file.equals("repulsive_pairs") && prefix.isEmpty() && entry.getKey().equals("base_instability_chance")
                && FusionConfigClient.effective().has("economy")
                && FusionConfigClient.effective().getAsJsonObject("economy").has("repulsive_fail_chance")) continue;
            List<String> path = new ArrayList<>(prefix); path.add(entry.getKey());
            if (entry.getValue().isJsonObject()) flatten(file, entry.getValue().getAsJsonObject(), path, output);
            else if (!entry.getValue().isJsonNull()) {
                boolean overridden = FusionConfigStore.getPath(FusionConfigClient.overrides(), file, path) != null;
                output.add(new Field(file, List.copyOf(path), entry.getValue(), overridden));
            }
        }
    }

    private int maxPage() {
        return Math.max(0, (filtered().size() - 1) / rowCount);
    }

    private void refreshRows() {
        if (rows.isEmpty()) return;
        List<Field> fields = filtered();
        page = Math.min(page, Math.max(0, (fields.size() - 1) / rowCount));
        for (int i = 0; i < rows.size(); i++) {
            Button button = rows.get(i);
            int index = page * rowCount + i;
            button.visible = index < fields.size();
            if (index < fields.size()) {
                Field field = fields.get(index);
                String text = (field.overridden() ? "* " : "") + labelFor(field) + "  =  " + printable(field.value());
                button.setMessage(Component.literal(clip(text, panelWidth - 18)));
            }
        }
    }

    private String labelFor(Field field) {
        // Economy labels are translated; all other paths retain their exact datapack IDs.
        if (field.file().equals("economy") && field.path().size() == 1) {
            String key = "config.enchantment_fusion.field." + field.path().getFirst();
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) return translated + " (" + field.path().getFirst() + ")";
        }
        return field.pathText();
    }

    private String clip(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…"))) + "…";
    }

    private static String printable(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        return value.toString();
    }

    private void selectRow(int row) {
        List<Field> fields = filtered();
        int index = page * rowCount + row;
        if (index < 0 || index >= fields.size()) return;
        selected = fields.get(index);
        selectedRevision = FusionConfigClient.revision();
        draft = printable(selected.value());
        valueBox.setValue(draft);
        status = "";
    }

    private void submit(boolean reset) {
        if (selected == null || !FusionConfigClient.canEdit() || pendingRequest != 0) return;
        try {
            JsonElement next = null;
            if (!reset) {
                if (selected.value().isJsonPrimitive() && selected.value().getAsJsonPrimitive().isString()) {
                    next = new JsonPrimitive(draft);
                } else next = JsonParser.parseString(draft);
            }
            if (local) {
                FusionConfigStore.UpdateResult result = FusionConfigStore.update(
                    selected.file(), selected.path(), next, reset, selectedRevision);
                FusionConfigClient.refreshLocal();
                status = result.reason();
                if (result.success()) reselect();
                refreshRows();
            } else {
                if (!ClientPlayNetworking.canSend(FusionConfigNetworking.UpdatePayload.TYPE)) {
                    status = "unavailable"; return;
                }
                JsonArray path = new JsonArray();
                for (String part : selected.path()) path.add(part);
                pendingRequest = nextRequestId++;
                ClientPlayNetworking.send(new FusionConfigNetworking.UpdatePayload(selected.file(), path.toString(),
                    reset ? "" : next.toString(), reset, selectedRevision, pendingRequest));
                status = "saving";
            }
        } catch (RuntimeException ex) {
            status = "invalid";
        }
    }

    public void onServerSync(FusionConfigNetworking.SyncPayload payload) {
        if (pendingRequest != 0 && payload.requestId() == pendingRequest) {
            pendingRequest = 0;
            status = payload.result();
            if (payload.result().equals("saved")) reselect();
        } else if (selected != null && selectedRevision != payload.revision()) {
            status = "stale";
        }
        refreshRows();
    }

    private void reselect() {
        if (selected == null) return;
        JsonElement current = FusionConfigStore.getPath(FusionConfigClient.effective(), selected.file(), selected.path());
        if (current == null || current.isJsonObject()) { selected = null; draft = ""; valueBox.setValue(""); return; }
        boolean overridden = FusionConfigStore.getPath(FusionConfigClient.overrides(), selected.file(), selected.path()) != null;
        selected = new Field(selected.file(), selected.path(), current, overridden);
        selectedRevision = FusionConfigClient.revision();
        draft = printable(current);
        valueBox.setValue(draft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean editable = selected != null && FusionConfigClient.canEdit() && pendingRequest == 0;
        valueBox.setEditable(editable);
        saveButton.active = editable;
        resetButton.active = editable && selected.overridden();
        ctx.centeredText(font, title, this.width / 2, 10, 0xFFFFFFFF);
        String count = (page + 1) + " / " + (maxPage() + 1);
        ctx.centeredText(font, count, x + panelWidth / 2, navigationY + 6, 0xFFD5CDBB);
        String fieldText = selected == null ? Component.translatable("config.enchantment_fusion.select").getString()
            : selected.pathText() + (selected.overridden() ? " *" : "");
        ctx.text(font, clip(fieldText, panelWidth), x, navigationY + 25, 0xFFFFE4AA, false);
        String hint;
        if (!FusionConfigClient.synced() && status.equals("unavailable")) {
            hint = Component.translatable("config.enchantment_fusion.status.unavailable").getString();
        } else if (!FusionConfigClient.synced()) hint = Component.translatable("config.enchantment_fusion.waiting").getString();
        else if (!FusionConfigClient.canEdit()) hint = Component.translatable("config.enchantment_fusion.read_only").getString();
        else if (!status.isEmpty()) {
            String key = "config.enchantment_fusion.status." + status;
            hint = Component.translatable(key).getString();
        }
        else if (selected != null && selected.value().isJsonArray()) hint = Component.translatable("config.enchantment_fusion.array_hint").getString();
        else hint = Component.translatable("config.enchantment_fusion.override_hint").getString();
        ctx.text(font, clip(hint, panelWidth), x, navigationY + 61, 0xFFBBBBBB, false);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
