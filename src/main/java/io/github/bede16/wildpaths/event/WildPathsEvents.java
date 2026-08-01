package io.github.bede16.wildpaths.event;

import io.github.bede16.wildpaths.WildPaths;
import io.github.bede16.wildpaths.command.WildPathsCommands;
import io.github.bede16.wildpaths.config.PathCreationRule;
import io.github.bede16.wildpaths.config.TramplingRule;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.data.WildPathsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WildPathsEvents {
    private static final int PLAYER_SAMPLE_INTERVAL = 20;
    private final Map<UUID, Integer> scanCursors = new HashMap<>();
    private final Map<UUID, WalkSample> lastWalkSamples = new HashMap<>();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WildPathsCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !event.getPlacedBlock().is(net.minecraft.tags.BlockTags.WOOL)) {
            return;
        }
        WildPathsSavedData data = WildPathsSavedData.get(level);
        data.clear(event.getPos().above());
        data.clear(event.getPos().above(2));
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 10 == 0) {
            WildPathsCommands.tickDebugOverlay(player);
        }
        if (!(player.level() instanceof ServerLevel level) || !isEnabled(level)) {
            return;
        }

        WildPathsSavedData data = WildPathsSavedData.get(level);
        BlockPos observedPos = walkedBlockPos(player, level);
        BlockState observedState = level.getBlockState(observedPos);
        boolean tramplingBlock = WildPathsConfig.findTrampling(observedState) != null;
        BlockPos protectedGroundPos = tramplingBlock ? observedPos.below() : observedPos;
        boolean protectedByWool = WildPathsSavedData.isProtected(level, protectedGroundPos);
        if (protectedByWool) {
            data.clear(protectedGroundPos);
            data.clear(observedPos);
        } else {
            recordPlayerTraffic(player, level, data, observedPos, observedState);
        }

        if (player.tickCount % PLAYER_SAMPLE_INTERVAL != 0) {
            return;
        }

        TransitionRule walkedTransition = WildPathsConfig.find(observedState);
        if (!protectedByWool && walkedTransition != null) {
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
        lastWalkSamples.clear();
        WildPathsCommands.clearDebugOverlays();
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
                WildPaths.LOGGER.debug(
                        "Applied {} timed transitions in {} ({} still tracked)",
                        decayed,
                        level.dimension().location(),
                        data.size()
                );
            }
        }
    }

    private static boolean isEnabled(ServerLevel level) {
        return !WildPathsConfig.onlyOverworld() || level.dimension() == Level.OVERWORLD;
    }

    private static boolean isConfigured(BlockState state) {
        return WildPathsConfig.find(state) != null
                || WildPathsConfig.findPathCreation(state) != null
                || WildPathsConfig.findTrampling(state) != null;
    }

    private static BlockPos walkedBlockPos(ServerPlayer player, ServerLevel level) {
        BlockPos feetPos = player.blockPosition();
        if (isConfigured(level.getBlockState(feetPos))) {
            return feetPos;
        }
        return feetPos.below();
    }

    private void recordPlayerTraffic(
            ServerPlayer player,
            ServerLevel level,
            WildPathsSavedData data,
            BlockPos observedPos,
            BlockState observedState
    ) {
        long packedPos = observedPos.asLong();
        WalkSample previous = lastWalkSamples.get(player.getUUID());
        boolean landedOrEntered = player.onGround()
                && (previous == null || !previous.onGround() || previous.packedPos() != packedPos);

        TramplingRule trampling = WildPathsConfig.findTrampling(observedState);
        if (landedOrEntered && trampling != null) {
            boolean trampled = data.recordTrampling(level, observedPos, trampling);
            if (trampled) {
                WildPaths.LOGGER.debug(
                        "Player traffic trampled {} to {} at {}",
                        trampling.from(),
                        trampling.to(),
                        observedPos
                );
            }
        } else {
            PathCreationRule transition = WildPathsConfig.findPathCreation(observedState);
            if (landedOrEntered && transition != null) {
                boolean created = data.recordTraffic(level, observedPos, transition);
                influenceNeighborWear(level, data, observedPos);
                if (created) {
                    WildPaths.LOGGER.debug(
                            "Player traffic changed {} to {} at {}",
                            transition.from(),
                            transition.to(),
                            observedPos
                    );
                }
            }
        }

        lastWalkSamples.put(player.getUUID(), new WalkSample(packedPos, player.onGround()));
    }

    private static void influenceNeighborWear(
            ServerLevel level,
            WildPathsSavedData data,
            BlockPos center
    ) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }

                int blockX = center.getX() + offsetX;
                int blockZ = center.getZ() + offsetZ;
                if (level.getChunkSource().getChunkNow(
                        SectionPos.blockToSectionCoord(blockX),
                        SectionPos.blockToSectionCoord(blockZ)
                ) == null) {
                    continue;
                }

                for (int offsetY = 1; offsetY >= -1; offsetY--) {
                    BlockPos neighborPos = center.offset(offsetX, offsetY, offsetZ);
                    PathCreationRule neighborRule = WildPathsConfig.findPathCreation(
                            level.getBlockState(neighborPos)
                    );
                    if (neighborRule == null) {
                        continue;
                    }

                    if (!WildPathsConfig.isPathCreationSpaceAllowed(level, neighborPos)) {
                        break;
                    }

                    if (level.random.nextDouble() <= neighborRule.neighborChance()) {
                        data.recordTraffic(level, neighborPos, neighborRule);
                    }
                    break;
                }
            }
        }
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
                if (WildPathsSavedData.isProtected(level, scannedPos)) {
                    data.clear(scannedPos);
                    continue;
                }
                TransitionRule transition = WildPathsConfig.find(level.getBlockState(scannedPos));
                if (transition != null && transition.discoverNearby()) {
                    data.observe(scannedPos, level.getGameTime());
                }
            }
        }

        scanCursors.put(player.getUUID(), (cursor + columns) % totalColumns);
    }

    private record WalkSample(long packedPos, boolean onGround) {
    }
}
