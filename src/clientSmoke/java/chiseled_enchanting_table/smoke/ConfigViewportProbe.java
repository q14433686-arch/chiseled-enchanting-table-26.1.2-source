package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.config.ConfigLayoutSpec;
import chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen;
import net.minecraft.client.Minecraft;

/** Real screen init/re-init contract. Not a physical SDL-window resize or all-screen visual test. */
final class ConfigViewportProbe {
    static void verify(Minecraft client) throws Exception {
        var screen=new ChiseledEnchantingConfigScreen(null);
        var field=ChiseledEnchantingConfigScreen.class.getDeclaredField("layout"); field.setAccessible(true);
        int[][] sizes={{320,180},{320,213},{427,240},{854,480},{320,213}};
        for (var size:sizes) {
            screen.initOverlay(client,size[0],size[1]);
            var layout=(ConfigLayoutSpec.ModalLayout)field.get(screen);
            if(layout.screenW()!=size[0] || layout.screenH()!=size[1]) throw new AssertionError("config invented viewport dimensions");
            var errors=ConfigLayoutSpec.verifyInvariants(layout);
            if(!errors.isEmpty()) throw new AssertionError("config viewport "+size[0]+"x"+size[1]+": "+errors);
        }
        screen.removed();
        System.out.println("CONFIG VIEWPORT PASS: real screen reinit honors actual 320x180/320x213/427x240/854x480 bounds; no all-page or physical resize claim");
    }
}
