package io.github.bede16.wildpaths.event;

import io.github.bede16.wildpaths.WildPaths;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.data.WildPathsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WildPathsEvents {
    private static final int PLAYER_SAMPLE_INTERVAL = 20;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % PLAYER_SAMPLE_INTERVAL != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !isEnabled(level)) {
            return;
        }

        BlockPos pathPos = player.blockPosition().below();
        if (level.getBlockState(pathPos).is(Blocks.DIRT_PATH)) {
            WildPathsSavedData.get(level).recordUse(pathPos, level.getGameTime());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        int interval = WildPathsConfig.CHECK_INTERVAL.getAsInt();
        long gameTime = event.getServer().getTickCount();
        if (gameTime % interval != 0) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!isEnabled(level)) {
                continue;
            }

            WildPathsSavedData data = WildPathsSavedData.get(level);
            int decayed = data.process(
                    level,
                    level.getGameTime(),
                    WildPathsConfig.DECAY_TICKS.getAsLong(),
                    WildPathsConfig.MAX_CHECKS_PER_INTERVAL.getAsInt()
            );
            if (decayed > 0) {
                WildPaths.LOGGER.debug("Recovered {} paths in {} ({} still tracked)", decayed, level.dimension().location(), data.size());
            }
        }
    }

    private static boolean isEnabled(ServerLevel level) {
        return !WildPathsConfig.ONLY_OVERWORLD.getAsBoolean() || level.dimension() == Level.OVERWORLD;
    }
}
