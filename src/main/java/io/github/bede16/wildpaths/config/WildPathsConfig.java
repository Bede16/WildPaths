package io.github.bede16.wildpaths.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.github.bede16.wildpaths.WildPaths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads all Wild Paths settings from one JSON5 file. */
public final class WildPathsConfig {
    public static final String FILE_NAME = "wild_paths.json5";

    private static final String DEFAULT_JSON5 = """
            {
              // Limits how much work Wild Paths performs at once.
              processing: {
                checkInterval: 200,
                maxChecksPerInterval: 1024,
                onlyOverworld: false,
              },

              // A block is observed only after a player walks on it.
              transitions: [
                {
                  from: "minecraft:dirt_path",
                  to: "minecraft:dirt",
                  ticks: 72000,
                },
                {
                  from: "minecraft:cobblestone",
                  to: "minecraft:mossy_cobblestone",
                  ticks: 240000,
                  requiresRain: true,
                },
                {
                  from: "minecraft:stone_bricks",
                  to: "minecraft:mossy_stone_bricks",
                  ticks: 240000,
                  requiresRain: true,
                },
              ],
            }
            """;

    private static volatile Settings settings = new Settings(200, 1_024, false, Map.of());

    public static synchronized void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                Files.writeString(configPath, DEFAULT_JSON5);
            }

            try (Reader reader = Files.newBufferedReader(configPath)) {
                settings = parse(reader);
            }
            WildPaths.LOGGER.info("Loaded {} Wild Paths transitions from {}", settings.transitions().size(), configPath);
        } catch (Exception exception) {
            WildPaths.LOGGER.error("Could not load {}. Using built-in defaults.", configPath, exception);
            settings = parse(new StringReader(DEFAULT_JSON5));
        }
    }

    public static int checkInterval() {
        return settings.checkInterval();
    }

    public static int maxChecksPerInterval() {
        return settings.maxChecksPerInterval();
    }

    public static boolean onlyOverworld() {
        return settings.onlyOverworld();
    }

    public static TransitionRule find(BlockState state) {
        return settings.transitions().get(state.getBlock());
    }

    private static Settings parse(Reader source) {
        JsonReader reader = new JsonReader(new StringReader(normalizeJson5(readAll(source))));
        reader.setLenient(true);
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("The config root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonObject processing = root.getAsJsonObject("processing");
        if (processing == null) {
            throw new IllegalArgumentException("Missing processing object");
        }

        int checkInterval = rangedInt(processing, "checkInterval", 1, 72_000);
        int maxChecks = rangedInt(processing, "maxChecksPerInterval", 1, 1_000_000);
        boolean onlyOverworld = processing.has("onlyOverworld") && processing.get("onlyOverworld").getAsBoolean();

        JsonArray entries = root.getAsJsonArray("transitions");
        if (entries == null) {
            throw new IllegalArgumentException("Missing transitions array");
        }

        Map<Block, TransitionRule> transitions = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Each transition must be an object");
            }

            JsonObject object = element.getAsJsonObject();
            Block from = resolveBlock(requiredString(object, "from"));
            Block to = resolveBlock(requiredString(object, "to"));
            long ticks = object.get("ticks").getAsLong();
            boolean requiresRain = object.has("requiresRain") && object.get("requiresRain").getAsBoolean();

            if (ticks < 1L) {
                throw new IllegalArgumentException("Transition ticks must be at least 1");
            }
            if (transitions.put(from, new TransitionRule(from, to, ticks, requiresRain)) != null) {
                throw new IllegalArgumentException("Duplicate transition for " + BuiltInRegistries.BLOCK.getKey(from));
            }
        }

        return new Settings(checkInterval, maxChecks, onlyOverworld, Map.copyOf(transitions));
    }

    private static String readAll(Reader source) {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4_096];
        try {
            int count;
            while ((count = source.read(buffer)) >= 0) {
                result.append(buffer, 0, count);
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not read config", exception);
        }
        return result.toString();
    }

    private static String normalizeJson5(String input) {
        StringBuilder withoutComments = new StringBuilder(input.length());
        char quote = 0;
        boolean escaped = false;

        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);

            if (quote != 0) {
                withoutComments.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
                withoutComments.append(current);
                continue;
            }

            if (current == '/' && index + 1 < input.length()) {
                char next = input.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    while (index < input.length() && input.charAt(index) != '\n') {
                        index++;
                    }
                    if (index < input.length()) {
                        withoutComments.append('\n');
                    }
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    while (index + 1 < input.length() && !(input.charAt(index) == '*' && input.charAt(index + 1) == '/')) {
                        if (input.charAt(index) == '\n') {
                            withoutComments.append('\n');
                        }
                        index++;
                    }
                    index++;
                    continue;
                }
            }

            withoutComments.append(current);
        }

        String uncommented = withoutComments.toString();
        StringBuilder normalized = new StringBuilder(uncommented.length());
        quote = 0;
        escaped = false;

        for (int index = 0; index < uncommented.length(); index++) {
            char current = uncommented.charAt(index);
            if (quote != 0) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
                normalized.append(current);
                continue;
            }

            if (current == ',') {
                int next = index + 1;
                while (next < uncommented.length() && Character.isWhitespace(uncommented.charAt(next))) {
                    next++;
                }
                if (next < uncommented.length() && (uncommented.charAt(next) == '}' || uncommented.charAt(next) == ']')) {
                    continue;
                }
            }

            normalized.append(current);
        }
        return normalized.toString();
    }

    private static int rangedInt(JsonObject object, String name, int minimum, int maximum) {
        if (!object.has(name)) {
            throw new IllegalArgumentException("Missing " + name);
        }
        int value = object.get(name).getAsInt();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing or invalid " + name);
        }
        return object.get(name).getAsString();
    }

    private static Block resolveBlock(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown block: " + name);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private record Settings(
            int checkInterval,
            int maxChecksPerInterval,
            boolean onlyOverworld,
            Map<Block, TransitionRule> transitions
    ) {
    }

    private WildPathsConfig() {
    }
}
