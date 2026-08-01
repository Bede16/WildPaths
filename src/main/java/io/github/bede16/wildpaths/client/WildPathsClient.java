package io.github.bede16.wildpaths.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class WildPathsClient {
    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> WildPathsConfigScreen.createLocal(parent)
        );
    }

    private WildPathsClient() {
    }
}

