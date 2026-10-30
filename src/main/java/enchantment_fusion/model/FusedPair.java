package enchantment_fusion.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Immutable ordered binary fusion {@code [A(primaryLevel) ▷ B(secondaryLevel)]}.
 * Enchantment IDs, string bounds, and level clamping {@code [1, 255]} are enforced symmetrically
 * across the compact constructor, persistent {@link #CODEC}, and network {@link #STREAM_CODEC} (API-5 / DATA-01 / DATA-2).
 */
public record FusedPair(
    String primaryId,
    int primaryLevel,
    String secondaryId,
    int secondaryLevel,
    String aberrationId
) {
    public static final int MAX_ENCHANT_ID_LENGTH = 128;
    public static final int MAX_ABERRATION_ID_LENGTH = 64;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 255;

    public static String canonicalizeEnchantId(String raw) {
        Objects.requireNonNull(raw, "enchantmentId");
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_ENCHANT_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid enchantment identifier length: " + raw);
        }
        Identifier parsed = Identifier.tryParse(trimmed);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid enchantment identifier: " + raw);
        }
        String canonical = parsed.toString();
        if (canonical.length() > MAX_ENCHANT_ID_LENGTH) {
            throw new IllegalArgumentException("Canonical enchantment identifier exceeds " + MAX_ENCHANT_ID_LENGTH + " characters: " + raw);
        }
        return canonical;
    }

    public static String canonicalizeAberrationId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_ABERRATION_ID_LENGTH
            ? trimmed.substring(0, MAX_ABERRATION_ID_LENGTH)
            : trimmed;
    }

    private static final Codec<String> ENCHANT_ID_CODEC = Codec.STRING.comapFlatMap(
        raw -> {
            if (raw == null) {
                return DataResult.error(() -> "Null enchantment identifier");
            }
            try {
                return DataResult.success(canonicalizeEnchantId(raw));
            } catch (IllegalArgumentException ex) {
                return DataResult.error(ex::getMessage);
            }
        },
        s -> s
    );

    private static final Codec<Integer> CLAMPED_LEVEL_CODEC = Codec.INT.xmap(
        v -> Math.clamp(v, MIN_LEVEL, MAX_LEVEL),
        v -> Math.clamp(v, MIN_LEVEL, MAX_LEVEL)
    );

    private static final StreamCodec<ByteBuf, String> ENCHANT_ID_STREAM =
        ByteBufCodecs.stringUtf8(MAX_ENCHANT_ID_LENGTH).map(
            raw -> {
                try {
                    return canonicalizeEnchantId(raw);
                } catch (IllegalArgumentException ex) {
                    throw new DecoderException(ex.getMessage(), ex);
                }
            },
            id -> {
                try {
                    return canonicalizeEnchantId(id);
                } catch (IllegalArgumentException ex) {
                    throw new EncoderException(ex.getMessage(), ex);
                }
            }
        );

    private static final StreamCodec<ByteBuf, Integer> CLAMPED_LEVEL_STREAM =
        ByteBufCodecs.VAR_INT.map(
            v -> Math.clamp(v, MIN_LEVEL, MAX_LEVEL),
            v -> Math.clamp(v, MIN_LEVEL, MAX_LEVEL)
        );

    public FusedPair {
        primaryId = canonicalizeEnchantId(primaryId);
        secondaryId = canonicalizeEnchantId(secondaryId);
        primaryLevel = Math.clamp(primaryLevel, MIN_LEVEL, MAX_LEVEL);
        secondaryLevel = Math.clamp(secondaryLevel, MIN_LEVEL, MAX_LEVEL);
        aberrationId = canonicalizeAberrationId(aberrationId);
    }

    public static final Codec<FusedPair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENCHANT_ID_CODEC.fieldOf("primary").forGetter(FusedPair::primaryId),
        CLAMPED_LEVEL_CODEC.fieldOf("primary_level").forGetter(FusedPair::primaryLevel),
        ENCHANT_ID_CODEC.fieldOf("secondary").forGetter(FusedPair::secondaryId),
        CLAMPED_LEVEL_CODEC.fieldOf("secondary_level").forGetter(FusedPair::secondaryLevel),
        Codec.STRING.optionalFieldOf("aberration_id", "").forGetter(FusedPair::aberrationId)
    ).apply(instance, FusedPair::new));

    public static final StreamCodec<ByteBuf, FusedPair> STREAM_CODEC = StreamCodec.composite(
        ENCHANT_ID_STREAM, FusedPair::primaryId,
        CLAMPED_LEVEL_STREAM, FusedPair::primaryLevel,
        ENCHANT_ID_STREAM, FusedPair::secondaryId,
        CLAMPED_LEVEL_STREAM, FusedPair::secondaryLevel,
        ByteBufCodecs.stringUtf8(MAX_ABERRATION_ID_LENGTH), FusedPair::aberrationId,
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

    public FusedPair withoutAberration() {
        return new FusedPair(primaryId, primaryLevel, secondaryId, secondaryLevel, "");
    }

    public FusedPair withPrimaryLevel(int newLevel) {
        return new FusedPair(primaryId, newLevel, secondaryId, secondaryLevel, aberrationId);
    }

    public FusedPair withSecondaryLevel(int newLevel) {
        return new FusedPair(primaryId, primaryLevel, secondaryId, newLevel, aberrationId);
    }

    /** Deterministic wire token binding a specific pair state for FLIP / SPLIT race guards (NET-1). */
    public String fingerprint() {
        return primaryId + ":" + primaryLevel + ">" + secondaryId + ":" + secondaryLevel + "#" + aberrationId;
    }
}
