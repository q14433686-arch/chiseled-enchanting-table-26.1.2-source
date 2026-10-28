package chiseled_enchanting_table;

import static org.junit.jupiter.api.Assertions.*;

import chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects;
import chiseled_enchanting_table.menu.EnchantingProtocol;
import chiseled_enchanting_table.menu.TableVisualEventPayload;
import chiseled_enchanting_table.menu.TableVisualEventPayload.Kind;
import chiseled_enchanting_table.menu.TableVisualEvents;
import io.netty.buffer.Unpooled;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class SensoryPolishRoundThreeTest {

    @Test
    void visualEventPayloadRoundTripsEveryKindAndClampsBounds() {
        assertEquals(8, EnchantingProtocol.CURRENT_REVISION_NUMBER);
        assertEquals("26_2_revision_8", EnchantingProtocol.REVISION);
        assertEquals(
            "chiseled_enchanting_table:table_visual_event_26_2_revision_8",
            TableVisualEventPayload.TYPE.id().toString()
        );

        BlockPos table = new BlockPos(100, 68, -240);
        BlockPos shelf = new BlockPos(102, 69, -239);
        for (Kind kind : Kind.values()) {
            int facing = (kind.ordinal() % 4);
            int slot = (kind.ordinal() % 6);
            TableVisualEventPayload original = new TableVisualEventPayload(
                kind, table, shelf, slot, facing, TableVisualEvents.COLOR_CRIMSON_BACKLASH ^ kind.ordinal()
            );
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                TableVisualEventPayload.CODEC.encode(buf, original);
                TableVisualEventPayload decoded = TableVisualEventPayload.CODEC.decode(buf);
                assertEquals(original, decoded);
                assertEquals(0, buf.readableBytes());
                assertEquals(Direction.from2DDataValue(facing), decoded.shelfFacingOrSouth());
            } finally {
                buf.release();
            }
        }

        // Out-of-bounds wire values clamp safely without throwing
        RegistryFriendlyByteBuf raw = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            raw.writeVarInt(999);
            raw.writeBlockPos(table);
            raw.writeBlockPos(shelf);
            raw.writeVarInt(42);
            raw.writeVarInt(-99);
            raw.writeInt(TableVisualEvents.COLOR_SACRED_GOLD);
            TableVisualEventPayload clamped = TableVisualEventPayload.CODEC.decode(raw);
            assertEquals(Kind.ARCANE_PULSE, clamped.kind());
            assertEquals(5, clamped.slot());
            assertEquals(-1, clamped.facing2D());
            assertEquals(Direction.SOUTH, clamped.shelfFacingOrSouth());
        } finally {
            raw.release();
        }
    }

    @Test
    void slotWorldCoordinatesPinpointExact3x2BookshelfSlotsAcrossAllFacings() {
        BlockPos shelf = new BlockPos(10, 70, 20);
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            double[] topLeft = TomeParticleEffects.slotWorldCoordinates(shelf, dir, 0);
            double[] topCenter = TomeParticleEffects.slotWorldCoordinates(shelf, dir, 1);
            double[] topRight = TomeParticleEffects.slotWorldCoordinates(shelf, dir, 2);
            double[] bottomCenter = TomeParticleEffects.slotWorldCoordinates(shelf, dir, 4);

            assertEquals(70.0D + TomeParticleEffects.SHELF_TOP_ROW_Y, topLeft[1], 1.0E-6D);
            assertEquals(70.0D + TomeParticleEffects.SHELF_TOP_ROW_Y, topCenter[1], 1.0E-6D);
            assertEquals(70.0D + TomeParticleEffects.SHELF_TOP_ROW_Y, topRight[1], 1.0E-6D);
            assertEquals(70.0D + TomeParticleEffects.SHELF_BOTTOM_ROW_Y, bottomCenter[1], 1.0E-6D);

            // Top-center and bottom-center share identical X/Z on the front face
            assertEquals(topCenter[0], bottomCenter[0], 1.0E-6D);
            assertEquals(topCenter[2], bottomCenter[2], 1.0E-6D);

            // Left and right columns are symmetric around center
            assertEquals(topCenter[0] * 2.0D, topLeft[0] + topRight[0], 1.0E-6D);
            assertEquals(topCenter[2] * 2.0D, topLeft[2] + topRight[2], 1.0E-6D);
        }
    }

    @Test
    void customParticleSpritesExistAsClean32x32TransparentRgbaAssets() throws Exception {
        Path base = Path.of("src/client/resources/assets/chiseled_enchanting_table/textures/particle");
        for (String name : new String[] {"scorched_page.png", "arcane_rune_spark.png"}) {
            Path p = base.resolve(name);
            assertTrue(Files.isRegularFile(p), "Missing custom particle sprite: " + p);
            BufferedImage img = ImageIO.read(p.toFile());
            assertNotNull(img, "Unreadable PNG: " + p);
            assertEquals(32, img.getWidth(), name + " width");
            assertEquals(32, img.getHeight(), name + " height");
            assertTrue(img.getColorModel().hasAlpha(), name + " must have alpha channel");

            // Outer 4 corners must be 100% transparent so billboards never show square box edges
            assertEquals(0, (img.getRGB(0, 0) >>> 24) & 0xFF, name + " top-left corner alpha");
            assertEquals(0, (img.getRGB(31, 0) >>> 24) & 0xFF, name + " top-right corner alpha");
            assertEquals(0, (img.getRGB(0, 31) >>> 24) & 0xFF, name + " bottom-left corner alpha");
            assertEquals(0, (img.getRGB(31, 31) >>> 24) & 0xFF, name + " bottom-right corner alpha");

            int visiblePixels = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 32) {
                        visiblePixels++;
                    }
                }
            }
            assertTrue(visiblePixels >= 120 && visiblePixels <= 700,
                name + " visible pixel count out of crisp sprite range: " + visiblePixels);
        }
    }
}
