package enchantment_fusion.mechanics.signatures;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

/** Snapshot the ordinary item armor modifiers before an explosion can break the item. */
public final class BlastArmorCapture {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private BlastArmorCapture() {}

    public record Frame(ServerPlayer player, DamageSource source, @Nullable Float withoutSelectedArmor) {}

    public static Frame begin(ServerPlayer player, DamageSource source) {
        Float without = source.is(DamageTypeTags.IS_EXPLOSION) ? calculateWithoutSelected(player) : null;
        Frame frame = new Frame(player, source, without);
        FRAMES.get().push(frame); // Includes an empty nested frame; never reuse its parent's data.
        return frame;
    }

    public static float currentArmor(ServerPlayer player, DamageSource source, float vanilla) {
        Frame frame = FRAMES.get().peek();
        if (frame == null || frame.player() != player || frame.source() != source || frame.withoutSelectedArmor() == null) {
            return vanilla;
        }
        return frame.withoutSelectedArmor();
    }

    public static void end(Frame frame) {
        Deque<Frame> stack = FRAMES.get();
        if (stack.peek() == frame) stack.pop(); else stack.remove(frame);
        if (stack.isEmpty()) FRAMES.remove();
    }

    private static @Nullable Float calculateWithoutSelected(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ARMOR);
        if (instance == null) return null;
        Set<Identifier> excluded = new HashSet<>();
        for (EquipmentSlot slot : BlastDefenseSkills.ARMOR_SLOTS) {
            var gear = BlastDefenseSkills.findInSlot(player, slot, true);
            if (gear == null) continue;
            ItemAttributeModifiers modifiers = gear.stack().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            modifiers.forEach(slot, (attribute, modifier) -> {
                // Only this actual item's ordinary armor modifiers, not armor enchantments,
                // toughness, armor from other slots, status effects or base attributes.
                if (attribute.equals(Attributes.ARMOR) && modifier.equals(instance.getModifier(modifier.id()))) {
                    excluded.add(modifier.id());
                }
            });
        }
        if (excluded.isEmpty()) return null;
        double base = instance.getBaseValue();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
        }
        double result = base;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += base * modifier.amount();
        }
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1.0D + modifier.amount();
        }
        result = instance.getAttribute().value().sanitizeValue(result);
        if (!Double.isFinite(result)) return null;
        return (float) Math.floor(result); // Match LivingEntity#getArmorValue.
    }
}
