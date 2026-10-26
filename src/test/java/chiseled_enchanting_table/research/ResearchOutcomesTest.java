package chiseled_enchanting_table.research;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchOutcomesTest {
    private static final String JSON = """
        {"schema":1,"topics":[{
          "id":"test:efficiency_2","revision":1,"title":"test:title","question":"test:question",
          "prerequisites":[],"breadth":1,"shelves":1,"depth":{"minecraft:efficiency":1},
          "hypotheses":[
            {"id":"test:derive","evidence":["test:field_solution"],"conclusion":"test:efficiency_2_learned"},
            {"id":"test:analyze","evidence":["test:sample_result"],"conclusion":"test:efficiency_2_learned"}]}],
         "outcomes":[{"conclusion":"test:efficiency_2_learned","enchantment":"minecraft:efficiency","branch":"test:general","max_level":2}],
         "sample_analyses":[{"topic":"test:efficiency_2","hypothesis":"test:analyze","evidence":"test:sample_result","enchantment":"minecraft:efficiency","minimum_level":2}]}
        """;
    private ResearchCatalog catalog() { return ResearchJson.catalog(JsonParser.parseString(JSON).getAsJsonObject()); }
    private ResearchCatalog.Library library(int level) { return new ResearchCatalog.Library(1,Map.of("minecraft:efficiency",level)); }
    @Test void lowLevelBookStartsResearchWithoutVanillaMaximum() {
        var c=catalog();
        assertEquals(ResearchEngine.Status.AVAILABLE,ResearchEngine.begin(c.topic("test:efficiency_2"),ResearchJournal.empty(),library(1),"test:derive").status());
    }
    @Test void unlearnedDoesNotManufactureEvenWithHighSample() {
        assertFalse(catalog().outcomes().canManufacture(ResearchJournal.empty(),"minecraft:efficiency","test:general",2));
    }
    @Test void derivationAndAnalysisReachSameManufacturingOutcome() {
        var c=catalog(); var t=c.topic("test:efficiency_2");
        var derived=ResearchEngine.begin(t,ResearchJournal.empty(),library(1),"test:derive").journal();
        derived=ResearchEngine.observe(t,derived,"test:field_solution").journal();
        derived=ResearchEngine.conclude(t,derived,library(1)).journal();
        var analyzed=ResearchEngine.begin(t,ResearchJournal.empty(),library(1),"test:analyze").journal();
        analyzed=c.analyses().analyze(t,analyzed,library(2)).journal();
        analyzed=ResearchEngine.conclude(t,analyzed,library(2)).journal();
        assertEquals(derived,analyzed);
        assertTrue(c.outcomes().canManufacture(derived,"minecraft:efficiency","test:general",2));
        assertFalse(c.outcomes().canManufacture(derived,"minecraft:efficiency","test:general",3));
    }
    @Test void analysisChecksActualSampleQuality() {
        var c=catalog(); var t=c.topic("test:efficiency_2");
        var j=ResearchEngine.begin(t,ResearchJournal.empty(),library(1),"test:analyze").journal();
        assertEquals(ResearchEngine.Status.EVIDENCE,c.analyses().analyze(t,j,library(1)).status());
        assertEquals(j,c.analyses().analyze(t,j,library(1)).journal());
    }
    @Test void analysisDoesNotConcludeOrConsumeLibrary() {
        var c=catalog(); var t=c.topic("test:efficiency_2"); var lib=library(2);
        var j=ResearchEngine.begin(t,ResearchJournal.empty(),lib,"test:analyze").journal();
        var result=c.analyses().analyze(t,j,lib);
        assertEquals(ResearchEngine.Status.OBSERVED,result.status()); assertTrue(result.journal().conclusions().isEmpty());
        assertEquals(Map.of("minecraft:efficiency",2),lib.knowledge());
    }
    @Test void analysisCannotSubstituteForUnchosenPracticeRoute() {
        var c=catalog(); var t=c.topic("test:efficiency_2");
        var j=ResearchEngine.begin(t,ResearchJournal.empty(),library(2),"test:derive").journal();
        assertEquals(ResearchEngine.Status.EVIDENCE,c.analyses().analyze(t,j,library(2)).status());
    }
    @Test void replayAndSecondRouteDoNotGrantExtraProgress() {
        var c=catalog(); var t=c.topic("test:efficiency_2");
        var j=ResearchEngine.begin(t,ResearchJournal.empty(),library(2),"test:analyze").journal();
        j=c.analyses().analyze(t,j,library(2)).journal();
        assertEquals(ResearchEngine.Status.DUPLICATE,c.analyses().analyze(t,j,library(2)).status());
        j=ResearchEngine.conclude(t,j,library(2)).journal();
        assertEquals(ResearchEngine.Status.COMPLETE,ResearchEngine.begin(t,j,library(1),"test:derive").status());
    }
    @Test void branchCapsNeverLeakIntoEachOther() {
        var o=new ResearchOutcomes(List.of(new ResearchOutcomes.Grant("test:learned","minecraft:sharpness","test:cutting",10),
            new ResearchOutcomes.Grant("test:learned","minecraft:sharpness","test:steady",7)),Set.of("test:learned"));
        var j=new ResearchJournal(Set.of("test:learned"),Map.of());
        assertEquals(10,o.manufacturingLimit(j,"minecraft:sharpness","test:cutting"));
        assertEquals(7,o.manufacturingLimit(j,"minecraft:sharpness","test:steady"));
        assertEquals(0,o.manufacturingLimit(j,"minecraft:sharpness","test:unknown"));
    }
    @Test void outcomesMustReferenceRealConclusions() {
        assertThrows(IllegalArgumentException.class,() -> new ResearchOutcomes(List.of(
            new ResearchOutcomes.Grant("test:missing","minecraft:efficiency","test:general",2)),Set.of()));
    }
    @Test void oldCatalogWithoutOutcomesStillLoads() {
        var root=JsonParser.parseString(JSON).getAsJsonObject(); root.remove("outcomes"); root.remove("sample_analyses");
        assertTrue(ResearchJson.catalog(root).outcomes().grants().isEmpty());
    }
    @Test void invalidAnalysisCannotFabricateEvidence() {
        var root=JsonParser.parseString(JSON).getAsJsonObject();
        root.getAsJsonArray("sample_analyses").get(0).getAsJsonObject().addProperty("evidence","test:fake");
        assertThrows(IllegalArgumentException.class,() -> ResearchJson.catalog(root));
    }
    @Test void savedLearningStillResolvesAfterRoundTrip() {
        var j=new ResearchJournal(Set.of("test:efficiency_2_learned"),Map.of());
        assertEquals(2,catalog().outcomes().manufacturingLimit(ResearchJson.journal(ResearchJson.journal(j)),"minecraft:efficiency","test:general"));
    }
}
