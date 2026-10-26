package chiseled_enchanting_table.render;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.cosmetic.RainbowCloth;
import chiseled_enchanting_table.registry.BlockRegistry;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBakedItemModel;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedItemModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;

/** Client-only recognition and animated item tint. Never writes names, NBT, dye or lore to stacks. */
public final class RainbowTableItemVisuals {
    private RainbowTableItemVisuals() {}
    public static boolean isRainbow(ItemStack stack) {
        if(!stack.is(BlockRegistry.CHISELED_ENCHANTING_TABLE.asItem())) return false;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data!=null && data.copyTag().getBooleanOr(RainbowCloth.ITEM_FLAG,false);
    }
    public static void decorateTooltip(ItemStack stack,List<Component> lines) {
        if(!isRainbow(stack)) return;
        if(!lines.isEmpty()) lines.set(0,lines.getFirst().copy().append(Component.literal(" ")).append(
            Component.translatable("item.chiseled_enchanting_table.rainbow_badge").withStyle(ChatFormatting.LIGHT_PURPLE)));
        int insert=Math.min(1,lines.size());
        lines.add(insert,Component.translatable("item.chiseled_enchanting_table.rainbow_cloth").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(insert+1,Component.translatable("item.chiseled_enchanting_table.rainbow_dye_hint").withStyle(ChatFormatting.GRAY));
    }
    public static int currentColor(ClientLevel level) {
        var client=Minecraft.getInstance();
        if(level==null) level=client.level;
        if(level!=null) return ChiseledEnchantingTableBlockEntityRenderer.clothColor(level.getGameTime(),
            client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        // World-less previews still animate; avoid float precision loss after long uptime.
        long millis=System.nanoTime()/1_000_000L;
        return ChiseledEnchantingTableBlockEntityRenderer.clothColor(Math.floorDiv(millis,50),Math.floorMod(millis,50)/50f);
    }
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack,context,flag,lines)->decorateTooltip(stack,lines));
        ModelLoadingPlugin.register(plugin->plugin.modifyItemModelBeforeBake().register((model,context)->{
            if(!context.itemId().equals(ChiseledEnchantingTable.identifier("chiseled_enchanting_table"))) return model;
            if(!(model instanceof CuboidItemModelWrapper.Unbaked source) || source.tints().isEmpty() || !(source.tints().getFirst() instanceof Dye)) {
                ChiseledEnchantingTable.LOGGER.warn("Rainbow table item animation skipped: resource pack supplied a non-cuboid model or no primary dye tint");
                return model;
            }
            return new AnimatedUnbaked(source);
        }));
    }
    /** Ephemeral bake-time tint, not a new serialized item-model codec or component. */
    public record RainbowTint(ItemTintSource fallback) implements ItemTintSource {
        @Override public int calculate(ItemStack stack,ClientLevel level,LivingEntity owner) {
            return isRainbow(stack) ? currentColor(level) : fallback.calculate(stack,level,owner);
        }
        @Override public MapCodec<? extends ItemTintSource> type() { return MapCodec.unit(this); }
    }
    private static final class AnimatedUnbaked extends WrapperUnbakedItemModel {
        private final CuboidItemModelWrapper.Unbaked source;
        AnimatedUnbaked(CuboidItemModelWrapper.Unbaked source) { super(source);this.source=source; }
        @Override public ItemModel bake(ItemModel.BakingContext context,Matrix4fc transform) {
            var tints=new ArrayList<>(source.tints());
            tints.set(0,new RainbowTint(tints.getFirst()));
            ItemModel baked=new CuboidItemModelWrapper.Unbaked(source.model(),source.transformation(),List.copyOf(tints)).bake(context,transform);
            return new AnimatedBaked(baked);
        }
    }
    private static final class AnimatedBaked extends WrapperBakedItemModel {
        AnimatedBaked(ItemModel model) { super(model); }
        @Override public void update(ItemStackRenderState state,ItemStack stack,ItemModelResolver resolver,
            ItemDisplayContext display,ClientLevel level,ItemOwner owner,int seed) {
            super.update(state,stack,resolver,display,level,owner,seed);
            // GUI atlas caching must not freeze an otherwise correctly calculated live colour.
            if(isRainbow(stack)) state.setAnimated();
        }
    }
}
