package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.GrapnelQuickChargeMath;
import org.junit.jupiter.api.Test;

final class GrapnelQuickChargeMathTest {
    @Test void realSingleMatchingReductionCanProveShorterTime() {
        boolean own = GrapnelQuickChargeMath.contribution(1, 2, 2, 1.25F, 0.75F);
        assertTrue(own); assertTrue(GrapnelQuickChargeMath.faster(1, own, 1.25F, 0.75F));
    }
    @Test void ambiguousOrIneffectiveEffectNeverEarnsProof() {
        assertFalse(GrapnelQuickChargeMath.contribution(2, 2, 2, 1.25F, 0.75F));
        assertFalse(GrapnelQuickChargeMath.contribution(1, 1, 2, 1.25F, 0.75F));
        assertFalse(GrapnelQuickChargeMath.contribution(1, 2, 2, 0.0F, -0.5F));
        assertFalse(GrapnelQuickChargeMath.contribution(1, 2, 2, Float.NaN, 0.75F));
        assertFalse(GrapnelQuickChargeMath.faster(1, false, 1.25F, 0.75F));
        assertFalse(GrapnelQuickChargeMath.faster(1, true, 1.25F, 1.25F));
        assertFalse(GrapnelQuickChargeMath.faster(1, true, 1.25F, -0.1F));
        assertFalse(GrapnelQuickChargeMath.faster(1, true, 1.25F, Float.POSITIVE_INFINITY));
    }
}
