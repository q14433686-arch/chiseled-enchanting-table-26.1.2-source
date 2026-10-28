package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.gui.GuiMicroEffects;
import chiseled_enchanting_table.menu.TableVisualEventPayload;
import chiseled_enchanting_table.menu.TableVisualEventPayload.Kind;
import chiseled_enchanting_table.menu.TableVisualEvents;
import chiseled_enchanting_table.render.TableCustomParticles;
import chiseled_enchanting_table.render.TableCustomParticles.QuadSnapshot;
import chiseled_enchanting_table.render.TableCustomParticles.SpriteKind;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * Live headless client + integrated-server verification for Round 3 sensory polish:
 * custom particle textures, 3D billboard quad extraction, live client floating-item sync,
 * cloth wash item detection, and GUI micro-animation state.
 */
public final class SensoryPolishProbe {
    private SensoryPolishProbe() {}

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("sensory polish probe: " + msg);
        }
    }

    public static void verify(Minecraft client, BlockPos tablePos) {
        // 1. Custom 32x32 particle textures are registered & loadable in ResourceManager
        check(
            client.getResourceManager().getResource(TableCustomParticles.SCORCHED_PAGE_TEXTURE).isPresent(),
            "missing scorched_page.png in client ResourceManager"
        );
        check(
            client.getResourceManager().getResource(TableCustomParticles.ARCANE_RUNE_SPARK_TEXTURE).isPresent(),
            "missing arcane_rune_spark.png in client ResourceManager"
        );

        // 2. Cloth wash item predicate accepts Water Bucket, Wet Sponge, Water Bottle and rejects non-water
        check(ChiseledEnchantingTableBlock.isClothWashItem(new ItemStack(Items.WATER_BUCKET)), "water bucket should wash cloth");
        check(ChiseledEnchantingTableBlock.isClothWashItem(new ItemStack(Items.WET_SPONGE)), "wet sponge should wash cloth");
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        check(ChiseledEnchantingTableBlock.isClothWashItem(waterBottle), "water bottle should wash cloth");
        ItemStack healingPotion = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        check(!ChiseledEnchantingTableBlock.isClothWashItem(healingPotion), "healing potion must not wash cloth");
        check(!ChiseledEnchantingTableBlock.isClothWashItem(new ItemStack(Items.LAVA_BUCKET)), "lava bucket must not wash cloth");
        check(waterBottle.get(DataComponents.POTION_CONTENTS) != null, "water bottle missing potion contents");

        // 3. Client block entity received live slot-0 DIAMOND_SWORD sync and bakes non-empty 3D floating item
        check(client.level != null, "client level null");
        var clientBe = client.level.getBlockEntity(tablePos);
        check(clientBe instanceof ChiseledEnchantingTableBlockEntity, "missing client table BE at " + tablePos);
        var tableBe = (ChiseledEnchantingTableBlockEntity) clientBe;
        check(
            tableBe.visualDisplayStack().is(Items.DIAMOND_SWORD),
            "client BE did not live-sync persisted DIAMOND_SWORD for 3D floating display: " + tableBe.visualDisplayStack()
        );
        ItemStackRenderState floating = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
            floating, tableBe.visualDisplayStack(), ItemDisplayContext.GROUND, client.level, client.player, 0
        );
        check(!floating.isEmpty(), "3D floating target item failed to bake ItemStackRenderState");

        // 4. Custom 3D particle choreographer spawns, ticks, and extracts both SCORCHED_PAGE and ARCANE_RUNE_SPARK quads
        TableCustomParticles.clear();
        BlockPos shelfPos = tablePos.offset(2, 1, 0);
        TableCustomParticles.handleVisualEvent(new TableVisualEventPayload(
            Kind.RUSH_BACKLASH, tablePos, shelfPos, 2, Direction.WEST.get2DDataValue(), TableVisualEvents.COLOR_CRIMSON_BACKLASH
        ));
        check(TableCustomParticles.activeEmitterCount() == 1, "expected 1 active 6s backlash emitter");
        check(TableCustomParticles.activeParticleCount() >= 16, "expected immediate backlash beam + scorched page burst");

        TableCustomParticles.handleVisualEvent(new TableVisualEventPayload(
            Kind.TOME_TETHER, tablePos, shelfPos, 1, Direction.WEST.get2DDataValue(), TableVisualEvents.COLOR_ARCANE_VIOLET
        ));
        TableCustomParticles.handleVisualEvent(new TableVisualEventPayload(
            Kind.TOME_LOCATE, tablePos, shelfPos, 4, Direction.WEST.get2DDataValue(), TableVisualEvents.COLOR_LOCATE_CYAN
        ));
        check(TableCustomParticles.activeEmitterCount() == 2, "expected backlash + 6s locate beacon emitters");
        TableCustomParticles.handleVisualEvent(new TableVisualEventPayload(
            Kind.CLEANSE_PURGE_CURSE, tablePos, tablePos, -1, -1, TableVisualEvents.COLOR_SACRED_GOLD
        ));
        TableCustomParticles.clientTick();
        TableCustomParticles.clientTick();

        List<QuadSnapshot> quads = TableCustomParticles.extractForTable(
            tablePos, 0.5F, new Vec3(tablePos.getX() + 0.5D, tablePos.getY() + 2.2D, tablePos.getZ() + 3.5D)
        );
        check(!quads.isEmpty(), "expected extracted 3D custom particle quads for table");
        boolean sawPage = false;
        boolean sawSpark = false;
        for (QuadSnapshot q : quads) {
            if (q.sprite() == SpriteKind.SCORCHED_PAGE) sawPage = true;
            if (q.sprite() == SpriteKind.ARCANE_RUNE_SPARK) sawSpark = true;
            check(Float.isFinite(q.lx()) && Float.isFinite(q.ly()) && Float.isFinite(q.lz()), "non-finite quad center");
        }
        check(sawPage, "expected SCORCHED_PAGE billboard quad in extracted snapshot");
        check(sawSpark, "expected ARCANE_RUNE_SPARK billboard quad in extracted snapshot");

        // 5. GUI micro-effects handle backlash jolt + mode switch without error
        GuiMicroEffects.reset();
        GuiMicroEffects.onVisualEvent(new TableVisualEventPayload(
            Kind.RUSH_BACKLASH, tablePos, shelfPos, 2, Direction.WEST.get2DDataValue(), TableVisualEvents.COLOR_CRIMSON_BACKLASH
        ));
        GuiMicroEffects.observeModeSignature(1);
        GuiMicroEffects.observeModeSignature(5);
        int jolt = GuiMicroEffects.currentPanelJoltX();
        check(jolt >= -3 && jolt <= 3, "jolt out of bounds: " + jolt);

        // 6. Bug 1 regression: save/reload BE with enchanted item -> remove item -> updateTag must clear stale client item!
        var integrated = client.getSingleplayerServer();
        check(integrated != null, "missing integrated server for reload sync probe");
        net.minecraft.nbt.CompoundTag postRemoveTag = integrated.submit(() -> {
            var sPlayer = integrated.getPlayerList().getPlayers().getFirst();
            var sLevel = sPlayer.level();
            var rawBe = sLevel.getBlockEntity(tablePos);
            check(rawBe instanceof ChiseledEnchantingTableBlockEntity, "missing server table BE");
            var sBe = (ChiseledEnchantingTableBlockEntity) rawBe;
            net.minecraft.nbt.CompoundTag saved = sBe.saveWithFullMetadata(sLevel.registryAccess());
            var loadedRaw = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                tablePos, sLevel.getBlockState(tablePos), saved, sLevel.registryAccess()
            );
            check(loadedRaw instanceof ChiseledEnchantingTableBlockEntity, "BlockEntity.loadStatic failed");
            var reloaded = (ChiseledEnchantingTableBlockEntity) loadedRaw;
            reloaded.setLevel(sLevel);
            check(reloaded.visualDisplayStack().is(Items.DIAMOND_SWORD), "reloaded BE lost DIAMOND_SWORD");
            reloaded.setItem(0, ItemStack.EMPTY);
            check(reloaded.visualDisplayStack().isEmpty(), "reloaded BE did not clear slot 0");
            return reloaded.getUpdateTag(sLevel.registryAccess());
        }).join();
        check(postRemoveTag.contains("Items"), "empty table updateTag omitted Items list (ghost item regression)");
        net.minecraft.nbt.CompoundTag merged = client.level.getBlockState(tablePos) != null
            ? postRemoveTag.copy()
            : postRemoveTag;
        merged.putString("id", "chiseled_enchanting_table:chiseled_enchanting_table");
        var clientLoaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            tablePos, tableBe.getBlockState(), merged, client.level.registryAccess()
        );
        check(clientLoaded instanceof ChiseledEnchantingTableBlockEntity, "client loadStatic failed");
        check(
            ((ChiseledEnchantingTableBlockEntity) clientLoaded).visualDisplayStack().isEmpty(),
            "client BE retained ghost floating item after empty updateTag"
        );

        TableCustomParticles.clear();
        GuiMicroEffects.reset();
    }
}
