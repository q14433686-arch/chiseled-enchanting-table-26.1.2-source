package chiseled_enchanting_table.research;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchKnowledgeTest {
    @Test void duplicatesNeverIncreaseBreadth() {
        var k = new ResearchKnowledge();
        for (int i=0;i<64;i++) k.include("minecraft:sharpness",3);
        assertEquals(Map.of("minecraft:sharpness",3),k.snapshot());
    }
    @Test void qualityIsBestLevelNotSumOrLastSeen() {
        var k = new ResearchKnowledge(); k.include("minecraft:sharpness",2); k.include("minecraft:sharpness",5); k.include("minecraft:sharpness",1);
        assertEquals(5,k.snapshot().get("minecraft:sharpness"));
    }
    @Test void differentNamespacesRemainDistinct() {
        var k = new ResearchKnowledge(); k.include("minecraft:sharpness",1); k.include("other:sharpness",1);
        assertEquals(2,k.snapshot().size());
    }
    @Test void snapshotsDoNotChangeDuringLaterScanning() {
        var k = new ResearchKnowledge(); k.include("minecraft:sharpness",1); var first=k.snapshot(); k.include("minecraft:efficiency",2);
        assertEquals(1,first.size()); assertThrows(UnsupportedOperationException.class,first::clear);
    }
    @Test void invalidLevelsCannotPolluteKnowledge() {
        var k = new ResearchKnowledge();
        assertThrows(IllegalArgumentException.class,() -> k.include("minecraft:sharpness",0));
        assertThrows(IllegalArgumentException.class,() -> k.include("minecraft:sharpness",256)); assertTrue(k.snapshot().isEmpty());
    }
}
