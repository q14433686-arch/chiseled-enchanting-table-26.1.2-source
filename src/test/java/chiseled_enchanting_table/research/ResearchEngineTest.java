package chiseled_enchanting_table.research;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static chiseled_enchanting_table.research.ResearchCatalog.*;
import static chiseled_enchanting_table.research.ResearchEngine.*;

class ResearchEngineTest {
    @TempDir Path dir;
    private final Library library = new Library(1,Map.of("test:knowledge",2));
    private final Topic topic = topic(1,Set.of());
    private static Topic topic(int revision,Set<String> prerequisites) {
        return new Topic("test:topic",revision,"test:title","test:question",prerequisites,1,1,Map.of("test:knowledge",2),
            Map.of("test:hypothesis",new Hypothesis("test:hypothesis",Set.of("test:a","test:b"),"test:understood")));
    }
    private ResearchJournal begun() { return begin(topic,ResearchJournal.empty(),library,"test:hypothesis").journal(); }
    private ResearchJournal completed() {
        var first = observe(topic,begun(),"test:a").journal();
        return conclude(topic,observe(topic,first,"test:b").journal(),library).journal();
    }
    @Test void oneShelfCanSupportInitialResearch() { assertEquals(Status.AVAILABLE,preview(topic,ResearchJournal.empty(),library).status()); }
    @Test void noMaterialOrRepeatedActionCounterRequired() { assertTrue(completed().conclusions().contains("test:understood")); }
    @Test void beginDoesNotCompleteResearch() { assertTrue(begun().conclusions().isEmpty()); }
    @Test void libraryGatesExplainAllMissingConditions() {
        var p = preview(topic,ResearchJournal.empty(),new Library(0,Map.of()));
        assertEquals(1,p.missingBreadth()); assertEquals(1,p.missingShelves()); assertEquals(Set.of("test:knowledge"),p.missingDepth());
    }
    @Test void repeatedSameKnowledgeDoesNotFakeBreadth() {
        var t = new Topic("test:broad",1,"test:title","test:question",Set.of(),2,1,Map.of(),topic.hypotheses());
        assertEquals(Status.BREADTH,preview(t,ResearchJournal.empty(),library).status());
    }
    @Test void prerequisitesAreConclusionsNotTopicNames() {
        assertEquals(Status.PREREQUISITES,preview(topic(1,Set.of("test:prior")),ResearchJournal.empty(),library).status());
    }
    @Test void evidenceNeedsAnActiveAttempt() { assertEquals(Status.NO_ATTEMPT,observe(topic,ResearchJournal.empty(),"test:a").status()); }
    @Test void unknownEvidenceCannotAdvance() { assertEquals(begun(),observe(topic,begun(),"test:bogus").journal()); }
    @Test void replayCannotAdvanceProgress() {
        var j = observe(topic,begun(),"test:a").journal();
        assertEquals(Status.DUPLICATE,observe(topic,j,"test:a").status());
        assertEquals(j,observe(topic,j,"test:a").journal());
    }
    @Test void retryBeginPreservesObservations() {
        var j = observe(topic,begun(),"test:a").journal();
        assertEquals(j,begin(topic,j,library,"test:hypothesis").journal());
    }
    @Test void finishRequiresDifferentRelevantEvidence() {
        var j = observe(topic,begun(),"test:a").journal();
        assertTrue(j.conclusions().isEmpty()); assertEquals(Status.OBSERVED,observe(topic,j,"test:b").status());
        assertEquals(Status.EVIDENCE,conclude(topic,j,library).status());
    }
    @Test void completedUnderstandingSurvivesWorkspaceRemoval() {
        assertEquals(Status.COMPLETE,preview(topic,completed(),new Library(0,Map.of())).status());
    }
    @Test void conclusionRechecksWorkspaceRatherThanTrustingStartSnapshot() {
        var observed = observe(topic,observe(topic,begun(),"test:a").journal(),"test:b").journal();
        assertEquals(Status.BREADTH,conclude(topic,observed,new Library(0,Map.of())).status());
        assertEquals(Status.COMPLETE,conclude(topic,observed,library).status());
    }
    @Test void cancelKeepsCompletedKnowledge() {
        assertEquals(completed().conclusions(),cancel(completed(),topic.id()).conclusions()); assertTrue(cancel(begun(),topic.id()).attempts().isEmpty());
    }
    @Test void changedDefinitionCannotCompleteOldExperiment() {
        assertEquals(Status.STALE,observe(topic(2,Set.of()),begun(),"test:a").status());
        assertEquals(Set.of("test:understood"),completed().conclusions());
    }
    @Test void snapshotsCannotBeMutated() {
        assertThrows(UnsupportedOperationException.class,() -> begun().attempts().clear());
        assertThrows(UnsupportedOperationException.class,() -> library.knowledge().put("test:x",4));
    }
    @Test void graphRejectsMissingPrerequisite() { assertThrows(IllegalArgumentException.class,() -> new ResearchCatalog(List.of(topic(1,Set.of("test:missing"))))); }
    @Test void graphRejectsCycleAndDuplicates() {
        assertThrows(IllegalArgumentException.class,() -> new ResearchCatalog(List.of(topic(1,Set.of("test:understood")))));
        assertThrows(IllegalArgumentException.class,() -> new ResearchCatalog(List.of(topic,topic)));
    }
    @Test void graphAcceptsConclusionDependency() {
        var next = new Topic("test:next",1,"test:title","test:question",Set.of("test:understood"),1,1,Map.of(),
            Map.of("test:next_h",new Hypothesis("test:next_h",Set.of("test:a"),"test:next_conclusion")));
        assertEquals(2,new ResearchCatalog(List.of(topic,next)).topics().size());
        assertEquals(Status.AVAILABLE,preview(next,completed(),library).status());
    }
    @Test void contraryObservationSurvivesChangingHypothesis() {
        var alternative = new Hypothesis("test:alternative",Set.of("test:contrary"),"test:alternative_conclusion");
        var t = new Topic(topic.id(),1,topic.title(),topic.question(),Set.of(),1,1,topic.depth(),
            Map.of("test:hypothesis",topic.hypotheses().get("test:hypothesis"),"test:alternative",alternative));
        var j = observe(t,begun(),"test:contrary").journal();
        assertEquals(Status.EVIDENCE,conclude(t,j,library).status());
        j = begin(t,j,library,"test:alternative").journal();
        assertEquals(Status.COMPLETE,conclude(t,j,library).status());
    }
    @Test void journalRoundTripKeepsPartialAndRemovedContent() {
        var j = new ResearchJournal(Set.of("removed:understood"),begun().attempts());
        assertEquals(j,ResearchJson.journal(ResearchJson.journal(j)));
    }
    @Test void futureSchemaAndFractionalRevisionRejected() {
        var root = ResearchJson.journal(begun()); root.addProperty("schema",2);
        assertThrows(IllegalArgumentException.class,() -> ResearchJson.journal(root));
        root.addProperty("schema",1); root.getAsJsonObject("attempts").getAsJsonObject("test:topic").addProperty("revision",1.5);
        assertThrows(ArithmeticException.class,() -> ResearchJson.journal(root));
    }
    @Test void worldStoreSeparatesPlayersAndRestoresAfterRestart() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); Path path = dir.resolve("research.json");
        var store = ResearchStore.load(path); store.put(a,completed()); store.put(b,begun()); store.save(path);
        var loaded = ResearchStore.load(path);
        assertEquals(completed(),loaded.get(a)); assertEquals(begun(),loaded.get(b)); assertFalse(loaded.dirty());
    }
    @Test void invalidStoreIsNotSilentlyOverwritten() throws Exception {
        Path path = dir.resolve("research.json"); Files.writeString(path,"{broken");
        assertThrows(java.io.IOException.class,() -> ResearchStore.load(path)); assertEquals("{broken",Files.readString(path));
    }
    @Test void saveFailureKeepsDirtyForRetry() throws Exception {
        Path file = dir.resolve("not_a_directory"); Files.writeString(file,"x");
        var store = new ResearchStore(); store.put(UUID.randomUUID(),begun());
        assertThrows(java.io.IOException.class,() -> store.save(file.resolve("journal.json"))); assertTrue(store.dirty());
    }
    @Test void catalogJsonValidatesAndDoesNotRequireEnchantmentEffects() {
        var json = JsonParser.parseString("""
            {"schema":1,"topics":[{"id":"test:topic","revision":1,"title":"test:title","question":"test:question",
            "prerequisites":[],"breadth":1,"shelves":1,"depth":{"test:knowledge":2},
            "hypotheses":[{"id":"test:hypothesis","evidence":["test:a","test:b"],"conclusion":"test:understood"}]}]}
            """).getAsJsonObject();
        assertEquals(topic,ResearchJson.catalog(json).topic(topic.id()));
    }
}
