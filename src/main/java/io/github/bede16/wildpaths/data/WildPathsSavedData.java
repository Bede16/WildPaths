package io.github.bede16.wildpaths.data;

import io.github.bede16.wildpaths.WildPaths;
import io.github.bede16.wildpaths.config.PathCreationRule;
import io.github.bede16.wildpaths.config.TramplingRule;
import io.github.bede16.wildpaths.config.TransitionRule;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
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
    private static final String WEAR_POSITIONS_TAG = "WearPositions";
    private static final String WEAR_WALKS_TAG = "WearWalks";
    private static final String WEAR_FAILED_ATTEMPTS_TAG = "WearFailedAttempts";

    private final Long2LongOpenHashMap lastUses = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastAttempts = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap failedAttempts = new Long2IntOpenHashMap();
    private final LongArrayFIFOQueue checkQueue = new LongArrayFIFOQueue();
    private final Long2IntOpenHashMap wearWalks = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap wearFailedAttempts = new Long2IntOpenHashMap();

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
        int count = Math.min(positions.length, lastUses.length);

        for (int index = 0; index < count; index++) {
            long packedPos = positions[index];
            long lastUse = lastUses[index];
            data.lastUses.put(packedPos, lastUse);
            data.lastAttempts.put(packedPos, index < lastAttempts.length ? lastAttempts[index] : lastUse);
            data.failedAttempts.put(packedPos, index < failedAttempts.length ? failedAttempts[index] : 0);
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
        int wearCount = Math.min(wearPositions.length, wearWalks.length);
        for (int index = 0; index < wearCount; index++) {
            long packedPos = wearPositions[index];
            data.wearWalks.put(packedPos, Math.max(0, wearWalks[index]));
            data.wearFailedAttempts.put(
                    packedPos,
                    index < wearFailures.length ? Math.max(0, wearFailures[index]) : 0
            );
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
        wearWalks.remove(packedPos);
        wearFailedAttempts.remove(packedPos);
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
        int walks = wearWalks.get(packedPos) + 1;
        wearWalks.put(packedPos, walks);

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

        int walks = wearWalks.get(packedPos) + 1;
        wearWalks.put(packedPos, walks);
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

            if (gameTime - lastUses.get(packedPos) < transition.ticks()
                    || gameTime - lastAttempts.get(packedPos) < transition.chanceInterval()) {
                checkQueue.enqueue(packedPos);
                continue;
            }

            if (transition.requiresRain() && !level.isRainingAt(pos.above())) {
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = new long[lastUses.size()];
        long[] timestamps = new long[lastUses.size()];
        long[] attempts = new long[lastUses.size()];
        int[] failures = new int[lastUses.size()];
        int index = 0;

        for (Long2LongOpenHashMap.Entry entry : lastUses.long2LongEntrySet()) {
            long packedPos = entry.getLongKey();
            positions[index] = packedPos;
            timestamps[index] = entry.getLongValue();
            attempts[index] = lastAttempts.get(packedPos);
            failures[index] = failedAttempts.get(packedPos);
            index++;
        }

        tag.putLongArray(POSITIONS_TAG, positions);
        tag.putLongArray(LAST_USES_TAG, timestamps);
        tag.putLongArray(LAST_ATTEMPTS_TAG, attempts);
        tag.putIntArray(FAILED_ATTEMPTS_TAG, failures);

        long[] wearPositions = new long[wearWalks.size()];
        int[] walks = new int[wearWalks.size()];
        int[] wearFailures = new int[wearWalks.size()];
        index = 0;
        for (Long2IntOpenHashMap.Entry entry : wearWalks.long2IntEntrySet()) {
            long packedPos = entry.getLongKey();
            wearPositions[index] = packedPos;
            walks[index] = entry.getIntValue();
            wearFailures[index] = wearFailedAttempts.get(packedPos);
            index++;
        }
        tag.putLongArray(WEAR_POSITIONS_TAG, wearPositions);
        tag.putIntArray(WEAR_WALKS_TAG, walks);
        tag.putIntArray(WEAR_FAILED_ATTEMPTS_TAG, wearFailures);
        return tag;
    }

    private void remove(long packedPos) {
        lastUses.remove(packedPos);
        lastAttempts.remove(packedPos);
        failedAttempts.remove(packedPos);
        setDirty();
    }

    private void removeWear(long packedPos) {
        wearWalks.remove(packedPos);
        wearFailedAttempts.remove(packedPos);
        setDirty();
    }

    public record TrackedEntry(long lastUse, long lastAttempt, int failedAttempts) {
    }

    public record WearEntry(int walks, int failedAttempts) {
    }
}
