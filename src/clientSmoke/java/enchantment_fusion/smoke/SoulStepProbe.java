package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.model.FusedPair;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Deterministic server fixtures in disposable space; real client input and multiplayer are separate. */
final class SoulStepProbe {
    private static final FusedPair BOOTS = FusedPair.clean("minecraft:soul_speed", 3, "minecraft:swift_sneak", 2);
    private static final FusedPair LEGS = BOOTS.flipped();
    private SoulStepProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin, boolean reverse) throws Exception {
        ServerLevel level = player.level();
        FusedPair pair = reverse ? LEGS : BOOTS;
        checkStation(level, pair, reverse);
        Vec3 position = player.position(), velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot();
        boolean sprint = player.isSprinting(), sneak = player.isShiftKeyDown(), ground = player.onGround();
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET), legs = player.getItemBySlot(EquipmentSlot.LEGS);
        Map<BlockPos, BlockState> prior = new LinkedHashMap<>();
        try {
            for (int x = -1; x <= 9; x++) for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++) {
                BlockPos p = origin.offset(x, y, z);
                require(level.hasChunkAt(p) && level.isInWorldBounds(p) && level.getBlockState(p).isAir(),
                    "Soul-step fixture needs loaded empty disposable space");
                prior.put(p, level.getBlockState(p));
            }
            for (int x = 0; x <= 8; x++) {
                level.setBlockAndUpdate(origin.offset(x, 0, 0), (x <= 3 ? Blocks.SOUL_SOIL : Blocks.DIRT).defaultBlockState());
            }
            for (int x = 6; x <= 8; x++) level.setBlockAndUpdate(origin.offset(x, 2, 0), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(8, 1, 0), Blocks.STONE.defaultBlockState()); // end wall
            player.setItemSlot(EquipmentSlot.FEET, reverse ? ItemStack.EMPTY : fused(level, Items.IRON_BOOTS.getDefaultInstance(), pair));
            player.setItemSlot(EquipmentSlot.LEGS, reverse ? fused(level, Items.IRON_LEGGINGS.getDefaultInstance(), pair) : ItemStack.EMPTY);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            player.setYRot(-90.0F); player.setXRot(0.0F);
            if (!reverse) {
                player.setShiftKeyDown(true); player.setSprinting(false);
                SoulStepSkills.tick(player); // fresh state with no collected or spent energy
                require(!SoulStepSkills.muffle(player, GameEvent.HIT_GROUND),
                    "Fresh, uncharged boots must not suppress movement vibration");
            }
            player.setShiftKeyDown(reverse); player.setSprinting(!reverse);
            SoulStepSkills.tick(player); // establish the physical start position
            for (int i = 1; i <= 6; i++) {
                player.setPos(origin.getX() + 0.5D + i * 0.05D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                player.setOnGround(true);
                SoulStepSkills.tick(player);
            }
            if (!reverse) {
                player.setSprinting(false); player.setShiftKeyDown(true);
                player.setPos(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                player.setOnGround(true); SoulStepSkills.tick(player); // large position change cannot earn energy
                player.setPos(origin.getX() + 4.55D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                player.setOnGround(true); SoulStepSkills.tick(player);
                require(player.getAttributeValue(Attributes.SNEAKING_SPEED) >= 0.59D,
                    "Soul-ground sprint must fund faster ordinary-ground sneaking");
                require(SoulStepSkills.muffle(player, GameEvent.HIT_GROUND) && !SoulStepSkills.muffle(player, GameEvent.ENTITY_DAMAGE),
                    "Only the player's movement vibrations, not unrelated action events, may be muffled");
                checkVibrationListener(level, player, origin);
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                SoulStepSkills.tick(player);
                require(!SoulStepSkills.muffle(player, GameEvent.HIT_GROUND), "Removing the boots must clear soul state");
            } else {
                require(!SoulStepSkills.activateCrawl(player), "An open area must not consume a prepared one-block crawl");
                player.setPos(origin.getX() + 5.4D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
                player.setOnGround(true);
                require(SoulStepSkills.activateCrawl(player) && SoulStepSkills.forceCrawling(player),
                    "A prepared soul form must activate toward a real one-block entrance");
                updatePose(player);
                require(player.getPose() == Pose.SWIMMING && player.getBoundingBox().getYsize() < 1.0D,
                    "Vanilla pose selection must use the smaller crawl collision box");
                player.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
                require(player.getX() > origin.getX() + 6.1D, "Crawling must enter the real one-block corridor");
                player.move(MoverType.SELF, new Vec3(3.0D, 0.0D, 0.0D));
                require(player.getX() <= origin.getX() + 7.71D, "The solid end wall must stop the crawl");
                require(SoulStepSkills.activateCrawl(player), "The active crawl can be voluntarily cancelled");
                updatePose(player);
                require(level.noCollision(player, player.getBoundingBox()), "Cancelling under a ceiling must not embed the player");
            }
        } finally {
            SoulStepSkills.onDisconnect(player);
            player.setItemSlot(EquipmentSlot.FEET, feet); player.setItemSlot(EquipmentSlot.LEGS, legs);
            for (var entry : prior.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            player.setPos(position); player.setDeltaMovement(velocity); player.setYRot(yaw); player.setXRot(pitch);
            player.setShiftKeyDown(sneak); player.setSprinting(sprint); player.setOnGround(ground);
            updatePose(player);
        }
    }
    private static void checkVibrationListener(ServerLevel level, ServerPlayer player, BlockPos origin) {
        int[] calls = {0};
        var user = new net.minecraft.world.level.gameevent.vibrations.VibrationSystem.User() {
            @Override public int getListenerRadius() { return 8; }
            @Override public net.minecraft.world.level.gameevent.PositionSource getPositionSource() {
                return new net.minecraft.world.level.gameevent.BlockPositionSource(origin);
            }
            @Override public boolean canReceiveVibration(ServerLevel world, BlockPos pos,
                    net.minecraft.core.Holder<GameEvent> event, GameEvent.Context context) { return false; }
            @Override public void onReceiveVibration(ServerLevel world, BlockPos pos,
                    net.minecraft.core.Holder<GameEvent> event, net.minecraft.world.entity.Entity source,
                    net.minecraft.world.entity.Entity owner, float distance) {}
            @Override public boolean isValidVibration(net.minecraft.core.Holder<GameEvent> event, GameEvent.Context context) {
                calls[0]++; return false;
            }
        };
        var system = new net.minecraft.world.level.gameevent.vibrations.VibrationSystem() {
            private final Data data = new Data();
            @Override public Data getVibrationData() { return data; }
            @Override public User getVibrationUser() { return user; }
        };
        var listener = new net.minecraft.world.level.gameevent.vibrations.VibrationSystem.Listener(system);
        listener.handleGameEvent(level, GameEvent.HIT_GROUND, GameEvent.Context.of(player), player.position());
        require(calls[0] == 0, "The real vibration listener must suppress the owner's muffled movement");
        listener.handleGameEvent(level, GameEvent.ENTITY_DAMAGE, GameEvent.Context.of(player), player.position());
        require(calls[0] == 1, "The listener must still delegate the owner's unrelated action events");
        listener.handleGameEvent(level, GameEvent.HIT_GROUND, new GameEvent.Context(null, null), player.position());
        require(calls[0] == 2, "The listener must still delegate movement events from other sources");
    }
    private static void updatePose(ServerPlayer player) throws Exception {
        var method = Player.class.getDeclaredMethod("updatePlayerPose");
        method.setAccessible(true);
        method.invoke(player);
    }
    private static ItemStack fused(ServerLevel level, ItemStack stack, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
    }
    private static void checkStation(ServerLevel level, FusedPair pair, boolean reverse) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = reg.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = reg.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : new boolean[]{false, true}) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int firstIndex = direct ? 0 : -1, secondIndex = direct ? 1 : -1;
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(reverse ? Items.IRON_LEGGINGS : Items.IRON_BOOTS));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET,
                firstIndex, secondIndex).executable(), "Both fusion paths must accept the direction's wearable carrier");
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(reverse ? Items.IRON_BOOTS : Items.IRON_LEGGINGS));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET,
                firstIndex, secondIndex).rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Both fusion paths must reject the other direction's carrier");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
