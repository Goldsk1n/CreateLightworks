package com.goldskinmc.lightworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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

    /**
     * Items that are not blocks (the Flashlight): a name, a tooltip, a recipe and an item model. They are found by their
     * names in the generated lang file, which Registrate writes for every registered item.
     */
    @Test
    void everyItemHasNameTooltipRecipeAndModel() throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        List<String> items = Pattern.compile("\"item\\.lightworks\\.([a-z0-9_]+)\":").matcher(lang).results()
                .map(match -> match.group(1)).distinct().sorted().toList();
        assertTrue(items.contains("flashlight"), "expected the Flashlight among the items, found " + items);
        List<String> missing = new ArrayList<>();
        for (String item : items) {
            requireKey(lang, "item.lightworks." + item + ".tooltip.summary", missing);
            requireFile(DATA.resolve("recipe/" + item + ".json"), missing);
            requireFile(Path.of("src/main/resources/assets/lightworks/models/item/" + item + ".json"), missing);
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    /**
     * The headlamp is no item of its own but data on Create's headgear, so the item check above does not see it: its
     * fitting and removing recipes, the headgear it fits (the item tag), its tooltip lines and key name, and the lamp's
     * part models, which the head layer draws.
     */
    @Test
    void theHeadlampHasItsRecipesTagTextAndModels() throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        for (String recipe : List.of("headlamp_fitting", "headlamp_removing", "flashlight_dyeing")) {
            requireFile(DATA.resolve("recipe/" + recipe + ".json"), missing);
        }
        Path tag = DATA.resolve("tags/item/headlamp_mounts.json");
        requireFile(tag, missing);
        if (Files.isRegularFile(tag)) {
            String mounts = Files.readString(tag);
            for (String headgear : List.of("create:goggles", "create:copper_diving_helmet", "create:netherite_diving_helmet")) {
                if (!mounts.contains("\"" + headgear + "\"")) {
                    missing.add(headgear + " in " + tag);
                }
            }
        }
        for (String key : List.of("lightworks.headlamp.fitted", "lightworks.headlamp.on", "lightworks.headlamp.off",
                "lightworks.headlamp.key_hint", "key.lightworks.headlamp", "key.categories.lightworks")) {
            requireKey(lang, key, missing);
        }
        for (String part : List.of("body", "lens", "lens_core")) {
            requireFile(Path.of("src/main/resources/assets/lightworks/models/block/headlamp/" + part + ".json"), missing);
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    /**
     * Every Engineer's Goggles line and hint named in the code has its text: a label and a value for each line, a text for
     * each hint (keys named as string literals, ternaries included).
     */
    @Test
    void everyGoggleLineAndHintHasText() throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        Pattern line = Pattern.compile("\\bline\\(tooltip, \"([a-z_]+)\"");
        Pattern hint = Pattern.compile("\\bhint\\(tooltip, ([^;]*)");
        Pattern literal = Pattern.compile("\"([a-z_]+)\"");
        List<String> missing = new ArrayList<>();
        int found = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var lines = line.matcher(source);
                while (lines.find()) {
                    found++;
                    requireKey(lang, "lightworks.goggles." + lines.group(1) + ".label", missing);
                    requireKey(lang, "lightworks.goggles." + lines.group(1), missing);
                }
                var hints = hint.matcher(source);
                while (hints.find()) {
                    var names = literal.matcher(hints.group(1));
                    while (names.find()) {
                        found++;
                        requireKey(lang, "lightworks.goggles.hint." + names.group(1), missing);
                    }
                }
            }
        }
        assertTrue(found >= 10, "expected goggle lines and hints in the code, found " + found);
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
        for (String pattern : List.of("steady", "blink", "opposite_blink", "pulse", "double_flash", "strobe")) {
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
