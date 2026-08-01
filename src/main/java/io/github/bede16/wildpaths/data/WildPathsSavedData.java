package io.github.bede16.wildpaths.data;

import io.github.bede16.wildpaths.WildPaths;
import io.github.bede16.wildpaths.config.PathCreationRule;
import io.github.bede16.wildpaths.config.TramplingRule;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

public final class WildPathsSavedData extends SavedData {
    private static final String DATA_NAME = WildPaths.MOD_ID + "_paths";
    private static final String POSITIONS_TAG = "Positions";
    private static final String LAST_USES_TAG = "LastUses";
    private static final String LAST_ATTEMPTS_TAG = "LastAttempts";
    private static final String FAILED_ATTEMPTS_TAG = "FailedAttempts";
    private static final String LAST_WET_TIMES_TAG = "LastWetTimes";
    private static final String LAST_DRYING_TIMES_TAG = "LastDryingTimes";
    private static final String WEAR_POSITIONS_TAG = "WearPositions";
    private static final String WEAR_WALKS_TAG = "WearWalks";
    private static final String WEAR_FAILED_ATTEMPTS_TAG = "WearFailedAttempts";
    private static final String WEAR_LAST_TRAFFIC_TAG = "WearLastTraffic";
    private static final String WEAR_LAST_RECOVERY_TAG = "WearLastRecovery";

    private final Long2LongOpenHashMap lastUses = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastAttempts = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap failedAttempts = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap lastWetTimes = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastDryingTimes = new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue checkQueue = new LongArrayFIFOQueue();
    private final Long2IntOpenHashMap wearWalks = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap wearFailedAttempts = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap wearLastTraffic = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap wearLastRecovery = new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue wearQueue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queuedWear = new LongOpenHashSet();

    public static WildPathsSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WildPathsSavedData::new, WildPathsSavedData::load),
                DATA_NAME
        );
    }

    public static WildPathsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WildPathsSavedData data = new WildPathsSavedData();
        long[] positions = tag.getLongArray(POSITIONS_TAG);
        long[] lastUses = tag.getLongArray(LAST_USES_TAG);
        long[] lastAttempts = tag.getLongArray(LAST_ATTEMPTS_TAG);
        int[] failedAttempts = tag.getIntArray(FAILED_ATTEMPTS_TAG);
        long[] lastWetTimes = tag.getLongArray(LAST_WET_TIMES_TAG);
        long[] lastDryingTimes = tag.getLongArray(LAST_DRYING_TIMES_TAG);
        int count = Math.min(positions.length, lastUses.length);

        for (int index = 0; index < count; index++) {
            long packedPos = positions[index];
            long lastUse = lastUses[index];
            data.lastUses.put(packedPos, lastUse);
            data.lastAttempts.put(packedPos, index < lastAttempts.length ? lastAttempts[index] : lastUse);
            data.failedAttempts.put(packedPos, index < failedAttempts.length ? failedAttempts[index] : 0);
            if (index < lastWetTimes.length) {
                data.lastWetTimes.put(packedPos, lastWetTimes[index]);
            }
            if (index < lastDryingTimes.length) {
                data.lastDryingTimes.put(packedPos, lastDryingTimes[index]);
            }
            data.checkQueue.enqueue(packedPos);
        }

        if (positions.length != lastUses.length) {
            WildPaths.LOGGER.warn(
                    "Ignored malformed Wild Paths save data: {} positions, {} timestamps",
                    positions.length,
                    lastUses.length
            );
        }

        long[] wearPositions = tag.getLongArray(WEAR_POSITIONS_TAG);
        int[] wearWalks = tag.getIntArray(WEAR_WALKS_TAG);
        int[] wearFailures = tag.getIntArray(WEAR_FAILED_ATTEMPTS_TAG);
        long[] wearLastTraffic = tag.getLongArray(WEAR_LAST_TRAFFIC_TAG);
        long[] wearLastRecovery = tag.getLongArray(WEAR_LAST_RECOVERY_TAG);
        int wearCount = Math.min(wearPositions.length, wearWalks.length);
        for (int index = 0; index < wearCount; index++) {
            long packedPos = wearPositions[index];
            data.wearWalks.put(packedPos, Math.max(0, wearWalks[index]));
            data.wearFailedAttempts.put(
                    packedPos,
                    index < wearFailures.length ? Math.max(0, wearFailures[index]) : 0
            );
            if (index < wearLastTraffic.length) {
                data.wearLastTraffic.put(packedPos, wearLastTraffic[index]);
            }
            if (index < wearLastRecovery.length) {
                data.wearLastRecovery.put(packedPos, wearLastRecovery[index]);
            }
            data.enqueueWear(packedPos);
        }
        if (wearPositions.length != wearWalks.length) {
            WildPaths.LOGGER.warn(
                    "Ignored malformed Wild Paths wear data: {} positions, {} walk counters",
                    wearPositions.length,
                    wearWalks.length
            );
        }
        return data;
    }

    public void observe(BlockPos pos, long gameTime) {
        long packedPos = pos.asLong();
        if (lastUses.containsKey(packedPos)) {
            return;
        }

        checkQueue.enqueue(packedPos);
        lastUses.put(packedPos, gameTime);
        lastAttempts.put(packedPos, gameTime);
        failedAttempts.put(packedPos, 0);
        setDirty();
    }

    public void recordUse(BlockPos pos, long gameTime) {
        long packedPos = pos.asLong();
        observe(pos, gameTime);
        lastUses.put(packedPos, gameTime);
        lastAttempts.put(packedPos, gameTime);
        failedAttempts.put(packedPos, 0);
        setDirty();
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.WOOL);
    }

    public void clear(BlockPos pos) {
        long packedPos = pos.asLong();
        boolean changed = lastUses.containsKey(packedPos) || wearWalks.containsKey(packedPos);
        lastUses.remove(packedPos);
        lastAttempts.remove(packedPos);
        failedAttempts.remove(packedPos);
        lastWetTimes.remove(packedPos);
        lastDryingTimes.remove(packedPos);
        wearWalks.remove(packedPos);
        wearFailedAttempts.remove(packedPos);
        wearLastTraffic.remove(packedPos);
        wearLastRecovery.remove(packedPos);
        if (changed) {
            setDirty();
        }
    }

    public boolean recordTraffic(ServerLevel level, BlockPos pos, PathCreationRule transition) {
        if (isProtected(level, pos)) {
            clear(pos);
            return false;
        }
        if (!WildPathsConfig.isPathCreationSpaceAllowed(level, pos)) {
            return false;
        }
        if (!level.getBlockState(pos).is(transition.from())) {
            removeWear(pos.asLong());
            return false;
        }

        long packedPos = pos.asLong();
        int walks = recordWear(packedPos, level.getGameTime());

        if (walks <= transition.minimumWalks()) {
            setDirty();
            return false;
        }

        int failures = wearFailedAttempts.get(packedPos);
        double chance = Math.min(
                transition.maxChance(),
                transition.chance() + failures * transition.chanceIncrease()
        );
        if (level.random.nextDouble() > chance) {
            wearFailedAttempts.put(packedPos, failures + 1);
            setDirty();
            return false;
        }

        if (!level.setBlock(pos, transition.to().defaultBlockState(), Block.UPDATE_ALL)) {
            setDirty();
            return false;
        }

        removeWear(packedPos);
        remove(packedPos);
        TransitionRule resultingTransition = WildPathsConfig.find(level.getBlockState(pos));
        if (resultingTransition != null) {
            if (resultingTransition.resetOnWalk()) {
                recordUse(pos, level.getGameTime());
            } else {
                observe(pos, level.getGameTime());
            }
        }
        return true;
    }

    public boolean recordTrampling(ServerLevel level, BlockPos pos, TramplingRule transition) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }

        long packedPos = pos.asLong();
        BlockPos groundPos = pos.below();
        if (isProtected(level, groundPos)) {
            clear(groundPos);
            clear(pos);
            return false;
        }
        if (!state.is(transition.from())) {
            removeWear(packedPos);
            return false;
        }

        int walks = recordWear(packedPos, level.getGameTime());
        if (walks <= transition.minimumWalks()) {
            setDirty();
            return false;
        }

        int failures = wearFailedAttempts.get(packedPos);
        double chance = Math.min(
                transition.maxChance(),
                transition.chance() + failures * transition.chanceIncrease()
        );
        if (level.random.nextDouble() > chance) {
            wearFailedAttempts.put(packedPos, failures + 1);
            setDirty();
            return false;
        }

        boolean wasDoublePlant = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        if (!level.setBlock(pos, transition.to().defaultBlockState(), Block.UPDATE_ALL)) {
            setDirty();
            return false;
        }
        if (wasDoublePlant && level.getBlockState(pos.above()).is(transition.from())) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        removeWear(packedPos);
        remove(packedPos);
        return true;
    }

    public int process(ServerLevel level, long gameTime, int maxChecks) {
        int checks = Math.min(maxChecks, checkQueue.size());
        int decayed = 0;

        for (int index = 0; index < checks; index++) {
            long packedPos = checkQueue.dequeueLong();
            if (!lastUses.containsKey(packedPos)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packedPos);
            if (level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())
            ) == null) {
                checkQueue.enqueue(packedPos);
                continue;
            }

            if (isProtected(level, pos)) {
                clear(pos);
                continue;
            }

            TransitionRule transition = WildPathsConfig.find(level.getBlockState(pos));
            if (transition == null) {
                remove(packedPos);
                continue;
            }

            if (gameTime - lastUses.get(packedPos) < transition.ticks()) {
                checkQueue.enqueue(packedPos);
                continue;
            }

            if (transition.requiresRain()) {
                if (!level.isRainingAt(pos.above())) {
                    dryTrackedBlock(packedPos, gameTime, transition);
                    checkQueue.enqueue(packedPos);
                    continue;
                }
                lastWetTimes.put(packedPos, gameTime);
                lastDryingTimes.put(packedPos, gameTime);
                setDirty();
            }

            if (gameTime - lastAttempts.get(packedPos) < transition.chanceInterval()) {
                checkQueue.enqueue(packedPos);
                continue;
            }

            int failures = failedAttempts.get(packedPos);
            double chance = Math.min(
                    transition.maxChance(),
                    transition.chance() + failures * transition.chanceIncrease()
            );
            lastAttempts.put(packedPos, gameTime);

            if (level.random.nextDouble() <= chance) {
                level.setBlock(pos, transition.to().defaultBlockState(), Block.UPDATE_ALL);
                removeWear(packedPos);
                remove(packedPos);
                decayed++;
            } else {
                failedAttempts.put(packedPos, failures + 1);
                checkQueue.enqueue(packedPos);
                setDirty();
            }
        }
        processWearRecovery(level, gameTime, maxChecks);
        return decayed;
    }

    public int size() {
        return lastUses.size();
    }

    public int wearSize() {
        return wearWalks.size();
    }

    public TrackedEntry trackedEntry(BlockPos pos) {
        long packedPos = pos.asLong();
        if (!lastUses.containsKey(packedPos)) {
            return null;
        }
        return new TrackedEntry(
                lastUses.get(packedPos),
                lastAttempts.get(packedPos),
                failedAttempts.get(packedPos)
        );
    }

    public WearEntry wearEntry(BlockPos pos) {
        long packedPos = pos.asLong();
        if (!wearWalks.containsKey(packedPos)) {
            return null;
        }
        return new WearEntry(wearWalks.get(packedPos), wearFailedAttempts.get(packedPos));
    }

    public WearEntry wearEntry(ServerLevel level, BlockPos pos) {
        recoverWear(pos.asLong(), level.getGameTime());
        return wearEntry(pos);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = new long[lastUses.size()];
        long[] timestamps = new long[lastUses.size()];
        long[] attempts = new long[lastUses.size()];
        int[] failures = new int[lastUses.size()];
        long[] wetTimes = new long[lastUses.size()];
        long[] dryingTimes = new long[lastUses.size()];
        int index = 0;

        for (Long2LongOpenHashMap.Entry entry : lastUses.long2LongEntrySet()) {
            long packedPos = entry.getLongKey();
            positions[index] = packedPos;
            timestamps[index] = entry.getLongValue();
            attempts[index] = lastAttempts.get(packedPos);
            failures[index] = failedAttempts.get(packedPos);
            wetTimes[index] = lastWetTimes.getOrDefault(packedPos, 0L);
            dryingTimes[index] = lastDryingTimes.getOrDefault(packedPos, 0L);
            index++;
        }

        tag.putLongArray(POSITIONS_TAG, positions);
        tag.putLongArray(LAST_USES_TAG, timestamps);
        tag.putLongArray(LAST_ATTEMPTS_TAG, attempts);
        tag.putIntArray(FAILED_ATTEMPTS_TAG, failures);
        tag.putLongArray(LAST_WET_TIMES_TAG, wetTimes);
        tag.putLongArray(LAST_DRYING_TIMES_TAG, dryingTimes);

        long[] wearPositions = new long[wearWalks.size()];
        int[] walks = new int[wearWalks.size()];
        int[] wearFailures = new int[wearWalks.size()];
        long[] wearTrafficTimes = new long[wearWalks.size()];
        long[] wearRecoveryTimes = new long[wearWalks.size()];
        index = 0;
        for (Long2IntOpenHashMap.Entry entry : wearWalks.long2IntEntrySet()) {
            long packedPos = entry.getLongKey();
            wearPositions[index] = packedPos;
            walks[index] = entry.getIntValue();
            wearFailures[index] = wearFailedAttempts.get(packedPos);
            wearTrafficTimes[index] = wearLastTraffic.getOrDefault(packedPos, 0L);
            wearRecoveryTimes[index] = wearLastRecovery.getOrDefault(packedPos, 0L);
            index++;
        }
        tag.putLongArray(WEAR_POSITIONS_TAG, wearPositions);
        tag.putIntArray(WEAR_WALKS_TAG, walks);
        tag.putIntArray(WEAR_FAILED_ATTEMPTS_TAG, wearFailures);
        tag.putLongArray(WEAR_LAST_TRAFFIC_TAG, wearTrafficTimes);
        tag.putLongArray(WEAR_LAST_RECOVERY_TAG, wearRecoveryTimes);
        return tag;
    }

    private void remove(long packedPos) {
        lastUses.remove(packedPos);
        lastAttempts.remove(packedPos);
        failedAttempts.remove(packedPos);
        lastWetTimes.remove(packedPos);
        lastDryingTimes.remove(packedPos);
        setDirty();
    }

    private void dryTrackedBlock(long packedPos, long gameTime, TransitionRule transition) {
        int failures = failedAttempts.get(packedPos);
        if (failures <= 0 || transition.dryingChanceDecrease() <= 0.0) {
            return;
        }

        if (!lastWetTimes.containsKey(packedPos)) {
            lastWetTimes.put(packedPos, gameTime);
            lastDryingTimes.put(packedPos, gameTime);
            setDirty();
            return;
        }

        long lastWet = lastWetTimes.get(packedPos);
        if (gameTime < lastWet) {
            lastWetTimes.put(packedPos, gameTime);
            lastDryingTimes.put(packedPos, gameTime);
            setDirty();
            return;
        }

        long dryingStart = lastWet + transition.dryingDelay();
        long lastDrying = Math.max(
                lastDryingTimes.getOrDefault(packedPos, lastWet),
                dryingStart
        );
        long elapsed = gameTime - lastDrying;
        long intervals = elapsed / transition.dryingInterval();
        if (intervals <= 0L) {
            return;
        }

        double chanceDecrease = intervals * transition.dryingChanceDecrease();
        int failureDecrease = (int) Math.min(
                Integer.MAX_VALUE,
                Math.ceil(chanceDecrease / transition.chanceIncrease() - 1.0E-12)
        );
        failedAttempts.put(packedPos, Math.max(0, failures - failureDecrease));
        lastDryingTimes.put(
                packedPos,
                lastDrying + intervals * transition.dryingInterval()
        );
        setDirty();
    }

    private void removeWear(long packedPos) {
        wearWalks.remove(packedPos);
        wearFailedAttempts.remove(packedPos);
        wearLastTraffic.remove(packedPos);
        wearLastRecovery.remove(packedPos);
        setDirty();
    }

    private int recordWear(long packedPos, long gameTime) {
        recoverWear(packedPos, gameTime);
        if (!wearWalks.containsKey(packedPos)) {
            enqueueWear(packedPos);
        }
        int walks = wearWalks.get(packedPos) + 1;
        wearWalks.put(packedPos, walks);
        wearLastTraffic.put(packedPos, gameTime);
        wearLastRecovery.put(packedPos, gameTime);
        setDirty();
        return walks;
    }

    private void processWearRecovery(ServerLevel level, long gameTime, int maxChecks) {
        int checks = Math.min(maxChecks, wearQueue.size());
        for (int index = 0; index < checks; index++) {
            long packedPos = wearQueue.dequeueLong();
            queuedWear.remove(packedPos);
            if (!wearWalks.containsKey(packedPos)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packedPos);
            if (level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())
            ) == null) {
                enqueueWear(packedPos);
                continue;
            }

            BlockState state = level.getBlockState(pos);
            PathCreationRule pathRule = WildPathsConfig.findPathCreation(state);
            TramplingRule tramplingRule = WildPathsConfig.findTrampling(state);
            boolean protectedByWool = pathRule != null
                    ? isProtected(level, pos)
                    : tramplingRule != null && isProtected(level, pos.below());
            if (protectedByWool || (pathRule == null && tramplingRule == null)) {
                removeWear(packedPos);
                continue;
            }

            recoverWear(packedPos, gameTime);
            if (wearWalks.containsKey(packedPos)) {
                enqueueWear(packedPos);
            }
        }
    }

    private void recoverWear(long packedPos, long gameTime) {
        if (!wearWalks.containsKey(packedPos) || !WildPathsConfig.wearRecoveryEnabled()) {
            return;
        }

        if (!wearLastTraffic.containsKey(packedPos)) {
            wearLastTraffic.put(packedPos, gameTime);
            wearLastRecovery.put(packedPos, gameTime);
            setDirty();
            return;
        }

        long lastTraffic = wearLastTraffic.get(packedPos);
        if (gameTime < lastTraffic) {
            wearLastTraffic.put(packedPos, gameTime);
            wearLastRecovery.put(packedPos, gameTime);
            setDirty();
            return;
        }

        long recoveryStart = lastTraffic + WildPathsConfig.wearRecoveryDelayTicks();
        long lastRecovery = Math.max(wearLastRecovery.getOrDefault(packedPos, lastTraffic), recoveryStart);
        long elapsed = gameTime - lastRecovery;
        long intervals = elapsed / WildPathsConfig.wearRecoveryIntervalTicks();
        if (intervals <= 0L) {
            return;
        }

        long recoveredLong = intervals > Integer.MAX_VALUE / WildPathsConfig.wearRecoveryAmountPerInterval()
                ? Integer.MAX_VALUE
                : intervals * WildPathsConfig.wearRecoveryAmountPerInterval();
        int recovered = (int) recoveredLong;
        int walks = Math.max(0, wearWalks.get(packedPos) - recovered);
        int failures = Math.max(0, wearFailedAttempts.get(packedPos) - recovered);
        if (walks == 0) {
            removeWear(packedPos);
            return;
        }

        wearWalks.put(packedPos, walks);
        wearFailedAttempts.put(packedPos, failures);
        wearLastRecovery.put(
                packedPos,
                lastRecovery + intervals * WildPathsConfig.wearRecoveryIntervalTicks()
        );
        setDirty();
    }

    private void enqueueWear(long packedPos) {
        if (queuedWear.add(packedPos)) {
            wearQueue.enqueue(packedPos);
        }
    }

    public record TrackedEntry(long lastUse, long lastAttempt, int failedAttempts) {
    }

    public record WearEntry(int walks, int failedAttempts) {
    }
}
