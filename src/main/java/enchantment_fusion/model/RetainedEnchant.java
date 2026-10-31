package enchantment_fusion.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A secondary enchantment preserved when its spent fusion cannot be materialized without a collision. */
public record RetainedEnchant(String id, int level) {
    public RetainedEnchant {
        id = FusedPair.canonicalizeEnchantId(id);
        if (level < 1 || level > FusedPair.MAX_LEVEL) throw new IllegalArgumentException("Invalid retained level: " + level);
    }

    /**
     * Bind the choice to the entire ordered preserved list, not just its first entry.
     * Thus a delayed duplicate packet cannot consume a second identical entry after
     * the first is extracted. The bounded digest also fits the station packet limit.
     */
    public static String selectionToken(List<RetainedEnchant> entries, int index) {
        if (entries == null || index < 0 || index >= entries.size()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RetainedEnchant entry : entries) {
                digest.update(entry.id().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0); // IDs cannot contain NUL.
                digest.update((byte) entry.level());
            }
            return "retained:" + index + ":" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by Java", ex);
        }
    }

    /** Bind the list selection to the shared table's session-only target version. */
    public static String versionedSelectionToken(List<RetainedEnchant> entries, int index, long targetRevision) {
        String listToken = selectionToken(entries, index);
        return targetRevision <= 0 || listToken.isEmpty() ? "" : listToken + "@" + targetRevision;
    }

    private static final Codec<String> ID_CODEC = Codec.STRING.comapFlatMap(raw -> {
        try { return DataResult.success(FusedPair.canonicalizeEnchantId(raw)); }
        catch (IllegalArgumentException | NullPointerException ex) { return DataResult.error(ex::getMessage); }
    }, id -> id);
    private static final StreamCodec<ByteBuf, String> ID_STREAM = ByteBufCodecs.stringUtf8(FusedPair.MAX_ENCHANT_ID_LENGTH).map(
        raw -> {
            try { return FusedPair.canonicalizeEnchantId(raw); }
            catch (IllegalArgumentException | NullPointerException ex) { throw new DecoderException(ex.getMessage(), ex); }
        },
        raw -> {
            try { return FusedPair.canonicalizeEnchantId(raw); }
            catch (IllegalArgumentException | NullPointerException ex) { throw new EncoderException(ex.getMessage(), ex); }
        }
    );
    private static final StreamCodec<ByteBuf, Integer> LEVEL_STREAM = ByteBufCodecs.VAR_INT.map(
        n -> {
            if (n < 1 || n > FusedPair.MAX_LEVEL) throw new DecoderException("Invalid retained level: " + n);
            return n;
        },
        n -> {
            if (n < 1 || n > FusedPair.MAX_LEVEL) throw new EncoderException("Invalid retained level: " + n);
            return n;
        }
    );
    public static final Codec<RetainedEnchant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ID_CODEC.fieldOf("id").forGetter(RetainedEnchant::id),
        Codec.intRange(1, FusedPair.MAX_LEVEL).fieldOf("level").forGetter(RetainedEnchant::level)
    ).apply(instance, RetainedEnchant::new));
    public static final StreamCodec<ByteBuf, RetainedEnchant> STREAM_CODEC = StreamCodec.composite(
        ID_STREAM, RetainedEnchant::id,
        LEVEL_STREAM, RetainedEnchant::level,
        RetainedEnchant::new
    );
}
