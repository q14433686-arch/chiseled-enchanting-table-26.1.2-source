package enchantment_fusion.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.gui.FusionConfigScreen;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client-side view of the server's effective catalog; never grants edit permission locally. */
public final class FusionConfigClient {
    private static JsonObject effective = new JsonObject();
    private static JsonObject overrides = new JsonObject();
    private static long revision;
    private static boolean canEdit;
    private static boolean synced;

    private FusionConfigClient() {}

    public static JsonObject effective() { return effective; }
    public static JsonObject overrides() { return overrides; }
    public static long revision() { return revision; }
    public static boolean canEdit() { return canEdit; }
    public static boolean synced() { return synced; }

    public static void openLocal() {
        FusionConfigStore.loadBundledForMenu();
        FusionConfigStore.View view = FusionConfigStore.view();
        effective = view.effective(); overrides = view.overrides(); revision = view.revision();
        canEdit = true; synced = true;
    }

    public static void request() {
        if (ClientPlayNetworking.canSend(FusionConfigNetworking.RequestPayload.TYPE)) {
            ClientPlayNetworking.send(new FusionConfigNetworking.RequestPayload(0));
        }
    }

    public static void apply(FusionConfigNetworking.SyncPayload payload) {
        try {
            JsonElement root = JsonParser.parseString(payload.effectiveJson());
            JsonElement patch = JsonParser.parseString(payload.overridesJson());
            if (!root.isJsonObject() || !patch.isJsonObject()) throw new IllegalArgumentException("invalid config sync");
            JsonObject docs = root.getAsJsonObject();
            if (!docs.has("signatures") || !docs.get("signatures").isJsonObject()
                || !docs.has("transmutations") || !docs.get("transmutations").isJsonObject()) {
                throw new IllegalArgumentException("missing catalogs");
            }
            SignatureCatalog.Snapshot signatures = SignatureCatalog.parseFromJson(docs.getAsJsonObject("signatures"));
            TransmuteCatalog.Snapshot transmutes = TransmuteCatalog.parseFromJson(docs.getAsJsonObject("transmutations"));
            effective = docs; overrides = patch.getAsJsonObject(); revision = payload.revision();
            canEdit = payload.canEdit(); synced = true;
            SignatureCatalog.publish(signatures);
            TransmuteCatalog.publish(transmutes);
            if (Minecraft.getInstance().screen instanceof FusionConfigScreen screen) screen.onServerSync(payload);
        } catch (RuntimeException ex) {
            EnchantmentFusion.LOGGER.warn("Ignoring malformed Enchantment Fusion config sync", ex);
        }
    }

    public static void refreshLocal() {
        FusionConfigStore.View view = FusionConfigStore.view();
        effective = view.effective(); overrides = view.overrides(); revision = view.revision();
        canEdit = true; synced = true;
    }

    public static void disconnect() {
        effective = new JsonObject(); overrides = new JsonObject(); revision = 0;
        canEdit = false; synced = false;
        SignatureCatalog.reloadFromJson(null);
        TransmuteCatalog.reloadFromJson(null);
    }
}
