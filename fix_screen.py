import re
with open("src/client/java/chiseled_enchanting_table/gui/ChiseledEnchantingTableScreen.java", "r") as f:
    code = f.read()

code = code.replace("ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 340.0f/512.0f, 0.0f, 166.0f/256.0f);", "ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);")

with open("src/client/java/chiseled_enchanting_table/gui/ChiseledEnchantingTableScreen.java", "w") as f:
    f.write(code)
