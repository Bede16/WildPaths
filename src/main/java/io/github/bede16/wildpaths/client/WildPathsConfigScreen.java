package io.github.bede16.wildpaths.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.network.SaveNumericConfigPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WildPathsConfigScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_TICKS = 2_147_483_647L;

    public static Screen createLocal(Screen parent) {
        return create(
                parent,
                WildPathsConfig.exportConfigScreenData(),
                WildPathsConfig::applyConfigScreenChanges
        );
    }

    public static Screen createRemote(Screen parent, String json) {
        return create(
                parent,
                json,
                updatedJson -> {
                    PacketDistributor.sendToServer(new SaveNumericConfigPayload(updatedJson));
                    return null;
                }
        );
    }

    private static Screen create(Screen parent, String json, ConfigSaver saver) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Wild Paths configuration"));

        builder.category(mobTrafficCategory(root));

        JsonObject processing = root.getAsJsonObject("processing");
        ConfigCategory.Builder processingCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Processing"));
        addBoolean(processingCategory, processing, "onlyOverworld", "Only process the Overworld");
        addInteger(processingCategory, processing, "checkInterval", "Check interval (ticks)", 1, 72_000);
        addInteger(processingCategory, processing, "maxChecksPerInterval", "Maximum checks per interval", 1, 1_000_000);
        addInteger(processingCategory, processing, "nearbyScanRadius", "Nearby scan radius", 0, 128);
        addInteger(processingCategory, processing, "nearbyScanDepth", "Nearby scan depth", 0, 64);
        addInteger(processingCategory, processing, "nearbyScanColumnsPerPlayer", "Columns per player sample", 0, 10_000);
        builder.category(processingCategory.build());

        JsonObject recovery = root.getAsJsonObject("wearRecovery");
        ConfigCategory.Builder recoveryCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Wear recovery"));
        addBoolean(recoveryCategory, recovery, "enabled", "Enable wear recovery");
        addLong(recoveryCategory, recovery, "delayTicks", "Recovery delay (ticks)", 0L, MAX_TICKS);
        addLong(recoveryCategory, recovery, "intervalTicks", "Recovery interval (ticks)", 1L, MAX_TICKS);
        addInteger(recoveryCategory, recovery, "amountPerInterval", "Wear removed per interval", 1, 1_000_000);
        builder.category(recoveryCategory.build());

        builder.category(trafficCategory(
                root.getAsJsonObject("pathCreation"),
                "Path creation",
                RuleKind.PATH_CREATION
        ));
        builder.category(trafficCategory(
                root.getAsJsonObject("trampling"),
                "Plant trampling",
                RuleKind.TRAMPLING
        ));
        builder.category(timedCategory(root));

        return builder
                .save(() -> saver.save(GSON.toJson(root)))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory trafficCategory(JsonObject section, String name, RuleKind kind) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal(name));
        addBoolean(category, section, "enabled", "Enabled");
        if (kind == RuleKind.PATH_CREATION) {
            category.group(stringList(
                    section,
                    "allowedAbove",
                    "Allowed blocks above",
                    "Block IDs or #tags that may be above a block while a path forms.",
                    "minecraft:"
            ));
        }
        category.group(transitionPairList(section, kind));
        JsonArray transitions = section.getAsJsonArray("transitions");
        for (JsonElement element : transitions) {
            JsonObject transition = element.getAsJsonObject();
            OptionGroup.Builder group = OptionGroup.createBuilder()
                    .name(Component.literal(ruleName(transition)));
            addInteger(group, transition, "minimumWalks", "Protected crossings", 0, 1_000_000);
            addDouble(group, transition, "chance", "Initial chance (0-1)", 0.000001, 1.0);
            addDouble(group, transition, "chanceIncrease", "Chance increase (0-1)", 0.0, 1.0);
            addDouble(group, transition, "maxChance", "Maximum chance (0-1)", 0.000001, 1.0);
            addDouble(group, transition, "neighborChance", "Neighbor chance (0-1)", 0.0, 1.0);
            category.group(group.build());
        }
        return category.build();
    }

    private static ConfigCategory mobTrafficCategory(JsonObject root) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.literal("Traffic mobs"));
        category.group(entityTypeList(
                root,
                "trafficMobs",
                "Always active mobs",
                "These mobs affect paths and plants whenever they move. minecraft:villager includes adults and babies."
        ));
        category.group(entityTypeList(
                root,
                "riddenTrafficMobs",
                "Player-ridden mobs",
                "These mobs affect paths and plants only while a player is riding them."
        ));
        return category.build();
    }

    private static ListOption<String> entityTypeList(
            JsonObject root,
            String property,
            String name,
            String description
    ) {
        return stringList(root, property, name, description, "minecraft:");
    }

    private static ListOption<String> stringList(
            JsonObject root,
            String property,
            String name,
            String description,
            String newEntry
    ) {
        List<String> initial = readStringArray(root, property);
        return ListOption.<String>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(description)))
                .binding(
                        List.copyOf(initial),
                        () -> readStringArray(root, property),
                        values -> writeStringArray(root, property, values)
                )
                .controller(StringControllerBuilder::create)
                .initial(newEntry)
                .maximumNumberOfEntries(128)
                .insertEntriesAtEnd(true)
                .build();
    }

    private static ListOption<String> transitionPairList(JsonObject container, RuleKind kind) {
        List<String> initial = readTransitionPairs(container);
        return ListOption.<String>createBuilder()
                .name(Component.literal("Transition order (from -> to)"))
                .description(OptionDescription.of(Component.literal(
                        "Add, edit, remove, or reorder rules. Use namespace:block -> namespace:block. "
                                + "New or renamed rules receive safe defaults; save and reopen to edit their values."
                )))
                .binding(
                        List.copyOf(initial),
                        () -> readTransitionPairs(container),
                        values -> writeTransitionPairs(container, values, kind)
                )
                .controller(StringControllerBuilder::create)
                .initial("minecraft:stone -> minecraft:stone")
                .maximumNumberOfEntries(256)
                .insertEntriesAtEnd(true)
                .build();
    }

    private static List<String> readTransitionPairs(JsonObject container) {
        List<String> values = new ArrayList<>();
        JsonArray transitions = container.getAsJsonArray("transitions");
        if (transitions != null) {
            for (JsonElement element : transitions) {
                if (element.isJsonObject()) {
                    JsonObject rule = element.getAsJsonObject();
                    values.add(rule.get("from").getAsString() + " -> " + rule.get("to").getAsString());
                }
            }
        }
        return values;
    }

    private static void writeTransitionPairs(JsonObject container, List<String> values, RuleKind kind) {
        Map<String, JsonObject> existing = new LinkedHashMap<>();
        JsonArray current = container.getAsJsonArray("transitions");
        if (current != null) {
            for (JsonElement element : current) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject rule = element.getAsJsonObject();
                existing.put(
                        rule.get("from").getAsString() + " -> " + rule.get("to").getAsString(),
                        rule
                );
            }
        }

        JsonArray updated = new JsonArray();
        for (String value : values) {
            TransitionPair pair = parseTransitionPair(value);
            String key = pair.from() + " -> " + pair.to();
            JsonObject rule = existing.get(key);
            updated.add(rule == null ? createDefaultRule(pair, kind) : rule);
        }
        container.add("transitions", updated);
    }

    private static TransitionPair parseTransitionPair(String value) {
        int separator = value.indexOf("->");
        if (separator < 1 || separator + 2 >= value.length()) {
            throw new IllegalArgumentException("Transition must use namespace:block -> namespace:block");
        }
        String from = value.substring(0, separator).trim();
        String to = value.substring(separator + 2).trim();
        if (from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("Transition block IDs cannot be empty");
        }
        return new TransitionPair(from, to);
    }

    private static JsonObject createDefaultRule(TransitionPair pair, RuleKind kind) {
        JsonObject rule = new JsonObject();
        rule.addProperty("from", pair.from());
        rule.addProperty("to", pair.to());
        if (kind == RuleKind.TIMED) {
            rule.addProperty("ticks", 0L);
            rule.addProperty("chanceInterval", 1200L);
            rule.addProperty("chance", 0.01);
            rule.addProperty("chanceIncrease", 0.01);
            rule.addProperty("maxChance", 0.25);
            rule.addProperty("requiresRain", false);
            rule.addProperty("dryingDelay", 2400L);
            rule.addProperty("dryingInterval", 1200L);
            rule.addProperty("dryingChanceDecrease", 0.0);
            rule.addProperty("spreadChance", 0.0);
            rule.addProperty("resetOnWalk", true);
            rule.addProperty("neighborResetChance", 0.0);
            rule.addProperty("discoverNearby", true);
        } else {
            rule.addProperty("minimumWalks", kind == RuleKind.TRAMPLING ? 1 : 5);
            rule.addProperty("chance", kind == RuleKind.TRAMPLING ? 0.25 : 0.10);
            rule.addProperty("chanceIncrease", kind == RuleKind.TRAMPLING ? 0.10 : 0.05);
            rule.addProperty("maxChance", kind == RuleKind.TRAMPLING ? 1.0 : 0.80);
            rule.addProperty("neighborChance", 0.50);
        }
        return rule;
    }

    private static List<String> readStringArray(JsonObject root, String property) {
        List<String> values = new ArrayList<>();
        JsonArray array = root.getAsJsonArray(property);
        if (array != null) {
            for (JsonElement element : array) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static void writeStringArray(JsonObject root, String property, List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value.trim());
        }
        root.add(property, array);
    }

    private static ConfigCategory timedCategory(JsonObject root) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.literal("Decay and moss"));
        category.group(transitionPairList(root, RuleKind.TIMED));
        JsonArray transitions = root.getAsJsonArray("transitions");
        for (JsonElement element : transitions) {
            JsonObject transition = element.getAsJsonObject();
            OptionGroup.Builder group = OptionGroup.createBuilder()
                    .name(Component.literal(ruleName(transition)));
            addLong(group, transition, "ticks", "Protected time (ticks)", 0L, MAX_TICKS);
            addLong(group, transition, "chanceInterval", "Chance interval (ticks)", 1L, MAX_TICKS);
            addDouble(group, transition, "chance", "Initial chance (0-1)", 0.000001, 1.0);
            addDouble(group, transition, "chanceIncrease", "Chance increase (0-1)", 0.0, 1.0);
            addDouble(group, transition, "maxChance", "Maximum chance (0-1)", 0.000001, 1.0);
            addLong(group, transition, "dryingDelay", "Drying delay (ticks)", 0L, MAX_TICKS);
            addLong(group, transition, "dryingInterval", "Drying interval (ticks)", 1L, MAX_TICKS);
            addDouble(group, transition, "dryingChanceDecrease", "Drying decrease (0-1)", 0.0, 1.0);
            addDouble(group, transition, "spreadChance", "Adjacent moss bonus (0-1)", 0.0, 1.0);
            addDouble(group, transition, "neighborResetChance", "Neighbor reset chance (0-1)", 0.0, 1.0);
            addBoolean(group, transition, "requiresRain", "Requires rain");
            addBoolean(group, transition, "resetOnWalk", "Reset on traffic");
            addBoolean(group, transition, "discoverNearby", "Discover near players");
            category.group(group.build());
        }
        return category.build();
    }

    private static void addInteger(
            OptionParent parent,
            JsonObject object,
            String property,
            String label,
            int minimum,
            int maximum
    ) {
        if (object == null || !object.has(property)) {
            return;
        }
        int initial = object.get(property).getAsInt();
        parent.add(Option.<Integer>createBuilder()
                .name(Component.literal(label))
                .binding(initial, () -> object.get(property).getAsInt(), value -> object.addProperty(property, value))
                .controller(option -> IntegerFieldControllerBuilder.create(option).range(minimum, maximum))
                .build());
    }

    private static void addLong(
            OptionParent parent,
            JsonObject object,
            String property,
            String label,
            long minimum,
            long maximum
    ) {
        if (object == null || !object.has(property)) {
            return;
        }
        long initial = object.get(property).getAsLong();
        parent.add(Option.<Long>createBuilder()
                .name(Component.literal(label))
                .binding(initial, () -> object.get(property).getAsLong(), value -> object.addProperty(property, value))
                .controller(option -> LongFieldControllerBuilder.create(option).range(minimum, maximum))
                .build());
    }

    private static void addDouble(
            OptionParent parent,
            JsonObject object,
            String property,
            String label,
            double minimum,
            double maximum
    ) {
        if (object == null || !object.has(property)) {
            return;
        }
        double initial = object.get(property).getAsDouble();
        parent.add(Option.<Double>createBuilder()
                .name(Component.literal(label))
                .binding(initial, () -> object.get(property).getAsDouble(), value -> object.addProperty(property, value))
                .controller(option -> DoubleFieldControllerBuilder.create(option).range(minimum, maximum))
                .build());
    }

    private static void addBoolean(
            OptionParent parent,
            JsonObject object,
            String property,
            String label
    ) {
        if (object == null || !object.has(property)) {
            return;
        }
        boolean initial = object.get(property).getAsBoolean();
        parent.add(Option.<Boolean>createBuilder()
                .name(Component.literal(label))
                .binding(initial, () -> object.get(property).getAsBoolean(), value -> object.addProperty(property, value))
                .controller(BooleanControllerBuilder::create)
                .build());
    }

    private static void addInteger(
            ConfigCategory.Builder parent,
            JsonObject object,
            String property,
            String label,
            int minimum,
            int maximum
    ) {
        addInteger(parent::option, object, property, label, minimum, maximum);
    }

    private static void addLong(
            ConfigCategory.Builder parent,
            JsonObject object,
            String property,
            String label,
            long minimum,
            long maximum
    ) {
        addLong(parent::option, object, property, label, minimum, maximum);
    }

    private static void addInteger(
            OptionGroup.Builder parent,
            JsonObject object,
            String property,
            String label,
            int minimum,
            int maximum
    ) {
        addInteger(parent::option, object, property, label, minimum, maximum);
    }

    private static void addLong(
            OptionGroup.Builder parent,
            JsonObject object,
            String property,
            String label,
            long minimum,
            long maximum
    ) {
        addLong(parent::option, object, property, label, minimum, maximum);
    }

    private static void addDouble(
            OptionGroup.Builder parent,
            JsonObject object,
            String property,
            String label,
            double minimum,
            double maximum
    ) {
        addDouble(parent::option, object, property, label, minimum, maximum);
    }

    private static void addBoolean(
            ConfigCategory.Builder parent,
            JsonObject object,
            String property,
            String label
    ) {
        addBoolean(parent::option, object, property, label);
    }

    private static void addBoolean(
            OptionGroup.Builder parent,
            JsonObject object,
            String property,
            String label
    ) {
        addBoolean(parent::option, object, property, label);
    }

    private static String ruleName(JsonObject transition) {
        return shortId(transition.get("from").getAsString())
                + " -> "
                + shortId(transition.get("to").getAsString());
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private enum RuleKind {
        PATH_CREATION,
        TRAMPLING,
        TIMED
    }

    private record TransitionPair(String from, String to) {
    }

    @FunctionalInterface
    private interface OptionParent {
        void add(Option<?> option);
    }

    @FunctionalInterface
    private interface ConfigSaver {
        String save(String json);
    }

    private WildPathsConfigScreen() {
    }
}
