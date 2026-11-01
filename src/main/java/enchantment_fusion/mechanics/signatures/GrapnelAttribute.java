package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The attribute-driven Lure directions share slot, source, datapack and native/projection checks. */
public final class GrapnelAttribute {
    public static final String SWIFT = "minecraft:swift_sneak", DEPTH = "minecraft:depth_strider", SWEEP = "minecraft:sweeping_edge";
    public static final String SOUL = "minecraft:soul_speed";
    record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {
        PrimaryAttributeProjection.Source source() { return new PrimaryAttributeProjection.Source(slot, stack, pair); }
    }
    private static boolean initialized;
    private GrapnelAttribute() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        PrimaryAttributeProjection.register(GrapnelAttribute::sources);
        GrapnelMotion.init(); GrapnelSweep.init();
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && GrapnelSkills.LURE.equals(pair.secondaryId())
            && (SWIFT.equals(pair.primaryId()) || DEPTH.equals(pair.primaryId()) || SWEEP.equals(pair.primaryId()) || SOUL.equals(pair.primaryId()));
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (!isPair(pair) || roles == null) return false;
        if (SWIFT.equals(pair.primaryId())) return roles.contains(FusionCarrier.LEG_ARMOR);
        if (DEPTH.equals(pair.primaryId()) || SOUL.equals(pair.primaryId())) return roles.contains(FusionCarrier.FOOT_ARMOR);
        // Vanilla only executes doSweepAttack for a sword; ordinary axes do not sweep.
        return roles.contains(FusionCarrier.SWORD);
    }
    private static EquipmentSlot slot(String id) {
        return SWIFT.equals(id) ? EquipmentSlot.LEGS : DEPTH.equals(id) || SOUL.equals(id) ? EquipmentSlot.FEET : EquipmentSlot.MAINHAND;
    }
    static Holder<Attribute> attribute(String id) {
        return SWIFT.equals(id) ? Attributes.SNEAKING_SPEED : DEPTH.equals(id)
            ? Attributes.WATER_MOVEMENT_EFFICIENCY : Attributes.SWEEPING_DAMAGE_RATIO;
    }
    /** Different primaries on the same item must not shadow one another's environments. */
    static List<Gear> selectedAll(ServerPlayer player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot); Map<String, Gear> best = new LinkedHashMap<>();
        if (stack.isEmpty() || stack.isBroken()) return List.of();
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (!isPair(pair) || slot(pair.primaryId()) != slot || !GrapnelSkills.applies(pair, stack)) continue;
            Gear previous = best.get(pair.primaryId());
            if (previous == null || pair.primaryLevel() > previous.pair().primaryLevel())
                best.put(pair.primaryId(), new Gear(slot, stack, pair));
        }
        return List.copyOf(best.values());
    }
    static @Nullable Gear selected(ServerPlayer player, EquipmentSlot slot) {
        List<Gear> selected = selectedAll(player, slot);
        return selected.isEmpty() ? null : selected.getFirst();
    }
    static boolean stillEquipped(ServerPlayer player, @Nullable Gear gear) {
        return gear != null && player.getItemBySlot(gear.slot()) == gear.stack() && !gear.stack().isEmpty() && !gear.stack().isBroken()
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            && GrapnelSkills.applies(gear.pair(), gear.stack());
    }
    static boolean unambiguous(Gear gear) {
        String id = gear.pair().primaryId();
        long references = gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()
            .stream().filter(pair -> pair.involves(id)).count();
        return references == 1 && gear.stack().getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of())
            .stream().noneMatch(retained -> id.equals(retained.id()));
    }
    static @Nullable PrimaryAttributeProjection.Evidence evidence(ServerPlayer player, @Nullable Gear gear) {
        if (!stillEquipped(player, gear) || !unambiguous(gear)) return null;
        if (SOUL.equals(gear.pair().primaryId())) return PrimaryLocationProjection.evidence(player, gear);
        return PrimaryAttributeProjection.evidence(player, gear.source(), attribute(gear.pair().primaryId()));
    }
    private static List<PrimaryAttributeProjection.Source> sources(ServerPlayer player) {
        List<PrimaryAttributeProjection.Source> result = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            for (Gear gear : selectedAll(player, slot)) {
                // Soul Speed is conditional LOCATION_CHANGED, not a permanent ATTRIBUTES effect.
                if (!SOUL.equals(gear.pair().primaryId())) result.add(gear.source());
            }
        }
        return result;
    }
    public static boolean hasEquipped(ServerPlayer player) {
        return selected(player, EquipmentSlot.MAINHAND) != null || selected(player, EquipmentSlot.LEGS) != null
            || selected(player, EquipmentSlot.FEET) != null;
    }
}
