package enchantment_fusion.compat;

import enchantment_fusion.compat.CetCleanseIds.Kind;
import enchantment_fusion.compat.CetCleanseIds.ParsedCleanseId;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side UI bridge projecting {@code 融合词条}, {@code 畸变词条}, {@code 融合附魔书},
 * and {@code 异位化形} into {@code 纂刻附魔台}'s Cleanse & Curse-Purge list.
 */
public final class CetCleanseClientBridge {
    private CetCleanseClientBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Object augmentCleanseOptionModel(
        ItemStack target,
        boolean allowCursePurge,
        Object baseResult
    ) {
        if (target == null || target.isEmpty() || !CetCleanseOperations.hasAnyCleanseableData(target)) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        HolderLookup.Provider registries = mc.level.registryAccess();

        try {
            Class<?> resultCls = baseResult.getClass();
            List<Object> basePinned = (List<Object>) invoke0(baseResult, "pinned");
            Map<Identifier, List<Object>> baseGroups = (Map<Identifier, List<Object>>) invoke0(baseResult, "groups");
            Map<Identifier, Holder<Enchantment>> baseHolders =
                (Map<Identifier, Holder<Enchantment>>) invoke0(baseResult, "holders");

            Class<?> availOptCls = Class.forName("chiseled_enchanting_table.menu.AvailableOption");
            Method purgeFactory = availOptCls.getMethod("purgeCurse", Identifier.class, int.class);
            Method extractFactory = availOptCls.getMethod("extractEnchantmentBook", Identifier.class, int.class);
            Method stripFactory = availOptCls.getMethod("stripSingleEnchantment", Identifier.class, int.class);
            Method downgradeFactory = availOptCls.getMethod("downgradeEnchantment", Identifier.class, int.class);

            List<Object> pinned = new ArrayList<>(basePinned);
            Map<Identifier, List<Object>> groups = new LinkedHashMap<>();
            Map<Identifier, Holder<Enchantment>> holders = new LinkedHashMap<>();

            Set<String> mirroredPrimaries = CetCleanseOperations.mirroredPrimaryEnchantIds(target);
            int totalIndependentTraits = CetCleanseOperations.countTotalIndependentTraits(target);

            // 1. Keep unfused vanilla enchantment groups (filtering out mirrored native primaries so fusions never duplicate)
            for (Map.Entry<Identifier, List<Object>> e : baseGroups.entrySet()) {
                Identifier id = e.getKey();
                if (mirroredPrimaries.contains(id.toString())) {
                    continue;
                }
                List<Object> children = new ArrayList<>(e.getValue());
                Holder<Enchantment> h = baseHolders.get(id);
                // Ensure STRIP_SINGLE_ENCHANTMENT is offered when unfused vanilla coexists with fused/transmuted traits!
                if (totalIndependentTraits >= 2 && h != null) {
                    boolean hasStrip = false;
                    for (Object ch : children) {
                        Object t = invoke0(ch, "type");
                        if ("STRIP_SINGLE_ENCHANTMENT".equals(String.valueOf(t))) {
                            hasStrip = true;
                            break;
                        }
                    }
                    if (!hasStrip) {
                        int lv = EnchantmentHelper.getEnchantmentsForCrafting(target).getLevel(h);
                        if (lv > 0) {
                            children.add(stripFactory.invoke(null, id, lv));
                        }
                    }
                }
                if (!children.isEmpty()) {
                    groups.put(id, children);
                    if (h != null) {
                        holders.put(id, h);
                    }
                }
            }

            // 2. Append FUSED_PAIRS: Aberrant pairs -> pinned PURGE_CURSE; Clean pairs -> Fused category group
            FusedEnchantmentList fusedList = target.getOrDefault(
                FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY
            );
            boolean canExtractFusion = CetCleanseOperations.canExtractAnyFusionTraitToBook(target);
            for (int i = 0; i < fusedList.size(); i++) {
                FusedPair pair = fusedList.pairs().get(i);
                int effLv = Math.max(1, Math.max(pair.primaryLevel(), pair.secondaryLevel()));
                Optional<Holder<Enchantment>> hOpt = FusionStationOperations.lookupEnchantment(
                    registries, pair.primaryId()
                );
                if (hOpt.isEmpty()) {
                    continue;
                }
                if (pair.isAberrant()) {
                    if (allowCursePurge) {
                        Identifier synId = CetCleanseIds.aberrantId(i, pair.primaryId());
                        pinned.add(purgeFactory.invoke(null, synId, effLv));
                    }
                } else {
                    Identifier synId = CetCleanseIds.fusedId(i, pair.primaryId());
                    List<Object> subRows = new ArrayList<>();
                    if (canExtractFusion) {
                        subRows.add(extractFactory.invoke(null, synId, effLv));
                    }
                    if (totalIndependentTraits >= 2) {
                        subRows.add(stripFactory.invoke(null, synId, effLv));
                    }
                    if (!subRows.isEmpty()) {
                        groups.put(synId, subRows);
                        holders.put(synId, hOpt.get());
                    }
                }
            }

            // 3. Append TRANSMUTED_ENCHANTS -> Downgrade + Extract Book + Strip Single
            Map<String, Integer> transmuted = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
            for (Map.Entry<String, Integer> entry : transmuted.entrySet()) {
                String enchId = entry.getKey();
                int curLv = entry.getValue();
                if (curLv <= 0) continue;
                Optional<Holder<Enchantment>> hOpt = FusionStationOperations.lookupEnchantment(registries, enchId);
                if (hOpt.isEmpty()) continue;

                Identifier synId = CetCleanseIds.transmuteId(enchId);
                List<Object> subRows = new ArrayList<>();
                for (int nextLv = curLv - 1; nextLv >= 1; nextLv--) {
                    subRows.add(downgradeFactory.invoke(null, synId, nextLv));
                }
                if (canExtractFusion) {
                    subRows.add(extractFactory.invoke(null, synId, curLv));
                }
                if (totalIndependentTraits >= 2) {
                    subRows.add(stripFactory.invoke(null, synId, curLv));
                }
                if (!subRows.isEmpty()) {
                    groups.put(synId, subRows);
                    holders.put(synId, hOpt.get());
                }
            }

            Constructor<?> ctor = resultCls.getDeclaredConstructor(List.class, Map.class, Map.class);
            ctor.setAccessible(true);
            return ctor.newInstance(pinned, groups, holders);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean tryPopulateFusionAwareCleanseList(
        Object listWidget,
        Minecraft minecraft,
        Set<Identifier> expandedCleanseCategories,
        Set<Identifier> visibleCleanseGroupIds,
        Function<Object, Object> buildTopEntry,
        Function<Object, Object> buildChildEntry,
        Consumer<Object> addRow,
        int navigationIndex,
        boolean targetChanged,
        double previousScroll
    ) {
        try {
            ItemStack target = (ItemStack) getDeclaredField(listWidget, "enchantable_item");
            if (target == null || target.isEmpty() || !CetCleanseOperations.hasAnyCleanseableData(target)) {
                return false;
            }
            visibleCleanseGroupIds.clear();
            Object handler = getDeclaredField(listWidget, "handler");
            Boolean enabled = (Boolean) invoke0(handler, "disenchantEnabled");
            if (!Boolean.TRUE.equals(enabled)) {
                return true;
            }

            Object cleanseFilter = getDeclaredField(listWidget, "cleanseFilter");
            String query = (String) invoke0(cleanseFilter, "query");
            Object mode = invoke0(cleanseFilter, "mode");
            boolean onlyAffordable = (Boolean) invoke0(cleanseFilter, "affordableOnly");
            boolean allowPurge = (Boolean) invoke0(handler, "disenchantAllowCursePurge");

            Class<?> modelCls = Class.forName("chiseled_enchanting_table.gui.CleanseOptionModel");
            Method buildM = modelCls.getDeclaredMethod("build", ItemStack.class, boolean.class);
            buildM.setAccessible(true);
            Object model = buildM.invoke(null, target, allowPurge);

            Class<?> filterBarCls = Class.forName("chiseled_enchanting_table.gui.InscribeFilterBar");
            Method matchesQueryM = filterBarCls.getDeclaredMethod(
                "matchesQuery", String.class, String.class, Identifier.class, List.class
            );
            matchesQueryM.setAccessible(true);

            Method modeAcceptsM = mode.getClass().getDeclaredMethod(
                "accepts",
                Class.forName("chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads$OptionType")
            );
            modeAcceptsM.setAccessible(true);

            Method listDisplayM = listWidget.getClass().getDeclaredMethod(
                "displayName", Class.forName("chiseled_enchanting_table.menu.AvailableOption")
            );
            listDisplayM.setAccessible(true);

            List<Object> pinned = (List<Object>) invoke0(model, "pinned");
            for (Object opt : pinned) {
                Object optType = invoke0(opt, "type");
                if (!(Boolean) modeAcceptsM.invoke(mode, optType)) continue;
                Object built = buildTopEntry.apply(opt);
                boolean canEnch = (Boolean) invoke0(built, "canEnchant");
                List<?> costOpts = (List<?>) getDeclaredField(built, "cost_options");
                String disp = (String) listDisplayM.invoke(listWidget, opt);
                Identifier optId = (Identifier) invoke0(opt, "id");
                if ((!onlyAffordable || canEnch)
                    && (Boolean) matchesQueryM.invoke(null, query, disp, optId, costOpts)) {
                    addRow.accept(built);
                }
            }

            Map<Identifier, List<Object>> grouped = (Map<Identifier, List<Object>>) invoke0(model, "groups");
            Map<Identifier, Holder<Enchantment>> holders =
                (Map<Identifier, Holder<Enchantment>>) invoke0(model, "holders");

            if (targetChanged && grouped.size() == 1) {
                expandedCleanseCategories.add(grouped.keySet().iterator().next());
            }

            ItemEnchantments currentVanilla = EnchantmentHelper.getEnchantmentsForCrafting(target);
            double refScale = (Double) invoke0(handler, "disenchantRefundScale");
            double extScale = (Double) invoke0(handler, "disenchantExternalRefundScale");
            Object lrCfg = invoke0(handler, "libraryResonanceConfig");

            Class<?> disAppCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication");
            Method provEssenceM = disAppCls.getMethod(
                "provenanceEssencePotential",
                Class.forName("chiseled_enchanting_table.mechanics.resonance.LibraryResonance$Config"),
                ItemStack.class,
                Holder.class,
                int.class,
                double.class,
                double.class
            );

            Class<?> uiEntryCls = Class.forName(
                "chiseled_enchanting_table.gui.EnchantementListWidget$EnchantUiEntry"
            );
            Method catEntryM = uiEntryCls.getDeclaredMethod(
                "categoryEntry",
                Minecraft.class,
                handler.getClass(),
                Identifier.class,
                String.class,
                String.class,
                int.class,
                int.class,
                boolean.class,
                listWidget.getClass()
            );
            catEntryM.setAccessible(true);

            boolean hasActiveFilter = (Boolean) invoke0(cleanseFilter, "hasActiveFilter");
            Method isCatExpandedM = listWidget.getClass().getDeclaredMethod("isCategoryExpanded", Identifier.class);
            isCatExpandedM.setAccessible(true);

            for (Map.Entry<Identifier, List<Object>> groupEntry : grouped.entrySet()) {
                Identifier id = groupEntry.getKey();
                List<Object> children = groupEntry.getValue();
                Holder<Enchantment> holder = holders.get(id);
                if (holder == null) continue;

                String headerTitle = resolveGroupHeaderTitle(target, id, holder, currentVanilla, minecraft);
                int accentColor = resolveGroupAccentColor(id);

                List<Object> matchedChildren = new ArrayList<>(children.size());
                for (Object child : children) {
                    Object chType = invoke0(child, "type");
                    if (!(Boolean) modeAcceptsM.invoke(mode, chType)) continue;
                    Object built = buildChildEntry.apply(child);
                    boolean canEnch = (Boolean) invoke0(built, "canEnchant");
                    List<?> costOpts = (List<?>) getDeclaredField(built, "cost_options");
                    Identifier chId = (Identifier) invoke0(child, "id");
                    String chDisp = (String) listDisplayM.invoke(listWidget, child);
                    boolean queryOk = (Boolean) matchesQueryM.invoke(null, query, headerTitle, id, costOpts)
                        || (Boolean) matchesQueryM.invoke(null, query, chDisp, chId, costOpts);
                    if ((!onlyAffordable || canEnch) && queryOk) {
                        matchedChildren.add(built);
                    }
                }
                if (matchedChildren.isEmpty()) continue;
                visibleCleanseGroupIds.add(id);

                int traitEssence = computeGroupEssenceBadge(
                    target, id, holder, currentVanilla, lrCfg, refScale, extScale, provEssenceM
                );
                boolean open = hasActiveFilter || (Boolean) isCatExpandedM.invoke(listWidget, id);
                String countBadge = Component.translatable(
                    "cet.enchantment_fusion.category_count", matchedChildren.size()
                ).getString();

                addRow.accept(catEntryM.invoke(
                    null,
                    minecraft,
                    handler,
                    id,
                    headerTitle,
                    countBadge,
                    accentColor,
                    traitEssence,
                    true,
                    listWidget
                ));

                if (open) {
                    Field lastChildF = uiEntryCls.getDeclaredField("last_child");
                    lastChildF.setAccessible(true);
                    for (int idx = 0; idx < matchedChildren.size(); idx++) {
                        Object child = matchedChildren.get(idx);
                        lastChildF.set(child, idx == matchedChildren.size() - 1);
                        addRow.accept(child);
                    }
                }
            }

            Method resumeNavM = listWidget.getClass().getDeclaredMethod("resumeNavigation", int.class);
            resumeNavM.setAccessible(true);
            resumeNavM.invoke(listWidget, navigationIndex);
            if (!targetChanged) {
                Method maxScrollM = findZeroArgMethod(listWidget.getClass(), "maxScrollAmount");
                double maxScroll = (Double) maxScrollM.invoke(listWidget);
                Method setScrollM = listWidget.getClass().getMethod("setScrollAmount", double.class);
                setScrollM.invoke(listWidget, Math.min(previousScroll, maxScroll));
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static @Nullable Holder<Enchantment> resolveSyntheticHolder(Identifier id, @Nullable Player player) {
        Identifier underlying = CetCleanseIds.underlyingEnchantmentIdentifier(id);
        if (underlying == null) {
            return null;
        }
        HolderLookup.Provider registries = null;
        if (player != null) {
            registries = player.level().registryAccess();
        } else if (Minecraft.getInstance().level != null) {
            registries = Minecraft.getInstance().level.registryAccess();
        }
        if (registries == null) {
            return null;
        }
        return FusionStationOperations.lookupEnchantment(registries, underlying.toString()).orElse(null);
    }

    public static @Nullable String resolveSyntheticDisplayName(Object handler, @Nullable Player player, Object option) {
        try {
            Identifier id = (Identifier) invoke0(option, "id");
            ParsedCleanseId parsed = CetCleanseIds.parse(id);
            if (parsed == null) {
                return null;
            }
            Object optType = invoke0(option, "type");
            String typeName = optType instanceof Enum<?> en ? en.name() : String.valueOf(optType);
            int level = (Integer) invoke0(option, "level");
            ItemStack target = (ItemStack) invoke0(handler, "getEnchantableItem");

            HolderLookup.Provider registries = player != null
                ? player.level().registryAccess()
                : (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : null);

            if (parsed.kind() == Kind.ABERRANT || parsed.kind() == Kind.FUSED) {
                FusedEnchantmentList list = target.getOrDefault(
                    FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY
                );
                if (parsed.pairIndex() < 0 || parsed.pairIndex() >= list.size()) {
                    return null;
                }
                FusedPair pair = list.pairs().get(parsed.pairIndex());
                String pairLabel = formatPairLabel(pair, registries);
                return switch (typeName) {
                    case "PURGE_CURSE" -> {
                        String sideKey = !pair.aberrationId().isEmpty() ? pair.aberrationId() : "overburden";
                        Component sideComp = Component.translatable(AberrantRegistry.titleKey(sideKey));
                        yield Component.translatable(
                            "cet.enchantment_fusion.purge_aberrant", pairLabel, sideComp
                        ).getString();
                    }
                    case "EXTRACT_ENCHANTMENT_BOOK" -> Component.translatable(
                        "cet.enchantment_fusion.extract_fused_book", pairLabel
                    ).getString();
                    case "STRIP_SINGLE_ENCHANTMENT" -> Component.translatable(
                        "cet.enchantment_fusion.strip_fused", pairLabel
                    ).getString();
                    default -> pairLabel;
                };
            }

            if (parsed.kind() == Kind.TRANSMUTE) {
                Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                int curLv = trans.getOrDefault(parsed.underlyingEnchantId(), Math.max(1, level));
                String traitLabel = formatTransmuteLabel(target, parsed.underlyingEnchantId(), curLv, registries);
                return switch (typeName) {
                    case "EXTRACT_ENCHANTMENT_BOOK" -> Component.translatable(
                        "cet.enchantment_fusion.extract_transmute_book", traitLabel
                    ).getString();
                    case "STRIP_SINGLE_ENCHANTMENT" -> Component.translatable(
                        "cet.enchantment_fusion.strip_transmute", traitLabel
                    ).getString();
                    case "DOWNGRADE_ENCHANTMENT" -> {
                        String nextLabel = formatTransmuteLabel(target, parsed.underlyingEnchantId(), level, registries);
                        yield Component.translatable(
                            "cet.enchantment_fusion.downgrade_transmute", traitLabel, nextLabel
                        ).getString();
                    }
                    default -> traitLabel;
                };
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveGroupHeaderTitle(
        ItemStack target,
        Identifier id,
        Holder<Enchantment> holder,
        ItemEnchantments currentVanilla,
        Minecraft mc
    ) {
        ParsedCleanseId parsed = CetCleanseIds.parse(id);
        HolderLookup.Provider reg = mc.level != null ? mc.level.registryAccess() : null;
        if (parsed == null) {
            int lv = currentVanilla.getLevel(holder);
            return Enchantment.getFullname(holder, lv).getString();
        }
        if (parsed.kind() == Kind.FUSED) {
            FusedEnchantmentList list = target.getOrDefault(
                FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY
            );
            if (parsed.pairIndex() >= 0 && parsed.pairIndex() < list.size()) {
                String label = formatPairLabel(list.pairs().get(parsed.pairIndex()), reg);
                return Component.translatable("cet.enchantment_fusion.group.fused", label).getString();
            }
        }
        if (parsed.kind() == Kind.TRANSMUTE) {
            Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
            int lv = trans.getOrDefault(parsed.underlyingEnchantId(), 1);
            String label = formatTransmuteLabel(target, parsed.underlyingEnchantId(), lv, reg);
            return Component.translatable("cet.enchantment_fusion.group.transmute", label).getString();
        }
        return Enchantment.getFullname(holder, 1).getString();
    }

    private static int resolveGroupAccentColor(Identifier id) {
        ParsedCleanseId parsed = CetCleanseIds.parse(id);
        if (parsed == null) {
            return 0xFF7AD6B8; // JADE_MINT
        }
        return parsed.kind() == Kind.FUSED ? 0xFFD9B25F : 0xFF7FC8F8;
    }

    private static int computeGroupEssenceBadge(
        ItemStack target,
        Identifier id,
        Holder<Enchantment> holder,
        ItemEnchantments currentVanilla,
        Object lrCfg,
        double refScale,
        double extScale,
        Method provEssenceM
    ) throws Exception {
        ParsedCleanseId parsed = CetCleanseIds.parse(id);
        if (parsed == null) {
            int lv = currentVanilla.getLevel(holder);
            return (Integer) provEssenceM.invoke(null, lrCfg, target, holder, lv, refScale, extScale);
        }
        if (parsed.kind() == Kind.FUSED) {
            FusedEnchantmentList list = target.getOrDefault(
                FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY
            );
            if (parsed.pairIndex() >= 0 && parsed.pairIndex() < list.size()) {
                return CetCleanseOperations.essenceForFusedPair(list.pairs().get(parsed.pairIndex()), refScale);
            }
        }
        if (parsed.kind() == Kind.TRANSMUTE) {
            Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
            int lv = trans.getOrDefault(parsed.underlyingEnchantId(), 1);
            return CetCleanseOperations.essenceForTransmuted(lv, refScale);
        }
        return 0;
    }

    private static String formatPairLabel(FusedPair pair, @Nullable HolderLookup.Provider registries) {
        Optional<SignatureEntry> sig = SignaturePairs.resolve(pair);
        String aName = shortEnchantName(pair.primaryId(), pair.primaryLevel(), registries);
        String bName = shortEnchantName(pair.secondaryId(), pair.secondaryLevel(), registries);
        String formula = "[" + aName + " ▷ " + bName + "]";
        if (sig.isPresent()) {
            String sigName = Component.translatable(sig.get().titleKey()).getString();
            return sigName + " " + formula;
        }
        return formula;
    }

    private static String formatTransmuteLabel(
        ItemStack target,
        String enchantId,
        int level,
        @Nullable HolderLookup.Provider registries
    ) {
        Optional<TransmuteVariant> varOpt = CrossDomainTransmutations.resolve(enchantId, target);
        if (varOpt.isPresent()) {
            String title = Component.translatable(varOpt.get().titleKey()).getString();
            return title + " " + roman(level);
        }
        return shortEnchantName(enchantId, level, registries);
    }

    private static String shortEnchantName(
        String enchantId,
        int level,
        @Nullable HolderLookup.Provider registries
    ) {
        if (registries != null) {
            Optional<Holder<Enchantment>> h = FusionStationOperations.lookupEnchantment(registries, enchantId);
            if (h.isPresent()) {
                return Enchantment.getFullname(h.get(), level).getString();
            }
        }
        int colon = enchantId.indexOf(':');
        String shortPath = colon >= 0 ? enchantId.substring(colon + 1) : enchantId;
        return shortPath + " " + roman(level);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private static Object invoke0(Object receiver, String methodName) throws Exception {
        Method m = findZeroArgMethod(receiver.getClass(), methodName);
        m.setAccessible(true);
        return m.invoke(receiver);
    }

    private static Method findZeroArgMethod(Class<?> cls, String name) throws NoSuchMethodException {
        try {
            return cls.getMethod(name);
        } catch (NoSuchMethodException ex) {
            Class<?> cur = cls;
            while (cur != null) {
                try {
                    return cur.getDeclaredMethod(name);
                } catch (NoSuchMethodException ignored) {
                    cur = cur.getSuperclass();
                }
            }
            throw ex;
        }
    }

    private static Object getDeclaredField(Object receiver, String name) throws Exception {
        Class<?> cur = receiver.getClass();
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(receiver);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
