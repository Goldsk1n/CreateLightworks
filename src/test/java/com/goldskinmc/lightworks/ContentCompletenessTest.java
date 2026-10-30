package com.goldskinmc.lightworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The v1.0 acceptance criterion "every block has a recipe, a Ponder scene and en_us text", checked on the
 * datagen output in {@code src/generated/resources} (run {@code runData} after adding a block). Every block
 * with a blockstate counts, except the invisible beam light.
 */
class ContentCompletenessTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path ASSETS = GENERATED.resolve("assets/lightworks");
    private static final Path DATA = GENERATED.resolve("data/lightworks");

    @Test
    void everyBlockHasNameTooltipRecipeLootAndPonderScene() throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        List<String> blocks = blocks();
        assertTrue(blocks.size() >= 8, "expected at least 8 blocks, found " + blocks);
        for (String block : blocks) {
            requireKey(lang, "block.lightworks." + block, missing);
            requireKey(lang, "block.lightworks." + block + ".tooltip.summary", missing);
            requireKey(lang, "lightworks.ponder." + block + ".header", missing);
            requireFile(DATA.resolve("recipe/" + block + ".json"), missing);
            requireFile(DATA.resolve("loot_table/blocks/" + block + ".json"), missing);
            requireFile(ASSETS.resolve("ponder/" + block + ".nbt"), missing);
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    private static List<String> blocks() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS.resolve("blockstates"))) {
            return files.map(file -> file.getFileName().toString().replace(".json", ""))
                    .filter(name -> !name.equals("beam_light"))
                    .sorted()
                    .toList();
        }
    }

    /** Create 6.0.0 finds option names under "create.<key>", 6.0.10 under the key itself. */
    @Test
    void warningPatternsAreNamedForEveryCreateVersion() throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        for (String pattern : List.of("steady", "blink", "pulse", "flash")) {
            requireKey(lang, "lightworks.warning_pattern." + pattern, missing);
            requireKey(lang, "create.lightworks.warning_pattern." + pattern, missing);
        }
        assertTrue(missing.isEmpty(), "Missing: " + missing);
    }

    private static void requireKey(String lang, String key, List<String> missing) {
        if (!lang.contains("\"" + key + "\":")) {
            missing.add(key);
        }
    }

    private static void requireFile(Path file, List<String> missing) {
        if (!Files.isRegularFile(file)) {
            missing.add(file.toString());
        }
    }
}
