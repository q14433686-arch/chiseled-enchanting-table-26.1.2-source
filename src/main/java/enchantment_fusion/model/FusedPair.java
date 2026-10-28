package enchantment_fusion.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Immutable ordered binary fusion {@code [A(primaryLevel) ▷ B(secondaryLevel)]}.
 * Enchantment IDs are validated as canonical {@link Identifier} strings and levels are clamped symmetrically.
 */
public record FusedPair(
    String primaryId,
    int primaryLevel,
    String secondaryId,
    int secondaryLevel,
    String aberrationId
) {
    private static final Codec<String> ENCHANT_ID_CODEC = Codec.STRING.comapFlatMap(
        raw -> {
            if (raw == null) {
                return DataResult.error(() -> "Null enchantment identifier");
            }
            Identifier parsed = Identifier.tryParse(raw.trim());
            return parsed != null
                ? DataResult.success(parsed.toString())
                : DataResult.error(() -> "Invalid enchantment identifier: " + raw);
        },
        s -> s
    );

    private static final StreamCodec<ByteBuf, Integer> CLAMPED_LEVEL_STREAM =
        ByteBufCodecs.VAR_INT.map(v -> Math.clamp(v, 1, 255), v -> Math.clamp(v, 1, 255));

    public FusedPair {
        Objects.requireNonNull(primaryId, "primaryId");
        Objects.requireNonNull(secondaryId, "secondaryId");
        Identifier pParsed = Identifier.tryParse(primaryId.trim());
        Identifier sParsed = Identifier.tryParse(secondaryId.trim());
        primaryId = pParsed != null ? pParsed.toString() : primaryId.trim();
        secondaryId = sParsed != null ? sParsed.toString() : secondaryId.trim();
        primaryLevel = Math.clamp(primaryLevel, 1, 255);
        secondaryLevel = Math.clamp(secondaryLevel, 1, 255);
        aberrationId = aberrationId == null ? "" : aberrationId.trim();
    }

    public static final Codec<FusedPair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENCHANT_ID_CODEC.fieldOf("primary").forGetter(FusedPair::primaryId),
        Codec.INT.fieldOf("primary_level").forGetter(FusedPair::primaryLevel),
        ENCHANT_ID_CODEC.fieldOf("secondary").forGetter(FusedPair::secondaryId),
        Codec.INT.fieldOf("secondary_level").forGetter(FusedPair::secondaryLevel),
        Codec.STRING.optionalFieldOf("aberration_id", "").forGetter(FusedPair::aberrationId)
    ).apply(instance, FusedPair::new));

    public static final StreamCodec<ByteBuf, FusedPair> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(128), FusedPair::primaryId,
        CLAMPED_LEVEL_STREAM, FusedPair::primaryLevel,
        ByteBufCodecs.stringUtf8(128), FusedPair::secondaryId,
        CLAMPED_LEVEL_STREAM, FusedPair::secondaryLevel,
        ByteBufCodecs.stringUtf8(64), FusedPair::aberrationId,
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
