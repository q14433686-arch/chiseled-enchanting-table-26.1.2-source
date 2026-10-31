package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

/** Prepared integration probes only: no real client input, natural explosions or modded protection. */
final class BlastDefenseProbe {
    private static final FusedPair COUNTER = FusedPair.clean("minecraft:blast_protection", 1, "minecraft:protection", 1);
    private static final FusedPair GUARD = FusedPair.clean("minecraft:protection", 2, "minecraft:blast_protection", 2);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private BlastDefenseProbe() {}

    static void exerciseCounter(ServerPlayer player, BlockPos origin) {
        checkCarriers(player.level(), COUNTER);
        var level = player.level();
        var saved = new State(player);
        Zombie target = new Zombie(level);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        try {
            saved.prepare();
            require(level.hasChunkAt(origin) && level.getBlockState(origin).isAir() && level.getBlockState(origin.east(2)).isAir(),
                "Counter fixture must use a loaded, empty disposable area");
            player.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            ItemStack chest = fused(level, new ItemStack(Items.IRON_CHESTPLATE), COUNTER);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            saved.installArmor(chest, helmet);
            DamageSource explosion = level.damageSources().explosion(null, null);
            float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float helmetArmor = armorValue(helmet, EquipmentSlot.HEAD);
            float expectedArmorDamage = CombatRules.getDamageAfterAbsorb(player, 10.0F, explosion, helmetArmor, toughness);
            float expected = CombatRules.getDamageAfterMagicAbsorb(expectedArmorDamage, 2.0F);
            player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
            float before = player.getHealth();
            require(player.hurtServer(level, explosion, 10.0F), "Explosion fixture must deal accepted damage");
            float actual = before - player.getHealth();
            require(Math.abs(actual - expected) < 0.01F,
                "Explosion must exclude only the fused chestplate's white armor and keep helmet armor and blast protection");
            require(BlastDefenseSkills.explosionResistance(player, 0.0D) >= 0.15D,
                "Blast-primary armor must retain some blast push resistance without duplicating a larger native value");
            require(Math.abs(BlastDefenseSkills.explosionResistance(player, 0.8D) - 0.8D) < 0.0001D,
                "An already larger native resistance must not be added again");

            BlastDefenseSkills.onDisconnect(player.getUUID());
            player.setHealth(player.getMaxHealth()); player.setAbsorptionAmount(0.0F);
            target.setNoAi(true); target.setPos(player.getX() + 2.0D, player.getY(), player.getZ());
            villager.setNoAi(true); villager.setPos(player.getX() - 1.0D, player.getY(), player.getZ());
            require(level.addFreshEntity(target) && level.addFreshEntity(villager), "Counter fixtures must enter world");
            float targetHealth = target.getHealth(), villagerHealth = villager.getHealth();
            player.invulnerableTime = 0;
            require(player.hurtServer(level, level.damageSources().magic(), 6.0F), "First actual hit must be accepted");
            require(target.getHealth() == targetHealth, "Under five hearts must not retaliate");
            player.invulnerableTime = 0;
            require(player.hurtServer(level, level.damageSources().magic(), 6.0F), "Second actual hit must be accepted");
            require(target.getHealth() < targetHealth, "Exceeding five hearts in one window must counter an eligible enemy");
            require(villager.getHealth() == villagerHealth, "The counter must not damage a nearby villager");
        } finally {
            target.discard(); villager.discard();
            BlastDefenseSkills.onDisconnect(player.getUUID());
            saved.restore();
        }
    }

    static void exerciseGuard(ServerPlayer player) {
        checkCarriers(player.level(), GUARD);
        var level = player.level();
        var saved = new State(player);
        try {
            saved.prepare();
            ItemStack chest = fused(level, new ItemStack(Items.IRON_CHESTPLATE), GUARD);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            player.setItemSlot(EquipmentSlot.CHEST, chest); player.setItemSlot(EquipmentSlot.HEAD, helmet);
            saved.installArmor(chest, helmet);
            DamageSource explosion = level.damageSources().explosion(null, null);
            float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float armoured = CombatRules.getDamageAfterAbsorb(player, 8.0F, explosion, player.getArmorValue(), toughness);
            // Protection II = 2, Blast Protection II = 4, plus 50% of the actual main contribution = 1.
            float expected = CombatRules.getDamageAfterMagicAbsorb(armoured, 7.0F);
            player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
            float before = player.getHealth();
            require(player.hurtServer(level, explosion, 8.0F), "Protection fixture must deal accepted explosion damage");
            require(Math.abs(before - player.getHealth() - expected) < 0.01F,
                "The reverse direction must keep both armor pieces and merge the main, secondary and converted contributions once");

            DamageSource ordinary = level.damageSources().generic();
            float normalArmoured = CombatRules.getDamageAfterAbsorb(player, 8.0F, ordinary, player.getArmorValue(), toughness);
            float normalExpected = CombatRules.getDamageAfterMagicAbsorb(normalArmoured, 2.0F);
            player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
            before = player.getHealth();
            require(player.hurtServer(level, ordinary, 8.0F), "Ordinary fixture damage must be accepted");
            require(Math.abs(before - player.getHealth() - normalExpected) < 0.01F,
                "Ordinary damage must retain only native Protection, not the explosion-only addition");
        } finally { saved.restore(); }
    }

    private static final class State {
        final ServerPlayer player; final List<ItemStack> slots = new ArrayList<>();
        final List<MobEffectInstance> effects;
        final GameType mode; final float health, absorption; final int invulnerability;
        final Vec3 position;
        final AttributeInstance armor, armorBackup;
        State(ServerPlayer player) {
            this.player = player;
            for (EquipmentSlot slot : SLOTS) slots.add(player.getItemBySlot(slot));
            effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
            mode = player.gameMode.getGameModeForPlayer(); health = player.getHealth(); absorption = player.getAbsorptionAmount();
            invulnerability = player.invulnerableTime; position = player.position();
            armor = player.getAttribute(Attributes.ARMOR);
            armorBackup = new AttributeInstance(Attributes.ARMOR, ignored -> {});
            armorBackup.replaceFrom(armor);
        }
        void prepare() {
            player.setGameMode(GameType.SURVIVAL); player.removeAllEffects();
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setAbsorptionAmount(0.0F);
        }
        void installArmor(ItemStack chest, ItemStack helmet) {
            armor.removeModifiers(); armor.setBaseValue(0.0D);
            install(chest, EquipmentSlot.CHEST); install(helmet, EquipmentSlot.HEAD);
        }
        void install(ItemStack item, EquipmentSlot slot) {
            item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ARMOR)) armor.addTransientModifier(modifier);
            });
        }
        void restore() {
            for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], slots.get(i));
            armor.replaceFrom(armorBackup);
            player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(effect);
            player.setGameMode(mode); player.setHealth(health); player.setAbsorptionAmount(absorption); player.invulnerableTime = invulnerability;
            player.teleportTo(position.x, position.y, position.z);
        }
    }
    private static float armorValue(ItemStack stack, EquipmentSlot slot) {
        double[] amount = {0.0D};
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) amount[0] += modifier.amount();
        });
        return (float) Math.floor(amount[0]);
    }
    private static ItemStack fused(ServerLevel level, ItemStack stack, FusedPair pair) {
        FusionStationOperations.applyFusedPairToGear(stack, pair, level.registryAccess());
        return stack;
    }
    private static void checkCarriers(ServerLevel level, FusedPair pair) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var first = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.primaryId())));
        var second = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pair.secondaryId())));
        for (boolean direct : List.of(false, true)) {
            SimpleContainer inv = new SimpleContainer(4);
            if (direct) {
                inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(first, pair.primaryLevel()));
                inv.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(second, pair.secondaryLevel()));
            } else inv.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            int a = direct ? 0 : -1, b = direct ? 1 : -1;
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.SHIELD));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).rejectReason()
                == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Both paths must reject a shield for this specific pair");
            inv.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.IRON_CHESTPLATE));
            require(FusionStationOperations.inspect(inv, level.registryAccess(), FusionStationOperations.TAB_SOCKET, a, b).executable(),
                "Both paths must accept appropriate armor");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
