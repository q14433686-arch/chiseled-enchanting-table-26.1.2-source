package enchantment_fusion.compat;

import enchantment_fusion.compat.CetCleanseIds.Kind;
import enchantment_fusion.compat.CetCleanseIds.ParsedCleanseId;
import enchantment_fusion.compat.CetCleanseOperations.ExtractResult;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Server/common reflection bridge invoked by optional {@code @Pseudo} Mixins when sister mod
 * {@code 纂刻附魔台 (chiseled-enchanting-table)} is installed.
 *
 * <p>Contains zero compile-time references to {@code chiseled_enchanting_table.*} or client classes,
 * keeping standalone compilation and architecture boundaries 100% clean.</p>
 */
public final class CetCleanseServerBridge {
    private CetCleanseServerBridge() {}

    /**
     * Intercepts {@code DisenchantmentApplication.apply(menu, payload)} for synthetic Fusion IDs,
     * mirrored primary guards, and combined {@code DISENCHANT_ALL} / {@code PURGE_ALL_CURSES} passes.
     *
     * @return {@code true} if handled (caller should cancel {@code CallbackInfo}).
     */
    @SuppressWarnings("unchecked")
    public static boolean tryHandleApply(Object menu, Object payload) {
        try {
            Boolean ritualActive = (Boolean) invoke0(menu, "isRitualActive");
            if (Boolean.TRUE.equals(ritualActive)) {
                return false;
            }
            ItemStack target = (ItemStack) invoke0(menu, "getEnchantableItem");
            if (target == null || target.isEmpty() || target.getCount() != 1) {
                return false;
            }
            Object optTypeEnum = invoke0(payload, "option_type");
            String optName = optTypeEnum instanceof Enum<?> en ? en.name() : String.valueOf(optTypeEnum);
            Identifier enchId = (Identifier) invoke0(payload, "enchantment_id");
            int reqLevel = (Integer) invoke0(payload, "enchantment_level");
            int costIdx = (Integer) invoke0(payload, "cost_option_index");
            Player player = (Player) getField(menu, "player");
            Level world = (Level) invoke0(menu, "world");

            // 1. Guard mirrored native primaries so vanilla strip/extract cannot duplicate a socketed FusedPair
            if (!CetCleanseIds.isSyntheticCleanseId(enchId)
                && CetCleanseOperations.isMirroredFusionPrimary(target, enchId)
                && ("STRIP_SINGLE_ENCHANTMENT".equals(optName)
                    || "EXTRACT_ENCHANTMENT_BOOK".equals(optName)
                    || "DOWNGRADE_ENCHANTMENT".equals(optName))) {
                return true;
            }

            // 2. Combined DISENCHANT_ALL when stack holds clean FUSED_PAIRS or TRANSMUTED_ENCHANTS
            if ("DISENCHANT_ALL".equals(optName) && CetCleanseOperations.hasAnyCleanseableData(target)) {
                Class<?> disAppCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication");
                Method totalRefundM = disAppCls.getMethod("totalFullDisenchantRefund",
                    Class.forName("chiseled_enchanting_table.mechanics.resonance.LibraryResonance$Config"),
                    ItemStack.class, double.class, double.class);
                Object lrCfg = invoke0(menu, "libraryResonanceConfig");
                double refScale = (Double) invoke0(menu, "disenchantRefundScale");
                double extScale = (Double) invoke0(menu, "disenchantExternalRefundScale");
                int totalRefund = (Integer) totalRefundM.invoke(null, lrCfg, target, refScale, extScale);

                ItemStack resulting = CetCleanseOperations.disenchantAllCombined(target);
                if (resulting == null) {
                    return true;
                }
                reconcileInkStainIfPresent(resulting);
                invoke1(menu, "setEnchantableSlot", ItemStack.class, resulting);
                if (totalRefund > 0) {
                    disAppCls.getMethod("awardRefundOrbs", menu.getClass(), int.class).invoke(null, menu, totalRefund);
                }
                playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 1.0F);
                emitCetVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), "onCleanseDisenchantAll");
                return true;
            }

            // 3. Combined PURGE_ALL_CURSES: let vanilla purge any vanilla curses, AND purify affordable Aberrant fusions!
            if ("PURGE_ALL_CURSES".equals(optName) && CetCleanseOperations.countAberrantFusions(target) > 0) {
                Boolean allowPurge = (Boolean) invoke0(menu, "disenchantAllowCursePurge");
                if (!Boolean.TRUE.equals(allowPurge)) {
                    return false;
                }
                // First purify each aberrant pair that the player can afford
                ItemStack working = target.copy();
                FusedEnchantmentList list = working.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                int purifiedCount = 0;
                for (int i = 0; i < list.size(); i++) {
                    FusedPair pair = list.pairs().get(i);
                    if (!pair.isAberrant()) continue;
                    Identifier synId = CetCleanseIds.aberrantId(i, pair.primaryId());
                    int effLv = Math.max(1, Math.max(pair.primaryLevel(), pair.secondaryLevel()));
                    if (tryPayCetCost(menu, player, synId, effLv, "PURGE_CURSE", -1, false)) {
                        ItemStack next = CetCleanseOperations.purifySingleAberrant(working, i);
                        if (next != null) {
                            working = next;
                            list = working.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                            purifiedCount++;
                        }
                    }
                }
                if (purifiedCount > 0) {
                    invoke1(menu, "setEnchantableSlot", ItemStack.class, working);
                    playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 0.78F);
                    emitCetPurgeVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), purifiedCount);
                }
                // Do NOT cancel so CleanseMechanics.applyPurgeAllCurses can also purge any vanilla curses on the updated slot!
                return false;
            }

            // 4. Targeted Synthetic Cleanse IDs
            ParsedCleanseId parsed = CetCleanseIds.parse(enchId);
            if (parsed == null) {
                return false;
            }

            switch (optName) {
                case "PURGE_CURSE" -> {
                    Boolean allowPurge = (Boolean) invoke0(menu, "disenchantAllowCursePurge");
                    if (!Boolean.TRUE.equals(allowPurge) || parsed.kind() != Kind.ABERRANT) {
                        return true;
                    }
                    FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                    if (parsed.pairIndex() < 0 || parsed.pairIndex() >= list.size()) {
                        return true;
                    }
                    FusedPair pair = list.pairs().get(parsed.pairIndex());
                    if (!pair.isAberrant()) {
                        return true;
                    }
                    int effLv = Math.max(1, Math.max(pair.primaryLevel(), pair.secondaryLevel()));
                    if (!tryPayCetCost(menu, player, enchId, effLv, "PURGE_CURSE", costIdx, false)) {
                        return true;
                    }
                    ItemStack purified = CetCleanseOperations.purifySingleAberrant(target, parsed.pairIndex());
                    if (purified != null) {
                        invoke1(menu, "setEnchantableSlot", ItemStack.class, purified);
                        playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 0.74F);
                        emitCetPurgeVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), 1);
                    }
                    return true;
                }
                case "STRIP_SINGLE_ENCHANTMENT" -> {
                    if (CetCleanseOperations.countTotalIndependentTraits(target) <= 1) {
                        return true;
                    }
                    ItemStack stripped = null;
                    int effLv = Math.max(1, reqLevel);
                    if (parsed.kind() == Kind.FUSED) {
                        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        if (parsed.pairIndex() >= 0 && parsed.pairIndex() < list.size()) {
                            FusedPair p = list.pairs().get(parsed.pairIndex());
                            effLv = Math.max(1, Math.max(p.primaryLevel(), p.secondaryLevel()));
                            if (tryPayCetCost(menu, player, enchId, effLv, "STRIP_SINGLE_ENCHANTMENT", costIdx, false)) {
                                stripped = CetCleanseOperations.stripSingleFusedPair(target, parsed.pairIndex());
                            }
                        }
                    } else if (parsed.kind() == Kind.TRANSMUTE) {
                        Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                        int curLv = trans.getOrDefault(parsed.underlyingEnchantId(), 0);
                        if (curLv > 0 && tryPayCetCost(menu, player, enchId, curLv, "STRIP_SINGLE_ENCHANTMENT", costIdx, false)) {
                            stripped = CetCleanseOperations.stripSingleTransmuted(target, parsed.underlyingEnchantId());
                        }
                    }
                    if (stripped != null) {
                        reconcileInkStainIfPresent(stripped);
                        invoke1(menu, "setEnchantableSlot", ItemStack.class, stripped);
                        playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 0.96F);
                        emitCetExtractOrStripVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), false);
                    }
                    return true;
                }
                case "EXTRACT_ENCHANTMENT_BOOK" -> {
                    if (!CetCleanseOperations.canExtractAnyFusionTraitToBook(target)) {
                        return true;
                    }
                    ExtractResult extracted = null;
                    if (parsed.kind() == Kind.FUSED) {
                        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                        if (parsed.pairIndex() >= 0 && parsed.pairIndex() < list.size()) {
                            FusedPair p = list.pairs().get(parsed.pairIndex());
                            int effLv = Math.max(1, Math.max(p.primaryLevel(), p.secondaryLevel()));
                            if (!p.isAberrant() && tryPayCetCost(menu, player, enchId, effLv, "EXTRACT_ENCHANTMENT_BOOK", costIdx, true)) {
                                extracted = CetCleanseOperations.extractFusedPairToBook(target, parsed.pairIndex());
                            }
                        }
                    } else if (parsed.kind() == Kind.TRANSMUTE && world != null) {
                        Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                        int curLv = trans.getOrDefault(parsed.underlyingEnchantId(), 0);
                        if (curLv > 0 && tryPayCetCost(menu, player, enchId, curLv, "EXTRACT_ENCHANTMENT_BOOK", costIdx, true)) {
                            extracted = CetCleanseOperations.extractTransmutedToBook(
                                target, parsed.underlyingEnchantId(), world.registryAccess()
                            );
                        }
                    }
                    if (extracted != null) {
                        ItemStack updated = extracted.updatedTarget();
                        reconcileInkStainIfPresent(updated);
                        invoke1(menu, "setEnchantableSlot", ItemStack.class, updated);
                        player.getInventory().placeItemBackInInventory(extracted.extractedBook());
                        playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 1.24F);
                        emitCetExtractOrStripVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), true);
                    }
                    return true;
                }
                case "DOWNGRADE_ENCHANTMENT" -> {
                    if (parsed.kind() != Kind.TRANSMUTE) {
                        return true;
                    }
                    Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
                    int curLv = trans.getOrDefault(parsed.underlyingEnchantId(), 0);
                    if (curLv <= 1 || reqLevel < 1 || reqLevel >= curLv) {
                        return true;
                    }
                    ItemStack downgraded = CetCleanseOperations.downgradeTransmuted(target, parsed.underlyingEnchantId(), reqLevel);
                    if (downgraded != null) {
                        double refScale = (Double) invoke0(menu, "disenchantRefundScale");
                        int refund = CetCleanseOperations.essenceForTransmutedStepDown(curLv, reqLevel, refScale);
                        invoke1(menu, "setEnchantableSlot", ItemStack.class, downgraded);
                        if (refund > 0) {
                            Class<?> disAppCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication");
                            disAppCls.getMethod("awardRefundOrbs", menu.getClass(), int.class).invoke(null, menu, refund);
                        }
                        playCetCleanseFeedback(player, (BlockPos) invoke0(menu, "tableBlockPos"), 1.12F);
                        emitCetExtractOrStripVisual(world, (BlockPos) invoke0(menu, "tableBlockPos"), false);
                    }
                    return true;
                }
                default -> {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryPayCetCost(
        Object menu,
        Player player,
        Identifier id,
        int level,
        String optionTypeName,
        int costIdx,
        boolean checkCatalystFreeXp
    ) throws Exception {
        Class<?> ewlCls = Class.forName("chiseled_enchanting_table.utils.EnchantmentWithLevel");
        Constructor<?> ewlCtor = ewlCls.getConstructor(Identifier.class, int.class);
        Object ewl = ewlCtor.newInstance(id, level);

        Class<? extends Enum> optCls = (Class<? extends Enum>) Class.forName(
            "chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads$OptionType"
        );
        Object optEnum = Enum.valueOf(optCls, optionTypeName);

        Method resolveBundle = menu.getClass().getMethod("resolveCleanseCostBundle", ewlCls, optCls, int.class);
        List<ItemStack> matchedBundle = (List<ItemStack>) resolveBundle.invoke(menu, ewl, optEnum, costIdx);
        if (!player.getAbilities().instabuild && matchedBundle == null) {
            return false;
        }

        boolean catalystRoute = false;
        if (checkCatalystFreeXp) {
            Class<?> quotesCls = Class.forName("chiseled_enchanting_table.menu.MenuCleanseQuotes");
            catalystRoute = (Boolean) quotesCls.getMethod("bundleUsesCatalyst", List.class).invoke(null, matchedBundle);
        }

        Class<?> xpConvCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.XpCostConversion");
        Class<?> xpReqCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.XpCostConversion$XpRequirement");
        Object xpReq;
        if (catalystRoute) {
            xpReq = xpReqCls.getField("FREE").get(null);
        } else {
            Method resolveXp = menu.getClass().getMethod("resolveEffectiveXpRequirement", ewlCls, optCls);
            xpReq = resolveXp.invoke(menu, ewl, optEnum);
        }
        boolean xpMet = (Boolean) xpReqCls.getMethod("isMetBy", Player.class).invoke(xpReq, player);
        if (!xpMet) {
            return false;
        }

        if (!catalystRoute) {
            xpConvCls.getMethod("deductXp", Player.class, xpReqCls).invoke(null, player, xpReq);
        }
        menu.getClass().getMethod("deductCleanseBundle", List.class).invoke(menu, matchedBundle);
        return true;
    }

    public static boolean canPurgeAnyAberrantNow(Object menu) {
        try {
            Boolean canPurge = (Boolean) invoke0(menu, "disenchantAllowCursePurge");
            if (!Boolean.TRUE.equals(canPurge)) {
                return false;
            }
            ItemStack target = (ItemStack) invoke0(menu, "getEnchantableItem");
            return CetCleanseOperations.countAberrantFusions(target) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static List<List<ItemStack>> overrideStripMaterialOptionsIfMultiTrait(
        Object pricingAccess,
        Object ewl,
        Object optionType
    ) {
        try {
            String optName = optionType instanceof Enum<?> en ? en.name() : String.valueOf(optionType);
            if (!"STRIP_SINGLE_ENCHANTMENT".equals(optName)) {
                return null;
            }
            ItemStack target = (ItemStack) invoke0(pricingAccess, "getEnchantableItem");
            if (!CetCleanseOperations.hasAnyCleanseableData(target)) {
                return null;
            }
            if (CetCleanseOperations.countTotalIndependentTraits(target) <= 1) {
                return List.of();
            }
            Method m = pricingAccess.getClass().getMethod(
                "getEnchantmentItemCostOptions", ewl.getClass(), optionType.getClass()
            );
            @SuppressWarnings("unchecked")
            List<List<ItemStack>> res = (List<List<ItemStack>>) m.invoke(pricingAccess, ewl, optionType);
            return res;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static Object overrideDowngradeXpRequirementIfTransmute(
        Object pricingAccess,
        Object ewl,
        Object optionType
    ) {
        try {
            String optName = optionType instanceof Enum<?> en ? en.name() : String.valueOf(optionType);
            if (!"DOWNGRADE_ENCHANTMENT".equals(optName)) {
                return null;
            }
            Identifier id = (Identifier) invoke0(ewl, "enchantment_id");
            ParsedCleanseId parsed = CetCleanseIds.parse(id);
            if (parsed == null || parsed.kind() != Kind.TRANSMUTE) {
                return null;
            }
            ItemStack target = (ItemStack) invoke0(pricingAccess, "getEnchantableItem");
            Map<String, Integer> trans = target.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
            int curLv = trans.getOrDefault(parsed.underlyingEnchantId(), 0);
            int nextLv = (Integer) invoke0(ewl, "enchantment_level");
            double scale = (Double) invoke0(pricingAccess, "disenchantRefundScale");
            int refund = CetCleanseOperations.essenceForTransmutedStepDown(curLv, nextLv, scale);
            Class<?> xpReqCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.XpCostConversion$XpRequirement");
            return xpReqCls.getMethod("refund", int.class).invoke(null, refund);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryQuickMoveFusionItemIntoTargetSlot(Object quickMoveAccess, int slotIndex, Player player) {
        try {
            // ENCHANTABLE_SLOT = 0, COST_SLOT = 1 in ChiseledEnchantingTableScreenHandler
            if (slotIndex == 0 || slotIndex == 1) {
                return false;
            }
            Boolean ritualActive = (Boolean) invoke0(quickMoveAccess, "ritualActive");
            if (Boolean.TRUE.equals(ritualActive)) {
                return false;
            }
            int slotCount = (Integer) invoke0(quickMoveAccess, "slotCount");
            if (slotIndex < 0 || slotIndex >= slotCount) {
                return false;
            }
            Method slotM = quickMoveAccess.getClass().getDeclaredMethod("slot", int.class);
            slotM.setAccessible(true);
            Slot sourceSlot = (Slot) slotM.invoke(quickMoveAccess, slotIndex);
            Slot targetSlot = (Slot) slotM.invoke(quickMoveAccess, 0);
            if (sourceSlot == null || targetSlot == null || !sourceSlot.hasItem() || targetSlot.hasItem()) {
                return false;
            }
            ItemStack clicked = sourceSlot.getItem();
            if (!CetCleanseOperations.hasAnyCleanseableData(clicked) || !targetSlot.mayPlace(clicked)) {
                return false;
            }
            ItemStack single = clicked.copyWithCount(1);
            targetSlot.set(single);
            if (clicked.getCount() > 1) {
                sourceSlot.set(clicked.copyWithCount(clicked.getCount() - 1));
            } else {
                sourceSlot.set(ItemStack.EMPTY);
            }
            Method changedM = quickMoveAccess.getClass().getDeclaredMethod("changed");
            changedM.setAccessible(true);
            changedM.invoke(quickMoveAccess);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void reconcileInkStainIfPresent(ItemStack stack) {
        try {
            Class<?> inkCls = Class.forName("chiseled_enchanting_table.mechanics.enchanting.InkStainLore");
            inkCls.getMethod("reconcileAfterRemovalCleanse", ItemStack.class).invoke(null, stack);
        } catch (Throwable ignored) {
        }
    }

    private static void playCetCleanseFeedback(@Nullable Player player, @Nullable BlockPos tablePos, float pitch) {
        if (player != null && player.level() instanceof ServerLevel sl) {
            BlockPos p = tablePos != null ? tablePos : player.blockPosition();
            sl.playSound(null, p, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.45F, pitch);
            sl.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.90F, 1.22F);
        }
    }

    private static void emitCetVisual(@Nullable Level world, @Nullable BlockPos pos, String methodName) {
        try {
            Class<?> vis = Class.forName("chiseled_enchanting_table.menu.TableVisualEvents");
            vis.getMethod(methodName, Level.class, BlockPos.class).invoke(null, world, pos);
        } catch (Throwable ignored) {
        }
    }

    private static void emitCetPurgeVisual(@Nullable Level world, @Nullable BlockPos pos, int count) {
        try {
            Class<?> vis = Class.forName("chiseled_enchanting_table.menu.TableVisualEvents");
            vis.getMethod("onCleansePurgeCurse", Level.class, BlockPos.class, int.class).invoke(null, world, pos, count);
        } catch (Throwable ignored) {
        }
    }

    private static void emitCetExtractOrStripVisual(@Nullable Level world, @Nullable BlockPos pos, boolean extract) {
        try {
            Class<?> vis = Class.forName("chiseled_enchanting_table.menu.TableVisualEvents");
            vis.getMethod("onCleanseExtractOrStrip", Level.class, BlockPos.class, boolean.class)
                .invoke(null, world, pos, extract);
        } catch (Throwable ignored) {
        }
    }

    private static Object invoke0(Object receiver, String methodName) throws Exception {
        Method m = findZeroArgMethod(receiver.getClass(), methodName);
        m.setAccessible(true);
        return m.invoke(receiver);
    }

    private static Object invoke1(Object receiver, String methodName, Class<?> paramType, Object arg) throws Exception {
        Method m = receiver.getClass().getMethod(methodName, paramType);
        m.setAccessible(true);
        return m.invoke(receiver, arg);
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

    private static Object getField(Object receiver, String fieldName) throws Exception {
        Field f = receiver.getClass().getField(fieldName);
        f.setAccessible(true);
        return f.get(receiver);
    }
}
