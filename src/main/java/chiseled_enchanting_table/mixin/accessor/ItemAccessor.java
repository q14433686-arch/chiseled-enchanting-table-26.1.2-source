package chiseled_enchanting_table.mixin.accessor;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Item.class)
public interface ItemAccessor {
    @Mutable
    @Accessor("components")
    void setComponents(ComponentMap components);

    @Mutable
    @Accessor("components")
    ComponentMap getComponents();
}