package chiseled_enchanting_table.block;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class ChiseledEnchantingTableBlockEntity extends BlockEntity implements Nameable, Container {
    private static final RandomSource RANDOM = RandomSource.create();
    public static final int DEFAULT_COLOR = 0xFFA020F0;

    public enum RitualCompletionKind {
        NATURAL,
        SACRIFICED_XP,
        RUSHED_NO_SACRIFICE
    }

    /** Chest-style persistent 2-slot container (slot 0: enchantable item/book, slot 1: lapis/material). */
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    /** Active queued inscriptions persisted across GUI close/reopen, chunk unloads, and world restarts. */
    private List<QueuedInscription> ritualQueue = new ArrayList<>();
    private long ritualTotalTicks = 0L;
    private long ritualRemainingTicks = 0L;
    @Nullable
    private UUID ritualOwnerUuid;

    @Nullable
    private Component customName;
    public volatile int color = -1;
    public volatile int epiphanySeed = 0;
    public volatile long epiphanyFreezeUntilGameTime = 0L;
    public volatile long pendingFreezeTicks = 0L;
    public volatile int frozenCycleIndex = 0;

    // Vanilla enchanting-table-style book animation state, copied as data fields so the 26.1 renderer can use it.
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;

    public ChiseledEnchantingTableBlockEntity(BlockPos pos, BlockState state) {
        super(EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, pos, state);
    }

    // ---- Persistent Container implementation (Chest-style persistence) ----

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT && hasActiveRitual()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT && hasActiveRitual()) return ItemStack.EMPTY;
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.items.size()) {
            return;
        }
        if (slot == ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT && hasActiveRitual()) return;
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        clearRitualState();
        this.setChanged();
    }

    // ---- Active Inscription Ritual state & progression ----

    public boolean hasActiveRitual() {
        return !this.ritualQueue.isEmpty() || !this.ritualCorruptQueue.isEmpty();
    }

    public long ritualRemainingTicks() {
        return Math.max(0L, this.ritualRemainingTicks);
    }

    public long ritualTotalTicks() {
        return Math.max(0L, this.ritualTotalTicks);
    }

    public List<QueuedInscription> queuedInscriptions() {
        return List.copyOf(this.ritualQueue);
    }

    public List<EnchantmentWithLevel> queuedEnchantmentSummary() {
        List<EnchantmentWithLevel> out = new ArrayList<>(this.ritualQueue.size());
        for (QueuedInscription q : this.ritualQueue) {
            out.add(new EnchantmentWithLevel(q.enchantmentId(), q.targetLevel()));
        }
        return List.copyOf(out);
    }

    public boolean anyQueuedStepUsedInfinityTome() {
        for (QueuedInscription q : this.ritualQueue) {
            if (q.usedInfinityTome()) {
                return true;
            }
        }
        return false;
    }

    public static final int MAX_RITUAL_STEPS = 256;
    public static final int MAX_RITUAL_CHARACTERS = 32_000;
    private long ritualSession;
    private ItemStack ritualTarget = ItemStack.EMPTY;
    private boolean ritualBlocked;
    private String ritualCorruptQueue = "";
    public long ritualSession() { return this.ritualSession; }
    public boolean ownsRitual(UUID actor) { return !hasActiveRitual() || actor.equals(this.ritualOwnerUuid); }
    public boolean canQueue(UUID actor) { return canQueue(actor, 0); }
    public boolean canQueue(UUID actor, int extraRefund) {
        long refund = Math.max(0, extraRefund);
        for (var step : ritualQueue) refund += step.conflictRefundPoints();
        if (refund > Integer.MAX_VALUE) return false;
        ItemStack target = getItem(ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT);
        return ownsRitual(actor) && !ritualBlocked && target.getCount() == 1
            && (ritualQueue.isEmpty() || ItemStack.isSameItemSameComponents(target, ritualTarget))
            && ritualQueue.size() < MAX_RITUAL_STEPS
            && InscriptionRitualMechanics.serializeQueue(ritualQueue).length() + 1024 <= MAX_RITUAL_CHARACTERS
            && ritualRemainingTicks <= Long.MAX_VALUE - InscriptionRitualMechanics.MAX_SINGLE_WAIT_TICKS
            && ritualTotalTicks <= Long.MAX_VALUE - InscriptionRitualMechanics.MAX_SINGLE_WAIT_TICKS;
    }
    public boolean cancelRitual(UUID actor) {
        if (!ownsRitual(actor)) return false;
        clearRitualState();
        setChanged();
        return true;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT || !hasActiveRitual();
    }
    @Override public boolean canTakeItem(net.minecraft.world.Container hopper, int slot, ItemStack stack) {
        return slot != ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT || !hasActiveRitual();
    }

    public void enqueueInscription(QueuedInscription step, int stepWaitTicks, @Nullable UUID playerUuid) {
        if (playerUuid == null || !canQueue(playerUuid, step.conflictRefundPoints()) || step.targetLevel() > 255
            || step.encode().length() > 1024) throw new IllegalStateException("Ritual admission rejected");
        if (this.ritualQueue.isEmpty()) {
            this.ritualTarget = getItem(ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT).copy();
            this.ritualSession = java.util.UUID.randomUUID().getLeastSignificantBits();
            if (this.ritualSession == 0) this.ritualSession = 1;
        }
        long add = Math.max(1L, Math.min(InscriptionRitualMechanics.MAX_SINGLE_WAIT_TICKS, stepWaitTicks));
        this.ritualTotalTicks = Math.addExact(this.ritualTotalTicks, add);
        this.ritualRemainingTicks = Math.addExact(this.ritualRemainingTicks, add);
        this.ritualQueue.add(step);
        this.ritualOwnerUuid = playerUuid;
        this.setChanged();
    }

    public void clearRitualState() {
        this.ritualQueue.clear();
        this.ritualTotalTicks = 0L;
        this.ritualRemainingTicks = 0L;
        this.ritualOwnerUuid = null;
        this.ritualTarget = ItemStack.EMPTY;
        this.ritualBlocked = false;
        this.ritualCorruptQueue = "";
    }

    /**
     * Finalizes ALL queued inscriptions onto persistent slot 0, applying overloaded Infinity Tome
     * transcription-slip («抄错») checks, provenance tracking, conflict refunds, and advancements.
     */
    public boolean completeActiveRitual(
        Level level,
        BlockPos pos,
        @Nullable ServerPlayer triggeringPlayer,
        RitualCompletionKind completionKind
    ) {
        if (this.ritualQueue.isEmpty()) {
            clearRitualState();
            return false;
        }
        ItemStack currentTarget = getItem(ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT);
        if (ritualBlocked) return false;
        if (currentTarget.getCount() != 1 || !ItemStack.isSameItemSameComponents(currentTarget, ritualTarget)) {
            ritualBlocked = true;
            chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.warn("Quarantined ritual target mismatch at {}", pos);
            return false;
        }
        for (QueuedInscription step : this.ritualQueue) {
            try {
                var holder = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(step.enchantmentId(), level);
                if (!currentTarget.is(Items.BOOK) && !currentTarget.is(Items.ENCHANTED_BOOK)
                    && !holder.value().canEnchant(currentTarget)) throw new IllegalArgumentException("Unsupported target");
            } catch (RuntimeException error) {
                ritualBlocked = true;
                chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.warn("Quarantined invalid ritual at {}", pos, error);
                return false;
            }
        }
        currentTarget = currentTarget.copy();

        ServerPlayer recipient = triggeringPlayer;
        if (recipient == null && this.ritualOwnerUuid != null && level.getServer() != null) {
            recipient = level.getServer().getPlayerList().getPlayer(this.ritualOwnerUuid);
        }

        List<QueuedInscription> finishing = List.copyOf(this.ritualQueue);

        var scan = InscriptionRitualMechanics.scanLibraryRitualContext(
            level,
            pos,
            ChiseledEnchantingTableData.shelfGatesConfig()
        );
        double slipChance = InscriptionRitualMechanics.computeTranscriptionSlipChance(
            scan.maxOverloadedInfinityTomeEnchants(),
            scan.totalDetectedBookshelves()
        );

        boolean targetWasBook = currentTarget.is(Items.BOOK) || currentTarget.is(Items.ENCHANTED_BOOK);
        if (currentTarget.is(Items.BOOK)) {
            ItemStack converted = new ItemStack(Items.ENCHANTED_BOOK, 1);
            var customNameComp = currentTarget.get(DataComponents.CUSTOM_NAME);
            if (customNameComp != null) {
                converted.set(DataComponents.CUSTOM_NAME, customNameComp);
            }
            currentTarget = converted;
        }

        ItemEnchantments.Mutable working = new ItemEnchantments.Mutable(
            EnchantmentHelper.getEnchantmentsForCrafting(currentTarget)
        );
        int totalConflictRefund = 0;
        boolean anySlipOccurred = false;
        boolean anyEpiphanyConsumed = false;
        boolean replicatedInfinityFromTome = false;
        Component lastSlippedDesc = null;
        int lastSlippedLevel = 1;

        for (QueuedInscription step : finishing) {
            Holder<Enchantment> holder;
            try {
                holder = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(step.enchantmentId(), level);
            } catch (RuntimeException ignored) {
                continue;
            }
            int finalLevel = InscriptionRitualMechanics.resolveTranscribedLevel(
                step.targetLevel(),
                scan.maxOverloadedInfinityTomeEnchants(),
                slipChance,
                level.getRandom().nextDouble(),
                level.getRandom().nextDouble()
            );
            if (finalLevel < step.targetLevel()) {
                anySlipOccurred = true;
                lastSlippedDesc = holder.value().description();
                lastSlippedLevel = finalLevel;
            }
            if (!targetWasBook) {
                var keys = new HashSet<>(working.keySet());
                for (var key : keys) {
                    if (!Enchantment.areCompatible(key, holder)) {
                        working.set(key, 0);
                    }
                }
            }
            working.set(holder, finalLevel);
            totalConflictRefund += Math.max(0, step.conflictRefundPoints());
            if (step.consumedEpiphany()) {
                anyEpiphanyConsumed = true;
            }
            if (step.masterTomeUsed() && MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(step.enchantmentId())) {
                replicatedInfinityFromTome = true;
            }
            EnchantmentProvenance.recordTablePayment(currentTarget, step.enchantmentId(), step.previousLevel(), finalLevel);
        }

        EnchantmentHelper.updateEnchantments(currentTarget, m -> {
            var mKeys = new HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var key : working.keySet()) {
                m.set(key, working.getLevel(key));
            }
        });
        EnchantmentProvenance.pruneToCurrentEnchantments(
            currentTarget,
            EnchantmentHelper.getEnchantmentsForCrafting(currentTarget)
        );

        this.items.set(ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT, currentTarget);
        clearRitualState();
        this.setChanged();

        if (totalConflictRefund > 0 && level instanceof ServerLevel serverLevel) {
            ExperienceOrb.award(
                serverLevel,
                net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0, 0.9, 0.0),
                totalConflictRefund
            );
        }

        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.4F, 1.0F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.1F, 1.15F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                pos.getX() + 0.5D,
                pos.getY() + 1.25D,
                pos.getZ() + 0.5D,
                28,
                0.35D,
                0.25D,
                0.35D,
                0.6D
            );
        }

        if (recipient != null) {
            if (anySlipOccurred && lastSlippedDesc != null) {
                Advancement.give(recipient, "scribes_slip");
                recipient.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable(
                        "gui.chiseled_enchanting_table.ritual.slip_warning",
                        lastSlippedDesc,
                        lastSlippedLevel
                    ).withStyle(ChatFormatting.YELLOW)
                ));
            }
            if (replicatedInfinityFromTome) {
                Advancement.give(recipient, "eternal_codex");
            }
            if (anyEpiphanyConsumed) {
                Advancement.give(recipient, "inscribe_epiphany");
            }
            if (completionKind == RitualCompletionKind.NATURAL && finishing.size() >= 2) {
                Advancement.give(recipient, "patient_archivist");
            }
            if (currentTarget.is(Items.ENCHANTED_BOOK)) {
                Advancement.give(recipient, "enchant_to_book");
                Advancement.checkBookMilestones(recipient, level, currentTarget);
            }
        }

        this.setChanged();
        syncOpenMenusWatchingTable(level, pos);
        return true;
    }

    public static void syncOpenMenusWatchingTable(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return;
        }
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            if (sp.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                && menu.world() == level && pos.equals(menu.tableBlockPos())) {
                menu.refreshLiveShelfState();
                menu.pushRitualStateToViewer();
                menu.broadcastChanges();
            }
        }
    }

    @Override
    public @Nullable Object getRenderData() {
        return colorOrDefault();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.level != null) {
            resolvePendingFreeze(this.level.getGameTime());
        }
        ContainerHelper.saveAllItems(output, this.items);
        output.putLong("ritualSession", this.ritualSession);
        if (!this.ritualTarget.isEmpty()) output.store("ritualTarget", ItemStack.CODEC, this.ritualTarget);
        output.putString("ritualQueue", this.ritualCorruptQueue.isEmpty()
            ? InscriptionRitualMechanics.serializeQueue(this.ritualQueue) : this.ritualCorruptQueue);
        output.putBoolean("ritualBlocked", this.ritualBlocked);
        output.putLong("ritualTotalTicks", this.ritualTotalTicks);
        output.putLong("ritualRemainingTicks", this.ritualRemainingTicks);
        output.putString("ritualOwnerUuid", this.ritualOwnerUuid != null ? this.ritualOwnerUuid.toString() : "");
        output.putInt("color", color);
        output.putInt("epiphanySeed", epiphanySeed);
        output.putLong("epiphanyFreezeUntil", epiphanyFreezeUntilGameTime);
        output.putLong("pendingFreezeTicks", pendingFreezeTicks);
        output.putInt("frozenCycleIndex", frozenCycleIndex);
        output.storeNullable("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.ritualSession = input.getLongOr("ritualSession", 0L);
        this.ritualTarget = input.read("ritualTarget", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        String rawQueue = input.getStringOr("ritualQueue", "");
        this.ritualCorruptQueue = "";
        this.ritualBlocked = input.getBooleanOr("ritualBlocked", false);
        try {
            this.ritualQueue = InscriptionRitualMechanics.deserializeQueue(rawQueue);
        } catch (IllegalArgumentException malformed) {
            this.ritualQueue = new ArrayList<>();
            this.ritualCorruptQueue = rawQueue.substring(0, Math.min(rawQueue.length(), MAX_RITUAL_CHARACTERS));
            this.ritualBlocked = true;
            chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.error(
                "Quarantined malformed ritual at {}; inventory retained, owner may cancel. Diagnostic queue limited to {} characters.",
                getBlockPos(), MAX_RITUAL_CHARACTERS, malformed);
        }
        this.ritualTotalTicks = Math.max(0L, input.getLongOr("ritualTotalTicks", 0L));
        this.ritualRemainingTicks = Math.max(0L, input.getLongOr("ritualRemainingTicks", 0L));
        String uuidStr = input.getStringOr("ritualOwnerUuid", "");
        if (!uuidStr.isBlank()) {
            try {
                this.ritualOwnerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                this.ritualOwnerUuid = null;
            }
        } else {
            this.ritualOwnerUuid = null;
        }
        int newColor = input.getIntOr("color", -1);
        this.epiphanySeed = input.getIntOr("epiphanySeed", 0);
        this.epiphanyFreezeUntilGameTime = input.getLongOr("epiphanyFreezeUntil", 0L);
        this.pendingFreezeTicks = input.getLongOr("pendingFreezeTicks", 0L);
        this.frozenCycleIndex = input.getIntOr("frozenCycleIndex", 0);
        boolean colorChanged = this.color != newColor;
        this.color = newColor;
        this.customName = parseCustomNameSafe(input, "CustomName");
        if (colorChanged && level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
        }
    }

    private void resolvePendingFreeze(long gameTime) {
        if (this.pendingFreezeTicks > 0L) {
            this.epiphanyFreezeUntilGameTime = gameTime + this.pendingFreezeTicks;
            this.pendingFreezeTicks = 0L;
            this.setChanged();
        }
    }

    public int getOrInitEpiphanySeed() {
        if (this.epiphanySeed == 0) {
            int init = (int) Mth.getSeed(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            if (init == 0) init = 1;
            this.epiphanySeed = init;
            this.setChanged();
        }
        return this.epiphanySeed;
    }

    public boolean isEpiphanyFrozen(long gameTime) {
        resolvePendingFreeze(gameTime);
        return this.epiphanyFreezeUntilGameTime > gameTime;
    }

    public long remainingFreezeTicks(long gameTime) {
        resolvePendingFreeze(gameTime);
        return Math.max(0L, this.epiphanyFreezeUntilGameTime - gameTime);
    }

    public int activeCycleIndex(long gameTime, int cycleTicks) {
        if (isEpiphanyFrozen(gameTime)) {
            return this.frozenCycleIndex & 0xFFFF;
        }
        return (int) ((gameTime / Math.max(200L, cycleTicks)) & 0xFFFFL);
    }

    public void addFreezeTicks(long gameTime, int cycleTicks, int addTicks, int maxTicks) {
        resolvePendingFreeze(gameTime);
        if (!isEpiphanyFrozen(gameTime)) {
            this.frozenCycleIndex = (int) ((gameTime / Math.max(200L, cycleTicks)) & 0xFFFFL);
            this.epiphanyFreezeUntilGameTime = gameTime + Math.min(maxTicks, addTicks);
        } else {
            this.epiphanyFreezeUntilGameTime = Math.min(gameTime + maxTicks, this.epiphanyFreezeUntilGameTime + addTicks);
        }
        this.setChanged();
    }

    public int clearFreezeAndAdvanceSeed() {
        this.epiphanyFreezeUntilGameTime = 0L;
        this.pendingFreezeTicks = 0L;
        return advanceEpiphanySeed();
    }

    public int advanceEpiphanySeed() {
        int cur = getOrInitEpiphanySeed();
        int next = cur * 1664525 + 1013904223;
        if (next == 0) next = 1;
        this.epiphanySeed = next;
        this.setChanged();
        return next;
    }

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        ChiseledEnchantingTableBlockEntity blockEntity
    ) {
        if (level.isClientSide()) {
            return;
        }
        if (blockEntity.hasActiveRitual() && !blockEntity.ritualBlocked) {
            blockEntity.ritualRemainingTicks = Math.max(0, blockEntity.ritualRemainingTicks - 1);
            if (level instanceof ServerLevel serverLevel && (level.getGameTime() % 10L == 0L)) {
                serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.15D,
                    pos.getZ() + 0.5D,
                    3,
                    0.22D,
                    0.15D,
                    0.22D,
                    0.18D
                );
            }
            if (blockEntity.ritualRemainingTicks <= 0L) {
                blockEntity.completeActiveRitual(level, pos, null, RitualCompletionKind.NATURAL);
            } else if (level.getGameTime() % 20L == 0L) {
                blockEntity.setChanged();
            }
        }
        // Stagger shelf scans across 20 ticks by block position hash so multiple tables never spike on tick 0.
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20L) != 0L) {
            return;
        }
        var resonanceCfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        if (!resonanceCfg.tomeRegenEnabled()
            || resonanceCfg.tomeConsumptionMode() == MasterTomeWear.ConsumptionMode.KEEP) {
            return;
        }
        ChiseledEnchantingTableBlock.ShelfPower power = ChiseledEnchantingTableBlock.calculateShelfPower(level, pos, false);
        MasterTomeWear.tickLibraryRegeneration(
            level,
            pos,
            ChiseledEnchantingTableData.shelfGatesConfig(),
            resonanceCfg,
            power.totalLibraryBookshelves()
        );
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChiseledEnchantingTableBlockEntity blockEntity) {
        bookAnimationTick(level, pos, state, blockEntity);
    }

    private static void bookAnimationTick(Level level, BlockPos pos, BlockState state, ChiseledEnchantingTableBlockEntity blockEntity) {
        blockEntity.oOpen = blockEntity.open;
        blockEntity.oRot = blockEntity.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0D, false);
        if (player != null || blockEntity.hasActiveRitual()) {
            if (player != null) {
                double dx = player.getX() - (pos.getX() + 0.5D);
                double dz = player.getZ() - (pos.getZ() + 0.5D);
                blockEntity.tRot = (float) Mth.atan2(dz, dx);
            } else {
                blockEntity.tRot += 0.08F;
            }
            blockEntity.open += 0.1F;
            if (blockEntity.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float previousFlipTarget = blockEntity.flipT;
                do {
                    blockEntity.flipT += (float) (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (previousFlipTarget == blockEntity.flipT);
            }
        } else {
            blockEntity.tRot += 0.02F;
            blockEntity.open -= 0.1F;
        }

        while (blockEntity.rot >= Mth.PI) blockEntity.rot -= Mth.TWO_PI;
        while (blockEntity.rot < -Mth.PI) blockEntity.rot += Mth.TWO_PI;
        while (blockEntity.tRot >= Mth.PI) blockEntity.tRot -= Mth.TWO_PI;
        while (blockEntity.tRot < -Mth.PI) blockEntity.tRot += Mth.TWO_PI;

        float rotationDelta = blockEntity.tRot - blockEntity.rot;
        while (rotationDelta >= Mth.PI) rotationDelta -= Mth.TWO_PI;
        while (rotationDelta < -Mth.PI) rotationDelta += Mth.TWO_PI;
        blockEntity.rot += rotationDelta * 0.4F;
        blockEntity.open = Mth.clamp(blockEntity.open, 0.0F, 1.0F);
        ++blockEntity.time;
        blockEntity.oFlip = blockEntity.flip;
        float flipVelocity = (blockEntity.flipT - blockEntity.flip) * 0.4F;
        flipVelocity = Mth.clamp(flipVelocity, -0.2F, 0.2F);
        blockEntity.flipA += (flipVelocity - blockEntity.flipA) * 0.9F;
        blockEntity.flip += blockEntity.flipA;
    }

    @Override
    public Component getName() {
        return this.customName != null ? this.customName : Component.translatable("container.chiseled_enchanting_table.chiseled_enchanting_table");
    }

    public void setCustomName(@Nullable Component customName) {
        this.customName = customName;
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return this.customName;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        DyedItemColor dyedColor = components.get(DataComponents.DYED_COLOR);
        this.color = dyedColor != null ? (dyedColor.rgb() & 0x00FFFFFF) : -1;
        this.customName = components.get(DataComponents.CUSTOM_NAME);
        CustomData customData = components.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            this.epiphanySeed = tag.getIntOr("epiphanySeed", 0);
            this.pendingFreezeTicks = tag.getLongOr("pendingFreezeTicks", 0L);
            this.frozenCycleIndex = tag.getIntOr("frozenCycleIndex", 0);
            if (this.level != null) {
                resolvePendingFreeze(this.level.getGameTime());
            }
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        if (this.customName != null) {
            componentMapBuilder.set(DataComponents.CUSTOM_NAME, this.customName);
        }
        if (this.color != -1) {
            componentMapBuilder.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color & 0x00FFFFFF));
        }
        long rem = this.pendingFreezeTicks > 0L
            ? this.pendingFreezeTicks
            : (this.level != null ? remainingFreezeTicks(this.level.getGameTime()) : 0L);
        if (rem > 0L) {
            CompoundTag tag = new CompoundTag();
            if (this.epiphanySeed != 0) {
                tag.putInt("epiphanySeed", this.epiphanySeed);
            }
            tag.putLong("pendingFreezeTicks", rem);
            tag.putInt("frozenCycleIndex", this.frozenCycleIndex);
            int totalSec = (int) ((rem + 19L) / 20L);
            String clock = String.format("%02d:%02d", totalSec / 60, totalSec % 60);
            componentMapBuilder.set(
                DataComponents.LORE,
                new ItemLore(java.util.List.of(
                    Component.translatable("item.chiseled_enchanting_table.frozen_lore", clock)
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.AQUA))
                ))
            );
            componentMapBuilder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("color");
        output.discard("epiphanySeed");
        output.discard("epiphanyFreezeUntil");
        output.discard("pendingFreezeTicks");
        output.discard("frozenCycleIndex");
        output.discard("ritualSession");
        output.discard("ritualTarget");
        output.discard("ritualQueue");
        output.discard("ritualBlocked");
        output.discard("ritualTotalTicks");
        output.discard("ritualRemainingTicks");
        output.discard("ritualOwnerUuid");
    }

    public DataComponentMap getAllComponents() {
        return this.collectComponents();
    }

    public int colorOrDefault() {
        return color == -1 ? DEFAULT_COLOR : (0xFF000000 | (color & 0x00FFFFFF));
    }
}
