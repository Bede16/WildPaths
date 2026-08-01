package io.github.bede16.wildpaths;

import io.github.bede16.wildpaths.client.WildPathsClient;
import io.github.bede16.wildpaths.event.WildPathsEvents;
import io.github.bede16.wildpaths.network.WildPathsNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Main entry point for Wild Paths. */
@Mod(WildPaths.MOD_ID)
public final class WildPaths {
    public static final String MOD_ID = "wild_paths";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WildPaths(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(new WildPathsEvents());
        modBus.addListener(WildPathsNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WildPathsClient.register(modContainer);
        }
    }
}

