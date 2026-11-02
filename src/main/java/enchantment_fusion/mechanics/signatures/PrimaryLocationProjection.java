package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Conditional primary effects are not ordinary permanent equipment attributes. This bridge
 * replays selected CURRENT Soul Speed and Frost Walker location/tick components only at
 * vanilla's corresponding events, on the same boots and level, when a primary is not native.
 * Each enchantment owns a separate active set and transient attribute IDs, so one projected
 * Frost effect cannot stop, replace or borrow the Soul Speed state. Other datapack effects,
 * including item damage, use vanilla code.
 */
public final class PrimaryLocationProjection {
    private static final int MAX_STATES = 512, MAX_EFFECTS = 128, MAX_DEPTH = 12;
    private static final List<String> PRIMARIES = List.of(GrapnelAttribute.SOUL, GrapnelFrost.FROST);
    private record Key(Holder<Attribute> attribute, Identifier nativeId) {}
    private record OwnerKey(UUID player, String primary) {}
    private record Source(ServerPlayer player, ItemStack stack, FusedPair pair, Holder<Enchantment> enchantment, int rank) {
        OwnerKey key() { return new OwnerKey(player.getUUID(), pair.primaryId()); }
    }
    private static final class State {
        final Source source;
        final ServerLevel level;
        final Map<Key, AttributeModifier> modifiers = new HashMap<>();
        @Nullable Set<EnchantmentLocationBasedEffect> activeSet;
        Set<EnchantmentLocationBasedEffect> activeSnapshot = Set.of();
        boolean inLocation;
        State(Source source) { this.source = source; this.level = source.player().level(); }
    }
    private static final Map<OwnerKey, State> OWNED = new HashMap<>();
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private PrimaryLocationProjection() {}

    /** No fallback if the datapack excludes this slot, or vanilla already runs the primary there. */
    public static boolean needsProjection(int nativeLevel, boolean matchingSlot) {
        return nativeLevel <= 0 && matchingSlot;
    }
    private static @Nullable Source source(ServerPlayer player, String primary) {
        if (!player.isAlive() || player.isSpectator()) return null;
        ItemStack stack = player.getItemBySlot(EquipmentSlot.FEET);
        if (stack.isEmpty() || stack.isBroken()) return null;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(player.registryAccess(), primary).orElse(null);
        if (enchantment == null || !needsProjection(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment),
            enchantment.value().matchingSlot(EquipmentSlot.FEET))) return null;
        FusedPair best = null;
        if (GrapnelFrost.FROST.equals(primary)) best = GrapnelFrost.projectionPair(stack);
        else if (GrapnelAttribute.SOUL.equals(primary)) {
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                if (!GrapnelAttribute.SOUL.equals(pair.primaryId())) continue;
                boolean lure = GrapnelAttribute.isPair(pair) && GrapnelSkills.applies(pair, stack);
                String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
                boolean step = SoulStepSkills.FORWARD.equals(signature) && SoulStepSkills.applies(pair, stack, signature);
                if ((lure || step) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
            }
        }
        if (best == null) return null;
        int rank = GrapnelFrost.FROST.equals(primary) ? GrapnelFrost.projectionRank(stack, best) : best.primaryLevel();
        return new Source(player, stack, best, enchantment, rank);
    }
    private static boolean same(Source a, @Nullable Source b) {
        return b != null && a.player() == b.player() && a.stack() == b.stack() && a.pair().equals(b.pair())
            && a.rank() == b.rank() && a.enchantment().value() == b.enchantment().value();
    }
    private static @Nullable Set<EnchantmentLocationBasedEffect> active(State state) {
        return state.source.player().activeLocationDependentEnchantments(EquipmentSlot.FEET).get(state.source.enchantment().value());
    }
    private static boolean stateIntact(State state) {
        Set<EnchantmentLocationBasedEffect> active = active(state);
        return active == state.activeSet && (active == null || active.equals(state.activeSnapshot));
    }
    private static void snapshot(State state) {
        state.activeSet = active(state);
        state.activeSnapshot = state.activeSet == null ? Set.of() : Set.copyOf(state.activeSet);
    }
    private static @Nullable State ensure(Source source) {
        ServerPlayer player = source.player(); OwnerKey key = source.key();
        State state = OWNED.get(key);
        if (state != null && (!same(state.source, source) || state.level != player.level() || !stateIntact(state))) {
            stop(state); state = null;
        }
        if (state == null) {
            // Someone else owns this enchantment's vanilla active-set: do not merge or clear it.
            if (OWNED.size() >= MAX_STATES || player.activeLocationDependentEnchantments(EquipmentSlot.FEET)
                .containsKey(source.enchantment().value())) return null;
            state = new State(source); OWNED.put(key, state);
        }
        return state;
    }
    /** Called before vanilla location handling so a native/restored item cannot overlap our old state. */
    public static void reconcile(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        for (String primary : PRIMARIES) {
            State state = OWNED.get(new OwnerKey(player.getUUID(), primary));
            if (state == null || state.inLocation) continue;
            Source current = source(player, primary);
            if (!same(state.source, current) || state.level != player.level() || !stateIntact(state)) stop(state);
        }
    }
    public static void locationChanged(ServerLevel level, LivingEntity entity) {
        if (CURRENT.get() != null || !(entity instanceof ServerPlayer player) || player.level() != level) return;
        reconcile(player);
        for (String primary : PRIMARIES) {
            Source source = source(player, primary); if (source == null) continue;
            State state = ensure(source); if (state == null) continue;
            State previous = CURRENT.get(); CURRENT.set(state); state.inLocation = true;
            try {
                source.enchantment().value().runLocationChangedEffects(level, source.rank(),
                    new EnchantedItemInUse(source.stack(), EquipmentSlot.FEET, player), player);
            } catch (RuntimeException error) {
                EnchantmentFusion.LOGGER.error("Conditional primary location replay failed", error);
                stop(state);
            } finally {
                state.inLocation = false;
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            }
            if (OWNED.get(source.key()) == state) {
                snapshot(state);
                if (!same(state.source, source(player, primary)) || state.level != player.level()) stop(state);
            }
        }
    }
    public static void locationChangedItem(ServerLevel level, ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.FEET && entity.getItemBySlot(slot) == stack) locationChanged(level, entity);
    }
    /** Replay datapack tick effects once, at the same point as native equipped enchantments. */
    public static void tick(ServerLevel level, LivingEntity entity) {
        if (CURRENT.get() != null || !(entity instanceof ServerPlayer player) || player.level() != level) return;
        reconcile(player);
        for (String primary : PRIMARIES) {
            Source source = source(player, primary); if (source == null) continue;
            State state = ensure(source); if (state == null) continue;
            State previous = CURRENT.get(); CURRENT.set(state);
            try {
                source.enchantment().value().tick(level, source.rank(),
                    new EnchantedItemInUse(source.stack(), EquipmentSlot.FEET, player), player);
            } catch (RuntimeException error) {
                EnchantmentFusion.LOGGER.error("Conditional primary tick replay failed", error);
                stop(state);
            } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
            if (OWNED.get(source.key()) == state && (!same(state.source, source(player, primary)) || state.level != player.level())) stop(state);
        }
    }
    public static void stopSlot(LivingEntity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.FEET) stopAll(entity);
    }
    public static void stopAll(LivingEntity entity) {
        if (entity instanceof ServerPlayer player)
            for (String primary : PRIMARIES) {
                State state = OWNED.get(new OwnerKey(player.getUUID(), primary));
                if (state != null) stop(state);
            }
    }
    private static void stop(State state) {
        ServerPlayer player = state.source.player();
        if (!OWNED.remove(state.source.key(), state)) return;
        Set<EnchantmentLocationBasedEffect> current = active(state);
        boolean ownSet = state.inLocation && CURRENT.get() == state || current == state.activeSet
            && (current == null || current.equals(state.activeSnapshot));
        State previous = CURRENT.get(); CURRENT.set(state);
        try {
            if (current != null && ownSet) state.source.enchantment().value().stopLocationBasedEffects(
                state.source.rank(), new EnchantedItemInUse(state.source.stack(), EquipmentSlot.FEET, player), player);
        } catch (RuntimeException error) {
            EnchantmentFusion.LOGGER.error("Conditional primary location cleanup failed", error);
        } finally {
            // Even an interrupted or externally replaced active-set cannot strand an owned modifier.
            for (var entry : List.copyOf(state.modifiers.entrySet())) remove(state, entry.getKey());
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
    public static void clearPlayer(ServerPlayer player) { stopAll(player); }
    public static void clearServer(MinecraftServer server) {
        for (State state : List.copyOf(OWNED.values())) stop(state);
        OWNED.clear();
    }
    public static void tickServer(MinecraftServer server) {
        for (State state : List.copyOf(OWNED.values())) {
            ServerPlayer player = state.source.player();
            if (server.getPlayerList().getPlayer(player.getUUID()) != player) stop(state); else reconcile(player);
        }
    }

    /** Temporarily leave our replay scope while another helper is re-entered by a datapack/mod. */
    public static @Nullable Object suspend() { State previous = CURRENT.get(); CURRENT.remove(); return previous; }
    public static void resume(@Nullable Object previous) { if (previous instanceof State state) CURRENT.set(state); else CURRENT.remove(); }
    public static boolean replaying() { return CURRENT.get() != null; }
    private static boolean ours(State state, EnchantedItemInUse item, Entity entity) {
        return entity == state.source.player() && item.itemStack() == state.source.stack() && item.inSlot() == EquipmentSlot.FEET;
    }
    private static Identifier projectedId(String primary, Identifier nativeId) {
        String name = GrapnelAttribute.SOUL.equals(primary) ? "soul_speed" : "frost_walker";
        return EnchantmentFusion.id("primary_location/" + name + "/" + nativeId.toString().replace(':', '/'));
    }
    /** Mixin entry: true means that vanilla's native-ID attribute operation was handled. */
    public static boolean activateAttribute(EnchantmentAttributeEffect effect, int level, EnchantedItemInUse item,
                                            Entity entity, boolean becameActive) {
        State state = CURRENT.get();
        if (state == null || !ours(state, item, entity)) return false;
        if (!becameActive || OWNED.get(state.source.key()) != state) return true;
        AttributeModifier nativeModifier = effect.getModifier(level, EquipmentSlot.FEET);
        if (!Double.isFinite(nativeModifier.amount())) return true;
        Key key = new Key(effect.attribute(), nativeModifier.id());
        AttributeModifier projected = new AttributeModifier(projectedId(state.source.pair().primaryId(), nativeModifier.id()), nativeModifier.amount(), nativeModifier.operation());
        AttributeInstance instance = state.source.player().getAttribute(effect.attribute());
        if (instance == null) return true;
        if (GrapnelFrost.FROST.equals(state.source.pair().primaryId())) {
            // A shared native attribute ID must not be doubled merely because two primaries
            // were projected under distinct owned IDs. Keep the existing owner untouched.
            if (instance.getModifier(nativeModifier.id()) != null) return true;
            for (State other : OWNED.values())
                if (other != state && other.source.player() == state.source.player() && other.modifiers.containsKey(key)) return true;
        }
        AttributeModifier existing = instance.getModifier(projected.id());
        if (existing == null) {
            instance.addTransientModifier(projected); state.modifiers.put(key, projected);
        } else if (projected.equals(existing) && projected.equals(state.modifiers.get(key))
            && !instance.getPermanentModifiers().contains(existing)) {
            // Already owned; duplicate entries must not stack.
        }
        return true;
    }
    public static boolean deactivateAttribute(EnchantmentAttributeEffect effect, int level, EnchantedItemInUse item, Entity entity) {
        State state = CURRENT.get();
        if (state == null || !ours(state, item, entity)) return false;
        remove(state, new Key(effect.attribute(), effect.getModifier(level, EquipmentSlot.FEET).id()));
        return true;
    }
    private static void remove(State state, Key key) {
        AttributeModifier owned = state.modifiers.remove(key);
        AttributeInstance instance = state.source.player().getAttribute(key.attribute());
        AttributeModifier current = owned == null || instance == null ? null : instance.getModifier(owned.id());
        if (current != null && current.equals(owned) && !instance.getPermanentModifiers().contains(current)) instance.removeModifier(current.id());
    }

    /** Active conditional effect + actual effective speed contribution, not merely a soul block or input. */
    static @Nullable PrimaryAttributeProjection.Evidence evidence(ServerPlayer player, GrapnelAttribute.Gear gear) {
        Holder<Enchantment> holder = FusionStationOperations.lookupEnchantment(player.registryAccess(), GrapnelAttribute.SOUL).orElse(null);
        if (holder == null || !holder.value().matchingSlot(gear.slot())) return null;
        int nativeLevel = gear.stack().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
        boolean nativeSource = nativeLevel > 0;
        if (nativeSource && nativeLevel != gear.pair().primaryLevel()) return null;
        State state = OWNED.get(new OwnerKey(player.getUUID(), GrapnelAttribute.SOUL));
        if (!nativeSource && (state == null || !same(state.source, source(player, GrapnelAttribute.SOUL)) || !stateIntact(state)
            || state.source.stack() != gear.stack() || !state.source.pair().equals(gear.pair()))) return null;
        Set<EnchantmentLocationBasedEffect> active = player.activeLocationDependentEnchantments(gear.slot()).get(holder.value());
        if (active == null) return null;
        List<EnchantmentAttributeEffect> effects = new ArrayList<>();
        int[] count = {0};
        for (EnchantmentLocationBasedEffect effect : active) if (!collect(effect, effects, count, 0)) return null;
        EnchantmentAttributeEffect match = null;
        for (EnchantmentAttributeEffect effect : effects) if (effect.attribute().equals(Attributes.MOVEMENT_SPEED)) {
            if (match != null) return null; match = effect;
        }
        if (match == null) return null;
        AttributeModifier expected = match.getModifier(gear.pair().primaryLevel(), gear.slot());
        if (!Double.isFinite(expected.amount())) return null;
        Identifier id = nativeSource ? expected.id() : projectedId(GrapnelAttribute.SOUL, expected.id());
        AttributeModifier wanted = nativeSource ? expected : new AttributeModifier(id, expected.amount(), expected.operation());
        if (!nativeSource && !wanted.equals(state.modifiers.get(new Key(match.attribute(), expected.id())))) return null;
        AttributeInstance instance = player.getAttribute(match.attribute());
        if (instance == null || !wanted.equals(instance.getModifier(id)) || instance.getPermanentModifiers().contains(wanted)) return null;
        int locationSources = locationSources(player, match.attribute(), id);
        if (locationSources != (nativeSource ? 1 : 0) || PrimaryAttributeProjection.nativeSources(player, match.attribute(), id) != 0) return null;
        double current = instance.getValue(), without = PrimaryAttributeProjection.valueWithout(instance, id);
        if (!Double.isFinite(current) || !Double.isFinite(without) || current <= without + 1.0E-7D) return null;
        return new PrimaryAttributeProjection.Evidence(id, current, without, nativeSource);
    }
    private static boolean collect(EnchantmentLocationBasedEffect effect, List<EnchantmentAttributeEffect> result, int[] count, int depth) {
        if (++count[0] > MAX_EFFECTS || depth > MAX_DEPTH) return false;
        if (effect instanceof EnchantmentAttributeEffect attribute) result.add(attribute);
        else if (effect instanceof AllOf.LocationBasedEffects all) {
            for (var child : all.effects()) if (!collect(child, result, count, depth + 1)) return false;
        }
        return true;
    }
    private static int locationSources(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        int result = 0; int[] count = {0};
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (var set : player.activeLocationDependentEnchantments(slot).values()) {
                List<EnchantmentAttributeEffect> effects = new ArrayList<>();
                for (var effect : set) if (!collect(effect, effects, count, 0)) return Integer.MAX_VALUE;
                for (var effect : effects) if (attribute.equals(effect.attribute()) && id.equals(effect.getModifier(1, slot).id())) result++;
            }
        }
        return result;
    }
}
