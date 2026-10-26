package chiseled_enchanting_table.render;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.DyeColor;

/** Only rainbow blocks use these five cloth faces. The normal block/item model is untouched. */
@Environment(EnvType.CLIENT)
final class RainbowClothRenderer {
    private RainbowClothRenderer() {}
    private static final float LO = -.02f / 16, HI = 16.02f / 16, TOP = 12.02f / 16;
    private static final net.minecraft.resources.Identifier TOP_TEXTURE = ChiseledEnchantingTable.identifier("textures/block/enchanting_table_top_overlay.png");
    private static final net.minecraft.resources.Identifier SIDE_TEXTURE = ChiseledEnchantingTable.identifier("textures/block/enchanting_table_side_overlay.png");
    private static final float[][] TOP_FACE = {{LO, TOP, LO}, {LO, TOP, HI}, {HI, TOP, HI}, {HI, TOP, LO}};
    private static final float[][] NORTH = {{HI, TOP, LO}, {HI, LO, LO}, {LO, LO, LO}, {LO, TOP, LO}};
    private static final float[][] SOUTH = {{LO, TOP, HI}, {LO, LO, HI}, {HI, LO, HI}, {HI, TOP, HI}};
    private static final float[][] WEST = {{LO, TOP, LO}, {LO, LO, LO}, {LO, LO, HI}, {LO, TOP, HI}};
    private static final float[][] EAST = {{HI, TOP, HI}, {HI, LO, HI}, {HI, LO, LO}, {HI, TOP, LO}};
    private static final float[][] TOP_UV = {{0, 0}, {0, 1}, {1, 1}, {1, 0}}, SIDE_UV = {{0, .25f}, {0, 1}, {1, 1}, {1, .25f}};

    /**
     * Vanilla {@code jeb_} sheep cycle on this version's dye palette: 16 diffuse colours,
     * 25 ticks each, linearly interpolated (26.3 reuses its {@code ColorLerper}; 26.1.2 has no
     * equivalent public helper, so the palette walk is spelled out here).
     */
    static int jebColor(long gameTime, float partialTick) {
        float phase = chiseled_enchanting_table.cosmetic.RainbowCloth.phase(gameTime, partialTick);
        int step = Math.min(15, (int) (phase / 25.0f));
        float frac = (phase - step * 25.0f) / 25.0f;
        int a = DyeColor.byId(step).getTextureDiffuseColor();
        int b = DyeColor.byId((step + 1) % 16).getTextureDiffuseColor();
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * frac);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * frac);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * frac);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static void submit(PoseStack pose, SubmitNodeCollector collector, int color, int light) {
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TOP_TEXTURE),
            (p, v) -> quad(p, v, color, light, 0, 1, 0, TOP_FACE, TOP_UV));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(SIDE_TEXTURE), (p, v) -> {
            quad(p, v, color, light, 0, 0, -1, NORTH, SIDE_UV);
            quad(p, v, color, light, 0, 0, 1, SOUTH, SIDE_UV);
            quad(p, v, color, light, -1, 0, 0, WEST, SIDE_UV);
            quad(p, v, color, light, 1, 0, 0, EAST, SIDE_UV);
        });
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int color, int light, float nx, float ny, float nz, float[][] points, float[][] uv) {
        for (int i = 0; i < 4; i++) out.addVertex(pose, points[i][0], points[i][1], points[i][2]).setColor(color)
            .setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
