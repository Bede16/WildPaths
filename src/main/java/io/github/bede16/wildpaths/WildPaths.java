package io.github.bede16.wildpaths;

import io.github.bede16.wildpaths.config.WildPathsConfig;
import io.github.bede16.wildpaths.event.WildPathsEvents;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Main entry point for Wild Paths. */
@Mod(WildPaths.MOD_ID)
public final class WildPaths {
    public static final String MOD_ID = "wild_paths";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WildPaths(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, WildPathsConfig.SPEC);
        NeoForge.EVENT_BUS.register(new WildPathsEvents());
    }
}
