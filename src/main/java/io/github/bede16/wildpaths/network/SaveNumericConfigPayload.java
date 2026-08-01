package io.github.bede16.wildpaths.network;

import io.github.bede16.wildpaths.WildPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveNumericConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<SaveNumericConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WildPaths.MOD_ID, "save_numeric_config")
    );
    public static final StreamCodec<FriendlyByteBuf, SaveNumericConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SaveNumericConfigPayload::json,
                    SaveNumericConfigPayload::new
            );

    @Override
    public Type<SaveNumericConfigPayload> type() {
        return TYPE;
    }
}

