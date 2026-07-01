import re

with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "r") as f:
    code = f.read()

imports = """
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
"""

code = code.replace("import net.minecraft.world.item.ItemStack;", "import net.minecraft.world.item.ItemStack;\n" + imports)

apply_logic = """
    public static Holder<Enchantment> IdentifierToEnchantment(Identifier id, Level world) {
        return world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static Identifier EnchantmentToIdentifier(Holder<Enchantment> enchant) {
        return enchant.unwrapKey().get().location();
    }

    public int calculateXpCostToLevel(int X) {
        int A = Math.min(X, 16);
        int B = Math.min(X, 31);
        int C = X;
        int Am = 2;
        int Ac = 7;
        int Bm = 5;
        int Bc = -38;
        int Cm = 9;
        int Cc = -158;
    
        return (Am * ((A - 1) * A) / 2 + Ac * A)
             + (Bm * (((B - 1) * B) / 2 - ((A - 1) * A) / 2) + Bc * (B - A))
             + (Cm * (((C - 1) * C) / 2 - ((B - 1) * B) / 2) + Cc * (C - B));
    }

    public boolean applyEnchantementCondition(ApplyEnchantmentPayload payload) {
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), payload.enchantment_level());
        int xp_level_cost = getEnchantmentXpLevelCost(ewl);
        if (this.player.experienceLevel < xp_level_cost && !player.getAbilities().instabuild) return false;

        ItemStack cost = getEnchantmentItemCost(ewl);
        ItemStack item_cost_stack = this.get_cost_item();
        if (!player.getAbilities().instabuild) {
            if (cost.isEmpty() || !ItemStack.isSameItemSameComponents(item_cost_stack, cost) || item_cost_stack.getCount() < cost.getCount()) return false;
        }

        if (!player.getAbilities().instabuild) {
            this.player.giveExperienceLevels(-xp_level_cost);
            item_cost_stack.shrink(cost.getCount());
        }
        return true;
    }

    public void applyEnchantementFromBook(ApplyEnchantmentPayload payload) {
        ItemStack enchanted_book = this.get_cost_item();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book);
        boolean exists = false;
        for (var entry : enchantments.entrySet()) {
            if (EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id()) && entry.getIntValue() == payload.enchantment_level()) {
                exists = true;
                break;
            }
        }
        if (!exists) return;
        
        ItemStack enchantable_item = this.get_enchantable_item();
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = IdentifierToEnchantment(payload.enchantment_id(), this.world);
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(enchantable_item))) return;

        ItemEnchantments.Mutable new_enchanted_book_enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (!EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id())) {
                new_enchanted_book_enchants.set(entry.getKey(), entry.getIntValue());
            }
        }
        EnchantmentHelper.updateEnchantments(enchanted_book, m -> {
            var keys = new java.util.HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0); // clear
            for (var entry : new_enchanted_book_enchants.keySet()) m.set(entry, new_enchanted_book_enchants.getLevel(entry));
        });

        if (EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book).isEmpty()) {
            this.inventory.setItem(COST_SLOT, enchanted_book.transmuteCopy(Items.BOOK, 1));
        }
        
        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item));
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK))) {
            var keys = new java.util.HashSet<>(newEnchantmentsComponent.keySet());
            for (var key : keys) {
                if (!Enchantment.areCompatible(key, enchantment)) {
                    newEnchantmentsComponent.set(key, 0); // remove conflicting
                }
            }
        }
        newEnchantmentsComponent.set(enchantment, enchantment_level);
        
        if (enchantable_item.is(Items.BOOK)) {
            ItemStack stack = enchantable_item.transmuteCopy(Items.ENCHANTED_BOOK, 1);
            this.inventory.setItem(ENCHANTABLE_SLOT, stack);
            enchantable_item = this.inventory.getItem(ENCHANTABLE_SLOT);
        }
        
        EnchantmentHelper.updateEnchantments(enchantable_item, m -> {
            var keys = new java.util.HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });
        
        this.inventory.setChanged();
        this.player.awardStat(Stats.ENCHANT_ITEM);
        this.world.playSound(null, this.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, this.world.random.nextFloat() * 0.1F + 0.9F);
    }

    public void applyEnchantement(ApplyEnchantmentPayload payload) {
        if (this.get_cost_item().is(Items.ENCHANTED_BOOK)) {
            applyEnchantementFromBook(payload);
            return;
        }
        
        boolean exist = false;
        for (EnchantmentWithLevel e : this.unlocked_enchantements) {
            if (e.enchantment_id().equals(payload.enchantment_id()) && e.enchantment_level() == payload.enchantment_level()) {
                exist = true;
                break;
            }
        }
        if (!exist) return;
        
        ItemStack enchantable_item = this.get_enchantable_item();
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = IdentifierToEnchantment(payload.enchantment_id(), this.world);
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(enchantable_item))) return;

        if (!applyEnchantementCondition(payload)) return;

        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item));
        var keys = new java.util.HashSet<>(newEnchantmentsComponent.keySet());
        for (var key : keys) {
            if (!Enchantment.areCompatible(key, enchantment)) {
                newEnchantmentsComponent.set(key, 0); // remove conflicting
            }
        }
        newEnchantmentsComponent.set(enchantment, enchantment_level);
        
        if (enchantable_item.is(Items.BOOK)) {
            ItemStack stack = enchantable_item.transmuteCopy(Items.ENCHANTED_BOOK, 1);
            this.inventory.setItem(ENCHANTABLE_SLOT, stack);
            enchantable_item = this.inventory.getItem(ENCHANTABLE_SLOT);
        }
        
        EnchantmentHelper.updateEnchantments(enchantable_item, m -> {
            var mKeys = new java.util.HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });
        
        this.inventory.setChanged();
        this.player.awardStat(Stats.ENCHANT_ITEM);
        this.world.playSound(null, this.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, this.world.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inventory);
    }

    @Override
"""

code = code.replace("""    public void applyEnchantement(ApplyEnchantmentPayload payload) {
        // TODO P2/P3: restore the full enchantment application rules after Mojang-name source migration.
    }

    @Override""", apply_logic)

with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "w") as f:
    f.write(code)
