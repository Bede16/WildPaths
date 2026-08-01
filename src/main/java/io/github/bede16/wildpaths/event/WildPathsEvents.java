package io.github.bede16.wildpaths.event;

import io.github.bede16.wildpaths.WildPaths;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.data.WildPathsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WildPathsEvents {
    private static final int PLAYER_SAMPLE_INTERVAL = 20;
    private final Map<UUID, Integer> scanCursors = new HashMap<>();

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

        WildPathsSavedData data = WildPathsSavedData.get(level);
        BlockPos observedPos = player.blockPosition().below();
        TransitionRule walkedTransition = WildPathsConfig.find(level.getBlockState(observedPos));
        if (walkedTransition != null) {
            if (walkedTransition.resetOnWalk()) {
                data.recordUse(observedPos, level.getGameTime());
            } else {
                data.observe(observedPos, level.getGameTime());
            }
        }

        scanNearbySurface(player, level, data);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        scanCursors.clear();
        WildPathsConfig.load();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        int interval = WildPathsConfig.checkInterval();
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
                    WildPathsConfig.maxChecksPerInterval()
            );
            if (decayed > 0) {
                WildPaths.LOGGER.debug("Recovered {} paths in {} ({} still tracked)", decayed, level.dimension().location(), data.size());
            }
        }
    }

    private static boolean isEnabled(ServerLevel level) {
        return !WildPathsConfig.onlyOverworld() || level.dimension() == Level.OVERWORLD;
    }

    private void scanNearbySurface(ServerPlayer player, ServerLevel level, WildPathsSavedData data) {
        int columnsPerSample = WildPathsConfig.nearbyScanColumnsPerPlayer();
        if (columnsPerSample == 0) {
            return;
        }

        int radius = WildPathsConfig.nearbyScanRadius();
        int diameter = radius * 2 + 1;
        int totalColumns = diameter * diameter;
        int columns = Math.min(columnsPerSample, totalColumns);
        int cursor = scanCursors.getOrDefault(player.getUUID(), 0);
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos scannedPos = new BlockPos.MutableBlockPos();

        for (int offset = 0; offset < columns; offset++) {
            int columnIndex = (cursor + offset) % totalColumns;
            int blockX = center.getX() + columnIndex % diameter - radius;
            int blockZ = center.getZ() + columnIndex / diameter - radius;

            if (level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(blockX),
                    SectionPos.blockToSectionCoord(blockZ)
            ) == null) {
                continue;
            }

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
            for (int depth = 0; depth <= WildPathsConfig.nearbyScanDepth(); depth++) {
                scannedPos.set(blockX, surfaceY - depth, blockZ);
                TransitionRule transition = WildPathsConfig.find(level.getBlockState(scannedPos));
                if (transition != null && transition.discoverNearby()) {
                    data.observe(scannedPos, level.getGameTime());
                }
            }
        }

        scanCursors.put(player.getUUID(), (cursor + columns) % totalColumns);
    }
}
