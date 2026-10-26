package chiseled_enchanting_table.render;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Only rainbow blocks use these five cloth faces. The normal block/item model is untouched. */
final class RainbowClothRenderer {
    private RainbowClothRenderer() {}
    private static final float LO=-.02f/16, HI=16.02f/16, TOP=12.02f/16;
    static void submit(PoseStack pose,SubmitNodeCollector collector,int color,int light) {
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(ChiseledEnchantingTable.identifier("textures/block/enchanting_table_top_overlay.png")),
            (p,v)->quad(p,v,color,light,0,1,0,new float[][]{{LO,TOP,LO},{LO,TOP,HI},{HI,TOP,HI},{HI,TOP,LO}},0));
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(ChiseledEnchantingTable.identifier("textures/block/enchanting_table_side_overlay.png")),(p,v)->{
            quad(p,v,color,light,0,0,-1,new float[][]{{HI,TOP,LO},{HI,LO,LO},{LO,LO,LO},{LO,TOP,LO}},.25f);
            quad(p,v,color,light,0,0,1,new float[][]{{LO,TOP,HI},{LO,LO,HI},{HI,LO,HI},{HI,TOP,HI}},.25f);
            quad(p,v,color,light,-1,0,0,new float[][]{{LO,TOP,LO},{LO,LO,LO},{LO,LO,HI},{LO,TOP,HI}},.25f);
            quad(p,v,color,light,1,0,0,new float[][]{{HI,TOP,HI},{HI,LO,HI},{HI,LO,LO},{HI,TOP,LO}},.25f);
        });
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer out,int color,int light,float nx,float ny,float nz,float[][] points,float topV) {
        float[][] uv=ny==1 ? new float[][]{{0,0},{0,1},{1,1},{1,0}} : new float[][]{{0,topV},{0,1},{1,1},{1,topV}};
        for(int i=0;i<4;i++) out.addVertex(pose,points[i][0],points[i][1],points[i][2]).setColor(color)
            .setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
    }
}
