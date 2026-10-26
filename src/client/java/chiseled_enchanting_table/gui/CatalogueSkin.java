package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** One nine-slice material and explicit ink bounds, shared by text controls and icon-only actions. */
final class CatalogueSkin {
    private CatalogueSkin() {}
    private static Identifier resource(String name) { return ChiseledEnchantingTable.identifier("gui/catalogue_"+name+".png"); }
    static void frame(GuiGraphicsExtractor ctx,int x,int y,int w,int h,boolean hovered,boolean enabled) {
        Identifier texture=resource(!enabled ? "frame_disabled" : hovered ? "frame_hover" : "frame");
        // Preserve corners rather than stretching an entire border under long filter labels.
        int[] xs={x,x+3,x+w-3,x+w}, ys={y,y+3,y+h-3,y+h};
        float[] uv={0,.25f,.75f,1};
        for(int row=0;row<3;row++) for(int col=0;col<3;col++)
            ctx.blit(texture,xs[col],ys[row],xs[col+1],ys[row+1],uv[col],uv[col+1],uv[row],uv[row+1]);
    }
    static void text(GuiGraphicsExtractor ctx,Font font,String label,int x,int y,int w,int h,int inset,int color) {
        int ix=x+inset, iy=y+inset, iw=w-2*inset, ih=h-2*inset;
        if(iw<=0 || ih<=0) return;
        ctx.enableScissor(ix,iy,ix+iw,iy+ih);
        try {
            RowRenderSupport.renderCenteredBoxText(ctx,font,label,ix,iw,iy+ih*.5f,
                Math.min(.60f,(float)ih/font.lineHeight),color);
        } finally { ctx.disableScissor(); }
    }
    static void icon(GuiGraphicsExtractor ctx,String name,int x,int y,int w,int h,boolean hovered,boolean enabled) {
        frame(ctx,x,y,w,h,hovered,enabled);
        int inset=TomeDirectoryLayout.BUTTON_INSET;
        int size=Math.min(7,Math.min(w,h)-2*inset);
        if(size<=0) return;
        int ix=x+(w-size)/2, iy=y+(h-size)/2;
        ctx.enableScissor(x+inset,y+inset,x+w-inset,y+h-inset);
        try {
            ctx.blit(resource("icon_"+name),ix,iy,ix+size,iy+size,0,1,0,1);
            if(!enabled) ctx.fill(ix,iy,ix+size,iy+size,0x990B1520);
        } finally { ctx.disableScissor(); }
    }
}
