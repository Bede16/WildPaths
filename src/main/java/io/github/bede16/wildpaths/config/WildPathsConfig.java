package io.github.bede16.wildpaths.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-synchronized common configuration. */
public final class WildPathsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.LongValue DECAY_TICKS;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue MAX_CHECKS_PER_INTERVAL;
    public static final ModConfigSpec.BooleanValue ONLY_OVERWORLD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("decay");
        DECAY_TICKS = builder
                .comment("Ticks without player use before a dirt path becomes dirt. 72000 ticks are three Minecraft days.")
                .defineInRange("decayTicks", 72_000L, 20L, Long.MAX_VALUE);
        CHECK_INTERVAL = builder
                .comment("How often each dimension processes its tracked paths, in ticks.")
                .defineInRange("checkInterval", 200, 1, 72_000);
        MAX_CHECKS_PER_INTERVAL = builder
                .comment("Maximum tracked paths processed per interval and dimension. Limits tick-time spikes.")
                .defineInRange("maxChecksPerInterval", 1_024, 1, 1_000_000);
        ONLY_OVERWORLD = builder
                .comment("If true, paths are tracked and decay only in the Overworld.")
                .define("onlyOverworld", false);
        builder.pop();

        SPEC = builder.build();
    }

    private WildPathsConfig() {
    }
}
