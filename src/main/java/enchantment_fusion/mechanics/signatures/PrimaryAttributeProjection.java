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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
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
import org.jetbrains.annotations.Nullable;

/**
 * Server-synchronised projection for selected non-native primary enchantment attributes.
 * No enchantment or component is written to an item. Sources must be actual equipped stacks,
 * already checked by the owning direction's current datapack and carrier rules.
 */
final class PrimaryAttributeProjection {
    record Source(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Key(Holder<Attribute> attribute, Identifier nativeId) {}
    private record Projection(Holder<Attribute> attribute, AttributeModifier modifier, Source source) {}
    /** Values are compared at the action and settlement; no attribute is mutated to test attribution. */
    record Evidence(Identifier modifierId, double value, double withoutPrimary, boolean nativeSource) {}
    private static final List<Function<ServerPlayer, List<Source>>> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final int MAX_EFFECTS = 64;
    private static final Map<UUID, List<Projection>> OWNED = new HashMap<>();
    private PrimaryAttributeProjection() {}

    static void register(Function<ServerPlayer, List<Source>> provider) {
        if (provider != null && !PROVIDERS.contains(provider)) PROVIDERS.add(provider);
    }
    static void refresh(ServerPlayer player, List<Source> sources) {
        List<Source> allSources = new ArrayList<>(sources);
        for (var provider : PROVIDERS) {
            try {
                List<Source> extra = provider.apply(player);
                if (extra != null) allSources.addAll(extra);
            } catch (RuntimeException error) {
                EnchantmentFusion.LOGGER.error("Primary attribute source failed", error);
            }
        }
        Map<Key, Projection> desired = new LinkedHashMap<>();
        if (player.isAlive() && !player.isSpectator()) for (Source source : allSources) {
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
                Key key = new Key(effect.attribute(), base.id()); Projection previous = desired.get(key);
                boolean samePrimaryOnItem = previous != null && previous.source().stack() == source.stack()
                    && previous.source().slot() == source.slot() && previous.source().pair().primaryId().equals(source.pair().primaryId());
                // The same native primary on one item uses its highest fused level, independent of provider order.
                // Equal-level effects and unrelated ID collisions keep vanilla's later-entry ordering.
                if (previous == null || AttributeContributionMath.replaceProjection(samePrimaryOnItem,
                    previous.source().pair().primaryLevel(), source.pair().primaryLevel()))
                    desired.put(key, new Projection(effect.attribute(), projected, source));
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
    /**
     * Prove the exact equipped primary supplies one effective attribute modifier. Vanilla
     * item modifiers are transient. If another equipped enchantment uses the same ID, the
     * active source is ambiguous, so no action is attributed to this pair. A datapack with
     * multiple effects on the tested attribute is likewise conservatively skipped.
     */
    static @Nullable Evidence evidence(ServerPlayer player, Source source, Holder<Attribute> attribute) {
        if (source == null || source.stack().isEmpty() || player.getItemBySlot(source.slot()) != source.stack()) return null;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), source.pair().primaryId()).orElse(null);
        AttributeInstance instance = player.getAttribute(attribute);
        if (enchantment == null || instance == null) return null;
        int nativeLevel = source.stack().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
        boolean nativeSource = nativeLevel > 0 && enchantment.value().matchingSlot(source.slot());
        if (nativeSource && nativeLevel != source.pair().primaryLevel()) return null;
        int level = nativeSource ? nativeLevel : Math.max(nativeLevel, source.pair().primaryLevel());
        AttributeModifier expected = null; int index = 0, selectedIndex = -1;
        for (var effect : enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
            if (index >= MAX_EFFECTS) return null;
            if (attribute.equals(effect.attribute())) {
                if (expected != null) return null;
                expected = effect.getModifier(level, source.slot()); selectedIndex = index;
            }
            index++;
        }
        if (expected == null || !Double.isFinite(expected.amount())) return null;
        int nativeSources = nativeSources(player, attribute, expected.id());
        if (nativeSources != (nativeSource ? 1 : 0)) return null;
        AttributeModifier active;
        if (nativeSource) {
            active = instance.getModifier(expected.id());
            if (!expected.equals(active)) return null;
        } else {
            Identifier id = EnchantmentFusion.id("primary_attribute/" + source.pair().primaryId().replace(':', '/')
                + "/" + source.slot().getName() + "/" + selectedIndex);
            active = new AttributeModifier(id, expected.amount(), expected.operation());
            if (nativeSources(player, attribute, id) != 0) return null;
            boolean owned = false;
            for (Projection projection : OWNED.getOrDefault(player.getUUID(), List.of())) {
                Source actual = projection.source();
                if (attribute.equals(projection.attribute()) && active.equals(projection.modifier())
                    && actual.slot() == source.slot() && actual.stack() == source.stack() && actual.pair().equals(source.pair())) {
                    owned = true; break;
                }
            }
            if (!owned || !active.equals(instance.getModifier(id)) || instance.hasModifier(expected.id())) return null;
        }
        if (instance.getPermanentModifiers().contains(active)) return null;
        double without = valueWithout(instance, active.id()), current = instance.getValue();
        if (!Double.isFinite(current) || !Double.isFinite(without) || current <= without + 1.0E-7D) return null;
        return new Evidence(active.id(), current, without, nativeSource);
    }
    static int nativeSources(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        int[] count = {0}, examined = {0};
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || stack.isBroken()) continue;
            // Include item-component modifiers as well as enchantments: vanilla applies both.
            stack.forEachModifier(slot, (candidate, modifier) -> {
                if (++examined[0] <= 128 && attribute.equals(candidate) && id.equals(modifier.id())) count[0]++;
            });
            if (examined[0] > 128) return Integer.MAX_VALUE;
        }
        return count[0];
    }
    /** Mirrors vanilla's add-value, add-base, multiply-total order, including the attribute clamp. */
    static double valueWithout(AttributeInstance instance, Identifier excluded) {
        List<AttributeContributionMath.Modifier> modifiers = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().equals(excluded)) continue;
            AttributeContributionMath.Operation operation = switch (modifier.operation()) {
                case ADD_VALUE -> AttributeContributionMath.Operation.ADD_VALUE;
                case ADD_MULTIPLIED_BASE -> AttributeContributionMath.Operation.ADD_MULTIPLIED_BASE;
                case ADD_MULTIPLIED_TOTAL -> AttributeContributionMath.Operation.ADD_MULTIPLIED_TOTAL;
            };
            modifiers.add(new AttributeContributionMath.Modifier(modifier.amount(), operation));
        }
        return AttributeContributionMath.calculate(instance.getBaseValue(), modifiers, instance.getAttribute().value()::sanitizeValue);
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
