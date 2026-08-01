package io.github.bede16.wildpaths.network;

import io.github.bede16.wildpaths.client.WildPathsClientPayloadHandler;
import io.github.bede16.wildpaths.config.WildPathsConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WildPathsNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                SaveNumericConfigPayload.TYPE,
                SaveNumericConfigPayload.STREAM_CODEC,
                WildPathsNetwork::handleSave
        );
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(
                    OpenNumericConfigPayload.TYPE,
                    OpenNumericConfigPayload.STREAM_CODEC,
                    WildPathsClientPayloadHandler::handleOpen
            );
        } else {
            registrar.playToClient(
                    OpenNumericConfigPayload.TYPE,
                    OpenNumericConfigPayload.STREAM_CODEC,
                    (payload, context) -> {
                    }
            );
        }
    }

    private static void handleSave(SaveNumericConfigPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.createCommandSourceStack().hasPermission(Commands.LEVEL_GAMEMASTERS)) {
            player.sendSystemMessage(Component.literal("Wild Paths: You do not have permission to edit the server config."));
            return;
        }

        String error = WildPathsConfig.applyConfigScreenChanges(payload.json());
        if (error == null) {
            player.sendSystemMessage(Component.literal("Wild Paths: Configuration saved and applied."));
        } else {
            player.sendSystemMessage(Component.literal("Wild Paths: Configuration was not saved: " + error));
        }
    }

    private WildPathsNetwork() {
    }
}

