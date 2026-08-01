package io.github.bede16.wildpaths.data;

import io.github.bede16.wildpaths.WildPaths;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

public final class WildPathsSavedData extends SavedData {
    private static final String DATA_NAME = WildPaths.MOD_ID + "_paths";
    private static final String POSITIONS_TAG = "Positions";
    private static final String LAST_USES_TAG = "LastUses";

    private final Long2LongOpenHashMap lastUses = new Long2LongOpenHashMap();
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
        int count = Math.min(positions.length, lastUses.length);

        for (int index = 0; index < count; index++) {
            data.lastUses.put(positions[index], lastUses[index]);
            data.checkQueue.enqueue(positions[index]);
        }

        if (positions.length != lastUses.length) {
            WildPaths.LOGGER.warn("Ignored malformed Wild Paths save data: {} positions, {} timestamps", positions.length, lastUses.length);
        }
        return data;
    }

    public void recordUse(BlockPos pos, long gameTime) {
        long packedPos = pos.asLong();
        if (!lastUses.containsKey(packedPos)) {
            checkQueue.enqueue(packedPos);
        }
        lastUses.put(packedPos, gameTime);
        setDirty();
    }

    public int process(ServerLevel level, long gameTime, long decayTicks, int maxChecks) {
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

            if (!level.getBlockState(pos).is(Blocks.DIRT_PATH)) {
                lastUses.remove(packedPos);
                setDirty();
                continue;
            }

            if (gameTime - lastUses.get(packedPos) >= decayTicks) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                lastUses.remove(packedPos);
                setDirty();
                decayed++;
            } else {
                checkQueue.enqueue(packedPos);
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
        int index = 0;

        for (Long2LongOpenHashMap.Entry entry : lastUses.long2LongEntrySet()) {
            positions[index] = entry.getLongKey();
            timestamps[index] = entry.getLongValue();
            index++;
        }

        tag.putLongArray(POSITIONS_TAG, positions);
        tag.putLongArray(LAST_USES_TAG, timestamps);
        return tag;
    }
}
