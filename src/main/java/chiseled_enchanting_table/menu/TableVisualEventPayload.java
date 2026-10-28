package chiseled_enchanting_table.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Lightweight clientbound visual event packet ({@code 26_1_2_revision_21}) driving custom
 * 3D pixel-art particles ({@code scorched_page.png}, {@code arcane_rune_spark.png}),
 * directional bookshelf-slot tethers/backlash beams, and GUI micro-animations.
 */
public record TableVisualEventPayload(
    Kind kind,
    BlockPos tablePos,
    BlockPos targetPos,
    int slot,
    int facing2D,
    int argbColor
) implements CustomPacketPayload {

    public enum Kind {
        /** FORCE_RUSH burned a Master Tome at (targetPos, slot): crimson beam + 6s scorched_page beacon + 220ms panel jolt. */
        RUSH_BACKLASH,
        /** Master Tome at (targetPos, slot) feeding energy into tablePos during copy or active ritual countdown. */
        TOME_TETHER,
        /** Table restoring or sanctifying a tome at (targetPos, slot) (SAFE_ABORT rollback, MEND_ONE, SANCTIFY_CATALYST). */
        TOME_RESTORE,
        /** Depleted Tome sacrificed for XP or ritual acceleration: fluttering scorched pages + warm amber sparks. */
        SACRIFICE_PAGES,
        /** Ritual inscription, fuel boost, or catalyst instant-complete ring + GUI target-slot ripple. */
        RITUAL_COMPLETE,
        /** Curse purge / purge-all-curses sacred-gold purification column + GUI gold slot ripple. */
        CLEANSE_PURGE_CURSE,
        /** Enchantment extraction to book or targeted strip jade-mint swirl + GUI jade-mint slot ripple. */
        CLEANSE_EXTRACT_BOOK,
        /** Mending gear directly repaired by Cleanse XP refund around targetPos (player block pos). */
        MENDING_REPAIR,
        /** Epiphany frozen into ice: frost-blue rune crystal ring at tablePos. */
        EPIPHANY_FREEZE,
        /** Epiphany reroll, Arcane Decipher, or item transformation completion pulse + GUI slot ripple. */
        ARCANE_PULSE,
        /** Table cloth dyed, jeb_ rainbow cloth activated, or washed clean with water/wet sponge. */
        CLOTH_DYE_OR_WASH
    }

    public static final Type<TableVisualEventPayload> TYPE =
        new Type<>(EnchantingProtocol.id("table_visual_event"));

    private static final Kind[] KINDS = Kind.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, TableVisualEventPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind.ordinal());
                buf.writeBlockPos(p.tablePos);
                buf.writeBlockPos(p.targetPos);
                buf.writeVarInt(p.slot);
                buf.writeVarInt(p.facing2D);
                buf.writeInt(p.argbColor);
            },
            buf -> {
                int ord = buf.readVarInt();
                Kind k = (ord >= 0 && ord < KINDS.length) ? KINDS[ord] : Kind.ARCANE_PULSE;
                BlockPos table = buf.readBlockPos();
                BlockPos target = buf.readBlockPos();
                int slot = Math.max(-1, Math.min(5, buf.readVarInt()));
                int facing = Math.max(-1, Math.min(3, buf.readVarInt()));
                int argb = buf.readInt();
                return new TableVisualEventPayload(k, table, target, slot, facing, argb);
            }
        );

    public Direction shelfFacingOrSouth() {
        return facing2D >= 0 && facing2D <= 3 ? Direction.from2DDataValue(facing2D) : Direction.SOUTH;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
