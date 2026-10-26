package chiseled_enchanting_table;
import chiseled_enchanting_table.cosmetic.RainbowCloth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RainbowClothTest {
    @Test void exactVanillaNameOnly() { assertTrue(RainbowCloth.magicName("jeb_"));assertFalse(RainbowCloth.magicName("_JEB"));assertFalse(RainbowCloth.magicName("Jeb_"));assertFalse(RainbowCloth.magicName(null)); }
    @Test void phaseLoopsEveryTwentySeconds() { assertEquals(RainbowCloth.phase(12,.5f),RainbowCloth.phase(412,.5f)); }
    @Test void partialTickIsNotDiscarded() { assertEquals(12.5f,RainbowCloth.phase(12,.5f)); }
    @Test void hugeWorldAgeKeepsFractionalPrecision() { assertEquals(.5f,RainbowCloth.phase(4_000_000_000L,.5f)); }
    @Test void wrapIsContinuous() { assertEquals(399.5f,RainbowCloth.phase(399,.5f));assertEquals(0,RainbowCloth.phase(399,1)); }
    @Test void invalidPartialTicksAreClamped() { assertEquals(0,RainbowCloth.phase(0,Float.NaN));assertEquals(0,RainbowCloth.phase(0,-1));assertEquals(1,RainbowCloth.phase(0,2)); }
    @Test void negativeTicksStillProduceValidPaletteIndex() { assertEquals(399,RainbowCloth.phase(-1,0)); }
}
