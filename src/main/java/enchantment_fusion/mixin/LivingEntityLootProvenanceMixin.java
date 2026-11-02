package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.EntityLootProvenance;
import enchantment_fusion.mechanics.signatures.GrapnelLooting;
import enchantment_fusion.mechanics.signatures.EmberHarvestCapture;
import enchantment_fusion.mechanics.signatures.RangedMendingCapture;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tie the real damage, death-loot call and the original loot consumer to one nested-safe action. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLootProvenanceMixin {
    @WrapMethod(method = "hurtServer")
    private boolean ef$observeLootDamage(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        GrapnelLooting.Frame frame = GrapnelLooting.beginDamage((LivingEntity) (Object) this, level, source);
        EmberHarvestCapture.Frame harvest = null; boolean accepted = false;
        try {
            harvest = EmberHarvestCapture.beginDamage((LivingEntity) (Object) this, level, source);
            accepted = original.call(level, source, damage); return accepted;
        } finally {
            try { EmberHarvestCapture.endDamage(harvest, accepted); }
            finally { GrapnelLooting.endDamage(frame, accepted); }
        }
    }
    @WrapMethod(method = "dropAllDeathLoot")
    private void ef$observeDeathLoot(ServerLevel level, DamageSource source, Operation<Void> original) {
        GrapnelLooting.Frame frame = GrapnelLooting.beginDeath((LivingEntity) (Object) this, level, source);
        boolean mending = false; EmberHarvestCapture.Frame harvest = null;
        try {
            mending = RangedMendingCapture.beginDeath((LivingEntity) (Object) this, level, source);
            harvest = EmberHarvestCapture.beginDeath((LivingEntity) (Object) this, level, source);
            original.call(level, source);
        } finally {
            try { EmberHarvestCapture.endDeath(harvest); }
            finally {
                try { RangedMendingCapture.endDeath(mending); }
                finally { GrapnelLooting.endDeath(frame); }
            }
        }
    }
    @WrapOperation(method = "dropExperience", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void ef$observeDeathExperience(ServerLevel level, Vec3 position, int amount, Operation<Void> original) {
        boolean entered = RangedMendingCapture.beginAward((LivingEntity) (Object) this, level, position, amount);
        boolean completed = false;
        try { original.call(level, position, amount); completed = true; }
        finally { RangedMendingCapture.endAward(entered, completed); }
    }
    @WrapOperation(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"))
    private void ef$observeLootOutput(LootTable table, LootParams params, long seed, Consumer<ItemStack> consumer,
                                      Operation<Void> original, ServerLevel level, DamageSource source, boolean killedByPlayer,
                                      ResourceKey<LootTable> key, Consumer<ItemStack> suppliedConsumer) {
        EntityLootProvenance.Scope scope = GrapnelLooting.beginLoot((LivingEntity) (Object) this, level, source);
        boolean harvest = false;
        if (scope == null) {
            scope = EmberHarvestCapture.beginLoot((LivingEntity) (Object) this, level, source);
            harvest = scope != null;
        }
        if (scope == null) { original.call(table, params, seed, consumer); return; }
        EntityLootProvenance.Scope captured = scope; boolean ordinaryHarvest = harvest, completed = false;
        try {
            original.call(table, params, seed, (Consumer<ItemStack>) stack -> {
                if (ordinaryHarvest && EmberHarvestCapture.ordinary(stack)) EntityLootProvenance.recordFinalOutput(captured, stack);
                EntityLootProvenance.consume(captured, stack, consumer);
            });
            completed = true;
        } finally {
            if (ordinaryHarvest) EmberHarvestCapture.endLoot(captured, completed);
            else GrapnelLooting.endLoot(captured, completed);
        }
    }
}
