package chiseled_enchanting_table.research;

import java.util.*;

/** Learned manufacturing limits per specialization. Does not alter registry maxima, prices or existing items. */
public final class ResearchOutcomes {
    public record Grant(String conclusion, String enchantment, String branch, int maxLevel) {
        public Grant {
            conclusion = ResearchCatalog.key(conclusion); enchantment = ResearchCatalog.key(enchantment);
            branch = ResearchCatalog.key(branch);
            if (maxLevel < 1 || maxLevel > 255) throw new IllegalArgumentException("Invalid manufacturing limit");
        }
    }
    private final List<Grant> grants;
    public ResearchOutcomes(Collection<Grant> grants, Set<String> knownConclusions) {
        if (grants.size() > 4096) throw new IllegalArgumentException("Too many research outcomes");
        Set<String> entries = new HashSet<>();
        for (Grant grant : grants) {
            if (!knownConclusions.contains(grant.conclusion())) throw new IllegalArgumentException("Unknown outcome conclusion " + grant.conclusion());
            if (!entries.add(grant.conclusion()+"|"+grant.enchantment()+"|"+grant.branch()))
                throw new IllegalArgumentException("Duplicate research outcome");
        }
        this.grants = List.copyOf(grants);
    }
    public List<Grant> grants() { return grants; }
    /** Zero means not learned. Branches never borrow each other's higher cap. */
    public int manufacturingLimit(ResearchJournal journal, String enchantment, String branch) {
        ResearchCatalog.key(enchantment); ResearchCatalog.key(branch);
        return grants.stream().filter(g -> g.enchantment().equals(enchantment) && g.branch().equals(branch)
            && journal.conclusions().contains(g.conclusion())).mapToInt(Grant::maxLevel).max().orElse(0);
    }
    public boolean canManufacture(ResearchJournal journal, String enchantment, String branch, int level) {
        return level > 0 && level <= manufacturingLimit(journal,enchantment,branch);
    }
}
