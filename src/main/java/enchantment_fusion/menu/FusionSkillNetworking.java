package enchantment_fusion.menu;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.DurabilityReserveSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
import enchantment_fusion.mechanics.signatures.FireTradeoffSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SealedOreSkills;
import enchantment_fusion.mechanics.signatures.FortuneBankSkills;
import enchantment_fusion.mechanics.signatures.WaterPressureSkills;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** One rebindable active-skill input; the server decides what can be activated. */
public final class FusionSkillNetworking {
    public record ActivatePayload() implements CustomPacketPayload {
        public static final Type<ActivatePayload> TYPE = new Type<>(EnchantmentFusion.id("activate_skill"));
        public static final ActivatePayload INSTANCE = new ActivatePayload();
        public static final StreamCodec<ByteBuf, ActivatePayload> CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CyclePayload() implements CustomPacketPayload {
        public static final Type<CyclePayload> TYPE = new Type<>(EnchantmentFusion.id("cycle_skill"));
        public static final CyclePayload INSTANCE = new CyclePayload();
        public static final StreamCodec<ByteBuf, CyclePayload> CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client reports the original miss branch; the server still validates charge, equipment and resources. */
    public record AirAttackPayload() implements CustomPacketPayload {
        public static final Type<AirAttackPayload> TYPE = new Type<>(EnchantmentFusion.id("air_attack"));
        public static final AirAttackPayload INSTANCE = new AirAttackPayload();
        public static final StreamCodec<ByteBuf, AirAttackPayload> CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private enum Skill {
        BREATH("skill.enchantment_fusion.breath_mining"),
        FROST_WAVE("skill.enchantment_fusion.frost_wave"),
        FROST_STEP("skill.enchantment_fusion.frost_step"),
        AMBER_SEAL("skill.enchantment_fusion.amber_seal"),
        SLICE_MODE("skill.enchantment_fusion.slice_mode"),
        LINE_BUILD("skill.enchantment_fusion.line_build"),
        SOUL_CRAWL("skill.enchantment_fusion.soul_crawl"),
        CHARGED_CROSSBOW("skill.enchantment_fusion.charged_crossbow"),
        DURABILITY_RESERVE("skill.enchantment_fusion.durability_reserve"),
        PATH_ECHO("skill.enchantment_fusion.path_echo"),
        MOMENTUM_SEAL("skill.enchantment_fusion.momentum_seal"),
        FIRE_COMPENSATION("skill.enchantment_fusion.fire_compensation"),
        FIRE_WAVE("skill.enchantment_fusion.fire_wave"),
        OPEN_SEALED_ORE("skill.enchantment_fusion.open_sealed_ore"),
        FORTUNE_BANK("skill.enchantment_fusion.fortune_bank"),
        WATER_PRESSURE("skill.enchantment_fusion.water_pressure");
        final String translation;
        Skill(String translation) { this.translation = translation; }
    }

    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> LAST_CYCLE = new HashMap<>();
    private static final Map<UUID, Long> LAST_AIR_ATTACK = new HashMap<>();
    private static final Map<UUID, Skill> SELECTED = new HashMap<>();
    private FusionSkillNetworking() {}

    private static List<Skill> equippedSkills(ServerPlayer player) {
        List<Skill> skills = new ArrayList<>(15);
        if (UnderwaterMiningSkills.hasEquippedSpend(player)) skills.add(Skill.BREATH);
        if (FrostFallSkills.find(player, true) != null) skills.add(Skill.FROST_WAVE);
        if (FrostFallSkills.find(player, false) != null) skills.add(Skill.FROST_STEP);
        if (AmberSealSkill.hasEquipped(player)) skills.add(Skill.AMBER_SEAL);
        if (PrecisionConstruction.hasSlice(player)) skills.add(Skill.SLICE_MODE);
        if (PrecisionConstruction.hasLine(player)) skills.add(Skill.LINE_BUILD);
        if (SoulStepSkills.hasEquippedCrawl(player)) skills.add(Skill.SOUL_CRAWL);
        if (RangedCadenceSkills.hasEquippedCrossbow(player)) skills.add(Skill.CHARGED_CROSSBOW);
        if (DurabilityReserveSkills.hasEquipped(player)) skills.add(Skill.DURABILITY_RESERVE);
        if (EchoPathSkills.hasEquipped(player)) skills.add(Skill.PATH_ECHO);
        if (MomentumSealSkills.hasEquipped(player)) skills.add(Skill.MOMENTUM_SEAL);
        if (FireTradeoffSkills.hasEquipped(player, true)) skills.add(Skill.FIRE_COMPENSATION);
        if (FireTradeoffSkills.hasEquipped(player, false)) skills.add(Skill.FIRE_WAVE);
        if (SealedOreSkills.hasOpenableSample(player)) skills.add(Skill.OPEN_SEALED_ORE);
        if (FortuneBankSkills.hasEquipped(player)) skills.add(Skill.FORTUNE_BANK);
        if (WaterPressureSkills.hasEquipped(player)) skills.add(Skill.WATER_PRESSURE);
        return skills;
    }

    private static boolean allowRequest(Map<UUID, Long> lastRequests, ServerPlayer player) {
        long tick = player.level().getGameTime();
        Long last = lastRequests.get(player.getUUID());
        if (last != null && tick >= last && tick - last < 5) return false;
        lastRequests.put(player.getUUID(), tick);
        return true;
    }

    private static void cycle(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        List<Skill> available = equippedSkills(player);
        if (available.isEmpty()) {
            SELECTED.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.no_active_skill"), true);
            return;
        }
        Skill previous = SELECTED.get(player.getUUID());
        int current = previous == null ? 0 : available.indexOf(previous);
        int next = current < 0 ? 0 : (current + 1) % available.size();
        Skill skill = available.get(next);
        SELECTED.put(player.getUUID(), skill);
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.selected_skill",
            Component.translatable(skill.translation), next + 1, available.size()), true);
    }

    private static void activate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        List<Skill> available = equippedSkills(player);
        if (available.isEmpty()) {
            SELECTED.remove(player.getUUID());
            return;
        }
        Skill chosen = SELECTED.get(player.getUUID());
        if (chosen == null || !available.contains(chosen)) {
            chosen = available.getFirst();
            SELECTED.put(player.getUUID(), chosen);
            if (available.size() > 1) player.sendSystemMessage(Component.translatable(
                "message.enchantment_fusion.selected_skill", Component.translatable(chosen.translation), 1, available.size()), true);
        }
        switch (chosen) {
            case BREATH -> UnderwaterMiningSkills.activate(player);
            case FROST_WAVE -> FrostFallSkills.releaseWave(player);
            case FROST_STEP -> FrostFallSkills.createAirStep(player);
            case AMBER_SEAL -> AmberSealSkill.activate(player);
            case SLICE_MODE -> PrecisionConstruction.cycleSlice(player);
            case LINE_BUILD -> PrecisionConstruction.activateLine(player);
            case SOUL_CRAWL -> SoulStepSkills.activateCrawl(player);
            case CHARGED_CROSSBOW -> RangedCadenceSkills.activateCrossbow(player);
            case DURABILITY_RESERVE -> DurabilityReserveSkills.activate(player);
            case PATH_ECHO -> EchoPathSkills.activate(player);
            case MOMENTUM_SEAL -> MomentumSealSkills.activate(player);
            case FIRE_COMPENSATION -> FireTradeoffSkills.toggle(player);
            case FIRE_WAVE -> FireTradeoffSkills.release(player);
            case OPEN_SEALED_ORE -> SealedOreSkills.openSample(player);
            case FORTUNE_BANK -> FortuneBankSkills.activate(player);
            case WATER_PRESSURE -> WaterPressureSkills.activate(player);
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(ActivatePayload.TYPE, ActivatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CyclePayload.TYPE, CyclePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AirAttackPayload.TYPE, AirAttackPayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            LAST_REQUEST.remove(id);
            LAST_CYCLE.remove(id);
            LAST_AIR_ATTACK.remove(id);
            SELECTED.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_REQUEST.clear();
            LAST_CYCLE.clear();
            LAST_AIR_ATTACK.clear();
            SELECTED.clear();
        });
        ServerPlayNetworking.registerGlobalReceiver(ActivatePayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (allowRequest(LAST_REQUEST, player)) activate(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(CyclePayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (allowRequest(LAST_CYCLE, player)) cycle(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(AirAttackPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (ctx.server().getPlayerList().getPlayer(player.getUUID()) != player) return;
                long tick = player.level().getGameTime();
                Long previous = LAST_AIR_ATTACK.put(player.getUUID(), tick);
                if (previous == null || previous != tick) SoulBladeSkills.onAirAttack(player);
            });
        });
    }
}
