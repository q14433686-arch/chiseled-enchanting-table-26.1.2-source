package chiseled_enchanting_table.research;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Temporary opt-in playtest bridge, not the final research page or a command to grant knowledge. */
public final class ResearchPrototype {
    private static final boolean ENABLED = Boolean.getBoolean("cet.research.prototype") || Files.isRegularFile(
        FabricLoader.getInstance().getConfigDir().resolve("cet-research-prototype.enabled"));
    private record Binding(ResourceKey<Level> dimension, BlockPos pos) {}
    private static final Map<UUID,Binding> BINDINGS = new HashMap<>();
    private static final String TOPIC = "prototype:efficiency_2";
    private ResearchPrototype() {}
    public static boolean enabled() { return ENABLED; }
    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BINDINGS.clear());
        if (!ENABLED) return;
        CommandRegistrationCallback.EVENT.register((dispatcher,context,selection) -> dispatcher.register(
            Commands.literal("cet_research")
                .executes(ctx -> run(ctx.getSource().getPlayerOrException(),"status",null))
                .then(Commands.literal("bind").then(Commands.argument("table",BlockPosArgument.blockPos())
                    .executes(ctx -> run(ctx.getSource().getPlayerOrException(),"bind",BlockPosArgument.getBlockPos(ctx,"table")))))
                .then(Commands.literal("start").executes(ctx -> run(ctx.getSource().getPlayerOrException(),"start",null)))
                .then(Commands.literal("analyze").executes(ctx -> run(ctx.getSource().getPlayerOrException(),"analyze",null)))
                .then(Commands.literal("conclude").executes(ctx -> run(ctx.getSource().getPlayerOrException(),"conclude",null)))
                .then(Commands.literal("cancel").executes(ctx -> run(ctx.getSource().getPlayerOrException(),"cancel",null)))
                .then(Commands.literal("unbind").executes(ctx -> run(ctx.getSource().getPlayerOrException(),"unbind",null)))
        ));
    }
    public static Optional<ResearchCatalog.Library> resolve(ServerPlayer player) {
        if (!ENABLED) return Optional.empty();
        Binding binding = BINDINGS.get(player.getUUID());
        if (binding == null || !binding.dimension().equals(player.level().dimension())) return Optional.empty();
        return ResearchWorkspace.resolveAt(player,binding.pos());
    }
    private static int run(ServerPlayer player,String action,BlockPos pos) {
        try {
            if (action.equals("bind")) {
                if (ResearchWorkspace.resolveAt(player,pos).isEmpty()) throw new IllegalStateException("Table unavailable, out of range, or not permitted");
                BINDINGS.put(player.getUUID(),new Binding(player.level().dimension(),pos.immutable()));
                message(player,"Bound table "+pos+". Every action rechecks range, dimension and permissions.");
            } else if (action.equals("unbind")) {
                BINDINGS.remove(player.getUUID()); message(player,"Unbound. Completed knowledge is kept."); return 1;
            } else if (action.equals("start")) {
                message(player,"Start: "+ResearchRuntime.begin(player,TOPIC,"prototype:analyze").status());
            } else if (action.equals("analyze")) {
                message(player,"Analyze: "+ResearchRuntime.analyzeSample(player,TOPIC).status()+". Sample is NOT consumed.");
            } else if (action.equals("conclude")) {
                message(player,"Conclude: "+ResearchRuntime.conclude(player,TOPIC).status());
            } else if (action.equals("cancel")) {
                ResearchRuntime.cancel(player,TOPIC); message(player,"Unfinished attempt cancelled; completed knowledge kept.");
            }
            var library = ResearchWorkspace.resolve(player).orElseThrow(() -> new IllegalStateException("Bind a nearby table with /cet_research bind x y z"));
            var goal = new ResearchGoalView.Goal("minecraft:efficiency","prototype:general",2);
            var view = ResearchGoalView.build(ResearchRuntime.catalog(),ResearchRuntime.journal(player),library,goal);
            message(player,"TEST PROTOTYPE: Efficiency II / learned manufacturing limit = "+view.learnedLimit());
            for (var route : view.routes()) {
                if (route.hypothesis().equals("prototype:job")) { message(player,"Job trial: NOT IMPLEMENTED. No fieldwork completion command exists."); continue; }
                message(player,"Sample route: "+route.next()+"; missing evidence="+route.missingEvidence());
                if (route.requirements().status() != ResearchEngine.Status.AVAILABLE && route.requirements().status() != ResearchEngine.Status.COMPLETE)
                    message(player,"Requirements: "+route.requirements());
            }
            message(player,"No final tab, trial effect, production item or price migration in this prototype.");
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            message(player,"Denied: "+e.getMessage()); return 0;
        }
    }
    private static void message(ServerPlayer player,String text) { player.sendSystemMessage(Component.literal("[Research test] "+text)); }
    static ResearchCatalog extend(ResearchCatalog base) {
        if (!ENABLED) return base;
        try (var stream = ResearchPrototype.class.getResourceAsStream("/data/chiseled_enchanting_table/chiseled_enchanting_table/research_prototype.json")) {
            if (stream == null) throw new IllegalStateException("Missing research prototype fixture");
            var fixture = ResearchJson.catalog(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            var topics = new ArrayList<>(base.topics().values()); topics.addAll(fixture.topics().values());
            var grants = new ArrayList<>(base.outcomes().grants()); grants.addAll(fixture.outcomes().grants());
            var analyses = new ArrayList<>(base.analyses().rules()); analyses.addAll(fixture.analyses().rules());
            return new ResearchCatalog(topics,grants,analyses);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read research prototype",e); }
    }
}
