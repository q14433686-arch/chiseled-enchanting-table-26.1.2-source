package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.*;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.menu.PayloadLimits;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real codecs, not source-string checks. No item/registry bootstrap required for these fixtures. */
class NetworkCompatibilityTest {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("audit", path); }

    private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            codec.encode(buf, value);
            assertEquals(value, codec.decode(buf));
            assertEquals(0, buf.readableBytes(), "Unconsumed bytes: encoder/decoder field drift");
        } finally { buf.release(); }
    }

    @Test void openingAndLiveShelfCodecsPreserveAllFields() {
        var unlocked = Set.of(new EnchantmentWithLevel(id("one"), 1), new EnchantmentWithLevel(id("two"), 255));
        var masters = Map.of(id("one"), 7, id("two"), 255);
        var display = new TransformationDisplay(id("rule"), id("input_tag"), true, id("out"), 2, 30, 3);
        roundTrip(AvailableEnchantmentPayload.AVAILABLE_CHISELED_ENCHANTMENT_CODEC,
            new AvailableEnchantmentPayload(unlocked, List.of(display), 37, 38, 123, 17, -123456789, masters));
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(unlocked, 37, 38, 123, 17, masters));
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(Set.of(), 0, 0, 0, 0, Map.of()));
    }

    @Test void allActionTokensAndNoBodyPacketsRoundTrip() {
        for (OptionType type : OptionType.values()) {
            roundTrip(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC,
                new ApplyEnchantmentPayload(type, id("action"), 255));
        }
        roundTrip(RerollEpiphanyPayload.CODEC, new RerollEpiphanyPayload());
        roundTrip(FreezeEpiphanyPayload.CODEC, new FreezeEpiphanyPayload());
    }

    @Test void costTableConfigurationAndRowsRoundTrip() {
        // No ItemStack fields in this fixture: validates every config field/order headlessly.
        var value = new EnchantCostTablePayload(true, false, true, false, 0.5,
            33, 0.13, 0.02, 0.03, 0.9, 4, 12, 1234, 500, 1200, 7, List.of(),
            11, List.of(), Set.of(id("treasure")), BookshelfProgression.Config.DEFAULT,
            LibraryResonance.Config.DEFAULT,
            List.of(new EnchantCostTablePayload.CostEntry(OptionType.PURGE_CURSE, id("curse"), 3, 42, List.of())));
        roundTrip(EnchantCostTablePayload.CODEC, value);
    }

    @Test void unknownActionsCannotSilentlyBecomeEnchantment() {
        assertThrows(IllegalArgumentException.class, () -> OptionType.fromToken("new_or_mismatched_action"));
    }

    @Test void countsAreValidatedBeforeAllocationAndIteration() {
        for (int count : new int[] {-1, Integer.MAX_VALUE, PayloadLimits.MAX_COLLECTION_ENTRIES + 1}) {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> PayloadLimits.readCount(buf));
            } finally { buf.release(); }
        }
        var truncated = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            truncated.writeVarInt(1); // missing first enchantment ID, must fail rather than accept empty state
            assertThrows(RuntimeException.class, () -> LiveShelfStatePayload.CODEC.decode(truncated));
        } finally { truncated.release(); }
    }
}
