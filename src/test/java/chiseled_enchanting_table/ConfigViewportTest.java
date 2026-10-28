package chiseled_enchanting_table;

import chiseled_enchanting_table.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigViewportTest {
    private ConfigLayoutSpec.ModalLayout layout(int width,int height) {
        return ConfigLayoutSpec.compute(width,height,ModRuntimeConfig.Preset.values().length,ConfigCatalog.CategoryId.values().length+1);
    }
    @Test void actualSmallLogicalViewportContainsModalAndControls() {
        for (int[] size : new int[][]{{320,180},{320,200},{320,213},{320,240},{427,240},{480,270},{854,480}}) {
            var l=layout(size[0],size[1]);
            assertEquals(size[0],l.screenW()); assertEquals(size[1],l.screenH());
            assertTrue(ConfigLayoutSpec.verifyInvariants(l).isEmpty(),size[0]+"x"+size[1]+": "+ConfigLayoutSpec.verifyInvariants(l));
            assertTrue(l.modal().x()>=0 && l.modal().y()>=0);
            assertTrue(l.modal().right()<=size[0] && l.modal().bottom()<=size[1]);
        }
    }
    @Test void smallerHeightActuallyShrinksViewportInsteadOfInventingSpace() {
        assertTrue(layout(320,180).listViewport().h()<layout(320,240).listViewport().h());
        assertTrue(layout(320,180).saveBtn().bottom()<=180);
    }
    @Test void largerViewShowsMoreContentWithoutUnboundedGrowth() {
        var compact=layout(320,213); var large=layout(854,480); var huge=layout(1920,1080);
        assertTrue(large.listViewport().h()>compact.listViewport().h());
        assertEquals(ConfigLayoutSpec.MODAL_TARGET_W,large.modal().w());
        assertEquals(large.modal().w(),huge.modal().w()); assertEquals(large.modal().h(),huge.modal().h());
    }
}
