package io.github.bede16.wildpaths.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads all Wild Paths settings from one JSON5 file. */
public final class WildPathsConfig {
    public static final String FILE_NAME = "wild_paths.json5";
    private static final int CURRENT_CONFIG_VERSION = 2;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String DEFAULT_JSON5 = """
            {
              configVersion: 2,

              // Limits how much work Wild Paths performs at once.
              processing: {
                checkInterval: 200,
                maxChecksPerInterval: 1024,
                onlyOverworld: false,
                nearbyScanRadius: 24,
                nearbyScanDepth: 6,
                nearbyScanColumnsPerPlayer: 128,
              },

              // Matching surface blocks are discovered gradually near active players.
              transitions: [
                {
                  from: "minecraft:dirt_path",
                  to: "minecraft:dirt",
                  // One protected hour after the last use, then a roll every five minutes.
                  ticks: 72000,
                  chanceInterval: 6000,
                  chance: 0.05,
                  chanceIncrease: 0.05,
                  maxChance: 1.0,
                  resetOnWalk: true,
                  discoverNearby: true,
                },
                {
                  from: "minecraft:cobblestone",
                  to: "minecraft:mossy_cobblestone",
                  // A small roll for every minute of rain that can reach the block.
                  ticks: 0,
                  chanceInterval: 1200,
                  chance: 0.005,
                  requiresRain: true,
                  resetOnWalk: false,
                  discoverNearby: true,
                },
                {
                  from: "minecraft:stone_bricks",
                  to: "minecraft:mossy_stone_bricks",
                  ticks: 0,
                  chanceInterval: 1200,
                  chance: 0.005,
                  requiresRain: true,
                  resetOnWalk: false,
                  discoverNearby: true,
                },
              ],
            }
            """;

    private static volatile Settings settings = new Settings(200, 1_024, false, 24, 6, 128, Map.of());

    public static synchronized void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                Files.writeString(configPath, DEFAULT_JSON5);
            }

            JsonObject root;
            try (Reader reader = Files.newBufferedReader(configPath)) {
                root = parseRoot(reader);
            }

            int fileVersion = root.has("configVersion") ? root.get("configVersion").getAsInt() : 1;
            if (fileVersion < CURRENT_CONFIG_VERSION) {
                Path backupPath = configPath.resolveSibling(FILE_NAME + ".before-v" + CURRENT_CONFIG_VERSION + ".backup");
                if (Files.notExists(backupPath)) {
                    Files.copy(configPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
                }

                root = migrate(root);
                Files.writeString(configPath, renderJson5(root));
                WildPaths.LOGGER.info("Updated {} from config version {} to {}. Backup: {}", configPath, fileVersion, CURRENT_CONFIG_VERSION, backupPath);
            } else if (fileVersion > CURRENT_CONFIG_VERSION) {
                WildPaths.LOGGER.warn("{} uses config version {}, but this Wild Paths release supports version {}", configPath, fileVersion, CURRENT_CONFIG_VERSION);
            }

            settings = parse(root);
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

    public static int nearbyScanRadius() {
        return settings.nearbyScanRadius();
    }

    public static int nearbyScanDepth() {
        return settings.nearbyScanDepth();
    }

    public static int nearbyScanColumnsPerPlayer() {
        return settings.nearbyScanColumnsPerPlayer();
    }

    public static TransitionRule find(BlockState state) {
        return settings.transitions().get(state.getBlock());
    }

    private static Settings parse(Reader source) {
        return parse(parseRoot(source));
    }

    private static JsonObject parseRoot(Reader source) {
        JsonReader reader = new JsonReader(new StringReader(normalizeJson5(readAll(source))));
        reader.setLenient(true);
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("The config root must be an object");
        }
        return rootElement.getAsJsonObject();
    }

    private static Settings parse(JsonObject root) {
        JsonObject processing = root.getAsJsonObject("processing");
        if (processing == null) {
            throw new IllegalArgumentException("Missing processing object");
        }

        int checkInterval = rangedInt(processing, "checkInterval", 1, 72_000);
        int maxChecks = rangedInt(processing, "maxChecksPerInterval", 1, 1_000_000);
        boolean onlyOverworld = processing.has("onlyOverworld") && processing.get("onlyOverworld").getAsBoolean();
        int nearbyScanRadius = optionalRangedInt(processing, "nearbyScanRadius", 24, 0, 128);
        int nearbyScanDepth = optionalRangedInt(processing, "nearbyScanDepth", 6, 0, 64);
        int nearbyScanColumns = optionalRangedInt(processing, "nearbyScanColumnsPerPlayer", 128, 0, 10_000);

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
            long chanceInterval = object.has("chanceInterval") ? object.get("chanceInterval").getAsLong() : checkInterval;
            double chance = object.has("chance") ? object.get("chance").getAsDouble() : 1.0;
            double chanceIncrease = object.has("chanceIncrease") ? object.get("chanceIncrease").getAsDouble() : 0.0;
            double maxChance = object.has("maxChance") ? object.get("maxChance").getAsDouble() : 1.0;
            boolean requiresRain = object.has("requiresRain") && object.get("requiresRain").getAsBoolean();
            boolean resetOnWalk = !object.has("resetOnWalk") || object.get("resetOnWalk").getAsBoolean();
            boolean discoverNearby = !object.has("discoverNearby") || object.get("discoverNearby").getAsBoolean();

            if (ticks < 0L) {
                throw new IllegalArgumentException("Transition ticks must be at least 0");
            }
            if (chanceInterval < 1L) {
                throw new IllegalArgumentException("Transition chanceInterval must be at least 1");
            }
            if (!Double.isFinite(chance) || chance <= 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Transition chance must be greater than 0 and at most 1");
            }
            if (!Double.isFinite(chanceIncrease) || chanceIncrease < 0.0 || chanceIncrease > 1.0) {
                throw new IllegalArgumentException("Transition chanceIncrease must be between 0 and 1");
            }
            if (!Double.isFinite(maxChance) || maxChance < chance || maxChance > 1.0) {
                throw new IllegalArgumentException("Transition maxChance must be at least chance and at most 1");
            }
            TransitionRule transition = new TransitionRule(
                    from,
                    to,
                    ticks,
                    chanceInterval,
                    chance,
                    chanceIncrease,
                    maxChance,
                    requiresRain,
                    resetOnWalk,
                    discoverNearby
            );
            if (transitions.put(from, transition) != null) {
                throw new IllegalArgumentException("Duplicate transition for " + BuiltInRegistries.BLOCK.getKey(from));
            }
        }

        return new Settings(
                checkInterval,
                maxChecks,
                onlyOverworld,
                nearbyScanRadius,
                nearbyScanDepth,
                nearbyScanColumns,
                Map.copyOf(transitions)
        );
    }

    private static JsonObject migrate(JsonObject existing) {
        JsonObject defaults = parseRoot(new StringReader(DEFAULT_JSON5));
        mergeMissing(existing, defaults);

        JsonArray existingTransitions = existing.getAsJsonArray("transitions");
        JsonArray defaultTransitions = defaults.getAsJsonArray("transitions");
        if (existingTransitions != null && defaultTransitions != null) {
            Map<String, JsonObject> defaultsByBlock = new LinkedHashMap<>();
            for (JsonElement element : defaultTransitions) {
                if (element.isJsonObject() && element.getAsJsonObject().has("from")) {
                    defaultsByBlock.put(element.getAsJsonObject().get("from").getAsString(), element.getAsJsonObject());
                }
            }

            for (JsonElement element : existingTransitions) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("from")) {
                    continue;
                }
                JsonObject transition = element.getAsJsonObject();
                JsonObject transitionDefaults = defaultsByBlock.get(transition.get("from").getAsString());
                if (transitionDefaults != null) {
                    mergeMissing(transition, transitionDefaults);
                }
            }
        }

        existing.addProperty("configVersion", CURRENT_CONFIG_VERSION);
        return existing;
    }

    private static void mergeMissing(JsonObject target, JsonObject defaults) {
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            if (!target.has(entry.getKey())) {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            } else if (target.get(entry.getKey()).isJsonObject() && entry.getValue().isJsonObject()) {
                mergeMissing(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            }
        }
    }

    private static String renderJson5(JsonObject root) {
        return "// Automatically migrated by Wild Paths. The previous file is stored next to this one.\n"
                + PRETTY_GSON.toJson(root)
                + "\n";
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

    private static int optionalRangedInt(JsonObject object, String name, int fallback, int minimum, int maximum) {
        if (!object.has(name)) {
            return fallback;
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
            int nearbyScanRadius,
            int nearbyScanDepth,
            int nearbyScanColumnsPerPlayer,
            Map<Block, TransitionRule> transitions
    ) {
    }

    private WildPathsConfig() {
    }
}

