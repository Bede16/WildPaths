package io.github.bede16.wildpaths.config;

import net.minecraft.world.level.block.Block;

/** One configured plant transition caused by players walking through it. */
public record TramplingRule(
        Block from,
        Block to,
        int minimumWalks,
        double chance,
        double chanceIncrease,
        double maxChance
) {
}
