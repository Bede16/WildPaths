package io.github.bede16.wildpaths.config;

import net.minecraft.world.level.block.Block;

/** One configured block transition caused by repeated player traffic. */
public record PathCreationRule(
        Block from,
        Block to,
        int minimumWalks,
        double chance,
        double chanceIncrease,
        double maxChance,
        double neighborChance
) {
}
