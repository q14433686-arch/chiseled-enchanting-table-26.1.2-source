package chiseled_enchanting_table.utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record EnchantmentWithLevel(Identifier enchantment_id, int enchantment_level) {
    public static final int MIN_WIRE_LEVEL = 1;
    public static final int MAX_WIRE_LEVEL = 255;

    public EnchantmentWithLevel {
        java.util.Objects.requireNonNull(enchantment_id, "enchantment_id");
    }

    /**
     * Bounded network codec (audit {@code CODEC-03}): rejects out-of-range levels {@code < 1}
     * or {@code > 255} on wire decode so malformed packets cannot poison enchantment caches.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentWithLevel> ENCHANTMENT_WITH_LEVEL_CODEC = StreamCodec.of(
        (buf, value) -> {
            Identifier.STREAM_CODEC.encode(buf, value.enchantment_id());
            int lv = value.enchantment_level();
            if (lv < MIN_WIRE_LEVEL || lv > MAX_WIRE_LEVEL) {
                throw new IllegalArgumentException("Enchantment level out of wire bounds [1..255]: " + lv);
            }
            ByteBufCodecs.VAR_INT.encode(buf, lv);
        },
        buf -> {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            int lv = ByteBufCodecs.VAR_INT.decode(buf);
            if (lv < MIN_WIRE_LEVEL || lv > MAX_WIRE_LEVEL) {
                throw new IllegalArgumentException("Enchantment level out of wire bounds [1..255]: " + lv);
            }
            return new EnchantmentWithLevel(id, lv);
        }
    );
}
