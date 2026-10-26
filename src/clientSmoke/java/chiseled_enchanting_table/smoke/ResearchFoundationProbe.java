package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.research.*;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Gateway integration only: fixture evidence is NOT a shipped gameplay research task. */
final class ResearchFoundationProbe {
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError("research foundation: " + why); }
    static void verify(ServerPlayer player,BlockPos tablePos) {
        check(ResearchRuntime.catalog().topics().containsKey("smoke:research"),"smoke datapack not loaded by real resource listener");
        boolean[] authorized = {true}, validEvent = {false};
        ResearchRuntime.setLibraryAccess(p -> authorized[0] && p == player
            && p.level().hasChunkAt(tablePos) && p.level().getBlockState(tablePos).is(BlockRegistry.CHISELED_ENCHANTING_TABLE)
            ? Optional.of(new ResearchCatalog.Library(1,Map.of("smoke:knowledge",1))) : Optional.empty());
        ResearchRuntime.registerEvidence("smoke:observation",p -> p == player && validEvent[0]);
        try {
            check(ResearchRuntime.preview(player,"smoke:research").orElseThrow().status() == ResearchEngine.Status.AVAILABLE,"initial gate");
            ResearchRuntime.begin(player,"smoke:research","smoke:hypothesis");
            check(ResearchRuntime.observe(player,"smoke:research","unknown:evidence").status() == ResearchEngine.Status.IRRELEVANT,"unknown provider awarded evidence");
            check(ResearchRuntime.observe(player,"smoke:research","smoke:observation").status() == ResearchEngine.Status.IRRELEVANT,"false verifier awarded evidence");
            authorized[0] = false; validEvent[0] = true;
            check(ResearchRuntime.observe(player,"smoke:research","smoke:observation").status() == ResearchEngine.Status.OBSERVED,"field evidence depended on open workspace");
            check(ResearchRuntime.observe(player,"smoke:research","smoke:observation").status() == ResearchEngine.Status.DUPLICATE,"replayed event advanced progress");
            boolean denied = false;
            try { ResearchRuntime.conclude(player,"smoke:research"); }
            catch (IllegalStateException expected) { denied = true; }
            check(denied,"conclusion bypassed workspace authorization");
            authorized[0] = true;
            check(ResearchRuntime.conclude(player,"smoke:research").status() == ResearchEngine.Status.COMPLETE,"verified conclusion failed");
            ResearchRuntime.cancel(player,"smoke:research");
            check(ResearchRuntime.journal(player).conclusions().contains("smoke:understanding"),"cancel lost understanding");
            System.out.println("RESEARCH FOUNDATION PASS: real datapack listener + UUID journal + workspace gate + unknown/false evidence denial + field observation + replay denial + conclusion recheck + permanent understanding");
        } finally { ResearchRuntime.setLibraryAccess(p -> Optional.empty()); }
    }
}
