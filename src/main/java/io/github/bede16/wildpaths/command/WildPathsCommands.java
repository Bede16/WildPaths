package io.github.bede16.wildpaths.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.data.WildPathsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Administrative commands for reloading and inspecting Wild Paths. */
public final class WildPathsCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wildpaths")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> debug(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                                        ))))
        );
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

    private static int status(CommandSourceStack source) {
        int enabledLevels = 0;
        int trackedBlocks = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (!isEnabled(level)) {
                continue;
            }
            enabledLevels++;
            trackedBlocks += WildPathsSavedData.get(level).size();
        }

        int finalEnabledLevels = enabledLevels;
        int finalTrackedBlocks = trackedBlocks;
        source.sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Wild Paths: %d transition rules, %d tracked blocks across %d enabled dimensions.",
                        WildPathsConfig.transitionCount(),
                        finalTrackedBlocks,
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
        String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        if (transition == null) {
            source.sendFailure(Component.literal(String.format(
                    Locale.ROOT,
                    "Wild Paths: %s at %s has no configured transition.",
                    blockName,
                    formatPos(pos)
            )));
            return 0;
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

    private static boolean isEnabled(ServerLevel level) {
        return !WildPathsConfig.onlyOverworld() || level.dimension() == Level.OVERWORLD;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private WildPathsCommands() {
    }
}
