package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/** One actual protection pass: select each item's maximum level before its one real evaluation. */
public final class ProtectionProjection {
    private record Request(EquipmentSlot slot, FusedEnchantmentList pairs, Map<Enchantment, Integer> levels) {}
    public static final class Frame {
        final @Nullable Frame previous; final ServerPlayer player; final DamageSource source;
        final Map<ItemStack, Map<Enchantment, Integer>> applied = new IdentityHashMap<>();
        final Map<ItemStack, Request> requested = new IdentityHashMap<>();
        Frame(@Nullable Frame previous, ServerPlayer player, DamageSource source) {
            this.previous = previous; this.player = player; this.source = source;
        }
    }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private ProtectionProjection() {}
    public static Frame begin(ServerPlayer player, DamageSource source) {
        Frame frame = new Frame(CURRENT.get(), player, source);
        Set<ItemStack> ambiguous = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EquipmentSlot slot : SignatureCatalog.ALL_EQUIP_SLOTS) {
            ItemStack item = player.getItemBySlot(slot);
            Map<String, Integer> ids = FireTradeoffSkills.protectionLevels(item, slot, source);
            if (ids.isEmpty() || ambiguous.contains(item)) continue;
            if (frame.requested.containsKey(item)) { frame.requested.remove(item); ambiguous.add(item); continue; }
            Map<Enchantment, Integer> levels = new IdentityHashMap<>();
            for (var entry : ids.entrySet()) {
                FusionStationOperations.lookupEnchantment(player.level().registryAccess(), entry.getKey()).ifPresent(holder ->
                    levels.merge(holder.value(), entry.getValue(), Math::max));
            }
            if (!levels.isEmpty()) frame.requested.put(item, new Request(slot,
                item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY), levels));
        }
        CURRENT.set(frame);
        return frame;
    }
    /** Never reroll: raise the level of the original invocation, before it consumes randomness. */
    public static int effectiveLevel(Enchantment enchantment, int original, ItemStack item, Entity victim, DamageSource source) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.player != victim || frame.source != source) return original;
        Request request = frame.requested.get(item);
        if (request == null || frame.player.getItemBySlot(request.slot()) != item
            || !request.pairs().equals(item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY))) return original;
        return Math.max(original, request.levels().getOrDefault(enchantment, original));
    }
    public static void observe(Enchantment enchantment, int level, ItemStack item, Entity victim, DamageSource source) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.player != victim || frame.source != source) return;
        frame.applied.computeIfAbsent(item, ignored -> new IdentityHashMap<>()).merge(enchantment, level, Math::max);
    }
    public static boolean applied(ServerPlayer player, DamageSource source, ItemStack item, Enchantment enchantment) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.player != player || frame.source != source) return false;
        Map<Enchantment, Integer> enchantments = frame.applied.get(item);
        return enchantments != null && enchantments.containsKey(enchantment);
    }
    public static void clear() { CURRENT.remove(); }
    public static void end(Frame frame) {
        if (CURRENT.get() != frame) { CURRENT.remove(); return; }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
    }
}
