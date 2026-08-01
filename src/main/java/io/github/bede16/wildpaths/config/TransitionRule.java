package io.github.bede16.wildpaths.config;

import net.minecraft.world.level.block.Block;

/** One configured block transition. */
public record TransitionRule(
        Block from,
        Block to,
        long ticks,
        long chanceInterval,
        double chance,
        double chanceIncrease,
        double maxChance,
        boolean requiresRain,
        boolean resetOnWalk,
        boolean discoverNearby
) {
}
