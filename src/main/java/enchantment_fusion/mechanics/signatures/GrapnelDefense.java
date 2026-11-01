package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/** Carrier, slot, and danger-type rules for protection-driven grapnel marks. */
public final class GrapnelDefense {
    public static final String PROTECTION = "minecraft:protection", FIRE = "minecraft:fire_protection";
    public static final String BLAST = "minecraft:blast_protection", PROJECTILE = "minecraft:projectile_protection";
    public static final String FEATHER = "minecraft:feather_falling";
    private static final Set<String> PRIMARIES = Set.of(PROTECTION, FIRE, BLAST, PROJECTILE, FEATHER);
    private static final Set<FusionCarrier> ARMOR = Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR,
        FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int MAX_GEAR = 24;
    private static final ThreadLocal<Boolean> POST_REPLAY = ThreadLocal.withInitial(() -> false);
    public record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private GrapnelDefense() {}

    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && PRIMARIES.contains(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (!isPair(pair) || roles == null) return false;
        if (FEATHER.equals(pair.primaryId())) return roles.contains(FusionCarrier.FOOT_ARMOR);
        boolean armor = roles.stream().anyMatch(ARMOR::contains);
        return armor || (!FIRE.equals(pair.primaryId()) && roles.contains(FusionCarrier.SHIELD));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return isPair(pair) && GrapnelSkills.applies(pair, stack);
    }
    public static boolean matchesDanger(FusedPair pair, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return false;
        return switch (pair.primaryId()) {
            case PROTECTION -> true;
            case FIRE -> source.is(DamageTypeTags.IS_FIRE);
            case BLAST -> source.is(DamageTypeTags.IS_EXPLOSION);
            case PROJECTILE -> source.is(DamageTypeTags.IS_PROJECTILE);
            case FEATHER -> source.is(DamageTypeTags.IS_FALL);
            default -> false;
        };
    }
    public static List<Gear> equipped(ServerPlayer player) {
        List<Gear> result = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!FireTradeoffSkills.slotMatches(stack, slot)) continue;
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                if (applies(pair, stack)) result.add(new Gear(slot, stack, pair));
                if (result.size() >= MAX_GEAR) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }
    public static boolean stillEquipped(ServerPlayer player, Gear gear) {
        return gear != null && player.getItemBySlot(gear.slot()) == gear.stack() && !gear.stack().isEmpty()
            && FireTradeoffSkills.slotMatches(gear.stack(), gear.slot()) && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    public static boolean hasEquipped(ServerPlayer player) { return !equipped(player).isEmpty(); }
    public static void clearServer() { POST_REPLAY.remove(); GrapnelDefenseCapture.clear(); }

    /** Replay other non-native defense post-attack components once per item/id; Protection shares MeleeGuard's replay. */
    public static void replayVictimPostAttack(ServerLevel level, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(POST_REPLAY.get()) || !(victim instanceof ServerPlayer player) || player.level() != level) return;
        POST_REPLAY.set(true);
        try {
            List<Gear> gears = equipped(player);
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = player.getItemBySlot(slot); Map<String, Integer> levels = new LinkedHashMap<>();
                for (Gear gear : gears) if (gear.slot() == slot && gear.stack() == stack && stillEquipped(player, gear)
                    && !PROTECTION.equals(gear.pair().primaryId())) levels.merge(gear.pair().primaryId(), gear.pair().primaryLevel(), Math::max);
                for (var entry : levels.entrySet()) {
                    Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), entry.getKey()).orElse(null);
                    if (primary == null) continue;
                    int nativeLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary);
                    if (nativeLevel > 0 && primary.value().matchingSlot(slot)) continue;
                    primary.value().doPostAttack(level, Math.max(nativeLevel, entry.getValue()),
                        new EnchantedItemInUse(stack, slot, player), EnchantmentTarget.VICTIM, victim, source);
                }
            }
        } finally { POST_REPLAY.remove(); }
    }
}
