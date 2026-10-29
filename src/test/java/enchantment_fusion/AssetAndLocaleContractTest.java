package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AssetAndLocaleContractTest {
    private static final List<String> LOCALES = List.of(
        "de_de", "en_us", "es_es", "fr_fr", "it_it", "ja_jp",
        "ko_kr", "pt_br", "ru_ru", "zh_cn", "zh_tw"
    );

    @Test
    void allElevenLocalesHaveExactKeyParityUnifiedTerminologyAndZeroDeadKeys() throws Exception {
        Path langDir = Path.of("src/client/resources/assets/enchantment_fusion/lang");
        JsonObject enUs = JsonParser.parseString(
            Files.readString(langDir.resolve("en_us.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        Set<String> expectedKeys = enUs.keySet();
        assertTrue(expectedKeys.size() >= 700, "Expected full 0.4.1 UI/matrix/signature translation table");
        assertFalse(expectedKeys.contains("gui.enchantment_fusion.tab.compendium"), "Dead tab key must stay purged");
        assertFalse(expectedKeys.contains("gui.enchantment_fusion.preview.idle_line1"), "Dead preview key must stay purged");
        assertEquals("Remove", enUs.get("gui.enchantment_fusion.btn.unsocket").getAsString());

        for (String code : LOCALES) {
            Path file = langDir.resolve(code + ".json");
            assertTrue(Files.isRegularFile(file), "Missing locale file: " + file);
            JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(expectedKeys, obj.keySet(), "Key parity mismatch in locale " + code);
            for (String k : obj.keySet()) {
                String val = obj.get(k).getAsString();
                assertFalse(val.contains("\n"), "Literal newline forbidden in " + code + ":" + k);
                assertTrue(val.chars().allMatch(ch -> ch <= 0xFFFF), "Non-BMP glyph forbidden in " + code + ":" + k);
            }
        }

        JsonObject zhCn = JsonParser.parseString(
            Files.readString(langDir.resolve("zh_cn.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        JsonObject zhTw = JsonParser.parseString(
            Files.readString(langDir.resolve("zh_tw.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        assertTrue(
            zhCn.get("tooltip.enchantment_fusion.aberrant_cet_active").getAsString().contains("\u7E82\u523B\u9644\u9B54\u53F0"),
            "zh_cn must reference sister mod as 纂刻附魔台"
        );
        assertTrue(
            zhTw.get("tooltip.enchantment_fusion.aberrant_cet_active").getAsString().contains("\u7E82\u523B\u9644\u9B54\u53F0"),
            "zh_tw must reference sister mod as 纂刻附魔台"
        );
        assertEquals("\u5171\u9E23\u7A33\u5B9A\u6676", zhCn.get("item.enchantment_fusion.resonance_stabilizer").getAsString());
    }

    @Test
    void allPixelArtPngAssetsExistAndGuiUsesZeroSolidFillPlaceholders() throws Exception {
        List<Path> requiredPngs = List.of(
            Path.of("icon.png"),
            Path.of("src/main/resources/assets/enchantment_fusion/icon.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/block/enchantment_fusion_table_top.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/block/enchantment_fusion_table_side.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/block/enchantment_fusion_table_bottom.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/item/fused_enchanted_book.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/item/fusion_grimoire.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/item/fusion_tattered_page.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/item/resonance_stabilizer.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/grimoire_spread.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/panel_frame.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/slot_primary.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/slot_secondary.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/slot_catalyst.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/slot_default.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/swap_button.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/swap_button_hover.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/swap_button_disabled.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/button_normal.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/button_hover.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/button_disabled.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/vessel_gem_empty.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/vessel_gem_filled.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/vfx_rune_halo.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/vfx_arcane_spark.png"),
            Path.of("src/client/resources/assets/enchantment_fusion/textures/gui/vfx_shockwave_ring.png")
        );

        for (Path png : requiredPngs) {
            assertTrue(Files.isRegularFile(png), "Missing required PNG texture: " + png);
            BufferedImage img = ImageIO.read(png.toFile());
            assertNotNull(img, "Corrupt PNG texture: " + png);
            assertTrue(img.getWidth() >= 12 && img.getHeight() >= 12, "Invalid dimensions on " + png);
        }

        try (var paths = Files.walk(Path.of("src/client/java/enchantment_fusion/gui"))) {
            List<Path> guiFiles = paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            assertFalse(guiFiles.isEmpty());
            for (Path jf : guiFiles) {
                String src = Files.readString(jf, StandardCharsets.UTF_8);
                assertFalse(src.contains(".fill("), "Raw solid-color .fill( placeholder forbidden in " + jf);
            }
        }
    }
}
