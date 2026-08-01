package io.github.bede16.wildpaths.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.bede16.wildpaths.config.PathCreationRule;
import io.github.bede16.wildpaths.config.TramplingRule;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.data.WildPathsSavedData;
import io.github.bede16.wildpaths.network.OpenNumericConfigPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Administrative commands for reloading and inspecting Wild Paths. */
public final class WildPathsCommands {
    private static final Set<UUID> DEBUG_OVERLAYS = new HashSet<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wildpaths")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource())))
                        .then(Commands.literal("config")
                                .executes(context -> openConfig(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setDebugOverlay(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        )))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> debug(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                                        ))))
        );
    }

    public static void clearDebugOverlays() {
        DEBUG_OVERLAYS.clear();
    }

    public static void tickDebugOverlay(ServerPlayer player) {
        if (!DEBUG_OVERLAYS.contains(player.getUUID())) {
            return;
        }

        if (WildPathsConfig.onlyOverworld() && player.level().dimension() != Level.OVERWORLD) {
            player.displayClientMessage(Component.literal("Wild Paths | disabled in this dimension"), true);
            return;
        }

        HitResult hit = player.pick(20.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal("Wild Paths | no block in range"), true);
            return;
        }

        player.displayClientMessage(debugOverlayText(player.serverLevel(), blockHit.getBlockPos()), true);
    }

    private static int setDebugOverlay(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (enabled) {
            DEBUG_OVERLAYS.add(player.getUUID());
            source.sendSuccess(() -> Component.literal("Wild Paths debug overlay enabled."), false);
            tickDebugOverlay(player);
        } else {
            DEBUG_OVERLAYS.remove(player.getUUID());
            player.displayClientMessage(Component.empty(), true);
            source.sendSuccess(() -> Component.literal("Wild Paths debug overlay disabled."), false);
        }
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        if (!WildPathsConfig.load()) {
            source.sendFailure(Component.literal(
                    "Wild Paths could not load the configuration and is using built-in defaults. Check the server log."
            ));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Wild Paths configuration reloaded successfully."),
                true
        );
        return 1;
    }

    private static int openConfig(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!NetworkRegistry.hasChannel(player.connection, OpenNumericConfigPayload.TYPE.id())) {
            source.sendFailure(Component.literal(
                    "Wild Paths config screen requires Wild Paths and YACL 3.8.2 or newer on your client."
            ));
            return 0;
        }

        PacketDistributor.sendToPlayer(
                player,
                new OpenNumericConfigPayload(WildPathsConfig.exportConfigScreenData())
        );
        return 1;
    }

    private static int status(CommandSourceStack source) {
        int enabledLevels = 0;
        int trackedBlocks = 0;
        int wornBlocks = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (!isEnabled(level)) {
                continue;
            }
            enabledLevels++;
            trackedBlocks += WildPathsSavedData.get(level).size();
            wornBlocks += WildPathsSavedData.get(level).wearSize();
        }

        int finalEnabledLevels = enabledLevels;
        int finalTrackedBlocks = trackedBlocks;
        int finalWornBlocks = wornBlocks;
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Wild Paths: %d timed, %d path creation, and %d trampling rules; %d timed and %d traffic-worn blocks across %d enabled dimensions.",
                        WildPathsConfig.transitionCount(),
                        WildPathsConfig.pathCreationTransitionCount(),
                        WildPathsConfig.tramplingTransitionCount(),
                        finalTrackedBlocks,
                        finalWornBlocks,
                        finalEnabledLevels
                )),
                false
        );
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Processing: every %d ticks, up to %d checks; nearby scan radius %d, %d columns per player sample.",
                        WildPathsConfig.checkInterval(),
                        WildPathsConfig.maxChecksPerInterval(),
                        WildPathsConfig.nearbyScanRadius(),
                        WildPathsConfig.nearbyScanColumnsPerPlayer()
                )),
                false
        );
        return 1;
    }

    private static int debug(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);
        TransitionRule transition = WildPathsConfig.find(state);
        PathCreationRule pathCreation = WildPathsConfig.findPathCreation(state);
        TramplingRule trampling = WildPathsConfig.findTrampling(state);
        BlockPos tramplingPos = trampling == null ? pos : normalizeTramplingPos(pos, state);
        String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        BlockPos protectedGroundPos = trampling == null ? pos : tramplingPos.below();
        if (WildPathsSavedData.isProtected(level, protectedGroundPos)) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            Locale.ROOT,
                            "Wild Paths: %s at %s is protected by wool directly underneath it; all progress is reset.",
                            blockName,
                            formatPos(pos)
                    )),
                    false
            );
            return 1;
        }

        if (transition == null && pathCreation == null && trampling == null) {
            source.sendFailure(Component.literal(String.format(
                    Locale.ROOT,
                    "Wild Paths: %s at %s has no configured transition.",
                    blockName,
                    formatPos(pos)
            )));
            return 0;
        }

        if (transition == null && pathCreation == null) {
            return debugTrampling(source, level, tramplingPos, blockName, trampling);
        }

        if (transition == null && !WildPathsConfig.isPathCreationSpaceAllowed(level, pos)) {
            String aboveName = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.above()).getBlock()).toString();
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            Locale.ROOT,
                            "Wild Paths creation at %s is blocked by %s above it, which is not whitelisted.",
                            formatPos(pos),
                            aboveName
                    )),
                    false
            );
            return 1;
        }

        if (transition == null) {
            return debugPathCreation(source, level, pos, blockName, pathCreation);
        }

        WildPathsSavedData.TrackedEntry entry = WildPathsSavedData.get(level).trackedEntry(pos);
        if (entry == null) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            Locale.ROOT,
                            "Wild Paths: %s at %s can transition to %s, but is not currently tracked.",
                            blockName,
                            formatPos(pos),
                            BuiltInRegistries.BLOCK.getKey(transition.to())
                    )),
                    false
            );
            return 0;
        }

        long gameTime = level.getGameTime();
        long protectedTicks = Math.max(0L, transition.ticks() - (gameTime - entry.lastUse()));
        long nextAttemptTicks = Math.max(0L, transition.chanceInterval() - (gameTime - entry.lastAttempt()));
        double currentChance = Math.min(
                transition.maxChance(),
                transition.chance() + entry.failedAttempts() * transition.chanceIncrease()
        );
        String rainState = transition.requiresRain()
                ? (level.isRainingAt(pos.above()) ? "rain requirement met" : "waiting for rain")
                : "no rain required";

        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Wild Paths: %s at %s -> %s; protected for %d more ticks; next roll in %d ticks.",
                        blockName,
                        formatPos(pos),
                        BuiltInRegistries.BLOCK.getKey(transition.to()),
                        protectedTicks,
                        nextAttemptTicks
                )),
                false
        );
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Chance %.2f%%, %d failed rolls, %s.",
                        currentChance * 100.0,
                        entry.failedAttempts(),
                        rainState
                )),
                false
        );
        return 1;
    }

    private static int debugPathCreation(
            CommandSourceStack source,
            ServerLevel level,
            BlockPos pos,
            String blockName,
            PathCreationRule transition
    ) {
        WildPathsSavedData.WearEntry entry = WildPathsSavedData.get(level).wearEntry(level, pos);
        int walks = entry == null ? 0 : entry.walks();
        int failures = entry == null ? 0 : entry.failedAttempts();
        int protectedWalks = Math.max(0, transition.minimumWalks() - walks);
        double currentChance = Math.min(
                transition.maxChance(),
                transition.chance() + failures * transition.chanceIncrease()
        );

        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Wild Paths creation: %s at %s -> %s; %d recorded walks, %d protected walks remaining.",
                        blockName,
                        formatPos(pos),
                        BuiltInRegistries.BLOCK.getKey(transition.to()),
                        walks,
                        protectedWalks
                )),
                false
        );
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Next crossing chance %.2f%%, %d failed rolls; neighbor wear chance %.2f%%.",
                        currentChance * 100.0,
                        failures,
                        transition.neighborChance() * 100.0
                )),
                false
        );
        return 1;
    }

    private static int debugTrampling(
            CommandSourceStack source,
            ServerLevel level,
            BlockPos pos,
            String blockName,
            TramplingRule transition
    ) {
        WildPathsSavedData.WearEntry entry = WildPathsSavedData.get(level).wearEntry(level, pos);
        int walks = entry == null ? 0 : entry.walks();
        int failures = entry == null ? 0 : entry.failedAttempts();
        int protectedWalks = Math.max(0, transition.minimumWalks() - walks);
        double currentChance = Math.min(
                transition.maxChance(),
                transition.chance() + failures * transition.chanceIncrease()
        );
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Wild Paths trampling: %s at %s -> %s; %d walks, %d protected walks remaining, chance %.2f%%, neighbor chance %.2f%%.",
                        blockName,
                        formatPos(pos),
                        BuiltInRegistries.BLOCK.getKey(transition.to()),
                        walks,
                        protectedWalks,
                        currentChance * 100.0,
                        transition.neighborChance() * 100.0
                )),
                false
        );
        return 1;
    }

    private static Component debugOverlayText(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        TramplingRule trampling = WildPathsConfig.findTrampling(state);
        if (trampling != null) {
            BlockPos tramplingPos = normalizeTramplingPos(pos, state);
            if (WildPathsSavedData.isProtected(level, tramplingPos.below())) {
                return Component.literal("WP | " + shortBlockName(state) + " | wool protected");
            }

            WildPathsSavedData.WearEntry entry = WildPathsSavedData.get(level).wearEntry(level, tramplingPos);
            int walks = entry == null ? 0 : entry.walks();
            int failures = entry == null ? 0 : entry.failedAttempts();
            double chance = Math.min(
                    trampling.maxChance(),
                    trampling.chance() + failures * trampling.chanceIncrease()
            );
            return compactWearOverlay(
                    shortBlockName(state),
                    shortBlockName(trampling.to().defaultBlockState()),
                    walks,
                    trampling.minimumWalks(),
                    chance
            );
        }

        if (WildPathsSavedData.isProtected(level, pos)) {
            return Component.literal("WP | " + shortBlockName(state) + " | wool protected");
        }

        PathCreationRule pathCreation = WildPathsConfig.findPathCreation(state);
        if (pathCreation != null) {
            if (!WildPathsConfig.isPathCreationSpaceAllowed(level, pos)) {
                return Component.literal("WP | " + shortBlockName(state) + " | blocked: "
                        + shortBlockName(level.getBlockState(pos.above())));
            }
            WildPathsSavedData.WearEntry entry = WildPathsSavedData.get(level).wearEntry(level, pos);
            int walks = entry == null ? 0 : entry.walks();
            int failures = entry == null ? 0 : entry.failedAttempts();
            double chance = Math.min(
                    pathCreation.maxChance(),
                    pathCreation.chance() + failures * pathCreation.chanceIncrease()
            );
            return compactWearOverlay(
                    shortBlockName(state),
                    shortBlockName(pathCreation.to().defaultBlockState()),
                    walks,
                    pathCreation.minimumWalks(),
                    chance
            );
        }

        TransitionRule transition = WildPathsConfig.find(state);
        if (transition == null) {
            return Component.literal("WP | " + shortBlockName(state) + " | no rule");
        }

        WildPathsSavedData.TrackedEntry entry = WildPathsSavedData.get(level).trackedEntry(pos);
        if (entry == null) {
            return Component.literal(String.format(
                    Locale.ROOT,
                    "WP | %s â†’ %s | not tracked",
                    shortBlockName(state),
                    shortBlockName(transition.to().defaultBlockState())
            ));
        }

        long gameTime = level.getGameTime();
        long protectedTicks = Math.max(0L, transition.ticks() - (gameTime - entry.lastUse()));
        long nextAttemptTicks = Math.max(0L, transition.chanceInterval() - (gameTime - entry.lastAttempt()));
        double chance = Math.min(
                transition.maxChance(),
                transition.chance() + entry.failedAttempts() * transition.chanceIncrease()
        );
        String timer = protectedTicks > 0L
                ? "safe " + ticksToSeconds(protectedTicks) + "s"
                : "next " + ticksToSeconds(nextAttemptTicks) + "s";
        String rain = transition.requiresRain()
                ? (level.isRainingAt(pos.above()) ? " | rain" : " | dry")
                : "";
        return Component.literal(String.format(
                Locale.ROOT,
                "WP | %s â†’ %s | %s | %.0f%%%s",
                shortBlockName(state),
                shortBlockName(transition.to().defaultBlockState()),
                timer,
                chance * 100.0,
                rain
        ));
    }

    private static Component compactWearOverlay(
            String from,
            String to,
            int walks,
            int minimumWalks,
            double chance
    ) {
        if (walks <= minimumWalks) {
            return Component.literal(String.format(
                    Locale.ROOT,
                    "WP | %s â†’ %s | %d/%d | protected",
                    from,
                    to,
                    walks,
                    minimumWalks
            ));
        }
        return Component.literal(String.format(
                Locale.ROOT,
                "WP | %s â†’ %s | %d walks | %.0f%%",
                from,
                to,
                walks,
                chance * 100.0
        ));
    }

    private static String shortBlockName(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static long ticksToSeconds(long ticks) {
        return (ticks + 19L) / 20L;
    }

    private static boolean isEnabled(ServerLevel level) {
        return !WildPathsConfig.onlyOverworld() || level.dimension() == Level.OVERWORLD;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static BlockPos normalizeTramplingPos(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    private WildPathsCommands() {
    }
}

