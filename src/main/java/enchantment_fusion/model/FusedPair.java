package enchantment_fusion.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable ordered binary fusion {@code [A(primaryLevel) ▷ B(secondaryLevel)]}.
 * Level bounds are clamped strictly to positive ints; vanilla maxLevel caps are enforced by {@code FusionPlanner}.
 */
public record FusedPair(
    String primaryId,
    int primaryLevel,
    String secondaryId,
    int secondaryLevel,
    String aberrationId
) {
    public FusedPair {
        Objects.requireNonNull(primaryId, "primaryId");
        Objects.requireNonNull(secondaryId, "secondaryId");
        primaryId = primaryId.trim();
        secondaryId = secondaryId.trim();
        primaryLevel = Math.max(1, Math.min(255, primaryLevel));
        secondaryLevel = Math.max(1, Math.min(255, secondaryLevel));
        aberrationId = aberrationId == null ? "" : aberrationId.trim();
    }

    public static final Codec<FusedPair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("primary").forGetter(FusedPair::primaryId),
        Codec.INT.fieldOf("primary_level").forGetter(FusedPair::primaryLevel),
        Codec.STRING.fieldOf("secondary").forGetter(FusedPair::secondaryId),
        Codec.INT.fieldOf("secondary_level").forGetter(FusedPair::secondaryLevel),
        Codec.STRING.optionalFieldOf("aberration_id", "").forGetter(FusedPair::aberrationId)
    ).apply(instance, FusedPair::new));

    public static final StreamCodec<ByteBuf, FusedPair> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, FusedPair::primaryId,
        ByteBufCodecs.VAR_INT, FusedPair::primaryLevel,
        ByteBufCodecs.STRING_UTF8, FusedPair::secondaryId,
        ByteBufCodecs.VAR_INT, FusedPair::secondaryLevel,
        ByteBufCodecs.STRING_UTF8, FusedPair::aberrationId,
        FusedPair::new
    );

    public static FusedPair clean(String primaryId, int primaryLevel, String secondaryId, int secondaryLevel) {
        return new FusedPair(primaryId, primaryLevel, secondaryId, secondaryLevel, "");
    }

    public boolean isAberrant() {
        return !aberrationId.isEmpty();
    }

    public boolean involves(String enchantmentId) {
        return primaryId.equals(enchantmentId) || secondaryId.equals(enchantmentId);
    }

    public FusedPair flipped() {
        return new FusedPair(secondaryId, secondaryLevel, primaryId, primaryLevel, aberrationId);
    }

    public FusedPair withPrimaryLevel(int newLevel) {
        return new FusedPair(primaryId, newLevel, secondaryId, secondaryLevel, aberrationId);
    }

    public FusedPair withSecondaryLevel(int newLevel) {
        return new FusedPair(primaryId, primaryLevel, secondaryId, newLevel, aberrationId);
    }
}
