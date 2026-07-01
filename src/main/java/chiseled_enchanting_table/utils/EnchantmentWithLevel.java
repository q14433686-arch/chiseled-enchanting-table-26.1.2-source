package chiseled_enchanting_table.utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record EnchantmentWithLevel(Identifier enchantment_id, int enchantment_level) {
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentWithLevel> ENCHANTMENT_WITH_LEVEL_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC,
        EnchantmentWithLevel::enchantment_id,
        ByteBufCodecs.VAR_INT,
        EnchantmentWithLevel::enchantment_level,
        EnchantmentWithLevel::new
    );
}
