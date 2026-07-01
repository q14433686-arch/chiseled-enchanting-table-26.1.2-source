import re
with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "r") as f:
    code = f.read()

method = """
    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (actionType == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE) {
            if (slotIndex < 0 || slotIndex >= this.slots.size()) return;
            ItemStack clickedStack = this.getSlot(slotIndex).getItem();
            if (clickedStack.isEmpty()) return;
            if ((slotIndex == COST_SLOT) || (slotIndex == ENCHANTABLE_SLOT) ) {
                chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, clickedStack, java.util.Optional.empty());
                clickedStack.setCount(0);
                this.inventory.setChanged();
                this.player.getInventory().setChanged();
                return;
            }
            ItemStack clickedStackNoEnchant = clickedStack.copy();
            EnchantmentHelper.updateEnchantments(clickedStackNoEnchant, m -> {
                var keys = new java.util.HashSet<>(m.keySet());
                for(var k: keys) m.set(k, 0);
            });
            boolean isBook = clickedStackNoEnchant.is(Items.BOOK) || clickedStackNoEnchant.is(Items.ENCHANTED_BOOK);
            
            if (clickedStackNoEnchant.isEnchantable() || (isBook && !this.getSlot(ENCHANTABLE_SLOT).hasItem())) {
                ItemStack tempStack = clickedStack.copy();
                this.getSlot(slotIndex).set(this.getSlot(ENCHANTABLE_SLOT).getItem());
                this.getSlot(ENCHANTABLE_SLOT).set(tempStack);

                if (this.getSlot(ENCHANTABLE_SLOT).getItem().getCount() > 1) {
                    ItemStack overflow = this.getSlot(ENCHANTABLE_SLOT).getItem().copy();
                    overflow.setCount(overflow.getCount() - 1);
                    this.getSlot(ENCHANTABLE_SLOT).getItem().setCount(1);
                    if (!this.getSlot(slotIndex).hasItem()) {
                        this.getSlot(slotIndex).set(overflow);
                    } else {
                        if (!this.moveItemStackTo(overflow, 2, 38, true)) {
                            this.player.drop(overflow, false);
                        }
                    }
                }
            } else {
                if (!this.getSlot(COST_SLOT).hasItem()) {
                    this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT+1, false);
                } else {
                    ItemStack costSlotStack = this.getSlot(COST_SLOT).getItem();
                    if (ItemStack.isSameItemSameComponents(clickedStack, costSlotStack)) {
                        this.moveItemStackTo(clickedStack, COST_SLOT, COST_SLOT+1, false);
                    } else {
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(this.player, costSlotStack, java.util.Optional.of(slotIndex));
                        costSlotStack.setCount(0);
                    }
                }
            }
            this.inventory.setChanged();
            this.player.getInventory().setChanged();
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }
"""

code = code.replace("    public ItemStack quickMoveStack(Player player, int index) {", method + "\n    public ItemStack quickMoveStack(Player player, int index) {")
with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "w") as f:
    f.write(code)
