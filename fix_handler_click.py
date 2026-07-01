import re
with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "r") as f:
    code = f.read()

code = code.replace("chiseled_enchanting_table.utils.InventoryUtils.insertItem", "chiseled_enchanting_table.utils.InventoryUtils.insertItem")

with open("src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "w") as f:
    f.write(code)
