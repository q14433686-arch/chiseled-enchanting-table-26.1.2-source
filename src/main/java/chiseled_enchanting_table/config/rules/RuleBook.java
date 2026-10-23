package chiseled_enchanting_table.config.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Game-version independent rule resolver. Stored preferences, authoritative pack values and
 * bootstrap components are separate inputs; consumers read one immutable published snapshot.
 * No Fabric/client static state or persistence is hidden in resolution.
 */
public final class RuleBook {
    public enum Origin { DEFAULT, CONFIG, DATAPACK, LOCKED_DEFAULT, LOCKED_DATAPACK, STARTUP, NORMALIZED }

    public record Definition(String key, Object defaultValue, boolean startupOnly) {
        public Definition {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Blank rule key");
            requireScalar(defaultValue);
        }

        private void validate(Object value) {
            requireScalar(value);
            Class<?> type = defaultValue instanceof Enum<?> e ? e.getDeclaringClass() : defaultValue.getClass();
            if (!type.isInstance(value)) throw new IllegalArgumentException("Wrong type for " + key);
        }
    }

    public record Policy(boolean enforceDatapackPriority, boolean lockRuntime, Set<String> lockedKeys) {
        public static final Policy NONE = new Policy(false, false, Set.of());
        public Policy { lockedKeys = Set.copyOf(lockedKeys); }
    }

    public record Value(Object effective, Origin origin, boolean editable) {
        public Value {
            requireScalar(effective);
            Objects.requireNonNull(origin);
        }
    }

    public record Snapshot(long revision, Map<String, Value> values) {
        public Snapshot {
            if (revision < 0) throw new IllegalArgumentException("Negative revision");
            values = Map.copyOf(values);
        }
        public Value rule(String key) {
            Value value = values.get(key);
            if (value == null) throw new IllegalArgumentException("Unknown rule " + key);
            return value;
        }
    }

    /** Opaque transaction, bound to both its originating book and exact prior snapshot. */
    public static final class Prepared {
        private final RuleBook owner;
        private final Snapshot base;
        private final Snapshot next;
        private Prepared(RuleBook owner, Snapshot base, Snapshot next) {
            this.owner = owner;
            this.base = base;
            this.next = next;
        }
        public Snapshot snapshot() { return next; }
    }

    private final Map<String, Definition> definitions;
    private final Map<String, Object> startup;
    private final AtomicReference<Snapshot> active;

    public RuleBook(Map<String, Definition> definitions, Map<String, Object> startup) {
        this.definitions = Map.copyOf(definitions);
        this.startup = Map.copyOf(startup);
        this.definitions.forEach((key, definition) -> {
            if (!key.equals(definition.key())) throw new IllegalArgumentException("Schema key mismatch");
            if (definition.startupOnly() && !this.startup.containsKey(key)) {
                throw new IllegalArgumentException("Missing actual bootstrap value for " + key);
            }
        });
        validateValues(this.startup);
        for (String key : this.startup.keySet()) {
            if (!this.definitions.get(key).startupOnly()) {
                throw new IllegalArgumentException("Runtime rule passed as bootstrap: " + key);
            }
        }
        active = new AtomicReference<>(resolve(0, Map.of(), Map.of(), false, Policy.NONE));
    }

    public Snapshot snapshot() { return active.get(); }

    public Prepared prepare(Map<String, Object> local, Map<String, Object> datapack,
                            boolean overrideDatapacks, Policy policy) {
        // Defensive copies prevent caller mutations during validation/resolution.
        local = Map.copyOf(local);
        datapack = Map.copyOf(datapack);
        validateValues(local);
        validateValues(datapack);
        for (String key : policy.lockedKeys()) {
            Definition definition = definitions.get(key);
            if (definition == null) throw new IllegalArgumentException("Unknown locked key " + key);
            if (definition.startupOnly()) {
                throw new IllegalArgumentException("World policy cannot lock a bootstrap component: " + key);
            }
        }
        for (String key : datapack.keySet()) {
            if (definitions.get(key).startupOnly()) {
                throw new IllegalArgumentException("World datapack cannot replace a bootstrap component: " + key);
            }
        }
        Snapshot base = active.get();
        return new Prepared(this, base, resolve(Math.incrementExact(base.revision()), local, datapack,
            overrideDatapacks, policy));
    }

    public Prepared withValidatedValues(Prepared original, Map<String, Object> normalized) {
        normalized = Map.copyOf(normalized);
        if (original.owner != this || !normalized.keySet().equals(definitions.keySet()))
            throw new IllegalArgumentException("Invalid normalized proposal");
        validateValues(normalized);
        Map<String, Value> values = new LinkedHashMap<>();
        for (var entry : normalized.entrySet()) {
            Value old = original.next.rule(entry.getKey());
            if (definitions.get(entry.getKey()).startupOnly() && !Objects.equals(old.effective(), entry.getValue()))
                throw new IllegalArgumentException("Normalization changed actual bootstrap value");
            values.put(entry.getKey(), new Value(entry.getValue(),
                Objects.equals(old.effective(), entry.getValue()) ? old.origin() : Origin.NORMALIZED, old.editable()));
        }
        return new Prepared(this, original.base, new Snapshot(original.next.revision(), values));
    }

    /**
     * Serialize commits, reject stale/foreign proposals BEFORE touching disk, persist BEFORE publish.
     * The supplied writer must itself use atomic replacement. A thrown exception leaves active intact.
     * Writer must not reenter commit (otherwise persistence ordering becomes ambiguous).
     */
    public synchronized Snapshot commit(Prepared prepared, Runnable atomicWriter) {
        if (committing) throw new IllegalStateException("Reentrant rule commit");
        if (prepared.owner != this || active.get() != prepared.base) {
            throw new IllegalStateException("Stale or foreign rule transaction");
        }
        Objects.requireNonNull(atomicWriter);
        committing = true;
        try {
            atomicWriter.run();
            active.set(prepared.next);
            return prepared.next;
        } finally {
            committing = false;
        }
    }
    private boolean committing;

    private Snapshot resolve(long revision, Map<String, Object> local, Map<String, Object> datapack,
                             boolean overrideDatapacks, Policy policy) {
        Map<String, Value> values = new LinkedHashMap<>();
        definitions.forEach((key, definition) -> {
            boolean hasPack = datapack.containsKey(key);
            boolean locked = policy.lockRuntime() || policy.lockedKeys().contains(key);
            Object value;
            Origin origin;
            boolean editable;
            if (definition.startupOnly()) {
                value = startup.get(key);
                origin = Origin.STARTUP;
                editable = false;
            } else if (locked) {
                value = hasPack ? datapack.get(key) : definition.defaultValue();
                origin = hasPack ? Origin.LOCKED_DATAPACK : Origin.LOCKED_DEFAULT;
                editable = false;
            } else if (hasPack && (policy.enforceDatapackPriority() || !overrideDatapacks)) {
                value = datapack.get(key);
                origin = Origin.DATAPACK;
                editable = false;
            } else if (local.containsKey(key)) {
                value = local.get(key);
                origin = Origin.CONFIG;
                editable = true;
            } else if (hasPack) {
                value = datapack.get(key);
                origin = Origin.DATAPACK;
                editable = true;
            } else {
                value = definition.defaultValue();
                origin = Origin.DEFAULT;
                editable = true;
            }
            values.put(key, new Value(value, origin, editable));
        });
        return new Snapshot(revision, values);
    }

    private void validateValues(Map<String, Object> values) {
        values.forEach((key, value) -> {
            Definition definition = definitions.get(key);
            if (definition == null) throw new IllegalArgumentException("Unknown rule " + key);
            definition.validate(value);
        });
    }

    private static void requireScalar(Object value) {
        if (!(value instanceof String || value instanceof Boolean || value instanceof Integer
            || value instanceof Long || value instanceof Double || value instanceof Float || value instanceof Enum<?>)) {
            throw new IllegalArgumentException("Rule values must be immutable scalars");
        }
        if (value instanceof Double d && !Double.isFinite(d)
            || value instanceof Float f && !Float.isFinite(f)) {
            throw new IllegalArgumentException("Non-finite rule value");
        }
    }
}
