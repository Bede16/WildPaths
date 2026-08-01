package io.github.bede16.wildpaths.network;

import io.github.bede16.wildpaths.WildPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNumericConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<OpenNumericConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WildPaths.MOD_ID, "open_numeric_config")
    );
    public static final StreamCodec<FriendlyByteBuf, OpenNumericConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    OpenNumericConfigPayload::json,
                    OpenNumericConfigPayload::new
            );

    @Override
    public Type<OpenNumericConfigPayload> type() {
        return TYPE;
    }
}

