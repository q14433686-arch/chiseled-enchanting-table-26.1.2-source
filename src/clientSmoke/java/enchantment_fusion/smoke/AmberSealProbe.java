package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Prepared integrated-server fixture; it is not a mouse/key, weather or multiplayer playtest. */
final class AmberSealProbe {
    private static final FusedPair PAIR = FusedPair.clean("minecraft:silk_touch", 1, "minecraft:fire_aspect", 2);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private AmberSealProbe() {}

    static void exercise(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.level();
        exerciseCarriers(level);
        Vec3 oldPosition = player.position();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        boolean oldGround = player.onGround();
        GameType oldMode = player.gameMode.getGameModeForPlayer();
        List<ItemStack> saved = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) saved.add(player.getItemBySlot(slot));
        Zombie target = new Zombie(level), splash = new Zombie(level);
        Set<UUID> denied = new HashSet<>();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !denied.contains(entity.getUUID()));
        BlockPos wall = origin.offset(1, 1, 0);
        BlockState oldWall = level.getBlockState(wall);
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            player.setYRot(-90.0F);
            player.setXRot(0.0F);
            player.setOnGround(false); // Avoid sweep damage to the splash fixture.
            target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            target.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
            splash.setNoAi(true);
            splash.setPos(player.getX() + 3.0D, player.getY(), player.getZ() + 1.1D);
            level.addFreshEntity(target);
            level.addFreshEntity(splash);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            FusionStationOperations.applyFusedPairToGear(sword, PAIR, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            AmberSealSkill.clear();

            // The real Player.attack damage call supplies the secondary fire setup.
            target.clearFire();
            target.invulnerableTime = 0;
            player.attack(target);
            require(target.isOnFire(), "A confirmed amber weapon hit must provide fire for the active skill");
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            require(!AmberSealSkill.activate(player), "A solid wall must block server-side target acquisition");
            level.setBlockAndUpdate(wall, oldWall);
            double initialSpeed = target.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            require(AmberSealSkill.activate(player), "A burning target in view should be sealable");
            require(target.getRemainingFireTicks() == 0 && target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < initialSpeed,
                "Sealing captures the existing fire timer and applies transient slowness");
            TagValueOutput saving = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            target.saveWithoutId(saving);
            var savedTarget = saving.buildResult();
            require(savedTarget.getShortOr("Fire", (short) 0) > 0 && target.getRemainingFireTicks() == 0,
                "Serializing an active seal must save a fire fallback without changing the live target");
            Zombie restoredCopy = new Zombie(level); // Codec round-trip only, not a disk/restart test.
            restoredCopy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedTarget));
            require(restoredCopy.getRemainingFireTicks() > 0,
                "Reloading the serialized target must restore the unspent fire fallback");
            target.setRemainingFireTicks(300);
            TagValueOutput laterFire = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            target.saveWithoutId(laterFire);
            require(laterFire.buildResult().getShortOr("Fire", (short) 0) >= 300,
                "The save fallback must never shorten a newer independent fire within the vanilla short range");
            target.clearFire();

            denied.add(target.getUUID());
            target.invulnerableTime = 0;
            player.attack(target);
            denied.clear();
            AmberSealSkill.tickServer(level.getServer());
            require(target.getRemainingFireTicks() == 0 && target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < initialSpeed,
                "A cancelled attack must not consume or ignite a seal");
            target.invulnerableTime = 0;
            float health = target.getHealth();
            player.attack(target);
            AmberSealSkill.tickServer(level.getServer());
            require(health - target.getHealth() > 2.0F, "The accepted attack should cause one extra heat-crystal hit");
            require(target.isOnFire() && Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - initialSpeed) < 1e-8,
                "The seal must clear its own slow and leave the new attack's fire intact");
            health = target.getHealth();
            AmberSealSkill.tickServer(level.getServer());
            require(target.getHealth() == health, "A consumed seal must not release a second shock");

            AmberSealSkill.clear();
            target.setRemainingFireTicks(150);
            require(AmberSealSkill.activate(player), "A second seal should start after fixture cleanup");
            player.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
            AmberSealSkill.tickServer(level.getServer());
            require(target.getRemainingFireTicks() >= 150 && Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - initialSpeed) < 1e-8,
                "Swapping to an identical stack cancels and returns unspent fire");

            AmberSealSkill.clear();
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            target.setRemainingFireTicks(150);
            require(AmberSealSkill.activate(player), "Manual cancellation fixture must seal first");
            require(AmberSealSkill.activate(player), "Using the skill again should cancel the current seal");
            require(target.getRemainingFireTicks() >= 150 && Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - initialSpeed) < 1e-8,
                "Manual cancellation must return remaining fire without keeping the slowdown");

            AmberSealSkill.clear();
            ItemStack spear = new ItemStack(Items.DIAMOND_SPEAR);
            FusionStationOperations.applyFusedPairToGear(spear, PAIR, level.registryAccess());
            player.setItemSlot(EquipmentSlot.MAINHAND, spear);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SPEAR));
            target.setHealth(target.getMaxHealth());
            target.setRemainingFireTicks(150);
            target.invulnerableTime = 0;
            require(AmberSealSkill.activate(player), "Main-hand spear should seal a burning target");
            player.stabAttack(EquipmentSlot.OFFHAND, target, 2.0F, true, false, false);
            AmberSealSkill.tickServer(level.getServer());
            require(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < initialSpeed,
                "A different offhand spear must not release the main-hand seal");
            target.invulnerableTime = 0;
            health = target.getHealth();
            player.stabAttack(EquipmentSlot.MAINHAND, target, 2.0F, true, false, false);
            AmberSealSkill.tickServer(level.getServer());
            require(health - target.getHealth() > 2.0F
                && Math.abs(target.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - initialSpeed) < 1e-8,
                "The actual main-hand stab must release the seal exactly once");

            AmberSealSkill.clear();
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            target.setHealth(1.0F);
            target.setRemainingFireTicks(150);
            target.invulnerableTime = 0;
            splash.setHealth(splash.getMaxHealth());
            splash.invulnerableTime = 0;
            require(AmberSealSkill.activate(player), "Lethal-hit fixture must start with a seal");
            player.attack(target);
            require(!target.isAlive(), "The fixture's direct attack must actually kill the target");
            health = splash.getHealth();
            AmberSealSkill.tickServer(level.getServer());
            require(splash.getHealth() < health, "An AFTER_DEATH-confirmed lethal hit must still release one splash");
        } finally {
            denied.clear();
            AmberSealSkill.clear();
            target.discard();
            splash.discard();
            level.setBlockAndUpdate(wall, oldWall);
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], saved.get(i));
            player.setGameMode(oldMode);
            player.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
            player.setOnGround(oldGround);
        }
    }

    private static void exerciseCarriers(ServerLevel level) {
        Holder<Enchantment> silk = enchant(level, "silk_touch"), fire = enchant(level, "fire_aspect");
        for (ItemStack weapon : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_SPEAR), new ItemStack(Items.MACE))) {
            require(DirectionalHostRules.validateNewOrChanged(weapon, PAIR) == RejectReason.NONE,
                "All four selected weapon roles must accept the reversed pair");
            require(AmberSealSkill.applies(PAIR, weapon, AmberSealSkill.SIGNATURE), "The selected signature must apply");
            require(!AmberSealSkill.applies(PAIR, weapon, "other_mapping"), "Datapack remapping must not be seized");
        }
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        FusionStationOperations.applyFusedPairToGear(axe, PAIR, level.registryAccess());
        require(axe.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(silk) == 1,
            "A natively supported axe must retain the Silk Touch primary");
        ItemStack shield = new ItemStack(Items.SHIELD);
        var socket = new SimpleContainer(4);
        socket.setItem(FusionStationOperations.SLOT_TARGET, shield.copy());
        socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(PAIR));
        require(FusionStationOperations.inspect(socket, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1)
            .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Fused-book socketing must reject shields");
        socket.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_SWORD));
        require(FusionStationOperations.inspect(socket, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1)
            .executable(), "Fused-book socketing must accept swords");
        var direct = new SimpleContainer(4);
        direct.setItem(FusionStationOperations.SLOT_TARGET, shield.copy());
        direct.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(silk, 1));
        direct.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(fire, 2));
        require(FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1)
            .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Direct fusion must reject shields");
        direct.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_SWORD));
        require(FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1)
            .executable(), "Direct fusion must accept swords");
        shield.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(PAIR));
        require(!AmberSealSkill.applies(PAIR, shield, AmberSealSkill.SIGNATURE)
            && shield.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(PAIR),
            "An old unsupported carrier must retain its existing fusion data and legacy path");
    }

    private static Holder<Enchantment> enchant(ServerLevel level, String path) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
