import os
import re

with open("/home/user/upstream_repo/src/main/java/chiseled_enchanting_table/chiseledEnchantingTable/ChiseledEnchantingTableScreenHandler.java", "r") as f:
    code = f.read()

# Replace mappings
code = code.replace("net.minecraft.entity.player.PlayerEntity", "net.minecraft.world.entity.player.Player")
code = code.replace("net.minecraft.entity.player.PlayerInventory", "net.minecraft.world.entity.player.Inventory")
code = code.replace("net.minecraft.inventory.Inventory", "net.minecraft.world.Container")
code = code.replace("net.minecraft.inventory.SimpleInventory", "net.minecraft.world.SimpleContainer")
code = code.replace("net.minecraft.item.ItemStack", "net.minecraft.world.item.ItemStack")
code = code.replace("net.minecraft.item.Items", "net.minecraft.world.item.Items")
code = code.replace("net.minecraft.screen.ScreenHandler", "net.minecraft.world.inventory.AbstractContainerMenu")
code = code.replace("net.minecraft.screen.slot.Slot", "net.minecraft.world.inventory.Slot")
code = code.replace("net.minecraft.screen.slot.SlotActionType", "net.minecraft.world.inventory.ContainerInput")
code = code.replace("net.minecraft.stat.Stats", "net.minecraft.stats.Stats")
code = code.replace("net.minecraft.util.Identifier", "net.minecraft.resources.Identifier")
code = code.replace("net.minecraft.world.World", "net.minecraft.world.level.Level")
code = code.replace("net.minecraft.network.RegistryByteBuf", "net.minecraft.network.RegistryFriendlyByteBuf")
code = code.replace("net.minecraft.network.codec.PacketCodec", "net.minecraft.network.codec.StreamCodec")
code = code.replace("net.minecraft.network.packet.CustomPayload", "net.minecraft.network.protocol.common.custom.CustomPacketPayload")
code = code.replace("net.minecraft.component.DataComponentTypes", "net.minecraft.core.component.DataComponents")
code = code.replace("net.minecraft.component.type.ItemEnchantmentsComponent", "net.minecraft.world.item.enchantment.ItemEnchantments")
code = code.replace("net.minecraft.enchantment.Enchantment", "net.minecraft.world.item.enchantment.Enchantment")
code = code.replace("net.minecraft.enchantment.EnchantmentHelper", "net.minecraft.world.item.enchantment.EnchantmentHelper")

# Replace method calls and specific structures
code = code.replace("CustomPayload.Id", "CustomPacketPayload.Type")
code = code.replace("getId()", "type()")
code = code.replace("codecOf", "codec")
code = code.replace("PlayerEntity", "Player")
code = code.replace("PlayerInventory", "Inventory")

with open("adapted_handler.java", "w") as f:
    f.write(code)
