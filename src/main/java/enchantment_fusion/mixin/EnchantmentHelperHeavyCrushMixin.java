package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.mechanics.signatures.MeleeGuardSkills;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelVolley;
import enchantment_fusion.mechanics.signatures.GrapnelProjectilePrimary;
import enchantment_fusion.mechanics.signatures.GrapnelDefense;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the same native-effect ordering for both orientations of Density/Breach. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperHeavyCrushMixin {
    @WrapMethod(method = "modifyDamage")
    private static float ef$soulThreadDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                              float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        float themed = MeleeAttackThemes.nonNativeDamage(level, weapon, victim, source,
            SegmentThreadSkills.modifyDamage(level, weapon, victim, source,
                SoulBladeSkills.modifyDamage(level, weapon, victim, source,
                    SoulThreadSkills.nonNativeDamage(level, weapon, victim, source, vanilla))));
        float knockbackPrimary = MeleeImpulseSkills.nonNativePrimaryDamage(level, weapon, victim, source, themed);
        float wear = MeleeWearSkills.modifyDamage(level, weapon, victim, source, knockbackPrimary);
        return GrapnelSkills.nonNativeProjectileDamage(level, weapon, victim, source, wear);
    }

    @WrapMethod(method = "getPiercingCount")
    private static int ef$grapnelPiercingCount(ServerLevel level, ItemStack weapon, ItemStack ammo, Operation<Integer> original) {
        return GrapnelProjectilePrimary.piercing(level, weapon, ammo, original.call(level, weapon, ammo));
    }

    @WrapMethod(method = "processProjectileCount")
    private static int ef$grapnelProjectileCount(ServerLevel level, ItemStack weapon, Entity shooter, int value,
                                                  Operation<Integer> original) {
        return GrapnelVolley.count(level, weapon, shooter, original.call(level, weapon, shooter, value));
    }

    @WrapMethod(method = "processProjectileSpread")
    private static float ef$grapnelProjectileSpread(ServerLevel level, ItemStack weapon, Entity shooter, float value,
                                                    Operation<Float> original) {
        return GrapnelVolley.spread(level, weapon, shooter, original.call(level, weapon, shooter, value));
    }

    @WrapMethod(method = "doPostAttackEffectsWithItemSourceOnBreak")
    private static void ef$guardVictimPostEffects(ServerLevel level, Entity victim, DamageSource source,
                                                  ItemStack weapon, Consumer<Item> attackerlessOnBreak,
                                                  Operation<Void> original) {
        original.call(level, victim, source, weapon, attackerlessOnBreak);
        MeleeGuardSkills.replayVictimPostAttack(level, victim, source);
        GrapnelDefense.replayVictimPostAttack(level, victim, source);
    }

    @WrapMethod(method = "modifyArmorEffectiveness")
    private static float ef$heavyArmor(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        return HeavyCrushSkill.armorEffect(level, weapon, victim, source, vanilla);
    }

    @WrapMethod(method = "modifyFallBasedDamage")
    private static float ef$heavySmash(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        return HeavyCrushSkill.fallEffect(level, weapon, victim, source, vanilla);
    }
}
