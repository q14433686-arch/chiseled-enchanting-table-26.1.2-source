package chiseled_enchanting_table.render;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Actual baked item models, real tooltip callbacks and live frames, including GUI-atlas animation flags. */
public final class RainbowItemProbe {
    private static int firstTint;
    private static long deadline;
    private static net.minecraft.nbt.CompoundTag originalData;
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError("rainbow item: "+message); }
    private static TrackingItemStackRenderState resolve(Minecraft client,ItemStack stack,ItemDisplayContext display) {
        var state=new TrackingItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state,stack,display,client.level,client.player,0);
        check(!state.isEmpty(),"item model did not bake");
        return state;
    }
    private static int tint(TrackingItemStackRenderState state) {
        return ((List<?>)state.getModelIdentity()).stream().filter(Integer.class::isInstance).map(Integer.class::cast)
            .reduce((a,b)->b).orElseThrow(()->new AssertionError("rainbow item: no baked tint identity"));
    }
    public static void begin(Minecraft client) {
        var rainbow=client.player.getInventory().getItem(8);
        var normal=client.player.getInventory().getItem(7);
        check(RainbowTableItemVisuals.isRainbow(rainbow),"real server loot flag missing");
        check(!RainbowTableItemVisuals.isRainbow(normal),"normal control marked rainbow");
        var animated=resolve(client,rainbow,ItemDisplayContext.GUI);
        var ordinary=resolve(client,normal,ItemDisplayContext.GUI);
        check(animated.isAnimated(),"GUI cache would freeze rainbow tint");
        check(!ordinary.isAnimated(),"normal table unnecessarily animated");
        firstTint=tint(animated);
        check(firstTint==RainbowTableItemVisuals.currentColor(client.level),"baked GUI model did not use live sheep tint");
        check(tint(ordinary)==new net.minecraft.client.color.item.Dye(0xFFA020F0).calculate(normal,client.level,client.player),"normal dye fallback changed");
        originalData=rainbow.get(DataComponents.CUSTOM_DATA).copyTag();
        deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var renamed=rainbow.copy();
        renamed.set(DataComponents.CUSTOM_NAME,Component.literal("Smoke custom table"));
        var lines=renamed.getTooltipLines(Item.TooltipContext.of(client.level),client.player,TooltipFlag.NORMAL);
        String badge=Component.translatable("item.chiseled_enchanting_table.rainbow_badge").getString();
        check(lines.getFirst().getString().contains("Smoke custom table") && lines.getFirst().getString().contains(badge),"custom name or badge lost");
        check(renamed.getHoverName().getString().equals("Smoke custom table"),"tooltip rewrote custom name component");
        check(lines.stream().anyMatch(c->c.getString().equals(Component.translatable("item.chiseled_enchanting_table.rainbow_cloth").getString())),"live tooltip callback missing rainbow explanation");
        var plainLines=normal.getTooltipLines(Item.TooltipContext.of(client.level),client.player,TooltipFlag.NORMAL);
        check(plainLines.stream().noneMatch(c->c.getString().contains(badge)),"normal table got rainbow badge");
        check(originalData.equals(renamed.get(DataComponents.CUSTOM_DATA).copyTag()),"presentation mutated freeze/rainbow metadata");
    }
    public static boolean laterFrame(Minecraft client) {
        var rainbow=client.player.getInventory().getItem(8);
        var state=resolve(client,rainbow,ItemDisplayContext.GUI);
        check(state.isAnimated(),"animated flag lost between frames");
        if(tint(state)==firstTint) {
            check(System.nanoTime()<deadline,"baked item tint stayed frozen across live frames");
            return false;
        }
        var hand=resolve(client,rainbow,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        check(hand.isAnimated() && tint(hand)==tint(state),"hand and inventory colours disagree");
        check(originalData.equals(rainbow.get(DataComponents.CUSTOM_DATA).copyTag()),"animation wrote per-frame NBT");
        System.out.println("RAINBOW ITEM PASS: real loot + baked GUI tint changes across live frames + animated-cache flag + hand parity + normal dye fallback + custom-name-safe badge/tooltips + immutable NBT");
        return true;
    }
    public static void afterReload(Minecraft client) {
        var rainbow=client.player.getInventory().getItem(8);
        var state=resolve(client,rainbow,ItemDisplayContext.GUI);
        check(state.isAnimated() && tint(state)==RainbowTableItemVisuals.currentColor(client.level),"resource reload lost rainbow wrapper/tint");
        check(!resolve(client,client.player.getInventory().getItem(7),ItemDisplayContext.GUI).isAnimated(),"reload animated normal item");
        System.out.println("RAINBOW ITEM RELOAD PASS: actual resource reload rebuilt animated model; normal model still static");
    }
}
