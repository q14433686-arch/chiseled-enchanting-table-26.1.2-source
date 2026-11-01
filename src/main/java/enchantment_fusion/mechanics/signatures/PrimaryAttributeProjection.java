package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Server-synchronised projection for selected non-native primary enchantment attributes.
 * No enchantment or component is written to an item. Sources must be actual equipped stacks,
 * already checked by the owning direction's current datapack and carrier rules.
 */
final class PrimaryAttributeProjection {
    record Source(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Key(Holder<Attribute> attribute, Identifier nativeId) {}
    private record Projection(Holder<Attribute> attribute, AttributeModifier modifier) {}
    private static final int MAX_EFFECTS = 64;
    private static final Map<UUID, List<Projection>> OWNED = new HashMap<>();
    private PrimaryAttributeProjection() {}

    static void refresh(ServerPlayer player, List<Source> sources) {
        Map<Key, Projection> desired = new LinkedHashMap<>();
        if (player.isAlive() && !player.isSpectator()) for (Source source : sources) {
            if (source == null || source.stack().isEmpty() || player.getItemBySlot(source.slot()) != source.stack()) continue;
            Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), source.pair().primaryId()).orElse(null);
            if (enchantment == null) continue;
            int nativeLevel = source.stack().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
            if (nativeLevel > 0 && enchantment.value().matchingSlot(source.slot())) continue;
            int index = 0;
            for (var effect : enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                if (index >= MAX_EFFECTS) break;
                AttributeModifier base = effect.getModifier(Math.max(nativeLevel, source.pair().primaryLevel()), source.slot());
                AttributeInstance instance = player.getAttribute(effect.attribute());
                int effectIndex = index++;
                if (instance == null || !Double.isFinite(base.amount()) || instance.hasModifier(base.id())) continue;
                Identifier id = EnchantmentFusion.id("primary_attribute/" + source.pair().primaryId().replace(':', '/')
                    + "/" + source.slot().getName() + "/" + effectIndex);
                AttributeModifier projected = new AttributeModifier(id, base.amount(), base.operation());
                // If a datapack repeats a native modifier ID, vanilla's later entry wins rather than stacking both.
                desired.put(new Key(effect.attribute(), base.id()), new Projection(effect.attribute(), projected));
                if (desired.size() >= MAX_EFFECTS) break;
            }
        }
        List<Projection> previous = OWNED.getOrDefault(player.getUUID(), List.of());
        for (Projection projection : previous) {
            if (!desired.containsValue(projection)) remove(player, projection);
        }
        List<Projection> actual = new ArrayList<>();
        for (Projection projection : desired.values()) {
            AttributeInstance instance = player.getAttribute(projection.attribute());
            if (instance == null) continue;
            AttributeModifier existing = instance.getModifier(projection.modifier().id());
            if (existing == null) {
                instance.addTransientModifier(projection.modifier()); actual.add(projection);
            } else if (existing.equals(projection.modifier()) && previous.contains(projection)
                && !instance.getPermanentModifiers().contains(existing)) {
                actual.add(projection);
            }
            // A conflicting ID is not ours: neither replace nor remove it.
        }
        if (actual.isEmpty()) OWNED.remove(player.getUUID()); else OWNED.put(player.getUUID(), actual);
    }
    private static void remove(ServerPlayer player, Projection projection) {
        AttributeInstance instance = player.getAttribute(projection.attribute());
        AttributeModifier current = instance == null ? null : instance.getModifier(projection.modifier().id());
        if (current != null && current.equals(projection.modifier()) && !instance.getPermanentModifiers().contains(current))
            instance.removeModifier(current.id());
    }
    static void clear(ServerPlayer player) {
        List<Projection> projections = OWNED.remove(player.getUUID());
        if (projections != null) for (Projection projection : projections) remove(player, projection);
    }
    static void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) clear(player);
        OWNED.clear();
    }
}
