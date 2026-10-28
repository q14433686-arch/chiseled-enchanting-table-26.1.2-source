package enchantment_fusion.model;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable DataComponent payload storing up to {@link #HARD_MAX_VESSEL_SLOTS} fused pairs on an item.
 */
public record FusedEnchantmentList(List<FusedPair> pairs) {
    public static final int HARD_MAX_VESSEL_SLOTS = 2;
    public static final FusedEnchantmentList EMPTY = new FusedEnchantmentList(List.of());

    public FusedEnchantmentList {
        if (pairs == null || pairs.isEmpty()) {
            pairs = List.of();
        } else {
            int limit = Math.min(HARD_MAX_VESSEL_SLOTS, pairs.size());
            pairs = List.copyOf(pairs.subList(0, limit));
        }
    }

    public static final Codec<FusedEnchantmentList> CODEC = FusedPair.CODEC
        .listOf(0, HARD_MAX_VESSEL_SLOTS)
        .xmap(FusedEnchantmentList::new, FusedEnchantmentList::pairs);

    public static final StreamCodec<ByteBuf, FusedEnchantmentList> STREAM_CODEC = FusedPair.STREAM_CODEC
        .apply(ByteBufCodecs.list(HARD_MAX_VESSEL_SLOTS))
        .map(FusedEnchantmentList::new, FusedEnchantmentList::pairs);

    public boolean isEmpty() {
        return pairs.isEmpty();
    }

    public int size() {
        return pairs.size();
    }

    public boolean hasAberration() {
        for (FusedPair pair : pairs) {
            if (pair.isAberrant()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsEnchantmentId(String enchantmentId) {
        for (FusedPair pair : pairs) {
            if (pair.involves(enchantmentId)) {
                return true;
            }
        }
        return false;
    }

    public FusedEnchantmentList append(FusedPair next) {
        List<FusedPair> copy = new ArrayList<>(pairs);
        if (copy.size() >= HARD_MAX_VESSEL_SLOTS) {
            throw new IllegalStateException("Cannot exceed hard vessel slot cap of " + HARD_MAX_VESSEL_SLOTS);
        }
        copy.add(next);
        return new FusedEnchantmentList(copy);
    }

    public FusedEnchantmentList replaceAt(int index, FusedPair updated) {
        List<FusedPair> copy = new ArrayList<>(pairs);
        copy.set(index, updated);
        return new FusedEnchantmentList(copy);
    }

    public FusedEnchantmentList removeAt(int index) {
        List<FusedPair> copy = new ArrayList<>(pairs);
        copy.remove(index);
        return copy.isEmpty() ? EMPTY : new FusedEnchantmentList(copy);
    }
}
