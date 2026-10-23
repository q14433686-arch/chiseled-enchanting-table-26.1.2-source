package chiseled_enchanting_table.config.rules;

import chiseled_enchanting_table.config.ConfigCatalog;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import java.util.*;

/** Bridges typed game DTOs to the pure resolver; all consumers use Data's published RuntimeFrame. */
public final class RuntimeRules {
    private static RuleBook book;
    private RuntimeRules() {}
    public record Prepared(RuleBook.Prepared transaction, ModRuntimeConfig configuration) {
        public RuleBook.Snapshot snapshot() { return transaction.snapshot(); }
    }
    public static synchronized Prepared prepare(ModRuntimeConfig local, ModRuntimeConfig raw,
                                                 ModRuntimeConfig.PackPolicy policy) {
        if (book == null) {
            Map<String, RuleBook.Definition> definitions = new LinkedHashMap<>();
            var defaults = ModRuntimeConfig.DEFAULT.toDraft();
            for (var option : ConfigCatalog.ALL_OPTIONS) {
                if (option.key().equals("override_datapacks")) continue;
                definitions.put(option.key(), new RuleBook.Definition(option.key(), option.get(defaults),
                    option.key().equals("enchanted_book_stack_size")));
            }
            book = new RuleBook(definitions, Map.of("enchanted_book_stack_size", ModRuntimeConfig.get().enchantedBookStackSize()));
        }
        Map<String,Object> preferences = new LinkedHashMap<>();
        Map<String,Object> pack = new LinkedHashMap<>();
        var draft = local.toDraft(); var rawDraft = raw.toDraft();
        for (var option : ConfigCatalog.ALL_OPTIONS) {
            if (option.key().equals("override_datapacks")) continue;
            preferences.put(option.key(), option.get(draft));
            if (policy.datapackOverriddenKeys().contains(option.key()) && !option.key().equals("enchanted_book_stack_size")) {
                pack.put(option.key(), option.get(rawDraft));
            }
        }
        Set<String> locked = new LinkedHashSet<>(policy.lockedKeys());
        locked.remove("override_datapacks"); locked.remove("enchanted_book_stack_size");
        boolean override = local.overrideDatapacks() && !policy.enforceDatapackPriority()
            && !policy.isKeyLocked("override_datapacks");
        var proposal = book.prepare(preferences, pack, override,
            new RuleBook.Policy(policy.enforceDatapackPriority(), policy.lockInGameConfig(), locked));
        draft.overrideDatapacks = override;
        for (var option : ConfigCatalog.ALL_OPTIONS) {
            if (!option.key().equals("override_datapacks")) option.setResolvedValue(draft, proposal.snapshot().rule(option.key()).effective());
        }
        // One cross-field normalization, not UI edit-normalization after each assignment.
        ModRuntimeConfig coherent = draft.build();
        Map<String,Object> normalized = new LinkedHashMap<>();
        var finalDraft = coherent.toDraft();
        for (var option : ConfigCatalog.ALL_OPTIONS) if (!option.key().equals("override_datapacks"))
            normalized.put(option.key(), option.get(finalDraft));
        return new Prepared(book.withValidatedValues(proposal, normalized), coherent);
    }
    public static synchronized void commit(Prepared prepared, Runnable atomicWriter) {
        book.commit(prepared.transaction(), atomicWriter);
    }
}
