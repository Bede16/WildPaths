package io.github.bede16.wildpaths.data;

import io.github.bede16.wildpaths.WildPaths;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

public final class WildPathsSavedData extends SavedData {
    private static final String DATA_NAME = WildPaths.MOD_ID + "_paths";
    private static final String POSITIONS_TAG = "Positions";
    private static final String LAST_USES_TAG = "LastUses";
    private static final String LAST_ATTEMPTS_TAG = "LastAttempts";
    private static final String FAILED_ATTEMPTS_TAG = "FailedAttempts";

    private final Long2LongOpenHashMap lastUses = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastAttempts = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap failedAttempts = new Long2IntOpenHashMap();
    private final LongArrayFIFOQueue checkQueue = new LongArrayFIFOQueue();

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
            WildPaths.LOGGER.warn("Ignored malformed Wild Paths save data: {} positions, {} timestamps", positions.length, lastUses.length);
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
        return tag;
    }

    private void remove(long packedPos) {
        lastUses.remove(packedPos);
        lastAttempts.remove(packedPos);
        failedAttempts.remove(packedPos);
        setDirty();
    }
}
