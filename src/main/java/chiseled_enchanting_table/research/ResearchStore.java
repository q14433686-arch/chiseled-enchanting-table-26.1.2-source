package chiseled_enchanting_table.research;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One journal per player UUID, world-local. Parse completely before publication; fail closed on corruption. */
public final class ResearchStore {
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Map<UUID,ResearchJournal> players = new HashMap<>();
    private boolean dirty;
    public ResearchJournal get(UUID player) { return players.getOrDefault(player,ResearchJournal.empty()); }
    public void put(UUID player, ResearchJournal journal) {
        Objects.requireNonNull(player); Objects.requireNonNull(journal);
        if (!get(player).equals(journal)) { players.put(player,journal); dirty = true; }
    }
    public boolean dirty() { return dirty; }
    public static ResearchStore load(Path path) throws IOException {
        ResearchStore store = new ResearchStore();
        if (!Files.exists(path)) return store;
        if (Files.size(path) > MAX_BYTES) throw new IOException("Research save too large");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Unsupported store schema");
            JsonObject values = root.getAsJsonObject("players");
            if (values.size() > 16384) throw new IllegalArgumentException("Too many players");
            for (var e : values.entrySet()) store.players.put(UUID.fromString(e.getKey()),ResearchJson.journal(e.getValue().getAsJsonObject()));
            return store;
        } catch (RuntimeException e) { throw new IOException("Invalid research save; not overwritten: " + path,e); }
    }
    public void save(Path path) throws IOException {
        if (!dirty) return;
        JsonObject root = new JsonObject(), values = new JsonObject(); root.addProperty("schema",1);
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> values.add(e.getKey().toString(),ResearchJson.journal(e.getValue())));
        root.add("players",values);
        byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Research save too large");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName()+".tmp");
        Files.write(temporary,bytes);
        try { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }
}
