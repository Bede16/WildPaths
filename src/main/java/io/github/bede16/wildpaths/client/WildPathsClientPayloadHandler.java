package io.github.bede16.wildpaths.client;

import io.github.bede16.wildpaths.network.OpenNumericConfigPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class WildPathsClientPayloadHandler {
    public static void handleOpen(OpenNumericConfigPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(WildPathsConfigScreen.createRemote(minecraft.screen, payload.json()));
    }

    private WildPathsClientPayloadHandler() {
    }
}

