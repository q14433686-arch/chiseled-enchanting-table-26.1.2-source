package chiseled_enchanting_table.research;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchGoalViewTest {
    private final ResearchCatalog.Library library = new ResearchCatalog.Library(1,Map.of("minecraft:efficiency",1));
    private final ResearchCatalog.Topic topic = new ResearchCatalog.Topic("test:topic",1,"test:title","test:question",Set.of(),1,1,
        Map.of("minecraft:efficiency",1),Map.of(
            "test:job",new ResearchCatalog.Hypothesis("test:job",Set.of("test:job_result"),"test:learned"),
            "test:analysis",new ResearchCatalog.Hypothesis("test:analysis",Set.of("test:sample"),"test:learned")));
    private final ResearchCatalog catalog = new ResearchCatalog(List.of(topic),List.of(
        new ResearchOutcomes.Grant("test:learned","minecraft:efficiency","test:general",2)),List.of(
        new ResearchSampleAnalysis.Rule("test:topic","test:analysis","test:sample","minecraft:efficiency",2)));
    private ResearchGoalView.View view(ResearchJournal j, ResearchCatalog.Library lib) {
        return ResearchGoalView.build(catalog,j,lib,new ResearchGoalView.Goal("minecraft:efficiency","test:general",2));
    }
    private ResearchGoalView.Route route(ResearchGoalView.View v,String hypothesis) {
        return v.routes().stream().filter(r -> r.hypothesis().equals(hypothesis)).findFirst().orElseThrow();
    }
    private ResearchJournal begin(String route) { return ResearchEngine.begin(topic,ResearchJournal.empty(),library,route).journal(); }
    @Test void targetShowsTwoWaysToSameManufacturingResult() {
        var v=view(ResearchJournal.empty(),library); assertEquals(2,v.routes().size()); assertEquals(0,v.learnedLimit());
        assertTrue(v.routes().stream().allMatch(r -> r.next()==ResearchGoalView.Action.CHOOSE_ROUTE));
    }
    @Test void selectedJobShowsFieldworkNotFakeAnalysisButton() {
        assertEquals(ResearchGoalView.Action.CONTINUE_FIELDWORK,route(view(begin("test:job"),library),"test:job").next());
    }
    @Test void sampleRouteShowsMissingSamplePrecisely() {
        var r=route(view(begin("test:analysis"),library),"test:analysis");
        assertEquals(ResearchGoalView.Action.FIND_SAMPLE,r.next()); assertEquals(2,r.sampleLevel()); assertEquals("minecraft:efficiency",r.sampleEnchantment());
    }
    @Test void actualHigherSampleEnablesAnalysis() {
        var v=view(begin("test:analysis"),new ResearchCatalog.Library(1,Map.of("minecraft:efficiency",2)));
        assertEquals(ResearchGoalView.Action.ANALYZE_SAMPLE,route(v,"test:analysis").next());
    }
    @Test void observationMakesConclusionActionAvailable() {
        var j=ResearchEngine.observe(topic,begin("test:job"),"test:job_result").journal();
        assertEquals(ResearchGoalView.Action.CONCLUDE,route(view(j,library),"test:job").next());
    }
    @Test void learnedGoalDoesNotAskToRepeatOtherRoute() {
        var j=new ResearchJournal(Set.of("test:learned"),Map.of());
        assertTrue(view(j,library).routes().stream().allMatch(r -> r.next()==ResearchGoalView.Action.LEARNED));
    }
    @Test void missingWorkspaceConditionsAreNotHiddenByRouteChoice() {
        assertTrue(view(ResearchJournal.empty(),new ResearchCatalog.Library(0,Map.of())).routes().stream()
            .allMatch(r -> r.next()==ResearchGoalView.Action.REQUIREMENTS));
    }
    @Test void otherBranchCannotDisplayThisBranchesReward() {
        var v=ResearchGoalView.build(catalog,ResearchJournal.empty(),library,new ResearchGoalView.Goal("minecraft:efficiency","test:other",2));
        assertTrue(v.routes().isEmpty());
    }
    @Test void pageSnapshotsAreImmutable() {
        var v=view(ResearchJournal.empty(),library);
        assertThrows(UnsupportedOperationException.class,() -> v.routes().clear());
        assertThrows(UnsupportedOperationException.class,() -> v.routes().getFirst().missingEvidence().clear());
    }
}
